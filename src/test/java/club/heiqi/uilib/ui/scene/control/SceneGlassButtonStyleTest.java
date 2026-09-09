package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiBackdropEffect;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/** 新配方覆盖真实 primitive、动态替换、内容属性主权与 Owner 清理。旧入口仍由原测试全量覆盖。 */
public class SceneGlassButtonStyleTest {
    private static final float EPSILON = 0.0001F;
    private final List<SceneRuntime> runtimes = new ArrayList<SceneRuntime>();

    @Before
    public void setUp() { ReactiveScheduler.get().reset(); }

    @After
    public void tearDown() {
        for (SceneRuntime runtime : runtimes) runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    @Test
    public void builderDefaultsAndCopiesKeepVariantDefaultsAndExplicitOverridesDistinct() {
        SceneGlassButtonStyle.Builder builder = SceneGlassButtonStyle.builder();
        SceneGlassButtonStyle standard = builder.build();
        SceneGlassButtonStyle primary = builder.variant(SceneButtonVariant.PRIMARY).build();
        Assert.assertEquals(0x0CEAF7FF, standard.getIdle().getTint());
        Assert.assertEquals(0x2079BEFF, primary.getIdle().getTint());
        Assert.assertEquals(8, standard.getCornerRadius());
        Assert.assertEquals(1, standard.getBorderWidth());
        Assert.assertEquals(160, standard.getTransitionMillis());
        Assert.assertEquals(2.0F, standard.getContentLift(), EPSILON);
        Assert.assertEquals(0.35F, standard.getDisabledOpacity(), EPSILON);
        Assert.assertNull(standard.getBackdrop());
        Assert.assertNull(standard.getForeground());
        Assert.assertEquals(standard, standard.toBuilder().build());
        Assert.assertEquals(standard.hashCode(), standard.toBuilder().build().hashCode());
        Assert.assertEquals(standard, SceneGlassButtonStyle.builder().variant(null).build());
        Assert.assertEquals(0x20FF8797, primary.toBuilder().variant(SceneButtonVariant.DANGER)
                .build().getIdle().getTint());
        SceneGlassButtonStyle.StateStyle explicit = new SceneGlassButtonStyle.StateStyle(
                0x33445566, 0xFF123456, 0.75F, 0.5F);
        SceneGlassButtonStyle custom = primary.toBuilder().idle(explicit).build();
        Assert.assertSame(explicit, custom.toBuilder().variant(SceneButtonVariant.DANGER).build().getIdle());
        Assert.assertEquals(standard.getIdle(), custom.toBuilder().variant(null).idle(null).build().getIdle());
    }

    @Test
    public void unseededComputedRecipeCanBindBeforeFirstFlush() {
        Fixture f = new Fixture(true, true);
        Assert.assertEquals(0x0CEAF7FF, f.button.getBackgroundColor());
        Assert.assertEquals(0.5F, f.button.__getSurfaceElevation(), EPSILON);
        f.style.set(f.style.get().toBuilder().variant(SceneButtonVariant.DANGER).foreground(0xFFABCDEF).build());
        f.flush();
        Assert.assertEquals(0x20FF8797, f.button.getBackgroundColor());
        Assert.assertEquals(0xFFABCDEF, f.content.getTextColor());
        f.mount.dispose();
        assertFrozenAfterDisposal(f);
    }

    @Test
    public void invalidNumericRecipesFailBeforeBinding() {
        rejects(() -> SceneGlassButtonStyle.builder().cornerRadius(-1).build());
        rejects(() -> SceneGlassButtonStyle.builder().borderWidth(-1).build());
        rejects(() -> SceneGlassButtonStyle.builder().transitionMillis(-1).build());
        rejects(() -> SceneGlassButtonStyle.builder().disabledOpacity(Float.NaN).build());
        rejects(() -> SceneGlassButtonStyle.builder().disabledOpacity(1.1F).build());
        rejects(() -> SceneGlassButtonStyle.builder().contentLift(Float.POSITIVE_INFINITY).build());
        rejects(() -> new SceneGlassButtonStyle.StateStyle(0, 0, -0.1F, 1.0F));
        rejects(() -> new SceneGlassButtonStyle.StateStyle(0, 0, 0.5F, Float.NaN));
    }

    @Test
    public void replacementChangesMaterialVariantShapeAndFilterWithoutRebinding() {
        Fixture f = new Fixture(true);
        Assert.assertEquals(0x0CEAF7FF, f.button.getBackgroundColor());
        UiBackdrop liquid = UiBackdrop.of(UiBackdropEffect.liquidGlass(UiGlassMaterial.DARK_THIN, 0.8F), 7, 1.25F);
        f.style.set(f.style.get().toBuilder().variant(SceneButtonVariant.PRIMARY).backdrop(liquid)
                .cornerRadius(12).borderWidth(2).contentLift(3.0F).build());
        f.flush();
        Assert.assertEquals(0x2079BEFF, f.button.getBackgroundColor());
        Assert.assertEquals(12, f.button.getCornerRadius());
        Assert.assertEquals(2, f.button.getBorderWidth());
        Assert.assertEquals(Transform.translate(0.0F, -1.5F), f.motionRoot.getTransform());
        Assert.assertEquals(0.68F, f.button.getBackdrop().getEffect().getLensStrength(), EPSILON);
        Assert.assertEquals(7, f.button.getBackdrop().getBlurRadius());
        Assert.assertEquals(1.25F, f.button.getBackdrop().getSaturation(), EPSILON);
        UiBackdrop other = UiBackdrop.of(UiBackdropEffect.liquidGlass(UiGlassMaterial.THIN, 0.4F), 3, 0.8F);
        f.style.set(f.style.get().toBuilder().backdrop(other).build());
        f.flush();
        Assert.assertEquals(UiGlassMaterial.THIN, f.button.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals(0.34F, f.button.getBackdrop().getEffect().getLensStrength(), EPSILON);
        UiBackdrop classic = UiBackdrop.of(UiBackdropEffect.classic(UiGlassMaterial.DARK_THIN), 4, 1.0F);
        f.style.set(f.style.get().toBuilder().backdrop(classic).build());
        f.flush();
        Assert.assertSame(classic, f.button.getBackdrop());
        f.style.set(f.style.get().toBuilder().backdrop(null).build());
        f.flush();
        Assert.assertNull(f.button.getBackdrop());
        f.assertBusinessProperties();
    }

    @Test
    public void customStatesRespectPressedAndDisabledPriorityAndFocusOnlyChangesEdge() {
        Fixture f = new Fixture(true);
        SceneGlassButtonStyle.StateStyle hovered = new SceneGlassButtonStyle.StateStyle(0x33445566, 0xFF112233, 0.75F, 0.9F);
        SceneGlassButtonStyle.StateStyle pressed = new SceneGlassButtonStyle.StateStyle(0x55445566, 0xFF223344, 0.1F, 0.2F);
        SceneGlassButtonStyle.StateStyle disabled = new SceneGlassButtonStyle.StateStyle(0x11445566, 0xFF334455, 0.2F, 0.3F);
        f.style.set(f.style.get().toBuilder().hovered(hovered).pressed(pressed).disabled(disabled)
                .focusEdge(0xFFABCDEF).disabledOpacity(0.2F).build());
        f.flush();
        f.harness.moveTo(f.button);
        f.flush();
        Assert.assertEquals(hovered.getTint(), f.button.getBackgroundColor());
        Assert.assertTrue(f.runtime.requestFocus(f.button));
        f.flush();
        Assert.assertEquals(0xFFABCDEF, f.button.getBorderColor());
        Assert.assertEquals(hovered.getTint(), f.button.getBackgroundColor());
        f.harness.press(f.button);
        f.flush();
        Assert.assertEquals(pressed.getTint(), f.button.getBackgroundColor());
        Assert.assertEquals(pressed.getElevation(), f.button.__getSurfaceElevation(), EPSILON);
        f.enabled.set(false);
        f.flush();
        Assert.assertEquals(disabled.getTint(), f.button.getBackgroundColor());
        Assert.assertEquals(disabled.getEdge(), f.button.getBorderColor());
        Assert.assertEquals(0.2F, f.motionRoot.getOpacity(), EPSILON);
        f.assertBusinessProperties();
    }

    @Test
    public void foregroundIsExplicitAndNullRestoresFallbackWithoutTouchingSiblings() {
        Fixture f = new Fixture(true);
        f.style.set(f.style.get().toBuilder().foreground(0xFF223344).build());
        f.flush();
        Assert.assertEquals(0xFF223344, f.content.getTextColor());
        Assert.assertEquals(0xFF778899, f.sibling.getTextColor());
        f.style.set(f.style.get().toBuilder().foreground(null).build());
        f.flush();
        Assert.assertEquals(0xFF112233, f.content.getTextColor());
        Assert.assertEquals(0xFF778899, f.sibling.getTextColor());
        f.assertBusinessProperties();
    }

    @Test
    public void dynamicDurationFinishesCurrentTracksAndMaterialNeverRevertsToCapturedRecipe() {
        Fixture f = new Fixture(true);
        f.runtime.__enableMotion();
        f.enabled.set(false);
        f.flush();
        f.sample(1);
        f.sample(41);
        Assert.assertTrue(f.motionRoot.getOpacity() > 0.35F && f.motionRoot.getOpacity() < 1.0F);
        f.style.set(f.style.get().toBuilder().transitionMillis(0).foreground(0xFF445566).build());
        f.flush();
        Assert.assertEquals(0.35F, f.motionRoot.getOpacity(), EPSILON);
        Assert.assertEquals(0xFF445566, f.content.getTextColor());
        Assert.assertEquals(0, f.runtime.__activeMotionCountForTest());
        UiBackdrop next = UiBackdrop.of(UiBackdropEffect.liquidGlass(UiGlassMaterial.THIN, 0.4F), 3, 0.8F);
        f.style.set(f.style.get().toBuilder().transitionMillis(400).backdrop(next).build());
        f.enabled.set(true);
        f.flush();
        f.sample(101);
        f.sample(301);
        Assert.assertTrue(f.motionRoot.getOpacity() > 0.35F && f.motionRoot.getOpacity() < 1.0F);
        f.style.set(f.style.get().toBuilder().backdrop(null).build());
        f.flush();
        f.sample(401);
        Assert.assertNull(f.button.getBackdrop());
        f.style.set(f.style.get().toBuilder().backdrop(next).build());
        f.flush();
        f.sample(501);
        Assert.assertEquals(1.0F, f.motionRoot.getOpacity(), EPSILON);
        Assert.assertEquals(UiGlassMaterial.THIN, f.button.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals(0.34F, f.button.getBackdrop().getEffect().getLensStrength(), EPSILON);
    }

    @Test
    public void unmountCancelsDynamicRecipeForegroundAndMotionSubscriptions() {
        Fixture f = new Fixture(true);
        f.runtime.__enableMotion();
        f.enabled.set(false);
        f.flush();
        f.sample(1);
        f.sample(41);
        f.mount.dispose();
        assertFrozenAfterDisposal(f);
    }

    @Test
    public void directBindingBelongsToRuntimeRootOwner() {
        Fixture f = new Fixture(false);
        f.runtime.__enableMotion();
        f.enabled.set(false);
        f.flush();
        f.sample(1);
        f.runtime.dispose();
        assertFrozenAfterDisposal(f);
    }

    private static void assertFrozenAfterDisposal(Fixture f) {
        int tint = f.button.getBackgroundColor();
        int foreground = f.content.getTextColor();
        Transform transform = f.motionRoot.getTransform();
        float opacity = f.motionRoot.getOpacity();
        UiBackdrop backdrop = f.button.getBackdrop();
        Assert.assertEquals(0, f.runtime.__activeMotionCountForTest());
        f.style.set(f.style.get().toBuilder().foreground(0xFFABCDEF).cornerRadius(24)
                .variant(SceneButtonVariant.DANGER).build());
        f.enabled.set(true);
        ReactiveScheduler.get().flush();
        f.sample(1001);
        Assert.assertEquals(tint, f.button.getBackgroundColor());
        Assert.assertEquals(foreground, f.content.getTextColor());
        Assert.assertEquals(transform, f.motionRoot.getTransform());
        Assert.assertEquals(opacity, f.motionRoot.getOpacity(), EPSILON);
        Assert.assertSame(backdrop, f.button.getBackdrop());
        Assert.assertEquals(8, f.button.getCornerRadius());
    }

    private static void rejects(Runnable action) {
        try { action.run(); Assert.fail("invalid style accepted"); }
        catch (IllegalArgumentException expected) { /* 明确拒绝非法公开配方。 */ }
    }

    private final class Fixture {
        final SceneInteractionHarness harness = SceneInteractionHarness.create();
        final SceneRuntime runtime = harness.getRuntime();
        final SceneNode root = SceneNode.column();
        final SceneNode motionRoot = SceneNode.row();
        final SceneNode sibling = new SceneNode();
        final Signal<Boolean> enabled = Signal.create(true);
        final Signal<SceneGlassButtonStyle> style = Signal.create(SceneGlassButtonStyle.builder().build());
        final SceneLayoutEngine layout = new SceneLayoutEngine(new FixedTextMeasurer());
        final Constraints canvas = new Constraints(200, 100);
        final MountHandle mount;
        final boolean computedSource;
        SceneNode button;
        SceneNode content;

        Fixture(boolean bindInsideMount) { this(bindInsideMount, false); }

        Fixture(boolean bindInsideMount, boolean computedSource) {
            this.computedSource = computedSource;
            runtimes.add(runtime);
            mount = runtime.mount(root, () -> {
                SceneButtonPrimitive.Result primitive = SceneButtonPrimitive.create(runtime,
                        new SceneButtonPrimitive.Props(Signal.create("Glass"), enabled, () -> {}));
                button = primitive.root();
                content = primitive.label();
                button.setPreferredWidth(80).setPreferredHeight(32);
                button.removeChild(content);
                motionRoot.setHitTestable(false);
                motionRoot.appendChild(content);
                sibling.setText("Sibling").setTextColor(0xFF778899).setHitTestable(false);
                motionRoot.appendChild(sibling);
                button.appendChild(motionRoot);
                content.setTransform(Transform.translate(3.0F, 4.0F)).setOpacity(0.6F);
                if (bindInsideMount) bind();
                return button;
            });
            if (!bindInsideMount) bind();
            runtime.flush();
            harness.mountRoot(root, 200, 100);
            layout.layout(root, canvas);
        }
        void bind() {
            ReadableSignal<SceneGlassButtonStyle> source = computedSource ? Computed.create(() -> style.get()) : style;
            SceneLiquidGlassStyle.bindButton(runtime, button, motionRoot, enabled, source);
            SceneLiquidGlassStyle.bindForeground(runtime, content, source, 0xFF112233);
        }
        void flush() {
            runtime.flush();
            Assert.assertEquals("配方变化不触发布局", 0, layout.layout(root, canvas).getRelayoutCount());
        }
        void sample(long millis) { runtime.__sampleMotion(TimeUnit.MILLISECONDS.toNanos(millis)); }
        void assertBusinessProperties() {
            Assert.assertEquals(Transform.translate(3.0F, 4.0F), content.getTransform());
            Assert.assertEquals(0.6F, content.getOpacity(), EPSILON);
            Assert.assertNull(button.getTransform());
            Assert.assertEquals(1.0F, button.getOpacity(), EPSILON);
        }
    }
}
