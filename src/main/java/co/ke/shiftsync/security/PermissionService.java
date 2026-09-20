package co.ke.shiftsync.security;

import co.ke.shiftsync.common.Role;
import co.ke.shiftsync.user.AppUser;
import org.springframework.stereotype.Service;

/** Fine-grained, per-resource permission checks that go beyond a static ROLE_*
 * authority — e.g. a MANAGER can only touch locations they run. Every
 * mutating service method calls these directly (not just the controller
 * layer), matching the frontend's `lib/auth.ts` design note: "hiding a
 * button is not access control". */
@Service
public class PermissionService {

    public boolean canManageLocation(AppUser user, Long locationId) {
        if (user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        return user.getRole() == Role.MANAGER
                && user.getManagedLocations().stream().anyMatch(l -> l.getId().equals(locationId));
    }

    public boolean canApproveSwapsAt(AppUser user, Long locationId) {
        return canManageLocation(user, locationId);
    }

    /** Who's allowed to see a location's on-duty roster: an admin (any
     * location), a manager (their own managed locations, same rule as
     * canManageLocation), or a staff member checking a location they're
     * actually certified at (they need this to know their own clock-in
     * state, not to browse other locations' rosters). This didn't exist
     * before — PresenceController.onDutyAt had no permission check at all,
     * open to any authenticated user for any location. */
    public boolean canViewPresenceAt(AppUser user, Long locationId) {
        if (user == null) return false;
        if (canManageLocation(user, locationId)) return true;
        return user.getRole() == Role.STAFF
                && user.getCertifiedLocations().stream().anyMatch(l -> l.getId().equals(locationId));
    }

    public void requireViewPresence(AppUser user, Long locationId) {
        if (!canViewPresenceAt(user, locationId)) {
            throw new co.ke.shiftsync.common.exceptions.UnauthorizedActionException(
                    "You don't have permission to view this location's on-duty roster.");
        }
    }

    public boolean canViewAuditLog(AppUser user) {
        return user != null && (user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER);
    }

    public boolean canExportAuditLog(AppUser user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    public boolean canManageUsers(AppUser user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    public boolean canManageSettings(AppUser user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    public void requireManageLocation(AppUser user, Long locationId) {
        if (!canManageLocation(user, locationId)) {
            throw new co.ke.shiftsync.common.exceptions.UnauthorizedActionException(
                    "You don't have permission to edit this location's schedule.");
        }
    }

    public void requireManageUsers(AppUser user) {
        if (!canManageUsers(user)) {
            throw new co.ke.shiftsync.common.exceptions.UnauthorizedActionException(
                    "Only admins can manage user accounts.");
        }
    }

    public void requireManageSettings(AppUser user) {
        if (!canManageSettings(user)) {
            throw new co.ke.shiftsync.common.exceptions.UnauthorizedActionException(
                    "Only admins can manage system setup.");
        }
    }
}
