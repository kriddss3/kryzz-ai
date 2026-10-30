package ai.daylight.assistant.ui.make

import ai.daylight.assistant.data.remote.MediaModel
import ai.daylight.assistant.data.remote.bareMiniMaxModel
import ai.daylight.assistant.domain.DEFAULT_MEDIA_RATIO
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.MakeKind
import ai.daylight.assistant.domain.MakeStyle
import ai.daylight.assistant.domain.MakeOutcome
import ai.daylight.assistant.domain.MakeRender
import ai.daylight.assistant.domain.MediaJobStatus
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.domain.RecentOutput
import ai.daylight.assistant.ui.home.HomeBoard
import ai.daylight.assistant.ui.shell.InkLine
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Everything Make says and decides that doesn't need a screen (C7), so it is unit tested.
 * Only real parameters are offered: the ratio when the request carries one, never the kit's
 * count, style, steps or seed, and no percentage (the providers report none).
 */
object MakeModel {
    private val RATIO = Regex("^(\\d{1,2}):(\\d{1,2})$")
    private val KNOWN_RATIOS = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3", "21:9", "9:21", "5:4", "4:5")
    private val QUEUED = setOf("queued", "queueing", "pending", "submitted", "waiting")
    private val PROCESSING = setOf("processing", "in_progress", "running", "preparing", "generating", "started")

    /** How many answers' files Make reads for its recent ink. */
    const val RECENT_LIMIT = 40

    /** Tiles in the recent ink row before the "+N" tile. */
    const val RECENT_TILES = 4

    // ── Ratio ────────────────────────────────────────────────────────────────

    /** The "W:H" ratios from a model's list, in its order. Words like "adaptive" are left out. */
    fun ratioChoices(supported: List<String>): List<String> =
        supported.map(String::trim).filter { ratioShape(it) != null }.distinct()

    /** 16:9 (the app's long-standing default) when offered, otherwise the model's first. */
    fun defaultRatio(choices: List<String>): String? = when {
        choices.isEmpty() -> null
        DEFAULT_MEDIA_RATIO in choices -> DEFAULT_MEDIA_RATIO
        else -> choices.first()
    }

    /** The chosen ratio if the model still takes it, else the default. Null: no Ratio dial. */
    fun ratioFor(chosen: String?, choices: List<String>): String? =
        chosen?.takeIf { it in choices } ?: defaultRatio(choices)

    fun ratioShape(ratio: String?): Pair<Int, Int>? {
        val match = RATIO.matchEntire(ratio?.trim().orEmpty()) ?: return null
        val w = match.groupValues[1].toInt()
        val h = match.groupValues[2].toInt()
        return if (w > 0 && h > 0) w to h else null
    }

    /**
     * The ratios a render can really ask for. MiniMax takes one for images and video: its
     * built-in model list says which (an id the list doesn't know gets the first model's,
     * since the ratio is a field of the API itself). OpenRouter video models report theirs in
     * the catalogue. OpenRouter's image request has no ratio field, so images there get none.
     */
    fun supportedRatios(kind: MakeKind, miniMax: Boolean, model: String, catalog: List<MediaModel>?): List<String> {
        if (model.isBlank()) return emptyList()
        if (!miniMax && kind == MakeKind.IMAGE) return emptyList()
        val list = catalog.orEmpty()
        val bare = bareMiniMaxModel(model)
        val match = list.firstOrNull { it.id.equals(model, ignoreCase = true) || (miniMax && it.id.equals(bare, ignoreCase = true)) }
        val ratios = match?.supportedAspectRatios ?: if (miniMax) list.firstOrNull()?.supportedAspectRatios.orEmpty() else emptyList()
        return ratioChoices(ratios)
    }

    /** Width over height of the frame a render fills: its ratio, else square for images and 16:9 for clips. */
    fun frameAspect(kind: MakeKind, ratio: String?): Float =
        ratioShape(ratio)?.let { (w, h) -> w.toFloat() / h } ?: if (kind == MakeKind.VIDEO) 16f / 9f else 1f

