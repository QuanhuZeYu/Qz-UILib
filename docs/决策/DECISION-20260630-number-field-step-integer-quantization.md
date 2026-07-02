# DECISION-20260630 NumberFieldRenderer step=1.0 整数量化遗留

- 类型：已解决（方案 D：WidgetSpec 接口 + SliderSpec.step）
- 登记日期：2026-06-30
- 解决日期：2026-06-30
- 触发上下文：commit `82ff78c0` slider range 上界修复后的 Oracle C 复审

## 1. 现象（历史）

`NumberFieldRenderer.java:70` 渲染有 range 的 NUMBER 字段时，`SceneSlider.Props` 写死 `step(1.0)`：

```java
// 历史代码（已删除）
SceneSlider.Props props = SceneSlider.Props.builder(numValue)
        .min(min).max(max).step(1.0)
        .onChange((value, committing) -> adapter.onFieldEdit(path, Double.valueOf(value)))
        .build();
```

`step=1.0` 让 slider 只能停在整数刻度，无法选小数值。

## 2. 受影响字段（已全部处理）

`QzUiLibModernSchema` 中以下字段已按方案 D 声明 widget：

| 字段 | range | 默认值 | 方案 D 声明 |
|---|---|---|---|
| `fontSystem.lerpMode` | (0,3) | 3.0 | `.slider()` |
| `fontSystem.aaMode` | (1,2) | 2.0 | `.slider()` |
| `fontSystem.smoothRangeMin` | (0,1) | 0.0 | `.slider(0.1)` |
| `fontSystem.smoothRangeMax` | (0,1) | 0.9 | `.slider(0.1)` |
| `fontSystem.drawStageUploadIntervalMs` | (0,1000) | 20.0 | 不写 `.slider()`（input） |
| `fontSystem.drawStageUploadLimitPerSecond` | (0,1000) | 20.0 | 不写 `.slider()`（input） |
| `fontSystem.drawStageUploadBatchSize` | (0,256) | 2.0 | 不写 `.slider()`（input） |
| `fontSystem.aaStrength` | (1,120) | 12.0 | `.slider()` |
| `fontSizeSetting.awtCharSize` | (8,256) | 64.0 | `.slider()` |
| `fontSizeSetting.charSize` | (1,72) | 9.0 | `.slider(0.5)` 半磅步进 |

## 3. 解决方案（方案 D：WidgetSpec 密封接口 + SliderSpec.step）

### 3.1 设计

新增 `WidgetSpec` 接口（原计划 Java 17 密封接口，因 Jabel desugar 不支持 `sealed` 关键字，退为普通接口 + record 实现，语义等价）：

- `WidgetSpec`（`club.heiqi.config.schema`）：widget 声明载体
- `SliderSpec(double step)`：record，`step<=0` 表示连续不量化，`step<0` 构造抛异常
- `InputSpec`：record，单例 `INSTANCE`

`FieldSpec` record 新增第 7 字段 `WidgetSpec widget`（null = 默认 input）。
`FieldSpec.Builder` 新增 3 个便捷方法：

- `.slider()` → `SliderSpec.continuous()`（step=0）
- `.slider(double step)` → `new SliderSpec(step)`
- `.input()` → `InputSpec.INSTANCE`

### 3.2 渲染层分发

`NumberFieldRenderer.render` 改为按 `spec.widget()` 分发：

```java
WidgetSpec w = spec.widget();
if (w instanceof SliderSpec) {
    SliderSpec s = (SliderSpec) w;
    return renderSlider(rt, spec, adapter, min, max, s.step());
}
return renderTextInput(rt, spec, adapter);
```

- **widget=null 或 InputSpec → input**（有 range 不再自动 slider）
- **widget=SliderSpec → slider**，step 由 `SliderSpec.step()` 透传

### 3.3 用户拍板决策

- widget 声明载体：WidgetSpec 接口（SliderSpec/InputSpec 子类）
- widget=null 默认：input（有 range 不再自动 slider）
- drawStage* 三字段(0,1000)：input（不写 `.slider()`）

### 3.4 附带优化

`renderTextInput` 的整数格式化（原 :123-136 重复逻辑）改为复用 `formatReadout`，消除重复。

## 4. Jabel 限制说明

原 Oracle 规划用 `sealed interface ... permits ...`，但 Jabel desugar 不支持 `sealed` 关键字
（编译报 `-source 8 中不支持 密封类`，即使其他 Java 14+ 语法如 record 可正常 desugar）。
故 `WidgetSpec` 退为普通接口，子类仍为 record implements。
分发用 `instanceof SliderSpec` 模式匹配，语义与密封接口等价，仅失去"编译期穷尽性检查"，
对本项目影响可忽略（实现类只有两个且稳定）。

## 5. 测试覆盖

- `FieldSpecWidgetTest`：`.slider()` / `.slider(0.1)` / `.input()` / 默认 null / 负 step 抛异常
- `NumberFieldRendererWidgetTest`：widget=null+range → input、InputSpec+range → input、
  SliderSpec → slider、step 透传渲染不崩、无 range slider 渲染不崩
- `UiSchemaFactory.serverSchema()` 的 `server.port` 补 `.slider()`，保持 `NumberFieldRendererTest`
  的 slider 用例语义不变
- 全量 2565 测试通过，0 失败 0 错误

## 6. 关联文档

- 修复 commit：`82ff78c0`（range 上界，历史）
- 本次解决 commit：见 git log（方案 D 实施）
