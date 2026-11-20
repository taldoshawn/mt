package com.mc1h.openfilelab;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Independent, bounded ZIP extractor; destination is implemented through Android SAF. */
public final class ZipExtractor {
    private ZipExtractor() { }
    public static final int MAX_ENTRIES = 3000;
    public static final long MAX_FILE_BYTES = 128L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;

    public interface Destination {
        void directory(List<String> components) throws IOException;
        OutputStream file(List<String> components) throws IOException;
    }

    public static final class Result {
        public final int files, directories;
        public final long extractedBytes;
        private Result(int files, int directories, long extractedBytes) {
            this.files = files;
            this.directories = directories;
            this.extractedBytes = extractedBytes;
        }
    }

    public static Result extract(InputStream source, Destination destination) throws IOException {
        if (source == null || destination == null) throw new IllegalArgumentException("Parâmetro nulo");
        int files = 0, directories = 0, entries = 0;
        long total = 0;
        Set<String> seenFiles = new HashSet<>();
        Set<String> seenDirs = new HashSet<>();
        byte[] block = new byte[32 * 1024];
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("ZIP excede " + MAX_ENTRIES + " entradas");
                List<String> path = validatePath(entry.getName());
                String key = String.join("/", path);
                if (entry.isDirectory()) {
                    if (seenFiles.contains(key)) throw new IOException("Pasta conflita com arquivo: " + key);
                    if (seenDirs.add(key)) { destination.directory(path); directories++; }
                    // A malicious ZIP may attach a huge data stream to a directory entry.
                    if (zip.read(block) != -1) throw new IOException("Pasta ZIP contém dados inesperados: " + key);
                } else {
                    if (seenDirs.contains(key) || !seenFiles.add(key)) {
                        throw new IOException("Arquivo duplicado ou conflitante: " + key);
                    }
                    if (entry.getSize() > MAX_FILE_BYTES) throw new IOException("Arquivo excede o limite: " + key);
                    long fileBytes = 0;
                    try (OutputStream output = destination.file(path)) {
                        if (output == null) throw new IOException("Destino não abriu arquivo: " + key);
                        int n;
                        while ((n = zip.read(block)) != -1) {
                            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Extração interrompida");
                            fileBytes += n;
                            total += n;
                            if (fileBytes > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) {
                                throw new IOException("Limite de extração atingido: " + key);
                            }
                            output.write(block, 0, n);
                        }
                    }
                    files++;
                }
                zip.closeEntry();
            }
        }
        return new Result(files, directories, total);
    }

    public static List<String> validatePath(String name) throws IOException {
        if (name == null || name.isEmpty() || name.charAt(0) == '/' || name.indexOf('\\') >= 0) {
            throw new IOException("Caminho ZIP absoluto ou inválido");
        }
        // Directory names conventionally end in '/', but internal empty segments are rejected.
        String normalized = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        if (normalized.isEmpty()) throw new IOException("Pasta raiz ZIP inválida");
        String[] parts = normalized.split("/", -1);
        if (parts.length > 16) throw new IOException("ZIP excede a profundidade máxima");
        List<String> clean = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.length() > 180 ||
                    part.indexOf(':') >= 0 || part.indexOf('\u0000') >= 0) {
                throw new IOException("Segmento ZIP inválido: " + part);
            }
            for (int i = 0; i < part.length(); i++) {
                if (Character.isISOControl(part.charAt(i))) {
                    throw new IOException("Caractere de controle em nome ZIP");
                }
            }
            clean.add(part);
        }
        return clean;
    }
}
