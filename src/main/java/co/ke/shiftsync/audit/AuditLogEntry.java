package co.ke.shiftsync.audit;

import co.ke.shiftsync.common.AuditEntityType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Immutable audit trail row. before/after are stored as JSON strings so this
 * one table can log any entity type without a schema per entity — the
 * tradeoff (documented) is you can't SQL-query inside them, only display them,
 * which is all the brief's "view shift history" / "export audit logs" needs. */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entityType,entityId"),
        @Index(name = "idx_audit_at", columnList = "atUtc")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant atUtc;

    private Long actorUserId;

    @Column(nullable = false)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditEntityType entityType;

    @Column(nullable = false)
    private String entityId;

    @Column(nullable = false)
    private String action;

    /** Locations lets admins export/filter audit logs by location even when
     * the entity itself (e.g. a user) has no direct location column. */
    private Long locationId;

    @Lob
    private String beforeJson;

    @Lob
    private String afterJson;
}
