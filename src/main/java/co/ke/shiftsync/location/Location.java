package co.ke.shiftsync.location;

import jakarta.persistence.*;
import lombok.*;

/** A Coastal Eats restaurant. Timezone is the IANA zone id (e.g. "America/Los_Angeles")
 * that every shift at this location is displayed in — see the time-handling
 * conventions documented on Shift. */
@Entity
@Table(name = "locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String city;

    /** IANA timezone id. We deliberately do not support a location whose
     * shifts split across two zones (see README "Intentional Ambiguities" —
     * a location near a state line is modeled as picking ONE governing zone,
     * documented as an assumption rather than half-supported). */
    @Column(nullable = false)
    private String timezone;

    @Builder.Default
    private boolean active = true;
}
