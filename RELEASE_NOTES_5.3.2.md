# Kryzz AI 5.3.2

Based on the 5.3.1 source tree.

## Changes

### Keyboard-aware side panel swipe
- Swiping right from the left edge to open the side panel now hides the keyboard before the panel slides in, so the IME never overlaps the panel.
- Swiping left to exit back to the library also hides the keyboard.
- (Already present in 5.3.1: the `KryzzSidePanelHost` dismisses the keyboard and clears focus on any panel open.)

### Agent animation instead of static text
- Image, video, and audio generation in Agent mode now show the animated `ThinkingIndicator` (pulsing dot + "Thinking") instead of static placeholder strings like "Creating your image…" or "Generating video…".
- The message content is kept empty while the generation is in progress, which triggers the indicator automatically.

### API setup dropdown
- The four stacked API key editors (OpenRouter, MiniMax, Parallel Search, Fish Audio) are replaced by a single provider dropdown.
- Selecting a provider from the dropdown shows only that provider's key editor underneath.
- The `ApiProviderEntry` data class makes it trivial to add more providers later — just append to the `apiProviders` list.
- Settings home subtitle updated from "OpenRouter, MiniMax, Parallel, and Fish Audio credentials" → "Provider API keys".
