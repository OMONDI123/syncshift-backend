package co.ke.shiftsync.user;

import co.ke.shiftsync.common.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findByRole(Role role);

    @Query("SELECT u FROM AppUser u JOIN u.managedLocations l WHERE l.id = :locationId")
    List<AppUser> findManagersOfLocation(@Param("locationId") Long locationId);

    @Query("SELECT u FROM AppUser u JOIN u.certifiedLocations l WHERE l.id = :locationId AND u.role = 'STAFF'")
    List<AppUser> findStaffCertifiedAtLocation(@Param("locationId") Long locationId);

    /** Pessimistic write lock on a single staff member's row, held for the
     * duration of the enclosing transaction. ShiftService.assignUser takes
     * this lock before re-checking constraints and committing an assignment,
     * so that if two managers try to assign the SAME staff member to two
     * DIFFERENT shifts at the same instant, the second transaction blocks
     * until the first commits — then re-evaluates against the now-current
     * data and correctly reports a double-booking/rest-period conflict
     * instead of silently racing past it. This is what makes the
     * "Simultaneous Assignment" scenario safe even though the two shifts
     * involved are different rows (so Shift's own @Version alone wouldn't
     * catch it). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.id = :id")
    Optional<AppUser> lockById(@Param("id") Long id);
}
