# UILib 所属层与输入范围规格

状态：已确认模块规格；本模块维护所属层空间、共同呈现、输入参与与结束合同。运行证据按实际场景与版本记录，不外推其他宿主。

状态、生命周期及输入范围遵循[核心职责规格](<2026-10-07-uilib-core-contract-design.md>)；普通布局、动态身份、内容投影分别遵循[常规布局](<2026-10-13-uilib-layout-spec.md>)、[动态结构](<uilib-dynamic-structure-spec.md>)与[文本内容](<2026-10-07-uilib-text-content-spec.md>)合同。

## 1. 声明、拥有与普通流

所属层是来源节点的直属逻辑孩子，来源由这一父子关系决定。声明使用 SceneLayer.Policy/create；自定义控件的 SceneControl.layerPolicy 返回非 null 时采用同一所属层语义，null 表示普通流。Policy 包含非负 logical px gap 及 INTERACTIVE/PASSIVE 参与声明；已声明所属层的策略信号不得给出 null。工厂接收页面上下文与只读策略来源，默认 COLUMN、宽高 NATURAL，普通 Size、padding、visible 与孩子声明继续可用，默认不隐含 modal。

打开及结束沿 visible/ownedChildren 与既有动态拥有声明。逻辑 parent、UUID、Owner 和挂载联系保持原树语义，不建立第二树。工厂不拥有外部策略或业务状态；来源销毁释放其所属孩子，页面结束沿既有作用域释放。

SceneChildMeasurer.flowChildren 提供当批不可修改的可见普通流直属子集合，保持逻辑顺序；所属层不计入 ROW/COLUMN 的 gap、普通自然尺寸、grid 列数/行高或来源 scroll extent。measureChild 与普通 ChildPlacements 不得直接测量、分配所属层；违例诊断包含来源/控件和实际子节点位置。普通内容投影不机械替换为另一套 flow 布局。

## 2. 测量与最终锚定

普通来源先取得最终分配外盒，已经包含普通流滚动位移；框架再按页面视口约束测量所属层。层内复用普通 COLUMN/ROW 的真实子测量与最终排列规则，policy 的读取进入实际测量/排列依赖，padding 只计算一次。字号、文案、主题、策略、视口与来源滚动变化沿有效依赖更新，纯滚动可复用当前文字度量。

所属层按来源最终外盒左侧对齐、外盒下方 gap 锚定；不翻到上方，不主动隐藏或缩小内容。能容纳的轴仅平移，使最终盒落在页面视口；层在该轴大于视口时从视口起点开始，保留尺寸及硬 min/max，超出部分按视口裁剪。嵌套层以外层最终外盒作为来源，不使用未平移首选盒或旧坐标。来源滚动不在层上叠加第二次。

独立验收例：视口 (600,400)，来源 (520,350,60,30)，层 (180,100)、gap 8，最终盒 (420,300,180,100)。视口 (160,90)，来源 (20,30,40,20)，层硬下界 (200,120)、gap 8，最终盒 (0,0,200,120)。

## 3. 裁剪与来源可用性

所属层的呈现路径从页面视口开始，逃离来源普通祖先的局部 clip；层自身及内部普通孩子的 clip 仍按已有声明处理。来源本身不逃离普通裁剪：来源有效外盒与其有效 clip、页面视口交集必须具有正面积。partial clip 保留所属层，full clip 停止发布该层有效几何；来源或逻辑祖先隐藏、禁用、销毁、脱离挂载也停止有效呈现，重新有效后仍按当前最终来源求解。

图像备用 paint capture、数学嵌入和文档内部内容投影不作为所属层来源通道。声明层时沿实际内容边界给出含 boundary/kind/layer 的定位诊断；不把备用分支静默变成交互主树。普通面板可包含这些内容与自己的所属层，两者继续进入所属能力的合同。

## 4. 同批共同几何与呈现序

每个有效节点在唯一 SceneGeometry 中恰一次。来源可用性、层边界、逻辑 modal 范围及有序呈现子树组与外盒/clip 同批发布；PaintPlan 按相同组序绘制，组内保留 engine placements 的内容顺序，并在每组末尾闭合 paintAfterChildren。图像捕获复用同管线，不以全局节点数组排序破坏内容栈。

同一范围的普通内容先于所属层；同范围多个层沿逻辑声明顺序，后声明绘制在前声明之上；嵌套层在所属外层之上。modal 范围维持逻辑归属，绘制位置不改变范围。当前 modal 层以下的非模态层停止有效呈现；当前 modal 的来源祖先层链保留，来源链绘制在当前 modal 以下，其他后台层不压到 modal 上方。范围的有效性依据当前声明及来源事实，禁用 modal 不使有效背景层被错误过滤。PASSIVE 层子树内 modal 声明不合法。

本批呈现事实仅用于消费当前几何，不能保存为另一个持久投影权威。声明/结构/范围/视口和度量失效后重建；永久拥有与移动继续由动态结构合同管理。输入参与与有效资格、Tab/滚轮边界及 owner 取消由第 6—7 节和唯一路由器承接。

## 5. 有限空间验收

