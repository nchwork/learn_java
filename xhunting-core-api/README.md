# xhunting-core-api: Case CRUD của core-api, từ Go sang Java core

Port nhóm API **Case CRUD** của service `core-api` (xHunting) sang **Java 21**, không dùng Spring.
Server HTTP dùng `com.sun.net.httpserver.HttpServer` có sẵn trong JDK. Case được lưu vào **MongoDB**
(qua `mongodb-driver-sync`), hoặc lưu trong RAM nếu không đặt `MONGO_URI`. User và tenant là dữ liệu demo cố định.
Thư viện ngoài chỉ có Jackson (JSON) và MongoDB driver.

Nguồn gốc: `api/core-api/main.proto` (định nghĩa API) và `sample/case_service_handler.go` (logic xử lý).

## API đã port

URL giữ nguyên như `option (google.api.http)` trong `main.proto`, body và lỗi theo định dạng của grpc-gateway.

| Method | URL | RPC Go | Ghi chú |
|--------|-----|--------|---------|
| GET    | `/core-api/tenants/{tenantId}/cases` | `GetAll` | `?q=&status=a,b&sort=-createdTime&offset=0&limit=20` |
| GET    | `/core-api/tenants/{tenantId}/cases/{id}` | `GetById` | trả kèm `uploadToken` mới (hạn 24h) |
| POST   | `/core-api/tenants/{tenantId}/cases` | `Create` | body `{"item": {...}}` |
| PUT    | `/core-api/tenants/{tenantId}/cases/{id}` | `Update` | không sửa được `folderPath`, `status` |
| DELETE | `/core-api/tenants/{tenantId}/cases/{id}` | `Delete` | xóa mềm |
| POST   | `/core-api/tenants/{tenantId}/cases/@/upload-token` | *(không có trong Go)* | chỉ để chạy local, xem bên dưới |
| GET    | `/healthz` | | kiểm tra server sống |

Lỗi trả về giống grpc-gateway: `{"code": 5, "message": "CaseNotFound", "details": []}`
với HTTP 400 (InvalidArgument), 401, 403, 404, 500.

**Chưa port** (phụ thuộc hệ thống khác): Execute/Cancel/Resume/ScanAgain, executions, stats, activities,
DeleteMany, file service, message queue, Pusher.

---

## Hướng dẫn dựng và chạy server ở local

### Bước 1. Cài JDK 21

Windows (PowerShell, dùng winget có sẵn trên Windows 10/11):

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

Hoặc tải bản cài `.msi` ở https://adoptium.net (chọn Temurin 21, Windows x64) và tick
**"Set JAVA_HOME variable"** khi cài.

Mở **cửa sổ PowerShell mới** rồi kiểm tra:

```powershell
java -version      # phải ra: openjdk version "21..."
echo $env:JAVA_HOME
```

macOS: `brew install --cask temurin@21`. Ubuntu: `sudo apt install openjdk-21-jdk`.

### Bước 2. Cài Maven (công cụ build, ~ `go build` + `go mod`)

```powershell
winget install Apache.Maven
mvn -v             # mở cửa sổ mới rồi kiểm tra; dòng "Java version" phải là 21
```

Nếu winget không có Maven: tải `apache-maven-3.9.x-bin.zip` ở https://maven.apache.org/download.cgi,
giải nén vào `C:\tools\maven`, thêm `C:\tools\maven\bin` vào biến môi trường `Path`.

> Lần build đầu Maven tải thư viện về `%USERPROFILE%\.m2\repository` (~ `GOPATH/pkg/mod`), nên cần mạng.
> Nếu công ty dùng proxy hoặc Nexus nội bộ, cấu hình trong `%USERPROFILE%\.m2\settings.xml`.

### Bước 3. Cài Docker Desktop (để chạy MongoDB)

```powershell
winget install Docker.DockerDesktop
```

