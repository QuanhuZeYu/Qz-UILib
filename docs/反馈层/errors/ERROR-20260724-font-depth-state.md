# 全局字体替换覆盖世界文字深度状态

## 错误现象

启用全局原版字体替换后，告示牌等世界空间文字不再受模型深度遮挡，从模型背面仍可透出；UILib screen/HUD 的二维文字本身没有同类诉求。

## 触发场景

`FontConfig.replaceOrigin=true` 时，原版 `FontRenderer.drawString` 的世界文字调用也进入 UILib 字体批处理。调用方已开启 depth test 并准备好世界深度缓冲，但字体批次 flush 会执行共享文本状态准备。

## 根本原因

`FontRenderStateSupport.prepareTextRenderState` 无条件关闭 depth test，把本应由世界/UI 调用阶段持有的状态错误下沉到全局字体替换层。既有 `FontRenderStateGuard` 虽会在调用结束后恢复 GL 状态，却无法让本次已关闭 depth test 的文字重新参与深度测试。screen/HUD 入口原本已由 `UiHostRenderSupport.prepareMainUiRenderState` 显式建立无 depth test 的二维状态，因此字体层兜底既重复又破坏世界语义。

## 修复方案

字体共享准备只保留纹理、混合、颜色、alpha、cull 与 lighting 等自身拥有的二维文本状态，不再启用、禁用或改写 depth test、depth mask、depth func。世界文字由字体层继承调用方深度状态；UILib screen/HUD 继续在 UI host 主阶段显式关闭 depth test。新增纯源码契约测试，同时锁定字体准备方法的深度禁区、UI host 的显式关闭及三个主 UI 入口的调用顺序。

## 预防措施

- 共享渲染 helper 只能改写自身明确拥有的 GL 状态；世界/UI 阶段语义必须留在各自 host 边界，禁止用全局字体替换层猜测调用阶段。
- 字体准备方法持续禁止出现 `GL_DEPTH_TEST`、`glDepthMask` 与 `glDepthFunc`；screen/HUD 新增主入口时必须先调用 UI host 状态准备再回放内容。
- 源码契约测试交 CI 执行；告示牌正面可见、背面受模型遮挡以及 screen/HUD 二维文字不受世界深度缓冲干扰，仍须用户真机验证。
