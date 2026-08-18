package club.heiqi.uilib.internal.devtools.playground;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * {@link TestPlaygroundHost} 页面状态机与骨架结构测试。
 *
 * <p>headless 构造（input=null）+ 真实布局引擎驱动：断言骨架树结构、初始 Home 页、
 * 分段导航切换（单槽替换、旧页析构）、同页 no-op、全部注册页可挂载。
 * 纯视觉/交互细节不在此断言。</p>
 */
public class TestPlaygroundHostTest {

    private static final int CANVAS_WIDTH = 720;
    private static final int CANVAS_HEIGHT = 520;

    private TestPlaygroundHost host;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new TestPlaygroundHost(null);
        doLayout();
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /** 用宿主布局引擎对齐主树（与真机 render 前的主树 layout 同口径）。 */
    private void doLayout() {
        SceneLayoutEngine engine = host.getLayoutEngine();
        engine.layout(host.__getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** 取第 index 个导航段节点（segmented root 的第 index 个子节点）。 */
    private SceneNode navSegment(int index) {
        SceneNode segmentedRoot = host.__getNavBar().__getChildren().get(0);
        return segmentedRoot.__getChildren().get(index);
    }

    /** 在指定节点中心合成 CLICK（DOWN+UP 两帧 route + flush）。 */
    private void clickNode(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int x = box.getX() + box.getWidth() / 2;
        int y = box.getY() + box.getHeight() / 2;
        clickAt(x, y);
        doLayout();
    }

    private void clickAt(int x, int y) {
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        host.__getRuntime().route(host.__getRoot(), builder.drainFrame(), 0, 0);
        host.__getRuntime().flush();
    }

    // ==================== 骨架结构 ====================

    @Test
    public void shellHasHeaderNavAndScrollViewport() {
        SceneNode root = host.__getRoot();
        List<SceneNode> children = root.__getChildren();
        Assert.assertEquals("root 三子：header/navBar/scrollContainer", 3, children.size());
        Assert.assertSame("header 为首页", host.__getHeader(), children.get(0));
        Assert.assertSame("navBar 为次子", host.__getNavBar(), children.get(1));
        host.__getNavBar();
        // navBar 内挂载了 segmented root（1 个直接子节点）
        Assert.assertEquals("navBar 挂 1 个 segmented", 1, host.__getNavBar().__getChildren().size());
        // 分段数与注册页数一致
        Assert.assertEquals("分段数 = 页数",
                PlaygroundPageRegistry.defaultPages().size(),
                host.__getNavBar().__getChildren().get(0).__getChildren().size());
        // content 挂在 viewport 下
        Assert.assertSame("content 在 viewport 内", host.__getContent().__getParent(), host.__getViewport());
    }

    @Test
    public void headerShowsTitleAndSubtitle() {
        SceneNode header = host.__getHeader();
        Assert.assertEquals("header 两行文本", 2, header.__getChildren().size());
        Assert.assertEquals("标题文本", "Qz UILib 测试场地", header.__getChildren().get(0).getText());
    }

    // ==================== 初始状态与页面切换 ====================

    @Test
    public void homeIsDefaultPage() {
        Assert.assertEquals("默认页下标 0", 0, host.__getDisplayedPageIndex());
        Assert.assertEquals("默认页 id=home", "home", host.__getDisplayedPageId());
        SceneNode pageRoot = host.__getDisplayedPageRoot();
        Assert.assertNotNull("默认页已挂载", pageRoot);
        Assert.assertSame("页面根挂在 content", pageRoot, host.__getContent().__getChildren().get(0));
    }

    @Test
    public void clickingSegmentsSwitchesPages() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        org.junit.Assume.assumeTrue("至少 3 页才演示中间切换", pages.size() >= 3);

        clickNode(navSegment(1));
        Assert.assertEquals("切到第 2 页", 1, host.__getDisplayedPageIndex());
        Assert.assertEquals(pages.get(1).id(), host.__getDisplayedPageId());
        Assert.assertNotNull(host.__getDisplayedPageRoot());

        int last = pages.size() - 1;
        clickNode(navSegment(last));
        Assert.assertEquals("切到末页", pages.get(last).id(), host.__getDisplayedPageId());

        clickNode(navSegment(0));
        Assert.assertEquals("切回首页", pages.get(0).id(), host.__getDisplayedPageId());
    }

    @Test
    public void switchDisposesOutgoingPageAndKeepsSingleLivePage() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        org.junit.Assume.assumeTrue("至少 2 页才演示切换析构", pages.size() >= 2);

        SceneNode homeRoot = host.__getDisplayedPageRoot();
        clickNode(navSegment(1));
        Assert.assertEquals("切到第 2 页", pages.get(1).id(), host.__getDisplayedPageId());
        Assert.assertNull("旧页根已从树摘除", homeRoot.__getParent());
        Assert.assertEquals("content 仅剩 1 个 live 页面", 1, host.__getContent().__getChildren().size());
        Assert.assertSame("页面根仍挂在 content",
                host.__getDisplayedPageRoot(), host.__getContent().__getChildren().get(0));
    }

    @Test
    public void switchingToSameIndexIsNoOp() {
        SceneNode homeRoot = host.__getDisplayedPageRoot();
        clickNode(navSegment(0));
        Assert.assertEquals("仍在 home", 0, host.__getDisplayedPageIndex());
        Assert.assertSame("页面根实例不变（未重建）", homeRoot, host.__getDisplayedPageRoot());
    }

    @Test
    public void everyRegistryPageMountsInHost() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        for (int i = 0; i < pages.size(); i++) {
            clickNode(navSegment(i));
            Assert.assertEquals("页 id 与注册表一致: " + pages.get(i).id(), i, host.__getDisplayedPageIndex());
            Assert.assertEquals(pages.get(i).id(), host.__getDisplayedPageId());
            Assert.assertNotNull("页面根非 null: " + pages.get(i).id(), host.__getDisplayedPageRoot());
        }
    }
}