# 2026-05-09 页面渲染上下文遗漏 runtimeAdapters

## 错误现象

- 背包页槽位占用统计、tooltip 和点击都正常，但槽位里的 Minecraft 物品图标不显示。
- 鼠标携带物品层同样不显示，表现为宿主图片能力整体失效。

## 触发场景

- `inventory_overview` 等 HTML-like 页面改为通过 `DocumentHostImageControl` 渲染 Minecraft 物品图标后，在真实屏幕宿主链路中打开页面。
- 页面控制器和文档作用域已经声明 `UiRuntimeAdapters.minecraftDefaults()`，但最终渲染上下文创建时漏传该依赖。

## 根本原因

- `UiScreenHostSession.render(...)` 创建 `UiRenderContext` 时误用了会默认注入 `UiRuntimeAdapters.empty()` 的构造重载。
- `DocumentHostImageControl` 的宿主图片绘制依赖 `UiRenderContext.getRuntimeAdapters().getHostImageRenderer()`；当适配器被静默置空时，`drawHostImage(...)` 会直接返回。
- 因为页面数据、布局、hover 和 click 逻辑都不依赖宿主图片渲染器，所以会出现“背包状态正常、只有物品图标消失”的假象。

## 修复方案

- `UiScreenHostSession` 显式把 `screen.getRuntimeAdapters()` 传入 `UiRenderContext`。
- 增加回归测试，验证宿主会话创建渲染上下文时不会丢失显式运行时适配器。

## 预防措施

- 涉及宿主能力的页面问题如果表现为“DOM/数据正常、贴图缺失”，优先检查 `UiRuntimeAdapters` 是否沿宿主链路完整透传到 `UiRenderContext`。
- 提供多个构造重载的运行时上下文类型时，调用方应尽量集中到单一工厂或辅助方法，避免误用默认空依赖重载。
