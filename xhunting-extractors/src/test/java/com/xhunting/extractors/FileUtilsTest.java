package com.xhunting.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Test ~ file *_test.go. {@code @ParameterizedTest} + {@code @CsvSource} ~ table-driven test của Go:
 * <pre>{@code
 * tests := []struct{ in, want string }{ {"a/logs.zip", "logs"}, ... }
 * for _, tt := range tests { t.Run(tt.in, func(t *testing.T) {...}) }
 * }</pre>
 * {@code @TempDir} ~ {@code t.TempDir()}.
 */
class FileUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "a/b/logs.zip,    logs",
            "a/b/logs.tar.gz, logs.tar",
            "noext,           noext",
    })
    void fileNameWithoutExt(String in, String want) {
        assertEquals(want, FileUtils.fileNameWithoutExt(Path.of(in)));
    }

    @ParameterizedTest
    @CsvSource({
            "a/b/logs.zip,    a/b/logs",
            "a/b/logs.tar.gz, a/b/logs",
            "a/b/logs.gz,     a/b/logs",
    })
    void removePathExt(String in, String want) {
        assertEquals(Path.of(want), FileUtils.removePathExt(Path.of(in)));
    }

    @Test
    void normalizeExtractedFolderLiftsFirstSubDir(@TempDir Path tmp) throws IOException {
        // tmp/logs/logs_host01/a.txt  ->  tmp/logs/a.txt
        Path dest = tmp.resolve("logs");
        Files.createDirectories(dest.resolve("logs_host01"));
        Files.writeString(dest.resolve("logs_host01/a.txt"), "hello");

        FileUtils.normalizeExtractedFolder(dest, tmp);

        assertEquals("hello", Files.readString(dest.resolve("a.txt")));
        assertFalse(Files.exists(dest.resolve("logs_host01")));
        assertFalse(Files.exists(tmp.resolve("__tmp__")));
    }

    @Test
    void deleteRecursivelyIgnoresMissingPath(@TempDir Path tmp) throws IOException {
        FileUtils.deleteRecursively(tmp.resolve("missing"));
        assertTrue(Files.exists(tmp));
    }
}
