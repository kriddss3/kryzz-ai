package ai.daylight.assistant.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

/** How the lock screen can open the app on this phone right now. */
enum class UnlockMethod {
    /** Fingerprint or face, with Cancel (the lock as it always was). */
    BIOMETRIC,

    /** No fingerprint or face works, but the screen lock (PIN, pattern, password) does. */
    BIOMETRIC_OR_SCREEN_LOCK,

    /** Nothing can unlock: the phone has no biometrics and no screen lock any more. */
    NONE,
}

/**
 * The lock screen's rules. Before C11 a phone whose fingerprints were removed after the lock
 * was turned on could never open Kryzz again: Unlock silently did nothing. Now the screen
 * lock is offered when no biometric works, and with neither the screen says so and opens
 * Android's security settings.
 */
object LockModel {
    fun method(
        biometricStatus: Int,
        withScreenLockStatus: Int,
        success: Int = BiometricManager.BIOMETRIC_SUCCESS,
    ): UnlockMethod = when {
        biometricStatus == success -> UnlockMethod.BIOMETRIC
        withScreenLockStatus == success -> UnlockMethod.BIOMETRIC_OR_SCREEN_LOCK
        else -> UnlockMethod.NONE
    }

    /** What the activity shows: nothing yet, the lock, or the app. */
    enum class Gate { WAIT, LOCKED, OPEN }

    /**
     * Until the stored settings are read ([loaded] false) nothing is shown: the default
     * settings say the lock is off, and deciding on them skipped the lock on every cold start.
     */
    fun gate(loaded: Boolean, lockOn: Boolean, unlocked: Boolean): Gate = when {
        !loaded -> Gate.WAIT
        lockOn && !unlocked -> Gate.LOCKED
        else -> Gate.OPEN
    }

    /** The app counts as unlocked without a prompt only once the stored settings say the lock is off. */
    fun unlockedWithoutPrompt(loaded: Boolean, lockOn: Boolean): Boolean = loaded && !lockOn

    fun body(method: UnlockMethod): String = when (method) {
        UnlockMethod.BIOMETRIC -> "Use your fingerprint or face to open your chats and settings."
        UnlockMethod.BIOMETRIC_OR_SCREEN_LOCK -> "No fingerprint or face is set up, so your screen lock opens Kryzz."
        UnlockMethod.NONE -> "This phone has no fingerprint, face or screen lock now. Set one up in Android settings to open Kryzz."
    }

    fun button(method: UnlockMethod): String =
        if (method == UnlockMethod.NONE) "Open security settings" else "Unlock"

    /** Prompt errors that mean "the person closed it"; they need no message. */
    fun quietError(code: Int): Boolean = code == BiometricPrompt.ERROR_USER_CANCELED ||
        code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
        code == BiometricPrompt.ERROR_CANCELED
}
