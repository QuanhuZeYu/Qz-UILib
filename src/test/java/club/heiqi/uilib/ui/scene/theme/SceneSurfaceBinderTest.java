package club.heiqi.uilib.ui.scene.theme;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
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

/**
 * 通用表面绑定器契约测试。
 *
 * <p>覆盖：配方→属性写入、四态优先级、focus 缘色、关闭滤镜不发 BACKDROP 且底色可读、
 * 配方更新只改属性不重建、卸载回收绑定、前景回落、液态配方只采样一次背景、浮雕豁免位（P-05）
 * 与静态表面表达（四态同值 + 零过渡，ChatContainer 迁移路径）。</p>
 */
public class SceneSurfaceBinderTest {

    private static final Constraints CANVAS = new Constraints(200, 100);
    private static final float EPSILON = 0.0001F;
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

    private static SceneSurfaceStyle liquid() {
        return SceneSurfaceStyle.builder()
                .backdrop(UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN, 6, 1.0F))
                .cornerRadius(10)
                .build();
    }

    private static SceneSurfaceStyle alternate() {
        return SceneSurfaceStyle.builder()
                .backdrop(UiBackdrop.liquidGlass(UiGlassMaterial.DARK_REGULAR, 12, 0.4F))
                .cornerRadius(18)
                .build();
    }

    private static SceneSurfaceStyle opaque() {
        return SceneTheme.liquidGlassDark().withoutBackdrop().surface(SceneTheme.Role.PANEL);
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int backdropCount(SceneNode root) {
        PaintPlan plan = new ScenePaintEngine(new FixedTextMeasurer()).paint(root).getPlan();
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void bindsSurfaceAttributesFromRecipe() {
        Fixture f = new Fixture(liquid());
        SceneSurfaceStyle recipe = liquid();

        Assert.assertEquals("染色写入节点背景", recipe.getIdle().getTint(), f.node.getBackgroundColor());
        Assert.assertEquals("缘色写入节点边框", recipe.getIdle().getEdge(), f.node.getBorderColor());
        Assert.assertEquals("圆角来自配方", 10, f.node.getCornerRadius());
        Assert.assertEquals("边框宽度来自配方", recipe.getBorderWidth(), f.node.getBorderWidth());
        Assert.assertEquals("实体高度来自配方", recipe.getIdle().getElevation(), f.node.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull("液态配方写入滤镜", f.node.getBackdrop());
        Assert.assertEquals("模糊半径来自配方", 6, f.node.getBackdrop().getBlurRadius());
        Assert.assertEquals("lens 系数按当前状态缩放后进入滤镜",
                1.0F * recipe.getIdle().getLensFactor(),
                f.node.getBackdrop().getEffect().getLensStrength(), EPSILON);
        Assert.assertEquals("前景绑定消费配方前景回落", 0xFF010203, f.textNode.getTextColor());
    }

    @Test
    public void statePriorityFollowsDisabledPressedHoverIdle() {
        Fixture f = new Fixture(liquid());
        int idleTint = f.node.getBackgroundColor();

        f.harness.moveTo(f.node);
        f.rt.flush();
        Assert.assertEquals("hover 用配方 hovered 染色", liquid().getHovered().getTint(), f.node.getBackgroundColor());
        Assert.assertNotEquals("hover 有可见变化", idleTint, f.node.getBackgroundColor());

        f.harness.press(f.node);
        f.rt.flush();
        Assert.assertEquals("pressed 压过 hovered", liquid().getPressed().getTint(), f.node.getBackgroundColor());
        Assert.assertEquals("pressed 实体落下", 0.0F, f.node.__getSurfaceElevation(), EPSILON);

        f.enabled.set(Boolean.FALSE);
        f.rt.flush();
        Assert.assertEquals("disabled 压过 pressed", liquid().getDisabled().getTint(), f.node.getBackgroundColor());
        Assert.assertEquals("disabled 缘色独立", liquid().getDisabled().getEdge(), f.node.getBorderColor());

        f.enabled.set(Boolean.TRUE);
        f.rt.flush();
        Assert.assertEquals("恢复启用回到 pressed", liquid().getPressed().getTint(), f.node.getBackgroundColor());
    }

    @Test
    public void focusEdgeOverridesBorderOnlyWhenEnabled() {
        Fixture f = new Fixture(liquid());
        Assert.assertTrue(f.rt.requestFocus(f.node));
        f.rt.flush();

        Assert.assertEquals("聚焦缘色来自配方 focusEdge",
                liquid().getFocusEdge(), f.node.getBorderColor());

        f.enabled.set(Boolean.FALSE);
        f.rt.flush();
        Assert.assertEquals("禁用态不残留焦点缘色",
                liquid().getDisabled().getEdge(), f.node.getBorderColor());
    }

    @Test
    public void nullBackdropWritesNoBackdropAndKeepsOpaqueTint() {
        Fixture f = new Fixture(opaque());

        Assert.assertNull("关闭滤镜时节点不持滤镜", f.node.getBackdrop());
        Assert.assertEquals("关闭滤镜时不发 BACKDROP 命令", 0, backdropCount(f.sceneRoot));
        Assert.assertEquals("替代底色不透明可读", 0xFF, alpha(f.node.getBackgroundColor()));
        Assert.assertEquals("圆角与边框仍由同一配方给出",
                opaque().getCornerRadius(), f.node.getCornerRadius());
    }

    @Test
    public void styleUpdateRewritesAttributesWithoutRebuilding() {
        Fixture f = new Fixture(liquid());
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        SceneNode identity = f.node;

        f.style.set(alternate());
        f.rt.flush();

        Assert.assertSame("配方更新不重建节点", identity, f.node);
        Assert.assertEquals("圆角随配方更新", 18, f.node.getCornerRadius());
        Assert.assertEquals("滤镜随配方更新", 12, f.node.getBackdrop().getBlurRadius());
        Assert.assertEquals("材质随配方更新", UiGlassMaterial.DARK_REGULAR,
                f.node.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("配方更新不新增订阅", effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    @Test
    public void mountDisposeReleasesBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Fixture f = new Fixture(liquid());
        Assert.assertTrue("绑定应新增订阅", ReactiveTestProbe.registeredEffectCount() > baseline);

        f.handle.dispose();
        f.rt.flush();
        Assert.assertEquals("卸载回收全部绑定", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    @Test
    public void bindForegroundUsesRecipeForegroundWhenPresent() {
        SceneSurfaceStyle withForeground = liquid().toBuilder().foreground(0xFFAABBCC).build();
        Fixture f = new Fixture(withForeground);

        Assert.assertEquals("配方前景优先于回落色", 0xFFAABBCC, f.textNode.getTextColor());

        f.style.set(withForeground.toBuilder().foreground(null).build());
        f.rt.flush();
        Assert.assertEquals("配方不管理前景时回落 fallback", 0xFF010203, f.textNode.getTextColor());
    }

    @Test
    public void liquidRecipeEmitsExactlyOneBackdropCommand() {
        Fixture f = new Fixture(liquid());
        Assert.assertEquals("一颗表面只采样一次背景", 1, backdropCount(f.sceneRoot));

        f.style.set(opaque());
        f.rt.flush();
        Assert.assertEquals("切换为关闭滤镜后不再采样", 0, backdropCount(f.sceneRoot));
    }

    /**
     * 浮雕豁免位（P-05）：配方声明 {@code reliefDisabled} 时绑定器不消费四态 elevation，
     * 恒把节点 {@code surfaceElevation} 写 -1（普通绘制路径）；其余五项照常独占写入——
     * 豁免只关浮雕通道，不改变写入权归属。
     */
    @Test
    public void reliefDisabledKeepsElevationOffWithoutYieldingOtherAttributes() {
        SceneSurfaceStyle exempt = liquid().toBuilder()
                .idle(new SceneSurfaceStyle.StateStyle(0x40112233, 0x80334455, 0.8F, 1.0F))
                .reliefDisabled(true)
                .build();
        Fixture f = new Fixture(exempt);

        Assert.assertTrue("豁免只关通道、不改配方数据（elevation 仍在配方里 > 0）",
                exempt.getIdle().getElevation() > 0.0F);
        Assert.assertEquals("豁免：节点不进浮雕通道", -1.0F, f.node.__getSurfaceElevation(), EPSILON);
        Assert.assertEquals("豁免不影响底色独占写入",
                exempt.getIdle().getTint(), f.node.getBackgroundColor());
        Assert.assertEquals("豁免不影响缘色独占写入",
                exempt.getIdle().getEdge(), f.node.getBorderColor());
        Assert.assertEquals("豁免不影响圆角独占写入", 10, f.node.getCornerRadius());
        Assert.assertEquals("豁免不影响描边宽独占写入",
                exempt.getBorderWidth(), f.node.getBorderWidth());
        Assert.assertNotNull("豁免不影响滤镜独占写入", f.node.getBackdrop());
        Assert.assertEquals("豁免配方仍恰好采样一次背景", 1, backdropCount(f.sceneRoot));
    }

    /** 豁免位默认关闭：既有控件的浮雕写入行为一个字节都不变（防止豁免位写反成默认开）。 */
    @Test
    public void reliefDisabledIsOffByDefaultAndNonExemptKeepsElevation() {
        Assert.assertFalse("默认不豁免", liquid().isReliefDisabled());

        SceneSurfaceStyle normal = liquid().toBuilder()
                .idle(new SceneSurfaceStyle.StateStyle(0x40112233, 0x80334455, 0.8F, 1.0F))
                .build();
        Fixture f = new Fixture(normal);
        Assert.assertEquals("非豁免：四态 elevation 照常写入",
                0.8F, f.node.__getSurfaceElevation(), EPSILON);
    }

    /**
     * 静态表面表达（ChatContainer 迁移路径）：四态同值 + 过渡时长 0 时，交互态进入悬停/按下/禁用
     * 都不改变任何表面输出——与迁移前「绕开绑定器、只写一次 idle」的普通绘制通道逐值等价。
     * 本用例是「静态表面」语义的守卫：配方若退回四态异值，悬停断言当场变红。
     *
     * <p>缘色有<b>第五条</b>路径：绑定器在 {@code focused} 时改用配方级 {@code focusEdge}（不属于四态）。
     * ChatContainer 不可命中也不可聚焦，该路径恒不激活，故生产配方无需拉平；本用例为验证「静态表面」
     * 的完整形态，显式把 focusEdge 也拉平到缘色。</p>
     */
    @Test
    public void staticSurfaceRecipeIgnoresInteractionStateChanges() {
        SceneSurfaceStyle.StateStyle flat =
                new SceneSurfaceStyle.StateStyle(0x40112233, 0x80334455, 0.0F, 1.0F);
        SceneSurfaceStyle stat = liquid().toBuilder()
                .idle(flat).hovered(flat).pressed(flat).disabled(flat)
                .focusEdge(flat.getEdge())
                .transitionMillis(0)
                .reliefDisabled(true)
                .build();
        Fixture f = new Fixture(stat);
        int tintIdle = f.node.getBackgroundColor();
        int edgeIdle = f.node.getBorderColor();
        Assert.assertEquals("前置：静态表面本就不进浮雕通道",
                -1.0F, f.node.__getSurfaceElevation(), EPSILON);

        f.harness.moveTo(f.node);
        f.rt.flush();
        Assert.assertEquals("静态表面：悬停态底色不变", tintIdle, f.node.getBackgroundColor());
        Assert.assertEquals("静态表面：悬停态缘色不变", edgeIdle, f.node.getBorderColor());

        f.harness.press(f.node);
        f.rt.flush();
        Assert.assertEquals("静态表面：按下态底色不变", tintIdle, f.node.getBackgroundColor());
        Assert.assertEquals("静态表面：按下态缘色不变", edgeIdle, f.node.getBorderColor());
        Assert.assertEquals("静态表面：按下态浮雕仍关闭",
                -1.0F, f.node.__getSurfaceElevation(), EPSILON);
        f.harness.release(f.node);

        f.enabled.set(Boolean.FALSE);
        f.rt.flush();
        Assert.assertEquals("静态表面：禁用态底色不变", tintIdle, f.node.getBackgroundColor());
        Assert.assertEquals("静态表面：禁用态缘色不变", edgeIdle, f.node.getBorderColor());
    }

    /**
     * 豁免位参与配方等值判定：Computed 按值去重，若该位不进 equals，
     * 两个豁免状态不同的配方会被判等，下游绑定不重算——豁免形同虚设。
     */
    @Test
    public void reliefFlagParticipatesInRecipeEquality() {
        SceneSurfaceStyle base = liquid();
        Assert.assertNotEquals("浮雕豁免位参与配方等值判定",
                base, base.toBuilder().reliefDisabled(true).build());
        Assert.assertEquals("同豁免状态重建相等",
                base.toBuilder().reliefDisabled(true).build(),
                base.toBuilder().reliefDisabled(true).build());
        Assert.assertEquals("豁免状态不改变同值配方的 hashCode",
                base.toBuilder().reliefDisabled(true).build().hashCode(),
                base.toBuilder().reliefDisabled(true).build().hashCode());
    }

    private final class Fixture {
        final SceneInteractionHarness harness = SceneInteractionHarness.create();
        final SceneRuntime rt = harness.getRuntime();
        final SceneNode sceneRoot = SceneNode.column();
        final Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        final Signal<SceneSurfaceStyle> style;
        final MountHandle handle;
        final SceneNode node;
        final SceneNode textNode;

        Fixture(SceneSurfaceStyle initial) {
            style = Signal.create(initial);
            runtimes.add(rt);
            final SceneNode[] holder = new SceneNode[2];
            handle = rt.mount(sceneRoot, () -> {
                SceneNode surface = new SceneNode();
                surface.setPreferredWidth(80).setPreferredHeight(32);
                surface.setHitTestable(true);
                SceneSurfaceBinder.bind(rt, surface, style, enabled, rt.interactionState(surface));
                rt.focusable(surface);

                SceneNode text = new SceneNode();
                text.setText("T");
                text.setHitTestable(false);
                surface.appendChild(text);
                SceneSurfaceBinder.bindForeground(rt, text, style, 0xFF010203);

                holder[0] = surface;
                holder[1] = text;
                return surface;
            });
            node = holder[0];
            textNode = holder[1];
            rt.flush();
            harness.mountRoot(sceneRoot, CANVAS.getAvailableWidth(), CANVAS.getAvailableHeight());
            new SceneLayoutEngine(new FixedTextMeasurer()).layout(sceneRoot, CANVAS);
            Assert.assertTrue("表面节点必须有可命中的几何",
                    SceneGeometry.absoluteBox(node, 0, 0).getWidth() > 0);
        }
    }
}
