package com.xhunting.coreapi.cases;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhunting.coreapi.auth.PermissionGuard;
import com.xhunting.coreapi.auth.UserContext;
import com.xhunting.coreapi.common.AesUtil;
import com.xhunting.coreapi.common.ApiException;
import com.xhunting.coreapi.common.Constants;
import com.xhunting.coreapi.config.AppProperties;
import com.xhunting.coreapi.eventlogs.EventLogBuilder;
import com.xhunting.coreapi.eventlogs.EventLogService;
import com.xhunting.coreapi.tenants.TenantService;
import com.xhunting.coreapi.users.User;
import com.xhunting.coreapi.users.UserInfo;
import com.xhunting.coreapi.users.UserService;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Logic nghiệp vụ Case CRUD, port từ {@code case_service_handler.go}. Gần như giống hệt
 * {@code CaseServiceHandler} của bản Java core; khác biệt nằm ở những thứ Spring làm thay:
 *
 * <ul>
 *   <li><b>{@code @Service} + constructor injection</b>: không còn chỗ nào {@code new CaseService(...)};
 *       Spring đọc constructor và tự truyền các bean phù hợp.</li>
 *   <li><b>Xác thực</b> đã xong trước khi vào đây ({@code UserContextArgumentResolver}); service chỉ kiểm
 *       tra quyền theo action, giống {@code permissionGuard.CanActivate} trong Go.</li>
 *   <li><b>Validate</b> đã chạy ở controller ({@code @Valid}), nên không còn {@code Validator.validateCase}.</li>
 *   <li><b>Repository</b>: {@code insert} của Spring Data ném {@link DuplicateKeyException} khi trùng _id
 *       ({@code save} thì ghi đè, nên Create phải dùng insert).</li>
 * </ul>
 */
@Service
public class CaseService {

    private static final String RESOURCE = "cases";
    private static final Duration UPLOAD_TOKEN_TTL = Duration.ofHours(24);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PermissionGuard permissionGuard;
    private final CaseRepository caseRepository;
    private final UserService userService;
    private final TenantService tenantService;
    private final EventLogService logPusher;
    private final ObjectMapper json;           // ObjectMapper do Spring Boot cấu hình sẵn
    private final String encryptKey;

    public CaseService(PermissionGuard permissionGuard, CaseRepository caseRepository, UserService userService,
                       TenantService tenantService, EventLogService logPusher, ObjectMapper json,
                       AppProperties props) {
        this.permissionGuard = permissionGuard;
        this.caseRepository = caseRepository;
        this.userService = userService;
        this.tenantService = tenantService;
        this.logPusher = logPusher;
        this.json = json;
        this.encryptKey = props.encryptKey();
    }

    // ------------------------------------------------------------------ Create

