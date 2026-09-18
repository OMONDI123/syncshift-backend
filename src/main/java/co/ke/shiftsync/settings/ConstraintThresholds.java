package co.ke.shiftsync.settings;

import jakarta.persistence.*;
import lombok.*;

/** Singleton row (always id=1) holding every rule number the constraint
 * engine and swap workflow use, so an admin can tune them from the Setup
 * page instead of them being buried in code — ports the frontend's
 * `lib/constraints.ts` hardcoded constants into an editable settings row. */
@Entity
@Table(name = "constraint_thresholds")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConstraintThresholds {

    @Id
    @Builder.Default
    private Long id = 1L;

    @Builder.Default private double minRestHours = 10;
    @Builder.Default private double dailyHardBlockHours = 12;
    @Builder.Default private double dailyWarningHours = 8;
    @Builder.Default private double weeklyWarningHours = 35;
    @Builder.Default private double weeklyFullTimeHours = 40;
    @Builder.Default private int maxPendingSwapsPerStaff = 3;
    @Builder.Default private int dropExpiryHoursBeforeShift = 24;
    @Builder.Default private int publishEditCutoffHours = 48;
    @Builder.Default private int sixthConsecutiveDayWarning = 6;
    @Builder.Default private int seventhConsecutiveDayBlock = 7;
}
