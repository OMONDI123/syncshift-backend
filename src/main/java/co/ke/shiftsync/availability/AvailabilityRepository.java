package co.ke.shiftsync.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvailabilityRepository extends JpaRepository<AvailabilityWindow, Long> {
    List<AvailabilityWindow> findByUserId(Long userId);
}
