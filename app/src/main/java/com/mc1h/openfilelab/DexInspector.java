package com.mc1h.openfilelab;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.Adler32;

/** Streams a standard DEX file, checking its header, internal bounds and integrity digests. */
public final class DexInspector {
    private static final int HEADER_SIZE = 112;
    private DexInspector() { }

    public static final class Report {
        public final String version;
        public final long declaredSize, actualSize;
        public final long strings, types, methods, fields, classes;
        public final boolean sha1Matches, adler32Matches;

        private Report(String version, long declaredSize, long actualSize,
                       long strings, long types, long methods, long fields, long classes,
                       boolean sha1Matches, boolean adler32Matches) {
            this.version = version;
            this.declaredSize = declaredSize;
            this.actualSize = actualSize;
            this.strings = strings;
            this.types = types;
            this.methods = methods;
            this.fields = fields;
            this.classes = classes;
            this.sha1Matches = sha1Matches;
            this.adler32Matches = adler32Matches;
        }

        public String summary() {
            return "DEX " + version + "\n" +
                    "Tamanho: " + actualSize + " bytes\n" +
                    "Classes: " + classes + "\n" +
                    "Métodos referenciados: " + methods + "\n" +
                    "Campos: " + fields + "\n" +
                    "Strings: " + strings + "\n" +
                    "Tipos: " + types + "\n" +
                    "SHA-1 do conteúdo: " + (sha1Matches ? "OK" : "DIVERGENTE") + "\n" +
                    "Adler-32: " + (adler32Matches ? "OK" : "DIVERGENTE");
        }
    }

    /** Does not close the supplied stream (allows inspection of a ZIP entry). */
    public static Report inspect(InputStream in) throws IOException {
        if (in == null) throw new IllegalArgumentException("Fluxo nulo");
        byte[] header = new byte[HEADER_SIZE];
        int pos = 0;
        while (pos < header.length) {
            int n = in.read(header, pos, header.length - pos);
            if (n < 0) throw new EOFException("Cabeçalho DEX incompleto");
            if (n == 0) { int one = in.read(); if (one < 0) throw new EOFException("Cabeçalho DEX incompleto"); header[pos++] = (byte) one; }
            else pos += n;
        }
        if (header[0] != 'd' || header[1] != 'e' || header[2] != 'x' || header[3] != '\n'
                || header[7] != 0 || header[4] < '0' || header[4] > '9'
                || header[5] < '0' || header[5] > '9' || header[6] < '0' || header[6] > '9') {
            throw new IOException("Magic DEX inválido ou formato não suportado");
        }
        ByteBuffer data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        long fileSize = u32(data, 32);
        long headerSize = u32(data, 36);
        if (headerSize != HEADER_SIZE) throw new IOException("Tamanho de cabeçalho DEX não suportado: " + headerSize);
        if (u32(data, 40) != 0x12345678L) throw new IOException("Marcador de endianness DEX inválido");
        if (fileSize < HEADER_SIZE) throw new IOException("Tamanho declarado DEX inválido");
        section(data, fileSize, 56, 60, 4, "string_ids");
        section(data, fileSize, 64, 68, 4, "type_ids");
        section(data, fileSize, 72, 76, 12, "proto_ids");
        section(data, fileSize, 80, 84, 8, "field_ids");
        section(data, fileSize, 88, 92, 8, "method_ids");
        section(data, fileSize, 96, 100, 32, "class_defs");
        section(data, fileSize, 104, 108, 1, "data");

        Adler32 adler = new Adler32();
        adler.update(header, 12, HEADER_SIZE - 12);
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException e) { throw new IOException("SHA-1 indisponível", e); }
        digest.update(header, 32, HEADER_SIZE - 32);
        long actualSize = HEADER_SIZE;
        byte[] buffer = new byte[16 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (n == 0) continue;
            actualSize += n;
            if (actualSize > fileSize) throw new IOException("DEX excede o tamanho declarado");
            adler.update(buffer, 0, n);
            digest.update(buffer, 0, n);
            if (Thread.currentThread().isInterrupted()) throw new IOException("Inspeção interrompida");
        }
        if (actualSize != fileSize) throw new IOException("DEX truncado: " + actualSize + "/" + fileSize);
        byte[] calculatedSha = digest.digest();
        byte[] expectedSha = Arrays.copyOfRange(header, 12, 32);
        boolean shaMatch = Arrays.equals(calculatedSha, expectedSha);
        boolean adlerMatch = adler.getValue() == u32(data, 8);
        return new Report(new String(header, 4, 3, java.nio.charset.StandardCharsets.US_ASCII),
                fileSize, actualSize, u32(data, 56), u32(data, 64), u32(data, 88),
                u32(data, 80), u32(data, 96), shaMatch, adlerMatch);
    }

    private static long u32(ByteBuffer buffer, int offset) {
        return Integer.toUnsignedLong(buffer.getInt(offset));
    }

    private static void section(ByteBuffer header, long fileSize, int countOffset,
                                int offsetOffset, long entrySize, String name) throws IOException {
        long count = u32(header, countOffset);
        long offset = u32(header, offsetOffset);
        if (count == 0) {
            if (offset != 0) throw new IOException("Offset inesperado em seção vazia " + name);
            return;
        }
        if (offset < HEADER_SIZE || offset > fileSize || count > (fileSize - offset) / entrySize) {
            throw new IOException("Seção DEX fora dos limites: " + name);
        }
    }
}
