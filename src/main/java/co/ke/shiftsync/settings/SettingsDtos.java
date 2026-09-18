package co.ke.shiftsync.settings;

public class SettingsDtos {

    public record ThresholdsRequest(
            Double minRestHours,
            Double dailyHardBlockHours,
            Double dailyWarningHours,
            Double weeklyWarningHours,
            Double weeklyFullTimeHours,
            Integer maxPendingSwapsPerStaff,
            Integer dropExpiryHoursBeforeShift,
            Integer publishEditCutoffHours,
            Integer sixthConsecutiveDayWarning,
            Integer seventhConsecutiveDayBlock
    ) {}

    public record ThresholdsResponse(
            double minRestHours, double dailyHardBlockHours, double dailyWarningHours,
            double weeklyWarningHours, double weeklyFullTimeHours, int maxPendingSwapsPerStaff,
            int dropExpiryHoursBeforeShift, int publishEditCutoffHours,
            int sixthConsecutiveDayWarning, int seventhConsecutiveDayBlock
    ) {
        public static ThresholdsResponse from(ConstraintThresholds t) {
            return new ThresholdsResponse(t.getMinRestHours(), t.getDailyHardBlockHours(), t.getDailyWarningHours(),
                    t.getWeeklyWarningHours(), t.getWeeklyFullTimeHours(), t.getMaxPendingSwapsPerStaff(),
                    t.getDropExpiryHoursBeforeShift(), t.getPublishEditCutoffHours(),
                    t.getSixthConsecutiveDayWarning(), t.getSeventhConsecutiveDayBlock());
        }
    }
}
