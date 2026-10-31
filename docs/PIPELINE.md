# DodoComm · 转发管线实现说明

本文档记录 `bark/`、`forward/`、`sms/`、`service/`、`work/` 五个包的实现细节、契约与已知边界。
架构总览见 [`ARCHITECTURE.md`](ARCHITECTURE.md)。

## 文件与职责

| 文件 | 职责 |
| --- | --- |
| `sms/SmsParser.kt` | `SMS_RECEIVED` Intent → `SmsPayload`：多段拼接、发送方、时间戳、subId、可选 SIM 名称 |
| `sms/SmsReceiver.kt` | manifest receiver；`goAsync()` + IO 协程 + 8s 超时 + `finish()` |
| `bark/BarkClient.kt` | Bark API V2 `POST /push`，404/405 时回退 V1 GET；全部错误 → 中文 `BarkResult`，绝不抛出 |
| `forward/FilterRules.kt` | 纯逻辑：黑名单 / 白名单 / 关键词（中文原因） |
| `forward/TitleTemplate.kt` | 纯逻辑：`{sender}` `{sim}` `{time}` 标题模板 + 分隔符清理 |
| `forward/ForwardEngine.kt` | 落库、去重、即时发送、`sendTestPush` / `resend` / `retryPending` |
| `service/ForwardService.kt` | `specialUse` 前台服务、常驻通知、暂停/测试动作、`START_STICKY` |
| `service/ServiceController.kt` | UI/Receiver/Worker 使用的启动/停止/开关/刷新通知门面 |
| `service/BootReceiver.kt` | 开机 / 应用更新后恢复服务 + 调度维护任务 |
| `service/NotificationHelper.kt` | 通知渠道、常驻通知构建与更新（唯一入口） |
| `work/WorkScheduler.kt` | WorkManager 唯一任务调度（维护 + 重试） |
| `work/RetryWorker.kt` | 网络约束的一次性补发（指数退避） |
| `work/MaintenanceWorker.kt` | 15 分钟周期：prune、补发、服务自愈 |
| `app/src/main/res/values/pipeline_strings.xml` | 通知相关中文文案（管线仅有的 res 文件） |

## 公开契约（与 ARCHITECTURE.md 完全一致）

```kotlin
class BarkClient {
    suspend fun send(settings: AppSettings, title: String, body: String): BarkResult
}

class ForwardEngine(context: Context, container: AppContainer) {
    suspend fun handleIncoming(payload: SmsPayload): ForwardRecord?
    suspend fun sendTestPush(): BarkResult
    suspend fun resend(recordId: Long): BarkResult
    suspend fun retryPending(): Boolean
    companion object { const val MAX_ATTEMPTS = 5; const val RETRY_BATCH_SIZE = 10 }
}

class ServiceController(context: Context, container: AppContainer) {
    fun isServiceRunning(): Boolean
    fun start(): Boolean
    fun startIfEnabled(): Boolean
    fun stop()
    fun setForwardingEnabled(enabled: Boolean)
    fun refreshNotification()
}

class WorkScheduler(context: Context) {
    fun ensureMaintenanceScheduled(); fun cancelMaintenance()
    fun enqueueRetry(); fun cancelRetry(); fun cancelAll()
    companion object { WORK_MAINTENANCE = "sms2bark_maintenance"; WORK_RETRY = "sms2bark_retry" }
}
```

`ForwardService` companion 保持 `ACTION_START/STOP/REFRESH/TEST_PUSH` 与 `@Volatile isRunning`。

## 转发流程

1. `SmsReceiver`（manifest receiver）收到 `SMS_RECEIVED`：
   - `goAsync()`；`CoroutineScope(SupervisorJob() + Dispatchers.IO).launch`
   - `withTimeout(8_000)` 包裹解析 + `ForwardEngine.handleIncoming`
   - `finally { pendingResult.finish() }`；异常只写 logcat（TAG `SMS2BARK`），绝不抛出
2. `SmsParser`：
   - `Telephony.Sms.Intents.getMessagesFromIntent(intent)`；正文按 PDU 顺序拼接
   - 发送方/时间戳取第一条：`originatingAddress`（为空时回退 `displayOriginatingAddress`）、`timestampMillis`
   - subId 依次读取 extras `subscription` / `subscription_id` / `sub_id`（Int/Long/String 均兼容，无效值跳过，默认 `-1`）
   - 有 `READ_PHONE_STATE` 时通过 `SubscriptionManager` 生成 `SIM {slot+1} · {displayName/carrierName}`，`SecurityException` 等全部吞掉
