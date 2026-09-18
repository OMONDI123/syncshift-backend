package co.ke.shiftsync.audit;

import co.ke.shiftsync.audit.AuditDtos.AuditEntryResponse;
import co.ke.shiftsync.common.AuditEntityType;
import co.ke.shiftsync.security.CurrentUser;
import co.ke.shiftsync.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/** Audit trail viewing (managers + admins) and CSV export (admins only) —
 * requirement #9 in the brief: "who made the change, when, what the
 * before/after state was" plus per-shift history and admin export. */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final CurrentUser currentUser;
    private final PermissionService permissions;

    @GetMapping
    public Page<AuditEntryResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        if (!permissions.canViewAuditLog(currentUser.get())) {
            throw new co.ke.shiftsync.common.exceptions.UnauthorizedActionException(
                    "Only managers and admins can view the audit log.");
        }
        return auditService.search(from, to, locationId, PageRequest.of(page, size)).map(AuditEntryResponse::from);
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public List<AuditEntryResponse> history(@PathVariable AuditEntityType entityType, @PathVariable String entityId) {
        if (!permissions.canViewAuditLog(currentUser.get())) {
            throw new co.ke.shiftsync.common.exceptions.UnauthorizedActionException(
                    "Only managers and admins can view the audit log.");
        }
        return auditService.historyFor(entityType, entityId).stream().map(AuditEntryResponse::from).collect(Collectors.toList());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long locationId) {
        if (!permissions.canExportAuditLog(currentUser.get())) {
            throw new co.ke.shiftsync.common.exceptions.UnauthorizedActionException(
                    "Only admins can export the audit log.");
        }
        var rows = auditService.search(from, to, locationId, PageRequest.of(0, 5000)).getContent();
        StringBuilder csv = new StringBuilder("id,atUtc,actorUserId,actorName,entityType,entityId,action,locationId,before,after\n");
        for (var e : rows) {
            csv.append(csvJoin(e.getId(), e.getAtUtc(), e.getActorUserId(), e.getActorName(),
                    e.getEntityType(), e.getEntityId(), e.getAction(), e.getLocationId(),
                    e.getBeforeJson(), e.getAfterJson())).append("\n");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header("Content-Disposition", "attachment; filename=audit-log.csv")
                .body(csv.toString());
    }

    private String csvJoin(Object... vals) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.length; i++) {
            if (i > 0) sb.append(",");
            String v = vals[i] == null ? "" : vals[i].toString().replace("\"", "'").replace("\n", " ");
            sb.append("\"").append(v).append("\"");
        }
        return sb.toString();
    }
}
