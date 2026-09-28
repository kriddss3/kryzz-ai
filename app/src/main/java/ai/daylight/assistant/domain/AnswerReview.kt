package ai.daylight.assistant.domain

/**
 * v5.9 review pass: before a substantial agent answer is final, the model gets one extra
 * round to check its own draft against the request and the tool results in context.
 *
 * The executor keeps the streamed draft, appends it to the working history as an assistant
 * message followed by [directive], and runs one more model call. The reviewer either
 * replies with [SENTINEL] (the draft stands) or writes the complete corrected answer, which
 * replaces the draft in the same chat message. Trivial turns (no lookups, no files, a plain
 * capability) skip the pass, so a quick question costs no extra call.
 *
 * Pure and unit-tested; [AgentExecutor] only wires the extra round.
 */
internal object AnswerReview {

    /** Exact reply that keeps the draft unchanged. */
    const val SENTINEL = "REVIEW_OK"

    /** Capabilities whose turns always count as real work, even without a lookup. */
    private val substantialCapabilities = setOf(
        AgentCapability.DEEP_RESEARCH,
        AgentCapability.WIDE_SEARCH,
        AgentCapability.DOCUMENT,
        AgentCapability.SPREADSHEET,
        AgentCapability.DATABASE
    )

    data class Input(
        val agentMode: Boolean,
        val voiceMode: Boolean,
        /** Settings: "Review answers before sending". */
        val enabled: Boolean,
        val capability: AgentCapability,
        val didSearch: Boolean,
        val didFetch: Boolean,
        val outputKinds: Set<OutputKind>,
        /** The model's own streamed text (blank when the executor would use a fallback copy). */
        val draft: String,
        val alreadyReviewed: Boolean,
        /** The cost cap stopped the tools; a review would spend past the user's limit. */
        val costCapped: Boolean
    )

    fun shouldReview(input: Input): Boolean {
        if (!input.enabled || !input.agentMode || input.voiceMode) return false
        if (input.alreadyReviewed || input.costCapped || input.draft.isBlank()) return false
        val producedDeliverable = input.outputKinds.any { it in ArtifactKinds.fileKinds || it == OutputKind.CODE }
        return input.didSearch || input.didFetch || producedDeliverable ||
            input.capability in substantialCapabilities
    }

    /** Whether the review round re-offers create_artifact so a flawed file can be replaced. */
    fun offersArtifact(outputKinds: Set<OutputKind>): Boolean = outputKinds.any { it in ArtifactKinds.fileKinds }

    fun directive(offerArtifact: Boolean): String = buildString {
        append(
            "Review pass. The assistant message above is your draft answer to the user's latest " +
                "request. Check it against that request and the tool results above: parts of the " +
                "request left out, claims that contradict a source or have no source, citation " +
                "numbers that point at the wrong source, and promises such as \"here is the file\" " +
                "when no file was created. If anything needs to change, reply with the complete " +
                "corrected answer exactly as the user should see it, with no preamble and no " +
                "mention of this review. If the draft is already correct, reply with exactly " +
                "$SENTINEL and nothing else."
        )
        if (offerArtifact) {
            append(
                " If the file you created has errors, call create_artifact again with the " +
                    "complete corrected content; the new file replaces the old one."
            )
        }
    }

    enum class Verdict {
        /** Too little text to tell yet; keep the draft on screen. */
        UNDECIDED,
        /** The reviewer approved the draft. */
        APPROVED,
        /** The reviewer is writing a replacement answer. */
        REVISION
    }

    /**
     * Classifies the reviewer's streamed text so far. The sentinel is matched after trimming
     * whitespace and light markdown (`**REVIEW_OK**`, `` `REVIEW_OK` ``); anything that
     * starts with it counts as approval, since a reviewer that approves and then comments
     * has not written a replacement. A blank final reply also keeps the draft.
     */
    fun classify(text: String, complete: Boolean): Verdict {
        val normalized = text.trim().trimStart('*', '`', '_', '"', ' ', '\n').uppercase()
        return when {
            normalized.startsWith(SENTINEL) -> Verdict.APPROVED
            normalized.isEmpty() -> if (complete) Verdict.APPROVED else Verdict.UNDECIDED
            SENTINEL.startsWith(normalized) -> if (complete) Verdict.REVISION else Verdict.UNDECIDED
            else -> Verdict.REVISION
        }
    }
}
