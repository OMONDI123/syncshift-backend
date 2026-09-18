package co.ke.shiftsync.user;

import co.ke.shiftsync.common.NotificationChannel;
import co.ke.shiftsync.common.Role;
import co.ke.shiftsync.location.Location;
import co.ke.shiftsync.skill.Skill;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A person in the system. Table is "app_users" (not "users") because "user"
 * is a reserved word in several SQL dialects including Postgres and H2.
 *
 * Deactivated accounts are kept forever (never hard-deleted) so historical
 * shift/audit records stay intact — see setActive() usage in UserService and
 * the "de-certified staff" ambiguity note in README.md.
 */
@Entity
@Table(name = "app_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"skills", "certifiedLocations", "managedLocations"})
@ToString(exclude = {"passwordHash", "skills", "certifiedLocations", "managedLocations"})
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Deterministic accent color for avatar initials in the UI. */
    private String avatarColor;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_skills", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "skill_id"))
    @Builder.Default
    private Set<Skill> skills = new HashSet<>();

    /** Locations this STAFF member is certified to work at. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_certified_locations", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "location_id"))
    @Builder.Default
    private Set<Location> certifiedLocations = new HashSet<>();

    /** Locations this MANAGER runs. Empty/irrelevant for ADMIN (sees everything) and STAFF. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_managed_locations", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "location_id"))
    @Builder.Default
    private Set<Location> managedLocations = new HashSet<>();

    /** Used by fairness analytics to compare actual vs. desired hours. STAFF only. */
    private Integer desiredWeeklyHours;

    /** Hourly pay rate. Not in the original brief; added so the overtime-cost
     * dashboard has something real to project against instead of a placeholder
     * of $0 for everyone — documented as an assumption in README.md. */
    private BigDecimal hourlyRate;

    /** IANA timezone this person's own clock/availability is anchored to —
     * independent of any location's timezone. See AvailabilityService and the
     * "Timezone Tangle" scenario. */
    @Column(nullable = false)
    private String homeTimezone;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationChannel notificationChannel = NotificationChannel.IN_APP_ONLY;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    @PrePersist
    void onCreate() {
        dateCreated = LocalDateTime.now();
        dateUpdated = dateCreated;
    }

    @PreUpdate
    void onUpdate() {
        dateUpdated = LocalDateTime.now();
    }
}
