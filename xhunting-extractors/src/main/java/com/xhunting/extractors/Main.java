package com.xhunting.extractors;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Chương trình demo, tương đương một {@code func main()} nhỏ.
 *
 * <pre>
 * mvn -q package
 * java -jar target/xhunting-extractors-0.1.0.jar &lt;file&gt; &lt;destDir&gt; [password...]
 * </pre>
 *
 * Biến môi trường {@code SEVEN_ZIP} chọn binary 7z (mặc định "7z"), giống việc bản Go đọc
 * {@code command_zip}, {@code command_seven_zip}... từ file config.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: java -jar xhunting-extractors.jar <file> <destDir> [password...]");
            System.exit(2); // ~ os.Exit(2)
        }

        Path file = Path.of(args[0]);
        Path dest = Path.of(args[1]);
        List<String> passwords = Arrays.asList(args).subList(2, args.length);

        String sevenZip = System.getenv().getOrDefault("SEVEN_ZIP", "7z"); // ~ os.Getenv + giá trị mặc định
        ExtractorService service = new DefaultExtractorService(ExtractorConfig.defaults());

        try {
            service.extract(CommandArchive.allUsing(sevenZip), file, dest, passwords);
            System.out.println("extracted to " + dest.toAbsolutePath());
        } catch (PasswordRequiredException e) {
            // catch subclass trước, class cha sau (~ errors.Is(err, ErrPasswordRequired))
            System.err.println("wrong or missing password: " + e.getMessage());
            System.exit(1);
        } catch (ExtractException e) {
            System.err.println("extract failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
