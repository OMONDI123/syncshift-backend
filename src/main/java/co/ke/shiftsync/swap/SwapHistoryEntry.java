package co.ke.shiftsync.swap;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.time.Instant;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwapHistoryEntry {
    private Instant atUtc;
    private String event;
    private Long byUserId;
}
