# `/qzuilib test` 视觉优先测试矩阵规划

本文保存 2026-06-05 对 `/qzuilib test` 新运行时矩阵的规划结论。新的测试页目标顺序是：先视觉化展示 UILib 能力，再验证功能是否符合浏览器语义。

## 当前结论

- 当前源码中的 `/qzuilib test` 只保留 DOM / CSS / Layout / Paint / Input / Controls / TextFont / Animation / RuntimeHost / RemoteNet 分组壳，运行时矩阵已清空。
- 后续不直接恢复旧矩阵和旧七字段卡片；旧编号和语义可作为素材，但展示模型、状态模型和自动/人工边界需要按本文重新设计。
- 首页应从“测试用例列表”改成“能力画廊 + 语义覆盖热力图 + 最近失败 + 人工任务”。
- 二级页应以真实视觉样例为主体，语义断言作为样例附属能力，而不是让测试按钮成为页面核心。
- 继续保留现有 10 个分组导航，降低入口、文档和现有测试调整成本。

## 文件规模约束

- `UiTestDocumentPageController` 已是超大文件，重建矩阵时不要继续把 registry、样例构建、断言和状态刷新全部堆回该类。
- 新增实现应优先拆成页面控制器、用例 registry、分组视觉 builder、语义 checker、结果 state 等协作者。
- 单个代码文件接近或超过 1000 行时必须评估职责拆分；拆分依据是职责、变更频率和复用边界，不是机械追求行数。

## 首页结构

| 区域 | 内容 | 验收要点 |
|---|---|---|
| 顶部说明 | test 版本、窗口尺寸、字体 epoch、网络模式、运行时统计 | 截图即可定位环境 |
| 功能画廊 | 代表性 UI 能力样例：布局、控件、文本、动画、overlay、远程能力 | 页面打开后先看到能力展示 |
| 语义覆盖热力图 | 10 个分组的视觉状态、语义状态、自动/人工数量、缺口数量 | 一屏内看清覆盖和风险 |
| 快速筛选 | 全部、视觉展示、自动语义、人工确认、已知缺口、失败 | 便于定位待处理项 |
| 最近失败 | 最近失败编号、实际结果、差异说明、复现入口 | 可直接交接 |
| 人工任务 | 只列需要人眼或游戏内确认的样例 | 每项必须有 `预期结果：...` |
| 环境信息 | Minecraft、Forge、LWJGL3ify、字体、窗口、鼠标、网络模式 | 保留现有动态刷新能力 |

## 二级页结构

| 区域 | 内容 | 验收要点 |
|---|---|---|
| 分组说明 | 当前分组覆盖范围、语义目标、已知边界 | 不恢复旧页面名结构 |
| 视觉样例区 | 默认展开高优先级样例，带标尺、标签、状态文本和事件日志 | 不需要点击按钮才知道在测什么 |
| 语义检查区 | 自动断言、人工确认、已知缺口、诊断摘要 | 自动与人工边界清晰 |
| 操作区 | 运行语义检查、重置样例、人工通过、人工失败、复制结果 | 结果能回写卡片状态 |
| 诊断区 | DOM 摘要、computed style、布局盒、命中结果、绘制/事件日志 | 失败可复现和交接 |

## 卡片字段

| 字段 | 要求 |
|---|---|
| 编号 | 建议使用 `VIS-CSS-001`、`SEM-LAYOUT-003` 等新前缀，旧编号可作为来源备注 |
| 展示目标 | 写明人能看到的能力，不写笼统页面名 |
| 浏览器语义 | 写明对应 DOM / CSS / Layout / Paint / Input 语义 |
| 视觉样例 | 必须真实绘制样例；需要层级、命中、滚动或事件时要直接画出观察目标 |
| 观察要点 | 必须以 `预期结果：` 开头，文本能被截图直接验收 |
| 语义断言 | 写明自动检查对象；不可自动时写明人工原因 |
| 视觉状态 | 使用独立视觉状态，避免自动通过掩盖视觉问题 |
| 语义状态 | 使用独立语义状态，避免视觉好看但语义错误 |
| 实际结果 | 自动或人工填写，不能直接拼接 Java 值对象默认地址 |
| 差异说明 | 失败时输出可复制的期望/实际摘要 |

## 状态模型

