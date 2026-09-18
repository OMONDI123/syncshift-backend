package co.ke.shiftsync.schedule;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** All shift instants are UTC; conversion to a specific IANA zone happens
 * only here, at the point of evaluation or display — never store or compare
 * "local" wall-clock strings. Using java.time's ZoneId/ZonedDateTime for the
 * conversion means DST transitions are handled correctly automatically,
 * the same guarantee the frontend gets from date-fns-tz. */
public final class TimeUtil {
    private TimeUtil() {}

    public static double durationHours(Instant start, Instant end) {
        return Duration.between(start, end).toMinutes() / 60.0;
    }

    public static double shiftDurationHours(Shift shift) {
        return durationHours(shift.getStartUtc(), shift.getEndUtc());
    }

    /** Hours of rest between the end of shift A and the start of shift B. */
    public static double restHoursBetween(Instant aEnd, Instant bStart) {
        return Duration.between(aEnd, bStart).toMinutes() / 60.0;
    }

    public static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    public static ZonedDateTime zoned(Instant instant, String timezone) {
        return instant.atZone(ZoneId.of(timezone));
    }

    /** 0=Sunday..6=Saturday, matching JS Date#getDay() so recurring
     * availability rows written by/for the frontend prototype line up. */
    public static int jsDayOfWeek(ZonedDateTime zdt) {
        return zdt.getDayOfWeek().getValue() % 7;
    }

    public static boolean isOvernightShift(Shift shift, String timezone) {
        ZonedDateTime start = zoned(shift.getStartUtc(), timezone);
        ZonedDateTime end = zoned(shift.getEndUtc(), timezone);
        return !start.toLocalDate().equals(end.toLocalDate());
    }
}
