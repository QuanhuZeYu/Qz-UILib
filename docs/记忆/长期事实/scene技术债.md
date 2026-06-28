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
- **状态**：reviewer 建议级已知边界，待真实需求触发再扩展
- **依据**：第 126 次交接记录

### L2 childConstraintsWouldChange O(n²)
- **现象**：逐子调 `buildChildConstraints` 叠加每子求解使脏判定为 O(n²)；
  freeze do-while 会进一步加重
- **状态**：待性能暴露再评估记忆化
- **依据**：第 126 次交接记录

### L3 percentHeight 在 ROW 容器下不生效
- **现象**：`percentHeight` 仅 COLUMN 主轴生效，ROW 下被当作普通 fill 子处理
- **状态**：有意边界，字段 Javadoc 已明确，不视为债
- **依据**：DECISION-20260628-scene-min-max-clamp.md

---

## 二、scene 非布局技术债（oracle 架构审核产出）

来源：`docs/开发者文档/reviews/REVIEW-20260625-scene-oracle-architecture-audit.md`

### B4 COLUMN fill O(n²) 约束判定
- **位置**：`SceneLayoutEngine:430-441`
- **状态**：缓做（单容器子数小 + 干净帧短路，沿用接受口径）
- **依据**：DECISION-20260626-b4-column-fill-on2-deferred.md

### B5 paint LEFT 无谓 measureWidth
- **位置**：`ScenePaintEngine:284`
- **状态**：真未还，待性能暴露

### B6 transform+clip 叠加坐标错位
- **位置**：`ScenePaintEngine:130`
- **状态**：批 1 FBO 方案已落地；剩余债转为批 3 纹理脏标记跨帧复用（待性能暴露）+ hit-test 对偶（SceneHitTester 对 transform 零感知，待交互需求触发）
- **依据**：DECISION-20260626-b6-transform-clip-fbo-deferred.md、REVIEW-20260625-scene-oracle-architecture-audit.md

### B8 滚动后 hover 滞留
- **位置**：`SceneInputRouter:147-168`
- **状态**：真未还，待交互需求触发

### A1 effect 内 set 慢一帧残留
- **状态**：大部分被不动点覆盖，残留待确认

### A6 bind impact 死参数
- **位置**：`SceneRuntime:178`
- **状态**：真未还（低优先）

### chrome 主题层
- **状态**：P2 大工程未立项

---

## 三、Phase 5 旧栈退役（阻塞中）

- **阻塞原因**：依赖 dom 旧栈在 Phase 4 完成后才能退役，当前不可启动
- **退役条件**：须满足 NORTH_STAR 规定的全部退役条件后才删

---

## 维护纪律

- 新增债务：立项时追加条目，标注状态「待评估/缓做/真未还/阻塞」
- 债务还清：将状态改为「已还清」并补 commit/决策依据，**不立即删除条目**（保留历史锚点一个周期）
- 口径修订：直接覆盖原条目描述，不追加历史状态变更日志
- 引用规则：其他文档提到 scene 技术债时，统一指向本文件，不复制清单内容
