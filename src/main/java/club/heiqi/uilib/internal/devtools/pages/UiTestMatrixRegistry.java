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
                "绘制命令顺序、stacking phase、clip/transform/top-layer 命中。",
                "预期结果：后续样例应直接画出重叠顺序、裁剪边界、transform 命中和 top-layer 层级。", 7, 3, 4));
        groups.add(new UiTestGroupSpec("INPUT", "Input 输入与事件语义",
                "capture/bubble、preventDefault、wheel、focus-visible、keyboard/textInput。",
                "事件传播和默认行为以日志轨道、焦点框和滚动状态展示。",
                "事件日志、传播顺序、默认行为、focus/focus-visible、wheel 滚动结果。",
                "预期结果：后续样例应直接画出事件顺序、焦点可见状态、滚轮默认行为和输入日志。", 5, 4, 1));
        groups.add(new UiTestGroupSpec("CTRL", "Controls 控件与表单语义",
                "button、input、textarea、checkbox/radio、select、slider/toggle/tab。",
                "表单控件以真实控件排列、值标签、选择态和 caret/selection 展示。",
                "value、checked、selection、caret、disabled、change 日志。",
                "预期结果：后续样例应直接画出控件值、选择状态、禁用状态、caret/selection 和 change 日志。", 7, 5, 2));
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
                "button / input / textarea / select / slider / tab 将以真实控件状态展示。",
                "视觉展示：待接入真实控件样例", 0xFF34D399));
        items.add(new UiTestGalleryItem("绘制能力画廊",
                "stacking / opacity / clip / transform / top-layer / scrollbar / host image 已以分层舞台展示。",
                "视觉展示：已接入 7 张绘制样例", 0xFFF59E0B));
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
                "自动诊断：输出 overflow 与 scroll range；scrollbar 几何、拖拽和命中保留人工确认。",
                "scrollbar track/thumb 几何与拖拽命中需要游戏内截图和交互确认，不能仅凭 overflow 声明自动通过。"));
        cases.add(new UiTestCaseSpec("VIS-PAINT-007", "PAINT", "host image fallback",
                "background-image:url(有效) 显示资源图；无效资源保留元素底色。",
                "两个面板：有效资源 url 与 缺失资源 url，分别展示图片与底色 fallback。",
                "预期结果：有效显示贴图，缺失显示纯底色，不出现 Minecraft 默认紫黑 missing texture。",
                "自动诊断：输出 background-image 声明与宿主资源；真实绘制和缺失 fallback 保留人工确认。",
                "host image 真实绘制与缺失资源 fallback 发生在宿主渲染阶段，需要截图确认，不能仅凭声明对象自动通过。"));
        return cases;
    }
}
