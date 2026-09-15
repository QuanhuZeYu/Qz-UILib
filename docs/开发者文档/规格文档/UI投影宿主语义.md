# 统一 UI 投放、宿主与输入语义

> 状态：**现行规范**（scene 输入层语义规范母本，现行规则据此实现）。「Input Scope」与「State 与 Intent」两节是**现行规范，不随实现删除**——`SceneFramePipeline` / `SceneInputRouter` / `LwjglInputSource` 及 `AGENTS.md` 据此约束。职责名不等于已冻结 Java API；「实施」一节的 U0 实现已删除，U1/U2 未单独立项，其历史与复活条件见该节。

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

- **U0（已删除，仅留语义与教训）**：曾经的 internal fake composition 用于证明「同一 factory 的两个 occurrence 共享 state、隔离 scene/focus，input 只 drain/dispatch 一次」；该实现（原 `ui.scene.input.SceneProjectionComposition`）于 2026-08 随同目录 `投放职责聚合方案.md` 的 A3 批次整类删除——它与 `SceneFramePipeline` 构成双重唯一 drain 者（`drainFrame()` 是一次性消费，两仲裁者并存即互相偷事件），且无渲染半边、生产零消费者。**复活触发条件：出现第二个真实的多输入投放面（H4）；复活前必须先解决 drain 独占冲突。** 语义条款保留于本文，作为未来实现的规范。
- **U1（未单独立项）**：screen/overlay 接入同一合同，overlay 默认 passive；scene overlay 现行合同（`SceneOverlayHost`）已按本语义实现。
- **U2（未执行）**：现有 HUD facade 迁移后删除 HUD owner/priority/runtime 特例；HUD facade 现行，本项作为长期方向保留。

上述三项都不引入正式 public `UiContent`/`UiProjection` API，不处理 scale/rotate 命中，也不改 GuiContainer。
