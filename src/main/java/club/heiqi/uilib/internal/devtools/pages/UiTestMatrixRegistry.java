package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * `/qzuilib test` 视觉优先测试矩阵 registry。
 */
final class UiTestMatrixRegistry {

    static final String SPEC_PATH = "docs/开发者文档/specs/qzuilib-test-page-visual-matrix-plan.md";

    private final List<UiTestGroupSpec> groups;
    private final List<UiTestGalleryItem> galleryItems;
    private final List<UiTestCaseSpec> cases;

    /**
     * 创建默认视觉矩阵 registry。
     *
     * @return 默认视觉矩阵 registry
     */
    static UiTestMatrixRegistry createDefault() {
        List<UiTestGroupSpec> groups = createDefaultGroups();
        List<UiTestGalleryItem> galleryItems = createDefaultGalleryItems();
        List<UiTestCaseSpec> cases = createDefaultCases();
        return new UiTestMatrixRegistry(groups, galleryItems, cases);
    }

    /**
     * 创建视觉矩阵 registry。
     *
     * @param groups 分组规格列表
     * @param galleryItems 首页画廊条目
     * @param cases 已接入样例列表
     */
    private UiTestMatrixRegistry(List<UiTestGroupSpec> groups, List<UiTestGalleryItem> galleryItems,
            List<UiTestCaseSpec> cases) {
        this.groups = Collections.unmodifiableList(new ArrayList<UiTestGroupSpec>(groups));
        this.galleryItems = Collections.unmodifiableList(new ArrayList<UiTestGalleryItem>(galleryItems));
        this.cases = Collections.unmodifiableList(new ArrayList<UiTestCaseSpec>(cases));
    }

    /**
     * 返回所有分组规格。
     *
     * @return 分组规格列表
     */
    List<UiTestGroupSpec> getGroups() {
        return groups;
    }

    /**
     * 返回首页功能画廊条目。
     *
     * @return 首页功能画廊条目
     */
    List<UiTestGalleryItem> getGalleryItems() {
        return galleryItems;
    }

    /**
     * 返回所有已接入样例。
     *
     * @return 已接入样例列表
     */
    List<UiTestCaseSpec> getCases() {
        return cases;
    }

    /**
     * 返回指定分组下已接入样例。
     *
     * @param groupCode 分组代码
     * @return 分组样例列表
     */
    List<UiTestCaseSpec> getCases(String groupCode) {
        List<UiTestCaseSpec> result = new ArrayList<UiTestCaseSpec>();
        for (UiTestCaseSpec testCase : cases) {
            if (testCase.getGroupCode().equals(groupCode)) {
                result.add(testCase);
            }
        }
        return result;
    }

    /**
     * 查找指定分组规格。
     *
     * @param groupCode 分组代码
     * @return 分组规格
     */
    UiTestGroupSpec getGroup(String groupCode) {
        for (UiTestGroupSpec group : groups) {
            if (group.getCode().equals(groupCode)) {
                return group;
            }
        }
        throw new IllegalArgumentException("未知测试分组：" + groupCode);
    }

