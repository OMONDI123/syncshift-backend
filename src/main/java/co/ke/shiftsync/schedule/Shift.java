package co.ke.shiftsync.schedule;

import co.ke.shiftsync.location.Location;
import co.ke.shiftsync.skill.Skill;
import co.ke.shiftsync.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A block of work at a location. All instants are stored as UTC (startUtc /
 * endUtc) and only ever converted to a location's IANA timezone at display
 * or evaluation time — see ConstraintService and the frontend's
 * time-handling convention this mirrors. An overnight shift (e.g. 11pm-3am)
 * is a single row whose startUtc/endUtc simply cross local midnight; no
 * special-casing is needed because everything downstream operates on the
 * UTC instant, not on a "day" field.
 *
 * @Version gives every shift optimistic-concurrency protection for free:
 * if two managers load the same shift and both try to mutate it, the second
 * write fails with a stale-version error, which ShiftService translates into
 * a clear "someone else just changed this" ConflictException — this is the
 * mechanism behind the "Simultaneous Assignment" evaluation scenario.
 */
@Entity
@Table(name = "shifts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "assignedUsers")
@ToString(exclude = "assignedUsers")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id")
    private Skill skillRequired;

    @Column(nullable = false)
    private int headcountNeeded;

    @Column(nullable = false)
    private Instant startUtc;

    @Column(nullable = false)
    private Instant endUtc;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ShiftStatus status = ShiftStatus.DRAFT;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "shift_assignments", joinColumns = @JoinColumn(name = "shift_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private Set<AppUser> assignedUsers = new HashSet<>();

    /** True when the shift starts Friday or Saturday evening (local to the
     * location) — see FairnessService.PREMIUM_START_HOUR for the exact cutoff.
     * Recomputed whenever start time or location changes. */
    @Builder.Default
    private boolean premium = false;

    @Column(length = 2000)
    private String notes;

    @Version
    private Long version;
}
