package co.ke.shiftsync.swap;

import co.ke.shiftsync.audit.AuditService;
import co.ke.shiftsync.common.AuditEntityType;
import co.ke.shiftsync.common.exceptions.BusinessRuleException;
import co.ke.shiftsync.common.exceptions.NotFoundException;
import co.ke.shiftsync.common.exceptions.UnauthorizedActionException;
import co.ke.shiftsync.notification.NotificationService;
import co.ke.shiftsync.notification.NotificationType;
import co.ke.shiftsync.schedule.ConstraintViolationException;
import co.ke.shiftsync.schedule.Shift;
import co.ke.shiftsync.schedule.ShiftDtos.AssignRequest;
import co.ke.shiftsync.schedule.ShiftDtos.UnassignRequest;
import co.ke.shiftsync.schedule.ShiftRepository;
import co.ke.shiftsync.schedule.ShiftService;
import co.ke.shiftsync.schedule.events.ShiftEditedEvent;
import co.ke.shiftsync.security.CurrentUser;
import co.ke.shiftsync.security.PermissionService;
import co.ke.shiftsync.settings.SettingsService;
import co.ke.shiftsync.user.AppUser;
import co.ke.shiftsync.user.UserRepository;
import co.ke.shiftsync.ws.RealtimeGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Requirement #3: swap/drop workflow exactly as designed in the frontend
 * prototype (`store/swapStore.ts`), including the two edge cases the brief
 * calls out by name — auto-cancel on shift edit, and the 3-pending-request
 * cap — plus the "Regret Swap" behavior: the original assignment is only
 * ever touched at manager-approval time, so a pre-approval cancel is a pure
 * no-op on the schedule.
 */
@Service
@RequiredArgsConstructor
public class SwapService {

    private static final Set<SwapStatus> PENDING_STATUSES = Set.of(SwapStatus.PENDING_PARTNER, SwapStatus.OPEN, SwapStatus.PENDING_MANAGER);

    private final SwapRepository repository;
    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final ShiftService shiftService;
    private final SettingsService settingsService;
    private final PermissionService permissions;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final RealtimeGateway realtimeGateway;

    public long pendingCountFor(Long userId) {
        return repository.findByRequestedById(userId).stream()
                .filter(s -> PENDING_STATUSES.contains(s.getStatus()))
                .count();
    }

    public List<SwapRequest> forShift(Long shiftId) {
        return repository.findByShiftId(shiftId);
    }

    public List<SwapRequest> pendingApprovalsFor(AppUser manager) {
        return repository.findByStatus(SwapStatus.PENDING_MANAGER).stream()
                .filter(s -> permissions.canApproveSwapsAt(manager, s.getShift().getLocation().getId()))
                .toList();
    }

    /** Backs the frontend's Marketplace page: every open drop a staff
     * member could actually pick up (their own certified locations), or
     * every open drop system-wide for a manager/admin keeping an eye on
     * coverage gaps across their locations. */
    public List<SwapRequest> openDropsFor(AppUser user) {
        List<SwapRequest> open = repository.findByStatus(SwapStatus.OPEN);
        if (user.getRole() == co.ke.shiftsync.common.Role.ADMIN) {
            return open;
        }
        if (user.getRole() == co.ke.shiftsync.common.Role.MANAGER) {
            return open.stream()
                    .filter(s -> permissions.canApproveSwapsAt(user, s.getShift().getLocation().getId()))
                    .toList();
        }
        Set<Long> certifiedLocationIds = user.getCertifiedLocations().stream().map(l -> l.getId()).collect(java.util.stream.Collectors.toSet());
        return open.stream().filter(s -> certifiedLocationIds.contains(s.getShift().getLocation().getId())).toList();
    }

    /** Backs the frontend's "My Swaps" list: everything this person has
     * requested, or has been asked to be the swap partner on, in any
     * status — not just what's still pending. */
    public List<SwapRequest> mineFor(Long userId) {
        List<SwapRequest> mine = new java.util.ArrayList<>(repository.findByRequestedById(userId));
        for (SwapRequest s : repository.findByPartnerId(userId)) {
            if (mine.stream().noneMatch(existing -> existing.getId().equals(s.getId()))) {
                mine.add(s);
            }
        }
        return mine;
    }

