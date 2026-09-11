package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.input.mock.MockPlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.FontSource;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintResult;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.ScenePortalHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLinePlan;

/**
 * 字号溯源与不变量守卫（字号动态化 I-4，守卫 3）。
 *
 * <p>三条不变量：</p>
 * <ol>
 *   <li><b>INV-FONT-1 溯源</b>：已装配树内每个文字节点的 {@link SceneNode#fontSizeSource()}
 *       不得是 {@link FontSource#UNRESOLVED}（孤儿文本）；未接线控件直接报出控件名 + 节点路径。</li>
 *   <li><b>作用域跟随</b>：非显式（EXPLICIT）的文字节点必须解析到本次挂载的作用域字号。</li>
 *   <li><b>几何/行计划同步</b>：运行期改作用域或用户倍率后，子树文字节点的
 *       {@code cachedLayout}（LayoutBox 宽高）与 {@code cachedTextPlan}（行高/行数）必须同步刷新——
 *       只钉 PaintCommand 字号不够，陈旧 box / 陈旧行计划是更隐蔽的失效形态。</li>
 * </ol>
 *
 * <p>变异口径（自证）：把 {@code SceneNode.onFontDeclarationChanged} 的标记去掉、或把
 * {@code invalidateFontSubtree} 的向下失效去掉，本类的几何/行计划用例必须变红。</p>
 */
public class ControlFontTraceabilityGuardTest {

    private static final int SCOPE_PX = 24;
    private static final int CANVAS_WIDTH = 1280;
    private static final int CANVAS_HEIGHT = 960;
    /** 带空格的长文本：固定 wrap 宽度下，字号翻倍必须使行数增长（无空格时按词换行不会拆行）。 */
    private static final String WRAP_TEXT =
            "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron";
    private static final Runnable NOOP = new Runnable() {
        public void run() {
        }
    };

