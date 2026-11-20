package com.mc1h.openfilelab;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** APK inventory from real ZIP entries, without interpreting proprietary binary AXML. */
public final class ApkInspector {
    private ApkInspector() { }

    public static String inspect(InputStream source) throws IOException {
        int entries = 0, assets = 0, resources = 0, nativeLibraries = 0;
        boolean manifest = false, resourceTable = false;
        List<String> dexReports = new ArrayList<>();
        Map<String, Integer> byAbi = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 20000) throw new IOException("Arquivo APK excede 20 mil entradas");
                String name = entry.getName();
                if (name == null || entry.isDirectory()) { zip.closeEntry(); continue; }
                if (name.equals("AndroidManifest.xml")) manifest = true;
                if (name.equals("resources.arsc")) resourceTable = true;
                if (name.startsWith("assets/")) assets++;
                if (name.startsWith("res/")) resources++;
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    String[] parts = name.split("/");
                    if (parts.length == 3) {
                        nativeLibraries++;
                        byAbi.merge(parts[1], 1, Integer::sum);
                    }
                }
                if (name.matches("classes([2-9][0-9]*)?\\.dex") && dexReports.size() < 30) {
                    try {
                        DexInspector.Report dex = DexInspector.inspect(zip);
                        dexReports.add(name + ": " + dex.classes + " classes, " + dex.methods
                                + " métodos; integridade "
                                + (dex.sha1Matches && dex.adler32Matches ? "OK" : "DIVERGENTE"));
                    } catch (IOException e) { dexReports.add(name + ": " + e.getMessage()); }
                }
                zip.closeEntry();
                if (Thread.currentThread().isInterrupted()) throw new IOException("Inspeção interrompida");
            }
        }
        StringBuilder result = new StringBuilder("INVENTÁRIO APK\n\n");
        result.append("Entradas: ").append(entries).append('\n');
        result.append("AndroidManifest.xml: ").append(manifest ? "presente" : "ausente").append('\n');
        result.append("resources.arsc: ").append(resourceTable ? "presente" : "ausente").append('\n');
        result.append("Recursos res/: ").append(resources).append('\n');
        result.append("Assets: ").append(assets).append('\n');
        result.append("Bibliotecas nativas: ").append(nativeLibraries).append('\n');
        for (Map.Entry<String, Integer> abi : byAbi.entrySet()) {
            result.append("• ").append(abi.getKey()).append(": ").append(abi.getValue()).append(" .so\n");
        }
        result.append("\nDEX:\n");
        if (dexReports.isEmpty()) result.append("Nenhum classes*.dex encontrado\n");
        else for (String line : dexReports) result.append("• ").append(line).append('\n');
        result.append("\nObservação: o pacote, a versão e as permissões exigem decodificar o Manifest binário.");
        return result.toString();
    }
}
