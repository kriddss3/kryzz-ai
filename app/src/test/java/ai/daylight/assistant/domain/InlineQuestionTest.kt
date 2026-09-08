package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the v5.7.2 interactive-question-card contract: fenced kryzz-question blocks in an
 * assistant reply parse into tappable cards, malformed/partial blocks degrade to ordinary
 * markdown, and answers are echoed back in a shape the model can connect to its question.
 */
class InlineQuestionTest {

    private fun questionsOf(content: String): List<InlineQuestion> =
        InlineQuestionProtocol.parse(content).filterIsInstance<MessageSegment.Question>().map { it.question }

    private fun markdownOf(content: String): List<String> =
        InlineQuestionProtocol.parse(content).filterIsInstance<MessageSegment.Markdown>().map { it.text }

    @Test fun plainTextStaysOneMarkdownSegment() {
        val segments = InlineQuestionProtocol.parse("Just a normal answer, no cards.")
        assertThat(segments).hasSize(1)
        assertThat(segments.first()).isEqualTo(MessageSegment.Markdown("Just a normal answer, no cards."))
    }

    @Test fun validBlockBecomesQuestionCard() {
        val content = "Pick one:\n```kryzz-question\n{\"question\": \"Which subject first?\", \"options\": [\"Business Management HL\", \"Math AI SL\"]}\n```"
        val questions = questionsOf(content)
        assertThat(questions).hasSize(1)
        assertThat(questions.first().question).isEqualTo("Which subject first?")
        assertThat(questions.first().options).containsExactly("Business Management HL", "Math AI SL").inOrder()
    }

    @Test fun textBeforeAndAfterBlockIsPreserved() {
        val content = "Before.\n```kryzz-question\n{\"question\": \"Tea or coffee?\", \"options\": [\"Tea\", \"Coffee\"]}\n```\nAfter."
        val segments = InlineQuestionProtocol.parse(content)
        assertThat(segments).hasSize(3)
        assertThat(segments[0]).isEqualTo(MessageSegment.Markdown("Before."))
        assertThat(segments[1]).isInstanceOf(MessageSegment.Question::class.java)
        assertThat(segments[2]).isEqualTo(MessageSegment.Markdown("After."))
    }

    @Test fun blockWithoutOptionsIsFreeTextQuestion() {
        val questions = questionsOf("```kryzz-question\n{\"question\": \"What should I call you?\"}\n```")
        assertThat(questions).hasSize(1)
        assertThat(questions.first().options).isEmpty()
    }

    @Test fun malformedJsonStaysInlineAsMarkdown() {
        val content = "Hi\n```kryzz-question\n{not json}\n```"
        val segments = InlineQuestionProtocol.parse(content)
        assertThat(questionsOf(content)).isEmpty()
        assertThat(segments).hasSize(1)
        // The raw block survives in the text stream so it renders as a code block.
        assertThat((segments.first() as MessageSegment.Markdown).text).contains("```kryzz-question")
    }

    @Test fun partialStreamedBlockDoesNotParse() {
        // Mid-stream the closing fence has not arrived yet — no card, no crash.
        val partial = "Thinking…\n```kryzz-question\n{\"question\": \"Whi"
        assertThat(questionsOf(partial)).isEmpty()
    }

    @Test fun invalidQuestionFailsValidation() {
        // Blank question text → not a card.
        assertThat(questionsOf("```kryzz-question\n{\"question\": \" \", \"options\": [\"a\"]}\n```")).isEmpty()
        // Oversized question → not a card.
        val huge = "x".repeat(InlineQuestion.MAX_QUESTION_CHARS + 1)
        assertThat(questionsOf("```kryzz-question\n{\"question\": \"$huge\"}\n```")).isEmpty()
    }

    @Test fun optionsAreTrimmedBlankDroppedAndCapped() {
        val options = (1..12).joinToString(", ") { "\"Option $it\"" }
        val questions = questionsOf("""```kryzz-question
{"question": "Pick", "options": [$options, "  ", " Option 1 "]}
```""")
        assertThat(questions).hasSize(1)
        // distinct() collapses the duplicate "Option 1", then the cap keeps 8.
        assertThat(questions.first().options).hasSize(8)
        assertThat(questions.first().options.first()).isEqualTo("Option 1")
    }

    @Test fun twoBlocksBothParse() {
        val content = "```kryzz-question\n{\"question\": \"First?\", \"options\": [\"a\"]}\n```\nmiddle\n```kryzz-question\n{\"question\": \"Second?\"}\n```"
        val questions = questionsOf(content)
        assertThat(questions.map { it.question }).containsExactly("First?", "Second?").inOrder()
        assertThat(markdownOf(content)).containsExactly("middle")
    }

    @Test fun validBlockAfterInvalidOneParsesAndKeepsInvalidInline() {
        val content = "```kryzz-question\n{bad}\n```\n```kryzz-question\n{\"question\": \"Real?\", \"options\": [\"yes\"]}\n```"
        val questions = questionsOf(content)
        assertThat(questions).hasSize(1)
        assertThat(questions.first().question).isEqualTo("Real?")
        assertThat(markdownOf(content).joinToString("\n")).contains("{bad}")
    }

    @Test fun unknownJsonKeysAreIgnored() {
        val questions = questionsOf("""```kryzz-question
{"question": "Colour?", "options": ["red"], "header": "ignored"}
```""")
        assertThat(questions).hasSize(1)
    }

    @Test fun answerEchoMentionsQuestionAndAnswer() {
        val echo = InlineQuestionProtocol.formatAnswerEcho("Which subject first?", "Physics SL")
        assertThat(echo).contains("Which subject first?")
        assertThat(echo).contains("Physics SL")
    }

    @Test fun containsCardDetectsCards() {
        assertThat(InlineQuestionProtocol.containsCard("no cards here")).isFalse()
        assertThat(
            InlineQuestionProtocol.containsCard("```kryzz-question\n{\"question\": \"Q?\", \"options\": [\"a\"]}\n```")
        ).isTrue()
    }

    @Test fun sanitisedEnforcesOptionLengthCap() {
        val longOption = "y".repeat(InlineQuestion.MAX_OPTION_CHARS + 1)
        assertThat(InlineQuestion("Q?", listOf(longOption)).sanitised()).isNull()
        assertThat(InlineQuestion("Q?", listOf("fine")).sanitised()).isNotNull()
    }
}
