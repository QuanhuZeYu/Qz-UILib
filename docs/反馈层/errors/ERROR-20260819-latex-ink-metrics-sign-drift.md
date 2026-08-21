# ERROR-20260819-latex-ink-metrics-sign-drift.md

## 摘要

MathMetrics.inkCenterOffsetY 的接口语义（javadoc：正=上方）与实现口径（y 向下：负=上方）长期漂移，
三个使用点各按一种假设写公式，靠「回退盒中心恰好 UP 正」的巧合相互抵消。上一轮把回退路径改为
AWT 字形边界（y 向下）打破巧合，真机大运算符（SUM/INT/PROD）整体偏移 2xinkCenterY（约 8px@16px）。

## 现象

用户反馈「改完求和符号之类的又炸了」。

## 根因

1. 语义漂移：inkCenterOffsetY javadoc 写「正=上方」，实现返回 bearingY+inkHeight/2
   （y 向下：负=上方，与渲染 quad 的 bearingY 同向）。
2. 使用点符号假设分裂：
   - layoutFence / layoutAtom：baselineY = -axis - C（按 y 向下写），正确；
   - layoutLimits：baseShift = C*scale - axis（按 UP 正写），错 2C；
   - 回退分支返回 (ascent-descent)/2（UP 正），恰好让 layoutLimits 在「字形未就绪」时正确，
     却让 fence 在同样场景错误——被时序/缓存掩盖成「矩阵括号 OK、求和 OK」的表象。
3. 探针覆盖不全：此前 ink 口径探针只测了 (){| 与根号，没测大运算符字形，SUM 的符号矛盾未暴露。
4. 缓存：LatexCache 键曾不含字形就绪状态（已修 inkEpoch），回退/真实布局切换的跳变被永久缓存放大。

## 修复

1. 统一 inkCenterOffsetY 语义为 y 向下（负=基线上方），与渲染 quad bearingY 同口径：
   javadoc 修正；default 改 (descent-ascent)/2；TextLayoutService 多码点/兜底分支同步。
2. layoutLimits 改 baseShift = -axis - inkCenterY*opScale（与 fence/atom 同式）。
3. LAYOUT_VERSION 9->10 使旧缓存盒失效。
4. 回归测试：bigOperatorAxisCenteredByInk（SUM ink 中心=分数线轴 ±2px）、
   inkCenterOffsetYMatchesQuadBearingContract（实现值 == 渲染 quad bearingY 口径，7 字形契约）。

## 教训

- 带方向/坐标系的度量接口必须在 javadoc 写死坐标系与符号，且所有实现路径（表/AWT/回退）
  与所有使用点必须同口径；发现漂移立即修，不要靠巧合。
- 回退值必须与真实值同口径同符号，否则「就绪前后」布局跳变。
- ink 度量探针必须覆盖全部基元字形类别（定界符、大运算符、根号），不能只测一两类。