package co.ke.shiftsync.availability;

import co.ke.shiftsync.availability.AvailabilityDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService service;

    @GetMapping
    public List<AvailabilityResponse> list(@PathVariable Long userId) {
        return service.forUser(userId).stream().map(AvailabilityResponse::from).toList();
    }

    @PutMapping("/recurring")
    public AvailabilityResponse setRecurring(@PathVariable Long userId, @Valid @RequestBody SetRecurringRequest req) {
        return AvailabilityResponse.from(service.setRecurring(userId, req.dayOfWeek(), req.startMinutes(), req.endMinutes()));
    }

    @DeleteMapping("/recurring/{dayOfWeek}")
    public ResponseEntity<Void> clearRecurring(@PathVariable Long userId, @PathVariable int dayOfWeek) {
        service.clearRecurring(userId, dayOfWeek);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exceptions")
    public AvailabilityResponse addException(@PathVariable Long userId, @Valid @RequestBody AddExceptionRequest req) {
        return AvailabilityResponse.from(service.addException(userId, req.date(), req.available()));
    }

    @DeleteMapping("/exceptions/{id}")
    public ResponseEntity<Void> removeException(@PathVariable Long userId, @PathVariable Long id) {
        service.removeException(id);
        return ResponseEntity.noContent().build();
    }
}
