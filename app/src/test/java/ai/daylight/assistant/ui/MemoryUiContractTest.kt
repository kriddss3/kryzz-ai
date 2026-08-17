package ai.daylight.assistant.ui

import ai.daylight.assistant.domain.MemoryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryUiContractTest {
    @Test
    fun addMemoryValidationMatchesRepositoryLimits() {
        assertEquals("Enter something to remember.", memoryInputError("   "))
        assertEquals("Add a little more detail.", memoryInputError("a".repeat(MemoryEngine.MIN_MEMORY_CHARS - 1)))
        assertNull(memoryInputError("a".repeat(MemoryEngine.MIN_MEMORY_CHARS)))
        assertNull(memoryInputError("a".repeat(MemoryEngine.MAX_MEMORY_CHARS)))
        assertEquals(
            "Keep this memory under ${MemoryEngine.MAX_MEMORY_CHARS} characters.",
            memoryInputError("a".repeat(MemoryEngine.MAX_MEMORY_CHARS + 1))
        )
    }
}
