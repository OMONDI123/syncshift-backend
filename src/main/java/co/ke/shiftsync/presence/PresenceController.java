package co.ke.shiftsync.presence;

import co.ke.shiftsync.presence.PresenceDtos.ClockRecordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService service;

    @GetMapping("/locations/{locationId}/on-duty")
    public List<ClockRecordResponse> onDutyAt(@PathVariable Long locationId) {
        return service.onDutyAt(locationId).stream().map(ClockRecordResponse::from).toList();
    }

    @PostMapping("/shifts/{shiftId}/clock-in")
    public ClockRecordResponse clockIn(@PathVariable Long shiftId) {
        return ClockRecordResponse.from(service.clockIn(shiftId));
    }

    @PostMapping("/shifts/{shiftId}/clock-out")
    public ClockRecordResponse clockOut(@PathVariable Long shiftId) {
        return ClockRecordResponse.from(service.clockOut(shiftId));
    }
}
