package co.ke.shiftsync.presence;

import co.ke.shiftsync.location.Location;
import co.ke.shiftsync.schedule.Shift;
import co.ke.shiftsync.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Requirement #6: "on-duty now" dashboard. A null clockOutUtc means the
 * person is currently on the clock. */
@Entity
@Table(name = "clock_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClockRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(nullable = false)
    private Instant clockInUtc;

    private Instant clockOutUtc;
}
