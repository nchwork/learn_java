package com.xhunting.coreapi.cases;

import com.xhunting.coreapi.common.Constants;
import java.util.List;

/**
 * Điều kiện tìm case, giữ nguyên ý tưởng của bản Java core (và {@code repositories.FindRequest} của Go):
 * filter là DỮ LIỆU, repository tự dịch sang query. Ở bản Spring nó được dịch thành {@code Criteria}
 * trong {@link CaseRepositoryImpl}.
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
}
