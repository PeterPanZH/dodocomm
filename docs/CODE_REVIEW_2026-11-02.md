# DodoComm 全局代码审查

> **修复状态（1.14.9）**：本报告是 v1.14.8 的基线审查。P1/P2 项已按报告修复，包含权限探针、通知白名单、更新路径/大小、隐私默认值、文档与 i18n。S-03 的「receiver 全部设为 exported=false」经模拟器验证会导致系统短信广播收不到，故保留系统 receiver 为 exported=true，并继续依赖 protected broadcast 与 protected action；这一点以兼容性为准。剩余架构类建议（完全异步设置加载、instrumentation/Macrobenchmark）保留为后续工作。

- 审查基线：`v1.14.8`（commit `209a7ec`，`com.dodocomm.app` versionCode 59）
- 审查范围：`app/src/main/java` 103 个 Kotlin 文件、Manifest、资源/i18n、Workers、Widget、备份/更新/导出的完整链路
- 验证（修复后）：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` 通过；**117/117 单测**，lint **0 errors / 93 warnings**
- 审查方式：静态代码审查 + 全量字符串/权限/Manifest 扫描 + 关键链路走查；**没有做第三方渗透测试，也没有覆盖所有 OEM 真机组合**

## 结论摘要

整体架构清晰：Keystore 加密、HMAC 去重、远程指令 HMAC、APK 签名校验、备份 AES-GCM、导出脱敏、FileProvider 限定目录、PendingIntent 全部 IMMUTABLE，这些做得比较扎实。未发现可直接利用的 0-click 远程代码执行。

但仍有几类需要优先处理的问题：

1. **ColorOS/系统权限状态错误**：App 内通知使用权状态探针与通知监听服务使用的判断不一致，会长期显示「去授权」。
2. **通知转发仍受已删除的「所有应用」开关影响**：UI 已改为空白名单=全部，但监听器仍有旧判断。
3. **隐私暴露面**：锁屏常驻通知、桌面组件直接显示最近消息正文/发送者；默认不设置 `FLAG_SECURE`；HTTP 通道可明文传输短信和令牌。
4. **更新下载文件名未净化**：远端 `versionName` 可影响本地写入路径；下载没有硬性大小上限。
5. **文档/安全声明与实际权限不一致**：README/SECURITY/docs 仍按「只收短信」描述，和电话、通知监听、`READ_CALL_LOG`、`QUERY_ALL_PACKAGES` 的实际行为不一致。
6. **性能与可维护性**：大量设置写入/启动解密/Widget 刷新在主线程；NotificationListener 对通知突发没有限流；测试只有 JVM 单测，没有权限、通知监听、Widget、全链路 instrumentation 测试。

建议先处理 P1（尤其是 1、2、3、4、5），再做 P2 的体验与工程债。

---

## P1：安全 / 隐私 / 功能正确性

### [S-01][P1] 更新包下载路径使用远端 `versionName`，未净化文件名
- 证据：`app/src/main/java/com/dodocomm/app/ui/AppViewModel.kt:210`
  ```kotlin
  val destination = File(appContext.cacheDir, "updates/DodoComm-${info.versionName}.apk")
  ```
- `UpdateManifest.parse` 只校验 `versionName` 非空（`core/update/UpdateInfo.kt:29-36`），未限制 `/`、`..`、控制字符。
- `UpdateInstaller.install` 虽然会做 `updatesDir` canonical 检查（`UpdateInstaller.kt:54-66`），但**下载阶段**已经可以写出 `cacheDir/updates` 之外的 app 私有文件；更新源被攻破或 manifest 被替换时会破坏配置/磁盘。
- 同时 `UpdateClient.download` 没有硬性最大字节数，只校验事后 SHA/size；恶意大文件可耗尽磁盘/流量。
- 建议：文件名只用 `versionCode` 或白名单字符，并强制 `canonicalFile.parent == updatesDir`；下载大小 `info.size` 与最大上限双重校验；manifest 的 `apkUrl` 继续限制 HTTPS。

### [S-02][P2] 自动化广播 receiver 导出，仅靠令牌保护
- 证据：`AndroidManifest.xml:77-83` 的 `AutomationReceiver` `exported="true"`；令牌比较在 `automation/AutomationReceiver.kt:35-41`。
- 令牌是 bearer token，界面会在 adb 示例中明文显示（`AutomationScreen.kt:210-218`），也能被复制到剪贴板；任何能读屏/截图的场景都可能泄露。
- 目前命令是非破坏性的，风险可控，但建议：
  - 增加调用方包名/签名白名单或可选 `signature` 级权限；
  - 令牌 UI 默认隐藏，adb 示例改为占位符；
  - 令牌长度/生成方式改为 `SecureRandom` 的 128-bit 以上随机值（当前 24 hex chars，96-bit，够用但建议统一）。

### [S-03][P2] 导出/受保护的 receiver 建议做 defense-in-depth
- `PhoneStateReceiver`、`BootReceiver`、`SmsReceiver` 都是 exported；其中 `PHONE_STATE`/`SMS_RECEIVED` 是 protected broadcast，普通应用不能伪造，实际风险低。
- 但 `PhoneStateReceiver` 只检查 action（`events/PhoneStateReceiver.kt:24-28`），建议对 receiver 增加 sender UID/权限校验，能设为 `exported="false"` 的尽量设为 false；至少对显式 Intent 增加 `getSendingUid()` 校验，避免 OEM/未来系统行为变化时被伪造来电事件。
- `Send`/`Boot` 安全性依赖系统 protected broadcast；建议在 Manifest 注释中写明，并在代码里校验 `intent.component`。

### [S-04][P1] 全局允许 HTTP 明文，且文档主动建议局域网 HTTP
- 证据：`AndroidManifest.xml:59` `android:usesCleartextTraffic="true"`；`README.md:55` 示例 `http://10.0.0.8:8080`。
- 渠道编辑页已有 HTTP 警告（`ChannelsScreen.kt:311-313`），但这是隐私产品，短信正文、Bark Device Key、ntfy Token、SMTP 授权码都可能明文经过网络。
- 建议：默认只允许 HTTPS；如果要保留自建局域网 HTTP，改为显式开关 + 首次确认 + 更醒目的警告；`SECURITY.md` 写明明文传输边界。

