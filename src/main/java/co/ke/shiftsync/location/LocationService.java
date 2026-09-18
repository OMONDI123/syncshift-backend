package co.ke.shiftsync.location;

import co.ke.shiftsync.audit.AuditService;
import co.ke.shiftsync.common.AuditEntityType;
import co.ke.shiftsync.common.exceptions.BusinessRuleException;
import co.ke.shiftsync.common.exceptions.NotFoundException;
import co.ke.shiftsync.location.LocationDtos.LocationRequest;
import co.ke.shiftsync.schedule.ShiftRepository;
import co.ke.shiftsync.security.CurrentUser;
import co.ke.shiftsync.security.PermissionService;
import co.ke.shiftsync.user.AppUser;
import co.ke.shiftsync.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

/** Part of the admin Setup module. A location can't be removed while any
 * shift, certified staff member, or manager still references it — the
 * brief's ambiguity note about de-certification is handled here by simply
 * not allowing deletion; de-certifying staff FROM a location (removing them
 * from its certified list) is a separate, always-allowed operation on
 * UserService that leaves their historical shifts/audit rows untouched
 * (see README). */
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository repository;
    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final PermissionService permissions;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public List<Location> findAll() {
        return repository.findAll();
    }

    @Transactional
    public Location create(LocationRequest req) {
        AppUser actor = currentUser.get();
        permissions.requireManageSettings(actor);
        validateTimezone(req.timezone());
        Location saved = repository.save(Location.builder()
                .name(req.name().trim()).city(req.city()).timezone(req.timezone()).active(true).build());
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.LOCATION, String.valueOf(saved.getId()), "created",
                null, null, saved.getId());
        return saved;
    }

    @Transactional
    public Location update(Long id, LocationRequest req) {
        AppUser actor = currentUser.get();
        permissions.requireManageSettings(actor);
        Location location = repository.findById(id).orElseThrow(() -> new NotFoundException("Location not found: " + id));
        validateTimezone(req.timezone());
        location.setName(req.name().trim());
        location.setCity(req.city());
        location.setTimezone(req.timezone());
        Location saved = repository.save(location);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.LOCATION, String.valueOf(id), "updated",
                null, null, id);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        AppUser actor = currentUser.get();
        permissions.requireManageSettings(actor);
        Location location = repository.findById(id).orElseThrow(() -> new NotFoundException("Location not found: " + id));
        if (shiftRepository.existsByLocationId(id)) {
            throw new BusinessRuleException("Can't delete a location that still has shifts. Remove or reassign them first.");
        }
        boolean referenced = userRepository.findAll().stream().anyMatch(u ->
                u.getCertifiedLocations().stream().anyMatch(l -> l.getId().equals(id))
                        || u.getManagedLocations().stream().anyMatch(l -> l.getId().equals(id)));
        if (referenced) {
            throw new BusinessRuleException("Can't delete a location while staff are still certified there or managers assigned to it.");
        }
        repository.delete(location);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.LOCATION, String.valueOf(id), "deleted");
    }

    private void validateTimezone(String tz) {
        try {
            ZoneId.of(tz);
        } catch (Exception e) {
            throw new BusinessRuleException("'" + tz + "' isn't a recognized IANA timezone id.");
        }
    }
}
