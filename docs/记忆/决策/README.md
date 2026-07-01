# 决策记录

本目录用于记录关键技术取舍、架构边界决定和重要实现约束。

## 何时新增决策记录

- 多种方案都可行，但最终必须固定一种
- 某个选择会长期影响目录结构、接口边界或依赖策略
- 未来很可能有人问“为什么当时这么做”
- 某项约束不是错误，也不是 review，但必须被后续协作者知道

## 文件命名

- `DECISION-YYYYMMDD-主题.md`

## 建议模板

```md
# 决策：主题

## 背景

## 候选方案

## 最终选择

## 选择原因

## 影响范围

## 后续注意事项
```

## 索引

- [`DECISION-20260531-记忆框架.md`](DECISION-20260531-记忆框架.md) - 采用分层 AI 协作记忆框架，拆分规则层、当前态层、长期事实层和决策层
- [`DECISION-20260531-event-return-value-vs-prevent-default.md`](DECISION-20260531-event-return-value-vs-prevent-default.md) - 事件 handler 返回值只停止传播，取消默认行为统一依赖 `preventDefault()`
- [`DECISION-20260601-visual-traversal-shared-semantics.md`](DECISION-20260601-visual-traversal-shared-semantics.md) - 新增共享视觉遍历层 `DocumentVisualTraversal`，统一 paint / hit-test / scroll 的 
  `fixed/sticky`、clip 链与 stacking phase 语义
- [`DECISION-20260601-font-family-deferred.md`](DECISION-20260601-font-family-deferred.md) - font-family 暂不接通，底层字体引擎无字体族维度，归为后续字体运行时改造专项，避免产出"只记录不生效"的假能力
- [`DECISION-20260601-textarea-soft-wrap-deferred.md`](DECISION-20260601-textarea-soft-wrap-deferred.md) - 历史决策：textarea 软换行曾暂缓并要求先重构行模型；现已被逻辑行 + 视觉行两级模型实现取代
- [`DECISION-20260601-textarea-soft-wrap-two-level-lines.md`](DECISION-20260601-textarea-soft-wrap-two-level-lines.md) - textarea 软换行采用逻辑行与视觉行两级模型，统一显示、caret、选区、点击、上下移动和滚动几何
- [`DECISION-20260618-text-input-model-and-merge-write.md`](DECISION-20260618-text-input-model-and-merge-write.md) - 文本输入控件用即时可变模型 + signal 单向派生（handler 不读 signal 当文本）；
  reactive 去重从 Signal.set 移到 flush 阶段对比帧初值，实现 I9 同帧写入合并语义
- [`DECISION-20260620-scene-composite-opacity-group-transform-offset.md`](DECISION-20260620-scene-composite-opacity-group-transform-offset.md) - scene 合成级 opacity/group/transform-offset 失效双通路方案
- [`DECISION-20260605-test-visual-matrix-collaborators.md`](DECISION-20260605-test-visual-matrix-collaborators.md) - `/qzuilib test` 视觉矩阵拆成 registry、分组视觉 builder、语义 checker 和结果 state，控制器只保留生命周期与导航
- [`DECISION-20260606-html-text-paint-clipping.md`](DECISION-20260606-html-text-paint-clipping.md) - HTML-like 长文本优先在绘制阶段按 overflow clip 保守裁剪，不截断 DOM 语义，跨帧布局缓存后续再做
- [`DECISION-20260606-dirty-subtree-layout-cache.md`](DECISION-20260606-dirty-subtree-layout-cache.md) - HTML-like 脏子树布局缓存先建立节点级脏版本与静态 block-flow 子树复用骨架，后续再扩展 flex/table/inline
- [`DECISION-20260608-remote-html-session-ttl.md`](DECISION-20260608-remote-html-session-ttl.md) - 远程 HTML session TTL 同时覆盖 HTML 拉取与交互提交，过期必须通知客户端错误或关闭对应 HUD
- [`DECISION-20260609-remote-ui-runtime-lease-protocol.md`](DECISION-20260609-remote-ui-runtime-lease-protocol.md) - 后续远程 UI 重构采用内部 Runtime + 显式 Lease 协议，区分 session、surface、revision、asset 与 
  closeScope，保持 NetService 通用边界
