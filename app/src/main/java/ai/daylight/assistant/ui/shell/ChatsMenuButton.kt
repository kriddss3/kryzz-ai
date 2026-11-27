package ai.daylight.assistant.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.dot.DotTheme

/**
 * The chat-library mark: three rows of dots, the same menu shape as three horizontal
 * lines, drawn in the strip's dot language with one yellow live dot.
 */
@Composable
fun ChatsMenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Open chats"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(width = 18.dp, height = 12.dp)) {
            val cols = 5
            val rows = 3
            val radius = 1.15.dp.toPx()
            val gapX = (size.width - radius * 2f) / (cols - 1)
            val gapY = (size.height - radius * 2f) / (rows - 1)
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val color = if (row == 0 && col == 0) c.accent else c.textPrimary
                    drawCircle(color, radius, Offset(radius + col * gapX, radius + row * gapY))
                }
            }
        }
    }
}
