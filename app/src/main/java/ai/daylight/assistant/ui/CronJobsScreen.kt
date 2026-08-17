package ai.daylight.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.data.CRON_DAY_NAMES
import ai.daylight.assistant.data.CronRecurrence
import ai.daylight.assistant.data.CronSchedulePresets
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import ai.daylight.assistant.ui.theme.LocalGlassOpacity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronJobsScreen(vm: CronViewModel, onBack: () -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTask by remember { mutableStateOf<ScheduledTaskEntity?>(null) }
    val context = LocalContext.current

    // Completion alerts need the runtime notification permission on API 33+; asked
    // when the user creates or re-enables a task, never on screen entry.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Cron jobs", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Text("No cron jobs yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Cron jobs run a prompt on a repeating schedule in the background and notify you when each run finishes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 320.dp)
                        )
                        FilledTonalButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("cron_add_empty")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text(" Create a cron job")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        CronTaskRow(
                            task = task,
                            onEnabled = {
                                if (it) ensureNotificationPermission()
                                vm.setEnabled(task.id, it)
                            },
                            onRunNow = { vm.runNow(task.id) },
                            onDelete = { deleteTask = task }
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cron_add")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(" New cron job")
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
        AddCronTaskDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, prompt, recurrence, hour, minute, daysOfWeek ->
                ensureNotificationPermission()
                vm.createTask(title, prompt, recurrence, hour, minute, daysOfWeek = daysOfWeek) { saved ->
                    if (saved) showAddDialog = false
                }
            }
        )
    }

    deleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTask = null },
            title = { Text("Delete “${task.title}”?") },
            text = { Text("The schedule stops and its dedicated conversation is removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(task.id)
                    deleteTask = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTask = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CronTaskRow(
    task: ScheduledTaskEntity,
    onEnabled: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit
) {
    val lastRunFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val nextRunAt = remember(task) { CronSchedulePresets.nextRunAt(task) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (task.enabled) {
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        CronSchedulePresets.label(task),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = task.enabled,
                    onCheckedChange = onEnabled,
                    modifier = Modifier.semantics { contentDescription = "Enable ${task.title}" }
                )
            }
            Text(
                task.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        task.lastRunAt?.let { "Last run ${lastRunFormat.format(Date(it))}" } ?: "Never run",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (task.enabled && nextRunAt != null) {
                        Text(
                            "Next run ${lastRunFormat.format(Date(nextRunAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onRunNow, modifier = Modifier.size(48.dp).testTag("cron_run_now")) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = "Run ${task.title} now",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete ${task.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddCronTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, CronRecurrence, Int, Int, Set<Int>) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var recurrenceName by rememberSaveable { mutableStateOf(CronRecurrence.DAILY.name) }
    val recurrence = runCatching { CronRecurrence.valueOf(recurrenceName) }.getOrDefault(CronRecurrence.DAILY)
    val timeState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
    var daysCsv by rememberSaveable { mutableStateOf("1") }
    var showDaysMenu by rememberSaveable { mutableStateOf(false) }
    val selectedDays = daysCsv.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
        .ifEmpty { setOf(1) }
    val valid = title.isNotBlank() && prompt.isNotBlank() &&
        (recurrence != CronRecurrence.WEEKLY || selectedDays.isNotEmpty())

    fun toggleDay(isoDay: Int) {
        val next = selectedDays.toMutableSet()
        if (isoDay in next) {
            if (next.size > 1) next.remove(isoDay)
        } else {
            next.add(isoDay)
        }
        daysCsv = next.sorted().joinToString(",")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New cron job") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Name") },
                    placeholder = { Text("Morning briefing") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cron_task_name"),
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt") },
                    placeholder = { Text("Summarise today's AI news") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("cron_task_prompt"),
                    shape = RoundedCornerShape(16.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Runs", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CronRecurrence.selectable.forEach { option ->
                            FilterChip(
                                selected = recurrence == option,
                                onClick = {
                                    recurrenceName = option.name
                                    if (option == CronRecurrence.WEEKLY) showDaysMenu = true
                                },
                                label = { Text(option.label) },
                                leadingIcon = if (recurrence == option) {
                                    { Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("cron_recurrence_${option.name.lowercase()}")
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Time of day", style = MaterialTheme.typography.titleSmall)
                    TimePicker(
                        state = timeState,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("cron_time_picker")
                    )
                }
                if (recurrence == CronRecurrence.WEEKLY) {
                    OutlinedButton(
                        onClick = { showDaysMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("cron_choose_days")
                    ) {
                        Text("Days: ${CronSchedulePresets.weekdayLabel(selectedDays)}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        title,
                        prompt,
                        recurrence,
                        timeState.hour,
                        timeState.minute,
                        if (recurrence == CronRecurrence.WEEKLY) selectedDays else emptySet()
                    )
                },
                enabled = valid,
                modifier = Modifier.testTag("cron_task_save")
            ) { Text("Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDaysMenu) {
        WeeklyDaysMenu(
            selectedDays = selectedDays,
            onToggle = ::toggleDay,
            onDone = { showDaysMenu = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeeklyDaysMenu(
    selectedDays: Set<Int>,
    onToggle: (Int) -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Which days?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Pick every day this job should run.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().testTag("cron_weekly_days"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CRON_DAY_NAMES.forEachIndexed { index, name ->
                        val isoDay = index + 1
                        val selected = isoDay in selectedDays
                        FilterChip(
                            selected = selected,
                            onClick = { onToggle(isoDay) },
                            label = { Text(name.take(3)) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(18.dp)) }
                            } else null,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("cron_day_$isoDay")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDone,
                enabled = selectedDays.isNotEmpty(),
                modifier = Modifier.testTag("cron_days_done")
            ) { Text("Done") }
        }
    )
}
