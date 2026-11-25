package com.xhunting.coreapi.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhunting.coreapi.auth.AuthGuard;
import com.xhunting.coreapi.auth.PermissionGuard;
import com.xhunting.coreapi.auth.UserContext;
import com.xhunting.coreapi.cases.Case;
import com.xhunting.coreapi.cases.CaseFilter;
import com.xhunting.coreapi.cases.CaseRepository;
import com.xhunting.coreapi.cases.UploadToken;
import com.xhunting.coreapi.common.AesUtil;
import com.xhunting.coreapi.common.ApiException;
import com.xhunting.coreapi.common.Constants;
import com.xhunting.coreapi.common.Validator;
import com.xhunting.coreapi.eventlogs.EventLogBuilder;
import com.xhunting.coreapi.eventlogs.EventLogService;
import com.xhunting.coreapi.tenants.TenantInfo;
import com.xhunting.coreapi.tenants.TenantService;
import com.xhunting.coreapi.users.User;
import com.xhunting.coreapi.users.UserInfo;
import com.xhunting.coreapi.users.UserService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Port nhóm Case CRUD của {@code core-api/internal/.../grpc/case_service_handler.go}:
 * Create, GetAll, GetById, Update, Delete. Logic nghiệp vụ giữ sát bản Go; phần phụ thuộc hệ thống
 * khác (fileService, executionRepository, endpointService, Pusher, message queue) được lược bỏ.
 *
 * Khác biệt Go -> Java đáng chú ý ở file này:
 * <ul>
 *   <li><b>Dependency injection:</b> Go truyền 16 dependency vào {@code NewCaseServiceHandler(...)}.
 *       Java cũng truyền qua constructor (constructor injection). Spring chỉ tự động hóa việc
 *       gọi constructor này, ý tưởng giống hệt.</li>
 *   <li><b>{@code defer}:</b> Go dùng {@code defer func(){ eventLogBuilder.SetError(err); push() }()}.
 *       Java không có defer, dùng {@code try { ... } catch { ghi lỗi; throw } finally { push }}.</li>
 *   <li><b>Lỗi:</b> mỗi bước Go có {@code if err != nil { return nil, errorx.Wrap(...) }}. Java ném
 *       {@link ApiException} và để nó bay thẳng lên tầng HTTP, code chính đọc liền mạch hơn.</li>
 *   <li><b>Không tìm thấy:</b> Go kiểm tra {@code if exist == nil}. Java:
 *       {@code repository.findOne(...).orElseThrow(ApiException::caseNotFound)}.</li>
 * </ul>
 */
public class CaseServiceHandler {

    private static final String RESOURCE = "cases";
    private static final Duration UPLOAD_TOKEN_TTL = Duration.ofHours(24);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthGuard authGuard;
    private final PermissionGuard permissionGuard;
    private final CaseRepository caseRepository;
    private final UserService userService;
    private final TenantService tenantService;
    private final EventLogService logPusher;
    private final ObjectMapper json;
    private final String encryptKey;

    public CaseServiceHandler(AuthGuard authGuard, PermissionGuard permissionGuard, CaseRepository caseRepository,
                              UserService userService, TenantService tenantService, EventLogService logPusher,
                              ObjectMapper json, String encryptKey) {
        this.authGuard = authGuard;
        this.permissionGuard = permissionGuard;
        this.caseRepository = caseRepository;
        this.userService = userService;
        this.tenantService = tenantService;
        this.logPusher = logPusher;
        this.json = json;
        this.encryptKey = encryptKey;
    }

    // ------------------------------------------------------------------ Create

