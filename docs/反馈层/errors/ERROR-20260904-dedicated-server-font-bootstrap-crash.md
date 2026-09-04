# ERROR-20260904-dedicated-server-font-bootstrap-crash.md

## 摘要

`CommonProxy.preInit`（`@SidedProxy` 的 serverSide 就是它）无条件调用 `FontService.initialize()`，
而该方法的第一步就是枚举系统字体。系统里没有可用字体时（issue #71：Alpine Linux 未装 fontconfig 或字体包），
AWT 在**构造字体管理器**阶段就抛 `RuntimeException: Fontconfig head is null`，异常沿 FML 生命周期上抛，
专用服务器直接起不来。修复按「两个契约」拆开：渲染骨架引导只属于客户端；CPU-only 文本测量与启动侧无关，
但必须能把环境级无字体降级成一次性可读失败。

## 现象

- 报告：`Qz-UILib` issue #71（附件 `crash-2026-08-27_05.11.56-server.txt`）。
- 环境：Alpine Linux / OpenJDK 25（Alpine 构建），292 个 mod 的整合包专用服务端；
  `qz_miner` 依赖 `qz_uilib`，因 `qz_uilib` 崩在 preInit 而整包无法启动。
- 关键栈（原样摘录）：

      java.lang.RuntimeException: Fontconfig head is null, check your fonts or fonts configuration
          at sun.awt.FontConfiguration.getVersion(FontConfiguration.java:1242)
          at sun.awt.X11FontManager.createFontConfiguration(X11FontManager.java:697)
          at sun.java2d.HeadlessGraphicsEnvironment.getAllFonts(HeadlessGraphicsEnvironment.java:76)
          at club.heiqi.uilib.font.FontResourceSnapshot.capture(FontResourceSnapshot.java:58)
          at club.heiqi.uilib.font.FontGenerationCandidate.prepare(FontGenerationCandidate.java:39)
          at club.heiqi.uilib.font.FontService.initialize(FontService.java:185)
          at club.heiqi.uilib.CommonProxy.preInit(CommonProxy.java:35)
          at club.heiqi.uilib.MyMod.preInit(MyMod.java:36)

- 注意 `HeadlessGraphicsEnvironment`：服务端 JVM 已经是 headless，仍然炸。headless 关掉的是窗口，
  不关掉字体后端对 fontconfig 的依赖。

## 根因

1. **契约混淆**：`initialize()` 建立的是**渲染**骨架（generation 发布、`GlyphPageManager.initialize()`、
   字形 worker 线程、`glyphPageManager::queueUpload` 上传回调），却放在服务端也会执行的公共代理里。
   服务端永远不会有 GL 上下文，这套东西在服务端没有任何消费方。
2. **权威缺位**：仓库里原本没有任何启动侧判定（唯一先例是 `EarlyMixins.buildMixinsForSide` 按
   `FMLLaunchHandler.side()` 分流 mixin）。所以只把调用挪到 `ClientProxy` 不够——
   `DefaultFontRendererAdapter` 有 11 处懒 `fontService.initialize()`，`DefaultTextMeasureService` 有
   1 处 `ensureLayoutRuntimeReady()`，下游 Mod 也能直接调 `FontService.initialize()`。门禁必须落在
   `FontService` 内部这一处，否则就是挪走一个调用点、留下面对同样的坑。
3. **环境假设**：快照层把 AWT 一定能枚举字体当作必然。真实情况是 AWT 字体子系统在零字体 Linux 上
   属于**类初始化级**不可用：`FontManagerFactory.getInstance()` 本身就抛，因此 `getAllFonts()`、
   `Font.createFont(...)`、`new Font(逻辑名, ...)` 全都抛同一个异常——不存在绕开枚举、只用逻辑字体的路线。
4. **失败半径**：引导期异常没有任何边界处理，直接进 FML 事件总线，于是一个可选增强能力（文本渲染）
   把宿主进程带走了。对照：同一条 candidate 链路在 reload/tick 路径上早有 `settleCandidateFailureLocked`
   （记一次日志 + 退避），只有**同步引导入口**没设防。

## 修复

1. 新增 `font/FontRuntimeEnvironment`（包内私有）作为启动环境判定的唯一权威：
   `allowsRenderBootstrap()` 仅在 `FMLLaunchHandler.side() == Side.SERVER` 时为 false；
   launch side 读不出来（单元测试、离线工具）时放行——判定不到不等于判定为服务端。
   同时提供 `isFontSubsystemUnavailable(Throwable)`：只按 AWT 字体管理器的文案识别
   （`Fontconfig head is null` 原文、含字体管理器类名的 `NoClassDefFoundError`、
   `ExceptionInInitializerError` 这类只有文案没有可用 cause 的形态）。
