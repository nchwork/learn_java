package com.xhunting.coreapi.cases;

import com.xhunting.coreapi.auth.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller. Thay cho {@code CoreApiServer} + {@code Router} của bản Java core
 * và cho các dòng {@code option (google.api.http) = { get: "..." }} trong main.proto của Go.
 *
 * <ul>
 *   <li>{@code @GetMapping("/{id}")} + {@code @PathVariable}: Spring tự khớp URL và lấy biến
 *       (bản core tự viết regex named group).</li>
 *   <li>{@code @RequestBody}: Jackson tự đọc JSON thành object (bản core gọi {@code json.readValue}).</li>
 *   <li>{@code @Valid}: chạy Bean Validation trên {@code item}; lỗi thành
 *       {@code MethodArgumentNotValidException} và được {@code GlobalExceptionHandler} trả 400.</li>
 *   <li>Giá trị trả về được Jackson ghi thành JSON với status 200.</li>
 *   <li>{@code UserContext ctx}: do {@code UserContextArgumentResolver} điền.</li>
 * </ul>
 * Controller chỉ "dịch" HTTP sang lời gọi service, không chứa logic nghiệp vụ.
 */
@RestController
@RequestMapping("/core-api/tenants/{tenantId}/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    /** {@code message CaseRequest { Case item = 2; }} */
    public record CaseRequest(@Valid @NotNull(message = "item is required") Case item) {
    }

    /** {@code message CaseResponse { Case item = 2; }} */
    public record CaseResponse(Case item) {
    }

    @GetMapping
    public CaseService.Page getAll(UserContext ctx,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(name = "status", required = false) List<String> statuses, // "a,b" -> [a, b]
                                   @RequestParam(required = false) String sort,
                                   @RequestParam(defaultValue = "0") int offset,
                                   @RequestParam(defaultValue = "20") int limit) {
        return caseService.getAll(ctx, q, statuses, sort, offset, limit);
    }

    @GetMapping("/{id}")
    public CaseResponse getById(UserContext ctx, @PathVariable String id) {
        return new CaseResponse(caseService.getById(ctx, id));
    }

    @PostMapping
    public CaseResponse create(UserContext ctx, @Valid @RequestBody CaseRequest request) {
        return new CaseResponse(caseService.create(ctx, request.item()));
    }

    @PutMapping("/{id}")
    public CaseResponse update(UserContext ctx, @PathVariable String id, @Valid @RequestBody CaseRequest request) {
        return new CaseResponse(caseService.update(ctx, id, request.item()));
    }

    @DeleteMapping("/{id}")
    public CaseResponse delete(UserContext ctx, @PathVariable String id) {
        return new CaseResponse(caseService.delete(ctx, id));
    }

    /** Chỉ để chạy local (không có trong core-api), xem README. */
    @PostMapping("/@/upload-token")
    public Map<String, String> issueUploadToken(UserContext ctx) {
        return caseService.issueUploadToken(ctx);
    }
}
