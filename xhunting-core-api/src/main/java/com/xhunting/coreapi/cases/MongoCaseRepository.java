package com.xhunting.coreapi.cases;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/**
 * Repository lưu case vào MongoDB, collection {@code cases}, giống core-api bản Go.
 *
 * Khác biệt Go -> Java:
 * <ul>
 *   <li>Go map struct <-> BSON bằng tag {@code bson:"tenantId"} và reflection của driver.
 *       Ở đây map TAY qua {@link Document} (thực chất là {@code Map<String, Object>}) để thấy rõ
 *       từng field. Driver Java cũng có {@code PojoCodecProvider} tự map, và Spring Data dùng
 *       annotation {@code @Document}, {@code @Id}, {@code @Field}.</li>
 *   <li>Go: {@code filter := bson.M{"tenantId": t, "isDeleted": bson.M{"$ne": true}}}.
 *       Java: builder có kiểu {@code Filters.and(Filters.eq("tenantId", t), Filters.ne("isDeleted", true))}.</li>
 *   <li>Go: {@code cursor, err := coll.Find(ctx, filter)} rồi {@code cursor.All(ctx, &items)}.
 *       Java: {@code coll.find(filter)} trả {@link FindIterable} (lazy), duyệt bằng for-each hoặc {@code into(list)}.</li>
 *   <li>Go truyền {@code context.Context} (timeout/cancel) vào mọi lệnh. Driver sync của Java không có
 *       tham số đó; timeout cấu hình ở {@code MongoClientSettings} hoặc qua connection string.</li>
 *   <li>Lỗi trùng khóa: Go {@code mongo.IsDuplicateKeyError(err)}; Java bắt {@link MongoWriteException}
 *       rồi xem {@code getError().getCategory() == DUPLICATE_KEY}.</li>
 * </ul>
 * {@link com.mongodb.client.MongoClient} an toàn đa luồng và tự có connection pool: tạo MỘT lần lúc khởi
 * động rồi dùng chung, giống {@code *mongo.Client} trong Go.
 */
public class MongoCaseRepository implements CaseRepository {

    public static final String COLLECTION = "cases";

    private final MongoCollection<Document> collection;

