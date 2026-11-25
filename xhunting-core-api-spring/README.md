# xhunting-core-api-spring: Case CRUD của core-api bằng Spring Boot

Cùng phạm vi và hành vi với [`xhunting-core-api`](../xhunting-core-api/) (bản Java core), nhưng viết bằng
**Spring Boot 3.4 + Spring Web + Spring Data MongoDB + Bean Validation**. Mục tiêu là đặt ba phiên bản cạnh nhau
(Go, Java core, Spring) để thấy Spring làm thay bạn những gì.

URL, JSON request/response, mã lỗi, tài khoản demo và dữ liệu mẫu **giống hệt bản Java core**, nên file
[`requests.http`](requests.http) và các lệnh curl/PowerShell dùng chung được.

| Method | URL | Ghi chú |
|--------|-----|---------|
| GET    | `/core-api/tenants/{tenantId}/cases` | `?q=&status=a,b&sort=-createdTime&offset=0&limit=20` |
| GET    | `/core-api/tenants/{tenantId}/cases/{id}` | trả kèm `uploadToken` mới (hạn 24h) |
| POST   | `/core-api/tenants/{tenantId}/cases` | body `{"item": {...}}` |
| PUT    | `/core-api/tenants/{tenantId}/cases/{id}` | không sửa được `folderPath`, `status` |
| DELETE | `/core-api/tenants/{tenantId}/cases/{id}` | xóa mềm |
| POST   | `/core-api/tenants/{tenantId}/cases/@/upload-token` | chỉ để chạy local |
| GET    | `/actuator/health` | Spring Actuator, thay `/healthz` |

---

## Hướng dẫn chạy ở local

