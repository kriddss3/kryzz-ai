package ai.daylight.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ai.daylight.assistant.ui.theme.KryzzRadius
import ai.daylight.assistant.ui.theme.KryzzSpacing

@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onDone: () -> Unit) {
    val openKey by vm.openRouterKey.collectAsStateWithLifecycle()
    val parallelKey by vm.parallelKey.collectAsStateWithLifecycle()
    val fishKey by vm.fishKey.collectAsStateWithLifecycle()
    val memoryEnabled by vm.memoryEnabled.collectAsStateWithLifecycle()
    val locationEnabled by vm.locationEnabled.collectAsStateWithLifecycle()
    val openCheck by vm.openRouterCheck.collectAsStateWithLifecycle()
    val parallelCheck by vm.parallelCheck.collectAsStateWithLifecycle()
    val fishCheck by vm.fishCheck.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onResult = vm::onLocationPermissionResult
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        OnboardingSignalBackdrop()
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = KryzzSpacing.ScreenHorizontal,
                    vertical = KryzzSpacing.Large
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KryzzSpacing.Medium)
        ) {
            Surface(
                color = scheme.primary.copy(alpha = 0.10f),
                contentColor = scheme.primary,
                shape = RoundedCornerShape(KryzzRadius.Control),
                border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.24f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("PRIVATE BY DESIGN", style = MaterialTheme.typography.labelSmall)
                }
            }

            KryzzMark(Modifier.size(94.dp), contentDescription = null)
            Text("KRYZZ AI", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Text(
                text = "Your private AI workspace",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Chat, research, build, and create with models you choose. Kryzz keeps your workspace local and never adds analytics or telemetry.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 600.dp)
            )

            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Connect your providers", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Add only the credentials you want Kryzz to use. You can test each connection before continuing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }

            ProviderNotice()
            CredentialCard(
                title = "OpenRouter",
                subtitle = "Required for chat and model access",
                value = openKey,
                onValue = { vm.openRouterKey.value = it },
                status = openCheck,
                testTag = "openrouter_key",
                requirement = "REQUIRED",
                icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                onTest = vm::testOpenRouter
            )
            CredentialCard(
                title = "Parallel Search",
                subtitle = "Optional; enables current web research and citations",
                value = parallelKey,
                onValue = { vm.parallelKey.value = it },
                status = parallelCheck,
                testTag = "parallel_key",
                requirement = "OPTIONAL",
                icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                onTest = vm::testParallel
            )
            CredentialCard(
                title = "Fish Audio",
                subtitle = "Optional; enables spoken voice replies during voice chat",
                value = fishKey,
                onValue = { vm.fishKey.value = it },
                status = fishCheck,
                testTag = "fish_key",
                requirement = "OPTIONAL",
                icon = { Icon(Icons.Outlined.Psychology, contentDescription = null) },
                onTest = vm::testFish
            )

            MemoryChoice(
                enabled = memoryEnabled,
                onEnabled = { vm.memoryEnabled.value = it }
            )
            LocationChoice(
                enabled = locationEnabled,
                onEnabled = { wanted ->
                    if (wanted) {
                        locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    } else {
                        vm.locationEnabled.value = false
                    }
                }
            )
            EncryptionNotice()
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = { vm.finish(onDone) },
                enabled = openKey.isNotBlank() || openCheck is CheckState.Success,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(KryzzRadius.Medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                )
            ) {
                Text("Continue", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "You can change or test keys later in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MemoryChoice(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(KryzzRadius.Large),
        colors = CardDefaults.cardColors(containerColor = scheme.surface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.88f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KryzzSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(KryzzSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(KryzzRadius.Control))
                    .background(scheme.surfaceVariant.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Cross-chat memory", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Off by default. If enabled, Kryzz may save useful preferences locally so future chats can be more relevant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabled
            )
        }
    }
}

@Composable
private fun LocationChoice(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(KryzzRadius.Large),
        colors = CardDefaults.cardColors(containerColor = scheme.surface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.88f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KryzzSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(KryzzSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(KryzzRadius.Control))
                    .background(scheme.surfaceVariant.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Approximate location", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Off by default. If enabled, Kryzz uses your coarse location so answers about places, weather, or news match your region. You can turn it off anytime in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabled
            )
        }
    }
}

