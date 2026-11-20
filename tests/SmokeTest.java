import com.mc1h.openfilelab.HexFormatter;
import com.mc1h.openfilelab.ZipIndex;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SmokeTest {
    public static void main(String[] args) throws Exception {
        String hex = HexFormatter.format(new byte[] {0x41, 0, (byte) 0xff, 0x5a});
        if (!hex.startsWith("00000000  41 00 FF 5A") || !hex.contains("|A..Z|")) {
            throw new AssertionError("HEX incorreto: " + hex);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("hello.txt"));
            zip.write("oi".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        List<String> entries = ZipIndex.inspect(new ByteArrayInputStream(buffer.toByteArray()), 10);
        if (entries.size() != 1 || !entries.get(0).contains("hello.txt")) {
            throw new AssertionError("ZIP incorreto: " + entries);
        }
        try {
            ZipIndex.inspect(new ByteArrayInputStream(new byte[0]), 0);
            throw new AssertionError("Aceitou limite inválido");
        } catch (IllegalArgumentException expected) { }
        System.out.println("OK: HEX, ZIP e limites básicos");
    }
}
