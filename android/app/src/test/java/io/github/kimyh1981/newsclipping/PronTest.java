package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.junit.Test;

/** 서버 녹음·웹(js/pron.js)과 폰 음성이 같은 글을 같게 읽어야 한다: tests/fixtures/pron.json을 같이 쓴다. */
public class PronTest {
    private static Path fixture() {
        for (Path p = Paths.get("").toAbsolutePath(); p != null; p = p.getParent()) {
            Path f = p.resolve("tests/fixtures/pron.json");
            if (Files.exists(f)) return f;
        }
        throw new IllegalStateException("tests/fixtures/pron.json을 찾지 못함");
    }

    @Test public void sameAsWeb() throws Exception {
        JSONArray cases = new JSONArray(new String(Files.readAllBytes(fixture()), StandardCharsets.UTF_8));
        for (int i = 0; i < cases.length(); i++) {
            JSONArray c = cases.getJSONArray(i);
            assertEquals(c.getString(0), c.getString(1), Pron.say(c.getString(0)));
        }
    }

    @Test public void tense() {
        assertEquals("쑤", Pron.tense("수"));
        assertEquals("껏", Pron.tense("것"));
        assertEquals("나", Pron.tense("나"));
    }
}
