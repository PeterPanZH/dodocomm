package com.dodocomm.app.core.backup

import android.content.Context
import android.net.Uri
import com.dodocomm.app.core.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads/writes encrypted configuration backups through a user-picked SAF URI. */
class BackupManager(private val context: Context) {
    private val deviceKeystore = DeviceKeystore()

    suspend fun export(uri: Uri, password: String, settings: AppSettings) = withContext(Dispatchers.IO) {
        val payload = SettingsBackupCodec.toJson(settings)
        val bytes = BackupCodec.encode(payload, password) { dek -> deviceKeystore.wrap(dek) }
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("无法打开备份文件")
        bytes
    }

    suspend fun inspect(uri: Uri): BackupInfo = withContext(Dispatchers.IO) {
        BackupCodec.info(readBytes(uri))
    }

    suspend fun importWithPassword(uri: Uri, password: String): AppSettings = withContext(Dispatchers.IO) {
        val raw = BackupCodec.decodeWithPassword(readBytes(uri), password)
        SettingsBackupCodec.fromJson(raw, currentSettings())
    }

    suspend fun importWithDeviceKey(uri: Uri): AppSettings = withContext(Dispatchers.IO) {
        val raw = BackupCodec.decodeWithDeviceWrapper(readBytes(uri)) { iv, ct ->
            deviceKeystore.unwrap(iv, ct)
        }
        SettingsBackupCodec.fromJson(raw, currentSettings())
    }

    private fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取备份文件")

    private fun currentSettings(): AppSettings =
        (context.applicationContext as com.dodocomm.app.Sms2BarkApp).container.settings.current()

    companion object {
        fun defaultFileName(): String = "dodocomm-backup-" + java.time.LocalDate.now() + ".dodocomm-backup"
    }
}
