# SMS2Bark · UI 设计规范（给 ui-dev）

目标：**精致、克制、现代**。参考 Material 3（Material You）与 Google mobile-android-design 技能，
做出 2026 年质感的个人工具 App，而不是系统设置页拼盘。

## 品牌与调性

- 应用名：嘟嘟通讯（英文工程名 SMS2Bark，命名灵感来自原神「嘟嘟通讯」活动）。一句话定位：*嘟嘟可传信，把安卓短信轻推到 iPhone。*
- 主题：可莉红粉 + 四叶草金（Klee palette），吉祥物为平滑 2D 嘟嘟可；同人作品，非官方。
- 关键词：温暖、可爱、轻快、可信。主视觉用「可莉红 → 玫瑰粉 → 四叶草金」的流动渐变，呼应可莉/嘟嘟可主题。
- Logo 语义：白奶油色嘟嘟可（长耳、豆豆眼、长尾）+ 奶油聊天气泡 + 四叶草；adaptive icon 使用 PNG 前景层。
- 不做花哨插画；用留白、柔和大圆角、渐变光晕、克制的动效营造高级感。

## 色彩（自定义品牌色，动态取色可选）

Light：
- primary #4F46E5 · onPrimary #FFFFFF · primaryContainer #E0E7FF · onPrimaryContainer #1E1B4B
- secondary #0891B2 · secondaryContainer #CFFAFE · onSecondaryContainer #083344
- tertiary #F59E0B · tertiaryContainer #FEF3C7
- background #F7F6FF · surface #FFFFFF · surfaceVariant #F1F0F8 · onSurface #1B1B25 · onSurfaceVariant #5A5968
- outline #C9C7D6 · outlineVariant #E5E3EF · error #DC2626

Dark：
- primary #A5B4FC · onPrimary #1E1B4B · primaryContainer #3730A3 · onPrimaryContainer #E0E7FF
- secondary #67E8F9 · secondaryContainer #164E63 · background #0E0F1E · surface #15172A
- surfaceVariant #1F2238 · onSurface #ECEBFF · onSurfaceVariant #A9A7BD · outline #4A4A63 · error #F87171

渐变：`BrandGradient = LinearGradient(listOf(#4F46E5, #7C5CFC, #06B6D4))`，
头部英雄卡/状态开关/图标底可用；暗色下降饱和度、加透明度（0.85）。

支持「跟随系统取色」开关（dynamicColor，默认关，开启后用 `dynamicLightColorScheme`）。
主题模式：跟随系统 / 浅色 / 深色（设置里可切换，立即生效）。

## 字体与形状

- 字体：系统默认（Roboto / 厂商字体），不加外部字体（性能 + 中文）。
- Typography：displayLarge 40sp/Medium、headlineMedium 26sp/SemiBold、titleLarge 20sp/SemiBold、
  titleMedium 16sp/Medium、bodyLarge 16sp/Regular、bodyMedium 14sp、labelMedium 12sp。
  大标题 letterSpacing 约 -0.5sp。
- Shapes：extraSmall 8dp、small 12dp、medium 18dp、large 24dp、extraLarge 32dp；
  卡片默认 20dp 圆角，英雄卡 28dp，底部弹层顶部 28dp。
- 间距：页面水平 padding 20dp；卡片内 18–20dp；块间距 16–20dp；底部内容留出 navigation bar inset。

## 信息架构

底部导航 3 个 tab（NavigationBar，选中态 pill indicator + 柔和 tonal 背景）：
**首页**（Icons.Rounded.Home / 状态）、**记录**（Icons.Rounded.History / 列表）、**设置**（Icons.Rounded.Tune）。
「保活向导」从首页 checklist 和设置页进入（全屏二级页，带返回）。
所有页面 edge-to-edge + `Scaffold` insets 处理。顶部大标题（不用传统 TopAppBar 的居中小标题），
滚动时可选收起。

## 页面规格

### 1. 首页 Home
自上而下：
1. **Hero 卡**（28dp 圆角，竖向渐变，光晕装饰）：
   - 左上：嘟嘟可吉祥物（圆形 glass 底）+「嘟嘟通讯」+ 副标题「嘟嘟可传信 · iPhone 秒收」。
   - 中央：大号状态切换（Switch 或自定义胶囊按钮），开=「正在转发」，关=「已暂停」；
     下方一行小字：「服务运行中 · 今日已送达 N 条」/「点击开启转发」。
   - 右上角可放一个「保活」小按钮（Icons.Rounded.Bolt）跳转向导。
   - 渐变上的文字白色/近白，注意对比度。
2. **今日概览**：3 个统计胶囊（已送达 / 待重试 / 已过滤），数字大、标签小，点击「已过滤」可跳记录并筛选过滤态。
3. **配置检查卡**（仅未全部完成时显示）：标题「还差 N 步完成设置」+ 线性进度条；
   项目：短信权限、通知权限、Bark 服务器配置、电池优化、自启动。
   每项一行：图标气泡 + 标题 + 副标题 + 尾部「去设置/已开启」。点击执行对应动作
   （权限 launcher / 跳设置页 / 打开服务器配置）。
4. **最近一条**：卡片预览发送方、正文两行、时间、状态 chip；点击进记录详情。
5. **快捷操作**：两个 tonal 按钮「发送测试推送」「保活向导」；测试推送结果用 Snackbar（成功/失败原因）。
6. 空状态：未配置时用柔和插画式圆形图标 + 「先配置 Bark 服务器」主按钮。

