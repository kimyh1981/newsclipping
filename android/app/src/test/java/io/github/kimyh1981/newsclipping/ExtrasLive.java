package io.github.kimyh1981.newsclipping;

import java.util.HashSet;
import java.util.List;

/**
 * 실제 인터넷에서 '언론사 직접 넣기'와 '농업 키워드'가 기사를 찾는지 본다 (단위 테스트가 아니라 news-check가 돌린다).
 * java ExtrasLive <주소 또는 키워드:낱말> ...
 */
public final class ExtrasLive {
    public static void main(String[] args) throws Exception {
        long now = System.currentTimeMillis();
        for (String a : args) {
            if (a.startsWith("키워드:")) {
                String k = a.substring(4);
                List<Brief.Item> got = Extras.keywordItems(k, now, 3, new HashSet<>());
                System.out.println("키워드 " + k + ": " + got.size() + "건");
                for (Brief.Item it : got) System.out.println("  " + it.source + " | " + it.spoken);
                continue;
            }
            Extras.Found f = Extras.find(a, "");
            if (f == null) { System.out.println(a + ": 기사 못 찾음"); continue; }
            List<Brief.Item> got = Extras.items(f.entries, f.name, false, now, 3, new HashSet<>());
            System.out.println(a + ": " + (f.viaGoogle ? "구글 뉴스" : "RSS") + " " + f.feed + " · 이름 " + f.name + " · 전체 " + f.entries.size() + "건, 최근 " + got.size() + "건");
            for (Brief.Item it : got) System.out.println("  " + it.spoken + (it.summary.isEmpty() ? "" : " / " + it.summary.substring(0, Math.min(60, it.summary.length()))));
        }
    }
}
