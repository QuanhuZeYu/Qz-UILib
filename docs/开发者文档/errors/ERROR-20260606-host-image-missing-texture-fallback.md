# 宿主图片缺失纹理回退到 Minecraft 紫黑 missing texture

## 错误现象

- `/qzuilib test` 的 `VIS-PAINT-007 / host image fallback` 样例中，缺失资源面板显示 Minecraft 默认紫黑 missing texture。
- 期望行为是缺失资源保留 UILib 元素自身底色或占位表现，不暴露 Minecraft 原生缺失贴图。

## 触发场景

- `background-image`、`img` 或宿主图片控件使用 `HostImageSource.texture(...)` 指向不存在的 `ResourceLocation`。
- `MinecraftHostImageRenderer` 直接调用 `TextureManager.bindTexture(texture)`。

## 根本原因

- Minecraft `TextureManager.bindTexture(...)` 在纹理对象缺失时会创建并加载 `SimpleTexture`。
- 加载失败后会把该 `ResourceLocation` 映射到 `TextureUtil.missingTexture`，因此 UILib 尚未决定 fallback 表现时，宿主层已经绘制了默认紫黑缺失纹理。
- 纯 JVM 回归测试最初直接构造 `MinecraftHostImageRenderer` 还会因为 eager 创建 `RenderItem` 触发 `Minecraft` 静态初始化，说明宿主渲染器构造阶段不应提前触碰 Minecraft 客户端对象。

## 修复方案

- 在 `MinecraftHostImageRenderer` 绑定 `TEXTURE` 源前，通过 `IResourceManager.getResource(...)` 预检资源是否存在。
- 资源缺失或读取失败时直接跳过贴图绘制，让调用方已绘制的背景色/占位自然保留。
- 检查资源可用性时显式关闭 `IResource.getInputStream()`，避免只做存在性检查也泄漏资源流。
- 将 `RenderItem` 从字段 eager 初始化改为物品渲染路径按需创建，避免纹理缺失测试触发 Minecraft/LWJGL 静态初始化。

## 预防措施

- 任何 UILib 自定义 fallback 语义都不要把缺失资源直接交给 Minecraft 默认绑定路径。
- 宿主资源检查必须发生在 `bindTexture` 前；否则 Minecraft 会缓存 missing texture 映射并污染后续同一资源位置的表现。
- 纯 JVM 测试不要构造会 eager 初始化 `Minecraft`、`RenderItem`、`GuiScreen` 或 GL 对象的类；必要时用窄接口注入可替代的检查器或渲染器。
- 修改宿主图片渲染器时至少运行 `MinecraftHostImageRendererTest` 和相关 `/qzuilib test` 页面断言测试。
