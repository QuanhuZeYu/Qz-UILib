# scene 新栈技术债权威清单

本文件是 scene 新栈技术债的**唯一权威源**。其他文档（交接记录、当前上下文、reviews 索引等）
只做指针引用「详见 `docs/诊断层/scene技术债.md`」，不再各自维护副本，避免口径漂移。

维护规则：债务状态变化时**只在本文件更新**；新债立项、旧债还清、口径修订都在此落地。
更新时优先覆盖原条目，不按日期追加历史状态。

---

## 一、布局算法债（scene layout engine）

### L1 嵌套 grow 子容器场景
- **现象**：容器 X 是父的 grow 子但非 fill 时，X 自身 `priorKnownInnerHeight` 返 `UNCONSTRAINED`，
  致 X 内 grow 子回退 shrink
- **状态**：**已还清**（2026-06-28）——
  `priorKnownInnerHeight` 闸门从 `isFillParentHeight && hasHeightConstraint` 放宽为
  `(isFillParentHeight || getFlexGrow>0 || getPercentHeight>0) && !isScrollable && hasHeightConstraint`，
  对齐 `computeHeight:266` 三合流口径，11 回归测试全绿（144 tests 0 failed）
- **依据**：（旧决策已删除，事实仍成立）
- **定性**：不对称判定缺陷（非有意边界），CSS §9.8 definite 语义：父分配 tight 高 → 子高度 definite

### L2 childConstraintsWouldChange O(n²)
- **现象**：逐子调 `buildChildConstraints` 叠加每子求解使脏判定为 O(n²)；
  freeze do-while 会进一步加重
- **状态**：与 B4 同一问题（`SceneLayoutEngine:430-441` 同段代码），**去重合并到 B4**
- **依据**：见 B4 条目（旧决策已删除，事实仍成立）

---

## 二、scene 非布局技术债（oracle 架构审核产出）

来源：oracle 架构审核产出（历史审查报告已清除，本节为遗留结论沉淀）

### B4 COLUMN fill O(n²) 约束判定（含 L2）
- **位置**：`SceneLayoutEngine:430-441`（行号已核实未漂移）
- **状态**：缓做（单容器子数小 + 干净帧短路，沿用接受口径）
- **依据**：（旧决策已删除，事实仍成立）
- **注**：L2 与本条描述同一段代码同一个问题，已合并到此条目

### B5 paint LEFT 无谓 measureWidth
- **位置**：原 `ScenePaintEngine:284`（已漂移，修复后逻辑在 `:299-319`）
- **状态**：**已还清**（commit `23bf3a94`，2026-06-28）——
  LEFT 分支直接返回 `paddingLeft` 不调 measureWidth，仅 CENTER/RIGHT 惰性量宽

### B6 transform+clip 叠加坐标错位
- **位置**：`ScenePaintEngine:129-150`（FBO 方案实现处，原 `:130` 注释语义已从
  "不支持 clipChildren" 变为 "FBO 方案实现"）
- **状态**：批 1 FBO 方案已落地；剩余债转为批 3 纹理脏标记跨帧复用
  + hit-test 对偶（SceneHitTester 对 transform 零感知）
- **依据**：（旧决策已删除，事实仍成立）
- **与偏离登记同步**：剩余债已在 NORTH_STAR.md 偏离登记 2 条
  （`2026-06-26` FBO 重栅格化 + `2026-06-26-hit-test` hit-test 零感知）登记，
  本条与之同步，不重复维护口径
- **真机状态**：FBO 有效避免裁切但性能压力大，批 3 需重新评估性能取舍

### B8 滚动后 hover 滞留
- **位置**：原 `SceneInputRouter:147-168`（已漂移，修复逻辑在 `:169-174` + `:407-417`）
- **状态**：**已还清**（commit `16dd6d56`，方案 Y'，2026-06-28）——
  Router 内部协议 `route → flush → layout → reconcileHoverAfterScroll`，
  flush+layout 后重做 hit-test 切 hover，不扩 I11 逃生舱②

### A1 effect 内 set 慢一帧残留
- **状态**：大部分被 ReactiveScheduler 不动点覆盖，残留语义边界待确认
- **依据**：oracle 架构审核历史产出（行号引用已随报告清除失效）
- **注**：诚实标注的开放项，非伪债；推进需核 `ReactiveScheduler.flush` 收敛终止条件

