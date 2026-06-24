# 第一版开发者入口真实性与边界可信度审查

## 审查信息

- 审查日期：2026-05-12
- 审查主题：第一版开发者入口真实性与边界可信度审查
- 审查视角：不预设项目描述天然为真，只依据源码事实、测试证据和文档一致性判断项目当前真实边界
- 审查范围：对外业务入口、诊断与示例入口、HUD 交互链路、输入隔离链路、相关测试与使用文档

## 审查方法

- 先阅读 `docs/AI记忆文档.md`、`docs/使用文档/README.md`、`docs/开发者文档/开放化调整.md`、`docs/开发者文档/reviews/README.md` 与既有审查结论，明确项目自述边界。
- 逐项核对 `UiDocumentScreens`、`UiDiagnosticsScreens`、`UiHudDocumentHost`、`UiHostInputCoordinator`、`MixinGuiScreenKeyboardIsolation`、`QzUiLibClientCommand` 等关键入口与链路实现。
- 结合 `UiDocumentScreensTest`、`UiHudDocumentHostTest`、`QzUiLibClientCommandTest` 等测试，判断现有结论属于“代码已证明”还是“文档描述更乐观”。

## 审查结论

项目当前已经基本形成第一版开发者入口，但“推荐边界已经清晰且可信”这件事还不能完全成立。

更准确地说：

- `UiDocumentScreens.createDocumentScreen(...)` 已经是明确且真实可用的业务入口。
- 默认测试入口也确实从背包注入与全局热键收敛到 `/qzuilib test`。
- 但 HUD 交互边界、诊断入口的真实开放程度、以及业务 API 与内部页面体系的隔离程度，仍存在“文档说法比实现边界更乐观”的问题。

因此，如果站在一个不信任项目自述的外部维护者角度，这个项目当前更接近：

- 已具备一个可用的业务开屏入口；
- 仍保留一套公开可见的内部页面定义体系；
- HUD 交互能力存在比文档更窄的真实限制；
- 关键宿主注入链更多依赖源码推断和局部单测，而不是完整运行时证据。

## 主要发现

### 1. 交互 HUD 的真实能力边界比文档描述更窄

- 参考位置：`src/main/java/club/heiqi/uilib/ui/hud/UiHudDocumentHost.java`
- 关键实现：`isInteractiveInputEnabled(...)` 仅在 `(screen != null || screenClassName != null) && !mouseGrabbed` 时返回 `true`。
- 影响实现：`HudEntry.isVisibleIn(...)` 允许 `INTERACTIVE` 层在 `INGAME` 与 `CONTAINER` 可见，但输入接通条件要求当前必须存在打开的 `GuiScreen`。
- 结论：交互 HUD 在纯游戏内通常只会“可见但不可交互”。
- 与文档偏差：`docs/使用文档/03-宿主集成/Minecraft界面入口.md` 中虽然提到“纯游戏内锁定鼠标状态仍不主动接管”，但整体表述仍容易让读者理解成交互 HUD 是纯 HUD 场景下的通用交互层。
- 风险：接入方可能按“游戏内交互 HUD”心智设计功能，最终发现只有打开容器或其他界面后才能真正交互。

### 2. 文档对交互 HUD 的适用界面范围表述过宽

- 参考位置：`src/main/java/club/heiqi/uilib/ui/hud/UiHudDocumentHost.java:405-429,714-722`
- 文档位置：`docs/使用文档/03-宿主集成/Minecraft界面入口.md:129,151`
- 现状：文档写的是“任意已打开且鼠标已自由的界面里接通命中和焦点输入”。
- 代码事实：`GuiIngameMenu`、`GuiMainMenu` 以及大多数 `net.minecraft.client.gui.Gui*` 页面会被归为 `MENU`；而 `MENU` 分类下 HUD 直接不可见。
- 结论：真实边界不是“任意已打开界面”，而是“非菜单界面中的特定范围”，当前主要是容器类与部分自定义屏幕。
- 风险：项目文档会让使用者高估交互 HUD 的通用性，尤其是误以为暂停菜单、自定义菜单页也能复用同一条交互路径。

