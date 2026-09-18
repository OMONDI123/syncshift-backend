package co.ke.shiftsync.location;

import jakarta.validation.constraints.NotBlank;

public class LocationDtos {

    public record LocationRequest(
            @NotBlank(message = "Location name is required") String name,
            String city,
            @NotBlank(message = "Timezone is required") String timezone
    ) {}

    public record LocationResponse(Long id, String name, String city, String timezone, boolean active) {
        public static LocationResponse from(Location l) {
            return new LocationResponse(l.getId(), l.getName(), l.getCity(), l.getTimezone(), l.isActive());
        }
    }
}
