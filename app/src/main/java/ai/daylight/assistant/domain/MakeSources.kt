package ai.daylight.assistant.domain

/**
 * A photo or clip the user added on Make, copied onto the phone (or a file Make already
 * saved). It is sent with the next render so the model can edit it or use it as a starting
 * frame or a reference.
 */
data class MakeSource(
    val id: String,
    val name: String,
    val mimeType: String,
    val localPath: String,
) {
    val image: Boolean get() = mimeType.startsWith("image/")
    val video: Boolean get() = mimeType.startsWith("video/")
    val usable: Boolean get() = (image || video) && localPath.isNotBlank()
}

/** Where one attached file sits in a video request. [wire] is the provider's role name. */
enum class SourceRole(val wire: String) {
    FIRST_FRAME("first_frame"),
    LAST_FRAME("last_frame"),
    REFERENCE_IMAGE("reference_image"),
    REFERENCE_VIDEO("reference_video"),
}

data class PlacedSource(val source: MakeSource, val role: SourceRole)

/**
 * How Make turns attached photos and clips into a request. One photo is the clip's first
 * frame. Two photos are the first and last frames. A clip, or three or more photos, are
 * references: providers will not mix fixed frames with references in one request.
 */
object MakeSources {
    const val MAX = 4
    const val MAX_BYTES = 20L * 1024 * 1024

    fun placeForVideo(sources: List<MakeSource>): List<PlacedSource> {
        val usable = sources.filter { it.usable }
        val images = usable.filter { it.image }
        val clips = usable.filter { it.video }
        return when {
            usable.isEmpty() -> emptyList()
            clips.isEmpty() && images.size == 1 -> listOf(PlacedSource(images[0], SourceRole.FIRST_FRAME))
            clips.isEmpty() && images.size == 2 -> listOf(
                PlacedSource(images[0], SourceRole.FIRST_FRAME),
                PlacedSource(images[1], SourceRole.LAST_FRAME),
            )
            else -> usable.map { source ->
                PlacedSource(source, if (source.video) SourceRole.REFERENCE_VIDEO else SourceRole.REFERENCE_IMAGE)
            }
        }
    }

    /** MiniMax image-to-video takes its shape from the photo, so the Ratio dial is only a fact. */
    fun frameSetsShape(sources: List<MakeSource>): Boolean {
        val roles = placeForVideo(sources).map { it.role }
        return roles.isNotEmpty() && roles.all { it == SourceRole.FIRST_FRAME || it == SourceRole.LAST_FRAME }
    }

    /** The line under the source row: what the next render will do with what is attached. */
    fun caption(kind: MakeKind, sources: List<MakeSource>, miniMax: Boolean): String {
        val usable = sources.filter { it.usable }
        if (usable.isEmpty()) {
            return if (kind == MakeKind.VIDEO) {
                "Add a photo or clip to start from, or describe the clip on its own."
            } else {
                "Add a photo or clip to edit, or describe the image on its own."
            }
        }
        if (kind == MakeKind.IMAGE) {
            return if (miniMax) {
                "MiniMax uses the first photo or clip as the subject."
            } else when {
                usable.size == 1 && usable[0].image -> "This photo is edited into the new image."
                usable.all { it.video } -> "The clip is sent as a still and edited into the new image."
                else -> "These are edited into the new image. A clip is sent as a still."
            }
        }
        val roles = placeForVideo(usable).map { it.role }
        return when {
            roles == listOf(SourceRole.FIRST_FRAME) ->
                if (miniMax) "This photo is the first frame. The clip follows its shape."
                else "This photo is the first frame of the clip."
            roles == listOf(SourceRole.FIRST_FRAME, SourceRole.LAST_FRAME) ->
                if (miniMax) "The first photo opens the clip and the second closes it. The clip follows the first photo's shape."
                else "The first photo opens the clip. The second closes it."
            else -> "These guide the clip. Photos and clips are references, not fixed frames."
        }
    }

    /** "2 photos, 1 clip" for the pill on a render that was sent with media. */
    fun labels(sources: List<MakeSource>): List<String> {
        val images = sources.count { it.image }
        val clips = sources.count { it.video }
        if (images == 0 && clips == 0) return emptyList()
        return listOf(
            buildList {
                if (images > 0) add(if (images == 1) "1 photo" else "$images photos")
                if (clips > 0) add(if (clips == 1) "1 clip" else "$clips clips")
            }.joinToString(", ")
        )
    }
}