### 3. 对外业务入口与内部页面定义体系并未真正解耦

- 参考位置：`src/main/java/club/heiqi/uilib/ui/screen/UiDocumentScreens.java`
- 现状：`UiDocumentScreens` 除了暴露 `createDocumentScreen(...)`，还同时承载 `PageDescriptor`、`DocumentScreenDefinition`、页面控制器工厂、definition-backed screen 等内部页面机制。
- 相关位置：`UiDiagnosticsScreens` 直接建立在这套 definition 机制上。
- 结论：从代码结构看，项目尚未形成“对外业务 API”和“内部页面实现框架”的强隔离；当前更多是通过文档与推荐用法约束开发者只走 `createDocumentScreen(...)`。
- 风险：源码阅读者很容易顺着公共类继续接触内部页面概念，进而把本应是内部组织机制的结构当成半公开能力依赖。

### 4. 诊断与示例入口并非真正封闭的内部能力

- 参考位置：`src/main/java/club/heiqi/uilib/ui/screen/UiDiagnosticsScreens.java`
- 命令入口位置：`src/main/java/club/heiqi/uilib/client/QzUiLibClientCommand.java`
- 现状：项目已经做到默认不开热键、不注入原版背包按钮，并把显式测试入口收口到 `/qzuilib test`。
- 但代码事实是：`createUiTest()`、`createUiTestLayout()`、`createHtmlLikeSmoke()`、`createHtmlLikeGlass()`、`createInventoryOverview()` 都是公开静态方法。
- 结论：这些页面当前不是“只有内部命令才能进入”的封闭入口，而是任何外部代码都可以直接调用的公共入口。
- 风险：文档把它们定义为“开发调试入口”，但 API 形态并没有阻止外部依赖；一旦被第三方直接接入，后续再收口就会带来兼容包袱。

### 5. 关键输入注入链的真实性更多停留在局部单测与实现推断

- 参考位置：`src/main/java/club/heiqi/uilib/mixin/early/MixinGuiScreenKeyboardIsolation.java`
- 相关协同实现：`src/main/java/club/heiqi/uilib/ui/input/UiHostInputCoordinator.java`
- 测试位置：`src/test/java/club/heiqi/uilib/ui/hud/UiHudDocumentHostTest.java`、`src/test/java/club/heiqi/uilib/client/QzUiLibClientCommandTest.java`、
  `src/test/java/club/heiqi/uilib/ui/screen/UiDocumentScreensTest.java`
- 现状：当前测试对根节点契约、HUD 焦点与键盘抢占、命令参数、HUD 注销安全性等都有纯 JVM 层覆盖。
- 缺口：没有看到能够证明 Mixin 注入点在真实 `GuiScreen.handleInput()` 链路中稳定生效、且与第三方 GUI 包装链路共存无冲突的自动化运行时证据。
- 结论：项目在“局部契约正确”这件事上证据较强，但在“完整宿主链路已被真实环境证明”这件事上证据仍偏弱。
- 风险：一旦运行环境里有额外的 GUI/输入改写模组，当前这套链路的稳定性仍主要依赖人工验证和经验判断。

### 6. Forge 配置模板的对外扩展点表述比源码真实可用性更乐观

- 参考文档：`docs/使用文档/02-控件/Forge配置模板.md`
- 关键实现：`src/main/java/club/heiqi/uilib/config/ForgeConfigTemplateScreen.java:913-925,1165-1302`
- 现状：文档把 `Spec.addPropertyEditorFactory(...)` 与 `PropertyBinding` 描述成对外扩展点，并明确写到“`PropertyBinding` 已开放给外部继承”。
- 代码事实：`PropertyBinding` 是 `ForgeConfigTemplateScreen` 的非静态内部抽象类，构造和初始化都深度依赖外部页面实例与其内部约定。
- 结论：该扩展点并非不可扩展，但远没有文档读感中那么自然；它更接近“内部可延展缝”，而不是边界清晰、形态稳定的外部扩展 API。
- 风险：外部开发者会高估自定义属性编辑器的可接入性，真正落地时才发现要理解页面内部构造方式与卡片初始化约定。

