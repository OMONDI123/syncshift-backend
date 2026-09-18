package co.ke.shiftsync.skill;

import co.ke.shiftsync.audit.AuditService;
import co.ke.shiftsync.common.AuditEntityType;
import co.ke.shiftsync.common.exceptions.BusinessRuleException;
import co.ke.shiftsync.common.exceptions.NotFoundException;
import co.ke.shiftsync.schedule.ShiftRepository;
import co.ke.shiftsync.security.CurrentUser;
import co.ke.shiftsync.security.PermissionService;
import co.ke.shiftsync.skill.SkillDtos.SkillRequest;
import co.ke.shiftsync.user.AppUser;
import co.ke.shiftsync.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Part of the admin Setup module: skills are a real, editable catalog
 * rather than a hardcoded enum, per the frontend's in-progress
 * `settingsStore.ts` design this backend mirrors. */
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository repository;
    private final UserRepository userRepository;
    private final ShiftRepository shiftRepository;
    private final PermissionService permissions;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public List<Skill> findAll() {
        return repository.findAll();
    }

    @Transactional
    public Skill create(SkillRequest req) {
        AppUser actor = currentUser.get();
        permissions.requireManageSettings(actor);
        String key = normalizeKey(req.key());
        if (repository.findByKeyIgnoreCase(key).isPresent()) {
            throw new BusinessRuleException("A skill with that key already exists.");
        }
        Skill saved = repository.save(Skill.builder().key(key).label(req.label()).colorHex(req.colorHex()).build());
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SETTINGS, "skill:" + saved.getId(), "skill_created");
        return saved;
    }

    @Transactional
    public Skill update(Long id, SkillRequest req) {
        AppUser actor = currentUser.get();
        permissions.requireManageSettings(actor);
        Skill skill = repository.findById(id).orElseThrow(() -> new NotFoundException("Skill not found: " + id));
        skill.setLabel(req.label());
        if (req.colorHex() != null) skill.setColorHex(req.colorHex());
        Skill saved = repository.save(skill);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SETTINGS, "skill:" + id, "skill_updated");
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        AppUser actor = currentUser.get();
        permissions.requireManageSettings(actor);
        Skill skill = repository.findById(id).orElseThrow(() -> new NotFoundException("Skill not found: " + id));
        boolean inUseByStaff = !userRepository.findAll().isEmpty()
                && userRepository.findAll().stream().anyMatch(u -> u.getSkills().contains(skill));
        boolean inUseByShifts = shiftRepository.existsBySkillRequired(skill);
        if (inUseByStaff || inUseByShifts) {
            throw new BusinessRuleException("Can't delete a skill that's still assigned to staff or used by a shift.");
        }
        repository.delete(skill);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.SETTINGS, "skill:" + id, "skill_deleted");
    }

    private String normalizeKey(String key) {
        return key.trim().toLowerCase().replaceAll("\\s+", "_");
    }
}
