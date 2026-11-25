package com.xhunting.extractors;

import java.nio.file.Path;

/**
 * Port của {@code gz_extractor.go}.
 *
 * Một file .gz (thường là .tar.gz) cần giải nén 2 lần bằng 7z:
 * <ol>
 *   <li>{@code logs.tar.gz} -> {@code dest/logs.tar}</li>
 *   <li>{@code dest/logs.tar} -> thư mục {@code <file bỏ .tar.gz>} (cạnh file gốc, giữ nguyên hành vi Go)</li>
 * </ol>
 * rồi chuẩn hóa thư mục {@code dest/logs}.
 */
final class GzExtractor {

    void extract(String command, Path file, Path dest) throws ExtractException {
        Path originalDest = dest;
        Path inner = dest.resolve(FileUtils.fileNameWithoutExt(file)); // dest/logs.tar

        CommandRunner.run(command, "x", file.toString(), "-o" + originalDest, "-y");
        CommandRunner.run(command, "x", inner.toString(), "-o" + FileUtils.removePathExt(file), "-y");

        ZipExtractor.normalize(FileUtils.removeExt(inner), originalDest);
    }
}
