package com.xhunting.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test tích hợp gọi binary 7z thật. Nếu máy không có 7z thì test bị BỎ QUA (assumeTrue),
 * giống {@code t.Skip("7z not installed")} trong Go.
 *
 * Mỗi test tạo cấu trúc: work/src/logs_host01/a.txt, nén thành archive, rồi giải nén vào out/.
 * Kết quả mong đợi sau normalize: out/&lt;tên archive&gt;/a.txt
 */
class DefaultExtractorServiceIntegrationTest {

    private static final String SEVEN_ZIP = System.getenv().getOrDefault("SEVEN_ZIP", "7z");

    @TempDir
    Path tmp;

    private Path src;
    private Path out;
    private final ExtractorService service = new DefaultExtractorService(ExtractorConfig.defaults());
    private final CommandArchive commands = CommandArchive.allUsing(SEVEN_ZIP);

    @BeforeAll
    static void requireSevenZip() {
        boolean available;
        try {
            available = new ProcessBuilder(SEVEN_ZIP).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0;
        } catch (IOException | InterruptedException e) { // multi-catch: Go không có cú pháp tương đương
            available = false;
        }
        assumeTrue(available, "7z not installed");
    }

    @BeforeEach
    void setUp() throws IOException {
        src = tmp.resolve("src");
        out = tmp.resolve("out");
        Files.createDirectories(src.resolve("logs_host01"));
        Files.createDirectories(out);
        Files.writeString(src.resolve("logs_host01/a.txt"), "hello xhunting");
    }

    @Test
    void extractsZipWithoutPassword() throws Exception {
        Path archive = pack("logs.zip", "-tzip");
        service.extract(commands, archive, out, List.of());
        assertEquals("hello xhunting", Files.readString(out.resolve("logs/a.txt")));
    }

    @Test
    void extractsSevenZipWithSecondPassword() throws Exception {
        Path archive = pack("logs.7z", "-t7z", "-pS3cret", "-mhe=off");
        service.extract(commands, archive, out, List.of("wrong", "S3cret"));
        assertEquals("hello xhunting", Files.readString(out.resolve("logs/a.txt")));
    }

    @Test
    void zipWithWrongPasswordsThrowsPasswordRequired() throws Exception {
        Path archive = pack("logs.zip", "-tzip", "-pS3cret");
        var e = assertThrows(PasswordRequiredException.class,
                () -> service.extract(commands, archive, out, List.of("nope")));
        assertEquals("Password required", e.getMessage());
    }

    @Test
    void extractsTar() throws Exception {
        Path archive = pack("logs.tar", "-ttar");
        service.extract(commands, archive, out, null);
        assertEquals("hello xhunting", Files.readString(out.resolve("logs/a.txt")));
    }

    @Test
    void extractsTarGzPlacedInsideDest() throws Exception {
        // Bản Go giải nén lớp tar vào cạnh file .tar.gz, nên file phải nằm trong dest (như xhunting thực tế).
        Path tar = pack("logs.tar", "-ttar");
        Path tarGz = out.resolve("logs.tar.gz");
        run(SEVEN_ZIP, "a", "-tgzip", tarGz.toString(), tar.toString());

        service.extract(commands, tarGz, out, List.of());
        assertEquals("hello xhunting", Files.readString(out.resolve("logs/a.txt")));
    }

    @Test
    void unsupportedFormat() throws Exception {
        Path file = tmp.resolve("notes.txt");
        Files.writeString(file, "plain");
        var e = assertThrows(ExtractException.class, () -> service.extract(commands, file, out, List.of()));
        assertEquals("unsupported format", e.getMessage());
    }

    /** Nén thư mục src/logs_host01 thành tmp/&lt;name&gt; bằng 7z. */
    private Path pack(String name, String... opts) throws Exception {
        Path archive = tmp.resolve(name);
        var cmd = new java.util.ArrayList<>(List.of(SEVEN_ZIP, "a"));
        cmd.addAll(List.of(opts));
        cmd.add(archive.toString());
        cmd.add(src.resolve("logs_host01").toString());
        run(cmd.toArray(String[]::new));
        return archive;
    }

    private static void run(String... cmd) throws Exception {
        int code = new ProcessBuilder(cmd).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
        assertEquals(0, code, "command failed: " + String.join(" ", cmd));
    }
}