    private final List<Fixture> fixtures = new ArrayList<Fixture>();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        for (Fixture fixture : fixtures) {
            fixture.harness.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    /** INV-FONT-1：挂载式全清单的文字节点都必须可溯源且跟随作用域（失败报控件名 + 路径）。 */
    @Test
    public void everyTextNodeIsTraceableAndFollowsScope() {
        StringBuilder unresolved = new StringBuilder();
        StringBuilder notFollowing = new StringBuilder();
        for (Map.Entry<String, ControlFontRuntimeGuardTest.InlineMounter> entry
                : ControlFontRuntimeGuardTest.inlineMounters().entrySet()) {
            Fixture fixture = fixture();
            MountHandle handle = entry.getValue().mount(fixture.runtime, fixture.parent);
            Assert.assertNotNull(entry.getKey() + " 的挂载器必须返回 MountHandle", handle);
            fixture.frame();
            handle.fontSize(SCOPE_PX);
            fixture.frame();
            SceneNode root = handle.getRoot();
            for (SceneNode text : textNodes(root)) {
                FontSource source = text.fontSizeSource();
                if (source == FontSource.UNRESOLVED) {
                    unresolved.append(entry.getKey()).append(pathOf(root, text))
                            .append(" text='").append(text.getText()).append("' ");
                    continue;
                }
                if (source != FontSource.EXPLICIT && text.effectiveFontSize() != SCOPE_PX) {
                    notFollowing.append(entry.getKey()).append(pathOf(root, text))
                            .append(" text='").append(text.getText())
                            .append("' source=").append(source)
                            .append(" eff=").append(text.effectiveFontSize()).append(" ");
                }
            }
        }
        Assert.assertEquals("已装配树内不得出现 UNRESOLVED 的孤儿文本", "", unresolved.toString());
        Assert.assertEquals("非显式文字必须解析到本次挂载的作用域字号",
                "", notFollowing.toString());
    }

    /** 裁决 2 契约钉：getFontSize() 必须返回生效值（= effectiveFontSize()），全清单逐节点。 */
    @Test
    public void getFontSizeAgreesWithEffectiveFontSize() {
        StringBuilder mismatch = new StringBuilder();
        for (Map.Entry<String, ControlFontRuntimeGuardTest.InlineMounter> entry
                : ControlFontRuntimeGuardTest.inlineMounters().entrySet()) {
            Fixture fixture = fixture();
            MountHandle handle = entry.getValue().mount(fixture.runtime, fixture.parent);
            fixture.frame();
            handle.fontSize(SCOPE_PX);
            fixture.frame();
            for (SceneNode text : textNodes(handle.getRoot())) {
                if (text.getFontSize() != text.effectiveFontSize()) {
                    mismatch.append(entry.getKey()).append(pathOf(handle.getRoot(), text))
                            .append(" get=").append(text.getFontSize())
                            .append(" eff=").append(text.effectiveFontSize()).append(" ");
                }
            }
        }
        Assert.assertEquals("getFontSize() 必须返回生效值（裁决 2 主流水位）", "", mismatch.toString());
    }

    /** 显式来源必须自洽：EXPLICIT 节点的 getExplicitFontSize() 不得为 null（来源可判定）。 */
    @Test
    public void explicitSourcesAreSelfConsistent() {
        StringBuilder broken = new StringBuilder();
        for (Map.Entry<String, ControlFontRuntimeGuardTest.InlineMounter> entry
                : ControlFontRuntimeGuardTest.inlineMounters().entrySet()) {
            Fixture fixture = fixture();
            MountHandle handle = entry.getValue().mount(fixture.runtime, fixture.parent);
            fixture.frame();
            handle.fontSize(SCOPE_PX);
            fixture.frame();
            for (SceneNode text : textNodes(handle.getRoot())) {
                if (text.fontSizeSource() == FontSource.EXPLICIT
                        && text.getExplicitFontSize() == null) {
                    broken.append(entry.getKey()).append(pathOf(handle.getRoot(), text)).append(' ');
                }
            }
        }
        Assert.assertEquals("EXPLICIT 来源必须能取到显式值（来源可判定性）", "", broken.toString());
    }

    /**
     * 陈旧几何 / 陈旧行计划：运行期改作用域字号后，盒高与行计划必须同步刷新（钉 box，不只钉字号）。
     *
     * <p>若向下失效被移除，布局器 canSkipClean 会复用旧 cachedLayout，本用例变红。</p>
     */
    @Test
    public void scopeChangeRefreshesSubtreeGeometryAndLinePlan() {
        Fixture fixture = fixture();
        Signal<Integer> scope = Signal.create(Integer.valueOf(16));
        MountHandle handle = fixture.mountWrappedLabel(scope, 80);
        fixture.frame();
        SceneNode label = handle.getRoot();
        int linesBefore = planOf(label).getLines().size();
        int heightBefore = boxOf(label).getHeight();
        int lineHeightBefore = planOf(label).getLineHeights()[0];

        scope.set(Integer.valueOf(32));
        fixture.frame();

        Assert.assertEquals("绘制字号必须跟随作用域",
                new LinkedHashSet<Integer>(Arrays.asList(Integer.valueOf(32))), fixture.paintedFontSizes());
        TextLinePlan plan = planOf(label);
        Assert.assertTrue("行计划必须按新字号重建（行数随字号增长）",
                plan.getLines().size() > linesBefore);
        Assert.assertTrue("行高必须随字号变化", plan.getLineHeights()[0] != lineHeightBefore);
        for (int i = 0; i < plan.getLineHeights().length; i++) {
            Assert.assertEquals("行计划第 " + i + " 行行高还是旧字号：陈旧行计划",
                    fixture.measurer.lineHeight(32), plan.getLineHeights()[i]);
        }
        LayoutBox box = boxOf(label);
        Assert.assertEquals("布局盒高与行计划不一致：陈旧 box",
                plan.getTotalHeight(), box.getHeight());
        Assert.assertTrue("盒高必须随字号增长：盒子没跟着变", box.getHeight() > heightBefore);
    }

    /**
     * 祖先作用域变更 → 后代几何/行计划刷新（向下失效路径）。
     *
     * <p>与 {@link #scopeChangeRefreshesSubtreeGeometryAndLinePlan} 的区别：作用域写在**父容器**上，
     * 文字叶在下一层，专门钉 {@code invalidateFontDescendants()} 的向下递归；去掉它这条必须变红。</p>
     */
    @Test
    public void ancestorScopeChangeRefreshesDescendantGeometry() {
        Fixture fixture = fixture();
        Signal<Integer> scope = Signal.create(Integer.valueOf(16));
        MountHandle handle = fixture.mountWrappedLabelUnderWrapper(scope, 80);
        fixture.frame();
        SceneNode label = fixture.lastLabel;
        Assert.assertNotNull("包装层下必须找到文字叶", label);
        int linesBefore = planOf(label).getLines().size();
        int heightBefore = boxOf(label).getHeight();

        scope.set(Integer.valueOf(32));
        fixture.frame();

        Assert.assertEquals("后代绘制字号必须跟随祖先作用域",
                new LinkedHashSet<Integer>(Arrays.asList(Integer.valueOf(32))),
                fixture.paintedFontSizes());
        TextLinePlan plan = planOf(label);
        Assert.assertTrue("后代行计划必须重建（行数增长）", plan.getLines().size() > linesBefore);
        for (int i = 0; i < plan.getLineHeights().length; i++) {
            Assert.assertEquals("后代行计划第 " + i + " 行仍是旧字号（向下失效缺失）",
                    fixture.measurer.lineHeight(32), plan.getLineHeights()[i]);
        }
        LayoutBox box = boxOf(label);
        Assert.assertEquals("后代盒高与行计划不一致（向下失效缺失）",
                plan.getTotalHeight(), box.getHeight());
        Assert.assertTrue("后代盒高必须增长", box.getHeight() > heightBefore);
        Assert.assertEquals("祖先作用域持有者自身也应是 SCOPE 来源",
                FontSource.SCOPE, handle.getRoot().fontSizeSource());
    }

    /**
     * 行计划缓存的失效契约：字号声明一变，**立刻**作废 cachedTextPlan（不等下一帧布局）。
     *
     * <p>补的是「绘制先于布局」路径：布局阶段会重建行计划把删缓存的行为掩盖掉，
     * 因此必须在 set+flush 之后、未跑帧之前断言 null。</p>
     */
    @Test
    public void declarationChangeInvalidatesTextPlanImmediately() {
        Fixture fixture = fixture();
        Signal<Integer> scope = Signal.create(Integer.valueOf(16));
        MountHandle handle = fixture.mountWrappedLabel(scope, 80);
        fixture.frame();
        Assert.assertNotNull("前置：布局后必须持有行计划缓存",
                handle.getRoot().getCachedTextPlan());

        scope.set(Integer.valueOf(32));
        fixture.runtime.flush();
        Assert.assertNull("字号声明变化必须立刻作废行计划缓存（绘制先于布局时不得用旧计划）",
                handle.getRoot().getCachedTextPlan());
        Assert.assertNull("字号声明变化必须立刻作废布局缓存", handle.getRoot().getCachedLayout());
    }

    /** Segmented/Tab 几何派生：作用域变更后段宽/条高必须随解析字号同步（metric 机制）。 */
    @Test
    public void segmentedGeometryFollowsScopeChange() {
        Fixture fixture = fixture();
        Signal<Integer> scope = Signal.create(Integer.valueOf(16));
        MountHandle handle = fixture.mountSegmented(scope);
        fixture.frame();
        SceneNode root = handle.getRoot();
        int padding = (root.getPreferredHeight() - fixture.measurer.lineHeight(16)) / 2;
        int widthBefore = root.__getChildren().get(0).getPreferredWidth();
        Assert.assertTrue("前置：段宽应为正数", widthBefore > 0);

        scope.set(Integer.valueOf(32));
        fixture.frame();

        Assert.assertEquals("条高必须随解析字号重算（metric 机制）",
                fixture.measurer.lineHeight(32) + 2 * padding, root.getPreferredHeight());
        Assert.assertTrue("段宽必须随解析字号增长",
                root.__getChildren().get(0).getPreferredWidth() > widthBefore);
        Assert.assertEquals("段标签绘制字号必须跟随",
                new LinkedHashSet<Integer>(Arrays.asList(Integer.valueOf(32))),
                fixture.paintedFontSizes());
    }

    /** 用户级倍率（裁决 1）：作用于解析出口且参与布局 —— 倍率变化后盒与行计划必须同步。 */
    @Test
    public void fontScaleChangeRefreshesGeometryAndLinePlan() {
        Fixture fixture = fixture();
        MountHandle handle = fixture.mountWrappedLabel(Signal.create(Integer.valueOf(24)), 80);
        fixture.frame();
        SceneNode label = handle.getRoot();
        int heightBefore = boxOf(label).getHeight();
        Assert.assertEquals("前置：倍率中性时 eff == 声明值", 24, label.effectiveFontSize());

        fixture.runtime.setFontScale(150);
        fixture.frame();

        Assert.assertEquals("倍率 1.5 后解析字号 = round(24*1.5)", 36, label.effectiveFontSize());
        Assert.assertEquals("绘制字号必须跟随解析值",
                new LinkedHashSet<Integer>(Arrays.asList(Integer.valueOf(36))), fixture.paintedFontSizes());
        TextLinePlan plan = planOf(label);
        for (int i = 0; i < plan.getLineHeights().length; i++) {
            Assert.assertEquals("倍率变化后行计划必须按解析字号重建",
                    fixture.measurer.lineHeight(36), plan.getLineHeights()[i]);
        }
        LayoutBox box = boxOf(label);
        Assert.assertEquals("布局盒高与行计划不一致：倍率未参与布局", plan.getTotalHeight(), box.getHeight());
        Assert.assertTrue("倍率变化后盒高必须增长", box.getHeight() > heightBefore);
    }

    /** INV-GEO-4：已知裁剪槽位不得静默截断（恰 1 行 / 行宽 ≤ 槽位宽 / 截断必须显式省略号）。 */
    @Test
    public void noSilentTextOverflowUnderClip() {
        Fixture table = fixture();
        MountHandle tableHandle = table.mountDataTable(SCOPE_PX);
        table.frame();
        assertNoSilentOverflow("SceneDataTable", table, tableHandle.getRoot());

        Fixture map = fixture();
        MountHandle mapHandle = map.mountKeyValueMap(SCOPE_PX);
        map.frame();
        assertNoSilentOverflow("SceneKeyValueMap", map, mapHandle.getRoot());

        Fixture field = fixture();
        MountHandle fieldHandle = field.mountObjectField(SCOPE_PX);
        field.frame();
        assertNoSilentOverflow("SceneObjectField", field, fieldHandle.getRoot());
    }

    /**
     * 单行裁剪槽位的溢出不变量：声明了槽位宽（maxTextWidth &gt; 0）且限 1 行的文本节点，
     * 行计划必须恰 1 行、行宽 ≤ 槽位宽；文本本身超宽时必须以省略号显式收尾。
     */
    private void assertNoSilentOverflow(String control, Fixture fixture, SceneNode root) {
        int checked = 0;
        for (SceneNode node : textNodes(root)) {
            int slot = node.getMaxTextWidth();
            if (slot <= 0 || node.getMaxLines() != 1) {
                continue;
            }
            TextLinePlan plan = node.getCachedTextPlan();
            Assert.assertNotNull(control + " 声明槽位宽后必须有行计划：" + node.getText(), plan);
            Assert.assertEquals(control + " 单行槽位必须恰 1 行：" + node.getText(),
                    1, plan.getLines().size());
            String line = plan.getLines().get(0);
            int lineWidth = fixture.measurer.measureWidth(line, node.effectiveFontSize());
            Assert.assertTrue(control + " 行宽必须 ≤ 槽位宽（" + lineWidth + " > " + slot + "）：" + line,
                    lineWidth <= slot);
            int fullWidth = fixture.measurer.measureWidth(node.getText(), node.effectiveFontSize());
            if (fullWidth > slot) {
                Assert.assertTrue(control + " 截断必须显式省略号收尾（不得静默裁切）：" + line,
                        line.endsWith("..."));
            }
            checked++;
        }
        Assert.assertTrue(control + " 必须至少检查到一个单行槽位文本节点", checked > 0);
    }

    /** 浮层族同型：Dialog / ContextMenu / Toast 的文字节点也必须可溯源且单值。 */
    @Test
    public void overlayFamilyTextNodesAreTraceableAndSingleValued() {
        StringBuilder offenders = new StringBuilder();

        Fixture dialog = fixture();
        final ScenePortalHandle[] portal = new ScenePortalHandle[1];
        Signal<Boolean> visible = Signal.create(Boolean.TRUE);
        dialog.runtime.mount(dialog.parent, new Supplier<SceneNode>() {
            public SceneNode get() {
                portal[0] = SceneDialog.create(dialog.runtime, new SceneDialog.Props(
                        visible, "标题", "正文",
                        Arrays.asList(SceneDialog.Button.of("取消", NOOP)),
                        NOOP));
                return new SceneNode();
            }
        });
        portal[0].fontSize(SCOPE_PX);
        dialog.frameOverlay();
        collectOverlayOffenders("SceneDialog", dialog, offenders);

        Fixture menu = fixture();
        SceneContextMenu.Handle menuHandle = SceneContextMenu.open(menu.runtime, 10, 10,
                Arrays.asList(SceneContextMenu.MenuItem.of("复制", NOOP)));
        menuHandle.fontSize(SCOPE_PX);
        menu.frameOverlay();
        collectOverlayOffenders("SceneContextMenu", menu, offenders);

        Fixture toast = fixture();
        SceneToast.defaultFontSize(toast.runtime, SCOPE_PX);
        SceneToast.show(toast.runtime, "通知", 5_000_000_000L);
        toast.frameOverlay();
        collectOverlayOffenders("SceneToast", toast, offenders);

        Assert.assertEquals("浮层族不得出现孤儿文本或非作用域字号", "", offenders.toString());
    }

    private void collectOverlayOffenders(String label, Fixture fixture, StringBuilder offenders) {
        List<SceneOverlayHost.Entry> entries = fixture.runtime.getOverlayHost().bottomFirst();
        for (SceneOverlayHost.Entry entry : entries) {
            for (SceneNode text : textNodes(entry.getRoot())) {
                FontSource source = text.fontSizeSource();
                if (source == FontSource.UNRESOLVED) {
                    offenders.append(label).append(" UNRESOLVED text='").append(text.getText())
                            .append("' ");
                } else if (source != FontSource.EXPLICIT && text.effectiveFontSize() != SCOPE_PX) {
                    offenders.append(label).append(" text='").append(text.getText())
                            .append("' source=").append(source)
                            .append(" eff=").append(text.effectiveFontSize()).append(" ");
                }
            }
        }
    }

    private static List<SceneNode> textNodes(SceneNode root) {
        List<SceneNode> found = new ArrayList<SceneNode>();
        collect(root, found);
        return found;
    }

    private static void collect(SceneNode node, List<SceneNode> out) {
        String text = node.getText();
        if (text != null && !text.isEmpty()) {
            out.add(node);
        }
        for (SceneNode child : node.__getChildren()) {
            collect(child, out);
        }
    }

    private static String pathOf(SceneNode root, SceneNode target) {
        List<String> path = new ArrayList<String>();
        return pathOf(root, target, "", path) ? path.get(0) : "(未找到路径)";
    }

    private static boolean pathOf(SceneNode node, SceneNode target, String path, List<String> out) {
        if (node == target) {
            out.add(path.isEmpty() ? "(root)" : path);
            return true;
        }
        int i = 0;
        for (SceneNode child : node.__getChildren()) {
            if (pathOf(child, target, path + "[" + i + "]", out)) {
                return true;
            }
            i++;
        }
        return false;
    }

    private static LayoutBox boxOf(SceneNode node) {
        Object cached = node.getCachedLayout();
        Assert.assertNotNull("布局后必须有 cachedLayout（陈旧 box 的观测点）：" + node.getText(), cached);
        Assert.assertTrue("cachedLayout 必须是 LayoutBox", cached instanceof LayoutBox);
        return (LayoutBox) cached;
    }

    private static TextLinePlan planOf(SceneNode node) {
        TextLinePlan plan = node.getCachedTextPlan();
        Assert.assertNotNull("布局后必须有 cachedTextPlan（陈旧行计划的观测点）", plan);
        return plan;
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixtures.add(fixture);
        return fixture;
    }

    /** 只观察 super.paint 产物的捕获引擎（同 SceneControlFontSizeEntryTest 口径）。 */
    private static final class CapturingPaintEngine extends ScenePaintEngine {
        List<PaintCommand> commands = new ArrayList<PaintCommand>();

        CapturingPaintEngine(SceneTextMeasurer measurer) {
            super(measurer);
        }

        @Override
        public PaintResult paint(SceneNode root) {
            PaintResult result = super.paint(root);
            commands = new ArrayList<PaintCommand>(result.getPlan().getCommands());
            return result;
        }
    }

    /**
     * 度量随字号变化（宽 = 码点 × 字号 / 2；行高 = 字号），并实现按宽贪心换行。
     *
     * <p>换行必须由度量器实现（{@link SceneTextMeasurer#splitLines} 的默认实现只按换行符拆行）；
     * 不实现的话「固定 wrap 宽下字号翻倍 → 行数增长」不会发生，陈旧行计划用例会失去意义。</p>
     */
    static final class FontMeasurer implements SceneTextMeasurer {
        public int measureWidth(String text, int fontSizePx) {
            return text == null ? 0 : text.codePointCount(0, text.length()) * fontSizePx / 2;
        }

        public int lineHeight(int fontSizePx) {
            return fontSizePx;
        }

        public int epoch() {
            return 0;
        }

        @Override
        public List<String> splitLines(String text, int fontSizePx, int wrapWidth, int textMode) {
            String safe = text == null ? "" : text;
            if (wrapWidth <= 0) {
                return Arrays.asList(safe.split("\n", -1));
            }
            List<String> lines = new ArrayList<String>();
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < safe.length(); i++) {
                char c = safe.charAt(i);
                if (c == '\n') {
                    lines.add(line.toString());
                    line.setLength(0);
                    continue;
                }
                line.append(c);
                if (line.length() > 1 && measureWidth(line.toString(), fontSizePx) > wrapWidth) {
                    line.setLength(line.length() - 1);
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(c);
                }
            }
            lines.add(line.toString());
            return lines;
        }
    }