    public Case create(UserContext userContext, Case item) {
        permissionGuard.canActivate(userContext, RESOURCE, Constants.ACTION_CREATE);

        EventLogBuilder eventLog = new EventLogBuilder(Constants.ACTION_CREATE, "case");
        try {
            item.setStatus(Constants.CASE_STATUS_OPEN);
            item.setNumberOfFiles(item.getFilenames().size());

            checkValidAssignees(item.getAssigneeIds(), item.getTenantId());
            checkValidTenant(userContext, item.getTenantId(), Constants.ACTION_CREATE);

            UploadToken token = decryptUploadToken(item.getUploadToken());
            if (!Objects.equals(token.folderPath(), item.getFolderPath()) || isBlank(token.caseId())) {
                throw ApiException.uploadTokenInvalid();
            }
            if (!token.caseId().matches("[0-9a-f]{24}")) {
                throw ApiException.invalidArgument("invalid case id in upload token");
            }
            item.setId(token.caseId());

            long now = Instant.now().toEpochMilli();
            item.setCreatorId(userContext.userId());
            item.setCreatedTime(now);
            item.setUpdatedTime(now);
            item.setUploadToken(null);

            Case created;
            try {
                created = caseRepository.insert(item);
            } catch (DuplicateKeyException e) {
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

    /** {@code message CaseFindResponse { repeated Case items; int64 count; }} */
    public record Page(List<Case> items, long count) {
    }

    public Page getAll(UserContext userContext, String q, List<String> statuses, String sort, int offset, int limit) {
        permissionGuard.canActivate(userContext, RESOURCE, Constants.ACTION_GET);

        CaseFilter filter = CaseFilter.forTenant(queryTenant(userContext))
                .notDeleted()
                .withNameContains(q)
                .withStatuses(statuses)
                .page(sort, Math.max(0, offset), limit <= 0 ? 20 : Math.min(limit, 100));
        filter = modifyFiltersIfNeed(userContext, filter, Constants.ACTION_GET);

        List<Case> items = caseRepository.findMany(filter).stream().map(this::decorateItem).toList();
        return new Page(items, caseRepository.count(filter));
    }

    // ------------------------------------------------------------------ GetById

    public Case getById(UserContext userContext, String id) {
        permissionGuard.canActivate(userContext, RESOURCE, Constants.ACTION_GET);
        Case item = getCaseById(userContext, id, Constants.ACTION_GET);
        item.setNumberOfFiles(item.getFilenames().size());
        item = decorateItem(item);

        if (permissionGuard.hasPermission(userContext, Constants.ACTION_CREATE)
                || permissionGuard.hasPermission(userContext, Constants.ACTION_UPDATE)) {
            long expiredAt = Instant.now().plus(UPLOAD_TOKEN_TTL).getEpochSecond();
            item.setUploadToken(encryptUploadToken(new UploadToken(item.getFolderPath(), item.getId(), expiredAt)));
        }
        return item;
    }

    // ------------------------------------------------------------------ Update

    public Case update(UserContext userContext, String id, Case item) {
        permissionGuard.canActivate(userContext, RESOURCE, Constants.ACTION_UPDATE);

        EventLogBuilder eventLog = new EventLogBuilder(Constants.ACTION_UPDATE, "case").targetId(id);
        try {
            checkValidAssignees(item.getAssigneeIds(), item.getTenantId());

            CaseFilter filter = modifyFiltersIfNeed(userContext,
                    CaseFilter.byId(queryTenant(userContext), id).notDeleted(), Constants.ACTION_UPDATE);
            Case exist = caseRepository.findOne(filter).orElseThrow(ApiException::caseNotFound);

            UploadToken token = decryptUploadToken(item.getUploadToken());
            if (!Objects.equals(token.folderPath(), exist.getFolderPath())
                    || !Objects.equals(token.caseId(), exist.getId())) {
                throw ApiException.uploadTokenInvalid();
            }
            if (!Objects.equals(item.getTenantId(), exist.getTenantId())) {
                checkValidTenant(userContext, item.getTenantId(), Constants.ACTION_UPDATE);
            }
            if (!item.getFilenames().equals(exist.getFilenames())) {
                checkValidFiles(item.getFilenames());
            }
            if (Constants.CASE_STATUS_VERIFIED.equals(exist.getStatus()) && item.getExpectedEndpoints() != 0) {
                throw ApiException.invalidArgument("CaseAlreadyVerified");
            }

            // Không sửa id, folderPath, status, creator, createdTime.
            exist.setTenantId(item.getTenantId());
            exist.setName(item.getName());
            exist.setPriority(item.getPriority());
            exist.setAssigneeIds(item.getAssigneeIds());
            exist.setExpectedEndpoints(item.getExpectedEndpoints());
            exist.setFilenames(item.getFilenames());
            exist.setNumberOfFiles(item.getFilenames().size());
            exist.setDescription(item.getDescription());
            exist.setUpdatedTime(Instant.now().toEpochMilli());
            Case updated = caseRepository.save(exist); // save = ghi đè document có cùng _id

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

    public Case delete(UserContext userContext, String id) {
        permissionGuard.canActivate(userContext, RESOURCE, Constants.ACTION_DELETE);

        Case item = caseRepository.findOne(CaseFilter.byId(queryTenant(userContext), id).notDeleted())
                .orElseThrow(ApiException::caseNotFound);
        if (userContext.isHunter() && !userContext.userId().equals(item.getCreatorId())) {
            throw ApiException.permissionDenied("CannotDelete");
        }
        if (Constants.CASE_STATUS_IN_PROGRESS.equals(item.getStatus())) {
            throw ApiException.invalidArgument("CannotDeleteInProgress");
        }

        item.setDeleted(true);
        item.setUpdatedTime(Instant.now().toEpochMilli());
        return caseRepository.save(item);
    }

    // ------------------------------------------------------------------ Upload token (demo)

    /** Không có trong core-api, chỉ để chạy local (xem README). */
    public Map<String, String> issueUploadToken(UserContext userContext) {
        permissionGuard.canActivate(userContext, RESOURCE, Constants.ACTION_CREATE);
        byte[] b = new byte[12];
        RANDOM.nextBytes(b);
        String caseId = HexFormat.of().formatHex(b);
        String folderPath = "/" + userContext.requestTenantId() + "/" + caseId;
        long expiredAt = Instant.now().plus(UPLOAD_TOKEN_TTL).getEpochSecond();
        return Map.of("caseId", caseId, "folderPath", folderPath,
                "uploadToken", encryptUploadToken(new UploadToken(folderPath, caseId, expiredAt)));
    }

    // ------------------------------------------------------------------ helpers

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

    private static String queryTenant(UserContext userContext) {
        String t = userContext.requestTenantId();
        return Constants.TENANT_MASTER.equals(t) ? Constants.TENANT_ANY : t;
    }

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
            if (!permissionGuard.hasPermission(new UserContext(assignee, tenantId), Constants.ACTION_UPDATE)) {
                throw ApiException.invalidArgument("AssigneeCannotUpdate");
            }
        }
    }

    private void checkValidTenant(UserContext userContext, String tenantId, String action) {
        if (tenantService.getTenantsByIds(List.of(tenantId)).isEmpty()) {
            throw ApiException.invalidArgument("TenantNotFound");
        }
        if (!permissionGuard.hasPermission(userContext.withRequestTenantId(tenantId), action)) {
            throw ApiException.invalidArgument("InvalidTenant");
        }
    }

    private static void checkValidFiles(List<String> files) {
        for (String file : files) {
            String lower = file.toLowerCase();
            if (Constants.COMPRESSED_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
                throw ApiException.invalidArgument("InvalidFileExtension: " + file);
            }
        }
    }

    private Case decorateItem(Case item) {
        Set<String> userIds = new HashSet<>(item.getAssigneeIds());
        if (item.getCreatorId() != null) {
            userIds.add(item.getCreatorId());
        }
        Map<String, UserInfo> users = userService.getUsersByIds(userIds).stream()
                .collect(Collectors.toMap(User::id, User::toInfo));
        item.setAssignees(item.getAssigneeIds().stream().map(users::get).filter(Objects::nonNull).toList());
        item.setCreator(users.get(item.getCreatorId()));
        item.setTenant(tenantService.getTenantsByIds(List.of(item.getTenantId())).stream().findFirst().orElse(null));
        return item;
    }

    private UploadToken decryptUploadToken(String encrypted) {
        try {
            return json.readValue(AesUtil.decrypt(Base64.getDecoder().decode(encrypted), encryptKey), UploadToken.class);
        } catch (IllegalArgumentException | IOException e) {
            throw ApiException.uploadTokenInvalid();
        }
    }

    private String encryptUploadToken(UploadToken token) {
        try {
            return Base64.getEncoder().encodeToString(AesUtil.encrypt(json.writeValueAsBytes(token), encryptKey));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
