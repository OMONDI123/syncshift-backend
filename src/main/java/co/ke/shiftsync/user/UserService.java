package co.ke.shiftsync.user;

import co.ke.shiftsync.audit.AuditService;
import co.ke.shiftsync.common.AuditEntityType;
import co.ke.shiftsync.common.NotificationChannel;
import co.ke.shiftsync.common.Role;
import co.ke.shiftsync.common.exceptions.BusinessRuleException;
import co.ke.shiftsync.common.exceptions.NotFoundException;
import co.ke.shiftsync.location.Location;
import co.ke.shiftsync.location.LocationRepository;
import co.ke.shiftsync.security.CurrentUser;
import co.ke.shiftsync.security.PermissionService;
import co.ke.shiftsync.skill.Skill;
import co.ke.shiftsync.skill.SkillRepository;
import co.ke.shiftsync.user.UserDtos.CreateUserRequest;
import co.ke.shiftsync.user.UserDtos.UpdateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String[] AVATAR_COLORS = {"#F5A623", "#2FAE66", "#E5484D", "#4B5468", "#DB8F14", "#1B3163"};
    private final Random random = new Random();

    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final SkillRepository skillRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissions;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public List<AppUser> findAll() {
        return userRepository.findAll();
    }

    public AppUser findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    @Transactional
    public AppUser createUser(CreateUserRequest req) {
        AppUser actor = currentUser.get();
        permissions.requireManageUsers(actor);

        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("A user with that email already exists.");
        }

        AppUser user = AppUser.builder()
                .name(req.name().trim())
                .email(email)
                .role(req.role())
                .avatarColor(AVATAR_COLORS[random.nextInt(AVATAR_COLORS.length)])
                .passwordHash(passwordEncoder.encode(DemoPasswords.forRole(req.role())))
                .homeTimezone(req.homeTimezone())
                .active(true)
                .hourlyRate(req.hourlyRate())
                .notificationChannel(NotificationChannel.IN_APP_ONLY)
                .build();

        applyRoleConditionalFields(user, req.role(), req.skillKeys(), req.certifiedLocationIds(),
                req.managedLocationIds(), req.desiredWeeklyHours());

        AppUser saved = userRepository.save(user);
        java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("name", saved.getName());
        after.put("email", saved.getEmail());
        after.put("role", saved.getRole());
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.USER, String.valueOf(saved.getId()),
                "created", null, after);
        return saved;
    }

    @Transactional
    public AppUser updateUser(Long userId, UpdateUserRequest req) {
        AppUser actor = currentUser.get();
        permissions.requireManageUsers(actor);
        AppUser user = findById(userId);

        java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
        before.put("role", user.getRole());
        before.put("managedLocationIds", user.getManagedLocations().stream().map(Location::getId).toList());

        if (req.email() != null) {
            String email = req.email().trim().toLowerCase();
            boolean taken = userRepository.findByEmailIgnoreCase(email)
                    .map(u -> !u.getId().equals(userId)).orElse(false);
            if (taken) throw new BusinessRuleException("A user with that email already exists.");
            user.setEmail(email);
        }
        if (req.name() != null) user.setName(req.name().trim());
        if (req.homeTimezone() != null) user.setHomeTimezone(req.homeTimezone());
        if (req.hourlyRate() != null) user.setHourlyRate(req.hourlyRate());
        if (req.notificationChannel() != null) user.setNotificationChannel(req.notificationChannel());

        Role effectiveRole = req.role() != null ? req.role() : user.getRole();
        if (req.role() != null) user.setRole(effectiveRole);

        // Role-conditional fields are re-applied whenever role changes OR the
        // caller explicitly sent new values, and CLEARED when they no longer
        // apply to the (possibly new) role — so a demoted manager doesn't
        // silently keep manage-location rights.
        applyRoleConditionalFields(user, effectiveRole,
                req.skillKeys(), req.certifiedLocationIds(), req.managedLocationIds(), req.desiredWeeklyHours());

        AppUser saved = userRepository.save(user);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.USER, String.valueOf(userId),
                "updated", before, req);
        return saved;
    }

    @Transactional
    public AppUser setActive(Long userId, boolean active) {
        AppUser actor = currentUser.get();
        permissions.requireManageUsers(actor);
        if (userId.equals(actor.getId()) && !active) {
            throw new BusinessRuleException("You can't deactivate your own account.");
        }
        AppUser user = findById(userId);
        user.setActive(active);
        AppUser saved = userRepository.save(user);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.USER, String.valueOf(userId),
                active ? "reactivated" : "deactivated");
        return saved;
    }

    /**
     * Applies skills/certifications/managed-locations conditionally on role,
     * clearing whichever don't apply. Passing `null` for a collection means
     * "leave as-is when it still applies to this role, else clear it" —
     * distinguishing "not provided" from "explicitly empty" only matters for
     * PATCH semantics on update; create always gets an explicit (possibly
     * empty) set.
     */
    private void applyRoleConditionalFields(AppUser user, Role role, Set<String> skillKeys,
                                             Set<Long> certifiedLocationIds, Set<Long> managedLocationIds,
                                             Integer desiredWeeklyHours) {
        if (role == Role.STAFF) {
            if (skillKeys != null) user.setSkills(resolveSkills(skillKeys));
            user.setDesiredWeeklyHours(desiredWeeklyHours);
        } else {
            user.setSkills(new HashSet<>());
            user.setDesiredWeeklyHours(null);
        }

        if (role != Role.ADMIN) {
            if (certifiedLocationIds != null) user.setCertifiedLocations(resolveLocations(certifiedLocationIds));
        } else {
            user.setCertifiedLocations(new HashSet<>());
        }

        if (role == Role.MANAGER) {
            if (managedLocationIds != null) user.setManagedLocations(resolveLocations(managedLocationIds));
        } else {
            user.setManagedLocations(new HashSet<>());
        }
    }

    private Set<Skill> resolveSkills(Set<String> keys) {
        Set<Skill> skills = new HashSet<>();
        for (String key : keys) {
            skills.add(skillRepository.findByKeyIgnoreCase(key)
                    .orElseThrow(() -> new BusinessRuleException("Unknown skill: " + key)));
        }
        return skills;
    }

    private Set<Location> resolveLocations(Set<Long> ids) {
        Set<Location> locations = new HashSet<>();
        for (Long id : ids) {
            locations.add(locationRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Unknown location id: " + id)));
        }
        return locations;
    }
}
