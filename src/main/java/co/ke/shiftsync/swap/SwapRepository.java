package co.ke.shiftsync.swap;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SwapRepository extends JpaRepository<SwapRequest, Long> {
    List<SwapRequest> findByShiftId(Long shiftId);
    List<SwapRequest> findByRequestedById(Long requestedById);
    List<SwapRequest> findByPartnerId(Long partnerId);
    List<SwapRequest> findByStatus(SwapStatus status);
}
