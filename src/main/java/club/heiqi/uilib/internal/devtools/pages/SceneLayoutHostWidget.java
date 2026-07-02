package club.heiqi.uilib.internal.devtools.pages;

import java.util.Arrays;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.control.SceneBreadcrumb;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 新栈 ui.scene Layout demo 宿主 Widget。
 *
 * <p>本页集中展示排版地基六项能力：FILL/SHRINK、ROW/COLUMN、padding/gap、
 * preferredWidth、SceneBreadcrumb 真实文字宽，以及 COLUMN 固定标题 + 唯一
 * fillParentHeight 视口吃剩余高度。滚轮只写 {@link #scrollSignal}，由 bind
 * 在 flush 阶段推给视口 scrollOffsetY，避免事件 handler 命令式改节点。</p>
 */
public class SceneLayoutHostWidget extends AbstractSceneHostWidget {

    private static final int SECTION_TITLE_COLOR = 0xFFEAF1FF;
    private static final int BLUE = 0xFF2563EB;
    private static final int GREEN = 0xFF059669;
    private static final int PURPLE = 0xFF7C3AED;
    private static final int TITLE_BAR_HEIGHT = 38;
    private static final int SCROLL_GAP = 3;

    private final SceneNode root;
    private final SceneNode viewport;
    private final SceneNode scrollContainer;
    private final SceneNode scrollbarColumn;
    private final SceneNode content;
    private final Signal<Integer> scrollSignal;
    private final MountHandle breadcrumbHandle;
    /**
     * 创建 Layout demo 宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式，无真机滚轮）
     */
    public SceneLayoutHostWidget(PlatformInputSource inputSource) {
        super(inputSource);

        this.root = createRoot();
        root.appendChild(createTitleBar());

        this.viewport = createViewport();
        this.content = createContent();
        viewport.appendChild(content);
        this.scrollContainer = createScrollContainer();
        scrollContainer.appendChild(viewport);
        root.appendChild(scrollContainer);

        content.appendChild(createFillShrinkSection());
        content.appendChild(createDirectionSection());
        content.appendChild(createSpacingSection());
        content.appendChild(createPreferredWidthSection());
        SceneNode breadcrumbSection = createBreadcrumbSection();
        this.breadcrumbHandle = runtime.mount(breadcrumbSection, SceneBreadcrumb.create(runtime, createBreadcrumbProps()));
        breadcrumbSection.appendChild(readout("每段按真实文字宽收缩，长段更宽、短段更窄"));
        content.appendChild(breadcrumbSection);
        content.appendChild(createViewportSection());

        this.scrollSignal = SceneScrolls.attach(runtime, viewport);

        // 滚动条叠加在 viewport 右侧（scrollContainer ROW 内独立列），照 ConfigScreen 范式。
        SceneScrollbar.Props sbProps = new SceneScrollbar.Props(
                viewport, scrollSignal, scrollSignal::set,
                SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                SceneScrollbar.DEFAULT_BAR_WIDTH, SceneScrollbar.DEFAULT_MIN_THUMB_HEIGHT);
        SceneScrollbar.Result sb = SceneScrollbar.create(runtime, sbProps);
        this.scrollbarColumn = sb.column();
        scrollContainer.appendChild(scrollbarColumn);

        runtime.flush();
    }

    /**
     * 创建根容器。
     *
     * @return 根场景节点
     */
    private SceneNode createRoot() {
        SceneNode node = new SceneNode();
        node.setFillParentHeight(true);
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setPadding(20);
        node.setGap(12);
        node.setBackgroundColor(SceneDemoTokens.ROOT_BG);
        return node;
    }

    /**
     * 创建固定标题条。
     *
     * @return 标题条节点
     */
    private SceneNode createTitleBar() {
        SceneNode titleBar = SceneNode.column();
        titleBar.setPreferredHeight(TITLE_BAR_HEIGHT);
        titleBar.setGap(4);
        titleBar.setHitTestable(false);
        titleBar.appendChild(text("Scene Layout demo", SceneDemoTokens.TITLE_COLOR));
        titleBar.appendChild(text("排版地基六项能力 · 每张卡片都是规则本身的实物证据", SceneDemoTokens.MUTED_COLOR));
        return titleBar;
    }

    /**
     * 创建滚动视口。
     *
     * @return 滚动视口节点
     */
    private SceneNode createViewport() {
        SceneNode node = SceneNode.column();
        node.setFillParentHeight(true);
        node.setFlexGrow(1);
        node.setScrollable(true);
        node.setClipChildren(true);
        node.setPadding(14);
        node.setGap(14);
        node.setBackgroundColor(SceneDemoTokens.VIEWPORT_BG);
        node.setCornerRadius(10);
        return node;
    }

    /**
     * 创建滚动容器（ROW：viewport + scrollbar 列），照 ConfigScreen 范式。
     *
     * @return 滚动容器节点
     */
    private SceneNode createScrollContainer() {
        SceneNode node = SceneNode.row();
        node.setFillParentHeight(true);
        node.setGap(SCROLL_GAP);
        return node;
    }

    /**
     * 创建视口内内容容器。
     *
     * @return 内容容器节点
     */
    private SceneNode createContent() {
        SceneNode node = SceneNode.column();
        node.setGap(14);
        return node;
    }

    /**
     * 创建 FILL 与 SHRINK 对比卡片。
     *
     * @return section 节点
     */
    private SceneNode createFillShrinkSection() {
        SceneNode section = section("FILL vs SHRINK", "默认填满父宽，对比内容驱动收缩。 ");
        section.appendChild(labelBox("WidthSizing.FILL → 撑满父宽", BLUE, false, 0));
        section.appendChild(labelBox("WidthSizing.SHRINK → 只包内容", GREEN, true, 0));
        section.appendChild(readout("上：FILL 盒宽 = 父可用宽；下：SHRINK 盒宽 = 内容宽 + padding×2"));
        return section;
    }

    /**
     * 创建 ROW 与 COLUMN 对比卡片。
     *
     * @return section 节点
     */
    private SceneNode createDirectionSection() {
        SceneNode section = section("ROW vs COLUMN", "同样子节点，仅主轴方向不同。 ");
        section.appendChild(chipGroup(FlexDirection.ROW, BLUE, 0));
        section.appendChild(chipGroup(FlexDirection.COLUMN, GREEN, 0));
        section.appendChild(readout("同样三个子节点：FlexDirection.ROW 横排，COLUMN 竖排"));
        return section;
    }

    /**
     * 创建 padding/gap 对比卡片。
     *
     * @return section 节点
     */
    private SceneNode createSpacingSection() {
        SceneNode section = section("padding / gap", "间距参与真实布局占位。 ");
        section.appendChild(spacingRow(2, 2, BLUE));
        section.appendChild(spacingRow(12, 12, PURPLE));
        section.appendChild(readout("上 padding=2 gap=2，下 padding=12 gap=12 · 间距真实占据布局空间"));
        return section;
    }

    /**
     * 创建 preferredWidth 优先级卡片。
     *
     * @return section 节点
     */
    private SceneNode createPreferredWidthSection() {
        SceneNode section = section("preferredWidth", "显式宽度压过 shrink/fill 决策。 ");
        section.appendChild(labelBox("无 preferredWidth → 内容宽", BLUE, true, 0));
        section.appendChild(labelBox("preferredWidth=120", GREEN, true, 120));
        section.appendChild(labelBox("preferredWidth=220", PURPLE, true, 220));
        section.appendChild(readout("preferredWidth 为最高优先级：压过 SHRINK / FILL 的宽度决策"));
        return section;
    }

    /**
     * 创建 Breadcrumb 卡片并返回待挂载 section。
     *
     * @return section 节点
     */
    private SceneNode createBreadcrumbSection() {
        SceneNode section = section("Breadcrumb", "段宽由真实文字测量决定。 ");
        return section;
    }

    /**
     * 创建 Breadcrumb 输入契约。
     *
     * @return Breadcrumb props
     */
    private SceneBreadcrumb.Props createBreadcrumbProps() {
        return new SceneBreadcrumb.Props(
                Arrays.asList(
                        new SceneBreadcrumb.Segment("/", "根目录"),
                        new SceneBreadcrumb.Segment("/a", "A"),
                        new SceneBreadcrumb.Segment("/a/options", "配置与选项"),
                        new SceneBreadcrumb.Segment("/a/options/long", "一个相当长的末段标签")),
                Signal.create(Boolean.TRUE),
                path -> { });
    }

    /**
     * 创建 fillParentHeight 视口结构示意卡片。
     *
     * @return section 节点
     */
    private SceneNode createViewportSection() {
        SceneNode section = section("fillParentHeight viewport", "固定标题兄弟 + 唯一填高视口。 ");
        section.appendChild(heightBox("固定标题条（兄弟）", BLUE, 20));
        section.appendChild(heightBox("fillParentHeight 视口（吃剩余高）", GREEN, 44));
        section.appendChild(readout("你正在看的整页外壳就是这个结构：标题固定，视口吃满剩余高并滚动"));
        return section;
    }

    /**
     * 创建标准 section 卡片。
     *
     * @param title 标题文本
     * @param description 说明文本
     * @return section 节点
     */
    private SceneNode section(String title, String description) {
        SceneNode node = SceneNode.column();
        node.setBackgroundColor(SceneDemoTokens.CARD_BG);
        node.setBorderColor(SceneDemoTokens.CARD_BORDER);
        node.setBorderWidth(1);
        node.setCornerRadius(10);
        node.setPadding(12);
        node.setGap(8);
        node.appendChild(text(title, SECTION_TITLE_COLOR));
        node.appendChild(text(description, SceneDemoTokens.MUTED_COLOR));
        return node;
    }

    /**
     * 创建文字节点。
     *
     * @param value 文本内容
     * @param color 文本颜色
     * @return 文本节点
     */
    private SceneNode text(String value, int color) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setHitTestable(false);
        return node;
    }

    /**
     * 创建带标签色块。
     *
     * @param label 文本
     * @param color 背景色
     * @param shrink 是否按内容收缩
     * @param preferredWidth 首选宽度，0 表示不指定
     * @return 色块节点
     */
    private SceneNode labelBox(String label, int color, boolean shrink, int preferredWidth) {
        SceneNode box = SceneNode.row();
        box.setPadding(8);
        box.setBackgroundColor(color);
        box.setCornerRadius(6);
        box.setHitTestable(false);
        if (shrink) {
            box.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        }
        if (preferredWidth > 0) {
            box.setPreferredWidth(preferredWidth);
        }
        box.appendChild(text(label, 0xFFFFFFFF));
        return box;
    }

    /**
     * 创建固定高度示意色条。
     *
     * @param label 文本
     * @param color 背景色
     * @param preferredHeight 首选高度
     * @return 色条节点
     */
    private SceneNode heightBox(String label, int color, int preferredHeight) {
        SceneNode box = labelBox(label, color, false, 0);
        box.setPreferredHeight(preferredHeight);
        return box;
    }

    /**
     * 创建三枚 chip 的方向演示组。
     *
     * @param direction 主轴方向
     * @param color 背景色
     * @param preferredWidth 首选宽度
     * @return chip 组节点
     */
    private SceneNode chipGroup(FlexDirection direction, int color, int preferredWidth) {
        SceneNode row = new SceneNode();
        row.setFlexDirection(direction);
        row.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        row.setPadding(6);
        row.setGap(6);
        row.setBackgroundColor(SceneDemoTokens.READOUT_BG);
        row.setCornerRadius(6);
        row.setHitTestable(false);
        if (preferredWidth > 0) {
            row.setPreferredWidth(preferredWidth);
        }
        row.appendChild(labelBox("A", color, true, 28));
        row.appendChild(labelBox("B", color, true, 28));
        row.appendChild(labelBox("C", color, true, 28));
        return row;
    }

    /**
     * 创建 padding/gap 对比行。
     *
     * @param padding 内边距
     * @param gap 间距
     * @param color chip 背景色
     * @return 对比行节点
     */
    private SceneNode spacingRow(int padding, int gap, int color) {
        SceneNode row = SceneNode.row();
        row.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        row.setPadding(padding);
        row.setGap(gap);
        row.setBackgroundColor(SceneDemoTokens.READOUT_BG);
        row.setCornerRadius(6);
        row.setHitTestable(false);
        row.appendChild(labelBox("1", color, true, 26));
        row.appendChild(labelBox("2", color, true, 26));
        row.appendChild(labelBox("3", color, true, 26));
        return row;
    }

    /**
     * 创建读数说明节点。
     *
     * @param value 读数文本
     * @return 读数节点
     */
    private SceneNode readout(String value) {
        SceneNode node = labelBox(value, SceneDemoTokens.READOUT_BG, false, 0);
        node.setCornerRadius(6);
        return node;
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** 回收资源：卸载 Breadcrumb 组件并释放 runtime 作用域。 */
    @Override
    public void dispose() {
        breadcrumbHandle.dispose();
        super.dispose();
    }

    /** @return 内部场景运行时 */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /** @return 内部布局引擎 */
    SceneLayoutEngine __getLayoutEngine() {
        return layoutEngine;
    }

    /** @return 场景树根节点 */
    SceneNode __getRoot() {
        return root;
    }

    /** @return 滚动视口节点 */
    SceneNode __getViewport() {
        return viewport;
    }

    /** @return 滚动容器节点（ROW：viewport + scrollbarColumn） */
    SceneNode __getScrollContainer() {
        return scrollContainer;
    }

    /** @return 滚动条列节点（scrollContainer 内 viewport 右侧独立列） */
    SceneNode __getScrollbarColumn() {
        return scrollbarColumn;
    }

    /** @return 视口内容容器节点 */
    SceneNode __getContent() {
        return content;
    }

    /** @return 纵向滚动受控源 */
    Signal<Integer> __getScrollSignal() {
        return scrollSignal;
    }

    /** @return 标题条固定高度常量 */
    static int __getTitleBarHeight() {
        return TITLE_BAR_HEIGHT;
    }
}
