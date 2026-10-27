package ai.daylight.assistant.ui.talk

import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.domain.MessageStatus
import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Test

class DepartureBoardTest {
    private fun msg(id: String, role: String, at: Long, content: String = id, status: MessageStatus = MessageStatus.COMPLETE) =
        MessageEntity(id = id, conversationId = "c", role = role, content = content, createdAt = at, status = status.name)

    @Test fun eachQuestionOpensAnExchangeWithTheAnswersThatFollow() {
        val exchanges = talkExchanges(
            listOf(
                msg("q1", "USER", 1), msg("a1", "ASSISTANT", 2),
                msg("q2", "USER", 3), msg("a2", "ASSISTANT", 4), msg("a2b", "ASSISTANT", 5),
                msg("q3", "USER", 6),
            )
        )
        assertThat(exchanges.map { it.id }).containsExactly("q1", "q2", "q3").inOrder()
        assertThat(exchanges[1].answers.map { it.id }).containsExactly("a2", "a2b").inOrder()
        assertThat(exchanges[2].answers).isEmpty()
    }

    @Test fun answersBeforeAnyQuestionStandAlone() {
        val exchanges = talkExchanges(listOf(msg("hello", "ASSISTANT", 1), msg("q", "USER", 2)))
        assertThat(exchanges).hasSize(2)
        assertThat(exchanges[0].question).isNull()
        assertThat(exchanges[0].id).isEqualTo("hello")
        assertThat(exchanges[0].gist).isEqualTo(TalkGist.FALLBACK)
    }

    @Test fun toolRowsAreIgnoredAndEmptyInputGivesNothing() {
        assertThat(talkExchanges(emptyList())).isEmpty()
        assertThat(talkExchanges(listOf(msg("t", "TOOL", 1)))).isEmpty()
    }

    @Test fun streamingAndGistComeFromTheExchange() {
        val ex = talkExchanges(
            listOf(msg("q", "USER", 1, "Any open pixel fonts?"), msg("a", "ASSISTANT", 2, status = MessageStatus.STREAMING))
        ).single()
        assertThat(ex.streaming).isTrue()
        assertThat(ex.gist).isEqualTo("OPEN PIXEL FONTS")
        assertThat(ex.createdAt).isEqualTo(1)
    }

    private val zone: TimeZone = TimeZone.getTimeZone("Europe/Riga")
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply { clear(); set(year, month, day, hour, minute) }.timeInMillis

    @Test fun boardTimesAreClockTodayDateThisYearMonthBefore() {
        val now = at(2026, Calendar.OCTOBER, 27, 21, 0)
        assertThat(boardTimeLabel(at(2026, Calendar.OCTOBER, 27, 9, 2), now, zone, Locale.UK)).isEqualTo("09:02")
        assertThat(boardTimeLabel(at(2026, Calendar.OCTOBER, 27, 9, 2), now, zone, Locale.US, is24Hour = false)).isEqualTo("9:02 AM")
        assertThat(boardTimeLabel(at(2026, Calendar.OCTOBER, 26, 23, 59), now, zone, Locale.UK)).isEqualTo("26 OCT")
        assertThat(boardTimeLabel(at(2025, Calendar.MARCH, 3, 12, 0), now, zone, Locale.UK)).isEqualTo("MAR 2025")
    }

    @Test fun answerPreviewIsPlainText() {
        assertThat(answerPreview("**Yes**, a few [1]. See [docs](https://x.y) and `code`.\n\n```kt\nval x = 1\n```"))
            .isEqualTo("Yes, a few . See docs and code.")
    }

    @Test fun replyPlaceholderFollowsTheState() {
        assertThat(replyPlaceholder(generating = false, editing = false, dictating = false)).isEqualTo("Reply")
        assertThat(replyPlaceholder(generating = true, editing = false, dictating = false)).isEqualTo("Queue a follow-up")
        assertThat(replyPlaceholder(generating = false, editing = true, dictating = false)).isEqualTo("Edit and resend")
        assertThat(replyPlaceholder(generating = true, editing = true, dictating = true)).isEqualTo("Listening…")
    }
}
