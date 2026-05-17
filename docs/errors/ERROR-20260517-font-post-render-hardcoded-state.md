# 字体渲染后硬编码 GL 状态

## 错误现象

- 开启原版字体接管后，字体绘制结束会强制启用纹理、alpha test、blend，并重置 blend 函数与顶点颜色。
- 在动态 GL 环境中，调用方进入字体绘制前可能处于自定义 shader、FBO、纹理单元、VAO/VBO、pixel store、裁剪或禁用 alpha/blend 的状态，硬编码收尾会破坏这些状态。

## 触发场景

- 原版或其他 Mod 在自定义 GL 状态下调用 `FontRenderer.drawString(...)`、`drawStringWithShadow(...)` 或 `drawSplitString(...)`。
- UILib 文档 deferred text scope 内发生字形页纹理创建、draw-stage 上传和批次 flush，且调用方依赖 scope 外层的 GL 状态继续绘制。

## 根本原因

- `MixinFontRenderer` 成功接管原版字体绘制后用 `qzuilib$applyVanillaFontPostRenderState()` 写死一组结束状态，试图模拟旧版原版字体调用的副作用。
- 字体状态保护边界主要包住最终 flush，未覆盖 draw-stage 字形上传和字符页纹理首次创建，仍可能在 flush 前污染调用方绑定状态或 pixel store。
- 单例 `FontRenderStateGuard` 只保存一份字段状态，不适合作为嵌套边界使用。

## 修复方案

- 移除 `MixinFontRenderer` 的硬编码原版收尾状态。
- 在 `DefaultFontRendererAdapter` 的非 deferred 绘制入口进入字符渲染前 `push` GL 状态，完成后 `pop` 恢复真实调用前状态。
- 在 deferred text scope 开始时建立状态保护边界，直到 scope 结束后恢复，覆盖字形上传、纹理创建和 flush。
- `FontRenderStateGuard` 改为栈式保存状态，并额外保存/恢复 client pixel store、active texture、shader program、texture binding、VAO/VBO、viewport 和固定管线矩阵栈。

## 预防措施

- 字体绘制不能靠硬编码状态“猜测原版结束态”，必须保存并恢复调用前真实 GL 状态。
- 字符页纹理创建、字形上传、shader/VAO/VBO 初始化和批次 flush 都属于会污染 GL 的渲染生命周期，保护边界必须覆盖完整链路。
- 后续新增 deferred 或跨命令字体批处理时，要确认 scope 能正确嵌套，且异常路径一定恢复状态并清理待提交批次。
