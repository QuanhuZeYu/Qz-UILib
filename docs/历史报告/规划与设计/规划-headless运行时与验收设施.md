# 规划-headless 运行时（Qt offscreen 式）与验收设施（立项草案）

**状态：** **已冻结**（2026-09-17，第三轮：§六 七个岔路已由用户裁定，见 §六）。M0 技术闸门已在本地实测打通（F7）；
本档自冻结起构成 M1 施工依据，施工中新出现的岔路回填 §六 并标注日期。
**定位（用户补充，2026-09-17）：** 对齐 **Qt `-platform offscreen`** 的形态——**同一份 UI 代码**在无游戏、无窗口的
进程里装配、布局、绘制，可脚本化注入输入、可导出像素。**首要用途是 agent 与开发者的快速迭代**
（不开游戏、改一行→秒级出图→看图判断），**测试/CI 回归是顺带用途，不是主用途**。
**目标仓：** Qz-UILib（branch `4.0`；MC 1.7.10 / Java 8 语法基线 / GTNH `2.9.0-beta-3`）。
**来源：** 用户发起「QzUILib 立项：完善 headless 设施，分辨率覆盖 360P~2K，不再采用纯软光栅，可用 OpenGL」，
补充口径「需要包含完整的 headless 纯代码输入（鼠标键盘等）方式」「非 CI 用途，主要类似 QT 的无头模式快速验证功能，
以及方便 agent 不开游戏快速渲染出图」。
**基线：** 本档全部事实为 2026-09-17 一手实测（探针脚本 + 只读命令），**未跑真机**。

## 一、用途与需求口径

| 编号 | 用途 | 场景 | 验收目标 |
|---|---|---|---|
| P1 | **agent 快速出图** | agent 改完 UI 代码，不开游戏，一条命令拿 PNG 自行判读 | 冷启动到出图 **≤ 3 s**（快路径）；页面/尺寸/输出可参数化 |
| P2 | **开发者快速验证功能** | 不启游戏验证布局、交互与观感 | 可指定页面与输入脚本；产物路径可预期；失败可诊断 |
| P3 | 回归验收（顺带） | 本地/CI 批量多分辨率冒烟 | 见 §四 B / §四 D |
| R1 | 完善 headless 设施 | — | §三 四件套 |
| R2 | 分辨率覆盖 360P~2K | — | §四 B |
| R3 | 不再纯软光栅，可用 OpenGL | — | §四 A |
| R4 | 完整纯代码输入（鼠标键盘等） | — | §四 C |

**与 Qt offscreen 的已知差距（诚实声明，非对齐项）：**

1. Qt offscreen 在 Linux **无需 X**；本仓 LWJGL2 路径在 Linux **必须 Xvfb**（F8）——本地 Windows agent 不受影响，
   只有无桌面的 Linux 服务器才需要；
2. Qt 有成熟的 `grab()` 与 `QTest` 输入注入；本仓要自建（§四 C/D）。

## 二、立项前一手核查事实

### F1 现有 headless 有两条互不相通的通道，都不覆盖「整帧」

- **字体出图通道**（`src/test/java/club/heiqi/uilib/font/render/software/`）：与生产共享 `TextLayoutService` /
  `GlyphGenerator` / `DefaultFontRendererAdapter.renderSegmentsToCollector` / `GlyphBatchCollector` /
  `GlyphPage`（真 skyline 槽位分配 + 真上传路径，只把 `GlApi` 换成 `SoftwareGlApi`），
  **唯一分叉在尾端**：真机 `FontBatchRenderer.flush` 走 GL，headless 走 `FontSoftwareRasterizer`（CPU 逐像素）。
- **scene 通道**（`ui/scene/testkit/ScenePaintCapture`）：`layout → paint → replay → RecordingRenderBackend`，
  **只记录 draw call，不产像素**。testkit 包注释明文：「变换后的最终像素位置属 GPU 顶点层，纯 JUnit mock
  backend 不可观测」。

### F2 帧驱动已平台无关，缺的只是「真后端 + 真上下文」

- `AbstractSceneHostWidget.render(int w, int h, UiRenderBackend ctx, int absX, int absY)` 接受任意后端
  （宿主子类只调 `super.render`，此处是唯一挂点）。
- `SceneHostAssembly.assemble(measurer, inputSource)` 是 runtime / layoutEngine / paintEngine / replayer / pipeline
  五件套唯一装配点；`inputSource` 可为 `null`（无输入退化模式）。
- `UiRenderBackend` 的**抽象方法只有 13 个**：`fillRect` / `drawSurface` / `drawBorder` / `pushClip` /
  `popClip` / `drawText`×2 / `pushGroupOpacity` / `popGroupOpacity` / `pushTransform` / `popTransform` /
  `pushTransformLayer` / `popTransformLayer`（源码逐行复核）。另有 7 个 `default` 方法：`scaled` /
  `publishTextDemand` / `drawImage` / `drawSurface`（分角圆角重载）/ `drawText`（两处 7 参重载）/ `drawSegments`。
  **后端可替换面很窄，是本立项最有利的结构事实**；但要留意 `drawImage` / `drawSegments` /
  `publishTextDemand` 都带**静默 no-op 兜底**——headless 后端不显式实现时，缺图与缺富文本不会报错，
  只会静默少内容（本仓反复踩的「能力探测静默降级」模式）。故出图完整性必须有显式判据，不能只看「没抛异常」。

### F3 输入事件模型完整，注入入口不完整

- `RawInputEvent`（`ui/scene/input/`）：`RawEventKind` = KEY / POINTER / TEXT 三类，已含 4 修饰键、
  `nativeKeyCode`、`nativeScanCode`、`wheelDelta`、`deltaX`/`deltaY`、`SceneMouseButton`（LEFT/RIGHT/MIDDLE/
  BUTTON_4/BUTTON_5/NONE）、`ScenePointerAction.CANCEL`（窗口失焦）。
- 生产链路：`PlatformInputSource.drainFrame()` → `SceneInputFrame` → `SceneRuntime.route`。
- 注入入口 `SceneInteractionHarness` 已覆盖 `click` / `press` / `release` / `pressReleaseAcrossFrames` /
  `moveTo` / `moveAt` / `scroll` / `pressKey` / `typeText` / `clickAt`；**消费者规模 261 处 / 65 个测试类**。
  **缺口**：按钮硬编码 `LEFT`、无修饰键、无 `KEY_RELEASED`/`REPEAT`、无右键/中键、无拖拽、无双击/三击、
  无横向滚轮、无 `CANCEL`（失焦）、无 IME/外部文本模式切换、时间戳恒 `1000L`（做不了长按与双击时间窗）。

### F4 测试 JVM 的 GL 现状（实测）

- 测试运行时 classpath **有** `com.github.GTNewHorizons:lwjgl3ify:3.0.31`（jar 名 `lwjgl3ify-3.0.31-dev.jar`），提供
  `org.lwjgl.opengl.Display|GLContext|PixelFormat|ContextCapabilities`、`org.lwjglx.*`、
  `org.lwjgl.input.Keyboard|Mouse`。
- **没有 `org.lwjgl.opengl.GL11`**（LWJGL2 主 API 不在 test classpath），也没有 LWJGL3 实体
  （`org.lwjgl.glfw.GLFW`、`org.lwjgl.opengl.GL` 均 `ClassNotFoundException`）
  → **生产渲染代码在测试 JVM 里链接不上**。
- lwjgl3ify 的 `org.lwjgl.* → org.lwjglx.*` redirect 由 RFB transformer + FML coremod 在启动期织入，
  **不是可以直接丢进 test classpath 的替代品**；3.x 的 `Display` 已改 SDL3 后端。
- LWJGL2 制品已在本地 Gradle 缓存（项目经 MC 依赖已拉取，可离线使用）：
  `org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209`、`lwjgl_util:2.9.4-nightly-20150209`、
  `lwjgl-platform:2.9.4-nightly-20150209:natives-{windows,linux,osx}`。
- 测试 JVM 为 JDK 17（RFG + Jabel 编 Java 8 字节码），`maxHeapSize = 1024m`。

