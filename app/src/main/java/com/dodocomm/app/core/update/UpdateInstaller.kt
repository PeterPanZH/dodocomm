package com.dodocomm.app.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/** Verifies and hands a downloaded APK to the system package installer. */
object UpdateInstaller {

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /** True when [apkFile] is signed by the same certificate as the running app. */
    fun verifyApkSignature(context: Context, apkFile: File): Boolean = try {
        val pm = context.packageManager
        val current = packageInfo(pm, context.packageName, 0) ?: return false
        val archive = packageInfo(pm, apkFile.absolutePath, PackageManager.GET_ACTIVITIES) ?: return false
        val currentSigners = signerDigests(current)
        val archiveSigners = signerDigests(archive)
        currentSigners.isNotEmpty() && currentSigners == archiveSigners
    } catch (_: Throwable) {
        false
    }

    /**
     * Launches the system installer. Returns false when the user still has to
     * grant the "install unknown apps" permission.
     */
    fun install(context: Context, apkFile: File): Boolean {
        if (!canInstall(context)) {
            openInstallPermissionSettings(context)
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("apk", uri)
        }
        context.startActivity(intent)
        return true
    }

    private fun packageInfo(pm: PackageManager, path: String, extraFlags: Int): PackageInfo? {
        val flags = extraFlags or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        return if (path.contains('/') || path.endsWith(".apk")) {
            pm.getPackageArchiveInfo(path, flags)
        } else {
            pm.getPackageInfo(path, flags)
        }
    }

    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty()
        }
        return signatures
            .filterNotNull()
            .map { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }
            .toSet()
    }
}
