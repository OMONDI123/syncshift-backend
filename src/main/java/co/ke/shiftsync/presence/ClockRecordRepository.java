package co.ke.shiftsync.presence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClockRecordRepository extends JpaRepository<ClockRecord, Long> {
    List<ClockRecord> findByLocationIdAndClockOutUtcIsNull(Long locationId);
    Optional<ClockRecord> findByUserIdAndShiftIdAndClockOutUtcIsNull(Long userId, Long shiftId);
}
