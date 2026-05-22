# ERROR-20260508-jvm-test-guiscreen-static-init

## 错误现象

- 在纯 JVM 单测中直接 `new` 继承 `GuiScreen` 的页面类时，测试在类初始化阶段失败。
- 典型异常为 `NoSuchMethodError: org.lwjgl.opengl.DisplayMode.<init>(int, int)`，随后触发 `GuiScreen` / `Minecraft` 静态初始化链崩溃。

## 触发场景

- 为 `BaseScreen` / `GuiScreen` 派生页面补单测时，直接在 `src/test/java` 中实例化页面对象。
- 当前测试运行环境未完整提供 Minecraft 客户端与 LWJGL 运行时兼容静态依赖。

## 根本原因

- `GuiScreen` 的静态初始化会继续触发 `Minecraft`、`RenderItem` 等客户端运行时类加载。
- 这条链路依赖真实客户端渲染环境，不适合放在纯 JVM 文本/逻辑测试中直接执行。

## 修复方案

- 不在纯 JVM 单测中直接实例化 `GuiScreen` 派生类。
- 需要验证页面作者逻辑时，优先下沉到不依赖 `GuiScreen` 的文档构建器、Presenter、控制器、状态机或纯数据转换层。
- 需要验证真实页面交互时，使用游戏内人工验证或能提供兼容客户端运行时的集成环境。

## 预防措施

- 新增页面前先判断测试目标是否会触发 `GuiScreen` / `Minecraft` 静态初始化。
- 如果类继承 `BaseScreen`、`GuiScreen` 或间接依赖 `Minecraft.getMinecraft()`，默认不要写纯 JVM 实例化测试。
- 需要长期验证的逻辑应尽量抽到不依赖宿主静态初始化的可测层。
