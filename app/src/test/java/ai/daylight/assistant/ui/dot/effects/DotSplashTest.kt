package ai.daylight.assistant.ui.dot.effects

import ai.daylight.assistant.ui.dot.DotColors
import ai.daylight.assistant.ui.dot.DotColorsLight
import ai.daylight.assistant.ui.dot.DotSecondaryColor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DotSplashTest {
    @Test fun sampleFormsASingleRippleThatReturnsToZeroBehindAndAhead() {
        val pitch = 16f
        val maxRadius = 200f
        val progress = 0.5f
        val frontR = DotSplashMath.frontRadius(progress, maxRadius, pitch)

        val farBehind = DotSplashMath.sample(
            dotX = frontR - pitch * 12f,
            dotY = 0f,
            originX = 0f,
            originY = 0f,
            progress = progress,
            maxRadius = maxRadius,
            pitchPx = pitch,
        )
        assertThat(farBehind.tintWeight).isWithin(0.0001f).of(0f)
        assertThat(farBehind.radiusScale).isWithin(0.0001f).of(1f)
        assertThat(farBehind.alphaBoost).isWithin(0.0001f).of(0f)

        val farAhead = DotSplashMath.sample(
            dotX = frontR + pitch * 12f,
            dotY = 0f,
            originX = 0f,
            originY = 0f,
            progress = progress,
            maxRadius = maxRadius,
            pitchPx = pitch,
        )
        assertThat(farAhead.tintWeight).isWithin(0.0001f).of(0f)

        val atCrest = DotSplashMath.sample(
            dotX = frontR,
            dotY = 0f,
            originX = 0f,
            originY = 0f,
            progress = progress,
            maxRadius = maxRadius,
            pitchPx = pitch,
        )
        assertThat(atCrest.tintWeight).isGreaterThan(0.5f)
        assertThat(atCrest.radiusScale).isGreaterThan(1f)
        assertThat(atCrest.alphaBoost).isGreaterThan(0f)
    }

    @Test fun sampleSwellsAndBoostsAlphaAtWaveCrest() {
        val pitch = 16f
        val maxRadius = 200f
        val progress = 0.4f
        val frontR = DotSplashMath.frontRadius(progress, maxRadius, pitch)

        val atCrest = DotSplashMath.sample(
            dotX = frontR,
            dotY = 0f,
            originX = 0f,
            originY = 0f,
            progress = progress,
            maxRadius = maxRadius,
            pitchPx = pitch,
        )
        assertThat(atCrest.radiusScale).isGreaterThan(1f)
        assertThat(atCrest.alphaBoost).isGreaterThan(0f)

        val farAhead = DotSplashMath.sample(
            dotX = frontR + pitch * 20f,
            dotY = 0f,
            originX = 0f,
            originY = 0f,
            progress = progress,
            maxRadius = maxRadius,
            pitchPx = pitch,
        )
        assertThat(farAhead.radiusScale).isWithin(0.001f).of(1f)
        assertThat(farAhead.alphaBoost).isWithin(0.001f).of(0f)
    }

    @Test fun sampleDisplacesDotsRadiallyOutwardFromOrigin() {
        val pitch = 16f
        val maxRadius = 200f
        val progress = 0.4f
        val frontR = DotSplashMath.frontRadius(progress, maxRadius, pitch)

        val east = DotSplashMath.sample(
            dotX = 100f + frontR,
            dotY = 100f,
            originX = 100f,
            originY = 100f,
            progress = progress,
            maxRadius = maxRadius,
            pitchPx = pitch,
        )
        assertThat(east.offsetX).isGreaterThan(0f)
        assertThat(east.offsetY).isWithin(0.0001f).of(0f)

        val northWest = DotSplashMath.sample(
            dotX = 100f - frontR * 0.7071f,
            dotY = 100f - frontR * 0.7071f,
            originX = 100f,
            originY = 100f,
            progress = progress,
            maxRadius = maxRadius,
            pitchPx = pitch,
        )
        assertThat(northWest.offsetX).isLessThan(0f)
        assertThat(northWest.offsetY).isLessThan(0f)

        val center = DotSplashMath.sample(
            dotX = 100f,
            dotY = 100f,
            originX = 100f,
            originY = 100f,
            progress = progress,
            maxRadius = maxRadius,
            pitchPx = pitch,
        )
        assertThat(center.offsetX).isWithin(0.0001f).of(0f)
        assertThat(center.offsetY).isWithin(0.0001f).of(0f)
    }

    @Test fun secondaryColorFromResolvesKnownAndDefaultsToAmber() {
        assertThat(DotSecondaryColor.from("amber")).isEqualTo(DotSecondaryColor.AMBER)
        assertThat(DotSecondaryColor.from("coral")).isEqualTo(DotSecondaryColor.CORAL)
        assertThat(DotSecondaryColor.from("emerald")).isEqualTo(DotSecondaryColor.EMERALD)
        assertThat(DotSecondaryColor.from("mono")).isEqualTo(DotSecondaryColor.MONO)
        assertThat(DotSecondaryColor.from(null)).isEqualTo(DotSecondaryColor.AMBER)
        assertThat(DotSecondaryColor.from("unknown")).isEqualTo(DotSecondaryColor.AMBER)
        assertThat(DotSecondaryColor.indexOf("unknown")).isEqualTo(0)
        assertThat(DotSecondaryColor.AMBER.colorFor(isDark = true)).isEqualTo(DotColors.Accent)
        assertThat(DotSecondaryColor.AMBER.colorFor(isDark = false)).isEqualTo(DotColorsLight.AccentText)
    }
}
