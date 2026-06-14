# 决策：宿主级背景模糊全局默认关闭并修复无条件全屏快照

## 背景

打开 `/qzuilib test` MODCFG 现代配置模板 demo（`ModernConfigTemplateScreen`）后游戏 FPS 明显偏低。此前已针对配置页**内容构建**做过系统性优化（见 `DECISION-20260614-modern-config-performance-optimization.md`：防抖、增量索引、差量列表、分批构建、延迟加载等），但那些优化只解决“打开瞬间全量构建 DOM/Binding/索引”的卡顿，无法解释打开后**持续**的低 FPS。

经渲染链路逐帧排查确认：真正的持续帧开销在屏幕渲染框架层，与配置页内容无关。`BaseScreen.drawScreen` 每帧调用 `UiScreenHostSession.render()`，其中宿主级背景模糊 `UiHostBackgroundBlurRenderer` 每帧无条件执行：

- `captureCurrentFramebuffer`：`glCopyTexSubImage2D` 全分辨率拷贝整屏，且该调用在“是否启用模糊”判断之前，禁用时也照样拷贝；无降采样、无内容复用。
- `drawBlurredBackground`：`BLUR_SAMPLES` 9 个采样点各画一次全屏四边形，即每帧 9 次全屏绘制。

`BackdropBlurConfig.hostBackgroundBlurEnabled` 默认 `true`，配置页未覆盖策略，因此默认开启。叠加 `doesGuiPauseGame()=false`（背景游戏世界不暂停），构成每帧固定的大额 GPU 开销，是所有 `BaseScreen` 界面共有的 FPS 瓶颈，配置页只是把它暴露得最明显。

宿主级模糊还是个“裸”实现：`BackdropBlurConfig` 里的降采样、内容版本追踪、分离式模糊等优化设施只服务元素级 `backdrop-filter`，宿主级一项都没用上。

## 候选方案

1. 仅在配置页 override `getBackdropBlurPolicy()` 关闭宿主模糊：改动最小，但只解决配置页，其它界面仍有同样开销。
2. 给宿主级模糊补降采样 + 内容复用：保留视觉效果并大幅降开销，但要给 `UiHostBackgroundBlurRenderer` 新增离屏降采样链路，改动和风险较大。
3. 把全局 `hostBackgroundBlurEnabled` 默认改为关闭：影响所有 `BaseScreen`，以性能为默认基线，想要模糊的页面用页面级策略显式开启。

## 最终选择

采用方案 3（用户确认）：

- `BackdropBlurConfig.hostBackgroundBlurEnabled` 字段默认与 `resetToDefaults()` 均改为 `false`。
- `applyPerformancePreset()` / `applyQualityPreset()` 显式置 `true`，避免“质量/性能预设却无模糊”的隐蔽不一致。
- 顺带修复无条件全屏快照 bug：`UiHostBackgroundBlurRenderer.captureCurrentFramebuffer` 增加 `BackdropBlurPolicy` 参数并在启用判断不通过时直接 return，与 `drawBlurredBackground` 的启用判断对称；`UiScreenHostSession.render` 提前解析页面策略再传入。

## 选择原因

- 性能优先作为默认基线更合理：宿主级背景模糊是纯装饰，却是每帧固定大额开销；配置页等功能界面更重清晰可读，而非背景模糊。
- 不违背 `DECISION-20260613-page-scoped-backdrop-blur-policy.md`，而是与之协同：该决策建立“全局默认 → 页面策略 → 运行时覆盖 → 元素样式”的继承链并强调页面不应去改全局单例；本次只调整这条链的**基线默认值**，想要模糊的页面继续用页面级 `BackdropBlurPolicy.quality()/performance()/withHostBackgroundBlurEnabled(true)` 显式开启，不受默认关闭影响。
- 修复 capture 无条件快照让“禁用即零开销”真正成立，也补齐了 page-scoped 决策“渲染链路各步骤都按有效策略解析”的精神（原先只有 draw 判断、capture 漏判）。
- 与既有配置页内容优化正交互补：内容优化解决初始构建卡顿，本次解决屏幕级每帧固定开销。

## 影响范围

- `BackdropBlurConfig`：字段默认值、`resetToDefaults()`、`applyPerformancePreset()`、`applyQualityPreset()`。
- `UiHostBackgroundBlurRenderer.captureCurrentFramebuffer`：新增 `BackdropBlurPolicy` 参数与启用判断（package-private，唯一调用点已同步）。
- `UiScreenHostSession.render`：提前解析页面策略并传入 capture。
- 所有未显式开启宿主模糊的 `BaseScreen` 界面：不再有每帧全屏快照与 9 次全屏模糊绘制开销，背景退化为普通半透明遮罩。
- 元素级 `backdrop-filter` 不受影响：本次只改宿主级默认开关。
- 测试：`BackdropBlurPolicyTest` 新增默认关闭、显式策略保留、预设开启三项断言。

## 后续注意事项

- 需要宿主级背景模糊视觉的页面：override `getBackdropBlurPolicy()` 返回 `quality()`/`performance()` 或 `inheritGlobal().withHostBackgroundBlurEnabled(true)`；或在初始化时 `BackdropBlurConfig.getInstance().setHostBackgroundBlurEnabled(true)`。
- 若未来要“默认关但保留轻量模糊”，可重启方案 2（宿主级降采样 + 内容复用），与本决策不冲突。
- 本次在无 JDK 的协作沙箱内完成，未实跑 `compileJava` 与测试；已通过完整 diff review、静态一致性核查和独立子 agent 审查。落地到本机后应按 AGENTS.md 补跑 `git diff --check`、`compileJava` 与 `BackdropBlurPolicyTest`（及 render/screen 相关回归）确认。
