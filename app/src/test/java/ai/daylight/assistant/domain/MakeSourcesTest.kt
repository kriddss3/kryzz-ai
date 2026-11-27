package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MakeSourcesTest {
    private fun photo(id: String) = MakeSource(id, "$id.jpg", "image/jpeg", "/$id.jpg")
    private fun clip(id: String) = MakeSource(id, "$id.mp4", "video/mp4", "/$id.mp4")

    @Test fun onePhotoIsTheOpeningFrameAndTwoPhotosBookendTheClip() {
        assertThat(MakeSources.placeForVideo(listOf(photo("a"))).map { it.role })
            .containsExactly(SourceRole.FIRST_FRAME)
        assertThat(MakeSources.placeForVideo(listOf(photo("a"), photo("b"))).map { it.role })
            .containsExactly(SourceRole.FIRST_FRAME, SourceRole.LAST_FRAME).inOrder()
    }

    @Test fun aClipOrExtraPhotosAreReferences() {
        assertThat(MakeSources.placeForVideo(listOf(clip("v"))).single().role).isEqualTo(SourceRole.REFERENCE_VIDEO)
        val mixed = MakeSources.placeForVideo(listOf(photo("a"), clip("v")))
        assertThat(mixed.map { it.role }).containsExactly(SourceRole.REFERENCE_IMAGE, SourceRole.REFERENCE_VIDEO).inOrder()
        assertThat(MakeSources.placeForVideo(listOf(photo("a"), photo("b"), photo("c"))).map { it.role })
            .containsExactly(SourceRole.REFERENCE_IMAGE, SourceRole.REFERENCE_IMAGE, SourceRole.REFERENCE_IMAGE)
    }

    @Test fun captionsSayWhatTheNextRenderWillDo() {
        assertThat(MakeSources.caption(MakeKind.IMAGE, emptyList(), miniMax = false)).contains("edit")
        assertThat(MakeSources.caption(MakeKind.IMAGE, listOf(photo("a")), miniMax = false)).contains("edited")
        assertThat(MakeSources.caption(MakeKind.IMAGE, listOf(photo("a")), miniMax = true)).contains("subject")
        assertThat(MakeSources.caption(MakeKind.VIDEO, listOf(photo("a")), miniMax = false)).contains("first frame")
        assertThat(MakeSources.caption(MakeKind.VIDEO, listOf(photo("a")), miniMax = true)).contains("shape")
        assertThat(MakeSources.frameSetsShape(listOf(photo("a")))).isTrue()
        assertThat(MakeSources.frameSetsShape(listOf(clip("v")))).isFalse()
        assertThat(MakeSources.caption(MakeKind.VIDEO, listOf(photo("a"), photo("b")), miniMax = false)).contains("closes")
        assertThat(MakeSources.caption(MakeKind.VIDEO, listOf(clip("v")), miniMax = false)).contains("references")
    }

    @Test fun labelsCountPhotosAndClips() {
        assertThat(MakeSources.labels(emptyList())).isEmpty()
        assertThat(MakeSources.labels(listOf(photo("a"), photo("b"), clip("v")))).containsExactly("2 photos, 1 clip")
    }
}
