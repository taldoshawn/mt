package com.mc1h.openfilelab;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Read-only metadata inspection, not extraction or APK rebuilding. */
public final class ZipIndex {
    private ZipIndex() {}

    public static List<String> inspect(InputStream stream, int maxEntries) throws IOException {
        if (maxEntries < 1 || maxEntries > 10000) {
            throw new IllegalArgumentException("Limite de entradas inválido");
        }
        ArrayList<String> result = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(stream)) {
            ZipEntry entry;
            while (result.size() < maxEntries && (entry = zip.getNextEntry()) != null) {
                String path = entry.getName();
                if (path == null) path = "(sem nome)";
                result.add((entry.isDirectory() ? "📁 " : "📄 ") + path
                        + (entry.getSize() < 0 ? "" : "  (" + entry.getSize() + " bytes)"));
                zip.closeEntry();
            }
            if (result.size() == maxEntries) result.add("… Listagem limitada a " + maxEntries + " entradas.");
        }
        return Collections.unmodifiableList(result);
    }
}
