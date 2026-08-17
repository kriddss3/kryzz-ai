package ai.daylight.assistant.ui

/**
 * Chat is the Obsidian Constellation home. The drawer owns conversation navigation;
 * Settings is the only other top-level screen.
 */
enum class MainTab {
    CHAT,
    SETTINGS;

    companion object {
        /** Source-compatible aliases for older callers while secondary destinations migrate. */
        @Deprecated("Use CHAT", ReplaceWith("CHAT"))
        val CHATS: MainTab = CHAT
        @Deprecated("Models are no longer a top-level tab", ReplaceWith("CHAT"))
        val LLMS: MainTab = CHAT
        @Deprecated("Skills are no longer a top-level tab", ReplaceWith("CHAT"))
        val SKILLS: MainTab = CHAT
    }
}
