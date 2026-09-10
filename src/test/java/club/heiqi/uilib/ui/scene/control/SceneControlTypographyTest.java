package club.heiqi.uilib.ui.scene.control;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.input.mock.MockPlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintResult;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * issue72：公开控件根字号必须同时驱动文字、输入几何和光标。
 *
 * <p>交互走现有 harness，帧推进走真实 FramePipeline 的有界 settle；不手写额外的
 * flush/layout 循环。断言来自该帧 PaintCommand，不调用 typography/visual-layout helper
 * 生成期望值。ScenePaintCapture 固定使用 8x16 度量，故这里复用 RecordingRenderBackend，
 * 仅截取真实 paint 返回值，确保字号错误不能被固定宽高掩盖。</p>
 */
public class SceneControlTypographyTest {

    private static final String LONG_TEXT = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String PLACEHOLDER = "Hint";
    private final List<Fixture> fixtures = new ArrayList<>();

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

    @Test
    public void buttonPaintTracksRootFontSizeRoundTrip() {
        assertRoundTrip(Control.BUTTON, "Button");
    }

    @Test
    public void inputPaintTracksRootFontSizeRoundTrip() {
        assertRoundTrip(Control.INPUT, "abcdef");
    }

    @Test
    public void inputPlaceholderTracksRootFontSizeRoundTrip() {
        assertRoundTrip(Control.INPUT, "");
    }

    @Test
    public void areaPaintTracksRootFontSizeRoundTripWithoutLosingSoftWrappedText() {
        assertRoundTrip(Control.AREA, LONG_TEXT);
    }

    @Test
    public void areaPlaceholderTracksRootFontSizeRoundTrip() {
        assertRoundTrip(Control.AREA, "");
    }

    @Test
    public void rootFontSizeConfiguredInsideMountAppliesToFirstPaint() {
        for (Control control : Control.values()) {
            Fixture fixture = fixture(new FontMeasurer(), 240, 640);
            fixture.mount(control, "Initial", 32, 560);
            fixture.frame();
            assertPaint(fixture, "Initial", 32);
            fixture.closeControl();
        }
        for (Control control : new Control[]{Control.INPUT, Control.AREA}) {
            Fixture fixture = fixture(new FontMeasurer(), 240, 640);
            fixture.mount(control, "", 32, 560);
            fixture.frame();
            assertPaint(fixture, PLACEHOLDER, 32);
            fixture.closeControl();
        }
    }

    @Test
    public void areaRewrapsWhenFontSizeChangesButLineHeightDoesNot() {
        FontMeasurer measurer = new FontMeasurer();
        measurer.constantLineHeight = 32;
        Fixture fixture = fixture(measurer, 180, 640);
        fixture.mount(Control.AREA, LONG_TEXT, 16, 560);
        fixture.frame();
        assertPaint(fixture, LONG_TEXT, 16);
        List<String> smallLines = paintedLines(fixture);
        Assert.assertTrue("夹具必须实际发生软换行", smallLines.size() > 1);

        fixture.control.setFontSize(32);
        fixture.frame();
        assertPaint(fixture, LONG_TEXT, 32);
        List<String> largeLines = paintedLines(fixture);
        Assert.assertTrue("相同行高不能使旧宽度缓存命中", largeLines.size() > smallLines.size());
        Assert.assertTrue("第一视觉行应容纳更少字符", largeLines.get(0).length() < smallLines.get(0).length());

        fixture.control.setFontSize(16);
        fixture.frame();
        assertPaint(fixture, LONG_TEXT, 16);
        Assert.assertEquals("字号恢复后换行边界也恢复", smallLines, paintedLines(fixture));
    }

    @Test
    public void reusedVisualRowUpdatesItsTextAndFontWithoutDroppingCharacters() {
        Fixture fixture = fixture(new FontMeasurer(), 180, 640);
        fixture.mount(Control.AREA, LONG_TEXT, 16, 560);
        fixture.frame();
        // 白盒仅观察节点身份，证明起点 key=0 被复用；文字正确性仍由完整 paint 输出判定。
        SceneNode firstRow = textLeaves(fixture.control).get(0).__getParent();
        String replacement = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        fixture.value.set(replacement);
        fixture.frame();
        assertPaint(fixture, replacement, 16);
        Assert.assertSame("同一视觉行起点应复用 row", firstRow,
                textLeaves(fixture.control).get(0).__getParent());

        fixture.control.setFontSize(32);
        fixture.frame();
        assertPaint(fixture, replacement, 32);
        Assert.assertSame("换行边界改变后首行 key 仍可复用", firstRow,
                textLeaves(fixture.control).get(0).__getParent());
    }