### F5 CI 环境（一手核实 GTNH 共享工作流 pin `8d2e9d2`）

- 跑测试的步骤是
  `xvfb-run --server-args="-screen 0 1366x768x24" ./gradlew --build-cache --info --stacktrace build`，
  预装 `mesa-utils xvfb x11-xserver-utils`。
- 即 **CI 的 `test` 天生有 DISPLAY + Mesa(llvmpipe)**；但 **Xvfb 屏幕固定 1366×768×24**，低于 2K 目标。
- 硬约束：**分辨率覆盖必须走 FBO 离屏**（`GL_MAX_TEXTURE_SIZE` 量级足够），**不得依赖窗口或默认 framebuffer 尺寸**。
- 注意：CI 方案在本轮重定位后**降级为可选**（P3 顺带用途），不再是立项主驱动。

### F6 现有软光栅的真实代价与缺口（实测 + 历史）

- **规模实测**：`build/reports/` 现存 **191 张 PNG / 50.1 MPx**；单张最大 `2536×2733 = 6.93 MPx`（`@4x` 放大图）。
- 即 CPU 软光栅**已经在跑比单个 2K 帧（3.69 MPx）更大的画布** → **性能不是本次升级的主因**。
- 主因是**保真度面**：软光栅只覆盖字形 quad（`FontSoftwareRasterizer` 类头自述「多抽头 AA 与 smoothstep
  属真机 shader 的抗锯齿近似，软件侧默认不做」）；scene 侧的圆角 band、裁剪/stencil、group opacity、
  backdrop 玻璃快照链**一条都不覆盖**；顶点层变换在 mock 后端不可观测。
- 历史实证：`ERROR-20260905-software-rasterizer-half-quad-rotated-sampling`（半 quad 180° 旋转采样致全部历史
  出图不可读）；`ERROR-20260818-overlay-toast-full-width-and-top-align`（headless 全绿而真机两处显示缺陷）；
  `docs/反馈层/踩坑记录.md`「测试盲区：为什么 headless 全绿却真机翻车」。

### F7 M0 技术闸门已本地实测通过（2026-09-17）

在**真实 gradle test JVM**（JDK 17.0.19，与 CI 同版本）里，用 init script 临时注入 LWJGL2 依赖与 natives
（**不改仓库构建文件**；探针测试类用完即删），测得：

| 观测项 | 结果 |
|---|---|
| `Display.create(PixelFormat stencil=8)` | OK（约 0.3~0.4 s） |
| `GL_VERSION` / `GL_RENDERER` | `4.6.0 NVIDIA` / `RTX 5070 Ti Laptop GPU` |
| `GL_STENCIL_BITS` | 8 |
| `1920×1080` / `2560×1440` FBO | `GL_FRAMEBUFFER_COMPLETE`（36053） |
| **`UiRenderContext` 构造** | **OK**（无需 MC 运行环境） |
| **生产 `UiRenderContext.fillRect` 画到自建 FBO** | **OK**；读回中心像素 `33,66,cc,ff` = 传入的 `0xFF3366CC` |
| 端到端（建上下文 + 2K FBO + 绘制 + `glReadPixels` + PNG 编码） | **534 ms**（读回 4 ms、PNG 编码 97 ms） |

结论：**「不再纯软光栅、改用 OpenGL」技术可行，且生产渲染后端零改动即可复用**。

### F8 Linux / CI 侧的 GL 边界（调研核实，2026-09-17）

- LWJGL2 的 Linux native（`liblwjgl64.so`）动态依赖 `libX11.so.6` / `libGL.so.1` / `libXrandr` /
  `libXcursor` / `libXxf86vm`，并引用 `XOpenDisplay` + `glXGetProcAddress`：**只有 GLX 一条通路**，
  无 EGL/OSMesa 后端 → **Linux（含 CI）必须有 X server**；既有 CI 已用 `xvfb-run`（F5）。
- LWJGL2 的三种「无窗口」手段都不解决 Linux：`Display` 无隐藏窗口开关；`Display.setParent(Canvas)` 仍要真实
  X 窗口；`Pbuffer` 是已弃用的 GLX pbuffer，同样要 X。
- **线程约束**：GL 调用必须固定在创建上下文的那个线程上（JUnit 测试线程即主线程，天然满足）。
- **反证澄清**：把 `lwjgl3ify` 当普通依赖丢进 test classpath 不可行（redirect 需 RFB system classloader +
  UniMixins 全套 JVM 参数，dev jar 内含 446 个 `org.lwjglx` 类）。真无 X 的兜底只有 LWJGL3 null 平台 + OSMesa
  （需 `libosmesa6`，与生产 LWJGL2 API 面不兼容），**不作主路径**。
- **生态先例**：`headlesshq/mc-runtime-test`（HeadlessMC + Xvfb 跑 1.7.10 客户端）——「Xvfb + Mesa 跑 1.7.10 渲染」
  是通行做法。
- **保真上限**：软件光栅器（llvmpipe / OSMesa / SwiftShader）本身也是 native 库，与真 GPU 驱动在纹理过滤、
  混合精度、多重采样上存在实现差异；**硬件级保真只有真 GPU**——这是 §五 分层的依据。

### F9 不开游戏能渲染到什么程度（实测：MC 依赖分布）

| 域 | 类数 | 含 `net.minecraft` |
|---|---|---|
| `ui/scene/*`（node/layout/paint/text/theme/form/overlay/image/control） | 137 | **0** |
| `ui/render`（圆角 / clip / backdrop / 快照 / 渲染上下文） | 40 | **0** |
| `ui/hud` | 24 | **0** |
| `ui/text` + `ui/markdown` + `ui/base` | 20 | **0** |
| `font` | 126 | 3（`FontRegistry` / `FontGenerationBuildRequest` / `MarkdownBlockParser`） |
| `internal/devtools/playground` | 16 | 2（仅 `TestPlaygroundEntry` / `TestPlaygroundScreen` 两个宿主入口） |
| `internal/chat3` | 47 | 14 |
| `client` | 14 | 4（HUD 监听与环境） |

- **整个 scene 栈 + 渲染栈 + HUD + 文本栈零 MC 依赖** → 可 headless 出图的面远大于「只渲染 playground 页」。
- `TestPlaygroundHost` 已在 **main 域**（`extends AbstractSceneHostWidget`，构造只吃 `PlatformInputSource`），
  可直接作为 headless 宿主骨架。

### F10 「快」的真正瓶颈不是 GL（实测）

- GL 侧（F7）：上下文 + 2K FBO + 绘制 + 读回 + PNG = **534 ms**（读回 4 ms、PNG 97 ms）。
- 而走 `gradlew test --tests ...` 的实测墙钟为 **12~22 s**（GL 探针 12 s、`MarkdownSoftwareRenderTest` 22.2 s），
  **Gradle 配置与编译是大头，GL 占比不到 5%**。
- → 「快速出图」的关键是**绕开每次启动的 Gradle 开销**（一次性导出 classpath 后直启 JVM，或常驻进程），
  而不是优化渲染本身。
- 启动侧另有两笔**一手核实**的固定成本（`ERROR-20260904-dedicated-server-font-bootstrap-crash`）：
  - `FontService.getInstance()` 一次调用即常驻 **150.12 MiB**（字形表 123.25 MiB + worker 侧 `long[]` 等）；
  - AWT 字体子系统**全有或全无**：零 fontconfig 环境（Alpine / 精简容器）在 `FontManagerFactory` 初始化即抛
    `Fontconfig head is null`，`getAllFonts` / `createFont` / 逻辑字体一起失效，**没有绕过路径**。
- 这两笔成本**只付一次**的性质，支持「常驻进程 + 请求出图」作为 agent 工作流的目标形态（§六-3）。

### F11 运行期 classpath 里没有 LWJGL2（实测，2026-09-17）

用 init script 探针（`--no-configuration-cache`，不改仓库）打印四个 configuration 的文件名：

