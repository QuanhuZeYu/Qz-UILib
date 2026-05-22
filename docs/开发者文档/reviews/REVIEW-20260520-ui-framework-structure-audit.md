# UI 部分代码框架结构审查

## 审查信息

- 审查日期：2026-05-20
- 审查主题：项目 UI 框架（HTML-like 文档体系 + 宿主集成层）的代码框架与分包结构合理性
- 审查视角：以"分层是否单向、职责是否单一、命名是否一致、对外面是否克制、可维护性是否健康"为核心评估维度
- 审查范围：`src/main/java/club/heiqi/uilib/ui/**` 下 21 个子包，共约 187 个 Java 文件、约 4.5 万行代码
- 明确排除：`uilib/font/**` 字体服务（按用户要求与本次审查口径）；`uilib/mixin`、`uilib/gl`、`uilib/config`、`uilib/client`、`uilib/internal` 中与字体重载强相关的部分一并排除
- 核实方法：按子包统计文件数与代码行数，逐包阅读关键类签名、构造方法、对外公开方法、类间依赖与命名前缀；对超过 1000 行的核心类追加方法清单核查；对死代码、跨包重复实体使用 `grep -R import` 全项目证据核查

---

## 审查结论（综合）

UI 部分代码**架构方向是合理的**：以浏览器（DOM/CSS/事件）为蓝本，构建了清晰的单向分层（dom + style → layout → paint command → renderer → render(GL/FBO)），命中测试是独立旁路；命令-回放分离、共享效果链、注入式时钟与运行时适配器等都体现了较深的设计考量。对外门面收敛克制，`UiDocumentScreens.createDocumentScreen(...)` 一个入口承担业务页，诊断/示例页通过 `Internal*` + 包级可见性彻底排除。

但同时存在以下**结构性技术债**，按严重度递减：

1. **5 个超千行的 god class 集中了大半引擎逻辑**（合计约 14000 行）
2. **示例 controller 与生产代码混杂**（约 250 KB / 5900 行 package-private 演示页打入主产物）
3. **`screen` 包过载（29 文件混 6 类抽象层级）与 `style` 包扁平（51 文件铺一层）**
4. **`dom/control` 实质是应用级控件却归属在 dom 包**
5. **`layout/UiAlignSelf` 是死代码**，全项目零引用
6. **30+ 个事件类模板代码重复**约 7×N 行
7. **input 包反向依赖 hud 与 screen**，破坏"input 是下游服务"的方向约束
8. **命名前缀 `Document*` / `Ui*` / `Internal*` / 无前缀** 四套并存且无书面规范

整体结论是：核心契约不需推翻，主要工作是结构整理与拆分。下面分项展开。

---

## 一、各子包整体作用与代码量

| 子包 | 文件数 | 行数 | 角色定位 |
|---|---:|---:|---|
| `ui/dom` | 48 | ~10039 | 文档作者层模型抽象（节点、事件、控件） |
| `ui/dom/control` | 19（计入上行） | — | 应用级高级控件（按钮、输入、表格、tooltip 等） |
| `ui/document` | 3 | ~2450 | 运行时适配（HtmlLikeDocumentWidget 等） |
| `ui/style` | 51 | ~6119 | CSS-like 样式系统（值、属性、选择器、级联） |
| `ui/layout` | 17 | ~5541 | HTML-like 文档布局核心（含 6 个旧 widget 测量值类） |
| `ui/paint` | 5 | ~1789 | 绘制命令生成与回放 |
| `ui/render` | 4 | ~3200 | 渲染后端（剪切栈、FBO、backdrop） |
| `ui/animation` | 8 | ~1987 | 声明式动画（transition / keyframes） |
| `ui/event` | 3 | ~169 | 宿主原始事件（鼠标/键/文本） |
| `ui/widget` | 4 | ~771 | 老 widget 体系（保留作为嵌入容器） |
| `ui/screen` | 29 | ~7548 | 业务门面 + 内部托管 + 示例 controller（混杂） |
| `ui/host` | 3 | ~341 | 宿主运行时支持（薄抽象） |
| `ui/hud` | 4 | ~896 | HUD 文档宿主 |
| `ui/input` | 6 | ~1133 | 输入服务与路由 |
| `ui/inventory` | 5 | ~497 | 物品栏槽位适配 |
| `ui/slot` | 2 | ~361 | 槽位通用模型 |
| `ui/image` | 4 | ~525 | 宿主图片渲染 |
| `ui/text` | 3 | ~230 | 文本测量服务（与字体系统对接） |
| `ui/theme` | 1 | ~64 | 单文件包：UiSurfaceStyle |
| `ui/runtime` | 1 | ~79 | 单文件包：UiRuntimeAdapters |
| `ui/diagnostic` | 2 | ~728 | 性能监视器 |
| `ui/control` | 0 | 0 | **空目录**，占名未用 |

