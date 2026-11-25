package com.xhunting.coreapi.cases;

import com.xhunting.coreapi.common.Constants;
import java.util.List;
import java.util.function.Predicate;

/**
 * Điều kiện tìm case, thay cho {@code repositories.FindRequest} của framework Go.
 *
 * Giống bản Go, filter là DỮ LIỆU (không phải code), nên mỗi repository tự dịch theo cách của nó:
 * <ul>
 *   <li>{@link InMemoryCaseRepository}: dịch thành {@link Predicate} qua {@link #toPredicate()}.</li>
 *   <li>{@link MongoCaseRepository}: dịch thành query BSON ({@code Filters.eq(...)}).</li>
 * </ul>
 *
 * Record bất biến: mỗi method {@code withXxx} trả về filter MỚI (pattern "wither"), thay cho kiểu Go
 * {@code findRequest.Filters = append(findRequest.Filters, ...)} sửa tại chỗ.
 *
 * @param tenantId        null hoặc "ANY" = không lọc tenant
 * @param id              lọc theo id (null = không lọc)
 * @param excludeDeleted  true = bỏ case đã xóa mềm ({@code repox.AddNotDeletedFilter})
 * @param creatorId       chỉ case do user này tạo (hunter khi xóa)
 * @param visibleToUserId chỉ case user này tạo HOẶC được giao (hunter khi xem/sửa)
 * @param nameContains    tìm theo tên, không phân biệt hoa thường
 * @param statuses        lọc theo trạng thái (rỗng = không lọc)
 * @param sort            "-createdTime" (giảm dần), "name", ...
 */
public record CaseFilter(
        String tenantId,
        String id,
        boolean excludeDeleted,
        String creatorId,
        String visibleToUserId,
        String nameContains,
        List<String> statuses,
        String sort,
        int offset,
        int limit) {

    public CaseFilter {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }

    public static CaseFilter forTenant(String tenantId) {
        return new CaseFilter(tenantId, null, false, null, null, null, List.of(), null, 0, 0);
    }

    public static CaseFilter byId(String tenantId, String id) {
        return forTenant(tenantId).withId(id);
    }

    public CaseFilter withId(String v) {
        return new CaseFilter(tenantId, v, excludeDeleted, creatorId, visibleToUserId, nameContains, statuses, sort, offset, limit);
    }

    public CaseFilter notDeleted() {
        return new CaseFilter(tenantId, id, true, creatorId, visibleToUserId, nameContains, statuses, sort, offset, limit);
    }

    public CaseFilter withCreator(String v) {
        return new CaseFilter(tenantId, id, excludeDeleted, v, visibleToUserId, nameContains, statuses, sort, offset, limit);
    }

    public CaseFilter visibleTo(String v) {
        return new CaseFilter(tenantId, id, excludeDeleted, creatorId, v, nameContains, statuses, sort, offset, limit);
    }

    public CaseFilter withNameContains(String v) {
        return new CaseFilter(tenantId, id, excludeDeleted, creatorId, visibleToUserId, v, statuses, sort, offset, limit);
    }

    public CaseFilter withStatuses(List<String> v) {
        return new CaseFilter(tenantId, id, excludeDeleted, creatorId, visibleToUserId, nameContains, v, sort, offset, limit);
    }

    public CaseFilter page(String sortBy, int off, int lim) {
        return new CaseFilter(tenantId, id, excludeDeleted, creatorId, visibleToUserId, nameContains, statuses, sortBy, off, lim);
    }

    public boolean filtersTenant() {
        return tenantId != null && !Constants.TENANT_ANY.equals(tenantId);
    }

    /** Dịch filter thành hàm kiểm tra cho repository in-memory. */
    public Predicate<Case> toPredicate() {
        Predicate<Case> p = c -> true;
        if (filtersTenant()) {
            p = p.and(c -> tenantId.equals(c.getTenantId()));
        }
        if (id != null) {
            p = p.and(c -> id.equals(c.getId()));
        }
        if (excludeDeleted) {
            p = p.and(c -> !c.isDeleted());
        }
        if (creatorId != null) {
            p = p.and(c -> creatorId.equals(c.getCreatorId()));
        }
        if (visibleToUserId != null) {
            p = p.and(c -> visibleToUserId.equals(c.getCreatorId()) || c.getAssigneeIds().contains(visibleToUserId));
        }
        if (nameContains != null && !nameContains.isBlank()) {
            String q = nameContains.toLowerCase();
            p = p.and(c -> c.getName() != null && c.getName().toLowerCase().contains(q));
        }
        if (!statuses.isEmpty()) {
            p = p.and(c -> statuses.contains(c.getStatus()));
        }
        return p;
    }
}
