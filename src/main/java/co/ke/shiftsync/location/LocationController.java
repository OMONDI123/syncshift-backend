package co.ke.shiftsync.location;

import co.ke.shiftsync.location.LocationDtos.LocationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only convenience endpoint — writes go through /setup/locations (admin-only). */
@RestController
@RequiredArgsConstructor
public class LocationController {

    private final LocationRepository repository;

    @GetMapping("/locations")
    public List<LocationResponse> list() {
        return repository.findAll().stream().map(LocationResponse::from).toList();
    }
}
