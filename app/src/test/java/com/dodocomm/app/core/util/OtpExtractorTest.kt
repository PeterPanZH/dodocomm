package com.dodocomm.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractorTest {

    @Test
    fun `extracts numeric code after keyword`() {
        assertEquals("123456", OtpExtractor.extract("【中国移动】您的验证码是 123456，5 分钟内有效"))
        assertEquals("8899", OtpExtractor.extract("校验码：8899"))
        assertEquals("A1B2C3", OtpExtractor.extract("Your verification code: A1B2C3"))
    }

    @Test
    fun `extracts code before keyword`() {
        assertEquals("456789", OtpExtractor.extract("456789 是本次登录验证码，请勿泄露"))
    }

    @Test
    fun `does not extract unrelated numbers`() {
        assertNull(OtpExtractor.extract("您的订单 123456 已发货"))
        assertNull(OtpExtractor.extract("余额 8888 元"))
        assertNull(OtpExtractor.extract(""))
    }
}
