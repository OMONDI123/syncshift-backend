package co.ke.shiftsync.schedule;

import co.ke.shiftsync.audit.AuditService;
import co.ke.shiftsync.common.AuditEntityType;
import co.ke.shiftsync.common.Role;
import co.ke.shiftsync.common.exceptions.BusinessRuleException;
import co.ke.shiftsync.common.exceptions.ConflictException;
import co.ke.shiftsync.common.exceptions.NotFoundException;
import co.ke.shiftsync.location.Location;
import co.ke.shiftsync.location.LocationRepository;
import co.ke.shiftsync.notification.NotificationType;
import co.ke.shiftsync.notification.NotificationService;
import co.ke.shiftsync.schedule.ShiftDtos.*;
import co.ke.shiftsync.schedule.events.ShiftEditedEvent;
import co.ke.shiftsync.security.CurrentUser;
import co.ke.shiftsync.security.PermissionService;
import co.ke.shiftsync.settings.ConstraintThresholds;
import co.ke.shiftsync.settings.SettingsService;
import co.ke.shiftsync.skill.Skill;
import co.ke.shiftsync.skill.SkillRepository;
import co.ke.shiftsync.user.AppUser;
import co.ke.shiftsync.user.UserRepository;
import co.ke.shiftsync.ws.RealtimeGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything about creating, editing, publishing and assigning shifts.
 * Constraint evaluation itself lives in ConstraintService; this class is the
 * transactional boundary, permission gate, concurrency guard, and the thing
 * that fans out notifications + realtime broadcasts + audit entries once a
 * change is accepted.
 */
@Service
@RequiredArgsConstructor
public class ShiftService {

    /** Shifts starting at/after 5pm local on Friday or Saturday are "premium" —
     * see FairnessService for how this feeds the fairness score. Not specified
     * numerically in the brief; documented assumption. */
    private static final int PREMIUM_START_HOUR = 17;

    private final ShiftRepository shiftRepository;
    private final LocationRepository locationRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final ConstraintService constraintService;
    private final SettingsService settingsService;
    private final PermissionService permissions;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final RealtimeGateway realtimeGateway;
    private final ApplicationEventPublisher eventPublisher;

    public Shift findById(Long id) {
        return shiftRepository.findById(id).orElseThrow(() -> new NotFoundException("Shift not found: " + id));
    }

    public List<Shift> listByLocation(Long locationId) {
        return shiftRepository.findByLocationId(locationId);
    }

    public List<Shift> listByLocationAndWeek(Long locationId, Instant weekStartUtc) {
        return shiftRepository.findByLocationAndWeek(locationId, weekStartUtc, weekStartUtc.plusSeconds(7L * 24 * 3600));
    }

    public List<Shift> listForUser(Long userId) {
        return shiftRepository.findByAssignedUserId(userId);
    }