    @Test
    public void inputClickAndArrowUseThePaintedFontMetrics() {
        Fixture fixture = fixture(new FontMeasurer(), 300, 160);
        fixture.mount(Control.INPUT, "abcdef", 16, 80);
        fixture.frame();
        fixture.control.setFontSize(32);
        fixture.frame();
        PaintCommand text = textCommands(fixture).get(0);
        fixture.harness.clickAt(text.getLeft() + fixture.measurer.measureWidth("ab", 32),
                text.getTop() + fixture.measurer.lineHeight(32) / 2);
        fixture.frame();
        assertCaretHeight(fixture, 32);
        fixture.harness.typeText("X");
        fixture.frame();
        Assert.assertEquals("点击以绘制字号定位到第二字符后", "abXcdef", fixture.value.get());
        assertPaint(fixture, "abXcdef", 32);

        fixture.harness.pressKey(SceneKey.ARROW_LEFT);
        fixture.harness.typeText("Y");
        fixture.frame();
        Assert.assertEquals("左箭头移动一个字符", "abYXcdef", fixture.value.get());
        assertPaint(fixture, "abYXcdef", 32);
    }

    @Test
    public void areaClickAndVerticalArrowAgreeWithPaintedSoftWrapRows() {
        Fixture fixture = fixture(new FontMeasurer(), 180, 640);
        fixture.mount(Control.AREA, LONG_TEXT, 16, 560);
        fixture.frame();
        fixture.control.setFontSize(32);
        fixture.frame();
        List<String> lines = paintedLines(fixture);
        Assert.assertTrue("至少两条视觉行", lines.size() > 1);
        PaintCommand first = textCommands(fixture).get(0);
        String columnPrefix = lines.get(0).substring(0, 2);
        fixture.harness.clickAt(first.getLeft() + fixture.measurer.measureWidth(columnPrefix, 32),
                first.getTop() + fixture.measurer.lineHeight(32) / 2);
        fixture.harness.pressKey(SceneKey.ARROW_DOWN);
        fixture.harness.typeText("!");
        fixture.frame();
        int insertion = lines.get(0).length() + columnPrefix.length();
        String expected = LONG_TEXT.substring(0, insertion) + "!" + LONG_TEXT.substring(insertion);
        Assert.assertEquals("DOWN 保持绘制列，跨到下一条视觉行", expected, fixture.value.get());
        assertPaint(fixture, expected, 32);
        assertCaretHeight(fixture, 32);
    }

    @Test
    public void areaFontChangeKeepsStationaryCaretFollowedByViewport() {
        Fixture fixture = fixture(new FontMeasurer(), 180, 240);
        fixture.mount(Control.AREA, LONG_TEXT, 16, 80);
        fixture.frame();
        PaintCommand first = textCommands(fixture).get(0);
        fixture.harness.clickAt(first.getLeft(), first.getTop() + 1);
        for (int i = 0; i < LONG_TEXT.length(); i++) {
            fixture.harness.pressKey(SceneKey.ARROW_RIGHT);
        }
        fixture.frame();
        SceneNode viewport = scrollableNode(fixture.control);
        Assert.assertNotNull("TextArea 必须有滚动视口", viewport);
        Assert.assertEquals("小字号末行在初始视口内", 0, viewport.getScrollOffsetY());

        // 编辑后指针移出；caret 索引不变，后续字号变化仍必须驱动滚动跟随。
        fixture.movePointerOutside();
        fixture.control.setFontSize(32);
        fixture.frame();
        Assert.assertTrue("caret 索引未变，字号改变仍应触发纵向跟随", viewport.getScrollOffsetY() > 0);
        assertCaretHeight(fixture, 32);
        PaintCommand caret = visibleCaret(fixture);
        Assert.assertTrue("光标完整落在实际 viewport 裁剪范围内", caretInsideActiveClips(fixture, caret));

        for (int i = 0; i < LONG_TEXT.length(); i++) {
            fixture.harness.pressKey(SceneKey.ARROW_UP);
        }
        fixture.frame();
        Assert.assertEquals("向上移动回首行，滚动跟随归零", 0, viewport.getScrollOffsetY());
        assertCaretHeight(fixture, 32);
    }

