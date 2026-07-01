# 决策：坐标系对齐 Flutter 三件套（raw / host / local + 框架自动注入 local）

## 背景

BUG1「滚动条拖拽失效」两次回归后，发现坐标系错位是**系统性问题**，不止 SceneScrollbar：

- `SceneScrollbar`：column DOWN handler 判定 thumb 视觉区时，`ev.getPointerY()`（画布逻辑，含 rootAbs）与 `SceneGeometry.absoluteBox`（host 局部，不含 rootAbs）混比，差一个 `rootAbsY`，真机 GUI 居中 margin≠0 时拖不动
- `SceneSlider`、`SceneTextInput`、`SceneTextAreaPrimitive`：同款错位——handler 内 `pointerX/Y` 与 `absoluteBox(node, 0, 0)` 混比，rootAbs≠0 时点击定位 / 拖动提交值偏移
- 根因不止在控件层：`SceneEvent` 注释误导（`pointerX/Y` 标"不叠加 rootAbs"但实际是屏幕绝对、含 rootAbs），导致多个控件作者按注释写混比代码

单测默认 `rootAbs=0,0`，画布逻辑 == host 局部，错位不暴露（假绿）；真机 GUI 居中才暴露。

## 行业调研

Librarian 调研 Flutter / Compose / Android View / Web DOM / SwiftUI 五大框架的指针坐标语义：

| 框架 | handler 默认坐标 | 框架是否自动注入 local |
|---|---|---|
| Flutter | `localPosition`（节点局部） | 是，`RenderObject.handleEvent` 自动注入 |
| Compose | `PointerInputChange.position`（节点局部） | 是，pointerInput 自动注入 |
| Android View | `event.getX/Y()`（view 局部） | 是，View 自动 dispatch 时换算 |
| Web DOM | `event.offsetX/Y`（target 局部）+ `clientX/Y`（viewport）+ `pageX/Y`（文档）+ `screenX/Y`（屏幕）多层 | 是，浏览器自动提供多层 |
| SwiftUI | `DragGesture.Value.location`（节点局部） | 是，gesture 自动注入 |

**共识**：所有主流框架的 handler 默认拿到的都是**节点局部坐标**，框架自动换算注入，业务 handler 几乎不需要手动减 rootAbs / 减父偏移。

## 候选方案

### 方案 A：维持现状 + 单测补 rootAbs≠0
- 不动 SceneEvent，控件层各自记得用 `hostPointerX/Y`
- 缺点：注释仍误导、靠人肉纪律、新控件容易再踩

### 方案 B：对齐 Flutter 三件套（raw / host / local + 框架自动注入 local）
- SceneEvent 显式三层：`pointerX/Y`（raw 屏幕绝对，含 rootAbs）→ `hostPointerX/Y`（host 局部，不含 rootAbs）→ `localPointerX/Y`（节点局部，框架自动注入，handler 默认用这个）
- I12 契约：handler 内坐标比对必须同系；raw 与 `absoluteBox(0,0)` 禁止混比
- 控件层 handler 默认用 `localPointerX/Y`，与节点几何同系，rootAbs 自然消去
- 旧 API（`pointerX/Y`）保留但改名/废弃走第 3 轮

### 方案 C：只加 hostPointer，不做 local 自动注入
- 第 1 轮实际已做到这一步（commit `a7959d41` + `0da919b6`）
- 缺点：控件层仍需手动 `hostPointer - absoluteBox(node,0,0)`，未对齐 Flutter「handler 默认 local」范式

## 最终选择

**方案 B（对齐 Flutter 三件套）**，分 3 轮落地。

### 第 1 轮（已完成，commit `0da919b6`）
- SceneEvent 主树新增 `hostPointerX/Y` 字段 + I12 契约注释（raw/host/local 三层定义）
- 3 控件同系修正：
  - `SceneTextInput`：点击定位改 `hostPointerX` 与 `absoluteBox` 同系
  - `SceneSlider`：拖动提交值改 `hostPointerX` 与 `absoluteBox` 同系
  - `SceneTextAreaPrimitive`：点击行号 + 行内 X 改 `hostPointerX/Y` 与 `absoluteBox` 同系
- SceneEvent 注释修正：`pointerX/Y` 明确标"屏幕绝对、含 rootAbs"，删除"不叠加 rootAbs"误导
- 受影响文件：`SceneEvent` / `SceneInputRouter` / `SceneTextInput` / `SceneSlider` / `SceneTextAreaPrimitive` / `NORTH_STAR.md`（I12 不变量）

### 第 2+3 轮（已完成，2026-06-30，commit 待提交）
- **A1 localPointer 移 ctx + bubble 每级重算**：`SceneEvent` 删除 `localPointerX/Y` 字段，local 移到 `SceneEventContext.getLocalPointerX/Y()` = `rawPointer - absoluteBox(currentNode, treeAbs)`。`currentNode` 每级 bubble 由 Router 更新，local 每级重算。handler 默认消费 `ctx.getLocalPointerX/Y`。
- **B2 hitTestable 改造**：
  - `SceneSliderPrimitive`：root hitTestable=false，track hitTestable=true；interactionState/focusable/on 全挂 track。pressed 写 track → MOVE 守卫的 pressed signal 正确写入。
  - `SceneTextAreaPrimitive`：content hitTestable=true；interactionState/focusable/on 全挂 content。wrapper `SceneTextArea` 的 interactionState/cursor 同步改到 content。
  - wrapper 同步：Slider cursor bind 改到 track；TextArea interactionState/cursor 改到 content。
