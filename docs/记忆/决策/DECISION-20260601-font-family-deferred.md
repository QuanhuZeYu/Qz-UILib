# 决策：font-family 暂不接通，归为字体运行时专项改造

## 背景

- 浏览器能力缺口复核把 font-family 列为 B 类"高性价比"候选，理由是底层 `FontType` 已有字体能力、开放代价小。
- 实际深入源码核实后，该前提不成立：`FontType` 只有 NORMAL/BOLD 字重，没有字体族概念。

## 核实结论：底层不支持按字体族名选字体

- 字体选择唯一入口 `FontMatcher.matchFontIndex` 只按 `codepoint + fontType(NORMAL/BOLD)` 在全局 `FontConfig.fontSort` 顺序里选第一个能显示该字符的字体，无字体族维度
- 字形表运行时 `GlyphRuntimeTables` 只按 NORMAL/BOLD 两套分桶，不按族名分桶
- `FontCatalog` 只是按 index 取的字体列表，不提供按 `Font.getFamily()` 查找
- 字体优先级由全局 `FontConfig.fontSort` 决定，与单个元素 CSS 无关

## 候选方案

1. 按 font-weight/font-style 样板只打通"声明 → 级联 → computed → 绘制命令透传 → 写入 TextStyle.fontFamily 字段"，文档标注"仅记录不生效"。
2. 跳过，归为独立的字体运行时改造专项。
3. 现在就做完整字体运行时改造，让 font-family 真生效。

## 最终选择

- 选择方案 2：暂不接通 font-family，归为后续独立的字体运行时改造专项。

## 选择原因

- 方案 1 会产出"假能力"：作者写 `font-family` 后级联/属性都正确，但屏幕字体不变，正是项目历次审查批评的"文档比实现更乐观"，与项目诚实边界原则冲突。
- 方案 3 需改造 `FontMatcher`（接受族名并做候选过滤）、`FontCatalog`（按 `Font.getFamily()` 建索引）、字形表运行时（增加字体族分桶维度），并把 `TextStyle.fontFamily` 透传到 `measureAwtWidth` 与 `drawInternal` 的字形查找，工作量与架构风险远超"照样板填缺口"的粒度，应单独立项评估。

## 影响范围与后续注意事项

- 本轮 B 类缺口集中填补不再包含 font-family。
- 后续若要真正支持 font-family，必须按方案 3 改造字体运行时，不要回退到方案 1 的"只记录不生效"假能力。
- 远程 CSS 中已出现的 `font-family:sans-serif` 声明（如 `RemoteConfigDocumentPages`）当前会被解析器静默忽略，这一现状在字体运行时改造前保持不变。
