# scene 文本输入能力补齐任务清单

> 状态：P0 完成；Phase D 全部完成；E1 Undo/Redo 落地；E2 IME 已核实（lwjgl3ify 无 preedit 回调→降级现状）；E3-E6 待做
> 目标仓：Qz-UILib（分支 4.0）；验证：每步 gradlew build 绿后提交，真机烟测由用户执行
> 基线：SceneTextInput(B1)/SceneTextArea(基础版) 自述缺口 = 选区、剪贴板、IME 组合态、caret 闪烁、横向滚动（TextArea 另有 soft wrap）
>
> 已落地提交：ce53cc2a(B1 几何) eeda6d72(clickCount 透传) 28ab8b03(TextInput 选区)
> 72301ff7(TextArea 跨行选区) 5956ffdc/f59c6afd(剪贴板) 4acf47b6(词跳/闪烁/纵向跟随)
> cf07f470(横向滚动地基 scrollableX + TextInput caret 横向跟随)
> 9af4868a(D4 TextArea soft wrap 视觉行模型) 13fa939d(E1 Undo/Redo)
>
> ## ⚠ 会话交接（压缩后必须知道的现场状态）
>
> 1. **工作区未提交（有意留待用户决定）**：`build.gradle.kts`（elytra-conventions 插件已注释）、
>    `dependencies.gradle`（elytraModpackVersion 区段注释 + Angelica 2.1.32 直接坐标）。
>    背景：GitHub 性能下降致 raw.githubusercontent 429，elytra-conventions 插件每次构建联网拉
>    manifest 炸配置阶段。绕行后构建通道全通。**GitHub 恢复后由用户决定还原**（文件内有注释标记）。
> 2. **构建命令**：绕行期间须 `gradlew build -x verifyRunClasspathIsolation`
>    （该检查依赖被注释的 lwjgl3ify）。配置缓存已生效，后续构建不联网。
> 3. **构建脚本**：`D:\Code\MC\Qz工作站\temp\uilib_build.py` 当前即「全量 build -x 隔离检查」版。
> 4. **下一任务**：E3 SceneContextMenu（overlay/portal 挂载、指针定位+边缘翻转、ESC/外部关闭、
>    菜单项与键盘导航）；E4 文本控件默认菜单集成。E1 已落地（13fa939d），E2 已关闭（降级）。
> 5. **遗留已知问题**：无。全量 build 绿（绕行配置下，含 checkstyle）。
>
> ### D4 落地要点（9af4868a，实施后的实际决策）
>
> 1. 视觉行列表 = VisualLineModel（primitive 内静态类）：TextLayoutEngine 实例级 + TextMeasureFunction
>    适配（widthOf=rt.measureTextWidth、prefixWidths=SceneTextGeometry.buildPrefixWidths）+ 几何查询
>    （visualRowOfCaret 纯二分 / moveVerticalCp 列保持 / homeCp / endCp / segmentText / caretCpFromPointer）。
> 2. 渲染 forEach key = 视觉行起始 char（visualStartIndex）；行内段/槽位 Computed 按 key 现查视觉行号，
>    视觉行重排自动跟随；依赖 value + availableWidthSignal + layoutDoneSignal（测量/布局变化兜底）。
> 3. 可用宽桥接：rt.bind(rt.layoutDoneSignal(), ...) 读 viewport 布局宽 - 左右 padding，变化才写
>    availableWidthSignal；首帧宽未知按 0 不换行，两趟收敛（真机帧管线自带 settle；测试需
>    doLayout + __setLayoutDoneEpoch(layoutEngine.layoutEpoch()) 手动桥接）。
> 4. caret 归属规则（软换行断行点）：纯二分「最后一个 vs ≤ caret」——断行点 caret 归后一行行首
>    （点击行首/↑↓ 到达该列时自然一致）；逻辑行边界（\n 占 char 不相邻）不受影响。
> 5. 点击定位测量语义变化：B6 的 per-click PrefixWidthCache 删除，点击直接查视觉行 boundaryXs
>    （引擎按内容指纹+宽+纪元缓存）——点击路径零测量（既有测试已改写）。
> 6. TextArea caret/selection 仍为码点索引；视觉行 char 索引转换集中在 VisualLineModel（codePointCount/
>    charOffsetForCodePointIndex 往返）。
> 6. **未推送**：UILib 4.0 本地领先 origin 的提交持续累积（本任务 ~10+ 个 + 此前 13 个），push 由用户决定。