- **C1 改名 getRawPointerX/Y**：`SceneEvent.pointerX/Y` → `rawPointerX/Y`，getter `getPointerX/Y()` → `getRawPointerX/Y()`。
- **D2 废弃 host 层**：删除 `SceneEvent.hostPointerX/Y` 字段 + getter，构造器签名去掉 hostPointer 参数。I12 三层→两层。
- **I12 三层→两层**：NORTH_STAR I12 不变量 + §4.5 坐标系契约段同步更新为两层（raw + local via ctx）。**直接修订 I12 信条**（信条/不变量本身改动，经用户确认，属 NORTH_STAR §修订纪律「信条本身的改动属重大架构变更，必须经用户确认」），非偏离登记——`<deviation-log>` 不留 revision 条目。
- **overlay 修复**：主 dispatch / CLICK 合成 / CANCEL 三处 ctx 注入 treeAbs（overlay 命中时=overlay anchor，主树=rootAbs），local 自动正确。删除原 TODO。
- **4 控件改造**：Slider/TextArea/Scrollbar/TextInput handler 改用 `ctx.getLocalPointerX/Y`，删除手动 `hostPointer - absoluteBox` 换算。
- **测试同步**：SceneInputRouterTest I12 段改用 ctx.getLocalPointer + evt.getRawPointer；Slider/TextArea 测试 requestFocus 改到 track/content、hitTestable 断言反转；注释更新。
- 受影响文件：`SceneEvent` / `SceneEventContext` / `SceneInputRouter` / `SceneSliderPrimitive` / `SceneSlider` / `SceneTextAreaPrimitive` / `SceneTextArea` / `SceneScrollbar` / `SceneTextInputPrimitive` / `NORTH_STAR.md` + 6 测试文件。

### 第 3 轮（已合并入第 2+3 轮）
- 旧 API 改名 / 废弃：`pointerX/Y` → `rawPointerX/Y` 已在第 2+3 轮完成；`hostPointerX/Y` 直接删除（无废弃周期，因第 1 轮刚引入）。

## 选择原因（第 2+3 轮升级为两层）

1. **对齐行业共识**：五大框架 handler 默认 local，本方案让 scene 栈与主流对齐，降低新控件作者踩坑面
2. **根治系统性错位**：不止 scrollbar，slider/textInput/textArea 同款错位，方案 B 一次性根治
3. **I12 契约硬约束**：把"raw 与 absoluteBox(0,0) 禁止混比"写进不变量，靠契约不靠人肉
4. **注释误导是根因之一**：方案 B 修正注释 + 三层显式命名，从源头堵住"按注释写错代码"
5. **分轮降风险**：第 1 轮只做同系修正（不动结构），第 2+3 轮做自动注入 + 改名 + 废弃 host（动结构 + 动 API），每轮可独立验证
6. **两层优于三层**：local 移到 ctx 每级重算后，host 是冗余中间层；废弃 host 减少 API 面、降低新控件作者选择困难（raw vs host vs local 三选一 → raw vs local 两选一）。B2 hitTestable 改造让 pressed/hover 写入正确节点，配合 local 每级重算实现 Flutter 式"handler 默认 local"范式。

## 影响范围

- `SceneEvent`：新增 `hostPointerX/Y` 字段（第 1 轮），第 2 轮加 `localPointerX/Y`，第 3 轮 `pointerX/Y` 改名
- `SceneInputRouter`：注入 `hostPointerX/Y`（第 1 轮），第 2 轮注入 `localPointerX/Y`
- `SceneTextInput` / `SceneSlider` / `SceneTextAreaPrimitive`：handler 改用 `hostPointerX/Y`（第 1 轮），第 2 轮改用 `localPointerX/Y`
- `NORTH_STAR.md`：新增 I12 不变量「坐标系三层 + handler 默认 local + raw 与 absoluteBox(0,0) 禁止混比」
- 单测：必须显式覆盖 `rootAbs≠0` 路径（I12 衍生约束）

## 后续注意事项

- **第 1 轮后**：控件层仍需手动 `hostPointer - absoluteBox(node,0,0)`，新控件作者必须读 I12 契约
- **第 2 轮前**：overlay localPointer 需先定义 overlay root 坐标系边界（overlay root 是否含 rootAbs）
- **第 3 轮改名**：需走废弃周期，旧控件迁移一并完成
- **单测纪律**：凡涉及指针坐标与节点几何比对的 handler，单测必须显式传非零 `rootAbsX/Y`，不能只测 `rootAbs=0,0` 默认路径（否则假绿）

## 关联

- 上游错误记录：`docs/开发者文档/errors/ERROR-20260630-scrollbar-drag-hit-test-transform.md`（坐标系错位系统性教训）
- 第 1 轮 commit：`0da919b6`（主树 + I12 + 3 控件 + 注释修正）
- 编排：研究（2 Oracle + Librarian 并行复核）→ 实施（A 规划 → B 实施 → C 复审 → D 收尾 → 待终审）
- 编排模式定义：`docs/开发者文档/编排模式/SUBAGENT-ORCHESTRATION.md`
