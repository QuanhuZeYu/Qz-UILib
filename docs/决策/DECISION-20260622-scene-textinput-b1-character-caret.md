# 决策：SceneTextInput 升级为 B1 字符级单行输入框核心版

## 背景

Demo Scene 2 的输入控件真机反馈后，用户明确表示当前输入控件「不符合预期、BUG 多、设计难理解」，要求重新设计并逐项拍板。

原 `SceneTextInput` 档位 A 的历史决策是「范式升级而非功能升级」：用声明式 caret 子节点替换旧栈命令式 caret 绘制，但保留 caret 恒末尾、无方向键、无选区等能力边界。该方案在 strangler 行为等价阶段合理，但用户现在重新拍板希望输入框具备可理解的字符级编辑核心。

## 候选方案

- **继续 A/A+ 修补**：保留末尾 caret，只修 placeholder、键盘桥、caret 高度等缺陷。
- **B1 字符级核心版**：支持 caretIndex、点击定位、方向键/Home/End、中间插入、Backspace/Delete，但不做选区/剪贴板/IME/闪烁/横向滚动。
- **B2/B3 完整单行编辑**：在 B1 基础上加入选区、拖拽选择、Ctrl+A/C/V/X、剪贴板。
- **C 输入系统扩展**：加入 IME composition 事件链与组合文本预览。

## 最终选择

本期选择 **B1 字符级单行输入框核心版**。

实现约束：
- 文本真值仍由外部 `value` signal 唯一持有；控件不缓存 value、不自改 value。
- 控件内部只持有一个本地 UI 状态 `caretIndex`，语义为真实文本的码点索引。
- `caretIndex` 使用 builder 闭包内局部 `Signal<Integer>`，对齐 `SceneSlider.draggingValue` 先例；本期只有单个 signal，不引入 record 打包，record 留待选区/滚动等多状态时再做。
- 视觉结构采用 `prefixText + caret + suffixText` 三节点：caret 仍是普通声明式节点，夹在 prefix/suffix 之间。
- `SceneRuntime` 持有 `SceneTextMeasurer` 窄端口，并暴露只读 `measureTextWidth` / `lineHeight` 委托；宿主层创建同一个 `TextMeasureServiceSceneAdapter` 同时注入 `SceneRuntime` 与 `SceneLayoutEngine`。
- PASSWORD 的点击定位和 caret 宽度按掩码后的 display 前缀度量，不按真实 value 前缀度量。
- 本期不实现横向滚动：长文本超出 root 后被 `clipChildren` 裁剪，caret 可能不可见。

## 选择原因

继续修补 A 不能解决用户的核心诉求：输入框的行为仍不像正常输入框，且 caret 位置靠「ROW 布局自然排到末尾」的技巧本身也不直观。

B1 是最小可理解升级：只增加一个 `caretIndex` 本地 UI 状态，就能解释点击定位、方向键和中间插入/删除；同时仍保留受控文本真值单一来源，不引入选区、剪贴板、IME 等更重的状态系统。

`prefixText + caret + suffixText` 优于单 textNode + caret transform：
- 布局和点击定位使用同一 `SceneTextMeasurer`，caret 视觉位置与点击命中自洽。
- 不依赖绝对定位/overlay 能力，也不踩 transform + clipChildren 的未验证组合。
- caret 仍是普通节点，符合声明式 Display List 范式。

本期不做横向滚动是有意 YAGNI：scene 当前只有 `scrollOffsetY`，无 `scrollOffsetX`；横向滚动需要独立地基，不应混入 B1。

## 影响范围

- `SceneRuntime` 增加 `SceneTextMeasurer` 窄端口注入与只读测量方法。
- `SceneHostWidget`、`SceneControlsHostWidget`、`SceneScrollHostWidget` 改为 runtime/layoutEngine 同源 measurer 双注入。
- `SceneTextInput` 从档位 A 升级到 B1：三节点显示、`caretIndex` 本地 signal、点击定位、方向键/Home/End、中间插入、Backspace/Delete。
- `SceneTextInputTest` 重写为 B1 行为验收，覆盖受控不自改、字符级移动、插入删除、emoji 码点安全、点击定位、readOnly/disabled、password 掩码定位、外部 value 缩短 clamp。

## 后续注意事项

- B1 不新增 NORTH_STAR 偏离登记：`SceneRuntime` 只持 scene 窄端口，不 import adapter 或 `ui.text.*`；handler 只写 `caretIndex` 或 `onChange`，不直接改节点属性。
- `caretIndex` 所有读取必须 clamp 到 `[0, codePointCount(value)]`，防外部 value 异步缩短时越界。
- 若未来做选区，应引入状态族 record（例如 anchor/focus/scrollOffset），并新增 range 渲染测试。
- 若未来做剪贴板，应通过宿主桥接，不让 scene 核心 import 平台或 MC 剪贴板 API。
- 若未来做 IME，应扩输入半环事件模型，不应只在控件层硬塞 composition 逻辑。
- 若未来做横向滚动，应独立设计 `scrollOffsetX` 或等价 viewport/content 结构，并评估是否需要 NORTH_STAR 偏离/转正。
