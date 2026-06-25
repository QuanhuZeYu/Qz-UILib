# DECISION-20260625 文本垂直对齐问题研究（Oracle 产出，已实施）

## 状态

已实施（commit `a05ea1c8`，分支 fix/text-vertical-alignment，2026-06-25）。
Oracle 研究方案 + 用户拍板 A+A+A + 补充"预留多种对齐扩展点"需求，实际落地见下方"实施记录"。
与 ink 字段补全升级正交，ink 升级先做并合回 4.0，本修复随后实施。

## 问题现象

字符串在 UI 组件（按钮、TextInput 编辑行）中几乎贴着组件顶部。以前字体偏上（坐标系不自洽）掩盖了它，现在坐标修正确后暴露。

## 根因（Oracle 已验证，亲读源码）

**单一根因**：scene 组件层从未实现"文本在其布局盒内的垂直对齐"。

- `ScenePaintEngine` 把文本绘制点恒定为局部 `(0,0)`（盒顶）
- 字体引擎 y 语义是"字符格顶部"（`FontBatchRenderer`：`baselineY = y + lineBaselineY*scale`）
- 文字永远从盒顶往下画，盒内不做任何垂直居中
- 以前 `lineBaselineY` 量纲混用偏大，baseline 被推低，恰好把贴顶文字推到接近视觉居中；`cb786955` 修正后偏移消失，贴顶本相暴露

**不是字体引擎 bug、不是布局引擎 bug，是 paint 层缺失盒内文字垂直对齐这一步。**

## 原修复方案（保守分步）

### 步骤1：paint 层补行框居中偏移（核心）

- `ScenePaintEngine`：`PaintCommand.text(0, 0, ...)` 改为 `PaintCommand.text(textLeft, textTop, ...)`
- `textTop = paddingTop + max(0, (innerHeight - lineHeight) / 2)`
- 文本高 ≥ 盒高时 inset=0（贴顶，与现状一致）
- 风险低，只改文本命令局部坐标

### 步骤2：让 paint 引擎能拿到 lineHeight

- 给 `ScenePaintEngine` 构造注入 `SceneTextMeasurer`（与 `SceneLayoutEngine` 同款）
- `AbstractSceneHostWidget` 改 `new ScenePaintEngine(measurer)`
- 约 14 处测试构造点机械更新（原估 20+，实际 14）
- 不破 I6（SceneTextMeasurer 是 scene 核心窄端口，非 GL/平台类型）

### 步骤3：修正 PaintCommand.text 过时 javadoc

- `PaintCommand`：把"基线对齐点"改为"文本行框左上角"

## 实施记录（与原方案的偏差与增强）

用户补充需求"考虑未来需要增加多种对齐 css 的情况"，Oracle 二次裁决后实际落地比原方案增强：

1. **新建 `TextVerticalAlign` 枚举**（`ui.scene.node` 包，TOP/CENTER/BOTTOM 三种全实现，默认 CENTER）
   - 放 node 包而非 paint 包，避免 node↔paint 环形依赖
   - 首版三种全实现（零成本不留 TODO 债），BASELINE 推迟（需 ascent/descent 度量）
2. **SceneNode 加 textVerticalAlign 字段**（PAINT 级，setter 调 markSelfPaint 不调 markSelfLayout，I4 守住）
3. **ScenePaintEngine.calculateTextTop**：switch 按 align 算 textTop，default 抛 UnsupportedOperationException（未来扩展忘补 case 立即报错）
4. **水平对齐同模式落地**：TextHorizontalAlign{LEFT,CENTER,RIGHT}，默认 LEFT 零回归

## 对 I6/信条七影响

不破。步骤1纯算术无 GL；步骤2注入的是 scene 核心端口；缓存失效逻辑已覆盖（盒高变化触发 markSelfPaint，fragment 重生成）。I4 守住（align 是 PAINT 级，不影响盒尺寸）。

## 与 ink 字段补全的关系

正交无依赖。本修复用行框居中（`(boxH-lineHeight)/2`），ink 升级用墨迹光学居中。本修复为 ink 升级铺好"文本垂直对齐有专门计算点"的结构前提，ink 落地后可回来增强 inset 公式。

## 已知边界

- 多行文本（`\n`）未处理，当前用单 lineHeight（单行行框），属后续扩展
- 水平对齐 textLeft 由 TextHorizontalAlign 控制，默认 LEFT
- ascent/descent/baseline 对上层不可见，推迟到真有混排/上下标需求时再说
