package club.heiqi.uilib.ui.scene.control;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link SceneTooltip} 单元测试。
 *
 * <p>覆盖：延时出现 / 即时消失的生命周期、延时未满取消、无 Motion 立即显示、
 * 组件卸载清理、目标卸载自动关闭、多行测量（换行宽度 / 超宽词省略 / 行数截断），
 * 以及默认浮层的 OVERLAY 配方外观、主题切换（含延迟显示后）不重建节点、指针穿透与卸载回收。
 * 延时用 Motion 采样器模拟时间推进（{@code __enableMotion + __sampleMotion}），无真实时钟。</p>
 */
public class SceneTooltipTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private SceneNode target;
    private Signal<String> textSignal;
    private MountHandle mountHandle;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int DELAY_MS = 500;
    private static final long MS = 1_000_000L;
    private static final float EPSILON = 0.0001F;

    /** 库默认主题的 OVERLAY 角色配方：浮层默认外观唯一来源。 */
    private static final SceneSurfaceStyle OVERLAY_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.OVERLAY);
    private static final int OVERLAY_BG_IDLE = OVERLAY_SURFACE.getIdle().getTint();
    private static final int OVERLAY_EDGE_IDLE = OVERLAY_SURFACE.getIdle().getEdge();
    private static final int TEXT_NORMAL = SceneThemes.DEFAULT.foreground();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
        target = new SceneNode();
        target.setPreferredWidth(60);
        target.setPreferredHeight(24);
        sceneRoot.appendChild(target);
        textSignal = Signal.create("提示文本");
        doLayout();
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    private void mountTooltip(SceneTooltip.Props props) {
        mountHandle = rt.mount(sceneRoot, () -> {
            SceneTooltip.attach(rt, props);
            return new SceneNode();
        });
        rt.flush();
        doLayout();
    }

    private void mountTooltipDefault() {
        mountTooltip(new SceneTooltip.Props(target, textSignal, null,
                DELAY_MS, 96, 3));
    }

    /** 在可切换的局部主题作用域内挂载（{@link SceneThemes#withTheme}）：主题变化驱动外观派生，不重建节点。 */
    private void mountTooltipWithTheme(Signal<SceneTheme> theme, SceneTooltip.Props props) {
        mountHandle = rt.mount(sceneRoot, () -> {
            SceneThemes.withTheme(theme, () -> SceneTooltip.attach(rt, props));
            return new SceneNode();
        });
        rt.flush();
        doLayout();
    }

    /** 逐个布局当前浮层（浮层不在主树布局链内）。 */
    private void doLayoutOverlays() {
        for (SceneOverlayHost.Entry entry : rt.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        }
    }

    private static int countType(List<PaintCommand> commands, PaintCommandType type) {
        int count = 0;
        for (PaintCommand command : commands) {
            if (command.getType() == type) {
                count++;
            }
        }
        return count;
    }

    /** 浮层根绝对盒（测试内浮层无宿主平移，cachedLayout 的 x/y 即绝对坐标）。 */
    private LayoutBox overlayBox() {
        return (LayoutBox) overlayRoot().getCachedLayout();
    }

    private void moveTo(int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
    }

    private void sampleMotion(long nanos) {
        // 阶段 2-2：__sampleMotion 不再内嵌 flush——宿主帧管线在 motion completion 时补 flush；
        // 本 helper 模拟宿主协议，保持「completion 同帧物化」的既有测试语义。
        boolean ranCompletion = rt.__sampleMotion(nanos);
        if (ranCompletion) {
            rt.flush();
        }
    }

    private int overlayCount() {
        return rt.getOverlayHost().size();
    }

    private SceneNode overlayRoot() {
        return rt.getOverlayHost().bottomFirst().get(0).getRoot();
    }

    // ==================== 延时出现 / 即时消失 ====================

    @Test
    public void delayedAppearanceAndImmediateDismiss() {
        rt.__enableMotion();
        mountTooltipDefault();
        moveTo(30, 12);
        Assert.assertEquals("hover 进入后延时未满不显示", 0, overlayCount());
        sampleMotion(0);
        Assert.assertEquals(0, overlayCount());
        sampleMotion(300 * MS);
        Assert.assertEquals(0, overlayCount());
        sampleMotion(600 * MS);
        Assert.assertEquals("延时届满后出现", 1, overlayCount());
        moveTo(200, 150);
        Assert.assertEquals("hover 结束即时关闭", 0, overlayCount());
    }

    @Test
    public void cancelledBeforeDelayNeverShows() {
        rt.__enableMotion();
        mountTooltipDefault();
        moveTo(30, 12);
        sampleMotion(100 * MS);
        moveTo(200, 150);
        sampleMotion(1_000 * MS);
        Assert.assertEquals(0, overlayCount());
    }

    @Test
    public void showsImmediatelyWithoutMotion() {
        // Motion 未启用 → 延时轨道即时完成（退化语义）
        mountTooltipDefault();
        moveTo(30, 12);
        Assert.assertEquals(1, overlayCount());
        moveTo(200, 150);
        Assert.assertEquals(0, overlayCount());
    }

    // ==================== 生命周期 ====================

    @Test
    public void componentUnmountCleansUp() {
        rt.__enableMotion();
        mountTooltipDefault();
        moveTo(30, 12);
        // 帧外创建的延时轨道在首次采样时钉定起点（与宿主逐帧采样一致），须先推一帧
        sampleMotion(0);
        sampleMotion(600 * MS);
        Assert.assertEquals(1, overlayCount());
        mountHandle.dispose();
        rt.flush();
        Assert.assertEquals("组件卸载后浮层摘除", 0, overlayCount());
        moveTo(30, 12);
        Assert.assertEquals("卸载后 hover 不再产生副作用", 0, overlayCount());
    }

    @Test
    public void targetUnmountClosesTooltip() {
        mountTooltipDefault();
        moveTo(30, 12);
        Assert.assertEquals(1, overlayCount());
        sceneRoot.removeChild(target);
        doLayout();
        Assert.assertEquals("目标卸载自动关闭", 0, overlayCount());
    }

    @Test
    public void disabledTooltipNeverShows() {
        Signal<Boolean> enabled = Signal.create(Boolean.FALSE);
        mountTooltip(new SceneTooltip.Props(target, textSignal, enabled, DELAY_MS, 96, 3));
        moveTo(30, 12);
        Assert.assertEquals(0, overlayCount());
    }

    // ==================== 多行测量 ====================

    @Test
    public void multilineWrapKeepsLinesWithinWidth() {
        textSignal.set("aaa bbb ccc ddd");
        mountTooltipDefault();
        moveTo(30, 12);
        Assert.assertEquals(1, overlayCount());
        SceneNode root = overlayRoot();
        List<SceneNode> lines = root.__getChildren();
        Assert.assertEquals(2, lines.size());
        for (SceneNode line : lines) {
            Assert.assertTrue("每行宽度 <= maxWidth(96)",
                    rt.measureTextWidth(line.getText(), 12) <= 96);
        }
    }

    @Test
    public void overlongWordEllipsizedToWidth() {
        // 16 字符 = 128px > 96px → 前缀 11 字符 + 省略号 = 96px
        textSignal.set("abcdefghijklmnop");
        mountTooltipDefault();
        moveTo(30, 12);
        SceneNode root = overlayRoot();
        List<SceneNode> lines = root.__getChildren();
        Assert.assertEquals(1, lines.size());
        Assert.assertEquals("abcdefghijk…", lines.get(0).getText());
    }

    @Test
    public void multilineTooltipWidthCoversLongestLine() {
        // 行 1 短、行 2 长：SHRINK 列宽必须覆盖最长行，而非只按第一行。
        textSignal.set("a bbbbbbbbbbb");
        mountTooltipDefault();
        moveTo(30, 12);
        SceneNode root = overlayRoot();
        layoutEngine.layout(root, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
        List<SceneNode> lines = root.__getChildren();
        Assert.assertEquals(2, lines.size());
        int longest = 0;
        for (SceneNode line : lines) {
            longest = Math.max(longest, rt.measureTextWidth(line.getText(), 12));
        }
        int padH = root.getPaddingLeft() + root.getPaddingRight();
        Object cached = root.getCachedLayout();
        Assert.assertNotNull(cached);
        int width = ((LayoutBox) cached).getWidth();
        Assert.assertEquals("列宽应覆盖最长行 + 水平 padding", longest + padH, width);
    }

    @Test
    public void maxLinesCapsAndForcesEllipsis() {
        // 15 个单字词 → 3 行；maxLines=2 → 截断并强制省略末行
        textSignal.set("a b c d e f g h i j k l m n o");
        mountTooltip(new SceneTooltip.Props(target, textSignal, null, DELAY_MS, 96, 2));
        moveTo(30, 12);
        SceneNode root = overlayRoot();
        List<SceneNode> lines = root.__getChildren();
        Assert.assertEquals(2, lines.size());
        Assert.assertTrue("末行以省略号结尾", lines.get(1).getText().endsWith("…"));
        Assert.assertTrue("末行宽度仍受 maxWidth 约束",
                rt.measureTextWidth(lines.get(1).getText(), 12) <= 96);
    }


    @Test
    public void breakLongWordsSplitsOverlongUrlAcrossLines() {
        // 与 overlongWordEllipsizedToWidth 同一输入：开启 breakLongWords 后不再截断而是逐行
        // 切开。tooltip 的存在意义就是给出被气泡截掉的完整地址，再截一次等于白做。
        textSignal.set("abcdefghijklmnop");
        mountTooltip(new SceneTooltip.Props(target, textSignal, null, DELAY_MS, 96, 3, true));
        moveTo(30, 12);
        List<SceneNode> lines = overlayRoot().__getChildren();
        Assert.assertEquals(2, lines.size());
        StringBuilder joined = new StringBuilder();
        for (SceneNode line : lines) {
            Assert.assertFalse("折行结果不得含省略号:" + line.getText(),
                    line.getText().contains("\u2026"));
            Assert.assertTrue("每行宽度 <= maxWidth(96)",
                    rt.measureTextWidth(line.getText(), 12) <= 96);
            joined.append(line.getText());
        }
        Assert.assertEquals("逐行拼回必须等于原文", "abcdefghijklmnop", joined.toString());
    }

    @Test
    public void sixArgPropsKeepsLegacyBreakLongWordsFalse() {
        // 六参便捷构造必须仍走旧行为，否则本类 overlongWordEllipsizedToWidth 会被静默改变
        SceneTooltip.Props legacy = new SceneTooltip.Props(target, textSignal, null, DELAY_MS, 96, 3);
        Assert.assertFalse("六参 = 旧 ellipsis 行为", legacy.breakLongWords());
        SceneTooltip.Props breaking = new SceneTooltip.Props(target, textSignal, null,
                DELAY_MS, 96, 3, true);
        Assert.assertTrue(breaking.breakLongWords());
    }


    @Test
    public void portalBoxWidthMustFitTheWrapWidthTheCallerAskedFor() {
        // 真机回归：attach 曾把 portal preferredWidth 硬编码为 DEFAULT_MAX_WIDTH_PX(260)，
        // 而聊天链接 tooltip 要求 320 换行宽 → 盒子比内容窄，文字挤过边框、并被盒子二次折行。
        // 本测试刻意不走「自己 layout 一遍」的路径：那样会绕开 portal 约束、给出假绿。
        int expected = 320 + 2 * SceneChromeTokens.PAD_SM + 2;
        Assert.assertEquals("盒宽 = 换行宽 + 左右 padding + 左右 1px 边框", expected,
                SceneTooltip.portalPreferredWidthPx(new SceneTooltip.Props(target, textSignal,
                        null, DELAY_MS, 320, 4, true)));
        Assert.assertEquals("不限宽时回退默认盒宽", SceneTooltip.DEFAULT_MAX_WIDTH_PX,
                SceneTooltip.portalPreferredWidthPx(new SceneTooltip.Props(target, textSignal,
                        null, DELAY_MS, 0, 4)));
    }

    @Test
    public void attachMustNotHardcodePortalPreferredWidth() throws Exception {
        // 零正则源码守卫（仓库既有风格）：盒宽只能由 props 推导，不得再写回硬编码常量
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream("src/main/java/club/heiqi/uilib/ui/scene/control/"
                        + "SceneTooltip.java"), "UTF-8"))) {
            String row;
            while ((row = reader.readLine()) != null) {
                sb.append(row).append('\n');
            }
        }
        String src = sb.toString();
        Assert.assertTrue("attach 必须用 portalPreferredWidthPx(props)",
                src.indexOf("new AnchoredPortalLayout(portalPreferredWidthPx(props)") >= 0);
        Assert.assertFalse("不得把盒宽写回硬编码 DEFAULT_MAX_WIDTH_PX",
                src.indexOf("new AnchoredPortalLayout(DEFAULT_MAX_WIDTH_PX") >= 0);
    }

    // ==================== 默认浮层外观：OVERLAY 配方 + 单次滤镜采样 ====================

    /**
     * 默认浮层 = {@link SceneThemes#DEFAULT} 的 OVERLAY 角色配方：底色/缘色/边框宽/圆角/实体高度/
     * 滤镜全部来自配方，行文本取主题正文前景色，一颗表面恰好采样一次滤镜。
     */
    @Test
    public void defaultOverlayUsesOverlayRecipeWithSingleBackdrop() {
        mountTooltipDefault();
        moveTo(30, 12);
        Assert.assertEquals("延时退化为立即显示", 1, overlayCount());
        rt.flush();
        doLayoutOverlays();

        SceneNode root = overlayRoot();
        Assert.assertEquals("浮层底色 = OVERLAY 配方 idle 染色", OVERLAY_BG_IDLE, root.getBackgroundColor());
        Assert.assertEquals("浮层缘色 = OVERLAY 配方 idle 缘色", OVERLAY_EDGE_IDLE, root.getBorderColor());
        Assert.assertEquals("浮层边框宽 = OVERLAY 配方", OVERLAY_SURFACE.getBorderWidth(), root.getBorderWidth());
        Assert.assertEquals("浮层圆角 = OVERLAY 配方", OVERLAY_SURFACE.getCornerRadius(), root.getCornerRadius());
        Assert.assertEquals("浮层实体高度 = OVERLAY 配方", OVERLAY_SURFACE.getIdle().getElevation(),
                root.__getSurfaceElevation(), EPSILON);

        UiBackdrop recipeBackdrop = OVERLAY_SURFACE.getBackdrop();
        Assert.assertNotNull("OVERLAY 配方自带滤镜", recipeBackdrop);
        Assert.assertNotNull("默认浮层应写入液态滤镜", root.getBackdrop());
        Assert.assertEquals("浮层材质 = OVERLAY 配方", recipeBackdrop.getEffect().getMaterial(),
                root.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("浮层模糊半径 = OVERLAY 配方", recipeBackdrop.getBlurRadius(),
                root.getBackdrop().getBlurRadius());

        List<SceneNode> lines = root.__getChildren();
        Assert.assertFalse("浮层应有行文本", lines.isEmpty());
        for (SceneNode line : lines) {
            Assert.assertEquals("行文本前景 = 主题正文色", TEXT_NORMAL, line.getTextColor());
        }

        PaintPlan plan = paintEngine.paint(root).getPlan();
        Assert.assertEquals("一颗浮层表面只采样一次滤镜", 1,
                countType(plan.getCommands(), PaintCommandType.BACKDROP));
    }

    // ==================== 主题切换（含延迟显示后） ====================

    /**
     * 延迟显示的浮层继承 {@link SceneThemes#withTheme} 局部主题（不是库默认档）；主题切换后浮层与
     * 文本前景随主题更新，节点身份不变、文本不丢、effect 数不增长。
     */
    @Test
    public void delayedOverlayInheritsScopedThemeAndFollowsThemeSwitch() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassLight());
        rt.__enableMotion();
        mountTooltipWithTheme(pageTheme, new SceneTooltip.Props(target, textSignal, null, DELAY_MS, 96, 3));
        moveTo(30, 12);
        Assert.assertEquals("延时未满不显示", 0, overlayCount());
        sampleMotion(0);
        sampleMotion(600 * MS);
        Assert.assertEquals("延时届满后出现", 1, overlayCount());
        rt.flush();

        SceneNode overlayIdentity = overlayRoot();
        SceneNode lineIdentity = overlayIdentity.__getChildren().get(0);
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneSurfaceStyle lightOverlay = light.surface(SceneTheme.Role.OVERLAY);
        SceneSurfaceStyle darkOverlay = dark.surface(SceneTheme.Role.OVERLAY);

        Assert.assertEquals("延迟显示的浮层继承局部主题（浅色档）", lightOverlay.getIdle().getTint(),
                overlayIdentity.getBackgroundColor());
        Assert.assertEquals("延迟显示的浮层缘色 = 浅色档配方", lightOverlay.getIdle().getEdge(),
                overlayIdentity.getBorderColor());
        Assert.assertEquals("延迟显示的行文本 = 浅色档正文前景", light.foreground(), lineIdentity.getTextColor());
        Assert.assertEquals("文本不丢", "提示文本", lineIdentity.getText());

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(dark);
        rt.flush();
        // 帧外 retarget：首帧钉定动画起点，第二帧推进并收敛（配方 transitionMillis=160）
        rt.__sampleMotion(1_000 * MS);
        rt.__sampleMotion(2_000 * MS);
        rt.flush();

        Assert.assertSame("主题切换不重建浮层", overlayIdentity, overlayRoot());
        Assert.assertSame("主题切换不重建行节点", lineIdentity, overlayRoot().__getChildren().get(0));
        Assert.assertEquals("浮层底色随主题更新", darkOverlay.getIdle().getTint(),
                overlayIdentity.getBackgroundColor());
        Assert.assertEquals("浮层缘色随主题更新", darkOverlay.getIdle().getEdge(),
                overlayIdentity.getBorderColor());
        Assert.assertEquals("浮层圆角随主题更新", darkOverlay.getCornerRadius(),
                overlayIdentity.getCornerRadius());
        Assert.assertEquals("行文本前景随主题更新", dark.foreground(), lineIdentity.getTextColor());
        Assert.assertEquals("文本不丢", "提示文本", lineIdentity.getText());
        Assert.assertEquals("切主题不新增订阅", effectsBeforeSwitch,
                ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 指针穿透（不得增加点击拦截） ====================

    /**
     * 浮层整树不可命中：点击浮层几何区域内的点必须穿透到下层节点；浮层根与行节点均
     * {@code hitTestable=false}。探针排在 target 之后（容器主轴布局的下一个位置），
     * 因此既覆盖浮层点击点、又不参与 target 的 hover 命中。
     */
    @Test
    public void overlayNeverInterceptsPointer() {
        SceneNode probe = new SceneNode();
        probe.setPreferredWidth(CANVAS_WIDTH);
        probe.setPreferredHeight(CANVAS_HEIGHT);
        sceneRoot.appendChild(probe);
        final int[] hits = new int[1];
        rt.on(probe, SceneEventType.POINTER_DOWN, (event, ctx) -> hits[0]++);

        textSignal.set("aaa bbb ccc ddd"); // 2 行 → 浮层高 42px，点击点落在 target(24px) 之下
        mountTooltipDefault();
        moveTo(30, 12);
        Assert.assertEquals("hover 仍命中 target 且浮层出现", 1, overlayCount());
        rt.flush();
        doLayoutOverlays();

        SceneNode root = overlayRoot();
        Assert.assertFalse("浮层根不可命中", root.isHitTestable());
        for (SceneNode line : root.__getChildren()) {
            Assert.assertFalse("行节点不可命中", line.isHitTestable());
        }

        LayoutBox box = overlayBox();
        Assert.assertNotNull("浮层已布局", box);
        int px = box.getX() + box.getWidth() - 2;
        int py = box.getY() + box.getHeight() - 2;
        LayoutBox probeBox = (LayoutBox) probe.getCachedLayout();
        Assert.assertNotNull("探针已布局", probeBox);
        Assert.assertTrue("点击点必须落在浮层内、探针内且避开 target: (" + px + "," + py + ")",
                py >= 24 && px >= probeBox.getX() && py >= probeBox.getY()
                        && px < probeBox.getX() + probeBox.getWidth()
                        && py < probeBox.getY() + probeBox.getHeight());

        InputFrameBuilder fb = new InputFrameBuilder(px, py);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, px, py, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
        Assert.assertEquals("浮层不拦截点击：事件穿透到下层节点", 1, hits[0]);
    }

    // ==================== 卸载回收 ====================

    /**
     * 卸载回收：handle.dispose + flush 后浮层移除，且绑定注册的 effect 回到基线（含浮层外观绑定）。
     */
    @Test
    public void disposeReleasesOverlaySurfaceBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        mountTooltipDefault();
        moveTo(30, 12);
        Assert.assertEquals("浮层已显示", 1, overlayCount());
        Assert.assertTrue("绑定应新增订阅", ReactiveTestProbe.registeredEffectCount() > baseline);

        mountHandle.dispose();
        rt.flush();
        Assert.assertEquals("卸载后浮层移除", 0, overlayCount());
        Assert.assertEquals("卸载回收全部绑定（含浮层外观）", baseline,
                ReactiveTestProbe.registeredEffectCount());
    }

}