2. `FontService.initialize()` 引导前门禁：服务端跳过且不报错（一次 info 日志）。
3. `FontService.ensureLayoutRuntimeReady()`（CPU-only 测量入口，不限启动侧）把环境级失败转成一次性的
   可读 `IllegalStateException`，文案含具体补救动作（Alpine：`apk add fontconfig ttf-dejavu`；
   Debian/Ubuntu：`fonts-dejavu-core`），并缓存结论，不再每次测量都去枚举系统字体。
   字体文件损坏、数量超限等**真实缺陷仍原样抛出**，不降级。
4. `FontResourceSnapshot.capture()` 在 `getAllFonts()` 处只补一句可定位上下文并保留 cause，
   策略不在快照层做。
5. 引导调用点从 `CommonProxy.preInit` 移到 `ClientProxy.preInit`（保持晚于配置回灌、早于渲染监听的
   既有时序），`preInit 时序` 日志与注释同步改口径。
6. 客户端无字体环境**不做静默降级**：原版 MC 客户端自身启动期就要构造 AWT 字体，字体子系统死了
   原版也活不下来；静默渲染空白只会把可定位的崩溃换成不可定位的白屏。故仍上抛，但换成带补救动作的文案。

## 证据

- 回归锁 `src/test/java/club/heiqi/uilib/font/FontRuntimeEnvironmentTest`：7 个用例；全量 build
  `3825 tests / 0 failures / 0 errors / 2 skipped`（新增 7 个；2 个 skipped 是既有
  `LatexReferenceComparisonTest` 的参考 jar 门禁）。
- 四项负控，每项只杀一个用例，证明断言真的挂在被断言的东西上：
  - 删掉 `initialize()` 的侧别门禁 ⇒ `dedicatedServerMustNot...` 失败：
    `实际构建次数：expected:<0> but was:<1>`（这一步在报告者机器上就是崩溃点）。
  - 环境判定永不命中（= 修复前行为）⇒ `fontlessEnvironment...` 直接漏出
    `RuntimeException: Fontconfig head is null, check your fonts or fonts configuration`
    ——issue #71 的原文异常在 Windows 上经注入 seam 复现。
  - 环境判定永远命中（真错被误降级）⇒ `realFontDefectMustNotBeDowngraded...` 的 `assertSame` 失败。
  - 把 `FontService.getInstance().initialize()` 塞回 `CommonProxy` ⇒ 字节码结构锁失败。
- 服务端被一并拦掉的实害：`QzFontWorker-*` 与 `QzFontGenerationBuilder-*` 线程、
  `glyphPageManager::queueUpload` 上传回调、以及一次完整 generation 构建。
- 独立复算 `GlyphRuntimeTables` 常驻开销：34 个按码点索引的数组 x 1,114,112 槽 = **123.25 MiB**
  （byte 4 个 / float 2 / int 18 / long 2 / short 8，构造时全部 `Arrays.fill` 过一遍，是真实占用），
  与本仓 CHANGELOG 中 P0-B 记录的约 123MiB 口径吻合。

## 教训

- **`@SidedProxy` 的 serverSide 就是 `CommonProxy` 本身**：留在 `CommonProxy` 里的每一行都会在服务端跑。
  「公共」不等于「服务端安全」，判据是这件事在服务端有没有消费方。
- 门禁要落在能力权威的入口，不是落在调用点：11 处懒初始化面前，删掉 1 个调用点等于没删。
- 降级必须成对写清允许什么与拒绝什么：环境不可用 ⇒ 可读失败 + 不重试；真实缺陷 ⇒ 原样抛出 + 可重试。
  只写前者会把产品缺陷吃成环境问题。
- AWT 字体子系统全有或全无：`FontManagerFactory` 初始化失败后 `getAllFonts`/`createFont`/逻辑字体
  一起失效，所以无字体不是可以局部绕过的状态，只能选择崩或明确不可用。
- 崩溃报告里出现 `HeadlessGraphicsEnvironment` 不代表安全：headless 关掉的是窗口，不是字体后端。

## 遗留（本轮确认但不属于崩溃根因）

- `GlyphPageManager:67` 以字段初始化器直接 `new GlyphRuntimeTables()`，而 `FontService:36` 是饿汉单例，
  服务端 `ModernConfigBootstrap:112` 查询 `isInitialized()` 就会触发类初始化，于是**修复后服务端仍常驻
  约 123 MiB 的字形表数组**。修它需要把表改惰性或把单例改按需（都要动既有不变量），
  与 issue #71 的「起不来」不是一件事，单列待裁。
