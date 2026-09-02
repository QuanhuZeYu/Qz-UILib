package club.heiqi.uilib.ui.scene.control;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * {@link SceneTooltip} 单元测试。
 *
 * <p>覆盖：延时出现 / 即时消失的生命周期、延时未满取消、无 Motion 立即显示、
 * 组件卸载清理、目标卸载自动关闭、多行测量（换行宽度 / 超宽词省略 / 行数截断）。
 * 延时用 Motion 采样器模拟时间推进（{@code __enableMotion + __sampleMotion}），无真实时钟。</p>
 */
public class SceneTooltipTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private SceneNode target;
    private Signal<String> textSignal;
    private MountHandle mountHandle;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int DELAY_MS = 500;
    private static final long MS = 1_000_000L;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
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
        int width = ((club.heiqi.uilib.ui.scene.layout.LayoutBox) cached).getWidth();
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

}
