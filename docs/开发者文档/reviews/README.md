# 审查报告

本文件作为审查报告索引，统一记录功能审查、开放化审查、易用性审查、代码评审结论等需要长期留档的结果。

详细报告存放在本目录下；本文件只保留指针、主题和必要摘要，避免单文件持续膨胀。

## 维护规则

- 新增审查报告后，必须在本索引增加条目，包含类型、详情文档和结论摘要。
- 后续复核发现旧审查存在误报、结论失效或能力边界变化时，必须同时更新原始审查报告正文和本索引。
- 原始报告正文使用 `后续复核（YYYY-MM-DD）` 批注标记，不删除历史判断；批注需说明当前正确口径和是否还需要修复。
- 只在本索引写摘要，不把完整修复过程或阶段流水账堆积到索引页。

## 审查记录索引

| 日期 | 简述 | 文档 |
|------|------|------|
| 2026-06-25 | scene 几何量与 clip 口径温床修复（B1 绝对坐标统一 + B3/I7 paint/hit-test clip 谓词统一） | [REVIEW-20260625-scene-geometry-clip-bugbed.md](REVIEW-20260625-scene-geometry-clip-bugbed.md) |
| 2026-06-25 | ink 紧凑 atlas mipmap 边缘硬裁边修复（UV/几何/uvBounds 协同外扩，烘焙羽化已回退） | [REVIEW-20260625-ink-mipmap-bleed.md](REVIEW-20260625-ink-mipmap-bleed.md) |
| 2026-06-18 | COMPOSITE 级失效连通坐实 + I7 粗粒度标脏债还清（reactive→DOM 接入审查阶段 2/3） | [REVIEW-20260618-composite-replay-and-i7-debt.md](REVIEW-20260618-composite-replay-and-i7-debt.md) |
| 2026-06-18 | reactive→DOM 失效层接入架构审查（P0 双重标脏，接入审查阶段 1） | [REVIEW-20260618-reactive-dom-invalidation.md](REVIEW-20260618-reactive-dom-invalidation.md) |
| 2026-06-18 | Scene 输入层 I1-I4 整条新输入层系统性收口审查（合并复盘） | [REVIEW-20260618-scene-input-i4-merge.md](REVIEW-20260618-scene-input-i4-merge.md) |
| 2026-06-18 | Scene 输入层点击聚焦 + emoji/codepoint 文本输入修复 | [REVIEW-20260618-scene-input-focus-codepoint.md](REVIEW-20260618-scene-input-focus-codepoint.md) |
| 2026-06-16 | NORTH_STAR 宪章对齐差距评估 | [REVIEW-20260616-north-star-alignment-gap.md](REVIEW-20260616-north-star-alignment-gap.md) |
| 2026-06-13 | 背景模糊系统配置化改造审查 | [REVIEW-20260613-backdrop-blur-config.md](REVIEW-20260613-backdrop-blur-config.md) |
| 2026-04-21 | lwjgl3ify 解耦输入后端审查 | [REVIEW-20260421-lwjgl3ify-decouple.md](REVIEW-20260421-lwjgl3ify-decouple.md) |

## 2026-06-25-scene-geometry-clip-bugbed
- 类型：scene 新栈架构温床修复（oracle 架构审核 B1 + B3/I7）
- 详情文档：[REVIEW-20260625-scene-geometry-clip-bugbed.md](REVIEW-20260625-scene-geometry-clip-bugbed.md)
- 审核方式：主 Agent 自审（reviewer 子代理两次空返回、原 session 失效，如实标注未经独立子代理复核）
- 结论摘要：**通过**。B1 删 3 个 primitive 私有 absoluteX/Y 裸累加，统一走 `SceneGeometry.absoluteBox`
  （已注入所有 scrollable 祖先 scrollOffsetY），数学等价且修复多层 scrollable 漏补偿，52 测试全绿。
  B3 抽 `SceneNode.isClipWindow()` 谓词供 paint/hit-test 共用，消除口径分裂。
  **关键核实**：fixer-2 推测成立——纯 clipChildren 节点 clip bounds 与自身盒重合，指针进子树前被
  父自身 bounds 检查（`SceneHitTester:93-96`）先行挡住，B3 当前无独立可观测 bug，修复价值为
  防御性口径统一（防未来 transform/scrollOffset 解耦时分裂）。569 scene 测试全绿。无需登记偏离。

## 2026-06-25-ink-mipmap-bleed
- 类型：字符渲染修复（方案① UV/几何/uvBounds 协同外扩 + 方案② 生成端烘焙 alpha 过渡带）
- 详情文档：[REVIEW-20260625-ink-mipmap-bleed.md](REVIEW-20260625-ink-mipmap-bleed.md)
- 结论摘要：两轮审核通过 + 真机验收通过。根因是 ink 子区 UV 精确贴字符 + shader uvBounds 硬墙 + padding 纯透明无过渡带，
  mipmap 降采样下边缘 AA 被 smoothstep 阈值化放大成硬边。最终方案仅保留方案①（FontBatchRenderer UV/几何协同外扩 INK_BLEED=1.0 像素，
  uvBounds 自动跟随）+ INK_PADDING 6→8。曾试方案②烘焙白色羽化但真机发现所有字符白边（半透明 alpha 参与 smoothstep 形成浅色描边），
  已回退删除。原理：不烘焙时 mipmap 降采样 ink 边缘 AA 像素自然渗透到 padding，UV 外扩让 shader 采到渗透 texel，过渡自然无白边。
  GlyphInfo/GlyphRuntimeTables/DefaultFontRendererAdapter 零改动，I6 守住。font 测试全绿。

