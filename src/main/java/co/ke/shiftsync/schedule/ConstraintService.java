package co.ke.shiftsync.schedule;

import co.ke.shiftsync.availability.AvailabilityRepository;
import co.ke.shiftsync.availability.AvailabilityType;
import co.ke.shiftsync.availability.AvailabilityWindow;
import co.ke.shiftsync.common.Role;
import co.ke.shiftsync.location.Location;
import co.ke.shiftsync.settings.ConstraintThresholds;
import co.ke.shiftsync.settings.SettingsService;
import co.ke.shiftsync.user.AppUser;
import co.ke.shiftsync.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Direct port of the frontend prototype's `lib/constraints.ts`. This is the
 * single source of truth the API calls before confirming any assignment —
 * every violation carries a plain-language message per the brief's
 * requirement to explain which rule broke and why, and blocked assignments
 * come back with up to 3 alternative-staff suggestions.
 */
@Service
@RequiredArgsConstructor
public class ConstraintService {

    private final AvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final ShiftRepository shiftRepository;
    private final SettingsService settingsService;

    public AssignmentCheckResult checkAssignment(AppUser user, Shift target, List<Shift> usersOtherShifts) {
        return checkAssignment(user, target, usersOtherShifts, false);
    }

    private AssignmentCheckResult checkAssignment(AppUser user, Shift target, List<Shift> usersOtherShifts,
                                                   boolean skipSuggestions) {
        ConstraintThresholds t = settingsService.get();
        List<ConstraintViolation> violations = new ArrayList<>();
        Location location = target.getLocation();

        // 1. Skill match
        boolean hasSkill = user.getSkills().stream().anyMatch(s -> s.getId().equals(target.getSkillRequired().getId()));
        if (!hasSkill) {
            violations.add(new ConstraintViolation(ViolationCode.SKILL_MISMATCH, Severity.BLOCK,
                    "%s isn't certified as a %s, and this shift requires one."
                            .formatted(user.getName(), target.getSkillRequired().getLabel())));
        }

        // 2. Location certification
        boolean certified = user.getCertifiedLocations().stream().anyMatch(l -> l.getId().equals(location.getId()));
        if (!certified) {
            violations.add(new ConstraintViolation(ViolationCode.LOCATION_NOT_CERTIFIED, Severity.BLOCK,
                    "%s isn't certified to work at %s.".formatted(user.getName(), location.getName())));
        }

        // 3. Double booking / overlap, including across locations
        Optional<Shift> overlapping = usersOtherShifts.stream()
                .filter(s -> TimeUtil.overlaps(s.getStartUtc(), s.getEndUtc(), target.getStartUtc(), target.getEndUtc()))
                .findFirst();
        overlapping.ifPresent(s -> violations.add(new ConstraintViolation(ViolationCode.DOUBLE_BOOKED, Severity.BLOCK,
                "%s is already scheduled at %s during this time.".formatted(user.getName(), s.getLocation().getName()))));

        // 4. Minimum rest period between shifts
        boolean tooClose = usersOtherShifts.stream().anyMatch(s -> {
            double restAfterOther = TimeUtil.restHoursBetween(s.getEndUtc(), target.getStartUtc());
            double restBeforeOther = TimeUtil.restHoursBetween(target.getEndUtc(), s.getStartUtc());
            return (restAfterOther >= 0 && restAfterOther < t.getMinRestHours())
                    || (restBeforeOther >= 0 && restBeforeOther < t.getMinRestHours());
        });
        if (tooClose && overlapping.isEmpty()) {
            violations.add(new ConstraintViolation(ViolationCode.REST_PERIOD, Severity.BLOCK,
                    "%s would have less than %s hours off between this shift and another one."
                            .formatted(user.getName(), stripTrailingZero(t.getMinRestHours()))));
        }

        // 5. Availability — evaluated in the STAFF MEMBER's own home timezone,
        // never the shift location's timezone. See TimeUtil / class javadoc.
        if (!isWithinAvailability(user, target)) {
            violations.add(new ConstraintViolation(ViolationCode.OUTSIDE_AVAILABILITY, Severity.BLOCK,
                    "%s hasn't marked themselves available during this time.".formatted(user.getName())));
        }

        // 6. Daily hours (in the shift's LOCATION-local calendar day)
        double dailyTotal = TimeUtil.shiftDurationHours(target) + hoursOnSameLocalDay(target, usersOtherShifts, location);
        if (dailyTotal > t.getDailyHardBlockHours()) {
            violations.add(new ConstraintViolation(ViolationCode.DAILY_HOURS_BLOCK, Severity.BLOCK,
                    "This would put %s at %.1f hours in one day — over the %s-hour limit."
                            .formatted(user.getName(), dailyTotal, stripTrailingZero(t.getDailyHardBlockHours()))));
        } else if (dailyTotal > t.getDailyWarningHours()) {
            violations.add(new ConstraintViolation(ViolationCode.DAILY_HOURS_BLOCK, Severity.WARNING,
                    "This puts %s at %.1f hours in one day.".formatted(user.getName(), dailyTotal)));
        }

        // 7. Consecutive days worked (location-local calendar days)
        int consecutive = consecutiveDaysIncluding(target, usersOtherShifts, location);
        if (consecutive >= t.getSeventhConsecutiveDayBlock()) {
            violations.add(new ConstraintViolation(ViolationCode.SEVENTH_CONSECUTIVE_DAY, Severity.BLOCK,
                    "This would be %s's %s consecutive day worked. Requires a manager override with a documented reason."
                            .formatted(user.getName(), ordinal(t.getSeventhConsecutiveDayBlock()))));
        } else if (consecutive == t.getSixthConsecutiveDayWarning()) {
            violations.add(new ConstraintViolation(ViolationCode.SEVENTH_CONSECUTIVE_DAY, Severity.WARNING,
                    "This would be %s's %s consecutive day worked.".formatted(user.getName(), ordinal(consecutive))));
        }

        // 8. Weekly hours (not a block by itself — surfaced in the OT dashboard too)
        double weeklyTotal = TimeUtil.shiftDurationHours(target)
                + usersOtherShifts.stream().mapToDouble(TimeUtil::shiftDurationHours).sum();
        if (weeklyTotal > t.getWeeklyFullTimeHours()) {
            violations.add(new ConstraintViolation(ViolationCode.DAILY_HOURS_BLOCK, Severity.WARNING,
                    "%s would be at %.1f hours this week — into overtime.".formatted(user.getName(), weeklyTotal)));
        } else if (weeklyTotal >= t.getWeeklyWarningHours()) {
            violations.add(new ConstraintViolation(ViolationCode.DAILY_HOURS_BLOCK, Severity.WARNING,
                    "%s would be at %.1f hours this week — approaching the %s-hour threshold."
                            .formatted(user.getName(), weeklyTotal, stripTrailingZero(t.getWeeklyFullTimeHours()))));
        }

        // Suggestions only computed at the top level to avoid infinite mutual
        // recursion with suggestAlternatives() (see its javadoc).
        List<AssignmentSuggestion> suggestions = (!skipSuggestions && violations.stream().anyMatch(v -> v.severity() == Severity.BLOCK))
                ? suggestAlternatives(target)
                : List.of();

        boolean ok = violations.stream().noneMatch(v -> v.severity() == Severity.BLOCK);
        return new AssignmentCheckResult(ok, violations, suggestions);
    }

