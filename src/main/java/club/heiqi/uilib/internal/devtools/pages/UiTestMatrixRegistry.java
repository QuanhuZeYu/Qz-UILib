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
        List<UiTestCaseSpec> cases = Collections.emptyList();
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
                "预期结果：后续样例应直接画出布局盒、标尺、滚动范围和定位参考框。", 6, 6, 0));
        groups.add(new UiTestGroupSpec("PAINT", "Paint 绘制、命中与视觉语义",
                "stacking、opacity、clip、transform、top-layer、scrollbar 与 host image fallback。",
                "绘制层级、裁剪、变换和滚动条以重叠舞台展示。",
                "绘制命令顺序、stacking phase、clip/transform/top-layer 命中。",
                "预期结果：后续样例应直接画出重叠顺序、裁剪边界、transform 命中和 top-layer 层级。", 7, 5, 2));
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
                "block flow / flex min-content / table auto / fixed-sticky 将以可截图样例展示。",
                "视觉展示：待接入真实布局样例", 0xFF38BDF8));
        items.add(new UiTestGalleryItem("控件能力画廊",
                "button / input / textarea / select / slider / tab 将以真实控件状态展示。",
                "视觉展示：待接入真实控件样例", 0xFF34D399));
        items.add(new UiTestGalleryItem("绘制能力画廊",
                "stacking / clip / transform / scrollbar / host image 将以分层舞台展示。",
                "视觉展示：待接入真实绘制样例", 0xFFF59E0B));
        items.add(new UiTestGalleryItem("远程能力画廊",
                "channel / fetch / stream / store / remote page / HUD 将以链路状态展示。",
                "视觉展示：待接入远程 smoke 样例", 0xFFA78BFA));
        return items;
    }
}