    @Transactional
    public SwapRequest requestSwap(Long shiftId, Long partnerUserId) {
        AppUser requester = currentUser.get();
        enforcePendingCap(requester.getId());
        Shift shift = shiftRepository.findById(shiftId).orElseThrow(() -> new NotFoundException("Shift not found: " + shiftId));
        AppUser partner = userRepository.findById(partnerUserId).orElseThrow(() -> new NotFoundException("User not found: " + partnerUserId));
        if (shift.getAssignedUsers().stream().noneMatch(u -> u.getId().equals(requester.getId()))) {
            throw new BusinessRuleException("You can only request a swap on a shift you're assigned to.");
        }

        SwapRequest swap = SwapRequest.builder()
                .kind(SwapKind.SWAP).shift(shift).requestedBy(requester).partner(partner)
                .status(SwapStatus.PENDING_PARTNER).createdAtUtc(Instant.now())
                .build();
        swap.getHistory().add(SwapHistoryEntry.builder().atUtc(Instant.now()).event("Swap requested").byUserId(requester.getId()).build());
        SwapRequest saved = repository.save(swap);

        notificationService.send(partner, NotificationType.SWAP_REQUESTED, "Swap request",
                "%s wants to swap a shift with you. Review it in the marketplace.".formatted(requester.getName()), shiftId, saved.getId());
        auditService.log(requester.getId(), requester.getName(), AuditEntityType.SWAP, String.valueOf(saved.getId()), "requested_swap",
                null, null, shift.getLocation().getId());
        broadcast(saved);
        return saved;
    }

    @Transactional
    public SwapRequest requestDrop(Long shiftId) {
        AppUser requester = currentUser.get();
        enforcePendingCap(requester.getId());
        Shift shift = shiftRepository.findById(shiftId).orElseThrow(() -> new NotFoundException("Shift not found: " + shiftId));
        if (shift.getAssignedUsers().stream().noneMatch(u -> u.getId().equals(requester.getId()))) {
            throw new BusinessRuleException("You can only drop a shift you're assigned to.");
        }

        int expiryHours = settingsService.get().getDropExpiryHoursBeforeShift();
        Instant expiresAt = shift.getStartUtc().minusSeconds(expiryHours * 3600L);

        SwapRequest swap = SwapRequest.builder()
                .kind(SwapKind.DROP).shift(shift).requestedBy(requester)
                .status(SwapStatus.OPEN).createdAtUtc(Instant.now()).expiresAtUtc(expiresAt)
                .build();
        swap.getHistory().add(SwapHistoryEntry.builder().atUtc(Instant.now()).event("Offered up for grabs").byUserId(requester.getId()).build());
        SwapRequest saved = repository.save(swap);

        auditService.log(requester.getId(), requester.getName(), AuditEntityType.SWAP, String.valueOf(saved.getId()), "requested_drop",
                null, null, shift.getLocation().getId());
        notifyEligiblePickups(saved, requester);
        broadcast(saved);
        return saved;
    }

    @Transactional
    public SwapRequest partnerAccept(Long swapId) {
        AppUser actor = currentUser.get();
        SwapRequest swap = get(swapId);
        if (swap.getStatus() != SwapStatus.PENDING_PARTNER) {
            throw new BusinessRuleException("This request is no longer waiting on a partner response.");
        }
        if (!swap.getPartner().getId().equals(actor.getId())) {
            throw new UnauthorizedActionException("This swap wasn't sent to you.");
        }
        swap.setStatus(SwapStatus.PENDING_MANAGER);
        swap.getHistory().add(SwapHistoryEntry.builder().atUtc(Instant.now()).event("Partner accepted").byUserId(actor.getId()).build());
        SwapRequest saved = repository.save(swap);
        notificationService.send(saved.getRequestedBy(), NotificationType.SWAP_ACCEPTED, "Swap accepted",
                "%s accepted your swap request. It's now waiting on a manager to approve it."
                        .formatted(actor.getName()), saved.getShift().getId(), swapId);
        notifyManagers(saved, "Swap needs your approval", "A shift swap was accepted by both staff and is waiting on you.");
        broadcast(saved);
        return saved;
    }

    @Transactional
    public SwapRequest pickUpDrop(Long swapId) {
        AppUser picker = currentUser.get();
        SwapRequest swap = get(swapId);
        if (swap.getStatus() != SwapStatus.OPEN) {
            throw new BusinessRuleException("This shift is no longer available to pick up.");
        }
        swap.setPartner(picker);
        swap.setStatus(SwapStatus.PENDING_MANAGER);
        swap.getHistory().add(SwapHistoryEntry.builder().atUtc(Instant.now()).event("Picked up").byUserId(picker.getId()).build());
        SwapRequest saved = repository.save(swap);
        notificationService.send(saved.getRequestedBy(), NotificationType.DROP_CLAIMED, "Your dropped shift was picked up",
                "%s picked up the shift you dropped. It's now waiting on a manager to approve."
                        .formatted(picker.getName()), saved.getShift().getId(), swapId);
        notifyManagers(saved, "Drop pickup needs your approval", "Someone picked up an open shift and is waiting on you.");
        broadcast(saved);
        return saved;
    }

