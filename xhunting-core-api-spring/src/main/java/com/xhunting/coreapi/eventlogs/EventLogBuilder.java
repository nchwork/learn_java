package com.xhunting.coreapi.eventlogs;

/**
 * Port của {@code eventlogs.NewBuilder(action, "case")}. Builder là pattern rất phổ biến
 * trong Java (Lombok {@code @Builder}, {@code HttpRequest.newBuilder()}...).
 */
public class EventLogBuilder {

    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";

    private final String action;
    private final String target;
    private String targetId;
    private String status = STATUS_FAILED; // mặc định failed, chỉ set succeeded khi chạy tới cuối
    private String error;

    public EventLogBuilder(String action, String target) {
        this.action = action;
        this.target = target;
    }

    public EventLogBuilder targetId(String targetId) {
        this.targetId = targetId;
        return this; // trả this để gọi nối tiếp: b.targetId(x).succeeded()
    }

    public EventLogBuilder succeeded() {
        this.status = STATUS_SUCCEEDED;
        return this;
    }

    public EventLogBuilder error(Throwable e) {
        this.error = e == null ? null : e.getMessage();
        return this;
    }

    public EventLog build(String userId) {
        return new EventLog(action, target, targetId, userId, status, error, System.currentTimeMillis());
    }
}
