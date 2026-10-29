package ai.daylight.assistant.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LockModelTest {
    private val ok = BiometricManager.BIOMETRIC_SUCCESS
    private val noneEnrolled = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    private val noHardware = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

    @Test fun fingerprintOrFaceWinsWhenItWorks() {
        assertThat(LockModel.method(ok, ok)).isEqualTo(UnlockMethod.BIOMETRIC)
        assertThat(LockModel.method(ok, noneEnrolled)).isEqualTo(UnlockMethod.BIOMETRIC)
    }

    @Test fun removedFingerprintsFallBackToTheScreenLock() {
        // The lock-out from before C11: biometrics gone, but a PIN still opens the app.
        assertThat(LockModel.method(noneEnrolled, ok)).isEqualTo(UnlockMethod.BIOMETRIC_OR_SCREEN_LOCK)
        assertThat(LockModel.method(noHardware, ok)).isEqualTo(UnlockMethod.BIOMETRIC_OR_SCREEN_LOCK)
    }

    @Test fun nothingLeftSendsYouToSecuritySettings() {
        assertThat(LockModel.method(noneEnrolled, noneEnrolled)).isEqualTo(UnlockMethod.NONE)
        assertThat(LockModel.button(UnlockMethod.NONE)).isEqualTo("Open security settings")
        assertThat(LockModel.button(UnlockMethod.BIOMETRIC)).isEqualTo("Unlock")
        assertThat(LockModel.button(UnlockMethod.BIOMETRIC_OR_SCREEN_LOCK)).isEqualTo("Unlock")
    }

    @Test fun everyMethodExplainsItself() {
        UnlockMethod.entries.forEach { assertThat(LockModel.body(it)).isNotEmpty() }
        assertThat(LockModel.body(UnlockMethod.BIOMETRIC_OR_SCREEN_LOCK)).contains("screen lock")
    }

    @Test fun nothingOpensBeforeTheStoredSettingsAreRead() {
        // The cold-start bypass: the defaults say "lock off", so they must not unlock.
        assertThat(LockModel.unlockedWithoutPrompt(loaded = false, lockOn = false)).isFalse()
        assertThat(LockModel.gate(loaded = false, lockOn = false, unlocked = false)).isEqualTo(LockModel.Gate.WAIT)
        assertThat(LockModel.gate(loaded = false, lockOn = true, unlocked = true)).isEqualTo(LockModel.Gate.WAIT)
    }

    @Test fun storedSettingsDecide() {
        assertThat(LockModel.unlockedWithoutPrompt(loaded = true, lockOn = false)).isTrue()
        assertThat(LockModel.unlockedWithoutPrompt(loaded = true, lockOn = true)).isFalse()
        assertThat(LockModel.gate(loaded = true, lockOn = true, unlocked = false)).isEqualTo(LockModel.Gate.LOCKED)
        assertThat(LockModel.gate(loaded = true, lockOn = true, unlocked = true)).isEqualTo(LockModel.Gate.OPEN)
        assertThat(LockModel.gate(loaded = true, lockOn = false, unlocked = false)).isEqualTo(LockModel.Gate.OPEN)
    }

    @Test fun closingThePromptIsQuiet() {
        assertThat(LockModel.quietError(BiometricPrompt.ERROR_USER_CANCELED)).isTrue()
        assertThat(LockModel.quietError(BiometricPrompt.ERROR_NEGATIVE_BUTTON)).isTrue()
        assertThat(LockModel.quietError(BiometricPrompt.ERROR_CANCELED)).isTrue()
        assertThat(LockModel.quietError(BiometricPrompt.ERROR_LOCKOUT)).isFalse()
        assertThat(LockModel.quietError(BiometricPrompt.ERROR_NO_BIOMETRICS)).isFalse()
    }
}
