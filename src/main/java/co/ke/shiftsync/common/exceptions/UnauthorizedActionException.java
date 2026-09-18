package co.ke.shiftsync.common.exceptions;

/** Thrown when an authenticated user lacks permission for the specific resource
 * they're trying to act on (e.g. a manager editing a location they don't run).
 * Distinct from Spring Security's 401/403 for missing/invalid credentials —
 * this is a 403 for "you're logged in, but not allowed to touch THIS one". */
public class UnauthorizedActionException extends RuntimeException {
    public UnauthorizedActionException(String message) {
        super(message);
    }
}
