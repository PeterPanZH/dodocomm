package com.dodocomm.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.dodocomm.app.BuildConfig
import com.dodocomm.app.appContainer
import com.dodocomm.app.core.model.BarkResult
import com.dodocomm.app.core.channel.ChannelType
import com.dodocomm.app.core.channel.PushChannel
import com.dodocomm.app.core.export.ExportFormat
import com.dodocomm.app.core.export.ExportRange
import com.dodocomm.app.core.security.AppLock
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.settings.AppLanguage
import com.dodocomm.app.core.settings.CopyMode
import com.dodocomm.app.core.settings.PushLevel
import com.dodocomm.app.core.settings.ThemeMode
import com.dodocomm.app.core.update.UpdateDownloadNotifier
import com.dodocomm.app.core.update.UpdateInstaller
import com.dodocomm.app.ui.model.HistoryFilter
import com.dodocomm.app.ui.model.PermissionState
import com.dodocomm.app.ui.model.SnackbarEvent
import com.dodocomm.app.ui.model.TestPushState
import com.dodocomm.app.ui.model.UpdateState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Thin UI-facing wrapper around [com.dodocomm.app.AppContainer].
 *
 * The container owns all state and does all IO; this view model only exposes
 * it to Compose, refreshes cheap system probes on resume, and turns one-shot
 * results into snackbar events. All heavy work stays off the main thread.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val appContext: Context get() = getApplication<Application>().applicationContext

    val settings: StateFlow<AppSettings> = container.settings.settings
    val records = container.history.records
    val stats = container.history.stats

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _testPushState = MutableStateFlow<TestPushState>(TestPushState.Idle)
    val testPushState: StateFlow<TestPushState> = _testPushState.asStateFlow()

    private val _templateTestState = MutableStateFlow<TestPushState>(TestPushState.Idle)
    val templateTestState: StateFlow<TestPushState> = _templateTestState.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _updateDialogVisible = MutableStateFlow(false)
    val updateDialogVisible: StateFlow<Boolean> = _updateDialogVisible.asStateFlow()

    private var autoUpdateCheckStarted = false
    private var updateDownloadJob: Job? = null

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    /** Set when another screen asks the history screen to open a record's detail. */
    private val _openRecordRequest = MutableStateFlow(-1L)
    val openRecordRequest: StateFlow<Long> = _openRecordRequest.asStateFlow()

    private val _events = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SnackbarEvent> = _events.asSharedFlow()

    init {
        refreshSystemState()
        maybeAutoCheckUpdates()
    }

    // -- system state -------------------------------------------------------

    /** Re-probes permissions, battery exemption and foreground service state. */
    fun refreshSystemState() {
        _serviceRunning.value = container.serviceController.isServiceRunning()
        _permissions.value = PermissionState.read(appContext)
    }

    /** Called whenever the activity becomes visible again. */
    fun onResume() {
        ensureServiceRunning()
        container.history.refresh()
        maybeAutoCheckUpdates()
    }

    // -- in-app updates -----------------------------------------------------

    /**
     * Automatic checks are throttled in [UpdatePrefs]: once per 24 h on
     * success, with exponential backoff (1 h → 2 h → … → 24 h) after failures.
     * Runs at most once per app process.
     */
    private fun maybeAutoCheckUpdates() {
        if (autoUpdateCheckStarted) return
        if (!container.settings.current().autoUpdateCheck) return
        if (!container.updatePrefs.autoCheckDue()) return
        autoUpdateCheckStarted = true
        checkForUpdates(manual = false)
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        updateSettings { it.copy(autoUpdateCheck = enabled) }
        if (enabled) {
            autoUpdateCheckStarted = false
            maybeAutoCheckUpdates()
        }
        postSnackbar(if (enabled) "已开启自动检查更新（每天一次）" else "已关闭自动检查更新")
    }

    /** Manual checks bypass the interval/backoff schedule. */
    fun checkForUpdates(manual: Boolean = true) {
        val current = _updateState.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        if (manual) {
            _updateState.value = UpdateState.Checking
            _updateDialogVisible.value = false
        }
        viewModelScope.launch {
            val result = runCatching {
                container.updateClient.fetch(BuildConfig.UPDATE_MANIFEST_URL, BuildConfig.VERSION_CODE)
            }
            result.fold(
                onSuccess = { info ->
                    container.updatePrefs.recordSuccess()
                    if (info != null) {
                        _updateState.value = UpdateState.Available(info)
                        if (manual) {
                            _updateDialogVisible.value = true
                        } else {
                            postSnackbar("发现新版本 ${info.versionName}，可在「设置 → 检查更新」中查看")
                        }
                    } else {
                        _updateState.value = UpdateState.UpToDate
                        if (manual) postSnackbar("已是最新版本")
                    }
                },
                onFailure = { error ->
                    container.updatePrefs.recordFailure()
                    if (manual) {
                        _updateState.value = UpdateState.Error(error.readableUpdateError())
                        _updateDialogVisible.value = true
                        postSnackbar("检查更新失败：${error.readableUpdateError()}", isError = true)
                    } else {
                        _updateState.value = UpdateState.Idle
                    }
                },
            )
        }
    }

    fun showUpdateDialog() {
        if (_updateState.value is UpdateState.Available ||
            _updateState.value is UpdateState.Error ||
            _updateState.value is UpdateState.Downloading ||
            _updateState.value is UpdateState.Ready
        ) {
            _updateDialogVisible.value = true
        }
    }

    fun dismissUpdateDialog() {
        _updateDialogVisible.value = false
        if (_updateState.value is UpdateState.Error) _updateState.value = UpdateState.Idle
    }

    /** Downloads, verifies (SHA-256 + signing certificate) and installs the APK. */
    fun downloadAndInstallUpdate() = startUpdateDownload(background = false)

    /** Starts a download that keeps going after the dialog is dismissed. */
    fun downloadUpdateInBackground() = startUpdateDownload(background = true)

    private fun startUpdateDownload(background: Boolean) {
        if (updateDownloadJob?.isActive == true) return
        val available = _updateState.value as? UpdateState.Available ?: return
        val info = available.info
        UpdateDownloadNotifier.cancel(appContext)
        updateDownloadJob = viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(info, 0)
            if (background) {
                _updateDialogVisible.value = false
                UpdateDownloadNotifier.showProgress(appContext, info.versionName, 0)
            }
            val destination = File(appContext.cacheDir, "updates/DodoComm-${info.versionName}.apk")
            try {
                val apk = container.updateClient.download(info, destination) { percent ->
                    _updateState.value = UpdateState.Downloading(info, percent)
                    if (background) {
                        UpdateDownloadNotifier.showProgress(appContext, info.versionName, percent)
                    }
                }
                when {
                    !UpdateInstaller.verifyApkSignature(appContext, apk) -> {
                        _updateState.value = UpdateState.Error("更新包签名与当前应用不一致，已拒绝安装")
                        _updateDialogVisible.value = true
                        UpdateDownloadNotifier.cancel(appContext)
                    }
                    background -> {
                        _updateState.value = UpdateState.Ready(info, apk.absolutePath)
                        _updateDialogVisible.value = false
                        UpdateDownloadNotifier.showReady(appContext, info.versionName, apk.absolutePath)
                        postSnackbar("更新已在后台下载完成，点按通知安装")
                    }
                    !UpdateInstaller.install(appContext, apk) -> {
                        _updateState.value = UpdateState.Error("请允许本应用安装未知来源应用，然后重试")
                        _updateDialogVisible.value = true
                        postSnackbar("需要「安装未知应用」权限才能继续更新", isError = true)
                    }
                    else -> {
                        _updateDialogVisible.value = false
                        _updateState.value = UpdateState.Idle
                        postSnackbar("更新包已下载，请按系统提示完成安装")
                    }
                }
            } catch (cancel: CancellationException) {
                UpdateDownloadNotifier.cancel(appContext)
                throw cancel
            } catch (error: Throwable) {
                UpdateDownloadNotifier.cancel(appContext)
                if (_updateState.value is UpdateState.Downloading) {
                    _updateState.value = UpdateState.Error(error.readableUpdateError())
                    _updateDialogVisible.value = true
                }
            } finally {
                updateDownloadJob = null
            }
        }
    }

    /** Opens the installer for a background-downloaded APK. */
    fun installReadyUpdate() {
        val ready = _updateState.value as? UpdateState.Ready ?: return
        val file = File(ready.apkPath)
        when {
            !file.exists() -> {
                _updateState.value = UpdateState.Error("更新包不存在，请重新下载")
                _updateDialogVisible.value = true
            }
            !UpdateInstaller.verifyApkSignature(appContext, file) -> {
                _updateState.value = UpdateState.Error("更新包签名与当前应用不一致，已拒绝安装")
                _updateDialogVisible.value = true
            }
            !UpdateInstaller.install(appContext, file) -> {
                _updateState.value = UpdateState.Error("请允许本应用安装未知来源应用，然后重试")
                _updateDialogVisible.value = true
            }
            else -> {
                UpdateDownloadNotifier.cancel(appContext)
                _updateDialogVisible.value = false
            }
        }
    }

    /** Cancels an in-flight APK download and returns to the available state. */
    fun cancelUpdateDownload() {
        val info = (_updateState.value as? UpdateState.Downloading)?.info ?: return
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        UpdateDownloadNotifier.cancel(appContext)
        _updateState.value = UpdateState.Available(info)
        _updateDialogVisible.value = false
        postSnackbar("已取消下载")
    }

    private fun Throwable.readableUpdateError(): String =
        message?.trim()?.ifEmpty { javaClass.simpleName } ?: javaClass.simpleName

    /**
     * Starts the persistent service when forwarding is enabled. Safe to call
     * often: it no-ops when the service is already running or forwarding is off.
     * @return whether the foreground service is running afterwards.
     */
    fun ensureServiceRunning(): Boolean {
        val current = container.settings.current()
        if (current.forwardingEnabled) {
            container.serviceController.startIfEnabled()
        }
        refreshSystemState()
        return _serviceRunning.value
    }

    // -- forwarding ---------------------------------------------------------

    fun setForwardingEnabled(enabled: Boolean) {
        container.serviceController.setForwardingEnabled(enabled)
        if (enabled) {
            ensureServiceRunning()
            container.history.refresh()
        } else {
            refreshSystemState()
        }
        // The foreground service may start asynchronously; probe again shortly.
        viewModelScope.launch {
            delay(500)
            refreshSystemState()
        }
    }

    // -- settings -----------------------------------------------------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val previous = container.settings.current()
        val updated = container.settings.update(transform)
        val serviceRelevant = updated.forwardingEnabled != previous.forwardingEnabled ||
            updated.isConfigured != previous.isConfigured ||
            updated.foregroundServiceEnabled != previous.foregroundServiceEnabled
        if (serviceRelevant) {
            ensureServiceRunning()
        }
    }

    fun setServerUrl(value: String) {
        updateSettings { it.copy(serverUrl = value.trim()) }
        _testPushState.value = TestPushState.Idle
    }

    fun setDeviceKey(value: String) {
        updateSettings { it.copy(deviceKey = value.trim()) }
        _testPushState.value = TestPushState.Idle
    }

    fun setDeviceName(value: String) = updateSettings { it.copy(deviceName = value.trim()) }

    fun setTitleTemplate(value: String) {
        updateSettings { it.copy(titleTemplate = value) }
        _templateTestState.value = TestPushState.Idle
    }

    fun setBodyTemplate(value: String) {
        updateSettings { it.copy(bodyTemplate = value) }
        _templateTestState.value = TestPushState.Idle
    }

    fun setPushGroup(value: String) = updateSettings { it.copy(pushGroup = value.trim()) }

    fun setPushSound(value: String) = updateSettings { it.copy(pushSound = value.trim()) }

    fun setPushIcon(value: String) = updateSettings { it.copy(pushIcon = value.trim()) }

    fun setPushUrl(value: String) = updateSettings { it.copy(pushUrl = value.trim()) }

    fun setPushLevel(level: PushLevel) = updateSettings { it.copy(pushLevel = level) }

    // -- push channels ------------------------------------------------------

    fun addChannel(type: ChannelType): PushChannel {
        val existing = container.settings.current().channels.firstOrNull { it.type == type && type == ChannelType.BARK }
        if (existing != null) return existing
        val channel = PushChannel.create(type)
        updateSettings { it.copy(channels = it.channels + channel) }
        return channel
    }

    fun updateChannel(channel: PushChannel) {
        updateSettings { current ->
            current.copy(channels = current.channels.map { if (it.id == channel.id) channel else it })
        }
    }

    fun setChannelEnabled(id: String, enabled: Boolean) {
        updateSettings { current ->
            current.copy(
                channels = current.channels.map {
                    if (it.id == id) it.copy(enabled = enabled) else it
                },
            )
        }
    }

    fun removeChannel(id: String) {
        val channel = container.settings.current().channels.firstOrNull { it.id == id } ?: return
        if (channel.type == ChannelType.BARK) return
        updateSettings { current ->
            current.copy(channels = current.channels.filterNot { it.id == id })
        }
    }

    suspend fun testChannel(id: String): com.dodocomm.app.core.channel.ChannelSendResult {
        val current = container.settings.current()
        val channel = current.channels.firstOrNull { it.id == id }
            ?: return com.dodocomm.app.core.channel.ChannelSendResult(
                channelId = id,
                channelName = "通道",
                type = ChannelType.BARK,
                success = false,
                message = "通道不存在",
            )
        return container.pushDispatcher.sendTo(
            settings = current,
            channel = channel,
            title = "DodoComm 通道测试",
            body = "这是一条测试推送：${channel.name}",
        )
    }

    fun setSenderWhitelist(values: List<String>) =
        updateSettings { it.copy(senderWhitelist = values.distinct()) }

    fun setSenderBlacklist(values: List<String>) =
        updateSettings { it.copy(senderBlacklist = values.distinct()) }

    fun setKeywordBlacklist(values: List<String>) =
        updateSettings { it.copy(keywordBlacklist = values.distinct()) }

    fun setRecordFilteredMessages(enabled: Boolean) =
        updateSettings { it.copy(recordFilteredMessages = enabled) }

    fun setCopyMode(mode: CopyMode) = updateSettings { it.copy(copyMode = mode) }

    fun setRetentionDays(days: Int) = updateSettings { it.copy(retentionDays = days) }

    fun setMaxRecords(max: Int) = updateSettings { it.copy(maxRecords = max) }

    /** Exports an encrypted configuration backup to a SAF [uri]. */
    fun exportBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            runCatching { container.backupManager.export(uri, password, container.settings.current()) }
                .onSuccess { postSnackbar("备份已导出（密码 + 本机密钥双重保护）") }
                .onFailure { postSnackbar("备份导出失败：${it.message ?: "未知错误"}", isError = true) }
        }
    }

    fun importBackupWithPassword(uri: Uri, password: String) {
        viewModelScope.launch {
            runCatching { container.backupManager.importWithPassword(uri, password) }
                .onSuccess { restored ->
                    container.settings.update { restored }
                    container.serviceController.startIfEnabled()
                    postSnackbar("配置已恢复")
                }
                .onFailure { postSnackbar("恢复失败：${it.message ?: "密码错误或文件损坏"}", isError = true) }
        }
    }

    fun importBackupWithDevice(uri: Uri) {
        viewModelScope.launch {
            runCatching { container.backupManager.importWithDeviceKey(uri) }
                .onSuccess { restored ->
                    container.settings.update { restored }
                    container.serviceController.startIfEnabled()
                    postSnackbar("已通过本机密钥恢复配置")
                }
                .onFailure { postSnackbar("本机恢复失败：${it.message ?: "请改用密码恢复"}", isError = true) }
        }
    }

    /** Exports records to a user-picked SAF [uri]. */
    fun exportRecords(uri: Uri, format: ExportFormat, range: ExportRange, masked: Boolean) {
        viewModelScope.launch {
            runCatching {
                container.recordExporter.export(
                    uri = uri,
                    format = format,
                    range = range,
                    masked = masked,
                    records = records.value,
                )
            }.onSuccess { count ->
                postSnackbar("已导出 $count 条记录")
            }.onFailure { error ->
                postSnackbar("导出失败：${error.message?.ifBlank { error.javaClass.simpleName } ?: "未知错误"}", isError = true)
            }
        }
    }

    /** Applies the retention policy immediately (used by the "清理" action). */
    fun pruneNow() {
        viewModelScope.launch {
            val settings = container.settings.current()
            val before = if (settings.retentionDays > 0) {
                System.currentTimeMillis() - settings.retentionDays * 86_400_000L
            } else {
                0L
            }
            if (before > 0) container.history.pruneBefore(before)
            container.history.prune(settings.maxRecords)
            container.history.refresh()
            postSnackbar("已按当前保留策略清理记录")
        }
    }

    fun setArchive(enabled: Boolean) = updateSettings { it.copy(isArchive = enabled) }

    fun setBadge(enabled: Boolean) = updateSettings { it.copy(badgeEnabled = enabled) }

    fun setAutoStartOnBoot(enabled: Boolean) = updateSettings { it.copy(autoStartOnBoot = enabled) }

    /** Enables the app lock after the setup dialog verified the PIN twice. */
    fun enableAppLock(pin: String): Boolean {
        if (!AppLock.isValidPin(pin)) return false
        val (salt, hash) = AppLock.setPin(pin)
        updateSettings { it.copy(appLockEnabled = true, appLockPinSalt = salt, appLockPinHash = hash) }
        postSnackbar("已开启应用锁")
        return true
    }

    fun disableAppLock(currentPin: String): Boolean {
        val settings = container.settings.current()
        if (!AppLock.verifyPin(currentPin, settings.appLockPinSalt, settings.appLockPinHash)) return false
        updateSettings { it.copy(appLockEnabled = false, appLockPinSalt = "", appLockPinHash = "") }
        postSnackbar("已关闭应用锁")
        return true
    }

    fun changeAppLockPin(currentPin: String, newPin: String): Boolean {
        val settings = container.settings.current()
        if (!AppLock.verifyPin(currentPin, settings.appLockPinSalt, settings.appLockPinHash)) return false
        if (!AppLock.isValidPin(newPin)) return false
        val (salt, hash) = AppLock.setPin(newPin)
        updateSettings { it.copy(appLockPinSalt = salt, appLockPinHash = hash) }
        postSnackbar("PIN 已更新")
        return true
    }

    fun setAutoLockMinutes(minutes: Int) = updateSettings { it.copy(autoLockMinutes = minutes) }

    fun setBiometricUnlock(enabled: Boolean) = updateSettings { it.copy(biometricUnlock = enabled) }

    fun setHideFromRecents(enabled: Boolean) {
        updateSettings { it.copy(hideFromRecents = enabled) }
        postSnackbar(if (enabled) "已开启后台隐藏：离开 App 后从最近任务移除" else "已关闭后台隐藏")
    }

    fun setThemeMode(mode: ThemeMode) = updateSettings { it.copy(themeMode = mode) }

    fun setAppLanguage(language: AppLanguage) = updateSettings { it.copy(appLanguage = language) }

    fun setDynamicColor(enabled: Boolean) = updateSettings { it.copy(dynamicColor = enabled) }

    /** Toggles the persistent foreground service and keeps it in sync. */
    fun setForegroundServiceEnabled(enabled: Boolean) {
        updateSettings { it.copy(foregroundServiceEnabled = enabled) }
        if (enabled) {
            if (container.settings.current().forwardingEnabled) {
                container.serviceController.start()
            }
            postSnackbar("已开启常驻通知栏")
        } else {
            container.serviceController.stop()
            postSnackbar("已关闭常驻通知栏，仅通过短信广播接收")
        }
        refreshSystemState()
    }

    // -- actions ------------------------------------------------------------

    fun sendTemplateTestPush() {
        if (_templateTestState.value is TestPushState.Loading) return
        val current = container.settings.current()
        if (!current.isConfigured) {
            _templateTestState.value = TestPushState.Failure("请先填写服务器地址和 Device Key")
            postSnackbar("请先配置 Bark 服务器", isError = true)
            return
        }
        _templateTestState.value = TestPushState.Loading
        viewModelScope.launch {
            val result = runCatching { container.forwardEngine.sendTemplateTestPush() }
                .getOrElse { error -> BarkResult.failure(error.message ?: "模板测试推送失败") }
            _templateTestState.value = if (result.success) {
                TestPushState.Success(result.message.ifBlank { "模板测试推送成功" })
            } else {
                TestPushState.Failure(result.message.ifBlank { "模板测试推送失败" })
            }
            postSnackbar(
                if (result.success) "模板测试推送已发送" else "模板测试推送失败：${result.message.ifBlank { "未知原因" }}",
                isError = !result.success,
            )
        }
    }

    fun sendTestPush() {
        if (_testPushState.value is TestPushState.Loading) return
        val current = container.settings.current()
        if (!current.isConfigured) {
            _testPushState.value = TestPushState.Failure("请先填写服务器地址和 Device Key")
            postSnackbar("请先配置 Bark 服务器", isError = true)
            return
        }
        _testPushState.value = TestPushState.Loading
        viewModelScope.launch {
            val result = runCatching { container.forwardEngine.sendTestPush() }
                .getOrElse { error -> BarkResult.failure(error.message ?: "测试推送失败") }
            _testPushState.value = if (result.success) {
                TestPushState.Success(result.message.ifBlank { "推送成功" })
            } else {
                TestPushState.Failure(result.message.ifBlank { "推送失败" })
            }
            if (result.success) {
                ensureServiceRunning()
            }
            postSnackbar(
                if (result.success) "嘟嘟可已把测试推送送到 iPhone ✅" else "嘟嘟可送信失败：${result.message.ifBlank { "未知原因" }}",
                isError = !result.success,
            )
            container.history.refresh()
        }
    }

    /** Re-sends one record; the caller owns the local loading indicator. */
    suspend fun resendRecord(recordId: Long): BarkResult {
        val result = runCatching { container.forwardEngine.resend(recordId) }
            .getOrElse { error -> BarkResult.failure(error.message ?: "重新发送失败") }
        container.history.refresh()
        postSnackbar(
            if (result.success) "已重新发送" else "重新发送失败：${result.message.ifBlank { "未知原因" }}",
            isError = !result.success,
        )
        return result
    }

    fun deleteRecord(recordId: Long) {
        viewModelScope.launch {
            container.history.delete(recordId)
            postSnackbar("记录已删除")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.history.clearAll()
            postSnackbar("转发记录已清空")
        }
    }

    fun refreshHistory() {
        container.history.refresh()
    }

    // -- navigation helpers -------------------------------------------------

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun requestOpenRecord(recordId: Long) {
        _openRecordRequest.value = recordId
    }

    fun consumeOpenRecord() {
        _openRecordRequest.value = -1L
    }

    // -- feedback -----------------------------------------------------------

    fun postSnackbar(message: String, isError: Boolean = false) {
        _events.tryEmit(SnackbarEvent(message, isError))
    }
}
