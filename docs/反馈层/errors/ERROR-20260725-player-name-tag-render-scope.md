# 玩家名称标签顺序与不安全的 detached 回放

## 现象

Minecraft 1.7.10 在 `RenderGlobal.renderEntities` 中先绘制实体及普通玩家名称标签，随后才绘制箱子等 TileEntity。后绘内容可能覆盖 framebuffer 中已有的标签像素，形成名称标签被箱子等内容遮掉的旧视觉问题。这是渲染顺序问题，与字体共享准备是否拥有 depth test/mask/func 的问题正交。

初版提交 `de15830c` 试图把普通玩家标签延后到 world-last，但该方案不能安全保持其他渲染参与者的作用域与生命周期。危险尝试现已移除；名称标签恢复原版即时绘制，旧视觉问题仍未修复。

## 触发场景

初版在 `Render.func_147906_a` 的 HEAD cancel 原调用，将参数加入 FIFO 队列，再于 `RenderWorldLastEvent` 通过 Invoker 脱离原调用栈回放。当前 Angelica 运行基线同时在该目标方法的 HEAD/RETURN 注入生命周期，并由 `Render.doRender` 外层的 RenderManager Mixin 建立和清理 entity scope。

Angelica 等渲染器还可能对同一世界执行主 pass、shadow pass 等多次遍历。队列没有 capture window 或 render-pass 身份，而 `RenderWorldLastEvent` 只属于特定 Forge 主世界流程，不是所有渲染 pass 都会成对触发的通用结束边界。因此 shadow pass 捕获的标签可能泄漏到主 pass 回放、重复绘制，或在没有对应 world-last 的路径中滞留后被丢弃。

## 根因

- **顺序根因**：普通玩家标签在实体阶段写入 framebuffer，TileEntity 随后写入；只调整字体 depth 所有权不能改变先后顺序。
- **pass 边界错误**：把 `RenderWorldLastEvent` 当作所有 world render pass 的统一尾部，但它无法机械限定 Angelica 的 shadow/main pass，也不能为队列中的每次捕获提供所属 pass 身份。
- **foreign lifecycle 被截断**：在同目标方法 HEAD cancel 会跳过其他 Mixin 预期执行的 RETURN 清理；稍后用 Invoker detached 回放又会重新进入该目标，却已脱离原 RenderManager 包裹 `Render.doRender` 的 entity scope。原本成对的同目标 HEAD/RETURN 与外层 owner scope 因而被拆开，可能遗留错误的 shader entity 状态。

本质错误不是“延后”这一目标本身，而是在没有证明所有 pass、同目标 Mixin 生命周期及外层 owner scope 的情况下，把原调用当成可任意取消和异地重放的无副作用函数。

## 纠偏

提交 `4d25977c` 精确回退 `de15830c` 的 HEAD cancel、队列、Invoker 与 world-last 路径，恢复原版即时绘制。`4d25977c` 已独立静态复审为 `PASS`，其 tree hash 与保留字体 depth 所有权修复的 `6a9a375b` 同为 `7f710329c933df350924008c8d4c23b9501436aa`，因此危险尝试已移除而 `6a9a375b` 仍保留。

该纠偏只恢复安全基线，不代表玩家名称标签顺序问题已解决。CI、编译、JUnit、Angelica shader 与游戏内标签矩阵均未执行，当前证据状态为 `INCOMPLETE`。

## 预防

- 移动或延迟任何渲染调用前，先列出同目标方法上所有 Mixin 的成对入口/出口生命周期，并证明 cancel、异常和回放路径均不会跳过 foreign cleanup。
- 同时证明调用方外层 owner scope 在回放时仍成立；依赖 RenderManager entity scope、shader entity ID、相机或矩阵的调用不得脱离原作用域后用 Invoker 重放。
- 对主 pass、shadow pass 及其他可能的 world render pass，必须有可机械识别且成对的 capture/drain 边界和 pass 身份；`RenderWorldLastEvent` 不得默认视为通用 pass 边界。
- 上述任一条件无法证明时保持原调用，只登记顺序问题为开放项，不以 cancel + queue + detached Invoker 冒充安全移动。
