package co.ke.shiftsync.schedule;

/** Thrown when an assignment attempt is blocked. Carries the full
 * AssignmentCheckResult (violations + suggestions) so the API can return a
 * structured 422 body instead of a flat string — the brief requires the
 * system to "clearly explain which rule was broken and why" AND "suggest
 * alternatives when possible", and both need to reach the client intact. */
public class ConstraintViolationException extends RuntimeException {
    private final AssignmentCheckResult result;

    public ConstraintViolationException(AssignmentCheckResult result) {
        super("Assignment blocked by one or more constraints.");
        this.result = result;
    }

    public AssignmentCheckResult getResult() {
        return result;
    }
}
