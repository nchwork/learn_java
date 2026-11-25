package com.xhunting.coreapi.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xhunting.coreapi.common.ApiException;
import com.xhunting.coreapi.handler.CaseServiceHandler;
import com.xhunting.coreapi.handler.FindParams;
import com.xhunting.coreapi.handler.RequestContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

/**
 * Server HTTP/JSON dùng {@link HttpServer} có sẵn trong JDK (module jdk.httpserver), đóng vai
 * grpc-gateway + gRPC server của bản Go.
 *
 * Khác biệt Go -> Java:
 * <ul>
 *   <li>Go: {@code http.ListenAndServe(addr, mux)}, mỗi request một goroutine tự động.
 *       Java: {@code HttpServer.create(...)} + phải TỰ chọn executor. Ta dùng
 *       {@code Executors.newVirtualThreadPerTaskExecutor()} (Java 21): mỗi request một virtual thread,
 *       rẻ như goroutine. Không set executor thì HttpServer xử lý tuần tự trên 1 thread!</li>
 *   <li>Go {@code http.Handler} có {@code (w ResponseWriter, r *Request)}. Java gộp cả hai vào
 *       {@link HttpExchange}: phải gọi {@code sendResponseHeaders} TRƯỚC khi ghi body.</li>
 *   <li>Lỗi được ném ra từ handler và bắt một chỗ duy nhất trong {@link #dispatch}, giống middleware
 *       recover/error-handler trong Go hoặc {@code @ControllerAdvice} trong Spring.</li>
 * </ul>
 */
public class CoreApiServer {

    private static final System.Logger LOG = System.getLogger("http");
    private static final String BASE = "/core-api/tenants/{tenantId}";

    private final CaseServiceHandler handler;
    private final ObjectMapper json;
    private final Router router = new Router();
    private HttpServer server;
    private ExecutorService executor;

    public CoreApiServer(CaseServiceHandler handler, ObjectMapper json) {
        this.handler = handler;
        this.json = json;
        registerRoutes();
    }

    /**
     * Bảng route lấy từ {@code option (google.api.http)} trong main.proto.
     * Lưu ý thứ tự: "/cases/@/upload-token" phải đăng ký TRƯỚC "/cases/{id}" ... ở đây khác method nên không đụng nhau.
     */
    private void registerRoutes() {
        router
                .add("GET", BASE + "/cases", (ex, p) -> Router.Response.ok(
                        handler.getAll(ctx(ex, p), findParams(ex))))
                .add("GET", BASE + "/cases/{id}", (ex, p) -> Router.Response.ok(
                        Map.of("item", handler.getById(ctx(ex, p)))))
                .add("POST", BASE + "/cases/@/upload-token", (ex, p) -> Router.Response.ok(
                        handler.issueUploadToken(ctx(ex, p))))
                .add("POST", BASE + "/cases", (ex, p) -> Router.Response.ok(
                        Map.of("item", handler.create(ctx(ex, p), readBody(ex, CaseRequest.class).item()))))
                .add("PUT", BASE + "/cases/{id}", (ex, p) -> Router.Response.ok(
                        Map.of("item", handler.update(ctx(ex, p), readBody(ex, CaseRequest.class).item()))))
                .add("DELETE", BASE + "/cases/{id}", (ex, p) -> Router.Response.ok(
                        Map.of("item", handler.delete(ctx(ex, p)))))
                .add("GET", "/healthz", (ex, p) -> Router.Response.ok(Map.of("status", "ok")));
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::dispatch); // method reference ~ truyền hàm như giá trị trong Go
        server.start();
        LOG.log(System.Logger.Level.INFO, "core-api listening on http://localhost:{0}", String.valueOf(port()));
    }

    /** Port thực tế (khi start(0) hệ điều hành tự chọn port trống, tiện cho test). */
    public int port() {
        return server.getAddress().getPort();
    }

    /** Graceful shutdown ~ {@code srv.Shutdown(ctx)} trong Go: chờ tối đa 2 giây cho request đang chạy. */
    public void stop() {
        if (server != null) {
            server.stop(2);
            executor.close(); // ExecutorService là AutoCloseable từ Java 19
        }
    }

    private void dispatch(HttpExchange ex) {
        long start = System.nanoTime();
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        int status;
        try {
            var match = router.match(method, path);
            if (match.isEmpty()) {
                status = router.pathExists(path) ? 405 : 404;
                writeJson(ex, status, error(status == 405 ? 12 : 5, status == 405 ? "Method Not Allowed" : "Not Found"));
            } else {
                Router.Response res = match.get().handler().handle(ex, match.get().matcher());
                status = res.status();
                writeJson(ex, status, res.body());
            }
        } catch (ApiException e) {
            status = e.code().httpStatus;
            writeJsonQuietly(ex, status, error(e.code().grpcCode, e.getMessage()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            status = 400;
            writeJsonQuietly(ex, status, error(3, "invalid JSON body: " + e.getOriginalMessage()));
        } catch (Exception e) {
            // Mọi lỗi bất ngờ -> 500, log stack trace (Go: middleware recover + log)
            LOG.log(System.Logger.Level.ERROR, "unhandled error on " + method + " " + path, e);
            status = 500;
            writeJsonQuietly(ex, status, error(13, "internal error"));
        } finally {
            ex.close();
        }
        LOG.log(System.Logger.Level.INFO, "{0} {1} -> {2} ({3} ms)", method, path, String.valueOf(status),
                String.valueOf((System.nanoTime() - start) / 1_000_000));
    }

    // --------------------------------------------------------------- helpers

    private static RequestContext ctx(HttpExchange ex, Matcher p) {
        String id = p.pattern().pattern().contains("?<id>") ? p.group("id") : null;
        return new RequestContext(ex.getRequestHeaders().getFirst("Authorization"), p.group("tenantId"), id);
    }

    private <T> T readBody(HttpExchange ex, Class<T> type) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] body = in.readAllBytes();
            if (body.length == 0) {
                throw ApiException.invalidArgument("request body is required");
            }
            return json.readValue(body, type);
        }
    }

    /** ?q=..&status=open,pending&sort=-createdTime&offset=0&limit=20 */
    private static FindParams findParams(HttpExchange ex) {
        Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
        List<String> statuses = q.containsKey("status")
                ? Arrays.stream(q.get("status").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();
        return new FindParams(q.get("q"), statuses, q.get("sort"),
                parseInt(q.get("offset"), 0), parseInt(q.get("limit"), FindParams.DEFAULT_LIMIT));
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(k, v);
        }
        return result;
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) { // Go: strconv.Atoi trả err; Java ném exception
            throw ApiException.invalidArgument("not a number: " + s);
        }
    }

    /** Body lỗi giống grpc-gateway: {"code": 5, "message": "CaseNotFound", "details": []} */
    private static Map<String, Object> error(int grpcCode, String message) {
        return Map.of("code", grpcCode, "message", message, "details", List.of());
    }

    private void writeJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length); // phải gọi trước khi ghi body
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void writeJsonQuietly(HttpExchange ex, int status, Object body) {
        try {
            writeJson(ex, status, body);
        } catch (IOException ignored) {
            // client đã ngắt kết nối hoặc header đã gửi, không làm gì thêm được
        }
    }
}
