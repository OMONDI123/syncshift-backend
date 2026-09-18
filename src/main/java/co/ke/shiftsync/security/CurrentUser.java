package co.ke.shiftsync.security;

import co.ke.shiftsync.user.AppUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Small helper so services/controllers can grab "who is calling right now"
 * without threading Authentication objects through every method signature. */
@Component
public class CurrentUser {

    public AppUser get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new IllegalStateException("No authenticated user in context.");
        }
        return principal.getUser();
    }

    public Long id() {
        return get().getId();
    }
}
