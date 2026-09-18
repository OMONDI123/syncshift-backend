package co.ke.shiftsync.settings;

import co.ke.shiftsync.location.Location;
import co.ke.shiftsync.location.LocationDtos.LocationRequest;
import co.ke.shiftsync.location.LocationDtos.LocationResponse;
import co.ke.shiftsync.location.LocationService;
import co.ke.shiftsync.settings.SettingsDtos.ThresholdsRequest;
import co.ke.shiftsync.settings.SettingsDtos.ThresholdsResponse;
import co.ke.shiftsync.skill.Skill;
import co.ke.shiftsync.skill.SkillDtos.SkillRequest;
import co.ke.shiftsync.skill.SkillDtos.SkillResponse;
import co.ke.shiftsync.skill.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The admin "Setup" module: skills catalog, locations, and the constraint
 * thresholds that drive the whole scheduling engine — everything the
 * frontend's in-progress SetupPage.tsx needs a backend for. Every mutating
 * endpoint is admin-only (enforced in the services, not just here).
 */
@RestController
@RequestMapping("/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SkillService skillService;
    private final LocationService locationService;
    private final SettingsService settingsService;

    @GetMapping("/skills")
    public List<SkillResponse> listSkills() {
        return skillService.findAll().stream().map(SkillResponse::from).toList();
    }

    @PostMapping("/skills")
    public SkillResponse createSkill(@Valid @RequestBody SkillRequest request) {
        return SkillResponse.from(skillService.create(request));
    }

    @PutMapping("/skills/{id}")
    public SkillResponse updateSkill(@PathVariable Long id, @Valid @RequestBody SkillRequest request) {
        return SkillResponse.from(skillService.update(id, request));
    }

    @DeleteMapping("/skills/{id}")
    public ResponseEntity<Void> deleteSkill(@PathVariable Long id) {
        skillService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/locations")
    public List<LocationResponse> listLocations() {
        return locationService.findAll().stream().map(LocationResponse::from).toList();
    }

    @PostMapping("/locations")
    public LocationResponse createLocation(@Valid @RequestBody LocationRequest request) {
        return LocationResponse.from(locationService.create(request));
    }

    @PutMapping("/locations/{id}")
    public LocationResponse updateLocation(@PathVariable Long id, @Valid @RequestBody LocationRequest request) {
        return LocationResponse.from(locationService.update(id, request));
    }

    @DeleteMapping("/locations/{id}")
    public ResponseEntity<Void> deleteLocation(@PathVariable Long id) {
        locationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/thresholds")
    public ThresholdsResponse getThresholds() {
        return ThresholdsResponse.from(settingsService.get());
    }

    @PutMapping("/thresholds")
    public ThresholdsResponse updateThresholds(@RequestBody ThresholdsRequest request) {
        return ThresholdsResponse.from(settingsService.update(request));
    }

    @GetMapping("/timezones")
    public List<String> timezones() {
        return java.time.ZoneId.getAvailableZoneIds().stream().sorted().toList();
    }
}
