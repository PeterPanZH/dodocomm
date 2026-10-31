# DodoComm · 架构与协作契约

Android 应用「嘟嘟通讯」：把收到的短信转发到用户配置的推送通道。命名灵感来自原神「嘟嘟通讯」活动（包名 com.dodocomm.app 保持不变）。
目标系统：Android 8–17；对 ColorOS / LineageOS 等定制系统提供保活向导。
包名：`com.dodocomm.app`。

## 技术栈（全部为当前稳定版，2026-10）

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Kotlin | 2.4.21 | 通过 AGP 9 built-in Kotlin 的 KGP classpath 固定 |
| AGP | 9.5.0 | 新 DSL + built-in Kotlin（**不要**再应用 `org.jetbrains.kotlin.android`） |
| Gradle | 9.6.0 | wrapper 中固定 |
| JDK | 21（toolchain 17） | 编译目标 Java 17 |
| Compose | BOM 2026.09.00（Compose UI 1.12.1，Material3 1.4） | 需要 compileSdk 37 + AGP 9 |
| compileSdk / targetSdk / minSdk | 37.2 / 36 / 26 | Compose 1.12 要求 compileSdk 37 |
| AndroidX | core 1.19.1, activity 1.13.0, lifecycle 2.11.0, navigation 2.10.2, work 2.12.0 | 只用 stable |
| HTTP | OkHttp 5.5.1 | 单元测试用 `mockwebserver3` 5.5.1 |
| 协程 | kotlinx-coroutines 1.11.0 | 全异步，主线程零 IO |
| 存储 | SharedPreferences + 原生 SQLite | 无 Room/KSP，构建更快、依赖更少 |

设计/迁移参考（已放入 `../refs/skills/`）：Google `mobile-android-design`、
`agp-9-upgrade`、`jetpack-compose-performance`。

## 目录与所有权

```
app/src/main/java/com/dodocomm/app/
  Sms2BarkApp.kt, AppContainer.kt      Lead
  core/                                Lead
    model/  settings/  db/  util/  system/
  bark/  forward/  sms/  service/  work/   pipeline-dev
  ui/                                     ui-dev
app/src/debug/                          verifier
app/src/main/res/values/pipeline_strings.xml  pipeline-dev（仅此一个 res 文件）
app/src/test/                           pipeline-dev（纯逻辑测试） / verifier（构建验证）
scripts/, screenshots/            本地验证工具与截图
docs/, release/, README.md              Lead
```

**跨所有权文件不要改。** 需要改 manifest / res / core 时发消息给 Lead。

## 核心契约（保持签名稳定；要改先通知 Lead + 相关 owner）

```kotlin
// AppContainer.kt（Lead）
class AppContainer(context: Context) {
    val settings: SettingsRepository
    val history: HistoryRepository
    val barkClient: BarkClient
    val serviceController: ServiceController
    val forwardEngine: ForwardEngine
    val workScheduler: WorkScheduler
    fun onAppStart()
}
val Context.appContainer: AppContainer   // (applicationContext as Sms2BarkApp).container

// bark/BarkClient.kt（pipeline）
class BarkClient {
    suspend fun send(settings: AppSettings, title: String, body: String): BarkResult
}

// forward/ForwardEngine.kt（pipeline）
class ForwardEngine(context: Context, container: AppContainer) {
    suspend fun handleIncoming(payload: SmsPayload): ForwardRecord?   // null = 重复/关闭
    suspend fun sendTestPush(): BarkResult
    suspend fun resend(recordId: Long): BarkResult
    suspend fun retryPending(): Boolean   // true = 仍有可重试记录（WorkManager retry）
}

// service/ServiceController.kt（pipeline）
class ServiceController(context: Context, container: AppContainer) {
    fun isServiceRunning(): Boolean
    fun start(): Boolean
    fun startIfEnabled(): Boolean
    fun stop()
    fun setForwardingEnabled(enabled: Boolean)
    fun refreshNotification()
}

// work/WorkScheduler.kt（pipeline）
class WorkScheduler(context: Context) {
    fun ensureMaintenanceScheduled()
    fun cancelMaintenance()
    fun enqueueRetry()
    fun cancelRetry()
    fun cancelAll()
}
```

