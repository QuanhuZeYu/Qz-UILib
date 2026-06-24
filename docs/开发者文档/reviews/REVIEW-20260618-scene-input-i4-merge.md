# REVIEW-20260618 Scene 输入层 I1-I4 系统性收口审查（合并复盘）

- 审查对象：`ui.scene.input` 新输入层整条 I1-I4（已 `--no-ff` 合回 `4.0`，merge `c6d152d5`）
- 审查者：oracle（ora-i4 session，掌握 I4 全部接口契约与拍板理由）
- 审查方式：核验合并态真实源码，对照 `NORTH_STAR.md` 第 5 节不变量（I1-I11）与第 9 节反模式
- 总体结论：**有条件 PASS，无 P0 阻断**

## 1. 整体结论

整条新输入层 I1-I4 架构对齐 NORTH_STAR 入口半环哲学（平台原始事件 → `PlatformInputSource` 契约线 → 改 signal → `f(state)` 重算），与渲染层 Display List 出口契约线架构对称。I7/I9/I10/I11 核心不变量守住，reactive 地基去重改动经全文核验安全无隐藏回归。合并态可维持。

唯一需用户知晓的**新发现**：合并态比分阶段审过的版本多了「Bug1 隐式聚焦块」和「失焦语义反转」两处 Router 改动，本轮补审——逻辑正确，但有一处与 I4d capture 的语义耦合需登记（N1）。

## 2. 观察点裁定表（10 组逐条）

| # | 观察点 | 裁定 | 理由 |
|---|------|------|------|
| 1 | I4a-O1：Tab KEY_UP 派新焦点 + 无焦点首 Tab KEY_DOWN 丢弃 | 已消解（真机侧）+ 维持登记（沙箱侧） | keyTyped 旁路只产 PRESSED 无 RELEASED → 真机 KEY_UP 不产生，"派给新焦点"真机不触发；沙箱语义正确 |
| 2 | I4a-O3：focusNext/Prev public 依赖包级 setRoot 先调 | 维持登记（YAGNI） | 唯一调用方是 route 内部（每帧先 setRoot），无悬空风险；Phase5 若暴露 API 再收口 |
| 3 | I4c-O1：LwjglCursorBackend 委托旧栈单例，同帧共存互相覆盖 | 维持登记 → Phase5 回填（P1） | 当前 demo 独占屏不共存无 bug；真实跨栈耦合债，Phase5 退役旧栈必须内联反射桥 |
| 4 | I4c-O2：instanceof 锁死 cursor 只对 LwjglInputSource | 维持登记（YAGNI） | 当前唯一生产 source；新增第二种时改构造器显式传 backend |
| 5 | I4c-O3/O5：MC keyTyped 单 char + KEY_UP 不经旁路 | 部分消解 | §11 Bug2 修复已用 onTextEvent 接完整 String，IME 整串提交在 lwjgl3ify 环境已解决；降级 char 路径与 KEY_UP 不产生维持登记 |
| 6 | I4d-O2：失焦 CANCEL 依赖宿主持续调 drainFrame | 维持登记（真机已验） | §12 真机失焦复验通过，实测宿主失焦后 drainFrame 仍被调用；风险已证伪 |
| 7 | I4d-O3：同帧 UP+失焦时 CANCEL 不投递 | 维持登记（语义合理） | UP 已正常收口交互，无需再 CANCEL |
| 8 | I4d-O4：capturedNode 无 Owner 生命周期绑定 | 维持登记（P2） | 正常闭环无泄漏；组件中途卸载悬挂引用与 pressedNode 同类既有风险，真机极难触发，见 N2 |
| 9 | §11 Bug2 残留三项 | 拆分裁定 | ①remove null → 维持登记（unregister 已 null-safe，addWeak 优先兜底）；②正则误伤 Javadoc → 转修复 P2（守护正则豁免注释行根治）；③external+capture 期 DOWN 仍隐式聚焦 → 并入 N1 |
| 10 | 真机 Bug 修复三项（preferredHeight 动态陈旧 / 负值无防御 / markSelfPaint 缺 self 去重） | 维持登记（YAGNI） | demo 静态设一次不触发；max 语义下负值退化无害；既有设计不破 I7 |

## 3. 新发现（合并后整体视角才暴露）

### N1（P1）— 隐式聚焦块与 capture/CANCEL 的三状态机顺序耦合，存一处语义盲区

合并态 Router 在 CANCEL 块之后、effectiveTarget 判定之前新增了 Bug1 隐式聚焦块：POINTER_DOWN 时按 hitTarget 分流 requestFocus/clearFocus。