---

## 二、架构亮点

值得肯定且应在后续重构中**保留**的设计：

1. **DOM 节点抽象贴合标准**：`DocumentNode → ElementNode/TextNode/DocumentFragmentNode`，`appendChild/insertBefore/replaceChild/cloneNode/querySelector*` 行为忠实，连跨文档/祖先环都做了校验。
2. **三级标脏机制**：`mutationVersion / layoutVersion / paintVersion` 配合 `UiStyleProperty.changeImpact(LAYOUT/PAINT)` 让"动画期间是否要重新布局"成为显式决策点。
3. **Paint command 与 renderer 解耦**：`DocumentPaintCommand` 不引用任何 GL 类型，`DocumentPaintEngine` 只产出值对象，`DocumentPaintRenderer` 才落地到 `UiRenderContext`，`RecordingUiRenderContext` 已被复用做测试。
4. **DocumentEffectChain 收敛 overflow / clip / backdrop / stacking 四处共享判定**，避免命中、绘制、滚动、布局四处各自维护隐式顺序。
5. **CSS 级联语义高保真**：`UiStyleResolver` 实现 `!important`、声明顺序、`inherit/initial/unset`、伪类伪元素分支；`UiSelector` 用 `(id, class/伪类, tag)` 三元组特异性 + sourceOrder 排序。
6. **依赖注入边界清晰**：`UiRuntimeAdapters`（窄类型 + 不可变 `with*`）、`DocumentAnimationClock`（接口可注入）、`DocumentCursorHost`（包接口 + 系统实现）、`FontRuntimeStatsSource` 都是注入而不是 service locator。
7. **WidgetBuildAttachmentTransaction**：build 期 commit/rollback 让首帧异常不留下半挂载状态。
8. **可见性收敛**：诊断页用 `Internal*` + package-private + `DevToolsScreenLauncher` 反射跳板，对外稳定面只剩 `UiDocumentScreens / UiHudDocumentHost / UiScreenManager / BaseScreen / UiSurfaceStyle / DocumentScreenChrome / DocumentUiScope`。
9. **`UiKeyboardCaptureState` 单状态机仲裁 screen/hud 键盘归属**，避免"两边都以为自己在收键盘"。
10. **`UiMainLayerSnapshotService` tile atlas 池化与 `PaintContextCompositor` 离屏层池**：对 backdrop-filter 这种昂贵效果是必要设计。
11. **`HtmlLikeDocumentWidget` 作为唯一桥接节点**：让 HTML-like 文档作为单个 widget 嵌入旧 retained 树，没有污染 Widget 基类。

---

## 三、问题清单（按优先级）

### P0 — 低风险高收益

#### P0-1 `layout/UiAlignSelf.java` 是死代码
- 全项目 `import` 该文件零次；所有调用方实际 import 的是 `style.UiAlignSelf`（唯一区别是缺 `BASELINE` 枚举值）。
- 历史遗留的孤儿，**可直接删除**。

#### P0-2 30+ 个 `DocumentElement*Event` 模板代码重复
- 每个 Event 类都手抄 `stopPropagation / stopImmediatePropagation / preventDefault / getEventPhase / isPropagationStopped / isDefaultPrevented` 方法，约 **7×N 行重复代码**。
- 应抽出 `AbstractDocumentElementEvent` 基类，子类只声明业务字段（target / 坐标 / 键码等）。

#### P0-3 事件契约不一致
- `DocumentElementFocusEvent` **没有** `DocumentEventControl`，无法 stopPropagation。
- `DocumentLinkActivationEvent` 用 `markHandled()` 而非 `preventDefault()`。
- `DocumentCustomEvent.preventDefault` 受 `cancelable` 控制，其他事件直接生效。
- 三种"取消语义"并存让作者层契约难以预期，应统一到一种。

#### P0-4 示例 controller 混在 `src/main` 打入主产物
- `screen` 包内 8 个 `HtmlLike*Controller` / `UiTest*` / `UiFontPerformanceBaseline` / `UiLayoutDiagnostics` 单文件最高 1627 行，合计**约 250 KB / 5900 行**，占 `screen` 包 50%+ 体积。
- 全部为 package-private 演示页，仅供 `/qzuilib test` 命令打开，可见性收敛得很好但仍打入产物。
- 建议迁到 `src/test` 或独立 `examples` 子模块；`InternalDiagnosticScreenRegistry` 通过 SPI 或反射按需加载。