- [`DECISION-20260610-character-font-rules.md`](DECISION-20260610-character-font-rules.md) - 字符级字体覆盖采用 `FontMatcher` 规则优先表，不接入 CSS `font-family` 样式维度
- [`DECISION-20260610-select-large-list-virtualization.md`](DECISION-20260610-select-large-list-virtualization.md) - `DocumentSelectControl` 大列表采用控件级虚拟化，保留完整数据语义但只渲染可视窗口 DOM
- [`DECISION-20260611-font-size-before-latex-math.md`](DECISION-20260611-font-size-before-latex-math.md) - 先扩展字体引擎字号能力，再实现 LaTeX 风格数学公式排版，避免公式渲染绕开项目字体系统
- [`DECISION-20260611-awt-ink-bounds-atlas.md`](DECISION-20260611-awt-ink-bounds-atlas.md) - 字体 atlas 引入 AWT baseline + actual pixel bounds 与可变 slot 契约，替代旧缩字号塞固定方格策略
- [`DECISION-20260612-shared-virtualized-option-list.md`](DECISION-20260612-shared-virtualized-option-list.md) - 抽取内部固定行高虚拟候选列表 helper，复用 select 与 autocomplete 的大候选渲染性能逻辑
- [`DECISION-20260612-lwjgl3ify-input-backend.md`](DECISION-20260612-lwjgl3ify-input-backend.md) - `UiInputService` 抽内部输入后端，反射接入 `lwjgl3ify` `InputEvents`，并以 `UiKeyCodes` 收拢业务层键码常量，发布产物不再声明该 Mod API 
  硬依赖
- [`DECISION-20260613-page-scoped-backdrop-blur-policy.md`](DECISION-20260613-page-scoped-backdrop-blur-policy.md) - 背景模糊采用页面级不可变策略与 `UiDocument` 运行时控制器，避免修改全局配置污染其它页面
- [`DECISION-20260613-modern-config-template-optional-module.md`](../开发者文档/legacy/DECISION-20260613-modern-config-template-optional-module.md) - Modern Config 模板页以运行时 config 模块检测为主用入口，Forge 配置页仅作为回退，不内置迁移
  **【已归档到 `docs/开发者文档/legacy/`，旧栈已拆除，被 DECISION-20260628 取代】**
- [`DECISION-20260614-modern-config-template-screen-no-split.md`](../开发者文档/legacy/DECISION-20260614-modern-config-template-screen-no-split.md) - ModernConfigTemplateScreen（846 行）不拆分 Spec/FieldSpec 为独立文件：
  未达硬门槛，拆分将大面积改动已定稿的 binding/TypeInference/SearchIndex 引用，违反批次边界
  **【已归档到 `docs/开发者文档/legacy/`，旧栈已拆除，被 DECISION-20260628 取代】**
- [`DECISION-20260614-modern-config-performance-optimization.md`](../开发者文档/legacy/DECISION-20260614-modern-config-performance-optimization.md) - ModernConfig 配置页面系统性性能优化：P0 防抖+增量索引+差量列表、P1 分批构建+延迟加载、P2 虚拟化+Binding 复用
  **【已归档到 `docs/开发者文档/legacy/`，旧栈已拆除，被 DECISION-20260628 取代】**
- [`DECISION-20260614-modern-config-phase3-optimization.md`](../开发者文档/legacy/DECISION-20260614-modern-config-phase3-optimization.md) - ModernConfig Phase3 性能优化方案
  **【已归档到 `docs/开发者文档/legacy/`，旧栈已拆除，被 DECISION-20260628 取代】**
- [`DECISION-20260614-host-background-blur-default-off.md`](DECISION-20260614-host-background-blur-default-off.md) - 宿主级背景模糊全局默认关闭并修复 capture 无条件全屏快照；性能优先基线，需要模糊的页面用页面级 BackdropBlurPolicy 显式开启
- [`DECISION-20260614-modern-config-paint-style-cache.md`](../开发者文档/legacy/DECISION-20260614-modern-config-paint-style-cache.md) - ModernConfig 绘制重放对每条命令递归到根的 `compute()` 改为单趟 ComputedStyle 备忘（经 
  computeWithParentStyle 复用祖先链）；2026-06-15 实测证伪：修复已编译但 render/fps 零改善，compute 非 ~3FPS 瓶颈，修复保留不回滚
  **【已归档到 `docs/开发者文档/legacy/`，旧栈已拆除，被 DECISION-20260628 取代】**
