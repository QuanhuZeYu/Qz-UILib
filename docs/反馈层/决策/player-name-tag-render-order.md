# 决策：玩家标签 pass-scoped 渲染顺序

## 背景

Minecraft 1.7.10 的 `RenderGlobal.renderEntities` 先绘制实体，再绘制 TileEntity。普通玩家名称与计分板标签原本在实体阶段写入 framebuffer，可能被随后绘制的箱子等内容覆盖。该问题属于调用顺序，与字体共享准备是否拥有 depth test/mask/func 正交。

延后调用不能只考虑“何时画”：还必须保留目标调用上的其他 Mixin 链，并为所属 world pass、shader phase/entity/item 与 lightmap 建立可配对的生命周期。初版 HEAD cancel + world-last detached Invoker 已证明不满足这些条件。

## 最终选择

采用“最终调用点 `@WrapOperation` + 两个 vanilla host scope + 可选 Angelica 精确版本围栏”的组合，不建立全局 world-last 队列。

### 捕获边界

- 在 `EntityRenderer.renderWorld(FJ)V` 中精确包装两个 `RenderGlobal.renderEntities` 调用。每次 host 调用内部完成实体与 TileEntity 绘制，正常返回后立即排空该 host 自己的 FIFO；两个 pass 不共享队列。
- scope 为渲染线程局部栈，嵌套 host 各自隔离。host 异常不回放，回放异常丢弃尾项，`finally` 清除当前 scope。
- 普通非潜行名称只包装 `RendererLivingEntity.func_96449_a` 中两个最终 `func_147906_a` INVOKE；玩家计分板只包装 `RenderPlayer.func_96449_a(AbstractClientPlayer,...)` 中两个最终 INVOKE。
- 只有启用原版字体替换且实体为 `AbstractClientPlayer` 时才尝试捕获。潜行标签的独立直绘、非玩家标签与第三方直接调用不在包装目标内，始终执行原调用；Angelica shadow 不经过两个 host scope，名称调用点也会立即执行原调用。
- `@WrapOperation` 保存可组合的 `Operation` 原调用链，使同目标方法上的 wrapper 与 HEAD/RETURN 仍在真正调用时成对执行；不使用会独占调用点的 `@Redirect`。UniMixins `0.3.1` 已固定提供 MixinExtras `0.5.0`，不另行引入或发布 MixinExtras 依赖。

该边界只承诺上述普通玩家名称与计分板调用点，不宣称接管全部实体标签或全部世界渲染入口。

### Angelica 兼容边界

- 通用 coordinator 与三个 vanilla 调用点 Mixin 不链接 Angelica ABI。Angelica 仅作为 `devOnlyNonPublishable` 开发依赖，不进入发布依赖。
- 无 Angelica 时允许捕获；存在 Angelica 时，仅精确版本 `2.1.50` 且可选 Mixin 已完成 installed handshake 才允许捕获。
- 未知版本、版本探针失败、空兼容状态或 guard 未安装时，当前 host 不建立 scope，标签保持原版即时绘制并至多告警一次。Angelica shadow 不经过两个 vanilla host 调用，也因无 scope 保持即时绘制。
- 已捕获批次进入 guard 时必须处于 `WorldRenderingPhase.NONE`。guard 临时建立 `ENTITIES` phase，保存调用方 entity/item，结束 phase 后按 entity、item 顺序恢复；该顺序避免 `setCurrentEntity` 隐式清零 item。
- 若进入 guard 时 phase 非 `NONE`，不嵌套或猜测 foreign phase，而是丢弃该异常帧的标签批次并至多告警一次。此分支与“兼容条件不满足时即时绘制”的 host 前置降级不同。

### Lightmap 生命周期

- 每个普通名称或计分板捕获项在原调用点保存 `OpenGlHelper.lastBrightnessX/Y`，回放该项前恢复其原始亮度坐标。
- 每个回放批次保存 post-host 的 X/Y，成对执行 `enableLightmap` / `disableLightmap`。嵌套 `finally` 先恢复批次入口坐标，再关闭 lightmap；批次或坐标恢复异常均不能跳过关闭步骤。

因此，延后标签既获得各实体调用点的亮度，也不会把最后一项坐标或启用中的 lightmap 泄漏给 host 后续渲染。

## 拒绝的方案

- **HEAD cancel + `RenderWorldLastEvent` detached Invoker**：world-last 不是所有主/阴影 pass 的成对边界；cancel 会截断同目标 RETURN cleanup，异地 Invoker 又脱离 RenderManager 外层 entity scope。
- **全局或跨 pass 队列**：无法证明捕获项属于哪个 host/pass，可能把 shadow 标签泄漏到主 pass、重复回放或永久滞留。
- **`@Redirect` 最终调用点**：会独占 INVOKE，无法像 `@WrapOperation` 一样保留其他可组合 wrapper 的原调用链。
- **拦截所有 `Render.func_147906_a` 或宣称接管全部标签**：范围会扩到非玩家、潜行直绘和第三方调用，超过当前可证明边界。
- **对任意 Angelica 版本直接链接或反射猜测 ABI**：phase 与 captured state 契约没有版本保证；未知版本必须即时降级，不能以“可能兼容”换取 shader 状态污染风险。
- **在非 `NONE` phase 强行嵌套 `ENTITIES`**：会破坏 foreign shader lifecycle；异常帧丢弃本批次比污染后续渲染更安全。

## 影响与证据

- `8d55cf4570a373e92e28b011b54b65a55821a3c5`：实现线程局部 pass scope、FIFO、三个通用 Mixin、Angelica `2.1.50` 可选 guard、加载矩阵与测试源码。
- `efc0d755d4c51e9bd44d09d49470567613e324fa`：为每项捕获并恢复原调用点 X/Y，批次成对开关 lightmap。
- `f48a4e4668ee7350202e19a57ef6da360df603b3`：保存 post-host X/Y，并以嵌套 `finally` 恢复坐标后关闭 lightmap。
- 最终组合独立静态复审未发现 P0/P1/P2，A1-A9 静态满足。仓内已有 coordinator、Angelica guard、加载矩阵和源码契约测试，但测试源码尚未运行。
- CI、编译、JUnit、发布制品、dedicated server、Angelica shader/shadow 与游戏内标签矩阵均无本次实证，当前状态为 `INCOMPLETE`。

## 演进

- 2026-07-25：由已回退的 world-last detached 方案转为 pass-scoped 调用点包装；以精确 Angelica guard、lightmap 双层恢复和未知环境即时降级限定兼容边界。