| configuration | 文件数 | 含 lwjgl 的文件 |
|---|---|---|
| `compileClasspath` | 115 | `lwjgl-2.9.4-nightly-20150209.jar`、`lwjgl_util-2.9.4-nightly-20150209.jar`、`lwjgl-platform-…-natives-{linux,osx,windows}.jar`、`librarylwjglopenal-20100824.jar`、`lwjgl3ify-3.0.31-{api,dev}.jar` |
| `testCompileClasspath` | 63 | 同上（除 `-api`） |
| `runtimeClasspath` | 100 | 仅 `librarylwjglopenal-20100824.jar`、`lwjgl3ify-3.0.31-dev.jar` |
| `testRuntimeClasspath` | 105 | 仅 `librarylwjglopenal-20100824.jar`、`lwjgl3ify-3.0.31-dev.jar` |

两条结论：

1. **编译期零改动成立**：生产渲染用的 `org.lwjgl.opengl.GL11` 在 `compileClasspath` 与 `testCompileClasspath` **都已在**，
   落 main 域或 test 域都**不需要新增编译依赖**；
2. **运行期不成立**：LWJGL2 主 jar 与 natives **不在任何 runtime classpath 上**（真机由 MC 客户端 / lwjgl3ify 供给）——
   headless 直启必须自带补充 classpath（LWJGL2 + natives）。这正是 M0 探针当初必须用 init script 注入的原因，
   也是 §六-2 与 M1 的必做项，而不是可选项。

### F12 打包链形状（实测，2026-09-17）

`tasks.withType(AbstractArchiveTask)` 实测出 7 个产物任务，主线是：

```
jar (classifier dev-preshadow, src/main/java)
  → shadowJar (classifier dev, ShadowJar ← Jar；输入含 main / test / mcLauncher / patchedMc / injectedTags)
      → reobfJar (发布 jar；输入 = 上面的 dev jar)
```

| 任务 | 类型 | 产物 |
|---|---|---|
| `jar` | `org.gradle.api.tasks.bundling.Jar` | `…-dev-preshadow.jar` |
| `shadowJar` | `com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar`（继承 `Jar`） | `…-dev.jar` |
| `sourcesJar` / `apiJar` | `Jar` | `…-sources.jar` / `…-api.jar` |
| `reobfJar` | `com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar`（**不是 `Jar` 子类**） | `qz_uilib-….jar`（发布物） |

两点施工含义：

1. **排除写在 `tasks.withType<Jar>` 上即可覆盖 `reobfJar`**：`reobfJar` 自身没有 CopySpec / `exclude`，但它吃的是
   `shadowJar` 的产物，排除随输入传播——M1 必须用 `jar tf` 逐产物实测确认，不靠推断；
2. **test 域不是「物理进不了包」**：`shadowJar` 的输入里含 `test` 源集输出——所以隔离一律靠显式排除 + 门禁，
   不能靠目录位置（这推翻了「落 test 域天然隔离」的先前假设）。

### F13 M1 落地实测：两处「宿主语义」缺口（2026-09-17）

M1 已落地（main 域 `internal.devtools.headless`，提交 `c7264618`）。第一次跑通时出现「GL 正常、字体正常、却整帧全透明」，
排查出的两处都不是 headless 独有缺陷，而是**照抄了渲染调用、漏抄了宿主语义**：

| 缺口 | 现象 | 根因 | 修法 |
|---|---|---|---|
| 帧前置语义 | 绘制无像素（自检报整帧全透明，`glError=0`） | 生产宿主在 `surface.render` 前设正交投影与 viewport；headless 自建这一段时顶点落在单位矩阵下被整体裁掉 | 帧前置上提 `UiHostRenderSupport.beginMainUiFrame`，MC 宿主与 headless 共用同一入口（`McScreenBridge` 改用之） |
| 宿主背景语义 | 有像素但 alpha≈18/255，导出后「白底淡字」 | UI 面板是半透明玻璃配方，真机叠在游戏世界之上；headless 从全透明开始时面板 alpha 停在极低值 | `HeadlessRequest.background`（默认不透明中性深色，`--bg=transparent` 可选）；自检增加 alpha 统计并对半透明底给出提示 |

附带修一处无 FML 宿主缺陷：`LaunchSide.isDedicatedServer()` 原先读 `Side.SERVER` 常量，非 FML classpath 上没有该类，
字体渲染 bootstrap 判定直接 `NoClassDefFoundError`；改为 `"SERVER".equals(side.name())`（按名字比较，行为等价）。

实测（本机 RTX 5070 Ti / GL 4.6.0）：1280×720 playground 首页、2 帧，**459 ms**（含字体初始化）；
像素自检 `ink=100% opaquePx=921600 meanAlpha=255 colors=1290 glError=0`；完整 `build` 通过
（5748 tests；`verifyHeadlessNotPackaged` 逐个校验 7 个产物均不含该包——门禁在首次运行时就抓出并修掉了 `apiJar` 漏排）。

### F14 M2 命令面判据与三档实测（2026-09-17）

出图完整性升级为**两条独立证据的交叉判据**：命令面（`HeadlessDrawSummary`：本帧下发了哪些绘制命令、
覆盖什么范围）+ 像素面（自检：有多少墨迹）。记录用 `RecordingUiRenderContext extends UiRenderContext`——
**必须是子类而非装饰器**：仓内多处按 `instanceof UiRenderContext` 解析像素上下文，装饰器会让玻璃/圆角路径静默降级。

判据（`HeadlessSelfCheck` 交叉段）：

- 命令数 > 0 而墨迹为 0 → **失败**（帧前置 / 帧缓冲绑定 / 裁剪栈问题，而非「UI 没画」）；
- 矩形命令全部落在视口外 → **失败**；部分越界 → 提示（不判失败）；
- 像素有内容而命令面为空 → 提示（记录器未挂上）。

三档实测（同一 playground 首页、2 帧、宿主背景 FF0E1014）：

| 尺寸 | 命令数 | 文本 | 几何 bounds | 视口外矩形 | 耗时 | 自检 |
|---|---|---|---|---|---|---|
| 640×360 | 82 | 48 条 / 924 字符 | 1,1..**832,451** | **2** | 401 ms | ok（ink=100%） |
| 1280×720 | 124 | 92 条 / 2100 字符 | 1,1..1279,717 | 0 | 615 ms | ok（ink=100%） |
| 2560×1440 | 124 | 92 条 / 2100 字符 | 1,1..2559,1437 | 0 | 694 ms | ok（ink=100%） |

两条结论：

1. **字体 GL 尾端确已上屏**：92 条文本命令 / 2100 字符对应像素 100% 覆盖、1290 种颜色（含抗锯齿灰阶），
   不再是「字体初始化成功但画不出来」；
2. **360P 暴露首个真实问题**：几何 bounds 832×451 超出 640×360、2 条矩形完全在视口外——小视口下 playground
   外壳（分段导航 9 项标签等）存在溢出。这不是 headless 设施缺陷，而是分辨率矩阵本该发现的东西，转入 M4 处理。

### F15 帧稳定语义与文本探针（2026-09-17）

M2 前半段暴露的隐患：**固定帧数出图会静默产出残缺内容**。同一次文本探针（同文本、同字号）：

| 帧数 | 墨迹颜色数 | PNG | 结果 |
|---|---|---|---|
| 1 | 1 | 1139 B | 整帧只有背景（文本完全没画） |
| 2 | 30 | 2309 B | 只有首个字形「Q」 |
| 10 / 40 | 180 | 8623 B | 收敛（完整文本） |

根因：字形由字体 worker 异步生成，固定帧数只保证「推进了几帧」，不保证「内容已就绪」；
而像素自检此前只看「有没有墨迹」，残缺文本照样判 ok——**这是比全透明更隐蔽的失败模式**。

修法（作为请求语义，不是补丁）：

- `HeadlessRequest` 增 `settleFrames`（默认 2）与 `maxFrames`（默认 60）：`frames` 降级为「最少帧数」，
  出图条件是**连续 settleFrames 帧像素指纹一致**（FNV 采样哈希），上限兜底；
- 命令面摘要改为「最后一帧」语义（`reset()`），与像素说的是同一帧；
- artifact 输出 `frames: 实际/上限`，达到上限即标注「未收敛」；
- 新增 `text-probe` 页面（`--page=text-probe --text=…`）：一行固定字号文本，给字体路径一个可归因的对照物。

