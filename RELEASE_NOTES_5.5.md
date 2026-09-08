# Kryzz AI 5.5.0

## Changes

### Side panel navigation
- Conversation and Settings selections close the side panel immediately, including after a swipe-open gesture.
- Stale opening animation targets can no longer resurrect the drawer after the caller has closed it.
- New-chat, Voice, Cron, and other panel navigation actions close consistently before routing.

### Provider-aware model workspace
- OpenRouter and MiniMax are now explicit active chat-provider choices in API setup.
- Chat, Agent, Research, Image, Video, Audio, and model-catalog screens refresh when the provider changes.
- Returned provider catalogs replace old lists instead of mixing models from the previous provider.
- Persisted model IDs are reconciled against the active provider; unavailable models cannot be selected.
- OpenRouter benchmarks remain available only for OpenRouter catalogs.
- Voice chat keeps its existing OpenRouter-only routing.

### Agent workspace
- Agent mode now has a code-drawn KryzzBot mascot with idle, thinking, tooling, success, and error moods.
- The mascot appears in the Agent header, empty workspace, workflow picker, and live activity status card.
- Agent motion respects the app animation setting and requires no additional image assets.

## Build

- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
- Version code: `63`
