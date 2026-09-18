package co.ke.shiftsync.analytics;

import co.ke.shiftsync.analytics.AnalyticsDtos.FairnessReport;
import co.ke.shiftsync.analytics.AnalyticsDtos.OvertimeReport;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** Requirement #5 (fairness analytics) and the cost-projection half of
 * requirement #4 (overtime). */
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService service;

    @GetMapping("/locations/{locationId}/distribution")
    public FairnessReport distribution(@PathVariable Long locationId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.distribution(locationId, from, to);
    }

    @GetMapping("/locations/{locationId}/overtime")
    public OvertimeReport overtime(@PathVariable Long locationId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant weekStartUtc) {
        return service.overtimeProjection(locationId, weekStartUtc);
    }
}