## 2026-06-18-composite-replay-and-i7-debt
- 类型：reactive→DOM 接入审查阶段 2/3 收口（COMPOSITE 连通坐实 + I7 粗粒度标脏债还清）
- 详情文档：[REVIEW-20260618-composite-replay-and-i7-debt.md](REVIEW-20260618-composite-replay-and-i7-debt.md)
- 结论摘要：阶段 1 P0 双重标脏还清后继续收口接入审查列出的剩余两条债。**阶段 2（COMPOSITE，P1）**：explorer 侦察 + fixer 验证坐实 `UiStyleChangeImpact.COMPOSITE` 注释"当前降级为 PAINT"是**历史遗留过时表述**——实际 `ElementNode` listener 已走独立
  `markCompositeMutated`（只 bump compositeVersion 不碰 paintVersion）、`HtmlLikeDocumentWidget` 路②已有 composite-only 回放分支、`DocumentPaintEngine.tryApplyCompositeReplay` 真就地更新命令（非 stub，5 引擎用例已验），**COMPOSITE
  早已真连通**，信条五铁律达成。补齐此前缺失的 widget 层端到端命中测试 2 个（opacity-only / transform-only，三信号黑盒断言锁定路②：generation 不变排除重建 + paintVersion 不变 + compositeVersion +1 排除双未变），改正过时注释 + 给 RecordingUiRenderContext 加 transform
  no-op 覆写使 widget 级 transform 测试可在无 GL 沙箱运行。无生产逻辑改动。**阶段 3（I7 粗粒度标脏，P1）**：oracle 全新 session 审查**否决了 ERROR-20260617 原登记的方向 1（reconcileChildren 批量 API，200-400 行）为过度设计**——债的根因是
  `markSubtreeLayoutMutation` 的**无条件递归**而非"逐次提交"。给出**方案 X**（<10 行）：`DocumentNode.recordStructuralMutation` 把递归标脏 `markSubtreeLayoutMutation` 降级为只标自身 self+subtree+向上冒泡的 `markLayoutMutation`，
  根除所有结构入口（append/remove/insert/replace/fragment）的兄弟株连。**两个被我误判为命门的风险被 oracle 化解**：风险 A（删除双重移除）因不引入批量删除而消失、删除路径零改动；风险 B（分 display 模式漏标）是陷阱——DOM 层分模式标兄弟正是方向 2 撞 I6 的同款错误，正确做法是 DOM 层一律只标容器自己，
  "兄弟几何是否真变"全下放给 layout 复用闸门（flex 走 forced 维度、block 走 translatedTo 平移、table 走列宽 forced，闸门维度已完备）。reconciler/UiComponentRuntime/DocumentLayoutEngine/删除路径全部零改动。回归锚点
  `documentsKnownCoarseSubtreeDirtyMarkingDebt` 翻转为正向 I7 断言 `stableSegmentSubtreeIsNotDirtiedByListMutation`，新增 8 测试（5 DOM version 零株连 + 3 layout 端到端防漏标），全绿零回归。NORTH_STAR 偏离登记 I7 条 + ERROR-20260617
  均结案为已还清。

## 2026-06-18-reactive-dom-invalidation
- 类型：reactive→DOM 失效层接入架构符合度审查（oracle ora-2 session，对照 NORTH_STAR 信条五 + I4/I7/I8/I9）
- 详情文档：[REVIEW-20260618-reactive-dom-invalidation.md](REVIEW-20260618-reactive-dom-invalidation.md)
- 结论摘要：oracle 审查 reactive 层接入 DOM 命令式失效层的成熟度，**接入约 40%、reactive 层零 DOM 污染守住 I6、桥接层单点收口（UiComponentRuntime）设计正确**，但发现 **1 个 P0 架构债——双重标脏**。关键事实（侦察图未点明）：属性→impact
  映射**早已长在数据模型**（`StyleDeclarationSlot`/`updateProperty` 每属性硬编码正确级别，`ElementNode` 的 `UiStyleChangeListener` 在 setter 链路按属性槽自动打节点级精确脏标记）；而桥接层 `createEffect(impact, body)` 在 body 跑完后**又**按调用方手传 impact 调
  `recordGlobalLayoutMutation` 打**第二次全局粗粒度脏标记**——任何 LAYOUT 级 bind 触发即 **root 整树标脏**，比已登记的行 242 容器子树债还粗一个数量级，同时违反 I4/I7/信条二。现有 `bind*` 手传值「碰巧」与属性槽一致，正确性靠自动链路兜底，手传 impact 实为噪声 + 性能 bug。**焦点 2（COMPOSITE）
  **：基础设施其实已全通（compositeVersion + tryApplyCompositeReplay 回放路径俱在），`UiStyleChangeImpact` 那句"降级为 PAINT"注释**已过时**，需 transform-only 动画测试验证回放命中后改注释（P1，多半文档债）。**焦点 3（双轨）**：宪章行 9 要替代的是**写侧命令式 bump**，读侧版本比对是
  I8 合法缓存实现**应保留**，真冗余即 P0 那次全局二次标脏。**焦点 4（行 242 债）**：紧迫性上升但排 P0 之后（全树标脏会掩盖容器子树债的真机帧率信号）。给出阶段 0-4 收口路线图。**阶段 1（P0 还债）已落地并 `--no-ff` 合回 `4.0`**（merge `50b4a167`，单提交 `fa1fed61`）：删 `createEffect`/`bind`
  的 impact 参数与末尾全局 bump，标脏全交属性 setter 自动链路；exp 侦察坐实 5 类写入全自带节点级自动标脏、唯一 B 类（纯副作用 effect）本就无需标脏，删除安全；唯一断言调整 `compositeBefore+2→+1`（删冗余 bump 后的正确值，I9 批处理意图不变）；component+reactive 包 116 测试全绿、
