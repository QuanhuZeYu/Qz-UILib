# ERROR-20260625 字形坐标系不自洽（烘焙57.6/渲染分母64）

## 错误现象

真机四类视觉缺陷：
- 所有字符偏上（高）
- EMOJI 严重偏右（高）
- 下划线 `_` 渲染丢失（高）
- 单字被裁切/缺边（中）
- 字整体偏小 10%（低）

## 触发场景

自研字体引擎在 Minecraft 真机渲染时普遍发生，非特定字体/字符问题，而是系统性坐标系不自洽。

## 根本原因

**烘焙字号与渲染缩放分母不自洽**：
- 烘焙：`DerivedFontCache.java:41` `size = max(glyphSize × fontScale, 6.0) = 64×0.9 = 57.6`
- 渲染：`FontBatchRenderer.java:244` `glyphScale = charSize / defaultGlyphSize`，defaultGlyphSize=64
- `GlyphGenerator.java:69` `lineBaselineY = round(glyphSize(64) - descent(来自57.6px字体))` 量纲混用 → 偏上
- 业界硬性约定（ImGui/TextMeshPro/Bevy 等7库）：烘焙字号 = 渲染缩放分母。本项违反。

`fontScale=0.9` 是早期为把字形塞进 64px 固定方格的补丁 hack，导致烘焙实际用 57.6px，但渲染仍按 64px 归一，所有几何量被 /64 注入 0.9 倍误差。

## 修复方案

方案B（用户拍板）：烘焙改 64，让 glyphSize 语义统一。
- `DerivedFontCache.java:41` 去掉 `× FontConfig.fontScale`，`size = max(glyphSize, 6.0)`
- `FontRegistry.java:64,80` 注册派生字号从 `awtCharSize×fontScale` 改为 `awtCharSize`
- `FontConfig.java` 移除已废弃的 `fontScale` 字段及配置读取/变更检测/快照刷新（5处），消除幽灵配置
- `GlyphGenerator.java:69` 与 `FontBatchRenderer.java:244` 无需改，量纲自洽后自动正确

## 预防措施

- 烘焙字号与渲染缩放分母必须保持同一常量，禁止用独立系数分别缩放
- 任何"为塞进固定方格而缩小字号"的 hack 必须登记偏离，不可静默植入配置常量
- 字体引擎改动对照 AWT 研究（`D:\CodeSpace\MC\JavaAWT研究`）的业界约定核实
- 真机验证必交用户跑（沙箱无 GUI）

## 依据

- 研究：`D:\CodeSpace\MC\JavaAWT研究\docs\开发者文档\Qz-UILib字体渲染修复建议.md`
- 业界调研：`D:\CodeSpace\MC\JavaAWT研究\docs\开发者文档\glyph-atlas-ink-bounds业界调研-20260624.md`
- 决策：`D:\CodeSpace\MC\JavaAWT研究\docs\记忆\决策\DECISION-20260624-glyph-mipmap-edge-erosion.md`
- 修复 commit：`cb786955`（分支 `fix/glyph-coordinate-system-mismatch`）
- 真机验证：待用户 runClient21
