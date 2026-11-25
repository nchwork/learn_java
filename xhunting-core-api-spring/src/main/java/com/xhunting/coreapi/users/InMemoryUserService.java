package com.xhunting.coreapi.users;

import java.util.Collection;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Danh sách user demo cố định, để chạy local không cần identity service / Keycloak.
 * {@code @Service}: Spring tạo MỘT instance (singleton) và tiêm vào ai cần {@link UserService}.
 * Có 2 constructor nên Spring dùng constructor không tham số (mặc định) dưới đây.
 */
@Service
public class InMemoryUserService implements UserService {

    private final Map<String, User> users = new LinkedHashMap<>();

    public InMemoryUserService() {
        this(demoUsers());
    }

    public InMemoryUserService(List<User> seed) {
        seed.forEach(u -> users.put(u.id(), u));
    }

    /** User demo. Xem bảng tài khoản trong README. */
    private static List<User> demoUsers() {
        return List.of(
                new User("u-admin", "admin", "Admin", "admin@example.local",
                        Set.of("admin"), Set.of("MASTER"), false, "admin-token"),
                new User("u-hunter1", "hunter1", "Hunter One", "hunter1@example.local",
                        Set.of("hunter"), Set.of("tenant-a"), false, "hunter1-token"),
                new User("u-hunter2", "hunter2", "Hunter Two", "hunter2@example.local",
                        Set.of("hunter"), Set.of("tenant-a"), false, "hunter2-token"),
                new User("u-viewer", "viewer", "Viewer", "viewer@example.local",
                        Set.of("viewer"), Set.of("tenant-a"), false, "viewer-token"),
                new User("u-disabled", "disabled", "Disabled Hunter", "disabled@example.local",
                        Set.of("hunter"), Set.of("tenant-a"), true, "disabled-token"));
    }

    @Override
    public List<User> getUsersByIds(Collection<String> ids) {
        // map + filter(nonNull): Java Stream thay cho vòng for + if ok := m[id]
        return ids.stream().map(users::get).filter(Objects::nonNull).toList();
    }

    @Override
    public Optional<User> findByAccessToken(String token) {
        return users.values().stream().filter(u -> u.accessToken().equals(token)).findFirst();
    }
}
