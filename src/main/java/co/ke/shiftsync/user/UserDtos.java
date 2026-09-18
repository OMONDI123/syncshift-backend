package co.ke.shiftsync.user;

import co.ke.shiftsync.common.NotificationChannel;
import co.ke.shiftsync.common.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public class UserDtos {

    public record CreateUserRequest(
            @NotBlank(message = "Name is required") String name,
            @NotBlank(message = "Email is required") @Email(message = "Must be a valid email") String email,
            @NotNull(message = "Role is required") Role role,
            Set<String> skillKeys,
            Set<Long> certifiedLocationIds,
            Set<Long> managedLocationIds,
            Integer desiredWeeklyHours,
            BigDecimal hourlyRate,
            @NotBlank(message = "Home timezone is required") String homeTimezone
    ) {}

    /** All fields optional/nullable — only non-null fields are applied (PATCH semantics). */
    public record UpdateUserRequest(
            String name,
            String email,
            Role role,
            Set<String> skillKeys,
            Set<Long> certifiedLocationIds,
            Set<Long> managedLocationIds,
            Integer desiredWeeklyHours,
            BigDecimal hourlyRate,
            String homeTimezone,
            NotificationChannel notificationChannel
    ) {}

    public record UserResponse(
            Long id, String name, String email, Role role, String avatarColor,
            List<String> skills, List<Long> certifiedLocationIds, List<Long> managedLocationIds,
            Integer desiredWeeklyHours, BigDecimal hourlyRate, String homeTimezone,
            NotificationChannel notificationChannel, boolean active
    ) {
        public static UserResponse from(AppUser u) {
            return new UserResponse(
                    u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getAvatarColor(),
                    u.getSkills().stream().map(co.ke.shiftsync.skill.Skill::getKey).sorted().toList(),
                    u.getCertifiedLocations().stream().map(co.ke.shiftsync.location.Location::getId).sorted().toList(),
                    u.getManagedLocations().stream().map(co.ke.shiftsync.location.Location::getId).sorted().toList(),
                    u.getDesiredWeeklyHours(), u.getHourlyRate(), u.getHomeTimezone(),
                    u.getNotificationChannel(), u.isActive()
            );
        }
    }

    public record SetActiveRequest(@NotNull Boolean active) {}
}
