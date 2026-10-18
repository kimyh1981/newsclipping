package io.github.kimyh1981.newsclipping;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 실제 기사로 '전체 듣기' 본문 뽑기를 점검한다 (단위 테스트가 아니라 news-check가 돌린다).
 * 오늘 원고(briefing.json)의 언론사마다 첫 기사 원문을 폰과 같은 코드(ArticleReader)로 열어, 뽑힌 문장 수와 앞 두 문장을 보여 준다.
 * java ArticleLive <briefing.json> [언론사 수]
 */
public final class ArticleLive {
    public static void main(String[] args) throws Exception {
        JSONObject b = new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8));
        int max = args.length > 1 ? Integer.parseInt(args[1]) : 40;
        int tried = 0, ok = 0;
        JSONArray secs = b.getJSONArray("sections");
        for (int i = 0; i < secs.length() && tried < max; i++) {
            JSONArray srcs = secs.getJSONObject(i).getJSONArray("sources");
            for (int j = 0; j < srcs.length() && tried < max; j++) {
                JSONObject src = srcs.getJSONObject(j);
                JSONArray items = src.getJSONArray("items");
                if (items.length() == 0) continue;
                JSONObject it = items.getJSONObject(0);
                String link = it.optString("link", "");
                if (link.isEmpty() || link.contains("news.google.com")) continue;
                tried++;
                List<String> s;
                try {
                    s = ArticleReader.read(link);
                } catch (Exception e) {
                    System.out.println("✗ " + src.getString("name") + " · 열지 못함 (" + e.getMessage() + ") " + link);
                    continue;
                }
                if (!s.isEmpty()) ok++;
                System.out.println((s.isEmpty() ? "✗ " : "✓ ") + src.getString("name") + " · " + s.size() + "문장 · " + it.optString("title"));
                for (int k = 0; k < Math.min(2, s.size()); k++) System.out.println("    " + s.get(k));
                if (s.size() > 2) System.out.println("    … 마지막: " + s.get(s.size() - 1));
            }
        }
        System.out.println("본문 뽑기: " + ok + "/" + tried + "곳");
    }
}