| 维度 | 状态 |
|---|---|
| 视觉状态 | 未观察、展示中、人工通过、视觉失败、已知视觉缺口 |
| 语义状态 | 未断言、自动通过、自动失败、人工待确认、已知语义缺口 |
| 汇总状态 | 通过、部分通过、失败、待确认、缺口 |

## 首轮矩阵

| 分组 | 首轮重点 | 建议数量 |
|---|---|---:|
| CSS | cascade、inheritance、box-sizing、background、visibility/pointer-events、overflow | 6 |
| Layout | block flow、margin collapse、inline/inline-block、flex min-content、table auto、fixed/sticky | 6 |
| Paint | stacking、opacity、clip、transform、top-layer、scrollbar、host image fallback | 7 |
| Input | capture/bubble、preventDefault、wheel、focus-visible、keyboard/textInput | 5 |
| Controls | button、input、textarea、checkbox/radio、select、slider/toggle/tab | 7 |
| TextFont | raw/formatted、baseline、fallback、wrap/trim、obfuscated | 5 |
| Animation | transition、keyframes、timing、fill-mode、layout-vs-paint | 5 |
| DOM | append/insert/replace/remove、fragment、textContent、classList、selector、link default | 7 |
| RuntimeHost | open timing、resize、runtime stats、HUD/container input、exception panel | 5 |
| RemoteNet | channel、fetch、stream、store、remote page/HUD、config sync、transport mode | 6 |

首轮总量按上表合计约 59 张，实际实现应分批推进，每批 8 到 12 张，优先 CSS / Layout / Paint / Controls 这类视觉收益最高的分组。

## 自动断言边界

| 类型 | 自动断言方式 |
|---|---|
| DOM | 节点归属、返回值、子节点顺序、textContent、classList、selector 结果 |
| CSS | computed style、继承结果、specificity 结果、可见性与 pointer-events 状态 |
| Layout | 布局盒尺寸、位置、margin collapse、flex/table 分配、scroll 范围 |
| Paint | 绘制命令顺序、stacking phase、clip/transform/top-layer 命中 |
| Input | 事件日志、传播顺序、默认行为、focus/focus-visible、wheel 滚动结果 |
| Controls | value、checked、selection、caret、disabled、change 日志 |
| TextFont | 测量宽度、line-height、wrap/trim 摘要、字体 epoch |
| Animation | timeline 状态、start/end/cancel 日志、最终样式 |
| RuntimeHost | 多数需要游戏内人工确认，自动只检查入口和状态摘要 |
| RemoteNet | 服务端往返、远程页面、HUD、配置保存需要游戏内确认 |

## 实施顺序

| 阶段 | 目标 | 完成标准 |
|---|---|---|
| P0 | 定稿视觉优先规格与数据模型 | 文档明确字段、状态、分组、自动/人工边界 |
| P1 | 首页视觉框架 | 首页展示能力画廊、热力图、筛选、环境信息 |
| P2 | CSS / Layout / Paint 核心样例 | 最能体现浏览器语义的视觉样例先落地 |
| P3 | Input / Controls / TextFont | 补齐交互、表单、文本字体类可观察样例 |
| P4 | Animation / RuntimeHost / RemoteNet | 补齐动画、宿主、远程和网络链路 |
| P5 | 结果交接 | 失败摘要、复制结果、最近失败、人工任务稳定可用 |

## 验证要求

- 每批至少通过 `git diff --check`、目标 `UiTestDocumentPageControllerTest` 和 `compileJava`。
- 涉及游戏内 UI、HUD、输入、命令入口、远程页面或网络 smoke 的能力，必须使用 `runClient21` 验证。
- 视觉语义样例要用 JVM 测试覆盖关键文本、按钮、分组入口和状态刷新，避免只靠人工观察发现断裂。
- CSS specificity 等样例不能用 inline 样式覆盖被测属性；视觉摘要不能直接拼接 Java 值对象默认地址。

## 后续协作提示

- 实现前先从 `4.0` 切分支，不在主分支直接开发。
- 不要继续修补旧运行时矩阵；先以本文模型重建 registry、builder、checker 和状态模型。
- 若新增或删除 test 分组，同步更新本文、`qzuilib-test-page-rebuild-plan.md`、`docs/使用文档/04-诊断入口/指令触发方案.md` 和 AI 当前态。
