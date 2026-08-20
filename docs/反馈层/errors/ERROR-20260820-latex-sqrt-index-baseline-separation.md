# ERROR-20260820 LaTeX 根指数与根号横线垂直分离

## 现象

`\sqrt[3]{x}` 真机渲染：根指数 3 与根号横线/被开方内容垂直分离（指数飞高一个字号量级）。
headless 软件渲染验收场地（`font.render.software` 测试集）复现并定位：布局盒内
`3` 的基线 y=-25.36，而根号横线顶仅 -15.56（分离约 10px，16px 字号下肉眼明显）。

## 根因

`MathLayoutService.layoutSqrt` 的根指数定位公式：

```java
// 错误：叠加了 index.getHeight()（约一个 script 字号的高度）
float indexY = -(radicand.getHeight() + clearance + thickness + index.getHeight()
        + MathConstants.ACCENT_GAP_EM * size);
```

`Builder.addBox` 的 dy 语义是「子盒**基线**相对父盒基线的下移量」。根指数的底部 =
基线 + 自身 depth（数字 depth≈0），因此指数基线 = 根号横线顶上方 `ACCENT_GAP` 即可；
再叠加 `index.getHeight()` 等于把指数多推高一个自身字号，与横线分离。

对照同类的 `layoutLimits` 上标公式（正确用法）：`supY = -(base.height + gap + sup.height)`
让 sup 的**底部**（基线+depth）恰好落在基字顶上方 gap —— 上标的高度项是「为对齐底部」
而加，不是「基线再上移一个身高」。根号指数缺失的正是这个语义区分。

## 修复

```java
// 正确：指数基线 = 横线顶上方 ACCENT_GAP
float indexY = -(radicand.getHeight() + clearance + thickness
        + MathConstants.ACCENT_GAP_EM * size);
```

锚定断言（`MathLayoutServiceTest.shouldLayoutSqrtWithIndex`）：

```java
Assert.assertEquals(rule.getY() - MathConstants.ACCENT_GAP_EM * S, glyph.getY(), EPS);
```

## 经验

- 盒布局里「基线 vs 顶部 vs 底部」对齐前先确认 `addBox` dy 语义（基线偏移），
  再决定是否叠加子盒 height/depth；「底部对齐」= 基线 + depth，「顶部对齐」= 基线 - height。
- 同类公式放一起对照（layoutSupSub/layoutLimits/layoutSqrt/layoutAccent），
  语义不一致处大概率是 bug 源。
- 该 bug 由 headless 像素/盒级验收在**不进游戏**的情况下定位并锚定回归，
  正是「同源指令流 + 双执行后端」验收场的第一个实战案例。
