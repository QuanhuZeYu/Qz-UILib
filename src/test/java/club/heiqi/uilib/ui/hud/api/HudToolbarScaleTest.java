package club.heiqi.uilib.ui.hud.api;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/** 公共缩放状态、默认工具、真实 scene 点击及视觉占位契约。 */
public class HudToolbarScaleTest {
    @Before public void before() {
        HudToolbarService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }
    @After public void after() {
        HudToolbarService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }
    private static SceneNode content() {
        return SceneNode.column().setPreferredWidth(200).setPreferredHeight(100);
    }
    private static SceneNode custom() {
        return SceneNode.row().setPreferredWidth(40).setPreferredHeight(20);
    }

    @Test public void customPaddingIsPreservedWhenAddingScaleControls() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        try {
            SceneNode custom = custom().setPadding(3, 5, 7, 9);
            HudToolbarLayer.Result layer = HudToolbarLayer.mount(rt, HudToolbarSpec.builder().build(), content(), r -> custom);
            Assert.assertEquals(3, custom.getPaddingTop()); Assert.assertEquals(5, custom.getPaddingRight());
            Assert.assertEquals(7, custom.getPaddingBottom()); Assert.assertEquals(9, custom.getPaddingLeft());
            Assert.assertEquals(6, layer.toolbar().getGap());
        } finally { rt.dispose(); }
    }

    @Test public void emptyCustomFactoryDoesNotPushDefaultButtonsOutsideContent() {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        try {
            HudToolbarLayer.Result layer = HudToolbarLayer.mount(harness.getRuntime(),
                    HudToolbarSpec.builder().build(), content(), rt -> SceneNode.row());
            harness.getRuntime().flush();
            harness.mountRoot(layer.root(), 800, 600);
            SceneNode plus = layer.toolbar().__getChildren().get(3);
            AnchorRect box = club.heiqi.uilib.ui.scene.layout.SceneGeometry.absoluteBox(plus, 0, 0);
            Assert.assertTrue("空工厂不占满视口挤走按钮", box.getX() + box.getWidth() <= 200);
            harness.click(plus);
            Assert.assertEquals(110, layer.scale().percent().get().intValue());
        } finally { harness.dispose(); }
    }

    @Test public void stateAccumulatesBeforeFlushAndClamps() {
        HudScaleState state = new HudScaleState();
        state.zoomIn(); state.zoomIn(); state.zoomIn();
        Assert.assertEquals(130, state.percent().get().intValue());
        state.setPercent(Integer.MAX_VALUE);
        state.zoomIn();
        Assert.assertEquals(HudScaleState.MAX_PERCENT, state.percent().get().intValue());
        state.setPercent(Integer.MIN_VALUE);
        state.zoomOut();
        Assert.assertEquals(HudScaleState.MIN_PERCENT, state.percent().get().intValue());
        state.reset();
        Assert.assertEquals(1.0f, state.factor(), 0.0f);
    }

    @Test public void registrationsIsolateStateAndShareAcrossOccurrences() {
        HudToolbarService service = HudToolbarService.getInstance();
        HudToolbarSpec spec = HudToolbarSpec.builder().build();
        HudRegistration registration = service.register("test:a", spec, rt -> custom());
        service.register("test:b", spec, rt -> custom());
        SceneRuntime first = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneRuntime second = new SceneRuntime(new FixedTextMeasurer(8, 16));
        try {
            HudToolbarLayer.Result a = service.mountLayer(first, "test:a", content());
            HudToolbarLayer.Result b = service.mountLayer(second, "test:a", content());
            service.scale("test:a").setPercent(150);
            Assert.assertSame(a.scale(), b.scale());
            Assert.assertEquals(1.5f, b.scaleFactor(), 0.0f);
            Assert.assertEquals(1.0f, service.scale("test:b").factor(), 0.0f);
            registration.close();
            Assert.assertNull(service.scale("test:a"));
            service.register("test:a", spec, rt -> custom());
            Assert.assertEquals(1.0f, service.scale("test:a").factor(), 0.0f);
        } finally {
            first.dispose(); second.dispose();
        }
    }

    @Test public void defaultButtonsUseSceneClickAndRespectLimits() {
        for (HudToolbarSide side : HudToolbarSide.values()) {
            SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
            try {
                HudToolbarLayer.Result layer = HudToolbarLayer.mount(harness.getRuntime(),
                        HudToolbarSpec.builder(side).build(), content(), rt -> custom());
                harness.getRuntime().flush();
                harness.mountRoot(layer.root(), 800, 600);
                Assert.assertEquals(4, layer.toolbar().__getChildren().size());
                SceneNode out = layer.toolbar().__getChildren().get(1);
                SceneNode reset = layer.toolbar().__getChildren().get(2);
                SceneNode in = layer.toolbar().__getChildren().get(3);
                harness.click(in);
                Assert.assertEquals(110, layer.scale().percent().get().intValue());
                harness.click(reset);
                Assert.assertEquals(100, layer.scale().percent().get().intValue());
                harness.click(out);
                Assert.assertEquals(90, layer.scale().percent().get().intValue());
                layer.scale().setPercent(50);
                harness.getRuntime().flush();
                harness.click(out);
                Assert.assertEquals(50, layer.scale().percent().get().intValue());
                layer.scale().setPercent(200);
                harness.getRuntime().flush();
                harness.click(in);
                Assert.assertEquals(200, layer.scale().percent().get().intValue());
            } finally { harness.dispose(); }
        }
    }

    @Test public void optOutPreservesCustomRootAndScalingSurvivesHiddenToolbar() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        Signal<Boolean> visible = Signal.create(true);
        SceneNode custom = custom();
        try {
            HudToolbarLayer.Result layer = HudToolbarLayer.mount(rt,
                    HudToolbarSpec.builder().scaleControls(false).visible(visible).build(), content(), r -> custom);
            rt.flush();
            Assert.assertSame(custom, layer.toolbar());
            Assert.assertFalse(layer.spec().isScaleControls());
            layer.scale().setPercent(150);
            Assert.assertEquals(300, layer.outerWidth(200));
            Assert.assertEquals(198, layer.outerHeight(100));
            visible.set(false); rt.flush();
            Assert.assertFalse(layer.isVisible());
            Assert.assertEquals(150, layer.outerHeight(100));
            Assert.assertEquals(100, layer.logicalOuterHeight(100));
        } finally { rt.dispose(); }
    }

    @Test public void visualExtentAndBottomRightPlacementUseSameScaleForAllSides() {
        // 预期以工作站 Python Fraction 验算：logical = 200x132 或 232x100。
        for (HudToolbarSide side : HudToolbarSide.values()) {
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            try {
                HudToolbarLayer.Result layer = HudToolbarLayer.mount(rt,
                        HudToolbarSpec.builder(side).scaleControls(false).build(), content(), r -> custom());
                rt.flush();
                int[] percents = {50, 150};
                int[] widths = side.isHorizontalEdge() ? new int[] {100, 300} : new int[] {116, 348};
                int[] heights = side.isHorizontalEdge() ? new int[] {66, 198} : new int[] {50, 150};
                int[] xs = side.isHorizontalEdge() ? new int[] {692, 492} : new int[] {676, 444};
                int[] ys = side.isHorizontalEdge() ? new int[] {526, 394} : new int[] {542, 442};
                for (int i = 0; i < percents.length; i++) {
                    layer.scale().setPercent(percents[i]);
                    Assert.assertEquals(widths[i], layer.outerWidth(200));
                    Assert.assertEquals(heights[i], layer.outerHeight(100));
                    AnchorRect rect = HudLayoutResolver.resolve(HudPlacement.defaultOf(HudAnchor.BOTTOM_RIGHT, 8),
                            800, 600, layer.outerWidth(200), layer.outerHeight(100), HudInsets.NONE);
                    Assert.assertEquals(xs[i], rect.getX());
                    Assert.assertEquals(ys[i], rect.getY());
                }
            } finally { rt.dispose(); }
        }
    }
}
