package co.ke.shiftsync.common.exceptions;

/** A plain business-rule violation (duplicate email, bad input, etc.) that should
 * surface as a 400 with a clear message — as opposed to a constraint violation
 * on an assignment, which carries structured detail (see AssignmentCheckResponse). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
