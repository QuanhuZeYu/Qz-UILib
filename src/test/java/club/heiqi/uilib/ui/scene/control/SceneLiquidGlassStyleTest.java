package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiBackdropEffect;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneHitTester;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/** 真实 primitive + 输入路由 + 绘制计划；玻璃反馈只改变外观，不改变布局和命中盒。 */
public class SceneLiquidGlassStyleTest {

    private static final float EPSILON = 0.0001F;
    private static final Constraints CANVAS = new Constraints(200, 100);
    private final List<SceneRuntime> runtimes = new ArrayList<SceneRuntime>();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        for (SceneRuntime runtime : runtimes) {
            runtime.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    @Test
    public void routedHoverPressAndReleaseChangeGlassWithoutMovingTheHitBox() {
        Fixture f = new Fixture(liquidBackdrop(), true);
        Appearance idle = f.frame();
        f.harness.moveTo(f.content);
        Assert.assertTrue(f.primitive.interaction().hovered().get());
        Appearance hover = f.frame();
        Assert.assertNotEquals("hover 有可见染色反馈", idle.background, hover.background);
        Assert.assertTrue("hover 缘光更明显", alpha(hover.edge) > alpha(idle.edge));
        Assert.assertTrue("hover 圆角展开", hover.radius > idle.radius);
        Assert.assertTrue("hover 透镜增强", hover.lens() > idle.lens());

        Assert.assertTrue(f.runtime.requestFocus(f.button));
        f.runtime.flush();
        Appearance focusedHover = f.frame();
        Assert.assertTrue(f.primitive.interaction().focused().get());
        Assert.assertNotEquals("焦点有独立轮廓", hover.edge, focusedHover.edge);
        Assert.assertEquals(hover.background, focusedHover.background);
        Assert.assertEquals(hover.lens(), focusedHover.lens(), EPSILON);

        f.harness.press(f.content);
        Assert.assertTrue(f.primitive.interaction().pressed().get());
        Assert.assertTrue("验证 pressed 与 hover 同时存在", f.primitive.interaction().hovered().get());
        Appearance pressed = f.frame();
        Assert.assertNotEquals("pressed 压过 hover 染色", hover.background, pressed.background);
        Assert.assertTrue("按压收拢圆角", pressed.radius < hover.radius);
        Assert.assertTrue("按压减弱透镜", pressed.lens() < hover.lens());
        f.leave();
        Assert.assertFalse(f.primitive.interaction().hovered().get());
        Assert.assertTrue("真实按压捕获持续到 UP", f.primitive.interaction().pressed().get());
        assertAppearance(pressed, f.frame());

        f.harness.moveTo(f.content);
        f.harness.release(f.content);
        Assert.assertFalse(f.primitive.interaction().pressed().get());
        Assert.assertEquals("同一按钮 DOWN/UP 真实合成一次激活", 1, f.clicks.get());
        assertAppearance(focusedHover, f.frame());
        f.leave();
        f.harness.pressAt(-1, -1);
        f.harness.releaseAt(-1, -1);
        Assert.assertFalse(f.primitive.interaction().focused().get());
        assertAppearance(idle, f.frame());
    }

    @Test
    public void disabledOverridesAnAlreadyFocusedPressedButtonAndBlocksActivation() {
        Fixture f = new Fixture(liquidBackdrop(), true);
        Appearance idle = f.frame();
        f.enabled.set(Boolean.FALSE);
        Appearance disabled = f.frame();
        Assert.assertTrue("禁用内容仍可辨认", disabled.opacity > 0.0F);
        Assert.assertTrue("禁用降低内容透明度", disabled.opacity < idle.opacity);
        Assert.assertEquals("禁用关闭液态透镜", 0.0F, disabled.lens(), EPSILON);
        Assert.assertNotEquals(idle.background, disabled.background);
        Assert.assertEquals(SceneCursor.NOT_ALLOWED, f.button.getCursor());

        f.enabled.set(Boolean.TRUE);
        f.runtime.flush();
        f.harness.moveTo(f.content);
        f.harness.press(f.content);
        Assert.assertTrue(f.primitive.interaction().focused().get());
        Assert.assertTrue(f.primitive.interaction().pressed().get());
        Appearance active = f.frame();
        f.enabled.set(Boolean.FALSE);
        Appearance disabledWhilePressed = f.frame();
        Assert.assertTrue("禁用时按压尚未 UP，确实在检验优先级", f.primitive.interaction().pressed().get());
        Assert.assertFalse("primitive 禁用会退出焦点", f.primitive.interaction().focused().get());
        Assert.assertNotEquals("不得残留焦点缘光", active.edge, disabledWhilePressed.edge);
        assertAppearance(disabled, disabledWhilePressed);
        Assert.assertFalse("禁用按钮不能重新获得焦点", f.runtime.requestFocus(f.button));
        f.harness.release(f.content);
        f.harness.pressKey(SceneKey.ENTER);
        f.harness.click(f.content);
        Assert.assertEquals("禁用不触发指针或键盘激活", 0, f.clicks.get());
        f.leave();
        assertAppearance(disabled, f.frame());

        f.enabled.set(Boolean.TRUE);
        assertAppearance(idle, f.frame());
        Assert.assertEquals(SceneCursor.POINTER, f.button.getCursor());
        f.harness.click(f.content);
        f.harness.pressKey(SceneKey.SPACE);
        Assert.assertEquals("重新启用后指针与键盘都可用", 2, f.clicks.get());
        f.frame();
    }

    @Test
    public void motionInterpolatesPaintSmoothlyBetweenObservedEndpoints() {
        Fixture f = new Fixture(liquidBackdrop(), true);
        Appearance idle = f.frame();
        f.harness.moveTo(f.content);
        Appearance hover = f.frame();
        f.leave();
        f.frame();
        f.runtime.__enableMotion();

        f.harness.moveTo(f.content);
        assertAppearance(idle, f.frame());
        f.sample(1);
        assertAppearance(idle, f.frame());
        f.sample(41);
        Appearance early = f.frame();
        f.sample(81);
        Appearance middle = f.frame();
        f.sample(121);
        Appearance late = f.frame();
        assertBetween("背景 alpha 中间值", alpha(idle.background), alpha(middle.background), alpha(hover.background));
        assertBetween("缘光 alpha 中间值", alpha(idle.edge), alpha(middle.edge), alpha(hover.edge));
        assertBetween("圆角中间值", idle.radius, middle.radius, hover.radius);
        assertBetween("透镜中间值", idle.lens(), middle.lens(), hover.lens());
        Assert.assertTrue(early.lens() < middle.lens());
        Assert.assertTrue(middle.lens() < late.lens());
        Assert.assertTrue("等长时间段中间快于起步，避免线性或跳变反馈",
                middle.lens() - early.lens() > early.lens() - idle.lens());
        Assert.assertTrue("末段减速到端点",
                late.lens() - middle.lens() > hover.lens() - late.lens());
        f.sample(161);
        assertAppearance(hover, f.frame());
        Assert.assertEquals(0, f.runtime.__activeMotionCountForTest());
    }

    @Test
    public void leavingDuringHoverMotionRedirectsFromTheCurrentAppearance() {
        Fixture f = new Fixture(liquidBackdrop(), true);
        Appearance idle = f.frame();
        f.runtime.__enableMotion();
        f.harness.moveTo(f.content);
        f.sample(1);
        f.sample(81);
        Appearance turning = f.frame();
        Assert.assertTrue(turning.lens() > idle.lens());

        f.leave();
        assertAppearance(turning, f.frame());
        f.sample(82);
        assertAppearance(turning, f.frame());
        f.sample(162);
        Appearance returning = f.frame();
        assertBetween("离开后透镜从当前值平滑回退", idle.lens(), returning.lens(), turning.lens());
        assertBetween("染色同样重定向", alpha(idle.background), alpha(returning.background), alpha(turning.background));
        f.sample(242);
        assertAppearance(idle, f.frame());
        Assert.assertEquals("没有继续追逐已失效的 hover 目标", 0, f.runtime.__activeMotionCountForTest());
    }

    @Test
    public void disabledOpacityUsesCompositeMotionAndMountDisposalStopsAllStyleWrites() {
        Fixture f = new Fixture(liquidBackdrop(), true);
        f.enabled.set(Boolean.FALSE);
        Appearance disabled = f.frame();
        f.enabled.set(Boolean.TRUE);
        f.runtime.flush();
        f.harness.moveTo(f.content);
        f.harness.press(f.content);
        Appearance active = f.frame();
        f.runtime.__enableMotion();
        f.enabled.set(Boolean.FALSE);
        assertAppearance(active, f.frame());
        f.sample(1);
        f.frame();
        f.sample(81);
        Assert.assertTrue("内容透明度采样标记 composite", f.content.__isCompositeDirty());
        Assert.assertFalse("透明度不重建内容 paint fragment", f.content.__isSelfPaintDirty());
        Assert.assertTrue("玻璃与染色采样标记 paint", f.button.__isSelfPaintDirty());
        Appearance middle = f.frame();
        assertBetween("禁用内容透明度有中间帧", disabled.opacity, middle.opacity, active.opacity);
        Assert.assertTrue(f.runtime.__activeMotionCountForTest() > 0);

        f.handle.dispose();
        Assert.assertFalse(f.sceneRoot.__getChildren().contains(f.button));
        assertNoLateWrites(f);
    }

    @Test
    public void bindingOutsideMountBelongsToRuntimeAndStopsOnRuntimeDisposal() {
        Fixture f = new Fixture(liquidBackdrop(), false);
        f.runtime.__enableMotion();
        f.harness.moveTo(f.content);
        f.sample(1);
        f.sample(81);
        f.frame();
        Assert.assertTrue(f.runtime.__activeMotionCountForTest() > 0);
        f.runtime.dispose();
        assertNoLateWrites(f);
    }

    @Test
    public void nullBackdropKeepsStateChromeAndUsabilityWithoutFilterCommands() {
        Fixture f = new Fixture(null, true);
        Appearance idle = f.frame();
        f.harness.moveTo(f.content);
        Appearance hover = f.frame();
        Assert.assertNotEquals(idle.background, hover.background);
        Assert.assertNotEquals(idle.edge, hover.edge);
        Assert.assertTrue(hover.radius > idle.radius);
        f.harness.press(f.content);
        Appearance pressed = f.frame();
        Assert.assertNotEquals(hover.background, pressed.background);
        Assert.assertTrue(pressed.radius < hover.radius);
        f.harness.release(f.content);
        Assert.assertEquals(1, f.clicks.get());
        f.frame();
        f.enabled.set(Boolean.FALSE);
        Appearance disabled = f.frame();
        Assert.assertNotEquals(pressed.edge, disabled.edge);
        Assert.assertTrue(disabled.opacity > 0.0F && disabled.opacity < idle.opacity);
        f.harness.click(f.content);
        Assert.assertEquals("无滤镜模式仍遵守禁用行为", 1, f.clicks.get());
        f.frame();
    }

    @Test
    public void classicRecipeIsPreservedInsteadOfBeingConvertedToLiquidGlass() {
        UiBackdrop classic = UiBackdrop.of(UiBackdropEffect.classic(UiGlassMaterial.THIN), 9, 1.3F);
        Fixture f = new Fixture(classic, true);
        Assert.assertSame(classic, f.frame().backdrop);
        f.harness.moveTo(f.content);
        Assert.assertSame(classic, f.frame().backdrop);
        f.harness.press(f.content);
        Assert.assertSame(classic, f.frame().backdrop);
        f.enabled.set(Boolean.FALSE);
        Assert.assertSame(classic, f.frame().backdrop);
    }

    private static UiBackdrop liquidBackdrop() {
        // 非默认配方，防止样式偷偷换成自己的材质、模糊或饱和度。
        return UiBackdrop.of(UiBackdropEffect.liquidGlass(UiGlassMaterial.DARK_THIN, 0.8F), 7, 1.25F);
    }

    private static int alpha(int color) {
        return color >>> 24;
    }

    private static void assertBetween(String message, float low, float actual, float high) {
        Assert.assertTrue(message + ": " + low + " < " + actual + " < " + high, low < actual && actual < high);
    }

    private static void assertAppearance(Appearance expected, Appearance actual) {
        Assert.assertEquals("染色", expected.background, actual.background);
        Assert.assertEquals("缘光", expected.edge, actual.edge);
        Assert.assertEquals("圆角", expected.radius, actual.radius);
        Assert.assertEquals("内容透明度", expected.opacity, actual.opacity, EPSILON);
        Assert.assertEquals("玻璃配方", expected.backdrop, actual.backdrop);
    }

    private static void assertNoLateWrites(Fixture f) {
        Appearance frozen = new Appearance(f.button, f.content);
        SceneCursor cursor = f.button.getCursor();
        f.button.clearDirtyFlags();
        f.content.clearDirtyFlags();
        Assert.assertEquals("Owner 清理取消所有样式轨道", 0, f.runtime.__activeMotionCountForTest());
        // 同时覆盖尚在进行的动画，以及卸载后新的上游 signal 意图。
        for (boolean enabled : new boolean[] { false, true }) {
            f.enabled.set(Boolean.valueOf(enabled));
            ReactiveScheduler.get().flush();
            f.sample(enabled ? 1001 : 501);
            assertAppearance(frozen, new Appearance(f.button, f.content));
            Assert.assertSame("卸载后不再派生新滤镜对象", frozen.backdrop, f.button.getBackdrop());
            Assert.assertEquals(cursor, f.button.getCursor());
            Assert.assertFalse(f.button.__isSelfPaintDirty());
            Assert.assertFalse(f.button.__isSelfLayoutDirty());
            Assert.assertFalse(f.content.__isCompositeDirty());
            Assert.assertFalse(f.content.__isSelfLayoutDirty());
            Assert.assertEquals(0, f.runtime.__activeMotionCountForTest());
        }
    }

    private static PaintCommand first(PaintPlan plan, PaintCommandType type) {
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == type) return command;
        }
        return null;
    }

    private static void assertBounds(AnchorRect bounds, PaintCommand command) {
        Assert.assertNotNull(command);
        Assert.assertEquals(bounds.getX(), command.getLeft());
        Assert.assertEquals(bounds.getY(), command.getTop());
        Assert.assertEquals(bounds.getX() + bounds.getWidth(), command.getRight());
        Assert.assertEquals(bounds.getBottom(), command.getBottom());
    }

    private static final class Appearance {
        final int background;
        final int edge;
        final int radius;
        final float opacity;
        final UiBackdrop backdrop;

        Appearance(SceneNode button, SceneNode content) {
            background = button.getBackgroundColor();
            edge = button.getBorderColor();
            radius = button.getCornerRadius();
            opacity = content.getOpacity();
            backdrop = button.getBackdrop();
        }

        float lens() {
            return backdrop.getEffect().getLensStrength();
        }
    }

    private final class Fixture {
        final SceneInteractionHarness harness = SceneInteractionHarness.create();
        final SceneRuntime runtime = harness.getRuntime();
        final SceneNode sceneRoot = SceneNode.column();
        final Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        final AtomicInteger clicks = new AtomicInteger();
        final SceneLayoutEngine layout = new SceneLayoutEngine(new FixedTextMeasurer());
        final ScenePaintEngine paint = new ScenePaintEngine(new FixedTextMeasurer());
        final UiBackdrop baseBackdrop;
        final MountHandle handle;
        final SceneNode button;
        final SceneNode content;
        final Object buttonLayout;
        final Object contentLayout;
        final AnchorRect bounds;
        SceneButtonPrimitive.Result primitive;

        Fixture(UiBackdrop baseBackdrop, boolean bindInsideMount) {
            this.baseBackdrop = baseBackdrop;
            runtimes.add(runtime);
            handle = runtime.mount(sceneRoot, () -> {
                primitive = SceneButtonPrimitive.create(runtime, new SceneButtonPrimitive.Props(
                        Signal.create("Glass"), enabled, clicks::incrementAndGet));
                primitive.root().setPreferredWidth(80).setPreferredHeight(32);
                if (bindInsideMount) {
                    SceneLiquidGlassStyle.bindButton(runtime, primitive.root(), primitive.label(),
                            enabled, SceneButtonVariant.STANDARD, baseBackdrop);
                }
                return primitive.root();
            });
            button = primitive.root();
            content = primitive.label();
            if (!bindInsideMount) {
                SceneLiquidGlassStyle.bindButton(runtime, button, content,
                        enabled, SceneButtonVariant.STANDARD, baseBackdrop);
            }
            runtime.flush();
            harness.mountRoot(sceneRoot, CANVAS.getAvailableWidth(), CANVAS.getAvailableHeight());
            layout.layout(sceneRoot, CANVAS);
            buttonLayout = button.getCachedLayout();
            contentLayout = content.getCachedLayout();
            bounds = SceneGeometry.absoluteBox(button, 0, 0);
            Assert.assertTrue(bounds.getWidth() > 0 && bounds.getHeight() > 0);
            paint.paint(sceneRoot);
        }

        void leave() {
            harness.moveAt(-1, -1);
        }

        void sample(long millis) {
            runtime.__sampleMotion(TimeUnit.MILLISECONDS.toNanos(millis));
        }

        Appearance frame() {
            runtime.flush();
            Assert.assertFalse("外观不应标脏按钮布局", button.__isSelfLayoutDirty());
            Assert.assertFalse("透明度不应标脏内容布局", content.__isSelfLayoutDirty());
            Assert.assertEquals("每个交互/动画帧零重排", 0, layout.layout(sceneRoot, CANVAS).getRelayoutCount());
            Assert.assertSame("复用 button 布局盒", buttonLayout, button.getCachedLayout());
            Assert.assertSame("复用 content 布局盒", contentLayout, content.getCachedLayout());
            SceneHitTester hitTester = new SceneHitTester();
            Assert.assertTrue("圆角变化不挖掉布局命中盒左上角",
                    hitTester.hitTest(sceneRoot, bounds.getX(), bounds.getY(), 0, 0).contains(button));
            Assert.assertTrue("圆角变化不缩小命中盒右下角", hitTester.hitTest(sceneRoot,
                    bounds.getX() + bounds.getWidth() - 1, bounds.getBottom() - 1, 0, 0).contains(button));
            Assert.assertFalse("滤镜不扩大按钮命中盒", hitTester.hitTest(sceneRoot,
                    bounds.getX() + bounds.getWidth(), bounds.getY(), 0, 0).contains(button));

            PaintPlan plan = paint.paint(sceneRoot).getPlan();
            Appearance appearance = new Appearance(button, content);
            PaintCommand background = first(plan, PaintCommandType.BACKGROUND);
            PaintCommand border = first(plan, PaintCommandType.BORDER);
            assertBounds(bounds, background);
            assertBounds(bounds, border);
            Assert.assertEquals(appearance.background, background.getColor());
            Assert.assertEquals(appearance.edge, border.getColor());
            Assert.assertEquals(appearance.radius, background.getCornerRadius());
            Assert.assertEquals(appearance.radius, border.getCornerRadius());
            Assert.assertTrue("始终保留可见边框", border.getBorderWidth() > 0 && alpha(border.getColor()) > 0);
            Assert.assertNotNull("禁用只降低可见度，仍绘制内容", first(plan, PaintCommandType.TEXT));
            PaintCommand opacity = first(plan, PaintCommandType.PUSH_OPACITY);
            if (appearance.opacity < 1.0F) {
                Assert.assertNotNull("内容透明度进入真实合成命令", opacity);
                Assert.assertEquals(appearance.opacity, opacity.getOpacity(), EPSILON);
                Assert.assertNotNull(first(plan, PaintCommandType.POP_OPACITY));
            } else {
                Assert.assertNull(opacity);
            }
            PaintCommand backdrop = first(plan, PaintCommandType.BACKDROP);
            if (baseBackdrop == null) {
                Assert.assertNull("关闭滤镜时节点不持配方", appearance.backdrop);
                Assert.assertNull("关闭滤镜时不产生 BACKDROP", backdrop);
            } else {
                assertBounds(bounds, backdrop);
                Assert.assertEquals(appearance.radius, backdrop.getCornerRadius());
                Assert.assertEquals(appearance.backdrop, backdrop.getBackdrop());
                Assert.assertEquals(baseBackdrop.getBlurRadius(), backdrop.getBackdrop().getBlurRadius());
                Assert.assertEquals(baseBackdrop.getSaturation(), backdrop.getBackdrop().getSaturation(), EPSILON);
                Assert.assertSame(baseBackdrop.getEffect().getMaterial(), backdrop.getBackdrop().getEffect().getMaterial());
                Assert.assertEquals(baseBackdrop.getEffect().getFamily(), backdrop.getBackdrop().getEffect().getFamily());
                Assert.assertTrue("滤镜先于透明染色", plan.getCommands().indexOf(backdrop) < plan.getCommands().indexOf(background));
            }
            return appearance;
        }
    }
}
