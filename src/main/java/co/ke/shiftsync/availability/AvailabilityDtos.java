package co.ke.shiftsync.availability;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class AvailabilityDtos {

    public record SetRecurringRequest(
            @NotNull @Min(0) @Max(6) Integer dayOfWeek,
            @NotNull @Min(0) @Max(1439) Integer startMinutes,
            @NotNull @Min(0) @Max(1439) Integer endMinutes
    ) {}

    public record AddExceptionRequest(
            @NotNull LocalDate date,
            @NotNull Boolean available
    ) {}

    public record AvailabilityResponse(Long id, Long userId, String type, Integer dayOfWeek, LocalDate date,
                                        Integer startMinutes, Integer endMinutes, boolean available) {
        public static AvailabilityResponse from(AvailabilityWindow w) {
            return new AvailabilityResponse(w.getId(), w.getUser().getId(), w.getType().name(), w.getDayOfWeek(),
                    w.getDate(), w.getStartMinutes(), w.getEndMinutes(), w.isAvailable());
        }
    }
}