### P1 — 包结构整理（仅移动文件，不改 API 行为）

#### P1-1 `screen/` 包过载
29 文件混了 6 类不同抽象层级与可见性：
- 业务门面：`UiDocumentScreens`
- 屏幕基类：`BaseScreen`
- 内部托管：`InternalDiagnosticScreenRegistry`、`InternalHostedScreenFactory`、`InternalScreenIdentity`、`UiScreenHostSession`、`UiHostBackgroundBlurRenderer`
- 文档页 SPI：`DocumentPageController`、`DocumentPageAuthoringSurface`、`DirectDocumentPageAuthoringSurface`、`DocumentPageRuntimeView`、`DocumentScreenChrome`、`DocumentUiScope`
- 示例 controller：`HtmlLike*` × 5、`UiTest*` × 1、`UiFontPerformanceBaseline`、`UiLayoutDiagnostics`
- 示例 model：`InventoryOverviewModel`、`UiTestMenuModel`、`UiTestMutationProbeState`、`UiTestDiagnosticsPresenter`、`InventoryOverviewSlotContentProvider`、`FontRuntimeStatsSource`

建议拆 `screen.host`（BaseScreen、UiScreenHostSession、UiHostBackgroundBlurRenderer、Internal*）、`screen.page`（DocumentPage* / DocumentScreenChrome / DocumentUiScope）、`screen.example`（HtmlLike* / UiTest* / 示例 model）。

#### P1-2 `style/` 包扁平 51 文件铺一层
建议按功能拆 `style.values`（长度、阴影、边框值类型）/ `style.props`（约 30 个枚举）/ `style.cascade`（Resolver、Declaration、Rule、Sheet、ComputedStyle、Variables）/ `style.selector`（UiSelector、UiPseudoClass、UiPseudoElement）。即便保持公开 API 兼容也能显著降低浏览成本。

#### P1-3 `dom/control/` 实质是应用级控件
- 19 个文件包含 `DocumentTextAreaControl`(912 行)、`DocumentSlotControl`(484 行) 等单文件 400-900 行的控件。
- 依赖 `HostImageSource / ResourceLocation / Keyboard`，已超出"纯 DOM 模型"。
- 项目 `ui/control` 目录正好是空的，应迁过去并让 `dom` 包回归"协议+模型"。

#### P1-4 `DocumentCursorHost` 包归属偏差
- `DocumentCursorHost` 与 `SystemDocumentCursorHost` 是宿主能力抽象，却埋在 `document` 包并使用包级可见性。
- `ui.host` 已存在并放着 `DocumentHostInteractionSession / RenderSupport / WidgetFactory`，应迁过去并提升为 public。

#### P1-5 单文件包整理
- `theme/UiSurfaceStyle`（1 文件 64 行）与 `style` 高度相关，可并入 `style/values` 子包。
- `runtime/UiRuntimeAdapters`（1 文件 79 行）作为依赖注入边界保留独立包尚有语义价值。
- 空目录 `ui.control/` 若不用作 P1-3 的目标位置，应删除以免占名。

### P2 — god class 拆分（影响内部结构，不影响公开 API）

#### P2-1 `DocumentLayoutEngine.java` — 2964 行
全部静态方法，inline / flex / table / absolute / sticky / text-wrap 50 余个 private static 方法挤同一文件。建议按子流程拆 `BlockFlow / FlexFlow / TableFlow / InlineFlow / TextWrap` 协作类。

#### P2-2 `HtmlLikeDocumentWidget.java` — 2107 行
6 大职责挤一个 Widget：渲染缓存（4 套 cachedXxx）、命中/passthrough、事件路由（约 25 个 dispatchXxx）、焦点（含 tabIndex 排序、scrollIntoView 路径）、拖拽与 HTML5-drag、光标级联解析。建议拆 `DocumentEventDispatcher / DocumentFocusManager / DocumentDragController / DocumentCursorResolver`，主体只剩 build / render / 路由委派。

#### P2-3 `UiRenderContext.java` — 1376 行
剪切栈 + 绘制 API + post pass 编排 + backdrop shader 调度多重职责挤一处；并把 `BACKDROP_SHADER_PROGRAM` 做成进程级静态单例（多窗口/多上下文隐患）。建议把 `ClipStack`、`PaintContextCompositor`、`DeferredPostMainPass` 提取顶级类；shader 状态改实例字段。

