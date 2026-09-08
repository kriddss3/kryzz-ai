package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalCalculatorTest {
    @Test fun basicArithmetic() {
        assertThat(LocalCalculator.evaluate("2+2")).isEqualTo(4.0)
        assertThat(LocalCalculator.evaluate("3*4+5")).isEqualTo(17.0)
        assertThat(LocalCalculator.evaluate("(1+2)*3")).isEqualTo(9.0)
        assertThat(LocalCalculator.evaluate("2^10")).isEqualTo(1024.0)
    }

    @Test fun unaryAndFunctions() {
        assertThat(LocalCalculator.evaluate("-3+5")).isEqualTo(2.0)
        assertThat(LocalCalculator.evaluate("sqrt(16)")).isEqualTo(4.0)
        assertThat(LocalCalculator.evaluate("abs(-7)")).isEqualTo(7.0)
        assertThat(LocalCalculator.evaluate("min(3, 9)")).isEqualTo(3.0)
        assertThat(LocalCalculator.evaluate("max(3, 9)")).isEqualTo(9.0)
        assertThat(LocalCalculator.evaluate("round(2.6)")).isEqualTo(3.0)
    }

    @Test fun constants() {
        assertThat(LocalCalculator.evaluate("pi")).isWithin(1e-9).of(Math.PI)
        assertThat(LocalCalculator.evaluate("e")).isWithin(1e-9).of(Math.E)
    }

    @Test fun rejectsDivisionByZero() {
        assertThrows(IllegalArgumentException::class.java) { LocalCalculator.evaluate("1/0") }
    }

    @Test fun rejectsUnknownNames() {
        assertThrows(IllegalArgumentException::class.java) { LocalCalculator.evaluate("foo(1)") }
    }

    @Test fun rejectsEmpty() {
        assertThrows(IllegalArgumentException::class.java) { LocalCalculator.evaluate("   ") }
    }
}
