# ERROR-20260519-lwjgl3ify-runtime-compile-classpath-gap

## 错误现象

- 新增宿主系统光标映射时，源码里直接 `import org.lwjgl.sdl.SDLMouse` 和 `me.eigenraven.lwjgl3ify.client.MainThreadExec` 后，`compileJava` 立即失败。
- 编译器提示找不到 `org.lwjgl.sdl` 包、找不到 `MainThreadExec`，以及 `org.lwjglx.Sys` / `Mouse` 的方法签名与运行时反编译看到的不一致。

## 触发场景

- 在 GTNH / Minecraft 1.7.10 工程里接入 `lwjgl3ify` 的 SDL3 / 主线程桥接能力。
- 通过 `javap` 可以在 `lwjgl3ify` jar 里看到目标类和方法，因此容易误以为源码编译期也能直接静态依赖这些 API。

## 根本原因

- 当前工程的**运行时类路径**和**源码编译类路径**并不完全一致。
- `lwjgl3ify` 提供的一部分桥接类与扩展方法在最终运行环境可见，但源码编译阶段实际参与解析的仍可能是旧签名或被其他依赖遮蔽的类型。
- 直接把这类运行时扩展 API 写成静态 import，会在 `compileJava` 阶段炸掉，或者落入方法签名不一致的问题。

## 修复方案

- 把系统光标宿主实现改为**反射桥接**：运行时按类名加载 `SDLMouse`、`MainThreadExec`、`org.lwjglx.input.Mouse` 等目标类型，再调用 `SDL_CreateSystemCursor`、`SDL_SetCursor`、`setNativeCursor(null)` 等方法。
- 仅保留对稳定编译可见类型（如 `Display`）的直接依赖，避免源码层静态耦合到 `lwjgl3ify` 的运行时扩展实现。

## 预防措施

- 在这个仓库里接入 `lwjgl3ify`、SDL3、主线程桥接或类似宿主扩展能力前，不要只看运行时 jar 反编译结果就直接写静态 import。
- 先用一次最小 `compileJava` 验证目标类型和方法在**源码编译期**是否真实可见，再决定是静态依赖还是反射桥接。
- 遇到 `javap` 能看到、但 `compileJava` 找不到的类或方法时，优先按“编译期/运行时类路径存在差异”排查，而不是反复猜测单个 import 或包名。
