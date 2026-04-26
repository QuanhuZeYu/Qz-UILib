# ERROR-20260426-ui-rounded-fill-cull-state

## 错误现象

- 游戏内 `HTML-like Smoke` 页面可以打开，rounded border 正常可见，但多个本应实心的 HTML-like 色块填充几乎不可见。
- 截图表现为：主 smoke 容器、header、flex 子项主要剩下蓝色/青色线框，紫色、黄色、绿色、红色填充块不明显或不可见。

## 触发场景

- 在 Minecraft 实机渲染链中打开诊断菜单页，再进入 `HTML-like Smoke` 子页。
- HTML-like smoke 文档大量使用带 `border-radius` 的背景填充，最终由 `UiRenderContext.fillRoundedRect(...)` 通过 OpenGL 面片绘制。

## 根本原因

- `UiScreenHostSession` 在绘制 widget 树前没有统一清理宿主世界渲染遗留的 3D OpenGL 状态。
- 当 `GL_CULL_FACE` 等状态从世界渲染泄露到 UI FBO 绘制阶段时，圆角填充使用的 `GL_TRIANGLE_FAN` 面片可能被背面剔除，而 `GL_LINE_LOOP` 边框仍能显示，导致“只见线框、不见填充”。

## 修复方案

- 在进入主 UI widget 树渲染前，统一准备稳定 2D 状态：关闭 depth、cull、alpha test、lighting，开启常规 alpha blend，并重置颜色。
- 同时缩短 smoke 页说明文案，避免中文句号单独换行影响截图判断。

## 预防措施

- 新增或修改底层 UI 渲染链时，不能假设 Minecraft 宿主已处于 GUI 友好的 OpenGL 状态。
- 任何进入 widget 树或 HTML-like paint replay 的入口，都应显式建立自己的 2D GL 状态边界。
- 游戏内 smoke 截图如果出现“边框可见但填充不可见”，优先检查 `GL_CULL_FACE`、`GL_DEPTH_TEST`、`GL_ALPHA_TEST` 等状态泄露。
