# ERROR-20260905 软光栅出图右上半区 180° 旋转采样（headless 视觉核查设施长期不可读）

> 日期：2026-09-05 分支：4.0 定级：**S（验证设施失效，非产品缺陷）**
> 引入：04a8a8bb（2026-08-20，LaTeX headless 渲染验收设施） 修复：2a5ab61a
> 存活时长：约 16 天，期间产出并被采信过结论的 PNG 逾 300 张。

## 一、现象

`build/reports/` 下三批 headless 出图的字形被破坏，且**与放大倍率无关**（@1x 同样烂）：

- `markdown-render/08-thematic-break@4x.png`：`code span` 画成 `ooɔǝ spǝll`（`e` → `ǝ`，即 `e` 的 180° 旋转）
- `markdown-render/01-heading@4x.png`：`一级标题` 的 `标` 右上半沿 TL→BR 对角被替换
- `latex-render/13-formula.png`：`f(x)` 的 `)` 画成 `;`；`12-formula.png`：矩阵 `a b / c d` 画成 `2 0 / a a`

关键误导特征：**180° 旋转对称的字形完全正常**（`0`、`8`、`一`、`二`、`三`、`工`、`。`、`=`、`I`），
不吃纹理的 decoration quad（虚线、code 衬底盒）也完全正常。所以图面呈现"一半字对、一半字错"
的形态，极易被读成"字体缺字""水平虚影""邻格串扰"。

## 二、根因

`FontSoftwareRasterizer` 把一个 glyph quad 拆成两个三角形栅格化。顶点槽序
（`GlyphRenderBatch.java:106-113`）：0=TL、1=BL、2=BR、3=TR。

- T1 = (TL, BL, BR) = 槽 0,1,2
- T2 = (BR, TR, TL) = 槽 2,3,0 —— 但只把**屏幕坐标**传了下去

`rasterizeTriangle` 算出的 w0/w1/w2 是**本三角形自身顶点**的重心坐标，而 `shade()` 恒按槽 0,1,2
取属性。于是 T2 的插值变成：BR 的权重 × TL 的 UV、TR 的权重 × BL 的 UV、TL 的权重 × BR 的 UV。

**解析证明（本文作者独立复核）**：在 T2 三个顶点处分别求值 ——
BR 处得 (u0,v0)，其真值应为 (u1,v1)；TR 处得 (u0,v1)，真值 (u1,v0)；TL 处得 (u1,v1)，真值 (u0,v0)。
三点均为真值的 (1-u,1-v) 补。仿射映射由三顶点唯一确定，故 **T2 全域恒等于把 UV 窗口做 180° 旋转采样**。
T1 的绑定本就正确 ⇒ **只有屏幕右上半区被旋转**。

排除项（均实测核对，非推测）：图集布局、`SoftwareGlApi.writeRegion` 行 stride、页边长 4096
（`FontConfig.java:56` 与 `GlyphRuntimeTablesView.java:158` 同源）、UV 归一化分母、
skyline 变尺寸槽位 —— **全部正确**。UV 由生产路 `FontBatchRenderer.resolveGlyphQuadMetrics`
（与游戏内同一份代码）生成，用 inkWidth 而非 advance，含 ±1px bleed。**度量面无辜。**

## 三、为什么活了 16 天

不是没人看，是**所有指标都在看错的方向**：

1. `profiles.txt` 记 `quads=33 ink=4384` —— 量"有多少墨"，从不量"是哪个字"。
   取错/转错字形不改变墨量数量级 ⇒ 该指标**结构上不可能发现本缺陷**。
2. 门禁的出图断言是 `pngCount >= expectedPng` —— **数文件个数**。
   它与 ink 计数是同一类断言：证明"东西产出了"，永不证明"东西是对的"。
3. `countInk >= MIN_INK_PER_PAGE` 地板：垃圾墨照样过线。
4. 旋转对称字幸免 + decoration 正常，使图面"看起来像字体问题而不像几何问题"，
   两次被误归因为"CJK 重影系场地既有特性"（该错误注释已在 2a5ab61a 一并更正）。

## 四、我（AI）在此缺陷上犯的三次错

登记在此以防重犯，均属**结论方式**错误而非数值错误：

- **把"既有"当成"无害"**：发现虚影早于当次改动，就据此放行。正确推论应是
  "既有 + 不可读 = 验证设施本身失效"。
- **用坏图当真对照**：以 `latex-render/12-formula.png` 修复前后 sha256 相同
  （27693b77…）证明"改动未影响 LaTeX 输出"。该结论技术上成立但**毫无价值** ——
  比的是两张一样坏的图。哈希相同只证明"坏得一致"。**对照物必须先自证可读。**
- **基于坏图误报**：宣称 `P11-side@4x.png` 左右断点差约 1 个码点、并推断"两栏可用宽度不等"。
  用修好的仪器重看：两栏同宽 269、同 4x、断点逐字符相同。**该结论撤回。**

## 五、作废范围（划清，不含糊）

