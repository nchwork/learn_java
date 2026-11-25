package com.xhunting.coreapi.http;

import com.sun.net.httpserver.HttpExchange;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Router tối giản: khớp (method, path) với các pattern kiểu {@code /core-api/tenants/{tenantId}/cases/{id}}.
 *
 * JDK HttpServer chỉ có {@code createContext(prefix, handler)} theo TIỀN TỐ, không có path variable.
 * Trong Go, bản gốc dùng grpc-gateway sinh route từ {@code option (google.api.http)}; nếu viết tay thì
 * Go 1.22+ có {@code mux.HandleFunc("GET /cases/{id}", ...)} và {@code r.PathValue("id")}.
 * Ở đây tự đổi {@code {name}} thành regex named group {@code (?<name>[^/]+)}.
 * Spring MVC làm việc này cho bạn bằng {@code @GetMapping("/cases/{id}")} + {@code @PathVariable}.
 */
public class Router {

    /** Hàm xử lý một route. {@code @FunctionalInterface} = interface 1 method, dùng được với lambda. */
    @FunctionalInterface
    public interface Handler {
        Response handle(HttpExchange exchange, Matcher path) throws Exception;
    }

    /** Kết quả trả về: HTTP status + object sẽ được ghi thành JSON. */
    public record Response(int status, Object body) {
        public static Response ok(Object body) {
            return new Response(200, body);
        }
    }

    private record Route(String method, Pattern pattern, Handler handler) {
    }

    /** Kết quả khớp route. */
    public record Match(Handler handler, Matcher matcher) {
    }

    private final List<Route> routes = new ArrayList<>();

    public Router add(String method, String template, Handler handler) {
        String regex = template.replaceAll("\\{(\\w+)}", "(?<$1>[^/]+)");
        routes.add(new Route(method, Pattern.compile("^" + regex + "/?$"), handler));
        return this;
    }

    public Optional<Match> match(String method, String path) {
        for (Route r : routes) {
            Matcher m = r.pattern().matcher(path);
            if (r.method().equalsIgnoreCase(method) && m.matches()) {
                return Optional.of(new Match(r.handler(), m));
            }
        }
        return Optional.empty();
    }

    /** Có route nào khớp path (bất kể method)? Dùng để trả 405 thay vì 404. */
    public boolean pathExists(String path) {
        return routes.stream().anyMatch(r -> r.pattern().matcher(path).matches());
    }
}