    @Test
    public void manualAreaScrollIsNotUndoneByTheNextOrdinaryFrame() {
        Fixture fixture = fixture(new FontMeasurer(), 180, 240);
        fixture.mount(Control.AREA, LONG_TEXT, 32, 80);
        fixture.frame();
        PaintCommand first = textCommands(fixture).get(0);
        fixture.harness.clickAt(first.getLeft(), first.getTop() + 1);
        fixture.frame();
        SceneNode viewport = scrollableNode(fixture.control);
        Assert.assertNotNull("TextArea 必须有滚动视口", viewport);
        fixture.harness.scroll(viewport, -1);
        int manualOffset = viewport.getScrollOffsetY();
        Assert.assertTrue("滚轮应把视口移离文首 caret", manualOffset > 0);
        fixture.movePointerOutside();
        fixture.frame();
        Assert.assertEquals("布局桥接不能误当成新的 caret 跟随请求", manualOffset, viewport.getScrollOffsetY());
        fixture.frame();
        Assert.assertEquals("后续普通帧保留用户滚动位置", manualOffset, viewport.getScrollOffsetY());
    }

    @Test
    public void inputFontChangeKeepsStationaryCaretInsideHorizontalViewport() {
        Fixture fixture = fixture(new FontMeasurer(), 160, 160);
        fixture.mount(Control.INPUT, "abcdefghijkl", 16, 80);
        fixture.frame();
        PaintCommand first = textCommands(fixture).get(0);
        fixture.harness.clickAt(first.getLeft(), first.getTop() + 1);
        fixture.harness.pressKey(SceneKey.END);
        fixture.frame();
        Assert.assertEquals("小字号文末尚在视口内", 0, fixture.control.getScrollOffsetX());
        fixture.control.setFontSize(32);
        fixture.frame();
        Assert.assertTrue("索引不变，字号放大仍触发横向跟随", fixture.control.getScrollOffsetX() > 0);
        assertCaretHeight(fixture, 32);
        Assert.assertTrue("横向滚动后 caret 完整可见", caretInsideActiveClips(fixture, visibleCaret(fixture)));
        fixture.control.setFontSize(16);
        fixture.frame();
        assertCaretHeight(fixture, 16);
        Assert.assertEquals("字号缩小后超额滚动被收回", 0, fixture.control.getScrollOffsetX());
        Assert.assertTrue(caretInsideActiveClips(fixture, visibleCaret(fixture)));
    }

    @Test
    public void selectedTextAndBothCaretSlotsTrackFontSizeInBothEditors() {
        for (Control control : new Control[]{Control.INPUT, Control.AREA}) {
            Fixture fixture = fixture(new FontMeasurer(), 300, 240);
            fixture.mount(control, "abcdef", 16, 160);
            fixture.frame();
            PaintCommand first = textCommands(fixture).get(0);
            int y = first.getTop() + fixture.measurer.lineHeight(16) / 2;
            int startX = first.getLeft() + fixture.measurer.measureWidth("a", 16);
            int endX = first.getLeft() + fixture.measurer.measureWidth("abcd", 16);
            fixture.harness.pressAt(startX, y);
            fixture.harness.moveAt(endX, y);
            fixture.harness.releaseAt(endX, y);
            for (int fontSize : new int[]{16, 32, 16}) {
                fixture.control.setFontSize(fontSize);
                fixture.frame();
                assertPaint(fixture, "abcdef", fontSize);
                PaintCommand selected = null;
                for (PaintCommand text : textCommands(fixture)) {
                    if (text.getTextStyle().getColor() == SceneChromeTokens.SELECTION_TEXT) {
                        selected = text;
                    }
                }
                Assert.assertNotNull("拖选应产生高亮文本片段", selected);
                Assert.assertEquals("选区内容不随字号漂移", "bcd", selected.getText());
                assertCaretHeight(fixture, fontSize);
                Assert.assertEquals("右端选区 caret 与高亮段宽度一致",
                        selected.getLeft() + fixture.measurer.measureWidth("bcd", fontSize),
                        visibleCaret(fixture).getLeft());
            }
            fixture.closeControl();
        }
    }