- 修复前服务端的字形 worker 线程没有任何关停路径（shutdown hook 只注册在 `ClientProxy`）；
  线程均为 daemon，故不会挂住服务器退出，只是白占。门禁后服务端不再创建它们。

## 同类服务端问题全清单（两路只读审计 + 一手抽查复核；证据等级见文末标注）

审计范围：UILib 620 个 main 源文件按 6 条线索穷尽；下游 `qz_miner` 292 个源文件按同样 6 条线索。
**下游结论：qz_miner 干净** —— 它对 UILib 的 14 处 import 全在 `ClientProxy` / `client/*` / `configGUI/*`
四个纯客户端文件里，服务端路径零触字体与 UI；因此 #71 的崩溃完全来自 UILib 自身的 preInit。

### 本轮一并修掉的（与 #71 同形，无需产品决策）

| 位置 | 问题 | 处理 |
| --- | --- | --- |
| `CommonProxy.preInit` | 客户端 devtools 自检端点集注册在服务端代理里：类加载即起常驻线程
  `QzNetSelfCheckTimeout`（`NetSelfCheckRegistry:70-78` 的静态 executor），并在服务端注册 1 channel +
  6 fetch + 1 stream + 3 store 与 3 个订阅，而这些端点唯一驱动者是客户端命令 | 注册移到
  `ClientProxy.preInit`（紧邻其驱动者 `DevToolsClientBootstrap`），并扩写结构锁：服务端代理字节码
  不得再出现 `NetRuntimeSelfChecks` |
| `ChatInputSurface.openUrl` | 只 `catch (Exception)`：无桌面会话时 AWT 桌面子系统集成抛的是
  `Error`（`InternalError`/`UnsatisfiedLinkError`），点一次聊天链接就能带走客户端 | 改为
  `catch (Exception | Error)` —— 与 #71 同一课：AWT 的失败形态不限于 Exception |

### 需要你裁的（都是「崩启动/崩运行」级别，但修法涉及契约或 API 面）

- **A1 越界字体配置打死 `FontService.<clinit>`（崩启动，条件触发，两侧都崩）**。链路一手复核过：
  `ModernConfigBootstrap:106` 把 yaml 值原样写进 `FontConfig.*` → `:112` 触碰 `FontService.getInstance()`
  → `FontService.<clinit>` → `new GlyphPageManager()` → `GlyphPageManager:68` 字段初始化器
  `FontRuntimeSettings.capture()` → 构造校验对 `awtCharSize<=0`/`charSize<=0`/`lerpMode` 越界抛
  `IllegalArgumentException` ⇒ `ExceptionInInitializerError`，**且此后该 JVM 内每次 `getInstance()`
  都变 `NoClassDefFoundError`**（比 #71 更持久：连可读文案都没了）。
  根因是磁盘路径只做类型校验、值域校验只在草稿/UI 路径生效（`DraftBuffer.validateField` 是
  `field.constraints()` 的唯一消费点；`ConfigManager.bootstrap` 用 `DraftValidator.noop()`）。
  缺键安全（`Authority` 注入默认值），只有「键在但值非法」触发。两个候选修法：
  (a) 磁盘路径也跑同一套声明式值域校验，越界 ⇒ `ConfigException` ⇒ 现有 `ModernConfigBootstrap:99`
  已经会「记 ERROR + 保留调用前值 + 不中断启动」；代价是范围按 UI 滑块界（如 `charSize` 上限 72），
  今天能用的 `charSize: 90` 会被整文件拒绝。 (b) 只在「产品无法表示」的域上兜底（capture 失败 ⇒
  警告 + 回上一套合法值），保留 UI 界外可用。两者语义不同，要你定哪个是承诺。
- **A2 未知 `netTransport` 裸抛（崩启动，条件触发）**：`NetTransportFactory:33` 对不认识的值直接
  `IllegalArgumentException`，`CommonProxy.preInit` 无兜底 ⇒ 一个拼错的可选字段拖死 292 mod 服务器；
  CHOICE 字段错型时 `Authority` 返回哨兵 `-1` 并被 `String.valueOf` 成字符串，也会走到这条。
  可选：响亮回退默认 `vanilla`，或按 A1(a) 一起被配置层拒掉。要的是「一个错字不该带走宿主」这条原则。
