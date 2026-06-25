# 审查报告：ink 紧凑 atlas mipmap 边缘硬裁边修复

- 类型：字符渲染修复（最终方案：UV/几何/uvBounds 协同外扩）
- 审查日期：2026-06-25
- 分支：`fix/ink-mipmap-bleed`（已合回 4.0 并清理）
- reviewer：两轮（有条件通过 → 通过）
- 真机验收：通过（2026-06-25）

## 背景

ink 渲染太过于贴紧字符，mipmap 降采样下出现裁剪硬边缘。根因：
1. ink 子区 UV 精确贴字符像素，shader `fontF.frag:51-54,23-25` 的 `uvBounds` 硬墙把 ink 子区外采样判 0
2. mipmap 高 mip 级 texel 跨 slot 混合，`INK_PADDING=6` 不足以隔离
3. padding 区纯透明（alpha=0），无过渡带，低 mip 级边缘 AA 被 `smoothstep` 阈值化放大成硬边

## 最终方案

仅保留方案①（UV/几何/uvBounds 协同外扩），方案②（烘焙 alpha 过渡带）已回退删除。

### 方案①（FontBatchRenderer）
- 新增 `INK_BLEED=1.0F` 常量
- `resolveGlyphQuadMetrics` UV 四边各外扩 `INK_BLEED/texSize`，几何 `quadX/Y -= INK_BLEED*glyphScale`、`renderW/H += 2*INK_BLEED*glyphScale`
- `uvBounds` 自动跟随（`GlyphRenderBatch.addQuad` 用同一组 UV 作 `v_uvBounds`）
- `INK_PADDING` 6→8（给 UV 外扩留余量）

### 原理
不烘焙时 padding level 0 纯透明，mipmap 降采样时 ink 边缘 AA 像素自然渗透到 padding 生成半透明 texel，
UV 外扩 + uvBounds 放宽让 shader 能采到这些渗透 texel，边缘过渡自然且无白边（单色字 RGB 被 `Color.rgb` 替换，无颜色偏移）。

## 方案演进

### 初始方案：方案①+②组合
- 方案②：`GlyphGenerator.bakeInkEdgeFeather` 在 ink 子区外 ≤1 像素 padding 圈烘焙半透明白色（alpha=127 单层羽化），彩色字形跳过
- reviewer 两轮审核通过，14 测试类全绿

### 真机暴露问题：所有字符白边
- 真机验收发现烘焙白色羽化导致所有字符（含单色字形）出现白边
- 根因：单色字形走 shader 单色路径虽用 `Color.rgb` 替换纹理 RGB，但烘焙的半透明 alpha 参与 `smoothstep` 阈值化形成浅色描边
- 用户决策：去掉羽化，仅保留 UV/几何外扩

### 回退方案②
- 删除 `bakeInkEdgeFeather` 方法 + `INK_FEATHER_RADIUS` 常量 + 调用点
- 删除 `GlyphGeneratorBakeInkEdgeFeatherTest`（整文件）
- `containsColoredPixels` 恢复 private
- 保留 `INK_PADDING=8` 和 `INK_BLEED=1.0`（方案①）
- commit `51da18f5`，净减 256 行

## 审核结论

### 第一轮：有条件通过
核心逻辑正确，UV/几何外扩配对、烘焙羽化、度量链路、测试重算均无误，NORTH_STAR I6 守住。无 P0 阻断。

### 第二轮：通过
P1-1（彩色字形白边）/P2-2/P2-3/P2-4 全部正确落地，8 测试用例全绿，无行为回归。

### 真机验收：通过
用户确认白边消除、硬裁边改善（2026-06-25）。

## NORTH_STAR 合规性

- I6 守住：`FontBatchRenderer`（渲染层）+ `GlyphGenerator`（生成层）无 signal/组件/DOM 改动
- 其余不变量（I1-I5/I7-I11）未触碰
- 无偏离登记需求
