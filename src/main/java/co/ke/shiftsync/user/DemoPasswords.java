package co.ke.shiftsync.user;

import co.ke.shiftsync.common.Role;

import java.util.Map;

/** One password per ROLE (not per person) for the seeded demo accounts —
 * mirrors the frontend prototype's `lib/auth.ts` DEMO_PASSWORDS exactly, so
 * the same login screen copy/paste works against this real backend. Real
 * production accounts would obviously get individual passwords / SSO; this
 * is purely to make the assessment's "log in as each role" deliverable easy
 * to demo without ten different credentials to remember. */
public class DemoPasswords {
    private static final Map<Role, String> PASSWORDS = Map.of(
            Role.ADMIN, "Admin@123",
            Role.MANAGER, "Manager@123",
            Role.STAFF, "Staff@123"
    );

    public static String forRole(Role role) {
        return PASSWORDS.get(role);
    }
}