- [`DECISION-20260615-shared-text-layout-engine.md`](DECISION-20260615-shared-text-layout-engine.md) - TextArea/CodeEditor/TextInput 抽取共享 `TextLayoutEngine` + `VisualLineLayout` + 前缀宽度向量；
  每帧 O(N²) 逐前缀 `measureTextWidth(substring)` 改 O(N) 增量，按内容+宽度+字体 epoch 缓存稳态零测量，测量与绘制解耦让 selection/caret 两层共享一次结果
- [`DECISION-20260616-north-star-charter.md`](DECISION-20260616-north-star-charter.md) - 引入根目录 `NORTH_STAR.md` 作为 UI 系统架构宪章（最高准绳），接入文档/记忆导航与 AGENTS 规则；
  本轮仅文档不动源码，大型重构据宪章分批另行立项，主战场为尚不存在的数据层（signal + 中央事务 + effect）
- [`DECISION-20260617-scroll-focus-no-signalization.md`](DECISION-20260617-scroll-focus-no-signalization.md) - 滚动态（C4）与 focus 投影不做 signal 化：滚动偏移不改 DOM 属性、是渲染层视口态故 I1 无缺口，signal 化撞 I6/帧末批处理铁律并倒退 I7/I8；
  方案 A 全 DOM 改坐标是 pre-RenderingNG 淘汰模型不回退；`focusEpochSignal` 唯一消费者零收益维持过渡桥，除非出现第二个 focus 投影消费者否则不升级（经三方裁决 + 复盘）
- [`DECISION-20260618-reactive-dom-invalidation-version-as-cache-key.md`](DECISION-20260618-reactive-dom-invalidation-version-as-cache-key.md) - 澄清 NORTH_STAR 行 9「effect 标记替代版本号模型」语义：
  要替代的是写侧命令式 bump（控件手调 recordXxxMutation），读侧版本号比对作为 I8 缓存命中实现保留不视为违背；据此 P0 还债删除 `UiComponentRuntime.createEffect`/`bind` 的 impact 参数与末尾全局标脏，标脏完全交属性 setter 自带的节点级精确自动链路
- [`DECISION-20260621-scene-textinput-tier-a-declarative-caret.md`](DECISION-20260621-scene-textinput-tier-a-declarative-caret.md) - scene 新栈 TextInput 批 3 锁定档位 A（caret 恒末尾、无选区/方向键/字符级定位/IME/剪贴板/闪烁，
  受控 value+onChange 行为等价旧栈）用全声明式 caret 子节点（靠 ROW 布局自然定位、聚焦态恒亮、零度量零核心侵入）还清旧栈命令式 render caret 范式债；辨析「功能维度 vs 范式维度正交」纠正前两轮 oracle 误读用户偏好；
  β（paint 注入 measurer）永久否决（撞 I6/破 I7-I8 fragment 复用/污染 SceneNode 纯数据地基），γ（SceneRuntime 薄委托 measureTextWidth）留作档位 B 字符级定位唯一回填方向，本批无偏离
- [`DECISION-20260622-scene-textinput-b1-character-caret.md`](DECISION-20260622-scene-textinput-b1-character-caret.md) - 用户重新拍板 Demo2 输入控件后，`SceneTextInput` 从档位 A 升级为 B1 字符级单行输入框核心版：
  闭包局部 `caretIndex` signal、prefix/caret/suffix 三节点、点击定位、方向键/Home/End、中间插入、Backspace/Delete；`SceneRuntime` 增加 `SceneTextMeasurer` 窄端口只读测量，宿主 runtime/layoutEngine 同源 measurer 双注入；
  本期不做选区/剪贴板/IME/闪烁/横向滚动，无需 NORTH_STAR 偏离登记
- [`DECISION-20260622-qzui-test-scene-hub.md`](DECISION-20260622-qzui-test-scene-hub.md) - `/qzuilib test` 后续使用 scene 新栈 test hub 承载；
  第一批只做新栈首页/导航容器并挂接已有 Scene/Controls/Scroll/Table demo，不迁旧视觉矩阵和旧断言 runner；旧 HTML-like / `ui.dom` 栈暂不删除但退出实际业务接入
