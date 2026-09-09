package club.heiqi.uilib.ui.scene.form;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link FormLabeledControl} 的纯 scene headless 几何契约与文字外观契约。
 *
 * <p><b>默认路径</b>（带 {@link SceneRuntime} 的重载）：标签文字取来源主题 {@code foreground}
 * （经 {@link FormThemes} 映射为 {@code textColor}）、辅助说明取 {@code mutedForeground}
 * （{@code mutedColor}）；来源主题切换只重派生文字色，节点身份与订阅数不变；
 * 不新增表面/玻璃（无底色/边框/圆角/滤镜/浮雕，PaintPlan 不发 {@code BACKDROP}）。</p>
 *
 * <p><b>旧静态路径</b>（三参重载）：语义与迁移前一致——不写文字色（沿用节点默认前景）、
 * 不订阅主题、不感知来源主题；结构、间距、命中与几何与默认路径完全一致。</p>
 *
 * <p>几何用例为纯布局断言，不需要 runtime；文字外观用例经 {@link SceneRuntime} 注册
 * mount/bind，照 {@code FormFieldShellTest} 范式直接 new SceneRuntime，放 form 同包。</p>
 */
public class FormLabeledControlTest {
    private static final int VIEWPORT_WIDTH = 240;
    private static final int VIEWPORT_HEIGHT = 300;
    private static final String LONG_ENGLISH =
            "minimumRemainingDurabilityBeforeAutomaticToolReplacement";
    private static final String LONG_CHINESE =
            "工具剩余耐久度低于此阈值时自动停止并更换备用工具";
    private static final String LABEL = "标签";
    private static final String HELPER = "辅助说明";
    /** {@code SceneNode} 默认前景（{@code ScenePaintProps.textColor} 初值）：旧静态路径沿用。 */
    private static final int NODE_DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    private static final float EPSILON = 0.0001F;

