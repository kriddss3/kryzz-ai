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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.data.CRON_DAY_NAMES
import ai.daylight.assistant.data.CronRecurrence
import ai.daylight.assistant.data.CronSchedulePresets
import ai.daylight.assistant.data.local.ScheduledTaskEntity
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
import ai.daylight.assistant.ui.tune.TuneSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Routines (cron jobs): each scheduled prompt as a ruled row with its schedule, a switch,
 * last and next run, Run now and delete. Crew (C9) builds on the same jobs.
 */
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

    TunePage("Routines", onBack, scrollable = false) {
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                TuneEmpty(
                    dotIcon = R.drawable.ic_kz_timer_dot,
                    words = "No routines",
                    body = "A routine runs a prompt on a schedule in the background and tells you when each run is done.",
                    action = "Create a routine",
                    onAction = { showAddDialog = true },
                    actionModifier = Modifier.testTag("cron_add_empty")
                )
            }
        } else {
            TuneDetail("Each routine runs in its own chat and notifies you when a run finishes.", Modifier.padding(top = 4.dp, bottom = 12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp)
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
                item { HorizontalDivider(thickness = 1.dp, color = DotTheme.c.surfaceRaised) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                DotPillButton(
                    "New routine",
                    { showAddDialog = true },
                    Modifier.testTag("cron_add"),
                    icon = R.drawable.ic_kz_plus
                )
            }
        }

        notice?.let { TuneDetail(it, Modifier.padding(bottom = 12.dp)) }
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
        DotConfirmDialog(
            title = "Delete “${task.title}”?",
            body = "The schedule stops and its own conversation is removed. This cannot be undone.",
            confirm = "Delete",
            destructive = true,
            onConfirm = {
                vm.delete(task.id)
                deleteTask = null
            },
            onDismiss = { deleteTask = null }
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
    val c = DotTheme.c
    val runFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val nextRunAt = remember(task) { CronSchedulePresets.nextRunAt(task) }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = DotType.Body.copy(fontSize = 15.sp, lineHeight = 19.sp),
                    color = if (task.enabled) c.textPrimary else c.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    CronSchedulePresets.label(task).uppercase(Locale.US),
                    Modifier.padding(top = 1.dp),
                    style = DotType.Caption.copy(fontSize = 10.sp, lineHeight = 14.sp),
                    color = c.textTertiary
                )
            }
            Box(
                Modifier
                    .size(56.dp, 48.dp)
                    .toggleable(value = task.enabled, role = Role.Switch, onValueChange = onEnabled)
                    .semantics { contentDescription = "Enable ${task.title}" },
                contentAlignment = Alignment.CenterEnd
            ) {
                DotToggle(checked = task.enabled, onChange = null)
            }
        }
        Text(
            task.prompt,
            Modifier.padding(top = 6.dp, end = 8.dp),
            style = DotType.BodySmall,
            color = if (task.enabled) c.textSecondary else c.textTertiary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (task.lastRunAt?.let { "Last run ${runFormat.format(Date(it))}" } ?: "Never run").uppercase(Locale.US),
                    style = DotType.Caption.copy(fontSize = 10.sp),
                    color = c.textTertiary
                )
                if (task.enabled && nextRunAt != null) {
                    Text(
                        "Next run ${runFormat.format(Date(nextRunAt))}".uppercase(Locale.US),
                        style = DotType.Caption.copy(fontSize = 10.sp),
                        color = c.textPrimary
                    )
                }
            }
            DotIconButton(
                R.drawable.ic_kz_play, "Run ${task.title} now", onRunNow,
                Modifier.testTag("cron_run_now"),
                iconSize = 18.dp
            )
            DotIconButton(R.drawable.ic_kz_trash, "Delete ${task.title}", onDelete, tint = c.textTertiary, iconSize = 18.dp)
        }
    }
}

