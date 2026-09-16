# 规划-headless 验收设施（立项草案）

**状态：** 立项**草案**（2026-09-17）。第一轮一手核查 + 方向骨架已成形，**M0 技术闸门已在本地实测打通**（见 F7）；
§六 五个岔路待用户裁定后冻结。裁定前本档不构成施工依据。
**来源：** 用户发起「QzUILib 立项：完善 headless 设施，分辨率覆盖 360P~2K，不再采用纯软光栅，可用 OpenGL，开始初步规划方向」，
补充口径「需要包含完整的 headless 纯代码输入（鼠标键盘等）方式」。
**目标仓：** Qz-UILib（branch `4.0`；MC 1.7.10 / Java 8 语法基线 / GTNH `2.9.0-beta-3`）。
**基线：** 本档全部事实为 2026-09-17 一手实测（探针脚本 + 只读命令），**未跑真机**；测试集规模以实时构建为准。

## 一、需求口径（用户给定，不自行扩展）

| 编号 | 需求 | 本档落点 |
|---|---|---|
| R1 | 完善 headless 设施 | §三 四件套 |
| R2 | 分辨率覆盖 360P~2K | §四 B |
| R3 | 不再采用纯软光栅，可用 OpenGL | §四 A |
| R4 | 完整 headless 纯代码输入（鼠标键盘等） | §四 C |

## 二、立项前一手核查事实

### F1 现有 headless 有两条互不相通的通道，都不覆盖「真机整帧」

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
  **后端可替换面很窄，是本次立项最有利的结构事实**；但要留意 `drawImage` / `drawSegments` /
  `publishTextDemand` 都带**静默 no-op 兜底**——headless 后端不显式实现时，缺图与缺富文本不会报错，
  只会静默少内容（本仓反复踩的「能力探测静默降级」模式）。故出图完整性必须有显式判据，不能只看「没抛异常」。

### F3 输入事件模型完整，注入入口不完整

- `RawInputEvent`（`ui/scene/input/`）：`RawEventKind` = KEY / POINTER / TEXT 三类，已含 4 修饰键、
  `nativeKeyCode`、`nativeScanCode`、`wheelDelta`、`deltaX`/`deltaY`、`SceneMouseButton`（LEFT/RIGHT/MIDDLE/
  BUTTON_4/BUTTON_5/NONE）、`ScenePointerAction.CANCEL`（窗口失焦）。
- 生产链路：`PlatformInputSource.drainFrame()` → `SceneInputFrame` → `SceneRuntime.route`。
- 注入入口 `SceneInteractionHarness` 已覆盖 `click` / `press` / `release` / `pressReleaseAcrossFrames` /
  `moveTo` / `moveAt` / `scroll` / `pressKey` / `typeText` / `clickAt`。
  **缺口**：按钮硬编码 `LEFT`、无修饰键、无 `KEY_RELEASED`/`REPEAT`、无右键/中键、无拖拽、无双击/三击、
  无横向滚轮、无 `CANCEL`（失焦）、无 IME/外部文本模式切换、时间戳恒 `1000L`（做不了长按与双击时间窗）。

### F4 测试 JVM 的 GL 现状（实测）

- 测试运行时 classpath **有** `com.github.GTNewHorizons:lwjgl3ify:3.0.31`（jar 名 `lwjgl3ify-3.0.31-dev.jar`），提供
  `org.lwjgl.opengl.Display|GLContext|PixelFormat|ContextCapabilities`、`org.lwjglx.*`、
  `org.lwjgl.input.Keyboard|Mouse`。
- **没有 `org.lwjgl.opengl.GL11`**（LWJGL2 主 API 不在 test classpath），也没有 LWJGL3 实体
  （`org.lwjgl.glfw.GLFW`、`org.lwjgl.opengl.GL` 均 `ClassNotFoundException`）
  → **生产渲染代码在测试 JVM 里链接不上**，这是当前最硬的一处阻塞。
