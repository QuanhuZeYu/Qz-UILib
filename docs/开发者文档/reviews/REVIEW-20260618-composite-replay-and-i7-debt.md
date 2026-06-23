# REVIEW-20260618 — COMPOSITE 级失效连通坐实 + I7 粗粒度标脏债还清（阶段 2/3）

> 性质：reactive→DOM 失效层接入审查的收尾两阶段。阶段 1（P0 双重标脏还债）见 `REVIEW-20260618-reactive-dom-invalidation.md`。
> 本轮目标：还清 reactive→DOM 接入审查暴露的全部剩余债（用户拍板"还完所有债再推进"）。

## 总体结论

**阶段 2、3 全部完成，零回归。reactive→DOM 失效层接入审查暴露的全部债（P0 双重标脏 + COMPOSITE 断链疑云 + I7 粗粒度标脏）已全部还清。**

- **阶段 2（COMPOSITE）**：核实 COMPOSITE 级失效**早已真连通**，非注释所称"降级为 PAINT"。补齐 widget 层端到端命中测试坐实，修正过时注释。无生产逻辑改动。
- **阶段 3（I7）**：还清行 242 偏离登记的粗粒度结构标脏债。**oracle 否决原方向 1（reconcileChildren 批量 API），改用更简单的方案 X（递归→非递归降级，<10 行）**，避开方向 1 的两个正确性风险。

---

## 阶段 2：COMPOSITE 级失效连通坐实

### 背景

阶段 1 审查时 `UiStyleChangeImpact.COMPOSITE` 的注释称"当前阶段降级处理为 PAINT，待分级脏标记完整实现后将独立走 composite-only 回放路径"。这与信条五铁律（60fps 动画不得触碰布局/绘制层）直接冲突——若 transform/opacity 真走重绘，动画必掉帧。需核实真实状态。

### 侦察结论（explorer）

**注释过时，COMPOSITE 在引擎层和 document 失效层都已真连通：**

1. `ElementNode.java:52-54` 对 COMPOSITE 走独立 `markCompositeMutated`（≠ PAINT 的 `markPaintMutated`），只 bump `compositeVersion`、不碰 `paintVersion`。
2. `HtmlLikeDocumentWidget.java:1080` 路②专门检测"paintVersion 未变 + compositeVersion 变"→ 走 composite-only 就地回放 `tryApplyCompositeReplayOnCache`。
3. `DocumentPaintEngine.tryApplyCompositeReplay:207-257` 是**完整真实现**（非 stub）：结构守卫通过后就地更新 TRANSFORM/PAINT_CONTEXT 命令字段，仅结构性翻转（transform 增删 identity、opacity 跨 0.999 paint-context 阈值）才回退全量重建。已被
   `DocumentPaintEngineTest` 5 个引擎层用例验证。

**关键缺口**：引擎层 `tryApplyCompositeReplay` 被孤立验证过，但"widget 三路缓存里路②端到端命中"这条连通链**无任何测试坐实**。

### 交付

1. **新增 widget 层端到端测试**（`HtmlLikeDocumentWidgetAnimationRuntimeTest`）：
   - `shouldHitCompositeReplayWhenOnlyOpacityChanges`：opacity 0.5→0.25（不跨阈值）。
   - `shouldHitCompositeReplayWhenOnlyTransformChanges`：transform translate 平移变更（始终非 identity）。这是此前完全缺失的 widget 级 transform 端到端命中测试。
   - **黑盒三信号断言**（不反射私有 `compositeReplayAppliedThisResolve`）：`paintCacheGeneration` 前后相等（命令未重建，排除路③全量重建）+ `paintVersion` 前后相等（COMPOSITE 不污染 paintVersion）+ `compositeVersion` +1（确有 COMPOSITE 变更且独立记账，
     排除路①双未变）。三信号唯一锁定路②命中。
   - 前置：先 render 一帧建命令缓存，否则 `tryApplyCompositeReplayOnCache` 因"未构建过命令"返回 false 落路③。
2. **`RecordingUiRenderContext` 加 `pushTransform`/`popTransform` no-op 覆写**（`HtmlLikeDocumentWidgetTestSupport`）：录制上下文只记逻辑坐标、不应用真实 GL 矩阵，no-op 语义正确，使 widget 级 transform 端到端测试可在无 GL 沙箱运行。顺带纠正旧 javadoc
   "transform 因 LWJGL native 无法在沙箱运行"的过时说法。
3. **重写 `UiStyleChangeImpact.COMPOSITE` 注释**：删除"降级为 PAINT"过时表述，改为陈述事实——已独立连通 composite-only 回放路径。

### 验证

AnimationRuntimeTest 35 + PaintEngineTest 57 全绿，compileJava 通过。**信条五铁律达成确认**：transform/opacity 变更走 composite-only 就地回放，不触碰布局/绘制层。

---

## 阶段 3：I7 粗粒度结构标脏债还清

### 背景

行 242 偏离登记的债：`DocumentNode.recordStructuralMutation → markSubtreeLayoutMutation` 对容器 append/removeChild **无条件向下递归标脏全部后代**（含 forEach keyed 复用、几何未变的稳定兄弟），layout 层 `resolveReusableLayoutBox` 的 version
闸门据此判定复用失败、真实重算。违反 I7（干净子树三阶段跳过）。详见 `ERROR-20260617-dom-coarse-subtree-dirty-marking.md`。