### 7. Forge 配置模板的自动化证据主要覆盖草稿工具层，没有覆盖模板页面作为对外模板的核心承诺

- 测试位置：`src/test/java/club/heiqi/uilib/config/ForgeConfigTemplatePropertyDraftsTest.java`
- 文档位置：`docs/使用文档/02-控件/Forge配置模板.md:110-120`
- 现状：当前测试已经覆盖数值范围校验、列表写回、`validValues` 回退、保存失败回滚、空状态文案等关键逻辑。
- 但这些测试验证的核心对象是 `ForgeConfigTemplatePropertyDrafts` 与消息逻辑，不是 `ForgeConfigTemplateScreen` 作为对外可复用模板页面的行为本身。
- 结论：项目已经证明“模板背后的草稿规则”大体可靠，但尚未充分证明“模板页面本身已形成稳定对外模板”。
- 风险：对外开放模板页面后，快捷键、关闭路径、文档装配、分类卡片构建和扩展编辑器接入等页面级行为仍主要依赖人工运行验证。

### 8. “HTML-like 语义”主要是作者心智，不应被理解为浏览器式标签语义已真实落地

- 参考位置：`src/main/java/club/heiqi/uilib/ui/dom/ElementNode.java`、`src/main/java/club/heiqi/uilib/ui/style/UiStyleResolver.java:114-137`
- 控件位置：`src/main/java/club/heiqi/uilib/ui/dom/control/DocumentButtonControl.java`、`src/main/java/club/heiqi/uilib/ui/dom/control/DocumentTextInputControl.java`、
  `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentHostImageControl.java`
- 现状：`button`、`input`、`img` 等标签名确实存在于作者 API 中，部分文档也使用“真实 `button` / `input` 语义”这类措辞。
- 代码事实：标签名真正被运行时系统直接消费的地方主要是默认 `display` 推导，例如 `span/table/tr/td`；按钮可点击、输入框可输入、图片不可命中等行为主要来自 `setFocusable(...)`、事件 handler、属性约定和自定义渲染器，而不是标签名本身自动带来的浏览器语义。
- 结论：项目当前更准确的描述应是“HTML-like 结构与部分样式/语义心智”，而不是“浏览器式标签语义已经内建落地”。
- 风险：如果继续用过强措辞描述控件标签，接入方会天然预期更完整的内建可访问性、禁用态、表单语义或默认交互契约。

### 9. 宿主图片能力对运行时适配器透传存在强依赖，当前更像条件成立时可用，而不是天然稳固能力

- 参考位置：`src/main/java/club/heiqi/uilib/ui/runtime/UiRuntimeAdapters.java`、`src/main/java/club/heiqi/uilib/ui/dom/control/DocumentHostImageControl.java`
- 历史问题：`docs/开发者文档/errors/ERROR-20260509-screen-context-missing-runtime-adapters.md`
- 现状：文档把 `DocumentHostImageControl` 描述成“像 `img` 一样挂到文档中”的宿主图片能力。
- 代码事实：控件本身只是在元素内容区调用 `context.drawHostImage(...)`；真正能否画出 Minecraft 物品或贴图，完全依赖 `UiRuntimeAdapters` 是否沿宿主链路完整透传到 `UiRenderContext`。
- 结论：这项能力在默认宿主链路正确时可用，但其成立前提比文档直觉更强，而且项目已经出现过一次因适配器丢失导致页面图片整体缺失的回归。
- 风险：接入方若把它理解成“控件自身天然可用”，排障时会忽视运行时适配器链路这个关键前提。

### 10. 部分“非公开内部属性约定”在机制上仍是普通 attribute，边界仍主要依赖文档约束

