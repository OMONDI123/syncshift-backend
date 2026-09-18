package co.ke.shiftsync.swap;

import co.ke.shiftsync.security.CurrentUser;
import co.ke.shiftsync.swap.SwapDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/swaps")
@RequiredArgsConstructor
public class SwapController {

    private final SwapService service;
    private final CurrentUser currentUser;

    @GetMapping("/shift/{shiftId}")
    public List<SwapResponse> forShift(@PathVariable Long shiftId) {
        return service.forShift(shiftId).stream().map(SwapMapper::toResponse).toList();
    }

    @GetMapping("/pending-approvals")
    public List<SwapResponse> pendingApprovals() {
        return service.pendingApprovalsFor(currentUser.get()).stream().map(SwapMapper::toResponse).toList();
    }

    @GetMapping("/pending-count")
    public long pendingCount() {
        return service.pendingCountFor(currentUser.id());
    }

    /** Additive read endpoints for the frontend's Marketplace page — the
     * original brief only specified per-shift and pending-approval views,
     * neither of which is enough to render "everything open I could pick
     * up" or "everything I've requested, in any status." */
    @GetMapping("/open")
    public List<SwapResponse> open() {
        return service.openDropsFor(currentUser.get()).stream().map(SwapMapper::toResponse).toList();
    }

    @GetMapping("/mine")
    public List<SwapResponse> mine() {
        return service.mineFor(currentUser.id()).stream().map(SwapMapper::toResponse).toList();
    }

    @PostMapping("/request-swap")
    public SwapResponse requestSwap(@Valid @RequestBody RequestSwapRequest request) {
        return SwapMapper.toResponse(service.requestSwap(request.shiftId(), request.partnerUserId()));
    }

    @PostMapping("/request-drop")
    public SwapResponse requestDrop(@Valid @RequestBody RequestDropRequest request) {
        return SwapMapper.toResponse(service.requestDrop(request.shiftId()));
    }

    @PostMapping("/{id}/partner-accept")
    public SwapResponse partnerAccept(@PathVariable Long id) {
        return SwapMapper.toResponse(service.partnerAccept(id));
    }

    @PostMapping("/{id}/pick-up")
    public SwapResponse pickUp(@PathVariable Long id) {
        return SwapMapper.toResponse(service.pickUpDrop(id));
    }

    @PostMapping("/{id}/manager-approve")
    public SwapResponse approve(@PathVariable Long id) {
        return SwapMapper.toResponse(service.managerApprove(id));
    }

    @PostMapping("/{id}/manager-reject")
    public SwapResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return SwapMapper.toResponse(service.managerReject(id, request.reason()));
    }

    @PostMapping("/{id}/cancel")
    public SwapResponse cancel(@PathVariable Long id) {
        return SwapMapper.toResponse(service.requesterCancel(id));
    }
}
