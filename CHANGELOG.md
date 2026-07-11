# CHANGELOG

本项目采用 [Keep a Changelog](https://keepachangelog.com/) 风格记录变更，版本号遵循
`主.次.修订[-标签]` 格式：主版本号变更代表破坏性 API 调整，次版本号代表能力扩展，
修订号代表行为修复或文档调整。

## [4.5.3-beta-4] - 2026-07-11

结构化列表多选：`List<CHOICE>` 默认渲染为受控 checkbox，已知值按 schema 顺序去重，
未知字符串显示失效标识且只允许删除；非法 passthrough 值继续由严格保存校验阻断写盘。
详细说明见 `.changelogs/4.5.3-beta-4.md`。本次只执行本地 beta 制品验证，不执行
merge、push、tag 或 release，也不修改 Qz-Miner。

### 新增

- `StructuredListModel` choice 显示、选中与不可变更新纯数据 helper
- `StructuredListFieldRenderer` 的 `List<CHOICE>` keyed 受控 checkbox 编辑器
- schema/model/runtime/scene 多选、失效值、输入、reset/reload 与写盘回归测试

### 兼容性

- 保留 `List<String>` 原分支和其它复杂列表 unsupported 行为
- config core 零 scene 依赖；不修改生产 schema、checkbox、router 或 row lineage

## [4.5.3-beta-3] - 2026-07-11

输入体验修复：结构化列表逐字符编辑保持 keyed row/input/focus，中文 IME 经通用
`McScreenBridge` 接入完整 String 文本桥。详细说明见 `.changelogs/4.5.3-beta-3.md`。
本次只执行本地 beta 制品验证，不执行 tag、push 或 release，也不修改 Qz-Miner。

### 修复

- StructuredList 所有内部编辑先更新 renderer 本地 rows，再通知 adapter，identity 逐字符修改不重建节点
- reset/reload 增加有限 identity lineage：当前唯一 identity 优先，历史唯一 identity 次之，空/重复/歧义 fail-closed
- 通用 `McScreenBridge` 幂等注册 `SceneLwjgl3ifyTextBridge`，失败降级，关闭 finally 注销并复位 external text mode
- 文本桥注册前校验 add/remove 与 begin/end 完整配对；半完成副作用事务独立回滚并保留失败步骤重试
- lwjgl3ify 可用性探测与注册统一锚定桥 classloader，并禁止探测触发类初始化
- devtools 页面移除手工 bridge owner，避免同屏双注册和双输入

### 诊断边界

- 旧日志 `Qz-Miner/run/client/logs/fml-client-latest.log:15313-15321` 是 beta-2 修复前基线，仅含 ROW/COLUMN grow WARN，不能证明 beta-3 行为
- 代码诊断：生产 Config 之前未注册 text bridge 是中文 IME 根因；renderer 本地 keyed rows 未在 adapter 回调前更新是确定的一键失焦根因
- ROW/COLUMN grow WARN 未顺手改布局，留作修复后实机复验项

## [4.5.3-beta-2] - 2026-07-11

正式结构化列表能力：递归 `ValueSpec` schema、严格 Authority/Draft/YAML、未知 member 保留、
嵌套 validator 错误路径，以及默认 scene keyed 列表编辑器。详细说明见
`.changelogs/4.5.3-beta-2.md`。本次只登记版本说明，不执行 tag、push 或 release。

### 新增

- `ValueKind` / `ValueSpec` / `Values` 与 `FieldType.STRUCTURED_LIST`
- `SectionSpec.Builder.structuredList`，表达 `List<Object{id:String,members:List<String>}>`
- 默认 renderer 的增删、上移/下移、标量与 `List<String>` member 编辑、reset/error 映射
- 结构化列表 schema/runtime/model/scene 回归测试

### 修复

- 保留旧五种字段类型与旧 `FieldSpec` 构造器；修复旧 `CHOICE` 兼容映射
- keyed 列表操作栏与 `forEach` 独占容器分离，避免 reconcile 丢失操作按钮
- 严格拒绝嵌套错误类型并保留未知对象 member 的 YAML round-trip
- 修复结构化列表 reset/reload 按位置复用 key；支持声明唯一 identity，重复/空 identity fail-closed
- 修复 `List<String>` 后代错误显示与排序/删除后的动态路径映射；补齐 renderer 交互和事务零提交证据

### 兼容性

- 不迁移现有调用方；`config.schema` / `config.runtime` 仍零 scene 依赖
- 连续 beta 预发布，稳定公共能力目标仍为 `4.6.0`

## [4.5.3-beta-1] - 2026-07-10

预发布修订（连续 beta）：草稿所有权 fail-closed、I3 展示初始化、**同 classloader 参与式 writer** 写前检测、UI 主线程契约、从磁盘显式 reload、配置回灌全局协调器与严格 disk 类型；**批次交换派发 / 简化线性化协调器 / section raw overlay 保留**。
**不是稳定 4.5.3**；稳定公共能力目标 **4.6.0**。详细说明见 `.changelogs/4.5.3-beta-1.md`。

### 新增

- `ConfigFileSnapshot` + `ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD` + `ConfigConflictException`
- save/flushRaw 参与式写前检测（精确字节 + 静态 monitor）；`reloadDraftFromDisk()` 三阶段
- `ModernConfigApplyCoordinator`：单一 monitor 线性化（无 lease/wait；同线程 reentrant register fail-fast）+ no-spin
- `MainThreadDispatcher` 真正批次交换（lock+ArrayDeque swap）+ per-side drain owner CAS + RuntimeException 隔离 + AssertionError/ErrorSink Assertion 尾重排
- `ConfigException.Category`；section raw overlay（**仅 MAP**；scalar/list section fail-closed）
- `DraftSignalAdapter` owner 线程封闭；`SchemaReplaceCompatibility`
- FontSort frozen discovered snapshot、canonical merge、筛选投影、全局索引输入与筛选拖拽提交边界

### 修复

- foreign/unbound draft 不得写任意 manager；Authority/YAML 零副作用
- save/flush 冻结 expected 基线；reload 推进 expected 后旧 prepared 结构化冲突
- disk / legacy raw 严格 NodeType；SIMPLE_LIST 严格拒绝 null 元素；schema section 未知子树 roundtrip
- schema section 为 scalar/list 时 bootstrap/reload fail-closed（禁止静默默认覆盖）
- reload 错误分类走 `Category`/`Reason`，ConfigManager/UI 禁止英文 substring 匹配
- 测试 hook AssertionError 回传且无条件释放 enqueueOwner；Forge bridge 真实 START/END 事件仅 END drain
- Atomic write 不承诺 fsync；`writeAll` deprecated 非参与式旁路，生产无调用（调用计数守卫）
- render 期 prefill 零副作用（局部只读）；reload 走磁盘重载而非仅 openDraft 旧 Authority
- fontSort 不再因 coordinator initial apply 丢失打开时字体列表；MOVE/CANCEL/no-op 不写草稿，合法 UP/索引/恢复默认才整体提交

### 兼容性

- 公共签名保留，但保存行为收紧：`DraftBuffer.from(authority)` 产生的 unowned draft，任意 `manager.save` 均返回 `DRAFT_OWNER_MISMATCH` 且零副作用；保存调用方迁移到 `manager.openDraft()`；`flushRaw` 仍 throws ConfigException（冲突为子类）
- 非正式 tag；对比基线 4.5.2

---

## [4.5.2] - 2026-07-10


修订补丁：配置保存增加可选提交前校验钩子（`DraftView` + `DraftValidator`）并接入 UI（向后兼容 patch 例外）。
详细说明见 `.changelogs/4.5.2.md`。

### 新增

- `DraftView` / `DraftValidator.validate(DraftView)` + 三参 bootstrap；二参委托 `noop()`
- 提交错误接入 `DraftSignalAdapter` / `ConfigScreen` 反馈摘要
- `ValidationResult.merge` / `summary`；fail-closed（`_config`）

### 修复

- 保存改为三阶段乐观事务；stale/并发冲突返回 INVALID 并保留实际修改，validator 全程锁外。
- NUMBER 字符串统一规范化为 Double；SIMPLE_LIST 保存期严格校验 `List<String>`；Authority/Draft prepared Map 在写盘后仅引用交换。
- 持久化锁外序列化、锁内 temp replace；ATOMIC_MOVE 不可用时为非严格原子 fallback。
- INVALID/成功后 UI 全字段 Signal 回读；同一 manager 的 BATCH_SAVE 通知期跨线程保存拒绝与监听器异常隔离。

### 兼容性

- 无公共 API 破坏；仅新增可选钩子
- 对比：[`4.5.1...4.5.2`](https://github.com/QuanhuZeYu/Qz-UILib/compare/4.5.1...4.5.2)

---

## [4.5.1] - 2026-07-10

修订补丁：修复宿主 scissor 基线与 clip 栈协作（issue #63，小地图等 HUD 叠用时字符/几何裁切失效）。
详细说明见 `.changelogs/4.5.1.md`。

### 修复

- 上下文入口捕获宿主 scissor/stencil；首层 clip 求交；栈空幂等恢复基线。
- 静态 FBO/deferred clear 与实例 restore 语义分离。
- clip 边界 flush deferred text batch。
- 新增 render 层 `ClipStackHostBaselineTest` 回归。

### 兼容性

- 无公共 API 破坏；更尊重宿主进入 UI 前的 scissor。
- 对比：[`4.5.0...4.5.1`](https://github.com/QuanhuZeYu/Qz-UILib/compare/4.5.0...4.5.1)。

---

## [4.5.0] - 2026-07-10

**重要重构发布。** scene 新栈成为 UI 主路径；HTML-like 旧栈与 Forge 配置模板 / 远程配置同步移除；
配置页切换为 Schema + `ConfigUI` + scene 控件。详细说明见 `.changelogs/4.5.0.md`。

### 新增 / 重构

- scene 新栈全链路：node / layout / paint / runtime / input / overlay / control / form / host。
- 声明式控件库（Primitive + 样式壳）、表单壳、配置 `ConfigScreen` + FieldRenderer。
- 本 mod modern 配置接入（YAML、`ModernConfigEntry`、保存回灌）。
- 宪章 I1–I12、控件契约 R1–R13 与结构门禁落地。

### 移除

- HTML-like document 业务栈大批源码与测试。
- `ForgeConfigTemplateScreen` 及远程配置同步相关 API。
- 通用 `ui.remote` 远程 HTML 门面当前不在源树（文档滞后项见项目交接）。

### 兼容性

- **破坏性**：依赖 document 栈或旧配置模板的接入方必须迁移到 scene + ConfigUI。
- 接入文档：`docs/使用文档/02-控件/配置页（ModernConfig）.md`。
- 对比：[`4.2.5...4.5.0`](https://github.com/QuanhuZeYu/Qz-UILib/compare/4.2.5...4.5.0)。

---

## [4.2.0] - 2026-06-09

第二个 4.x 稳定发布版本。保持 4.1.x 稳定 API 向后兼容，重点扩展浏览器语义、远程 UI、
远程配置同步、网络实时子层和 `/qzuilib test` 视觉矩阵，并切换到 GTNH 2.9 beta 开发依赖基线。

### 新增

- 远程 UI 会话运行时：新增内部 `RemoteUiProtocol`、`RemoteUiAssetStore`、
  `RemoteUiSessionManager`、`RemoteUiServerRuntime`、`RemoteUiClientRuntime` 与租约清理链路，
  远程页面 / 远程 HUD 的 stream、submit、close、expired 均携带并校验
  `sessionId + surfaceId + contentRevision`。
- 服务端权威远程配置页：新增 `ConfigSyncTarget`、`ConfigSyncCategorySpec`、
  `RemoteConfigDocumentPages`、`ConfigTemplateRemoteSyncController` 与服务端配置会话管理，支持
  Forge 配置模板通过远程页面同步和提交。
- 网络实时子层：`NetService.realtime(...)`、`NetRealtimeChannel`、`NetRealtimeMessage`、
  `NetRealtimeDropPolicy` 与传输层实时帧，为高频小二进制帧提供实验性通道。
- `/qzuilib test` 视觉优先矩阵：重建 DOM / CSS / Layout / Paint / Input / Controls /
  TextFont / Animation / RuntimeHost 等分组，接入 53 张核心视觉样例，并提供当前样例断言与
  一键全量断言。
- HTML-like 能力扩展：`DocumentNode.textContent` 读写、`input type=password/number`、
  textarea 软换行两级行模型、远程 CSS `background-image: url(...)` 单图解析。
- 脏子树布局缓存：支持静态 block / flex / table / inline-block / display:none 子树复用，
  并允许普通流位置变化后的整体平移复用。
- 运行时与视觉自动断言：补齐 DOM、CSS、Layout、Paint、Input、Controls、TextFont、Animation、
  RuntimeHost 多分组的机器诊断与短日志回写。

### 修改

- 远程页面和远程 HUD 对外 facade 保持不变，内部改为 session / surface / content revision 绑定，
  避免旧 stream、旧 submit、手动关闭后的 expired 回调污染当前页面。
- HTML-like 视觉遍历统一为普通树 + top-layer 根盒共享场景，paint、hit-test、scroll metrics、
  fixed containing block、clip chain 和 transform 运行态使用同一口径。
- `HtmlLikeDocumentWidgetTest` 按主题拆分为 Scroll、Drag、FocusKeyboard、LayoutCache、
  AnimationRuntime、Rendering、HitTest、HudRuntime、EventDispatch、InlineLayoutCache 等测试类。
- 长文本绘制裁剪新增 `DocumentTextPaintClipper`，减少被 overflow clip 裁掉的长单行文本提交量。
- 字体运行时高频诊断日志默认受 `Config.fontRuntimeDebug` 控制，避免淹没游戏内断言日志。
- 开发依赖基线同步到 GTNH `2.9.0-beta-1`，`gtnhsettingsconvention` 升级到 `2.0.25`，
  非平台硬依赖的整合包兼容依赖改为 non-publishable 配置。

### 修复

- 浏览器语义修复：DOM 同父移动、`removeChild` 返回值、`querySelector*` 文档根排除、
  `focusout` 事件、hover / active 状态传播、wheel 事件默认滚动前分发、布尔 `disabled`、
  margin collapse、flex min-content、table auto 列宽、absolute auto margin、fixed clip chain。
- top-layer / HUD / select 修复：select 弹层 detach 生命周期、transform 后弹层锚点、HUD top-layer
  后代预过滤、popup 关闭后 hover / cursor 刷新、运行态 transform 后滚轮和滚动条命中。
- 动画运行态修复：keyframe forwards fill 按 direction 与最终迭代奇偶写入终值，`display:none`
  中断运行中 transition 时派发 `transitioncancel`。
- 文本与控件修复：`textInput.preventDefault()` 阻止内置 input / textarea 改值，textarea stale
  visual line cache 越界保护，输入框 auto 高度和 caret / selection 绘制坐标修正。
- 绘制修复：transform 栈内禁用延迟文本批处理，避免文本 batch 使用屏幕坐标绕过父矩阵；
  host image 缺失资源保留 UILib 底色，不泄漏 Minecraft 紫黑 missing texture。
- 动态样式修复：挂载后的 `UiStyleSheet` 变更触发缓存失效，`UiStyleDeclaration.copyFrom(...)`
  对已挂载元素触发布局 / 绘制失效。
- `/qzuilib test` 的 `VIS-PAINT-005` top-layer 样例改为挂根后延迟注册，避免未挂载样例提前
  调用内部 top-layer API 后被 detached top-layer 剪枝清理。

### 测试

- 新增远程 UI runtime / protocol / asset / session、远程页面、远程 HUD、远程配置同步、
  网络实时帧、Forge 生命周期、浏览器语义和视觉矩阵相关测试。
- 补充 DocumentVisualTraversal、DocumentHitTestEngine、DocumentScrollState、DocumentPaintEngine、
  DocumentAnimationTimeline 与 HtmlLikeDocumentWidget 各主题回归测试。
- 发布前已验证：`git diff --check`、`./gradlew.bat --no-configuration-cache test`、
  `./gradlew.bat --no-configuration-cache --no-daemon -x test publishToMavenLocal`、
  `UiTestDocumentPageControllerTest` 与 `VIS-PAINT-005` 定向断言。

### 构建与发布

- `runClient21` 的 CodeChickenLib MCP mapping 目录改为启动前自动写入运行目录配置。
- 当前 `runClient21` 已解除 `BytePatternMatcher` 缺类；本地 GTNH 2.9 beta smoke 仍可能受
  `ServerUtilities 2.3.0` 与 `Et-Futurum-Requiem 2.6.40-GTNH` 第三方 mixin 冲突阻塞。
- 当前发布渠道仍为 JitPack + GTNH Maven。源码版本号由 Git tag / GTNH Gradle 推导，发布
  `4.2.0` 时应在最终提交上创建并推送 `4.2.0` tag。

---

## [4.1.0-LTS] - 2026-05-23

第一版长期支持版本。覆盖发布前 P0 / P1 / P2 阶段的全量审查与修补，公共 API 边界
确定，文档与实现完成对齐。后续 4.1.x 仅做兼容性修复，不引入破坏性变更。

### 新增

- 浏览器语义示例页扩展：补全 hover、focus、active、文本排版、滚动条与 ESC 默认行为
  等浏览器一致行为的展示与回归。
- 动画能力 Phase 2 / Phase 3：补齐 transform、box-shadow、backdrop-filter、cubic-bezier
  与 `steps()` 缓动；transition 通过 `DocumentTransitionSpec` 支持 per-property
  duration / delay / timing；keyframe 支持 `animation-direction`、无限迭代与
  `ElementNode.animate(...)` 命令式启动。新增 transitionstart / transitioncancel /
  animationstart / animationiteration 事件派发链路。
- 设置页核心控件四件套：复选框、单选组、滑块（含小数滑块自动附文本输入）、标签页。
  数值属性绑定支持自动滑条与文本输入兜底；`draggable=true` 元素默认应用
  `cursor: pointer`。
- HTML-like 语义元素：`document.a()` / `ul()` / `ol()` / `li()` / `img()` / `table()`
  提供最小可用语义闭环；`img` 支持 width / height 属性与远程位图缓存；`a[href]` 走
  片段跳转 + `setLinkActivationHandler(...)` 业务回调。
- 字体排序控件：可视化重排、分页、搜索、序号输入；写回到 Forge `Property` 列表。
- UI 框架结构审查展示页：以可滚动看板呈现分层链路、优先级、热区。
- 运行时自检页：在游戏内对 FontService reload / fallback / 异步线程拦截、
  ForgeConfigTemplate 冷构造、DocumentRemoteImageCache 关停做现场断言。失败立即抛出
  `IllegalStateException`，由 Minecraft 崩溃面板捕获完整堆栈。
- 远程位图缓存：`http(s)://` URL 通过 `DocumentRemoteImageCache` 异步下载并按 LRU 驱逐。
- LGPL v3 开源许可证。

### 修改

- 全部 god class 完成结构拆分：`UiRenderContext`、`ElementNode`、
  `DocumentLayoutEngine`、`DocumentAnimationTimeline`、`HtmlLikeDocumentWidget` 各拆为
  3-5 个协作类；layout helper 按 Flex / Table / Inline / Positioned / Text 分模块。
- 布局热路径：减少重复测量、提取测量缓存、关闭非必要的二次相对偏移计算。
- 公共 API 收口：诊断页工厂方法降为 package-private，仅 `/qzuilib test` 可调起；
  `internal` 包类加 `@apiNote` 标记 LTS 不承诺；`UiHudDocumentHost` 的钩子方法加
  `@apiNote 仅供框架内部 forge 事件钩子调用` 警告。
- HUD 文档层：`PASSIVE` / `INTERACTIVE` 两层语义在 javadoc 与文档中明确分列可见性
  与输入语义；INTERACTIVE 层只在 `GuiContainer` 子类宿主下且鼠标已释放时可交互。
- 字体重载链路：reload 拦截非渲染主线程调用，避免 worker 线程释放 GL 资源触发
  "No context is current" 致命崩溃。
- 文档体系重构为四条路线：使用文档（外部接入）、开发者文档（内部架构）、
  reviews（审查报告）、errors（错误记录）。
- README 默认提供英文版本，提供中文跳转。
- 默认控件附加 web 语义鼠标指针样式（pointer / text / move / not-allowed 等）。
- `flex align-items: baseline` 当前等价于 START，新增 `LOG.warn` 一次性提示。

### 修复

- 单行输入框强制 `white-space: nowrap` 修复光标错位。
- 字体排序还原至主配置页并修复拖拽时锁顶部条目。
- 滑动条拖拽释放后正确提交。
- 键盘默认行为取消语义（preventDefault / stopPropagation）。
- 语义展示页 focus 崩溃；hover 与文本排版的视觉细节。
- 字体生成调度器在重入时通过 `awaitTermination` + 代际隔离保证旧任务不会写入新
  `GlyphPageManager`；`GlyphPage` 零数据 buffer 不再跨实例共享。
- `FontShaderProgram.loadProgram` 在编译 / 链接异常时通过 try/finally 释放 vertex /
  fragment shader 与新建 program。
- `UiMainLayerSnapshotService` 增加 32 槽 snapshot 池上限与按帧驱逐策略，避免异常
  关屏导致 GL 纹理 / FBO 持续增长。
- `DocumentRemoteImageCache.trimCacheIfNeeded` 加 `AtomicBoolean` 守门防并发驱逐；
  FIFO 改为按 `lastAccessedAt` 最旧驱逐的 LRU 策略。
- `UiLayoutInvalidationRegistry` 改为显式 `LOCK` 对象 + 锁外触发 `invalidateLayoutTree`，
  避免持锁回调引发死锁。
- `CodepointTextCache` BMP 路径加 `synchronized` 保证并发可见性。
- `SystemDocumentCursorHost` 反射降级路径改为 `AtomicBoolean` + 一次性 `LOG.debug`，
  并修复字段声明顺序避免静态初始化 NPE。
- `UiInputService` / `UiNativeTextInputInspector` / `ForgeConfigTemplateScreen`
  反射 ignored 块改为按字段去重的 `LOG.debug` 一次性日志。

### 移除

- `DocumentLinkActivationEvent.markHandled()` / `isHandled()`：v4.0 已 `@Deprecated`，
  本版正式删除。改用 `preventDefault()` / `isDefaultPrevented()` 与浏览器原生事件保持
  一致。

### 资源生命周期

- 新增 `ClientProxy` 关停链路：JVM `Runtime.addShutdownHook` 先关停
  `DocumentRemoteImageCache` 再关停 `FontService`；客户端断连
  （`FMLNetworkEvent.ClientDisconnectionFromServerEvent`）触发 HUD 注册表清理。
- 三个内部线程池（`FontService`、`GlyphGenerationDispatcher`、
  `DocumentRemoteImageCache`）均提供显式 `shutdown()` + 2 秒 `awaitTermination`。
- `ShaderProgramSupport.compileShader` 在编译失败时通过 try/finally 调用
  `glDeleteShader`，避免 GL 对象泄漏。

### 测试

- 新增 `FlexLayoutHelperBoundaryTest` / `TableLayoutHelperBoundaryTest` /
  `InlineLayoutHelperBoundaryTest` / `PositionedLayoutHelperBoundaryTest`：覆盖
  helper 拆分后的负尺寸、嵌套、auto cross-size 边界用例。
- 新增 `FontServiceLayoutRuntimeSmokeTest`：覆盖 `ensureLayoutRuntimeReady`
  幂等性。
- ForgeConfigTemplate 冷构造、FontService reload 三场景由"运行时自检页"在真机
  GL context 下覆盖。

### 构建与发布

- `jitpack.yml` 补 `install: ./gradlew --no-configuration-cache --no-daemon -x test
  publishToMavenLocal` 步骤，验证 JitPack 真能完整跑通构建。
- 当前发布渠道：JitPack + GTNH Maven。Modrinth / CurseForge 项目 ID 暂留空，未来
  补丁版按需补全。

---

## [4.0.0] - [4.0.20-beta] 历史预发布

`4.0.0` ~ `4.0.20-beta` 为 4.0 系列的能力开发与 god class 拆分阶段，未对外承诺
LTS 稳定性。本仓库自 `4.1.0-LTS` 起开始按 LTS 标准维护。

[4.1.0-LTS]: https://github.com/QuanHu1995/Qz-UILib/releases/tag/4.1.0-LTS
[4.2.0]: https://github.com/QuanHu1995/Qz-UILib/releases/tag/4.2.0
