package ai.daylight.assistant.data

import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.SkillEntity
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
}