    /** {@code POST /core-api/tenants/{tenantId}/cases}, body {@code {"item": {...}}}. */
    public Case create(RequestContext reqCtx, Case item) {
        UserContext userContext = getUserContext(reqCtx, Constants.ACTION_CREATE);
        if (item == null) {
            throw ApiException.invalidArgument("item is required");
        }

        EventLogBuilder eventLog = new EventLogBuilder(Constants.ACTION_CREATE, "case");
        try {
            item.setStatus(Constants.CASE_STATUS_OPEN);
            item.setNumberOfFiles(item.getFilenames().size());
            Validator.validateCase(item);

            checkValidAssignees(item.getAssigneeIds(), item.getTenantId());
            checkValidTenant(userContext, item.getTenantId(), Constants.ACTION_CREATE);

            UploadToken token = decryptUploadToken(item.getUploadToken());
            if (!Objects.equals(token.folderPath(), item.getFolderPath()) || isBlank(token.caseId())) {
                throw ApiException.uploadTokenInvalid();
            }
            // Go: primitive.ObjectIDFromHex(token.CaseId) -> id phải là 24 ký tự hex
            if (!token.caseId().matches("[0-9a-f]{24}")) {
                throw ApiException.invalidArgument("invalid case id in upload token");
            }
            item.setId(token.caseId());

            long now = Instant.now().toEpochMilli();
            item.setCreatorId(userContext.userId());
            item.setCreatedTime(now);
            item.setUpdatedTime(now);
            item.setUploadToken(null); // không lưu token

            Case created;
            try {
                created = caseRepository.create(item);
            } catch (CaseRepository.DuplicateCaseException e) {
                throw ApiException.invalidArgument("CaseExisted: id");
            }

            eventLog.targetId(created.getId()).succeeded();
            return decorateItem(created);
        } catch (RuntimeException e) {
            eventLog.error(e);
            throw e;
        } finally {
            logPusher.create(eventLog.build(userContext.userId())); // ~ defer
        }
    }

    // ------------------------------------------------------------------ GetAll

    /** {@code GET /core-api/tenants/{tenantId}/cases}. Trả về danh sách + tổng số (count). */
    public Page getAll(RequestContext reqCtx, FindParams params) {
        UserContext userContext = getUserContext(reqCtx, Constants.ACTION_GET);

        CaseFilter filter = CaseFilter.forTenant(queryTenant(userContext))
                .notDeleted()
                .withNameContains(params.q())
                .withStatuses(params.statuses())
                .page(params.sort(), params.offset(), params.limit());
        filter = modifyFiltersIfNeed(userContext, filter, Constants.ACTION_GET);

        List<Case> items = caseRepository.findMany(filter).stream().map(this::decorateItem).toList();
        long count = caseRepository.count(filter);
        return new Page(items, count);
    }

    /** {@code message CaseFindResponse { repeated Case items; int64 count; }} */
    public record Page(List<Case> items, long count) {
    }

    // ------------------------------------------------------------------ GetById

    /** {@code GET /core-api/tenants/{tenantId}/cases/{id}}. */
    public Case getById(RequestContext reqCtx) {
        UserContext userContext = getUserContext(reqCtx, Constants.ACTION_GET);
        Case item = getCaseById(userContext, reqCtx.id(), Constants.ACTION_GET);
        item.setNumberOfFiles(item.getFilenames().size());
        item = decorateItem(item);

        // Người có quyền create/update nhận kèm upload token mới (hạn 24h) để upload thêm file / sửa case.
        if (permissionGuard.hasPermission(userContext, Constants.ACTION_CREATE)
                || permissionGuard.hasPermission(userContext, Constants.ACTION_UPDATE)) {
            long expiredAt = Instant.now().plus(UPLOAD_TOKEN_TTL).getEpochSecond();
            item.setUploadToken(encryptUploadToken(new UploadToken(item.getFolderPath(), item.getId(), expiredAt)));
        }
        return item;
    }

    // ------------------------------------------------------------------ Update

