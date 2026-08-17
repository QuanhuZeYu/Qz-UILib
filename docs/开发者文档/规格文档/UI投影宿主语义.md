# 统一 UI 投放、宿主与输入语义

> 状态：语义规范母本（scene 输入层现行规则据此实现）；U0 已落地，U1/U2 未单独立项。职责名不等于已冻结 Java API。

## 中心模型

```text
business state -> content -> projection occurrence -> host adapter -> native host
                         -> semantic intent -> consumer -> new state
```

- content 不认识 screen、game overlay、Minecraft input 或 GL。
- 同一 content 多次投放时共享业务 state/factory，不共享 live `SceneNode`、runtime、layout、focus、capture、hover、cursor、overlay 或 animation。
- screen 与 game overlay 只提供 placement、viewport、render timing、native input 和 lifecycle。
- GuiContainer、native Slot、carried 与 tooltip 不属于该合同。

## Input Scope

- 一个 native input source 只有一个 composition owner；adapter 只登记 projection，不各自 drain。
- projection 作为完整 occurrence 按最终 visual order top-first 仲裁，不公开 main/native 双 lane。
- claim preview 必须只读；`PASS` 不写 focus、pressed、capture、hover、state 或 intent，只有 winner 实际 dispatch。
- DOWN 对 CLICK/gesture/focus/capture/outside-dismiss participant claim；裸 bounds `PASS`。
- MOVE 对显式 move/cursor participant或 leaf hover claim；SCROLL 是 event-only 仲裁，不改变 active gesture owner。
- DOWN winner 持有单一 pointer gesture 到 UP/CANCEL；第二 button 整段吞掉，不建立第二 owner。
- projection 失去 alive/visible/interactive 时清 hover/cursor/focus；active owner 另收一次 CANCEL，尾部吞到物理 UP/CANCEL。
- composition owner 是 native cursor 唯一写者；terminal/topology 变化后用末次 pointer 做 hover-only reconcile。
- 一个 input scope 只有一个 keyboard/text projection owner；跨 projection 或 host click-away 会释放旧 owner。

## State 与 Intent

- 外部异步结果经 immutable message 或 owner-thread handoff 发布 state，不直接写 scene。
- handler 只发布 semantic intent；consumer 决定同步/异步工作并发布 pending/success/error state。
- consumer/content 不调用 scheduler、flush、layout、paint 或 replay。

## 实施

- U0：internal fake composition，证明同一 factory 的两个 occurrence 共享 state、隔离 scene/focus，input 只 drain/dispatch 一次。——**已落地**：`ui.scene.input.SceneProjectionComposition` 按本语义实现，保持 package-private（不构成正式 public `UiContent`/`UiProjection` API）。
- U1：screen/overlay 接入同一合同，overlay 默认 passive。——未单独立项；scene overlay 现行合同（`SceneOverlayHost`）已按本语义实现。
- U2：现有 HUD facade 迁移后删除 HUD owner/priority/runtime 特例。——未执行，HUD facade 现行；本项作为长期方向保留。

U0 不增加正式 public `UiContent`/`UiProjection` API，不处理 scale/rotate 命中，也不改 GuiContainer。
