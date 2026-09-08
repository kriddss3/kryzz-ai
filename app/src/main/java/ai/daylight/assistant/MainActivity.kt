package ai.daylight.assistant

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.daylight.assistant.ui.AppNavigation
import ai.daylight.assistant.ui.DaylightBackground
import ai.daylight.assistant.ui.KryzzLaunchIntro
import ai.daylight.assistant.ui.KryzzMark

import ai.daylight.assistant.ui.RootViewModel
import ai.daylight.assistant.ui.theme.DaylightTheme

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)

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
            val settings by root.settings.collectAsStateWithLifecycle()
            var showIntro by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(settings.biometricLock) { if (!settings.biometricLock) unlocked = true }
            LaunchedEffect(settings.animationsEnabled) { if (!settings.animationsEnabled) showIntro = false }
            DaylightTheme(
                settings.themeMode,
                settings.appPalette,
                settings.textPalette,
                settings.fontStyle,
                settings.fontScale,
                settings.surfaceOpacity,
                settings.animationsEnabled
            ) {
                if (showIntro) {
                    KryzzLaunchIntro { showIntro = false }
                } else if (settings.biometricLock && !unlocked) {
                    DaylightBackground(style = settings.backgroundStyle, blurRadius = settings.backgroundBlur, colouredGradient = settings.colouredGradient) { BiometricLockScreen(::authenticate) }
                } else {
                        AppNavigation(container, settings.onboardingComplete, settings.backgroundStyle, settings.backgroundBlur, settings.colouredGradient)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) unlocked = false
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

    private fun authenticate() {
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) return
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                }
            }
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Kryzz AI")
                .setSubtitle("Your local conversations stay private")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("Cancel")
                .build()
        )
    }
}

@Composable
private fun BiometricLockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.padding(bottom = 8.dp).size(76.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            ) { KryzzMark(Modifier.padding(15.dp), contentDescription = null) }
            Text("Kryzz AI is locked", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Authenticate to view local conversations and settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onUnlock,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Unlock") }
        }
    }
}
