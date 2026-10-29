package ai.daylight.assistant.ui.shell

import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.ui.Routes
import ai.daylight.assistant.ui.dot.effects.DotFont5x7
import ai.daylight.assistant.voice.VoicePhase
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InkLineTest {
    // --- sanitiser -------------------------------------------------------------------

    @Test fun sanitiserUppercasesAndKeepsSupportedCharacters() {
        assertThat(InkLine.sanitize("talk ready")).isEqualTo("TALK READY")
        assertThat(InkLine.sanitize("Run step 3/5: 60%")).isEqualTo("RUN STEP 3/5: 60%")
    }

    @Test fun sanitiserStripsAccentsAndDropsUnsupportedCharacters() {
        assertThat(InkLine.sanitize("Café “naïve” résumé")).isEqualTo("CAFE NAIVE RESUME")
        assertThat(InkLine.sanitize("it's ok…")).isEqualTo("ITS OK")
        assertThat(InkLine.sanitize("你好")).isEmpty()
    }

    @Test fun sanitiserTurnsSeparatorsIntoSingleSpaces() {
        assertThat(InkLine.sanitize("  a,b;c_d|e  \n f ")).isEqualTo("A B C D E F")
    }

    @Test fun sanitiserOutputIsAlwaysDrawable() {
        val nasty = "Ünïcödé — emoji 🦑 tabs\tand\u0000 controls ∑ «quotes» @home & co."
        val clean = InkLine.sanitize(nasty)
        assertThat(clean.all { DotFont5x7.supports(it) }).isTrue()
        assertThat(clean).doesNotContain("  ")
        assertThat(clean).isEqualTo(clean.trim())
    }

    @Test fun sanitiserCapsLength() {
        assertThat(InkLine.sanitize("x".repeat(100), maxChars = 10)).hasLength(10)
        assertThat(InkLine.sanitize("abc def", maxChars = 4)).isEqualTo("ABC")
        assertThat(InkLine.sanitize("abc", maxChars = -3)).isEmpty()
        assertThat(InkLine.sanitize("")).isEmpty()
    }

    // --- messages --------------------------------------------------------------------

    @Test fun talkLines() {
        assertThat(InkLines.talk(generating = false)).isEqualTo(InkLine("TALK READY", live = false))
        assertThat(InkLines.talk(generating = true)).isEqualTo(InkLine("TALK THINKING", live = true))
        assertThat(InkLines.talk(true, "Searching: pixel 10 battery test").text).isEqualTo("TALK SEARCHING")
        assertThat(InkLines.talk(true, "Reading the page").text).isEqualTo("TALK READING")
        assertThat(InkLines.talk(true, "Searching the web…").text).isEqualTo("TALK SEARCHING")
        // Odd labels fall back instead of printing junk on the strip.
        assertThat(InkLines.talk(true, "🦑 x").text).isEqualTo("TALK THINKING")
        assertThat(InkLines.talk(true, "").text).isEqualTo("TALK THINKING")
    }

    @Test fun talkReadingCountsLikeTheMockup() {
        assertThat(InkLines.talk(true, "Reading: fontsource.org/x", step = 3, of = 5))
            .isEqualTo(InkLine("TALK READING 3 OF 5", live = true))
        // Without a count, or for other verbs, the plain verb is shown.
        assertThat(InkLines.talk(true, "Reading: fontsource.org/x").text).isEqualTo("TALK READING")
        assertThat(InkLines.talk(true, "Searching: fonts", step = 1, of = 5).text).isEqualTo("TALK SEARCHING")
        // The step never runs past the total.
        assertThat(InkLines.talk(true, "Reading: x.com", step = 9, of = 5).text).isEqualTo("TALK READING 5 OF 5")
        assertThat(InkLines.talk(false, "Reading: x.com", step = 3, of = 5).text).isEqualTo("TALK READY")
    }

    @Test fun runLines() {
        assertThat(InkLines.run(generating = false)).isEqualTo(InkLine("RUN READY"))
        assertThat(InkLines.run(generating = true)).isEqualTo(InkLine("RUN THINKING", live = true))
        assertThat(InkLines.run(true, doneSteps = 2, totalSteps = 5)).isEqualTo(InkLine("RUN STEP 3 OF 5", live = true))
        assertThat(InkLines.run(true, doneSteps = 0, totalSteps = 4).text).isEqualTo("RUN STEP 1 OF 4")
        // The step never runs past the plan, even when every step is already done.
        assertThat(InkLines.run(true, doneSteps = 5, totalSteps = 5).text).isEqualTo("RUN STEP 5 OF 5")
        assertThat(InkLines.run(false, doneSteps = 5, totalSteps = 5)).isEqualTo(InkLine("RUN DONE"))
        assertThat(InkLines.run(true, latestActivity = "Writing DOCX").text).isEqualTo("RUN WRITING")
    }

    @Test fun voiceLines() {
        assertThat(InkLines.voice(VoicePhase.IDLE)).isEqualTo(InkLine("VOICE READY"))
        assertThat(InkLines.voice(VoicePhase.LISTENING)).isEqualTo(InkLine("VOICE LISTENING", live = true))
        assertThat(InkLines.voice(VoicePhase.PROCESSING).text).isEqualTo("VOICE THINKING")
        assertThat(InkLines.voice(VoicePhase.PROCESSING, searching = true).text).isEqualTo("VOICE SEARCHING")
        assertThat(InkLines.voice(VoicePhase.SPEAKING).text).isEqualTo("VOICE SPEAKING")
    }

    @Test fun mutedVoiceSaysSoWhileListening() {
        assertThat(InkLines.voice(VoicePhase.LISTENING, muted = true)).isEqualTo(InkLine("VOICE MUTED", live = true))
        // Kryzz still talks while the mic is muted.
        assertThat(InkLines.voice(VoicePhase.SPEAKING, muted = true).text).isEqualTo("VOICE SPEAKING")
        assertThat(InkLines.voice(VoicePhase.IDLE, muted = true).text).isEqualTo("VOICE READY")
    }

    @Test fun tuneAndNotYetLines() {
        assertThat(InkLines.tune(AgentQuality.BALANCED)).isEqualTo(InkLine("TUNE BALANCED"))
        assertThat(InkLines.tune(AgentQuality.MAX).text).isEqualTo("TUNE MAX")
        assertThat(InkLines.notYet(Arm.MAKE)).isEqualTo(InkLine("MAKE NOT YET"))
        assertThat(InkLines.notYet(Arm.LINK).live).isFalse()
    }

    @Test fun everyMessageIsAlreadyClean() {
        val lines = buildList {
            add(InkLines.talk(false)); add(InkLines.talk(true, "Searching: x"))
            add(InkLines.run(true, 1, 3)); add(InkLines.run(false))
            VoicePhase.entries.forEach { add(InkLines.voice(it)) }
            VoicePhase.entries.forEach { add(InkLines.voice(it, muted = true)) }
            AgentQuality.entries.forEach { add(InkLines.tune(it)) }
            Arm.entries.forEach { add(InkLines.notYet(it)) }
        }
        lines.forEach { line -> assertThat(InkLine.sanitize(line.text)).isEqualTo(line.text) }
    }

    @Test fun depthPagesKeepTheirArmOnTheStrip() {
        assertThat(defaultInkLine(Routes.chat("a")).text).isEqualTo("TALK READY")
        assertThat(defaultInkLine(Routes.BOOT).text).isEqualTo("TALK READY")
        assertThat(defaultInkLine(Routes.API).text).isEqualTo("TUNE KEYS")
        assertThat(defaultInkLine(Routes.models()).text).isEqualTo("TUNE MODELS")
        assertThat(defaultInkLine(Routes.CRON).text).isEqualTo("TUNE ROUTINES")
        assertThat(defaultInkLine(Routes.MAKE).text).isEqualTo("MAKE READY")
        assertThat(defaultInkLine(null).text).isEmpty()
        listOf(
            Routes.SETTINGS, Routes.API, Routes.PERSONA, Routes.TOOLS, Routes.PRIVACY, Routes.APPEARANCE,
            Routes.VOICE, Routes.MEMORY, Routes.CRON, Routes.SKILLS, Routes.LLMS
        ).forEach { assertThat(defaultInkLine(it).text).startsWith("TUNE") }
    }

    @Test fun stripCapacityMatchesTheMockup() {
        // 372dp at a 2.2dp pitch is 169 columns: 27 characters after the live mark.
        assertThat(inkStripCapacity(169)).isEqualTo(27)
        assertThat(inkStripCapacity(0)).isEqualTo(0)
        assertThat(inkStripCapacity(3)).isEqualTo(0)
        // n characters need n * 6 - 1 columns after column 4, leaving the last column dark.
        val n = inkStripCapacity(169)
        assertThat(InkStripSpec.TEXT_START_COL + DotFont5x7.columns("X".repeat(n))).isAtMost(169 - 1)
    }

    // --- shared state ----------------------------------------------------------------

    @Test fun newestScreenOwnsTheStripAndReleasingRestoresTheOlderOne() {
        val state = InkStripState()
        val chat = Any()
        val settings = Any()
        assertThat(state.line).isNull()
        state.update(chat, InkLine("TALK READY"), Arm.TALK)
        state.update(settings, InkLine("TUNE BALANCED"), Arm.TUNE)
        assertThat(state.line?.text).isEqualTo("TUNE BALANCED")
        assertThat(state.reportedArm).isEqualTo(Arm.TUNE)
        // An older screen updating during a transition must not steal the strip.
        state.update(chat, InkLine("TALK THINKING", live = true), Arm.TALK)
        assertThat(state.line?.text).isEqualTo("TUNE BALANCED")
        state.release(settings)
        assertThat(state.line).isEqualTo(InkLine("TALK THINKING", live = true))
        state.release(chat)
        assertThat(state.line).isNull()
        assertThat(state.reportedArm).isNull()
    }

    @Test fun busyArmsAreTrackedPerKey() {
        val state = InkStripState()
        state.setBusy("a", Arm.RUN)
        state.setBusy("b", Arm.TALK)
        assertThat(state.busyArms).containsExactly(Arm.RUN, Arm.TALK)
        assertThat(state.isBusy("a")).isTrue()
        assertThat(state.isBusy(null)).isFalse()
        state.setBusy("a", null)
        assertThat(state.busyArms).containsExactly(Arm.TALK)
        assertThat(state.isBusy("a")).isFalse()
    }

    @Test fun flashOverridesUntilItsOwnSerialClearsIt() {
        val state = InkStripState()
        state.flash(InkLines.notYet(Arm.MAKE))
        val first = state.flash!!.first
        state.flash(InkLines.notYet(Arm.FIND))
        // A stale timer from the first flash must not clear the second one.
        state.clearFlash(first)
        assertThat(state.flash?.second?.text).isEqualTo("FIND NOT YET")
        state.clearFlash(state.flash!!.first)
        assertThat(state.flash).isNull()
    }
}
