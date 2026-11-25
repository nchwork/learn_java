package com.xhunting.coreapi.handler;

import java.util.List;

/**
 * Tham số tìm kiếm cho GetAll. Bản Go nhận {@code sdf.FindRequest} (payload JSON gồm filters,
 * sort, offset, limit). Bản demo đơn giản hóa thành query string:
 * {@code ?q=ransom&status=open,pending&sort=-createdTime&offset=0&limit=20}
 */
public record FindParams(String q, List<String> statuses, String sort, int offset, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    /** Compact constructor chuẩn hóa giá trị (Go thường làm trong FindRequest.Prepare()). */
    public FindParams {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        offset = Math.max(0, offset);
        limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    public static FindParams defaults() {
        return new FindParams(null, List.of(), null, 0, DEFAULT_LIMIT);
    }
}
