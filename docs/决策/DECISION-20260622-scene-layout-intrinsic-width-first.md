# 决策：scene 排版地基优先补内容宽

## 背景

scene 新栈已具备 ROW/COLUMN、padding、gap、固定尺寸、文本叶 shrink、纵向 scroll/clip 等基础布局能力，但真实配置页所需的 CSS 排版能力仍不足。用户明确指出颜色和外观属性可先直接写，token/theme 只是锦上添花，当前更重要的是排版引擎。

批 2 `SceneBreadcrumb` 曾因容器无 shrink-to-fit 宽度，只能用 `label.length() * APPROX_CHAR_WIDTH` 估算段宽，已在 `NORTH_STAR.md` 偏离登记为引擎能力债。

## 候选方案

- 先做 token/theme：集中颜色、间距、字号等外观常量。
- 先做完整 CSS selector/cascade/stylesheet：移植旧 HTML-like 样式系统。
- 先补 scene layout intrinsic width：最小实现容器内容驱动宽度，再逐步补 flex-grow、align-self、min/max。

## 最终选择

优先补 scene layout 排版地基，不把 token/theme 作为当前主线，不移植旧栈 selector/cascade/stylesheet。

第一步 P0 已实现 `SceneNode.WidthSizing { FILL, SHRINK }`：默认 `FILL` 保持零回归；`SHRINK` 容器在子节点已布局后按内容宽回收，ROW 为子宽之和加 gap 与水平 padding，COLUMN 为子最大宽加水平 padding，并 clamp 到父级 available outerWidth。
`preferredWidth` 仍最高优先级。

第二步 P1-a 已复用 `fillParentHeight` 支持 COLUMN 中固定兄弟后唯一 fill 子吃剩余高度。实现只通过下传约束让子端 `computeHeight` 消费高度，不新增属性、不父侧改写子盒；多个 fill 子、固定兄弟高度不可先验、无高度约束均继续回退 shrink-to-fit。

## 选择原因

- 真实配置页首先需要 label/控件/分组的稳定排版能力，而不是全局颜色 token。
- 完整 CSS 级联与选择器会引入过早复杂度，并有退化为全树重排的风险。
- `WidthSizing.SHRINK` 是 opt-in 能力，默认 fill 行为不变，可用最小改动还清 Breadcrumb 字符宽估算债。
- 下传约束和约束变化判断阶段不读取子 cache，避免父宽依赖子宽导致循环依赖；只有子节点布局完成后才读取本帧子布局缓存回收宽度。
- P1-a 满足 Layout demo 与未来配置页常见的“固定标题/说明 + 滚动视口吃剩余高”需求，同时避免一次性引入完整 flex-grow 权重求解器。

## 影响范围

- `SceneNode` 新增容器宽度策略 API：`setWidthSizing(...)` / `getWidthSizing()`。
- `SceneLayoutEngine` 新增容器 shrink-to-fit 宽度回收逻辑。
- `SceneBreadcrumb` 删除 `APPROX_CHAR_WIDTH` 估算和 `setPreferredWidth(...)` 绕行，segBtn 改用 `WidthSizing.SHRINK`。
- `NORTH_STAR.md` 中 2026-06-21 容器无 shrink-to-fit 偏离已标记还清。
- `SceneLayoutEngine` 在 COLUMN 容器中对唯一 `fillParentHeight` 子下传剩余高度，`NORTH_STAR.md` 中 2026-06-20 COLUMN 主轴 fill 偏离已标记 P1-a 部分还清。

## 后续注意事项

- 下一批可先落独立 Layout demo；若继续 P1，再评估多 fill 子 flex-grow 权重分配与子项级 `align-self`，再评估 min/max 宽高。
- 暂不做 flex-wrap、position、百分比单位、margin、box-sizing、横向滚动和表格自动列宽。
- 新 layout 能力必须只在 layout 局部求解和缓存复用路径内落地，不新增向下递归标脏，不把 CSS 化演变成全量重排。
- token/theme 可在排版地基稳定后作为维护性优化单独启动。
