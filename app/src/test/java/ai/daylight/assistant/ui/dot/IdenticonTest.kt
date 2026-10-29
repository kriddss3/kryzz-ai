package ai.daylight.assistant.ui.dot

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** C9: bot faces match `identicon()` in kit/screens/src/sprites.py exactly. */
class IdenticonTest {
    private fun rows(key: String): List<String> =
        Identicon.grid(key).map { row -> row.joinToString("") { if (it) "1" else "0" } }

    @Test fun scoutWithSeedFourMatchesThePythonOutput() {
        // python: identicon("Scout4", 7), the face in the kit's crew.png and bot-detail.png
        assertThat(rows(Identicon.key("Scout", 4))).containsExactly(
            "1011101",
            "0111110",
            "1001001",
            "1001001",
            "0100010",
            "0111110",
            "1110111"
        ).inOrder()
    }

    @Test fun theOtherKitBotsMatchToo() {
        assertThat(rows("Pilot5")).containsExactly("1100011", "0101010", "1011101", "1011101", "0111110", "1000001", "1000001").inOrder()
        assertThat(rows("Shelf0")).containsExactly("1110111", "1011101", "0001000", "0100010", "1100011", "0110110", "1011101").inOrder()
        assertThat(rows("Tally2")).containsExactly("0011100", "0100010", "1011101", "0110110", "0011100", "1100011", "1101011").inOrder()
    }

    @Test fun everyFaceIsMirroredAndSevenBySeven() {
        listOf("Scout4", "Kryzz0", "Ålesund ferry 12", "").forEach { key ->
            val grid = Identicon.grid(key)
            assertThat(grid).hasSize(Identicon.SIZE)
            grid.forEach { row ->
                assertThat(row).hasSize(Identicon.SIZE)
                assertThat(row).isEqualTo(row.reversed())
            }
        }
    }

    @Test fun theSeedChangesTheFaceButNotTheName() {
        assertThat(Identicon.key("Scout", 4)).isEqualTo("Scout4")
        assertThat(Identicon.grid(Identicon.key("Scout", 4))).isNotEqualTo(Identicon.grid(Identicon.key("Scout", 5)))
        assertThat(Identicon.grid("Scout4")).isEqualTo(Identicon.grid("Scout4"))
    }

    @Test fun liveDotsFollowTheKitsDiagonalRule() {
        // s_crew.py: a lit cell turns yellow when (x + y) % 3 == 0.
        assertThat(Identicon.liveDot(0, 0)).isTrue()
        assertThat(Identicon.liveDot(1, 2)).isTrue()
        assertThat(Identicon.liveDot(1, 1)).isFalse()
        assertThat(Identicon.liveDot(6, 6)).isTrue()
    }
}