    /** {@code PUT /core-api/tenants/{tenantId}/cases/{id}}, body {@code {"item": {...}}}. */
    public Case update(RequestContext reqCtx, Case item) {
        UserContext userContext = getUserContext(reqCtx, Constants.ACTION_UPDATE);
        if (item == null) {
            throw ApiException.invalidArgument("item is required");
        }

        EventLogBuilder eventLog = new EventLogBuilder(Constants.ACTION_UPDATE, "case").targetId(reqCtx.id());
        try {
            Validator.validateCase(item);
            checkValidAssignees(item.getAssigneeIds(), item.getTenantId());

            CaseFilter filter = modifyFiltersIfNeed(userContext,
                    CaseFilter.byId(queryTenant(userContext), reqCtx.id()).notDeleted(),
                    Constants.ACTION_UPDATE);
            Case exist = caseRepository.findOne(filter).orElseThrow(ApiException::caseNotFound);

            UploadToken token = decryptUploadToken(item.getUploadToken());
            if (!Objects.equals(token.folderPath(), exist.getFolderPath())
                    || !Objects.equals(token.caseId(), exist.getId())) {
                throw ApiException.uploadTokenInvalid();
            }

            if (!Objects.equals(item.getTenantId(), exist.getTenantId())) {
                checkValidTenant(userContext, item.getTenantId(), Constants.ACTION_UPDATE);
            }
            if (!item.getFilenames().equals(exist.getFilenames())) { // ~ slices.Equal
                Validator.checkValidFiles(item.getFilenames());
            }
            if (Constants.CASE_STATUS_VERIFIED.equals(exist.getStatus()) && item.getExpectedEndpoints() != 0) {
                throw ApiException.invalidArgument("CaseAlreadyVerified");
            }

            // UpdateOptions{UpdateTenantId: true, ExcludedProperties: ["folderPath", "status"]}
            // -> chép các field được phép sửa từ item sang exist; giữ nguyên id, folderPath, status, creator, createdTime.
            exist.setTenantId(item.getTenantId());
            exist.setName(item.getName());
            exist.setPriority(item.getPriority());
            exist.setAssigneeIds(item.getAssigneeIds());
            exist.setExpectedEndpoints(item.getExpectedEndpoints());
            exist.setFilenames(item.getFilenames());
            exist.setNumberOfFiles(item.getFilenames().size());
            exist.setDescription(item.getDescription());
            exist.setUpdatedTime(Instant.now().toEpochMilli());
            caseRepository.update(exist);

            // Đọc lại theo tenant MỚI (Go: newCtx := userContext.Clone(); newCtx.SetRequestTenantId(item.TenantId))
            UserContext newCtx = userContext.withRequestTenantId(item.getTenantId());
            Case updated = caseRepository.findOne(CaseFilter.byId(queryTenant(newCtx), reqCtx.id()))
                    .orElseThrow(ApiException::caseNotFound);

            eventLog.succeeded();
            return decorateItem(updated);
        } catch (RuntimeException e) {
            eventLog.error(e);
            throw e;
        } finally {
            logPusher.create(eventLog.build(userContext.userId()));
        }
    }

    // ------------------------------------------------------------------ Delete

    /** {@code DELETE /core-api/tenants/{tenantId}/cases/{id}}: xóa mềm (isDeleted = true). */
    public Case delete(RequestContext reqCtx) {
        UserContext userContext = getUserContext(reqCtx, Constants.ACTION_DELETE);

        CaseFilter filter = CaseFilter.byId(queryTenant(userContext), reqCtx.id()).notDeleted();
        Case item = caseRepository.findOne(filter).orElseThrow(ApiException::caseNotFound);

        if (userContext.isHunter() && !userContext.userId().equals(item.getCreatorId())) {
            throw ApiException.permissionDenied("CannotDelete");
        }
        if (Constants.CASE_STATUS_IN_PROGRESS.equals(item.getStatus())) {
            throw ApiException.invalidArgument("CannotDeleteInProgress");
        }

        item.setDeleted(true);
        item.setUpdatedTime(Instant.now().toEpochMilli());
        caseRepository.update(item);
        return item;
    }

    // ------------------------------------------------------------------ Upload token (demo)

    /**
     * KHÔNG có trong core-api: ở hệ thống thật, upload token do luồng upload file cấp. Endpoint này giúp
     * chạy thử local: cấp sẵn một case id mới + folderPath + token để gọi Create.
     * {@code POST /core-api/tenants/{tenantId}/cases/@/upload-token}
     */
    public Map<String, String> issueUploadToken(RequestContext reqCtx) {
        UserContext userContext = getUserContext(reqCtx, Constants.ACTION_CREATE);
        String caseId = newObjectIdHex();
        String folderPath = "/" + userContext.requestTenantId() + "/" + caseId;
        long expiredAt = Instant.now().plus(UPLOAD_TOKEN_TTL).getEpochSecond();
        String token = encryptUploadToken(new UploadToken(folderPath, caseId, expiredAt));
        return Map.of("caseId", caseId, "folderPath", folderPath, "uploadToken", token);
    }

    // ------------------------------------------------------------------ helpers (chữ thường trong Go)

    /** {@code getUserContext}: xác thực + kiểm tra quyền trên resource "cases". */
    private UserContext getUserContext(RequestContext reqCtx, String action) {
        UserContext userContext = authGuard.canActivate(reqCtx.authorization(), reqCtx.tenantId());
        permissionGuard.canActivate(userContext, RESOURCE, action);
        return userContext;
    }

