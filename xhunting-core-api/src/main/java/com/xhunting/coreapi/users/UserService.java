package com.xhunting.coreapi.users;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Tương đương {@code golang-common/users.UserService} (bản Go gọi gRPC sang identity service). */
public interface UserService {

    /** Go: GetUserByIds trả lỗi "UserNotFound" nếu thiếu id nào. Java: trả list, người gọi tự so số lượng. */
    List<User> getUsersByIds(Collection<String> ids);

    Optional<User> findByAccessToken(String token);
}
