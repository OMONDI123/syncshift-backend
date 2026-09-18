package co.ke.shiftsync.schedule;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TimeUtilTest {

    @Test
    void overlapsDetectsSimpleOverlap() {
        Instant aStart = Instant.parse("2026-01-01T10:00:00Z");
        Instant aEnd = Instant.parse("2026-01-01T14:00:00Z");
        Instant bStart = Instant.parse("2026-01-01T13:00:00Z");
        Instant bEnd = Instant.parse("2026-01-01T17:00:00Z");
        assertTrue(TimeUtil.overlaps(aStart, aEnd, bStart, bEnd));
    }

    @Test
    void overlapsFalseWhenBackToBack() {
        Instant aStart = Instant.parse("2026-01-01T10:00:00Z");
        Instant aEnd = Instant.parse("2026-01-01T14:00:00Z");
        Instant bStart = Instant.parse("2026-01-01T14:00:00Z");
        Instant bEnd = Instant.parse("2026-01-01T17:00:00Z");
        assertFalse(TimeUtil.overlaps(aStart, aEnd, bStart, bEnd));
    }

    @Test
    void restHoursBetweenComputesCorrectly() {
        Instant end = Instant.parse("2026-01-01T22:00:00Z");
        Instant nextStart = Instant.parse("2026-01-02T05:00:00Z");
        assertEquals(7.0, TimeUtil.restHoursBetween(end, nextStart), 0.001);
    }

    @Test
    void jsDayOfWeekMatchesSundayZero() {
        // 2026-01-04 is a Sunday
        ZonedDateTime sunday = ZonedDateTime.parse("2026-01-04T09:00:00Z");
        assertEquals(0, TimeUtil.jsDayOfWeek(sunday));
        ZonedDateTime saturday = ZonedDateTime.parse("2026-01-03T09:00:00Z");
        assertEquals(6, TimeUtil.jsDayOfWeek(saturday));
    }

    @Test
    void isOvernightShiftDetectsMidnightCrossing() {
        Shift shift = Shift.builder()
                .startUtc(Instant.parse("2026-01-01T07:00:00Z")) // 11pm Pacific (UTC-8) Dec 31
                .endUtc(Instant.parse("2026-01-01T11:00:00Z"))   // 3am Pacific Jan 1
                .build();
        assertTrue(TimeUtil.isOvernightShift(shift, "America/Los_Angeles"));
    }
}
