package co.ke.shiftsync.schedule;

import java.util.List;

public record AssignmentCheckResult(boolean ok, List<ConstraintViolation> violations, List<AssignmentSuggestion> suggestions) {
    public static AssignmentCheckResult empty() {
        return new AssignmentCheckResult(false, List.of(), List.of());
    }
}
