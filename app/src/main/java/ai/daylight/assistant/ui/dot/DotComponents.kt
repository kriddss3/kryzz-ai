package ai.daylight.assistant.ui.dot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Kit sheet handle: a 32x4dp bar in borderStrong, 10dp from the top (screens/src/common.py). */
@Composable
fun DotSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(32.dp, 4.dp)
            .background(DotTheme.c.borderStrong, RoundedCornerShape(2.dp))
    )
}
