# Phase 2 剩余浏览器语义工程化修复

## 修复结论

- 本批集中收口 `REVIEW-20260601-browser-semantics-phase2-audit.md` 中剩余的已实现能力语义偏差，避免继续按零散小补丁推进。
- 已修复：空块自身 margin collapse、父子顶部 margin collapse 递归、row flex 多行 `align-content`、flex 交叉轴 auto margin 禁用 stretch、absolute 水平 auto margin 居中、table auto 列宽内容测量、textInput capture → target → bubble 分发、transform fixed containing block 下 fixed 后代参与滚动范围计算。
- 复核确认：`position:sticky` 创建 stacking context 是现代 CSS 语义，原审查 2.4 属规范口径误报，不改实现。

## 实现范围

- 样式层新增 `UiAlignContent`、`UiStyleDeclaration.setAlignContent(...)`、computed style 级联和远程 CSS `align-content` 解析。
- `FlexLayoutHelper` 将 row wrap 布局改为行计划模型，统一处理 `align-content`、多行 stretch、主轴/交叉轴 auto margin。
- `DocumentLayoutEngine` 扩展 block margin collapse：空块 top/bottom 自身折叠，并递归合并首个可折叠子块的 top margin。
- `PositionedLayoutHelper` 支持 absolute 元素在 `left/right/width` 同时确定且左右 `margin:auto` 时平分剩余空间。
- `TableLayoutHelper` 在 auto 列宽分配前纳入单元格 intrinsic outer width，避免内容列被简单均分压窄。
- `DocumentKeyboardEventDispatcher` 为 textInput 补齐 capture 阶段；`ElementNode` 新增 `setCaptureTextInputHandler` / `getCaptureTextInputHandler`。
- `DocumentScrollMetricsCalculator` 将 transform fixed containing block 状态纳入内容边界递归，viewport fixed 仍不扩大普通滚动范围。

## 验证

- `./gradlew.bat --offline --no-configuration-cache "-Pgtnh.settings.blowdryerTag=" test --tests "club.heiqi.uilib.ui.layout.FlexLayoutHelperBoundaryTest" --tests "club.heiqi.uilib.ui.layout.PositionedLayoutHelperBoundaryTest" --tests "club.heiqi.uilib.ui.layout.TableLayoutHelperBoundaryTest" --tests "club.heiqi.uilib.ui.layout.DocumentLayoutEngineTest" --tests "club.heiqi.uilib.ui.layout.DocumentScrollStateTest" --tests "club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTest"`
- `./gradlew.bat --offline --no-configuration-cache "-Pgtnh.settings.blowdryerTag=" compileJava`
- `git diff --check`

## 后续注意

- `inline-block baseline` 未在本批强行落地。当前行内布局会先落真实 child box，再由 `InlineLayoutContext` 只记录占位高度；要完整修复 baseline，需要改为延迟生成 inline-block box 或支持布局盒重定位，影响面明显大于本批其他项。
- B 类浏览器能力缺口仍按 `REVIEW-20260601-capability-gap-recheck.md` 的分级推进，本批不混入属性选择器、兄弟组合器等新能力扩展。
