package com.xhunting.coreapi.eventlogs;

/** Một bản ghi nhật ký thao tác (bản Go: eventlogs.EventLog được push sang service khác). */
public record EventLog(String action, String target, String targetId, String userId, String status,
                       String error, long createdTime) {
}