- [`DECISION-20260622-scene-layout-intrinsic-width-first.md`](DECISION-20260622-scene-layout-intrinsic-width-first.md) - scene 样式/CSS 能力优先补排版地基而非 token/theme；
  P0 实现 `WidthSizing.SHRINK` 容器内容宽回收并还清 Breadcrumb 字符宽估算债，后续再评估 flex-grow、align-self、min/max
- [`DECISION-20260623-scene-overlay-foundation.md`](DECISION-20260623-scene-overlay-foundation.md) - Scene 浮层控件地基按通用 top-layer 建设，覆盖 overlay roots、stacking、跨 clip 绘制、浮层优先命中、anchor 定位、dismiss 与 Owner 
  清理；`SceneSelect` 只是首个消费者和验收用例
- [`DECISION-20260623-scene-modern-config-foundation.md`](../开发者文档/legacy/DECISION-20260623-scene-modern-config-foundation.md) - Scene 现代配置页不直接搬迁旧 DOM 12 模板页，先补通用 `top-layer/overlay` 地基和 `SceneSelect`，再做一期 
  `STRING/NUMBER/BOOLEAN/CHOICE`、扁平分类、草稿保存和真实配置数据适配；inline listbox 仅作临时探针或降级兜底
  **【已废弃，被 DECISION-20260628 取代；已归档到 `docs/开发者文档/legacy/`，旧栈已拆除】**
- [`DECISION-20260624-overlay-anchor-hit-test-frame-delay.md`](DECISION-20260624-overlay-anchor-hit-test-frame-delay.md) - overlay 锚定 hit-test 滞后一帧属 retained-mode 固有延迟（非 replay 视觉错位），
  视觉零错位、触发面极窄（仅 page 级滚动同帧命中）、不破 I7/I8/I11，接受不修
- [`DECISION-20260624-scene-viewport-overlay-promotion.md`](DECISION-20260624-scene-viewport-overlay-promotion.md) - scrollable 视口与 overlay 多 paint root 转正为宪章一等能力（§4/§4.5 正文追加，
  不新增不变量，不改代码，纯文档对齐）
- [`DECISION-20260624-scene-unstyled-primitives.md`](DECISION-20260624-scene-unstyled-primitives.md) - scene 控件层建立 public unstyled primitive + styled wrapper：
  `flat` 仅作战术过渡，TextInput/Select 行为内核与默认 chrome 分层，DataTable 等高级控件消费 primitive
- [`DECISION-20260625-text-vertical-alignment-refactor.md`](DECISION-20260625-text-vertical-alignment-refactor.md) - 文本垂直对齐改用 em-box 居中模型（emHeight=fontSize），
  与字体渲染器 em-box 顶锚点一致；推翻前一次 half-leading content-area 模型（坐标系错配）
- [`DECISION-20260625-perform-layout-step-order-refactor.md`](DECISION-20260625-perform-layout-step-order-refactor.md) - performLayout 五步骤重构为 A/B/C/D 四步骤（容器尺寸先定再定位子），
  消除 ROW 交叉轴居中 bug 的结构性温床；已补债完成（commit 54974ec7）
- [`DECISION-20260625-b2-textarea-text-geometry.md`](DECISION-20260625-b2-textarea-text-geometry.md) - TextArea O(N²) 文本几何消除选 scene 内自建轻量缓存（方案 B(R1)），
  否决复用旧栈 TextLayoutEngine（撞 I6/I10）；关键陷阱：scene measureWidth 含 ceil+round 双取整，
  逐码点相加会漂移且 FixedTextMeasurer 线性导致测试假绿，buildPrefixWidths 函数体禁改
- [`DECISION-20260625-primitive-coloring-baseline.md`](DECISION-20260625-primitive-coloring-baseline.md) - primitive 上色基调：暴露只读状态 + wrapper 单向供颜色 token，
  禁止可写颜色 signal 反灌；TextArea caret 上色对齐 TextInput 范式，补齐文本三态色控制