`ForwardService` companion 必须保留：

```kotlin
const val ACTION_START/ACTION_STOP/ACTION_REFRESH/ACTION_TEST_PUSH
@Volatile var isRunning: Boolean
```

### 已完成的核心 API（Lead，可直接使用）

- `SettingsRepository`: `settings: StateFlow<AppSettings>`、`current(): AppSettings`、
  `update { }: AppSettings`、`replace()`。写盘是同步 commit（可在 receiver 线程调用；
  UI 文本框请本地记住、失焦/Done 时再落盘，避免每键写盘）。
- `AppSettings`: 字段见源码；`normalizedServerBase()`、`pushEndpoint()` 会把用户输入
  （可含 `.../push`、可省略 scheme）规范化为 `https://host[:port]/push`。
- `HistoryRepository`: `records: StateFlow<List<ForwardRecord>>`、`stats: StateFlow<TodayStats>`、
  `refresh()`、`insert(record, dedupeKey): Long`（-1 = 重复）、
  `updateResult(id, status, attempts, error, response)`、`get`、`listForRetry`、
  `delete`、`clearAll`、`prune`、`lastRecord()`。
- `HistoryDao.DEFAULT_HISTORY_LIMIT = 300`、`MAX_HISTORY_ROWS = 500`。
- `AppPermissions`: `hasReceiveSms/hasPostNotifications/hasReadPhoneState/areNotificationsEnabled/isIgnoringBatteryOptimizations/coreRuntimePermissions()`。
- `SystemNavigator`: `openAppDetails/openNotificationSettings/openBatteryOptimizationSettings/
  requestIgnoreBatteryOptimizations/openBatterySettings/openAutoStartSettings/deviceLabel`。
- `OemGuides.detect(): OemGuide`：根据 Build 信息返回 ColorOS / LineageOS / 通用向导
  （含 `GuideAction` 按钮动作）。
- `TimeFormatter`, `Hashing.sha256`。

### 数据与状态

```kotlin
data class SmsPayload(sender, body, smsTimestamp, receivedAt, subscriptionId, simLabel)
enum class ForwardStatus { PENDING, SENT, FAILED, FILTERED, SKIPPED }
data class ForwardRecord(id, sender, body, receivedAt, createdAt, simLabel, subscriptionId,
                         status, attempts, error, title, barkResponse)
data class TodayStats(sent, failed, filtered, pending)
data class BarkResult(success, code, message, rawBody)
```

SQLite 表 `records(id, sender, body, received_at, created_at, sim_label, subscription_id,
status, attempts, error, title, bark_response, dedupe_key UNIQUE)`。

**去重键**：`sha256("$sender|$smsTimestamp|$body")`。收到重复广播或重复投递时
`insert()` 返回 -1，直接跳过。

## 转发流程（pipeline 实现）

1. `SmsReceiver`（manifest receiver，`BROADCAST_SMS` 保护，`goAsync()`）→ `SmsParser` 解析
   多段短信、发送方、时间戳、`subscription` extra；可选 `READ_PHONE_STATE` 已授权时把
   subId 映射成「SIM 2 · 中国移动」。
2. 组装 `SmsPayload`，调用 `ForwardEngine.handleIncoming()`；8 秒超时保护，异常只记日志。
3. `ForwardEngine`：
   - 总开关关闭 → 返回 null（不记录）。
   - `FilterRules`：白名单非空时仅放行命中者；黑名单命中；关键词命中 → `FILTERED`
     （若 `recordFilteredMessages=false` 则不记录）。
   - `TitleTemplate.render()`：token `{sender}`、`{sim}`、`{time}`，默认「来自 {sender}」；
     引擎把短信正文作为 Bark body。
   - 落库 PENDING（`HistoryRepository.insert`）→ 立即调用 `BarkClient.send()`；
     成功 → SENT；失败 → FAILED + `WorkScheduler.enqueueRetry()`。
   - 更新常驻通知（`ServiceController.refreshNotification()`）。
