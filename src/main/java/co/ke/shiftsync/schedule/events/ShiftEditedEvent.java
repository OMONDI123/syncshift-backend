package co.ke.shiftsync.schedule.events;

import org.springframework.context.ApplicationEvent;

/** Published whenever a manager edits an existing shift's core details
 * (time, location, skill, headcount) — NOT when staff are simply assigned/
 * unassigned. SwapService listens for this to auto-cancel any pending
 * swap/drop request tied to the shift, per the brief's edge case: "If a
 * swap is pending and the manager edits that shift, the swap request
 * should be automatically cancelled with notification." */
public class ShiftEditedEvent extends ApplicationEvent {
    private final Long shiftId;

    public ShiftEditedEvent(Object source, Long shiftId) {
        super(source);
        this.shiftId = shiftId;
    }

    public Long getShiftId() {
        return shiftId;
    }
}
