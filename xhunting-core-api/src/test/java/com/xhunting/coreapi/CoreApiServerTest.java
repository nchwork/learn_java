package com.xhunting.coreapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Bộ test dùng chung cho mọi kiểu lưu trữ; lớp con chọn config (in-memory hoặc MongoDB).
 * Go tương đương: một hàm test nhận repository làm tham số, gọi từ 2 hàm TestXxx.
 *
 * Test end-to-end: dựng server thật trên port ngẫu nhiên rồi gọi bằng {@link HttpClient} (JDK 11+).
 * Go tương đương: {@code httptest.NewServer(handler)} + {@code http.Get(srv.URL + ...)}.
 */
abstract class CoreApiServerTest {

    private static final String ADMIN = "admin-token";
    private static final String HUNTER1 = "hunter1-token";
    private static final String HUNTER2 = "hunter2-token";
    private static final String VIEWER = "viewer-token";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private App app;
    private String base;

    /** Lớp con trả config muốn test (port 0 = tự chọn port trống). */
    protected abstract AppConfig config();

    @BeforeEach
    void start() throws Exception {
        app = Main.buildApp(config());
        app.start(0);
        base = "http://localhost:" + app.server().port() + "/core-api/tenants/";
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void fullCrudFlowAsHunter() throws Exception {
        // 1. Xin upload token (case id + folderPath)
        JsonNode token = call("POST", "tenant-a/cases/@/upload-token", HUNTER1, null, 200);
        String caseId = token.get("caseId").asText();

        // 2. Create
        String body = """
                {"item": {
                  "tenantId": "tenant-a",
                  "name": "Lateral movement host-07",
                  "priority": "high",
                  "assigneeIds": ["u-hunter2"],
                  "filenames": ["host07.zip"],
                  "folderPath": "%s",
                  "uploadToken": "%s"
                }}""".formatted(token.get("folderPath").asText(), token.get("uploadToken").asText());
        JsonNode created = call("POST", "tenant-a/cases", HUNTER1, body, 200).get("item");
        assertEquals(caseId, created.get("id").asText());
        assertEquals("open", created.get("status").asText());
        assertEquals("u-hunter1", created.get("creator").get("id").asText());
        assertEquals("Tenant A", created.get("tenant").get("name").asText());

        // 3. Get by id: có upload token mới để update
        JsonNode got = call("GET", "tenant-a/cases/" + caseId, HUNTER1, null, 200).get("item");
        String uploadToken = got.get("uploadToken").asText();
        assertNotNull(uploadToken);

        // 4. Update: đổi tên + priority; status gửi lên bị bỏ qua
        String update = """
                {"item": {
                  "tenantId": "tenant-a", "name": "Renamed", "priority": "critical", "status": "verified",
                  "filenames": ["host07.zip", "host08.7z"], "folderPath": "ignored", "uploadToken": "%s"
                }}""".formatted(uploadToken);
        JsonNode updated = call("PUT", "tenant-a/cases/" + caseId, HUNTER1, update, 200).get("item");
        assertEquals("Renamed", updated.get("name").asText());
        assertEquals("open", updated.get("status").asText());
        assertEquals(2, updated.get("numberOfFiles").asInt());
        assertEquals(got.get("folderPath").asText(), updated.get("folderPath").asText());

        // 5. hunter2 không phải creator -> không xóa được
        JsonNode denied = call("DELETE", "tenant-a/cases/" + caseId, HUNTER2, null, 403);
        assertEquals("CannotDelete", denied.get("message").asText());

        // 6. Delete bởi creator, rồi Get -> 404
        call("DELETE", "tenant-a/cases/" + caseId, HUNTER1, null, 200);
        JsonNode notFound = call("GET", "tenant-a/cases/" + caseId, HUNTER1, null, 404);
        assertEquals(5, notFound.get("code").asInt());
    }

    @Test
    void hunterOnlySeesOwnOrAssignedCases() throws Exception {
        // seed: case 1 tạo bởi hunter1 giao cho hunter2; case 2 tạo bởi hunter2
        assertEquals(1, call("GET", "tenant-a/cases", HUNTER1, null, 200).get("count").asInt());
        assertEquals(2, call("GET", "tenant-a/cases", HUNTER2, null, 200).get("count").asInt());
        // admin (MASTER) xem mọi tenant
        assertEquals(3, call("GET", "MASTER/cases", ADMIN, null, 200).get("count").asInt());
    }

    @Test
    void filtersAndPaging() throws Exception {
        JsonNode page = call("GET", "MASTER/cases?status=open,verified&sort=name&limit=1", ADMIN, null, 200);
        assertEquals(2, page.get("count").asInt());
        assertEquals(1, page.get("items").size());
        assertEquals("Ransomware host-01", page.get("items").get(0).get("name").asText());
    }

    @Test
    void authAndPermissionErrors() throws Exception {
        call("GET", "tenant-a/cases", null, null, 401);
        call("GET", "tenant-b/cases", HUNTER1, null, 403);      // không thuộc tenant-b
        call("POST", "tenant-a/cases/@/upload-token", VIEWER, null, 403); // viewer chỉ được get
    }

    @Test
    void validationAndTokenErrors() throws Exception {
        JsonNode token = call("POST", "tenant-a/cases/@/upload-token", HUNTER1, null, 200);
        String noName = """
                {"item": {"tenantId": "tenant-a", "priority": "urgent", "folderPath": "%s", "uploadToken": "%s"}}"""
                .formatted(token.get("folderPath").asText(), token.get("uploadToken").asText());
        String msg = call("POST", "tenant-a/cases", HUNTER1, noName, 400).get("message").asText();
        assertTrue(msg.contains("name is required") && msg.contains("priority"), msg);

        String wrongFolder = """
                {"item": {"tenantId": "tenant-a", "name": "x", "folderPath": "/other", "uploadToken": "%s"}}"""
                .formatted(token.get("uploadToken").asText());
        assertEquals("UploadTokenInvalid", call("POST", "tenant-a/cases", HUNTER1, wrongFolder, 400).get("message").asText());

        String disabledAssignee = """
                {"item": {"tenantId": "tenant-a", "name": "x", "assigneeIds": ["u-disabled"],
                          "folderPath": "%s", "uploadToken": "%s"}}"""
                .formatted(token.get("folderPath").asText(), token.get("uploadToken").asText());
        assertEquals("AssigneeDisabled", call("POST", "tenant-a/cases", HUNTER1, disabledAssignee, 400).get("message").asText());

        call("POST", "tenant-a/cases", HUNTER1, "{not json", 400);
    }

    @Test
    void cannotDeleteInProgressCase() throws Exception {
        JsonNode err = call("DELETE", "tenant-a/cases/65f000000000000000000002", HUNTER2, null, 400);
        assertEquals("CannotDeleteInProgress", err.get("message").asText());
    }

    @Test
    void verifiedCaseCannotChangeExpectedEndpoints() throws Exception {
        String id = "65f000000000000000000003";
        String token = call("GET", "MASTER/cases/" + id, ADMIN, null, 200).get("item").get("uploadToken").asText();
        String body = """
                {"item": {"tenantId": "tenant-b", "name": "Webshell DMZ", "expectedEndpoints": 5,
                          "folderPath": "x", "uploadToken": "%s"}}""".formatted(token);
        assertEquals("CaseAlreadyVerified", call("PUT", "MASTER/cases/" + id, ADMIN, body, 400).get("message").asText());
    }

    private JsonNode call(String method, String path, String bearer, String body, int expectedStatus) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> res = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, res.statusCode(), method + " " + path + " -> " + res.body());
        return json.readTree(res.body());
    }
}
