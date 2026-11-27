package ai.daylight.assistant

import ai.daylight.assistant.data.AttachmentStore
import ai.daylight.assistant.data.GeneratedOutputStore
import ai.daylight.assistant.data.MemoryRepository
import ai.daylight.assistant.data.local.AssistantDatabase
import ai.daylight.assistant.data.local.ConversationEntity
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.data.preferences.AppPreferences
import ai.daylight.assistant.data.remote.MiniMaxClient
import ai.daylight.assistant.data.remote.OpenRouterClient
import ai.daylight.assistant.data.remote.ParallelClient
import ai.daylight.assistant.domain.AgentExecutor
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.security.SecureCredentialStore
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v5.13.7: a whole Talk turn on a real phone, with the model played by a fake HTTP layer.
 * The "model" asks for seven files (PDF, PowerPoint, Excel, HTML, vCard, SQLite and a ZIP with
 * Word and JSON inside) and a PNG, which must be refused with a hint. The real executor,
 * Android's PDF and SQLite writers and the database run as in the app. A follow-up turn then
 * checks that the deck's source goes back to the model, so "add a slide" can edit it.
 */
@RunWith(AndroidJUnit4::class)
class TalkFilesEndToEndTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
    private val database = Room.inMemoryDatabaseBuilder(context, AssistantDatabase::class.java).build()
    private val requests = mutableListOf<String>()
    private val replies = ArrayDeque<String>()

    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests += Buffer().also { request.body?.writeTo(it) }.readUtf8()
        val payload = replies.removeFirstOrNull() ?: text("(no more scripted replies)")
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .header("Content-Type", "application/json")
            .body(payload.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    @After fun close() = database.close()

    private fun text(content: String) = buildJsonObject {
        putJsonArray("choices") {
            addJsonObject {
                putJsonObject("message") { put("role", "assistant"); put("content", content) }
                put("finish_reason", "stop")
            }
        }
    }.toString()

    private fun calls(vararg files: Pair<String, String>) = buildJsonObject {
        putJsonArray("choices") {
            addJsonObject {
                putJsonObject("message") {
                    put("role", "assistant")
                    put("content", "")
                    putJsonArray("tool_calls") {
                        files.forEachIndexed { index, (_, arguments) ->
                            addJsonObject {
                                put("id", "call_$index")
                                put("type", "function")
                                putJsonObject("function") { put("name", "create_artifact"); put("arguments", arguments) }
                            }
                        }
                    }
                }
                put("finish_reason", "tool_calls")
            }
        }
    }.toString()

    private fun file(name: String, content: String) = name to buildJsonObject {
        put("file_name", name); put("content", content)
    }.toString()

    @Test fun aTalkTurnMakesRealFilesOfManyKinds() = runBlocking {
        val credentials = SecureCredentialStore(context)
        if (credentials.openRouterKey() == null) credentials.setOpenRouterKey("sk-or-e2e-test")
        val dao = database.dao()
        val executor = AgentExecutor(
            dao, AppPreferences(context), credentials, OpenRouterClient(http, json), MiniMaxClient(http, json),
            ParallelClient(http, json), GeneratedOutputStore(context), AttachmentStore(context), MemoryRepository(dao), json
        )
        val conversationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertConversation(ConversationEntity(conversationId, "Files", now, now))
        dao.upsertMessage(MessageEntity(UUID.randomUUID().toString(), conversationId, "USER", "make me a pdf, slides and the rest", now - 2_000, mode = "CHAT"))

        val zip = "bundle.zip" to buildJsonObject {
            put("file_name", "bundle.zip"); put("content", "Notes and data")
            putJsonArray("files") {
                addJsonObject { put("path", "docs/notes.docx"); put("content", "# Notes\n\n- one\n- two") }
                addJsonObject { put("path", "data/points.json"); put("content", "{\"points\": [1, 2, 3]}") }
            }
        }.toString()
        replies += calls(
            file("report.pdf", "# Report\n\nŠis ir **tests** ar garumzīmēm.\n\n| A | B |\n|---|---|\n| 1 | 2 |"),
            file("pitch.pptx", "# Pitch\nOne line\n\n## Problem\n- Slow\n- Costly\n\n## Plan\n1. Build\n2. Ship"),
            file("budget.xlsx", "item,cost\nTea,2.5\nBread,1.2\nTotal,=SUM(B2:B3)"),
            file("index.html", "```html\n<!doctype html><title>Hi</title><h1>Hello</h1>\n```"),
            file("contacts.vcf", "BEGIN:VCARD\nVERSION:3.0\nFN:Anna\nEND:VCARD"),
            file("shop.sqlite", "CREATE TABLE item (id INTEGER PRIMARY KEY, name TEXT);\nINSERT INTO item (name) VALUES ('Tea');"),
            zip,
            file("logo.png", "not a picture")
        )
        replies += text("Done. Tap a card to open a file.")

        val answer = executor.generateReply(conversationId, requestedMode = AssistantMode.CHAT)
        assertEquals("Done. Tap a card to open a file.", answer)

        // Talk offered the file tool with the file-name schema, and told the model about it.
        val first = requests.first()
        assertTrue(first.contains("\"name\":\"create_artifact\""))
        assertTrue(first.contains("\"file_name\""))
        assertTrue(first.contains("You have tools; use them"))
        assertFalse(first.contains("\"name\":\"update_plan\""))
        // The PNG was refused with a way forward, fed back to the model.
        assertTrue(requests[1].contains("generate_image"))

        val reply = dao.messages(conversationId).last { it.role == "ASSISTANT" }
        val outputs = json.decodeFromString<List<GeneratedOutput>>(reply.outputsJson)
        val byName = outputs.associateBy { it.fileName }
        assertEquals(
            setOf("report.pdf", "pitch.pptx", "budget.xlsx", "index.html", "contacts.vcf", "shop.sqlite", "bundle.zip"),
            byName.keys
        )
        fun bytes(name: String): ByteArray {
            val path = byName.getValue(name).localPath!!
            Log.i("TalkFilesE2E", "$name -> $path")
            assertTrue(path.endsWith("/$name"))
            return File(path).readBytes()
        }
        assertTrue(String(bytes("report.pdf").copyOf(5)) == "%PDF-")
        listOf("pitch.pptx", "budget.xlsx", "bundle.zip").forEach { assertTrue(it, String(bytes(it).copyOf(2)) == "PK") }
        assertTrue(String(bytes("shop.sqlite").copyOf(15)) == "SQLite format 3")
        assertEquals("<!doctype html><title>Hi</title><h1>Hello</h1>", String(bytes("index.html")))
        assertTrue(String(bytes("contacts.vcf")).contains("\r\nFN:Anna\r\n"))
        assertEquals(OutputKind.PRESENTATION, byName.getValue("pitch.pptx").kind)
        assertEquals(OutputKind.TEXT, byName.getValue("index.html").kind)
        assertEquals(OutputKind.ARCHIVE, byName.getValue("bundle.zip").kind)
        ZipFile(byName.getValue("bundle.zip").localPath!!).use { archive ->
            val names = archive.entries().asSequence().map { it.name }.toSet()
            assertEquals(setOf("docs/notes.docx", "data/points.json"), names)
            val docx = archive.getInputStream(archive.getEntry("docs/notes.docx")).readBytes()
            assertTrue(String(docx.copyOf(2)) == "PK")
        }

        // Follow-up: the latest files' source goes back to the model for edits.
        dao.upsertMessage(MessageEntity(UUID.randomUUID().toString(), conversationId, "USER", "add a slide about the team", System.currentTimeMillis(), mode = "CHAT"))
        replies += text("Sure.")
        executor.generateReply(conversationId, requestedMode = AssistantMode.CHAT)
        val followUp = requests.last()
        assertTrue(followUp.contains("--- pitch.pptx (source as written"))
        assertTrue(followUp.contains("## Problem"))
    }
}
