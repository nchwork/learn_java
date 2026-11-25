package com.xhunting.coreapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.xhunting.coreapi.common.AesUtil;
import com.xhunting.coreapi.common.ApiException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AesUtilTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void roundTrip() {
        byte[] plain = "{\"caseId\":\"abc\"}".getBytes(StandardCharsets.UTF_8);
        byte[] enc = AesUtil.encrypt(plain, KEY);
        assertEquals(0, (enc.length - 16) % 16, "IV + whole blocks, như bản Go");
        assertArrayEquals(plain, AesUtil.decrypt(enc, KEY));
    }

    @Test
    void wrongKeyIsRejected() {
        byte[] enc = AesUtil.encrypt("hello".getBytes(StandardCharsets.UTF_8), KEY);
        assertThrows(ApiException.class, () -> AesUtil.decrypt(enc, "ffffffffffffffffffffffffffffffff"));
    }
}