- lwjgl3ify 的 `org.lwjgl.* → org.lwjglx.*` redirect 由 RFB transformer + FML coremod 在启动期织入，
  **不是可以直接丢进 test classpath 的替代品**；3.x 的 `Display` 已改 SDL3 后端。
- LWJGL2 制品已在本地 Gradle 缓存（项目经 MC 依赖已拉取，可离线加依赖）：
  `org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209`、`lwjgl_util:2.9.4-nightly-20150209`、
  `lwjgl-platform:2.9.4-nightly-20150209:natives-{windows,linux,osx}`。
- 测试 JVM 为 JDK 17（RFG + Jabel 编 Java 8 字节码），`maxHeapSize = 1024m`。

### F5 CI 环境（一手核实 GTNH 共享工作流 pin `8d2e9d2`）

- 跑测试的步骤是
  `xvfb-run --server-args="-screen 0 1366x768x24" ./gradlew --build-cache --info --stacktrace build`，
  预装 `mesa-utils xvfb x11-xserver-utils`。
- 即 **CI 的 `test` 天生有 DISPLAY + Mesa(llvmpipe)**，本立项**不需要新增 Xvfb 步骤**；
  但 **Xvfb 屏幕固定 1366×768×24**，低于 2K 目标。
- 由此得一条硬约束：**分辨率覆盖必须走 FBO 离屏**（`GL_MAX_TEXTURE_SIZE` 量级足够），
  **不得依赖窗口或默认 framebuffer 尺寸**。
- 该步骤用 `--build-cache`：GL 出图用例若进 `build`，须确认 Gradle Test 缓存输入不会跨环境错误命中。

### F6 现有软光栅的真实代价与缺口（实测 + 历史）

- **规模实测**：`build/reports/` 现存 **191 张 PNG / 50.1 MPx**；单张最大 `2536×2733 = 6.93 MPx`（`@4x` 放大图）。
- 即 CPU 软光栅**已经在跑比单个 2K 帧（3.69 MPx）更大的画布** → **性能不是本次升级的主因**。
- 主因是**保真度面**：软光栅只覆盖字形 quad（`FontSoftwareRasterizer` 类头自述「多抽头 AA 与 smoothstep
  属真机 shader 的抗锯齿近似，软件侧默认不做」）；scene 侧的圆角 band、裁剪/stencil、group opacity、
  backdrop 玻璃快照链**一条都不覆盖**；顶点层变换在 mock 后端不可观测。
- 历史实证：`ERROR-20260905-software-rasterizer-half-quad-rotated-sampling`（半 quad 180° 旋转采样致全部历史
  出图不可读，且与放大倍率无关）；`ERROR-20260818-overlay-toast-full-width-and-top-align`（headless 全绿而真机
  两处显示缺陷）；`docs/反馈层/踩坑记录.md`「测试盲区：为什么 headless 全绿却真机翻车」。

### F7 M0 技术闸门已本地实测通过（2026-09-17）

在**真实 gradle test JVM**（JDK 17.0.19，与 CI 同版本）里，用 init script 临时注入 LWJGL2 依赖与 natives
（**不改仓库构建文件**；探针测试类用完即删，`git status` 确认工作区无残留），测得：

| 观测项 | 结果 |
|---|---|
| `org.lwjgl.opengl.GL11` 来源 | `lwjgl-2.9.4-nightly-20150209.jar` |
| `Display.create(PixelFormat stencil=8)` | OK（约 0.3~0.4 s） |
| `GL_VERSION` / `GL_RENDERER` | `4.6.0 NVIDIA` / `RTX 5070 Ti Laptop GPU` |
| `GL_STENCIL_BITS` | 8 |
| `1920×1080` / `2560×1440` FBO | `GL_FRAMEBUFFER_COMPLETE`（36053） |
| **`UiRenderContext` 构造** | **OK**（无需 MC 运行环境） |
| **生产 `UiRenderContext.fillRect` 画到自建 FBO** | **OK**；读回中心像素 `33,66,cc,ff` = 传入的 `0xFF3366CC` |
| 端到端（建上下文 + 2K FBO + 绘制 + `glReadPixels` + PNG 编码） | **534 ms**（其中读回 4 ms、PNG 编码 97 ms） |

