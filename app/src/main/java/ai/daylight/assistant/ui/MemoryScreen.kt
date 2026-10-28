package ai.daylight.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.MemoryEntity
import ai.daylight.assistant.domain.MemoryEngine
import ai.daylight.assistant.ui.dot.DotButtonStyle
import ai.daylight.assistant.ui.dot.DotConfirmDialog
import ai.daylight.assistant.ui.dot.DotDialog
import ai.daylight.assistant.ui.dot.DotDialogTitle
import ai.daylight.assistant.ui.dot.DotField
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotPill
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotToggle
import ai.daylight.assistant.ui.tune.TuneDetail
import ai.daylight.assistant.ui.tune.TuneEmpty
import ai.daylight.assistant.ui.tune.TunePage
import ai.daylight.assistant.ui.tune.TuneRow
import ai.daylight.assistant.ui.tune.TuneSection
import ai.daylight.assistant.ui.tune.TuneTrailing
import java.util.Locale

/**
 * Memory: the chat memory switch, a search field, then each saved fact as a ruled row with
 * its category, a switch, pin and delete. Empty shows the kit empty state (§10).
 */
@Composable
fun MemoryScreen(vm: MemoryViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    TunePage(
        "Memory",
        onBack,
        scrollable = false,
        actions = {
            if (memories.isNotEmpty()) {
                DotIconButton(R.drawable.ic_kz_trash, "Clear all memories", { confirmClear = true }, tint = DotTheme.c.textSecondary)
            }
        }
    ) {
        TuneRow(
            R.drawable.ic_kz_memory, "Chat memory",
            if (settings.memoryEnabled) memoryCountCaption(memories.size) else "Paused, nothing is saved or used",
            TuneTrailing.Switch(settings.memoryEnabled, vm::setMemoryEnabled)
        )
        Spacer(Modifier.height(12.dp))
        DotField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = "Search memories",
            leadingIcon = R.drawable.ic_kz_search
        )
        Spacer(Modifier.height(16.dp))

        if (memories.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                TuneEmpty(
                    dotIcon = R.drawable.ic_kz_memory_dot,
                    words = if (query.isBlank()) "No memories" else "No match",
                    body = if (query.isBlank()) {
                        "Facts you share in chat, like your name or your plans. Kept only on this phone and recalled when they help."
                    } else {
                        "No saved memory matches that search."
                    },
                    action = if (query.isBlank()) "Add a memory" else null,
                    onAction = { showAddDialog = true }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(memories, key = { it.id }) { memory ->
                    MemoryRow(
                        memory = memory,
                        onEnabled = { vm.setEnabled(memory.id, it) },
                        onPin = { vm.togglePinned(memory.id, !memory.pinned) },
                        onDelete = { vm.delete(memory.id) }
                    )
                }
                item { HorizontalDivider(thickness = 1.dp, color = DotTheme.c.surfaceRaised) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                DotPillButton("Add a memory", { showAddDialog = true }, icon = R.drawable.ic_kz_plus)
            }
        }

        notice?.let { TuneDetail(it, Modifier.padding(bottom = 12.dp)) }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onAdd = vm::add
        )
    }

    if (confirmClear) {
        DotConfirmDialog(
            title = "Clear all memories?",
            body = "Every saved memory is deleted from this phone. Chat history stays as it is.",
            confirm = "Clear all",
            destructive = true,
            onConfirm = {
                confirmClear = false
                vm.clearAll()
            },
            onDismiss = { confirmClear = false }
        )
    }
}

private fun memoryCountCaption(count: Int): String =
    "${if (count == 1) "1 memory" else "$count memories"}, ${MemoryEngine.Category.entries.size} categories"

/** One memory: category and state in mono, the fact in Geist, then switch, pin and delete. */
@Composable
private fun MemoryRow(
    memory: MemoryEntity,
    onEnabled: (Boolean) -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val c = DotTheme.c
    val category = runCatching { MemoryEngine.Category.valueOf(memory.category) }.getOrDefault(MemoryEngine.Category.OTHER)
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (memory.pinned) {
                Icon(painterResource(R.drawable.ic_kz_pin), "Pinned", Modifier.padding(end = 6.dp).size(14.dp), tint = c.textPrimary)
            }
            Text(
                listOf(category.label(), if (memory.enabled) "Active" else "Paused")
                    .joinToString(" · ")
                    .uppercase(Locale.US),
                Modifier.weight(1f),
                style = DotType.Caption,
                color = c.textTertiary,
                maxLines = 1
            )
            // The 56x48 box is the switch, so the touch target is full size.
            Box(
                Modifier
                    .size(56.dp, 48.dp)
                    .toggleable(value = memory.enabled, role = Role.Switch, onValueChange = onEnabled)
                    .semantics { contentDescription = "Use this memory" },
                contentAlignment = Alignment.CenterEnd
            ) {
                DotToggle(checked = memory.enabled, onChange = null)
            }
        }
        Text(
            memory.content,
            Modifier.padding(end = 8.dp),
            style = DotType.Body,
            color = if (memory.enabled) c.textPrimary else c.textTertiary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DotIconButton(
                R.drawable.ic_kz_pin,
                if (memory.pinned) "Unpin memory" else "Pin memory",
                onPin,
                tint = if (memory.pinned) c.textPrimary else c.textTertiary,
                iconSize = 18.dp
            )
            DotIconButton(R.drawable.ic_kz_trash, "Delete memory", onDelete, tint = c.textTertiary, iconSize = 18.dp)
        }
    }
}

/** Add a memory, in the kit dialog: the fact, its category, then Cancel and Save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, MemoryEngine.Category, (Boolean) -> Unit) -> Unit
) {
    val c = DotTheme.c
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

    DotDialog(onDismiss = onDismiss, dismissible = !saving, padded = false, modifier = Modifier.testTag("add_memory_dialog")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                DotDialogTitle("Add a memory")
                Text(
                    "Saved only on this phone and used when it helps.",
                    Modifier.padding(top = 4.dp),
                    style = DotType.BodySmall,
                    color = c.textTertiary
                )
            }
            DotIconButton(R.drawable.ic_kz_close, "Close add memory", onDismiss, enabled = !saving)
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            DotField(
                value = content,
                onValueChange = {
                    content = it
                    saveError = null
                },
                label = "What should Kryzz remember?",
                placeholder = "For example: I prefer concise answers",
                singleLine = false,
                minLines = 3,
                maxLines = 5,
                isError = visibleError != null,
                supporting = visibleError ?: "${MemoryEngine.MIN_MEMORY_CHARS}–${MemoryEngine.MAX_MEMORY_CHARS} characters",
                supportingEnd = "${content.trim().length}/${MemoryEngine.MAX_MEMORY_CHARS}",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                fieldModifier = Modifier.focusRequester(focusRequester).testTag("memory_content")
            )
            Spacer(Modifier.height(20.dp))
            TuneSection("Category")
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MemoryEngine.Category.entries.forEach { option ->
                    DotPill(
                        option.label(),
                        selected = option == category,
                        onClick = { category = option },
                        modifier = Modifier.testTag("memory_category_${option.name.lowercase(Locale.US)}")
                    )
                }
            }
            TuneDetail(category.description(), Modifier.padding(top = 6.dp))
        }
        HorizontalDivider(thickness = 1.dp, color = c.border)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            DotPillButton("Cancel", onDismiss, style = DotButtonStyle.Ghost, enabled = !saving)
            DotPillButton(
                "Save memory",
                { submit() },
                Modifier.heightIn(min = 48.dp).testTag("save_memory"),
                enabled = validationError == null,
                busy = saving
            )
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
