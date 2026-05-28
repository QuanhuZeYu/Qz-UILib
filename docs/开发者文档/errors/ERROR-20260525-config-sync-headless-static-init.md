# 配置同步测试误触 GuiScreen 静态初始化

## 错误现象

为配置同步新增纯 JVM 测试后，执行 `ConfigSyncModelsTest` / `ConfigTemplateRemoteSessionTest` 时失败，堆栈表现为：

- `NoSuchMethodError: org.lwjgl.opengl.DisplayMode.<init>(int, int)`
- `NoClassDefFoundError: Could not initialize class club.heiqi.uilib.config.ForgeConfigTemplateScreen`

## 触发场景

- 在纯 JVM 环境测试配置同步模型、配置定义快照或服务端配置会话。
- 业务逻辑代码为了复用字符串工具方法，间接引用了 `ForgeConfigTemplateScreen.normalizeInlineText(...)`。
- `ForgeConfigTemplateScreen` 继承 `BaseScreen` / `GuiScreen`，类加载时连带触发 Minecraft 客户端静态初始化。

## 根本原因

- 纯数据层 `ConfigSyncModels` 不应该依赖任何 `GuiScreen` 相关类型。
- 即使只调用一个静态工具方法，只要方法定义在 `GuiScreen` 相关类里，JVM 仍会先初始化该类。
- 在当前测试环境下，Minecraft/LWJGL 的客户端静态初始化不成立，因此测试阶段直接崩溃。

## 修复方案

- 将 `ConfigSyncModels` 中的文本归一化逻辑改为本类内私有实现。
- 不再从 `ForgeConfigTemplateScreen` 借用 `normalizeInlineText(...)`。
- 保持配置同步模型、配置目标、服务端配置会话三层都只依赖 `Configuration` / `Property` 等纯数据类型。

## 预防措施

- 纯 JVM 可复用逻辑不要依赖 `GuiScreen`、`BaseScreen`、`Minecraft`、渲染器或控件类上的静态方法。
- 如需共享无状态工具，优先下沉到独立的 headless 友好工具类。
- 新增纯 JVM 测试前，先检查被测类的 import 和静态调用，确认不会触发客户端类初始化。
