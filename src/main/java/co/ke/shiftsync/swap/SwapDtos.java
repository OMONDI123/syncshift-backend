package co.ke.shiftsync.swap;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class SwapDtos {

    public record RequestSwapRequest(@NotNull Long shiftId, @NotNull Long partnerUserId) {}

    public record RequestDropRequest(@NotNull Long shiftId) {}

    public record PickUpDropRequest() {}

    public record RejectRequest(@NotNull String reason) {}

    public record SwapResponse(Long id, String kind, Long shiftId, Long requestedByUserId, String requestedByName,
                                Long partnerUserId, String partnerName, String status, Instant createdAtUtc,
                                Instant resolvedAtUtc, Instant expiresAtUtc, List<HistoryEntryDto> history) {}

    public record HistoryEntryDto(Instant atUtc, String event, Long byUserId) {}
}