#### P2-4 `UiMainLayerSnapshotService.java` — 1418 行 / `DocumentAnimationTimeline.java` — 1380 行 / `DocumentScrollState.java` — 987 行
均接近或超过 1000 行，建议同时观察是否有提取协作者的空间（atlas 池、生命周期事件、滚动条几何等都是潜在拆分点）。

#### P2-5 `ElementNode.java` — 1155 行 / 106 方法
单文件同时承担：tagName / UID / 伪元素元数据、`LinkedHashMap` 属性、双套自定义事件 handler Map（冒泡 + 捕获）、`DomTokenList`、`UiStyleDeclaration`、focusable/tabIndex/ARIA、scroll/focus 桥接、`customRenderer`，以及 14 个具名 setXxxHandler + 4 个 captureXxxHandler 字段。建议把"事件 handler 容器"、"焦点/滚动桥接"、"ARIA/语义角色"拆成 mixin 或独立组件。

#### P2-6 `UiStyleDeclaration.java` ≈ 2300 行 / `ComputedStyle.java` ≈ 700 行 五点同改
每加一个 CSS 属性需改 6 处：`UiStyleProperty` 枚举 + `UiStyleDeclaration` 字段+setter+update 方法 + `UiStyleResolver.cascade` 调用 + `readDeclarationValue` switch + `ComputedStyle` 字段+构造参数+getter。典型 shotgun surgery 风险点；可考虑 `EnumMap<UiStyleProperty, Object>` + 类型化访问器收敛。

### P3 — 跨切问题

#### P3-1 input 包反向依赖宿主
- `UiHostInputCoordinator` 直接 import `ui.hud.UiHudDocumentHost`。
- `UiNativeTextInputInspector` 直接 import `ui.screen.BaseScreen`。
- input 应是被宿主调用的下游服务，这两条耦合让 input 包绑死了 hud 与 screen。可通过依赖注入（hud/screen 各自注册即时抢占处理器）或上移到 host 层解决。

#### P3-2 事件机制双轨制
- `addEventListener(type, handler, capture)` 通用注册 + 14 个 `setXxxHandler / setCaptureXxxHandler` 具名 setter 语义重叠。
- 长期看应只保留前者（与浏览器一致），后者降级为 thin wrapper 并标记 `@Deprecated` 或在文档明确分工。

#### P3-3 `host` 抽象过薄
- `ui.host` 只放支持函数（`DocumentHostInteractionSession / RenderSupport / WidgetFactory`）。
- `BaseScreen` 仍直接 `new UiScreenHostSession(this)`、`UiScreenHostSession` 仍在 screen 包内组装 `paintContextCompositor`、`mainLayerSnapshotService`、`backgroundBlurRenderer`。
- screen 与 hud 两条宿主路径独立维护一套相似流程（render 帧、deferred replay、interaction session），没有抽出 `DocumentHost` 公共接口。host 包当前更像 utility 而非抽象层。

#### P3-4 命名前缀混乱无规范
- HTML-like 文档路径用 `Document*`，老 widget 路径用 `Ui*`。
- 但 `dom.UiDocument`、`render.UiRenderContext`、`render.UiBackdropShaderProgram`（明明是文档路径渲染后端）又用 `Ui*`。
- `screen` 包内并存 `Document* / Internal* / Ui* / BaseScreen` 四套前缀。
- 建议在 `docs/使用文档/` 中固化命名约定：`Document*` = HTML-like 文档作者侧 API；`Ui*` = UILib 内部基础设施 / 通用工具；`Internal*` = 库内私有；无前缀类需说明理由。

#### P3-5 跨包名实体重复
- `layout.UiAlignSelf` 与 `style.UiAlignSelf` 同名不同义（前者死代码，见 P0-1）。
- `layout.UiInsets / UiLength` 与 `style.UiStyleInsets / UiStyleLength` 是两套并存的长度/边距模型——前者为 int 像素值用于早期 Widget 测量，后者为 PIXEL/PERCENT/CALC/AUTO 可解析值用于 HTML-like 文档。两条路径并存合理，但命名缺前缀区分易误读，应在 layout 包内统一加 `Widget` 或类似前缀。

#### P3-6 `UiStyleVariables` 与级联引擎脱钩
- 注释明确"当前阶段不解析 `var(...)` 表达式"，使变量沦为普通字典容器。
- 没有发挥 CSS 变量的级联与作用域价值；应实现最小 `var(name, fallback)` 解析或降级为 hint。

