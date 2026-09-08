package ai.daylight.assistant.data

import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.SkillEntity
import ai.daylight.assistant.domain.StarterSkills
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class SkillRepository(private val dao: AssistantDao) {
    val skills: Flow<List<SkillEntity>> = dao.observeSkills()

    suspend fun save(name: String, description: String, instructions: String, examplePrompts: List<String>): SkillEntity {
        val now = System.currentTimeMillis()
        val skill = SkillEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim().take(60),
            description = description.trim().take(240),
            instructions = instructions.trim().take(8_000),
            examplePrompts = examplePrompts.map(String::trim).filter(String::isNotBlank).take(6).joinToString("\n"),
            enabled = true,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertSkill(skill)
        return skill
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setSkillEnabled(id, enabled, System.currentTimeMillis())
    suspend fun delete(id: String) = dao.deleteSkill(id)

    /**
     * Inserts missing starter skills. Never overwrites a row the user already has
     * (same id or same name), so disable/delete/customise is respected.
     */
    suspend fun seedStarterSkills() {
        val existing = dao.allSkills()
        val ids = existing.map { it.id }.toSet()
        val names = existing.map { it.name.lowercase() }.toSet()
        val now = System.currentTimeMillis()
        StarterSkills.catalog.forEach { starter ->
            if (starter.id in ids || starter.name.lowercase() in names) return@forEach
            dao.upsertSkill(
                SkillEntity(
                    id = starter.id,
                    name = starter.name.trim().take(60),
                    description = starter.description.trim().take(240),
                    instructions = starter.instructions.trim().take(8_000),
                    examplePrompts = starter.examplePrompts.map(String::trim).filter(String::isNotBlank).take(6).joinToString("\n"),
                    enabled = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }
}
