package co.ke.shiftsync.schedule;

import co.ke.shiftsync.schedule.ShiftDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** Requirement #2 (shift scheduling) end to end: create/edit/publish shifts,
 * assign/unassign staff with full constraint enforcement, and a pure preview
 * endpoint for "what would happen if" checks. */
@RestController
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    @GetMapping("/shifts/{id}")
    public ShiftResponse get(@PathVariable Long id) {
        return ShiftMapper.toResponse(shiftService.findById(id));
    }

    @GetMapping("/locations/{locationId}/shifts")
    public List<ShiftResponse> listByLocation(
            @PathVariable Long locationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant weekStartUtc) {
        List<Shift> shifts = weekStartUtc != null
                ? shiftService.listByLocationAndWeek(locationId, weekStartUtc)
                : shiftService.listByLocation(locationId);
        return shifts.stream().map(ShiftMapper::toResponse).toList();
    }

    @GetMapping("/users/{userId}/shifts")
    public List<ShiftResponse> listForUser(@PathVariable Long userId) {
        return shiftService.listForUser(userId).stream().map(ShiftMapper::toResponse).toList();
    }

    @PostMapping("/shifts")
    public ShiftResponse create(@Valid @RequestBody CreateShiftRequest request) {
        return ShiftMapper.toResponse(shiftService.create(request));
    }

    @PutMapping("/shifts/{id}")
    public ShiftResponse update(@PathVariable Long id, @RequestBody UpdateShiftRequest request) {
        return ShiftMapper.toResponse(shiftService.update(id, request));
    }

    @PostMapping("/shifts/{id}/publish")
    public ShiftResponse publish(@PathVariable Long id, @RequestParam(required = false) Long expectedVersion) {
        return ShiftMapper.toResponse(shiftService.publish(id, expectedVersion));
    }

    @PostMapping("/shifts/{id}/unpublish")
    public ShiftResponse unpublish(@PathVariable Long id, @RequestParam(required = false) Long expectedVersion,
                                    @RequestParam(defaultValue = "false") boolean overrideCutoff,
                                    @RequestParam(required = false) String overrideReason) {
        return ShiftMapper.toResponse(shiftService.unpublish(id, expectedVersion, overrideCutoff, overrideReason));
    }

    @PostMapping("/locations/{locationId}/schedule/publish-week")
    public List<ShiftResponse> publishWeek(@PathVariable Long locationId,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant weekStartUtc) {
        return shiftService.publishWeek(locationId, weekStartUtc).stream().map(ShiftMapper::toResponse).toList();
    }

    @PostMapping("/shifts/{id}/check-assignment")
    public AssignmentCheckResponse checkAssignment(@PathVariable Long id, @RequestParam Long userId) {
        return AssignmentCheckResponse.from(shiftService.checkAssignment(id, userId));
    }

    @PostMapping("/shifts/{id}/assign")
    public ShiftResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return ShiftMapper.toResponse(shiftService.assign(id, request));
    }

    @PostMapping("/shifts/{id}/unassign")
    public ShiftResponse unassign(@PathVariable Long id, @Valid @RequestBody UnassignRequest request) {
        return ShiftMapper.toResponse(shiftService.unassign(id, request));
    }

    @DeleteMapping("/shifts/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        shiftService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