- [`DECISION-20260625-text-vertical-alignment-research.md`](DECISION-20260625-text-vertical-alignment-research.md) - 文本垂直对齐问题研究（Oracle 产出，em-box 居中模型前置研究，已实施 commit a05ea1c8）
- [`DECISION-20260626-b4-column-fill-on2-deferred.md`](DECISION-20260626-b4-column-fill-on2-deferred.md) - B4 COLUMN fill O(n²) 约束判定缓做记录（单容器子数小 + 干净帧短路，沿用接受口径）
- [`DECISION-20260626-b6-transform-clip-fbo-deferred.md`](DECISION-20260626-b6-transform-clip-fbo-deferred.md) - B6 transform+clip 叠加坐标错位 FBO 方案评估与推迟（批 1 已落地，批 3 纹理缓存待性能暴露）
- [`DECISION-20260626-layer-not-promoted-to-contract.md`](DECISION-20260626-layer-not-promoted-to-contract.md) - 图层概念不上契约层 + 契约层 GL 术语清零
- [`DECISION-20260627-display-list-contract-line.md`](DECISION-20260627-display-list-contract-line.md) - Display List 契约线阶段 1 落地 + 并发框架方向（measurer 并发底座 + 子树并行基建，运行路径撤走只留基建）；原"slider 不修"拍板已于 2026-06-28 推翻，缺陷 D 由 DECISION-20260628 独立根治，并发方向独立保留
- [`DECISION-20260628-scene-min-max-clamp.md`](DECISION-20260628-scene-min-max-clamp.md) - scene min/max clamp + percent + margin + align-self 四期 deepwork oracle 8 项裁决：
  maxHeight 声明式元数据 + 路径甲 freeze do-while 守 I7 + clamp 优先级 min 赢 CSS + align-self 独立枚举 + margin 五处联动 + percent 父先验基准 fallback shrink grow 优先
- [`DECISION-20260628-scene-slider-defect-d-fix.md`](DECISION-20260628-scene-slider-defect-d-fix.md) - SceneSlider 缺陷 D（松手提交偶发丢失）根治：修法甲 + 全面重构
  （draggingValue 降级纯渲染只写不读 + 事件坐标当场算提交值 + capture 托管 + NaN 防御）；
  拖拽类控件范式约束"瞬态 signal 只写不读、业务值用事件坐标当场算"；推翻 DECISION-20260627"slider 不修"拍板
- [`DECISION-20260628-scene-l1-grow-prior-asymmetry.md`](DECISION-20260628-scene-l1-grow-prior-asymmetry.md) - L1 嵌套 grow 子容器场景修复：priorKnownInnerHeight 闸门不对称判定
  （只认 fill 不认 grow/percent）→ 对齐 computeHeight 三合流口径 + 排除 scrollable；
  Oracle 裁决 L1 是缺陷非有意边界（CSS §9.8 definite 语义）
- [`DECISION-20260628-modern-config-new-mental-model.md`](DECISION-20260628-modern-config-new-mental-model.md) - 现代化配置页全新思维模型——三态四层软依赖架构
  （Authority/DraftBuffer/Persistence 三态 + 核心层/UI层 四层 + config 零硬依赖 uilib 软依赖）；
  废弃 DECISION-20260623，不参考旧栈完全重新设计
- [`DECISION-20260630-coordinate-system-flutter-alignment.md`](DECISION-20260630-coordinate-system-flutter-alignment.md) - 指针坐标系对齐 Flutter 三件套（raw/host/local 三层 + 框架自动注入 local）；
  根治 scrollbar/slider/textInput/textArea 同款 rootAbs 错位；I12 契约禁止 raw 与 absoluteBox(0,0) 混比；
  分 3 轮落地（第 1 轮主树+I12+3 控件同系修正 commit `0da919b6`，第 2 轮 overlay localPointer+结构对齐，第 3 轮旧 API 改名）
- [`DECISION-20260701-archunit-removal-review-discipline.md`](DECISION-20260701-archunit-removal-review-discipline.md) - 移除 ArchUnit 自动守卫，L2 纯数学边界改评审纪律 + package-info 声明 + AGENTS.md 索引三重软约束
- [`DECISION-20260701-scene-container-factory-and-chain-setter.md`](DECISION-20260701-scene-container-factory-and-chain-setter.md) - scene 布局引擎收口 P2 批次：setter 链式化 + 4 个静态工厂糖 + bind impact 去留（待用户拍板）；P0/P1 已完成