/** New routine, in the kit dialog: name, prompt, how often, the time, and (weekly) the days. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddCronTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, CronRecurrence, Int, Int, Set<Int>) -> Unit
) {
    val c = DotTheme.c
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

    DotDialog(onDismiss = onDismiss, padded = false) {
        DotDialogTitle("New routine", Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp))
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            DotField(
                value = title,
                onValueChange = { title = it.take(80) },
                label = "Name",
                placeholder = "Morning briefing",
                fieldModifier = Modifier.testTag("cron_task_name")
            )
            Spacer(Modifier.height(16.dp))
            DotField(
                value = prompt,
                onValueChange = { prompt = it },
                label = "Prompt",
                placeholder = "Summarise today's AI news",
                singleLine = false,
                minLines = 3,
                maxLines = 5,
                fieldModifier = Modifier.testTag("cron_task_prompt")
            )
            Spacer(Modifier.height(20.dp))
            TuneSection("Runs")
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CronRecurrence.selectable.forEach { option ->
                    DotPill(
                        option.label,
                        selected = recurrence == option,
                        onClick = {
                            recurrenceName = option.name
                            if (option == CronRecurrence.WEEKLY) showDaysMenu = true
                        },
                        modifier = Modifier.testTag("cron_recurrence_${option.name.lowercase(Locale.US)}")
                    )
                }
            }
            if (recurrence == CronRecurrence.WEEKLY) {
                Row(Modifier.padding(top = 8.dp)) {
                    DotPillButton(
                        "Days: ${CronSchedulePresets.weekdayLabel(selectedDays)}",
                        { showDaysMenu = true },
                        Modifier.testTag("cron_choose_days"),
                        style = DotButtonStyle.Ghost,
                        icon = R.drawable.ic_kz_calendar
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            TuneSection("Time of day")
            TimePicker(
                state = timeState,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("cron_time_picker"),
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.surface,
                    clockDialSelectedContentColor = c.bg,
                    clockDialUnselectedContentColor = c.textSecondary,
                    selectorColor = c.textPrimary,
                    containerColor = c.surfaceRaised,
                    periodSelectorBorderColor = c.border,
                    periodSelectorSelectedContainerColor = c.textPrimary,
                    periodSelectorUnselectedContainerColor = c.surface,
                    periodSelectorSelectedContentColor = c.bg,
                    periodSelectorUnselectedContentColor = c.textSecondary,
                    timeSelectorSelectedContainerColor = c.textPrimary,
                    timeSelectorUnselectedContainerColor = c.surface,
                    timeSelectorSelectedContentColor = c.bg,
                    timeSelectorUnselectedContentColor = c.textPrimary
                )
            )
        }
        HorizontalDivider(thickness = 1.dp, color = c.border)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            DotPillButton("Cancel", onDismiss, style = DotButtonStyle.Ghost)
            DotPillButton(
                "Schedule",
                {
                    onAdd(
                        title,
                        prompt,
                        recurrence,
                        timeState.hour,
                        timeState.minute,
                        if (recurrence == CronRecurrence.WEEKLY) selectedDays else emptySet()
                    )
                },
                Modifier.testTag("cron_task_save"),
                enabled = valid
            )
        }
    }

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
    DotDialog(onDismiss = onDone) {
        DotDialogTitle("Which days?")
        Text(
            "Pick every day this routine should run.",
            Modifier.padding(top = 8.dp, bottom = 12.dp),
            style = DotType.Body,
            color = DotTheme.c.textSecondary
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag("cron_weekly_days"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CRON_DAY_NAMES.forEachIndexed { index, name ->
                val isoDay = index + 1
                DotPill(
                    name.take(3),
                    selected = isoDay in selectedDays,
                    onClick = { onToggle(isoDay) },
                    toggle = true,
                    modifier = Modifier.testTag("cron_day_$isoDay")
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
            DotPillButton(
                "Done",
                onDone,
                Modifier.testTag("cron_days_done"),
                enabled = selectedDays.isNotEmpty()
            )
        }
    }
}
