package ai.daylight.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.data.local.MemoryEntity
import ai.daylight.assistant.domain.MemoryEngine
import ai.daylight.assistant.ui.theme.LocalGlassOpacity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(vm: MemoryViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Memory", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (memories.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear all memories")
                            Text(" Clear all")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalGlassOpacity.current),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = LocalGlassOpacity.current),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Chat memory", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.memoryEnabled) {
                                "${memories.size} memor${if (memories.size == 1) "y" else "ies"} · ${MemoryEngine.Category.entries.size} categories"
                            } else {
                                "Paused — facts are not being saved or used"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.memoryEnabled,
                        onCheckedChange = vm::setMemoryEnabled,
                        modifier = Modifier.semantics { contentDescription = "Enable chat memory" }
                    )
                }
            }

            OutlinedTextField(
                value = vm.query.collectAsState().value,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search memories…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Text("No memories yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Memories are facts you share in chat — like your name, what you like, or your plans. They are stored only on this device and recalled when relevant.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 320.dp)
                        )
                        FilledTonalButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text(" Add a memory")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(memories, key = { it.id }) { memory ->
                        MemoryRow(
                            memory = memory,
                            onEnabled = { vm.setEnabled(memory.id, it) },
                            onPin = { vm.togglePinned(memory.id, !memory.pinned) },
                            onDelete = { vm.delete(memory.id) }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

            if (memories.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(" Add a memory")
                }
            }

            notice?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onAdd = vm::add
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all memories?") },
            text = { Text("Every stored memory is deleted permanently from this device. Chat history is not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearAll()
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MemoryRow(
    memory: MemoryEntity,
    onEnabled: (Boolean) -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val category = runCatching { MemoryEngine.Category.valueOf(memory.category) }.getOrDefault(MemoryEngine.Category.OTHER)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (memory.enabled) {
            MaterialTheme.colorScheme.surface.copy(alpha = LocalGlassOpacity.current)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
        },
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(
                        category.label(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                if (memory.pinned) {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (memory.enabled) "Active" else "Paused",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = memory.enabled,
                    onCheckedChange = onEnabled,
                    modifier = Modifier.semantics { contentDescription = "Use this memory" }
                )
            }
            Text(
                memory.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (memory.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPin, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = if (memory.pinned) "Unpin memory" else "Pin memory",
                        tint = if (memory.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete memory",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, MemoryEngine.Category, (Boolean) -> Unit) -> Unit
) {
    var content by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(MemoryEngine.Category.USER) }
    var saving by remember { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val validationError = memoryInputError(content)
    val visibleError = saveError ?: validationError.takeIf { content.isNotEmpty() }

    fun submit() {
        if (saving || validationError != null) return
        keyboard?.hide()
        saving = true
        saveError = null
        onAdd(content.trim(), category) { saved ->
            saving = false
            if (saved) onDismiss() else saveError = "This memory is already saved."
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(minOf(maxHeight, 640.dp))
                    .testTag("add_memory_dialog"),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Add a memory",
                                modifier = Modifier.semantics { heading() },
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                "Saved only on this device and used when relevant.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss, enabled = !saving) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close add memory")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = content,
                                onValueChange = {
                                    content = it
                                    saveError = null
                                },
                                label = { Text("What should Kryzz remember?") },
                                placeholder = { Text("For example: I prefer concise answers") },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .testTag("memory_content"),
                                shape = RoundedCornerShape(16.dp),
                                isError = visibleError != null,
                                supportingText = {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            visibleError ?: "${MemoryEngine.MIN_MEMORY_CHARS}–${MemoryEngine.MAX_MEMORY_CHARS} characters",
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text("${content.trim().length}/${MemoryEngine.MAX_MEMORY_CHARS}")
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { submit() })
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Category", style = MaterialTheme.typography.titleSmall)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MemoryEngine.Category.entries.forEach { option ->
                                        FilterChip(
                                            selected = option == category,
                                            onClick = { category = option },
                                            label = { Text(option.label()) },
                                            leadingIcon = if (option == category) {
                                                { Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(18.dp)) }
                                            } else null,
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("memory_category_${option.name.lowercase()}")
                                        )
                                    }
                                }
                                Text(
                                    category.description(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !saving,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Cancel") }
                        Button(
                            onClick = { submit() },
                            enabled = validationError == null && !saving,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_memory")
                        ) {
                            if (saving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save memory")
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

internal fun memoryInputError(value: String): String? {
    val length = value.trim().length
    return when {
        length == 0 -> "Enter something to remember."
        length < MemoryEngine.MIN_MEMORY_CHARS -> "Add a little more detail."
        length > MemoryEngine.MAX_MEMORY_CHARS -> "Keep this memory under ${MemoryEngine.MAX_MEMORY_CHARS} characters."
        else -> null
    }
}

private fun MemoryEngine.Category.label(): String = when (this) {
    MemoryEngine.Category.USER -> "About you"
    MemoryEngine.Category.PREFERENCE -> "Preference"
    MemoryEngine.Category.GOAL -> "Goal"
    MemoryEngine.Category.PROJECT -> "Project"
    MemoryEngine.Category.OTHER -> "Note"
}

private fun MemoryEngine.Category.description(): String = when (this) {
    MemoryEngine.Category.USER -> "Identity, background, location, or other facts about you."
    MemoryEngine.Category.PREFERENCE -> "Things you like, dislike, or want Kryzz to do consistently."
    MemoryEngine.Category.GOAL -> "An outcome you are actively working toward."
    MemoryEngine.Category.PROJECT -> "Ongoing work, study, or a longer-running task."
    MemoryEngine.Category.OTHER -> "A durable detail that does not fit the other categories."
}

