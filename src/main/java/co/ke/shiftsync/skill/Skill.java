package co.ke.shiftsync.skill;

import jakarta.persistence.*;
import lombok.*;

/** Admin-managed skill catalog (e.g. "bartender", "line cook"). Skills started
 * as a fixed enum in the original design; the brief implies new skills may be
 * added as the business grows, so this is a real catalog table rather than a
 * hardcoded list — see the Setup module. */
@Entity
@Table(name = "skills")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable machine key, e.g. "bartender". Lowercase, underscore-separated. */
    @Column(nullable = false, unique = true)
    private String key;

    /** Human label, e.g. "Bartender". */
    @Column(nullable = false)
    private String label;

    /** Hex color used consistently by the UI for this skill's badge. */
    private String colorHex;
}
