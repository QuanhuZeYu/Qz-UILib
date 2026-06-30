# REVIEW-20260630 slider range 上界修复复审

- 类型：commit 复审（根因推断校正 + 修复正确性复核）
- 复审对象：commit `82ff78c0` `[Fix]: 修 8 处 number range 上界 Double.MAX_VALUE 导致 slider 溢出卡死`
- 复审方：Oracle C
- 复审结论：**通过，无阻断项**；commit message 根因链有一环推断错误，特此登记校正

## 1. 修复内容回顾

`QzUiLibModernSchema.java` 中 8 处 number 字段 range 上界原为 `Double.MAX_VALUE`，本次收为合理上界：

| 字段 | 原 range | 新 range |
|---|---|---|
| smoothRangeMin / smoothRangeMax | (0, Double.MAX_VALUE) | (0, 1) |
| drawStageUploadIntervalMs / drawStageUploadLimitPerSecond | (0, Double.MAX_VALUE) | (0, 1000) |
| drawStageUploadBatchSize | (0, Double.MAX_VALUE) | (0, 256) |
| aaStrength | (1, Double.MAX_VALUE) | (1, 120) |
| awtCharSize | (8, Double.MAX_VALUE) | (8, 256) |
| charSize | (1, Double.MAX_VALUE) | (1, 72) |

## 2. commit message 根因推断的误差

commit `82ff78c0` 正文根因链写为：

> slider 归一化分母为 `Long.MAX_VALUE`，滑块卡死无法拖动
> → readout 显示天文数字
> → **字段值超出 range 上界，`hasError` 恒 true，`canSave` 恒 false，保存按钮始终灰显**

Oracle C 复核发现其中 **"字段值超出 range 上界 → hasError 恒 true"** 这一环推断错误：

- slider 默认值（如 `awtCharSize=64.0`、`charSize=9.0`、`aaStrength=12.0`）远小于 `Double.MAX_VALUE`，本就落在 range 合法区间内
- 即便 slider 因 `Math.round(Double.MAX_VALUE)` 溢出为 `Long.MAX_VALUE` 导致 progress 归一化异常，draft 中的字段值仍是 schema 默认值，并未被推到 range 上界之外
- 且 `Long.MAX_VALUE(9.22e18) < Double.MAX_VALUE(1.79e308)`，即便 slider 把值推到 `Long.MAX_VALUE`，相对 `Double.MAX_VALUE` 上界仍属合法，`hasError` 不会恒 true

## 3. 实际根因链

真实根因是 **slider 量化溢出导致交互卡死 + readout 显示异常**，与 `hasError` / `canSave` 无直接因果：

1. `SceneSliderPrimitive` 量化滑块值时调用 `Math.round(max)`
2. `max = Double.MAX_VALUE` 经 `Math.round` 溢出为 `Long.MAX_VALUE`（`Math.round` 返回 `long`，`Double.MAX_VALUE` 超出 `long` 范围）
3. slider 归一化分母变为 `Long.MAX_VALUE`，progress 计算异常，**滑块卡在最左、无法拖动到合理值**
4. readout 显示 `Long.MAX_VALUE` 量级的天文数字
5. 用户无法通过 slider 选到合理值，保存按钮即便逻辑上可点击，实际交互被 slider 卡死阻断

## 4. 修复仍正确的理由

虽然 commit 根因推断有一环误差，修复方案本身正确：

- 修 range 上界后 `max` 落入合理量级（≤256 / ≤120 / ≤72 等），`Math.round(max)` 不再溢出
- slider 归一化分母恢复正常，progress 可正常拖动
- readout 显示真实值
- `SceneSliderPrimitive.normalizeValue` 逻辑本身正确，溢出纯由 `max` 过大导致，无需改动消费方

## 5. commit 历史不改写

commit `82ff78c0` 已提交，按项目规范不改写历史。本复审文档作为根因推断误差的正式登记，后续若再排查同类 slider 卡死问题，应以本文件第 3 节根因链为准，而非 commit message。

## 6. 边界回归测试

本次 fixer D 收尾已在 `QzUiLibModernEndToEndTest` 补 3 项 range 上界边界测试，锁定上界本身合法、上界 +1 非法：

- `awtCharSizeRangeUpperBoundBoundary`：256 可保存 / 257 INVALID
- `aaStrengthRangeUpperBoundBoundary`：120 可保存 / 121 INVALID
- `charSizeRangeUpperBoundBoundary`：72 可保存 / 73 INVALID

防止上界被改回 `Double.MAX_VALUE` 或误调时静默回归。

## 7. 关联遗留

`NumberFieldRenderer.java:70` `step=1.0` 整数量化导致 slider 只能选整数，对 `charSize` / `awtCharSize` / `smoothRangeMin` 等想要小数步进的字段构成已知遗留，本次未修，登记于
`docs/记忆/决策/DECISION-20260630-number-field-step-integer-quantization.md`。
