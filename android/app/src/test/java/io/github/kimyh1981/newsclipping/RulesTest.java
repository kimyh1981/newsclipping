package io.github.kimyh1981.newsclipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import org.junit.Test;

public class RulesTest {
    private static final int SIX = 6 * 60, EIGHT = 8 * 60;

    @Test public void weekdayMorningWindow() {
        assertTrue(Rules.inWindow(Calendar.TUESDAY, 6 * 60, SIX, EIGHT, true));
        assertTrue(Rules.inWindow(Calendar.FRIDAY, 7 * 60 + 59, SIX, EIGHT, true));
        assertFalse(Rules.inWindow(Calendar.MONDAY, 8 * 60, SIX, EIGHT, true));
        assertFalse(Rules.inWindow(Calendar.MONDAY, 5 * 60 + 59, SIX, EIGHT, true));
    }

    @Test public void weekendsOnlyWhenAllowed() {
        assertFalse(Rules.inWindow(Calendar.SATURDAY, 7 * 60, SIX, EIGHT, true));
        assertFalse(Rules.inWindow(Calendar.SUNDAY, 7 * 60, SIX, EIGHT, true));
        assertTrue(Rules.inWindow(Calendar.SATURDAY, 7 * 60, SIX, EIGHT, false));
    }

    @Test public void overnightAndAllDay() {
        assertTrue(Rules.inWindow(Calendar.MONDAY, 23 * 60, 22 * 60, 2 * 60, false));
        assertTrue(Rules.inWindow(Calendar.MONDAY, 60, 22 * 60, 2 * 60, false));
        assertFalse(Rules.inWindow(Calendar.MONDAY, 12 * 60, 22 * 60, 2 * 60, false));
        assertTrue(Rules.inWindow(Calendar.MONDAY, 12 * 60, SIX, SIX, false));
        assertEquals("06:00", Rules.hhmm(SIX));
    }

    @Test public void chunksKeepPausesAndRespectLimit() {
        assertEquals(Arrays.asList("좋은 아침입니다.", "", "먼저 주요 신문.", "기사."),
                Rules.chunks("좋은 아침입니다.\n\n\n먼저 주요 신문.\n기사.\n\n", 4000));
        assertEquals(Arrays.asList("가나다라.", "마바사."), Rules.chunks("가나다라. 마바사.", 8));
        assertTrue(Rules.chunks("", 100).isEmpty());
        for (String c : Rules.chunks("아주긴문장없이계속되는텍스트입니다", 5)) assertTrue(c.length() <= 5);
    }
}
