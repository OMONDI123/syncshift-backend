package co.ke.shiftsync.common.exceptions;

/** Optimistic-concurrency / simultaneous-edit conflict — HTTP 409.
 * Raised when a shift's version doesn't match what the caller expected,
 * or when a concurrent assignment attempt for the same staff member lost
 * the race (see ShiftService.assignUser). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
