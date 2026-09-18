package co.ke.shiftsync.audit;

import co.ke.shiftsync.common.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByAtUtcDesc(AuditEntityType type, String entityId);

    @Query("SELECT a FROM AuditLogEntry a WHERE " +
            "(cast(:from as timestamp) IS NULL OR a.atUtc >= cast(:from as timestamp)) AND " +
            "(cast(:to as timestamp) IS NULL OR a.atUtc <= cast(:to as timestamp)) AND " +
            "(cast(:locationId as long) IS NULL OR a.locationId = cast(:locationId as long)) " +
            "ORDER BY a.atUtc DESC")
    Page<AuditLogEntry> search(@Param("from") Instant from, @Param("to") Instant to,
                                @Param("locationId") Long locationId, Pageable pageable);

	Page<AuditLogEntry> findAll(Specification<AuditLogEntry> spec, Pageable sorted);
}