    @Test
    public void focusedCaretTracksFontSizeRoundTripInBothEditors() {
        for (Control control : new Control[]{Control.INPUT, Control.AREA}) {
            Fixture fixture = fixture(new FontMeasurer(), 300, 240);
            fixture.mount(control, "abc", 16, 160);
            fixture.frame();
            PaintCommand text = textCommands(fixture).get(0);
            fixture.harness.clickAt(text.getLeft(), text.getTop() + 1);
            fixture.harness.pressKey(SceneKey.ARROW_RIGHT);
            for (int fontSize : new int[]{16, 32, 16}) {
                fixture.control.setFontSize(fontSize);
                fixture.frame();
                assertPaint(fixture, "abc", fontSize);
                assertCaretHeight(fixture, fontSize);
                PaintCommand prefix = textCommands(fixture).get(0);
                Assert.assertEquals("首字符仍处于 caret 前", "a", prefix.getText());
                Assert.assertEquals("caret 横坐标与已绘制前缀宽度一致",
                        prefix.getLeft() + fixture.measurer.measureWidth("a", fontSize),
                        visibleCaret(fixture).getLeft());
            }
            fixture.closeControl();
        }
    }

    @Test
    public void areaClickOnSecondVisualRowUsesPaintedCoordinates() {
        Fixture fixture = fixture(new FontMeasurer(), 180, 640);
        fixture.mount(Control.AREA, LONG_TEXT, 32, 560);
        fixture.frame();
        List<String> lines = paintedLines(fixture);
        List<PaintCommand> texts = textCommands(fixture);
        Assert.assertTrue("夹具必须绘制多行", lines.size() > 1);
        PaintCommand second = texts.get(1);
        Assert.assertTrue("第二命令属于下一视觉行", second.getTop() > texts.get(0).getTop());
        String prefix = lines.get(1).substring(0, 2);
        fixture.harness.clickAt(second.getLeft() + fixture.measurer.measureWidth(prefix, 32),
                second.getTop() + fixture.measurer.lineHeight(32) / 2);
        fixture.harness.typeText("!");
        fixture.frame();
        int insertion = lines.get(0).length() + prefix.length();
        String expected = LONG_TEXT.substring(0, insertion) + "!" + LONG_TEXT.substring(insertion);
        Assert.assertEquals("点击纵横坐标共同决定视觉行内插入点", expected, fixture.value.get());
        assertPaint(fixture, expected, 32);
    }

    @Test
    public void fontEpochRefreshesInputAndAreaCaretHeightsWithoutChangingFontSize() {
        for (Control control : new Control[]{Control.INPUT, Control.AREA}) {
            FontMeasurer measurer = new FontMeasurer();
            Fixture fixture = fixture(measurer, 300, 240);
            fixture.mount(control, "abc", 32, 160);
            fixture.frame();
            PaintCommand text = textCommands(fixture).get(0);
            fixture.harness.clickAt(text.getLeft(), text.getTop() + 1);
            fixture.frame();
            assertCaretHeight(fixture, 32);
            int before = visibleCaret(fixture).getBottom() - visibleCaret(fixture).getTop();

            measurer.extraLineHeight = 9;
            measurer.measureEpoch++;
            fixture.frame();
            assertPaint(fixture, "abc", 32);
            assertCaretHeight(fixture, 32);
            Assert.assertTrue("字体重载应更新 caret 的显式高度",
                    visibleCaret(fixture).getBottom() - visibleCaret(fixture).getTop() > before);
            fixture.closeControl();
        }
    }

    @Test
    public void removedVisualRowsReleaseBindingsAndDoNotReactToLaterFontChanges() throws Exception {
        Fixture fixture = fixture(new FontMeasurer(), 180, 640);
        fixture.mount(Control.AREA, "short", 16, 560);
        fixture.frame();
        int shortEffects = registeredEffectCount();
        fixture.value.set(LONG_TEXT);
        fixture.frame();
        List<SceneNode> longLeaves = textLeaves(fixture.control);
        SceneNode removedLeaf = longLeaves.get(longLeaves.size() - 1);
        Assert.assertTrue("增加视觉行必须增加被 Owner 持有的绑定", registeredEffectCount() > shortEffects);

        fixture.value.set("short");
        fixture.frame();
        Assert.assertEquals("动态行删除退订，而非只从树上移除", shortEffects, registeredEffectCount());
        Assert.assertFalse(textLeaves(fixture.control).contains(removedLeaf));
        int detachedFont = removedLeaf.getFontSize();
        fixture.control.setFontSize(32);
        fixture.frame();
        assertPaint(fixture, "short", 32);
        Assert.assertEquals("已卸载行不得继续接收根字号变化", detachedFont, removedLeaf.getFontSize());

        fixture.value.set(LONG_TEXT);
        fixture.frame();
        fixture.value.set("short");
        fixture.frame();
        Assert.assertEquals("重复增删不能积累订阅", shortEffects, registeredEffectCount());
    }

