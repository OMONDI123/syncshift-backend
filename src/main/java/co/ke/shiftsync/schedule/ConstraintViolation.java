package co.ke.shiftsync.schedule;

/** A single broken (or nearly broken) rule, in plain language — the brief
 * requires the system to "clearly explain which rule was broken and why",
 * and `message` is written to stand alone in a UI toast/banner. */
public record ConstraintViolation(ViolationCode code, Severity severity, String message) {
}