    /** Wide frames take the full width with the dials in a row below; tall ones sit beside them. */
    fun wideFrame(aspect: Float): Boolean = aspect > 1.05f

    /** The nearest common ratio ("16:9") within 3%, else the reduced pair, for a made file. */
    fun ratioLabel(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val aspect = width.toDouble() / height
        KNOWN_RATIOS.firstOrNull { known ->
            val (w, h) = ratioShape(known) ?: return@firstOrNull false
            abs(ln(aspect / (w.toDouble() / h))) < 0.03
        }?.let { return it }
        val g = gcd(width, height)
        val (w, h) = width / g to height / g
        return if (w <= 50 && h <= 50) "$w:$h" else String.format(Locale.US, "%.2f:1", aspect)
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    // ── Status while rendering ───────────────────────────────────────────────

    /**
     * The word for where a render stands. Images are one request, so they just render. Video
     * uses its job's real status: Sending until the provider accepts it, then the provider's
     * own Queued or Processing, then Downloading. An unknown word is shown as it came.
     */
    fun statusWord(kind: MakeKind, jobStatus: String?): String {
        if (kind == MakeKind.IMAGE) return "Rendering"
        val status = jobStatus?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            status.isEmpty() || status == MediaJobStatus.SENDING -> "Sending"
            status == MediaJobStatus.DOWNLOADING -> "Downloading"
            status in QUEUED -> "Queued"
            status in PROCESSING -> "Processing"
            else -> {
                val words = status.replace('_', ' ').replace('-', ' ').filter { it.isLetter() || it == ' ' }.trim()
                if (words.isEmpty() || words.length > 16) "Processing" else words.replaceFirstChar { it.uppercase() }
            }
        }
    }

    fun providerName(miniMax: Boolean): String = if (miniMax) "MiniMax" else "OpenRouter"