    @Transactional
    public SwapRequest managerApprove(Long swapId) {
        AppUser manager = currentUser.get();
        SwapRequest swap = get(swapId);
        if (swap.getStatus() != SwapStatus.PENDING_MANAGER) {
            throw new BusinessRuleException("This request is no longer pending.");
        }
        Long locationId = swap.getShift().getLocation().getId();
        if (!permissions.canApproveSwapsAt(manager, locationId)) {
            auditService.log(manager.getId(), manager.getName(), AuditEntityType.SWAP, String.valueOf(swapId),
                    "denied_swap_approval_unauthorized_location", null, null, locationId);
            throw new UnauthorizedActionException("You don't manage this shift's location.");
        }

        if (swap.getPartner() != null) {
            try {
                shiftService.unassign(swap.getShift().getId(), new UnassignRequest(swap.getRequestedBy().getId(), null));
                shiftService.assign(swap.getShift().getId(), new AssignRequest(swap.getPartner().getId(), null, false, null));
            } catch (ConstraintViolationException ex) {
                throw new BusinessRuleException(
                        "Approving this would now break a scheduling rule for the incoming staff member: "
                                + ex.getResult().violations().stream().map(v -> v.message()).findFirst().orElse("constraint violated"));
            }
        }

        swap.setStatus(SwapStatus.APPROVED);
        swap.setResolvedAtUtc(Instant.now());
        swap.getHistory().add(SwapHistoryEntry.builder().atUtc(Instant.now()).event("Approved by manager").byUserId(manager.getId()).build());
        SwapRequest saved = repository.save(swap);

        notificationService.send(swap.getRequestedBy(), NotificationType.SWAP_APPROVED, "Swap approved",
                "Your shift change was approved by a manager.", swap.getShift().getId(), swapId);
        if (swap.getPartner() != null) {
            notificationService.send(swap.getPartner(), NotificationType.SWAP_APPROVED, "Swap approved",
                    "Your shift change was approved by a manager.", swap.getShift().getId(), swapId);
        }
        auditService.log(manager.getId(), manager.getName(), AuditEntityType.SWAP, String.valueOf(swapId), "approved",
                null, null, locationId);
        broadcast(saved);
        return saved;
    }

    @Transactional
    public SwapRequest managerReject(Long swapId, String reason) {
        AppUser manager = currentUser.get();
        SwapRequest swap = get(swapId);
        Long locationId = swap.getShift().getLocation().getId();
        if (!permissions.canApproveSwapsAt(manager, locationId)) {
            auditService.log(manager.getId(), manager.getName(), AuditEntityType.SWAP, String.valueOf(swapId),
                    "denied_swap_rejection_unauthorized_location", null, null, locationId);
            throw new UnauthorizedActionException("You don't manage this shift's location.");
        }
        swap.setStatus(SwapStatus.REJECTED);
        swap.setResolvedAtUtc(Instant.now());
        swap.getHistory().add(SwapHistoryEntry.builder().atUtc(Instant.now()).event("Rejected: " + reason).byUserId(manager.getId()).build());
        SwapRequest saved = repository.save(swap);
        notificationService.send(swap.getRequestedBy(), NotificationType.SWAP_REJECTED, "Swap request rejected", reason,
                swap.getShift().getId(), swapId);
        auditService.log(manager.getId(), manager.getName(), AuditEntityType.SWAP, String.valueOf(swapId), "rejected",
                null, reason, locationId);
        broadcast(saved);
        return saved;
    }

