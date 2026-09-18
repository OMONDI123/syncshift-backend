package co.ke.shiftsync.availability;

import co.ke.shiftsync.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * A staff member's self-declared availability, evaluated in THEIR OWN
 * homeTimezone — never a shift location's timezone. See
 * ConstraintService.isWithinAvailability for why: a Seattle-based person
 * certified to also work a Miami shift didn't move to Eastern time when
 * they said "9am-5pm".
 *
 * RECURRING rows repeat weekly by dayOfWeek (0=Sunday..6=Saturday, matching
 * java.time.DayOfWeek.getValue() % 7 convention used in AvailabilityService).
 * EXCEPTION rows are keyed by a specific calendar date (in the user's home
 * zone) and override any recurring window for that date, whether marking
 * extra availability or an explicit unavailable day.
 *
 * DST: because we store day-of-week + minutes-from-midnight rather than a
 * fixed UTC offset, evaluation always re-derives the correct local wall-clock
 * time at read time via ZoneId — the same guarantee java.time makes for any
 * zoned datetime arithmetic across a DST transition.
 */
@Entity
@Table(name = "availability_windows")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AvailabilityType type;

    /** RECURRING only. 0=Sunday..6=Saturday. */
    private Integer dayOfWeek;

    /** EXCEPTION only. Calendar date in the user's home timezone. */
    private LocalDate date;

    @Column(nullable = false)
    private Integer startMinutes;

    @Column(nullable = false)
    private Integer endMinutes;

    /** false = explicit "unavailable" exception overriding a recurring window. */
    @Builder.Default
    private boolean available = true;
}
