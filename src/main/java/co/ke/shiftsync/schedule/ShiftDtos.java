package co.ke.shiftsync.schedule;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class ShiftDtos {

    public record CreateShiftRequest(
            @NotNull Long locationId,
            @NotNull Long skillId,
            @Min(1) int headcountNeeded,
            @NotNull Instant startUtc,
            @NotNull Instant endUtc,
            String notes
    ) {}

    /** All fields optional — only supplied ones are applied. */
    public record UpdateShiftRequest(
            Long locationId,
            Long skillId,
            Integer headcountNeeded,
            Instant startUtc,
            Instant endUtc,
            String notes,
            Long expectedVersion,
            boolean overrideCutoff,
            String overrideReason
    ) {}

    public record AssignRequest(
            @NotNull Long userId,
            Long expectedVersion,
            boolean override,
            String overrideReason
    ) {}

    public record UnassignRequest(
            @NotNull Long userId,
            Long expectedVersion
    ) {}

    public record ShiftResponse(
            Long id, Long locationId, String locationName, String locationTimezone,
            Long skillId, String skillKey, String skillLabel,
            int headcountNeeded, Instant startUtc, Instant endUtc,
            String status, boolean premium, String notes, Long version,
            List<AssignedStaffSummary> assignedStaff
    ) {}

    public record AssignedStaffSummary(Long userId, String name, String avatarColor) {}

    public record AssignmentCheckResponse(boolean ok, List<ViolationDto> violations, List<SuggestionDto> suggestions) {
        public static AssignmentCheckResponse from(AssignmentCheckResult r) {
            return new AssignmentCheckResponse(
                    r.ok(),
                    r.violations().stream().map(v -> new ViolationDto(v.code().name(), v.severity().name(), v.message())).toList(),
                    r.suggestions().stream().map(s -> new SuggestionDto(s.userId(), s.userName(), s.reason())).toList()
            );
        }
    }

    public record ViolationDto(String code, String severity, String message) {}
    public record SuggestionDto(Long userId, String userName, String reason) {}
}
