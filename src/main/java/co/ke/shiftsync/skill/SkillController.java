package co.ke.shiftsync.skill;

import co.ke.shiftsync.skill.SkillDtos.SkillResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only convenience endpoint — writes go through /setup/skills (admin-only). */
@RestController
@RequiredArgsConstructor
public class SkillController {

    private final SkillRepository repository;

    @GetMapping("/skills")
    public List<SkillResponse> list() {
        return repository.findAll().stream().map(SkillResponse::from).toList();
    }
}
