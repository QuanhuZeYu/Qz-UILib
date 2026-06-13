# UI 背景模糊开发者快捷配置规划

本文规划 `UiDocumentScreens` 调用层的背景模糊快捷配置方式。当前只定义 API 形状、配置级联语义和实现阶段，不修改源码实现。

## 目标

- 给页面作者提供一行式关闭背景模糊的入口，避免不需要磨砂背景的页面承担默认效果。
- 给需要模糊效果的页面作者提供低成本预设，例如性能优先、质量优先、兼容性优先。
- 保留高级用户细粒度调整能力，但不要求普通作者理解 shader、FBO、快照池或降采样细节。
- 让配置语义接近 CSS 级联：全局默认、页面级策略、元素级样式逐层覆盖。
- 支持运行时调整，页面打开后仍可改变本页面或全局背景模糊策略。

## 非目标

- 不把 OpenGL、FBO、ShaderProgram 或 Minecraft `GuiScreen` 生命周期暴露给页面作者。
- 不要求每个元素都能覆盖所有高级渲染参数；元素级仍优先表达视觉样式。
- 不把全局配置作为唯一入口，否则多个页面或不同作者需求会互相污染。
- 不在本阶段设计可视化配置页或持久化配置文件。

## 推荐组合

采用方案 A + C：

- A：扩展 `UiDocumentScreens.DocumentScreenEnvironment`，承载页面级 `BackdropBlurPolicy`。
- C：为 `UiDocumentScreens.createDocumentScreen(...)` 增加快捷重载，允许直接传入 `BackdropBlurPreset` 或 `BackdropBlurPolicy`。

该组合让简单场景保持一行调用，复杂场景仍可通过显式环境表达。

## 分级覆盖模型

背景模糊配置分为三层，后层覆盖前层：

1. 全局默认层：`BackdropBlurConfig`
2. 页面策略层：`BackdropBlurPolicy`，挂在 `DocumentScreenEnvironment`
3. 元素样式层：`UiStyleDeclaration` 的 `backdrop-blur-radius`、`backdrop-saturation`

### 覆盖规则

- `BackdropBlurConfig` 提供运行时默认值和全局高级参数。
- `BackdropBlurPolicy` 的字段允许为“未声明”，未声明时继承全局默认。
- `BackdropBlurPolicy.disabled()` 是强禁用，页面内元素即使声明 `backdrop-blur-radius` 也不触发 backdrop-filter 渲染。
- 元素级样式只决定元素自身视觉效果，不应修改全局或页面级配置。
- 高级渲染路径开关优先遵循页面策略；页面未声明时回落到全局配置。
- 若页面策略禁用宿主级背景模糊，仅影响页面壳层背景，不影响元素级 backdrop-filter，除非整体 `enabled=false`。

## API 形状

### BackdropBlurPreset

建议新增枚举：

```java
public enum BackdropBlurPreset {
    DEFAULT,
    DISABLED,
    PERFORMANCE,
    QUALITY,
    COMPATIBILITY
}
```

语义：

- `DEFAULT`：完全继承全局配置。
- `DISABLED`：禁用页面宿主级背景模糊与元素级 backdrop-filter。
- `PERFORMANCE`：降低模糊半径、采样点和快照压力。
- `QUALITY`：提高模糊半径上限和视觉质量，允许更高成本。
- `COMPATIBILITY`：优先固定管线或 tint fallback，降低 shader/FBO 兼容风险。

### BackdropBlurPolicy

建议新增不可变策略类：

```java
public final class BackdropBlurPolicy {
    public static BackdropBlurPolicy inheritGlobal();
    public static BackdropBlurPolicy disabled();
    public static BackdropBlurPolicy performance();
    public static BackdropBlurPolicy quality();
    public static BackdropBlurPolicy compatibility();

    public BackdropBlurPolicy withEnabled(boolean enabled);
    public BackdropBlurPolicy withHostBackgroundBlurEnabled(boolean enabled);
    public BackdropBlurPolicy withHostBackgroundBlurStrength(float strength);
    public BackdropBlurPolicy withMaxBlurRadius(int radius);
    public BackdropBlurPolicy withShaderEnabled(boolean enabled);
    public BackdropBlurPolicy withFixedPipelineEnabled(boolean enabled);
    public BackdropBlurPolicy withTintFallbackEnabled(boolean enabled);
}
```

设计约束：

- 类应不可变，`withXxx(...)` 返回新实例，避免页面间共享对象被运行时修改造成串扰。
- 每个字段内部区分“未声明”和“声明值”，便于继承全局配置。
- 不把所有 `BackdropBlurConfig` 高级字段都塞进快捷 API；可先开放作者最可能需要的字段。

### DocumentScreenEnvironment 扩展

建议新增字段和方法：

```java
private final BackdropBlurPolicy backdropBlurPolicy;

public DocumentScreenEnvironment withBackdropBlurPolicy(BackdropBlurPolicy policy);
public BackdropBlurPolicy getBackdropBlurPolicy();

public static DocumentScreenEnvironment withoutBackgroundBlur();
public static DocumentScreenEnvironment minecraftDefaults(BackdropBlurPreset preset);
```

默认值：