实测（800×200，文本 `Qz UILib 对拍样本 Ag123`，字号 32）：默认 settle 自动推进 **6 帧**收敛（577 ms），
`--settle=1` 4 帧（525 ms），`--frames=20 --settle=3` 23 帧（583 ms）——三者输出**逐字节一致**（8623 B / 180 色）。

顺带修探针自身缺陷：`SceneLabel` 在零宽约束下会把文本裁到只剩首字符，探针必须显式给可用宽度。

### F16 与软光栅出图对拍（2026-09-17，M2 收口）

新增 `HeadlessTextParityTest`（test 域 `font/render/software`，与软光栅套件同包）：

- GL 侧走**进程外直启** `HeadlessShotMain --page=text-probe`（与 agent 真实路径一致），直启所需 classpath 文件与
  natives 目录由 Gradle 经 system property 注入 test JVM（`tasks.withType<Test>` + `dependsOn exportHeadlessClasspath`）——
  **test JVM 自身不需要 LWJGL2**，也避开 lwjgl3ify shim 与真 LWJGL2 在同一条 classpath 上的先后之争；
- 软光栅侧走既有验收通道 `LatexSoftwareRenderKit.render(text, 32, true)`；
- 判据是**几何**而非逐像素（两者 AA 实现不同）：墨迹宽度比 ∈ [0.75, 1.25]、高度比 ∈ [0.6, 1.4]，实测值打进测试输出。

实测（文本 `Qz UILib 对拍样本 Ag123`，字号 32，背景 `0xFF202020`）：

| 通道 | 墨迹 | 说明 |
|---|---|---|
| GL（headless 直启） | **393 × 36** | 真机路径：FBO + 生产 `UiRenderContext` |
| 软光栅（既有验收） | **382 × 34** | `LatexSoftwareRenderKit`，advance=389 |
| 比值 | 宽 **1.029** / 高 **1.059** | 差异 3~6%，属 AA 实现差异，度量链路一致 |

**M2 至此收口**：字体 GL 尾端上屏有据（F14）、出图完整性判据落地（F14）、帧稳定语义堵住残缺出图（F15）、
跨通道几何对拍通过（本则）。下一步 M3：纯代码输入设备模型与脚本化输入。

### F17 M3 纯代码输入设备模型与输入脚本（2026-09-17）

设备模型表达**用户动作**，而不是平台事件：

- `HeadlessInputDevice`：`moveTo / moveBy / press / release / click / doubleClick / scroll / keyDown / keyUp /
  pressKey / type / compose / cancelPointer / frame / wait`；时间轴按 16.67ms 单调推进；
  修饰键由「当前按住的键集合」推导；`click` / `pressKey` 自动插入帧边界（跨帧）。
- `HeadlessInputScript`：可读脚本，每条语句换行或 `;` 分隔、`#` 注释；语法错误显式失败（不静默跳过）。
  关键字：`move / moveby / down / up / click / dblclick / scroll / keydown / keyup / key / type / compose / cancel / frame / wait`。
- `HeadlessInputSource`：实现 `PlatformInputSource` + `KeyboardTextInputSource`，事件经**生产 `InputFrameBuilder`**
  封板（不另造帧构造）；`drainFrame()` 内部先推进设备再封板，因此**帧划分与生产帧管线天然对齐**，
  调用方不需要手工对齐帧号。整串文本（`compose`）与逐字符（`type`）两条文本路径都在。

端到端实测（一条命令）：`--actions="move 315 88; frame; click; wait 4"` 完成「移到导航 → 点击 → 切页 → 出图」：

| 指标 | 基线（无脚本） | 点击导航第 2 项 |
|---|---|---|
| input | dispatched=0 | **dispatched=3**，pointer=315,88 |
| 命令/文本 | 62 / 46 条 / 1050 字符 | 54 / 30 条 / **413 字符** |
| PNG | 200 031 B | 108 587 B |
| 差分 | — | **170 495 像素（18.5%）**，页面切到「单行文本」 |

契约测试 `HeadlessInputDeviceTest`（5 项）：跨帧点击、修饰键跟随按住键、`wait` 语义、整串文本单帧交付、
非法脚本显式失败。

踩坑记录：`wait` 最初实现在**动作队列之前**生效，导致「队列里还有动作时先空转」——实测 `dispatched=0`、
脚本整段静默不执行。已改为「队列耗尽后才空转」，并由 `waitRunsAfterQueuedActions` 钉住。

### F18 启动器、分辨率矩阵与「上下文是进程级资源」（2026-09-17）

**启动器**：`exportHeadlessClasspath` 现在同时生成 `build/headless/{classpath.txt, shot-args.txt, qz-shot.bat, qz-shot.sh}`
——用 Java `@argfile` 承载超长 classpath，agent 直接 `qz-shot.bat --page=… --size=… --out=…`。
实测冷启动 **1.62 s**（含 JVM 启动 + 8 帧渲染 + PNG），达成 P1「≤ 3 s」目标（Gradle 路径为 12~22 s）。

**分辨率矩阵**：CLI 支持 `--sizes=WxH,WxH,…`，一次进程内跑多档；多档时 `--out` 自动加尺寸后缀，末尾给矩阵汇总行。

| 尺寸 | 命令 / 文本 | 几何 bounds | 越界 | 颜色数 | 自检 |
|---|---|---|---|---|---|
| 640×360 | 41 / 24 条 / 462 字符 | 1,1..**832,451** | **1** | 1232 | ok |
| 1280×720 | 62 / 46 条 / 1050 字符 | 1,1..1279,717 | 0 | 1290 | ok |
| 1920×1080 | 62 / 46 条 / 1050 字符 | 1,1..1919,1077 | 0 | 1276 | ok |
| 2560×1440 | 62 / 46 条 / 1050 字符 | 1,1..2559,1437 | 0 | 1289 | ok |

整批（四档）墙钟 **2.42 s**；矩阵汇总行：`matrix: 4/4 ok`。

**修复：GL 上下文必须是进程级资源**。矩阵首跑时第 2 档起全部 `glError=1281`（GL_INVALID_VALUE）、颜色数从 1290
掉到 217（文字大面积丢失）——根因是会话 `close()` 里调了 `Display.destroy()`：字体 atlas 等全局 GL 对象挂在上下文上，
下一个会话拿到的是失效纹理。改为 `ensureContext()`（进程级复用）+ `close()` 只释放 FBO/纹理/renderbuffer，
`shutdownContext()` 留给显式收尾。修后 4/4 ok、各档颜色数恢复一致。

**360P 溢出根因（F14 的发现至此收口为业务待办）**：`PlaygroundKit.MAX_CONTENT_WIDTH = 860`，而 640×360 下可用内容宽
只有「视口宽 − 2×padding = 608」；分段导航 9 项标签各按「文本宽 + 2×PAD_LG」取 preferredWidth 且无法收缩 → 横向溢出
（几何 bounds 832 > 640）。这是**业务外壳的小视口适配问题**，不是设施缺陷——headless 的职责是把它暴露出来
（`outsideViewport=1` + 提示）。修法需业务侧决策（内容最大宽与父约束取 min，或分段导航加滚动/换行），设施不擅自改 UI。

### F19 消费者域扩展：9 个 playground 页面批量出图（2026-09-17）

页面维度进入请求语义：`HeadlessRequest.pageIndex`（-1 = 由页面自身决定），CLI `--page-index=N` / `--page-indexes=0,1,…`，
与 `--sizes` 组成「页面 × 尺寸」笛卡尔积。

切页走宿主**公开入口** `TestPlaygroundHost.showPage(index)`——signal-first，与用户点击导航**同一条通道**，
不依赖命中坐标（窄画布下点击会静默 miss），也不新增第二条切页路径。

实测（9 个 playground 页面 × 1280×720，一次进程）：

| 页 | 0 总览 | 1 单行文本 | 2 多行文本 | 3 浮层 | 4 响应式 | 5 富文本 | 6 控制字符 | 7 LaTeX | 8 Markdown |
|---|---|---|---|---|---|---|---|---|---|
| 颜色数 | 1290 | 1277 | 1261 | 1430 | 1298 | 1765 | 1172 | 1154 | 2098 |
| 收敛帧 | 4 | 11 | 12 | 12 | 13 | 12 | 11 | 13 | 8 |