阶段 1 修完 P0 全树标脏后，这条容器子树标脏债成为唯一剩余的粗粒度标脏点。

### 关键裁决：oracle 否决方向 1，改用方案 X

ERROR 文档原登记的方向 1 = 新增 `reconcileChildren(List<ChildOperation>)` 批量提交 API（200-400 行）。**oracle 架构裁决否决之**：

- **债的根因不是"逐次提交"，而是 `markSubtreeLayoutMutation` 的无条件递归。** 把递归标脏降级为"只标自己 + 向上冒泡"（复用既有 `markLayoutMutation`）即在所有结构入口根除债。
- **方案 X 在每个维度不劣于方向 1，且消解方向 1 的两个真实风险**：
  - **风险 A（删除路径双重移除）直接消失**：不引入批量删除，onCleanup 链路零改动。removeChild 单次株连也被同一处改动同步修复。
  - **风险 B（分 display 模式漏标）是陷阱**：方向 1 若按"分模式连带标兄弟"实现，就让 DOM 层理解 flex/table 几何传播规则——这正是方向 2 被否的撞 I6 错误。正确做法是 DOM 层一律只标容器自己，把"兄弟几何是否真变"全下放给 layout 复用闸门，而闸门维度已完备。

### 闸门兜底各模式（oracle 逐模式源码论证）

| display | 兄弟增删对稳定兄弟影响 | 闸门如何捕获 |
|---|---|---|
| flex | grow/shrink 主轴空间重分配，稳定 item 尺寸真变 | 最终主轴尺寸作 `forcedContentWidth/Height` 传入 → 闸门 forced 维度不等 → 重算 |
| block | 后续兄弟 flow 位置偏移，尺寸不变 | flowTop 变（不在闸门比对维度）→ 走 `translatedTo` 平移复用 → 位置正确且子树跳过 |
| inline-block | 同行 fragment 落位/换行，尺寸不变 | 引擎重算 cursor 传入新 containingLeft/flowTop → 平移复用 |
| inline | 行盒整体重排 | 不走元素级 box 复用，无"稳定 inline 兄弟"概念，非债 |
| table | 行列结构变→列宽重分配，cell 宽真变 | cell `forcedContentWidth` 基于列宽 → 闸门 forced 不等 → 重算 |

这印证深层架构事实：SceneNode 新模型的 `descendantLayoutDirty` 路标下沉，在旧 DOM 这里有 version 版等价物——容器标 self + 冒泡刷祖先 subtree，兄弟支 version 未碰即平移复用。

### 交付（方案 X，保守版）

`DocumentNode.java` 6 处改动（<10 行有效逻辑）：
- `recordStructuralMutation:519` `markSubtreeLayoutMutation`→`markLayoutMutation`（容器只标自己+subtree+冒泡，不递归）
- `:520` 删除冗余 `propagateSubtreeLayoutMutationToAncestors`（markLayoutMutation 内已含冒泡）
- `:525-526` 旧父合并为单行 `markLayoutMutation`
- `:528-530` changedSubtree 改 `markLayoutMutation`（保守版：标被移动节点 self、不递归子树，正确性绝对安全）
- `replaceChild:280`、`clearChildren:296` 顺带改（被替换/清空节点已 parent=null，递归标无意义）
- **reconciler / UiComponentRuntime / DocumentLayoutEngine / 删除路径全部零改动**

私有 `markSubtreeLayoutMutation` 保留（`markSubtreeMutated`/`__markSubtreeLayoutDirty` 仍合法调用——文档级样式表/变量全局失效确需递归全标，与结构增删株连债无关）。

### 验证

- **回归锚点翻转**：`DocumentBreadcrumbControlTest.documentsKnownCoarseSubtreeDirtyMarkingDebt`（断言"债存在"）→ `stableSegmentSubtreeIsNotDirtiedByListMutation`（assertEquals 验证 I7 正向达成）。兑现 ERROR 登记预埋的"修复后翻转"信号。
- **新增 5 个 DOM 层 version 零株连测试**（`DocumentNodeStructuralMutationDirtyTest`）：INSERT/REMOVE 稳定兄弟零株连、嵌套子树不株连、MOVE 保守版子树保护、跨容器移动旧父株连隔离。
- **新增 3 个 layout 端到端防漏标测试**（`DocumentLayoutEngineTest`）：flex forced 维度兜底、block translatedTo 平移兜底、table 行增删列宽重算。
- 全量 1737 测试 9 失败=历史预存环境集（7× DocumentPaintRendererTest NoClassDefFoundError + cursor backend + TextLayoutService），git stash 隔离验证零回归。
- **fixer 自查与 oracle 方案无任何不符**：无一项测试需改 reconciler/layout 才能通过，正面验证 oracle"根因是无条件递归、降级即根除"判断成立。

---

## 本轮元层面价值

oracle 把方向 1（200-400 行批量 API）压成方案 X（<10 行降级递归）。explorer 侦察和 orchestrator 都默认沿用 ERROR 文档登记的方向 1，oracle 一眼看穿根因是"无条件递归"而非"逐次提交"，并识破风险 B 提问本身埋的 I6 陷阱。这是"先派 oracle 定方案再实现"对高风险架构改动的直接价值——避免了一次过度设计的大改动。