- `minecraftDefaults()` 使用 `BackdropBlurPolicy.inheritGlobal()`。
- `minecraftFormattedDefaults()` 同样继承全局配置。
- 新构造函数需要保持向后兼容，旧构造路径自动填充 `inheritGlobal()`。

### UiDocumentScreens 快捷重载

建议新增：

```java
public static GuiScreen createDocumentScreen(
        DocumentScreenContentBuilder contentBuilder,
        BackdropBlurPreset preset);

public static GuiScreen createDocumentScreen(
        DocumentScreenContentBuilder contentBuilder,
        BackdropBlurPolicy policy);

public static GuiScreen createDocumentScreen(
        DocumentScreenEnvironment environment,
        BackdropBlurPolicy policy,
        DocumentScreenContentBuilder contentBuilder);
```

调用示例：

```java
GuiScreen plainScreen = UiDocumentScreens.createDocumentScreen(
        document -> buildPlainPage(document),
        BackdropBlurPreset.DISABLED);
```

```java
GuiScreen glassScreen = UiDocumentScreens.createDocumentScreen(
        document -> buildGlassPage(document),
        BackdropBlurPolicy.quality()
                .withHostBackgroundBlurStrength(1.4F)
                .withMaxBlurRadius(64));
```

## 运行时可调模型

运行时可调需要避免直接修改全局单例影响其他页面。建议引入页面级运行态配置句柄。

### 页面级运行态状态

内部页面宿主保存一个 `BackdropBlurRuntimeState`：

```java
final class BackdropBlurRuntimeState {
    BackdropBlurPolicy basePolicy;
    BackdropBlurPolicy runtimeOverride;
    int version;
}
```

有效策略按顺序合并：

```text
BackdropBlurConfig 全局默认
  -> DocumentScreenEnvironment basePolicy
  -> BackdropBlurRuntimeState runtimeOverride
  -> 元素级 UiStyleDeclaration
```

### 对外运行时句柄

可在后续阶段给 `UiDocument` 或 screen provision 增加受控访问入口：

```java
public interface BackdropBlurController {
    BackdropBlurPolicy getPolicy();
    void setPolicy(BackdropBlurPolicy policy);
    void updatePolicy(UnaryOperator<BackdropBlurPolicy> updater);
    void resetPolicyOverride();
}
```

候选挂载位置：

- `UiDocument.getBackdropBlurController()`：作者在页面构建和事件回调中都能访问，最贴近页面作者心智。
- `DocumentScreenProvision` 扩展 lifecycle 回调：适合宿主代码控制，但普通页面作者不够顺手。

推荐优先采用 `UiDocument.getBackdropBlurController()`，内部再桥接到 screen runtime state。

### 运行时变更影响

策略变更后应触发：

- 当前 screen 重绘。
- backdrop 相关快照失效。
- 若影响元素级最大半径，应让下一帧重新解析 effect chain 或至少重新构建 paint command。
- 不必强制重建 DOM 树。

## 实现阶段

### 阶段一：页面级静态策略

- 新增 `BackdropBlurPreset`。
- 新增不可变 `BackdropBlurPolicy`。
- 扩展 `DocumentScreenEnvironment` 保存页面策略。
- 新增 `UiDocumentScreens.createDocumentScreen(...)` 快捷重载。
- 渲染链路查询“当前页面有效策略”，替代只读全局 `BackdropBlurConfig`。

### 阶段二：运行时页面覆盖

- 在内部 screen session 或 document runtime 上保存 `BackdropBlurRuntimeState`。
- 新增 `BackdropBlurController`。
- 策略变更触发 repaint、快照失效和 effect chain 重新解析。
- 添加最小 JVM 测试覆盖策略合并和运行时覆盖行为。

### 阶段三：文档与诊断

- 更新 `docs/使用文档/01-入门/最小文档页面.md`，加入禁用背景模糊示例。
- 更新稳定 API 清单，标注 `BackdropBlurPreset`、`BackdropBlurPolicy` 和快捷重载的稳定等级。
- 在 `/qzuilib test` 或诊断信息中显示当前页面有效策略、渲染路径和 fallback 原因。

## 风险与取舍

- 页面级策略如果直接写入全局 `BackdropBlurConfig`，会污染其他同时存在的 HUD 或远程页面；因此必须做页面作用域合并。
- `DISABLED` 需要明确是“全页面背景模糊禁用”，否则作者可能误以为只关闭宿主级背景。
- 元素级样式不应拥有 shader/fallback 等高级渲染路径控制，否则 CSS-like 模型会变得过重。
- 运行时可调若不触发 effect chain 失效，可能出现配置已变但旧 paint command 仍沿用旧半径的问题。
- 远程页面和 HUD 是否允许服务端控制背景模糊需要单独评估，首阶段可只支持本地文档 screen。

## 验收标准

- 旧调用 `UiDocumentScreens.createDocumentScreen(document -> ...)` 行为保持不变。
- 作者可以通过一行 `BackdropBlurPreset.DISABLED` 关闭单个页面的背景模糊。
- 作者可以通过 `BackdropBlurPolicy` 覆盖单个页面的宿主级强度和最大元素模糊半径。
- 页面 A 的策略变更不影响页面 B 或全局默认配置。
- 运行时调整策略后，下一帧视觉效果生效，不需要关闭重开页面。
- 禁用 shader 或固定管线时，诊断信息能说明实际走到的降级路径。