    /** "0:07", "1:05", "12:03": time since the render started. There is no estimate to show. */
    fun elapsedLabel(ms: Long): String {
        val s = (ms / 1_000).coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    // ── The strip ────────────────────────────────────────────────────────────

    /**
     * Make's strip line: what is rendering (live), what is on show ("MAKE CLIP READY 6S" as
     * in the kit), a failed or stopped render, or ready. [outcome] is the last render's end,
     * already dropped once its notice was dismissed.
     */
    fun inkLine(running: MakeRender?, jobStatus: String?, shown: GeneratedOutput?, durationMs: Long?, outcome: MakeOutcome?): InkLine = when {
        running?.running == true -> InkLine("MAKE ${statusWord(running.kind, jobStatus).uppercase(Locale.ROOT)}", live = true)
        shown?.kind == OutputKind.VIDEO -> InkLine(
            "MAKE CLIP READY" + (durationMs?.takeIf { it > 0 }?.let { " ${(it / 1_000.0).roundToInt()}S" }).orEmpty()
        )
        shown != null -> InkLine("MAKE IMAGE READY")
        outcome == MakeOutcome.FAILED -> InkLine("MAKE FAILED")
        outcome == MakeOutcome.STOPPED -> InkLine("MAKE STOPPED")
        else -> InkLine("MAKE READY")
    }

    // ── Result ───────────────────────────────────────────────────────────────

    fun kindOf(output: GeneratedOutput): MakeKind? = when (output.kind) {
        OutputKind.IMAGE -> MakeKind.IMAGE
        OutputKind.VIDEO -> MakeKind.VIDEO
        else -> null
    }

    /** An image or clip Make can show: it has a file on the phone or a link. */
    fun isMedia(output: GeneratedOutput): Boolean =
        kindOf(output) != null && (output.localPath != null || output.remoteUrl != null)

    /** The file a finished render made: the newest image or clip in its conversation since it started. */
    fun resultFor(render: MakeRender?, recent: List<RecentOutput>): RecentOutput? {
        if (render == null || render.outcome != MakeOutcome.DONE) return null
        val conversation = render.conversationId ?: return null
        return recent.firstOrNull {
            it.conversationId == conversation && it.createdAt >= render.startedAt && isMedia(it.output) && kindOf(it.output) == render.kind
        }
    }

    fun readyCaption(kind: MakeKind): String = if (kind == MakeKind.VIDEO) "Clip ready" else "Image ready"

    /** "6.0 s". */
    fun durationLabel(ms: Long): String = String.format(Locale.US, "%.1f s", ms / 1_000.0)

    /** "Made 10:21, 6.0 s": the time it was made (as Home words it) and a clip's length. */
    fun madeCaption(createdAt: Long, now: Long, zone: ZoneId, durationMs: Long? = null): String =
        "Made ${HomeBoard.timeLabel(createdAt, now, zone)}" +
            (durationMs?.takeIf { it > 0 }?.let { ", ${durationLabel(it)}" }).orEmpty()

    /**
     * The spec pills under a result's prompt, read from the file itself: a clip's length,
     * the ratio and its height ("720p"); an image's ratio and pixel size.
     */
    /** v2.1: the pill on a running render's prompt when it is dressed in a style. */
    fun styleSpecs(style: MakeStyle): List<String> = if (style == MakeStyle.PLAIN) emptyList() else listOf("${style.label} style")

    fun specs(kind: MakeKind, durationMs: Long?, width: Int, height: Int): List<String> = buildList {
        if (kind == MakeKind.VIDEO) durationMs?.takeIf { it > 0 }?.let { add(durationLabel(it)) }
        ratioLabel(width, height)?.let(::add)
        if (width > 0 && height > 0) {
            add(if (kind == MakeKind.VIDEO) "${minOf(width, height)}p" else "$width x $height")
        }
    }

    // ── Player ───────────────────────────────────────────────────────────────

    /** "00:02.3": minutes, seconds and tenths, as the kit's player shows them. */
    fun clockLabel(ms: Long): String {
        val tenths = (ms.coerceAtLeast(0) / 100)
        val seconds = tenths / 10
        return String.format(Locale.US, "%02d:%02d.%d", seconds / 60, seconds % 60, tenths % 10)
    }

    /** Which of [count] scrubber dots the playhead sits on. */
    fun scrubIndex(positionMs: Long, durationMs: Long, count: Int): Int {
        if (count <= 1 || durationMs <= 0) return 0
        return ((positionMs.toDouble() / durationMs) * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }

    /** The time a touch at [x] across a scrubber [width] wide points at. */
    fun seekPosition(x: Float, width: Float, durationMs: Long): Long {
        if (width <= 0f || durationMs <= 0) return 0
        return ((x / width).coerceIn(0f, 1f) * durationMs).toLong()
    }

    /** The middle of each of [frames] equal parts of a clip: where the filmstrip samples. */
    fun filmstripTimes(durationMs: Long, frames: Int): List<Long> =
        if (durationMs <= 0 || frames <= 0) emptyList() else List(frames) { ((it + 0.5) * durationMs / frames).toLong() }

    /** The filmstrip frame the playhead is in. */
    fun currentFrame(positionMs: Long, durationMs: Long, frames: Int): Int {
        if (durationMs <= 0 || frames <= 0) return 0
        return (positionMs * frames / durationMs).toInt().coerceIn(0, frames - 1)
    }

    // ── Recent ink ───────────────────────────────────────────────────────────

    /** The tiles to draw (files that can be opened, newest first) and how many more there are. */
    fun recentTiles(recent: List<RecentOutput>, max: Int = RECENT_TILES): Pair<List<RecentOutput>, Int> {
        val openable = recent.filter { it.output.localPath != null || it.output.remoteUrl != null }
        return openable.take(max) to (openable.size - max).coerceAtLeast(0)
    }
}