Nếu đã chạy được bản Java core thì máy bạn đã đủ: **JDK 21, Maven, Docker Desktop**. Nếu chưa, làm Bước 1–3 trong
[README của bản core](../xhunting-core-api/README.md#hướng-dẫn-dựng-và-chạy-server-ở-local).

### 1. Bật MongoDB

```powershell
cd learn_java\xhunting-core-api-spring
docker compose up -d
```

MongoDB ở `localhost:27017`, Mongo Express (xem dữ liệu) ở http://localhost:8081.

### 2. Chạy test

```powershell
mvn test
```

Mong đợi `Tests run: 9, Failures: 0`. Test **tự bật một MongoDB riêng trong Docker** bằng Testcontainers
(không đụng vào Mongo ở bước 1), chạy app Spring thật trên port ngẫu nhiên rồi gọi API. Docker Desktop phải đang chạy;
nếu không, 7 test tích hợp hiện `Skipped`. Lần đầu sẽ hơi lâu vì Maven tải Spring và Docker tải image `mongo:7`.

### 3. Chạy server

```powershell
mvn spring-boot:run
```

hoặc build jar rồi chạy:

```powershell
mvn package
java -jar target\xhunting-core-api-spring-0.1.0.jar
```

Thấy `Started CoreApiApplication in ... seconds` và `seeded 3 demo cases` (lần đầu) là xong.
Trong IntelliJ: mở thư mục `xhunting-core-api-spring`, chạy `CoreApiApplication.main`.

**Khác bản core:** bản này **luôn dùng MongoDB** (mặc định `mongodb://localhost:27017`), không có chế độ lưu RAM.
Quên bật Mongo thì app vẫn khởi động nhưng request đầu tiên sẽ lỗi timeout sau khoảng 30 giây.

### 4. Gọi thử

Mở [`requests.http`](requests.http) trong IntelliJ/VS Code, hoặc dùng đúng các lệnh PowerShell/curl ở
[bước 8 của bản core](../xhunting-core-api/README.md#bước-8-gọi-thử-api). Bảng tài khoản demo cũng ở README đó
(`admin-token`, `hunter1-token`, `hunter2-token`, `viewer-token`).

### Cấu hình

Khai báo trong [`application.yml`](src/main/resources/application.yml), ghi đè bằng biến môi trường:

| Biến | Mặc định | Ý nghĩa |
|------|----------|---------|
| `PORT` | `8080` | Port HTTP |
| `MONGO_URI` | `mongodb://localhost:27017` | Địa chỉ MongoDB |
| `MONGO_DATABASE` | `xhunting` | Tên database |
| `ENCRYPT_KEY` | khóa dev 32 byte | Khóa AES mã hóa upload token |
| `SEED_DEMO_DATA` | `true` | Tạo 3 case mẫu nếu collection trống |

Cũng có thể truyền tham số: `java -jar target\xhunting-core-api-spring-0.1.0.jar --server.port=9090`.

### Lỗi thường gặp

| Triệu chứng | Cách xử lý |
|-------------|------------|
| Port 8080 đã bị chiếm (đang chạy bản core?) | Tắt bản kia, hoặc `$env:PORT=9090` |
| `Timed out after 30000 ms while waiting ... localhost:27017` | MongoDB chưa bật: `docker compose up -d` |
| `mvn test` báo 7 test `Skipped` | Docker Desktop chưa chạy |
| Test lỗi `Could not find a valid Docker environment` | Mở Docker Desktop. File `src/test/resources/docker-java.properties` đã ép API 1.44 cho Docker Engine 29+ |
| Muốn xóa sạch dữ liệu | `docker compose down -v` rồi `docker compose up -d` |

---

## Ba phiên bản đặt cạnh nhau

| Việc cần làm | Go (core-api) | Java core (`xhunting-core-api`) | Spring Boot (bản này) |
|---|---|---|---|
| Khởi động, nối dây | `main.go`: `NewXxx(...)` truyền tay | `Main.buildApp`: `new` từng class | `@SpringBootApplication` + `@Service/@Component`, Spring tự gọi constructor |
| Cấu hình | yaml + `mapstructure` tag | `AppConfig.fromEnv` | `application.yml` + `@ConfigurationProperties` (`AppProperties`) |
| HTTP server | grpc-gateway + gRPC | `HttpServer` JDK + executor virtual thread | Tomcat nhúng, `spring.threads.virtual.enabled` |
| Định tuyến | `option (google.api.http)` trong proto | `Router` tự viết (regex) | `@RestController`, `@GetMapping("/{id}")`, `@PathVariable` |
| JSON | `protojson` | `ObjectMapper` tự tạo, `readValue/writeValue` | Jackson tự cấu hình, `@RequestBody` / giá trị trả về |
| Validate | tag `validate:"required,max=255"` | `Validator.validateCase` viết tay | `@NotBlank @Size @Pattern` + `@Valid` |
| Lấy user từ request | `getUserContext` trong từng handler | `AuthGuard` gọi trong handler | `UserContextArgumentResolver`: controller nhận sẵn `UserContext` |
| Xử lý lỗi | `status.Error(codes.X)`, gateway đổi sang HTTP | `try/catch` trong `dispatch` | `@RestControllerAdvice` (`GlobalExceptionHandler`) |
| Repository | tự viết với mongo-driver | `MongoCaseRepository` ~150 dòng | `interface CaseRepository extends MongoRepository` (Spring sinh code) |
| Map document | tag `bson:"createdByUserId"` | `toDocument/fromDocument` viết tay | `@Document`, `@Id`, `@Field("createdByUserId")`, `@Transient` |
| Query động | `bson.M{...}` | `Filters.and(Filters.eq(...))` | `Criteria.where("creatorId").is(...)` qua `MongoTemplate` |
| Index | migration / tạo lúc start | `createIndex` trong constructor | `@Indexed`, `@CompoundIndex` + `auto-index-creation` |
| Trùng khóa | `mongo.IsDuplicateKeyError` | `MongoWriteException` + `DUPLICATE_KEY` | `DuplicateKeyException` (từ `insert`; `save` thì ghi đè) |
| Dữ liệu mẫu | không có | `Main.seed` | `CommandLineRunner` + `@ConditionalOnProperty` |
| Health check | tự viết | `/healthz` tự viết | Actuator `/actuator/health` |
| Test tích hợp | `httptest` + Mongo thật | server port 0 + Mongo có sẵn | `@SpringBootTest(RANDOM_PORT)` + Testcontainers `@ServiceConnection` |

Phần **logic nghiệp vụ** (`CaseService` so với `CaseServiceHandler` bản core) gần như giữ nguyên từng dòng. Spring
thay phần "ống nước" xung quanh, không thay cách bạn viết business logic. Đây cũng là điều nên mang theo khi chuyển từ Go.

## Cấu trúc code

```
src/main/java/com/xhunting/coreapi/
├── CoreApiApplication.java        @SpringBootApplication, main()
├── cases/
│   ├── CaseController.java        @RestController: 6 endpoint
│   ├── CaseService.java           @Service: logic Create/GetAll/GetById/Update/Delete (port từ Go)
│   ├── Case.java                  @Document + Bean Validation + Jackson
│   ├── CaseRepository.java        extends MongoRepository<Case, String>, CaseQueries
│   ├── CaseQueries.java           interface truy vấn động
│   ├── CaseRepositoryImpl.java    cài đặt bằng MongoTemplate + Criteria
│   ├── CaseFilter.java            filter dạng dữ liệu (giống bản core)
│   └── UploadToken.java
├── auth/
│   ├── UserContextArgumentResolver.java  đọc Authorization + {tenantId} cho controller
│   └── AuthGuard.java, PermissionGuard.java, UserContext.java
├── config/
│   ├── AppProperties.java         @ConfigurationProperties("xhunting")
│   ├── WebConfig.java             đăng ký argument resolver
│   └── DemoDataSeeder.java        CommandLineRunner tạo dữ liệu mẫu
├── common/                        ApiException, GlobalExceptionHandler, AesUtil, Constants
└── users/, tenants/, eventlogs/   @Service in-memory (giống bản core)
src/main/resources/application.yml
src/test/java/.../CaseApiIntegrationTest.java   7 kịch bản giống bản core, Mongo qua Testcontainers
```

## Bài tập tiếp theo (giai đoạn 3 của lộ trình)

1. Thay `AuthGuard` + `UserContextArgumentResolver` bằng **Spring Security** với JWT
   (`spring-boot-starter-oauth2-resource-server`), role lấy từ claim.
2. Tách `Case` thành `CaseDocument` (entity) và `CaseDto` (API) + MapStruct.
3. Viết `@WebMvcTest` cho `CaseController` với `@MockitoBean CaseService` (test nhanh không cần Mongo).
4. Thêm `spring-boot-docker-compose` để `mvn spring-boot:run` tự bật `docker-compose.yml`.
