package co.ke.shiftsync.auth;

import co.ke.shiftsync.auth.AuthDtos.*;
import co.ke.shiftsync.security.AppUserPrincipal;
import co.ke.shiftsync.security.CurrentUser;
import co.ke.shiftsync.security.JwtService;
import co.ke.shiftsync.user.AppUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/**
 * Real, signed-JWT login — see DemoPasswords for the seeded per-role demo
 * credentials used across every seeded account, exactly one password per
 * role, matching the frontend prototype's login screen.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CurrentUser currentUser;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            var auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password()));
            AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();
            AppUser user = principal.getUser();
            String token = jwtService.generateToken(principal, user.getId(), user.getRole().name());
            return new LoginResponse(token, jwtService.expirationMinutes(), toSummary(user));
        } catch (org.springframework.security.core.AuthenticationException ex) {
            throw new BadCredentialsException("Incorrect email or password.");
        }
    }

    @GetMapping("/me")
    public UserSummary me() {
        return toSummary(currentUser.get());
    }

    private UserSummary toSummary(AppUser user) {
        return new UserSummary(user.getId(), user.getName(), user.getEmail(), user.getRole().name(), user.getAvatarColor());
    }
}