### [S-05][P2] Keystore 失败时设置加密会静默降级为明文
- 证据：`core/crypto/RecordCipher.kt:32-38` 的 `encrypt()` 在失败时 `.getOrDefault(value)`；`SettingsRepository.writeAll` 用它加密 `deviceKey`、`automationToken`、`remoteSecret`、SMTP 密码等（`SettingsRepository.kt:270-312`）。
- 数据库行使用严格的 `encryptOrThrow`，但设置/渠道走的是宽松版；Keystore 异常时敏感值可能明文落盘，只会写日志和 `RecordEncryptionStatus`。
- 建议：设置中的敏感字段也使用严格加密；失败时禁用保存并明确提示用户，而不是静默写明文。

### [S-06][P1] 崩溃日志是明文 SharedPreferences，且可被一键复制/分享
- 证据：`core/crash/CrashLogger.kt:90-115` 把完整 stack trace 写入 `dodocomm_crash_logs`；`CrashLogsScreen.kt` 提供复制全部。
- 异常消息可能包含 URL、服务端返回、Token、短信内容片段；崩溃日志没有加密、没有脱敏。
- 建议：加密或至少过滤 URL/长数字/Token；复制前提示；默认只在 debug 保存完整 trace，release 只保留类名/行号。

### [S-07][P2] CI/CD 供应链风险
- `.github/workflows/release.yml` 和 `ci.yml` 使用 `actions/checkout@v7`、`setup-java@v6`、`gradle/actions/setup-gradle@v6` 等 **major tag**，不是 SHA 锁定。
- release workflow 把 `${{ inputs.tag }}` 直接插入 shell（`RELEASE_TAG="${{ inputs.tag }}"` / `TAG="${{ inputs.tag }}"`）；只有有 workflow 触发权限的人能利用，但仍建议改为环境变量传递并校验 tag 格式。
- 依赖没有 Gradle dependency verification / SBOM / 漏洞扫描；建议至少开启 Dependabot 或 `dependency-check`。
- 建议：Actions 固定到 commit SHA；release 加 environment protection / 手动 approval；tag 输入正则校验。

