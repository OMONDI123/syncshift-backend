package co.ke.shiftsync.config;

import co.ke.shiftsync.audit.AuditLogEntry;
import co.ke.shiftsync.audit.AuditLogRepository;
import co.ke.shiftsync.availability.AvailabilityRepository;
import co.ke.shiftsync.availability.AvailabilityType;
import co.ke.shiftsync.availability.AvailabilityWindow;
import co.ke.shiftsync.common.AuditEntityType;
import co.ke.shiftsync.common.NotificationChannel;
import co.ke.shiftsync.common.Role;
import co.ke.shiftsync.location.Location;
import co.ke.shiftsync.location.LocationRepository;
import co.ke.shiftsync.notification.Notification;
import co.ke.shiftsync.notification.NotificationRepository;
import co.ke.shiftsync.notification.NotificationType;
import co.ke.shiftsync.schedule.Shift;
import co.ke.shiftsync.schedule.ShiftRepository;
import co.ke.shiftsync.schedule.ShiftStatus;
import co.ke.shiftsync.settings.ConstraintThresholds;
import co.ke.shiftsync.settings.ConstraintThresholdsRepository;
import co.ke.shiftsync.skill.Skill;
import co.ke.shiftsync.skill.SkillRepository;
import co.ke.shiftsync.swap.SwapHistoryEntry;
import co.ke.shiftsync.swap.SwapKind;
import co.ke.shiftsync.swap.SwapRepository;
import co.ke.shiftsync.swap.SwapRequest;
import co.ke.shiftsync.swap.SwapStatus;
import co.ke.shiftsync.user.AppUser;
import co.ke.shiftsync.user.DemoPasswords;
import co.ke.shiftsync.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Realistic seed data reproducing the same Coastal Eats scenario used by the
 * frontend prototype's `data/seed.ts` — same locations, people, skills and
 * shift layout — so the two are easy to cross-check during API integration,
 * and so every evaluator scenario in the brief has something concrete to
 * walk through immediately after startup: a Sunday-night open drop, an
 * unfilled Saturday shift for the "Overtime Trap"/"Simultaneous Assignment"
 * demos, a cross-country-certified staff member for the "Timezone Tangle",
 * and a swap already sitting at pending_manager for the "Regret Swap".
 *
 * Only runs against an empty database (checked via skill count) and only
 * when shiftsync.seed.enabled=true (see application.yml).
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    @Value("${shiftsync.seed.enabled:true}")
    private boolean enabled;

    private final SkillRepository skillRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final AvailabilityRepository availabilityRepository;
    private final ShiftRepository shiftRepository;
    private final SwapRepository swapRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final ConstraintThresholdsRepository thresholdsRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || skillRepository.count() > 0) return;

        thresholdsRepository.save(ConstraintThresholds.builder().id(1L).build());

        Map<String, Skill> skills = seedSkills();
        Map<String, Location> locations = seedLocations();
        Map<String, AppUser> users = seedUsers(locations, skills);
        seedAvailability(users);
        Map<String, Shift> shifts = seedShifts(locations, skills, users);
        seedSwapsAndNotificationsAndAudit(users, shifts);
    }

    private Map<String, Skill> seedSkills() {
        Map<String, Skill> byKey = new HashMap<>();
        record S(String key, String label, String color) {}
        for (S s : new S[]{
                new S("bartender", "Bartender", "#C2410C"),
                new S("server", "Server", "#2563EB"),
                new S("line_cook", "Line Cook", "#16A34A"),
                new S("prep_cook", "Prep Cook", "#7C3AED"),
                new S("host", "Host", "#DB2777"),
        }) {
            byKey.put(s.key(), skillRepository.save(Skill.builder().key(s.key()).label(s.label()).colorHex(s.color()).build()));
        }
        return byKey;
    }

    private Map<String, Location> seedLocations() {
        Map<String, Location> byId = new HashMap<>();
        byId.put("loc-sea", locationRepository.save(Location.builder().name("Seattle Harbor").city("Seattle, WA").timezone("America/Los_Angeles").active(true).build()));
        byId.put("loc-pdx", locationRepository.save(Location.builder().name("Portland Pearl").city("Portland, OR").timezone("America/Los_Angeles").active(true).build()));
        byId.put("loc-mia", locationRepository.save(Location.builder().name("Miami Shore").city("Miami, FL").timezone("America/New_York").active(true).build()));
        byId.put("loc-bos", locationRepository.save(Location.builder().name("Boston Wharf").city("Boston, MA").timezone("America/New_York").active(true).build()));
        return byId;
    }

    private Map<String, AppUser> seedUsers(Map<String, Location> loc, Map<String, Skill> sk) {
        Map<String, AppUser> byId = new HashMap<>();

        byId.put("u-admin", userRepository.save(AppUser.builder()
                .name("Dana Reyes").email("dana@coastaleats.com").role(Role.ADMIN)
                .avatarColor("#0F2148").passwordHash(hash(Role.ADMIN)).homeTimezone("America/Los_Angeles")
                .notificationChannel(NotificationChannel.IN_APP_AND_EMAIL).active(true).build()));

        byId.put("u-mgr-west", userRepository.save(AppUser.builder()
                .name("Marcus Chen").email("marcus@coastaleats.com").role(Role.MANAGER)
                .avatarColor("#14264D").passwordHash(hash(Role.MANAGER)).homeTimezone("America/Los_Angeles")
                .managedLocations(setOf(loc.get("loc-sea"), loc.get("loc-pdx")))
                .notificationChannel(NotificationChannel.IN_APP_AND_EMAIL).active(true).build()));

        byId.put("u-mgr-east", userRepository.save(AppUser.builder()
                .name("Priya Nair").email("priya@coastaleats.com").role(Role.MANAGER)
                .avatarColor("#1B3163").passwordHash(hash(Role.MANAGER)).homeTimezone("America/New_York")
                .managedLocations(setOf(loc.get("loc-mia"), loc.get("loc-bos")))
                .notificationChannel(NotificationChannel.IN_APP_AND_EMAIL).active(true).build()));

        byId.put("u-sarah", userRepository.save(AppUser.builder()
                .name("Sarah Kim").email("sarah@coastaleats.com").role(Role.STAFF)
                .avatarColor("#F5A623").passwordHash(hash(Role.STAFF)).homeTimezone("America/Los_Angeles")
                .skills(setOf(sk.get("bartender"), sk.get("server")))
                .certifiedLocations(setOf(loc.get("loc-sea"), loc.get("loc-pdx")))
                .desiredWeeklyHours(30).hourlyRate(java.math.BigDecimal.valueOf(19.50)).active(true).build()));

        byId.put("u-john", userRepository.save(AppUser.builder()
                .name("John Alvarez").email("john@coastaleats.com").role(Role.STAFF)
                .avatarColor("#2FAE66").passwordHash(hash(Role.STAFF)).homeTimezone("America/Los_Angeles")
                .skills(setOf(sk.get("bartender"), sk.get("server"), sk.get("host")))
                .certifiedLocations(setOf(loc.get("loc-sea")))
                .desiredWeeklyHours(35).hourlyRate(java.math.BigDecimal.valueOf(18.00)).active(true).build()));

        byId.put("u-maria", userRepository.save(AppUser.builder()
                .name("Maria Gonzalez").email("maria@coastaleats.com").role(Role.STAFF)
                .avatarColor("#E5484D").passwordHash(hash(Role.STAFF)).homeTimezone("America/Los_Angeles")
                .skills(setOf(sk.get("line_cook"), sk.get("prep_cook")))
                .certifiedLocations(setOf(loc.get("loc-sea"), loc.get("loc-pdx")))
                .desiredWeeklyHours(32).hourlyRate(java.math.BigDecimal.valueOf(20.00)).active(true).build()));

        byId.put("u-noah", userRepository.save(AppUser.builder()
                .name("Noah Park").email("noah@coastaleats.com").role(Role.STAFF)
                .avatarColor("#F0A93A").passwordHash(hash(Role.STAFF)).homeTimezone("America/Los_Angeles")
                .skills(setOf(sk.get("host"), sk.get("server")))
                .certifiedLocations(setOf(loc.get("loc-pdx")))
                .desiredWeeklyHours(20).hourlyRate(java.math.BigDecimal.valueOf(17.00)).active(true).build()));

        byId.put("u-liam", userRepository.save(AppUser.builder()
                .name("Liam O'Brien").email("liam@coastaleats.com").role(Role.STAFF)
                .avatarColor("#DB8F14").passwordHash(hash(Role.STAFF)).homeTimezone("America/New_York")
                .skills(setOf(sk.get("bartender"), sk.get("server")))
                .certifiedLocations(setOf(loc.get("loc-bos"), loc.get("loc-mia")))
                .desiredWeeklyHours(34).hourlyRate(java.math.BigDecimal.valueOf(19.00)).active(true).build()));

        byId.put("u-ava", userRepository.save(AppUser.builder()
                .name("Ava Thompson").email("ava@coastaleats.com").role(Role.STAFF)
                .avatarColor("#8A93A8").passwordHash(hash(Role.STAFF)).homeTimezone("America/New_York")
                .skills(setOf(sk.get("line_cook")))
                .certifiedLocations(setOf(loc.get("loc-mia")))
                .desiredWeeklyHours(40).hourlyRate(java.math.BigDecimal.valueOf(21.00)).active(true).build()));

        byId.put("u-diego", userRepository.save(AppUser.builder()
                .name("Diego Ramirez").email("diego@coastaleats.com").role(Role.STAFF)
                .avatarColor("#4B5468").passwordHash(hash(Role.STAFF)).homeTimezone("America/New_York")
                .skills(setOf(sk.get("server"), sk.get("host")))
                .certifiedLocations(setOf(loc.get("loc-bos")))
                .desiredWeeklyHours(25).hourlyRate(java.math.BigDecimal.valueOf(17.50)).active(true).build()));

        // "Timezone Tangle": certified at a Pacific location AND an Eastern
        // one, home timezone Pacific — their "9am-5pm" availability is
        // evaluated in Pacific time no matter which location's shift is
        // being checked. See ConstraintService.isWithinAvailability.
        byId.put("u-jordan", userRepository.save(AppUser.builder()
                .name("Jordan Blake").email("jordan@coastaleats.com").role(Role.STAFF)
                .avatarColor("#F5A623").passwordHash(hash(Role.STAFF)).homeTimezone("America/Los_Angeles")
                .skills(setOf(sk.get("bartender"), sk.get("server")))
                .certifiedLocations(setOf(loc.get("loc-sea"), loc.get("loc-mia")))
                .desiredWeeklyHours(30).hourlyRate(java.math.BigDecimal.valueOf(19.50)).active(true).build()));

        return byId;
    }

    private void seedAvailability(Map<String, AppUser> u) {
        recurring(u.get("u-sarah"), new int[]{1, 2, 3, 4, 5}, 16 * 60, 23 * 60 + 59);
        recurring(u.get("u-john"), new int[]{0, 5, 6}, 10 * 60, 23 * 60);
        recurring(u.get("u-maria"), new int[]{1, 2, 3, 4, 5, 6}, 8 * 60, 20 * 60);
        recurring(u.get("u-jordan"), new int[]{0, 1, 2, 3, 4, 5, 6}, 9 * 60, 17 * 60);
        recurring(u.get("u-liam"), new int[]{3, 4, 5, 6}, 14 * 60, 23 * 60 + 59);
        recurring(u.get("u-ava"), new int[]{1, 2, 3, 4, 5}, 7 * 60, 19 * 60);
        recurring(u.get("u-diego"), new int[]{4, 5, 6}, 12 * 60, 23 * 60);
        recurring(u.get("u-noah"), new int[]{5, 6}, 15 * 60, 22 * 60);
    }

    private void recurring(AppUser user, int[] days, int startMin, int endMin) {
        for (int d : days) {
            availabilityRepository.save(AvailabilityWindow.builder()
                    .user(user).type(AvailabilityType.RECURRING).dayOfWeek(d)
                    .startMinutes(startMin).endMinutes(endMin).available(true).build());
        }
    }

    private Map<String, Shift> seedShifts(Map<String, Location> loc, Map<String, Skill> sk, Map<String, AppUser> u) {
        Map<String, Shift> named = new HashMap<>();
        LocalDate sunday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));

        // --- Seattle Harbor (Pacific) ---
        named.put("sea-0-17", shiftAt(loc.get("loc-sea"), sunday, 0, 17, 6, sk.get("server"), 1, set(u, "u-sarah")));
        shiftAt(loc.get("loc-sea"), sunday, 0, 23, 4, sk.get("bartender"), 1, set(u, "u-john")); // overnight 11pm-3am
        shiftAt(loc.get("loc-sea"), sunday, 3, 16, 5, sk.get("bartender"), 1, set(u, "u-sarah"));
        named.put("sunday-chaos", shiftAt(loc.get("loc-sea"), sunday, 0, 19, 4, sk.get("server"), 1, set(u, "u-john")));
        named.put("sea-friday-double", shiftAt(loc.get("loc-sea"), sunday, 5, 18, 5, sk.get("server"), 2, set(u, "u-sarah", "u-john")));
        shiftAt(loc.get("loc-sea"), sunday, 6, 17, 5, sk.get("bartender"), 1, set(u, "u-jordan"));

        // --- Portland Pearl (Pacific) ---
        shiftAt(loc.get("loc-pdx"), sunday, 1, 11, 6, sk.get("line_cook"), 1, set(u, "u-maria"));
        shiftAt(loc.get("loc-pdx"), sunday, 2, 11, 6, sk.get("line_cook"), 1, set(u, "u-maria"));
        shiftAt(loc.get("loc-pdx"), sunday, 3, 11, 6, sk.get("line_cook"), 1, set(u, "u-maria"));
        shiftAt(loc.get("loc-pdx"), sunday, 4, 11, 6, sk.get("line_cook"), 1, set(u, "u-maria"));
        shiftAt(loc.get("loc-pdx"), sunday, 5, 11, 7, sk.get("line_cook"), 1, set(u, "u-maria"));
        // Left unassigned on purpose: assigning Maria here would be her 6th
        // consecutive day worked — a manager doing so should see the warning.
        named.put("maria-6th-day", shiftAt(loc.get("loc-pdx"), sunday, 6, 11, 7, sk.get("line_cook"), 1, Set.of()));
        shiftAt(loc.get("loc-pdx"), sunday, 5, 17, 5, sk.get("host"), 1, set(u, "u-noah"));
        shiftAt(loc.get("loc-pdx"), sunday, 6, 17, 5, sk.get("host"), 1, set(u, "u-noah"));

        // --- Miami Shore (Eastern) ---
        shiftAt(loc.get("loc-mia"), sunday, 1, 10, 8, sk.get("line_cook"), 1, set(u, "u-ava"));
        shiftAt(loc.get("loc-mia"), sunday, 2, 10, 8, sk.get("line_cook"), 1, set(u, "u-ava"));
        shiftAt(loc.get("loc-mia"), sunday, 3, 10, 8, sk.get("line_cook"), 1, set(u, "u-ava"));
        shiftAt(loc.get("loc-mia"), sunday, 4, 10, 8, sk.get("line_cook"), 1, set(u, "u-ava"));
        shiftAt(loc.get("loc-mia"), sunday, 5, 10, 8, sk.get("line_cook"), 1, set(u, "u-ava"));
        // "Overtime Trap": Ava is already at 40h — adding this unassigned
        // Saturday shift would push her over. Left open on purpose.
        named.put("overtime-trap", shiftAt(loc.get("loc-mia"), sunday, 6, 10, 8, sk.get("line_cook"), 1, Set.of()));
        shiftAt(loc.get("loc-mia"), sunday, 5, 18, 5, sk.get("bartender"), 1, set(u, "u-jordan"));
        // "Simultaneous Assignment" target: unfilled, qualifies Jordan (and
        // Liam), used to demo two managers racing to assign the same person.
        named.put("collision-shift", shiftAt(loc.get("loc-mia"), sunday, 6, 18, 5, sk.get("bartender"), 1, Set.of()));

        // --- Boston Wharf (Eastern) ---
        shiftAt(loc.get("loc-bos"), sunday, 3, 14, 6, sk.get("server"), 1, set(u, "u-liam"));
        shiftAt(loc.get("loc-bos"), sunday, 4, 14, 6, sk.get("server"), 1, set(u, "u-liam"));
        shiftAt(loc.get("loc-bos"), sunday, 5, 17, 6, sk.get("server"), 2, set(u, "u-liam", "u-diego"));
        shiftAt(loc.get("loc-bos"), sunday, 6, 17, 6, sk.get("host"), 1, set(u, "u-diego"));

        return named;
    }

    private Shift shiftAt(Location location, LocalDate weekStartSunday, int dayOffset, int startHour, int durationHours,
                           Skill skill, int headcount, Set<AppUser> assigned) {
        ZoneId zone = ZoneId.of(location.getTimezone());
        ZonedDateTime start = ZonedDateTime.of(weekStartSunday.plusDays(dayOffset), LocalTime.of(startHour, 0), zone);
        ZonedDateTime end = start.plusHours(durationHours);
        boolean premium = (dayOffset == 5 || dayOffset == 6) && startHour >= 17;
        Shift shift = Shift.builder()
                .location(location).skillRequired(skill).headcountNeeded(headcount)
                .startUtc(start.toInstant()).endUtc(end.toInstant())
                .status(ShiftStatus.PUBLISHED).premium(premium)
                .assignedUsers(new HashSet<>(assigned))
                .build();
        return shiftRepository.save(shift);
    }

    private void seedSwapsAndNotificationsAndAudit(Map<String, AppUser> u, Map<String, Shift> shifts) {
        Instant now = Instant.now();

        // Sarah <-> Jordan swap on the Friday Seattle double-server shift,
        // already accepted by Jordan and sitting at pending_manager — the
        // "Regret Swap" scenario's starting point.
        Shift fridayShift = shifts.get("sea-friday-double");
        SwapRequest swap = SwapRequest.builder()
                .kind(SwapKind.SWAP).shift(fridayShift).requestedBy(u.get("u-sarah")).partner(u.get("u-jordan"))
                .status(SwapStatus.PENDING_MANAGER).createdAtUtc(now)
                .build();
        swap.getHistory().add(SwapHistoryEntry.builder().atUtc(now).event("Sarah requested a swap with Jordan").byUserId(u.get("u-sarah").getId()).build());
        swap.getHistory().add(SwapHistoryEntry.builder().atUtc(now).event("Jordan accepted").byUserId(u.get("u-jordan").getId()).build());
        swapRepository.save(swap);

        // Open drop on the Sunday-night Seattle shift — the "Sunday Night
        // Chaos" scenario: John just called out, coverage is needed now.
        Shift chaosShift = shifts.get("sunday-chaos");
        SwapRequest drop = SwapRequest.builder()
                .kind(SwapKind.DROP).shift(chaosShift).requestedBy(u.get("u-john"))
                .status(SwapStatus.OPEN).createdAtUtc(now)
                .expiresAtUtc(chaosShift.getStartUtc().minusSeconds(24 * 3600))
                .build();
        drop.getHistory().add(SwapHistoryEntry.builder().atUtc(now).event("Offered up for grabs — called out").byUserId(u.get("u-john").getId()).build());
        swapRepository.save(drop);

        notificationRepository.save(Notification.builder().user(u.get("u-mgr-west")).type(NotificationType.APPROVAL_NEEDED)
                .title("Swap needs your approval")
                .body("Sarah Kim \u2194 Jordan Blake for Friday's Seattle Harbor server shift.")
                .linkShiftId(fridayShift.getId()).linkSwapId(swap.getId()).createdAt(now).build());

        notificationRepository.save(Notification.builder().user(u.get("u-mgr-east")).type(NotificationType.OVERTIME_WARNING)
                .title("Overtime risk this week")
                .body("Ava Thompson is projected at 48 hours if Saturday's shift is confirmed.")
                .createdAt(now).build());

        notificationRepository.save(Notification.builder().user(u.get("u-john")).type(NotificationType.SCHEDULE_PUBLISHED)
                .title("This week's schedule is live")
                .body("Seattle Harbor's schedule was published by Marcus Chen.")
                .createdAt(now).readAt(now).build());

        auditLogRepository.save(AuditLogEntry.builder().atUtc(now).actorUserId(u.get("u-mgr-west").getId())
                .actorName("Marcus Chen").entityType(AuditEntityType.SHIFT).entityId("location:" + fridayShift.getLocation().getId())
                .action("published_week").locationId(fridayShift.getLocation().getId()).build());
    }

    private String hash(Role role) {
        return passwordEncoder.encode(DemoPasswords.forRole(role));
    }

    private Set<AppUser> set(Map<String, AppUser> u, String... ids) {
        Set<AppUser> out = new HashSet<>();
        for (String id : ids) out.add(u.get(id));
        return out;
    }

    private Set<Location> setOf(Location... locations) {
        return new HashSet<>(java.util.Arrays.asList(locations));
    }

    @SafeVarargs
    private <T> Set<T> setOf(T... items) {
        return new HashSet<>(java.util.Arrays.asList(items));
    }
}
