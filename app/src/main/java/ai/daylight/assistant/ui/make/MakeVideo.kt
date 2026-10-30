package ai.daylight.assistant.ui.make

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A clip player on a TextureView with the platform MediaPlayer: no new dependency, and unlike
 * VideoView's SurfaceView it clips to the frame's rounded corners and scrolls with the page.
 * It loops, and pauses when the app goes to the background.
 */
@Stable
internal class MakePlayer(private val source: String) {
    var durationMs by mutableLongStateOf(0L)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var playing by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var wantPlay = true

    fun attach(texture: SurfaceTexture) {
        release()
        val out = Surface(texture)
        surface = out
        val p = MediaPlayer()
        player = p
        try {
            p.setSurface(out)
            p.setDataSource(source)
            p.isLooping = true
            p.setOnPreparedListener { mp ->
                prepared = true
                durationMs = mp.duration.toLong().coerceAtLeast(0)
                if (positionMs > 0) mp.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
                if (wantPlay) {
                    mp.start()
                    playing = true
                }
            }
            p.setOnErrorListener { _, _, _ ->
                failed = true
                playing = false
                true
            }
            p.prepareAsync()
        } catch (_: Throwable) {
            failed = true
        }
    }

    fun release() {
        prepared = false
        playing = false
        runCatching { player?.release() }
        player = null
        runCatching { surface?.release() }
        surface = null
    }

    fun toggle() {
        val p = player ?: return
        if (!prepared) {
            wantPlay = !wantPlay
            return
        }
        if (p.isPlaying) pause() else {
            wantPlay = true
            p.start()
            playing = true
        }
    }

    fun pause() {
        wantPlay = false
        val p = player ?: return
        if (prepared && p.isPlaying) p.pause()
        playing = false
    }

    fun seekTo(ms: Long) {
        positionMs = ms.coerceIn(0, durationMs.coerceAtLeast(0))
        val p = player ?: return
        if (prepared) p.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
    }

    /** Reads the playhead; the screen calls this every 100ms while the clip plays. */
    fun poll() {
        val p = player ?: return
        if (prepared) positionMs = runCatching { p.currentPosition.toLong() }.getOrDefault(positionMs)
    }
}

@Composable
internal fun rememberMakePlayer(source: String): MakePlayer {
    val player = remember(source) { MakePlayer(source) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    LaunchedEffect(player, player.playing) {
        while (player.playing) {
            player.poll()
            delay(100)
        }
    }
    return player
}

/** The picture: a TextureView the player draws into. Tap to pause or play. */
@Composable
internal fun MakeVideoSurface(player: MakePlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = player.attach(texture)
                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        player.release()
                        return true
                    }
                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
        modifier = modifier
    )
}

/** The clip in its frame, with a tap layer over it (an AndroidView keeps its own touches). */
@Composable
internal fun MakeVideoFrame(player: MakePlayer, modifier: Modifier = Modifier) {
    Box(modifier) {
        MakeVideoSurface(player, Modifier.fillMaxSize())
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = if (player.playing) "Pause" else "Play") { player.toggle() }
                .semantics { contentDescription = "Clip" }
        )
    }
}

/**
 * Kit dot scrubber: [count] dots, the played part white, a 4x18dp yellow playhead. Tap or
 * drag to seek. TalkBack reads it as a slider in seconds.
 */
@Composable
internal fun DotScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    count: Int = 44,
) {
    val c = DotTheme.c
    val head = MakeModel.scrubIndex(positionMs, durationMs, count)
    val seconds = durationMs / 1_000f
    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(durationMs) {
                detectTapGestures { onSeek(MakeModel.seekPosition(it.x, size.width.toFloat(), durationMs)) }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSeek(MakeModel.seekPosition(change.position.x, size.width.toFloat(), durationMs))
                }
            }
            .semantics {
                contentDescription = "Clip position"
                stateDescription = "${MakeModel.clockLabel(positionMs)} of ${MakeModel.clockLabel(durationMs)}"
                progressBarRangeInfo = ProgressBarRangeInfo(positionMs / 1_000f, 0f..seconds.coerceAtLeast(0.1f))
                setProgress { target ->
                    onSeek((target * 1_000).toLong())
                    true
                }
            }
    ) {
        val dot = 4.dp.toPx()
        val pitch = if (count > 1) (size.width - dot) / (count - 1) else 0f
        val cy = size.height / 2f
        for (i in 0 until count) {
            val x = dot / 2 + i * pitch
            if (i == head) {
                val w = 4.dp.toPx()
                val h = 18.dp.toPx()
                drawRoundRect(c.accentText, Offset(x - w / 2, cy - h / 2), Size(w, h), CornerRadius(w / 2, w / 2))
            } else {
                drawCircle(if (i < head) c.textPrimary else c.borderStrong, dot / 2, Offset(x, cy))
            }
        }
    }
}

/** Times on both sides and the 44dp pause button in the middle, as in the kit. */
@Composable
internal fun PlayerControls(player: MakePlayer, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(MakeModel.clockLabel(player.positionMs), style = DotType.Data.copy(fontSize = 13.sp), color = c.textPrimary)
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = null) { player.toggle() }
                .semantics { contentDescription = if (player.playing) "Pause" else "Play" },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(44.dp).border(1.dp, c.borderStrong, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(if (player.playing) R.drawable.ic_kz_pause else R.drawable.ic_kz_play),
                    null,
                    Modifier.size(20.dp),
                    tint = c.textPrimary
                )
            }
        }
        Text(MakeModel.clockLabel(player.durationMs), style = DotType.Data.copy(fontSize = 13.sp), color = c.textTertiary)
    }
}


/**
 * The kit filmstrip: six frames from the clip, the one under the playhead outlined white and
 * the rest at 55%. Tapping a frame jumps there. Frames are read once per clip, off the main thread.
 */
@Composable
internal fun Filmstrip(path: String, durationMs: Long, positionMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, frames: Int = 6) {
    val c = DotTheme.c
    val times = remember(durationMs, frames) { MakeModel.filmstripTimes(durationMs, frames) }
    val side = with(LocalDensity.current) { 96.dp.roundToPx() }
    var bitmaps by remember(path, times) { mutableStateOf<List<ImageBitmap?>>(emptyList()) }
    LaunchedEffect(path, times) {
        if (times.isNotEmpty()) bitmaps = withContext(Dispatchers.IO) { MakeMedia.frames(path, times, side) }
    }
    if (times.isEmpty()) return
    val current = MakeModel.currentFrame(positionMs, durationMs, frames)
    val shape = RoundedCornerShape(6.dp)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        times.forEachIndexed { i, t ->
            val on = i == current
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(if (on) Modifier.border(1.5.dp, c.textPrimary, RoundedCornerShape(7.dp)).padding(2.dp) else Modifier)
                    .clip(shape)
                    .background(c.surfaceRaised)
                    .alpha(if (on) 1f else .55f)
                    .clickable(onClickLabel = "Jump to ${MakeModel.clockLabel(t)}") { onSeek(t) }
                    .semantics { role = Role.Button }
            ) {
                bitmaps.getOrNull(i)?.let {
                    Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}
