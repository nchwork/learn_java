package com.xhunting.extractors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Port của {@code tar_extractor.go}: tar không có mật khẩu nên không có tham số passwords. */
final class TarExtractor {

    void extract(String command, Path file, Path dest) throws ExtractException {
        Path target = dest.resolve(FileUtils.fileNameWithoutExt(file));
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new ExtractException("cannot create " + target, e);
        }

        CommandRunner.run(command, "x", file.toString(), "-o" + target, "-y");

        ZipExtractor.normalize(target, dest);
    }
}