    /**
     * 创建默认十组视觉矩阵分组。
     *
     * @return 默认分组列表
     */
    private static List<UiTestGroupSpec> createDefaultGroups() {
        List<UiTestGroupSpec> groups = new ArrayList<UiTestGroupSpec>();
        groups.add(new UiTestGroupSpec("DOM", "DOM 与选择器语义",
                "节点归属、插入移动、fragment、属性、classList 与 selector 查询。",
                "DOM 结构变更、selector 命中、链接默认行为以可截图结构卡片展示。",
                "节点归属、返回值、子节点顺序、textContent、classList、selector 结果。",
                "预期结果：后续样例应直接画出 DOM 结构、变更结果和 selector 命中目标。", 7, 7, 0));
        groups.add(new UiTestGroupSpec("CSS", "CSS 级联与样式语义",
                "cascade、inheritance、box-sizing、background、visibility/pointer-events 与 overflow。",
                "级联、继承、盒模型和可见性以对比色块和状态标签展示。",
                "computed style、继承结果、specificity 结果、可见性与 pointer-events 状态。",
                "预期结果：后续样例应直接画出 CSS 计算差异、盒模型边界和可见性命中结果。", 6, 6, 0));
        groups.add(new UiTestGroupSpec("LAYOUT", "Layout 布局与尺寸语义",
                "block flow、margin collapse、inline/inline-block、flex min-content、table auto 与 fixed/sticky。",
                "布局结果以标尺、边界框和尺寸标签展示，截图即可比对。",
                "布局盒尺寸、位置、margin collapse、flex/table 分配、scroll 范围。",
                "预期结果：后续样例应直接画出布局盒、标尺、滚动范围和定位参考框。", 6, 5, 1));
        groups.add(new UiTestGroupSpec("PAINT", "Paint 绘制、命中与视觉语义",
                "stacking、opacity、clip、transform、top-layer、scrollbar 与 host image fallback。",
                "绘制层级、裁剪、变换和滚动条以重叠舞台展示。",
                "绘制命令顺序、stacking phase、clip/transform/top-layer 与 scroll range。",
                "预期结果：后续样例应直接画出重叠顺序、裁剪边界、transform 命中和 top-layer 层级。", 7, 4, 3));
        groups.add(new UiTestGroupSpec("INPUT", "Input 输入与事件语义",
                "capture/bubble、preventDefault、wheel、focus-visible、keyboard/textInput。",
                "事件传播和默认行为以日志轨道、焦点框和滚动状态展示。",
                "事件日志、传播顺序、默认行为、focus/focus-visible、wheel 滚动结果。",
                "预期结果：后续样例应直接画出事件顺序、焦点可见状态、滚轮默认行为和输入日志。", 5, 4, 1));
        groups.add(new UiTestGroupSpec("CTRL", "Controls 控件与表单语义",
                "button、input、textarea、checkbox/radio、select、slider/toggle/tab。",
                "表单控件以真实控件排列、值标签、选择态和 caret/selection 展示。",
                "value、checked、selection 替换、top-layer 展开、disabled、change 日志。",
                "预期结果：后续样例应直接画出控件值、选择状态、禁用状态、caret/selection 和 change 日志。", 7, 7, 0));
        groups.add(new UiTestGroupSpec("TEXT", "TextFont 文本、字体与国际化语义",
                "raw/formatted、baseline、fallback、wrap/trim 与 obfuscated。",
                "文本测量、换行、baseline 和字体 fallback 以行框标尺展示。",
                "测量宽度、line-height、wrap/trim 摘要、字体 epoch。",
                "预期结果：后续样例应直接画出文本行框、baseline、换行截断和字体 epoch 变化。", 5, 4, 1));
        groups.add(new UiTestGroupSpec("ANIM", "Animation 动画与 Transition 语义",
                "transition、keyframes、timing、fill-mode、layout-vs-paint。",
                "动画轨道、关键帧状态和最终样式以时间轴卡片展示。",
                "timeline 状态、start/end/cancel 日志、最终样式。",
                "预期结果：后续样例应直接画出动画轨道、时间状态、事件日志和最终样式。", 5, 4, 1));
        groups.add(new UiTestGroupSpec("HOST", "RuntimeHost 宿主运行时语义",
                "open timing、resize、runtime stats、HUD/container input、exception panel。",
                "宿主窗口、运行时统计、HUD 输入和异常面板以环境卡片展示。",
                "入口状态、窗口尺寸、运行时统计、HUD/container 输入和异常摘要。",
                "预期结果：后续样例应直接画出宿主尺寸、运行时统计、HUD 输入链路和异常面板摘要。", 5, 1, 4));
        groups.add(new UiTestGroupSpec("NET", "RemoteNet 远程、配置与网络语义",
                "channel、fetch、stream、store、remote page/HUD、config sync、transport mode。",
                "远程链路、store 快照、配置同步和传输模式以状态面板展示。",
                "服务端往返、远程页面、HUD、配置保存和传输模式摘要。",
                "预期结果：后续样例应直接画出网络模式、往返状态、store 快照和远程页面/HUD 入口。", 6, 1, 5));
        groups.add(new UiTestGroupSpec("MODCFG", "ModernConfig 现代配置模板完整 demo",
                "STRING/NUMBER/BOOLEAN/CHOICE/LONG_TEXT/SIMPLE_LIST/TABLE/OBJECT/KEY_VALUE_MAP/PRESET_SELECTOR/RAW_EDITOR/ENHANCED_PICKER 12 入口、搜索、草稿/保存/恢复。",
                "完整配置模板页以独立屏幕展示，组页面嵌入 12 入口预览与跳转按钮。",
                "模块能力检测、屏幕跳转与返回、12 入口可见性。",
                "预期结果：点击「打开完整现代配置模板 demo 页」后进入 ModernConfigTemplateScreen，12 入口可见，ESC 返回 test 页。", 1, 0, 1));
        groups.add(new UiTestGroupSpec("REACTIVE", "Reactive 声明式三基石 demo",
                "show 条件渲染 / forEach keyed 列表 / bindText 文本绑定，纯 signal 驱动的任务清单（独立屏幕）。",
                "完整响应式 demo 页以独立屏幕展示，组页面嵌入「打开 demo」按钮与三基石说明卡片。",
                "show/forEach/bindText 在真机端到端有效：增删/打乱只动变化行、条件显隐稳定不重建、计数随 signal 派生刷新。",
                "预期结果：点击「打开声明式三基石 demo 页」后进入响应式 demo，增删/打乱任务只动变化行、开关显隐说明区块、计数随之刷新，ESC 返回 test 页。", 1, 0, 1));
        groups.add(new UiTestGroupSpec("SCENE_DEMO", "Scene 新栈端到端 demo",
                "signal 驱动 SceneNode → layout → paint → PaintPlan → UiRenderContext 完整新栈通路（独立屏幕）。",
                "新栈 demo 页以独立屏幕展示，组页面嵌入「打开 demo」按钮与场景说明卡片。",
                "端到端验证：改 signal 只该节点重绘、layout 缓存命中、paint fragment 复用（I7/I8）。",
                "预期结果：点击「打开 Scene demo 页」后深灰背景显示文本「Scene Demo: Hello」，按空格切换背景色只触发 PAINT 级、按 T 切换文本触发 LAYOUT 级，ESC 返回 SCENE_DEMO 组页面。", 1, 0, 1));
        return groups;
    }

    /**
     * 创建首页功能画廊条目。
     *
     * @return 首页功能画廊条目
     */
    private static List<UiTestGalleryItem> createDefaultGalleryItems() {
        List<UiTestGalleryItem> items = new ArrayList<UiTestGalleryItem>();
        items.add(new UiTestGalleryItem("布局能力画廊",
                "block flow / flex min-content / table auto / inline / fixed/sticky 已以视觉样例展示。",
                "视觉展示：已接入 6 张布局样例", 0xFF38BDF8));
        items.add(new UiTestGalleryItem("控件能力画廊",
                "button / input / textarea / select / slider / tab 已以真实控件状态展示。",
                "视觉展示：已接入 7 张控件样例", 0xFF34D399));
        items.add(new UiTestGalleryItem("绘制能力画廊",
                "stacking / opacity / clip / transform / top-layer / scrollbar / host image 已以分层舞台展示。",
                "视觉展示：已接入 7 张绘制样例", 0xFFF59E0B));
        items.add(new UiTestGalleryItem("文本能力画廊",
                "raw/formatted / baseline / fallback / wrap/trim / obfuscated 已以文本舞台展示。",
                "视觉展示：已接入 5 张文本样例", 0xFFF472B6));
        items.add(new UiTestGalleryItem("动画能力画廊",
                "transition / keyframes / timing / fill-mode / layout-vs-paint 已以时间轴舞台展示。",
                "视觉展示：已接入 5 张动画样例", 0xFFFB7185));
        items.add(new UiTestGalleryItem("远程能力画廊",
                "channel / fetch / stream / store / remote page / HUD 将以链路状态展示。",
                "视觉展示：待接入远程 smoke 样例", 0xFFA78BFA));
        return items;
    }

