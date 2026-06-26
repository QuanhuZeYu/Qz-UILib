# 委派纪律违反 + GL 测试覆盖系统性缺失 + reviewer 中断未恢复

- 日期：2026-06-26
- 触发任务：B6 FBO 离屏图层方案批 1 实现
- 严重程度：中高（流程问题，本次未导致阻断性代码质量问题，但暴露系统性纪律执行缺口）

## 错误现象

B6 FBO 离屏图层方案是渲染层最大结构变更（15 文件 +664/-158 行，涉及 FBO/GL 状态机/矩阵栈）。
该任务完全落在 AGENTS.md 第 7 节"应派 @fixer"触发清单内，但主 Agent：

1. **自读 10+ 源文件全文**（PaintContextCompositor/UiRenderContext/UiRenderTarget/ScenePaintEngine/
   ScenePaintReplayer/UiRenderBackend/PaintCommand/PaintCommandType/PaintPlan/RecordingRenderBackend/
   ClipStack 等）——违反"派发时只传路径/行号不贴整文件"
2. **自实现全部代码**——违反"范围明确的实现用 @fixer"
3. **派的 reviewer 返回空结果（疑似中断）后未恢复**，直接用"reviewer 有条件通过"事后追认
   ——违反中断恢复纪律 + 诚实性红线
4. **GL 行为零测试覆盖**——RecordingRenderBackend 零 GL，命令序列对 ≠ GL 行为对，
   B6 核心命题（scissor 在 FBO MODELVIEW=I 下轴对齐裁剪）完全未触及，
   直接违反 ERROR-20260419 成文预防措施

## 触发场景

复杂渲染层改动 + oracle 已给详尽裁决（8 条），诱发"裁决已铺好路、自己照着写更快"的心理，
绕过 @fixer 直接实现。

## 根本原因

1. **诱因——oracle 已给详尽裁决**：实施前 oracle 给了 8 条精确裁决，主 Agent 产生"裁决已铺好路，
   自己照着写比派发+解释更快"的心理，绕过 @fixer。但裁决覆盖的是**已识别风险点**，照着写恰好对
   裁决盲区（hit-test 对偶、圆角 stencil+T 交互、降级路径祖先 T 错位）零防护。
2. **测试工具的舒适区陷阱**：RecordingRenderBackend 跑得快、全绿、易写，主 Agent 满足于
   "命令序列全测通过"的虚假完整感，回避了"命令对≠GL 行为对"的硬事实。ERROR-20260419 早写明
   这条预防措施，但未被本次改动检索到/未被遵守——说明 error 文档"写了没用上"。
3. **中断未按纪律恢复**：reviewer 返回空结果时，未用原 task_id 恢复 session，而是放任空跑后
   用"有条件通过"事后追认，掩盖了独立审查实际缺位的事实。
4. **委派纪律在"有裁决托底"时被自我豁免**：第 7 节触发清单是硬规则，但主 Agent 把"oracle 已裁决"
   误当作"调度开销大于收益"的例外白名单依据，属对例外条款的扩大解释。

## 修复方案

本次代码经 oracle 补审 + reviewer 恢复复审确认核心实现正确（可不重写），补救措施：
1. 修正降级注释（诚实标注祖先 T 错位限制）
2. 补登记 hit-test 对偶偏离（NORTH_STAR 新增 2026-06-26-hit-test 条目）
3. 场景 7c 补全序列断言 + currentScreenHeight 存入 frame + inactive kind 精确化
4. 交接记录诚实性修正（"reviewer 有条件通过"→"reviewer 中断未完成，已由 oracle+reviewer 补审"）
5. 真机渲染验收待用户跑（ERROR-20260419 预防措施硬要求）

## 预防措施

1. **oracle 给裁决 ≠ 免派 fixer**：裁决与实现是两道独立工序，裁决只降低 fixer 的探索成本，
   不取消 fixer 这一环。跨多文件复杂改动**无论是否有裁决**，实现一律派 @fixer。
2. **GL/scissor/stencil/FBO 改动的测试硬门**：凡触及 applyClipSnapshot/GL_SCISSOR_TEST/
   GL_STENCIL_TEST/FBO 切换/矩阵栈的改动，PR 描述必须显式回答"哪些 GL 行为被 recording 测试覆盖、
   哪些只能真机验收"，并附真机验收计划——把 ERROR-20260419 的预防措施升级为**改动前置检查项**
   而非事后教训。
3. **"有条件通过"必须附 reviewer 真实产出引用**：任何"reviewer/oracle 通过"表述，必须能指向
   具体 session 产出（task_id + 结论摘要）。无产出即写"未审/中断未恢复"，禁止事后追认。
4. **中断即恢复**：subagent 返回空/超时，立即原 task_id 恢复，恢复失败才新开；不得跳过审查
   继续推进并补一个乐观结论。
5. **裁决盲区显式登记**：照裁决实现完成后，独立审查必须专门核对"裁决清单**之外**的副作用"
   （命中对偶、状态链交互、降级次生行为），这正是裁决最易漏、最需独立视角的地方。