**复现条件**：handler 在 DOWN 内调 `requestPointerCapture()` 建立显式捕获后，下一次 POINTER_DOWN（capture 仍持有、尚未 UP 释放）落在另一个非 focusable 区域或树外。
- 隐式聚焦块无条件对所有 POINTER_DOWN 执行，判定只看 hitTarget（与 capturedNode/pressedNode 正交）。
- capture 期间的 DOWN 触发 `clearFocus()`，而该 DOWN 的事件投递却因 capturedNode 优先被强制投给捕获节点。
- 结果：焦点被清空，但事件投给捕获节点——焦点状态机与指针状态机对同一个 DOWN 做了语义相反的归属。

**影响面**：仅在"显式 capture 持有期 + 期间又来 DOWN"才触发。当前 demo 无 `requestPointerCapture` 调用方（capture 仅沙箱测试覆盖），真机零触发。是 §11 Bug2 残留③的更完整刻画——不仅 external 模式，是所有 capture 期 DOWN。

**不变量对照**：不破 I1/I7/I9/I10/I11（仍只走 signal、零标脏、零平台 import），是语义一致性问题。

**裁定与最小修复方向**：capture 持有期应抑制隐式聚焦——隐式聚焦块加守卫 `capturedNode == null`。**当前无生产 capture 调用方，登记为「拖拽/capture 功能生产化前置必修项」，不阻断本次合并。**

### N2（P2）— capturedNode 悬挂引用的轻量收口点

FocusManager 的 focusables 已走 `Owner.onCleanup` 回收，而 capturedNode 没有。若未来 capture 进入生产，建议在节点 onCleanup 时一并 `if (capturedNode == node) releasePointerCapture()`。当前 P2 备忘。

### 其余：无跨阶段组合缺陷

重点核验 enter/leave + CANCEL + 隐式聚焦三者的顺序耦合（呼应 I3 hover continue 吞状态教训）：hover 块（MOVE-only）→ CANCEL 块（continue）→ 隐式聚焦块（DOWN-only）→ effectiveTarget 判定，四块的 type 守卫互斥或正交，continue 时序正确。树外 DOWN 的 clearFocus
在隐式聚焦块先执行再走到 `hitTarget==null→continue`，§12 已专门修了这个时序陷阱，正确。无 I3 式的"状态更新被 continue 吞掉"。

## 4. reactive 地基改动专项判定：安全，无隐藏回归

§12 把去重从 `Signal.set` 移到 `ReactiveScheduler.flush` 阶段1。逐条核验 Signal.java + ReactiveScheduler.java 全文：

- **去重时机正确**：flush 阶段1 对每个 signal 比 `peek()`（帧初值）vs 合并终值（pendingWrites LinkedHashMap 末值），仅净变化才 applyAndNotify + 入日志。根治了"同帧 set 到中间值再 set 回帧初值"被旧逻辑误判丢弃的 latent bug，根因分析准确、修复对症。
- **I9 单点 flush 不破**：pendingWrites 仍是唯一队列，flush 仍是唯一 apply 点。LinkedHashMap 同 key 覆盖末值 + 迭代序=首次插入序，apply 顺序稳定。
- **事务日志正确**：只记净变化的源 signal，Computed 派生值不入日志（可重放重算）。日志关闭时零额外开销。
- **undo/redo 不受影响**：applyAndRerun 直接用 Entry 的 before/after，绕过队列与去重，去重移位完全不碰这条路径。
- **阶段2 不动点保留**：runEffectsToFixpoint 逻辑未动，MAX_FLUSH_PASSES 防环未动。
- **可重入保护未动**：flushing 标志 + undo/redo 的 flushing 守卫完整。

**附带收益证实**：此改动顺带根治了 scene 层"同帧 hover A→B→A 中间节点残留 true"瑕疵——旧根因正是 Signal.set 拿已 flush 旧值去重，现在去重在 flush 阶段比帧初值，B 的 false→true→false 终值=帧初值被正确吸收为无净变化。（对应 Router 过时注释已在本轮收尾更新。）

## 5. 下一步建议

**必须做（P1，不阻断当前合并）**：
- N1 capture 期隐式聚焦守卫：拖拽/capture 功能生产化前必修（加 `capturedNode == null` 守卫）。已登记进度文件。
- I4c-O1 Phase5 回填：退役旧栈时内联 LwjglCursorBackend 反射桥。Phase5 checklist 必做。

**可搁置（P2，登记不动）**：
- §11 残留②守护正则豁免注释行（本轮收尾已根治）。
- Router 过时注释更新（本轮收尾已处理）。
- N2 capturedNode onCleanup 收口（随 capture 生产化一起做）。

**等 Phase5 自然收口**：I4c-O1 跨栈单例、旧 UiInputService/UiInputRouter 退役（I5 范畴）。

**纯 YAGNI 不做**：观察点 #2/#4/#7/#10、真机修复三项、KEY_UP poll 差分。