## 一、范围

P0（用户点名）：框选 + 剪贴板
P1：词跳转、caret 闪烁、横向滚动与 caret 视口跟随、soft wrap、Ctrl+Home/End
P2：Undo/Redo、IME 组合态、右键上下文菜单、Dialog/Modal、Toast
共同前置：事件层 clickCount（双击/三击合成，全库无双击事件）

## 二、分期与步骤

### Phase A：事件层 clickCount（P0/P2 共同前置）
- A1 clickCount 通道：RawInputEvent/ScenePointerEvent/SceneInputFrame 加字段，全链构造点透传（InputFrameBuilder、SceneInputRouter、SceneProjectionComposition）
- A2 InputFrameBuilder 合成：同按钮 DOWN 序列计数，时间窗 ≤500ms + 位移 ≤4px 递增 click2/click3，超窗/换按钮/位移超限重置
- A3 单元测试：合成边界（时间窗、位移、按钮切换、跨帧）+ 既有输入测试回归
- 公共 API ①：`ScenePointerEvent.getClickCount()`

### Phase B：选区核心（P0 框选）
- B1 `TextSelection` 模型（anchorCp/focusCp 码点索引，isActive，normalized，本地 UI 态不碰 value）+ 单测
- B2 TextInput 集成：POINTER_DOWN 折叠选区记 anchor；拖动 MOVE 扩展 focus；Shift+方向键/Home/End 扩展；双击选词/三击选行（`SceneTextGeometry` 补词边界工具）；Ctrl+A 全选；失焦保留选区
- B3 选区渲染：行结构 prefix/caret/suffix → prefix/highlight/caret/suffix 四节点（highlight=选中段文本，背景色+文本反色，GEOMETRY 级，不依赖绝对定位）；caret 按 focus 落在 highlight 前/后
- B4 替换/删除语义：TEXT_INPUT 替换选区；Backspace/Delete 有选区整段删、无选区单码点
- B5 只读态：可选中/可移动/可 Shift 扩展，禁止编辑（复用现有 readOnly 分支）
- B6 TextArea 跨行选区：全局 anchor/focus、跨行拖选（行+列解析）、跨行块状高亮、双击选行/三击选全文、Shift+↑/↓/Home/End 扩展
- B7 测试：SceneTextInputTest/SceneTextAreaTest/TextSelectionTest 扩展
- 公共 API ②：`TextSelection`；Result record 增 `selection` 组件（编译期破坏，下游重编译，见 §四）

### Phase C：剪贴板（P0 尾）
- C1 core 接口 `ClipboardBackend`（getClipboardText/setClipboardText）+ `ClipboardBackendProvider`（照 CursorBackend 先例，零平台依赖）
- C2 `LwjglClipboardBackend`：org.lwjglx.input.Keyboard → org.lwjgl.input.Keyboard → GLFW 三级反射降级，全失败静默 no-op
- C3 接线：SceneRuntime 持有 backend（测试可注入 fake）；LwjglInputSource implements Provider；宿主装配
- C4 快捷键：Ctrl+C（有选区复制选区，无选区复制全部——原版语义）、Ctrl+X（复制+删除）、Ctrl+V（粘贴替换选区，经 maxLength/inputType 过滤）、Ctrl+A；readOnly 仅 Ctrl+C；无后端时快捷键静默无效
- C5 测试：fake backend 全链路 + 降级路径
- 公共 API ③：`ClipboardBackend`/`ClipboardBackendProvider` + SceneRuntime 装配方法

