package com.dodocomm.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateManifestTest {

    private val sha = "a".repeat(64)

    private fun manifest(versionCode: Int = 13, versionName: String = "1.6.0"): String =
        """{"versionCode":$versionCode,"versionName":"$versionName","apkUrl":"https://example.com/DodoComm.apk","sha256":"$sha","size":123,"changelog":"fixes"}"""

    @Test
    fun `newer version is parsed`() {
        val info = UpdateManifest.parse(manifest(), currentVersionCode = 12)!!
        assertEquals(13, info.versionCode)
        assertEquals("1.6.0", info.versionName)
        assertEquals(sha, info.sha256)
        assertEquals(123L, info.size)
        assertEquals("fixes", info.changelog)
    }

    @Test
    fun `same or older version is not an update`() {
        assertNull(UpdateManifest.parse(manifest(versionCode = 12), currentVersionCode = 12))
        assertNull(UpdateManifest.parse(manifest(versionCode = 11), currentVersionCode = 12))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid hash is rejected`() {
        UpdateManifest.parse(manifest().replace(sha, "not-a-hash"), currentVersionCode = 12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid url is rejected`() {
        UpdateManifest.parse(
            manifest().replace("https://example.com/DodoComm.apk", "file:///tmp/x.apk"),
            currentVersionCode = 12,
        )
    }
}
