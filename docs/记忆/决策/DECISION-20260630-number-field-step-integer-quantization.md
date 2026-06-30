# DECISION-20260630 NumberFieldRenderer step=1.0 整数量化遗留

- 类型：已知遗留登记（非本次修复范围）
- 登记日期：2026-06-30
- 触发上下文：commit `82ff78c0` slider range 上界修复后的 Oracle C 复审

## 1. 现象

`NumberFieldRenderer.java:70` 渲染有 range 的 NUMBER 字段时，`SceneSlider.Props` 写死 `step(1.0)`：

```java
// NumberFieldRenderer.java:68-72
// step=1 离散量化（P1 简化：整数语义）
SceneSlider.Props props = SceneSlider.Props.builder(numValue)
        .min(min).max(max).step(1.0)
        .onChange((value, committing) -> adapter.onFieldEdit(path, Double.valueOf(value)))
        .build();
```

`step=1.0` 让 slider 只能停在整数刻度，无法选小数值。

## 2. 受影响字段

`QzUiLibModernSchema` 中以下字段语义上需要小数步进，当前被 step=1 限制只能选整数：

| 字段 | range | 默认值 | 小数需求 |
|---|---|---|---|
| `fontSizeSetting.charSize` | (1, 72) | 9.0 | 字号常需 9.5 / 10.5 等半磅步进 |
| `fontSizeSetting.awtCharSize` | (8, 256) | 64.0 | 字符生成分辨率，64 / 96 / 128 已够，但 0.5 步进更灵活 |
| `fontSystem.smoothRangeMin` | (0, 1) | 0.0 | 平滑系数，0.0~1.0 必须小数，step=1 实际只能选 0 或 1 |
| `fontSystem.smoothRangeMax` | (0, 1) | 0.9 | 同上，且默认值 0.9 本身就无法用 step=1 的 slider 选到 |
| `fontSystem.characterSpacing` | 无 range | 0.1 | 走 textInput 分支，不受 step 影响（对照项） |
| `fontSystem.shadowOffsetX/Y` | 无 range | 0.5 | 同上，走 textInput 分支 |

> 注：`smoothRangeMax` 默认值 0.9 与 step=1 冲突尤为明显——slider 无法表达默认值，会强制吸附到 0 或 1。

## 3. 当前状态

- **已知遗留，本次未修**
- commit `82ff78c0` 只修 range 上界溢出，不动 step
- 修 range 后 slider 不再卡死，但小数字段仍只能选整数，是次级易用性问题

## 4. 后续方向（择一）

### 方向 A：schema DSL 开放 step 声明

在 `ConfigSchema.builder()` DSL 增加 `.step(double)` 方法，`FieldConstraints` 增加 `step` 字段，`NumberFieldRenderer` 从 `spec.constraints().step()` 读取，未声明时按字段类型给默认 step（整数字段 1.0，浮点字段 0.1 或按 range 跨度推算）。

- 优点：声明式，字段级精确控制
- 代价：DSL 扩面 + `FieldConstraints` schema 变更 + 序列化兼容性核对

### 方向 B：NumberFieldRenderer 根据 schema 类型/range 推导 step

不改 DSL，在 `NumberFieldRenderer.renderSlider` 内根据 `min` / `max` / 默认值小数位推算 step（如 range 跨度 ≤10 用 0.1，≤100 用 1，含小数默认值则按默认值小数位对齐）。

- 优点：零 DSL 改动，纯渲染层启发式
- 代价：启发式可能误判（如 range (0,1) 但实际只想 0/1/0.5 三档），缺字段级精确控制

### 建议取舍

倾向方向 A：step 是字段语义的一部分，应由 schema 显式声明而非渲染层猜。但 A 涉及 schema DSL 扩面，需单独立项排期，不混入 slider range 修复批次。

## 5. 不在本次范围的理由

- commit `82ff78c0` 聚焦"slider 卡死 + 天文数字 + 保存按钮灰显"的阻断性 bug，step=1 是次级易用性问题，不阻断保存
- step 改动涉及 schema DSL 或渲染层启发式设计，需独立评审，混入会扩大改动面、拖慢阻断修复合入
- 留待后续单独排期，配合 schema DSL 扩展批次一并处理

## 6. 关联文档

- 复审记录：`docs/开发者文档/reviews/REVIEW-20260630-slider-range-fix.md`
- 修复 commit：`82ff78c0`