    /** {@code getCaseById}. */
    private Case getCaseById(UserContext userContext, String caseId, String action) {
        CaseFilter filter = modifyFiltersIfNeed(userContext,
                CaseFilter.byId(queryTenant(userContext), caseId).notDeleted(), action);
        return caseRepository.findOne(filter).orElseThrow(ApiException::caseNotFound);
    }

    /** Hunter chỉ thấy/sửa case mình tạo hoặc được giao; chỉ xóa case mình tạo. */
    private CaseFilter modifyFiltersIfNeed(UserContext userContext, CaseFilter filter, String action) {
        if (!userContext.isHunter()) {
            return filter;
        }
        return switch (action) {
            case Constants.ACTION_DELETE -> filter.withCreator(userContext.userId());
            case Constants.ACTION_GET, Constants.ACTION_UPDATE -> filter.visibleTo(userContext.userId());
            default -> filter;
        };
    }

    /** {@code if userContext.RequestTenantId == "MASTER" { userContext.RequestTenantId = "ANY" }} */
    private static String queryTenant(UserContext userContext) {
        String t = userContext.requestTenantId();
        return Constants.TENANT_MASTER.equals(t) ? Constants.TENANT_ANY : t;
    }

    /** {@code checkValidAssignees}: assignee phải tồn tại, không bị khóa, và có quyền update case. */
    private void checkValidAssignees(List<String> assigneeIds, String tenantId) {
        if (assigneeIds.isEmpty()) {
            return;
        }
        List<User> assignees = userService.getUsersByIds(assigneeIds);
        if (assignees.size() != new HashSet<>(assigneeIds).size()) {
            throw ApiException.invalidArgument("AssigneeNotFound");
        }
        for (User assignee : assignees) {
            if (assignee.disabled()) {
                throw ApiException.invalidArgument("AssigneeDisabled");
            }
            // Bản Go dựng context chỉ có UserInfo; ở đây gắn thêm tenant của case để kiểm tra đúng tenant.
            if (!permissionGuard.hasPermission(new UserContext(assignee, tenantId), Constants.ACTION_UPDATE)) {
                throw ApiException.invalidArgument("AssigneeCannotUpdate");
            }
        }
    }

    /** {@code checkValidTenant}: tenant phải tồn tại và user có quyền action trên tenant đó. */
    private void checkValidTenant(UserContext userContext, String tenantId, String action) {
        if (tenantService.getTenantsByIds(List.of(tenantId)).isEmpty()) {
            throw ApiException.invalidArgument("TenantNotFound");
        }
        if (!permissionGuard.hasPermission(userContext.withRequestTenantId(tenantId), action)) {
            throw ApiException.invalidArgument("InvalidTenant");
        }
    }

    /** {@code decorateItem}: gắn thông tin tenant, assignees, creator cho response. */
    private Case decorateItem(Case item) {
        Set<String> userIds = new HashSet<>(item.getAssigneeIds());
        if (item.getCreatorId() != null) {
            userIds.add(item.getCreatorId());
        }
        // Collectors.toMap ~ vòng for dựng map[string]*UserInfo trong Go
        Map<String, UserInfo> users = userService.getUsersByIds(userIds).stream()
                .collect(Collectors.toMap(User::id, User::toInfo));

        item.setAssignees(item.getAssigneeIds().stream().map(users::get).filter(Objects::nonNull).toList());
        item.setCreator(users.get(item.getCreatorId()));
        item.setTenant(tenantService.getTenantsByIds(List.of(item.getTenantId())).stream()
                .findFirst().orElse(null));
        return item;
    }

    /** {@code decryptUploadToken}: base64 -> AES -> JSON. */
    private UploadToken decryptUploadToken(String encrypted) {
        try {
            byte[] raw = Base64.getDecoder().decode(encrypted);
            return json.readValue(AesUtil.decrypt(raw, encryptKey), UploadToken.class);
        } catch (IllegalArgumentException | java.io.IOException e) { // base64 sai hoặc JSON sai
            throw ApiException.uploadTokenInvalid();
        }
    }

    /** {@code encryptUploadToken}: JSON -> AES -> base64. */
    private String encryptUploadToken(UploadToken token) {
        try {
            return Base64.getEncoder().encodeToString(AesUtil.encrypt(json.writeValueAsBytes(token), encryptKey));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sinh id kiểu Mongo ObjectID: 12 byte = 24 ký tự hex. */
    private static String newObjectIdHex() {
        byte[] b = new byte[12];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

}
