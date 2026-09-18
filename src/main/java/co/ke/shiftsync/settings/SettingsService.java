package co.ke.shiftsync.settings;

import co.ke.shiftsync.audit.AuditService;
import co.ke.shiftsync.common.AuditEntityType;
import co.ke.shiftsync.security.CurrentUser;
import co.ke.shiftsync.security.PermissionService;
import co.ke.shiftsync.settings.SettingsDtos.ThresholdsRequest;
import co.ke.shiftsync.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final ConstraintThresholdsRepository repository;
    private final PermissionService permissions;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    /** Everyone (any authenticated user) can READ thresholds — the UI needs
     * them to explain rules to staff too — only admins can WRITE them. */
    public ConstraintThresholds get() {
        return repository.findById(1L).orElseGet(() -> repository.save(ConstraintThresholds.builder().id(1L).build()));
    }

    @Transactional
    public ConstraintThresholds update(ThresholdsRequest req) {
        AppUser actor = currentUser.get();
        permissions.requireManageSettings(actor);
        ConstraintThresholds t = get();
        var before = SettingsDtos.ThresholdsResponse.from(t);

        if (req.minRestHours() != null) t.setMinRestHours(req.minRestHours());
        if (req.dailyHardBlockHours() != null) t.setDailyHardBlockHours(req.dailyHardBlockHours());
        if (req.dailyWarningHours() != null) t.setDailyWarningHours(req.dailyWarningHours());
        if (req.weeklyWarningHours() != null) t.setWeeklyWarningHours(req.weeklyWarningHours());
        if (req.weeklyFullTimeHours() != null) t.setWeeklyFullTimeHours(req.weeklyFullTimeHours());
        if (req.maxPendingSwapsPerStaff() != null) t.setMaxPendingSwapsPerStaff(req.maxPendingSwapsPerStaff());
        if (req.dropExpiryHoursBeforeShift() != null) t.setDropExpiryHoursBeforeShift(req.dropExpiryHoursBeforeShift());
        if (req.publishEditCutoffHours() != null) t.setPublishEditCutoffHours(req.publishEditCutoffHours());
        if (req.sixthConsecutiveDayWarning() != null) t.setSixthConsecutiveDayWarning(req.sixthConsecutiveDayWarning());
        if (req.seventhConsecutiveDayBlock() != null) t.setSeventhConsecutiveDayBlock(req.seventhConsecutiveDayBlock());

        ConstraintThresholds saved = repository.save(t);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SETTINGS, "thresholds", "updated",
                before, SettingsDtos.ThresholdsResponse.from(saved));
        return saved;
    }
}