    private boolean isWithinAvailability(AppUser user, Shift shift) {
        String tz = user.getHomeTimezone();
        ZonedDateTime zonedStart = TimeUtil.zoned(shift.getStartUtc(), tz);
        ZonedDateTime zonedEnd = TimeUtil.zoned(shift.getEndUtc(), tz);
        int dow = TimeUtil.jsDayOfWeek(zonedStart);
        int startMin = zonedStart.getHour() * 60 + zonedStart.getMinute();
        long dayDiff = ChronoUnit.DAYS.between(zonedStart.toLocalDate(), zonedEnd.toLocalDate());
        int endMin = zonedEnd.getHour() * 60 + zonedEnd.getMinute() + (dayDiff > 0 ? 24 * 60 : 0);
        LocalDate localDate = zonedStart.toLocalDate();

        List<AvailabilityWindow> windows = availabilityRepository.findByUserId(user.getId());

        Optional<AvailabilityWindow> exception = windows.stream()
                .filter(w -> w.getType() == AvailabilityType.EXCEPTION && localDate.equals(w.getDate()))
                .findFirst();
        if (exception.isPresent()) return exception.get().isAvailable();

        final int fDow = dow, fStart = startMin, fEnd = endMin;
        return windows.stream().anyMatch(w -> w.getType() == AvailabilityType.RECURRING && w.isAvailable()
                && w.getDayOfWeek() != null && w.getDayOfWeek() == fDow
                && w.getStartMinutes() <= fStart && w.getEndMinutes() >= fEnd);
    }