    /** 场景运行时（文字外观用例消费方），每用例独立 new，避免跨用例污染。 */
    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer(8, 16));
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 长中英文在不同字宽/行高下均须保持声明顺序，且全部受 240px 视口约束。 */
    @Test
    public void verticalWrapsLongTextWithoutOverlapOrViewportOverflow() {
        assertLongTextGeometry(LONG_ENGLISH, new FixedTextMeasurer(8, 16));
        assertLongTextGeometry(LONG_ENGLISH, new FixedTextMeasurer(13, 20));
        assertLongTextGeometry(LONG_CHINESE, new FixedTextMeasurer(8, 16));
        assertLongTextGeometry(LONG_CHINESE, new FixedTextMeasurer(13, 20));
    }

    private static void assertLongTextGeometry(String text, FixedTextMeasurer measurer) {
        SceneNode control = sizedControl(137, 29);
        SceneNode form = FormLabeledControl.vertical(text, text + text, control);

        layout(form, measurer);

        SceneNode label = form.__getChildren().get(0);
        SceneNode helper = form.__getChildren().get(1);
        assertTrue(bottom(label) <= y(helper));
        assertTrue(bottom(helper) <= y(control));
        assertInsideViewport(form, label, helper, control);
        assertEquals("控件固有宽度应由控件自己声明", 137, box(control).getWidth());
        assertEquals("wrapper 宽度来自视口约束，不得固化为控件宽度", VIEWPORT_WIDTH, box(form).getWidth());
    }

    /** label/helper 均可缺省，且不同控件固有尺寸不会被 wrapper 写死。 */
    @Test
    public void verticalAllowsOptionalTextAndPreservesDifferentControlIntrinsicSizes() {
        assertOptionalTextGeometry(null, null, 61, 17);
        assertOptionalTextGeometry("label", null, 103, 23);
        assertOptionalTextGeometry(null, "helper", 149, 31);
    }

    private static void assertOptionalTextGeometry(String label, String helper, int width, int height) {
        SceneNode control = sizedControl(width, height);
        SceneNode form = FormLabeledControl.vertical(label, helper, control);
        layout(form, new FixedTextMeasurer(8, 16));
        assertEquals(width, box(control).getWidth());
        assertEquals(height, box(control).getHeight());
        assertEquals(VIEWPORT_WIDTH, box(form).getWidth());
        assertInsideViewport(form, control);
        assertEquals((label == null ? 0 : 1) + (helper == null ? 0 : 1) + 1,
                form.__getChildren().size());
    }

    private static SceneNode sizedControl(int width, int height) {
        SceneNode control = new SceneNode();
        control.setPreferredWidth(width);
        control.setPreferredHeight(height);
        return control;
    }

    private static void layout(SceneNode root, FixedTextMeasurer measurer) {
        new SceneLayoutEngine(measurer).layout(root, new Constraints(VIEWPORT_WIDTH, VIEWPORT_HEIGHT));
    }

    private static void assertInsideViewport(SceneNode form, SceneNode... nodes) {
        assertNodeInsideViewport(form);
        for (SceneNode node : nodes) {
            assertNodeInsideViewport(node);
        }
    }

    private static void assertNodeInsideViewport(SceneNode node) {
        assertTrue(x(node) >= 0);
        assertTrue(right(node) <= VIEWPORT_WIDTH);
        assertTrue(y(node) >= 0);
        assertTrue(bottom(node) <= VIEWPORT_HEIGHT);
    }

    private static LayoutBox box(SceneNode node) {
        return (LayoutBox) node.getCachedLayout();
    }

    private static int x(SceneNode node) {
        int value = 0;
        for (SceneNode current = node; current != null; current = current.__getParent()) {
            value += box(current).getX();
        }
        return value;
    }

    private static int y(SceneNode node) {
        int value = 0;
        for (SceneNode current = node; current != null; current = current.__getParent()) {
            value += box(current).getY();
        }
        return value;
    }

    private static int right(SceneNode node) {
        return x(node) + box(node).getWidth();
    }

    private static int bottom(SceneNode node) {
        return y(node) + box(node).getHeight();
    }

    // ==================== 文字外观用例的装配与查找工具 ====================

    /** 在可切换局部主题作用域内构建默认路径（带 rt 重载）的表单行。 */
    private MountHandle mountThemed(Signal<SceneTheme> pageTheme, String label, String helper,
                                    SceneNode control, final SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] =
                    FormLabeledControl.vertical(runtime, label, helper, control));
            return holder[0];
        });
    }

    /** 标签槽内的文字节点（标签槽 = 首子，文字 = 槽首子）。 */
    private static SceneNode labelNode(SceneNode form) {
        return form.__getChildren().get(0).__getChildren().get(0);
    }

    /** 说明槽内的文字节点（说明槽 = 次子，文字 = 槽首子）。 */
    private static SceneNode helperNode(SceneNode form) {
        return form.__getChildren().get(1).__getChildren().get(0);
    }

    /** 内容槽（末子）。 */
    private static SceneNode contentSlot(SceneNode form) {
        return form.__getChildren().get(form.__getChildren().size() - 1);
    }

    /** 断言节点未承载任何表面/玻璃：无底色/边框/圆角/滤镜/浮雕（本类不新增表面）。 */
    private static void assertNoSurface(SceneNode node) {
        Assert.assertEquals("不新增底色", 0, node.getBackgroundColor());
        Assert.assertEquals("不新增边框宽", 0, node.getBorderWidth());
        Assert.assertEquals("不新增圆角", 0, node.getCornerRadius());
        Assert.assertNull("不装滤镜", node.getBackdrop());
        Assert.assertEquals("不绑浮雕", -1.0F, node.__getSurfaceElevation(), EPSILON);
    }

    /** 统计 PaintPlan 中的 BACKDROP 命令数（语义表面是否真的采样了背景）。 */
    private static int backdropCount(SceneNode root) {
        PaintPlan plan = new ScenePaintEngine(new FixedTextMeasurer(8, 16)).paint(root).getPlan();
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    // ==================== 默认路径：文字取来源主题语义色 ====================

    /**
     * 默认路径：标签取主题 {@code foreground}、辅助说明取主题 {@code mutedForeground}
     * （经 {@link FormThemes} 映射为 {@code textColor}/{@code mutedColor}），两类色值不同；
     * 结构、间距、命中语义与不新增表面全部保持。
     */
    @Test
    public void themedPathTakesForegroundAndMutedForegroundFromTheme() {
        SceneTheme theme = SceneTheme.liquidGlassDark();
        FormTheme mapped = FormThemes.of(theme);
        Assert.assertNotEquals("前置：foreground 与 mutedForeground 必须不同",
                theme.foreground(), theme.mutedForeground());

        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThemed(Signal.create(theme), LABEL, HELPER, sizedControl(120, 20), holder);
        runtime.flush();

        SceneNode form = holder[0];
        Assert.assertEquals("标签取主题 foreground", theme.foreground(), labelNode(form).getTextColor());
        Assert.assertEquals("标签经 FormThemes 映射取 textColor",
                mapped.textColor(), labelNode(form).getTextColor());
        Assert.assertEquals("helper 取主题 mutedForeground",
                theme.mutedForeground(), helperNode(form).getTextColor());
        Assert.assertEquals("helper 经 FormThemes 映射取 mutedColor",
                mapped.mutedColor(), helperNode(form).getTextColor());
        Assert.assertNotEquals("helper 不得误取正文前景",
                labelNode(form).getTextColor(), helperNode(form).getTextColor());
        Assert.assertEquals("标签文本不变", LABEL, labelNode(form).getText());
        Assert.assertEquals("helper 文本不变", HELPER, helperNode(form).getText());

        Assert.assertEquals("结构仍为 标签槽 + 说明槽 + 内容槽", 3, form.__getChildren().size());
        Assert.assertEquals("间距保持 GAP=2", 2, form.getGap());
        Assert.assertTrue("标签槽仍裁剪溢出", form.__getChildren().get(0).isClipChildren());
        Assert.assertTrue("说明槽仍裁剪溢出", form.__getChildren().get(1).isClipChildren());
        Assert.assertTrue("内容槽仍裁剪溢出", contentSlot(form).isClipChildren());
        Assert.assertFalse("标签文字仍不可命中", labelNode(form).isHitTestable());
        Assert.assertFalse("helper 文字仍不可命中", helperNode(form).isHitTestable());

        assertNoSurface(form);
        assertNoSurface(form.__getChildren().get(0));
        assertNoSurface(form.__getChildren().get(1));
        assertNoSurface(contentSlot(form));
        handle.dispose();
    }

    /**
     * 主题切换：标签/辅助说明文字色随来源主题重派生，节点身份、控件节点与订阅数不变
     * （两主题前景必须真的不同，否则 {@code Computed} 按值记忆化不会传播）。
     */
    @Test
    public void themeSwitchRepaintsTextWithoutRebuildingOrAddingSubscriptions() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("前置：两主题 foreground 必须不同，否则切换不传播",
                dark.foreground(), light.foreground());
        Assert.assertNotEquals("前置：两主题 mutedForeground 必须不同",
                dark.mutedForeground(), light.mutedForeground());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        SceneNode control = sizedControl(120, 20);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThemed(pageTheme, LABEL, HELPER, control, holder);
        runtime.flush();

        SceneNode form = holder[0];
        SceneNode label = labelNode(form);
        SceneNode helper = helperNode(form);
        Assert.assertEquals("前置：深色档标签色", dark.foreground(), label.getTextColor());
        Assert.assertEquals("前置：深色档 helper 色", dark.mutedForeground(), helper.getTextColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("切换后标签取新主题 foreground", light.foreground(), label.getTextColor());
        Assert.assertEquals("切换后 helper 取新主题 mutedForeground",
                light.mutedForeground(), helper.getTextColor());
        Assert.assertSame("主题切换不重建根节点", form, holder[0]);
        Assert.assertSame("主题切换不重建标签文字节点", label, labelNode(form));
        Assert.assertSame("主题切换不重建 helper 文字节点", helper, helperNode(form));
        Assert.assertSame("主题切换不重建控件节点", control, contentSlot(form).__getChildren().get(0));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    /** 默认路径的 label/helper 缺省规则与旧路径一致（null / 空串不建槽）。 */
    @Test
    public void themedPathKeepsOptionalTextStructure() {
        assertThemedOptionalText(null, null, 1);
        assertThemedOptionalText(LABEL, null, 2);
        assertThemedOptionalText(null, HELPER, 2);
        assertThemedOptionalText("", "", 1);
    }

    private void assertThemedOptionalText(String label, String helper, int expectedChildren) {
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThemed(Signal.create(SceneTheme.liquidGlassDark()), label, helper,
                sizedControl(120, 20), holder);
        runtime.flush();
        Assert.assertEquals("缺省文本时的槽位数量不变",
                expectedChildren, holder[0].__getChildren().size());
        Assert.assertEquals("内容槽恒为末子并挂载控件", 1,
                contentSlot(holder[0]).__getChildren().size());
        handle.dispose();
    }

    // ==================== 默认路径：不新增表面/玻璃 ====================

    /** 默认路径不新增表面/玻璃：无底色/边框/圆角/滤镜/浮雕，PaintPlan 不发 BACKDROP。 */
    @Test
    public void themedPathAddsNoSurfaceOrBackdropInPaintPlan() {
        final SceneNode[] holder = new SceneNode[1];
        SceneNode sceneRoot = SceneNode.column();
        MountHandle handle = runtime.mount(sceneRoot, () -> {
            SceneThemes.withTheme(Signal.create(SceneTheme.liquidGlassDark()), () -> holder[0] =
                    FormLabeledControl.vertical(runtime, LABEL, HELPER, sizedControl(120, 20)));
            return holder[0];
        });
        runtime.flush();
        layout(sceneRoot, new FixedTextMeasurer(8, 16));

        assertNoSurface(holder[0]);
        Assert.assertNull("表单行不装滤镜", holder[0].getBackdrop());
        Assert.assertEquals("表单行不发 BACKDROP 命令", 0, backdropCount(sceneRoot));
        handle.dispose();
    }

    // ==================== 旧静态路径：语义不变 ====================

    /**
     * 旧签名语义不变：不写文字色（沿用节点默认前景）、不订阅主题、不感知来源主题，
     * 结构/间距/命中/无表面与默认路径一致。
     */
    @Test
    public void legacySignatureKeepsStaticTextAndIgnoresTheme() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        SceneNode control = sizedControl(120, 20);

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () ->
                    holder[0] = FormLabeledControl.vertical(LABEL, HELPER, control));
            return holder[0];
        });
        runtime.flush();

        SceneNode form = holder[0];
        Assert.assertEquals("旧签名标签保持节点默认前景",
                NODE_DEFAULT_TEXT_COLOR, labelNode(form).getTextColor());
        Assert.assertEquals("旧签名 helper 保持节点默认前景",
                NODE_DEFAULT_TEXT_COLOR, helperNode(form).getTextColor());
        Assert.assertEquals("旧签名不因文字派生新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        Assert.assertEquals("旧签名结构不变", 3, form.__getChildren().size());
        Assert.assertEquals("旧签名间距不变", 2, form.getGap());
        Assert.assertFalse("旧签名标签文字不可命中", labelNode(form).isHitTestable());
        Assert.assertFalse("旧签名 helper 文字不可命中", helperNode(form).isHitTestable());
        assertNoSurface(form);

        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("旧签名不随主题切换改色（标签）",
                NODE_DEFAULT_TEXT_COLOR, labelNode(form).getTextColor());
        Assert.assertEquals("旧签名不随主题切换改色（helper）",
                NODE_DEFAULT_TEXT_COLOR, helperNode(form).getTextColor());
        handle.dispose();
    }

    // ==================== 卸载回收 ====================

    /** 卸载回收：默认路径的文字绑定 effect 随 mount 卸载全部退订，主题更新不再写入旧节点。 */
    @Test
    public void unmountReleasesTextBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThemed(pageTheme, LABEL, HELPER, sizedControl(120, 20), holder);
        runtime.flush();
        Assert.assertTrue("默认路径应注册主题文字绑定",
                ReactiveTestProbe.registeredEffectCount() > baseline);

        SceneNode form = holder[0];
        int labelColorBefore = labelNode(form).getTextColor();
        int helperColorBefore = helperNode(form).getTextColor();
        handle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载后文字绑定 effect 应回收",
                baseline, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧标签",
                labelColorBefore, labelNode(form).getTextColor());
        Assert.assertEquals("卸载后主题更新不再写入旧 helper",
                helperColorBefore, helperNode(form).getTextColor());
    }

    /** 带 rt 的重载拒绝 null runtime（构建期纪律：主题必须在来源作用域内解析）。 */
    @Test
    public void themedOverloadRejectsNullRuntime() {
        try {
            FormLabeledControl.vertical(null, LABEL, HELPER, sizedControl(120, 20));
            Assert.fail("rt 为 null 时应抛 NullPointerException");
        } catch (NullPointerException expected) {
            Assert.assertEquals("rt", expected.getMessage());
        }
    }
}