compileJava 通过、
  零回归。

## 2026-06-18-scene-input-i4-merge
- 类型：Scene 输入层 I1-I4 整条新输入层系统性收口审查（合并复盘，ora-i4 session）
- 详情文档：[REVIEW-20260618-scene-input-i4-merge.md](REVIEW-20260618-scene-input-i4-merge.md)
- 结论摘要：oracle 对已合回 `4.0`（merge `c6d152d5`）的整条 I1-I4 新输入层做合并复盘审查，**整体有条件 PASS，无 P0 阻断**。I7/I9/I10/I11 核心不变量全部守住，reactive 地基去重改动（从 `Signal.set` 移到 `ReactiveScheduler.flush` 阶段1）经 Signal.java +
  ReactiveScheduler.java 全文逐条核验**安全无隐藏回归**（去重时机、I9 单点 flush、事务日志、undo/redo、不动点、可重入保护全部完好），并证实顺带根治了 scene 层"同帧 hover A→B→A 残留 true"瑕疵。逐条核验 10 组已登记观察点。**关键新发现 N1（P1，合并视角才暴露）**：显式 `requestPointerCapture`
  持有期间又来 POINTER_DOWN 时，隐式聚焦块会 `clearFocus` 而事件却被强制投给捕获节点，焦点机构与指针机构对同一 DOWN 做相反归属。真机零触发（当前无生产 capture 调用方），不破任何不变量，纯语义一致性问题，**登记为"拖拽/capture 功能生产化前置必修项"**（隐式聚焦块加 `capturedNode==null` 守卫）。N2（P2）
  capturedNode 无 Owner 绑定的轻量收口点随 capture 生产化一起做。其余观察点维持登记（YAGNI）或 Phase5 自然收口（I4c-O1 跨栈光标单例覆盖）。转修复项：守护正则豁免注释行（本轮已根治）、Router 过时 hover 注释（本轮已更新）。

## 2026-06-18-scene-input-focus-codepoint
- 类型：Scene 输入层 I4 真机暴露两 bug 的修复审查（点击无法聚焦 + 无法输出 emoji/codepoint）
- 详情文档：[REVIEW-20260618-scene-input-focus-codepoint.md](REVIEW-20260618-scene-input-focus-codepoint.md)
- 结论摘要：oracle 审查**有条件 PASS（0 阻断项）**。Bug1（点击无法聚焦）根因 = Router 的 CLICK 合成不自动聚焦、demo handler 也不调 `ctx.requestFocus()`；修复在核心层 `SceneInputRouter` 的 POINTER_DOWN 沿 `hitChain` 最深向 root 找首个 focusable 并
  `requestFocus`，点树内非 focusable / 树外均保持焦点不变（用户拍板不 blur），守住 I7 零标脏 / I11 只写 signal / I10 核心包零平台 import。Bug2（emoji 碎字符）根因 = 全链路以 16 位 char 为单位、lwjgl3ify 把 emoji 拆成两次 surrogate keyTyped；修复全在适配层：新建
  `SceneLwjgl3ifyTextBridge` 反射对接 lwjgl3ify onTextEvent（完整 String，external 模式）+ `LwjglInputSource` 降级路径 surrogate-aware 累积 + BACKSPACE 改 `offsetByCodePoints`，核心包零改动。1710 测试 9 失败=历史预存集，零回归。**待真机验收**：
  onTextEvent 真机路径沙箱无法验、探针待清。登记观察点：① Bridge remove API 可能为 null 致监听器泄漏隐患（真机确认 lwjgl3ify 是否提供 addWeak/remove）；② 守护正则 `\bInputEvents\s*\.` 误伤 Javadoc，建议后续豁免注释行。

