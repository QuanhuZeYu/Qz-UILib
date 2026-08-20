# ERROR-20260820 根指数定位的 TeX shift 语义（根号内侧 vs 顶之外）

## 现象

对齐 TeX 算法重写 `MathLayoutService.layoutSqrt` 时，按「index 顶 = 根号字形顶上方
0.55×根号总高」实现根指数定位，渲染后指数在根号**顶之外**，与横线垂直分离约 20px
（headless 行分布/quad 诊断可见）——与上一轮修复的分离瑕疵形成镜像错误。

## 根因

TeX（JLaTeXMath NthRoot.createBox）的指数定位：

```java
float bottomShift = FACTOR * (squareRoot.getHeight() + squareRoot.getDepth()); // FACTOR=0.55
r.setShift(squareRoot.getDepth() - r.getDepth() - bottomShift);
```

Box.setShift 的语义是「该盒**基线**相对行基线的下移量」（y 向下正），不是「顶部抬高」。
展开：指数基线 = sqrtBox.depth − 指数.depth − 0.55×(sqrtBox 总高)，而 sqrtBox 的 depth
由内容决定（根号字形 shift 为负、不贡献深度）——结果指数基线落在**根号盒深度侧内移**，
视觉位于根号内侧中上部（基线 ≈ 横线顶高度、x 在根号左上）。把 0.55 因子解读成
「顶之外抬高」方向就反了。

## 修复

```java
float sqrtHeight = radicand.getHeight() + clr + radicalAscent;
float sqrtDepth = radicand.getDepth();
float indexY = sqrtDepth - index.getDepth()
        - MathConstants.SQRT_INDEX_FACTOR * (sqrtHeight + sqrtDepth);
builder.addBox(index, radicalWidth * 0.6F, indexY, MathConstants.SCRIPT_SCALE);
```

锚定断言同步更新（`MathLayoutServiceTest.shouldLayoutSqrtWithIndex`）。

## 经验

- 移植 TeX 算法先确认目标盒模型的**基线/shift 语义**（TeX Box.setShift = 基线位移；
  自研 MathBox 的 addBox dy = 子盒基线偏移，二者同构但"顶/底对齐"公式的展开方向
  取决于 depth 在哪一侧）。
- 「0.55 × 总高」这类因子不要凭直觉决定符号方向，按参考实现逐项展开后对着
  headless quad 诊断验证（本错误由行分布一屏即见）。