    @Test
    public void unmountReleasesTypographyAndDynamicRowOwners() throws Exception {
        for (Control control : Control.values()) {
            Fixture fixture = fixture(new FontMeasurer(), 180, 640);
            int before = registeredEffectCount();
            fixture.mount(control, control == Control.AREA ? LONG_TEXT : "abc", 16, 560);
            fixture.frame();
            List<SceneNode> detached = textLeaves(fixture.control);
            // 卸载前记录旧通道推送到各文字节点的层 1 声明快照（退订的可证伪锚点）。
            List<Integer> explicitBeforeUnmount = new ArrayList<Integer>();
            for (SceneNode leaf : detached) {
                explicitBeforeUnmount.add(leaf.getExplicitFontSize());
            }
            Assert.assertTrue("控件挂载应注册响应式工作", registeredEffectCount() > before);
            fixture.closeControl();
            Assert.assertEquals("mount Owner 应释放包括 typography 在内的全部 effect", before,
                    registeredEffectCount());
            fixture.control.setFontSize(32);
            fixture.value.set("changed after unmount");
            LayoutResult afterUnmount = fixture.frame();
            Assert.assertEquals("空树没有控件文字", "", paintedText(fixture));
            // 「退订 + 已摘除」的可证伪表达：卸载后旧通道不得再向文字节点推送层 1 声明
            // （effect 泄漏时会把根的新字号推下去，本断言即红）；且卸载子树不再进入场景帧。
            for (int i = 0; i < detached.size(); i++) {
                SceneNode leaf = detached.get(i);
                Assert.assertEquals("卸载后旧通道不得再推送字号声明（typography effect 已退订）",
                        explicitBeforeUnmount.get(i), leaf.getExplicitFontSize());
                Assert.assertFalse("已卸载节点不得进入场景帧的重排集合",
                        afterUnmount.getRelayoutedNodes().contains(leaf)
                                || afterUnmount.getConstraintRelayoutedNodes().contains(leaf));
                // 卸载不变量：文字节点保留旧通道在挂载期写下的层 1 声明（16），
                // 不再接收根字号变化 —— 若 typography effect 泄漏，把根的新字号 32 推下来即红。
                Assert.assertEquals("卸载的文字不能继续接收根字号（声明已定 + effect 已退订）",
                        16, leaf.getFontSize());
            }
        }
    }

    @Test
    public void normalFrameConvergesAndSteadyFrameDoesNoExtraLayoutOrTextMeasurement() {
        Fixture fixture = fixture(new FontMeasurer(), 180, 640);
        fixture.mount(Control.AREA, LONG_TEXT, 16, 560);
        fixture.frame();
        for (int fontSize : new int[]{32, 16}) {
            fixture.control.setFontSize(fontSize);
            fixture.frame();
            assertPaint(fixture, LONG_TEXT, fontSize);
            int widthCalls = fixture.measurer.widthCalls;
            LayoutResult steady = fixture.frame();
            Assert.assertEquals("稳态只需首轮 settle 探脏", 1, fixture.pipeline.__settlePasses());
            Assert.assertEquals("稳态没有节点重新布局", 0, steady.getRelayoutCount());
            Assert.assertEquals("稳态不应重新测量或重建换行", widthCalls, fixture.measurer.widthCalls);
        }
    }

    private void assertRoundTrip(Control control, String value) {
        Fixture fixture = fixture(new FontMeasurer(), 240, 640);
        fixture.mount(control, value, 16, 560);
        for (int fontSize : new int[]{16, 32, 16}) {
            fixture.control.setFontSize(fontSize);
            fixture.frame();
            assertPaint(fixture, value.isEmpty() ? PLACEHOLDER : value, fontSize);
        }
    }

    private Fixture fixture(FontMeasurer measurer, int width, int height) {
        Fixture fixture = new Fixture(measurer, width, height);
        fixtures.add(fixture);
        return fixture;
    }

