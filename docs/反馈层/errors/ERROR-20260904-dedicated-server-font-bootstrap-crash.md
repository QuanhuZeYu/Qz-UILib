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
