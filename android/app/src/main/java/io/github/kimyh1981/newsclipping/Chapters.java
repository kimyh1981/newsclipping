package io.github.kimyh1981.newsclipping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 홈 화면 진행바의 계산 (주인 요청 2026-10-17: 유튜브 진행바처럼 섹션마다 끊어 보이고, 끌면 그 언론사 로고가 커지고, 놓으면 거기부터 읽기).
 * 원고 줄을 토막(섹션)으로 나누고, 줄마다 언론사 누리집(로고 벽의 열쇠)과 바 위의 자리(0~1)를 정한다.
 * 줄의 길이는 글자 수에 비례한다고 본다. 안드로이드에 기대지 않아 단위 테스트로 확인한다.
 */
final class Chapters {
    private final int[] chapter;
    private final String[] site;
    /** 줄 i가 시작하는 자리 (0~1), 마지막 칸은 1 */
    private final float[] at;
    private final int count;

    private Chapters(int[] chapter, String[] site, float[] at, int count) {
        this.chapter = chapter; this.site = site; this.at = at; this.count = count;
    }

    /** siteOf: 기사의 언론사 누리집 (모르면 "") */
    static Chapters of(List<Brief.Line> lines, Function<Brief.Item, String> siteOf) {
        int n = lines.size();
        int[] chapter = new int[n];
        String[] site = new String[n];
        float[] at = new float[n + 1];
        int ch = 0, lesson = -1;
        float total = 0;
        for (int i = 0; i < n; i++) {
            Brief.Line l = lines.get(i);
            // 빈 줄(섹션 사이)이나 제품 공부·농업영어로 바뀌는 곳에서 새 토막
            boolean blank = l.text.isEmpty();
            int kind = l.lesson > 0 ? (l.english ? 2 : 1) : 0;
            if (i > 0 && (blank || (lesson >= 0 && kind != lesson))) ch++;
            if (!blank) lesson = kind;
            chapter[i] = ch;
            at[i] = total;
            total += blank ? 2 : l.text.length() + 4;
        }
        at[n] = total;
        for (int i = 0; i <= n; i++) at[i] = total > 0 ? at[i] / total : 0;
        // 언론사: 기사 줄은 그 기사의, 'OO 소식입니다.'·섹션 소개는 같은 토막에서 바로 뒤 기사의 언론사
        String next = "";
        for (int i = n - 1; i >= 0; i--) {
            Brief.Line l = lines.get(i);
            if (i == n - 1 || chapter[i] != chapter[i + 1]) next = "";
            if (l.item != null) {
                String s = siteOf.apply(l.item);
                next = s == null ? "" : s;
            }
            site[i] = l.text.isEmpty() ? "" : next;
        }
        // 맺음말·인사처럼 기사가 없는 토막의 줄은 언론사 없음 (위에서 토막이 바뀌면 비웠다)
        return new Chapters(chapter, site, at, n == 0 ? 0 : ch + 1);
    }

    int count() { return count; }

    int size() { return site.length; }

    String site(int line) { return line >= 0 && line < site.length ? site[line] : ""; }

    float fractionOf(int line) { return at[Math.max(0, Math.min(line, at.length - 1))]; }

    /** 토막 경계 (0, …, 1): 바에서 끊어 그릴 자리 */
    float[] bounds() {
        List<Float> out = new ArrayList<>();
        out.add(0f);
        for (int i = 1; i < chapter.length; i++) if (chapter[i] != chapter[i - 1]) out.add(at[i]);
        out.add(1f);
        float[] b = new float[out.size()];
        for (int i = 0; i < b.length; i++) b[i] = out.get(i);
        return b;
    }

    List<Integer> chapterList() {
        List<Integer> out = new ArrayList<>();
        for (int c : chapter) out.add(c);
        return Collections.unmodifiableList(out);
    }

    /** 바의 자리(0~1) → 그 자리의 줄 */
    int lineAt(float x) {
        if (site.length == 0) return 0;
        for (int i = site.length - 1; i > 0; i--) if (x >= at[i]) return i;
        return 0;
    }

    /** 놓은 줄 → 읽기 시작할 줄: 같은 토막에서 같은 언론사가 시작하는 줄 */
    int seekTarget(int line) {
        if (site.length == 0) return 0;
        int i = Math.max(0, Math.min(line, site.length - 1));
        String s = site[i];
        while (i > 0 && chapter[i - 1] == chapter[i] && site[i - 1].equals(s) && !s.isEmpty()) i--;
        return i;
    }
}
