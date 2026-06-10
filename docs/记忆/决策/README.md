# 决策记录

本目录用于记录关键技术取舍、架构边界决定和重要实现约束。

## 何时新增决策记录

- 多种方案都可行，但最终必须固定一种
- 某个选择会长期影响目录结构、接口边界或依赖策略
- 未来很可能有人问“为什么当时这么做”
- 某项约束不是错误，也不是 review，但必须被后续协作者知道

## 文件命名

- `DECISION-YYYYMMDD-主题.md`

## 建议模板

```md
# 决策：主题

## 背景

## 候选方案

## 最终选择

## 选择原因

## 影响范围

## 后续注意事项
```

## 索引

- [`DECISION-20260531-记忆框架.md`](DECISION-20260531-记忆框架.md) - 采用分层 AI 协作记忆框架，拆分规则层、当前态层、长期事实层和决策层
- [`DECISION-20260531-event-return-value-vs-prevent-default.md`](DECISION-20260531-event-return-value-vs-prevent-default.md) - 事件 handler 返回值只停止传播，取消默认行为统一依赖 `preventDefault()`
- [`DECISION-20260601-visual-traversal-shared-semantics.md`](DECISION-20260601-visual-traversal-shared-semantics.md) - 新增共享视觉遍历层 `DocumentVisualTraversal`，统一 paint / hit-test / scroll 的 `fixed/sticky`、clip 链与 stacking phase 语义
- [`DECISION-20260601-font-family-deferred.md`](DECISION-20260601-font-family-deferred.md) - font-family 暂不接通，底层字体引擎无字体族维度，归为后续字体运行时改造专项，避免产出"只记录不生效"的假能力
- [`DECISION-20260601-textarea-soft-wrap-deferred.md`](DECISION-20260601-textarea-soft-wrap-deferred.md) - 历史决策：textarea 软换行曾暂缓并要求先重构行模型；现已被逻辑行 + 视觉行两级模型实现取代
- [`DECISION-20260601-textarea-soft-wrap-two-level-lines.md`](DECISION-20260601-textarea-soft-wrap-two-level-lines.md) - textarea 软换行采用逻辑行与视觉行两级模型，统一显示、caret、选区、点击、上下移动和滚动几何
- [`DECISION-20260605-test-visual-matrix-collaborators.md`](DECISION-20260605-test-visual-matrix-collaborators.md) - `/qzuilib test` 视觉矩阵拆成 registry、分组视觉 builder、语义 checker 和结果 state，控制器只保留生命周期与导航
- [`DECISION-20260606-html-text-paint-clipping.md`](DECISION-20260606-html-text-paint-clipping.md) - HTML-like 长文本优先在绘制阶段按 overflow clip 保守裁剪，不截断 DOM 语义，跨帧布局缓存后续再做
- [`DECISION-20260606-dirty-subtree-layout-cache.md`](DECISION-20260606-dirty-subtree-layout-cache.md) - HTML-like 脏子树布局缓存先建立节点级脏版本与静态 block-flow 子树复用骨架，后续再扩展 flex/table/inline
- [`DECISION-20260607-codegraph-memory-mode.md`](DECISION-20260607-codegraph-memory-mode.md) - 将 CodeGraph MCP 作为动态代码关系查询层，仓库记忆继续只保存稳定事实、决策和交接
- [`DECISION-20260608-remote-html-session-ttl.md`](DECISION-20260608-remote-html-session-ttl.md) - 远程 HTML session TTL 同时覆盖 HTML 拉取与交互提交，过期必须通知客户端错误或关闭对应 HUD
- [`DECISION-20260609-remote-ui-runtime-lease-protocol.md`](DECISION-20260609-remote-ui-runtime-lease-protocol.md) - 后续远程 UI 重构采用内部 Runtime + 显式 Lease 协议，区分 session、surface、revision、asset 与 closeScope，保持 NetService 通用边界
- [`DECISION-20260610-character-font-rules.md`](DECISION-20260610-character-font-rules.md) - 字符级字体覆盖采用 `FontMatcher` 规则优先表，不接入 CSS `font-family` 样式维度
- [`DECISION-20260610-select-large-list-virtualization.md`](DECISION-20260610-select-large-list-virtualization.md) - `DocumentSelectControl` 大列表采用控件级虚拟化，保留完整数据语义但只渲染可视窗口 DOM
