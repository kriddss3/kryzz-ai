package ai.daylight.assistant.ui.home

import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.domain.AgentPlan
import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.domain.MakeKind
import ai.daylight.assistant.domain.PlanStep
import ai.daylight.assistant.domain.PlanStepStatus
import ai.daylight.assistant.ui.shell.Arm
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HomePortCaptionTest {
    private val zone = ZoneId.of("UTC")
    private fun at(day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private val now = at(26, 10, 24) // Mon 26 Oct 2026, 10:24
    private val base = HomeSnapshot(
        threadCount = 3,
        lastThreadAt = at(26, 9, 12),
        quality = AgentQuality.BALANCED,
        costCapCents = 25,
        now = now,
        zone = zone
    )

    private fun plan(vararg statuses: PlanStepStatus) =
        AgentPlan("run-1", statuses.mapIndexed { i, s -> PlanStep("Step $i", s) })

    @Test fun eightPortsInRailOrder() {
        assertThat(HomeBoard.ports(base).map { it.arm }).containsExactlyElementsIn(Arm.entries).inOrder()
    }

    @Test fun talkCountsThreadsAndShowsTheLastTime() {
        assertThat(HomeBoard.caption(Arm.TALK, base)).isEqualTo("3 threads, last 09:12")
        assertThat(HomeBoard.caption(Arm.TALK, base.copy(threadCount = 1))).isEqualTo("1 thread, last 09:12")
        assertThat(HomeBoard.caption(Arm.TALK, base.copy(threadCount = 0, lastThreadAt = null))).isEqualTo("No threads yet")
        assertThat(HomeBoard.caption(Arm.TALK, base.copy(lastThreadAt = at(25, 22, 0)))).isEqualTo("3 threads, last yesterday")
        assertThat(HomeBoard.caption(Arm.TALK, base.copy(busyArms = setOf(Arm.TALK)))).isEqualTo("Writing a reply")
    }

    @Test fun runShowsTheLivePlanStepOnlyWhileBusy() {
        assertThat(HomeBoard.caption(Arm.RUN, base)).isEqualTo("No plan running")
        val busy = base.copy(busyArms = setOf(Arm.RUN))
        assertThat(HomeBoard.caption(Arm.RUN, busy)).isEqualTo("Working on a task")
        val withPlan = busy.copy(runPlan = plan(PlanStepStatus.DONE, PlanStepStatus.DONE, PlanStepStatus.IN_PROGRESS, PlanStepStatus.PENDING, PlanStepStatus.PENDING))
        assertThat(HomeBoard.caption(Arm.RUN, withPlan)).isEqualTo("Step 3 of 5")
        val allDone = busy.copy(runPlan = plan(PlanStepStatus.DONE, PlanStepStatus.DONE))
        assertThat(HomeBoard.caption(Arm.RUN, allDone)).isEqualTo("Step 2 of 2")
        // A plan left over from a finished run is ignored when Run is not busy.
        assertThat(HomeBoard.caption(Arm.RUN, withPlan.copy(busyArms = emptySet()))).isEqualTo("No plan running")
    }

    @Test fun tuneShowsQualityAndTheEffectiveCap() {
        assertThat(HomeBoard.caption(Arm.TUNE, base)).isEqualTo("Balanced, cap $0.25")
        assertThat(HomeBoard.caption(Arm.TUNE, base.copy(costCapCents = 0))).isEqualTo("Balanced, cap off")
        // Max raises a lower cap to $1.00, the way the agent runs it.
        assertThat(HomeBoard.caption(Arm.TUNE, base.copy(quality = AgentQuality.MAX))).isEqualTo("Max, cap $1.00")
        assertThat(HomeBoard.caption(Arm.TUNE, base.copy(quality = AgentQuality.MAX, costCapCents = 200))).isEqualTo("Max, cap $2.00")
        assertThat(HomeBoard.caption(Arm.TUNE, base.copy(quality = AgentQuality.FAST, costCapCents = 500))).isEqualTo("Fast, cap $5.00")
    }

    @Test fun unbuiltArmsAreUnlitAndLinkCarriesTheLaterTag() {
        val ports = HomeBoard.ports(base).associateBy { it.arm }
        listOf(Arm.CREW, Arm.LINK).forEach { assertThat(ports.getValue(it).state).isEqualTo(PortState.UNLIT) }
        listOf(Arm.TALK, Arm.MAKE, Arm.FIND, Arm.RUN, Arm.VOICE, Arm.TUNE).forEach { assertThat(ports.getValue(it).state).isEqualTo(PortState.IDLE) }
        assertThat(ports.filterValues { it.later }.keys).containsExactly(Arm.LINK)
        assertThat(ports.getValue(Arm.LINK).caption).isEqualTo("Essential, coming later")
        assertThat(ports.getValue(Arm.CREW).caption).endsWith("not yet")
    }

    @Test fun findSaysWhenItIsOffOrWhatItLastSearched() {
        assertThat(HomeBoard.caption(Arm.FIND, base)).isEqualTo("Free images to reuse")
        assertThat(HomeBoard.caption(Arm.FIND, base.copy(lastFindQuery = "lighthouses"))).isEqualTo("Last: lighthouses")
        // Long searches are cut to fit the port.
        assertThat(HomeBoard.caption(Arm.FIND, base.copy(lastFindQuery = "lighthouses at night in winter")))
            .isEqualTo("Last: lighthouses at ni…")
        // Off wins: Find can't search while web search is off.
        assertThat(HomeBoard.caption(Arm.FIND, base.copy(findEnabled = false, lastFindQuery = "lighthouses")))
            .isEqualTo("Off, web search is off")
    }

    @Test fun makeSaysWhatIsRenderingOrWhenTheLastWasMade() {
        assertThat(HomeBoard.caption(Arm.MAKE, base)).isEqualTo("Images and clips")
        assertThat(HomeBoard.caption(Arm.MAKE, base.copy(lastMakeKind = MakeKind.IMAGE, lastMakeAt = at(26, 9, 40))))
            .isEqualTo("Last image 09:40")
        assertThat(HomeBoard.caption(Arm.MAKE, base.copy(lastMakeKind = MakeKind.VIDEO, lastMakeAt = at(25, 20, 0))))
            .isEqualTo("Last clip yesterday")
        val busy = base.copy(busyArms = setOf(Arm.MAKE), makeKind = MakeKind.VIDEO, lastMakeAt = at(26, 9, 40))
        assertThat(HomeBoard.caption(Arm.MAKE, busy)).isEqualTo("Rendering a clip")
        assertThat(HomeBoard.caption(Arm.MAKE, busy.copy(makeKind = MakeKind.IMAGE))).isEqualTo("Rendering an image")
        assertThat(HomeBoard.port(Arm.MAKE, busy).state).isEqualTo(PortState.BUSY)
        assertThat(HomeBoard.status(busy)).isEqualTo("Make is rendering a clip.")
    }

    @Test fun busyArmsLightTheirPortsButUnlitArmsStayUnlit() {
        val ports = HomeBoard.ports(base.copy(busyArms = setOf(Arm.TALK, Arm.RUN, Arm.LINK))).associateBy { it.arm }
        assertThat(ports.getValue(Arm.TALK).state).isEqualTo(PortState.BUSY)
        assertThat(ports.getValue(Arm.RUN).state).isEqualTo(PortState.BUSY)
        assertThat(ports.getValue(Arm.LINK).state).isEqualTo(PortState.UNLIT)
    }

    @Test fun statusSaysWhatIsRunningAndIsNeverAGreeting() {
        assertThat(HomeBoard.status(base)).isEqualTo("Nothing is running. Last thread at 09:12.")
        assertThat(HomeBoard.status(base.copy(threadCount = 0, lastThreadAt = null)))
            .isEqualTo("Nothing is running. Ask anything below to start.")
        assertThat(HomeBoard.status(base.copy(lastThreadAt = at(19, 8, 0)))).isEqualTo("Nothing is running. Last thread on 19 Oct.")
        val both = base.copy(
            busyArms = setOf(Arm.TALK, Arm.RUN),
            runPlan = plan(PlanStepStatus.DONE, PlanStepStatus.IN_PROGRESS, PlanStepStatus.PENDING)
        )
        assertThat(HomeBoard.status(both)).isEqualTo("Run is on step 2 of 3. Talk is writing a reply.")
        assertThat(HomeBoard.status(base.copy(busyArms = setOf(Arm.RUN)))).isEqualTo("Run is working on a task.")
        listOf(base, both).forEach { s ->
            assertThat(HomeBoard.status(s).lowercase()).doesNotContain("hello")
            assertThat(HomeBoard.status(s).lowercase()).doesNotContain("good morning")
        }
    }

    @Test fun timeLabelsReadLikeABoard() {
        assertThat(HomeBoard.timeLabel(at(26, 0, 5), now, zone)).isEqualTo("00:05")
        assertThat(HomeBoard.timeLabel(at(25, 23, 59), now, zone)).isEqualTo("yesterday")
        assertThat(HomeBoard.timeLabel(at(22, 12, 0), now, zone)).isEqualTo("Thu")
        assertThat(HomeBoard.timeLabel(at(19, 12, 0), now, zone)).isEqualTo("19 Oct")
        assertThat(HomeBoard.timePhrase(at(26, 9, 12), now, zone)).isEqualTo("at 09:12")
        assertThat(HomeBoard.timePhrase(at(22, 12, 0), now, zone)).isEqualTo("on Thu")
    }

    @Test fun dateBlockIsEnglishUppercase() {
        assertThat(HomeBoard.dateTitle(now, zone)).isEqualTo("MON 26")
        assertThat(HomeBoard.monthCaption(now, zone)).isEqualTo("October")
    }

    @Test fun makeOfferAppearsOnlyForMediaRequests() {
        assertThat(HomeBoard.makeOffer("a poster photo of a lighthouse")).isEqualTo(AgentCapability.IMAGE)
        assertThat(HomeBoard.makeOffer("make a short video of the ferry")).isEqualTo(AgentCapability.VIDEO)
        assertThat(HomeBoard.makeOffer("an animated logo")).isEqualTo(AgentCapability.VIDEO)
        assertThat(HomeBoard.makeOffer("what should I imagine for dinner")).isNull()
        assertThat(HomeBoard.makeOffer("   ")).isNull()
        assertThat(makeOfferLabel(AgentCapability.IMAGE)).isEqualTo("Make an image instead")
        assertThat(makeOfferLabel(AgentCapability.VIDEO)).isEqualTo("Make a video instead")
    }
}
