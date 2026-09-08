package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import java.time.ZoneOffset
import org.junit.Test

class LocalClockTest {
    @Test fun snapshotUsesProvidedInstantAndZone() {
        val clock = LocalClock.snapshot(nowMillis = 1_700_000_000_000L, zone = ZoneOffset.UTC)
        assertThat(clock.isoDate).isEqualTo("2023-11-14")
        assertThat(clock.weekday).isEqualTo("Tuesday")
        assertThat(clock.timezone).isEqualTo("Z")
        assertThat(clock.utcOffset).isEqualTo("Z")
        assertThat(clock.unixMs).isEqualTo(1_700_000_000_000L)
        assertThat(clock.isoTime).isNotEmpty()
    }
}
