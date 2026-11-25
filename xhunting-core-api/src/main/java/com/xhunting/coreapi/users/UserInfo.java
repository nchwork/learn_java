package com.xhunting.coreapi.users;

/** Thông tin user trả ra API ({@code message UserInfo}). Bất biến -> record. */
public record UserInfo(String id, String name, String displayName, String email) {
}