3. `ForwardEngine.handleIncoming`：
   - 总开关关闭 → `null`（不落库）
   - 过滤命中且 `recordFilteredMessages=false` → `null`（不落库）；否则落库 `FILTERED` 并返回
   - 标题 `TitleTemplate.render`；`dedupeKey = sha256("$sender|$smsTimestamp|$body")`
   - `HistoryRepository.insert` 返回 `-1`（重复广播）→ `null`
   - 未配置服务器 → 落库后更新为 `FAILED`（错误文案 `未配置 Bark 服务器…`，attempts 保持 0，不入重试队列；配置好后由维护任务补发）
   - 已配置 → 立即 `BarkClient.send`：成功写 `SENT` + 原始响应；失败写 `FAILED` + 中文错误并 `enqueueRetry()`
   - 每次状态变化后 `ServiceController.refreshNotification()`（服务未运行则自动跳过）
4. 重试：
   - `RetryWorker`（网络约束、指数退避 15s、唯一任务 KEEP）与 `MaintenanceWorker`（15min）都调用 `retryPending()`
   - `retryPending()`：`Mutex` 防并发重试；一次最多 10 条、每条最多 5 次（`listForRetry` 过滤）；离线时改为 `enqueueRetry()` 后返回 `false`，不消耗尝试次数
   - 仍可重试 → 返回 `true` → `RetryWorker` `Result.retry()`

### 并发与取消

- 每条记录用 `ConcurrentHashMap.newKeySet<Long>` 做单飞（single-flight）保护：接收广播的即时发送、用户重发、WorkManager 补发不会并发重复推送同一条。
- 接收端 8s 超时（或服务销毁）取消协程时：`ForwardEngine` 在 `NonCancellable` 中把仍为 `PENDING` 的记录标注「发送被中断」并入重试队列。语义是 **at-least-once**：极窄的取消窗口内可能出现一次重复 Bark 推送，但绝不会丢短信。
- 未配置 / 已过滤 / 重复广播都不能触发任何网络请求。

## Bark API 映射（V2 POST `/push`，JSON）

| JSON 字段 | 来源 | 省略条件 |
| --- | --- | --- |
| `device_key` | `settings.deviceKey` | 必填（未配置直接失败） |
| `title` | 渲染后的标题 | 必填 |
| `body` | 短信正文 | 必填 |
| `group` | `pushGroup` | 空字符串 |
| `sound` | `pushSound` | 空字符串 |
| `level` | `pushLevel.apiValue`（active/timeSensitive/passive/critical） | 总发送 |
| `icon` | `pushIcon` | 空字符串 |
| `url` | `pushUrl` | 空字符串 |
| `isArchive` | `"1"` | `isArchive=false` |
| `autoCopy` | `"1"` | `autoCopy=false` |
| `badge` | `1` | `badgeEnabled=false` |

- 成功判定：HTTP 2xx **且**（响应无 `code` 字段或 `code == 200`）。
- 失败文案：优先服务器 `message` 字段（前缀「推送失败：」）；否则按 HTTP 状态映射中文提示。
- `connectTimeout=10s`、`readTimeout=15s`；HTTP 调用使用 OkHttp `enqueue` + `suspendCancellableCoroutine`，协程取消会取消请求，不占用线程。
- 仅 POST 返回 HTTP 404/405 时回退一次 V1 GET：
  `GET {base}/{urlEncode deviceKey}/{urlEncode title}/{urlEncode body}?group=&sound=&level=&isArchive=1&autoCopy=1`（只用非空参数；`HttpUrl` 自动 UTF-8 百分号编码，中文标题/正文安全）。
- 网络异常映射：超时 / 无法解析域名 / TLS 失败 / 连接失败 / 其它 IO，均为中文可读文案。
- 服务端自签名 HTTPS 证书不支持（仅 HTTP 自建服务器或有效证书 HTTPS）。

## 保活实现（全部为官方机制）

- **短信广播**：manifest receiver，进程被杀也能收到，是核心通道（`goAsync` 保证有约 8s 后台执行窗口）。
  `SMS_RECEIVED` 本身是系统受保护广播（只有系统电话栈能发送），因此 receiver 不声明
  `BROADCAST_SMS` 权限也依然防伪造；去掉 receiver 级权限反而提升 OEM 兼容性（部分 OEM 电话栈不持有该权限）。
