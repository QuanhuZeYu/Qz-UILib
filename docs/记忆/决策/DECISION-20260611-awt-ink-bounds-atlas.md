# 决策：字体 atlas 采用 AWT ink bounds 与可变 slot 契约

## 背景

外部研究目录 `D:\CodeSpace\MC\JavaAWT研究` 验证了旧字体生成策略会把字形缩小到固定 `glyphSize` 方格内，部分默认 Windows 字体近似场景存在触底仍不满足 fits 的情况。继续把字号缩小、atlas 裁剪和光标推进混成一个契约，会阻碍后续字号能力和数学公式排版。

## 候选方案

- 保持旧固定 `glyphSize x glyphSize` slot，只作为兼容路径继续使用。
- 只放宽裁剪，不改变测量和渲染基线契约。
- 引入 AWT baseline + actual pixel bounds，按每个 glyph 的实际 ink bounds 分配 atlas slot。

## 最终选择

字体 atlas 引入 AWT baseline + ink bounds 契约：生成阶段按实际像素 bounds 输出 bitmap、advance、slot 宽高和 atlas baseline；运行时按 codepoint 直索引保存 slot 与基线字段；渲染阶段按默认 glyphSize 到 UI charSize 的比例映射 quad。

## 选择原因

- advance 只负责光标推进，ink bounds 只负责 bitmap 装箱和绘制边界，避免旧策略互相污染。
- 可变 slot 能承载超出默认方格的 glyph，不再通过缩小字号规避裁剪。
- runtime tables 仍保持 primitive array 直索引，渲染热路径不引入对象 key 或 Map 查询。
- 空白字符保留 advance 但不占用 atlas bitmap，符合 AWT 实际像素扫描结果。

## 影响范围

- `GlyphInfo` 增加 slot、atlas baseline、line baseline、bearing 和 hasBitmap 字段。
- `GlyphGenerator` 不再执行“缩小直到塞进 glyphSize”的循环，改为 actual pixel bounds 生成 slot bitmap。
- `GlyphPage` 改为 shelf packing，可上传不同宽高的 glyph 图像。
- `GlyphRuntimeTables` 增加 slotX/Y/W/H、atlasBaselineX/Y、lineBaselineY 等按 codepoint 直索引数组。
- `DefaultFontRendererAdapter` / `FontBatchRenderer` 根据 atlas baseline 计算屏幕 quad，UV 按完整可变 slot 采样，空白 glyph 只推进不提交 quad。
- `TextLayoutService` 宽度测量改用同一派生字体下的 AWT `TextLayout.getAdvance()`。

## 后续注意事项

- 本次仍只有默认字号维度；后续扩展多字号时需要把字号纳入 glyph cache key 或 runtime table 分层。
- 游戏内视觉仍需用真实 `fontSort`、目标 OS 和 `runClient21` 观察 CJK、emoji、下划线、删除线和文本基线。
- `FontMatcher` 对空白字符允许匹配字体，以便生成 advance-only glyph；不要再用 outline 是否为空判断空白不可显示。
- 新 atlas slot 已包含生成阶段的安全 padding；渲染 UV 不再沿用旧固定格时代的 1px 内缩，否则小 slot 会反转 UV 或裁掉边缘像素。
