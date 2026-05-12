# HTML-like 框架开发者易用性审查

## 审查信息

- 审查日期：2026-05-08
- 审查主题：HTML-like 框架对于其他开发者是否足够易用
- 审查范围：对外使用文档、业务入口 API、控件作者 API、诊断示例入口

## 审查结论

当前 HTML-like 框架对项目维护者已经基本可用，但对首次接入的其他开发者还不能算足够易用。

更准确地说，`UiDocumentScreens.createDocumentScreen(...)` 已经把业务开屏路径显著收敛，但首次上手仍存在作者心智模型分裂、控件文档不连续、默认约束依赖人工记忆等摩擦点。

## 主要发现

### 1. 最小页面路径仍有隐式必填约束

- 参考位置：`docs/使用文档/01-入门/最小文档页面.md`、`src/main/java/club/heiqi/uilib/ui/screen/UiDocumentScreens.java`
- 现状：`createDocumentScreen(...)` 已负责创建 `UiDocument`、挂载 `HtmlLikeDocumentWidget`、启用根视口滚动并返回 `GuiScreen`。
- 问题：页面作者仍需手动为根元素声明 `100%` 宽高，否则页面区域可能不符合预期；该约束目前主要由文档提醒承担。
- 影响：首次接入者容易把它理解成框架特例，而不是真正的开箱即用入口。

### 2. DOM-like API 与推荐控件 API 的心智模型不完全一致

- 参考位置：`src/main/java/club/heiqi/uilib/ui/dom/UiDocument.java`、`src/main/java/club/heiqi/uilib/ui/dom/control/DocumentTextInputControl.java`、`docs/使用文档/02-控件/基础控件.md`
- 现状：`UiDocument` 暴露了 `document.input()` 等 DOM-like 工厂；按钮控件明确使用真实 `button type="button"` 元素。
- 问题：推荐的 `DocumentTextInputControl` 实际使用的是 `div` 作为根元素，而不是 `document.input()`；这会让开发者难以判断哪些 tag 只是结构语义，哪些会承载真实交互语义。
- 影响：外部开发者容易在“直接拼 DOM 元素”和“优先使用控件适配器”之间产生困惑。

### 3. 控件文档偏能力清单，缺少连续业务示例

- 参考位置：`docs/使用文档/02-控件/基础控件.md`、`docs/使用文档/02-控件/表格与背包槽位.md`
- 现状：按钮、输入、开关、分段选择器、表格等控件边界说明已经存在。
- 问题：文档更像能力列表，缺少一个完整业务页面示例来串联标题、输入、按钮、事件处理、页面打开方式等常见接入动作。
- 影响：新开发者知道 API 存在，但不容易快速形成“正常页面应该怎么拼”的操作路径。

### 4. 对外推荐入口与内部页面体系共处同一大类，可发现性一般

- 参考位置：`src/main/java/club/heiqi/uilib/ui/screen/UiDocumentScreens.java`
- 现状：`UiDocumentScreens` 同时承载业务推荐入口、诊断页入口、descriptor 与 definition 相关结构。
- 问题：外部开发者在查看源码时，很容易先接触到内部页面组织概念，而不是一眼看到“我应该只用 `createDocumentScreen(...)`”。
- 影响：源码入口的阅读成本高于理想状态，不利于外部开发者快速建立稳定用法。

### 5. 诊断页更像维护者工具，不像接入者示例中心

- 参考位置：`docs/使用文档/03-宿主集成/Minecraft界面入口.md`、`docs/使用文档/04-诊断入口/指令触发方案.md`
- 现状：已有 `/qzuilib test`、Smoke 页、Glass Lab 页、背包概览示例页等开发期入口。
- 问题：这些页面主要承担验证和调试职责，尚未与正式接入文档形成清晰的“先看哪个示例，再对应哪份文档”的体验链路。
- 影响：新开发者难以把示例页中看到的效果快速映射回具体 API 用法。

## 现阶段优点

- `UiDocumentScreens.createDocumentScreen(...)` 已经提供了明确的业务开屏入口。
- `docs/使用文档/01-入门/项目定位与能力边界.md` 对能力边界说明较清晰，能减少错误预期。
- 基础控件已形成 `Document*Control` 级别封装，并有相应测试覆盖，内部可靠性基础较好。

## 优先改进建议

1. 让 `createDocumentScreen(...)` 默认兜底根节点全视口样式，减少首次接入的隐式前置知识。
2. 新增一篇完整业务页面示例文档，串联标题、输入、按钮、事件和宿主打开方式。
3. 在文档中明确约定“结构节点优先直接写 DOM-like 元素，交互控件优先使用 `Document*Control`”，并解释相关边界。

## 结论摘要

- 以维护者标准看：当前框架已经可用。
- 以首次接入的其他开发者标准看：当前仍未达到足够顺手的程度。
- 后续开放化工作应继续优先围绕业务入口收敛、控件心智一致性和接入文档连续性推进。
