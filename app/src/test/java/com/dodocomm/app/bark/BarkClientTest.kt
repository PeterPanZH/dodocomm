package com.dodocomm.app.bark

import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.settings.CopyMode
import com.dodocomm.app.core.settings.PushLevel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the Bark API contract against a real local HTTP server
 * (`mockwebserver3`): V2 POST payload, success/failure parsing, GET fallback
 * for legacy servers and the unconfigured fast-fail.
 */
class BarkClientTest {

    private lateinit var server: MockWebServer
    private val client = BarkClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun configuredSettings(
        serverUrl: String = server.url("/").toString(),
        deviceKey: String = "device-key-1",
    ): AppSettings = AppSettings(
        serverUrl = serverUrl,
        deviceKey = deviceKey,
        pushGroup = "短信",
    )

    private fun takeRequest(): mockwebserver3.RecordedRequest =
        checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "no request reached MockWebServer" }

    @Test
    fun `post success parses bark code and sends expected json`() = runBlocking {
        server.enqueue(MockResponse(code = 200, body = """{"code":200,"message":"success"}"""))

        val result = client.send(configuredSettings(), "来自 95588", "验证码 123456")

        assertTrue(result.success)
        assertEquals(200, result.code)
        assertEquals("推送成功", result.message)
        assertTrue(result.rawBody!!.contains("success"))

        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/push", request.url.encodedPath)
        val json = JSONObject(checkNotNull(request.body).utf8())
        assertEquals("device-key-1", json.getString("device_key"))
        assertEquals("来自 95588", json.getString("title"))
        assertEquals("验证码 123456", json.getString("body"))
        assertEquals("短信", json.getString("group"))
        assertEquals("active", json.getString("level"))
        assertEquals("1", json.getString("isArchive"))
        assertFalse(json.has("sound"))
        assertFalse(json.has("autoCopy"))
        assertFalse(json.has("badge"))
    }

    @Test
    fun `code copy mode sends only the extracted verification code`() = runBlocking {
        server.enqueue(MockResponse(code = 200, body = """{"code":200}"""))
        val settings = configuredSettings().copy(copyMode = CopyMode.CODE)
        client.send(settings, "来自 95588", "【银行】验证码 123456，5 分钟内有效")
        val json = JSONObject(checkNotNull(takeRequest().body).utf8())
        assertEquals("123456", json.getString("copy"))
        assertFalse(json.has("autoCopy"))
    }

    @Test
    fun `optional style fields are mapped when enabled`() = runBlocking {
        server.enqueue(MockResponse(code = 200, body = """{"code":200}"""))
        val settings = configuredSettings().copy(
            pushSound = "bell",
            pushLevel = PushLevel.TIME_SENSITIVE,
            pushIcon = "https://example.com/icon.png",
            pushUrl = "https://example.com",
            isArchive = false,
            copyMode = CopyMode.FULL,
            badgeEnabled = true,
        )

        assertTrue(client.send(settings, "t", "b").success)

        val json = JSONObject(checkNotNull(takeRequest().body).utf8())
        assertEquals("bell", json.getString("sound"))
        assertEquals("timeSensitive", json.getString("level"))
        assertEquals("https://example.com/icon.png", json.getString("icon"))
        assertEquals("https://example.com", json.getString("url"))
        assertEquals("1", json.getString("autoCopy"))
        assertEquals(1, json.getInt("badge"))
        assertFalse(json.has("isArchive"))
    }

    @Test
    fun `http 2xx without code field counts as success`() = runBlocking {
        server.enqueue(MockResponse(code = 200, body = "ok"))
        val result = client.send(configuredSettings(), "t", "b")
        assertTrue(result.success)
        assertEquals(200, result.code)
    }

    @Test
    fun `bark failure code inside http 200 is reported`() = runBlocking {
        server.enqueue(
            MockResponse(code = 200, body = """{"code":400,"message":"failed to get device token"}"""),
        )
        val result = client.send(configuredSettings(), "t", "b")
        assertFalse(result.success)
        assertEquals(400, result.code)
        assertTrue(result.message.contains("failed to get device token"))
    }

    @Test
    fun `http server error becomes readable chinese message`() = runBlocking {
        server.enqueue(MockResponse(code = 500, body = "upstream exploded"))
        val result = client.send(configuredSettings(), "t", "b")
        assertFalse(result.success)
        assertEquals(500, result.code)
        assertTrue(result.message.contains("500"))
        assertTrue(result.message.contains("服务器"))
    }

    @Test
    fun `http 404 falls back to legacy get route with encoded segments`() = runBlocking {
        server.enqueue(MockResponse(code = 404, body = """{"code":404,"message":"not found"}"""))
        server.enqueue(MockResponse(code = 200, body = """{"code":200,"message":"success"}"""))
        val settings = configuredSettings().copy(isArchive = true)

        val result = client.send(settings, "来自 95588", "验证码 123456")

        assertTrue(result.success)
        val post = takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/push", post.url.encodedPath)

        val get = takeRequest()
        assertEquals("GET", get.method)
        assertEquals(
            listOf("device-key-1", "来自 95588", "验证码 123456"),
            get.url.pathSegments,
        )
        assertEquals("短信", get.url.queryParameter("group"))
        assertEquals("active", get.url.queryParameter("level"))
        assertEquals("1", get.url.queryParameter("isArchive"))
        assertNull(get.url.queryParameter("sound"))
        assertNull(get.url.queryParameter("autoCopy"))
    }

    @Test
    fun `unconfigured settings fail fast without network`() = runBlocking {
        val result = client.send(configuredSettings(deviceKey = ""), "t", "b")
        assertFalse(result.success)
        assertTrue(result.message.contains("未配置"))
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `invalid server url is rejected with readable message`() = runBlocking {
        val result = client.send(
            AppSettings(serverUrl = "http://not a valid host", deviceKey = "k"),
            "t",
            "b",
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("地址无效"))
    }
}