### A6 bind impact 参数
- **位置**：`SceneRuntime` bind 方法（javadoc `:179-181`，方法体 `:186-196`）
- **状态**：**已裁决保留为有意设计，不视为债**（commit `16dd6d56`，2026-06-28）——
  oracle 裁决保留参数（删参是 123 调用点零收益破坏性迁移），
  正名为「声明式失效意图标注，与 setter 自动打出的实际级别构成 I4 双轨审查锚点」，
  运行时不依赖此参数决定级别
- **依据**：commit `16dd6d56` message

### D1 SceneSlider 松手提交偶发丢失（缺陷 D）
- **现象**：`draggingValue` 走 queueWrite 帧末 flush，UP handler 同帧读回依赖
  "写后同帧可见"，契约错配致松手提交偶发丢失
- **状态**：**已还清**（commit c37b1b3c，2026-06-28）——修法甲 + 全面重构
  （`draggingValue` 降级纯渲染只写不读 + 事件坐标当场算提交值 + capture 托管
  + NaN/Infinity 防御）
- **依据**：（旧决策已删除，事实仍成立）
- **范式约束**：拖拽类控件"瞬态 signal 只写不读、业务值用事件坐标当场算"

### chrome 主题层
- **状态**：P2 大工程未立项

---

## 三、有意边界（非债，仅记录设计取舍）

### L3 percentHeight 在 ROW 容器下不生效
- **现象**：`percentHeight` 仅 COLUMN 主轴生效，ROW 下被当作普通 fill 子处理
- **性质**：有意设计边界，字段 Javadoc 已明确，**不视为债**
- **依据**：（旧决策已删除，事实仍成立）

---

## 四、Phase 5 旧栈退役

旧 HTML-like / `ui.dom` 栈已废弃，不再维护。退役清理作为方向性待办，但不在当前 UI 层工作主线内。

### 旧栈大文件拆分侦察登记（2026-07-03，待用户裁决）

**文档矛盾**：本节「已废弃」与 `项目结构.md:18`「旧栈仍是对外主路径」直接冲突，须用户裁决哪个是当前真相。

**源码事实**（explorer 侦察）：
- 旧栈 18 个 >900 行文件 + config 2 个，**零 `@Deprecated` 标注**，`DocumentLayoutEngine` 类头反写「后续阶段继续扩展」
- 6 月仍高频活跃（60+ 提交：脏子树布局缓存、ModernConfig 46→75fps、B6 FBO 等实质性改动）
- 业务入口深度依赖：`ClientProxy`/`config`/`UiHudRenderPipeline`/`UiDocumentScreens` → 旧栈
- **旧栈无硬约束文档**（新栈有 R1-R12/I1-I12 + 硬约束总目录守护，旧栈没有），盲拆风险高

**render 包 2 文件剔除旧栈清单**：`UiRenderContext`(1045) / `UiMainLayerSnapshotService`(1195) 是新旧两栈共用支撑（scene devtools 入口直接 import），**不可当旧栈拆**。

**当前处置**：不启动旧栈拆分。待用户裁决文档矛盾 + Phase 5 退役时间表后再议。详见 `docs/反馈层/交接.md`。

### 狭义移除精确盘点（2026-07-03，0 可删）

用户决策"狭义范围（仅 ui/document + ui/dom）+ 只删0外部依赖部分"。explorer 精确盘点结论：

- **74 个生产文件（document 8 + dom 66）+ 19 测试文件，可删 = 0**
- 37 个表面"零外部引用"A 候选，经反向依赖闭包分析**全部被 B 类种子传递依赖**：
  - B 类种子（外部直接引用）：`ElementNode`/`UiDocument`/`DocumentNode`/`TextNode`/`HtmlLikeDocumentWidget` + 29 个 event/handler/bounds 类，被 config/control/layout/paint/hud/screen/remote/style.cascade/style.selector/animation/net/host/devtools + 30+ test 深度依赖
  - A 候选不可删根因示例：`ElementNode extends ElementInteractionNode`、`ElementNode` 持有 `ElementInteractionHandlers`（13 handler 字段）→ 每个 handler 对应 event 被 dispatcher 持有 → dispatcher 被 `HtmlLikeDocumentWidget` 持有 → widget 被 config/hud 业务入口直接依赖
- **"只删0依赖"口径下"全线移除"达成度 = 0%**

**要真正移除 ui/document + ui/dom**，必须先迁移/删除全部外部业务依赖方（config/control/layout/paint/hud/screen/remote/style/animation/net/host/devtools + 30+ test），属多阶段大工程，需用户重新决策策略（迁移依赖方到 scene 新栈，或连同业务功能一起删）。**当前不启动**。

### 选项A迁移蓝图（2026-07-03 explorer gap 分析，月级工程）

