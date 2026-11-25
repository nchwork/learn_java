package com.xhunting.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Kiểm tra nhận diện định dạng bằng nội dung, kể cả khi đuôi file nói dối. */
class ArchiveDetectorTest {

    @TempDir
    Path tmp;

    @Test
    void detectsZipEvenWithWrongExtension() throws IOException {
        Path file = tmp.resolve("really-a-zip.bin");
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("a.txt"));
            zip.write("hi".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertEquals(".zip", ArchiveDetector.detectArchiveExtByContent(file));
    }

    @Test
    void detectsGzip() throws IOException {
        Path file = tmp.resolve("x.tar.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write("data".getBytes(StandardCharsets.UTF_8));
        }
        // Giống bản Go: .tar.gz vẫn trả về ".gz"
        assertEquals(".gz", ArchiveDetector.detectArchiveExtByContent(file));
    }

    @Test
    void detectsRarAndSevenZipMagic() throws IOException {
        Path rar = tmp.resolve("a.dat");
        Files.write(rar, new byte[] {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00});
        assertEquals(".rar", ArchiveDetector.detectArchiveExtByContent(rar));

        Path sevenZip = tmp.resolve("b.dat");
        Files.write(sevenZip, new byte[] {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C});
        assertEquals(".7z", ArchiveDetector.detectArchiveExtByContent(sevenZip));
    }

    @Test
    void detectsTarByUstarAtOffset257() throws IOException {
        byte[] header = new byte[512];
        byte[] ustar = "ustar".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(ustar, 0, header, 257, ustar.length);
        Path file = tmp.resolve("noext");
        Files.write(file, header);
        assertEquals(".tar", ArchiveDetector.detectArchiveExtByContent(file));
    }

    @Test
    void fallsBackToLowercaseExtension() throws IOException {
        Path file = tmp.resolve("notes.TXT");
        Files.writeString(file, "plain text");
        assertEquals(".txt", ArchiveDetector.detectArchiveExtByContent(file));
    }

    @Test
    void missingFileFallsBackToExtension() {
        assertEquals(".zip", ArchiveDetector.detectArchiveExtByContent(tmp.resolve("missing.zip")));
    }
}
