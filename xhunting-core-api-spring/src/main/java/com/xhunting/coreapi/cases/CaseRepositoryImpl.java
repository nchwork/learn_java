package com.xhunting.coreapi.cases;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Cài đặt {@link CaseQueries} bằng {@link MongoTemplate}.
 *
 * So với {@code MongoCaseRepository.toBson} của bản Java core: {@code Filters.eq("tenantId", t)} thành
 * {@code Criteria.where("tenantId").is(t)}. Điểm hay: Criteria dùng TÊN FIELD JAVA ({@code creatorId}) và
 * Spring tự đổi sang tên trong DB ({@code createdByUserId}) theo {@code @Field}; id String tự đổi sang ObjectId.
 *
 * Spring tự tiêm {@link MongoTemplate} vào constructor (không cần {@code @Autowired} khi chỉ có 1 constructor).
 */
class CaseRepositoryImpl implements CaseQueries {

    private final MongoTemplate mongo;

    CaseRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<Case> findOne(CaseFilter filter) {
        return Optional.ofNullable(mongo.findOne(toQuery(filter), Case.class));
    }

    @Override
    public List<Case> findMany(CaseFilter filter) {
        Query q = toQuery(filter).with(toSort(filter.sort())).skip(filter.offset());
        if (filter.limit() > 0) {
            q.limit(filter.limit());
        }
        return mongo.find(q, Case.class);
    }

    @Override
    public long count(CaseFilter filter) {
        return mongo.count(toQuery(filter), Case.class);
    }

    static Query toQuery(CaseFilter f) {
        List<Criteria> parts = new ArrayList<>();
        if (f.filtersTenant()) {
            parts.add(Criteria.where("tenantId").is(f.tenantId()));
        }
        if (f.id() != null) {
            if (!ObjectId.isValid(f.id())) {
                return new Query(Criteria.where("id").is("__no_match__"));
            }
            parts.add(Criteria.where("id").is(f.id()));
        }
        if (f.excludeDeleted()) {
            parts.add(Criteria.where("deleted").ne(true));
        }
        if (f.creatorId() != null) {
            parts.add(Criteria.where("creatorId").is(f.creatorId()));
        }
        if (f.visibleToUserId() != null) {
            parts.add(new Criteria().orOperator(
                    Criteria.where("creatorId").is(f.visibleToUserId()),
                    Criteria.where("assigneeIds").is(f.visibleToUserId())));
        }
        if (f.nameContains() != null && !f.nameContains().isBlank()) {
            parts.add(Criteria.where("name").regex(Pattern.quote(f.nameContains()), "i"));
        }
        if (!f.statuses().isEmpty()) {
            parts.add(Criteria.where("status").in(f.statuses()));
        }
        return parts.isEmpty() ? new Query() : new Query(new Criteria().andOperator(parts));
    }

    private static Sort toSort(String sort) {
        String s = (sort == null || sort.isBlank()) ? "-createdTime" : sort;
        boolean desc = s.startsWith("-");
        String field = switch (desc ? s.substring(1) : s) {
            case "name", "updatedTime", "priority" -> desc ? s.substring(1) : s;
            default -> "createdTime";
        };
        return desc ? Sort.by(field).descending() : Sort.by(field).ascending();
    }
}
