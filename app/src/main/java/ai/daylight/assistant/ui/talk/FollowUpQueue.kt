package ai.daylight.assistant.ui.talk

/** What the screen does with a queued follow-up once a reply stops streaming. */
sealed interface FollowUpAction {
    data object None : FollowUpAction

    /** The reply finished normally: send the queued text as the next message. */
    data class Send(val text: String) : FollowUpAction

    /** The reply was stopped or failed, or an edit is in progress: put the text back in the reply line. */
    data class Restore(val text: String) : FollowUpAction
}

/**
 * "Queue a follow-up": one message slot the user can fill while a reply streams. It lives
 * in the UI only (ChatViewModel is unchanged) and is sent automatically when the reply
 * ends. Queueing twice adds to the same message instead of replacing it.
 */
data class FollowUpQueue(val text: String? = null) {
    val isEmpty: Boolean get() = text.isNullOrBlank()

    fun add(more: String): FollowUpQueue {
        val clean = more.trim()
        if (clean.isEmpty()) return this
        return FollowUpQueue(if (isEmpty) clean else "$text\n$clean")
    }

    fun clear(): FollowUpQueue = FollowUpQueue()

    /**
     * Called once when generation flips from running to idle. A queued message is sent
     * only after a normal finish; if the user pressed stop, the reply failed, or an older
     * message is being edited (sending would cut the conversation there), the text goes
     * back into the reply line so nothing is lost and nothing is sent by surprise.
     */
    fun onGenerationEnded(stoppedByUser: Boolean, failed: Boolean, editing: Boolean): Pair<FollowUpQueue, FollowUpAction> {
        val queued = text?.trim().orEmpty()
        if (queued.isEmpty()) return clear() to FollowUpAction.None
        return clear() to if (stoppedByUser || failed || editing) FollowUpAction.Restore(queued) else FollowUpAction.Send(queued)
    }

    companion object {
        /** Puts restored text in front of whatever is already typed, one per line. */
        fun mergeIntoDraft(restored: String, draft: String): String =
            listOf(restored.trim(), draft.trim()).filter { it.isNotEmpty() }.joinToString("\n")
    }
}