    private double hoursOnSameLocalDay(Shift target, List<Shift> others, Location location) {
        LocalDate targetDay = TimeUtil.zoned(target.getStartUtc(), location.getTimezone()).toLocalDate();
        return others.stream()
                .filter(s -> TimeUtil.zoned(s.getStartUtc(), location.getTimezone()).toLocalDate().equals(targetDay))
                .mapToDouble(TimeUtil::shiftDurationHours)
                .sum();
    }

    private int consecutiveDaysIncluding(Shift target, List<Shift> others, Location location) {
        String tz = location.getTimezone();
        TreeSet<LocalDate> days = new TreeSet<>();
        days.add(TimeUtil.zoned(target.getStartUtc(), tz).toLocalDate());
        for (Shift s : others) days.add(TimeUtil.zoned(s.getStartUtc(), tz).toLocalDate());

        int best = 1, run = 1;
        LocalDate prev = null;
        for (LocalDate d : days) {
            if (prev != null && ChronoUnit.DAYS.between(prev, d) == 1) {
                run++;
                best = Math.max(best, run);
            } else if (prev != null) {
                run = 1;
            }
            prev = d;
        }
        return best;
    }

    /** Looks for up to 3 STAFF who have the required skill, are certified at
     * this location, and pass every other check clean — this is the "who
     * else could take this?" the brief's Sunday-Night-Chaos scenario needs.
     * Only ever called at the top level (skipSuggestions=true internally) to
     * avoid the two functions recursing into each other forever. */
    private List<AssignmentSuggestion> suggestAlternatives(Shift target) {
        List<AppUser> candidates = userRepository.findByRole(Role.STAFF).stream()
                .filter(u -> u.getSkills().stream().anyMatch(s -> s.getId().equals(target.getSkillRequired().getId())))
                .filter(u -> u.getCertifiedLocations().stream().anyMatch(l -> l.getId().equals(target.getLocation().getId())))
                .toList();

        List<AssignmentSuggestion> out = new ArrayList<>();
        for (AppUser u : candidates) {
            if (out.size() >= 3) break;
            List<Shift> theirShifts = shiftRepository.findByAssignedUserIdExcluding(u.getId(), target.getId() == null ? -1L : target.getId());
            AssignmentCheckResult check = checkAssignment(u, target, theirShifts, true);
            if (check.ok()) {
                out.add(new AssignmentSuggestion(u.getId(), u.getName(),
                        "Has the required skill, is certified here, and is free at this time."));
            }
        }
        return out;
    }

    private static String stripTrailingZero(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    private static String ordinal(int n) {
        return switch (n) {
            case 6 -> "6th";
            case 7 -> "7th";
            default -> n + "th";
        };
    }
}