    /** "Regret Swap" scenario: cancelling before manager approval never
     * touches the schedule, because approval is the only place assignments
     * actually change — see managerApprove(). */
    @Transactional
    public SwapRequest requesterCancel(Long swapId) {
        AppUser actor = currentUser.get();
        SwapRequest swap = get(swapId);
        if (!swap.getRequestedBy().getId().equals(actor.getId())) {
            throw new UnauthorizedActionException("Only the person who made this request can cancel it.");
        }
        if (!PENDING_STATUSES.contains(swap.getStatus())) {
            throw new BusinessRuleException("This request is already resolved.");
        }
        swap.setStatus(SwapStatus.CANCELLED);
        swap.setResolvedAtUtc(Instant.now());
        swap.getHistory().add(SwapHistoryEntry.builder().atUtc(Instant.now()).event("Cancelled by requester").byUserId(actor.getId()).build());
        SwapRequest saved = repository.save(swap);
        if (swap.getPartner() != null) {
            notificationService.send(swap.getPartner(), NotificationType.SWAP_CANCELLED, "Swap cancelled",
                    "The other staff member cancelled this swap before it was approved.", swap.getShift().getId(), swapId);
        }
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SWAP, String.valueOf(swapId), "cancelled_by_requester");
        broadcast(saved);
        return saved;
    }

    /** Listens for manager edits to a shift and auto-cancels anything
     * pending on it — the exact edge case named in the brief. */
    @EventListener
    @Transactional
    public void onShiftEdited(ShiftEditedEvent event) {
        List<SwapRequest> affected = repository.findByShiftId(event.getShiftId()).stream()
                .filter(s -> PENDING_STATUSES.contains(s.getStatus()))
                .toList();
        for (SwapRequest s : affected) {
            s.setStatus(SwapStatus.CANCELLED);
            s.setResolvedAtUtc(Instant.now());
            s.getHistory().add(SwapHistoryEntry.builder().atUtc(Instant.now()).event("Auto-cancelled: manager edited this shift").build());
            repository.save(s);
            notificationService.send(s.getRequestedBy(), NotificationType.SWAP_CANCELLED, "Swap request cancelled",
                    "A manager edited this shift, so the pending swap was automatically cancelled.", s.getShift().getId(), s.getId());
            if (s.getPartner() != null) {
                notificationService.send(s.getPartner(), NotificationType.SWAP_CANCELLED, "Swap request cancelled",
                        "A manager edited this shift, so the pending swap was automatically cancelled.", s.getShift().getId(), s.getId());
            }
            broadcast(s);
        }
    }

    /** Runs every 15 minutes: drops nobody claimed inside the configured
     * expiry window (default 24h before the shift) quietly expire. */
    @Scheduled(fixedRate = 15 * 60 * 1000)
    @Transactional
    public void expireStaleDrops() {
        Instant now = Instant.now();
        List<SwapRequest> stale = repository.findByStatus(SwapStatus.OPEN).stream()
                .filter(s -> s.getExpiresAtUtc() != null && s.getExpiresAtUtc().isBefore(now))
                .toList();
        for (SwapRequest s : stale) {
            s.setStatus(SwapStatus.EXPIRED);
            s.getHistory().add(SwapHistoryEntry.builder().atUtc(now).event("Expired — unclaimed before the shift").build());
            repository.save(s);
            notificationService.send(s.getRequestedBy(), NotificationType.DROP_EXPIRED, "Dropped shift expired unclaimed",
                    "Nobody picked up the shift you dropped, and it's now too close to the start time to claim.",
                    s.getShift().getId(), s.getId());
            broadcast(s);
        }
    }

    private void enforcePendingCap(Long userId) {
        int max = settingsService.get().getMaxPendingSwapsPerStaff();
        if (pendingCountFor(userId) >= max) {
            throw new BusinessRuleException(
                    "You already have %d pending swap or drop requests. Resolve one before requesting another.".formatted(max));
        }
    }

    private SwapRequest get(Long swapId) {
        return repository.findById(swapId).orElseThrow(() -> new NotFoundException("Swap request not found: " + swapId));
    }

    private void notifyManagers(SwapRequest swap, String title, String body) {
        for (AppUser manager : userRepository.findManagersOfLocation(swap.getShift().getLocation().getId())) {
            notificationService.send(manager, NotificationType.APPROVAL_NEEDED, title, body, swap.getShift().getId(), swap.getId());
        }
    }

    /** A drop is only useful to staff who could actually pick it up:
     * certified at the shift's location, excluding the person who dropped
     * it. This doesn't run the full constraint check (skill/availability/
     * rest) — that's still enforced at pickUpDrop() / managerApprove() time —
     * it's just "who should hear a shift became available", matching the
     * brief's "swap request updates" notification requirement. */
    private void notifyEligiblePickups(SwapRequest drop, AppUser requester) {
        Long locationId = drop.getShift().getLocation().getId();
        for (AppUser staff : userRepository.findStaffCertifiedAtLocation(locationId)) {
            if (staff.getId().equals(requester.getId())) continue;
            notificationService.send(staff, NotificationType.DROP_POSTED, "A shift is up for grabs",
                    "%s dropped a shift at %s. Check the marketplace if you're free."
                            .formatted(requester.getName(), drop.getShift().getLocation().getName()),
                    drop.getShift().getId(), drop.getId());
        }
    }

    private void broadcast(SwapRequest swap) {
        realtimeGateway.swapChanged(swap.getShift().getLocation().getId(), SwapMapper.toResponse(swap));
    }
}