    private static void assertPaint(Fixture fixture, String expected, int fontSize) {
        List<PaintCommand> texts = textCommands(fixture);
        Assert.assertFalse("必须产生 TEXT 命令", texts.isEmpty());
        Assert.assertEquals("各视觉行和选区片段拼接必须保留完整文字", expected, paintedText(fixture));
        for (PaintCommand text : texts) {
            Assert.assertEquals("所有已绘制片段使用根字号：" + text.getText(), fontSize,
                    text.getTextStyle().getFontSize());
        }
    }

    private static List<PaintCommand> textCommands(Fixture fixture) {
        List<PaintCommand> result = new ArrayList<>();
        for (PaintCommand command : fixture.paint.commands) {
            if (command.getType() == PaintCommandType.TEXT && !command.getText().isEmpty()) {
                result.add(command);
            }
        }
        return result;
    }

    private static String paintedText(Fixture fixture) {
        StringBuilder text = new StringBuilder();
        for (PaintCommand command : textCommands(fixture)) {
            text.append(command.getText());
        }
        return text.toString();
    }

    private static List<String> paintedLines(Fixture fixture) {
        Map<Integer, StringBuilder> lines = new TreeMap<>();
        for (PaintCommand command : textCommands(fixture)) {
            lines.computeIfAbsent(command.getTop(), ignored -> new StringBuilder()).append(command.getText());
        }
        List<String> result = new ArrayList<>();
        for (StringBuilder line : lines.values()) {
            result.add(line.toString());
        }
        return result;
    }

    private static PaintCommand visibleCaret(Fixture fixture) {
        PaintCommand found = null;
        for (PaintCommand command : fixture.paint.commands) {
            if (command.getType() == PaintCommandType.BACKGROUND
                    && command.getColor() == SceneChromeTokens.BORDER_FOCUS
                    && command.getRight() - command.getLeft() == 1) {
                Assert.assertNull("只能有一个可见 caret 槽", found);
                found = command;
            }
        }
        Assert.assertNotNull("聚焦输入必须绘制 caret", found);
        return found;
    }

    private static void assertCaretHeight(Fixture fixture, int fontSize) {
        PaintCommand caret = visibleCaret(fixture);
        Assert.assertEquals("光标高度使用当前字体度量", fixture.measurer.lineHeight(fontSize),
                caret.getBottom() - caret.getTop());
    }

    private static boolean caretInsideActiveClips(Fixture fixture, PaintCommand caret) {
        List<PaintCommand> clips = new ArrayList<>();
        for (PaintCommand command : fixture.paint.commands) {
            if (command.getType() == PaintCommandType.CLIP_PUSH) {
                clips.add(command);
            } else if (command.getType() == PaintCommandType.CLIP_POP) {
                clips.remove(clips.size() - 1);
            } else if (command == caret) {
                for (PaintCommand clip : clips) {
                    if (caret.getTop() < clip.getTop() || caret.getBottom() > clip.getBottom()
                            || caret.getLeft() < clip.getLeft() || caret.getRight() > clip.getRight()) {
                        return false;
                    }
                }
                return !clips.isEmpty();
            }
        }
        return false;
    }

    /** 生命周期与 key 复用需要节点身份；不读取 primitive 的槽位顺序或私有 visual model。 */
    private static List<SceneNode> textLeaves(SceneNode root) {
        List<SceneNode> result = new ArrayList<>();
        if (root.getText() != null && !root.getText().isEmpty()) {
            result.add(root);
        }
        for (SceneNode child : root.__getChildren()) {
            result.addAll(textLeaves(child));
        }
        return result;
    }

    private static SceneNode scrollableNode(SceneNode root) {
        if (root.isScrollable()) return root;
        for (SceneNode child : root.__getChildren()) {
            SceneNode found = scrollableNode(child);
            if (found != null) return found;
        }
        return null;
    }

    /** 复用 scheduler 已有测试探针（与 ChatToolbarIconsTest 同入口），不新增 main API。 */
    private static int registeredEffectCount() throws Exception {
        Method method = ReactiveScheduler.class.getDeclaredMethod("registeredEffectCount");
        method.setAccessible(true);
        return ((Integer) method.invoke(ReactiveScheduler.get())).intValue();
    }

    private enum Control { BUTTON, INPUT, AREA }

    private static final class FontMeasurer implements SceneTextMeasurer {
        int constantLineHeight;
        int extraLineHeight;
        int measureEpoch;
        int widthCalls;

