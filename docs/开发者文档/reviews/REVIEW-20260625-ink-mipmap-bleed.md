# 审查报告：ink 紧凑 atlas mipmap 边缘硬裁边修复

- 类型：字符渲染修复（方案① UV/几何/uvBounds 协同外扩 + 方案② 生成端烘焙 alpha 过渡带）
- 审查日期：2026-06-25
- 分支：`fix/ink-mipmap-bleed`
- 提交：`aabfa5b8`
- reviewer：两轮（有条件通过 → 通过）

## 背景

ink 渲染太过于贴紧字符，mipmap 降采样下出现裁剪硬边缘。根因：
1. ink 子区 UV 精确贴字符像素，shader `fontF.frag:51-54,23-25` 的 `uvBounds` 硬墙把 ink 子区外采样判 0
2. mipmap 高 mip 级 texel 跨 slot 混合，`INK_PADDING=6` 不足以隔离
3. padding 区纯透明（alpha=0），无过渡带，低 mip 级边缘 AA 被 `smoothstep` 阈值化放大成硬边

## 方案

用户拍板：mipmap 保留，方案①+②组合，`inkBleed=1.0`，`INK_PADDING=8`，叠加方案②。

### 方案①（FontBatchRenderer）
- 新增 `INK_BLEED=1.0F` 常量
- `resolveGlyphQuadMetrics` UV 四边各外扩 `INK_BLEED/texSize`，几何 `quadX/Y -= INK_BLEED*glyphScale`、`renderW/H += 2*INK_BLEED*glyphScale`
- `uvBounds` 自动跟随（`GlyphRenderBatch.addQuad` 用同一组 UV 作 `v_uvBounds`）

### 方案②（GlyphGenerator）
- `INK_PADDING` 6→8
- 新增 `bakeInkEdgeFeather`：ink 子区外 ≤1 像素 padding 圈烘焙半透明白色（alpha=127 单层羽化）
- 彩色字形跳过羽化（避免 shader 彩色路径下 emoji 边缘白边）

## 审核结论

### 第一轮：有条件通过

核心逻辑正确，UV/几何外扩配对、烘焙羽化、度量链路、测试重算均无误，NORTH_STAR I6 守住。无 P0 阻断。

发现项：
- **P1-1**：彩色字形烘焙白色羽化可能产生白边 → 第二轮已修复（彩色字形跳过羽化）
- **P1-2**：羽化半径=1 对高 mip 级可能不足 → 需真机验证
- **P2-1**：`bakeInkEdgeFeather` 全图遍历性能 → 暂不处理（离线缓存）
- **P2-2**：`collect` 死代码路径不适用 bleed → 第二轮已加 Javadoc 守卫
- **P2-3**：Javadoc 表述略误导 → 第二轮已修正
- **P2-4**：测试用例不符合真实契约 → 第二轮已补真实约束用例
- **P2-5**：atlas 容量回归未监控 → 真机观察

### 第二轮：通过

P1-1/P2-2/P2-3/P2-4 全部正确落地，8 测试用例全绿，无行为回归。

关键确认：
- 烘焙顺序 `containsColoredPixels → if(!coloredGlyph) bakeInkEdgeFeather` 正确
- `containsColoredPixels` 检测烘焙前原始 AWT 像素，彩色检测准确
- 可见性 `private→package-private` 合理（同包测试可直接调用，不依赖真实字体）
- `shouldKeepUvInsideSlotUnderRealInkPaddingContract` 数值正确（inkLeftInSlot=8 边界用例）
- 新测试全部用手工构造 BufferedImage，不依赖真实字体/环境

## 仍未验证

1. **真机 mipmap 视觉效果**：单层羽化（radius=1）只缓解 1 像素硬裁边，更高 mip 级仍可能采到透明墙。需用户真机验收。
2. **彩色 emoji 实际渲染**：P1-1 守卫逻辑正确，但未真机验证 emoji 边缘无白边。

## NORTH_STAR 合规性

- I6 守住：`FontBatchRenderer`（渲染层）+ `GlyphGenerator`（生成层）无 signal/组件/DOM 改动
- 其余不变量（I1-I5/I7-I11）未触碰
- 无偏离登记需求
