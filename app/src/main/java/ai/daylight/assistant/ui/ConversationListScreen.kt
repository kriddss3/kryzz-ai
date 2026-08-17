package ai.daylight.assistant.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.data.local.ConversationSummary
import ai.daylight.assistant.ui.theme.LocalGlassOpacity
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class LibraryFilter(val label: String) {
    CHATS("All")
}

internal enum class LibraryDateSection(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This week"),
    EARLIER("Earlier")
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    vm: ConversationListViewModel,
    onOpen: (String) -> Unit,
    onNew: (String) -> Unit,
    onModels: () -> Unit,
    onLlms: () -> Unit,
    onSkills: () -> Unit,
    onSettings: () -> Unit,
    onChat: () -> Unit = onSettings
) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf("") }
    var rename by remember { mutableStateOf<ConversationSummary?>(null) }
    var delete by remember { mutableStateOf<ConversationSummary?>(null) }
    var appMenu by remember { mutableStateOf(false) }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(pendingExport)
                }
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                if (content != null) vm.import(content)
            }
        }
    }

    LaunchedEffect(notice) {
        notice?.let {
            snack.showSnackbar(it)
            vm.notice.value = null
        }
    }
    val groupedConversations = remember(conversations) {
        conversations.groupBy { conversationDateSection(it.updatedAt) }
    }

    Scaffold(
        modifier = Modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalGlassOpacity.current),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = { Text("Library", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onModels) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Models")
                    }
                    Box {
                        IconButton(onClick = { appMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = appMenu,
                            onDismissRequest = { appMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export conversations") },
                                leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                                onClick = {
                                    appMenu = false
                                    vm.export {
                                        pendingExport = it
                                        createFile.launch("kryzz-ai-conversations.json")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import conversations") },
                                leadingIcon = { Icon(Icons.Outlined.Upload, contentDescription = null) },
                                onClick = {
                                    appMenu = false
                                    openFile.launch(arrayOf("application/json"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                onClick = {
                                    appMenu = false
                                    onSettings()
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.testTag("new_chat"),
                onClick = { vm.create(onNew) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New chat") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 104.dp)
        ) {
            item(key = "library_search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Search library") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                )
            }

            if (conversations.isEmpty()) {
                item(key = "library_empty") {
                    LibraryEmptyState(hasQuery = query.isNotBlank())
                }
            } else {
                LibraryDateSection.entries.forEach { section ->
                    val sectionItems = groupedConversations[section].orEmpty()
                    if (sectionItems.isNotEmpty()) {
                        item(key = "library_section_${section.name}") {
                            Text(
                                text = section.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 4.dp)
                            )
                        }
                        items(sectionItems, key = { it.id }) { item ->
                            ConversationRow(
                                item = item,
                                section = section,
                                onOpen = { onOpen(item.id) },
                                onPin = { vm.pin(item.id, !item.pinned) },
                                onRename = { rename = item },
                                onDelete = { delete = item }
                            )
                        }
                    }
                }
            }
        }
    }

    rename?.let { item ->
        RenameDialog(
            initial = item.title,
            onDismiss = { rename = null }
        ) {
            vm.rename(item.id, it)
            rename = null
        }
    }
    delete?.let { item ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text("Delete conversation?") },
            text = {
                Text("This removes “${item.title}” and its messages from this device. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(item.id)
                    delete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { delete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LibraryEmptyState(hasQuery: Boolean) {
    val title: String
    val message: String
    when {
        hasQuery -> {
            title = "No results"
            message = "Try a different title or phrase."
        }
        else -> {
            title = "Your library is ready"
            message = "Start a new chat and it will be organised here."
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 58.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (hasQuery) Icons.Outlined.Search else Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationRow(
    item: ConversationSummary,
    section: LibraryDateSection,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onMove: (() -> Unit)? = null,
    onDelete: () -> Unit,
    horizontalPadding: Dp = 4.dp
) {
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .testTag("library_conversation_row")
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menu = true }
                )
                .padding(start = horizontalPadding, end = 0.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.pinned) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = item.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = compactUpdatedAt(item.updatedAt, section),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Conversation actions")
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (item.pinned) "Unpin" else "Pin") },
                        leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                        onClick = {
                            menu = false
                            onPin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            onRename()
                        }
                    )
                    if (onMove != null) {
                        DropdownMenuItem(
                            text = { Text("Move to folder") },
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                            onClick = {
                                menu = false
                                onMove()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            onDelete()
                        }
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = horizontalPadding),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

internal fun conversationDateSection(timestamp: Long, now: Long = System.currentTimeMillis()): LibraryDateSection {
    val today = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val week = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
    return when {
        timestamp >= today.timeInMillis -> LibraryDateSection.TODAY
        timestamp >= yesterday.timeInMillis -> LibraryDateSection.YESTERDAY
        timestamp >= week.timeInMillis -> LibraryDateSection.THIS_WEEK
        else -> LibraryDateSection.EARLIER
    }
}

internal fun compactUpdatedAt(timestamp: Long, section: LibraryDateSection): String = when (section) {
    LibraryDateSection.TODAY, LibraryDateSection.YESTERDAY ->
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
    LibraryDateSection.THIS_WEEK ->
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    LibraryDateSection.EARLIER ->
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}

@Composable
internal fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename conversation") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