#### P3-7 `DocumentCustomRenderer` 抽象漏出
- `DocumentPaintEngine` 通过 `DocumentCustomRenderer.render(UiRenderContext, ...)` 强制让控件作者直接拿到渲染后端。
- "命令生成阶段中立性"在自定义渲染处被穿透；可改成派发自定义 `DocumentPaintCommand` 类型由 renderer 解释。

#### P3-8 `__elementUid / __appendGeneratedChild` 等双下划线伪 module-private 约定
- `dom` 包中 `__elementUid`、`__appendGeneratedChild`、`__allocateElementUid`、`__setInteractionRuntime`、`__createPseudoElementRuntime`、`__getCustomEventHandlers` 等命名遍布。
- 是 Java 缺少 module-private 时的妥协，但作为 `public` 暴露反而对作者侧可见。可收拢到独立的 `dom.internal` 子包并通过 friend 类（package-private 工厂）受控访问。

---

## 四、改进优先级建议

| 优先级 | 类别 | 项目 | 改动范围 | 风险 |
|---|---|---|---|---|
| P0 | 死代码清理 | 删除 `layout/UiAlignSelf` | 1 个文件 | 低 |
| P0 | 重复抽取 | 抽 `AbstractDocumentElementEvent` 基类 | ~30 个 Event 文件 | 低 |
| P0 | 契约统一 | 统一事件取消语义 | ~3 个 Event 文件 | 低 |
| P0 | 产物瘦身 | 示例 controller 移出 `src/main` | ~250 KB / 5900 行；需建 examples 子模块或 `src/test` 入口 | 中 |
| P1 | 包整理 | `screen` 拆 host/page/example 子包 | 29 文件移动 | 低 |
| P1 | 包整理 | `style` 拆 values/props/cascade/selector 子包 | 51 文件移动 | 低 |
| P1 | 包整理 | `dom/control` → `ui/control` | 19 文件移动 | 低 |
| P1 | 包整理 | `DocumentCursorHost` → `ui/host` | 2 文件移动 | 低 |
| P1 | 包整理 | 单文件孤儿包并入相关包 | 2~3 文件 | 低 |
| P2 | god class 拆 | `DocumentLayoutEngine` 拆子流程 | 2964 行核心引擎 | 高（需完整测试覆盖） |
| P2 | god class 拆 | `HtmlLikeDocumentWidget` 拆 4 协作者 | 2107 行核心 widget | 高 |
| P2 | god class 拆 | `UiRenderContext` 提取 ClipStack/Compositor/DeferredPass | 1376 行渲染上下文 | 中 |
| P2 | god class 拆 | `ElementNode` 抽 mixin | 1155 行节点类 | 中 |
| P2 | 五点同改收敛 | `UiStyleDeclaration` / `ComputedStyle` 走 EnumMap + 类型化访问器 | 2 个核心类 | 高 |
| P3 | 反向依赖修复 | input 包注入式解耦 | 2 个文件 | 中 |
| P3 | 双轨整理 | 事件 API 收敛到 addEventListener 主路径 | 跨包 | 中 |
| P3 | 抽象升级 | 抽 `DocumentHost` 接口让 host 真正承载抽象 | host + screen + hud | 中 |
| P3 | 命名规范 | 写入 `docs/使用文档/` 固化前缀约定 | 文档 | 低 |

---

## 五、不在本次审查范围内的事项

按用户口径与审查目标边界，以下内容**不在本次审查结论中**：

- `uilib/font/**` 字体服务（API、字形、布局、page、render、shader、util、event、config 全部子包）
- `uilib/mixin` 中字体相关 Mixin
- `uilib/gl/shader` 字体批渲染相关 GL shader 资源
- `uilib/font` 与 `FontConfig` 相关的运行时锁、generation handoff、SplashProgress 处理逻辑
- 字体诊断指标（仅在 UI 层引用 `FontRuntimeStatsSource` 这一注入接口被一并提及，未深入字体侧实现）

如需后续单独审查字体服务，应独立开一份 `REVIEW-YYYYMMDD-font-system-*.md`。

---

## 六、后续动作

- 本次审查结论已沉淀至本文件与 `docs/开发者文档/reviews/README.md` 索引页。
- 是否启动具体重构任务由用户后续决定；按优先级建议从 P0 死代码清理与事件基类抽取开始。
- 重构推进时，每个 P0~P1 项均建议独立提交（commit），便于回滚；P2 god class 拆分需先补齐核心引擎单元测试再动手。
- 本文件不再回写整改状态——按 `AGENTS.md` 规范，整改进度只在索引页保留摘要，避免审查正文持续膨胀。