| 场景 | 可观察结果 |
| --- | --- |
| 正式 scrollable+clip 页面，来源普通按钮与层内真实长文字按钮 | 来源自然高度和 scrollMaximum 保持；真实 FT/HB 决定层自然高度，面板外有可见 PaintCommand；parent/UUID/Owner 保持 |
| 两例锚点、padding、超视口硬盒与嵌套来源 | 最终盒符合独立例值，硬边界保持，嵌套使用外层最终外盒，每节点恰一次 |
| partial/full clip、滚动与动态文案字号策略视口 | 来源可用性和层位置同代更新，纯滚动复用有效文字度量 |
| ROW/COLUMN/grid 内声明所属层 | 普通 gap、自然尺寸、列槽和 extent 不被层改变；既有 grid 600/400/160/601 行为保持 |
| 普通 placements 含层或内容捕获分支声明层 | 拒绝并指出实际来源/边界/声明位置 |
| 当前 modal 位于所属层中，来源祖先链及禁用来源 | 祖先链保留、后台层被隔离、层内 scope 与绘制顺序一致；禁用来源不压制有效背景层 |

证据采用正式 PageSession、真实 FT/HB、同批 geometry/PaintCommand 与已有内容回归；此空间验收不外推 MC、IME 或完整 GPU。

## 6. 输入参与与有效范围

INTERACTIVE 子树沿标准控件既有 click/focus/scroll/edit 规则参与；出现或重排不形成新的 Tab 循环、不重新执行范围初始焦点、不抢文字目标。显式 modal 才形成范围转换，进入与返回沿核心第 6.3.1 节。

PASSIVE 整个逻辑子树仅绘制，对下层指针透明，不成为 hover、手势、滚轮、焦点、键盘/文本或 ESC 目标。祖先 PASSIVE 继续约束嵌套 INTERACTIVE；子树内 modal 声明不合法，隐藏或来源无有效呈现也须诊断实际 layer/modal 声明位置。

范围归属按逻辑祖先，不能按 modal 外盒拒绝所属层中的合法点击。当前 modal 的控件与所属交互层是唯一前台输入范围，外部点击不同时激活后台。选择先消费本批来源空间可用性，再确定有效 modal 及最终呈现；不能先进入无来源的内层范围、触发失焦后再补偿。当前内层 modal 的来源祖先 Layer 保留必要呈现链，外层内容仍不接收输入。

来源可用性与前台参与分别核验：层因 modal 覆盖没有有效 Entry，不等于来源本身失效；覆盖期间的外层合法恢复目标按核心规则保存。来源完全裁掉、隐藏、禁用、卸载或销毁则永久取消原恢复资格，之后重新有效也不能复活该记录。范围终止及前台失活不隐含业务提交或草稿取消，正常用户导航失焦仍按编辑声明处理。

## 7. 导航、滚轮与联系结束

Tab/Shift+Tab 保持有效范围中的逻辑声明顺序，跳过 PASSIVE 与呈现失活 Layer；视觉分组及 sourceOrdered 内容捕获序不能作为导航序。主动导航只沿目标的呈现祖先显露，到所属 Layer 边界停止；可滚 Layer 自身及内部滚动容器，不以脱离普通流的屏幕坐标滚动来源面板。初始焦点、返回、普通位置和内容更新继续沿核心既有不追焦规则。

滚轮命中所属交互 Layer 后，在其内部呈现祖先中寻找可滚动目标，到该层边界停止；内部无目标也不能穿透来源或后台。未命中 Layer 的滚轮保持原范围规则，PASSIVE 对其下方内容透明。

被覆盖、层隐藏、来源失效、范围结束或卸载时，既有 FocusRouter/PointerRouter/Mount 终止失效联系；指针尾部吞到 physical end，来源恢复不复活 owner，UP 不能激活后台。组字联系终止及独立草稿去留沿编辑规格第 2 节，来源失活不暗触 BLUR 提交。明确移动来源保 UUID 与挂载身份，按新归属、来源空间与有效范围复核持续交互。永久拥有及页面结束沿动态结构/页面合同释放所属资源，工厂不关闭外部 policy。

## 8. 输入与公开消费验收

| 场景 | 可观察结果 |
| --- | --- |
| 来源 clip/scroll 面板打开标准按钮与输入框 Layer | 真实 FT/HB 内容在面板外可编辑和点击，业务 Signal 更新；普通出现不抢焦点 |
| 视觉层序与逻辑 Tab、PASSIVE 祖先下嵌套 INTERACTIVE | 导航保持逻辑顺序；整被动分支透明、不改文字或 ESC 目标、不成为 hover |
| 外层 modal 盒外所属 Layer、内层 modal、关闭与返回 | 所属点击成功且后台不响应；来源祖先链稳定，恢复有效外层焦点，外层结束带走内层及 Layer |
| source full clip/disabled/hidden/unmount，DOWN 后失效再 UP | 无失效内层范围；原手势、恢复与组字联系终止，后台不被尾部激活，业务草稿保留且无隐式提交 |
| Layer 内主动导航与 wheel，内部无scroll目标 | 仅显露/滚动层内祖先，不滚来源或后台；未命中层的 wheel 保旧规则 |
| 同页来源明确移动和新范围隔离 | 保节点/挂载连续性及合法草稿，按新范围继续或终止交互 |
| 隐藏 PASSIVE 中声明 modal | 可定位 layer/modal 的合法性诊断 |
| 正式 Gallery 页面片段与 null-parent coreJar payload | 真实打开、frame、pointer 发布业务值；Policy/create 类型来自 coreJar，关闭后外部策略仍有效 |

通过 layerInputScenario、pointerInputScenario、focusScopeScenario、focusRevealScenario、escapeInputScenario、dynamicStructureScenario、galleryScenario、coreArtifactScenario 核对注入 Core 行为；Gallery 复用正式共享声明与旅程。注入组字只验证目标隔离，不外推原生输入法、MC 或 GPU。