    private static final class Fixture {
        final FontMeasurer measurer = new FontMeasurer();
        final SceneInteractionHarness harness;
        final SceneRuntime runtime;
        final SceneNode scene = SceneNode.column();
        final SceneNode parent = SceneNode.column();
        final SceneLayoutEngine layout;
        final CapturingPaintEngine paint;
        final SceneFramePipeline pipeline;
        final MockPlatformInputSource input;
        final int width = CANVAS_WIDTH;
        final int height = CANVAS_HEIGHT;
        final List<PaintCommand> commands = new ArrayList<PaintCommand>();
        SceneNode lastLabel;

        Fixture() {
            harness = SceneInteractionHarness.create(measurer);
            runtime = harness.getRuntime();
            scene.appendChild(parent);
            harness.mountRoot(scene, width, height);
            layout = new SceneLayoutEngine(measurer);
            paint = new CapturingPaintEngine(measurer);
            input = new MockPlatformInputSource(width, height);
            pipeline = new SceneFramePipeline(runtime, layout, paint, new ScenePaintReplayer(),
                    measurer, input);
        }

        /** wrap 文本的 SceneLabel：行数与字号强相关，用来观测陈旧行计划。 */
        MountHandle mountWrappedLabel(Signal<Integer> scope, int wrapWidth) {
            MountHandle handle = runtime.mount(parent, SceneLabel.create(runtime,
                    SceneLabel.Props.builder(Signal.create(WRAP_TEXT)).wrapWidth(wrapWidth).build()));
            handle.fontSize(scope);
            return handle;
        }