- **`specialUse` 前台服务**：`FOREGROUND_SERVICE_SPECIAL_USE` + manifest `<property>` subtype；通知渠道 `IMPORTANCE_LOW`、静音、常驻；无 6 小时限制（该限制只针对 `dataSync`）。通知动作「暂停」写回设置并停止服务，「测试」发一次测试推送并把结果显示在常驻通知里。
- **开机恢复**：`BOOT_COMPLETED` / `QUICKBOOT_POWERON` / `MY_PACKAGE_REPLACED`；仅当 `forwardingEnabled && autoStartOnBoot` 时 `startIfEnabled()`（还会校验 `foregroundServiceEnabled` 与已配置），全部 try/catch。
  Android 16 / 部分 OEM 定制系统在进程由后台（开机广播）拉起时可能拒绝 `startForegroundService`：此时不崩溃，改发一条
  「短信转发服务未自动恢复」提醒通知（alerts 渠道，点击打开 App 后在 MainActivity.onResume 的可见上下文里恢复服务）；
  短信广播接收与即时转发不受影响。
- **WorkManager**：15 分钟维护任务（prune 500 条、补发、`startIfEnabled()` 自愈）；失败补发走网络约束的一次性任务 + 指数退避。
- **电池优化 / OEM 自启动**：由 UI 向导跳官方设置页（`core/system`），应用内不做任何 hack；**不使用**后台音频、无障碍、1px Activity、循环 Alarm。
- 候选限制（如实说明）：Android 12+ 后台进程（含 WorkManager worker）里 `startForegroundService` 可能被系统拒绝，`start()` 会捕获并返回 `false`；此时短信接收+即时转发仍然工作，前台服务会在用户打开 App、开机或系统允许的时机恢复。

## 通知

- 渠道：`sms2bark_service`（LOW，常驻）、`sms2bark_alerts`（DEFAULT，预留提醒）。
- 常驻通知文本：`常驻转发中 · 今日已发送 N 条 · 最新 {sender} {相对时间}`，无记录时「等待新短信」，暂停时「转发已暂停」。数据来自内存 StateFlow，零 IO，刷新廉价。
- 点击打开 `MainActivity`；动作为 `PendingIntent.getService`（独立 requestCode、`FLAG_IMMUTABLE`）。
- `POST_NOTIFICATIONS` 被拒绝/撤销时：`updateOngoing` 直接跳过并捕获 `SecurityException`；服务本身仍可运行（系统任务管理器仍显示 FGS 通知）。

## 测试（JVM，`./gradlew :app:testDebugUnitTest`）

| 测试 | 覆盖 |
| --- | --- |
| `bark/BarkClientTest`（8） | V2 POST JSON/字段映射、`code` 成功判定、HTTP 错误中文文案、404→GET 回退与中文路径编码、未配置快速失败、非法地址 |
| `forward/FilterRulesTest`（12） | 黑白名单、+86 号码容错（7 位后缀规则）、短号不误判、关键词大小写、空白项忽略、黑名单优先 |
| `forward/TitleTemplateTest`（8） | 三个 token、空 sim 分隔符清理、默认模板、纯文本模板、永不空标题 |
| `core/util/HashingTest`（2） | SHA-256 标准向量、去重键稳定性 |

均不依赖 Android 运行时（`unitTests.isReturnDefaultValues=true`）。

## 给 UI / 调试 harness 的接口备注

- `ServiceController.setForwardingEnabled(true)`：写设置 → `startIfEnabled()`（仅当 `foregroundServiceEnabled` 且已配置服务器/Device Key 时才启动前台服务，避免未配置时出现「常驻转发中」通知）→ 调度周期任务；`false`：写设置 → 取消周期/重试任务 → 停止服务。配置完成（保存服务器地址/Device Key）后如需立即显示常驻通知，请再调用 `startIfEnabled()`。
- `refreshNotification()` 在服务未运行时是 no-op；`ForwardEngine` 每完成一次发送都会自动调用。
- `ForwardEngine.sendTestPush()` 不写历史；`resend(recordId)` 会更新该记录的 status/attempts/bark_response。
- `ForwardEngine` / `BarkClient` 所有 suspend API 都可在任意 dispatcher 调用（内部切到 IO）。
