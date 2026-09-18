package co.ke.shiftsync.swap;

import co.ke.shiftsync.swap.SwapDtos.HistoryEntryDto;
import co.ke.shiftsync.swap.SwapDtos.SwapResponse;

public final class SwapMapper {
    private SwapMapper() {}

    public static SwapResponse toResponse(SwapRequest s) {
        return new SwapResponse(
                s.getId(), s.getKind().name(), s.getShift().getId(),
                s.getRequestedBy().getId(), s.getRequestedBy().getName(),
                s.getPartner() == null ? null : s.getPartner().getId(),
                s.getPartner() == null ? null : s.getPartner().getName(),
                s.getStatus().name(), s.getCreatedAtUtc(), s.getResolvedAtUtc(), s.getExpiresAtUtc(),
                s.getHistory().stream().map(h -> new HistoryEntryDto(h.getAtUtc(), h.getEvent(), h.getByUserId())).toList()
        );
    }
}
