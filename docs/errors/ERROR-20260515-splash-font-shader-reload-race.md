# 2026-05-15 Splash 字体 shader 重载竞态

## 错误现象

- 客户端启动阶段崩溃，崩溃报告显示 `SplashProgress` 的 Splash 线程抛出异常。
- 根因栈为 `club.heiqi.uilib.gl.shader.ShaderProgramSupport.linkAndValidateProgram` 抛出 `IllegalStateException: 字体着色器链接失败`。
- 触发路径是 Angelica 在 SplashProgress 内存条中调用原版 `FontRenderer.drawString`，被 UILib 的 `MixinFontRenderer` 接管后进入 `DefaultFontRendererAdapter`。

## 触发场景

- `FontConfig.replaceOrigin=true`。
- Splash 加载线程正在绘制文字，同时客户端主线程执行 `FontRenderer.onResourceManagerReload`，触发 UILib 字体系统 `reload()`。
- `reload()` 会关闭并重建 shader、批渲染器、字符页和调度器；Splash 线程可能同时在 `drawString` 中使用同一套运行时对象。

## 根本原因

- 字体资源重载与 `drawString` 没有共享同一把运行时锁，导致 Splash 线程和客户端主线程并发访问字体 GL 资源。
- shader 初始化失败后 `FontShaderProgram.initialized` 会停留在已初始化状态，后续调用可能继续使用半初始化状态。
- Mixin 接管路径没有保护运行时异常，字体管线一次失败会直接把原版启动线程带崩。

## 修复方案

- `DefaultFontRendererAdapter` 在进入字体运行时绘制和测量时同步锁定 `FontService` 实例，与 `FontService.reload()` 的同步边界对齐。
- `FontShaderProgram.initialize()` 在失败时关闭已创建资源并把初始化状态回滚为 false。
- `MixinFontRenderer` 对 UILib 字体管线异常做单次日志记录并放行原版 `FontRenderer`，避免启动线程因自定义字体失败直接崩溃。

## 预防措施

- 不要用“非客户端主线程回落原版”作为默认修复，因为 SplashProgress 里仍有自定义字体渲染需求。
- 字体 GL 资源、批渲染器、字符页和 shader 的重载/绘制必须处于同一运行时互斥边界内。
- 字符页首次 `glTexImage2D` 不能依赖驱动对未初始化纹理内容的默认值；若后续会生成 mipmap，整张纹理必须先显式填充透明像素，否则新上传字形周围会把旧显存内容混进低层 mip，表现为纯黑块或脏边。
- 早期启动线程、Splash 线程、资源重载线程相关问题需要优先查 `run/client/logs/fml-client-latest.log` 中 `Splash thread Exception` 的上下文，而不是只看最终 crash report 的 `SplashProgress.finish` 包装异常。