## 2026-06-16-north-star-alignment-gap
- 类型：NORTH_STAR 宪章与当前实现的架构对齐差距评估
- 详情文档：[REVIEW-20260616-north-star-alignment-gap.md](REVIEW-20260616-north-star-alignment-gap.md)
- 结论摘要：以宪章 `NORTH_STAR.md` 第 3 节信条与第 5 节不变量为对照轴，基于源码逐条判定。核心结论——**渲染层（④DOM ⑤Layout ⑥Paint/Display List ⑦GL Render）已大体成形并持续优化；数据层（①signal ②reactive/effect ③组件只跑一次 + 中央事务）基本不存在**，当前更新链路是「命令式改 DOM →
  `markSubtreeMutated()` → `layoutVersion++/paintVersion++` 版本号失效」。可坐实差距：无 signal/effect/依赖图、无中央事务与状态快照（信条一/二/四、I1/I2/I3 未满足）；分级失效仅 LAYOUT/PAINT 两级、缺 COMPOSITE，且 TRANSFORM/OPACITY 归 PAINT（信条五部分对齐，与已知
  transform/hover 掉帧同源）；无 keyed 列表协调（I5 未满足）。已对齐资产：渲染层零 ElementNode 引用且 GL 收敛于此（I6 渲染侧达成），`DocumentPaintCommand` 为事实上的 Display List，脏子树布局缓存、样式 LAYOUT/PAINT 分级标注、合成层/字形图集/滚动免重建可作为重构地基。提纯项：Paint 层约 135
  处、命令自身约 21 处仍持有 ElementNode（I6 数据侧未达成）。给出 6 步重构优先级建议（数据层地基 → 响应式绑定与 keyed → 补 COMPOSITE → 提纯契约线 → 失效模型迁移 → 保留式 GPU 场景），均待用户立项。本评估为活地图，按重构批次回填。**【2026-06-20 后续推进】本报告"数据层基本不存在"的核心结论已被后续工作大幅推翻：
  `ui.reactive`（Signal/Effect/Computed/中央事务）已落地，`ui.scene` 新栈按 strangler 路线完成 Phase 0-3（声明式基石 + keyed 列表协调 + LAYOUT/PAINT/COMPOSITE 三级分级失效 + 合成级动画），与旧 `ui.dom` 栈物理隔离共存。原报告作旧栈差距历史快照保留，当前真实进度以
  `docs/记忆/当前态/` 与 NORTH_STAR《偏离登记》为准。**

## 2026-06-13-backdrop-blur-config
- 类型：背景模糊系统配置化改造与解耦性审查
- 详情文档：[REVIEW-20260613-backdrop-blur-config.md](REVIEW-20260613-backdrop-blur-config.md)
- 结论摘要：审查确认背景模糊设计解耦性良好（★★★★☆），使用标准 OpenGL API，MC 版本无关抽象设计完善，仅 Tessellator 和 LWJGL2 为轻微耦合点。新增 `BackdropBlurConfig` 统一配置类，提供 3 类常用参数（模糊上限、宿主级开关、宿主级强度）和 13 类高级参数（渲染路径控制、性能优化、调试诊断），内置三种预设（性能/质量/兼容性优先）。
  解耦原硬编码参数：`DocumentEffectChain` 模糊半径上限改为配置驱动、`UiBackdropFilterRenderer` Shader/固定管线/Tint 降级可独立开关、`UiHostBackgroundBlurRenderer` 支持强度调节、shader 代码模糊上限从 56 提升到 128。编译测试通过，向后兼容（原常量保留为 `@Deprecated`）。

## 2026-06-13-lwjgl3ify-decouple
- 类型：`lwjgl3ify` 输入解耦质量审查与修复归档
- 详情文档：[REVIEW-20260613-lwjgl3ify-decouple.md](REVIEW-20260613-lwjgl3ify-decouple.md)
- 结论摘要：审查确认输入后端解耦方向成立，并完成两批次修复：反射失败日志按方法/字段去重、系统光标区分初始化与运行时失败、fallback `REPEATED` 语义写入决策、InputEvents 时间戳优先读取、`UiKeyCodes` 补齐键码覆盖、`LwjglInputRuntime` 新增包内运行时可用性检查。执行计划中提到的测试编译基础设施问题在收尾复核时未复现，
  `compileTestJava` 当前通过。

## 2026-06-09-production-code-round9
- 类型：全项目生产代码第九轮审查
- 详情文档：[REVIEW-20260609-production-code-round9.md](REVIEW-20260609-production-code-round9.md)
- 结论摘要：接续第八轮远程 UI Runtime + Lease Protocol 修复后继续只读审查 `src/main/java`，避开 remote UI runtime 主线，发现 2 个 P2 问题：配置同步服务端 session 与 per-player Store 状态没有跟随玩家离线、远程配置页关闭或 remote page TTL 清理；Forge/FML 回退传输没有发送
  `META` capability handshake，切换 `netTransport=forge` 后 capability-gated 的配置模板远程同步会保持不可用。额外抽查 `ui/input`、`ui/host`、`internal/devtools` 未形成第三个可坐实 finding。
- 后续复核：2026-06-09 已修复 2 个 findings：配置同步服务端 session 与 `NetStore` per-player 状态现在随玩家离线、remote page 关闭和 TTL 过期清理；Forge/FML 回退传输新增等价 META handshake lifecycle，并通过配置生命周期、Forge handshake、
  NetService/RemoteDocumentPages/Vanilla lifecycle/TransportFactory 定向测试和 `compileJava`。

