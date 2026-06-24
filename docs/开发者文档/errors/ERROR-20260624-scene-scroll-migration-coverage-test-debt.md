# 滚动迁移侦察覆盖不全 + 旧测试「错对错」暴露

## 错误现象

滚动双缺陷修复任务中连续踩两个坑：

1. **fixer 漏迁 ObjectField host**：第一轮 fixer 只改了 Form/Layout/Table/Scroll
   四个 host，遗漏 `SceneObjectFieldHostWidget`。用户真机复测发现 Object 页面
   仍然滚不到底，二次返工才补上。
2. **迁移后测试回归**：SceneScrolls 封装迁移 host 后，`SceneLayoutDemoTest
   .wheelShouldUpdateViewportScrollOffsetViaSignal` 断言失败（`:143`）。

## 触发场景

### 坑 1：侦察覆盖不全
explorer 侦察滚动缺陷时，对 ObjectField/SimpleList/KeyValueMap 三个 host 的
滚动公式标注了「推测同构但未逐一读」（侦察报告「推测部分」第 2 条）。
主 Agent 据此派 fixer 时，只在缺陷 A 修复点列了 Form/Layout/Table/Scroll
四个**已确认**的 host，未把「推测未读」的三个 host 纳入修复范围。

### 坑 2：旧测试「错对错」
`SceneLayoutDemoTest:129` 用旧公式 `contentBox.getHeight() -
viewportBox.getHeight()` 算 maxScroll。迁移前 host 也用旧公式（漏 28px
padding），测试与实现「错对错」刚好匹配，测试通过。迁移后 host 走
`SceneGeometry.maxScrollY`（含 padding 闭式），测试还用旧公式，
两侧口径不一致，断言失败。

## 根本原因

### 坑 1 根因
- **侦察报告的「推测/未验证」项被当作「不存在」处理**。explorer 明确标注了
  「未逐一读 host viewport 是否 scrollable」，但主 Agent 派 fixer 时未把
  「推测未读」等同于「必须补查」，直接限定范围到已确认项。
- **缺少全量 grep 兜底**。修复后未用 grep 全量扫描 pages 目录确认无残留
  旧公式，导致漏网。

### 坑 2 根因
- **测试与实现共用同一套错误公式**，形成「错对错」平衡。任何一方先改对，
  平衡打破，测试暴露。
- 这本质是**测试债**：测试没有用独立的正确口径（如 `SceneGeometry.maxScrollY`
  闭式）做断言，而是复制了实现的错误公式。

## 修复方案

### 坑 1 修复
- `SceneObjectFieldHostWidget.java:78-87` 旧公式 →
  `SceneGeometry.maxScrollY(viewport)`，清理冗余强转 + null 检查 + import。
- 全量 grep `pages` 目录确认无残留旧公式（`contentBox.getHeight() -
  viewportBox.getHeight` 等模式）。

### 坑 2 修复
- `SceneLayoutDemoTest:129` 旧公式 → `SceneGeometry.maxScrollY(viewport)`，
  与 `SceneScrolls.attach` 内部闭式口径一致。
- 全量 grep 测试目录，确认仅 `SceneScrollViewportTest:495` 残留旧公式，
  但该测试 viewport 无 padding，旧公式与闭式等价，不需改。

## 预防措施

1. **侦察报告的「推测/未验证」项 = 必须补查项**，不得据此限定 fixer 范围。
   主 Agent 派 fixer 前应把「推测未读」的文件全量读一遍或派 fixer 兜底覆盖。
2. **修复后全量 grep 兜底**。同类修复（如「旧公式 → 新公式」）完成后，
   grep 全项目确认无残留旧模式，不依赖侦察报告的完整性。
3. **测试断言用独立正确口径**，不要复制实现的公式。测试的 maxScroll 应直接
   调 `SceneGeometry.maxScrollY`（权威闭式），而非手写 `contentH - viewportH`。
4. **封装是根治**。这次最终用 `SceneScrolls.attach` 把 maxScrollY 闭式 +
   条件 stop 收口为一处不可绕过的入口，从根上消灭「调用方手写公式」的复发
   路径。后续新增滚动页只调 `attach`，不可能再写错公式。

## 关联

- 滚动 API 封装决策：`docs/记忆/决策/`（SceneScrolls 候选 A 评估）
- 三个 commit：`89b2f5d6`（封装）/ `2a78f43d`（控件迁移）/ `b3afef39`（host 迁移）

## 补充教训（2026-06-24 SceneDataTable commit 2）

fixer 在 commit 2 实现时擅自覆盖了 `docs/记忆/当前态/交接记录.md`，
把第 90 次交接内容覆盖成第 91 次，丢失滚动 API 封装全部记录。

**根因**：派发 fixer 时未明确禁止触碰 `docs/记忆/` 下文件。
**预防**：派发 fixer 时任务说明必须包含「不要改任何记忆文档
（docs/记忆/ 下的文件由主 Agent 统一管理，fixer 不得触碰）」。
已纳入交接记录重要纪律。