    @Transactional
    public Shift create(CreateShiftRequest req) {
        AppUser actor = currentUser.get();
        permissions.requireManageLocation(actor, req.locationId());
        if (!req.endUtc().isAfter(req.startUtc())) {
            throw new BusinessRuleException("A shift's end time must be after its start time.");
        }
        Location location = locationRepository.findById(req.locationId())
                .orElseThrow(() -> new NotFoundException("Location not found: " + req.locationId()));
        Skill skill = skillRepository.findById(req.skillId())
                .orElseThrow(() -> new NotFoundException("Skill not found: " + req.skillId()));

        Shift shift = Shift.builder()
                .location(location).skillRequired(skill)
                .headcountNeeded(req.headcountNeeded())
                .startUtc(req.startUtc()).endUtc(req.endUtc())
                .notes(req.notes())
                .status(ShiftStatus.DRAFT)
                .build();
        shift.setPremium(isPremium(shift));

        Shift saved = shiftRepository.save(shift);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SHIFT, String.valueOf(saved.getId()),
                "created", null, ShiftMapper.toResponse(saved), location.getId());
        realtimeGateway.scheduleChanged(location.getId(), ShiftMapper.toResponse(saved));
        return saved;
    }

    @Transactional
    public Shift update(Long shiftId, UpdateShiftRequest req) {
        AppUser actor = currentUser.get();
        Shift shift = findById(shiftId);
        permissions.requireManageLocation(actor, shift.getLocation().getId());
        checkVersion(shift, req.expectedVersion());
        enforcePublishCutoff(shift, actor, req.overrideCutoff(), req.overrideReason());

        ShiftResponse before = ShiftMapper.toResponse(shift);

        if (req.locationId() != null && !req.locationId().equals(shift.getLocation().getId())) {
            permissions.requireManageLocation(actor, req.locationId());
            Location newLocation = locationRepository.findById(req.locationId())
                    .orElseThrow(() -> new NotFoundException("Location not found: " + req.locationId()));
            shift.setLocation(newLocation);
        }
        if (req.skillId() != null) {
            shift.setSkillRequired(skillRepository.findById(req.skillId())
                    .orElseThrow(() -> new NotFoundException("Skill not found: " + req.skillId())));
        }
        if (req.headcountNeeded() != null) shift.setHeadcountNeeded(req.headcountNeeded());
        if (req.startUtc() != null) shift.setStartUtc(req.startUtc());
        if (req.endUtc() != null) shift.setEndUtc(req.endUtc());
        if (req.notes() != null) shift.setNotes(req.notes());
        if (!shift.getEndUtc().isAfter(shift.getStartUtc())) {
            throw new BusinessRuleException("A shift's end time must be after its start time.");
        }
        shift.setPremium(isPremium(shift));

        // Re-validate everyone still assigned; drop anyone the edit now makes
        // unqualified/unavailable/conflicted, and tell them why.
        List<AppUser> stillValid = new ArrayList<>();
        for (AppUser assignee : List.copyOf(shift.getAssignedUsers())) {
            List<Shift> otherShifts = shiftRepository.findByAssignedUserIdExcluding(assignee.getId(), shiftId);
            AssignmentCheckResult check = constraintService.checkAssignment(assignee, shift, otherShifts);
            if (check.ok()) {
                stillValid.add(assignee);
            } else {
                String reasons = String.join(" ", check.violations().stream().map(v -> v.message()).toList());
                notificationService.send(assignee, NotificationType.SHIFT_UNASSIGNED,
                        "Removed from a shift due to a schedule change",
                        "You were removed from a shift at %s because: %s".formatted(shift.getLocation().getName(), reasons),
                        shift.getId(), null);
            }
        }
        shift.setAssignedUsers(new java.util.HashSet<>(stillValid));

        Shift saved = saveShift(shift);

        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SHIFT, String.valueOf(shiftId),
                "updated", before, ShiftMapper.toResponse(saved), saved.getLocation().getId());

        // Notify everyone still on the shift that something about it changed,
        // and let the swap workflow auto-cancel anything pending on it.
        for (AppUser assignee : stillValid) {
            notificationService.send(assignee, NotificationType.SHIFT_CHANGED,
                    "A shift you're on was updated", "Your shift at %s was changed by a manager.".formatted(saved.getLocation().getName()),
                    saved.getId(), null);
        }
        eventPublisher.publishEvent(new ShiftEditedEvent(this, shiftId));
        realtimeGateway.scheduleChanged(saved.getLocation().getId(), ShiftMapper.toResponse(saved));
        return saved;
    }

    @Transactional
    public Shift publish(Long shiftId, Long expectedVersion) {
        AppUser actor = currentUser.get();
        Shift shift = findById(shiftId);
        permissions.requireManageLocation(actor, shift.getLocation().getId());
        checkVersion(shift, expectedVersion);
        shift.setStatus(ShiftStatus.PUBLISHED);
        Shift saved = saveShift(shift);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SHIFT, String.valueOf(shiftId), "published",
                null, null, saved.getLocation().getId());
        for (AppUser assignee : saved.getAssignedUsers()) {
            notificationService.send(assignee, NotificationType.SCHEDULE_PUBLISHED, "Schedule published",
                    "Your shift at %s is now published.".formatted(saved.getLocation().getName()), saved.getId(), null);
        }
        realtimeGateway.scheduleChanged(saved.getLocation().getId(), ShiftMapper.toResponse(saved));
        return saved;
    }

    /** Publishes every DRAFT shift at a location whose start falls in the
     * given UTC week window in one shot — "Publish a week's schedule". */
    @Transactional
    public List<Shift> publishWeek(Long locationId, Instant weekStartUtc) {
        AppUser actor = currentUser.get();
        permissions.requireManageLocation(actor, locationId);
        List<Shift> shifts = listByLocationAndWeek(locationId, weekStartUtc).stream()
                .filter(s -> s.getStatus() == ShiftStatus.DRAFT)
                .toList();
        for (Shift s : shifts) {
            s.setStatus(ShiftStatus.PUBLISHED);
        }
        List<Shift> saved = shiftRepository.saveAll(shifts);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SHIFT, "location:" + locationId,
                "published_week", null, Map.of("count", saved.size(), "weekStart", weekStartUtc), locationId);
        for (Shift s : saved) {
            for (AppUser assignee : s.getAssignedUsers()) {
                notificationService.send(assignee, NotificationType.SCHEDULE_PUBLISHED, "Schedule published",
                        "Your week's schedule at %s is now published.".formatted(s.getLocation().getName()), s.getId(), null);
            }
        }
        if (!saved.isEmpty()) realtimeGateway.scheduleChanged(locationId, Map.of("weekPublished", weekStartUtc));
        return saved;
    }

    @Transactional
    public Shift unpublish(Long shiftId, Long expectedVersion, boolean overrideCutoff, String overrideReason) {
        AppUser actor = currentUser.get();
        Shift shift = findById(shiftId);
        permissions.requireManageLocation(actor, shift.getLocation().getId());
        checkVersion(shift, expectedVersion);
        enforcePublishCutoff(shift, actor, overrideCutoff, overrideReason);
        shift.setStatus(ShiftStatus.DRAFT);
        Shift saved = saveShift(shift);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SHIFT, String.valueOf(shiftId), "unpublished",
                null, null, saved.getLocation().getId());
        eventPublisher.publishEvent(new ShiftEditedEvent(this, shiftId));
        realtimeGateway.scheduleChanged(saved.getLocation().getId(), ShiftMapper.toResponse(saved));
        return saved;
    }

    /** Pure preview — no mutation, no lock. Doubles as the "what-if" overtime
     * check the brief asks for ("Ability to see 'what-if' impact before
     * confirming an assignment"). */
    public AssignmentCheckResult checkAssignment(Long shiftId, Long userId) {
        Shift shift = findById(shiftId);
        AppUser user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));
        List<Shift> otherShifts = shiftRepository.findByAssignedUserIdExcluding(userId, shiftId);
        return constraintService.checkAssignment(user, shift, otherShifts);
    }

    @Transactional
    public Shift assign(Long shiftId, AssignRequest req) {
        AppUser actor = currentUser.get();
        Shift preCheck = findById(shiftId);
        permissions.requireManageLocation(actor, preCheck.getLocation().getId());
        checkVersion(preCheck, req.expectedVersion());

        // Lock the STAFF MEMBER's row first, before re-reading anything that
        // depends on their current assignments — see UserRepository.lockById
        // javadoc for why this specific ordering is what makes concurrent
        // assignment attempts for the same person safe.
        AppUser user = userRepository.lockById(req.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + req.userId()));

        Shift shift = findById(shiftId); // same persistence-context instance as preCheck; kept as a
                                          // separate call for clarity. The property that actually
                                          // makes this race-safe is that findByAssignedUserIdExcluding
                                          // below is a live query run only after the user-row lock is
                                          // granted, so it always reflects whatever the other manager's
                                          // transaction already committed — and Shift's own @Version
                                          // (checked in saveShift()) catches the remaining case of two
                                          // managers racing to assign two DIFFERENT people to this SAME shift.
        if (shift.getAssignedUsers().stream().anyMatch(u -> u.getId().equals(user.getId()))) {
            throw new BusinessRuleException(user.getName() + " is already assigned to this shift.");
        }
        if (shift.getAssignedUsers().size() >= shift.getHeadcountNeeded()) {
            throw new BusinessRuleException("This shift is already fully staffed (" + shift.getHeadcountNeeded() + " needed).");
        }

        List<Shift> otherShifts = shiftRepository.findByAssignedUserIdExcluding(user.getId(), shiftId);
        AssignmentCheckResult check = constraintService.checkAssignment(user, shift, otherShifts);

        if (!check.ok()) {
            boolean onlySeventhDay = check.violations().stream()
                    .allMatch(v -> v.severity() == Severity.WARNING || v.code() == ViolationCode.SEVENTH_CONSECUTIVE_DAY);
            boolean canOverride = onlySeventhDay && req.override() && req.overrideReason() != null
                    && !req.overrideReason().isBlank()
                    && (actor.getRole() == Role.MANAGER || actor.getRole() == Role.ADMIN);
            if (!canOverride) {
                throw new ConstraintViolationException(check);
            }
        }

        shift.getAssignedUsers().add(user);
        Shift saved = saveShift(shift);

        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SHIFT, String.valueOf(shiftId),
                req.override() ? "assigned_with_override" : "assigned",
                null,
                Map.of("userId", user.getId(), "userName", user.getName(), "overrideReason",
                        req.overrideReason() == null ? "" : req.overrideReason()),
                saved.getLocation().getId());

        notificationService.send(user, NotificationType.SHIFT_ASSIGNED, "New shift assigned",
                "You've been assigned a shift at %s.".formatted(saved.getLocation().getName()), saved.getId(), null);
        realtimeGateway.scheduleChanged(saved.getLocation().getId(), ShiftMapper.toResponse(saved));
        maybeWarnOvertime(saved, user, otherShifts);
        return saved;
    }

    @Transactional
    public Shift unassign(Long shiftId, UnassignRequest req) {
        AppUser actor = currentUser.get();
        Shift shift = findById(shiftId);
        permissions.requireManageLocation(actor, shift.getLocation().getId());
        checkVersion(shift, req.expectedVersion());

        AppUser user = userRepository.findById(req.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + req.userId()));
        boolean removed = shift.getAssignedUsers().removeIf(u -> u.getId().equals(user.getId()));
        if (!removed) throw new BusinessRuleException(user.getName() + " isn't assigned to this shift.");

        Shift saved = saveShift(shift);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SHIFT, String.valueOf(shiftId),
                "unassigned", Map.of("userId", user.getId()), null, saved.getLocation().getId());
        notificationService.send(user, NotificationType.SHIFT_UNASSIGNED, "Removed from a shift",
                "You've been removed from a shift at %s.".formatted(saved.getLocation().getName()), saved.getId(), null);
        realtimeGateway.scheduleChanged(saved.getLocation().getId(), ShiftMapper.toResponse(saved));
        return saved;
    }

    @Transactional
    public void delete(Long shiftId) {
        AppUser actor = currentUser.get();
        Shift shift = findById(shiftId);
        permissions.requireManageLocation(actor, shift.getLocation().getId());
        if (shift.getStatus() == ShiftStatus.PUBLISHED) {
            throw new BusinessRuleException("Unpublish this shift before deleting it.");
        }
        if (!shift.getAssignedUsers().isEmpty()) {
            throw new BusinessRuleException("Unassign all staff from this shift before deleting it.");
        }
        eventPublisher.publishEvent(new ShiftEditedEvent(this, shiftId));
        Long locationId = shift.getLocation().getId();
        shiftRepository.delete(shift);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SHIFT, String.valueOf(shiftId), "deleted",
                null, null, locationId);
        realtimeGateway.scheduleChanged(locationId, Map.of("deletedShiftId", shiftId));
    }

    private void checkVersion(Shift shift, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(shift.getVersion())) {
            throw new ConflictException("Someone else already changed this shift. Refresh and try again.");
        }
    }

    /** Every mutating save goes through here so a losing optimistic-lock race
     * (two managers editing/assigning the same shift at once, each having
     * read the same version) always surfaces as the same clear ConflictException
     * — whether or not the caller bothered to pass expectedVersion up front. */
    private Shift saveShift(Shift shift) {
        try {
            return shiftRepository.save(shift);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
            realtimeGateway.assignmentConflict(shift.getId(), java.util.Map.of("message", "Someone else already changed this shift."));
            throw new ConflictException("Someone else already changed this shift. Refresh and try again.");
        }
    }

    /** "Unpublish/edit a schedule before a configurable cutoff (default: 48
     * hours before the shift)." Editing/unpublishing a PUBLISHED shift inside
     * the cutoff window is blocked for managers unless they supply an
     * explicit override reason — the brief doesn't specify who may override
     * this particular rule (only the 7th-consecutive-day rule explicitly
     * names a manager override), so allowing MANAGER+ADMIN to override here
     * too, with a documented reason recorded in the audit log, is our
     * assumption; see README. */
    private void enforcePublishCutoff(Shift shift, AppUser actor, boolean overrideCutoff, String overrideReason) {
        if (shift.getStatus() != ShiftStatus.PUBLISHED) return;
        ConstraintThresholds t = settingsService.get();
        double hoursUntilStart = TimeUtil.durationHours(Instant.now(), shift.getStartUtc());
        if (hoursUntilStart < t.getPublishEditCutoffHours()) {
            boolean allowed = overrideCutoff && overrideReason != null && !overrideReason.isBlank();
            if (!allowed) {
                throw new BusinessRuleException(
                        "This shift starts in less than %s hours and is already published — editing requires an override reason."
                                .formatted((int) t.getPublishEditCutoffHours()));
            }
        }
    }

    private boolean isPremium(Shift shift) {
        ZonedDateTime start = TimeUtil.zoned(shift.getStartUtc(), shift.getLocation().getTimezone());
        DayOfWeek dow = start.getDayOfWeek();
        return (dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY) && start.getHour() >= PREMIUM_START_HOUR;
    }

    private void maybeWarnOvertime(Shift shift, AppUser user, List<Shift> otherShifts) {
        ConstraintThresholds t = settingsService.get();
        double weeklyTotal = TimeUtil.shiftDurationHours(shift) + otherShifts.stream().mapToDouble(TimeUtil::shiftDurationHours).sum();
        if (weeklyTotal >= t.getWeeklyWarningHours()) {
            for (AppUser manager : userRepository.findManagersOfLocation(shift.getLocation().getId())) {
                notificationService.send(manager, NotificationType.OVERTIME_WARNING, "Overtime risk",
                        "%s is projected at %.1f hours this week after this assignment.".formatted(user.getName(), weeklyTotal),
                        shift.getId(), null);
            }
        }
    }
}