        MountHandle mountSegmented(Signal<Integer> scope) {
            MountHandle handle = runtime.mount(parent, SceneSegmented.create(runtime,
                    new SceneSegmented.Props(Signal.create(Integer.valueOf(0)),
                            Arrays.asList("One", "Two", "Three"), Signal.create(Boolean.TRUE),
                            index -> { }, null)));
            handle.fontSize(scope);
            return handle;
        }

        /** 作用域写在包装容器上、文字叶在下一层：专测向下失效。 */
        MountHandle mountWrappedLabelUnderWrapper(Signal<Integer> scope, int wrapWidth) {
            final SceneNode[] holder = new SceneNode[1];
            MountHandle handle = runtime.mount(parent, new Supplier<SceneNode>() {
                public SceneNode get() {
                    SceneNode wrapper = SceneNode.column();
                    wrapper.appendChild(SceneLabel.create(runtime,
                            SceneLabel.Props.builder(Signal.create(WRAP_TEXT)).wrapWidth(wrapWidth).build()).get());
                    holder[0] = wrapper;
                    return wrapper;
                }
            });
            handle.fontSize(scope);
            lastLabel = holder[0].__getChildren().get(0);
            return handle;
        }

        MountHandle mountDataTable(int fontSizePx) {
            Signal<List<SceneDataTable.Row>> rows = Signal.create(Arrays.<SceneDataTable.Row>asList(
                    new SceneDataTable.Row(Arrays.asList("很长的单元格文本内容一", "很长的单元格文本内容二")),
                    new SceneDataTable.Row(Arrays.asList("另一段很长的单元格文本三", "另一段很长的单元格文本四"))));
            MountHandle handle = runtime.mount(parent, SceneDataTable.create(runtime,
                    SceneDataTable.Props.builder(rows)
                            .columns(Arrays.asList(SceneDataTable.Column.text("很长的表头文本甲", 80),
                                    SceneDataTable.Column.text("很长的表头文本乙", 80)))
                            .rowHeight(28)
                            .viewportHeight(160)
                            .build()));
            handle.fontSize(fontSizePx);
            return handle;
        }

