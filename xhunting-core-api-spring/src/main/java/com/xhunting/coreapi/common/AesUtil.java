package com.xhunting.coreapi.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Port của {@code software-development-framework/golang/encryptions/aes_util.go}:
 * AES-CBC, padding PKCS#7, IV ngẫu nhiên 16 byte đặt ở ĐẦU ciphertext.
 *
 * Khác biệt Go -> Java:
 * <ul>
 *   <li>Go ghép từng mảnh: {@code aes.NewCipher} + {@code cipher.NewCBCEncrypter} + tự viết {@code pad/unpad}.
 *       Java dùng một chuỗi "transformation": {@code "AES/CBC/PKCS5Padding"}. Tên là PKCS5 nhưng
 *       với block 16 byte thì nó chính là PKCS#7, nên ciphertext tương thích với bản Go.</li>
 *   <li>{@code rand.Reader} -> {@link SecureRandom}.</li>
 *   <li>Lỗi crypto của Java là checked {@link GeneralSecurityException}; ở đây gói lại thành
 *       {@link IllegalStateException} / {@link ApiException} cho gọn.</li>
 *   <li>Go cho key là {@code []byte(string)}. Java phải chỉ rõ charset khi đổi String -> byte[].</li>
 * </ul>
 */
public final class AesUtil {

    private static final int BLOCK_SIZE = 16;
    private static final SecureRandom RANDOM = new SecureRandom(); // thread-safe, dùng chung được

    private AesUtil() {
    }

    public static byte[] encrypt(byte[] plain, String key) {
        try {
            byte[] iv = new byte[BLOCK_SIZE];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(key), new IvParameterSpec(iv));
            byte[] body = cipher.doFinal(plain);

            byte[] out = new byte[BLOCK_SIZE + body.length]; // IV || ciphertext
            System.arraycopy(iv, 0, out, 0, BLOCK_SIZE);
            System.arraycopy(body, 0, out, BLOCK_SIZE, body.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES encrypt failed", e);
        }
    }

    public static byte[] decrypt(byte[] data, String key) {
        if (data.length < BLOCK_SIZE || (data.length - BLOCK_SIZE) % BLOCK_SIZE != 0) {
            throw ApiException.uploadTokenInvalid();
        }
        try {
            byte[] iv = Arrays.copyOfRange(data, 0, BLOCK_SIZE);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(key), new IvParameterSpec(iv));
            return cipher.doFinal(data, BLOCK_SIZE, data.length - BLOCK_SIZE);
        } catch (GeneralSecurityException e) {
            // Sai key hoặc token bị sửa -> padding sai -> BadPaddingException
            throw ApiException.uploadTokenInvalid();
        }
    }

    private static SecretKeySpec keySpec(String key) {
        byte[] k = key.getBytes(StandardCharsets.UTF_8);
        if (k.length != 16 && k.length != 24 && k.length != 32) {
            throw new IllegalArgumentException("AES key must be 16, 24 or 32 bytes, got " + k.length);
        }
        return new SecretKeySpec(k, "AES");
    }
}
