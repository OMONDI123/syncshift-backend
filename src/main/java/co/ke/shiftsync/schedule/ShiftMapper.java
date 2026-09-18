package co.ke.shiftsync.schedule;

import co.ke.shiftsync.schedule.ShiftDtos.AssignedStaffSummary;
import co.ke.shiftsync.schedule.ShiftDtos.ShiftResponse;

public final class ShiftMapper {
    private ShiftMapper() {}

    public static ShiftResponse toResponse(Shift s) {
        return new ShiftResponse(
                s.getId(), s.getLocation().getId(), s.getLocation().getName(), s.getLocation().getTimezone(),
                s.getSkillRequired().getId(), s.getSkillRequired().getKey(), s.getSkillRequired().getLabel(),
                s.getHeadcountNeeded(), s.getStartUtc(), s.getEndUtc(),
                s.getStatus().name(), s.isPremium(), s.getNotes(), s.getVersion(),
                s.getAssignedUsers().stream()
                        .map(u -> new AssignedStaffSummary(u.getId(), u.getName(), u.getAvatarColor()))
                        .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                        .toList()
        );
    }
}