    /**
     * 创建首批真实视觉样例。
     *
     * @return 首批视觉样例列表
     */
    private static List<UiTestCaseSpec> createDefaultCases() {
        List<UiTestCaseSpec> cases = new ArrayList<UiTestCaseSpec>();
        cases.add(new UiTestCaseSpec("VIS-DOM-001", "DOM", "append/insert 移动与返回值",
                "appendChild 移动已有节点并返回被追加节点；insertBefore 同父移动时先移除再按参考节点重排。",
                "A/B/C token 列表先 append A 到末尾，再 insertBefore(A,C)，日志显示两个返回值。",
                "预期结果：最终顺序为 B,A,C，appendReturn 与 insertReturn 均为 A，A 只保留一个父节点归属。",
                "自动断言：检查子节点顺序、A 父节点、C 前兄弟和返回值日志。", ""));
        cases.add(new UiTestCaseSpec("VIS-DOM-002", "DOM", "replace/remove 返回与脱离",
                "replaceChild 返回被替换旧节点；removeChild 返回被移除节点，两个旧节点都应脱离父节点。",
                "old/keep/remove 列表执行 replace old->new 与 remove remove，结构牌显示剩余 new/keep。",
                "预期结果：最终顺序为 new,keep，replaceReturn=old，removeReturn=remove，old 与 remove 的 parent 均为空。",
                "自动断言：检查返回值日志、最终顺序、替换节点归属和被移除节点 parent。", ""));
        cases.add(new UiTestCaseSpec("VIS-DOM-003", "DOM", "DocumentFragment 展开插入",
                "DocumentFragment 插入时展开其子节点，fragment 自身不进入最终 DOM，插入后自身清空。",
                "fragment 内 F1/F2/F3 三个 token 插入目标列表，旁侧日志显示 fragment childCount。",
                "预期结果：目标列表显示 F1,F2,F3，fragmentCount=0，fragment 本身不出现在目标列表中。",
                "自动断言：检查目标子节点数量、顺序和 fragmentCount 日志。", ""));
        cases.add(new UiTestCaseSpec("VIS-DOM-004", "DOM", "textContent 替换子树",
                "setTextContent 应移除元素现有子树，并用单一文本节点承载新纯文本内容。",
                "复杂旧子树被设置为 `textContent 已替换`，日志显示 childCount 与 textContent。",
                "预期结果：目标面板只显示 textContent 已替换，childCount=1，首个子节点是文本节点。",
                "自动断言：检查 childCount、首子节点类型、textContent 值和日志摘要。", ""));
        cases.add(new UiTestCaseSpec("VIS-DOM-005", "DOM", "classList token 同步",
                "classList add/remove/toggle/contains 维护有序去重 token，并同步 className 与样式失效。",
                "token card 先 add active，再 remove active 并 toggle vis-dom-selected，日志展示 className。",
                "预期结果：最终 className 包含 vis-dom-token 与 vis-dom-selected，不再包含 active，contains(selected)=true。",
                "自动断言：检查 classList contains、className、toggle 返回值和日志属性。", ""));
        cases.add(new UiTestCaseSpec("VIS-DOM-006", "DOM", "selector 深度优先命中",
                "querySelector 返回第一个匹配元素，querySelectorAll 按文档深度优先顺序返回所有匹配元素。",
                "三个 selector item 中第二个带 target class，日志显示查询表达式和命中数量。",
                "预期结果：querySelector 命中 S2 target，querySelectorAll(.item) 顺序为 first,target,third。",
                "自动断言：在样例挂载后检查 document.querySelector、querySelectorAll 数量与顺序。", ""));
        cases.add(new UiTestCaseSpec("VIS-DOM-007", "DOM", "a[href] 链接默认激活",
                "a[href] 具备链接语义和默认焦点能力，未 preventDefault 的 click 应触发文档级 link activation。",
                "真实 a[href] 链接按钮点击后，文档级 linkActivationHandler 把 href 写入日志。",
                "预期结果：点击链接后日志显示 activated:https://example.test/qz-dom，链接保持 focusable=true。",
                "自动断言：点击 a[href]，检查 href、focusable 和文档级激活日志。", ""));
        cases.add(new UiTestCaseSpec("VIS-CSS-001", "CSS", "Specificity 三阶色块",
                "样式表 specificity 按 id > class > tag 决定最终背景，不用 inline 样式覆盖被测背景。",
                "sample 标签、.specificity-class 与 #specificity-id 三个色块直接展示级联结果。",
                "预期结果：sample 标签色块为灰蓝色，class 色块为蓝色，id 色块为绿色，三者文本清楚标注命中的选择器。",
                "自动断言：后续接 computed style 背景色与匹配规则优先级；当前先展示样式表驱动的视觉结果。", ""));
        cases.add(new UiTestCaseSpec("VIS-CSS-002", "CSS", "box-sizing 盒模型对比",
                "content-box 与 border-box 在相同 width、padding、border 下产生不同外观宽度。",
                "两个同宽声明的盒子并排展示，边框与 padding 使用高对比颜色标记。",
                "预期结果：content-box 盒子视觉宽度更大，border-box 盒子保持声明宽度，标签分别显示 content-box 与 border-box。",
                "自动断言：后续接布局盒宽度检查；当前先提供可截图盒模型对比。", ""));
        cases.add(new UiTestCaseSpec("VIS-CSS-003", "CSS", "visibility 与 pointer-events 状态牌",
                "visibility:hidden 隐藏绘制但保留结构，pointer-events:none 使元素不可命中。",
                "可见、隐藏占位、不可命中三个状态牌横向排列并标注状态。",
                "预期结果：可见状态牌正常显示，hidden 占位只保留结构说明，pointer-events:none 状态牌显示禁用命中标签。",
                "自动断言：后续接 computed visibility 与 pointer-events 状态；当前先展示状态牌。", ""));
        cases.add(new UiTestCaseSpec("VIS-LAYOUT-001", "LAYOUT", "block flow 与 margin collapse 标尺",
                "块级流按垂直顺序布局，相邻 margin collapse 应被可观察标尺说明。",
                "两段 block 卡片之间加入标尺和 collapse 文案，展示垂直流顺序。",
                "预期结果：Block A 在上、Block B 在下，中间标尺说明相邻 margin collapse 观察点。",
                "自动断言：后续接布局盒 top/height 与相邻 margin 摘要；当前先展示垂直流标尺。", ""));
        cases.add(new UiTestCaseSpec("VIS-LAYOUT-002", "LAYOUT", "flex min-content 收缩轨道",
                "row flex item 默认 min-width:auto 时长文本保留 min-content 宽度，显式 min-width:0 才可继续收缩。",
                "同一 flex 轨道中展示 long label 与 min-width:0 label 的宽度差异。",
                "预期结果：min-content 项保持较宽，min-width:0 项可压缩并显示截断提示。",
                "自动断言：后续接 flex item 宽度与 min-width:auto 摘要；当前先展示轨道差异。", ""));
        cases.add(new UiTestCaseSpec("VIS-LAYOUT-003", "LAYOUT", "table auto 内容列宽",
                "table auto 布局应让内容较长列获得更宽空间，并保持单元格边框对齐。",
                "两列表格展示短标签列与长内容列，边框用于观察列宽分配。",
                "预期结果：长内容列比短标签列更宽，表格边框和行列对齐稳定。",
                "自动断言：后续接 table 列宽分配摘要；当前先展示 auto 表格外观。", ""));
        cases.add(new UiTestCaseSpec("VIS-PAINT-001", "PAINT", "stacking 与 opacity 重叠舞台",
                "positioned 元素、z-index 和 opacity 共同决定重叠绘制顺序。",
                "红蓝绿三层绝对定位块重叠，顶部层带透明度标签。",
                "预期结果：绿色层位于最上方，蓝色半透明层覆盖红色层的一部分，层级标签可见。",
                "自动断言：后续接绘制命令顺序和 opacity phase；当前先展示重叠舞台。", ""));
        cases.add(new UiTestCaseSpec("VIS-PAINT-002", "PAINT", "overflow clip 裁剪窗口",
                "overflow:hidden 祖先裁剪超出 padding box 的子内容。",
                "裁剪窗口内放置超出边界的亮色条，窗口边框标注 clip boundary。",
                "预期结果：亮色条在裁剪窗口外不可见，clip boundary 边框清楚显示。",
                "自动断言：后续接 clip 链与命中可达性摘要；当前先展示裁剪窗口。", ""));
        cases.add(new UiTestCaseSpec("VIS-PAINT-003", "PAINT", "transform 视觉命中舞台",
                "transform 只影响绘制与命中，不改变原始布局占位。",
                "原始占位框与旋转后的亮色卡片同时展示，旁边标注 transform 参数。",
                "预期结果：半透明占位框保持原位，旋转卡片偏移显示，transform 参数以文本标注。",
                "自动断言：后续接 transform 命中与布局盒摘要；当前需截图确认旋转视觉。", "需要人眼确认旋转卡片与原始占位框的相对位置。"));
        // CSS 004-006
        cases.add(new UiTestCaseSpec("VIS-CSS-004", "CSS", "继承与级联优先级",
                "可继承属性从祖先继承，非继承属性不继承；specificity 仍优先于继承值。",
                "祖先声明 color，子不声明则继承；子声明 width（非继承）使用自身值。",
                "预期结果：子文本颜色继承祖先，子宽度为独立声明值而非父内容宽。",
                "自动断言：接 computed color 继承与 width 非继承结果；当前展示继承视觉。", ""));
        cases.add(new UiTestCaseSpec("VIS-CSS-005", "CSS", "background 与 url/none",
                "background-color 提供底色；background-image:url(...) 显示资源；none 仅底色。",
                "三个面板分别使用纯色、url 背景、显式 none 覆盖。",
                "预期结果：url 面板显示贴图，none 面板只留底色不显示图片。",
                "自动断言：接 computed background-color 与 background-image；提供可截图对比。", ""));
        cases.add(new UiTestCaseSpec("VIS-CSS-006", "CSS", "overflow 行为对比",
                "hidden 裁剪无滚动条；auto 溢出显示滚动；visible 允许内容越界可见。",
                "三个容器内放宽子元素，分别设 hidden/auto/visible 。",
                "预期结果：hidden 子不可见；auto 出现可操作滚动条；visible 子越界可见。",
                "自动断言：接 overflow 样式、clip 范围与滚动能力；当前展示行为差异。", ""));
        // LAYOUT 004-006
        cases.add(new UiTestCaseSpec("VIS-LAYOUT-004", "LAYOUT", "inline 与 inline-block 排列",
                "inline 参与行内流式，inline-block 拥有盒模型但仍按基线参与行布局。",
                "文本中混排 inline 标签与带固定宽高的 inline-block 卡片。",
                "预期结果：inline 与文本同基线连续，inline-block 作为独立盒占据空间。",
                "自动断言：接 inline 布局盒与 inline-block 宽高；当前展示混排视觉。", ""));
        cases.add(new UiTestCaseSpec("VIS-LAYOUT-005", "LAYOUT", "inline-block baseline 对齐",
                "inline-block 基线应对齐其最后行文本基线，与相邻 inline 文本基线一致。",
                "多行 inline-block 与相邻纯文本同行，观察底部对齐。",
                "预期结果：inline-block 底部基线与相邻文本对齐，高度由内容决定。",
                "自动断言：inline-block baseline 需行内布局落位模型；当前人工截图确认。", "inline-block baseline 涉及 IFC 基线计算，当前引擎为近似，待重构后自动。"));
        cases.add(new UiTestCaseSpec("VIS-LAYOUT-006", "LAYOUT", "fixed/sticky 参考框与滚动",
                "fixed 相对视口或 transform containing block 定位，不随滚动移动；sticky 保留占位并在阈值吸附。",
                "可滚动容器内置 sticky 头部 + 内容块 + fixed 定位按钮，滚动观察。",
                "预期结果：sticky 滚动到阈值后吸附固定，fixed 始终相对视口不随容器内容滚。",
                "自动断言：接 fixed/sticky 布局盒位置、scroll 范围与 containing block；展示定位。", ""));
        // PAINT 004-007
        cases.add(new UiTestCaseSpec("VIS-PAINT-004", "PAINT", "transform 命中舞台",
                "transform 改变绘制与命中 quad，不改变布局占位；点击应命中视觉变换后区域。",
                "可点击的旋转/平移卡片，旁注 transform 值与命中提示。",
                "预期结果：视觉变换后点击新位置命中，点击原布局位不命中。",
                "自动断言：接 transform 命中摘要与布局盒不变；复杂矩阵保留人工。", "transform 命中需完整 visual quad 与 hit-test 联动，当前保留人工交互确认。"));
        cases.add(new UiTestCaseSpec("VIS-PAINT-005", "PAINT", "top-layer 绘制与命中",
                "top-layer 元素（如弹层）绘制在普通 stacking 之后，优先接收命中与焦点。",
                "普通内容 + 真实注册到文档 top-layer 的弹层，弹层覆盖普通高 z-index 内容。",
                "预期结果：弹层处于文档 top-layer，覆盖下方内容；命中与焦点仍需后续交互验证。",
                "自动断言：验证元素已注册为文档 top-layer；当前提供可截图层级。", ""));
        cases.add(new UiTestCaseSpec("VIS-PAINT-006", "PAINT", "scrollbar 几何与命中",
                "overflow:auto/scroll 时生成 scrollbar track/thumb，thumb 拖动影响 scroll offset。",
                "带 overflow:auto 的高容器 + 长内容，展示 track 与 thumb 位置。",
                "预期结果：thumb 尺寸与内容比例一致，位于 track 内；点击 thumb 区域可交互。",
                "自动断言：检查 overflow:auto、正向 scroll range 和 scrollTo 运行态偏移变化；track/thumb 几何仍需截图确认。",
                ""));
        cases.add(new UiTestCaseSpec("VIS-PAINT-007", "PAINT", "host image fallback",
                "background-image:url(有效) 显示资源图；无效资源保留元素底色。",
                "两个面板：有效资源 url 与 缺失资源 url，分别展示图片与底色 fallback。",
                "预期结果：有效显示贴图，缺失显示纯底色，不出现 Minecraft 默认紫黑 missing texture。",
                "自动诊断：输出 background-image 声明与宿主资源；真实绘制和缺失 fallback 保留人工确认。",
                "host image 真实绘制与缺失资源 fallback 发生在宿主渲染阶段，需要截图确认，不能仅凭声明对象自动通过。"));
        // INPUT 001-005
        cases.add(new UiTestCaseSpec("VIS-INPUT-001", "INPUT", "capture/bubble 事件轨道",
                "click 事件按 capture -> target -> bubble 顺序传播，target capture 与 target handler 都处于 AT_TARGET。",
                "嵌套 root/target 事件面板，点击 target 后日志轨道展示四段传播顺序。",
                "预期结果：日志依次显示 root-capture、target-capture、target、root-bubble。",
                "自动断言：触发真实 click 分发，检查事件日志顺序与事件阶段。", ""));
        cases.add(new UiTestCaseSpec("VIS-INPUT-002", "INPUT", "preventDefault 默认行为",
                "preventDefault 阻止元素默认行为，但不等同于 stopPropagation。",
                "两个原生 button 对比：一个按 Enter 触发默认 click，另一个 key handler 调用 preventDefault。",
                "预期结果：默认按钮记录 click；preventDefault 按钮只记录阻止默认，不触发 click。",
                "自动断言：聚焦两个 button 并发送 Enter，检查默认 click 与被阻止 click 的差异。", ""));
        cases.add(new UiTestCaseSpec("VIS-INPUT-003", "INPUT", "wheel 事件与默认滚动",
                "wheel 事件先按 DOM-like 顺序分发，未 preventDefault 时再执行默认滚动。",
                "可滚动面板内放置 wheel target，日志显示 capture/target/bubble，滚动偏移展示默认滚动结果。",
                "预期结果：wheel 日志先出现，随后 scrollTop 从 0 增大。",
                "自动断言：发送真实 wheel 输入，检查事件顺序、deltaY 和 scrollTop 变化。", ""));
        cases.add(new UiTestCaseSpec("VIS-INPUT-004", "INPUT", "focus-visible 焦点提示",
                "鼠标/程序化焦点不显示键盘焦点提示，键盘遍历焦点应显示 focus-visible。",
                "两个可聚焦按钮展示 focus 事件日志和可见焦点提示说明。",
                "预期结果：鼠标/程序化聚焦日志为 focusVisible=false，Tab/键盘遍历聚焦日志为 focusVisible=true。",
                "自动诊断：输出 focus handler 与焦点遍历日志；真实焦点边框观感保留人工确认。",
                "focus-visible 的真实边框观感、键鼠切换手感和游戏内 Tab 链路需要截图与交互确认。"));
        cases.add(new UiTestCaseSpec("VIS-INPUT-005", "INPUT", "keyboard/textInput 日志",
                "key 与 textInput 事件分别按 capture -> target -> bubble 传播，文本输入只发给当前焦点链路。",
                "可聚焦 target 面板同时注册 key 与 textInput 日志，展示按键码、输入文本和传播阶段。",
                "预期结果：key 日志先按 DOM 顺序出现，随后 textInput 日志记录输入文本。",
                "自动断言：聚焦 target，发送 key 与 textInput，检查两类事件传播顺序。", ""));
        // CTRL 001-007
        cases.add(new UiTestCaseSpec("VIS-CTRL-001", "CTRL", "button 默认/focus/disabled 状态",
                "button 控件应在默认、键盘焦点、悬停、按下和 disabled 状态间同步视觉与 action 语义。",
                "三枚真实 DocumentButtonControl 横排展示主按钮、focus/hover 观察按钮和禁用按钮，日志记录 action。",
                "预期结果：主按钮可点击并更新日志，禁用按钮保持灰态且不触发 action，focus/hover 按钮可用于截图观察。",
                "自动断言：点击主按钮和禁用按钮，检查 action 日志、disabled 布尔属性和禁用按钮无事件。", ""));
        cases.add(new UiTestCaseSpec("VIS-CTRL-002", "CTRL", "input text/password/number 值语义",
                "input type=text 保留文本，password 显示掩码但真实 change 值保留，number 过滤非数值语法字符。",
                "三枚 DocumentTextInputControl 分别展示 text/password/number，日志输出 change 值。",
                "预期结果：text 显示 Alpha，password 显示掩码但日志记录 Secret，number 只保留 -12.5E3。",
                "自动断言：聚焦三个输入框并发送文本，检查 value 属性、password 掩码与 change 日志。", ""));
        cases.add(new UiTestCaseSpec("VIS-CTRL-003", "CTRL", "textarea caret/selection 多行状态",
                "textarea 支持多行 value、selection 替换、caret 渲染、滚动和 disabled/readOnly 边界。",
                "真实 DocumentTextAreaControl 展示多行文本、黄色 caret 与蓝色 selection 色，日志记录替换后的值。",
                "预期结果：多行内容可见，聚焦后黄色 caret 与蓝色 selection 清晰，不随控件偏移错位。",
                "自动断言：执行 Ctrl+A 替换，检查 value、change 日志和布局盒；caret/selection 绘制仍需截图确认。",
                ""));
        cases.add(new UiTestCaseSpec("VIS-CTRL-004", "CTRL", "checkbox/radio checked 与 change",
                "checkbox 支持 checked/mixed/disabled，radio group 保持单选互斥并触发 change。",
                "复选框列展示未选、已选、半选、禁用；旁侧单选组展示三项互斥选择。",
                "预期结果：点击 checkbox 后变为 checked，radio 只选中专家项，mixed 与 disabled 状态标签稳定。",
                "自动断言：点击 checkbox 与 radio 第三项，检查 aria-checked、aria-disabled 与 change 日志。", ""));
        cases.add(new UiTestCaseSpec("VIS-CTRL-005", "CTRL", "select 弹层与 table 状态表",
                "select 当前值写入 value/aria-selected，展开面板使用 top-layer 语义；table 控件保持行列对齐。",
                "DocumentSelectControl 与 DocumentTableControl 并排展示，表格记录 select value、popup 和布局状态。",
                "预期结果：select 当前值为石头，展开后 popup 覆盖在控件下方，表格行列边框对齐。",
                "自动断言：打开 select，检查 popup top-layer、选项选择、value、change 日志和 table 同步；弹层位置仍需截图确认。",
                ""));
        cases.add(new UiTestCaseSpec("VIS-CTRL-006", "CTRL", "slider/toggle 值与开关状态",
                "slider 通过键盘和拖拽更新 aria-valuenow，toggle 通过点击/键盘更新 aria-checked 并触发 change。",
                "水平 DocumentSliderControl 与 DocumentToggleSwitchControl 并排展示，日志记录数值和开关变化。",
                "预期结果：slider 初始 40，键盘右箭头后为 50；toggle 初始关闭，Enter 后开启。",
                "自动断言：聚焦 slider 发送右箭头，聚焦 toggle 发送 Enter，检查 aria 和 change 日志。", ""));
        cases.add(new UiTestCaseSpec("VIS-CTRL-007", "CTRL", "tab/focus/disabled 组合状态",
                "tablist 使用 roving focus 与 aria-selected，disabled input/button 保留视觉但拒绝焦点和交互。",
                "DocumentTabControl 展示三页内容，下方放置禁用 input 与禁用 button，用日志记录 tab change。",
                "预期结果：点击事件标签后面板切换，禁用 input/button 保持灰态且不能获得焦点。",
                "自动断言：点击第二个 tab，尝试聚焦禁用 input/button，检查 aria-selected、disabled 和日志。", ""));
        // TEXT 001-005
        cases.add(new UiTestCaseSpec("VIS-TEXT-001", "TEXT", "raw/formatted 文本模式对比",
                "UILib raw 文本不解析 `§` 格式码，Minecraft formatted 文本解析颜色与样式码。",
                "两行文本并排展示相同 `§a` 内容，一行 raw 一行 Minecraft formatted。",
                "预期结果：raw 行能看到原始 §a 字符，formatted 行显示为 Minecraft 格式文本，两者标签清晰。",
                "自动断言：检查 TextNode 内容模式、原始文本和 formatted 文本测量摘要。", ""));
        cases.add(new UiTestCaseSpec("VIS-TEXT-002", "TEXT", "字符宽度、line-height 与 baseline 标尺",
                "文本测量服务提供字符宽度与 line-height，布局阶段使用稳定行框作为 baseline 观察基础。",
                "中英文与粗斜体文本排列在同一标尺舞台，旁侧显示 line-height 标签。",
                "预期结果：中英文文本位于同一行框内，baseline 标尺与文字下沿关系稳定，line-height 标签可见。",
                "自动断言：检查测量宽度、line-height、字体样式和样例布局盒。", ""));
        cases.add(new UiTestCaseSpec("VIS-TEXT-003", "TEXT", "字体 fallback 缺字展示",
                "缺字字符应走字体 fallback，而不是留下空白或破坏相邻文本布局。",
                "同一行展示 ASCII、汉字、希腊字母和符号，配合 fallback 说明标签。",
                "预期结果：fallback 行中 ASCII、汉字、Ω 和雪花符号都应可见，不出现异常空白块或布局跳动。",
                "自动诊断：输出文本内容、测量宽度和布局盒；真实 fallback 字形观感保留人工截图确认。",
                "字体 fallback 的真实字形来自宿主字体运行时，需要游戏内截图确认缺字字符是否可见。"));
        cases.add(new UiTestCaseSpec("VIS-TEXT-004", "TEXT", "trim 与 wrap 文本溢出",
                "nowrap + text-overflow 应裁剪长文本，normal white-space 应按容器宽度换行。",
                "同一长文本分别放入 trim 行和 wrap 盒，宽度固定并用边框标出容器。",
                "预期结果：trim 行保持单行裁剪，wrap 盒产生多行文本，不遮挡相邻控件。",
                "自动断言：检查 white-space、text-overflow、容器宽度和 wrap/trim 测量摘要。", ""));
        cases.add(new UiTestCaseSpec("VIS-TEXT-005", "TEXT", "obfuscated 动态文本与字体 epoch",
                "Minecraft obfuscated 格式码应走 formatted 文本路径，动态字形不应改变布局宽度，字体 epoch 用于缓存失效。",
                "固定宽度面板内展示 §k obfuscated 文本，旁侧显示字体 epoch 诊断标签。",
                "预期结果：obfuscated 面板宽度保持稳定，动态字符只影响可见字形，不挤压 epoch 标签。",
                "自动断言：检查 formatted 文本模式、§k 原始内容、固定宽度布局盒和字体 epoch。", ""));
        // ANIM 001-005
        cases.add(new UiTestCaseSpec("VIS-ANIM-001", "ANIM", "transition 状态时间轴",
                "transition 应在被测属性变化时创建运行态插值，并分发 transitionstart / transitionend 生命周期事件。",
                "背景色 transition 色块与 0/50/100 时间轴同屏展示，日志记录 start/end。",
                "预期结果：transition target 从蓝色过渡到绿色，时间轴标签清楚标注 0ms、450ms、900ms。",
                "自动断言：切换 background-color，检查 timeline paint transition、transitionstart/end 日志和最终样式。", ""));
        cases.add(new UiTestCaseSpec("VIS-ANIM-002", "ANIM", "keyframes 三段 stop 轨道",
                "命名 keyframes 应注册到 document，declared animation 使用 stop 轨道驱动运行态样式并分发生命周期事件。",
                "qzAnimPulse 展示 0% 蓝、50% 黄、100% 绿的三段 keyframes 舞台。",
                "预期结果：keyframes pulse 色块按蓝、黄、绿三段轨道变化，右侧 stop 图例可截图识别。",
                "自动断言：检查 qzAnimPulse 注册、三段 stop、animationstart/end 日志和 forwards fill。", ""));
        cases.add(new UiTestCaseSpec("VIS-ANIM-003", "ANIM", "timing function 对比轨道",
                "linear 与 steps timing function 应分别改变动画进度曲线，且不改变 keyframes 注册语义。",
                "两个 paint-only translate 轨道并排展示 linear 与 steps(4,end) 的节奏差异。",
                "预期结果：linear 轨道连续移动，steps 轨道按 4 阶离散跳变，标签分别标明 timing function。",
                "自动断言：检查 computed animation timing function、半程进度和 paint keyframe 活跃计数。", ""));
        cases.add(new UiTestCaseSpec("VIS-ANIM-004", "ANIM", "fill-mode 最终样式保持",
                "forwards fill-mode 应在动画结束后保留末帧运行态，none 则回到作者侧 computed style 基准值。",
                "forwards 与 none 两个宽度动画面板并排展示结束后的宽度差异。",
                "预期结果：forwards 面板结束后保持更宽，none 面板回到基础宽度，两者标签清晰。",
                "自动断言：推进动画到结束后，检查 forwards 运行态布局宽度、none 基准宽度和 fill 计数。", ""));
        cases.add(new UiTestCaseSpec("VIS-ANIM-005", "ANIM", "layout-vs-paint 动画影响范围",
                "layout-affecting width 动画会驱动重排，paint-only translate 动画只改变绘制/命中视觉 quad。",
                "两个轨道分别展示 width 推动 sibling 位置变化，以及 translate 保持布局槽位不变。",
                "预期结果：layout 轨道中 sibling 随宽度增长右移；paint 轨道中元素视觉平移但 layout slot 保持原位。",
                "自动诊断：输出 layout/paint keyframe 计数、运行态布局盒与 transform 摘要；视觉节奏保留人工确认。",
                "layout-vs-paint 的真实运动节奏和截图观感需要游戏内确认，不能仅用布局盒数字自动通过。"));
        // HOST 001-005
        cases.add(new UiTestCaseSpec("VIS-HOST-001", "HOST", "open timing 开屏时序",
                "`/qzuilib test` 从聊天命令进入页面时，应避开聊天关闭流程并在下一帧稳定挂载。",
                "命令、延后一帧、页面挂载三段状态牌展示开屏链路。",
                "预期结果：从聊天框执行命令后页面稳定打开，状态牌显示 command -> deferred -> mounted。",
                "自动诊断：输出开屏链路状态牌和布局盒；真实聊天关闭流程需游戏内人工确认。",
                "开屏时序依赖 Minecraft Screen 生命周期和聊天关闭流程，需要 runClient21 人工确认。"));
        cases.add(new UiTestCaseSpec("VIS-HOST-002", "HOST", "resize 与 viewport fill",
                "宿主窗口尺寸变化后，DocumentPage 应重新计算内容区域并保持 viewport fill 约束。",
                "两张不同尺寸的 viewport 预览卡与 fill 比例状态牌展示 resize 目标。",
                "预期结果：调整窗口后卡片重新排布，滚动位置不异常跳变，页面仍按 94% x 92% 填充。",
                "自动诊断：输出 resize 预览盒与 fill 摘要；真实窗口调整和滚动稳定性需人工确认。",
                "窗口 resize、滚动位置和真实宿主填充行为需要游戏内截图与交互确认。"));
        cases.add(new UiTestCaseSpec("VIS-HOST-003", "HOST", "runtime stats 状态摘要",
                "页面环境区应逐帧读取 DocumentPageRuntimeView 的窗口、鼠标和 UiRuntimeStats 摘要。",
                "frame、render、input 三张状态牌与 stats-source 卡展示运行时统计来源。",
                "预期结果：环境信息持续显示窗口、鼠标、frame 和 render，样例卡标明 DocumentPageRuntimeView 来源。",
                "自动断言：检查 runtime stats 状态牌、DocumentPageRuntimeView 来源摘要和布局盒可见。", ""));
        cases.add(new UiTestCaseSpec("VIS-HOST-004", "HOST", "HUD/container input 链路",
                "HUD 文档层在容器态按命中接管输入，未命中或外部点击应放回宿主原生界面。",
                "HUD display、HUD input、native fallback 三张状态牌展示输入仲裁路径。",
                "预期结果：纯 HUD 在容器界面中隐藏，交互 HUD 可接收点击和键盘焦点，外部点击归还原生焦点。",
                "自动诊断：输出 HUD/container 输入链路状态牌；真实点击、键盘焦点和原生回退需人工确认。",
                "HUD 显隐、容器态输入桥接和焦点回退依赖真实游戏界面，需要 runClient21 人工确认。"));
        cases.add(new UiTestCaseSpec("VIS-HOST-005", "HOST", "exception panel 故障展示",
                "运行时样例故障应显示可读异常摘要，而不是让客户端无提示退出。",
                "故意失败、异常面板、客户端保活三张状态牌与堆栈摘要展示故障面板形态。",
                "预期结果：故意失败用例显示可读错误，不导致客户端无提示退出。",
                "自动诊断：输出异常面板结构；真实故障保活和可读错误展示需游戏内确认。",
                "真实异常面板涉及宿主运行时错误边界，不能在 JVM 页面断言中故意抛错验证。"));
        cases.add(new UiTestCaseSpec("VIS-MODCFG-001", "MODCFG", "现代配置模板完整 demo（独立屏幕）",
                "ModernConfigTemplateScreen 12 入口、搜索、草稿/保存/恢复、嵌套导航。",
                "组页面放置「打开完整现代配置模板 demo 页」按钮与 12 入口预览卡片；点击按钮跳转到 ModernConfigTemplateScreen。",
                "预期结果：点击按钮后进入现代配置模板页，12 个模板入口可见，ESC 或返回按钮回到 /qzuilib test 的 MODCFG 组页面。",
                "自动诊断：检测 club.heiqi.config 模块可用性状态牌；屏幕跳转、12 入口可见性与返回链路需游戏内人工确认。",
                "现代配置模板 demo 为独立 BaseScreen，屏幕跳转、12 入口可见性与返回链路需 runClient21 游戏内确认，无法在 JVM 文档页断言中验证。"));
        cases.add(new UiTestCaseSpec("VIS-REACTIVE-001", "REACTIVE", "声明式三基石 demo（独立屏幕）",
                "show 条件渲染 / forEach keyed 列表 / bindText 文本绑定，全部界面变化只经由改 signal 触发（UI = f(state)）。",
                "组页面放置「打开声明式三基石 demo 页」按钮与三基石说明卡片；点击按钮跳转到 ReactiveTriadDemoScreen。",
                "预期结果：进入 demo 后，添加/移除/打乱任务只增删移动变化行，开关切换显隐说明区块且稳定不重建，底部计数随任务 signal 派生刷新，ESC 返回 REACTIVE 组页面。",
                "自动诊断：组页面渲染按钮与说明卡片；三基石真机端到端（增量行协调、条件显隐、派生计数）需 runClient21 游戏内确认。",
                "声明式三基石 demo 为独立 BaseScreen，行增量协调、条件显隐与派生计数刷新的真机视觉需 runClient21 确认，无法在 JVM 文档页断言中验证。"));
        cases.add(new UiTestCaseSpec("VIS-SCENE-001", "SCENE_DEMO", "Scene 新栈端到端 demo（独立屏幕）",
                "signal 驱动 SceneNode → layout → paint → PaintPlan → UiRenderContext 完整新栈通路。",
                "组页面放置「打开 Scene demo 页」按钮与新栈说明卡片；点击按钮跳转到 SceneDemoScreen。",
                "预期结果：进入 demo 后深灰背景显示文本「Scene Demo: Hello」，按空格切换背景色、按 T 切换文本，ESC 返回 SCENE_DEMO 组页面。",
                "自动诊断：组页面渲染按钮与说明卡片；新栈端到端（I7/I8 缓存命中、layout-paint 增量）需 runClient21 游戏内确认。",
                "新栈 ui.scene 端到端 demo 为独立 BaseScreen，I7/I8 增量渲染的真机视觉需 runClient21 确认，无法在 JVM 文档页断言中验证。"));
        cases.add(new UiTestCaseSpec("VIS-SCENE-002", "SCENE_DEMO", "Scene 控件 demo（Checkbox/Toggle/Tab 等，独立屏幕）",
                "受控控件群 SceneCheckbox/Toggle/Slider/TextInput/Tab：零内部状态，当前值由外部 signal 驱动，交互经回调交还期望新值（契约 R7/R8/R9/R10）。Tab 内容区经 N 个独立 show 按 activeIndex 切页。",
                "组页面放置「打开 Scene 控件 demo」按钮与受控双向说明卡片；点击按钮跳转到 SceneControlsDemoScreen。",
                "预期结果：进入 demo 后显示 Checkbox/Toggle/Slider/TextInput 与一个 Tab（多页签 + 单内容区），点击页签切页（受控闭环：onActivate→外部 signal→show 切内容），ESC 返回 SCENE_DEMO 组页面。",
                "自动诊断：组页面渲染按钮与说明卡片；受控双向闭环、四态切换与命中穿透的真机视觉需 runClient21 游戏内确认。",
                "新栈 ui.scene 控件 demo 为独立 BaseScreen，受控双向闭环与交互态切换的真机视觉需 runClient21 确认，无法在 JVM 文档页断言中验证。"));
        return cases;
    }
}
