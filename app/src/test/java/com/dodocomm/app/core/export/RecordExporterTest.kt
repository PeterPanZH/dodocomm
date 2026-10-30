package com.dodocomm.app.core.export

import com.dodocomm.app.core.model.ForwardRecord
import com.dodocomm.app.core.model.ForwardStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordExporterTest {

    private val now = System.currentTimeMillis()

    private fun record(
        sender: String = "10086",
        body: String = "验证码 123456",
        at: Long = now,
    ) = ForwardRecord(
        id = 1,
        sender = sender,
        body = body,
        receivedAt = at,
        createdAt = at,
        status = ForwardStatus.SENT,
    )

    @Test
    fun `csv includes header, escaping and BOM`() {
        val csv = RecordExporter.toCsv(
            listOf(record(sender = "95588", body = "金额 =100, 验证码 123456\n第二行")),
            masked = false,
        )
        assertTrue(csv.startsWith("\uFEFFreceived_at,"))
        assertTrue(csv.contains("\"金额 =100, 验证码 123456\n第二行\""))
        assertTrue(csv.contains("\"95588\""))
    }

    @Test
    fun `json keeps verification code while masking phone numbers`() {
        val json = RecordExporter.toJson(
            listOf(record(sender = "13800138000", body = "手机号 13800138000 收到验证码 123456")),
            masked = true,
        )
        val obj = JSONObject(json).getJSONArray("records").getJSONObject(0)
        assertEquals("138******00", obj.getString("sender"))
        assertTrue(obj.getString("body").contains("123456"))
        assertFalse(obj.getString("body").contains("13800138000"))
    }

    @Test
    fun `range filter excludes old records`() {
        val old = record(at = now - 40L * 86_400_000L)
        val fresh = record(at = now - 2L * 86_400_000L)
        val filtered = RecordExporter.filter(listOf(old, fresh), ExportRange.LAST_7_DAYS)
        assertEquals(1, filtered.size)
        assertEquals(fresh.id, filtered.first().id)
    }

    @Test
    fun `short sender is fully masked`() {
        assertEquals("****", RecordExporter.maskSender("1008"))
        assertEquals("100*86", RecordExporter.maskSender("10086"))
    }
}