### 2. 记录 History
- 顶部标题「转发记录」+ 右侧「⋯」菜单（清空记录）。
- 筛选 chips：全部 / 已送达 / 待重试(失败+待发) / 已过滤；右侧计数。
- 按日期 sticky header（今天/昨天/M月d日），LazyColumn：key=id、contentType=status。
- 记录卡：左侧发送方首字头像（按发送方 hash 从一组柔色里取色）、发送方、时间、SIM 标签、
  正文最多 2 行、右上状态 chip（SENT 绿 / FAILED 红 / PENDING 琥珀 / FILTERED 灰）。
  失败卡可展开显示错误原因。
- 点击 → ModalBottomSheet 详情：完整正文（可复制）、元信息、Bark 标题、错误、操作：
  复制、重新发送（成功后 sheet 内状态更新 + Snackbar）、删除。
- 空状态：圆形图标 +「还没有转发记录」+ 说明。
- 删除/清空要有确认对话框；清空后 Snackbar 带撤销？不要求撤销，确认即可。

### 3. 设置 Settings
分组卡片，每组标题小字 + 卡片：
1. **Bark 服务器**：服务器地址（示例 `https://api.day.app` 或 `http://10.0.0.8:8080`）、
   Device Key（密码样式，可显示/隐藏）、「测试连接」按钮（调用 forwardEngine.sendTestPush()，
   显示 loading + 结果行）、提示「支持自建 Bark，局域网 http 也可用」。
2. **推送样式**：标题模板（`{sender}` `{sim}` `{time}` 三个 chip 点击插入）、通知分组 group、
   声音 sound、级别 level（下拉：即时/时效性/静默/重要）、图标 URL、点击跳转 URL、
   归档 isArchive、自动复制 autoCopy、角标 badge 开关。
3. **转发规则**：发送者白名单（非空时只转发这些号码，逗号/换行分隔，Chip 展示）、
   发送者黑名单、关键词黑名单；说明匹配规则（号码后缀匹配、关键词包含）；「记录被过滤短信」开关。
4. **后台与可靠性**：前台常驻服务开关、开机自启开关、电池优化状态行（已优化/未优化 + 去设置）、
   「保活向导」入口、WorkManager 周期任务说明（每 15 分钟补发）。
5. **外观**：主题模式（跟随系统/浅色/深色 分段按钮）、跟随系统取色开关。
6. **关于**：版本号（BuildConfig.VERSION_NAME）、开源/隐私说明（短信内容仅发送到你配置的推送通道或 SMTP 服务器；
   本地保留最近 500 条记录，可清空）、使用帮助（复用保活向导 + 服务器配置提示）。
文本输入：OutlinedTextField，圆角 16dp；文本改动仅本地状态，`onFocusChanged`/ImeAction.Done 时写回 settings；
避免每个按键触发 commit。

### 4. 保活向导 KeepAliveGuide
- 顶部：设备卡（SystemNavigator.deviceLabel() + OemGuides.detect() 的 title/subtitle）。
- 步骤列表（编号圆点 + 标题 + 描述 + 可选操作按钮），已完成/未完成状态可基于权限探测
  （短信/通知/电池优化），但「自启动/锁定后台」无法检测 → 显示「已了解」勾选（本地 rememberSaveable，不持久化）。
- 底部说明 note 卡（灰色 tonal）：「为什么这些是正规方式」+ 明确说明不使用后台音频等黑科技，
  以及系统限制的诚实解释（如 ColorOS 深度休眠可能延迟，但短信到达时广播仍能唤醒）。
- 提供「打开电池优化设置」「打开自启动设置」「打开通知设置」「打开应用详情」按钮（SystemNavigator）。
- 针对类原生 Android / LineageOS 的文案要区分（无厂商自启动）。

## 交互与动效
- 状态开关：可用自定义 switch + 渐变 thumb；状态变化有 spring 动画 + 卡片光晕呼吸（很轻）。
- 数字统计：AnimatedContent 数字变化。
- 列表进入：fade+slide 轻微（不要每项都重动画）。
- Snackbar：操作反馈；错误用 errorContainer 色。
- 所有可点区域 ≥ 48dp；图标都有 contentDescription（装饰性图标 null 但语义由文本承担）。
- 支持深色、系统字体缩放（用 sp）、屏幕旋转（状态 rememberSaveable）。
- 动效克制：200–300ms，避免无限循环动画（除非常轻的状态光晕，且尊重 reduced motion 可不做）。

## 性能要求（来自 jetpack-compose-performance 技能）
- 只用 LazyColumn 渲染列表，提供 key + contentType；不嵌套可滚动容器。
- collectAsStateWithLifecycle；ViewModel 持有容器引用（AndroidViewModel），不要每屏 new repo。
- 避免在 composition 里做重计算/格式化（用 remember 或预计算模型）；不在 lazy item 内读 LocalContext（提到外层）。
- 不使用 `@Immutable` 标记含 List 的模型来「优化」；数据类本身足够。
- 动画属性用 lambda/graphicsLayer 延迟到绘制阶段。
- 页面首帧不阻塞：读 Flow 的初始空值先渲染骨架/空状态。

## 文案
全中文（简体），语气简洁友好，不用感叹号堆砌；错误信息给「原因 + 下一步」。
状态文案：正在转发 / 已暂停 / 已送达 / 待重试 / 已过滤 / 服务未运行。
