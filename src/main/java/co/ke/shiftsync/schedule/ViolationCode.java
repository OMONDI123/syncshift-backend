package co.ke.shiftsync.schedule;

/** Mirrors the frontend prototype's ViolationCode union exactly (types/index.ts)
 * for wire compatibility. Note DAILY_HOURS_BLOCK is deliberately reused for
 * the weekly-hours warnings too, same as the frontend does — kept as-is here
 * rather than "fixed" so the two systems agree on the wire shape; a cleaner
 * WEEKLY_HOURS_WARNING code is a good follow-up once both sides can change
 * together (see README "Known limitations"). */
public enum ViolationCode {
    DOUBLE_BOOKED,
    REST_PERIOD,
    SKILL_MISMATCH,
    LOCATION_NOT_CERTIFIED,
    OUTSIDE_AVAILABILITY,
    DAILY_HOURS_BLOCK,
    SEVENTH_CONSECUTIVE_DAY
}
