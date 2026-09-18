package co.ke.shiftsync.schedule;

import co.ke.shiftsync.skill.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findByLocationId(Long locationId);

    @Query("SELECT s FROM Shift s JOIN s.assignedUsers u WHERE u.id = :userId")
    List<Shift> findByAssignedUserId(@Param("userId") Long userId);

    @Query("SELECT s FROM Shift s JOIN s.assignedUsers u WHERE u.id = :userId AND s.id <> :excludeShiftId")
    List<Shift> findByAssignedUserIdExcluding(@Param("userId") Long userId, @Param("excludeShiftId") Long excludeShiftId);

    @Query("SELECT s FROM Shift s WHERE s.location.id = :locationId AND s.startUtc >= :from AND s.startUtc < :to")
    List<Shift> findByLocationAndWeek(@Param("locationId") Long locationId, @Param("from") Instant from, @Param("to") Instant to);

    boolean existsBySkillRequired(Skill skill);

    boolean existsByLocationId(Long locationId);
}