| 结论 | 状态 |
|---|---|
| 2026-08-20 起所有 headless PNG 的**人眼观感判读**（latex-render / latex-compare ours 侧 /
| markdown-render / markdown-compare side 图） | **作废** |
| M3/M4/M5 中"我自己读 @4x 图"得出的验收 | **作废** |
| @4x 真放大工程本身 | 基础采样错，成果**白做**（设施已随修复复活） |
| `MarkdownChat3ParityTest` 的**数值**半边（段宽 0.0D / TIE 2.0D / 行宽 1 / 命中区 1，
| 19 PARITY + 11 NEW，FAIL=0） | **仍有效** —— 其值取自 `TextLayoutService` 度量与
| `PaintCommand`/`LINK_REGION` 几何，不取像素 |
| 游戏内 GL 渲染路 | **有效**（用户实机截图干净可读） |

旁证：A 路走 `ChatLineLayouter.splitFragments` 真机同源度量。若共享装配连度量一起弄坏，
两侧宽度会**发散**而非在 0.0D 容差下逐位相等。**坏的是采样，不是度量。**

## 六、修复

`FontSoftwareRasterizer`：`rasterizeTriangle`/`shade` 增加 `offA/offB/offC` 顶点槽偏移，
T1 传 (0, s, 2s)、T2 传 (2s, 3s, 0)，属性插值按本三角形自身顶点绑定。41 行 diff，
**未重新生成任何图集**（不需要——图集从来是对的）。

## 七、防复发

新增 `FontSoftwareRasterizerSamplingTest`（4 用例，纯机器判定，不看图）：

1. `quadAttributeInterpolationBindsEachTriangleToItsOwnVertices` —— 4×4 全异色纹理 + 8×6 quad，
   逐像素与独立 oracle 精确比对（±2）。**判别力地板**：另算一遍缺陷模型预测，要求
   "缺陷预测 ≠ 正确期望"的像素数 ≥10（否则本断言是同义反复的空跑）。
2. `rotationSymmetricTextureIsInvisibleToTheDefectAsPositiveControl` —— 正对照：对称纹理下
   缺陷应完全不可见，证明 oracle 不过敏、判据不恒红。
3. `decorationQuadIsUnaffectedBySampling` —— decoration 纯色直出正对照。
4. `realPipelineGlyphQuadsSampleAtlasWindowAtCorrectPositions` —— 真装配链渲染 "AEFI"@16px，
   **逐字形**把右上半区与 atlas 窗口正确采样比对，一致率 ≥0.85；反 ∅ 地板：字形 ≥4、
   每字形 T2 像素 ≥12、T2 内 atlas 真墨 ≥4、全页墨水 ≥150；**左下半区作逐字形正对照**。

几何选择说明：合成 quad 用非正方形 8×6，使 3(px+0.5)=4(py+0.5) 无整数解，从根上避开
像素中心恰落在 TL→BR 对角线上被两个三角形各画一次的二义区。

## 八、立此规矩

- **任何"视觉自查"结论，必须先证明仪器可读**：拿一张已知内容的出图，断言其中**至少一个
  非对称字形**的像素布局符合预期，再谈判读。
- **出图类断言必须含一条"能区分是哪个字"的判据**。墨量、文件数、非零性均不合格。
- **对照物自证**：用哈希/差异做对照前，先确认对照物本身不是缺陷产物。
- **图像读数不得作为发起代码改动的唯一依据**：必须同时给出**横向与纵向**两个维度的实测游程/边界
  （例如「底色带 x 范围 + 每条带宽 + 带间空档」与「底色带 y 范围 + 每条带高 + 带间空档」），
  **只量一个维度等于没量**。2026-09-05 实案：读图者把「围栏底色三截」（纵向 8px 缝）读成「右缘参差」
  （横向），据此发起 M8 横向修复——该修复本身方向正确且经实测有效，但**没有解决用户报的问题**；
  第二轮又据此得出「M8 没改任何像素、横向统一早已成立」的反向误判，同样被逐列实测证伪
  （M8 前底色矩形 w=92/302/11，M8 后 302/302/302）。教训升级：**读数要落到「哪个轴、什么数值、
  哪版对照」三件套，缺一项就不得写成改动前提**（本仓第五次误读，且这次读的是自己写的数）。

## 九、验证记录

- `--tests "*FontSoftwareRasterizerSampling*"` → 4/0
- `--tests "*MarkdownSoftwareRender*" --tests "*LatexSoftwareRender*"` → 10/0 与 37/0，重出图
- 全量 `gradlew build --offline` → BUILD SUCCESSFUL，360 套件 **3979** 测试 0 failures 0 errors
- 计数核对：修复前 3975 + 新增 4 = 3979，**无静默删除**
- 出图时间戳链：光栅器改于 08:40:56，全部 294 张对拍图与 render 图重出于 08:48:12–13，
  **全在修复之后**，无旧图混入
- 新图 sha256（修复后）：`00-full-page.png` 6793d092…；`08-thematic-break@4x.png` e19ecfc1…；
  `01-heading@4x.png` 8f7de1bb…；`latex-render/01-formula.png` f62c8d93…；`09` 0443be48…
- **未由本次复核复现**：子代理自述"修复前用例 1)、4) 实测红（A 右上半一致率 0.606=40/66）"。
  因果链已由解析证明 + 修复前图的实际指纹 + 修复后图干净三方闭合，故未回滚复验。