`batch: 9/9 ok`，整批墙钟 **4.56 s**（含 JVM 启动）。

两个副产物：

1. **settle 在页面维度同样必要**：切页后各页收敛帧从 4（首页）升到 8~13——固定帧数出图会截到半成品；
2. 复杂页（Markdown 渲染）在 headless 下完整可读：标题层级、围栏代码块、嵌套引用、滚动条均在位。

### F20 M5 收口：失败语义、退出码与成本基线（2026-09-17）

**失败语义实测（三连测）**：

| 场景 | 期望 | 实测 |
|---|---|---|
| 无 natives（等价无 GL） | 显式失败 + 可操作指引，不产出图 | exit **3**；`[GL 上下文] 加载 LWJGL2 natives 失败：请确认 natives 已解压且 -Djava.library.path 指向该目录（qz-shot.bat 已自带）` |
| 未知页面 | 显式失败 | exit **3**；`[能力探测] 未知页面：no-such-page` |
| 透明背景 | 成功 + 提示 | exit **0**；自检 `meanAlpha=4.8`、不透明占比 1.15% + 提示「若期望不透明判读，请把宿主背景设为不透明（--bg=RRGGBB）」 |

顺带修掉两处语义缺陷：

1. **退出码分流**：原先所有失败都返回 4（自检失败），环境/上下文失败也是 4，与接口契约（3 = 能力/上下文/渲染失败）
   不符——agent 无法区分「环境没准备好」与「UI 有问题」。现按失败类型分流（3 与 4 分开）；
2. **诊断可读性**：`UnsatisfiedLinkError` 会把整条 `java.library.path`（数千字符）拼进消息。现由 `HeadlessFailure.brief`
   统一截断到 200 字符（唯一实现，诊断输出与上下文创建共用）。

**软光栅回退语义修正（原 A2）**：软光栅只覆盖字形通道（`FontSoftwareRasterizer` 消费 `SoftwareRenderFrame`），
**不渲染 scene 命令**（面板 / 圆角 / 裁剪 / 组不透明 / 合成）→ **像素级回退在技术上不成立**。
无 GL 时的正确行为是「显式失败 + 可操作指引」，而不是静默降级成半张图。
A2 与 §六-6 据此修正为：保留软光栅作为**字体侧既有验收通道**，不作为 headless 出图的回退路径。

**成本基线**（统一口径：启动器直启，含 JVM 启动）：

| 场景 | 墙钟 |
|---|---|
| 单张 1280×720 | **1.96 s** |
| 分辨率矩阵四档（360P/720P/1080P/2K） | **2.31 s** |
| 九个 playground 页面 | **4.58 s** |
| 单帧 GL 侧（M0 实测，2K） | 534 ms（读回 4 ms、PNG 编码 97 ms） |

内存口径（RGBA 单帧）：360P 0.88 MiB → 2K 14.06 MiB；字体系统首次初始化常驻约 150 MiB（`FontService`，进程内只付一次）。

### F21 完整开发类路径供给与 chat3/HUD 边界结论（2026-09-17）

**新增完整开发类路径供给**：`exportHeadlessClasspath` 现在额外产出 `classpath-full.txt` + `qz-shot-full.bat`
（137 项 = 最小集 107 项 + `compileClasspath` + `patchedMc` / `mcLauncher` 源集输出，即重编译后的 Minecraft 类）。

- 起因（实测）：渲染触及 MC 类型的页面时链接失败——`NoClassDefFoundError: net/minecraft/util/IChatComponent`；
  根因是 **MC 类不在 `compileClasspath` 上**（RFG 把重编译产物放在 `patchedMc` 源集输出）；
- 补上 `patchedMc` + `mcLauncher` 后链接成功（同一命令从 exit 1 变为正常启动）；
- 定位：**最小集仍是 agent 默认**（快、无 MC 静态初始化风险），完整集给「页面本来就要 MC 类型」的场景。

**chat3 / HUD 边界结论（2026-09-17 初判 → 2026-09-18 修正）**：

| 消费者 | 可 headless 性 | 证据 |
|---|---|---|
| chat3 视图 | **已纳入**（`--page=chat`） | 走生产同一入口 `ChatSceneController.buildContent(SceneRuntime)`。2026-09-17 判为「空画面（`commands=0`/`colors=1`）说明内容挂在宿主装配链上」**结论有误**：真实成因是两处接线——① 漏写 `setHostViewport` → `chatWidthFor(0)` 收敛到 1px；② `--frames` 默认 2 帧时消息组 180 ms 入场动画期间整树 `opacity=0` 且像素逐帧不变，被「连续 N 帧像素一致」的稳定判据误判为已收敛。补齐后 1280×720 实测 `commands=23 [surface=5 segments=18]`、`colors=1318`，出图含气泡 / 组头 / markdown / 公式 / 链接 / 折行 |
| HUD | **否（宿主在 MC 域）** | `ui/hud` API 层零 MC 依赖，但宿主装配 `SceneHudHost` / `HudRegistry` / `ClientHudServiceImpl` 全在 `client/` 包；在 headless 复刻等于新增一套 HUD 虚拟窗口装配，违背「复用生产链路」原则 |

结论修正：**chat3 不需要新抽象**——它的内容构建入口（`buildContent(SceneRuntime)`）本来就是宿主无关的，
缺的只是接线与时间语义（视口先写入、帧数覆盖入场动画）。
**HUD 需要一处搬迁 + 依赖倒置**：把 `client.hud.SceneHudHost.RetainedWindow`（外壳 + 五件套 + 空内容语义）
上提为 `ui.scene.host` 里宿主无关的单窗口宿主，由 client 与 headless 共用；四角锚定数学已在宿主无关的
`SceneAnchorResolver`。在 headless 复刻第二套 HUD 装配仍是禁止项。
「有命令无像素 / 有像素无命令」两个自检提示保留，本次空画面正是被它们点出来的。

### F22 环境面落地：宿主环境端口（2026-09-18）

**裁定**：环境事实走**注入端口**（构造依赖），不做单例直读、不做服务定位器。判据是
「静态量必须有失效通道」——框架今日获取环境事实的两条路各有缺口：进程级静态/单例直读
（`Config.useDebug` 被 23 处每帧读、`LanguageEpochService` 自述「不是 signal 通道」）
在 headless 出图与测试里**无法替换**、在运行期**无法通知**消费者；构造期注入
（`HudScaleSetting`、`SceneThemes.install(runtime, signal)`）已被证明可用，却各自为政、每加一个
环境量就要多改一次宿主构造签名。环境面把后者上升为统一语义，给前者一个明确归属。

**落地**（`club.heiqi.uilib.ui.env`）：`UiEnvironment` 端口 + 三域
`DiagnosticsEnvironment` / `LocaleEnvironment` / `ResourceEnvironment`（含各自缺席实现）
+ 缺席态 `UiEnvironment.empty()` + 生产适配器 `ProcessUiEnvironment`（**无状态**转发：
`Config.useDebug` / 语言代际 / 资源代际，因此多实例语义等价，无需单点装配）。

注入点是**构造依赖**：`SceneRuntime(SceneTextMeasurer, UiEnvironment)` 成为唯一公开构造
（传 null 快速失败，不给「缺省即静默缺席」的路径），`SceneHostAssembly.assemble` 加第三参，
`SceneHostAssembly.defaultEnvironment()` 与既有的 `defaultMeasurer()` 对称、是全仓唯一生产环境装配点。

三条不变量（写在 `UiEnvironment` javadoc）：① 只读且方向**自外向内**（宿主 → runtime；
与 `SceneFontEnvironment` 的 runtime → 节点下行暴露方向相反，不得互相顶替）；② 缺席态与
「未安装态」**逐位等价**（各域 javadoc 写明具体缺席值）；③ 环境值是 O(1) 帧内直读，
**禁止**缓存进构造期字段或 `Computed` 快照（后者即仓库内已实测的静默失效事故同型）。

