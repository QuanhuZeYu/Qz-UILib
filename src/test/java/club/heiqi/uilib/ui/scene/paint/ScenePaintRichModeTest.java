package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;

/**
 * scene 绘制引擎与回放器的富文本模式契约测试。
 *
 * <p>验证：maxTextWidth 拆行出 N 条 TEXT 命令、内容模式透传进命令与回放参数、
 * 无换行配置时单命令路径零回归。</p>
 */
public class ScenePaintRichModeTest {

    private final ScenePaintReplayer replayer = new ScenePaintReplayer();

    @Test
    public void shouldEmitPerLineTextCommandsWithMode() {
        SplitRecordingMeasurer measurer = new SplitRecordingMeasurer();
        measurer.nextLines = java.util.Arrays.asList("A", "B");
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("AB");
        textNode.setMaxTextWidth(100);
        textNode.setTextContentMode(TextStyle.TEXT_MODE_RICH_TAGS);
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(200));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        List<PaintCommand> textCommands = new ArrayList<PaintCommand>();
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.TEXT) {
                textCommands.add(command);
            }
        }
        Assert.assertEquals(2, textCommands.size());
        Assert.assertEquals("A", textCommands.get(0).getText());
        Assert.assertEquals(TextStyle.TEXT_MODE_RICH_TAGS, textCommands.get(0).getTextStyle().getTextMode());
        Assert.assertEquals("B", textCommands.get(1).getText());
        Assert.assertEquals(TextStyle.TEXT_MODE_RICH_TAGS, textCommands.get(1).getTextStyle().getTextMode());
        // 行坐标按行高（16）递增
        Assert.assertEquals(textCommands.get(0).getTop() + 16, textCommands.get(1).getTop());
        // 拆行请求带正确参数
        Assert.assertEquals("AB", measurer.lastText);
        Assert.assertEquals(100, measurer.lastWrapWidth);
        Assert.assertEquals(TextStyle.TEXT_MODE_RICH_TAGS, measurer.lastTextMode);
    }

    @Test
    public void shouldKeepSingleLineWithoutWrap() {
        SplitRecordingMeasurer measurer = new SplitRecordingMeasurer();
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("AB");
        textNode.setTextContentMode(TextStyle.TEXT_MODE_RICH_TAGS);
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(200));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        int textCommandCount = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.TEXT) {
                textCommandCount++;
                Assert.assertEquals("AB", command.getText());
                Assert.assertEquals(TextStyle.TEXT_MODE_RICH_TAGS, command.getTextStyle().getTextMode());
            }
        }
        Assert.assertEquals(1, textCommandCount);
        Assert.assertEquals(0, measurer.callCount);
    }

    @Test
    public void shouldAdvanceLinesByPerLineHeightWithOversizedSpan() {
        SplitRecordingMeasurer measurer = new SplitRecordingMeasurer();
        measurer.nextLines = java.util.Arrays.asList("A", "B");
        measurer.lineHeightByText.put("A", Integer.valueOf(16));
        measurer.lineHeightByText.put("B", Integer.valueOf(32));
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("AB");
        textNode.setMaxTextWidth(100);
        textNode.setTextContentMode(TextStyle.TEXT_MODE_RICH_TAGS);
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(200));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        List<PaintCommand> textCommands = new ArrayList<PaintCommand>();
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.TEXT) {
                textCommands.add(command);
            }
        }
        Assert.assertEquals(2, textCommands.size());
        // 布局高度按逐行行高求和（16+32=48），块高与内高一致 → 首行顶 0
        Assert.assertEquals(0, textCommands.get(0).getTop());
        // 第二行按首行行高 16 推进（不再侵入式等距）
        Assert.assertEquals(16, textCommands.get(1).getTop());
    }

    @Test
    public void shouldCenterSingleLineMixedTextByMaxLineHeight() {
        SplitRecordingMeasurer measurer = new SplitRecordingMeasurer();
        measurer.lineHeightByText.put("AB", Integer.valueOf(32));
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("AB");
        textNode.setTextContentMode(TextStyle.TEXT_MODE_RICH_TAGS);
        root.appendChild(textNode);

        // 布局：非 wrap 富文本高度按行内最大字号行高 32
        layoutEngine.layout(root, new Constraints(200));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        List<PaintCommand> textCommands = new ArrayList<PaintCommand>();
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.TEXT) {
                textCommands.add(command);
            }
        }
        Assert.assertEquals(1, textCommands.size());
        // 块高 32 与内高一致 → 顶 0（混排大字不再超出块顶）
        Assert.assertEquals(0, textCommands.get(0).getTop());
    }

    @Test
    public void shouldReplayWithTextMode() {
        SplitRecordingMeasurer measurer = new SplitRecordingMeasurer();
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("X");
        textNode.setTextContentMode(TextStyle.TEXT_MODE_RICH_TAGS);
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(200));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        RecordingRenderBackend backend = new RecordingRenderBackend();
        replayer.replay(plan, backend, 0, 0);

        RenderCallView textCall = null;
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) {
            if ("drawText".equals(call.methodName())) {
                textCall = new RenderCallView(call);
            }
        }
        Assert.assertNotNull(textCall);
        Assert.assertEquals("X", textCall.getString(0));
        Assert.assertEquals(7, textCall.argCount());
        Assert.assertEquals(TextStyle.TEXT_MODE_RICH_TAGS, textCall.getInt(6));
    }

    @Test
    public void shouldRegenerateFragmentOnModeChange() {
        SplitRecordingMeasurer measurer = new SplitRecordingMeasurer();
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("X");
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(200));
        paintEngine.paint(root);

        textNode.setTextContentMode(TextStyle.TEXT_MODE_RICH_TAGS);
        PaintResult result = paintEngine.paint(root);

        Assert.assertTrue(result.getRegeneratedFragmentCount() >= 1);
    }

    /** 记录 splitLines 调用参数的测量替身（组合委托 FixedTextMeasurer）。 */
    private static final class SplitRecordingMeasurer implements SceneTextMeasurer {

        private final FixedTextMeasurer delegate = new FixedTextMeasurer(1, 16);

        private String lastText;
        private int lastWrapWidth;
        private int lastTextMode;
        private int callCount;
        private List<String> nextLines = new ArrayList<String>();
        private java.util.Map<String, Integer> lineHeightByText = new java.util.HashMap<String, Integer>();

        @Override
        public int measureWidth(String text, int fontSizePx) {
            return delegate.measureWidth(text, fontSizePx);
        }

        @Override
        public int lineHeight(int fontSizePx) {
            return delegate.lineHeight(fontSizePx);
        }

        @Override
        public int ascent(int fontSizePx) {
            return delegate.ascent(fontSizePx);
        }

        @Override
        public int descent(int fontSizePx) {
            return delegate.descent(fontSizePx);
        }

        @Override
        public int lineGap(int fontSizePx) {
            return delegate.lineGap(fontSizePx);
        }

        @Override
        public int epoch() {
            return delegate.epoch();
        }

        @Override
        public List<String> splitLines(String text, int fontSizePx, int wrapWidth, int textMode) {
            lastText = text;
            lastWrapWidth = wrapWidth;
            lastTextMode = textMode;
            callCount++;
            return new ArrayList<String>(nextLines);
        }

        @Override
        public int lineHeight(String text, int fontSizePx, int textMode) {
            Integer custom = lineHeightByText.get(text);
            return custom == null ? delegate.lineHeight(fontSizePx) : custom.intValue();
        }
    }

    /** RenderCall 只读视图（记录参数数与 typed getter 转调）。 */
    private static final class RenderCallView {

        private final RecordingRenderBackend.RenderCall delegate;

        private RenderCallView(RecordingRenderBackend.RenderCall delegate) {
            this.delegate = delegate;
        }

        private int argCount() {
            return delegate.args().length;
        }

        private String getString(int index) {
            return delegate.getString(index);
        }

        private int getInt(int index) {
            return delegate.getInt(index);
        }
    }
}
