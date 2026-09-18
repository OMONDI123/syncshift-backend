package co.ke.shiftsync.schedule;

import co.ke.shiftsync.availability.AvailabilityRepository;
import co.ke.shiftsync.availability.AvailabilityType;
import co.ke.shiftsync.availability.AvailabilityWindow;
import co.ke.shiftsync.location.Location;
import co.ke.shiftsync.settings.ConstraintThresholds;
import co.ke.shiftsync.settings.SettingsService;
import co.ke.shiftsync.skill.Skill;
import co.ke.shiftsync.user.AppUser;
import co.ke.shiftsync.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** Unit tests for the ported constraint engine (see ConstraintService javadoc
 * for the frontend source this mirrors). Repositories are mocked so these
 * run without a database. */
class ConstraintServiceTest {

    private AvailabilityRepository availabilityRepository;
    private UserRepository userRepository;
    private ShiftRepository shiftRepository;
    private SettingsService settingsService;
    private ConstraintService constraintService;

    private Skill bartender;
    private Skill server;
    private Location seattle;

    @BeforeEach
    void setUp() {
        availabilityRepository = Mockito.mock(AvailabilityRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        shiftRepository = Mockito.mock(ShiftRepository.class);
        settingsService = Mockito.mock(SettingsService.class);
        constraintService = new ConstraintService(availabilityRepository, userRepository, shiftRepository, settingsService);

        when(settingsService.get()).thenReturn(ConstraintThresholds.builder().id(1L).build());
        when(availabilityRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(userRepository.findByRole(Mockito.any())).thenReturn(List.of());

        bartender = Skill.builder().id(1L).key("bartender").label("Bartender").build();
        server = Skill.builder().id(2L).key("server").label("Server").build();
        seattle = Location.builder().id(1L).name("Seattle Harbor").timezone("America/Los_Angeles").build();
    }

    private AppUser staffWith(Long id, Set<Skill> skills, Set<Location> certs, String homeTz) {
        return AppUser.builder().id(id).name("Test User").homeTimezone(homeTz)
                .skills(skills).certifiedLocations(certs).build();
    }

    @Test
    void blocksWhenSkillMissing() {
        AppUser user = staffWith(1L, Set.of(server), Set.of(seattle), "America/Los_Angeles");
        when(availabilityRepository.findByUserId(1L)).thenReturn(List.of(fullyAvailableAllWeek(user)));
        Shift shift = Shift.builder().id(10L).location(seattle).skillRequired(bartender).headcountNeeded(1)
                .startUtc(Instant.parse("2026-01-05T18:00:00Z")).endUtc(Instant.parse("2026-01-05T22:00:00Z")).build();

        AssignmentCheckResult result = constraintService.checkAssignment(user, shift, List.of());

        assertFalse(result.ok());
        assertTrue(result.violations().stream().anyMatch(v -> v.code() == ViolationCode.SKILL_MISMATCH));
    }

    @Test
    void blocksWhenNotCertifiedAtLocation() {
        Location miami = Location.builder().id(2L).name("Miami Shore").timezone("America/New_York").build();
        AppUser user = staffWith(1L, Set.of(bartender), Set.of(miami), "America/New_York");
        Shift shift = Shift.builder().id(10L).location(seattle).skillRequired(bartender).headcountNeeded(1)
                .startUtc(Instant.parse("2026-01-05T18:00:00Z")).endUtc(Instant.parse("2026-01-05T22:00:00Z")).build();

        AssignmentCheckResult result = constraintService.checkAssignment(user, shift, List.of());

        assertFalse(result.ok());
        assertTrue(result.violations().stream().anyMatch(v -> v.code() == ViolationCode.LOCATION_NOT_CERTIFIED));
    }

    @Test
    void blocksOnOverlappingDoubleBooking() {
        AppUser user = staffWith(1L, Set.of(bartender), Set.of(seattle), "America/Los_Angeles");
        Shift existing = Shift.builder().id(9L).location(seattle).skillRequired(bartender).headcountNeeded(1)
                .startUtc(Instant.parse("2026-01-05T17:00:00Z")).endUtc(Instant.parse("2026-01-05T21:00:00Z")).build();
        Shift target = Shift.builder().id(10L).location(seattle).skillRequired(bartender).headcountNeeded(1)
                .startUtc(Instant.parse("2026-01-05T19:00:00Z")).endUtc(Instant.parse("2026-01-05T23:00:00Z")).build();

        AssignmentCheckResult result = constraintService.checkAssignment(user, target, List.of(existing));

        assertFalse(result.ok());
        assertTrue(result.violations().stream().anyMatch(v -> v.code() == ViolationCode.DOUBLE_BOOKED));
    }

    @Test
    void blocksWhenLessThanMinRestBetweenShifts() {
        AppUser user = staffWith(1L, Set.of(bartender), Set.of(seattle), "America/Los_Angeles");
        // Ends 22:00, next starts 05:00 next day = 7h rest, under the 10h minimum.
        Shift existing = Shift.builder().id(9L).location(seattle).skillRequired(bartender).headcountNeeded(1)
                .startUtc(Instant.parse("2026-01-05T18:00:00Z")).endUtc(Instant.parse("2026-01-05T22:00:00Z")).build();
        Shift target = Shift.builder().id(10L).location(seattle).skillRequired(bartender).headcountNeeded(1)
                .startUtc(Instant.parse("2026-01-06T05:00:00Z")).endUtc(Instant.parse("2026-01-06T09:00:00Z")).build();

        AssignmentCheckResult result = constraintService.checkAssignment(user, target, List.of(existing));

        assertFalse(result.ok());
        assertTrue(result.violations().stream().anyMatch(v -> v.code() == ViolationCode.REST_PERIOD));
    }

    @Test
    void allowsCleanAssignment() {
        AppUser user = staffWith(1L, Set.of(bartender), Set.of(seattle), "America/Los_Angeles");
        when(availabilityRepository.findByUserId(1L)).thenReturn(List.of(fullyAvailableAllWeek(user)));
        Shift shift = Shift.builder().id(10L).location(seattle).skillRequired(bartender).headcountNeeded(1)
                .startUtc(Instant.parse("2026-01-05T18:00:00Z")).endUtc(Instant.parse("2026-01-05T22:00:00Z")).build();

        AssignmentCheckResult result = constraintService.checkAssignment(user, shift, List.of());

        assertTrue(result.ok(), () -> "Unexpected violations: " + result.violations());
    }

    private AvailabilityWindow fullyAvailableAllWeek(AppUser user) {
        return AvailabilityWindow.builder().user(user).type(AvailabilityType.RECURRING)
                .dayOfWeek(1).startMinutes(0).endMinutes(1439).available(true).build();
    }
}