结论：**「不再纯软光栅、改用 OpenGL」在测试域技术可行，且生产渲染后端零改动即可复用**。
仍待实测的是 CI 侧（Xvfb 1366×768×24 + Mesa llvmpipe）与非 Windows 平台，见 F8 与 §八。

### F8 Linux / CI 侧的 GL 边界（调研核实，2026-09-17）

- LWJGL2 的 Linux native（`liblwjgl64.so`）动态依赖 `libX11.so.6` / `libGL.so.1` / `libXrandr` /
  `libXcursor` / `libXxf86vm`，并引用 `XOpenDisplay` + `glXGetProcAddress`：**只有 GLX 一条通路**，
  无 EGL/OSMesa 后端 → **Linux（含 CI）必须有 X server**；既有 CI 已用 `xvfb-run`（F5），故**无需新增 CI 步骤**。
- LWJGL2 的三种「无窗口」手段都不解决 Linux：`Display` 无隐藏窗口开关；`Display.setParent(Canvas)` 仍要真实
  X 窗口（只是把 GL 画到 AWT peer 上）；`Pbuffer` 是已弃用的 GLX pbuffer，同样要 X。
- **线程约束**：GL 调用必须固定在创建上下文的那个线程上（JUnit 测试线程即主线程，天然满足；跨线程用 GL 不行）。
- CI 侧建议显式设 `LIBGL_ALWAYS_SOFTWARE=1` / `GALLIUM_DRIVER=llvmpipe`，消除驱动选择的不确定性。
- llvmpipe 性能量级（**调研推断，非实测**）：`2560×1440` 单帧约 10–10² ms 量级；建议 CI 常态跑小分辨率冒烟、
  1440p 只跑单帧。
- **反证澄清**：把 `lwjgl3ify` 当普通依赖丢进 test classpath 不可行——其 redirect 需 RFB system classloader +
  UniMixins 全套 JVM 参数（dev jar 内含 446 个 `org.lwjglx` 类）。真无 X 的兜底只有 LWJGL3 null 平台 + OSMesa
  （需 `libosmesa6`，且与生产 LWJGL2 API 面不兼容），故**不作主路径**，仅作环境不可用时的备选。

## 三、目标形态：headless 验收运行时四件套

| 件 | 内容 | 现状 |
|---|---|---|
| 驱动 | 多帧推进 + 帧时间可控（注入而非 `System.nanoTime` 独裁） | `render` 已有；时间源需可控化 |
| 输入 | 纯代码设备模型：鼠标（移动/按键/滚轮/拖拽）、键盘（按下/释放/重复/修饰键）、文本（char/整串/IME 接管）、焦点、`CANCEL` | 事件模型齐、注入入口缺 |
| 渲染 | 真 GL 离屏（FBO）→ 像素；路由与生产同源 | 缺上下文与后端 |
| 观测 | PNG 人眼判读 + 基础机判 + 命令轨迹 + 结构化诊断 | 字体侧有、scene 侧无 |

## 四、方案骨架

### A 渲染载体（R3）

- **A1（推荐）**：test 域显式补 LWJGL2 API + natives（`testRuntimeOnly`，natives 按 OS 分类器），
  与生产 `org.lwjgl.opengl.GL11` 调用面**同源**；`Display.create(PixelFormat)` 取 GL 上下文
  （本地 Windows 直接可用；CI 靠既有 Xvfb），**渲染目标一律自建 FBO**（尺寸 = 目标分辨率，与窗口/Xvfb 屏幕解耦），
  `glReadPixels` 回读 → PNG。
  - `PixelFormat` 需含 **stencil**（`ClipStack` 的 clip 走 scissor + stencil mask）与 alpha；窗口尺寸只需最小。
  - **线程约束**：GL 调用必须固定在创建上下文的那一个线程上（JUnit 测试线程天然满足）。
  - CI 侧建议显式设 `LIBGL_ALWAYS_SOFTWARE=1` / `GALLIUM_DRIVER=llvmpipe`（见 F8）。
