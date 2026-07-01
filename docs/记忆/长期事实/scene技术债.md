# scene 新栈技术债权威清单

本文件是 scene 新栈技术债的**唯一权威源**。其他文档（交接记录、当前上下文、reviews 索引等）
只做指针引用「详见 `docs/记忆/长期事实/scene技术债.md`」，不再各自维护副本，避免口径漂移。

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
- **依据**：DECISION-20260628-scene-l1-grow-prior-asymmetry.md
- **定性**：不对称判定缺陷（非有意边界），CSS §9.8 definite 语义：父分配 tight 高 → 子高度 definite

### L2 childConstraintsWouldChange O(n²)
- **现象**：逐子调 `buildChildConstraints` 叠加每子求解使脏判定为 O(n²)；
  freeze do-while 会进一步加重
- **状态**：与 B4 同一问题（`SceneLayoutEngine:430-441` 同段代码），**去重合并到 B4**
- **依据**：见 B4 条目及 `DECISION-20260626-b4-column-fill-on2-deferred.md`

---

## 二、scene 非布局技术债（oracle 架构审核产出）

来源：`docs/开发者文档/reviews/REVIEW-20260625-scene-oracle-architecture-audit.md`

### B4 COLUMN fill O(n²) 约束判定（含 L2）
- **位置**：`SceneLayoutEngine:430-441`（行号已核实未漂移）
- **状态**：缓做（单容器子数小 + 干净帧短路，沿用接受口径）
- **依据**：`DECISION-20260626-b4-column-fill-on2-deferred.md`
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
- **依据**：`DECISION-20260626-b6-transform-clip-fbo-deferred.md`、
  REVIEW-20260625-scene-oracle-architecture-audit.md
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
- **依据**：REVIEW-20260625:172-185（原文未落盘待确认）
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
- **依据**：DECISION-20260628-scene-slider-defect-d-fix.md
- **范式约束**：拖拽类控件"瞬态 signal 只写不读、业务值用事件坐标当场算"

### chrome 主题层
- **状态**：P2 大工程未立项

---

## 三、有意边界（非债，仅记录设计取舍）

### L3 percentHeight 在 ROW 容器下不生效
- **现象**：`percentHeight` 仅 COLUMN 主轴生效，ROW 下被当作普通 fill 子处理
- **性质**：有意设计边界，字段 Javadoc 已明确，**不视为债**
- **依据**：DECISION-20260628-scene-min-max-clamp.md

---

## 四、Phase 5 旧栈退役

旧 HTML-like / `ui.dom` 栈已废弃，不再维护。退役清理作为方向性待办，但不在当前 UI 层工作主线内。

---

## 维护纪律

- 新增债务：立项时追加条目，标注状态「待评估/缓做/真未还/阻塞」
- 债务还清：将状态改为「已还清」并补 commit/决策依据，**不立即删除条目**（保留历史锚点一个周期）
- 口径修订：直接覆盖原条目描述，不追加历史状态变更日志
- 引用规则：其他文档提到 scene 技术债时，统一指向本文件，不复制清单内容
- **去重纪律**：同一问题只在一个条目登记，跨分区重复时用指针引用，避免双源漂移
- **依据可追溯**：依据必须指向当前可追溯的文档（交接记录只保留最近一次，不可作为长期依据）
- **伪债务已清除**：曾逐条源码+commit 核实清除伪债务（已还清未标记/口径过时/有意边界误登/重复登记/依据链断裂），后续新增债务须先确认非伪债
