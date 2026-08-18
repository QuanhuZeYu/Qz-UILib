# CHANGELOG

本项目采用 [Keep a Changelog](https://keepachangelog.com/) 风格记录变更，版本号遵循
`主.次.修订[-标签]` 格式：主版本号变更代表破坏性 API 调整，次版本号代表能力扩展，
修订号代表行为修复或文档调整。

## [Unreleased]

### 新增

- scene 文本输入底层能力补齐（P0/P1）：TextInput/TextArea 框选（双击选词、三击选行、跨行拖选、Shift 扩展）、剪贴板（Ctrl+C/X/V，ClipboardBackend 平台接口 + LWJGL 反射降级链）、词跳转（Ctrl+←/→、Ctrl+Backspace/Delete）、caret 闪烁（帧时间驱动 530/430ms 相位）、Ctrl+Home/End 文首尾、TextInput 横向滚动与 caret 跟随（scrollableX 布局地基）、TextArea caret 纵向跟随
- TextArea soft wrap：逻辑行按视口可用宽经 TextLayoutEngine 软换行为视觉行（五节点视觉行渲染、跨视觉行块状选区、↑/↓ 视觉行列保持、Home/End 视觉行级、点击命中视觉行、可用宽经 layoutDoneSignal 两趟收敛）
- 文本控件 Undo/Redo：TextEditHistory 编辑历史（before/after/caret 快照、默认 100 条上限、连续输入 500ms 合并、外部 value 写入惰性清历史）+ Ctrl+Z 撤销 / Ctrl+Y 或 Ctrl+Shift+Z 重做（TextInput/TextArea 全编辑路径入历史）
- SceneContextMenu 右键上下文菜单：portalAnchored overlay 挂载、指针处锚定 + 上下边缘翻转、ESC/外部点击/选择关闭、菜单项（label/enabled/分隔线）、↑/↓ 循环高亮 + Enter 激活、指针 hover 进入菜单项即移动高亮（Enter 激活 hover 项、↑/↓ 从 hover 项继续，移出保留）、TextInput/TextArea 右键集成默认菜单（复制/剪切/粘贴/全选/撤销/重做，按 readOnly/选区/历史启停）
- SceneDialog 模态对话框：80% 暗色遮罩铺满全屏拦截指针、卡片窗口中心对齐（标题/正文/按钮行）、Tab 环自动限定对话框内（active overlay focus scope）、ESC/按钮关闭、PRIMARY/NORMAL/DANGER 按钮 + Enter/Space 激活、打开聚焦首按钮；出现/退场淡入淡出动画（受控 visible 桥接延迟卸载，退场期间可取消重放淡入）；alert/confirm 命令式便捷 API
- SceneToast 非模态通知：命令式 show、按 runtime 弱引用单例 host（portal/到期绑定挂 root owner，页面切换不中断）、底部堆叠队列、条目按内容宽度收缩并水平居中、帧时间驱动自动消失（默认 3s，到期先淡出再移除）、出现淡入+上移动画、类型化入口（INFO/SUCCESS/WARNING/ERROR 类型色点）、整树 hitTestable=false 指针穿透
- 字体世界加载上传泵：`FontService.pumpWorldLoadUploads()` 公共入口 + `MixinMinecraftWorldLoadPump`（注入 launchIntegratedServer 服务端等待循环与 loadWorld 入口），在渲染帧停摆窗口内泵送批上传，进入世界第一帧文字纹理即就绪
- `FontService.isRenderThreadCaptured()`：主渲染上下文建立判据（首帧 RenderTick 捕获 renderThread），供接管路径区分 Splash 阶段

### 变更

- scene 宿主一帧时序协议重构为显式帧管线 `SceneFramePipeline`：11 个命名阶段顺序契约、settle 跨帧状态显式化（DEFERRED 标志）、flush 单点收拢带事务审计标签、epoch 桥接写入所有权归管线、PAINT 前置断言与 flush 预算护栏（行为等价重构，提交 7cfbebe1…cce3bff4）
- 阶段 3 时序改进：锚点不可见 overlay 的 dismiss 同帧生效（消除滞后一帧）；motion completion flush 合并进 SETTLE 首轮（LAYOUT_POST_FLUSH 恢复纯布局）
- SceneRuntime 增加 internal 桥 `__runRoot`：runtime 级资源（通知浮层宿主等）可显式挂 root owner 与 runtime 同寿（页面卸载不中断通知服务）
- 字体字符页生成链路优化：字符页批上传（attrib push/pop 与 mipmap 重建按批次结算，批结算失败整页 quarantine）、上传迁移至 RenderTick START 稳定阶段（draw 收集路径零上传，tickDrawStage/drawStageUploadBatchSize 保留为遗留兼容入口）、GlyphGenerationResult 改持 RGBA 快照精简拷贝链、宽度测量 miss 时间窗预算（`FontConfig.widthCacheMissBudgetPerWindow`，<=0 回退）
- 字体 reload 惰性生命周期重置（P0-B）：只清 state/location/width/matchedFont 四类门控数组（fill 约 123MiB→29MiB），其余几何数组靠 location 门控与 generation 校验惰性失效
- 字体上传 attrib 精确恢复（P1-C 遗留）：上传路径只触碰纹理服务器状态（绑定/texParameter/纹理对象）与 client unpack state，pushAttrib 掩码从 GL_ALL_ATTRIB_BITS 精简为 GL_TEXTURE_BIT（pushClientAttrib 维持 GL_CLIENT_PIXEL_STORE_BIT），不再全量保存服务器状态
- 字符页装箱从 shelf packing 替换为 STB 同款 skyline bottom-left 紧密排列：slot 优先放入天际线最低处（混合字号下消除行高浪费，页面积占用下降）；slotGap 并入占位尺寸抬升天际线、页边缘不强制 gap；reservation 回退改为天际线快照恢复（仅尾部回退语义不变）；生成侧/上传管线/渲染 UV 均无改动
- 字体渲染热路径逐 glyph 调用消除（P2-F）：GlyphRuntimeTablesView 构造时冻结帧级页表快照（各页纹理 ID/边长，无效页记 0），draw 与 demand 判定改读快照 getter（零 FontRuntimeAccess call），旧逐页 call 方法保留为兼容入口

### 修复

- SceneToast 退场状态机列表竞态：tick 曾「remove 退场完成条目后按原索引 set 退场标记副本」，索引错位致同 id 双份（原条目 + leaving 副本）与相邻条目被覆盖 → forEach 重复 key 崩溃（真机 crash-2026-08-18_14.02.57）；改为构建式更新（跳过即删、逐条追加），回归测试 OverlayKeyIntegrityTest 锚定
- 进入世界后界面文字不出现：launchIntegratedServer 服务端等待循环与 loadWorld chunk 渲染器构建期间渲染帧完全停摆，帧驱动上传静默；世界加载上传泵恢复窗口期上传
- Forge 加载界面（Splash）字体接管断链：Splash 独立 GL 上下文且无渲染循环，主管线纹理/着色器不跨上下文；未捕获阶段按需泵送上传 + 主渲染线程捕获时检测异上下文 GL 活动并全量重建（字符页 reset、批渲染器/着色器置空惰性重建）

### 移除

- 移除 scene 演示测试台（`internal/devtools/pages` 31 文件与 `/qzuilib test`、`/qzuilib scene_test` 子命令）；保留 `/qzuilib modernconfig` 配置页调试入口与网络自检三件套
- 清理 13 份与代码脱节的架构/规格文档（旧 document 栈教程与已作废规格），重写 9 份（架构图 00/01/08、稳定 API 清单、项目定位等）

## [4.8.0] - 2026-08-17

### 新增

- 增加同一业务 state 的 screen/overlay projection composition，逐 occurrence 隔离 scene、focus、capture、hover、cursor 与 animation
- 增加 Config-scoped Material 主题、Setting Row 页面结构与基于 host frame timestamp 的 Motion；覆盖 Button/Toggle/Navigation/section，并补齐共享 focus border/选中态、输入与选择控件 chrome、Slider/Scrollbar 反馈及字段 dirty/error 语义色

### 变更

- 以 snapshot-only `HostImageSource.itemIcon(ItemStack)` 替换 4.x LIVE/SNAPSHOT/Slot 图片双栈；移除 GuiContainer、inventory-slot renderer 与旧万能 item renderer 公共合同
- 分离普通图片、item raster 与 cache composite 事务，并为失败 FBO、纹理和 adapter owner 建立可重试清理边界
- 现代配置页在世界内使用覆盖完整 framebuffer 的 80% 不透明暗色遮罩，游戏画面从整个背景连续透出，不再裁切 surface 只露底部一截；Tab 改为立即严格单 live 切换，并在完整布局发布后以满 opacity 的标题/字段卡片级联进入替代字段区 `1→0→1` 明灭
- 增强配置导航与滚动 Motion：侧栏选中项增加指示条伸缩、标签横移和文字色插值；主视口滚轮以 160ms ease-out 从当前显示 offset 收敛到可累计目标并同步 scrollbar thumb，持续输入不会反复零速起步，拖动 scrollbar 会从当前可见 offset 直接接管
- scene host 在 post-flush 主树与 overlay 完成布局后发布最终 layout epoch，并以最多三轮 observer settle 消化同帧布局写入；internal stagger reveal 在 presentation shell 位移期间关闭整棵子树输入，归位或 Owner 卸载后恢复，避免视觉与命中盒错位
- 字体排序与 draggable SimpleList 改为主流中线插槽换位：被拖行越过相邻项中线即重排，keyed layout 与累计滚动后抓取点保持跟手，同帧 `MOVE→UP/CANCEL/SCROLL` 不再丢失最终顺序，激活前受控更新及未 flush 外部 Draft 不会被旧快照覆盖；scene 同步焦点生命周期事件保证索引编辑先于保存、恢复默认、拖拽或 section 卸载处理
- 搜索选择器全链路重做：居中 70% 卡片面板、多分类维度、无上限结果列表与可见滚动条、物品图标渲染分级回退、多列成员网格（已选择容器）、成员删除一步直达

### 修复

- 修复 TextInput 从 hover 切到 focus 时背景反向变暗，以及 caret/透明选中层在 Motion 半程因 RGB 与 alpha 同时衰减产生的暗闪
- 修复字段卡片进入动画中后代输入框 clip 停在终态坐标、导致顶部暂时被裁的问题；级联改用像素对齐的 presentation geometry 位移，避免逐卡全屏 FBO
- 配置 NUMBER 读取按 raw 类别保真——整数回 Long、浮点回 Double，不可安全缩窄的实现保持原 Number

### 兼容性

- 本次发布为 minor `4.8.0`；正式 tag 前 FML 远端范围恢复 `[4.8.0,4.9.0)`；Qz-Miner 编译依赖经仓库 libs 内置 dev 制品解析，不依赖 JitPack
- 删除 `SearchPickerPresentation.Builder.cancelRemove/confirmRemove` 文案 API 与对应 getter，`SearchPicker` 成员删除改为一步直达（beta API，非 LTS 承诺范围，唯一下游 Qz-Miner 已同步）
- 主 `NetEnvelope` v2 与 Realtime v1 保持不变，不增加运行时协议协商或跨 major fallback

## [4.7.0] - 2026-08-14

详细说明见 `.changelogs/4.7.0.md`。

### 新增

- 字体异步核心 Phase A-F：字体重载 signal 驱动、不可变 generation、glyph 请求 token 状态机、有界 demand 调度、事务化 upload 与后台候选换代
- 多维度架构图集、字体引擎代码地图与原版物品渲染流程参照图等文档，规格文档目录与文件名中文化

### 变更

- 物品渲染改为上层替换当帧直绘：纯 2D 图标由 Qz 等价自绘，3D block/多 pass item model 委托原版 `renderItemAndEffectIntoGUI`（含 Forge hook）；删除 FBO 栅格化、GL 状态围栏、错误跟踪与帧中止组件
- 字体管线尾状态幂等与批渲染守卫收口：per-unit TEXTURE_2D 显式恢复，批渲染 blend 与 vanilla 一致（`glBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ZERO)`）
- 配置页 Material 主题 Motion 扩展：导航平滑滚动与指示条动效、字段卡片级联进入动画

### 修复

- 修复 HUD GL 状态围栏崩溃（issue #70）：`glGet*` 查询缓冲容量提升到 16，满足 LWJGL2 对 remaining 的恒定 ≥16 校验
- 修复宿主裁切基线与文字批渲染边界（issue #63）
- 修复字体排序与 draggable 列表拖拽时序、输入框动画与滚轮响应

### 兼容性

- FML 远端版本范围固定为 `[4.7.0,4.8.0)`；已发布旧 `4.6.3` 仍携带 `[4.6.2,4.7.0)` 并拒绝正式 `4.7.0`，混合双端需要协调升级
- 主 `NetEnvelope` v2 与 Realtime v1 保持不变，不增加运行时协议协商或公共 API 破坏

## [4.6.3] - 2026-07-25

### 修复

- 字体共享准备改为继承调用阶段的 depth test、depth mask 与 depth func，世界文字不再被统一覆盖为二维绘制状态
- 普通玩家名称与计分板标签在同一世界渲染 pass 内延后到 TileEntity 之后，以 pass-scoped FIFO 回放，并收紧 Angelica 与 lightmap 状态恢复边界
- scene 宿主关闭时强制将系统光标恢复为 `DEFAULT`，避免非默认光标样式泄漏到后续界面

### 兼容性

- FML 远端版本范围固定为 `[4.6.2,4.7.0)`；已发布旧 `4.6.2` 仍按精确版本检查并拒绝正式 `4.6.3`，混合双端需要协调升级
- 主 `NetEnvelope` v2 与 Realtime v1 保持不变，不增加运行时协议协商或公共 API 破坏
- Angelica 标签延后路径仅支持精确版本 `2.1.50`；未知版本降级为原调用点即时绘制

## [4.6.2] - 2026-07-21

### 修复

- 修复 Forge HUD `Post(ALL)` 返回后泄漏 depth、blend、clip、纹理及现代 GL binding 等入口状态，避免后续物品栏玩家预览出现深度遮挡错乱

### 兼容性

- 开发依赖基线适配 GTNH `2.9.0-beta-2`
- 不改变公共 API、配置语义或既有 HUD 注册生命周期

## [4.6.1] - 2026-07-17

### 修复

- 修复 JitPack canonical 坐标下 main、dev 与 sources 分类制品的发布，使显式使用 `dev` classifier 的消费方可正确解析制品

### 兼容性

- 不改变业务逻辑、公共 API 或 UI 行为；普通 Maven publication 的制品语义保持不变

## [4.6.0] - 2026-07-16

### 新增

- 增加通用被动 HUD API、TextHud/CompactHud 预制、四角稳定堆叠、安全区与显式占位扩展
- 配置 schema 增加递归结构化列表、choice 多选、可扩展搜索选择器元数据及字段级结构化列表视口高度（默认 320 logical px，可显式声明 640 等正高度）
- ConfigUI 增加结构化列表编辑器、领域值展示 SPI 与受控搜索选择器
- SearchPicker 增加显式 LIST_MEMBERS 绑定，提供关闭态摘要与 Manage、按稳定 raw 列表项编辑/追加及两步确认删除；raw 列表保留为默认折叠的高级入口
- Scene 增加平台图片绘制管线、搜索选择器与结构化列表所需交互能力，并补齐输入、焦点与 zLevel 边界
- HostImage ItemStack 增加静态快照入口、跨帧 identity/尺寸 LRU、每帧公平补图预算与小型 FBO 栅格缓存

### 修复

- 配置草稿所有权、磁盘变更检测、重载恢复、保存校验与批次回灌改为 fail-closed 事务边界
- StructuredList 保留未知成员并严格校验嵌套类型，修复 identity、错误路径、默认恢复及宽窄布局
- SearchPicker 收敛为 ALL/SELECTED 两态，支持未枚举 key 无损编辑；LIST_MEMBERS 当前成员/结果容量为 6×42px / 12×34px，按内容动态收缩且 portal 受可用高度裁剪，并收口 active overlay 的 Tab 焦点范围与关闭后焦点恢复
- SearchPicker LIST_MEMBERS 成员长标签在自身布局盒内显式裁剪，问题提示位于固定右侧操作区之前；移除整行隐式编辑命中，仅可见编辑/删除按钮触发动作，保留旧 `currentMemberFormatter` Provider 兼容
- 宿主物品 renderer 增加能力感知的完整 GL 状态围栏和可验证恢复；FBO 事务异常安全，恢复失败时中止当前 UI 帧而非静默污染后续命令
- 默认、自定义及旧版 HostImage renderer 在运行时适配器边界统一接受不可绕过且幂等的 ItemStack 完整状态围栏；公开 `render` 与 `renderGuarded` 共用单次围栏执行，直接 `render(ITEM_STACK, ...)` 失败时保留阶段与原 cause 抛出，未实际恢复验证不得伪报成功，普通纹理与位图仍走轻量路径
- Core Profile 下固定管线、client-active texture 与相关纹理能力按运行时探测结果降级，避免能力缺失时继续走不安全路径
- 修复中文 IME 文本桥生命周期、字体排序拖拽状态、主线程批次派发边界及 ItemStack scene backend zLevel 恢复

### 兼容性

- HUD 首版不提供输入或拖拽；registration 在断线或世界卸载后保留，不要求调用方仅因这些生命周期事件重新注册
- 对比基线 4.5.2；现有简单配置字段、既有 ConfigUI 入口及未声明视口高度的旧 StructuredList schema API 保持兼容
- SearchPicker、ValueEditorProvider 与 ConfigUI editor registry 仍为 beta API，不属于 LTS 稳定承诺
- LIST_MEMBERS 当前成员使用 6×42px 双行布局、结果使用 12×34px 行布局；不合并重复 candidate，malformed/duplicate 仅显示通用提示且不泄露 raw；确认只替换目标项或追加，Picker 删除需两步确认，raw 仍可高级修正/删除；旧 SINGLE_VALUE/Codec 路径保持兼容
- 配置 canonical、YAML 与网络语义不由 UILib 自动改写

## [4.5.3-beta-12] - 2026-07-12

### 修复

- StructuredList 使用独立 320px 首选视口高度，短窗口仍由外层约束收紧，不改变 SimpleList 全局高度
- 对象卡片标题槽限制为最多 260px 并允许伸缩裁剪，按钮紧随标题，宽屏剩余空白保留在右侧
- StructuredList 单字段恢复默认改为读取 `FieldSpec.defaultValue()` 并深拷贝，非空默认可正确回填

### 兼容性

- 不改变按钮尺寸/identity、SimpleList 默认高度或 Scene 布局引擎；本次仅发布 Maven Local，不执行 merge、push、tag 或 release

## [4.5.3-beta-11] - 2026-07-12

### 修复

- StructuredList 对象卡片 header 的 identity 标题改用可收缩的剩余宽度槽并裁剪长文本，固定操作按钮不再被遮挡或挤出卡片

### 兼容性

- 不改变折叠、排序、删除行为或 Scene 布局引擎；本次仅发布 Maven Local，不执行 merge、push、tag 或 release

## [4.5.3-beta-10] - 2026-07-12

### 变更

- SearchPicker beta API 将 `SelectionMode` 收敛为 `ALL`、`SELECTED`，不保留旧模式别名
- 变体浮层仅显示全部状态与指定状态；指定状态统一使用 checkbox，允许选择 1..N 个唯一 key
- 从 ALL 切换 SELECTED 不自动选择，空草稿禁用确认；切回 ALL 保留面板草稿但提交空 keys
- 当前候选未枚举的旧 key 以通用失效项展示，默认保留、可移除且确认不会丢失

### 兼容性

- SearchPicker 仍为 beta API；配置 canonical、YAML 与网络语义不由 UILib 改写
- 本次仅发布 Maven Local，不执行 merge、push、tag 或 release

## [4.5.3-beta-8] - 2026-07-12

### 修复

- 结构化成员的 `List<String>` raw 列表与可选 picker 改为唯一 grow 编辑列纵向排列，避免窄宽下两个填充控件横排裁剪
- 保留 member 行 label；无 picker 与 `List<CHOICE>` 装配行为不变

### 兼容性

- 本次仅发布 Maven Local，不执行 merge、push、tag 或 release

## [4.5.3-beta-7] - 2026-07-12

收口结构化列表搜索选择器的无状态写回、领域反馈、raw/picker 并存与 identity 标题，并修复
SceneScrollbar 无 overflow 时宽度变化导致的配置页多帧 ROW grow 诊断。

### 修复

- codec 按当前受控值无状态编码；领域文案错误分阶段反馈，异常/null 零写 Draft
- raw member 与 picker 同时展示，结构化列表标题优先使用稳定 identity
- scrollbar 始终占用 barWidth；无 overflow 时 track/thumb 透明且输入早退

### 兼容性

- SearchPicker、ValueEditorProvider 与 ConfigUI editor registry 为 beta API，不属于 LTS 稳定承诺
- 本次仅发布 Maven Local，不执行 merge、push、tag 或 release

## [4.5.3-beta-6] - 2026-07-12

搜索选择器支持 ALL、SINGLE、MULTIPLE 三种不可变选择，并以受控当前值驱动二阶段变体面板。

### 新增

- 变体模式分段选择、keyed checkbox、Cancel/Confirm 与键盘交互
- StructuredList picker 的受控 decode 接线，reset/reload 不重建控件

### 兼容性

- 保留 `Selection(candidateKey, variantKey)` 与 `SceneSearchPicker.Props` 旧六参构造器
- codec 编码异常或 null 均不写 Draft

## [4.5.3-beta-5] - 2026-07-11

搜索选择器 beta API 已接入结构化列表 member renderer 与 ConfigUI 每 screen editor registry。
本能力为预发布 API，不属于 LTS 稳定承诺。

### 新增

- `WidgetSpec` / `SearchPickerSpec(editorId, maxItems)`，namespaced id 且预算上限 64
- `SearchPickerData` 不可变候选、变体、选择与去重截断结果
- `ValueEditorProvider` / `Codec` / `VisualAdapter` / `Registry` 平台无关契约
- `SearchPickerFieldSupport` 的 decode/search/encode fail-soft 接线及预算截断
- ConfigUI 5 参 editor registry 定制入口，按 screen 隔离并在字段装配前冻结

### 兼容性

- `ValueSpec` 旧工厂与 API 保留；widget 只作 schema UI 元数据，不参与 YAML、默认值、校验或 schema 兼容判定
- 保留 ConfigUI 2/3/4 参与 FieldRendererRegistry 无参入口；不改 Qz-Miner、SceneSearchPicker 或图片契约

### 修复

- 本地预算限制保留 provider 的既有截断标志，少量候选仍可正确显示上游结果已截断
- `ValueEditorProvider.searchFunction()` 必须显式返回独立函数；Registry 注册时一次读取并拒绝 null，picker 后续只调用永久保存的快照，不再回读原 provider getter 或旧 search 路径

## [4.5.3-beta-4] - 2026-07-11

结构化列表多选：`List<CHOICE>` 默认渲染为受控 checkbox，已知值按 schema 顺序去重，
未知字符串显示失效标识且只允许删除；非法 passthrough 值继续由严格保存校验阻断写盘。
详细说明见 `.changelogs/4.5.3-beta-4.md`。本次只执行本地 beta 制品验证，不执行
merge、push、tag 或 release，也不修改 Qz-Miner。

### 新增

- `StructuredListModel` choice 显示、选中与不可变更新纯数据 helper
- `StructuredListFieldRenderer` 的 `List<CHOICE>` keyed 受控 checkbox 编辑器
- schema/model/runtime/scene 多选、失效值、输入、reset/reload 与写盘回归测试

### 兼容性

- 保留 `List<String>` 原分支和其它复杂列表 unsupported 行为
- config core 零 scene 依赖；不修改生产 schema、checkbox、router 或 row lineage

## [4.5.3-beta-3] - 2026-07-11

输入体验修复：结构化列表逐字符编辑保持 keyed row/input/focus，中文 IME 经通用
`McScreenBridge` 接入完整 String 文本桥。详细说明见 `.changelogs/4.5.3-beta-3.md`。
本次只执行本地 beta 制品验证，不执行 tag、push 或 release，也不修改 Qz-Miner。

### 修复

- StructuredList 所有内部编辑先更新 renderer 本地 rows，再通知 adapter，identity 逐字符修改不重建节点
- reset/reload 增加有限 identity lineage：当前唯一 identity 优先，历史唯一 identity 次之，空/重复/歧义 fail-closed
- 通用 `McScreenBridge` 幂等注册 `SceneLwjgl3ifyTextBridge`，失败降级，关闭 finally 注销并复位 external text mode
- 文本桥注册前校验 add/remove 与 begin/end 完整配对；半完成副作用事务独立回滚并保留失败步骤重试
- lwjgl3ify 可用性探测与注册统一锚定桥 classloader，并禁止探测触发类初始化
- devtools 页面移除手工 bridge owner，避免同屏双注册和双输入

### 诊断边界

- 旧日志 `Qz-Miner/run/client/logs/fml-client-latest.log:15313-15321` 是 beta-2 修复前基线，仅含 ROW/COLUMN grow WARN，不能证明 beta-3 行为
- 代码诊断：生产 Config 之前未注册 text bridge 是中文 IME 根因；renderer 本地 keyed rows 未在 adapter 回调前更新是确定的一键失焦根因
- ROW/COLUMN grow WARN 未顺手改布局，留作修复后实机复验项

## [4.5.3-beta-2] - 2026-07-11

正式结构化列表能力：递归 `ValueSpec` schema、严格 Authority/Draft/YAML、未知 member 保留、
嵌套 validator 错误路径，以及默认 scene keyed 列表编辑器。详细说明见
`.changelogs/4.5.3-beta-2.md`。本次只登记版本说明，不执行 tag、push 或 release。

### 新增

- `ValueKind` / `ValueSpec` / `Values` 与 `FieldType.STRUCTURED_LIST`
- `SectionSpec.Builder.structuredList`，表达 `List<Object{id:String,members:List<String>}>`
- 默认 renderer 的增删、上移/下移、标量与 `List<String>` member 编辑、reset/error 映射
- 结构化列表 schema/runtime/model/scene 回归测试

### 修复

- 保留旧五种字段类型与旧 `FieldSpec` 构造器；修复旧 `CHOICE` 兼容映射
- keyed 列表操作栏与 `forEach` 独占容器分离，避免 reconcile 丢失操作按钮
- 严格拒绝嵌套错误类型并保留未知对象 member 的 YAML round-trip
- 修复结构化列表 reset/reload 按位置复用 key；支持声明唯一 identity，重复/空 identity fail-closed
- 修复 `List<String>` 后代错误显示与排序/删除后的动态路径映射；补齐 renderer 交互和事务零提交证据

### 兼容性

- 不迁移现有调用方；`config.schema` / `config.runtime` 仍零 scene 依赖
- 连续 beta 预发布，稳定公共能力目标仍为 `4.6.0`

## [4.5.3-beta-1] - 2026-07-10

预发布修订（连续 beta）：草稿所有权 fail-closed、I3 展示初始化、**同 classloader 参与式 writer** 写前检测、UI 主线程契约、从磁盘显式 reload、配置回灌全局协调器与严格 disk 类型；**批次交换派发 / 简化线性化协调器 / section raw overlay 保留**。
**不是稳定 4.5.3**；稳定公共能力目标 **4.6.0**。详细说明见 `.changelogs/4.5.3-beta-1.md`。

### 新增

- `ConfigFileSnapshot` + `ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD` + `ConfigConflictException`
- save/flushRaw 参与式写前检测（精确字节 + 静态 monitor）；`reloadDraftFromDisk()` 三阶段
- `ModernConfigApplyCoordinator`：单一 monitor 线性化（无 lease/wait；同线程 reentrant register fail-fast）+ no-spin
- `MainThreadDispatcher` 真正批次交换（lock+ArrayDeque swap）+ per-side drain owner CAS + RuntimeException 隔离 + AssertionError/ErrorSink Assertion 尾重排
- `ConfigException.Category`；section raw overlay（**仅 MAP**；scalar/list section fail-closed）
- `DraftSignalAdapter` owner 线程封闭；`SchemaReplaceCompatibility`
- FontSort frozen discovered snapshot、canonical merge、筛选投影、全局索引输入与筛选拖拽提交边界

### 修复

- foreign/unbound draft 不得写任意 manager；Authority/YAML 零副作用
- save/flush 冻结 expected 基线；reload 推进 expected 后旧 prepared 结构化冲突
- disk / legacy raw 严格 NodeType；SIMPLE_LIST 严格拒绝 null 元素；schema section 未知子树 roundtrip
- schema section 为 scalar/list 时 bootstrap/reload fail-closed（禁止静默默认覆盖）
- reload 错误分类走 `Category`/`Reason`，ConfigManager/UI 禁止英文 substring 匹配
- 测试 hook AssertionError 回传且无条件释放 enqueueOwner；Forge bridge 真实 START/END 事件仅 END drain
- Atomic write 不承诺 fsync；`writeAll` deprecated 非参与式旁路，生产无调用（调用计数守卫）
- render 期 prefill 零副作用（局部只读）；reload 走磁盘重载而非仅 openDraft 旧 Authority
- fontSort 不再因 coordinator initial apply 丢失打开时字体列表；MOVE/CANCEL/no-op 不写草稿，合法 UP/索引/恢复默认才整体提交

### 兼容性

- 公共签名保留，但保存行为收紧：`DraftBuffer.from(authority)` 产生的 unowned draft，任意 `manager.save` 均返回 `DRAFT_OWNER_MISMATCH` 且零副作用；保存调用方迁移到 `manager.openDraft()`；`flushRaw` 仍 throws ConfigException（冲突为子类）
- 非正式 tag；对比基线 4.5.2

---

## [4.5.2] - 2026-07-10


修订补丁：配置保存增加可选提交前校验钩子（`DraftView` + `DraftValidator`）并接入 UI（向后兼容 patch 例外）。
详细说明见 `.changelogs/4.5.2.md`。

### 新增

- `DraftView` / `DraftValidator.validate(DraftView)` + 三参 bootstrap；二参委托 `noop()`
- 提交错误接入 `DraftSignalAdapter` / `ConfigScreen` 反馈摘要
- `ValidationResult.merge` / `summary`；fail-closed（`_config`）

### 修复

- 保存改为三阶段乐观事务；stale/并发冲突返回 INVALID 并保留实际修改，validator 全程锁外。
- NUMBER 字符串统一规范化为 Double；SIMPLE_LIST 保存期严格校验 `List<String>`；Authority/Draft prepared Map 在写盘后仅引用交换。
- 持久化锁外序列化、锁内 temp replace；ATOMIC_MOVE 不可用时为非严格原子 fallback。
- INVALID/成功后 UI 全字段 Signal 回读；同一 manager 的 BATCH_SAVE 通知期跨线程保存拒绝与监听器异常隔离。

### 兼容性

- 无公共 API 破坏；仅新增可选钩子
- 对比：[`4.5.1...4.5.2`](https://github.com/QuanhuZeYu/Qz-UILib/compare/4.5.1...4.5.2)

---

## [4.5.1] - 2026-07-10

修订补丁：修复宿主 scissor 基线与 clip 栈协作（issue #63，小地图等 HUD 叠用时字符/几何裁切失效）。
详细说明见 `.changelogs/4.5.1.md`。

### 修复

- 上下文入口捕获宿主 scissor/stencil；首层 clip 求交；栈空幂等恢复基线。
- 静态 FBO/deferred clear 与实例 restore 语义分离。
- clip 边界 flush deferred text batch。
- 新增 render 层 `ClipStackHostBaselineTest` 回归。

### 兼容性

- 无公共 API 破坏；更尊重宿主进入 UI 前的 scissor。
- 对比：[`4.5.0...4.5.1`](https://github.com/QuanhuZeYu/Qz-UILib/compare/4.5.0...4.5.1)。

---

## [4.5.0] - 2026-07-10

**重要重构发布。** scene 新栈成为 UI 主路径；HTML-like 旧栈与 Forge 配置模板 / 远程配置同步移除；
配置页切换为 Schema + `ConfigUI` + scene 控件。详细说明见 `.changelogs/4.5.0.md`。

### 新增 / 重构

- scene 新栈全链路：node / layout / paint / runtime / input / overlay / control / form / host。
- 声明式控件库（Primitive + 样式壳）、表单壳、配置 `ConfigScreen` + FieldRenderer。
- 本 mod modern 配置接入（YAML、`ModernConfigEntry`、保存回灌）。
- 宪章 I1–I12、控件契约 R1–R13 与结构门禁落地。

### 移除

- HTML-like document 业务栈大批源码与测试。
- `ForgeConfigTemplateScreen` 及远程配置同步相关 API。
- 通用 `ui.remote` 远程 HTML 门面当前不在源树（文档滞后项见项目交接）。

### 兼容性

- **破坏性**：依赖 document 栈或旧配置模板的接入方必须迁移到 scene + ConfigUI。
- 接入文档：`docs/使用文档/02-控件/配置页（ModernConfig）.md`。
- 对比：[`4.2.5...4.5.0`](https://github.com/QuanhuZeYu/Qz-UILib/compare/4.2.5...4.5.0)。

---

## [4.2.0] - 2026-06-09

第二个 4.x 稳定发布版本。保持 4.1.x 稳定 API 向后兼容，重点扩展浏览器语义、远程 UI、
远程配置同步、网络实时子层和 `/qzuilib test` 视觉矩阵，并切换到 GTNH 2.9 beta 开发依赖基线。

### 新增

- 远程 UI 会话运行时：新增内部 `RemoteUiProtocol`、`RemoteUiAssetStore`、
  `RemoteUiSessionManager`、`RemoteUiServerRuntime`、`RemoteUiClientRuntime` 与租约清理链路，
  远程页面 / 远程 HUD 的 stream、submit、close、expired 均携带并校验
  `sessionId + surfaceId + contentRevision`。
- 服务端权威远程配置页：新增 `ConfigSyncTarget`、`ConfigSyncCategorySpec`、
  `RemoteConfigDocumentPages`、`ConfigTemplateRemoteSyncController` 与服务端配置会话管理，支持
  Forge 配置模板通过远程页面同步和提交。
- 网络实时子层：`NetService.realtime(...)`、`NetRealtimeChannel`、`NetRealtimeMessage`、
  `NetRealtimeDropPolicy` 与传输层实时帧，为高频小二进制帧提供实验性通道。
- `/qzuilib test` 视觉优先矩阵：重建 DOM / CSS / Layout / Paint / Input / Controls /
  TextFont / Animation / RuntimeHost 等分组，接入 53 张核心视觉样例，并提供当前样例断言与
  一键全量断言。
- HTML-like 能力扩展：`DocumentNode.textContent` 读写、`input type=password/number`、
  textarea 软换行两级行模型、远程 CSS `background-image: url(...)` 单图解析。
- 脏子树布局缓存：支持静态 block / flex / table / inline-block / display:none 子树复用，
  并允许普通流位置变化后的整体平移复用。
- 运行时与视觉自动断言：补齐 DOM、CSS、Layout、Paint、Input、Controls、TextFont、Animation、
  RuntimeHost 多分组的机器诊断与短日志回写。

### 修改

- 远程页面和远程 HUD 对外 facade 保持不变，内部改为 session / surface / content revision 绑定，
  避免旧 stream、旧 submit、手动关闭后的 expired 回调污染当前页面。
- HTML-like 视觉遍历统一为普通树 + top-layer 根盒共享场景，paint、hit-test、scroll metrics、
  fixed containing block、clip chain 和 transform 运行态使用同一口径。
- `HtmlLikeDocumentWidgetTest` 按主题拆分为 Scroll、Drag、FocusKeyboard、LayoutCache、
  AnimationRuntime、Rendering、HitTest、HudRuntime、EventDispatch、InlineLayoutCache 等测试类。
- 长文本绘制裁剪新增 `DocumentTextPaintClipper`，减少被 overflow clip 裁掉的长单行文本提交量。
- 字体运行时高频诊断日志默认受 `Config.fontRuntimeDebug` 控制，避免淹没游戏内断言日志。
- 开发依赖基线同步到 GTNH `2.9.0-beta-1`，`gtnhsettingsconvention` 升级到 `2.0.25`，
  非平台硬依赖的整合包兼容依赖改为 non-publishable 配置。

### 修复

- 浏览器语义修复：DOM 同父移动、`removeChild` 返回值、`querySelector*` 文档根排除、
  `focusout` 事件、hover / active 状态传播、wheel 事件默认滚动前分发、布尔 `disabled`、
  margin collapse、flex min-content、table auto 列宽、absolute auto margin、fixed clip chain。
- top-layer / HUD / select 修复：select 弹层 detach 生命周期、transform 后弹层锚点、HUD top-layer
  后代预过滤、popup 关闭后 hover / cursor 刷新、运行态 transform 后滚轮和滚动条命中。
- 动画运行态修复：keyframe forwards fill 按 direction 与最终迭代奇偶写入终值，`display:none`
  中断运行中 transition 时派发 `transitioncancel`。
- 文本与控件修复：`textInput.preventDefault()` 阻止内置 input / textarea 改值，textarea stale
  visual line cache 越界保护，输入框 auto 高度和 caret / selection 绘制坐标修正。
- 绘制修复：transform 栈内禁用延迟文本批处理，避免文本 batch 使用屏幕坐标绕过父矩阵；
  host image 缺失资源保留 UILib 底色，不泄漏 Minecraft 紫黑 missing texture。
- 动态样式修复：挂载后的 `UiStyleSheet` 变更触发缓存失效，`UiStyleDeclaration.copyFrom(...)`
  对已挂载元素触发布局 / 绘制失效。
- `/qzuilib test` 的 `VIS-PAINT-005` top-layer 样例改为挂根后延迟注册，避免未挂载样例提前
  调用内部 top-layer API 后被 detached top-layer 剪枝清理。

### 测试

- 新增远程 UI runtime / protocol / asset / session、远程页面、远程 HUD、远程配置同步、
  网络实时帧、Forge 生命周期、浏览器语义和视觉矩阵相关测试。
- 补充 DocumentVisualTraversal、DocumentHitTestEngine、DocumentScrollState、DocumentPaintEngine、
  DocumentAnimationTimeline 与 HtmlLikeDocumentWidget 各主题回归测试。
- 发布前已验证：`git diff --check`、`./gradlew.bat --no-configuration-cache test`、
  `./gradlew.bat --no-configuration-cache --no-daemon -x test publishToMavenLocal`、
  `UiTestDocumentPageControllerTest` 与 `VIS-PAINT-005` 定向断言。

### 构建与发布

- `runClient21` 的 CodeChickenLib MCP mapping 目录改为启动前自动写入运行目录配置。
- 当前 `runClient21` 已解除 `BytePatternMatcher` 缺类；本地 GTNH 2.9 beta smoke 仍可能受
  `ServerUtilities 2.3.0` 与 `Et-Futurum-Requiem 2.6.40-GTNH` 第三方 mixin 冲突阻塞。
- 当前发布渠道仍为 JitPack + GTNH Maven。源码版本号由 Git tag / GTNH Gradle 推导，发布
  `4.2.0` 时应在最终提交上创建并推送 `4.2.0` tag。

---

## [4.1.0-LTS] - 2026-05-23

第一版长期支持版本。覆盖发布前 P0 / P1 / P2 阶段的全量审查与修补，公共 API 边界
确定，文档与实现完成对齐。后续 4.1.x 仅做兼容性修复，不引入破坏性变更。

### 新增

- 浏览器语义示例页扩展：补全 hover、focus、active、文本排版、滚动条与 ESC 默认行为
  等浏览器一致行为的展示与回归。
- 动画能力 Phase 2 / Phase 3：补齐 transform、box-shadow、backdrop-filter、cubic-bezier
  与 `steps()` 缓动；transition 通过 `DocumentTransitionSpec` 支持 per-property
  duration / delay / timing；keyframe 支持 `animation-direction`、无限迭代与
  `ElementNode.animate(...)` 命令式启动。新增 transitionstart / transitioncancel /
  animationstart / animationiteration 事件派发链路。
- 设置页核心控件四件套：复选框、单选组、滑块（含小数滑块自动附文本输入）、标签页。
  数值属性绑定支持自动滑条与文本输入兜底；`draggable=true` 元素默认应用
  `cursor: pointer`。
- HTML-like 语义元素：`document.a()` / `ul()` / `ol()` / `li()` / `img()` / `table()`
  提供最小可用语义闭环；`img` 支持 width / height 属性与远程位图缓存；`a[href]` 走
  片段跳转 + `setLinkActivationHandler(...)` 业务回调。
- 字体排序控件：可视化重排、分页、搜索、序号输入；写回到 Forge `Property` 列表。
- UI 框架结构审查展示页：以可滚动看板呈现分层链路、优先级、热区。
- 运行时自检页：在游戏内对 FontService reload / fallback / 异步线程拦截、
  ForgeConfigTemplate 冷构造、DocumentRemoteImageCache 关停做现场断言。失败立即抛出
  `IllegalStateException`，由 Minecraft 崩溃面板捕获完整堆栈。
- 远程位图缓存：`http(s)://` URL 通过 `DocumentRemoteImageCache` 异步下载并按 LRU 驱逐。
- LGPL v3 开源许可证。

### 修改

- 全部 god class 完成结构拆分：`UiRenderContext`、`ElementNode`、
  `DocumentLayoutEngine`、`DocumentAnimationTimeline`、`HtmlLikeDocumentWidget` 各拆为
  3-5 个协作类；layout helper 按 Flex / Table / Inline / Positioned / Text 分模块。
- 布局热路径：减少重复测量、提取测量缓存、关闭非必要的二次相对偏移计算。
- 公共 API 收口：诊断页工厂方法降为 package-private，仅 `/qzuilib test` 可调起；
  `internal` 包类加 `@apiNote` 标记 LTS 不承诺；`UiHudDocumentHost` 的钩子方法加
  `@apiNote 仅供框架内部 forge 事件钩子调用` 警告。
- HUD 文档层：`PASSIVE` / `INTERACTIVE` 两层语义在 javadoc 与文档中明确分列可见性
  与输入语义；INTERACTIVE 层只在 `GuiContainer` 子类宿主下且鼠标已释放时可交互。
- 字体重载链路：reload 拦截非渲染主线程调用，避免 worker 线程释放 GL 资源触发
  "No context is current" 致命崩溃。
- 文档体系重构为四条路线：使用文档（外部接入）、开发者文档（内部架构）、
  reviews（审查报告）、errors（错误记录）。
- README 默认提供英文版本，提供中文跳转。
- 默认控件附加 web 语义鼠标指针样式（pointer / text / move / not-allowed 等）。
- `flex align-items: baseline` 当前等价于 START，新增 `LOG.warn` 一次性提示。

### 修复

- 单行输入框强制 `white-space: nowrap` 修复光标错位。
- 字体排序还原至主配置页并修复拖拽时锁顶部条目。
- 滑动条拖拽释放后正确提交。
- 键盘默认行为取消语义（preventDefault / stopPropagation）。
- 语义展示页 focus 崩溃；hover 与文本排版的视觉细节。
- 字体生成调度器在重入时通过 `awaitTermination` + 代际隔离保证旧任务不会写入新
  `GlyphPageManager`；`GlyphPage` 零数据 buffer 不再跨实例共享。
- `FontShaderProgram.loadProgram` 在编译 / 链接异常时通过 try/finally 释放 vertex /
  fragment shader 与新建 program。
- `UiMainLayerSnapshotService` 增加 32 槽 snapshot 池上限与按帧驱逐策略，避免异常
  关屏导致 GL 纹理 / FBO 持续增长。
- `DocumentRemoteImageCache.trimCacheIfNeeded` 加 `AtomicBoolean` 守门防并发驱逐；
  FIFO 改为按 `lastAccessedAt` 最旧驱逐的 LRU 策略。
- `UiLayoutInvalidationRegistry` 改为显式 `LOCK` 对象 + 锁外触发 `invalidateLayoutTree`，
  避免持锁回调引发死锁。
- `CodepointTextCache` BMP 路径加 `synchronized` 保证并发可见性。
- `SystemDocumentCursorHost` 反射降级路径改为 `AtomicBoolean` + 一次性 `LOG.debug`，
  并修复字段声明顺序避免静态初始化 NPE。
- `UiInputService` / `UiNativeTextInputInspector` / `ForgeConfigTemplateScreen`
  反射 ignored 块改为按字段去重的 `LOG.debug` 一次性日志。

### 移除

- `DocumentLinkActivationEvent.markHandled()` / `isHandled()`：v4.0 已 `@Deprecated`，
  本版正式删除。改用 `preventDefault()` / `isDefaultPrevented()` 与浏览器原生事件保持
  一致。

### 资源生命周期

- 新增 `ClientProxy` 关停链路：JVM `Runtime.addShutdownHook` 先关停
  `DocumentRemoteImageCache` 再关停 `FontService`；客户端断连
  （`FMLNetworkEvent.ClientDisconnectionFromServerEvent`）触发 HUD 注册表清理。
- 三个内部线程池（`FontService`、`GlyphGenerationDispatcher`、
  `DocumentRemoteImageCache`）均提供显式 `shutdown()` + 2 秒 `awaitTermination`。
- `ShaderProgramSupport.compileShader` 在编译失败时通过 try/finally 调用
  `glDeleteShader`，避免 GL 对象泄漏。

### 测试

- 新增 `FlexLayoutHelperBoundaryTest` / `TableLayoutHelperBoundaryTest` /
  `InlineLayoutHelperBoundaryTest` / `PositionedLayoutHelperBoundaryTest`：覆盖
  helper 拆分后的负尺寸、嵌套、auto cross-size 边界用例。
- 新增 `FontServiceLayoutRuntimeSmokeTest`：覆盖 `ensureLayoutRuntimeReady`
  幂等性。
- ForgeConfigTemplate 冷构造、FontService reload 三场景由"运行时自检页"在真机
  GL context 下覆盖。

### 构建与发布

- `jitpack.yml` 补 `install: ./gradlew --no-configuration-cache --no-daemon -x test
  publishToMavenLocal` 步骤，验证 JitPack 真能完整跑通构建。
- 当前发布渠道：JitPack + GTNH Maven。Modrinth / CurseForge 项目 ID 暂留空，未来
  补丁版按需补全。

---

## [4.0.0] - [4.0.20-beta] 历史预发布

`4.0.0` ~ `4.0.20-beta` 为 4.0 系列的能力开发与 god class 拆分阶段，未对外承诺
LTS 稳定性。本仓库自 `4.1.0-LTS` 起开始按 LTS 标准维护。

[4.1.0-LTS]: https://github.com/QuanHu1995/Qz-UILib/releases/tag/4.1.0-LTS
[4.2.0]: https://github.com/QuanHu1995/Qz-UILib/releases/tag/4.2.0