### [S-08][P2] 更新 manifest 允许 `http://127.0.0.1`/`localhost`
- 证据：`core/update/UpdateInfo.kt:37-43`。
- 设计上为本地测试保留，但生产 manifest 从 GitHub HTTPS 取回后仍允许 local URL；如果更新源被替换，可触发本机请求（SSRF/本地 DoS）。签名仍能阻止恶意 APK 安装。
- 建议：release 构建只允许 HTTPS；本地测试用 debug flag 或测试构建变体。

### [P-01][P1] 通知使用权状态探针错误，和监听服务判断不一致
- 证据：`core/system/AppPermissions.kt:28-32` 只查 `NotificationManagerCompat.getEnabledListenerPackages`，没有 Secure 设置兜底；`PermissionState.read` 使用它。
- 而 `DodoNotificationListener.isAccessGranted` 增加了 `enabled_notification_listeners` fallback（`events/DodoNotificationListener.kt:85-100`）。
- 结果：OnePlus/ColorOS 上实际已授权，但设置向导/诊断/权限卡片仍显示「去授权」——这正是用户此前反馈的问题。
- 建议：抽出唯一的 `NotificationAccess.isGranted(context)`，供 `AppPermissions`、监听器、诊断共用；返回结果带 `secureSetting` 兜底。并加单元测试（可注入字符串）。

### [P-02][P1] 锁屏/桌面暴露最近消息内容
- 证据：`service/NotificationHelper.kt:94-109` 常驻通知显示 `last.sender` 和今日送达；`widget/WidgetUpdater.kt:94-144` 的 4×2/2×3/4×4 显示发送者和正文；`widget_dodocomm_dashboard.xml` 有 `recent1_body`/`recent2_body`。
- 即使开启应用锁，通知和 Widget 仍可能显示在锁屏/桌面；应用锁的 `FLAG_SECURE` 只保护 Activity，不保护通知/Widget。
- 建议：应用锁开启时默认隐藏消息内容（显示「新消息」或空），或提供「锁屏隐藏内容 / 桌面组件隐藏内容」开关；通知使用 `setPublicVersion`/`VISIBILITY_PRIVATE` 配合脱敏的 public version。

### [P-03][P1] README/SECURITY/docs 权限声明与实际权限不一致
- 证据：`README.md:84` 只列 `RECEIVE_SMS`、通知、FGS、开机、网络；`SECURITY.md:43-45` 写「permissions are limited to receiving SMS, notifications, optional phone state」。
- 实际 Manifest 还有 `READ_CALL_LOG`、`READ_PHONE_STATE`（重复声明两次）、`QUERY_ALL_PACKAGES`、`com.android.permission.GET_INSTALLED_APPS`、`REQUEST_INSTALL_PACKAGES`、`FOREGROUND_SERVICE_SPECIAL_USE`，并启用通知监听服务。
- 这不仅是文案问题：商店审核/用户信任/隐私合规都会受影响。建议把权限表、用途、可选性、关闭后果写全；删除重复的 `READ_PHONE_STATE`（Manifest:8 与 :30）。
- `QUERY_ALL_PACKAGES`/`GET_INSTALLED_APPS` 仅用于应用转发规则页；应在文档和首次使用处说明，并考虑只显示用户已选/可启动应用（如产品允许）以减少权限面。

### [S-09][P2] 应用锁的失败锁定只在内存/页面状态
- 证据：`ui/screens/AppLockScreen.kt:66-69,99-120`：5 次失败锁 30 秒，`failedAttempts`/`lockedUntil` 是 `rememberSaveable`，进程被杀或重新进入页面可重置；6 位 PIN 可被反复尝试。
- PBKDF2 150k 让每次尝试有成本，但没有跨进程持久化锁定/退避。
- 建议：把失败计数和锁定截止时间持久化到设置（加密存储），重启后继续锁定；或使用 `BiometricPrompt` 作为主要解锁，PIN 作为回退。

