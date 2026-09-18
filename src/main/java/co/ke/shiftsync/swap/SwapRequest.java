package co.ke.shiftsync.swap;

import co.ke.shiftsync.schedule.Shift;
import co.ke.shiftsync.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Requirement #3 (shift swapping & coverage). A SWAP always needs a named
 * partner; a DROP starts partnerless (status OPEN) until either someone
 * picks it up (partner set, status -> PENDING_MANAGER) or it EXPIREs
 * unclaimed. The ORIGINAL assignment is never touched until a manager
 * approves — see SwapService.managerApprove — which is exactly what makes
 * the "Regret Swap" scenario a no-op on the schedule itself.
 */
@Entity
@Table(name = "swap_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwapRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SwapKind kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id")
    private AppUser requestedBy;

    /** SWAP: the specific person being asked to trade. DROP: null until
     * someone picks it up, then the picker. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_user_id")
    private AppUser partner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SwapStatus status;

    @Column(nullable = false)
    private Instant createdAtUtc;

    private Instant resolvedAtUtc;

    /** DROP only: auto-expires this many hours before the shift if unclaimed. */
    private Instant expiresAtUtc;

    @ElementCollection
    @CollectionTable(name = "swap_history", joinColumns = @JoinColumn(name = "swap_id"))
    @OrderColumn(name = "seq")
    @Builder.Default
    private List<SwapHistoryEntry> history = new ArrayList<>();
}