- **A2（回退）**：无 GL 环境（无 natives / 上下文创建失败）回退到现有软光栅出图；是否常驻待裁定（§六-2）。
- **A3（不采用）**：在测试域复用 lwjgl3ify / LWJGL3。理由见 F4：redirect 依赖 coremod，测试域拿不到，
  且会让「测试路径 ≠ 生产 API 面」。
- **代表性边界**：A1 的 GL 是桌面 LWJGL2 + WGL/GLX，**不代表**真机 MC 宿主、Angelica 或 lwjgl3ify 上下文；
  它代表的是顶点变换后的像素位置、clip/stencil 语义、混合、shader（字体 AA、玻璃）与多分辨率布局。

### B 分辨率矩阵（R2）

- 建议档位（待裁）：`640×360` / `854×480` / `960×540` / `1280×720` / `1600×900` / `1920×1080` / `2560×1440`。
- 内存口径（RGBA 单帧，Python 验算）：360P 0.88 MiB → 2K **14.06 MiB**；2K 相对 360P 为 **16 倍像素**；
  全档同时持有 35.4 MiB。测试 JVM `maxHeapSize=1024m` 下 2K 单帧仅占 **1.37%**
  → **约束不在内存，在串行与复用**：矩阵逐档跑、逐档释放，不并行持有。
- 第二维：MC GUI Scale（1/2/3/4）只在宿主边界验收需要（`HostViewportScale.compose`）；默认矩阵不含，
  需要时作为显式子集。
- 与密度档位联动：`PickerDensityPreference.AUTO` 按视口求解密度档，矩阵应覆盖 AUTO 的档位切换边界
  （现仅 `PickerDensityPanelWiringTest` 断过 1920×1080 → 标准档）。

### C 输入设备模型（R4）

- **C1 设备模型**：`HeadlessInputDevice`（`moveTo` / `press` / `release` / `click(button)` /
  `scroll(dx,dy)` / `keyDown` / `keyUp` / `type` / `compose(text)` / `focus` / `cancel`），
  内部按帧累积 `RawInputEvent` 并产出 `SceneInputFrame`。
- **C2 时间轴**：事件时间戳可控（默认每帧 +16.67ms 单调推进），支持长按、双击时间窗、跨帧 DOWN/UP。
- **C3 兼容**：保留 `SceneInteractionHarness` 的节点快捷方法作为语法糖（`click(node)` 等），底层换成设备模型，
  既有调用点不破。
- **C4 纪律**：必须走生产链路 `PlatformInputSource → SceneInputFrame → SceneRuntime.route`，**不旁路 router**。
- **C5 焦点与文本**：`KeyboardTextInputSource` 的 `pushText` / `setExternalTextMode` 已提供「外部文本接管 ↔
  char 路径回落」两态，设备模型需两种模式都可驱动。

### D 观测与产物

- PNG：`build/reports/headless/<suite>/<case>-<WxH>.png`（沿用既有 `build/reports` 约定）。
- 机判（**不做字面快照**）：非空墨水率、绘制不越界、跨分辨率结构不变量（如「面板不超出视口」）。
- 命令轨迹：`RecordingRenderBackend` 保留为「路由/命令面」证据，与像素证据**不可互替**。
- 人眼判读：沿用用户既有姿势（headless 出的 PNG 由用户目检）。

## 五、已声明的边界（不能声称的）