### Phase D：P1 编辑与浏览体验
- D1 词跳转：Ctrl+←/→（词边界=字母数字连接符连续段）；顺带 Ctrl+Backspace/Delete 删词
- D2 caret 闪烁：聚焦时 530ms 亮/430ms 暗 相位循环；输入/移动重置为亮+相位归零；caretVisible=focused&&enabled&&blinkOn；时间源用 frameTimeNanos，不新增 tick API
- D3 横向滚动+跟随：TextInput 横向 scrollOffsetX（先核实 SceneNode 横向能力，缺则扩展布局引擎——风险点）；caret 越界最小滚动；TextArea 纵向 caret 跟随视口；TextArea 横向需求由 D4 soft wrap 消除
- D4 TextArea soft wrap：接入 TextLayoutEngine/VisualLineLayout；行模型逻辑行→视觉行；caret 全局索引↔视觉行列映射；点击命中、↑/↓ 保持列、Home/End 视觉行首尾；选区高亮跨视觉行（本清单最大单项）✅ 已落地 9af4868a
- D5 Ctrl+Home/End：TextInput 首尾；TextArea 全文首尾
- D6 测试

### Phase E：P2
- E1 Undo/Redo：TextEditHistory（before/after/caretBefore/caretAfter，上限 100 条）；Ctrl+Z/Y；受控协调=外部 value 变更（≠栈顶 after）清历史；连续 TEXT_INPUT 500ms 窗合并为一条（简单版先行，合并为增强）
- E2 IME 组合态：✅ 已核实（javap 2.1.16/3.0.23）——lwjgl3ify InputEvents$KeyboardListener 仅 onKeyEvent/onTextEvent，TextEvent 仅最终文本字段，无 preedit/composition 回调；按既定方案降级仅最终文本（现状），组合态不做（E2 关闭）
- E3 `SceneContextMenu` 组件：overlay/portal 挂载、指针处定位+视口边缘翻转、点击外部/ESC 关闭、菜单项（label/enabled/分隔线）、↑/↓/Enter 键盘导航
- E4 文本控件集成：默认菜单（复制/剪切/粘贴/全选/撤销/重做，按 readOnly/选区启停）+ 右键打开；Props 加 `contextMenuFactory` 覆盖（公共 API ④，可延后）
- E5 `SceneDialog`：模态遮罩+焦点陷阱（Tab 环限定）、标题/内容/按钮、ESC 取消
- E6 `SceneToast`：非模态通知、自动消失、多 toast 队列堆叠
- E7 测试 + 使用文档 02-控件 新增页 + CHANGELOG

## 三、关键设计决策（实施中按此执行，变更先说明）

1. 选区渲染走「四节点」演化（prefix/highlight/caret/suffix），不引入绝对定位依赖
2. selection 与 caretIndex 的关系：caret≡focus；无选区 anchor==focus（折叠）
3. 失焦保留选区（原版语义），不自动折叠
4. 剪贴板无选区时 Ctrl+C 复制全文（原版语义）
5. caret 闪烁不新增公共 tick API，用帧时间驱动
6. TextArea 横向滚动需求由 soft wrap 覆盖，不单独做行级横向滚动
7. undo 历史是控件本地 UI 态，与受控 value 的协调按 §E1 规则

## 四、公共 API 变更总清单（待用户确认）

| # | 变更 | 类型 | 兼容后果 |
|---|---|---|---|
| ① | ScenePointerEvent.getClickCount()（+Raw/Frame 通道） | 新增方法 | 无破坏 |
| ② | TextSelection 新类型；TextInput/TextArea Primitive.Result 增 selection 组件 | 新类型+record 组件 | record 变更=旧编译产物失效，下游随新版本重编译（常规） |
| ③ | ClipboardBackend/ClipboardBackendProvider + SceneRuntime 装配 | 新接口+方法 | 无破坏 |
| ④ | SceneContextMenu/SceneDialog/SceneToast 新组件；（可选）Props.contextMenuFactory | 新公共 API | 无破坏 |
| ⑤ | IME 组合态通道（E2 核实后定形） | 新增 | 待定 |

## 五、风险与依赖

- SceneNode/布局引擎横向 scrollOffsetX 能力未核实（D3 前置检查，缺则扩布局引擎，工作量大）
- record 组件变更破坏编译产物——按 UILib 既有流程随版本发布
- IME 组合态：已核实 lwjgl3ify 无 preedit 回调，降级仅最终文本（E2 关闭，见交接节）
- soft wrap 行模型改造涉及 TextArea 全部几何路径，回归面大（现有 SceneTextAreaTest 全量护航）
- 每步改动须守：core 平台无关（I10）、state/signal 驱动（I1）、受控 value 契约