---

## P1/P2：功能正确性

### [F-01][P1] 通知监听仍使用已删除的 `notificationAllApps`
- 证据：`events/DodoNotificationListener.kt:29-31`：
  ```kotlin
  if (!settings.notificationAllApps && packageName !in settings.notificationSelectedPackages) return
  ```
- 但 UI 已删除「所有应用」开关，`EventFilters.appAllowed` 的新语义是「空白名单=全部，非空空名单=限制」（`events/EventFilters.kt`）。
- 旧用户/备份恢复 `notificationAllApps=false` 且白名单为空时，监听器会直接丢弃所有通知，EventForwarder 的空白名单=全部永远不会执行。
- 建议：监听器改用 `EventFilters.appAllowed(settings, packageName)`，删除 `notificationAllApps` 字段/备份字段或明确迁移。

### [F-02][P1] 来电/应用通知不刷新 Widget
- 证据：`events/EventForwarder.kt:128-192` 的 `deliver()` 只更新历史、通知，没有 `WidgetUpdater.updateAll`；而短信路径在 `forward/ForwardEngine.kt:299` 刷新。
- 结果：Widget 的最近事件/统计在电话、应用通知后仍是旧数据，直到下一次 SMS、手动刷新或系统更新。
- 同类：`work/ResumeWorker.kt:15-24` 自动恢复暂停后也不刷新 Widget，暂停状态可能停留在 Widget 上。
- 建议：在 EventForwarder 成功/失败后、ResumeWorker 恢复后统一调用 WidgetUpdater；或让 HistoryRepository 的更新触发一次防抖 Widget 刷新。

### [F-03][P1] 通知/电话的「测试」结果与实际过滤不一致
- 证据：`AppViewModel.testPhoneEvent`/`testNotificationEvent`（`AppViewModel.kt`）只调用 `EventForwarder.forward...` 然后固定 snackbar「已发送」；EventForwarder 在开关关闭、过滤、暂停、无通道时会返回 null/FAILED。
- 用户会看到「已发送」但全无推送，难以诊断。
- 建议：根据返回的 `ForwardRecord?`/状态显示「已发送 / 被过滤 / 转发关闭 / 无通道」，并复用真实管线的错误原因。

### [F-04][P2] 通知测试推送（常驻通知动作）只走 Bark 旧字段
- 证据：`forward/ForwardEngine.kt:144-153` 的 `sendTestPush()` 直接 `barkClient.send(settings)`；`ForwardService.handleTestPush` 使用它。
- 若用户只配置了 ntfy/Telegram/Pushover/Email，常驻通知的「测试」会报 Bark 未配置；首页测试按钮走 `PushDispatcher`，行为不一致。
- 建议：所有测试统一走 `pushDispatcher.dispatch`，与真实发送路径一致。

### [F-05][P2] Android 12 及以下「通知权限」修复路径无效
- 证据：`HomeScreen.handleSetupAction` 的 NOTIFICATIONS 分支只调用 `launchPermissions()`（`HomeScreen.kt:164-168`）；`coreRuntimePermissions()` 只在 API 33+ 才请求 `POST_NOTIFICATIONS`。
- 若用户在系统设置里关闭了本应用通知，API <33 上 `notificationsReady=false`，但请求权限不会打开任何设置，检查卡片永远无法完成。
- 诊断页同样：`DiagnosticsScreen.kt:133-134` 在低版本直接 `launch(POST_NOTIFICATIONS)`（lint `InlinedApi` 已提示）。
- 建议：API <33 时 `SystemNavigator.openNotificationSettings(context)`；API 33+ 才请求权限。

### [F-06][P2] 自动暂停恢复不刷新 Widget
- 已在 F-02 说明；单独列出便于修复排期。