## 2026-06-09-production-code-round8-remote-ui-runtime-lease
- 类型：全项目生产代码第八轮审查（远程 UI Runtime + Lease Protocol）
- 详情文档：[REVIEW-20260609-production-code-round8-remote-ui-runtime-lease.md](REVIEW-20260609-production-code-round8-remote-ui-runtime-lease.md)
- 结论摘要：只读审查当前远程 UI Runtime + Lease Protocol 第一阶段实现与高风险网络生命周期边界，发现 4 个问题：Net Stream/Fetch 超时只在下一次入站包到来时触发；远程 UI 固定 TTL 没有主动 lease 清扫，过期可见关闭只在后续操作时触发；客户端异步失败/旧回调路径没有完整终止本地 mount；新 lease 协议字段在 decode
  阶段被补默认值，削弱显式 `surfaceId` / `contentRevision` / `closeScope` 校验。未发现 `NetService` 混入 keepalive / renew / remote UI 业务语义。
- 后续复核：2026-06-09 已修复 4 个 findings：Net 通用 timeout tick、`ui.remote` 主动 lease cleanup、客户端 mount discard / terminalize、协议 decode 显式字段校验均已落地并通过定向测试与 `compileJava`。

## 2026-06-09-production-code-round7
- 类型：全项目生产代码第七轮审查
- 详情文档：[REVIEW-20260609-production-code-round7.md](REVIEW-20260609-production-code-round7.md)
- 结论摘要：只读审查第六轮修复后的剩余高风险边界，发现并已修复 2 个问题：运行态 transform 统一参与 fixed containing block、clip chain、paint、hit-test 和 scroll metrics 口径；默认滚动和滚动条拖拽复用 hit-test suppression / `pointer-events:none` 口径，passthrough
  scrollable overlay 不再吃掉 wheel 或 scrollbar drag。remote UI session 本轮未形成新 finding，固定 TTL 与不隐式续期仍按现有决策处理。

## 2026-06-09-production-code-round6
- 类型：全项目生产代码第六轮审查修复
- 详情文档：[REVIEW-20260609-production-code-round6.md](REVIEW-20260609-production-code-round6.md)
- 结论摘要：修复 animation/scroll/remote/session/transition 生命周期 5 个问题：keyframe forwards fill 按 `animation-direction` 和最终迭代奇偶写入终值；transform 后滚轮与滚动条命中复用 hit-test 的 inverse transform 口径；
  `RemoteHudSubmitEvent.dismiss()` 改为 session 精确关闭；远程页面 screen 绑定 session/generation，手动关闭后旧 expired 不再弹错误页；`display:none` 中断运行中 transition 会派发 `transitioncancel`。

## 2026-06-08-production-code-round5
- 类型：全项目生产代码第五轮审查修复
- 详情文档：[REVIEW-20260608-production-code-round5.md](REVIEW-20260608-production-code-round5.md)
- 结论摘要：修复远程页面与远程 HUD session 生命周期 3 个问题：带 `sessionId` 的 HUD dismiss 不再按 `overlayId` 误关新 HUD；远程页面客户端用当前 session/generation 丢弃旧 stream 成功/失败回调；服务端远程 HTML session TTL 过期时向页面发送可见错误通知、向 HUD 发送
  session-scoped dismiss，避免过期后提交静默丢弃或 sticky dialog 静默停留。

## 2026-06-08-production-code-round4
- 类型：全项目生产代码第四轮审查
- 详情文档：[REVIEW-20260608-production-code-round4.md](REVIEW-20260608-production-code-round4.md)
- 结论摘要：接续第三轮修复后继续审查 `src/main/java` 生产代码，发现 5 个问题：移除展开 select 子树后只清 top-layer 注册但未关闭控件状态；`UiStyleDeclaration.copyFrom(...)` 公开样式复制绕过 layout/paint 失效版本；transform 下 select top-layer popup 使用未变换布局坐标锚定；
  HUD 用 `visibility:hidden` 隐藏注册项可被显式 visible 后代在 shared scene 渲染时绕过；select popup 关闭后 hover/cursor 不立即刷新。

## 2026-06-08-production-code-round3
- 类型：全项目生产代码第三轮审查
- 详情文档：[REVIEW-20260608-production-code-round3.md](REVIEW-20260608-production-code-round3.md)
- 结论摘要：本轮避开 `/qzuilib test` 与测试文件主线，聚焦 `src/main/java` 生产代码。发现 4 个问题：`textInput.preventDefault()` 不能阻止内置 input/textarea 改值；HUD 交互预过滤漏掉 top-layer 后代导致 select popup option 点击不可可靠进入真实路由；挂载后的
  `UiStyleSheet` 再变更不会触发布局/绘制缓存失效；移除已展开 select 子树后 top-layer 注册残留，造成状态泄漏和重挂载异常。

## 2026-06-08-ui-test-matrix-round2
- 类型：`/qzuilib test` 视觉/语义矩阵第二轮审查
- 详情文档：[REVIEW-20260608-ui-test-matrix-round2.md](REVIEW-20260608-ui-test-matrix-round2.md)
- 结论摘要：第二轮避开第一轮低优先级问题，转向断言强度和真实浏览器语义。发现 4 个问题：原生 `disabled` 表单元素仍可通过鼠标路径派发 `click`；Animation 自动断言可在未验证真实 transition/keyframe 生命周期和渲染样例时通过；`VIS-CTRL-007` 宣称 roving focus 但所有 tab 仍为 `tabindex="0"`；
  `VIS-CTRL-005` 选择 option 时直接调用 handler，绕过真实 top-layer 命中测试。

