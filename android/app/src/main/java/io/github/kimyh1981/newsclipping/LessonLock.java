package io.github.kimyh1981.newsclipping;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

/**
 * 잠긴 제품 공부 원고(briefing.json의 lessonsEnc)를 사내 코드로 푼다 (퇴사자가 못 보게, 2026-10-17).
 * 서버 tools/lessons-crypt.js와 같은 방식: AES-256-GCM, 열쇠 = PBKDF2-HMAC-SHA256(코드, salt, iter).
 * 열쇠 만들기는 폰에서 0.5초쯤 걸려서, 같은 코드·salt면 한 번 만든 열쇠를 다시 쓴다.
 */
final class LessonLock {
    private LessonLock() {}

    private static final Map<String, byte[]> keys = new HashMap<>();

    /** 풀어서 {product, english}. 코드가 틀리거나 망가졌으면 예외 */
    static JSONObject open(JSONObject box, String code) throws Exception {
        byte[] salt = Base64.getDecoder().decode(box.getString("salt"));
        byte[] iv = Base64.getDecoder().decode(box.getString("iv"));
        byte[] data = Base64.getDecoder().decode(box.getString("data"));
        int iter = box.optInt("iter", 150000);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(code.trim(), salt, iter), "AES"), new GCMParameterSpec(128, iv));
        return new JSONObject(new String(c.doFinal(data), StandardCharsets.UTF_8)); // 꼬리표가 안 맞으면(코드가 틀리면) 여기서 예외
    }

    private static synchronized byte[] key(String code, byte[] salt, int iter) throws Exception {
        String id = code + "|" + Base64.getEncoder().encodeToString(salt) + "|" + iter;
        byte[] k = keys.get(id);
        if (k == null) {
            k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(code.toCharArray(), salt, iter, 256)).getEncoded();
            keys.put(id, k);
        }
        return k;
    }
}
