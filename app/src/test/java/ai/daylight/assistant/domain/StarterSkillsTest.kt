package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StarterSkillsTest {
    @Test fun catalogIsUniqueAndSizedForPrompt() {
        assertThat(StarterSkills.catalog.size).isAtLeast(6)
        assertThat(StarterSkills.catalog.map { it.id }.toSet()).hasSize(StarterSkills.catalog.size)
        assertThat(StarterSkills.catalog.map { it.name.lowercase() }.toSet()).hasSize(StarterSkills.catalog.size)
        StarterSkills.catalog.forEach { skill ->
            assertThat(StarterSkills.isStarterId(skill.id)).isTrue()
            assertThat(skill.name.length).isIn(3..60)
            assertThat(skill.description.length).isIn(8..240)
            assertThat(skill.instructions.length).isIn(20..8_000)
            assertThat(skill.examplePrompts).isNotEmpty()
        }
    }
}
