package com.xhunting.coreapi.cases;

import java.util.List;
import java.util.Optional;

/**
 * Tương đương interface {@code cases.CaseRepository} của core-api (bản Go dùng MongoDB).
 *
 * Khác biệt Go -> Java:
 * <ul>
 *   <li>Go trả {@code (*Case, error)} với {@code nil} khi không tìm thấy. Java trả
 *       {@link Optional}: người gọi buộc phải xử lý trường hợp rỗng
 *       ({@code orElseThrow(ApiException::caseNotFound)}).</li>
 *   <li>Mọi method KHÔNG trả về cùng instance đang lưu, mà trả bản sao (xem InMemoryCaseRepository).</li>
 * </ul>
 * Spring Data sẽ sinh implementation của interface kiểu này cho bạn ({@code extends MongoRepository<Case, String>}).
 */
public interface CaseRepository {

    /** @throws DuplicateCaseException nếu id đã tồn tại (Go: *repositories.DuplicateFieldError) */
    Case create(Case item);

    Optional<Case> findOne(CaseFilter filter);

    List<Case> findMany(CaseFilter filter);

    long count(CaseFilter filter);

    /** Ghi đè toàn bộ case có cùng id. Trả false nếu không có. */
    boolean update(Case item);

    /** Bản Go: exception riêng cho lỗi trùng khóa. */
    class DuplicateCaseException extends RuntimeException {
        public DuplicateCaseException(String id) {
            super("duplicate case id " + id);
        }
    }
}
