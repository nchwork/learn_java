package com.xhunting.extractors;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Port của {@code extractors/default_service.go}.
 *
 * <pre>{@code
 * type DefaultExtractorService struct { Config *ExtractorConfig }
 *
 * func NewDefaultExtractorService(config *ExtractorConfig) (*DefaultExtractorService, error) {...}
 *
 * func (inst *DefaultExtractorService) Extract(commandArchive CommandArchive, file, dest string,
 *                                              passwords []string) error {
 *     ext := detectArchiveExtByContent(file)
 *     switch ext {
 *     case common_constants.ExtensionZip: return inst.ExtractZip(...)
 *     ...
 *     default: return fmt.Errorf("unsupported format")
 *     }
 *     return nil   // <- dòng này không bao giờ chạy tới
 * }
 * }</pre>
 *
 * Khác biệt Go -> Java:
 * <ul>
 *   <li>{@code struct} + hàm {@code NewXxx(...)} -> {@code class} + constructor. Go trả
 *       {@code (*T, error)} từ constructor; Java constructor không trả giá trị, lỗi thì ném exception.</li>
 *   <li>Receiver {@code (inst *DefaultExtractorService)} -> {@code this} (ngầm định).</li>
 *   <li>{@code switch} trên string -> switch expression trên enum (Java 21). Compiler báo lỗi nếu
 *       thiếu case nào của enum, và mỗi nhánh không cần {@code break}.</li>
 *   <li>Dòng {@code return nil} cuối hàm Go là code chết nhưng Go vẫn compile. Java báo lỗi
 *       "unreachable statement" nên không viết được dòng đó.</li>
 *   <li>Trong {@code ExtractorConfig}, bản Go có thể nhận {@code nil}. Java dùng
 *       {@link Objects#requireNonNull} để fail sớm.</li>
 * </ul>
 */
public class DefaultExtractorService implements ExtractorService {

    private final ExtractorConfig config;

    // Mỗi định dạng một object (xem javadoc của ZipExtractor để biết lý do tách file).
    private final ZipExtractor zip = new ZipExtractor();
    private final SevenZipExtractor sevenZip = new SevenZipExtractor();
    private final RarExtractor rar = new RarExtractor();
    private final TarExtractor tar = new TarExtractor();
    private final GzExtractor gz = new GzExtractor();

    public DefaultExtractorService(ExtractorConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public ExtractorConfig config() {
        return config;
    }

    @Override // annotation báo compiler: method này phải ghi đè method của interface (Go không có)
    public void extract(CommandArchive commandArchive, Path file, Path dest, List<String> passwords)
            throws ExtractException {
        String ext = ArchiveDetector.detectArchiveExtByContent(file);
        config.logger().log(System.Logger.Level.INFO, "extract {0} (detected {1}) -> {2}", file, ext, dest);

        ArchiveFormat format = ArchiveFormat.fromExtension(ext)
                .orElseThrow(() -> new ExtractException("unsupported format"));

        // Java không có giá trị mặc định cho List như nil slice của Go; chấp nhận null cho tiện.
        List<String> pw = passwords == null ? List.of() : passwords;

        switch (format) {
            case ZIP -> zip.extract(commandArchive.commandZip(), file, dest, pw);
            case SEVEN_ZIP -> sevenZip.extract(commandArchive.commandSevenZip(), file, dest, pw);
            case RAR -> rar.extract(commandArchive.commandRar(), file, dest, pw);
            case TAR, TAR_GZ -> tar.extract(commandArchive.commandTar(), file, dest);
            case GZ -> gz.extract(commandArchive.commandGz(), file, dest);
        }
    }
}
