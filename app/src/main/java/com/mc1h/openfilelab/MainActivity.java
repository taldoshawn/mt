package com.mc1h.openfilelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** A new, independently written SAF file manager. No MT Manager code or assets. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(12, 18, 28);
    private static final int SURFACE = Color.rgb(23, 33, 47);
    private static final int TEXT = Color.rgb(234, 242, 249);
    private static final int MUTED = Color.rgb(153, 170, 185);
    private static final int ACCENT = Color.rgb(110, 231, 183);
    private static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".apk") ||
                lower.endsWith(".jar") || lower.endsWith(".apks");
    }
    private static final String[] PROJECTION = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Panel[] panels = {new Panel(), new Panel()};
    private LinearLayout body;
    private boolean closed;

    private static final class Item {
        final String id, name, mime;
        final long size;
        Item(String id, String name, String mime, long size) {
            this.id = id;
            this.name = name;
            this.mime = mime;
            this.size = size;
        }
        boolean isDir() { return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime); }
    }

    private static final class Panel {
        Uri tree;
        final ArrayDeque<String> ids = new ArrayDeque<>();
        final ArrayDeque<String> names = new ArrayDeque<>();
        List<Item> items = Collections.emptyList();
        String error;
        String filter = "";
        boolean busy;
        int requestId;
        String currentId() { return ids.peekLast(); }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        setContentView(shell);
        TextView heading = label("OPENFILELAB", 21, ACCENT, true);
        heading.setLetterSpacing(0.12f);
        heading.setPadding(dp(16), dp(17), dp(12), dp(2));
        shell.addView(heading);
        TextView subtitle = label("v0.2.1 • arquivos • APK • DEX • ZIP", 11, MUTED, false);
        subtitle.setPadding(dp(16), 0, dp(8), dp(13));
        shell.addView(subtitle);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int i = 0; i < 2; i++) {
            String saved = getPreferences(MODE_PRIVATE).getString("root_" + i, "");
            if (!saved.isEmpty()) {
                try {
                    Panel panel = panels[i];
                    panel.tree = Uri.parse(saved);
                    panel.ids.add(DocumentsContract.getTreeDocumentId(panel.tree));
                    panel.names.add("Raiz");
                    load(i);
                } catch (RuntimeException ignored) { panels[i].tree = null; }
            }
        }
        render();
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String content, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private GradientDrawable pill(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(11));
        d.setStroke(dp(1), stroke);
        return d;
    }
    private TextView action(String text, Runnable callback) {
        TextView button = label(text, 12, ACCENT, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setBackground(pill(SURFACE, Color.rgb(44, 63, 79)));
        button.setOnClickListener(v -> callback.run());
        return button;
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(6), dp(6), dp(6), dp(6));
        return layout;
    }

    private void render() {
        if (closed || body == null) return;
        body.removeAllViews();
        for (int index = 0; index < 2; index++) {
            final int pane = index;
            Panel p = panels[pane];
            LinearLayout col = column();
            col.setBackground(pill(Color.rgb(17, 26, 39), Color.rgb(41, 57, 73)));
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, -1, 1);
            colParams.setMargins(dp(5), 0, dp(5), dp(9));
            body.addView(col, colParams);

            TextView title = label("PAINEL " + (pane + 1), 12, ACCENT, true);
            title.setPadding(dp(5), dp(7), 0, dp(5));
            col.addView(title);
            TextView path = label(String.join(" / ", p.names), 11, MUTED, false);
            path.setSingleLine(true);
            path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            path.setPadding(dp(5), 0, 0, dp(8));
            col.addView(path);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            col.addView(top);
            TextView pick = action("Pasta", () -> selectRoot(pane));
            top.addView(pick, new LinearLayout.LayoutParams(0, dp(39), 1));
            TextView up = action("↑", () -> goUp(pane));
            LinearLayout.LayoutParams upParams = new LinearLayout.LayoutParams(dp(43), dp(39));
            upParams.setMargins(dp(4), 0, 0, 0);
            top.addView(up, upParams);

            LinearLayout sub = new LinearLayout(this);
            sub.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, dp(39));
            subParams.setMargins(0, dp(5), 0, dp(8));
            col.addView(sub, subParams);
            sub.addView(action("+ Pasta", () -> create(pane, true)), new LinearLayout.LayoutParams(0, dp(38), 1));
            TextView makeFile = action("+ Arquivo", () -> create(pane, false));
            LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(0, dp(38), 1);
            fileParams.setMargins(dp(4), 0, 0, 0);
            sub.addView(makeFile, fileParams);

            LinearLayout filterBar = new LinearLayout(this);
            filterBar.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-1, dp(35));
            filterParams.setMargins(0, 0, 0, dp(5));
            col.addView(filterBar, filterParams);
            TextView filterButton = action(p.filter.isEmpty() ? "⌕ Filtrar arquivos" : "⌕ " + p.filter,
                    () -> filterPanel(pane));
            filterButton.setSingleLine(true);
            filterButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
            filterBar.addView(filterButton, new LinearLayout.LayoutParams(0, -1, 1));
            if (!p.filter.isEmpty()) {
                TextView clear = action("×", () -> { p.filter = ""; render(); });
                LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(38), -1);
                clearParams.setMargins(dp(4), 0, 0, 0);
                filterBar.addView(clear, clearParams);
            }
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout list = column();
            scroll.addView(list);
            if (p.tree == null) {
                list.addView(hint("Escolha uma pasta para navegar. Nenhuma permissão geral é necessária."));
            } else if (p.busy) {
                list.addView(hint("Carregando arquivos…"));
            } else if (p.error != null) {
                list.addView(hint("Erro: " + p.error + "\nSelecione a pasta novamente se o acesso expirou."));
            } else if (p.items.isEmpty()) {
                list.addView(hint("Pasta vazia"));
            } else {
                int visible = 0;
                for (Item item : p.items) {
                    if (!p.filter.isEmpty() && !item.name.toLowerCase(Locale.ROOT).contains(p.filter)) continue;
                    visible++;
                    TextView line = label((item.isDir() ? "📁 " : "📄 ") + item.name,
                            13, TEXT, item.isDir());
                    line.setSingleLine(true);
                    line.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    line.setPadding(dp(5), dp(11), dp(3), dp(11));
                    line.setOnClickListener(v -> openItem(pane, item));
                    line.setOnLongClickListener(v -> { itemMenu(pane, item); return true; });
                    list.addView(line, new LinearLayout.LayoutParams(-1, dp(46)));
                    View separator = new View(this);
                    separator.setBackgroundColor(Color.rgb(36, 50, 65));
                    list.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
                }
                if (visible == 0) list.addView(hint("Nenhum arquivo corresponde ao filtro."));
            }
            TextView footer = label("Toque para abrir • segure para ações", 10, MUTED, false);
            footer.setPadding(dp(5), dp(8), 0, dp(4));
            col.addView(footer);
        }
    }
    private TextView hint(String text) {
        TextView h = label(text, 12, MUTED, false);
        h.setPadding(dp(8), dp(18), dp(8), dp(8));
        return h;
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    private void filterPanel(int pane) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(panels[pane].filter);
        edit.setHint("Parte do nome do arquivo");
        LinearLayout wrapper = column();
        wrapper.setPadding(dp(20), dp(4), dp(20), dp(4));
        wrapper.addView(edit);
        new AlertDialog.Builder(this).setTitle("Filtrar painel " + (pane + 1))
                .setView(wrapper).setNegativeButton("Cancelar", null)
                .setPositiveButton("Aplicar", (dialog, which) -> {
                    String input = edit.getText().toString().trim().toLowerCase(Locale.ROOT);
                    panels[pane].filter = input.length() > 80 ? input.substring(0, 80) : input;
                    render();
                }).show();
    }

    private void selectRoot(int pane) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, 401 + pane);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode < 401 || requestCode > 402 || resultCode != RESULT_OK || data == null) return;
        Uri tree = data.getData();
        if (tree == null) return;
        int pane = requestCode - 401;
        try {
            int mode = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (mode != 0) getContentResolver().takePersistableUriPermission(tree, mode);
            Panel p = panels[pane];
            p.tree = tree;
            p.ids.clear();
            p.names.clear();
            p.ids.addLast(DocumentsContract.getTreeDocumentId(tree));
            p.names.addLast("Raiz");
            getPreferences(MODE_PRIVATE).edit().putString("root_" + pane, tree.toString()).apply();
            load(pane);
        } catch (Exception e) { toast("Falha no acesso: " + message(e)); }
    }
    private Uri documentUri(Uri tree, String id) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, id);
    }
    private List<Item> queryChildren(Uri tree, String parentId) throws Exception {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        List<Item> result = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(children, PROJECTION, null, null, null)) {
            if (cursor == null) throw new IOException("O provedor não retornou arquivos");
            int idIndex = cursor.getColumnIndexOrThrow(PROJECTION[0]);
            int nameIndex = cursor.getColumnIndexOrThrow(PROJECTION[1]);
            int mimeIndex = cursor.getColumnIndexOrThrow(PROJECTION[2]);
            int sizeIndex = cursor.getColumnIndexOrThrow(PROJECTION[3]);
            while (cursor.moveToNext()) {
                String id = cursor.getString(idIndex);
                if (id == null) continue;
                String name = cursor.getString(nameIndex);
                String mime = cursor.getString(mimeIndex);
                long size = cursor.isNull(sizeIndex) ? -1L : cursor.getLong(sizeIndex);
                result.add(new Item(id, name == null ? "(sem nome)" : name,
                        mime == null ? "application/octet-stream" : mime, size));
                if (result.size() > 20000) throw new IOException("Pasta com mais de 20 mil arquivos");
            }
        }
        result.sort(Comparator.comparing(Item::isDir).reversed()
                .thenComparing(item -> item.name.toLowerCase(Locale.ROOT)));
        return result;
    }
    private void load(int pane) {
        Panel p = panels[pane];
        Uri tree = p.tree;
        String id = p.currentId();
        if (tree == null || id == null) { render(); return; }
        int ticket = ++p.requestId;
        p.busy = true;
        p.error = null;
        render();
        workers.execute(() -> {
            List<Item> found = Collections.emptyList();
            String failure = null;
            try { found = queryChildren(tree, id); }
            catch (Exception e) { failure = message(e); }
            List<Item> finalFound = found;
            String finalFailure = failure;
            mainHandler.post(() -> {
                if (closed || p.requestId != ticket) return;
                p.items = finalFound;
                p.error = finalFailure;
                p.busy = false;
                render();
            });
        });
    }
    private void goUp(int pane) {
        Panel p = panels[pane];
        if (p.ids.size() > 1) {
            p.ids.removeLast();
            p.names.removeLast();
            load(pane);
        }
    }
    private void openItem(int pane, Item item) {
        if (item.isDir()) {
            Panel p = panels[pane];
            p.ids.addLast(item.id);
            p.names.addLast(item.name);
            load(pane);
            return;
        }
        String lower = item.name.toLowerCase(Locale.ROOT);
        if (isArchive(item.name)) {
            inspectZip(pane, item);
        } else if (lower.endsWith(".dex")) {
            inspectDex(pane, item);
        } else if (lower.matches(".*\\.(txt|xml|json|java|kt|smali|md|gradle|ini|cfg|properties|html|css|js|ts|py|sh|log|yml|yaml)$")) {
            editText(pane, item);
        } else {
            new AlertDialog.Builder(this).setTitle(item.name)
                    .setItems(new String[] {"Abrir como texto UTF-8", "Visualizar em hexadecimal"},
                            (dialog, which) -> { if (which == 0) editText(pane, item); else inspectHex(pane, item); })
                    .show();
        }
    }
    private void itemMenu(int pane, Item item) {
        List<String> actions = new ArrayList<>();
        Collections.addAll(actions, "Abrir", "Visualizar HEX", "Detalhes", "Renomear",
                "Copiar para outro painel", "Excluir");
        if (!item.isDir() && isArchive(item.name)) actions.add("Extrair ZIP nesta pasta");
        if (!item.isDir() && item.name.toLowerCase(Locale.ROOT).endsWith(".apk"))
            actions.add("Inventário técnico do APK");
        if (!item.isDir() && item.name.toLowerCase(Locale.ROOT).endsWith(".dex"))
            actions.add("Inspecionar cabeçalho DEX");
        new AlertDialog.Builder(this).setTitle(item.name)
                .setItems(actions.toArray(new String[0]), (dialog, index) -> {
                    switch (actions.get(index)) {
                        case "Abrir": openItem(pane, item); break;
                        case "Visualizar HEX":
                            if (!item.isDir()) inspectHex(pane, item);
                            else toast("Pasta não é arquivo binário");
                            break;
                        case "Detalhes":
                            new AlertDialog.Builder(this).setTitle("Detalhes")
                                    .setMessage("Nome: " + item.name + "\nMIME: " + item.mime
                                            + "\nTamanho: " + item.size + " bytes\nID: " + item.id)
                                    .setPositiveButton("OK", null).show();
                            break;
                        case "Renomear": rename(pane, item); break;
                        case "Copiar para outro painel": copyToOther(pane, item); break;
                        case "Excluir": confirmDelete(pane, item); break;
                        case "Extrair ZIP nesta pasta": extractArchive(pane, item); break;
                        case "Inventário técnico do APK": inspectApk(pane, item); break;
                        case "Inspecionar cabeçalho DEX": inspectDex(pane, item); break;
                        default: break;
                    }
                }).show();
    }
    private void prompt(String title, String value, java.util.function.Consumer<String> onOk) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(value);
        edit.setSelectAllOnFocus(true);
        LinearLayout wrapper = column();
        wrapper.setPadding(dp(22), dp(6), dp(22), dp(3));
        wrapper.addView(edit);
        new AlertDialog.Builder(this).setTitle(title).setView(wrapper)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar", (d, w) -> {
                    String name = edit.getText().toString().trim();
                    if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
                        toast("Nome inválido");
                    } else onOk.accept(name);
                }).show();
    }
    private void runOperation(int pane, String success, Work task) {
        workers.execute(() -> {
            String fail = null;
            try { task.run(); } catch (Exception e) { fail = message(e); }
            String failure = fail;
            mainHandler.post(() -> {
                if (closed) return;
                toast(failure == null ? success : "Falha: " + failure);
                load(pane);
            });
        });
    }
    @FunctionalInterface private interface Work { void run() throws Exception; }
    private void create(int pane, boolean folder) {
        Panel p = panels[pane];
        if (p.tree == null) { selectRoot(pane); return; }
        String parent = p.currentId();
        Uri tree = p.tree;
        prompt(folder ? "Criar pasta" : "Criar arquivo de texto", "", name ->
                runOperation(pane, "Criado", () -> {
                    String mime = folder ? DocumentsContract.Document.MIME_TYPE_DIR : "text/plain";
                    Uri result = DocumentsContract.createDocument(getContentResolver(), documentUri(tree, parent), mime, name);
                    if (result == null) throw new IOException("O provedor recusou a criação");
                }));
    }
    private void rename(int pane, Item item) {
        Panel p = panels[pane];
        Uri tree = p.tree;
        prompt("Renomear", item.name, name -> runOperation(pane, "Renomeado", () -> {
            Uri result = DocumentsContract.renameDocument(getContentResolver(), documentUri(tree, item.id), name);
            if (result == null) throw new IOException("O provedor recusou a renomeação");
        }));
    }
    private void confirmDelete(int pane, Item item) {
        new AlertDialog.Builder(this).setTitle("Excluir " + item.name + "?")
                .setMessage("A exclusão pode ser permanente. Esta ação não pode ser desfeita pelo aplicativo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (d, which) -> {
                    Uri tree = panels[pane].tree;
                    runOperation(pane, "Excluído", () -> {
                        if (!DocumentsContract.deleteDocument(getContentResolver(), documentUri(tree, item.id))) {
                            throw new IOException("O provedor recusou a exclusão");
                        }
                    });
                }).show();
    }
    private static byte[] boundedRead(InputStream in, int cap) throws IOException {
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = source.read(chunk)) != -1) {
                if (out.size() + read > cap) throw new IOException("Arquivo excede o limite de " + cap + " bytes");
                out.write(chunk, 0, read);
            }
            return out.toByteArray();
        }
    }
    private void inspectHex(int pane, Item item) {
        readAsync(pane, item, 128 * 1024, bytes ->
                showText("HEX • " + item.name + " (até 128 KiB)", HexFormatter.format(bytes)));
    }
    private void inspectZip(int pane, Item item) {
        Uri uri = documentUri(panels[pane].tree, item.id);
        workers.execute(() -> {
            String report;
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new IOException("Falha na leitura do arquivo");
                List<String> lines = ZipIndex.inspect(in, 1500);
                report = lines.isEmpty() ? "Nenhuma entrada detectada." : String.join("\n", lines);
            } catch (Exception e) { report = "Erro: " + message(e); }
            String finalReport = report;
            mainHandler.post(() -> { if (!closed) showText("Conteúdo ZIP/APK • " + item.name, finalReport); });
        });
    }
    private void inspectApk(int pane, Item item) {
        Uri file = documentUri(panels[pane].tree, item.id);
        workers.execute(() -> {
            String result;
            try (InputStream in = getContentResolver().openInputStream(file)) {
                if (in == null) throw new IOException("Não foi possível ler o APK");
                result = ApkInspector.inspect(in);
            } catch (Exception e) { result = "Erro na inspeção: " + message(e); }
            String report = result;
            mainHandler.post(() -> { if (!closed) showText("APK • " + item.name, report); });
        });
    }

    private void inspectDex(int pane, Item item) {
        Uri file = documentUri(panels[pane].tree, item.id);
        workers.execute(() -> {
            String result;
            try (InputStream in = getContentResolver().openInputStream(file)) {
                if (in == null) throw new IOException("Não foi possível ler o DEX");
                result = DexInspector.inspect(in).summary();
            } catch (Exception e) { result = "Erro na análise DEX: " + message(e); }
            String report = result;
            mainHandler.post(() -> { if (!closed) showText("DEX • " + item.name, report); });
        });
    }

    /** SAF archive extraction into a unique new folder. No existing files are overwritten. */
    private void extractArchive(int pane, Item item) {
        Panel panel = panels[pane];
        if (panel.tree == null || panel.currentId() == null) { toast("Pasta indisponível"); return; }
        Uri tree = panel.tree;
        String parentId = panel.currentId();
        Uri archive = documentUri(tree, item.id);
        String baseName = item.name.replaceFirst("(?i)\\.(zip|apk|jar|apks)$", "");
        if (baseName.isEmpty()) baseName = "arquivo";
        String newFolder = (baseName.length() > 72 ? baseName.substring(0, 72) : baseName)
                + "_extraido_" + System.currentTimeMillis();
        new AlertDialog.Builder(this)
                .setTitle("Extrair " + item.name + "?")
                .setMessage("Será criada uma pasta nova no painel atual. "
                        + "Limites: 3.000 entradas, 128 MiB por arquivo e 256 MiB no total. "
                        + "Uma falha tentará remover a pasta parcial.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Extrair", (dialog, which) -> runOperation(pane,
                        "Extração concluída na pasta " + newFolder, () -> {
                    Uri root = DocumentsContract.createDocument(getContentResolver(),
                            documentUri(tree, parentId),
                            DocumentsContract.Document.MIME_TYPE_DIR, newFolder);
                    if (root == null) throw new IOException("Não foi possível criar pasta de extração");
                    try (InputStream source = getContentResolver().openInputStream(archive)) {
                        if (source == null) throw new IOException("Não foi possível ler o ZIP");
                        ZipExtractor.Result stats = ZipExtractor.extract(source,
                                new SafArchiveDestination(root));
                        if (stats.files == 0 && stats.directories == 0)
                            throw new IOException("ZIP vazio ou sem entradas válidas");
                    } catch (Exception e) {
                        try { DocumentsContract.deleteDocument(getContentResolver(), root); }
                        catch (Exception cleanup) { e.addSuppressed(cleanup); }
                        throw e;
                    }
                })).show();
    }

    private final class SafArchiveDestination implements ZipExtractor.Destination {
        private final Map<String, Uri> directories = new HashMap<>();
        private final Set<String> files = new HashSet<>();

        SafArchiveDestination(Uri root) { directories.put("", root); }

        private Uri ensureParents(List<String> components, int count) throws IOException {
            Uri current = directories.get("");
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < count; i++) {
                if (prefix.length() > 0) prefix.append('/');
                prefix.append(components.get(i));
                String key = prefix.toString();
                if (files.contains(key)) throw new IOException("Arquivo bloqueia uma pasta: " + key);
                Uri known = directories.get(key);
                if (known == null) {
                    known = DocumentsContract.createDocument(getContentResolver(), current,
                            DocumentsContract.Document.MIME_TYPE_DIR, components.get(i));
                    if (known == null) throw new IOException("Não foi possível criar pasta: " + key);
                    directories.put(key, known);
                }
                current = known;
            }
            return current;
        }

        @Override public void directory(List<String> components) throws IOException {
            ensureParents(components, components.size());
        }

        @Override public OutputStream file(List<String> components) throws IOException {
            String key = String.join("/", components);
            if (directories.containsKey(key) || !files.add(key))
                throw new IOException("Caminho de arquivo duplicado: " + key);
            Uri parent = ensureParents(components, components.size() - 1);
            Uri created = DocumentsContract.createDocument(getContentResolver(), parent,
                    "application/octet-stream", components.get(components.size() - 1));
            if (created == null) throw new IOException("Não foi possível criar: " + key);
            OutputStream out = getContentResolver().openOutputStream(created, "w");
            if (out == null) throw new IOException("Não foi possível abrir: " + key);
            return out;
        }
    }

    @FunctionalInterface private interface DataHandler { void accept(byte[] data) throws Exception; }
    private void readAsync(int pane, Item item, int cap, DataHandler callback) {
        Uri uri = documentUri(panels[pane].tree, item.id);
        workers.execute(() -> {
            byte[] content = null;
            String fail = null;
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new IOException("Não foi possível abrir o arquivo");
                content = boundedRead(in, cap);
            } catch (Exception e) { fail = message(e); }
            byte[] result = content;
            String error = fail;
            mainHandler.post(() -> {
                if (closed) return;
                if (error != null) { toast("Erro: " + error); return; }
                try { callback.accept(result); }
                catch (Exception e) { toast("Erro: " + message(e)); }
            });
        });
    }
    private void showText(String title, String text) {
        ScrollView scroll = new ScrollView(this);
        TextView content = label(text, 12, TEXT, false);
        content.setTypeface(Typeface.MONOSPACE);
        content.setTextIsSelectable(true);
        content.setPadding(dp(13), dp(8), dp(13), dp(8));
        scroll.addView(content);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll)
                .setPositiveButton("Fechar", null).show();
    }
    private void editText(int pane, Item item) {
        readAsync(pane, item, 1024 * 1024, bytes -> {
            boolean bom = bytes.length >= 3 && (bytes[0] & 0xff) == 0xEF
                    && (bytes[1] & 0xff) == 0xBB && (bytes[2] & 0xff) == 0xBF;
            int start = bom ? 3 : 0;
            java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            String value;
            try { value = decoder.decode(ByteBuffer.wrap(bytes, start, bytes.length - start)).toString(); }
            catch (CharacterCodingException e) {
                showText("Arquivo não é UTF-8", "Para evitar perda de dados, abra em HEX.");
                return;
            }
            EditText field = new EditText(this);
            field.setText(value);
            field.setTextColor(TEXT);
            field.setTextSize(13);
            field.setTypeface(Typeface.MONOSPACE);
            field.setGravity(Gravity.TOP | Gravity.START);
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            field.setMinLines(12);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(field);
            new AlertDialog.Builder(this).setTitle("Editar • " + item.name)
                    .setView(scroll)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Salvar cópia", (d, which) -> {
                        String newText = field.getText().toString();
                        Uri tree = panels[pane].tree;
                        String parentId = panels[pane].currentId();
                        if (tree == null || parentId == null) { toast("Pasta de origem indisponível"); return; }
                        runOperation(pane, "Cópia salva com sufixo .editado", () -> {
                            Uri newFile = DocumentsContract.createDocument(getContentResolver(),
                                    documentUri(tree, parentId), "text/plain", item.name + ".editado");
                            if (newFile == null) throw new IOException("Não foi possível criar a cópia");
                            try {
                                OutputStream output = getContentResolver().openOutputStream(newFile, "w");
                                if (output == null) throw new IOException("Falha ao abrir a cópia");
                                try (OutputStream out = output) {
                                    if (bom) out.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                                    out.write(newText.getBytes(StandardCharsets.UTF_8));
                                }
                            } catch (Exception e) {
                                try { DocumentsContract.deleteDocument(getContentResolver(), newFile); }
                                catch (Exception ignored) { }
                                throw e;
                            }
                        });
                    }).show();
        });
    }
    private void copyToOther(int fromPane, Item source) {
        int other = 1 - fromPane;
        Panel from = panels[fromPane];
        Panel to = panels[other];
        if (from.tree == null || to.tree == null) { toast("Escolha pastas nos dois painéis"); return; }
        if (from.tree.equals(to.tree) && to.currentId().equals(source.id)) {
            toast("Não é possível copiar um arquivo para ele mesmo");
            return;
        }
        if (source.isDir() && from.tree.equals(to.tree) && to.ids.contains(source.id)) {
            toast("Não é possível copiar uma pasta para dentro dela mesma");
            return;
        }
        Uri sourceTree = from.tree, targetTree = to.tree;
        String destination = to.currentId();
        new AlertDialog.Builder(this).setTitle("Copiar " + source.name)
                .setMessage("Destino: painel " + (other + 1) + ". Cria uma cópia, sem remover a origem.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Copiar", (d, which) -> runOperation(other, "Cópia criada", () -> {
                    copyRecursive(sourceTree, source, targetTree, documentUri(targetTree, destination),
                            0, new AtomicInteger());
                })).show();
    }
    private void copyRecursive(Uri sourceTree, Item source, Uri targetTree, Uri targetParent,
                               int depth, AtomicInteger count) throws Exception {
        if (depth > 16 || count.incrementAndGet() > 5000) throw new IOException("Limite de segurança da cópia atingido");
        Uri created = DocumentsContract.createDocument(getContentResolver(), targetParent,
                source.isDir() ? DocumentsContract.Document.MIME_TYPE_DIR : source.mime, source.name);
        if (created == null) throw new IOException("Falha ao criar " + source.name);
        try {
            if (source.isDir()) {
                String createdId = DocumentsContract.getDocumentId(created);
                Uri destinationUri = documentUri(targetTree, createdId);
                for (Item child : queryChildren(sourceTree, source.id)) {
                    copyRecursive(sourceTree, child, targetTree, destinationUri, depth + 1, count);
                }
            } else {
                InputStream input = getContentResolver().openInputStream(documentUri(sourceTree, source.id));
                OutputStream output = getContentResolver().openOutputStream(created, "w");
                if (input == null || output == null) {
                    if (input != null) input.close();
                    if (output != null) output.close();
                    throw new IOException("Falha ao abrir fluxo de cópia");
                }
                try (InputStream in = input; OutputStream out = output) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                }
            }
        } catch (Exception e) {
            try { DocumentsContract.deleteDocument(getContentResolver(), created); }
            catch (Exception ignored) { }
            throw e;
        }
    }
    private static String message(Throwable e) {
        String s = e.getMessage();
        return s == null || s.isEmpty() ? e.getClass().getSimpleName() : s;
    }
    @Override public void onBackPressed() {
        if (panels[0].ids.size() > 1) { goUp(0); return; }
        super.onBackPressed();
    }
    @Override protected void onDestroy() {
        closed = true;
        workers.shutdownNow();
        super.onDestroy();
    }
}