用户决策选项A（迁依赖方到 scene 新栈，迁完再删旧栈）。explorer 能力 gap 分析结论：

**scene 新栈现状**：控件层（Phase 4）成熟，但**宿主粘合层（Phase 5）完全缺失**。当前是"控件库 + demo hub"，非完整 UI 框架。scene 包零 import 旧栈（物理隔离干净）。

**8 类依赖方的 scene 承接能力**：
| 依赖方 | scene 能力 | 状态 |
|---|---|---|
| 文本域（DocumentTextAreaControl） | SceneTextArea 已有 | ✅ 完全有 |
| config 控件层 | SceneTextInput/Toggle/Slider/Select/DataTable | ◐ 部分有（控件有，模板框架无） |
| 代码编辑器（DocumentCodeEditorControl） | 无 | ✗ 完全没有（牵连 text/layout 共享层） |
| HUD 宿主（UiHudDocumentHost） | 无（scene 仅 overlay，无 HUD 层级/输入抢占/聊天框共存） | ✗ 完全没有 |
| 屏幕入口（UiDocumentScreens） | 无（无 GuiScreen 桥接/屏幕管理） | ✗ 完全没有 |
| 远程HTML（RemoteHtmlDocumentParser） | 无（无 HTML 解析/远程UI协议） | ✗ 完全没有 |
| 旧栈 layout/paint | scene 有自己的 SceneLayoutEngine/ScenePaintEngine | 旧栈内部不迁 |

**可立即迁移子任务：0**。devtools 是 scene 唯一已迁消费群体，残留唯一旧栈消费者 `UiHudDemoController` 依赖 scene 缺失的 HUD 宿主。其余依赖方全是业务核心入口，都依赖 scene 缺失的宿主粘合层。

**推进前置（P2 架构级工程，每项需 Oracle 裁决 + 用户拍板）**：
1. scene HUD 宿主能力（层级/输入抢占/聊天框共存）
2. scene 屏幕入口（GuiScreen 桥接/屏幕管理）
3. scene 远程 UI 协议（HTML 解析/session/客户端桥）
4. scene config 模板框架（FieldSpec/PropertyBinding/草稿/保存闭环）
5. scene 高级控件（CodeEditor/ColorPicker/TreeView/SlotGrid/Autocomplete）

**结论**：选项A 是月级工程，不适合自主推进。需按"补 scene 宿主能力 → 迁业务入口 → 删旧栈"多会话推进，每阶段需用户拍板。当前维持现状。

---

## 五、KeyValueMap K3 Mutations 拆分裁决（不做）

**裁决**：不抽（2026-07-03 Oracle 裁决）。

**理由**：
- 增删改方法（updateRow/addRow/removeRow/publishRows）读写 `rows` Signal，无法脱离 reactive 做 L2 纯数学单测；L3 端到端路径已由 `SceneKeyValueMapTest` 完整覆盖（addRow/removeRow/updateRow/min-max 边界全有断言）
- 与 K1 本质不同：K1 抽的 `validateRows` 是纯 POJO 函数（理想 L2 目标），K3 是有副作用的 Signal 编排（L3、逻辑简单）
- 抽出反增耦合：双向牵连（mutation 依赖 `Props` 嵌套类，又被 `buildRow` 调用）+ safeRows 副本 + 类头开销，代码总量不减反增
- AGENTS §5「按职责/变更频率/复用边界拆，不按行数机械拆」：mutation 层与 create/buildRow 是同一条渲染-编辑编排链，非可独立演化/复用的职责

**纠正 A1 推断**：`canAdd(List,int)`/`canRemove(List,int)` 已是纯值参数（不读 Props），A1 称"全部依赖 Props"不准确。

---

## 维护纪律

- 新增债务：立项时追加条目，标注状态「待评估/缓做/真未还/阻塞」
- 债务还清：将状态改为「已还清」并补 commit/决策依据，**不立即删除条目**（保留历史锚点一个周期）
- 口径修订：直接覆盖原条目描述，不追加历史状态变更日志
- 引用规则：其他文档提到 scene 技术债时，统一指向本文件，不复制清单内容
- **去重纪律**：同一问题只在一个条目登记，跨分区重复时用指针引用，避免双源漂移
- **依据可追溯**：依据必须指向当前可追溯的文档（交接记录只保留最近一次，不可作为长期依据）
- **伪债务已清除**：曾逐条源码+commit 核实清除伪债务（已还清未标记/口径过时/有意边界误登/重复登记/依据链断裂），后续新增债务须先确认非伪债
