# 字体系统 shutdown hook 非渲染线程释放 GL 资源崩溃

## 错误现象

- 游戏关闭阶段出现 JVM fatal error，不是普通 Java 异常。
- `run\client\logs\latest.log` 在 `07:28:09` 记录 `Stopping!`，同一时间生成 `run\client\hs_err_pid31308.log`。
- `hs_err_pid31308.log` 显示 `EXCEPTION_ACCESS_VIOLATION (0xc0000005)`，problematic frame 为 `lwjgl_opengl.dll+0x1096a`。

## 触发场景

- 使用 `runClient21` 进入游戏。
- 游戏正常关闭时，JVM 执行 `QzUiLibShutdown` shutdown hook。
- shutdown hook 调用 `FontService.shutdown()`，进而释放字体批渲染器、VAO / VBO / shader 等 GL 资源。

## 根本原因

- `QzUiLibShutdown` 是 JVM shutdown hook 线程，不是持有 OpenGL context 的 Minecraft 客户端渲染线程。
- `FontService.shutdown()` 无条件调用 `clearRenderResources()`，最终进入 `FontRenderTool.dispose()` 的 `GL30.glDeleteVertexArrays(...)`。
- LWJGL/Angelica 的 GL 删除调用在无有效 GL 上下文或错误线程中进入 native 层，触发 `EXCEPTION_ACCESS_VIOLATION`。

关键栈：

```text
JavaThread "QzUiLibShutdown"
org.lwjgl.opengl.GL30C.glDeleteVertexArrays
com.gtnewhorizons.angelica.glsm.GLStateManager.glDeleteVertexArrays
club.heiqi.uilib.font.render.FontRenderTool.dispose
club.heiqi.uilib.font.render.FontBatchRenderer.dispose
club.heiqi.uilib.font.FontService.clearRenderResources
club.heiqi.uilib.font.FontService.shutdown
club.heiqi.uilib.ClientProxy.onJvmShutdown
```

## 修复方案

- `FontService.shutdown()` 继续停止字体生成调度器，避免 worker 在 JVM 退出阶段持有运行时引用。
- GL 资源释放新增线程归属判断：只有当前线程是已捕获的渲染线程，或尚未捕获渲染线程但线程名是 `Client thread` 时，才执行 `clearRenderResources()`。
- 非渲染线程关停时跳过 GL 释放，并记录一次性 warn；底层 GL context 销毁由客户端退出流程兜底。
- 增加 `FontServiceLayoutRuntimeSmokeTest.shouldSkipGlResourceReleaseFromNonRenderThreadShutdown`，覆盖非渲染线程关停不会调用 shader/GL 资源关闭路径。

## 预防措施

- JVM shutdown hook、worker、下载线程、网络线程等非渲染线程不得直接调用 OpenGL 创建、删除或重载资源。
- 字体批渲染器、字符页纹理、shader program、VAO / VBO 的释放必须和创建/重载一样受渲染线程边界保护。
- 分析关闭阶段崩溃时，优先同时查看 `latest.log` 的 `Stopping!` 时间点和最新 `hs_err_pid*.log` 的当前线程与 Java frames。
- native 崩溃不会被 `catch (RuntimeException)` 捕获，不能依赖外层 try/catch 作为 GL 线程错误的保护手段。
