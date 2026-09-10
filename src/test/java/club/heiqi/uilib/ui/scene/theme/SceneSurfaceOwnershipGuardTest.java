package club.heiqi.uilib.ui.scene.theme;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * 表面写入权守卫契约测试（G20 第 3 步）。
 *
 * <p>守卫的运行期语义：{@code SceneSurfaceBinder} 首次写入后，公开 setter 对**该节点**的
 * 表面属性（backgroundColor / borderColor / borderWidth / cornerRadius / backdrop）抛
 * {@link IllegalStateException}；绑定器自身走 {@code __write*} 内部通道不受影响；绑定释放
 * （Owner/Binding dispose）后写入权归还应用侧。契约依据见「契约 §4 属性归属表」。</p>
 *
 * <p>覆盖：接管后逐属性拒绝（含消息可诊断性）/ bind 前与首次 flush 前合法 / 释放后归还 /
 * 未接管节点与轻量槽零影响。</p>
 */
public class SceneSurfaceOwnershipGuardTest {

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

    /** 接管后：五个公开表面 setter 全部拒绝，且异常消息含属性名与节点诊断。 */
    @Test
    public void takeoverRejectsEveryPublicSurfaceSetter() {
        Fixture f = new Fixture();
        assertRejected(f.node, "backgroundColor", new Runnable() {
            public void run() { f.node.setBackgroundColor(0xFF112233); }
        });
        assertRejected(f.node, "borderColor", new Runnable() {
            public void run() { f.node.setBorderColor(0xFF112233); }
        });
        assertRejected(f.node, "borderWidth", new Runnable() {
            public void run() { f.node.setBorderWidth(3); }
        });
        assertRejected(f.node, "cornerRadius", new Runnable() {
            public void run() { f.node.setCornerRadius(11); }
        });
        assertRejected(f.node, "cornerRadius", new Runnable() {
            public void run() { f.node.setCornerRadius(1, 2, 3, 4); }
        });
        assertRejected(f.node, "backdrop", new Runnable() {
            public void run() { f.node.setBackdrop(null); }
        });
    }

    /** bind 之前的构造期写与 bind 之后首次 flush 之前的播种都合法（ChatInputChrome 先例）。 */
    @Test
    public void preBindAndPreFlushSeedingStayLegal() {
        SceneNode plain = new SceneNode();
        plain.setBackgroundColor(0xFF010203).setBorderWidth(2).setCornerRadius(9)
                .setBorderColor(0xFF040506);
        Assert.assertEquals("bind 之前的链式构造完全合法", 0xFF010203, plain.getBackgroundColor());
        Assert.assertEquals(2, plain.getBorderWidth());

        final SceneNode[] holder = new SceneNode[1];
        final SceneRuntime rt = SceneInteractionHarness.create().getRuntime();
        runtimes.add(rt);
        Signal<SceneSurfaceStyle> style = Signal.create(recipe());
        Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        rt.mount(SceneNode.column(), new java.util.function.Supplier<SceneNode>() {
            public SceneNode get() {
                SceneNode surface = new SceneNode();
                surface.setPreferredWidth(80).setPreferredHeight(32);
                SceneSurfaceBinder.bind(rt, surface, style, enabled, rt.interactionState(surface));
                // bind 之后、首次 flush 之前播种：绑定器尚未写入，接管标记未置位。
                surface.setCornerRadius(12);
                surface.setBorderWidth(5);
                holder[0] = surface;
                return surface;
            }
        });
        Assert.assertEquals("首次 flush 之前的播种合法", 12, holder[0].getCornerRadius());
        Assert.assertEquals(5, holder[0].getBorderWidth());

        rt.flush();
        Assert.assertEquals("flush 后绑定器接管，值取自配方", 10, holder[0].getCornerRadius());
        assertRejected(holder[0], "cornerRadius", new Runnable() {
            public void run() { holder[0].setCornerRadius(3); }
        });
    }

    /** 绑定释放后写入权归还：dispose 之后静态写必须成功且不被绑定器覆盖。 */
    @Test
    public void bindingDisposeReturnsWriteOwnership() {
        Fixture f = new Fixture();
        assertRejected(f.node, "backgroundColor", new Runnable() {
            public void run() { f.node.setBackgroundColor(0xFF112233); }
        });

        f.handle.dispose();
        f.rt.flush();

        f.node.setBackgroundColor(0xFF123456);
        f.node.setBorderWidth(4);
        f.node.setCornerRadius(6);
        Assert.assertEquals("释放后底色归应用侧", 0xFF123456, f.node.getBackgroundColor());
        Assert.assertEquals("释放后描边宽归应用侧", 4, f.node.getBorderWidth());
        Assert.assertEquals("释放后圆角归应用侧", 6, f.node.getCornerRadius());
    }

    /** 未接管节点零影响；轻量槽（写独占子节点）也不受守卫约束。 */
    @Test
    public void unboundNodeAndLightSlotsAreUntouched() {
        SceneNode plain = new SceneNode().setBackgroundColor(0xFF111111).setBorderWidth(1)
                .setBorderColor(0xFF222222).setCornerRadius(4).setBackdrop(null);
        Assert.assertEquals(1, plain.getBorderWidth());
        Assert.assertEquals(4, plain.getCornerRadius());

        Fixture f = new Fixture();
        SceneNode caret = new SceneNode();
        f.node.appendChild(caret);
        caret.setBackgroundColor(0xFFABCDEF);
        Assert.assertEquals("轻量槽写独占子节点不受影响", 0xFFABCDEF, caret.getBackgroundColor());
    }

    private static SceneSurfaceStyle recipe() {
        return SceneSurfaceStyle.builder()
                .backdrop(UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN, 6, 1.0F))
                .cornerRadius(10)
                .build();
    }

    private static void assertRejected(SceneNode node, String property, Runnable action) {
        try {
            action.run();
            Assert.fail("接管后静态写 " + property + " 应被拒绝");
        } catch (IllegalStateException expected) {
            String message = expected.getMessage();
            Assert.assertNotNull("异常消息不可为 null", message);
            Assert.assertTrue("消息应含属性名（实际=" + message + "）", message.contains(property));
            Assert.assertTrue("消息应含节点诊断（实际=" + message + "）",
                    message.contains("SceneNode#"));
        }
    }

    private final class Fixture {
        final SceneInteractionHarness harness = SceneInteractionHarness.create();
        final SceneRuntime rt = harness.getRuntime();
        final SceneNode sceneRoot = SceneNode.column();
        final Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        final Signal<SceneSurfaceStyle> style = Signal.create(recipe());
        final MountHandle handle;
        final SceneNode node;

        Fixture() {
            runtimes.add(rt);
            final SceneNode[] holder = new SceneNode[1];
            handle = rt.mount(sceneRoot, new java.util.function.Supplier<SceneNode>() {
                public SceneNode get() {
                    SceneNode surface = new SceneNode();
                    surface.setPreferredWidth(80).setPreferredHeight(32);
                    surface.setHitTestable(true);
                    SceneSurfaceBinder.bind(rt, surface, style, enabled, rt.interactionState(surface));
                    holder[0] = surface;
                    return surface;
                }
            });
            node = holder[0];
            rt.flush();
            harness.mountRoot(sceneRoot, CANVAS.getAvailableWidth(), CANVAS.getAvailableHeight());
            new SceneLayoutEngine(new FixedTextMeasurer()).layout(sceneRoot, CANVAS);
        }
    }
}