package com.xhunting.coreapi.cases;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository Spring Data. Chỉ cần KHAI BÁO interface: lúc khởi động Spring sinh class implement sẵn
 * các method {@code save, insert, findById, findAll, count, deleteById...} cho collection "cases".
 * Bản Java core phải viết tay {@code MongoCaseRepository} (~150 dòng); bản Go cũng tự viết repository.
 *
 * Truy vấn động (filter theo tenant, hunter, tên, trạng thái...) không diễn đạt gọn bằng tên method được,
 * nên tách ra {@link CaseQueries} và viết bằng {@code MongoTemplate} trong {@link CaseRepositoryImpl}.
 * Quy ước: class tên {@code <TênRepository>Impl} được Spring tự ghép vào repository này.
 *
 * Ví dụ "derived query" (Spring đọc tên method để tạo query), không dùng trong bài nhưng nên biết:
 * {@code List<Case> findByTenantIdAndStatusIn(String tenantId, List<String> statuses);}
 */
public interface CaseRepository extends MongoRepository<Case, String>, CaseQueries {
}
