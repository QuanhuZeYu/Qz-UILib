# 决策：HTML-like 脏子树布局缓存与可平移复用

## 背景

HTML-like 文档此前主要依赖全局 `layoutVersion` / `paintVersion`。任一 layout-affecting DOM、文本或样式变更都会让 `HtmlLikeDocumentWidget` 从根节点重新执行静态布局，长文档或多块内容页面容易因局部变更触发整棵布局树重建。

本轮长期目标是建设工程化脏子树优化方案，而不是只靠页面作者节流、拆页或专用控件规避。

## 候选方案

1. 直接对所有布局模式做完整增量布局：收益最大，但 flex、table、inline formatting、absolute/fixed containing block、margin collapse 与动画运行态耦合较多，首轮风险高。
2. 只做 TextNode 级换行缓存：能降低文本测量成本，但仍无法避免父子树布局遍历，也不能作为通用脏子树基础设施。
3. 先建立节点级布局脏版本与上一轮布局盒复用骨架，再保守覆盖静态 block-flow 子树：收益可验证，改动边界较小，后续可按同一缓存协议扩展。

## 最终选择

采用方案 3：新增 `DocumentNode` 的节点自身与子树布局变更版本，`DocumentLayoutBox` 记录布局输入参数、节点版本与文本测量 epoch，`DocumentLayoutEngine` 在静态布局 pass 中以上一轮根布局盒为候选缓存，先复用满足条件的未脏 block 子树。

在可平移复用补测后，进一步放宽 `containingLeft` / `flowTop` 完全一致限制：当节点版本、子树版本、文本测量 epoch、containing 宽高和 forced size 保持一致时，允许静态 block 子树在普通流位置变化后整体平移复用。

在 flex 主轴/交叉轴分配、flex item 尺寸、auto margin 与脱流定位边界补测后，进一步将静态 `display:flex` 子树纳入同一复用协议。flex 复用不新增单独缓存模型，仍要求版本、文本测量 epoch、containing 宽高和 forced size 不变，且仍排除含 absolute/fixed 盒的子树与 transform fixed containing block 子树。

## 选择原因

- 可以在不改变现有 DOM/CSS/绘制语义的前提下，让局部文本或 DOM 变更避免重排未脏兄弟 block 子树。
- 节点版本是后续扩展 flex/table/inline 缓存的基础设施，避免把优化写死在某个页面或测试用例里。
- 首轮只在静态布局启用；layout-affecting 动画运行态仍禁用子树复用，避免时间相关布局值被误缓存。
- 元素样式、class、属性与文档级样式表/变量变更可能影响后代继承或选择器匹配，因此按子树或全局脏标记处理，优先保证正确性。

## 影响范围

- `DocumentNode` 记录 `layoutMutationVersion` 与 `subtreeLayoutMutationVersion`，结构变更会标记新父级、旧父级和移动子树。
- `ElementNode` 的 layout-affecting 属性、class 和样式变更改为标记元素整棵子树。
- `UiDocument` 的样式表、样式变量和 top-layer 变化作为全局布局脏树处理。
- `DocumentLayoutBox` 保存布局缓存元数据，并提供递归平移能力与布局复用诊断计数。
- `DocumentLayoutBox` 平移时同步更新布局输入坐标元数据，避免连续平移复用使用旧输入坐标计算差值。
- `HtmlLikeDocumentWidget` 只在静态布局重建时把上一轮 `cachedLayoutBox` 传给布局引擎；运行态 layout 动画不复用。

## 后续注意事项

- 当前复用条件仍保守：只覆盖静态 `display:block` / `display:flex` / `display:none` 子树，不覆盖 table、inline-block、inline formatting、含 absolute/fixed 盒的子树和 transform fixed containing block 子树。
- `containingLeft` / `flowTop` 已允许变化并通过整体平移复用；该能力已覆盖 margin collapse、auto margin、relative/sticky 偏移、absolute/fixed containing block、transform fixed containing block、flex 主轴/交叉轴分配、flex item 尺寸与 flex auto margin 边界测试。
- 后续扩展 table/inline 时必须分别补充列宽、行高、单元格内容变化、换行、baseline、inline-block 落位、文本测量 epoch 和脱流定位回归测试，不能只依赖通用版本判断。
- 文档级样式规则支持后代/子代选择器，任何可能影响匹配关系的结构或属性变更都必须保守标记相关子树，不能只标记单个节点。
