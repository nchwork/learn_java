# xhunting-extractors: giải nén archive, từ Go sang Java core

Port package `golang-common/extractors` của xHunting sang **Java 21 thuần** (không Spring, không thư viện ngoài
cho code chính). Tính năng: nhận một file archive (zip, 7z, rar, tar, gz), tự nhận diện định dạng bằng magic bytes,
gọi binary `7z` để giải nén, thử lần lượt danh sách mật khẩu nếu archive có mật khẩu, rồi "nâng" thư mục con
đầu tiên lên để kết quả luôn là `dest/<tên archive>/...`.

## Chạy

Cần JDK 21, Maven 3.9 và `7z` trong PATH (Windows: cài 7-Zip rồi đặt `SEVEN_ZIP=C:\Program Files\7-Zip\7z.exe`).

```bash
mvn test                                   # 20 test; test tích hợp tự bỏ qua nếu không có 7z
mvn package
java -jar target/xhunting-extractors-0.1.0.jar logs.zip out/ wrongpass S3cret
```

## Bản đồ file Go -> Java

| Go (`golang-common/`)                        | Java (`src/main/java/com/xhunting/extractors/`)        |
|----------------------------------------------|--------------------------------------------------------|
| `extractors/service.go`                      | `ExtractorService.java` (interface)                    |
| `extractors/model.go`                        | `CommandArchive.java` (record)                         |
| `extractors/default_service.go`              | `DefaultExtractorService.java`, `ExtractorConfig.java` |
| `detectArchiveExtByContent` (cùng file trên) | `ArchiveDetector.java`                                 |
| `extractors/zip_extractor.go`                | `ZipExtractor.java`                                    |
| `extractors/sevenzip_extractor.go`           | `SevenZipExtractor.java`                               |
| `extractors/rar_extractor.go`                | `RarExtractor.java`                                    |
| `extractors/tar_extractor.go`                | `TarExtractor.java`                                    |
| `extractors/gz_extractor.go`                 | `GzExtractor.java`                                     |
| `utils.go` (`RunCmd`)                        | `CommandRunner.java`                                   |
| `utils.go` (`RemoveExt`, `NormalizeExtractedFolder`...) | `FileUtils.java`                            |
| `constants.go` (`ExtensionZip`...)           | `ArchiveFormat.java` (enum)                            |
| `errorx.New(PasswordRequired)`               | `PasswordRequiredException.java`, `ExtractException.java` |
| `*_test.go`                                  | `src/test/java/.../*Test.java` (JUnit 5)               |

Mỗi file Java đều có javadoc trích đoạn Go tương ứng và ghi chú khác biệt ngay tại chỗ.

## Những khác biệt quan trọng nhất

**1. Method rải nhiều file vs class một file.** Go cho `ExtractZip`, `ExtractRar`... là method của cùng
`*DefaultExtractorService` nhưng nằm ở các file khác nhau. Java bắt một class nằm trọn trong một file, nên mỗi
định dạng thành một class nhỏ package-private, và `DefaultExtractorService` giữ các instance đó (composition).

**2. Interface ngầm vs khai báo.** Go thỏa mãn interface ngầm. Java phải `implements ExtractorService` và
nên gắn `@Override` để compiler bắt lỗi gõ sai tên method.

**3. `error` vs exception.**
- `return err` -> `throw new ExtractException(...)`; `fmt.Errorf("...: %w", err)` -> `new ExtractException(msg, cause)`.
- `errors.Is(err, ErrX)` -> `catch (PasswordRequiredException e)` (bắt subclass trước class cha).
- Dùng *checked* exception (`extends Exception`) để compiler ép xử lý, gần tinh thần `if err != nil`.
- Hệ quả khó chịu: lambda trong stream không được ném checked exception, xem `SevenZipExtractor.tryRun`.