    public MongoCaseRepository(MongoDatabase database) {
        this.collection = database.getCollection(COLLECTION);
        // Index cho các truy vấn hay dùng (Go thường tạo trong migration hoặc lúc start).
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("tenantId"), Indexes.descending("createdTime")));
        collection.createIndex(Indexes.ascending("createdByUserId"));
        collection.createIndex(Indexes.ascending("assigneeIds"));
    }

    @Override
    public Case create(Case item) {
        try {
            collection.insertOne(toDocument(item));
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw new DuplicateCaseException(item.getId());
            }
            throw e;
        }
        return item.copy();
    }

    @Override
    public Optional<Case> findOne(CaseFilter filter) {
        // Optional.ofNullable: first() trả null khi không có (giống mongo.ErrNoDocuments của Go)
        return Optional.ofNullable(collection.find(toBson(filter)).first()).map(MongoCaseRepository::fromDocument);
    }

    @Override
    public List<Case> findMany(CaseFilter filter) {
        FindIterable<Document> it = collection.find(toBson(filter))
                .sort(toSort(filter.sort()))
                .skip(filter.offset());
        if (filter.limit() > 0) {
            it = it.limit(filter.limit());
        }
        List<Case> result = new ArrayList<>();
        for (Document d : it) { // con trỏ được đóng tự động khi duyệt hết
            result.add(fromDocument(d));
        }
        return result;
    }

    @Override
    public long count(CaseFilter filter) {
        return collection.countDocuments(toBson(filter));
    }

    @Override
    public boolean update(Case item) {
        return collection.replaceOne(Filters.eq("_id", new ObjectId(item.getId())), toDocument(item))
                .getMatchedCount() > 0;
    }

    public boolean isEmpty() {
        return collection.countDocuments() == 0;
    }

    // ------------------------------------------------------------ CaseFilter -> BSON

    static Bson toBson(CaseFilter f) {
        List<Bson> parts = new ArrayList<>();
        if (f.filtersTenant()) {
            parts.add(Filters.eq("tenantId", f.tenantId()));
        }
        if (f.id() != null) {
            if (!ObjectId.isValid(f.id())) {
                // id không phải 24 ký tự hex thì chắc chắn không tồn tại (Go: ObjectIDFromHex trả lỗi)
                return Filters.eq("_id", "__no_match__");
            }
            parts.add(Filters.eq("_id", new ObjectId(f.id())));
        }
        if (f.excludeDeleted()) {
            parts.add(Filters.ne("isDeleted", true));
        }
        if (f.creatorId() != null) {
            parts.add(Filters.eq("createdByUserId", f.creatorId()));
        }
        if (f.visibleToUserId() != null) {
            // {"$or": [{"createdByUserId": u}, {"assigneeIds": u}]}  (mảng: eq khớp nếu chứa phần tử)
            parts.add(Filters.or(
                    Filters.eq("createdByUserId", f.visibleToUserId()),
                    Filters.eq("assigneeIds", f.visibleToUserId())));
        }
        if (f.nameContains() != null && !f.nameContains().isBlank()) {
            // Pattern.quote: escape ký tự đặc biệt để người dùng không chèn được regex (tránh ReDoS)
            parts.add(Filters.regex("name", Pattern.quote(f.nameContains()), "i"));
        }
        if (!f.statuses().isEmpty()) {
            parts.add(Filters.in("status", f.statuses()));
        }
        return parts.isEmpty() ? new Document() : Filters.and(parts);
    }

    private static Bson toSort(String sort) {
        String s = (sort == null || sort.isBlank()) ? "-createdTime" : sort;
        boolean desc = s.startsWith("-");
        String field = switch (desc ? s.substring(1) : s) {
            case "name", "updatedTime", "priority" -> desc ? s.substring(1) : s;
            default -> "createdTime"; // chỉ cho sort theo field cho phép
        };
        return desc ? Sorts.descending(field) : Sorts.ascending(field);
    }

    // ------------------------------------------------------------ Case <-> Document

    /** Tên field giữ theo bản Go (bson tag), ví dụ creatorId lưu là "createdByUserId", id lưu là "_id". */
    static Document toDocument(Case c) {
        return new Document("_id", new ObjectId(c.getId()))
                .append("createdTime", c.getCreatedTime())
                .append("updatedTime", c.getUpdatedTime())
                .append("tenantId", c.getTenantId())
                .append("name", c.getName())
                .append("priority", c.getPriority())
                .append("status", c.getStatus())
                .append("assigneeIds", c.getAssigneeIds())
                .append("expectedEndpoints", c.getExpectedEndpoints())
                .append("createdByUserId", c.getCreatorId())
                .append("numberOfFiles", c.getNumberOfFiles())
                .append("filenames", c.getFilenames())
                .append("description", c.getDescription())
                .append("folderPath", c.getFolderPath())
                .append("isDeleted", c.isDeleted());
    }

    static Case fromDocument(Document d) {
        Case c = new Case();
        c.setId(d.getObjectId("_id").toHexString());
        c.setCreatedTime(d.get("createdTime", 0L)); // get(key, default): tránh NPE khi field thiếu
        c.setUpdatedTime(d.get("updatedTime", 0L));
        c.setTenantId(d.getString("tenantId"));
        c.setName(d.getString("name"));
        c.setPriority(d.getString("priority"));
        c.setStatus(d.getString("status"));
        c.setAssigneeIds(d.getList("assigneeIds", String.class));
        c.setExpectedEndpoints(d.get("expectedEndpoints", 0));
        c.setCreatorId(d.getString("createdByUserId"));
        c.setNumberOfFiles(d.get("numberOfFiles", 0));
        c.setFilenames(d.getList("filenames", String.class));
        c.setDescription(d.getString("description"));
        c.setFolderPath(d.getString("folderPath"));
        c.setDeleted(d.get("isDeleted", false));
        return c;
    }
}