## 六-B、D4 soft wrap 实施设计（TextArea 视觉行模型）

### 索引体系
- TextLayoutEngine/LogicalTextLine/VisualLineLayout 全部使用 **char 索引**；
  TextArea 的 caret/selection 保持 **码点索引**（既有 API/测试兼容）。
- 转换点集中在视觉行模型两侧：逻辑行构建（码点→char 区间）与命中/移动几何
  （char→码点，用 offsetByCodePoints 往返）。

### 视觉行模型
- `TextLayoutEngine` 实例级持有（create 闭包），`layout(logicalLines, availableWidth, epoch, lineHeight, true, measure)`。
- `Computed<List<VisualLineLayout>> visualLines`：依赖 value + 可用宽（viewport 内宽，
  布局后取）+ 字号 + textMeasureEpoch。TextMeasureFunction 适配：
  `widthOf = rt.measureTextWidth(text, fontSize)`，`prefixWidths` 覆盖为
  `SceneTextGeometry.buildPrefixWidths`（逐前缀整测量，像素与现有路径一致）。
- 逻辑行构建复用现有 `LineStructureCache` 的 split 语义，包成 `LogicalTextLine`
  （char start/end + text）。LineStructureCache 的码点前缀和被视觉行模型取代。

### 渲染
- forEach 视觉行（key=visualStartIndex），每行五节点（prefix/caretBefore/highlight/
  caretAfter/suffix），行内段切分用视觉行 char 区间与码点选区的交集。
- 行高 = rt.lineHeight(fontSize)（视觉行同高）。

### caret 与选区几何
- caret 码点索引 → 视觉行：按 visualStartIndex（char）二分，再转码点。
- 点击命中：relY/lineH → 视觉行 → `resolveClosestCaretIndex(localX)`（char）→ 码点。
- ↑/↓：视觉行 ±1，列保持（视觉行内码点列 clamp）；Home/End 视觉行首尾；
  Ctrl+Home/End 文首尾（不变）。
- 拖选/Shift 扩展：全局码点 anchor/focus 不变，只换定位几何。
- caret 纵向跟随：视觉行号 × lineH（替换现有逻辑行号计算）。

### 验证
- 既有 SceneTextAreaTest 全量护航（行结构五节点、跨行选区、词跳、剪贴板等语义不变）。
- 新用例：窄视口长行拆多视觉行（行数/文本段）、caret 跨视觉行移动与列保持、
  跨视觉行选区高亮、Home/End 视觉行级、点击定位视觉行、跟随滚动视觉行号。

### 横向滚动落地要点（cf07f470，D4 前已完成）

- `SceneLayoutProps.scrollableX`（LAYOUT 级）+ SceneNode setter；
  `SizingCalculator.viewportWidth`（preferredWidth > 约束宽 > 内容宽）；
  `ConstraintResolver.buildChildConstraints` 的 scrollableX 分支给子宽 UNCONSTRAINED（内容自由溢出）。
- `SceneGeometry.childXBase/maxScrollX` + paint 引擎与 hit-tester 的 X 注入（与纵向对称）。
- TextInput root 默认 `setScrollableX(true)`；caret 横向跟随 effect 用**测量宽闭式**算 maxScroll
  （不读子布局——同 flush 内文本绑定 setText 先清子缓存，读布局恒 none）。
- 文本叶 computeWidth 修 UNCONSTRAINED 边界（`min(-1, 80)` 负宽 → boundedOuter=MAX）。
- 布局引擎对 scrollableX 首次解耦约束需**两趟**收敛（真机 frame pipeline 自带 settle；测试需两次 doLayout）。

## 六、实施纪律

- 每步独立提交；build 绿（JUnit+checkstyle）才提交；失败修复重跑
- 真机烟测（框选手感、中文输入、剪贴板跨应用）由用户在阶段末执行
- 踩坑记录 docs/反馈层/errors/；公共 API 变更同步 CHANGELOG
