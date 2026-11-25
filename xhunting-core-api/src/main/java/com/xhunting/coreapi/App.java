package com.xhunting.coreapi;

import com.xhunting.coreapi.http.CoreApiServer;
import java.util.List;

/**
 * Ứng dụng đã nối dây xong: server HTTP + các tài nguyên phải đóng khi tắt (MongoClient...).
 * Go thường viết {@code defer client.Disconnect(ctx)} ngay trong main; Java gom vào một chỗ để đóng theo thứ tự.
 */
public record App(CoreApiServer server, List<AutoCloseable> resources) {

    public void start(int port) throws java.io.IOException {
        server.start(port);
    }

    /** Tắt server trước (ngừng nhận request), rồi mới đóng kết nối DB. */
    public void stop() {
        server.stop();
        for (AutoCloseable r : resources) {
            try {
                r.close();
            } catch (Exception e) {
                System.getLogger("app").log(System.Logger.Level.WARNING, "close failed", e);
            }
        }
    }
}