### [F-07][P2] 远程指令 UI 同一秒生成相同 nonce，第二条会被当重放
- 证据：`RemoteManagementScreen.kt:74-82` 使用 `System.currentTimeMillis()/1000` 作为 nonce；`RemoteCommandHandler` 要求 `nonce > remoteLastNonce`（`core/remote/RemoteCommandHandler.kt:36-40`）。
- 用户在同一秒点 STATUS 和 PAUSE，后一条会被拒绝；UI 不提示。
- 建议：nonce 使用「秒级时间戳 + 随机/递增后缀」或发送前检查/提示；协议可以保留秒级时间窗，但同秒命令应支持。

### [F-08][P2] 路由规则「上移/下移」按全局列表交换，跨来源规则会错位
- 证据：`AppViewModel.moveRoutingRule`（`AppViewModel.kt:435-445`）在完整列表上 `index + direction` 交换；路由页只显示当前 source 子集（`RoutingRulesScreen.kt`）。
- 若当前 source 的两条规则中间夹着另一个 source 的规则，点击上移可能和不可见规则交换，视觉顺序不变。
- 建议：只在该 source 的规则 id 序列内重排，再重建完整列表。

### [F-09][P2] `RoutingEngine.targets` 的 fallback 逻辑存在歧义
- 证据：`core/routing/RoutingRule.kt` 的 targets：只要任一匹配规则 fallbackToDefault，就回退默认，而不是按首条命中/继续匹配语义逐条判断；`matchedTargets` 为空时可能返回默认目标，即使命中的规则明确关闭 fallback。
- 建议：明确「首条命中」还是「继续匹配合并」时 fallback 应作用于哪条规则，并补多规则测试。

### [F-10][P2] 设置里的「最多保留」可大于历史实际加载上限
- 证据：`HistoryRepository.refreshNow` 把 limit 强制 `coerceIn(1, 500)`（`HistoryRepository.kt:43`），而设置下拉可选 1000/5000（`SettingsScreen.kt:1325`），文档又说最多 500。
- 用户选 1000/5000 后 UI 仍只看到最多 500 条，数据库可能保留更多；预期不一致。
- 建议：统一上限；或 UI 明确「数据库保留 N 条，界面最多显示 500 条」。

### [F-11][P2] 模板编辑器返回键可能丢失未提交内容
- 证据：`ui/screens/TemplateEditorScreen.kt` 标题/正文主要在 `onFocusChanged { !isFocused }` 时保存；返回按钮直接 `onBack`，没有显式提交当前输入。
- 用户编辑后直接按返回（尚未失焦）可能丢失修改。
- 建议：返回/系统 BackHandler 前调用保存；或改为实时保存（debounce）。

### [F-12][P2] 更新 manifest 的 APK 下载没有最大大小
- 证据：`UpdateClient.download` 只按响应的 contentLength/size 报进度，没有硬上限；`info.size` 可以是任意大。
- 建议：限制 `size <= 100MB` 等业务上限，超过直接拒绝；下载流累计超过上限立即中止。

### [F-13][P2] 数据库 `onDowngrade` 调用了 `onUpgrade`，方向错误
- 证据：`core/db/HistoryDatabase.kt:64-66`：`onDowngrade(oldVersion, newVersion) { onUpgrade(oldVersion, newVersion) }`。
- 若从 v4 降级到 v3 再升级，`ALTER TABLE ADD COLUMN source` 可能因列已存在失败；本次用户数据可能丢失/崩溃。
- 建议：不要用 `onUpgrade` 处理 downgrade；可改成安全重建/明确抛错，或 `onDowngrade` 直接 `onCreate` 前做显式数据迁移策略。

---

## 隐私

### [P-04][P1] `FLAG_SECURE` 仅在开启应用锁时启用
- 证据：`ui/MainActivity.kt:227-233`，`applyLockWindowFlags(current.appLockEnabled)`。
- 未启用应用锁时，短信正文/发送者/验证码会出现在最近任务缩略图、截图、屏幕录制中；对隐私工具来说应默认隐藏。
- 建议：默认开启 `FLAG_SECURE` 和 `setRecentsScreenshotEnabled(false)`；如需截图可提供「允许截图」开关。

