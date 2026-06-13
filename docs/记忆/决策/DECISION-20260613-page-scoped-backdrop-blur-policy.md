# 决策：页面级背景模糊策略

## 背景

背景模糊原先主要通过 `BackdropBlurConfig` 全局单例控制。业务页面需要一行关闭背景模糊、使用性能/质量/兼容预设，并能在页面打开后调整本页面效果；如果直接修改全局配置，会污染其它文档 screen、HUD 或后续页面。

## 候选方案

- 继续让页面作者修改 `BackdropBlurConfig` 全局单例，调用简单但会产生页面间串扰。
- 在 `UiDocumentScreens.DocumentScreenEnvironment` 中携带页面策略，并给 `createDocumentScreen(...)` 增加预设/策略快捷重载。
- 在 `UiDocument` 上提供页面级运行时控制器，事件回调和构建回调都能访问。

## 最终选择

采用页面级 `BackdropBlurPolicy` + `BackdropBlurPreset` + `UiDocument.getBackdropBlurController()`。

策略按 `BackdropBlurConfig` 具体字段全局默认 -> `DocumentScreenEnvironment` 页面策略 -> 页面运行时覆盖 -> 元素样式解析。`BackdropBlurPolicy.disabled()` 只设置页面级总开关，会强禁用当前页面宿主级背景模糊和元素级 `backdrop-filter`，但不把底层 shader/fallback 字段写成页面覆盖值。

## 选择原因

- 页面作者可以用 `BackdropBlurPreset.DISABLED` 一行关闭当前页面背景模糊。
- 不暴露 OpenGL、FBO、shader 或 Minecraft `GuiScreen` 生命周期。
- 运行时控制器挂在 `UiDocument`，页面构建与事件回调都能访问，且只影响所属文档。
- 渲染链路从页面当前有效策略读取开关和半径上限，不再把全局配置作为唯一入口。
- `BackdropBlurConfig` 没有元素级与宿主级共享的全局总开关；`BackdropBlurPolicy.enabled` 只表示页面级强制开关，未声明时具体字段仍继承全局配置。

## 影响范围

- `UiDocumentScreens.DocumentScreenEnvironment` 新增页面级策略字段与 `withBackdropBlurPolicy(...)`。
- `UiDocumentScreens.createDocumentScreen(...)` 新增 `BackdropBlurPreset` / `BackdropBlurPolicy` 快捷重载。
- `UiDocument` 新增 `getBackdropBlurController()`；内部宿主在构建前注入基础策略。
- `DocumentEffectChain`、`DocumentPaintEngine`、`UiBackdropFilterRenderer` 和 `UiHostBackgroundBlurRenderer` 会按页面有效策略解析背景模糊。

## 后续注意事项

- 远程页面和 HUD 是否允许服务端控制背景模糊仍需单独评估，当前决策只覆盖本地文档 screen 的页面级策略。
- 若后续开放更多高级字段，应继续保持策略不可变和字段未声明可继承的语义。