## 2026-06-08-ui-test-matrix-round1
- 类型：`/qzuilib test` 视觉/语义矩阵第一轮审查
- 详情文档：[REVIEW-20260608-ui-test-matrix-round1.md](REVIEW-20260608-ui-test-matrix-round1.md)
- 结论摘要：审查 registry、状态回写、批量运行、断言 runner 和测试覆盖一致性，发现 2 个中等优先级问题：`runAllAssertions()` 会污染各分组当前页索引，导致一键测试后打开分组默认停在最后一张样例；视觉状态模型已有人工通过/失败/已知缺口枚举和聚合逻辑，但页面没有人工结果回写入口，人工确认样例无法沉淀状态。

## 2026-06-06-ui-test-visual-assertions
- 类型：`/qzuilib test` 视觉样例断言代码审查与修复复核
- 详情文档：[REVIEW-20260606-ui-test-visual-assertions.md](REVIEW-20260606-ui-test-visual-assertions.md)
- 结论摘要：审查最新 CSS / Layout / Paint 视觉样例批次，发现 CSS 继承、overflow、block flow margin collapse、top-layer、scrollbar 与 host image fallback 多处样例/断言边界不一致。已修复可机器验证项；后续已将 scrollbar 的 overflow、scroll range 与 scrollTo
  偏移升级为自动断言，track/thumb 几何与拖拽仍保留人工观察边界；host image fallback 仍为人工待确认诊断。

## 2026-06-01-capability-gap-recheck
- 类型：浏览器能力缺口复核（取代 2026-05-18 结论）
- 详情文档：[REVIEW-20260601-capability-gap-recheck.md](REVIEW-20260601-capability-gap-recheck.md)
- 结论摘要：以当前源码为准重新核实，确认 `REVIEW-20260518` 正文结论已严重滞后——其列为"待实现/部分实现"的 20+ 项（transform 主体、sticky、flex order、border-collapse、text-shadow/transform/indent、white-space 五模式、list-style、`!important`、`::before/::
  after`、结构伪类、后代/子代选择器、contextmenu、dblclick、transitionend/animationend、CustomEvent、cloneNode、DocumentFragment、`<a>` 链接、select/checkbox/radio 等）已完整落地。当前真实剩余缺口约 23 主项：12 项完全未实现、约 11 项部分实现，另发现
  `DocumentScrollMetricsCalculator` 未跟随 fixed containing block 语义这一运行时一致性缺口。重点提示：剩余缺口需先按 A 类（已声明有意边界：Grid/float/gradient/transform 矩阵/baseline/完整 Web Animations/var() 等）与 B 类（性价比待评估真缺口）分级，
避免把有意边界当待补缺陷。
  `REVIEW-20260518` 正文结论以本复核取代。

## 2026-06-02-flex-min-content-runtime-pages
- 类型：运行时页面浏览器语义回归复查与修复
- 详情文档：[REVIEW-20260602-flex-min-content-runtime-pages.md](REVIEW-20260602-flex-min-content-runtime-pages.md)
- 结论摘要：运行时页面在浏览器语义修复后暴露的主要显示风险源于库侧 row flex item `min-width:auto` 仍用 max-content 近似，导致可换行文本比真实浏览器更难收缩。已将 auto 最小宽度改为 CSS-like min-content 测量，并将诊断页、配置模板和字体排序等确需等分收缩的 row flex 子项显式设置 `min-width:0`。

## 2026-06-01-browser-semantics-phase2-audit
- 类型：浏览器语义一致性审查（Phase 2）
- 详情文档：[REVIEW-20260601-browser-semantics-phase2-audit.md](REVIEW-20260601-browser-semantics-phase2-audit.md)
- 结论摘要：Phase 1 合并后系统性检查全子系统，发现 28 处与浏览器标准不一致（高 9 / 中 13 / 低 6）。高严重度集中在：min/max 约束应用顺序错误、负 margin collapse 不完整、flex item min-width 默认值为 0 而非 auto、flex-basis box-sizing 转换条件错误、insertBefore/replaceChild
  同父节点索引偏移 bug、position:fixed 不创建 stacking context、overflow+border-radius 裁剪缺失、disabled 布尔属性语义错误。P0 修复代价低且影响面大，建议优先处理。
- 首批 P0/P1 修复复核（2026-06-01，提交 `2d1bffa`）：逐项对照 DOM / CSS 2.1 / Flexbox / Positioned Layout 规范确认 8 项修复方向均向浏览器语义靠拢，离线复跑相关测试集全绿。明确三项后续未尽边界（非缺陷）：负 margin collapse 仅相邻兄弟完整、父子折叠路径仍为 max 近似（属 1.6 P3）；圆角 clip
  为圆形近似且仅双向 overflow 都裁剪时生效；fixed 仍无条件清空祖先 clip chain（属 2.3 P2/P3），均划入后续批次。
- 第二批 P2/低风险语义修复（2026-06-01）：按工程化分组收口 DOM、事件、样式、绘制四类作者可见契约：`removeChild` 返回/异常语义、`querySelector*` 排除内部根节点、`focusout` 独立冒泡与焦点切换顺序、hover/active 状态通知不中断祖先、`border-collapse` 继承、`font-style` 布局失效、inset
  box-shadow 绘制层级。复核时确认报告中 `text-shadow` "非继承"结论与 CSS 标准不符，已作为审查误报保留继承语义。