@Composable
private fun OnboardingSignalBackdrop() {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.45f
    Canvas(Modifier.fillMaxSize()) {
        val radius = size.minDimension * 0.66f
        val center = Offset(size.width * 0.84f, size.height * 0.08f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    scheme.primary.copy(alpha = if (dark) 0.09f else 0.05f),
                    scheme.secondary.copy(alpha = if (dark) 0.025f else 0.012f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )
        drawLine(
            color = scheme.primary.copy(alpha = if (dark) 0.08f else 0.04f),
            start = Offset(size.width * 0.05f, size.height * 0.18f),
            end = Offset(size.width * 0.95f, size.height * 0.12f),
            strokeWidth = 1.dp.toPx()
        )
        repeat(4) { index ->
            drawCircle(
                color = scheme.secondary.copy(alpha = if (dark) 0.14f else 0.08f),
                radius = 1.5.dp.toPx(),
                center = Offset(
                    size.width * (0.18f + index * 0.21f),
                    size.height * (0.16f - index * 0.014f)
                )
            )
        }
    }
}

@Composable
private fun CredentialCard(
    title: String,
    subtitle: String,
    value: String,
    onValue: (String) -> Unit,
    status: CheckState,
    testTag: String,
    requirement: String,
    icon: @Composable () -> Unit,
    onTest: () -> Unit
) {
    var revealKey by rememberSaveable(title) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(KryzzRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surface.copy(alpha = 0.94f),
            contentColor = scheme.onSurface
        ),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.88f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier.padding(KryzzSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(KryzzSpacing.Small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KryzzSpacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(KryzzRadius.Control))
                        .background(scheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides scheme.primary) { icon() }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = if (requirement == "REQUIRED") {
                        scheme.primary.copy(alpha = 0.12f)
                    } else {
                        scheme.surfaceVariant.copy(alpha = 0.76f)
                    },
                    contentColor = if (requirement == "REQUIRED") {
                        scheme.primary
                    } else {
                        scheme.onSurfaceVariant
                    },
                    shape = RoundedCornerShape(KryzzRadius.Small)
                ) {
                    Text(
                        requirement,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                label = { Text("API key") },
                visualTransformation = if (revealKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { revealKey = !revealKey },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (revealKey) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (revealKey) "Hide API key" else "Show API key",
                            modifier = Modifier.size(21.dp)
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(KryzzRadius.Control),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = scheme.primary,
                    unfocusedBorderColor = scheme.outlineVariant,
                    focusedContainerColor = scheme.surfaceVariant.copy(alpha = 0.34f),
                    unfocusedContainerColor = scheme.surfaceVariant.copy(alpha = 0.22f),
                    cursorColor = scheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            )

            StatusText(status, Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = onTest,
                enabled = status !is CheckState.Checking,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(KryzzRadius.Control),
                border = BorderStroke(1.dp, scheme.outlineVariant)
            ) {
                if (status is CheckState.Checking) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Test key", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun ProviderNotice() {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceVariant.copy(alpha = 0.72f),
            contentColor = scheme.onSurface
        ),
        shape = RoundedCornerShape(KryzzRadius.Card),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.76f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(KryzzSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(KryzzSpacing.Small),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(KryzzRadius.Control))
                    .background(scheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Security,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Provider privacy", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Privacy notice: chat requests go to OpenRouter and the selected model provider. Web-search requests go separately to Parallel. Review each provider's terms before continuing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EncryptionNotice() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(KryzzSpacing.Small),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(KryzzRadius.Control))
                .background(scheme.tertiary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                tint = scheme.tertiary,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            "Keys are encrypted with Android Keystore, excluded from backup, and never sent to the other provider. No analytics or telemetry.",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusText(state: CheckState, modifier: Modifier = Modifier) {
    val (text, color) = when (state) {
        CheckState.Idle -> "Not tested" to MaterialTheme.colorScheme.onSurfaceVariant
        CheckState.Checking -> "Checking…" to MaterialTheme.colorScheme.primary
        is CheckState.Success -> state.message to MaterialTheme.colorScheme.tertiary
        is CheckState.Error -> state.message to MaterialTheme.colorScheme.error
    }
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = color)
}