        @Override
        public int measureWidth(String text, int fontSizePx) {
            widthCalls++;
            return text == null ? 0 : text.codePointCount(0, text.length()) * fontSizePx / 2;
        }

        @Override
        public int lineHeight(int fontSizePx) {
            return (constantLineHeight > 0 ? constantLineHeight : fontSizePx) + extraLineHeight;
        }

        @Override
        public int epoch() {
            return measureEpoch;
        }
    }

    /** 只观察 super.paint 的产物：FramePipeline 仍执行真实 paint/replay。 */
    private static final class CapturingPaintEngine extends ScenePaintEngine {
        List<PaintCommand> commands = new ArrayList<>();

        CapturingPaintEngine(SceneTextMeasurer measurer) {
            super(measurer);
        }

        @Override
        public PaintResult paint(SceneNode root) {
            PaintResult result = super.paint(root);
            commands = new ArrayList<>(result.getPlan().getCommands());
            return result;
        }
    }

    private static final class Fixture {
        final FontMeasurer measurer;
        final SceneInteractionHarness harness;
        final SceneRuntime runtime;
        final SceneNode scene = SceneNode.column();
        final CapturingPaintEngine paint;
        final SceneFramePipeline pipeline;
        final MockPlatformInputSource input;
        final int width;
        final int height;
        Signal<String> value;
        MountHandle mount;
        SceneNode control;

        Fixture(FontMeasurer measurer, int width, int height) {
            this.measurer = measurer;
            this.width = width;
            this.height = height;
            harness = SceneInteractionHarness.create(measurer);
            runtime = harness.getRuntime();
            harness.mountRoot(scene, width, height);
            paint = new CapturingPaintEngine(measurer);
            input = new MockPlatformInputSource(width, height);
            pipeline = new SceneFramePipeline(runtime, new SceneLayoutEngine(measurer), paint,
                    new ScenePaintReplayer(), measurer, input);
            pipeline.__setAssertionsEnabled(true);
        }

        void mount(Control kind, String initial, int fontSize, int viewportHeight) {
            value = Signal.create(initial);
            Supplier<SceneNode> component;
            switch (kind) {
                case BUTTON:
                    component = SceneButton.create(runtime,
                            new SceneButton.Props(value, Signal.create(true), () -> {}));
                    break;
                case INPUT:
                    component = SceneTextInput.create(runtime, SceneTextInput.Props.builder(value)
                            .placeholder(PLACEHOLDER).onChange(value::set).build());
                    break;
                case AREA:
                    component = SceneTextArea.create(runtime, SceneTextArea.Props.builder(value)
                            .placeholder(PLACEHOLDER).viewportHeight(viewportHeight).onChange(value::set).build());
                    break;
                default:
                    throw new AssertionError(kind);
            }
            mount = runtime.mount(scene, () -> component.get().setFontSize(fontSize));
            control = mount.getRoot();
        }

        /**
         * 显式模拟操作后移出编辑器：MOVE 在真实 ROUTE 阶段建立 hover，源的粘滞位置随后供
         * HOVER_RECONCILE 使用。没有补 flush，也不在每帧偷偷重发 MOVE。
         *
         * <p>滚动跨目标时 Router 既有协议把 hover 写入留到下一帧，与 pipeline 的 PAINT
         * pending-write 断言有独立矛盾；字号/滚动跟随用例用场外指针隔离该既有行为。</p>
         */
        void movePointerOutside() {
            input.enqueuePointer(ScenePointerAction.MOVE, width + 1, height + 1,
                    SceneMouseButton.NONE, 0, 0, 0, false, false, false, false, 1000L);
        }

        LayoutResult frame() {
            // harness 的输入时间为 1000ns；使用同一时钟避免测试偶然落在 caret 闪烁关闭区间。
            LayoutResult result = pipeline.run(scene, width, height, new RecordingRenderBackend(), 0, 0, 1000L);
            Assert.assertFalse("正常帧必须在现有 settle 上限内收敛", pipeline.__isSettleDeferred());
            Assert.assertFalse("paint 前不能残留布局脏标", scene.__isSelfLayoutDirty());
            Assert.assertFalse("paint 前子树布局必须已完成", scene.__isDescendantLayoutDirty());
            return result;
        }

        void closeControl() {
            mount.dispose();
        }
    }
}
