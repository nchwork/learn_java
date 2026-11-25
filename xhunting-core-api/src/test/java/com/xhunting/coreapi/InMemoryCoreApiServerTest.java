package com.xhunting.coreapi;

/** Chạy toàn bộ CoreApiServerTest với repository in-memory. */
class InMemoryCoreApiServerTest extends CoreApiServerTest {

    @Override
    protected AppConfig config() {
        return new AppConfig(0, AppConfig.DEV_ENCRYPT_KEY, true, "", "unused");
    }
}