**接线范围（本版）**：帧管线 `phaseReplay` 的采样开关由 `Config.useDebug` 静态直读改为
`runtime.environment().diagnostics().debugEnabled()`（有 runtime 引用可直达，行为等价，
且 headless / 测试自此可注入自己的诊断实现）。其余读取点的**接线进度与前提**写在
`ProcessUiEnvironment` javadoc，按可达性分两类，下次接手不必重新盘点：
有 runtime 引用但改端口读解决不了的（`registerDebugHud` 的 Computed 快照，需先给诊断域补
可订阅通道）；需先补「节点 → 环境」通道的（控件内 `Config.useDebug` 采样点、
`UiPerformanceMonitor`、`HostImageSource`、`PickerIconResolver` / `PickerRevisionBridge` 的代际比对）。

**测试侧**：`SceneRuntime` 旧的无参 / 单参构造删除，**283 处**测试构造点机械迁移到 testkit 收口工厂
`SceneTestEnvironments.runtime(...)`（103 个文件，纯机械替换 + import 补插，断言零改动；
`SceneInteractionHarness` 同包免 import）。`UiSamplingRenderSemanticsTest` 是唯一需要语义改动的：
它原本靠改 `Config.useDebug` 静态字段驱动帧管线，现改为**注入诊断环境**（不再污染进程静态态）；
`UiPerformanceMonitor` 侧仍是静态直读（未接线），故该测试两侧同时打开以构成完整「采样开启」语义。

**本版边界**：不提供环境量的**订阅（signal）**通道，只提供值读与代际；需要响应式派生的域接入时
再补（届时 `UiEnvironment` 的 default 域访问器保证新增域不破坏既有实现）。

### F23 chat 页出图空画面的根因：虚拟时钟与消息出生时刻的时序竞态（2026-09-18）

**现象**：`--page=chat` 出图纯色（`commands=0`、`colors=1`、PNG 5308 bytes），与 F21 记录的
`commands=23 / colors=1318` 不符；同机 `--page=playground` 正常（`commands=62`、`colors=1290`）。

**排查链**（每步都是一手实测）：

1. **与工作区改动无关**：在 F21 当时的提交 `a6396aff` 上另建 worktree 复跑，输出 PNG 与当前
   **逐位同字节数**（5308）；
2. **树不空**：临时探针显示内容根布局盒 320×322、节点 41 个、消息 6 条（默认消息集），全部有尺寸；
3. **组 opacity 恒 0**：6 个消息组节点 `opacity=0.0`（精确 0，不是极小值），22 帧不变；
4. **整树被跳过**：paint 对「零透明子树」有跳过优化，全部组为零 ⇒ `commands=0`、`bounds=(empty)`；
5. **直接量出生时刻与帧时钟**：`born=…970385 now=…970032 delta=-353` —— 出生时刻比帧时钟起点**晚 353ms**。

**根因**：`ChatSceneProbeHost` 的虚拟时钟 `clockMillis` 用**字段初始化器**取值，它执行于
`super(...)` 之后、`ChatSceneController` 创建与消息 append **之前**；而控制器初始化（度量 / 段解析装配）
实测可耗数百毫秒。入场动画进度 = `(帧时钟 − 组出生时刻) / 180ms`，出生时刻落在起点之后 ⇒ 进度恒为负
⇒ 每组 opacity 恒 0。默认 `frames=20`（≈320ms）追不回 353ms 的偏差，于是稳定出空图。

**为什么 F21 当时能出图**：这是**时序竞态**而非恒坏——控制器初始化耗时随字体 / 类加载预热而变，
预热后偏差趋近 0，入场动画照常播完。同一个二进制在不同时刻可以得到两种结果，
F21 的 `commands=23` 与本次的 `commands=0` **都是真实测量**。

**修复**：把 `clockMillis` 的取值挪到内容构建完成之后（构造体末尾），并写明「初值不能放字段初始化器」
的理由。修复后同一命令实测 `commands=23 [surface=5 segments=18]`、`colors=1286`、
PNG 49906 bytes，与 F21 记录量级一致。

**可复用教训**：凡「虚拟时钟 + wall-clock 出生时间戳」混用处，时钟起点必须晚于所有出生时间戳的
产生点；字段初始化器是最容易踩的位置（它在 `super()` 之后、构造体之前）。

### F24 出图取证的第二个产出：气泡内文本换行宽与气泡内宽不同源（2026-09-18）

**现象**：F23 修复后出图已正常，但人眼在图上发现「最后一条消息的第二行文字伸出了气泡右缘」。

**取证**（像素级，不靠肉眼）：逐行分离「气泡底色像素」与「文字亮像素」求各自右边界。
修正前视口 1280：链接消息气泡止于 x=253、文字画到 266（溢出 13px）；长文本消息气泡止于
x=254、文字画到 298（溢出 44px）；两条短消息内边距恒为 10~11px 正常。

**根因**：气泡**换行宽**取自 composer 的 `maxLine = chatWidthFor(v) − 2×bubblePaddingX`，
那是「气泡外宽上限」的**父口径**（未乘 `bubbleMaxWidthRatio`）；而气泡节点与行节点实际被钳到
`maxBubbleWidthPx = round(父口径 × 0.85)`。两者相差 0.85 倍 ⇒ 文本按 300 换行、按 255 显示。
**只有内容宽超过气泡上限的消息触发**，短消息一切正常，故长期未暴露；生产与 headless 同路
（`ChatHudWindow` 无参构造 ⇒ `segmentFlowWrapper = null`，与探针宿主一致），**不是 headless 特有**。

**修复**：`ChatMessageList.buildGroupNode` 改为由 `maxBubbleWidthPx` 派生换行宽（同式扣 accent
条常量；逐行缩进仍由行节点 padding/reserve 处理），换行宽与钳宽自此同源。

**既有测试为何没拦住**：`longSelfMessageClampsBubbleToMaxWidthAndKeepsAccentInside` 的注释与期望
把错误口径写成了事实（「maxBubble = 119；行切分宽 = 140」），且断言只量**节点宽**——节点宽被钳到
97，文字仍按 140 换行画出去。已改为逐行累加段落实宽的溢出锁。

**可复用教训**：

1. 「上限」与「内容宽」是两个量：任何 `min(内容, 上限)` 式的钳制必须让**内容按钳制后的宽重排**，
   否则钳制只作用于外框、内容照样画出去；
2. **只量节点宽量不出溢出**——溢出是「内容超出节点盒」，断言必须量内容（段落实宽）；
3. headless 出图的价值不止「出一张图」：像素级测量能发现生产代码里长期潜伏的口径缺陷。

### F25 宿主窗口上提：SceneHostWindow 与 headless HUD 页（2026-09-18）

**问题**：HUD 宿主的可复用单元（外壳 + 内容 + 装饰层 + 独立帧管线）原本是
`client.hud.SceneHudHost.RetainedWindow` 的私有内部类，直接读三处 client 事实
（`MyMod.LOG` / `HudTokens` / `HudToolbarService`）⇒ headless 无法复现「HUD 放置后的画面」，
只剩「在 headless 里照抄第二套装配」这条禁止项。

**上提**：新增 `ui.scene.host.SceneHostWindow`——**独立类型，不继承 `AbstractSceneHostWidget`**：
后者是「Widget 派生页面宿主：有输入源、随屏幕生命周期」，前者是「保留式窗口：无输入、内容空即隐」，
两者只共用 `SceneHostAssembly` 的装配口径。三处外部事实倒置为构造参数：
`failureSink`（原 `MyMod.LOG`）、`Shell`（原 `HudTokens` 内边距 + 外壳底色，`Shell.HUD_DEFAULT`
成为全仓唯一一份默认外壳）、`ContentDecorator`（原 `HudToolbarService.mountLayer`，失败单点隔离）。
`RetainedWindow` 退化为薄包装，工具栏注册表版本与工具栏层探针留在 client。

