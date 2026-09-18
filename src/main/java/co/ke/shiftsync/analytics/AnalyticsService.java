package co.ke.shiftsync.analytics;

import co.ke.shiftsync.analytics.AnalyticsDtos.*;
import co.ke.shiftsync.location.Location;
import co.ke.shiftsync.location.LocationRepository;
import co.ke.shiftsync.common.exceptions.NotFoundException;
import co.ke.shiftsync.schedule.Shift;
import co.ke.shiftsync.schedule.ShiftRepository;
import co.ke.shiftsync.schedule.TimeUtil;
import co.ke.shiftsync.settings.SettingsService;
import co.ke.shiftsync.user.AppUser;
import co.ke.shiftsync.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Requirement #5 (fairness) and the overtime-cost half of requirement #4.
 * Neither the fairness-score formula nor the overtime-cost model is
 * specified numerically in the brief, so both are documented, defensible
 * choices rather than the "one true answer" — see each method's javadoc.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final SettingsService settingsService;

    /** Hours (and premium-shift counts) actually assigned per staff member
     * over an arbitrary period — this is what a manager pulls up to verify
     * or refute a "I never get good shifts" complaint (the "Fairness
     * Complaint" evaluation scenario): show the raw counts, then the
     * fairness score below for a single-number summary. */
    public FairnessReport distribution(Long locationId, Instant from, Instant to) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found: " + locationId));
        List<AppUser> staff = userRepository.findStaffCertifiedAtLocation(locationId);
        List<Shift> shifts = shiftRepository.findByLocationId(locationId).stream()
                .filter(s -> !s.getStartUtc().isBefore(from) && s.getStartUtc().isBefore(to))
                .toList();

        List<DistributionEntry> entries = new ArrayList<>();
        for (AppUser u : staff) {
            List<Shift> theirs = shifts.stream().filter(s -> s.getAssignedUsers().contains(u)).toList();
            double hours = theirs.stream().mapToDouble(TimeUtil::shiftDurationHours).sum();
            long premium = theirs.stream().filter(Shift::isPremium).count();
            entries.add(new DistributionEntry(u.getId(), u.getName(), hours, u.getDesiredWeeklyHours(), premium, theirs.size()));
        }
        entries.sort((a, b) -> Double.compare(b.hoursWorked(), a.hoursWorked()));

        double score = premiumFairnessScore(entries);
        return new FairnessReport(locationId, from.toString(), to.toString(), score, entries,
                "Fairness score = 100 minus the coefficient of variation of premium (Fri/Sat evening) shifts "
                        + "worked across certified staff, floored at 0. 100 = perfectly even distribution.");
    }

    /** Coefficient-of-variation-based score over premium shift counts.
     * Documented assumption: undefined (all-zero) case reads as perfectly
     * fair (100) rather than undefined/NaN, since "nobody has gotten a
     * premium shift yet" isn't unfair — it's just early. */
    private double premiumFairnessScore(List<DistributionEntry> entries) {
        if (entries.isEmpty()) return 100.0;
        double[] counts = entries.stream().mapToDouble(DistributionEntry::premiumShiftsWorked).toArray();
        double mean = Arrays.stream(counts).average().orElse(0);
        if (mean == 0) return 100.0;
        double variance = Arrays.stream(counts).map(c -> Math.pow(c - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double cv = stdDev / mean;
        return Math.max(0, Math.round((100 - cv * 100) * 10.0) / 10.0);
    }

    /** Requirement #4's overtime-cost dashboard. Weekly-only (Mon–Sun UTC-week
     * boundary passed in by the caller) since hours/consecutive-day rules are
     * already evaluated per calendar week elsewhere.
     * Cost model (documented assumption — hourlyRate isn't in the original
     * brief, added specifically so this dashboard has real numbers instead of
     * $0 for everyone): hours up to the weekly full-time threshold are paid
     * at hourlyRate; anything above it is paid at 1.5x, which is the common
     * (if not universal) US default and clearly labeled as an assumption. */
    public OvertimeReport overtimeProjection(Long locationId, Instant weekStartUtc) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found: " + locationId));
        double fullTimeThreshold = settingsService.get().getWeeklyFullTimeHours();
        Instant weekEnd = weekStartUtc.plusSeconds(7L * 24 * 3600);
        List<Shift> shifts = shiftRepository.findByLocationAndWeek(locationId, weekStartUtc, weekEnd);

        Map<AppUser, Double> hoursByUser = new LinkedHashMap<>();
        for (Shift s : shifts) {
            double hours = TimeUtil.shiftDurationHours(s);
            for (AppUser u : s.getAssignedUsers()) {
                hoursByUser.merge(u, hours, Double::sum);
            }
        }

        List<OvertimeEntry> entries = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var e : hoursByUser.entrySet()) {
            AppUser u = e.getKey();
            double hours = e.getValue();
            BigDecimal rate = u.getHourlyRate() != null ? u.getHourlyRate() : BigDecimal.ZERO;
            double regularHours = Math.min(hours, fullTimeThreshold);
            double otHours = Math.max(0, hours - fullTimeThreshold);
            BigDecimal regularCost = rate.multiply(BigDecimal.valueOf(regularHours)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal otCost = rate.multiply(BigDecimal.valueOf(1.5)).multiply(BigDecimal.valueOf(otHours)).setScale(2, RoundingMode.HALF_UP);
            total = total.add(regularCost).add(otCost);
            entries.add(new OvertimeEntry(u.getId(), u.getName(), hours, rate, regularCost, otCost, hours > fullTimeThreshold));
        }
        entries.sort((a, b) -> Double.compare(b.hoursScheduled(), a.hoursScheduled()));
        return new OvertimeReport(locationId, weekStartUtc.toString(), total, entries);
    }
}