1. FBO 出图**不代表**真机 MC 宿主 / Angelica / lwjgl3ify 上下文；
2. **不覆盖**原版包装类禁令（Tessellator 等）一类问题；
3. GL 输出依赖驱动实现，**不作为逐像素金样**（仓内已有「金样 OS 条件化」先例）；
4. **不经过** `LwjglInputSource` 的 poll 差分与宿主回调旁路语义——headless 注入的是帧，
   桥内部的差分/边沿丢失类缺陷不在覆盖范围内。
5. **保真分层，不可越级声称**：契约层（`RecordingRenderBackend` / FakeGl，必跑）→ 几何与语义层
   （llvmpipe 渲染 + 容差或结构断言，CI 可跑）→ 观感层（真机截图基线，仅用户可判）。
   headless GL 出图属**第二层**，不构成第三层证据。

## 六、待用户裁定（冻结前必须收口）

| # | 岔路 | 建议 |
|---|---|---|
| 1 | **依赖引入**：test 域新增 `org.lwjgl.lwjgl:lwjgl` + `lwjgl-platform`（natives 按 OS 分类器） | 批准（属依赖变更，按规范须先确认） |
| 2 | **软光栅去留** | 保留为「无 GL 回退」，但**降级出验收主路径**；其语义测试不受影响 |
| 3 | **分辨率档位集合** | 先取 360P / 720P / 1080P / 2K 四档为主矩阵，其余按需扩 |
| 4 | **设施归属** | test 域（不进生产 jar）；如需实机诊断另开 `internal.devtools` 入口 |
| 5 | **CI 策略** | GL 用例进常规 `gradlew build`（每 PR 都跑，CI 有 Xvfb+Mesa，耗时 ↑）vs 独立任务/标签过滤 |

## 七、建议分批

- **M0 闸门（可行性）**：本地部分**已通过**（F7）。剩两件事——① 按裁定在 `dependencies.gradle` 落
  `testRuntimeOnly` 依赖（当前仅为 init script 临时注入，仓库未改）；② 在 CI 跑一次确认 Xvfb + Mesa(llvmpipe)
  路径与耗时。**CI 不通过则本立项降级为「软光栅补齐 scene 侧 + GL 用例仅本地跑」。**
- **M1 渲染地基**：headless GL 后端（`UiRenderBackend` 实现，复用或薄包 `UiRenderContext`——F7 已证其可构造、可绘到 FBO）；
  出图完整性需显式覆盖 `drawText` / `drawSegments` / `drawImage`（后两者是 default no-op，漏接不报错），
  再接字体 GL 尾端；与既有软光栅出图做一次对拍，差异登记为记录基线（不作逐像素锱铢必较）。
- **M2 输入地基**：设备模型 + 语义全覆盖 + 既有 harness 迁移（调用点零破坏）。
- **M3 端到端**：headless host 多帧驱动 + 分辨率矩阵 + 冒烟场景（复用 playground 页或 markdown 页）。
- **M4 收口**：软光栅降级为回退、文档与规格落点、CI 接线与耗时评估。

## 八、技术未知项状态（按 2026-09-17 实测更新）

| # | 未知项 | 状态 |
|---|---|---|
| 1 | LWJGL2 `Display.create()` 在 JDK 17 测试 JVM 下能否初始化 | **已实测通过**（F7） |
| 2 | CI（Xvfb 1366×768×24 + llvmpipe）下 GLX 上下文与 2K FBO 可用性 | **未实测**，需一次 CI 运行 |
| 3 | `UiRenderContext` 在无 MC 资源环境下的可构造性 | **已实测通过**（F7） |
| 4 | 2K 一帧的耗时预算 | **已实测**：FBO + 绘制 + 读回 + PNG 端到端 534 ms（本机 GPU） |
| 5 | 完整 scene（含玻璃/裁剪/字体）2K 单帧耗时 | **未实测**，M1 出图后量化 |
| 6 | 非 Windows 平台（Linux/llvmpipe、macOS）的上下文与读回 | **未实测** |