Khởi động lại máy nếu được yêu cầu, mở Docker Desktop và chờ biểu tượng cá voi báo "Engine running". Kiểm tra:

```powershell
docker compose version
```

> Không muốn dùng Docker? Cài MongoDB Community Server 7 (https://www.mongodb.com/try/download/community) dưới
> dạng Windows service, nó cũng lắng nghe ở `localhost:27017`, các bước sau giữ nguyên.
> Hoặc bỏ qua MongoDB hoàn toàn: không đặt `MONGO_URI` thì server lưu dữ liệu trong RAM.

### Bước 4. Lấy code

```powershell
git clone https://github.com/nchwork/learn_java.git
cd learn_java\xhunting-core-api
```

### Bước 5. Bật MongoDB

```powershell
docker compose up -d          # chạy MongoDB 7 ở localhost:27017 và Mongo Express ở http://localhost:8081
docker compose ps             # xhunting-mongo phải ở trạng thái "healthy"
```

Mongo Express là giao diện web để xem collection `cases` (database `xhunting`). Tắt bằng `docker compose down`;
thêm `-v` nếu muốn xóa luôn dữ liệu.

### Bước 6. Chạy test

```powershell
mvn test
```

Kết quả mong đợi: `Tests run: 16, Failures: 0` và `BUILD SUCCESS`. Test dựng server thật trên một port
ngẫu nhiên rồi gọi API như client thật. Cùng một bộ test chạy 2 lần: với RAM và với MongoDB
(mỗi lần một database tạm, xóa khi xong). Nếu MongoDB chưa bật, lớp test Mongo được bỏ qua và tổng chỉ còn
`Tests run: 9`.
Đổi địa chỉ Mongo cho test bằng biến `MONGO_TEST_URI`.

### Bước 7. Chạy server

Đặt `MONGO_URI` để server lưu vào MongoDB (không đặt thì lưu trong RAM):

```powershell
$env:MONGO_URI = "mongodb://localhost:27017"   # PowerShell, chỉ có hiệu lực trong cửa sổ này
```

```bash
export MONGO_URI=mongodb://localhost:27017      # Git Bash / Linux / macOS
```

Cách A, chạy thẳng từ source (~ `go run .`):

```powershell
mvn compile exec:java
```

Cách B, build ra file jar rồi chạy (~ `go build` rồi chạy binary):

```powershell
mvn package
java -jar target\xhunting-core-api-0.1.0.jar
```

Thấy hai dòng `storage: MongoDB mongodb://localhost:27017/xhunting` và `core-api listening on http://localhost:8080`
là xong. Dừng bằng `Ctrl+C`. Lần chạy đầu, nếu collection trống, server tạo sẵn 3 case mẫu.

Cách C, chạy trong IntelliJ IDEA: **File > Open** chọn thư mục `xhunting-core-api` (IntelliJ tự nhận `pom.xml`),
đợi index xong, mở `src/main/java/com/xhunting/coreapi/Main.java`, bấm nút ▶ cạnh `public static void main`.
Đặt biến môi trường ở **Run > Edit Configurations > Environment variables**.

### Cấu hình (biến môi trường)

| Biến | Mặc định | Ý nghĩa |
|------|----------|---------|
| `PORT` | `8080` | Port HTTP |
| `ENCRYPT_KEY` | khóa dev 32 byte | Khóa AES (16/24/32 ký tự) mã hóa upload token |
| `MONGO_URI` | *(trống)* | Ví dụ `mongodb://localhost:27017`. Trống = lưu trong RAM |
| `MONGO_DATABASE` | `xhunting` | Tên database |
| `SEED_DEMO_DATA` | `true` | Tạo sẵn 3 case mẫu khi khởi động (với Mongo: chỉ khi collection `cases` trống) |

PowerShell: `$env:PORT=9090; mvn compile exec:java`. Bash: `PORT=9090 mvn compile exec:java`.

### Tài khoản demo

Gửi header `Authorization: Bearer <token>`:

| Token | User id | Role | Tenant | Quyền |
|-------|---------|------|--------|-------|
| `admin-token` | u-admin | admin | MASTER (mọi tenant) | get/create/update/delete mọi case |
| `hunter1-token` | u-hunter1 | hunter | tenant-a | chỉ thấy case mình tạo hoặc được giao; chỉ xóa case mình tạo |
| `hunter2-token` | u-hunter2 | hunter | tenant-a | như trên |
| `viewer-token` | u-viewer | viewer | tenant-a | chỉ get |
| `disabled-token` | u-disabled | hunter (bị khóa) | tenant-a | bị từ chối 401; gán làm assignee sẽ lỗi `AssigneeDisabled` |

Dữ liệu mẫu: case `65f...001` (tenant-a, hunter1 tạo, giao hunter2), `65f...002` (tenant-a, hunter2 tạo,
đang `in_progress` nên không xóa được), `65f...003` (tenant-b, admin tạo, đã `verified`).
Admin dùng tenant `MASTER` trên URL để xem mọi tenant.

### Bước 8. Gọi thử API

Dễ nhất: mở file [`requests.http`](requests.http) trong IntelliJ hoặc VS Code (extension REST Client) và bấm chạy
từng request.

Hoặc dùng PowerShell (cả luồng tạo case):

```powershell
$base = "http://localhost:8080/core-api/tenants/tenant-a"
$h = @{ Authorization = "Bearer hunter1-token" }

# 1. Xin upload token: hệ thống thật cấp token này ở bước upload file
$t = Invoke-RestMethod -Method Post -Uri "$base/cases/@/upload-token" -Headers $h

# 2. Tạo case
$body = @{ item = @{
    tenantId = "tenant-a"; name = "Demo case"; priority = "high"
    filenames = @("host01.zip"); folderPath = $t.folderPath; uploadToken = $t.uploadToken
} } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri "$base/cases" -Headers $h -ContentType "application/json" -Body $body

# 3. Danh sách
Invoke-RestMethod -Uri "$base/cases?sort=name" -Headers $h | ConvertTo-Json -Depth 6

# 4. Xóa
Invoke-RestMethod -Method Delete -Uri "$base/cases/$($t.caseId)" -Headers $h
```

Hoặc curl (Git Bash / Linux / macOS; trên PowerShell gõ `curl.exe` thay vì `curl`):

```bash
curl -s -X POST -H "Authorization: Bearer hunter1-token" \
     http://localhost:8080/core-api/tenants/tenant-a/cases/@/upload-token
curl -s -H "Authorization: Bearer admin-token" http://localhost:8080/core-api/tenants/MASTER/cases
```

### Gặp lỗi thường gặp

| Triệu chứng | Cách xử lý |
|-------------|------------|
| `'java' is not recognized` / `mvn` không chạy | Mở cửa sổ terminal mới sau khi cài; kiểm tra `Path` có `...\bin` của JDK/Maven |
| `mvn -v` báo Java 8 hoặc 17 | Đặt `JAVA_HOME` trỏ tới JDK 21, mở lại terminal |
| `release version 21 not supported` | Maven đang dùng JDK cũ, như dòng trên |
| `BindException: Address already in use` | Port 8080 đang bận: đổi `PORT`, hoặc tìm tiến trình bằng `netstat -ano \| findstr :8080` |
| Lỗi tải dependency / PKIX certificate | Mạng công ty chặn Maven Central: cấu hình proxy/mirror trong `.m2\settings.xml` |
| `UploadTokenInvalid` | Token phải được cấp bởi server dùng cùng `ENCRYPT_KEY`, và `folderPath` gửi lên phải khớp với token. Đổi `ENCRYPT_KEY` thì token cũ hết dùng được |
| Dữ liệu mất sau khi tắt server | Chưa đặt `MONGO_URI` nên đang lưu trong RAM (log hiện `storage: in-memory`) |
| `MongoTimeoutException` / `Timed out ... localhost:27017` lúc khởi động | MongoDB chưa chạy: `docker compose up -d`, kiểm tra Docker Desktop đang chạy |
| Port 27017 hoặc 8081 đã bị chiếm | Đã có MongoDB khác đang chạy: dùng luôn nó, hoặc đổi port trong `docker-compose.yml` |
| Muốn xóa sạch dữ liệu | `docker compose down -v` rồi `docker compose up -d` |

---

## Cấu trúc code: Go -> Java

```
src/main/java/com/xhunting/coreapi/
├── Main.java                      main.go: đọc config, nối dây dependency, start, chờ Ctrl+C
├── App.java                       server + tài nguyên cần đóng (MongoClient) khi tắt
├── AppConfig.java                 config yaml + mapstructure -> record đọc biến môi trường
├── http/
│   ├── CoreApiServer.java         grpc-gateway + gRPC server -> JDK HttpServer + virtual threads
│   ├── Router.java                route "{tenantId}/cases/{id}" -> regex named group
│   └── CaseRequest.java           message CaseRequest
├── handler/
│   ├── CaseServiceHandler.java    case_service_handler.go: Create/GetAll/GetById/Update/Delete
│   ├── RequestContext.java        sdf.Context (tenant_id, id, metadata Authorization)
│   └── FindParams.java            sdf.FindRequest (đơn giản hóa)
├── cases/
│   ├── Case.java                  message Case + struct cases.Case
│   ├── UploadToken.java           message UploadToken
│   ├── CaseRepository.java        interface cases.CaseRepository
│   ├── MongoCaseRepository.java   repository MongoDB: Case <-> Document, CaseFilter -> Filters (BSON)
│   ├── InMemoryCaseRepository.java  bản lưu RAM: ConcurrentHashMap
│   └── CaseFilter.java            repositories.FindRequest: filter dạng dữ liệu, mỗi repository tự dịch
├── auth/                          authGuard, permissionGuard, contexts.UserContext, authx.HasHunterRole
├── users/, tenants/               users.UserService, tenants.TenantService (gRPC -> in-memory)
├── eventlogs/                     eventlogs.NewBuilder + logPusher
└── common/
    ├── AesUtil.java               encryptions/aes_util.go (AES-CBC + PKCS#7, IV ở đầu)
    ├── Validator.java             //@gotags: validate:"..." -> kiểm tra viết tay
    ├── ApiException.java          status.Error(codes.X) + ErrCaseNotFound...
    └── Constants.java             CaseStatusXxx, ActionXxx
```

## Khác biệt Go -> Java đáng nhớ trong bài này

1. **Mỗi request một goroutine vs executor.** Go `net/http` tự chạy mỗi request trên một goroutine.
   JDK `HttpServer` mặc định chỉ có 1 thread; phải `setExecutor(Executors.newVirtualThreadPerTaskExecutor())`.
   Virtual thread (Java 21) rẻ như goroutine.
2. **`defer` -> `try/finally`.** Event log được push trong `finally`, lỗi được ghi trong `catch` rồi ném lại.
3. **`if err != nil` -> exception.** `ApiException` (unchecked) bay thẳng lên `CoreApiServer.dispatch`, nơi duy nhất
   đổi lỗi thành HTTP status. Spring làm việc này bằng `@ControllerAdvice`.
4. **`nil` -> `Optional`.** `repository.findOne(...).orElseThrow(ApiException::caseNotFound)` thay cho `if exist == nil`.
5. **Map và concurrency.** Go map phải bọc `sync.RWMutex`. `ConcurrentHashMap` an toàn sẵn; `putIfAbsent`,
   `computeIfPresent` là thao tác atomic.
6. **Copy struct.** Go `b := *a` copy giá trị. Java gán chỉ copy tham chiếu, nên repository trả `copy()` để
   handler sửa object không làm hỏng dữ liệu đang lưu.
7. **Bất biến.** `UserContext` là record; Go `Clone()` + `SetRequestTenantId()` thành `withRequestTenantId()` trả object mới.
8. **Filter là dữ liệu.** Giống `FindRequestFilter` của Go, `CaseFilter` là record mô tả điều kiện; bản RAM dịch thành
   `Predicate<Case>`, bản Mongo dịch thành `Filters.and(Filters.eq(...), Filters.or(...))` (Go: `bson.M{...}`).
   Hunter thấy case mình tạo hoặc được giao = `$or: [{createdByUserId: u}, {assigneeIds: u}]`.
9. **DI.** `NewCaseServiceHandler(16 tham số)` thành constructor injection. `Main.buildServer` là chỗ nối dây,
   đúng phần Spring sẽ làm thay bạn.
10. **JSON.** Go có `encoding/json` trong thư viện chuẩn; Java không có, dùng Jackson (Spring cũng dùng Jackson).
    Record được Jackson đọc/ghi trực tiếp; class thường cần getter/setter.
11. **Crypto.** `aes.NewCipher` + `cipher.NewCBCEncrypter` + `pad()` tự viết gộp thành `Cipher.getInstance("AES/CBC/PKCS5Padding")`,
    ciphertext tương thích với bản Go (PKCS5 với block 16 byte chính là PKCS#7).
12. **Test.** `httptest.NewServer` -> start server trên port 0 + `java.net.http.HttpClient`. Một bộ test trừu tượng,
    hai lớp con (RAM, Mongo) ~ một hàm test nhận repository làm tham số trong Go.
13. **MongoDB driver.** `mongo.Connect` -> `MongoClients.create(uri)`, tạo một lần và dùng chung (có pool sẵn);
    `defer client.Disconnect` -> `MongoClient` là `AutoCloseable`, đóng trong `App.stop()`.
    `coll.Find(ctx, filter)` + `cursor.All` -> `collection.find(filter)` duyệt bằng for-each.
    Driver sync của Java không nhận `context.Context`; timeout đặt trong connection string hoặc `MongoClientSettings`.
    `mongo.IsDuplicateKeyError` -> bắt `MongoWriteException` có category `DUPLICATE_KEY`.
14. **Map document.** Go map struct <-> BSON bằng tag `bson:"..."`. Ở đây map tay qua `org.bson.Document`
    (`toDocument`/`fromDocument`) để thấy rõ từng field; tên field giữ như bản Go (`_id` là ObjectId, `createdByUserId`, `isDeleted`).

## Chỗ khác hoặc lược bớt so với bản Go

- Xác thực dùng token demo thay vì JWT; phân quyền là bảng role -> action cố định thay vì policy từ identity service.
- `checkValidAssignees`: kiểm tra quyền của assignee trên **tenant của case** (bản Go dựng context không có tenant).
- `GetAll` nhận query string đơn giản thay vì `FindRequest.payload` JSON; không đếm file/endpoint (cần file service).
- `GetById` không có `latestExecutionId` (cần execution repository).
- Giữ nguyên hành vi Go: Create/Update **không kiểm tra `expiredAt`** của upload token. Đây là điểm đáng hỏi lại team.
- Endpoint `cases/@/upload-token` chỉ để chạy local.

## Bước tiếp theo khi học Spring

Viết lại đúng project này bằng Spring Boot để thấy phần nào biến mất:
`CoreApiServer` + `Router` -> `@RestController` + `@GetMapping`; `Validator` -> `@Valid` + Bean Validation;
`MongoCaseRepository` -> `MongoRepository<Case, String>` (Spring sinh code); `AuthGuard`/`PermissionGuard` -> Spring Security;
`Main.buildServer` -> `@Service`/`@Component` + auto-wiring; `ApiException` handling -> `@RestControllerAdvice`.