### [P-05][P2] 通知监听/QUERY_ALL_PACKAGES/READ_CALL_LOG 缺少集中说明
- 实际权限很大（读所有通知、读应用列表、读通话记录）；用户可从应用选择器/系统设置授予，但 README/SECURITY 未覆盖。
- 建议：在「事件与模板 → 应用通知/电话」和首次授权前解释数据范围、用途、不会上传；隐私政策/README 同步。

### [P-06][P2] Token/密钥/短信内容进入剪贴板后没有清理
- 证据：`AutomationScreen.kt` 的 copyToken、`RemoteManagementScreen.kt` 的密钥/指令复制、`HistoryScreen` 的正文复制。
- Android 13 前剪贴板可被其他应用读取；13+ 系统提示但仍可能被输入法/剪贴板管理器读取。
- 建议：敏感复制使用 `ClipDescription.EXTRA_IS_SENSITIVE`（API 33+）；提供复制后定时清空；UI 文案提示。

### [P-07][P2] 日志包含发送者/通知包名等元数据
- 证据：`sms/SmsReceiver.kt:46-58` 记录 sender/subId/sim/length；`DodoNotificationListener` 异常只记录类名，但其他路径可能记录 URI。
- 短信正文没有直接打印（好），但 sender 也是敏感信息；release 应降低日志级别或只保留计数/错误码。

---

## 性能 / 健壮性

### [P-08][P2] 设置读取/写入、Keystore 解密在主线程
- 证据：`SettingsRepository` 初始化 `MutableStateFlow(readAll())`（`SettingsRepository.kt:27`），`writeAll` 使用 `commit()`（:266-330）；Application 启动即创建 container（`Sms2BarkApp.kt`），UI 每次 toggle 都同步写。
- 启动时可能解密所有渠道凭证、路由规则、列表；低端设备/多通道时首帧 jank；主线程 `commit` 也会卡。
- 建议：启动读取放 IO，先发默认值再更新；写入用 `apply` + 独立序列化线程；敏感加密在 IO 执行。

### [P-09][P2] `AppViewModel.refreshSystemState` 在主线程刷新全部 Widget
- 证据：`AppViewModel.kt:98-102` 在 `init`/onResume 调用 `WidgetUpdater.updateAll`；`WidgetUpdater` 会遍历 5 个 provider。
- 建议：widget 刷新放到 IO/后台，或至少防抖；权限探针保留在主线程。

### [P-10][P2] 通知突发没有背压/限流
- 证据：`DodoNotificationListener` 每次 `onNotificationPosted` 都 `scope.launch`；`EventForwarder.deliver` 直接并发 `PushDispatcher.dispatch`（每个渠道一个 async）。群聊/同步应用可在短时间产生几十条通知，触发几十个 HTTP + DB 写入。
- 建议：增加 `Semaphore`/Channel 队列（例如最多 2-3 并发），合并同 key 更新；对高频通知做节流/去重窗口。

### [P-11][P2] 历史刷新在每次 insert/update 都触发两次 DB 查询
- 证据：`HistoryRepository.insert/updateResult` 调用 `refresh()`；`refreshNow` 分别 `dao.list` 和 `dao.todayStats`。每收到一条消息/事件至少两次查询，突发时放大。
- 建议：防抖/合并刷新（如 100-300ms），或一次事务内更新统计缓存。

### [P-12][P2] 广域 `runCatching`/`catch Throwable` 过多，错误被吞
- 扫描到 108 处 `runCatching`/`catch Throwable`；多数是必要的系统边界，但部分掩盖了配置损坏/数据库/权限错误，诊断页无法看到根因。
- 建议：区分 expected/recoverable 与 bug；记录结构化错误分类，UI 只展示用户可行动信息，日志保留技术细节（脱敏）。

### [P-13][P2] 没有 instrumentation/UI/性能回归测试
- 当前 28 个测试文件、110 个 JVM 单测；没有 `androidTest`，也没有 baseline profile/启动基准。
- 关键未覆盖：通知监听服务、通知使用权状态、权限请求流程、Widget 更新、Manifest 导出面、备份导入预览、应用选择器。
- 建议：至少加 `androidTest` 覆盖权限/通知监听/Widget RemoteViews 的 inflate（可用 Robolectric 或仪器测试）；加 baseline profile 优化启动。