**4. Chạy process ngoài: `exec.Command` -> `ProcessBuilder`.**
- Exit code khác 0 **không** tự thành lỗi trong Java; phải tự kiểm tra `waitFor() != 0`.
- Output không đọc sẽ làm đầy pipe và **treo process con**. Go không gán `cmd.Stdout` thì output vào /dev/null;
  Java phải `redirectOutput(DISCARD)` hoặc đọc hết stream.
- `CombinedOutput()` -> `redirectErrorStream(true)`.
- Bắt `InterruptedException` thì phải gọi lại `Thread.currentThread().interrupt()`. Go dùng `context.Context` để hủy.

**5. `switch` trên string vs enum.** Hằng `ExtensionZip = ".zip"` thành `enum ArchiveFormat`. Switch expression
trên enum (Java 21) không cần `break` và compiler báo lỗi nếu thiếu case. Dòng `return nil` sau switch của bản Go
là code chết; Java báo "unreachable statement" nên không viết được.

**6. Struct -> record.** `CommandArchive` là `record`: bất biến, tự có constructor, accessor, `equals`, `toString`.
Không có struct tag `mapstructure`; ở Spring việc đó do `@ConfigurationProperties` làm. Không có zero value
`""`, mặc định là `null`, nên validate trong compact constructor.

**7. File và đường dẫn.** `string` + `path/filepath` + `os` -> `Path` + `Files`.
- `defer f.Close()` -> try-with-resources. `Files.list`/`Files.walk` trả `Stream` giữ file handle, cũng phải đóng.
- JDK không có `filepath.Ext` và `os.RemoveAll`, phải tự viết (`FileUtils.ext`, `FileUtils.deleteRecursively`).
- `os.ReadDir` của Go trả kết quả đã sắp xếp; `Files.list` thì không, nên `firstSubDir` phải `sorted()`.

**8. Byte có dấu.** Go `byte` là uint8, Java `byte` là -128..127, nên so magic `0x8B` phải viết `(byte) 0x8B`.
`bytes.HasPrefix` / slice `head[257:262]` -> `Arrays.equals(a, from, to, b, from, to)`.
JDK không có `http.DetectContentType`; với các định dạng này nó cũng chỉ so magic bytes, nên bản Java chỉ giữ
bước magic bytes + fallback đuôi file.

**9. Hàm cấp package -> static method.** `golang_common.RunCmd(...)` thành `CommandRunner.run(...)` trong lớp
`final` có constructor `private`. Chữ thường/chữ hoa của Go -> package-private/`public` của Java.

**10. Test.** `go test` -> `mvn test`. Table-driven test -> `@ParameterizedTest` + `@CsvSource`. `t.TempDir()` ->
`@TempDir`. `t.Skip(...)` -> `assumeTrue(...)`. Lưu ý Surefire mặc định chỉ chạy class tên `*Test` (`*IT` là của
Failsafe), nên test tích hợp tên `DefaultExtractorServiceIntegrationTest`.

## Hành vi giữ nguyên từ bản Go (cần biết)

- `checkNeedPassword*` giải nén thử một lần không mật khẩu chỉ để đọc output. Bản Java giữ nguyên, nhưng đóng stdin
  của 7z để nó không đứng chờ nhập mật khẩu.
- `.gz` giải nén 2 lớp, lớp tar được đổ vào cạnh file gốc (`RemovePathExt(file)`), nên file `.tar.gz` phải nằm sẵn
  trong `dest` như cách xHunting dùng.
- Phát hiện gzip luôn trả `.gz`, nên nhánh `TAR_GZ` trong switch chỉ để đủ case.

## Bài tập gợi ý

1. Gom phần "thử không mật khẩu rồi thử từng mật khẩu" của Zip/7z/Rar vào một abstract class (template method).
2. Thay `ZipExtractor` bằng `java.util.zip` thuần (không cần 7z) cho zip không mật khẩu.
3. Khi học Spring: biến `DefaultExtractorService` thành `@Service`, `CommandArchive` thành `@ConfigurationProperties`.
