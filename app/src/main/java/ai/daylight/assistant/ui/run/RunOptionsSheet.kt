package ai.daylight.assistant.ui.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.effects.DotToggle
import ai.daylight.assistant.ui.talk.AttachTile
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.OptionRow
import ai.daylight.assistant.ui.talk.Segment
import ai.daylight.assistant.ui.talk.SheetLabel

/**
 * Run options, opened from the "+" on Run's reply line. Same parts as Talk's sheet plus the
 * workflow: Talk/Run (fresh chats only), attach, workflow, web search, model, voice chat,
 * the chat library and New run.
 */
@Composable
fun RunOptionsSheet(
    modeLocked: Boolean,
    workflowLabel: String,
    webSearch: Boolean,
    modelLabel: String,
    onTalk: () -> Unit,
    onWorkflow: () -> Unit,
    onWebSearch: (Boolean) -> Unit,
    onModel: () -> Unit,
    onPickImage: () -> Unit,
    onPickCamera: () -> Unit,
    onPickFile: () -> Unit,
    onVoice: () -> Unit,
    onChats: () -> Unit,
    onNewRun: () -> Unit,
    onDismiss: () -> Unit,
) {
    DotSheet(onDismiss) {
        if (!modeLocked) {
            SheetLabel("Mode")
            Segment(left = "Talk", right = "Run", rightSelected = true, onLeft = onTalk, onRight = onDismiss)
            Spacer(Modifier.height(20.dp))
        }
        SheetLabel("Attach")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AttachTile(R.drawable.ic_kz_image, "Image", onPickImage, Modifier.weight(1f))
            AttachTile(R.drawable.ic_kz_camera, "Camera", onPickCamera, Modifier.weight(1f))
            AttachTile(R.drawable.ic_kz_file, "File", onPickFile, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        OptionRow(R.drawable.ic_kz_tools, "Workflow", value = workflowLabel, onClick = onWorkflow)
        OptionRow(R.drawable.ic_kz_web, "Web search", onClick = { onWebSearch(!webSearch) }) {
            DotToggle(checked = webSearch, onChange = onWebSearch)
        }
        OptionRow(R.drawable.ic_kz_model, "Model", value = modelLabel, onClick = onModel)
        OptionRow(R.drawable.ic_kz_voice_wave, "Voice chat", value = "In this chat", onClick = onVoice)
        OptionRow(R.drawable.ic_kz_history, "Chats", onClick = onChats)
        OptionRow(R.drawable.ic_kz_chat_new, "New run", onClick = onNewRun)
    }
}