**headless 消费**：新增 `--page=hud`（`HudSceneProbeHost implements UiSurface`）——同一份聊天
内容树套 HUD 外壳并按四角锚定放置（`--page-index` 0/1/2/3 = 左上/右上/左下/右下，默认左下）。
`HeadlessSession` 的宿主类型由 `AbstractSceneHostWidget` 放宽为 `UiSurface`：页面宿主有两种形态，
会话只驱动渲染面，不假定宿主内部结构（否则只能二选一：要么让 HUD 页继承基类从而跑起两条管线，
要么永远出不了 HUD 图）。

**验收（一手实测）**：

- `--page=hud` 默认左下 `bounds=4,364..338,716`，`--page-index=0` 左上 `bounds=4,4..338,356`；
  外壳宽 334 = 内容 320 + 2×7 内边距（与生产外壳几何同源）；
- 空消息集 `--text=` → `commands=0 / bounds=(empty) / colors=1`：整窗（含外壳）隐藏，
  空窗路径在 headless 下可见、可与「设施没出图」区分；
- 客户端既有防线 `SceneHudPipelineTest`（608 行：工具栏外框、空窗自愈、倍率缩放逐命令对拍）
  **零改动**通过；新增 `SceneHostWindowTest` 只钉上提后新增/易退化的语义（外壳开关、装饰层隔离与
  测量、空内容判定、环境根成对、null 快速失败），不重复镜像既有覆盖。

**可复用教训**：判断「能不能上提」的判据不是代码行数，而是**外部事实的条数**——把三处外部事实
变成构造参数后，同一份装配即可在客户端与无游戏进程下运行；反过来，任何仍读静态单例的装配点，
都是下一个不可复用单元。

## 三、目标形态

**四件套 + 一个出口：**

| 件 | 内容 | 现状 |
|---|---|---|
| 驱动 | 多帧推进 + 帧时间可控（注入而非 `System.nanoTime` 独裁） | `render` 已有；时间源需可控化 |
| 输入 | 纯代码设备模型：鼠标（移动/按键/滚轮/拖拽）、键盘（按下/释放/重复/修饰键）、文本（char/整串/IME 接管）、焦点、`CANCEL` | 事件模型齐、注入入口缺 |
| 渲染 | 真 GL 离屏（FBO）→ 像素；路由与生产同源 | 缺上下文与后端 |
| 观测 | PNG 人眼判读 + 自检信号 + 命令轨迹 + 结构化诊断 | 字体侧有、scene 侧无 |
| **出口** | **一条命令出图**（页面 × 尺寸 × 输入脚本 × 输出路径） | **无**（P1 的核心缺口） |

## 四、方案骨架

### A 渲染载体（R3）

- **A1（推荐）**：LWJGL2 原生 + natives（F7 已实测）；`Display.create(PixelFormat)` 取上下文，
  **渲染目标一律自建 FBO**（尺寸 = 目标分辨率，与窗口/Xvfb 屏幕解耦），`glReadPixels` → PNG。
  - `PixelFormat` 需含 **stencil**（`ClipStack` 走 scissor + stencil mask）与 alpha；窗口尺寸只需最小。
  - **线程约束**：GL 调用固定在创建上下文的线程。
  - 像素路径**直接复用生产 `UiRenderContext`**（F7 已证可构造、可绘），不另造第二套后端。
- **A2（修正：无 GL 时不作像素回退）**：软光栅只覆盖字形通道、不渲染 scene 命令，**像素级回退不成立**；
- **A3（不采用）**：测试域复用 lwjgl3ify / LWJGL3（F4、F8 已给反证）。
- **代表性边界**：A1 的 GL 是桌面 LWJGL2 + WGL/GLX，**不代表**真机 MC 宿主、Angelica 或 lwjgl3ify 上下文；
  它代表顶点变换后的像素位置、clip/stencil 语义、混合、shader（字体 AA、玻璃）与多分辨率布局。

### B 分辨率矩阵（R2）

- 建议档位（待裁）：`640×360` / `1280×720` / `1920×1080` / `2560×1440`（可扩 `854×480` / `960×540` / `1600×900`）。
- 内存口径（RGBA 单帧，Python 验算）：360P 0.88 MiB → 2K **14.06 MiB**；2K 相对 360P 为 **16 倍像素**；
  全档同时持有 35.4 MiB → **约束不在内存，在串行与复用**。
- 第二维：MC GUI Scale（1/2/3/4）只在宿主边界验收需要（`HostViewportScale.compose`）；默认矩阵不含。
- 与密度档位联动：`PickerDensityPreference.AUTO` 按视口求解密度档，矩阵应覆盖 AUTO 的档位切换边界
  （现仅 `PickerDensityPanelWiringTest` 断过 1920×1080 → 标准档）。
- **新定位下**：尺寸首先是**命令行参数**（P1/P2），矩阵是它的批量展开。

### C 输入设备模型（R4）

- **C1 设备模型**：`HeadlessInputDevice`（`moveTo` / `press` / `release` / `click(button)` /
  `scroll(dx,dy)` / `keyDown` / `keyUp` / `type` / `compose(text)` / `focus` / `cancel`），
  内部按帧累积 `RawInputEvent` 并产出 `SceneInputFrame`。
- **C2 时间轴**：事件时间戳可控（默认每帧 +16.67ms 单调推进），支持长按、双击时间窗、跨帧 DOWN/UP。
- **C3 脚本化（P2 的关键）**：输入序列可由外部脚本描述（如「移动到按钮 → 点击 → 出图」），
  使 agent 一次调用即可完成「交互 + 出图」。
- **C4 兼容与纪律**：保留 `SceneInteractionHarness` 的节点快捷方法作为语法糖（261 处调用点零破坏）；
  必须走生产链路 `PlatformInputSource → SceneInputFrame → SceneRuntime.route`，**不旁路 router**。
- **C5 焦点与文本**：`KeyboardTextInputSource` 的 `pushText` / `setExternalTextMode` 已提供「外部文本接管 ↔
  char 路径回落」两态，两种模式都要能驱动。

### D 观测与产物

- PNG：`build/reports/headless/<suite>/<case>-<WxH>.png`（沿用既有 `build/reports` 约定）。
- **自检信号（P1 必需）**：agent 判读前先要能区分「图有效」与「图没画出来」——至少给非空墨水率、绘制越界、
  缺字形计数、`glGetError`；否则「空白图」会被误判成代码 bug（对照 F2 的静默降级）。
- 机判（**不做字面快照**）：结构不变量（如「面板不超出视口」）。
- 命令轨迹：`RecordingRenderBackend` 保留为「路由/命令面」证据，与像素证据**不可互替**。

### E 出图入口与速度分层（P1/P2 的核心，本轮新增）

| 层 | 形态 | 预计冷启动 | 说明 |
|---|---|---|---|
| 快路径 | 一次性导出 classpath 到文件 → `java -cp @cp <入口> --page=… --size=… --out=…` | **1~3 s** | 绕开 Gradle；agent 默认路径 |
| 常驻模式（候选） | 长驻进程按请求出图 | 单请求 **≤ 1 s** | 摊掉 150 MiB 字体常驻与上下文创建（F10） |
| Gradle 路径 | `JavaExec` 任务（`gradlew qzShot -Ppage=…`） | 3~8 s | classpath 由 Gradle 托管 |
| 测试域路径 | `gradlew test --tests …` | 12~22 s | 回归用（P3） |

四者**共用同一套 headless 装配与后端**，区别只在启动壳。

**设施落点（§六-1 已裁定）**：main 域 `internal.devtools.headless`（与 `playground`/`glass` 同层），
**硬约束是不进打包产物**：

- **编译期零依赖改动**（F11 实测）：`org.lwjgl.opengl.GL11` 已在 `compileClasspath`；
- **打包隔离**：`tasks.withType<Jar>` 上 `exclude("club/heiqi/uilib/internal/devtools/headless/**")`——覆盖
  `jar` / `shadowJar` / `sourcesJar` / `apiJar`，并经 dev jar 传播到 `reobfJar`（F12）；
- **隔离门禁**：新增 `verifyHeadlessNotPackaged` 挂到 `check`，逐个打开上述产物断言不含该包（对照 `addon.late.gradle`
  的 `verifyRunClasspathIsolation` 先例）——排除是意图，门禁才是保证；
