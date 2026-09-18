package co.ke.shiftsync.auth;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record LoginResponse(String token, long expiresInMinutes, UserSummary user) {}

    public record UserSummary(Long id, String name, String email, String role, String avatarColor) {}
}
