# ERROR-20260820 斜体几何从平移升级为斜切（i 标签与数学变量共用语义）

## 背景

对齐 TeX 标准实施数学变量斜体：FontType 只有 NORMAL/BOLD 两套字形表，真斜体字形
（cmmi 派生）需第三套 123MiB 表，成本不可接受。采用几何斜切近似（顶部顶点右移
slant×字高、底部不动，slant = tan14° ≈ 0.25，对齐 cmmi 斜角）。

## 变更与影响

- GlyphRenderBatch.addQuad 的 italic 几何：原「整 quad 右移 2px」改为「斜切」
  （顶部顶点右移 0.25×height，底部顶点不动）。
- 共用语义：富文本 i 标签与 LaTeX 数学变量走同一 italic 通道——i 标签的视觉从
  平移变为斜切（更接近真斜体，但属可感知的视觉变更）。
- 斜切后 quad 顶点不再轴对齐：软件光栅化器按三角形重心插值 UV 自然正确；
  真机 shader 同样按顶点插值，无需改着色器。

## 配套

- 数学变量范围：ORD 类 ASCII 字母（TeX mathnormal capitals/small 映射）；数字、
  函数名（OP）、text 内容（TEXT）直体。
- 斜体校正（TeX Char.italic 近似）：MathMetrics.italicCorrection default 0；
  真机度量 = 0.25×xHeight（斜切几何的视觉右倾），布局侧仅对单字符数学变量基底的
  上标右移此量。
- cramped style 同步落地：根式/分数分子分母/重音基底内上标抬升用 sup3。

## 经验

- 「跟随标准」在字形表结构受限时先算清成本梯度：真斜体字形（第三套表）远大于
  几何斜切（渲染层一行）；斜切与 TeX cmmi 斜角一致时视觉几乎等价。
- 复用现有通道（italic）时检查共享方（i 标签）的语义承诺——此处视觉升级可接受，
  但已记录为可感知变更，若下游回归再评估分通道。
