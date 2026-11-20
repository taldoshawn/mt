package com.mc1h.openfilelab;

public final class HexFormatter {
    private HexFormatter() {}

    public static String format(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 4 + 100);
        for (int i = 0; i < bytes.length; i += 16) {
            appendHex(output, i, 8);
            output.append("  ");
            for (int j = 0; j < 16; j++) {
                if (i + j < bytes.length) {
                    appendHex(output, bytes[i + j] & 0xff, 2);
                } else {
                    output.append("  ");
                }
                output.append(j == 7 ? "  " : " ");
            }
            output.append(" |");
            for (int j = 0; j < 16 && i + j < bytes.length; j++) {
                int c = bytes[i + j] & 0xff;
                output.append(c >= 32 && c <= 126 ? (char) c : '.');
            }
            output.append('|').append('\n');
        }
        return output.toString();
    }

    private static void appendHex(StringBuilder out, int n, int digits) {
        final char[] alphabet = "0123456789ABCDEF".toCharArray();
        for (int shift = (digits - 1) * 4; shift >= 0; shift -= 4) {
            out.append(alphabet[(n >>> shift) & 0x0f]);
        }
    }
}
