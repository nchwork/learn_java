package com.xhunting.extractors;

import java.nio.file.Path;
import java.util.List;

/**
 * Port của {@code extractors/service.go}.
 *
 * <pre>{@code
 * type ExtractorService interface {
 *     Extract(commandArchive CommandArchive, file, dest string, passwords []string) error
 * }
 * }</pre>
 *
 * Khác biệt Go -> Java:
 * <ul>
 *   <li>Go: interface được thỏa mãn NGẦM (structural typing). Java: class phải khai báo
 *       {@code implements ExtractorService} rõ ràng (nominal typing).</li>
 *   <li>Go trả về {@code error}. Java ném exception; {@code throws ExtractException} là
 *       checked exception nên compiler BẮT người gọi xử lý, gần với tinh thần
 *       "if err != nil" nhất.</li>
 *   <li>{@code string} cho đường dẫn -> {@link Path} (java.nio.file), an toàn kiểu hơn.</li>
 *   <li>{@code []string} -> {@code List<String>} (immutable được, có generics).</li>
 * </ul>
 */
public interface ExtractorService {

    void extract(CommandArchive commandArchive, Path file, Path dest, List<String> passwords)
            throws ExtractException;
}
