package com.dodocomm.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetHostTest {
    @Test
    fun `detects ColorOS and its brand variants`() {
        listOf(
            Triple("OnePlus", "OnePlus", ""),
            Triple("OPPO", "OPPO", ""),
            Triple("realme", "realme", ""),
            Triple("OnePlus", "OnePlus", "V17.0.0"),
        ).forEach { (manufacturer, brand, rom) ->
            assertEquals(
                WidgetHostFamily.COLOR_OS,
                WidgetHost.familyOf(manufacturer, brand, rom),
            )
        }
    }

    @Test
    fun `detects Huawei Honor Xiaomi and vivo families`() {
        assertEquals(WidgetHostFamily.HARMONY_OS, WidgetHost.familyOf("HUAWEI", "HUAWEI", ""))
        assertEquals(WidgetHostFamily.MAGIC_OS, WidgetHost.familyOf("HONOR", "HONOR", ""))
        assertEquals(WidgetHostFamily.HYPER_OS, WidgetHost.familyOf("Xiaomi", "Redmi", ""))
        assertEquals(WidgetHostFamily.HYPER_OS, WidgetHost.familyOf("Xiaomi", "POCO", ""))
        assertEquals(WidgetHostFamily.ORIGIN_OS, WidgetHost.familyOf("vivo", "iQOO", ""))
        assertEquals(WidgetHostFamily.ONE_UI, WidgetHost.familyOf("samsung", "samsung", ""))
        assertEquals(WidgetHostFamily.STOCK, WidgetHost.familyOf("Google", "google", ""))
    }

    @Test
    fun `OEM card hosts use neutral card and tighter inset`() {
        val coloros = WidgetHost.profileOf("OnePlus", "OnePlus", "V17")
        val harmony = WidgetHost.profileOf("HUAWEI", "HUAWEI", "")
        val xiaomi = WidgetHost.profileOf("Xiaomi", "Xiaomi", "")
        val vivo = WidgetHost.profileOf("vivo", "vivo", "")
        val stock = WidgetHost.profileOf("Google", "google", "")

        listOf(coloros, harmony, xiaomi, vivo).forEach { profile ->
            assertTrue(profile.hostCard)
            assertTrue(profile.outerInsetDp <= 2)
        }
        assertFalse(stock.hostCard)
        assertEquals(4, stock.outerInsetDp)
    }
}
