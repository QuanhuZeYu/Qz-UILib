# 玩家名称标签顺序、detached 失败与 pass-scoped 纠偏

## 现象

Minecraft 1.7.10 在 `RenderGlobal.renderEntities` 中先绘制实体及普通玩家名称标签，随后才绘制箱子等 TileEntity。后绘内容可能覆盖 framebuffer 中已有的标签像素，形成名称标签被箱子等内容遮掉的旧视觉问题。这是渲染顺序问题，与字体共享准备是否拥有 depth test/mask/func 的问题正交。

初版提交 `de15830c` 试图把普通玩家标签延后到 world-last，但该方案不能安全保持其他渲染参与者的作用域与生命周期。危险尝试先由 `4d25977c` 精确移除；当前已由 `8d55cf45`、`efc0d755`、`f48a4e46` 改为 pass-scoped 最终调用点方案。实现和静态复审已完成，CI 与运行态证据仍缺失，状态为 `INCOMPLETE`。

## 触发场景

初版在 `Render.func_147906_a` 的 HEAD cancel 原调用，将参数加入 FIFO 队列，再于 `RenderWorldLastEvent` 通过 Invoker 脱离原调用栈回放。当前 Angelica 运行基线同时在该目标方法的 HEAD/RETURN 注入生命周期，并由 `Render.doRender` 外层的 RenderManager Mixin 建立和清理 entity scope。

Angelica 等渲染器还可能对同一世界执行主 pass、shadow pass 等多次遍历。队列没有 capture window 或 render-pass 身份，而 `RenderWorldLastEvent` 只属于特定 Forge 主世界流程，不是所有渲染 pass 都会成对触发的通用结束边界。因此 shadow pass 捕获的标签可能泄漏到主 pass 回放、重复绘制，或在没有对应 world-last 的路径中滞留后被丢弃。

最终方案改在 `EntityRenderer.renderWorld(FJ)V` 的两个 `RenderGlobal.renderEntities` host 调用外建立独立 scope。每个 host 内部先完成实体、再完成 TileEntity；普通非潜行名称与玩家计分板各自在两个最终 `func_147906_a` INVOKE 捕获，host 正常返回后才按 FIFO 回放。潜行标签仍是独立直绘；shadow 和第三方直接调用不经过 host scope，保持即时绘制。

## 根因

- **顺序根因**：普通玩家标签在实体阶段写入 framebuffer，TileEntity 随后写入；只调整字体 depth 所有权不能改变先后顺序。
- **pass 边界错误**：把 `RenderWorldLastEvent` 当作所有 world render pass 的统一尾部，但它无法机械限定 Angelica 的 shadow/main pass，也不能为队列中的每次捕获提供所属 pass 身份。
- **foreign lifecycle 被截断**：在同目标方法 HEAD cancel 会跳过其他 Mixin 预期执行的 RETURN 清理；稍后用 Invoker detached 回放又会重新进入该目标，却已脱离原 RenderManager 包裹 `Render.doRender` 的 entity scope。原本成对的同目标 HEAD/RETURN 与外层 owner scope 因而被拆开，可能遗留错误的 shader entity 状态。

本质错误不是“延后”这一目标本身，而是在没有证明所有 pass、同目标 Mixin 生命周期及外层 owner scope 的情况下，把原调用当成可任意取消和异地重放的无副作用函数。

## 纠偏

提交 `4d25977c` 精确回退 `de15830c` 的 HEAD cancel、队列、Invoker 与 world-last 路径，恢复原版即时绘制。`4d25977c` 已独立静态复审为 `PASS`，其 tree hash 与保留字体 depth 所有权修复的 `6a9a375b` 同为 `7f710329c933df350924008c8d4c23b9501436aa`，因此危险尝试已移除而 `6a9a375b` 仍保留。

安全基线之后，以三笔独立提交形成当前真值：

- `8d55cf4570a373e92e28b011b54b65a55821a3c5`：用 `@WrapOperation` 保留可组合原调用链；为两个 vanilla host 建立线程局部栈与独立 FIFO；只捕获普通玩家名称和计分板最终调用点。无 scope、shadow 与第三方直接调用即时原绘制；host 异常不回放，回放异常丢弃尾项并清理 scope。
- 同一提交把 Angelica ABI 隔离到 `2.1.50` 可选 guard。仅“无 Angelica”或“精确 `2.1.50` 且 installed handshake 成功”允许捕获；未知版本、探针失败或 guard 缺失均在 host 前即时降级并单次告警。guard 只接受 phase `NONE`，临时建立 `ENTITIES`，结束后按 entity、item 恢复；非法 phase 丢弃该异常帧批次并单次告警。Angelica 依赖为 `devOnlyNonPublishable`，不进入发布依赖。
- `efc0d755d4c51e9bd44d09d49470567613e324fa`：每个捕获项保存原调用点的 `OpenGlHelper.lastBrightnessX/Y`，回放前逐项恢复；回放批次成对启用和关闭 lightmap。
- `f48a4e4668ee7350202e19a57ef6da360df603b3`：再保存批次入口的 post-host X/Y，并用嵌套 `finally` 先恢复坐标、再关闭 lightmap，避免正常或异常回放把最后一项亮度与 lightmap 状态泄漏给 host。

最终组合独立静态复审未发现 P0/P1/P2，A1-A9 静态满足。仓内已新增加载矩阵、coordinator/guard 单元测试与源码契约测试，但测试源码尚未运行；CI、编译、JUnit、发布制品、dedicated server、Angelica shader/shadow 与游戏内标签矩阵均未执行，当前证据状态仍为 `INCOMPLETE`。

## 预防

- 移动或延迟任何渲染调用前，先列出同目标方法上所有 Mixin 的成对入口/出口生命周期，并证明 cancel、异常和回放路径均不会跳过 foreign cleanup。
- 同时证明调用方外层 owner scope 在回放时仍成立；依赖 RenderManager entity scope、shader entity ID、相机或矩阵的调用不得脱离原作用域后用 Invoker 重放。
- 对主 pass、shadow pass 及其他可能的 world render pass，必须有可机械识别且成对的 capture/drain 边界和 pass 身份；`RenderWorldLastEvent` 不得默认视为通用 pass 边界。
- 延后渲染的完整合同必须同时覆盖 pass scope、foreign lifecycle、shader phase/entity/item 与 lightmap；不能只证明其中一部分。每项调用态与批次入口态都要区分保存，并以嵌套 `finally` 覆盖异常恢复。
- 优先在最终 INVOKE 使用 `@WrapOperation` 保存原调用链，不用 HEAD cancel、detached Invoker 或独占式 `@Redirect` 截断其他 wrapper。
- 可选渲染器只对已验证的精确 ABI 建围栏；未知版本、guard 缺失或无 scope 时即时原绘制。已进入已知 guard 却遇到非法 foreign phase 时，不得强行嵌套，应丢弃异常帧批次并节流告警。
- 当前契约只覆盖启用原版字体替换时的普通玩家名称与计分板，不得外推为潜行标签、全部实体标签或全部 world pass 已接管。详细取舍见 `../决策/player-name-tag-render-order.md`。