        MountHandle mountKeyValueMap(int fontSizePx) {
            Signal<List<KeyValueRow>> rows = Signal.create(Arrays.<KeyValueRow>asList(
                    new KeyValueRow("很长的键名文本内容", "很长的值文本内容", ValueType.STRING)));
            MountHandle handle = runtime.mount(parent, SceneKeyValueMap.create(runtime,
                    SceneKeyValueMap.Props.builder(rows).label("很长的属性面板标题文本").build()));
            handle.fontSize(fontSizePx);
            return handle;
        }

        MountHandle mountObjectField(int fontSizePx) {
            Map<String, Object> initial = new java.util.LinkedHashMap<String, Object>();
            initial.put("name", "qz");   // 非空才有行键槽位（tag: 单行槽位断言必须有节点可查）
            Signal<Map<String, Object>> value = Signal.create(initial);
            MountHandle handle = runtime.mount(parent, SceneObjectField.create(runtime,
                    SceneObjectField.Props.builder(value)
                            .label("很长的对象字段标签名称文本")
                            .showScrollbar(true)
                            .build()));
            handle.fontSize(fontSizePx);
            return handle;
        }

        void frame() {
            captureScene();
            captureScene();
        }

        void frameOverlay() {
            frame();
            List<SceneOverlayHost.Entry> entries = runtime.getOverlayHost().bottomFirst();
            for (SceneOverlayHost.Entry entry : entries) {
                layout.layout(entry.getRoot(), new Constraints(width, height));
            }
            commands.clear();
            for (SceneOverlayHost.Entry entry : entries) {
                paint.paint(entry.getRoot());
                commands.addAll(paint.commands);
                for (SceneNode text : textNodes(entry.getRoot())) {
                    paint.paint(text);
                    commands.addAll(paint.commands);
                }
            }
        }

        private void captureScene() {
            pipeline.run(scene, width, height, new RecordingRenderBackend(), 0, 0, 1000L);
            commands.clear();
            commands.addAll(paint.commands);
        }

        /** 本帧全部 TEXT 命令的字号集合（wrap 文本会逐行绘制，故按集合断言）。 */
        Set<Integer> paintedFontSizes() {
            Set<Integer> sizes = new LinkedHashSet<Integer>();
            for (PaintCommand command : commands) {
                if (command.getType() == PaintCommandType.TEXT && !command.getText().isEmpty()) {
                    sizes.add(Integer.valueOf(command.getTextStyle().getFontSize()));
                }
            }
            return sizes;
        }

        int paintedFontSize(String text) {
            for (PaintCommand command : commands) {
                if (command.getType() == PaintCommandType.TEXT && text.equals(command.getText())) {
                    return command.getTextStyle().getFontSize();
                }
            }
            throw new AssertionError("绘制产物中未找到文本：" + text);
        }
    }
}
