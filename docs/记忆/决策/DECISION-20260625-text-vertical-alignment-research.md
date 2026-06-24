# DECISION-20260625 文本垂直对齐问题研究（Oracle 产出，待实施）

## 状态

研究完成，方案待用户拍板实施。与 ink 字段补全升级正交，用户决定先做 ink 升级，本修复延后。

## 问题现象

字符串在 UI 组件（按钮、TextInput 编辑行）中几乎贴着组件顶部。以前字体偏上（坐标系不自洽）掩盖了它，现在坐标修正确后暴露。

## 根因（Oracle 已验证，亲读源码）

**单一根因**：scene 组件层从未实现"文本在其布局盒内的垂直对齐"。

- `ScenePaintEngine.java:245` 把文本绘制点恒定为局部 `(0,0)`（盒顶）
- 字体引擎 y 语义是"字符格顶部"（`FontBatchRenderer.java:245`：`baselineY = y + lineBaselineY*scale`）
- 文字永远从盒顶往下画，盒内不做任何垂直居中
- 以前 `lineBaselineY` 量纲混用偏大，baseline 被推低，恰好把贴顶文字推到接近视觉居中；`cb786955` 修正后偏移消失，贴顶本相暴露

**不是字体引擎 bug、不是布局引擎 bug，是 paint 层缺失盒内文字垂直对齐这一步。**

## 修复方案（保守分步）

### 步骤1：paint 层补行框居中偏移（核心）

- `ScenePaintEngine.java:241-246`：`PaintCommand.text(0, 0, ...)` 改为 `PaintCommand.text(textLeft, textTop, ...)`
- `textTop = paddingTop + max(0, (innerHeight - lineHeight) / 2)`
- 文本高 ≥ 盒高时 inset=0（贴顶，与现状一致）
- 风险低，只改文本命令局部坐标

### 步骤2：让 paint 引擎能拿到 lineHeight

- 给 `ScenePaintEngine` 构造注入 `SceneTextMeasurer`（与 `SceneLayoutEngine` 同款）
- `AbstractSceneHostWidget.java:58` 改 `new ScenePaintEngine(measurer)`
- 约 20+ 测试构造点机械更新
- 不破 I6（SceneTextMeasurer 是 scene 核心窄端口，非 GL/平台类型）

### 步骤3：修正 PaintCommand.text 过时 javadoc

- `PaintCommand.java:196-197`：把"基线对齐点"改为"文本行框左上角"

## 对 I6/信条七影响

不破。步骤1纯算术无 GL；步骤2注入的是 scene 核心端口；缓存失效逻辑已覆盖（盒高变化触发 markSelfPaint，fragment 重生成）。

## 与 ink 字段补全的关系

正交无依赖。本修复用行框居中（`(boxH-lineHeight)/2`），ink 升级用墨迹光学居中。本修复为 ink 升级铺好"文本垂直对齐有专门计算点"的结构前提，ink 落地后可回来增强 inset 公式。

## 待用户拍板

1. 居中口径：行框居中（推荐，零新增度量）vs 等 ink 做墨迹光学居中
2. 步骤2注入方式：注入 SceneTextMeasurer（推荐）vs 布局阶段预存 inset 字段
3. 多行首版范围：单行优先、多行 inset 钳到非负（推荐）
