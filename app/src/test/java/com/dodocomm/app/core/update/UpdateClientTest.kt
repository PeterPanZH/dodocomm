package com.dodocomm.app.core.update

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateClientTest {

    private lateinit var server: MockWebServer
    private val client = UpdateClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun info(url: String, bytes: ByteArray) = UpdateInfo(
        versionCode = 14,
        versionName = "1.6.1",
        apkUrl = url,
        sha256 = sha256(bytes),
        size = bytes.size.toLong(),
        changelog = "test",
    )

    @Test
    fun `manifest fetch returns newer update and skips older`() = runBlocking {
        val bytes = "apk".toByteArray()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"versionCode":14,"versionName":"1.6.1","apkUrl":"${server.url("/apk")}","sha256":"${sha256(bytes)}","size":3,"changelog":"c"}""",
            ),
        )
        val update = client.fetch(server.url("/latest.json").toString(), currentVersionCode = 13)
        assertEquals("1.6.1", update!!.versionName)
        assertEquals(sha256(bytes), update.sha256)

        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"versionCode":13,"versionName":"1.6.0","apkUrl":"${server.url("/apk")}","sha256":"${sha256(bytes)}","size":3}""",
            ),
        )
        assertNull(client.fetch(server.url("/latest.json").toString(), currentVersionCode = 13))
    }

    @Test
    fun `download verifies hash and reports progress`() = runBlocking {
        val bytes = "DodoComm-APK-bytes".repeat(100).toByteArray()
        val url = server.url("/DodoComm-1.6.1.apk").toString()
        server.enqueue(MockResponse(code = 200, body = String(bytes)))

        val destination = File.createTempFile("dodocomm-update", ".apk").apply { delete() }
        var lastProgress = -1
        val downloaded = client.download(info(url, bytes), destination) { lastProgress = it }

        assertTrue(downloaded.exists())
        assertEquals(sha256(bytes), sha256(downloaded.readBytes()))
        assertEquals(100, lastProgress)
        downloaded.delete()
        Unit
    }

    @Test
    fun `download rejects a hash mismatch and deletes the file`() = runBlocking {
        val expected = "expected".toByteArray()
        val actual = "tampered".toByteArray()
        val url = server.url("/bad.apk").toString()
        server.enqueue(MockResponse(code = 200, body = String(actual)))

        val destination = File.createTempFile("dodocomm-bad-update", ".apk").apply { delete() }
        val error = runCatching {
            client.download(info(url, expected), destination) { }
        }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
        assertFalse(destination.exists())
    }
}
