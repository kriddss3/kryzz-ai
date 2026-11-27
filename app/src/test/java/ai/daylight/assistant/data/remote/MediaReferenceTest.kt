package ai.daylight.assistant.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

/** OpenRouter image and video requests omit empty media and keep a reference when one is set. */
class MediaReferenceTest {
    private val appJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    @Test fun anImageRequestWithoutAReferenceStaysTextOnly() {
        val encoded = appJson.encodeToString(ImageGenerationRequest(model = "google/gemini", prompt = "a lantern"))
        assertThat(encoded).doesNotContain("input_references")
    }

    @Test fun anImageRequestCarriesThePhotoToEdit() {
        val encoded = appJson.encodeToString(
            ImageGenerationRequest(
                model = "google/gemini",
                prompt = "make it night",
                inputReferences = listOf(
                    OpenRouterInputReference(type = "image_url", imageUrl = OpenRouterMediaUrl("data:image/jpeg;base64,abc"))
                ),
            )
        )
        assertThat(encoded).contains("\"input_references\"")
        assertThat(encoded).contains("\"type\":\"image_url\"")
        assertThat(encoded).contains("data:image/jpeg;base64,abc")
    }

    @Test fun aVideoRequestUsesAFrameOrAClipButNotBothFieldsAtOnce() {
        val frame = appJson.encodeToString(
            VideoGenerationRequest(
                model = "google/veo",
                prompt = "she turns",
                aspectRatio = "16:9",
                frameImages = listOf(
                    OpenRouterFrameImage(
                        type = "image_url",
                        imageUrl = OpenRouterMediaUrl("data:image/png;base64,abc"),
                        frameType = "first_frame",
                    )
                ),
            )
        )
        assertThat(frame).contains("\"frame_images\"")
        assertThat(frame).contains("\"frame_type\":\"first_frame\"")
        assertThat(frame).doesNotContain("input_references")

        val clip = appJson.encodeToString(
            VideoGenerationRequest(
                model = "google/veo",
                prompt = "continue this",
                inputReferences = listOf(
                    OpenRouterInputReference(type = "video_url", videoUrl = OpenRouterMediaUrl("data:video/mp4;base64,abc"))
                ),
            )
        )
        assertThat(clip).contains("\"video_url\"")
        assertThat(clip).doesNotContain("frame_images")
        assertThat(clip).doesNotContain("image_url")
    }
}