- 第三批 P2 事件语义修复（2026-06-01）：补齐 `wheel` DOM-like 事件分发，滚轮输入先按 capture → target → bubble 触发 `DocumentElementWheelEvent`，再执行默认滚动；handler 返回 `true` 只停止传播，不取消默认滚动，`preventDefault()` 会阻止默认滚动。仍未收口项包括 fixed
  clip chain、父子 margin collapse 递归等影响面更大的布局/视觉语义。
- 剩余语义工程化修复（2026-06-02）：详情见 [REVIEW-20260602-phase2-remaining-semantics.md](REVIEW-20260602-phase2-remaining-semantics.md)。已收口空块/递归 margin collapse、row flex `align-content`、flex 交叉轴 auto margin、
  absolute auto margin 居中、table auto 内容列宽、textInput capture 阶段、transform fixed containing block 下滚动范围一致性；复核确认 sticky stacking context 为审查误报。遗留：`inline-block baseline` 需要行内布局盒延迟落位，单独处理。

## 2026-06-01-browser-semantics-phase2-followup
- 类型：浏览器语义修复代码审查（Phase 2 后续批次）
- 详情文档：[REVIEW-20260601-browser-semantics-phase2-followup.md](REVIEW-20260601-browser-semantics-phase2-followup.md)
- 审查提交：`7371007`（合并入 `73a46e1`）
- 结论摘要：**通过**。7 项修复全部方向正确，代码质量达到工程化标准，测试覆盖完整，回归测试全绿。修复项：`removeChild` 返回值与异常语义（WHATWG DOM §4.2.6）、`querySelector*` 排除内部根节点（WHATWG DOM §4.5.6）、`focusout` 独立冒泡事件与焦点切换顺序（W3C UI Events §4.3.7，顺序为
  focusout→focusin→blur→focus）、hover/active 状态通知不中断祖先（CSS 伪类状态与事件分发层正确分离）、`border-collapse` 继承标记修正（CSS 2.1 §17.6）、`font-style` 变更影响级别从 PAINT 改为 LAYOUT、inset box-shadow 绘制层级修正（CSS Backgrounds Level 3
  §9，顺序为 outset shadow→background→inset shadow→border）。遗留：`focusin`/`focusout` 的 `cancelable: false` 语义未区分（已知取舍）；wheel 事件 DOM 分发（3.4 P2）本批未覆盖。

## 2026-06-01-wheel-dom-event
- 类型：wheel DOM 事件语义修复代码审查（Phase 2 第三批）
- 详情文档：[REVIEW-20260601-wheel-dom-event.md](REVIEW-20260601-wheel-dom-event.md)
- 审查提交：`f846cce`（合并入 `9a283f3`）
- 结论摘要：**通过**。对应 audit 报告 3.4，方向正确、达到工程化质量而非临时补丁。核对要点：`DocumentElementWheelEvent` 同时暴露原始 `wheelDelta` 与浏览器式 `deltaY`（取反正确）；`dispatchWheel` 与 `mousedown`/`mouseup` 同构，capture→target→bubble 顺序正确；严格遵守
  `DECISION-20260531`（返回 true 仅停止传播、`preventDefault()` 才取消默认滚动）；wheel target 由命中测试给出且不依赖 handler 注册，与默认滚动目标（最深可滚盒）解耦，符合浏览器 scroll chaining；滚动后 hover 刷新口径由 `consumed` 改为 `scrollState.getVersion()` 变化，
  更精确（修正 `ERROR-20260509` 根因）。遗留边界（非缺陷）：未实现 `deltaX` 横向滚轮、`deltaMode`/`deltaZ`、passive listener；fixed clip chain、父子 margin collapse 递归仍属后续批次。验证：`git diff --check` 通过；本轮离线 `test` 因 `ERROR-20260601`
  GitHub manifest 外部波动未复跑，待网络恢复补跑收口。

## 2026-05-25-project-code-structure-audit
- 类型：全项目代码结构深度审查（覆盖 UI / font / net / config / client / mixin / internal）
- 详情文档：[REVIEW-20260525-project-code-structure-audit.md](REVIEW-20260525-project-code-structure-audit.md)
- 结论摘要：当前主代码约 445 个 Java 文件、约 8 万行；项目主线设计仍然成立，对外入口与网络协议心智较克制，但内部能力扩张后出现第二层复用边界不足。审查时重点问题包括：稳定 API 清单与源码漂移、旧审查索引中的当前行数摘要过期、`ui.screen.example` 约 1 万行诊断/示例代码仍进主产物、远程页面与远程 HUD 的 session/Stream/提交逻辑重复、
  `ForgeConfigTemplateScreen` / `NetRuntimeSelfChecks` / `UiStyleDeclaration` 等子系统级大文件继续膨胀、public `__` 内部 API 与 input 反向依赖仍未收口。P0 文档漂移与旧索引过期摘要已完成整改；P1 已完成诊断页边界收口、远程页面/HUD session gateway 复用、
  配置模板绑定与文档构建拆分、网络自检注册/执行/远程 smoke 构造拆分；P2 已完成样式声明 paint-only slot 试点、HUD/TextArea/UiDocument 二轮拆分、`NetService` 内部协作者拆分和字体 mixin fallback 收口；P3 已完成兼容性收口：`__` public 内部 API 保持君子协定并补充文档边界，input
  反向依赖改为注册抽象，远程图片缓存拆分 clear/shutdown，CUSTOM renderer 使用边界已补充到文档与 Javadoc。