- **运行期补充 classpath**：LWJGL2 + natives 不在 runtime classpath（F11），直启壳需自带（§六-2 的解压任务产出固定目录，
  `-Djava.library.path` 指向它）。

## 五、已声明的边界（不能声称的）

1. FBO 出图**不代表**真机 MC 宿主 / Angelica / lwjgl3ify 上下文；
2. **不覆盖**原版包装类禁令（Tessellator 等）一类问题；
3. GL 输出依赖驱动实现，**不作为逐像素金样**；
4. **不经过** `LwjglInputSource` 的 poll 差分与宿主回调旁路语义——headless 注入的是帧，
   桥内部的差分/边沿丢失类缺陷不在覆盖范围内；
5. **保真分层，不可越级声称**：契约层（`RecordingRenderBackend` / FakeGl，必跑）→ 几何与语义层
   （llvmpipe 渲染 + 容差或结构断言）→ 观感层（真机截图基线，仅用户可判）。headless GL 出图属**第二层**；
6. **环境依赖**：字体引擎走 AWT 系统字体（`FontRegistry` ← `GraphicsEnvironment.getAllFonts()`），
   生产与 headless **同源**；但**环境缺字体即不可用**（F10）——容器/CI 出图需装字体（含中文字体），
   否则出图出现豆腐块或直接初始化失败，**不得当作代码缺陷**；
7. Linux 无桌面环境下需要 Xvfb（与 Qt offscreen 的实质差距，见 §一）。

## 六、岔路裁定（2026-09-17 用户裁定，已冻结）

用户口径（原文）：「1 不进生产包放哪都可以」→「只要不影响打包体积放 main 域也可以」；「其余采纳建议」。

| # | 岔路 | 裁定 |
|---|---|---|
| 1 | **设施归属** | **main 域 `internal.devtools.headless`**（与 `playground`/`glass` 同层），**且不影响打包体积**：`tasks.withType<Jar>` 排除该包 + `check` 挂 `verifyHeadlessNotPackaged` 门禁（口径见 §四 E，传播路径见 F12）。位置由用户明确为次要项，「不进生产包 / 不影响打包体积」是硬约束 |
| 2 | **natives 供给** | 采纳建议：Gradle 任务一次解压到固定目录 + 文档化路径，运行期以 `-Djava.library.path` 指过去（F11：runtime classpath 不含 natives，必须自带） |
| 3 | **快路径形态** | 采纳建议：M1 先直启 JVM（`java -cp @cp <入口> --page=… --size=… --out=…`），常驻进程留作 M4 候选（F10：150 MiB 字体常驻是常驻模式的收益来源） |
| 4 | **首批可渲染范围** | 采纳建议：M1 只做 playground 首页 + 核心控件冒烟；配置页 / chat3 / HUD 分批进 M4 |
| 5 | **分辨率档位** | 采纳建议：360P / 720P / 1080P / 2K 四档；854×480 / 960×540 / 1600×900 按需扩 |
| 6 | **软光栅去留** | 采纳建议：保留为「无 GL 回退」（A2），退出主路径；其语义测试与既有出图不受影响 |
| 7 | **CI 策略** | 采纳建议：本轮不接 CI，需要时再开独立任务（F5 的 Xvfb + Mesa 环境仍可用） |

裁定顺带修正的三条口径（施工时必须遵守）：

1. 「落 main 域 ⇒ 零依赖改动」**只在编译期成立**；运行期必须补 LWJGL2 + natives（F11）；
2. 「落 test 域天然不进包」**不成立**（F12：`shadowJar` 吃 test 输出）——隔离一律靠排除 + 门禁；
3. 门禁必须遍历 `AbstractArchiveTask` 产物，而不是硬编码任务名——构建链新增打包任务时硬编码门禁会静默失效。

## 七、建议分批

- **M0 闸门（可行性）**：**已通过**（F7，本地）。CI 侧不再是闸门（降级为可选）。
- **M1 出图入口与最小闭环（P1 核心）**：**已完成**（提交 `c7264618`，实测见 F13：459 ms，自检全绿）。
  范围：main 域 `internal.devtools.headless`——上下文 + FBO + `UiRenderContext` 复用 + PNG 落盘 + 自检信号
  + 命令行参数（页面/尺寸/输出/背景/帧数）；**目标「一条命令渲染 playground 首页出图 ≤ 3 s」已达成**。
  实际落地顺序（每步独立验证，先证「不影响打包」再往里加代码）：
  1. **打包隔离 + 门禁先行**（§六-1 硬约束）：`tasks.withType<Jar>` 排除该包 + `verifyHeadlessNotPackaged` 挂 `check`，
     用 `jar tf` 对 `jar` / `shadowJar` / `sourcesJar` / `apiJar` / `reobfJar` 逐个产物确认（先放一个哨兵类即可验证链路）；
  2. **natives 解压任务**（§六-2）：把 LWJGL2 natives 解到固定目录（建议 `build/headless/natives`），文档化路径；
  3. **headless 运行期 classpath**（F11）：一次性导出「main 输出 + LWJGL2 主 jar + natives」到 classpath 文件，供直启壳使用
     （F10：绕开 Gradle 才是「快」的关键）；
  4. **入口与最小闭环**：上下文 + FBO + PNG + 自检信号（墨水率 / 缺字形 / `glGetError`）+ 命令行参数；冷启动实测回填 §八-4。
- **M2 渲染地基**：**已完成**（F14 / F15 / F16）——字体 GL 尾端上屏有据（命令面 × 像素面）；出图完整性判据落地；
  帧稳定语义（settle）与文本探针（`text-probe`）堵住「固定帧数出图静默残缺」；与软光栅出图几何对拍通过
  （宽比 1.029 / 高比 1.059），并由此把 headless 出图接进了既有验收测试体系。
- **M3 输入设备模型**：**已完成**（F17）——C1 设备模型 / C2 时间轴 / C3 脚本化 / C4 走生产链路不旁路 / C5 文本两态；
  端到端以「一条命令完成点击切页并出图」收口，契约测试 5 项钉住时序。
- **M4 铺开**：**已完成**（F18 / F19）——启动器（冷启动 1.62 s）、分辨率矩阵 4 档（2.42 s、4/4 ok）、
  GL 上下文进程级复用修复、消费者域扩展（9 个 playground 页面批量 9/9 ok、4.56 s）。
  常驻进程形态经实测后判定**不需要**：直启冷启动 1.62 s 已满足 P1，常驻只省 1 s 却引入进程管理成本。
  360P 外壳溢出已定根因并转业务待办（不属设施）。
- **M5 收口**：**已完成**（F20）——失败语义与退出码分流、软光栅回退语义修正（不作像素回退）、成本基线表、
  使用文档落点（`docs/使用文档/headless出图指南.md`）。CI 接线按 §六-7 裁定不做。

## 八、技术未知项状态

| # | 未知项 | 状态 |
|---|---|---|
| 1 | LWJGL2 `Display.create()` 在 JDK 17 下能否初始化 | **已实测通过**（F7） |
| 2 | `UiRenderContext` 在无 MC 环境下的可构造性 | **已实测通过**（F7） |
| 3 | 2K 一帧的 GL 侧耗时 | **已实测**：534 ms（本机 GPU） |
| 4 | 快路径（直启 JVM）的真实冷启动耗时 | **已实测**：启动器直启含 JVM **1.96 s** 出图（内部 capture 459~615 ms）；Gradle 路径 12~22 s（F18/F20） |
| 5 | 完整 scene（含玻璃/裁剪/字体）单帧耗时 | **已实测**：生产链路端到端 1280×720 每张 459~615 ms（含字体初始化与 settle 多帧，F14/F18） |
| 6 | 无字体环境下的失败语义与自检表现 | **部分实测**：能力探测已报 `fonts=0` 并在自检提示；零 fontconfig 容器的实际崩溃形态本机无法复现（已知 `ERROR-20260904`） |
| 7 | 非 Windows 平台（Linux/llvmpipe、macOS） | **未实测**（本机为 Windows；Linux 需 Xvfb + Mesa，路径已由 F8 调研确认，待外部环境验证） |
| 8 | CI 侧（Xvfb + llvmpipe） | **未实测**，已降级为可选 |
