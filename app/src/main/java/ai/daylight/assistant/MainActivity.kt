package ai.daylight.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.daylight.assistant.data.preferences.SettingsState
import ai.daylight.assistant.ui.AppNavigation
import ai.daylight.assistant.ui.DaylightBackground
import ai.daylight.assistant.ui.KryzzLaunchIntro
import ai.daylight.assistant.ui.RootViewModel
import ai.daylight.assistant.ui.lock.LockModel
import ai.daylight.assistant.ui.lock.LockScreen
import ai.daylight.assistant.ui.lock.UnlockMethod
import ai.daylight.assistant.ui.theme.DaylightTheme

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var unlockMethod by mutableStateOf(UnlockMethod.BIOMETRIC)
    private var lockMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Request the highest available display refresh rate (120Hz / 90Hz)
        // so animations and the voice bubble stay smooth on high-refresh panels.
        // Android defaults to 60Hz unless the app opts in.
        enableHighRefreshRate()
        val container = (application as DaylightApplication).container
        setContent {
            val root: RootViewModel = viewModel(factory = ai.daylight.assistant.ui.DaylightViewModelFactory(container))
            val stored by root.storedSettings.collectAsStateWithLifecycle()
            val settings = stored ?: SettingsState()
            val loaded = stored != null
            var showIntro by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(loaded, settings.biometricLock) {
                if (LockModel.unlockedWithoutPrompt(loaded, settings.biometricLock)) unlocked = true
            }
            LaunchedEffect(settings.animationsEnabled) { if (!settings.animationsEnabled) showIntro = false }
            DaylightTheme(
                mode = settings.themeMode,
                fontScale = settings.fontScale,
                animationsEnabled = settings.animationsEnabled
            ) {
                val gate = LockModel.gate(loaded, settings.biometricLock, unlocked)
                if (showIntro) {
                    KryzzLaunchIntro { showIntro = false }
                } else if (gate == LockModel.Gate.WAIT) {
                    // A blank frame while the stored settings load (a few milliseconds).
                    DaylightBackground {}
                } else if (gate == LockModel.Gate.LOCKED) {
                    DaylightBackground {
                        LockScreen(unlockMethod, lockMessage, ::authenticate, ::openSecuritySettings)
                    }
                } else {
                    AppNavigation(container, settings.onboardingComplete, settings.dotBackground)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUnlockMethod()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            unlocked = false
            lockMessage = null
        }
    }

    /**
     * Switches the window to the highest supported refresh rate (120Hz, 90Hz, etc.).
     * Android keeps the default (60Hz) unless an app explicitly raises it via
     * [android.view.Window.setFrameRate] or the legacy
     * [android.view.Surface.setFrameRate] / layout-params path. Here we iterate the
     * display's supported modes and pick the one with the highest refresh rate at
     * the current resolution, then apply it through the window's attributes.
     */
    @android.annotation.SuppressLint("ObsoleteSdkInt")
    private fun enableHighRefreshRate() {
        runCatching {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val mode = display.supportedModes.maxByOrNull { it.refreshRate }
            if (mode != null && mode.refreshRate > 60f) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = mode.modeId
                }
            }
        }
    }

    /** Re-reads what can unlock the phone (fingerprints or the screen lock may have changed). */
    private fun refreshUnlockMethod() {
        val manager = BiometricManager.from(this)
        unlockMethod = LockModel.method(
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK),
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        )
    }

    private fun authenticate() {
        refreshUnlockMethod()
        val method = unlockMethod
        if (method == UnlockMethod.NONE) return
        lockMessage = null
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Kryzz AI")
            .setSubtitle("Your local conversations stay private")
        if (method == UnlockMethod.BIOMETRIC) {
            info.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("Cancel")
        } else {
            // A prompt that allows the screen lock must not have a negative button.
            info.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        }
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lockMessage = null
                    unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!LockModel.quietError(errorCode)) lockMessage = errString.toString()
                }
            }
        ).authenticate(info.build())
    }

    private fun openSecuritySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
}
