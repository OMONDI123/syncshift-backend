package co.ke.shiftsync.notification;

/** Mirrors the frontend's notification kinds (types/index.ts) so the two
 * systems agree on the wire shape once API integration happens. */
public enum NotificationType {
    SHIFT_ASSIGNED,
    SHIFT_CHANGED,
    SHIFT_UNASSIGNED,
    SCHEDULE_PUBLISHED,
    SWAP_REQUESTED,
    SWAP_ACCEPTED,
    SWAP_APPROVED,
    SWAP_REJECTED,
    SWAP_CANCELLED,
    DROP_POSTED,
    DROP_CLAIMED,
    DROP_EXPIRED,
    AVAILABILITY_CHANGED,
    OVERTIME_WARNING,
    APPROVAL_NEEDED
}
