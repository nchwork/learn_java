package com.xhunting.coreapi.cases;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Repository lưu trong RAM, thay cho MongoDB để chạy local không cần cài gì.
 *
 * Khác biệt Go -> Java về concurrency:
 * <ul>
 *   <li>Go: {@code map} KHÔNG an toàn khi nhiều goroutine ghi, phải bọc {@code sync.RWMutex}.
 *       Java: {@link ConcurrentHashMap} an toàn sẵn cho từng thao tác.</li>
 *   <li>{@code putIfAbsent} là atomic, nên "kiểm tra trùng rồi chèn" không bị race
 *       (giống unique index của Mongo).</li>
 *   <li>Mỗi request HTTP chạy trên một virtual thread (xem CoreApiServer), tương tự mỗi request
 *       một goroutine trong Go.</li>
 * </ul>
 */
public class InMemoryCaseRepository implements CaseRepository {

    private final Map<String, Case> store = new ConcurrentHashMap<>();

    @Override
    public Case create(Case item) {
        Case stored = item.copy();
        if (store.putIfAbsent(stored.getId(), stored) != null) {
            throw new DuplicateCaseException(stored.getId());
        }
        return stored.copy();
    }

    @Override
    public Optional<Case> findOne(CaseFilter filter) {
        return query(filter).findFirst().map(Case::copy);
    }

    @Override
    public List<Case> findMany(CaseFilter filter) {
        Stream<Case> s = query(filter).sorted(comparator(filter.sort())).skip(filter.offset());
        if (filter.limit() > 0) {
            s = s.limit(filter.limit());
        }
        return s.map(Case::copy).toList();
    }

    @Override
    public long count(CaseFilter filter) {
        return query(filter).count();
    }

    @Override
    public boolean update(Case item) {
        // computeIfPresent: atomic "nếu có thì thay" (Go cần lock quanh 2 bước đọc + ghi)
        return store.computeIfPresent(item.getId(), (id, old) -> item.copy()) != null;
    }

    private Stream<Case> query(CaseFilter filter) {
        return store.values().stream().filter(filter.toPredicate());
    }

    /** "-createdTime" -> giảm dần theo createdTime. Mặc định: mới nhất trước. */
    private static Comparator<Case> comparator(String sort) {
        String s = (sort == null || sort.isBlank()) ? "-createdTime" : sort;
        boolean desc = s.startsWith("-");
        String field = desc ? s.substring(1) : s;
        Comparator<Case> cmp = switch (field) {
            case "name" -> Comparator.comparing(Case::getName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "updatedTime" -> Comparator.comparingLong(Case::getUpdatedTime);
            case "priority" -> Comparator.comparing(Case::getPriority, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparingLong(Case::getCreatedTime);
        };
        return desc ? cmp.reversed() : cmp;
    }
}
