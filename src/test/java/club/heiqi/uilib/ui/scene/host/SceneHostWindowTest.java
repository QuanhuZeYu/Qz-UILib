package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.env.UiEnvironment;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@link SceneHostWindow} 的装配契约：外壳语义、装饰层隔离、空内容判定与环境根成对。
 *
 * <p>只钉「上提后新增/易退化的语义」——外壳/管线/放置的既有行为已由
 * {@code SceneHudPipelineTest} 在客户端宿主层覆盖，此处不重复镜像。</p>
 */
public class SceneHostWindowTest {

    private static final FixedTextMeasurer MEASURER = new FixedTextMeasurer(8, 16);

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    private static SceneHostWindow.ContentFactory body(String text) {
        return rt -> SceneNode.row().setHitTestable(false).setText(text).setFontSize(14);
    }

    private static SceneHostWindow assemble(SceneHostWindow.Shell shell,
            SceneHostWindow.ContentFactory factory, SceneHostWindow.ContentDecorator decorator,
            Consumer<RuntimeException> failureSink) {
        return new SceneHostWindow(MEASURER, UiEnvironment.empty(), shell, factory, decorator,
                failureSink);
    }

    @Test
    public void defaultShellCarriesPaddingAndBackground() {
        SceneHostWindow window = assemble(SceneHostWindow.Shell.HUD_DEFAULT, body("BODY"), null, null);
        try {
            assertEquals(7, window.root().getPaddingLeft());
            assertEquals(7, window.root().getPaddingRight());
            assertEquals(6, window.root().getPaddingTop());
            assertEquals(6, window.root().getPaddingBottom());
            assertEquals(SceneChromeTokens.HUD_SHELL_BG, window.root().getBackgroundColor());
            LayoutBox box = window.measure(200, 100);
            assertEquals("外壳盒 = 内容 4 字符 × 8px + 两侧内边距", 2 * 7 + 32, box.getWidth());
            assertEquals("外壳盒 = 行高 16 + 上下内边距", 2 * 6 + 16, box.getHeight());
        } finally {
            window.dispose();
        }
    }

    @Test
    public void bareShellLeavesContentFlush() {
        SceneHostWindow window = assemble(SceneHostWindow.Shell.BARE, body("BODY"), null, null);
        try {
            assertEquals(0, window.root().getPaddingLeft());
            assertEquals(0, window.root().getPaddingTop());
            LayoutBox box = window.measure(200, 100);
            assertEquals(32, box.getWidth());
            assertEquals(16, box.getHeight());
        } finally {
            window.dispose();
        }
    }

    @Test
    public void decoratorExtendsOuterMeasurement() {
        SceneHostWindow window = assemble(SceneHostWindow.Shell.HUD_DEFAULT, body("BODY"),
                (rt, content) -> {
                    SceneNode outer = SceneNode.column().setHitTestable(false)
                            .setWidthSizing(SceneNode.WidthSizing.SHRINK);
                    outer.appendChild(content);
                    outer.appendChild(SceneNode.row().setHitTestable(false)
                            .setWidthSizing(SceneNode.WidthSizing.SHRINK)
                            .setText("BAR").setFontSize(14).setPreferredHeight(20));
                    return outer;
                }, null);
        try {
            assertSame("内容根仍是工厂产物，装饰层只是外框", "BODY", window.content().getText());
            LayoutBox box = window.measure(200, 100);
            assertEquals("装饰层高度必须计入外框", 2 * 6 + 16 + 20, box.getHeight());
        } finally {
            window.dispose();
        }
    }

    @Test
    public void decoratorFailureIsIsolatedAndReported() {
        List<RuntimeException> reported = new ArrayList<RuntimeException>();
        SceneHostWindow window = assemble(SceneHostWindow.Shell.HUD_DEFAULT, body("BODY"),
                (rt, content) -> {
                    throw new IllegalStateException("toolbar boom");
                }, reported::add);
        try {
            assertEquals("失败必须上报", 1, reported.size());
            assertEquals("toolbar boom", reported.get(0).getMessage());
            assertEquals("装饰失败 → 内容直通（外框 = 内容根）", 1,
                    window.root().__getChildren().size());
            assertSame(window.content(), window.root().__getChildren().get(0));
            assertEquals("主体照常可测量", 2 * 6 + 16, window.measure(200, 100).getHeight());
        } finally {
            window.dispose();
        }
    }

    @Test
    public void decoratorReturningNullIsTreatedAsFailure() {
        List<RuntimeException> reported = new ArrayList<RuntimeException>();
        SceneHostWindow window = assemble(SceneHostWindow.Shell.BARE, body("BODY"),
                (rt, content) -> null, reported::add);
        try {
            assertEquals(1, reported.size());
            assertSame(window.content(), window.root().__getChildren().get(0));
        } finally {
            window.dispose();
        }
    }

    /** 空内容整窗隐藏，但帧推进照常 → signal 物化不被跳帧锁死（宿主合同 A2）。 */
    @Test
    public void emptyContentIsHiddenYetSignalsStillMaterialize() {
        Signal<Boolean> show = Signal.create(Boolean.FALSE);
        SceneHostWindow window = assemble(SceneHostWindow.Shell.HUD_DEFAULT, rt -> {
            SceneNode root = SceneNode.row().setHitTestable(false);
            rt.show(root, show, () -> SceneNode.row().setHitTestable(false)
                    .setText("late").setFontSize(14));
            return root;
        }, null, null);
        try {
            window.measure(200, 100);
            assertTrue("signal 未物化 → 内容空尺寸", window.isEmptyContent());
            show.set(Boolean.TRUE);
            window.settleWithoutPaint(200, 100, 1L); // 空窗帧推进照常 flush/layout
            window.measure(200, 100);
            assertFalse("空窗 settle 后内容必须物化", window.isEmptyContent());
        } finally {
            window.dispose();
        }
    }

    @Test
    public void disposeReleasesEnvironmentRoot() {
        SceneHostWindow window = assemble(SceneHostWindow.Shell.BARE, body("BODY"), null, null);
        assertSame("构造期必须把树根交给 runtime", window.runtime(),
                window.root().__getFontEnvironment());
        window.dispose();
        assertNull("dispose 必须摘除环境根引用（与 attachTree 成对）",
                window.root().__getFontEnvironment());
    }

    @Test
    public void nullCollaboratorsFailFast() {
        SceneHostWindow.ContentFactory factory = body("BODY");
        assertThrows(IllegalArgumentException.class, () -> new SceneHostWindow(null,
                UiEnvironment.empty(), SceneHostWindow.Shell.BARE, factory, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SceneHostWindow(MEASURER,
                null, SceneHostWindow.Shell.BARE, factory, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SceneHostWindow(MEASURER,
                UiEnvironment.empty(), null, factory, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SceneHostWindow(MEASURER,
                UiEnvironment.empty(), SceneHostWindow.Shell.BARE, null, null, null));
        assertThrows(IllegalStateException.class, () -> new SceneHostWindow(MEASURER,
                UiEnvironment.empty(), SceneHostWindow.Shell.BARE, rt -> null, null, null));
    }
}