4. `BarkClient`（API V2）：
   - `POST {settings.pushEndpoint()}`，JSON：`device_key, title, body, group, sound, level,
     icon, url, isArchive, badge`（空字符串/关闭的字段省略）。
   - 成功判定：HTTP 2xx 且响应 JSON `code == 200`（无 code 字段时以 HTTP 2xx 为准）。
   - GET fallback：仅当 POST 返回 404/405 时按 `{base}/{key}/{title}/{body}?...` 重试一次。
   - 超时：连接 10s / 读取 15s；错误信息放在 `BarkResult.message`（中文可读）。
5. `RetryWorker`：网络约束 + 指数退避（唯一任务名 `sms2bark_retry`，KEEP），
   每次最多 10 条、每条最多 5 次尝试；`MaintenanceWorker` 每 15 分钟：prune 500 条、
   补发可重试记录、仅当 `forwardingEnabled && foregroundServiceEnabled` 时尝试恢复前台服务。
6. `ForwardService`：`specialUse` 前台服务（manifest 已声明 subtype property）、
   `START_STICKY`、常驻通知含「暂停/恢复」「发送测试」动作；`isRunning` 用 volatile。
7. `BootReceiver`：`BOOT_COMPLETED` / `QUICKBOOT_POWERON` / `MY_PACKAGE_REPLACED`，
   在 try/catch 中 `startForegroundService`（Android 15+ 允许 specialUse 从 BOOT_COMPLETED
   启动；若 OEM 仍拦截则记录日志，不影响短信广播路径）。

## 保活设计（正规方式，无音频/无障碍黑科技）

- 短信接收依赖系统 `SMS_RECEIVED` 广播（manifest receiver，进程被杀也能收到），这是核心通道。
- 常驻 `specialUse` 前台服务：官方机制、无 6 小时限制（dataSync 才有），notification 可见。
- `BOOT_COMPLETED` 后由 BootReceiver（豁免上下文）恢复前台服务；若 Android 16/OEM 仍拦截，则发一条可点击的恢复通知。**不要**在 `Application.onCreate` 里启动 FGS —— Android 16 会因进程由后台广播拉起而抛 `ForegroundServiceStartNotAllowedException`。
- WorkManager 周期任务做补发与自愈。
- 电池优化豁免 + OEM 自启动设置由 App 内向导引导用户手动开启（跳系统设置页）。
- 不使用：后台播放静音音频、无障碍服务、1px Activity、循环 Alarm 等。

## 构建 / 验证环境（workspace 本地）

```bash
source scripts/env.sh          # verifier 维护：JAVA_HOME / ANDROID_HOME / GRADLE_USER_HOME / HOME / TMPDIR
cd sms2bark
$GRADLE :app:assembleDebug     # 本地 Gradle 9.6.0 二进制，避免 wrapper 再下载
$GRADLE :app:testDebugUnitTest
```

- Android SDK：`toolchain/android-sdk`（platforms 36 / 37.0 / 37.2, build-tools 36.0.0 / 37.0.0, emulator, system-image android-36 google_apis arm64）
- Gradle：`toolchain/gradle/gradle-9.6.0/bin/gradle`
- 模拟器：`sms2bark_api36`（pixel_7, 1080x2400）；网络需 `adb shell svc wifi disable` 后走 slirp 才能访问宿主 `10.0.2.2:8787`（见 `scripts/prepare_emulator.sh`）。
- Mock Bark：`scripts/mock_bark_server.py`，日志写到本地 `verify/bark_requests.log`。

## 调试协议

调试用的 `DebugReceiver` 已移除：release 和 debug 构建都不再暴露可导出的配置/历史读取入口。需要 E2E 测试时使用 adb 输入、Mock Bark 和正常 UI 流程。

## 性能与可维护性约定

- 主线程零 IO / 零网络；数据库与 HTTP 全部 `Dispatchers.IO`。
- Compose：`LazyColumn` 必须有稳定 key + contentType；StateFlow 用 `collectAsStateWithLifecycle`；
  避免在 lazy item 里读 CompositionLocal；动画用 `graphicsLayer` 延迟读取；不滥用 `@Immutable`。
- R8 开启（release），资源收缩开启；启动路径只创建容器，服务/网络懒加载。
- 单例都是 lazy；所有依赖显式传递；纯逻辑（过滤、模板、Bark JSON、去重键）必须有单元测试。
- 中文文案只放在 UI 层（Compose 里内联）或通知资源；数据/领域层不写 UI 文案硬编码。
