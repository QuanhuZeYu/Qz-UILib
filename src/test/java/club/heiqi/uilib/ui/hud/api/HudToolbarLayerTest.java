package club.heiqi.uilib.ui.hud.api;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * HUD 外接工具栏层契约：四边几何（不遮挡主体）、外框尺寸参与 placement、可见性挂摘、
 * 工具栏与内容拖动区域的事件互不抢占。
 */
public class HudToolbarLayerTest {

    private static final int CONTENT_W = 200;
    private static final int CONTENT_H = 100;
    private static final int GAP = 6;
    private static final int THICKNESS = 30;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    private static SceneNode content() {
        return SceneNode.column().setPreferredWidth(CONTENT_W).setPreferredHeight(CONTENT_H);
    }

    /** 工具栏节点只给内在宽度：高度由挂载边决定（水平边 = 厚度，竖直边 = 交叉轴拉满内容高）。 */
    private static SceneNode toolbarNode() {
        return SceneNode.row().setPreferredWidth(60);
    }

    private static HudToolbarSpec spec(HudToolbarSide side, Signal<Boolean> visible) {
        return HudToolbarSpec.builder(side).scaleControls(false).gap(GAP).thickness(THICKNESS).visible(visible).build();
    }

    private static void layout(SceneNode root) {
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16))
                .layout(root, new Constraints(800, 600));
    }

    private static AnchorRect box(SceneNode node) {
        return SceneGeometry.absoluteBox(node, 0, 0);
    }

    private static HudToolbarLayer.Result mount(SceneRuntime rt, HudToolbarSpec spec) {
        return HudToolbarLayer.mount(rt, spec, content(), r -> toolbarNode());
    }

    @Test
    public void bottomSideKeepsToolbarOutsideContentBelow() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        HudToolbarLayer.Result layer = mount(rt, spec(HudToolbarSide.BOTTOM, Signal.create(Boolean.TRUE)));
        rt.flush();
        layout(layer.root());

        AnchorRect content = box(layer.content());
        AnchorRect toolbar = box(layer.toolbar());
        Assert.assertEquals("内容从外框原点开始", 0, content.getY());
        Assert.assertEquals("工具栏在内容下方且留 gap",
                content.getY() + content.getHeight() + GAP, toolbar.getY());
        Assert.assertEquals("厚度钉死工具栏主轴尺寸", THICKNESS, toolbar.getHeight());
        Assert.assertTrue("工具栏不得与内容重叠",
                toolbar.getY() >= content.getY() + content.getHeight());
        Assert.assertEquals("外框高 = 内容 + gap + 厚度",
                CONTENT_H + GAP + THICKNESS, layer.outerHeight(CONTENT_H));
        Assert.assertEquals("水平边不改外框宽", CONTENT_W, layer.outerWidth(CONTENT_W));
    }

    @Test
    public void topSideKeepsToolbarOutsideContentAbove() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        HudToolbarLayer.Result layer = mount(rt, spec(HudToolbarSide.TOP, Signal.create(Boolean.TRUE)));
        rt.flush();
        layout(layer.root());

        AnchorRect toolbar = box(layer.toolbar());
        AnchorRect content = box(layer.content());
        Assert.assertEquals("工具栏在外框原点", 0, toolbar.getY());
        Assert.assertEquals("内容在工具栏下方且留 gap", THICKNESS + GAP, content.getY());
        Assert.assertTrue("工具栏不得与内容重叠",
                content.getY() >= toolbar.getY() + toolbar.getHeight());
    }

    @Test
    public void leftAndRightSidesAddThicknessToOuterWidth() {
        SceneRuntime leftRt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        HudToolbarLayer.Result left = mount(leftRt,
                spec(HudToolbarSide.LEFT, Signal.create(Boolean.TRUE)));
        leftRt.flush();
        layout(left.root());
        AnchorRect leftToolbar = box(left.toolbar());
        AnchorRect leftContent = box(left.content());
        Assert.assertEquals("左挂载：工具栏在原点", 0, leftToolbar.getX());
        Assert.assertEquals("左挂载：厚度钉死工具栏副轴尺寸", THICKNESS, leftToolbar.getWidth());
        Assert.assertEquals("左挂载：内容在工具栏右侧且留 gap", THICKNESS + GAP, leftContent.getX());
        Assert.assertEquals("竖直边外框宽 = 内容 + gap + 厚度",
                CONTENT_W + GAP + THICKNESS, left.outerWidth(CONTENT_W));
        Assert.assertEquals("竖直边不改外框高", CONTENT_H, left.outerHeight(CONTENT_H));
        Assert.assertEquals("竖直工具栏拉满内容高", CONTENT_H, leftToolbar.getHeight());

        SceneRuntime rightRt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        HudToolbarLayer.Result right = mount(rightRt,
                spec(HudToolbarSide.RIGHT, Signal.create(Boolean.TRUE)));
        rightRt.flush();
        layout(right.root());
        AnchorRect rightContent = box(right.content());
        AnchorRect rightToolbar = box(right.toolbar());
        Assert.assertEquals("右挂载：内容在原点", 0, rightContent.getX());
        Assert.assertEquals("右挂载：工具栏在内容右侧且留 gap",
                CONTENT_W + GAP, rightToolbar.getX());
        Assert.assertTrue("工具栏不得与内容重叠",
                rightToolbar.getX() >= rightContent.getX() + rightContent.getWidth());
    }

    @Test
    public void hiddenToolbarLeavesTreeAndShrinksOuterBoxThenReturns() {
        Signal<Boolean> visible = Signal.create(Boolean.FALSE);
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        HudToolbarLayer.Result layer = mount(rt, spec(HudToolbarSide.BOTTOM, visible));
        rt.flush();
        Assert.assertNull("不可见时工具栏不在树中", layer.toolbar().__getParent());
        Assert.assertFalse(layer.isVisible());
        Assert.assertEquals("不可见时外框退化为内容高", CONTENT_H, layer.outerHeight(CONTENT_H));
        Assert.assertEquals("不可见时外框只剩内容一个子节点", 1, layer.root().__getChildren().size());
        Assert.assertSame(layer.content(), layer.root().__getChildren().get(0));

        visible.set(Boolean.TRUE);
        rt.flush();
        Assert.assertNotNull("恢复可见时工具栏按挂载边插回", layer.toolbar().__getParent());
        Assert.assertEquals("插回后仍是内容之后", layer.content(), layer.root().__getChildren().get(0));
        Assert.assertEquals("恢复可见后外框重新计入厚度",
                CONTENT_H + GAP + THICKNESS, layer.outerHeight(CONTENT_H));

        visible.set(Boolean.FALSE);
        rt.flush();
        Assert.assertNull("再次隐藏仍能摘掉", layer.toolbar().__getParent());
    }

    @Test
    public void outerBoxFeedsPlacementSoToolbarStaysInsideSafeArea() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        HudToolbarLayer.Result layer = mount(rt, spec(HudToolbarSide.BOTTOM, Signal.create(Boolean.TRUE)));
        rt.flush();
        layout(layer.root());

        AnchorRect outer = HudLayoutResolver.resolve(
                HudPlacement.defaultOf(HudAnchor.BOTTOM_LEFT, 8),
                400, 200, layer.outerWidth(CONTENT_W), layer.outerHeight(CONTENT_H), HudInsets.NONE);
        Assert.assertEquals("外框高计入工具栏", CONTENT_H + GAP + THICKNESS, outer.getHeight());
        Assert.assertEquals("外框贴底：底边 = 视口 - margin", 200 - 8, outer.getY() + outer.getHeight());
        AnchorRect toolbar = box(layer.toolbar());
        AnchorRect content = box(layer.content());
        Assert.assertEquals("工具栏底部即外框底部（不越出安全区）",
                outer.getHeight(), toolbar.getY() + toolbar.getHeight());
        Assert.assertTrue("内容不被工具栏覆盖",
                toolbar.getY() >= content.getY() + content.getHeight());
    }

    /**
     * 事件仲裁：工具栏与内容拖动区域各自命中，不互相抢；工具栏按下停止冒泡，
     * 外框（编辑外壳的潜在宿主）不会把按钮按下当成拖动起点。
     */
    @Test
    public void toolbarPressesDoNotReachContentDragRegion() {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        List<String> log = new ArrayList<String>();
        try {
            HudToolbarSpec spec = spec(HudToolbarSide.BOTTOM, Signal.create(Boolean.TRUE));
            SceneNode content = content();
            HudToolbarLayer.Result layer = HudToolbarLayer.mount(harness.getRuntime(), spec, content,
                    r -> toolbarNode());
            // 编辑态语义：内容仅在编辑态可命中并承接拖动；工具栏始终可命中。
            content.setHitTestable(false);
            harness.getRuntime().on(content, club.heiqi.uilib.ui.scene.input.SceneEventType.POINTER_DOWN,
                    (event, ctx) -> log.add("drag"));
            harness.getRuntime().on(layer.toolbar(),
                    club.heiqi.uilib.ui.scene.input.SceneEventType.POINTER_DOWN,
                    (event, ctx) -> {
                        log.add("toolbar");
                        ctx.stopPropagation();
                    });
            harness.getRuntime().on(layer.root(),
                    club.heiqi.uilib.ui.scene.input.SceneEventType.POINTER_DOWN,
                    (event, ctx) -> log.add("wrapper"));
            harness.getRuntime().flush();
            harness.mountRoot(layer.root(), 800, 600);

            harness.click(layer.toolbar());
            Assert.assertEquals("工具栏按下只到工具栏，且不冒泡到外框", 1, log.size());
            Assert.assertEquals("toolbar", log.get(0));

            log.clear();
            content.setHitTestable(true);
            harness.getRuntime().flush();
            harness.click(content);
            Assert.assertTrue("内容可命中时必须收到拖动起点", log.contains("drag"));
            Assert.assertFalse("内容按下不得误触工具栏", log.contains("toolbar"));
        } finally {
            harness.dispose();
        }
    }
}
