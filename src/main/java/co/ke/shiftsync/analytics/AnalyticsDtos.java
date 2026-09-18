package co.ke.shiftsync.analytics;

import java.math.BigDecimal;

public class AnalyticsDtos {

    public record DistributionEntry(Long userId, String name, double hoursWorked, Integer desiredWeeklyHours,
                                     long premiumShiftsWorked, long totalShiftsWorked) {}

    public record FairnessReport(Long locationId, String from, String to, double fairnessScore,
                                  java.util.List<DistributionEntry> entries, String note) {}

    public record OvertimeEntry(Long userId, String name, double hoursScheduled, BigDecimal hourlyRate,
                                 BigDecimal projectedRegularCost, BigDecimal projectedOvertimeCost, boolean overWeeklyThreshold) {}

    public record OvertimeReport(Long locationId, String weekStart, BigDecimal totalProjectedCost,
                                  java.util.List<OvertimeEntry> entries) {}
}
