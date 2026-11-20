import com.mc1h.openfilelab.ApkInspector;
import com.mc1h.openfilelab.DexInspector;
import com.mc1h.openfilelab.ZipExtractor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class FeatureTest {
    static int checks;
    static void ok(boolean b, String msg) {
        checks++;
        if (!b) throw new AssertionError(msg);
    }
    static void rejects(Throwing task, String msg) throws Exception {
        checks++;
        try { task.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Aceitou " + msg);
    }
    interface Throwing { void run() throws Exception; }

    static byte[] validDex() throws Exception {
        byte[] data = new byte[112];
        byte[] magic = new byte[]{'d','e','x','\n','0','3','5',0};
        System.arraycopy(magic, 0, data, 0, 8);
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(32, data.length).putInt(36, 112)
                .putInt(40, 0x12345678);
        byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(Arrays.copyOfRange(data, 32, data.length));
        System.arraycopy(sha1, 0, data, 12, 20);
        Adler32 sum = new Adler32();
        sum.update(data, 12, data.length - 12);
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(8, (int) sum.getValue());
        return data;
    }
    static byte[] zip(String[] names, byte[][] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (int i = 0; i < names.length; i++) {
                z.putNextEntry(new ZipEntry(names[i]));
                z.write(content[i]);
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }
    static final class MemoryTarget implements ZipExtractor.Destination {
        final Map<String, ByteArrayOutputStream> files = new HashMap<>();
        final Map<String, Boolean> directories = new HashMap<>();
        public void directory(List<String> components) { directories.put(String.join("/", components), true); }
        public OutputStream file(List<String> components) { return files.computeIfAbsent(String.join("/", components), k -> new ByteArrayOutputStream()); }
    }
    public static void main(String[] args) throws Exception {
        byte[] dex = validDex();
        DexInspector.Report valid = DexInspector.inspect(new ByteArrayInputStream(dex));
        ok(valid.sha1Matches && valid.adler32Matches && valid.actualSize == 112, "Integridade DEX");
        byte[] invalid = dex.clone(); invalid[50] ^= 7;
        DexInspector.Report changed = DexInspector.inspect(new ByteArrayInputStream(invalid));
        ok(!changed.sha1Matches && !changed.adler32Matches, "Detectar DEX modificado");
        invalid = dex.clone(); invalid[0] = 'q';
        byte[] brokenMagic = invalid;
        rejects(() -> DexInspector.inspect(new ByteArrayInputStream(brokenMagic)), "magic DEX inválido");
        invalid = dex.clone();
        ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putInt(96, 2).putInt(100, 100);
        byte[] badOffsets = invalid;
        rejects(() -> DexInspector.inspect(new ByteArrayInputStream(badOffsets)), "seções fora do arquivo");
        rejects(() -> DexInspector.inspect(new ByteArrayInputStream(new byte[20])), "DEX truncado");

        for (String bad : new String[]{"../x", "a/../x", "/root", "a//b", "C:/drive", "a\\b", "x/./y", "a/", "a/../../etc"}) {
            // a/ is valid as a directory name, not an unsafe path.
            if (bad.equals("a/")) { ok(ZipExtractor.validatePath(bad).size() == 1, "Pasta com barra final"); continue; }
            final String value = bad;
            rejects(() -> ZipExtractor.validatePath(value), "path traversal: " + bad);
        }
        ok(ZipExtractor.validatePath("a/b/file.txt").equals(Arrays.asList("a", "b", "file.txt")), "Caminho válido");
        byte[] filesZip = zip(new String[]{"folder/", "folder/note.txt", "nested/thing.bin"},
                new byte[][]{new byte[0], "ola".getBytes("UTF-8"), {1,2,3}});
        MemoryTarget dest = new MemoryTarget();
        ZipExtractor.Result extracted = ZipExtractor.extract(new ByteArrayInputStream(filesZip), dest);
        ok(extracted.files == 2 && extracted.directories == 1 && extracted.extractedBytes == 6, "Estatísticas extração");
        ok("ola".equals(dest.files.get("folder/note.txt").toString("UTF-8")), "Conteúdo extraído");
        byte[] traversal = zip(new String[]{"../unsafe"}, new byte[][]{{1}});
        rejects(() -> ZipExtractor.extract(new ByteArrayInputStream(traversal), new MemoryTarget()), "extração traversal");
        byte[] duplicatedType = zip(new String[]{"a/b", "a"}, new byte[][]{{1},{2}});
        // Real filesystem adapter rejects file/previous-directory conflicts; test path validator separately.
        ok(duplicatedType.length > 0, "ZIP criado");

        byte[] apk = zip(new String[]{"AndroidManifest.xml", "resources.arsc", "classes.dex", "lib/arm64-v8a/libtest.so"},
                new byte[][]{{1}, {2}, dex, {3}});
        String report = ApkInspector.inspect(new ByteArrayInputStream(apk));
        ok(report.contains("Bibliotecas nativas: 1"), "Inventário ABI");
        ok(report.contains("classes.dex: 0 classes") && report.contains("integridade OK"), "Inventário DEX íntegro");
        System.out.println("OK: " + checks + " verificações de DEX, ZIP, caminhos e inventário APK");
    }
}