---

## 交互 / 易用性 / 文案

### [U-01][P2] 首页「配置检查」不包含电话权限/通知使用权
- 证据：`HomeScreen.buildSetupItems` 只有短信、通知、推送通道、电池、自启动（`HomeScreen.kt:619-665`）。
- 用户开启电话/应用通知后，首页仍显示配置完成，但真正的事件权限可能缺失；需要到「事件与模板/设置向导/诊断」才发现。
- 建议：根据 `phoneForwardEnabled`/`notificationForwardEnabled` 动态加入电话/通知使用权检查项。

### [U-02][P2] 应用选择器 700+ 应用没有「全选/反选/图标」
- 证据：`EventRulesScreens.kt` 的 AppPickerDialog 只提供搜索和逐项 checkbox；真机 716 个应用。
- 建议：增加「全选当前筛选结果 / 清除选择」、应用图标、按字母分组；对系统应用提供「显示系统应用」开关。

### [U-03][P2] 历史状态筛选计数不随来源/高级筛选变化
- 证据：`HistoryScreen.kt:156-165` 的 counts 只按全局 records 计算；`visibleRecords` 才应用 source/sender/date（:179-188）。
- 选择「电话」来源后，顶部「已送达 100」仍包含短信/通知，用户会误判。
- 建议：counts 基于当前 source/advanced filter 的 records 计算，或去掉计数。

### [U-04][P2] 设置里仍有不可达的旧「推送」页/路由
- 证据：`SettingsScreen.kt:416` 仍有 `SettingsSection.PUSH` 分支，`PushStyleCard`（:978）只剩推送通道行；Overview 已直接进 Channels，不再进入 PUSH。
- 建议：删除 enum/分支/函数，避免后续维护者误以为还有第二入口。

### [U-05][P2] 自动化/组件页「添加桌面小组件」只 pin 2×2
- 证据：`AutomationScreen.kt` 的 requestPinAppWidget 固定 `DodoWidgetProvider`，但文案说可选 5 种尺寸。
- 建议：提供尺寸选择（2×2/4×2/4×1/2×3/4×4）或明确「先 pin 2×2，再长按调整尺寸/从组件库选其他尺寸」。

### [U-06][P2] 小字号/触控面积存在可访问性问题
- lint 有 32 个 `SmallSp` 警告；Widget 大量 8–10sp 文本，RuleRow 的上下移动 `IconButton` 视觉尺寸 28dp。
- 建议：关键操作至少 12sp/48dp 触控；长文本 ellipsize；为图标按钮提供 contentDescription（部分已有）。

### [U-07][P2] 远程指令、自动化令牌界面缺少防截屏/敏感标记
- 结合 P-04/P-06：token/密钥在设置页明文展示/复制；建议敏感区域默认隐藏、复制时标记 sensitive，且开启应用锁时对整个 Activity `FLAG_SECURE`（已部分）。

### [T-01][P1] 文档/安全声明已经过时且不准确
- 证据：
  - `README.md:84`：只列少量权限，漏掉 `READ_CALL_LOG`/`QUERY_ALL_PACKAGES`/`GET_INSTALLED_APPS`/`REQUEST_INSTALL_PACKAGES`/`FOREGROUND_SERVICE_SPECIAL_USE`。
  - `README.md:148`：还写着「单元测试 57/57、14 warnings」，当前是 110 tests / 94 warnings。
  - `SECURITY.md:43-45`：「permissions are limited to ... optional phone state」，与 Manifest 不符。
  - `docs/使用说明.md:123,132` 仍写「设置 → 更多事件」，实际入口已改为「事件与模板」；:133 还写「点通知使用权」，但该入口已移到设置向导/诊断。
  - `docs/使用说明.md:181` 仍说「只接收短信、数据只发送到 Bark」，实际支持电话/通知/多通道。
