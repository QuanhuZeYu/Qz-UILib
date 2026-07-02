# 决策：scrollable 视口与 overlay 多 paint root 转正为宪章一等能力

## 背景

NORTH_STAR 偏离登记中两条「待转正」条目，经 SceneSelect 三批真机缺陷验收通过后条件成熟：
- A1（`id="2026-06-21-扩展"`）：scrollable 视口 `computeHeight` 主动忽略内容高、
  钉死视口高，打破纯 bottom-up 布局模型
- A2（`id="2026-06-23"`）：overlay 多 paint root + 独立 hit-test 入口，
  打破单一渲染树模型

2026-06-24 派 oracle 独立核验，裁决两条均可转正。

## 候选方案

1. **合并转正**：两条同性质（UI 库固有一等能力被宪章结构描述漏掉），
   都改 §4/§4.5 正文、都不新增不变量、都已真机验收，一次编辑覆盖
2. **只转 A1**：A2 暂挂账待更多消费者验证
3. **只转 A2**：A1 暂挂账
4. **暂不转正**：两条继续挂账

## 最终选择

采用方案 1：合并转正。

## 选择原因

- **代码侧早已稳定**：两条偏离的代码已是事实标准，转正是「文件对齐代码」
- **不破坏任何不变量**：I7/I8/I10/I11 全部守住，无需新增 I12
  - A1：scrollOffsetY 走 geometry 级零重排，selfConsumesConstraint 守 I8
  - A2：per-root 独立缓存隔离约束，dismiss 写 signal，anchor 读取属逃生舱①
- **同性质**：都是「UI 库固有一等能力被宪章结构描述漏掉」，非债
- **回归锚点齐备**：SceneScrollViewportTest/SceneHitTesterTest/
  SceneAnchoredOverlayPipelineTest/SceneOverlayPipelineTest/
  SceneOverlayHitTestTest/SceneOverlayDismissTest/SceneOverlayPortalTest

## 影响范围

- `NORTH_STAR.md` §4 渲染管线模型：追加视口容器 + top-layer 多 paint root 两段
- `NORTH_STAR.md` §4.5 输入半环：追加浮层优先命中一段
- `NORTH_STAR.md` 偏离登记：两条标 ✅已转正 + 修正 A2 scope 类名
- **不改任何代码**

## 后续注意事项

- 转正后这两条不再是「债」，无需回填
- A1 的显式 viewport/content 双节点方案降级为未来可选优化，非必须
- A2 的 scope 类名已修正为 `AbstractSceneHostWidget`（原误标 `SceneHostWidget`）
- oracle 评估依据见本决策文件与 NORTH_STAR 偏离登记原文
