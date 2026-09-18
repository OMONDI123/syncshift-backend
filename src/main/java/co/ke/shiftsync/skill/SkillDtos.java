package co.ke.shiftsync.skill;

import jakarta.validation.constraints.NotBlank;

public class SkillDtos {

    public record SkillRequest(
            @NotBlank(message = "Skill key is required") String key,
            @NotBlank(message = "Skill label is required") String label,
            String colorHex
    ) {}

    public record SkillResponse(Long id, String key, String label, String colorHex) {
        public static SkillResponse from(Skill s) {
            return new SkillResponse(s.getId(), s.getKey(), s.getLabel(), s.getColorHex());
        }
    }
}