- 影响：用户/审核者会基于错误的安全承诺做判断；隐私合规风险。
- 建议：以当前 Manifest 为准重写权限表，更新版本/测试数字/入口路径/支持的事件源；把 SECURITY 的「least privilege」改为事实描述，并列出明文 HTTP 边界。

### [T-02][P2] i18n 仍有 23 个缺失键，es/fr 有中文残留
- 扫描结果（en 词典为基准）：
  - WidgetHost 的新宿主添加提示（7 条）、模板测试/示例、格式化的繁体周几等未入词典；
  - es/fr 中 `事件与模板`、`先配置推送通道，消息才能送达`、电话提示等仍是中文；
  - `en` 字典存在不自然键 `たった今` -> `たった日`（应删除/修正）。
- 已有 parity/empty/ZXQ 测试，但只比较 en/ja 键集；建议把「所有语言不得含未翻译中文（除 zh）」「键值语义抽查」加入 CI。

### [T-03][P2] 记录上限/文档/UI 文案互相矛盾
- README 说最多 500；设置可选 1000/5000；`HistoryRepository` 界面最多 500。统一表述和实现（见 F-10）。

### [T-04][P3] 其余文案不一致
- 通知页/诊断中「通知权限」与「通知使用权」两个概念容易混淆，建议明确「通知使用权（读取其他应用通知）」vs「通知权限（显示本应用通知）」。
- `RetryWorker` 注释引用不存在的 `ForwardEngine.MAX_ATTEMPTS`（实际 `DEFAULT_MAX_ATTEMPTS`）。
- 事件页已删提示，但 `docs` 仍提旧「所有应用」开关（docs:134），需同步删除。

---

## 安全性正面结论（已做得好的部分）

- 本地历史字段 AES-256-GCM，Keystore key 不导出；数据库迁移有加密。
- 远程指令使用 HMAC-SHA256 + 时间窗 + nonce 单调 + 常量时间比较。
- 应用锁 PIN 使用 PBKDF2-HMAC-SHA256（150k）+ per-install salt + 常量时间比较。
- 配置备份 AES-256-GCM + PBKDF2（200k）+ 设备 Keystore wrapper；`allowBackup=false`。
- 更新 APK 校验 SHA-256、大小、签名证书一致性，且有 cache 目录 canonical 约束，安装器路径不可越界。
- FileProvider 只暴露 `cache/updates`，所有 PendingIntent 使用 `FLAG_IMMUTABLE`。
- 导出默认脱敏；CSV 防公式注入；JSON/CSV 不含推送凭证。
- 无分析/广告 SDK，无动态代码加载；大部分危险操作有确认/错误反馈。
- 单测/ lint 门槛有效，110 个测试覆盖纯逻辑核心。

## 建议修复顺序

1. **P1 功能/隐私（本轮先做）**
   - 统一 `NotificationAccess` 探针；监听器改用 `EventFilters.appAllowed`；
   - 更新下载文件名/大小上限；
   - 应用锁/默认 `FLAG_SECURE`；Widget/通知内容脱敏开关；
   - 文档权限/安全声明同步。
2. **P1/P2 稳定性**
   - EventForwarder/ResumeWorker/维护任务刷新 Widget；
   - 测试推送统一走 PushDispatcher；
   - Android <33 通知权限跳转；
   - 路由重排/fallback 测试。
3. **性能**
   - 设置读写/Widget 刷新移出主线程；
   - 历史刷新防抖、通知监听限流；
   - 启动 baseline profile。
4. **工程/测试**
   - 修 i18n 23 个键与 es/fr 中文；补 instrumentation/Macrobenchmark；
   - CI Actions SHA 锁定、release environment、依赖审计；
   - 删除死代码（PUSH 页、`notificationAllApps`、`OPEN_EVENTS`）。

## 复现命令

```bash
cd /Users/peter/Documents/deepseek-harness/default-workspace/sms2bark
source scripts/env.sh
$GRADLE :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

本次结果：**110/110 tests, BUILD SUCCESSFUL, lint 0 errors / 94 warnings**。

> 说明：本次没有修改产品代码，只输出审查报告。P1 项建议确认后按批次修复，每批补回归测试。
