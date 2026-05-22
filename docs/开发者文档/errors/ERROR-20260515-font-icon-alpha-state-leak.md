# 字体接管后图标透明状态污染

## 错误现象

- 开启 `FontConfig.replaceOrigin` 后，服务端列表、tooltip 或物品 UI 中的部分图标出现白底、黑底、变白、变黑等透明边缘异常。
- 异常并非所有图标稳定复现，常与文本、tooltip、物品覆盖层、宿主图片绘制的先后顺序有关。

## 触发场景

- 原版或其他 Mod 在绘制图标、物品覆盖层、服务端列表项期间调用 `minecraft.fontRenderer`，被 UILib 字体管线接管。
- 图标绘制路径没有在主体绘制前完整重建 blend/alpha 状态，继承了上一个字体或 UI 阶段的 GL 状态。
- 字体或 UI 绘制到离屏 FBO 时使用普通 `SRC_ALPHA/ONE_MINUS_SRC_ALPHA` 混合，alpha 通道按源 alpha 再乘一次，和后续预乘 alpha 回贴假设不一致。

## 根本原因

- 字体与主 UI 准备状态只设置了颜色混合函数，没有用 `glBlendFuncSeparate` 单独维护 coverage alpha。
- `MinecraftHostImageRenderer.renderItemStack()` 只在物品 overlay 前设置图片混合状态，物品主体可能吃到字体管线残留状态。
- `FontBatchRenderer.initialize()` 首次创建 VAO/VBO 时发生在字体 flush 状态保护边界外，可能把调用方绑定的 VAO/VBO 清到 0。
- 原版多人列表等固定管线界面会在文本之后继续绘制透明图标；早期修复曾尝试在替换原版 `FontRenderer` 后硬编码补齐 `GL_ALPHA_TEST`、blend 和白色顶点颜色，但这只能覆盖少数原版路径，在动态 GL 环境中会覆盖调用方真实状态。

## 修复方案

- 字体绘制、主 UI 绘制、主后置回放和 backdrop 固定管线混合统一使用 `glBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA)`。
- 宿主物品图标主体绘制前先调用图片混合状态初始化，避免继承前置阶段状态。
- 字体批渲染器首次初始化底层 GL 资源时使用 `FontRenderStateGuard` 包裹，恢复调用方 shader、纹理、VAO/VBO 与矩阵状态。
- `MixinFontRenderer` 不再硬编码模拟原版字体结束状态；字体适配器在字符渲染前建立 `FontRenderStateGuard` 边界，并在完成后恢复调用前真实 GL 状态。

## 预防措施

- 任何进入独立 FBO 并最终按预乘 alpha 回贴的绘制路径，都要明确设置 separate alpha 混合函数。
- 任何调用 Minecraft 原版物品、字体或第三方 renderer 的入口，都要在主体和 overlay 前分别建立稳定 GL 状态，或把完整调用生命周期放进状态保护边界，不能只依赖调用后硬编码某一套状态。
- 字体、shader、VAO、VBO 等 GL 资源的首次初始化也要视为会污染状态的渲染操作，必须包进状态保护边界。
- Mixin 替换原版渲染方法时，不能用一组固定 `glEnable`、blend 或颜色状态假定调用方后续需求；应优先保存并恢复进入字体绘制前的真实 GL 状态。
- 以后排查图标白底/黑底时，优先检查 `GL_ALPHA_TEST`、`GL_BLEND`、blend src/dst、`GL_COLOR_WRITEMASK`、`GL_CURRENT_PROGRAM`、active texture、texture binding 与 VAO/VBO。
