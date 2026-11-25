package com.xhunting.coreapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhunting.coreapi.cases.Case;
import com.xhunting.coreapi.config.DemoDataSeeder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cùng 7 kịch bản với {@code CoreApiServerTest} của bản Java core, chạy trên app Spring thật
 * (Tomcat trên port ngẫu nhiên) và MongoDB thật trong Docker.
 *
 * {@code @SpringBootTest(webEnvironment = RANDOM_PORT)}: khởi động toàn bộ app như production.
 * {@code @Testcontainers(disabledWithoutDocker = true)}: máy không có Docker thì test bị bỏ qua thay vì lỗi.
 * Trong test được dùng {@code @Autowired} lên field (code chính nên dùng constructor injection).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class CaseApiIntegrationTest {

    private static final String ADMIN = "admin-token";
    private static final String HUNTER1 = "hunter1-token";
    private static final String HUNTER2 = "hunter2-token";
    private static final String VIEWER = "viewer-token";

    @LocalServerPort
    int port;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    DemoDataSeeder seeder;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    /** Mỗi test bắt đầu từ đúng 3 case mẫu. */
    @BeforeEach
    void resetData() {
        mongo.getCollection(mongo.getCollectionName(Case.class)).deleteMany(new Document());
        seeder.run();
    }

    @Test
    void fullCrudFlowAsHunter() throws Exception {
        JsonNode token = call("POST", "tenant-a/cases/@/upload-token", HUNTER1, null, 200);
        String caseId = token.get("caseId").asText();

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

        // Document trong Mongo dùng tên field như bản Go
        Document raw = mongo.getCollection("cases").find(new Document("createdByUserId", "u-hunter1")
                .append("name", "Lateral movement host-07")).first();
        assertNotNull(raw);
        assertEquals(false, raw.getBoolean("isDeleted"));

        JsonNode got = call("GET", "tenant-a/cases/" + caseId, HUNTER1, null, 200).get("item");
        String uploadToken = got.get("uploadToken").asText();

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

        assertEquals("CannotDelete", call("DELETE", "tenant-a/cases/" + caseId, HUNTER2, null, 403).get("message").asText());

        call("DELETE", "tenant-a/cases/" + caseId, HUNTER1, null, 200);
        assertEquals(5, call("GET", "tenant-a/cases/" + caseId, HUNTER1, null, 404).get("code").asInt());
    }

    @Test
    void hunterOnlySeesOwnOrAssignedCases() throws Exception {
        assertEquals(1, call("GET", "tenant-a/cases", HUNTER1, null, 200).get("count").asInt());
        assertEquals(2, call("GET", "tenant-a/cases", HUNTER2, null, 200).get("count").asInt());
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
        call("GET", "tenant-b/cases", HUNTER1, null, 403);
        call("POST", "tenant-a/cases/@/upload-token", VIEWER, null, 403);
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
        assertEquals("CannotDeleteInProgress",
                call("DELETE", "tenant-a/cases/65f000000000000000000002", HUNTER2, null, 400).get("message").asText());
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
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/core-api/tenants/" + path))
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