- 文档位置：`docs/使用文档/01-入门/最小文档页面.md:56-57`
- 代码位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentHitTestEngine.java`、`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java`
- 现状：文档已经提醒 `data-hit-test-hidden="true"` 这类 attribute 是内部 overlay / 命中约定，不是公开 CSS 能力。
- 代码事实：这些约定仍然直接暴露为普通 attribute，作者代码完全可以手工写入，运行时也会真实生效。
- 结论：当前“不鼓励使用”的边界依旧主要靠文档自律，而不是靠专门 API、命名隔离或访问限制来守住。
- 风险：一旦业务代码开始直接依赖这些内部 attribute，后续就会像依赖诊断入口、内部页面 definition 一样形成难以回收的隐式公开面。

## 当前可信的部分

- `UiDocumentScreens.createDocumentScreen(...)` 的业务开屏入口是真实成立的，且已默认补齐根元素 `width:100%`、`height:100%` 与 `overflow-y:auto`。
- `/qzuilib test` 与 `/qzuilib hud_demo` 的显式命令入口已落地，旧的默认热键与背包按钮注入路径在当前代码中没有继续暴露。
- Forge 配置模板背后的属性草稿规则已有一定纯 JVM 测试支撑，包括数值范围、列表写回、`validValues` 遗留值回退、保存失败回滚和空状态文案。
- HUD 的若干关键局部边界已有测试支撑，包括：
  - 即时键盘抢占先于宿主处理；
  - 原生文本输入框聚焦时剥离 HUD 键盘输入；
  - 点击回调里即时 `unregister()` 不会破坏当前输入遍历；
  - 默认阻断点击穿透，显式 `data-hit-test-passthrough="true"` 时放行。

## 主要风险汇总

1. 外部接入者可能把交互 HUD 理解为“纯游戏内可交互 HUD 框架”，而不是“非菜单界面上的交互叠层”。
2. 外部开发者仍可能直接依赖诊断页与示例页 API，削弱首版开放边界的可收口性。
3. 源码结构会把内部页面定义体系暴露给阅读者，增加错误依赖内部概念的概率。
4. 输入注入链的完整可信度仍不足以只靠单测结论背书，后续环境变化时有回归风险。
5. 配置模板虽然已经对外文档化，但页面级开放性和扩展点稳定性尚未被充分证明。
6. “HTML-like 标签语义”与“内部 attribute 约定”都还存在被外部过度相信、进而形成错误依赖的风险。

## 优先整改建议

1. 收紧 HUD 文档表述，把“交互 HUD 当前真实可交互范围”写成明确约束，避免继续使用“任意已打开界面”这类宽泛表述。
2. 明确区分“公开但不推荐”与“内部实现”两类入口；如果诊断页未来不希望被依赖，应考虑进一步收口 API 可见性或增加更明确的隔离层。
3. 继续压缩 `UiDocumentScreens` 对内部页面 definition 机制的外部可感知度，降低源码阅读时的边界混淆。
4. 为宿主输入注入链补充更贴近真实运行环境的验证证据，至少在审查文档或诊断文档中明确哪些结论已经过运行时验证，哪些仍主要基于源码推断。
5. 收紧配置模板文档中关于“对外扩展点”的表述；若希望 `PropertyBinding` 真正成为外部扩展面，应进一步降低其对外部页面实例和内部初始化约定的耦合。
6. 调整文档中“真实 `button` / `input` / `img` 语义”这类措辞，改为更贴近当前实现的“HTML-like 结构与控件适配语义”。
7. 对 `data-hit-test-hidden`、`data-hit-test-passthrough` 等内部 attribute 约定建立更明确的封装边界，至少避免继续把它们默默扩散成事实上的公开作者接口。

## 结论摘要

- 以“是否已有一个真实可用的业务入口”来看：当前答案是肯定的。
- 以“项目自述边界是否已经足够可信”来看：当前答案是否定的。
- 当前最主要的问题不是入口不存在，而是若不仔细读源码，外部开发者会比项目实际能力更乐观地理解 HUD 交互范围、诊断入口封闭性、配置模板扩展性、宿主图片能力前提，以及 HTML-like 语义本身的真实含义。
