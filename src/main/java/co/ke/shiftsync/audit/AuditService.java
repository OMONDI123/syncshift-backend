package co.ke.shiftsync.audit;

import co.ke.shiftsync.common.AuditEntityType;
import jakarta.persistence.criteria.Predicate;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public void log(Long actorUserId, String actorName, AuditEntityType type, String entityId, String action) {
        log(actorUserId, actorName, type, entityId, action, null, null, null);
    }

    public void log(Long actorUserId, String actorName, AuditEntityType type, String entityId, String action, Long locationId) {
        log(actorUserId, actorName, type, entityId, action, null, null, locationId);
    }

    public void log(Long actorUserId, String actorName, AuditEntityType type, String entityId, String action,
                     Object before, Object after) {
        log(actorUserId, actorName, type, entityId, action, before, after, null);
    }

    public void log(Long actorUserId, String actorName, AuditEntityType type, String entityId, String action,
                     Object before, Object after, Long locationId) {
        repository.save(AuditLogEntry.builder()
                .atUtc(Instant.now())
                .actorUserId(actorUserId)
                .actorName(actorName)
                .entityType(type)
                .entityId(entityId)
                .action(action)
                .locationId(locationId)
                .beforeJson(toJson(before))
                .afterJson(toJson(after))
                .build());
    }

    public List<AuditLogEntry> historyFor(AuditEntityType type, String entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByAtUtcDesc(type, entityId);
    }
    public Page<AuditLogEntry> search(Instant from, Instant to, Long locationId, Pageable pageable) {
        Specification<AuditLogEntry> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("atUtc"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("atUtc"), to));
            }
            if (locationId != null) {
                predicates.add(cb.equal(root.<Long>get("locationId"), locationId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "atUtc"));

        return repository.findAll(spec, sorted);
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