- **B2 稳定文本测量 API 在零字体服务端仍抛**（现文案可读、且不再崩启动）：`DefaultTextMeasureService`
  与 `DefaultFontRendererAdapter` 的测量入口、`FontService` 测量族，三组都列在
  `docs/使用文档/v4.x-LTS-稳定API清单.md`。要么给无字体回退度量，要么在 LTS 清单把该能力标为
  「条件可用」。这是产品承诺，不擅自改。
- **B4 渲染入口拿 `isInitialized()` 当门**：门禁后该值在服务端永假，`initializeForRender` 会空转后
  继续走 GL。需要「渲染不可用」的可判据（= 把 `FontRuntimeEnvironment` 或等价的渲染可用性判据公开）；
  新增公开方法是公共 API 变更，按规范先问你。顺带：同一判据公开后可让 `ModernConfigBootstrap`
  在服务端根本不触碰 `FontService`，一并消掉下面 C1 的 123 MiB。
- **B5 服务端 CLIENT 队列无界增长（静默失效 + 泄漏）**：`NetService.mainThreadExecutor()` 与
  `runOnMainThread(NetSide.CLIENT, ...)` 是公共 API，但全仓 CLIENT 队列唯一排空点在
  `ForgeMainThreadDispatcherBridge.onClientTick`，专用服务端永不 post 该事件 ⇒ 回调永不执行且队列无界。
  建议：入队即判可用，服务端要么拒绝要么改投服务端主线程，并给队列上界。

### 已查且不构成服务端风险的（记下来免得重复查）

- mixin 侧别：`EarlyMixins.buildMixinsForSide` 已按 launch side 分流，7 个客户端专属 mixin 只在 CLIENT
  加，服务端只加 `network.MixinNetHandlerPlayServer`（其 import 全是服务端类）。
- `@Mod(guiFactory=...)`：字符串 hint，取用点在客户端配置 GUI；报告者的栈证明元数据阶段已过。
- `VanillaPacketBuilders:141`、`FontGenerationBuildRequest:104`、`font/util/FontRegistry:138` 三处
  客户端类触碰都是 `Class.forName` + catch Throwable ⇒ 服务端安全。
- `font/util/FontRegistry`（含另一处 `getAllFonts()`）全仓零实例化，是遗留死壳（D-4 待删）。
- `club.heiqi.config/**` 与 scene core（`ui/scene`、`ui/reactive`、`ui/base`、`ui/event`）零
  MC/LWJGL/AWT import；`util/GlAttribDepth` 两个公共入口都 `catch (Throwable)` 并只警告一次；
  `ui/hud/api/ClientHudService` 用反射 Holder + 明确「服务端不得调用」文案 —— 后者是本仓 side
  契约应有的范式，其余公共客户端 API 建议照它整改（全仓 `@SideOnly` 目前出现 0 次）。
- 16 个 `@SubscribeEvent` 逐个核对：服务端会 post 的事件处理器只用 `EntityPlayerMP`/`NetworkManager`/
  `NetHandlerPlayServer` 等服务端类型。`UiHudRenderListener.onWorldUnload` 虽挂在服务端也 post 的
  `WorldEvent` 上，但它只注册在客户端总线且内部再判 `world.isRemote`。
- `mixins.qz_uilib.late.json` 是死配置（无 `ILateMixinLoader`/包内类引用点），`mixins.qz_uilib.json`
  指向不存在的 `mixin.center` 包且三个列表全空 —— 往里放东西不会生效，配置陷阱，未动。
- 字体线程（`QzFontWorker-*`、`QzFontGenerationBuilder-*`）与 devtools 线程均 `setDaemon(true)`，
  服务端即便误起也不会挂住退出。
- 版本面（下游）：`qz_miner` 的 `@Mod` 运行时门是 `qz_uilib@[4.7.0,5.0.0)`，而 `5.3.0` 更新日志写
  「最低 4.8.0」——下限没跟上，整合包仍可把 qz_miner 配到会崩服务端的旧 UILib。要么把 #71 修复
  backport 到 4.7.x，要么把 qz_miner 的下限抬到含修复的版本（属下游仓与版本策略，交你决定）。

证据等级标注（免得把二手当一手）：本节内 `@SubscribeEvent` 的 16 个处理器逐个核对、
`club/heiqi/config/**` 与 scene core 的 import 面扫描、`GlAttribDepth`/`ClientHudService` 两条
**出自审计子代理，我没有逐行复核**，按二手对待；其余条目（mixin 分流、反射三处、
`FontRegistry` 零实例化、线程 daemon 标志、late 配置无引用点、A1 崩溃链、
`dispatcher.reset()` 不碰字符页）我逐条读过代码或跑过 grep/实验。
