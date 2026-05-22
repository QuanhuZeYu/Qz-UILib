# ERROR-20260513-jvm-test-font-guard-bufferutils

## 错误现象

- 运行纯 JVM 测试时，多个 `widget.render(...)` 相关用例同时失败。
- 首个失败堆栈显示 `java.lang.NoClassDefFoundError: org/lwjgl/BufferUtils`。
- 触发链路为 `UiRenderContext -> DefaultFontRendererAdapter -> FontRenderStateGuard`。

## 触发场景

- 测试侧使用 recording render context，但其父类 `UiRenderContext` 构造时仍会初始化默认字体适配器。
- `FontRenderStateGuard` 在实例字段初始化阶段直接调用 `org.lwjgl.BufferUtils.createIntBuffer(...)`。
- 当前纯 JVM 测试 worker 类路径没有提供这条 LWJGL 工具类。

## 根本原因

- 该类只为了分配一个 direct `IntBuffer`，却把 buffer 创建绑定在 LWJGL 工具类上。
- 结果是即使测试并未真正进入 OpenGL 状态保护逻辑，只要构造默认字体适配器，就会在类初始化阶段提前炸掉。

## 修复方案

- 将 `FontRenderStateGuard` 的 `viewportBuffer` 创建改为 JDK 自带的 `ByteBuffer.allocateDirect(...).order(...).asIntBuffer()`。
- 保留 direct buffer 与 native order，避免改变实际运行时的 OpenGL 数据布局。

## 预防措施

- 纯状态对象如果只需要 direct buffer，不要默认依赖 LWJGL `BufferUtils`。
- 尤其是会在构造器、静态字段或实例字段初始化阶段运行的代码，要优先使用 JDK 可用实现，避免把测试环境提前拉进图形运行时依赖。
- 新增渲染相关基础设施时，要检查“构造即触发”的类初始化路径是否会影响纯 JVM 测试。