## 2026-05-08-html-like-developer-usability
- 类型：HTML-like 框架开发者易用性审查
- 详情文档：[REVIEW-20260508-html-like-developer-usability.md](REVIEW-20260508-html-like-developer-usability.md)
- 后续整改：已完成根节点默认全视口、完整业务页面示例、结构节点与交互控件边界说明，以及诊断入口链路收敛；整改状态只在索引页保留摘要，不回写原始审查正文。

## 2026-05-12-first-version-entry-truthfulness-and-boundary-credibility
- 类型：第一版开发者入口真实性与边界可信度审查
- 详情文档：[REVIEW-20260512-first-version-entry-truthfulness-and-boundary-credibility.md](REVIEW-20260512-first-version-entry-truthfulness-and-boundary-credibility.md)
- 结论摘要：业务开屏入口与显式诊断命令已经真实落地，但 HUD 交互边界、诊断入口封闭性、配置模板扩展性、宿主图片能力前提以及业务 API 与内部页面体系的隔离程度，仍存在"文档比实现更乐观"的问题。

## 2026-05-18-browser-capability-gap-audit
- 类型：浏览器常用能力差距审查
- 详情文档：[REVIEW-20260518-browser-capability-gap-audit.md](REVIEW-20260518-browser-capability-gap-audit.md)
- **结论已失效**：正文"30 项完全没有实现"等数字与清单已严重滞后，现状以 [2026-06-01-capability-gap-recheck](#2026-06-01-capability-gap-recheck) 为准；本条目仅保留历史审查价值。
- 结论摘要：共核查 65 项浏览器常用能力（CSS 布局/样式/选择器、事件、DOM、表单），其中 27 项完整实现、8 项部分实现（声明与实现不一致）、30 项完全没有实现。发现 `cursor` 属性声明链路完整但系统光标从未映射、`overflow-wrap`/`word-break` 样式已声明但布局引擎未消费、`font-weight`/`font-style` 底层有能力但 CSS
  属性层未暴露，三处属"文档比实现更乐观"。后续补齐状态：`cursor`、`overflow-wrap` / `word-break`、`focus()` / `blur()` / `scrollTo()` / `scrollIntoView()`、兄弟节点遍历、`font-weight` / `font-style`、`dblclick` / `contextmenu` /
  `transitionend` / `animationend`、`textarea`（含软换行两级行模型）、最小 `select`、flex `order`、`calc()` 最小混合长度、`position:sticky` 首阶段闭环、`text-shadow`、`text-transform`、`text-indent`、`white-space:
  pre/pre-wrap/pre-line` 和单图 `background-image` 已落地；`font-family`、`display:grid`、`transform`、gradient、多背景、多重阴影、`float` 和完整浏览器原生下拉能力仍按详情文档边界处理。

## 2026-05-21-animation-capability-assessment
- 类型：动画系统能力评估与增强方案审查
- 详情文档：[REVIEW-20260521-animation-capability-assessment.md](REVIEW-20260521-animation-capability-assessment.md)
- 结论摘要：当前动画系统已完整实现 transition、keyframe animation（多段 stop、fill mode）、三级影响分层和事件派发；Phase 1 已补齐 transform（PAINT 级矩阵注入、命中测试反向映射）与标准 cubic-bezier，Phase 2 已补齐 animation-direction / infinite / 定位属性动画，Phase 3
  首批已补齐 per-property transition timing、`steps(...)` 缓动、`ElementNode.animate(...)` 命令式入口，以及 `transitionstart` / `transitioncancel` / `animationstart` / `animationiteration` 事件。当前仍不承诺 keyframe per-stop
  timing、完整 Web Animations API 时间轴、暂停/反向播放等高级控制。布局引擎 `LayoutContext` 优化（pass-local 样式缓存、固有宽度缓存、positioned 预测量跳过）与动画方案完全兼容，不需要变更计划。

## 2026-05-20-ui-framework-structure-audit
- 类型：UI 部分代码框架结构审查（明确排除字体服务）
- 详情文档：[REVIEW-20260520-ui-framework-structure-audit.md](REVIEW-20260520-ui-framework-structure-audit.md)
- 结论摘要：该报告记录了当时 UI 分层、核心大类、示例/诊断代码、包结构、事件模板、input 反向依赖与命名规范等结构性风险，并给出 P0~P3 整改顺序。后续已完成死代码删除、事件取消语义统一、示例子包迁移、screen/style/control/host 包边界整理，以及布局、渲染、动画、Widget 等多处内部协作者提取。
- 当前状态说明：本条目只保留历史审查与整改方向；当前代码规模、热点文件和剩余风险以 [2026-05-25-project-code-structure-audit](#2026-05-25-project-code-structure-audit) 为新的基线，避免旧整改行数误导后续判断。
