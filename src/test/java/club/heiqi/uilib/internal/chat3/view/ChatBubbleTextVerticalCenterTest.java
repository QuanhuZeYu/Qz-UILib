package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import net.minecraft.util.ChatComponentText;

/**
 * 气泡内文字垂直居中的守卫（2026-09-02 真机反馈「气泡内文字似乎有点偏下」）。
 *
 * <p>本缺陷有两层，两层都要锁，只修一层都会留下可见偏移：</p>
 * <ol>
 *   <li><b>双重 ascent</b>（下沉约一个 ascent，主因）：见
 *       {@code UiSegmentsBaselineAnchorTest}（源码锁）。该环节发生在 SEGMENTS 命令<b>之后</b>
 *       的回放里，paint 计划看不见，所以只能靠源码锁 + 下面 Test A 的生产节点锁合力。</li>
 *   <li><b>行距堆在一侧</b>（残余 2.5px）：段流节点钉的行框高 = 字号 + 行距，大于 em-box
 *       (= 字号)。{@code TextVerticalAlign.TOP} 会把整段行距留在文字<b>下方</b>，单行气泡
 *       看上去贴底；CENTER 才等价 CSS half-leading 的上下均分。</li>
 * </ol>
 *
 * <p><b>为什么必须有 Test A</b>：本类初版只有手搓气泡（Test B/C），反向对照
 * 「把 ChatMessageList 的段流节点改回 TOP」时全绿——手搓节点锁的是自己那份复刻，
 * 不是生产代码。虚断言（见 踩坑记录 同日第 3 条）的复发，故补生产构树锁。</p>
 */
public class ChatBubbleTextVerticalCenterTest {

    private static final long T0 = 1_700_000_000_000L;

    private final FixedTextMeasurer measurer = new FixedTextMeasurer();
    private final SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
    private final ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

    /**
     * A. 生产构树锁：ChatMessageList 真实产出的<b>每一个</b>段流节点都必须 CENTER。
     *
     * <p>全树扫描而不是只看气泡：组头名/时间/块公式与正文行同因（行框高 &gt; em-box），
     * 任何一处退回 TOP 都会让那一段文字偏下。</p>
     */
    @Test
    public void productionSegmentsNodesNeverUseTopAlign() {
        ChatSceneController controller = new ChatSceneController(
                new ChatLineLayouterAdapter(),
                new ChatSceneController.SelfNameProvider() {
                    @Override
                    public String selfName() {
                        return "Alex";
                    }
                },
                new ChatMessageList.SegmentParser() {
                    @Override
                    public List<TextSegment> parse(String text, int baseColor) {
                        club.heiqi.uilib.font.layout.TextStyle style =
                                new club.heiqi.uilib.font.layout.TextStyle();
                        style.setColor(baseColor);
                        List<TextSegment> list = new ArrayList<TextSegment>();
                        list.add(new TextSegment(text, style));
                        return list;
                    }
                });
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello world"), 1, T0));
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> second line here"), 2, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();

        List<SceneNode> segmentsNodes = new ArrayList<SceneNode>();
        collectSegmentsNodes(root, segmentsNodes);
        Assert.assertFalse("生产树里必须真的有段流节点（否则本锁空转）", segmentsNodes.isEmpty());

        int fontSize = ChatMarkdownSettings.getChatFontSizePx();
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        for (SceneNode node : segmentsNodes) {
            Assert.assertEquals("段流节点的行框高大于 em-box 时不得用 TOP（会把行距全堆到文字下方）: "
                            + node.getSegments(),
                    TextVerticalAlign.CENTER, node.getTextVerticalAlign());
            int pinned = node.getPreferredHeight();
            if (pinned > 0) {
                Assert.assertTrue("钉的行框高必须不小于字号，否则 CENTER 无意义：pinned=" + pinned
                                + " fontSize=" + fontSize + " lineHeight=" + lineHeight,
                        pinned >= fontSize);
            }
        }
    }

    /** 递归收集带段流的节点（含自身）。 */
    private static void collectSegmentsNodes(SceneNode node, List<SceneNode> out) {
        if (node.getSegments() != null && !node.getSegments().isEmpty()) {
            out.add(node);
        }
        for (SceneNode child : node.__getChildren()) {
            collectSegmentsNodes(child, out);
        }
    }

    /** 生产 ChatSceneController 需要的度量替身：固定 4px/字符。 */
    private static final class ChatLineLayouterAdapter
            implements club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter.Measure {
        @Override
        public float advance(String text, int fontSizePx) {
            return text.length() * 4.0F;
        }

        @Override
        public int epoch() {
            return 0;
        }
    }

    /** B. 居中算式锁：单行气泡的 em-box 在气泡内上下留白必须相等（±1px 取整误差）。 */
    @Test
    public void singleLineBubbleCentersEmBox() {
        int fontSize = ChatMarkdownSettings.getChatFontSizePx();
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        int paddingY = ChatMarkdownSettings.getBubblePaddingY();
        PaintCommand command = paintFirstSegments(fontSize, lineHeight, paddingY, 1);
        int bubbleHeight = 2 * paddingY + lineHeight;

        int top = command.getTop();
        int bottomGap = bubbleHeight - (top + fontSize);
        Assert.assertTrue("em-box 上下留白差必须 <=1px（上=" + top + " 下=" + bottomGap
                        + "，气泡高=" + bubbleHeight + "，字号=" + fontSize + "）",
                Math.abs(top - bottomGap) <= 1);
        Assert.assertTrue("上留白不得小于气泡 paddingY=" + paddingY + "，实际=" + top
                        + "（小于它说明文字溢出到 padding 之外，正是旧缺陷形态）",
                top >= paddingY);
    }

    /** C. 多行气泡：行距逐行等分，整块文字在气泡内居中。 */
    @Test
    public void multiLineBubbleKeepsUniformLeading() {
        int fontSize = ChatMarkdownSettings.getChatFontSizePx();
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        int paddingY = ChatMarkdownSettings.getBubblePaddingY();
        List<PaintCommand> commands = paintSegments(fontSize, lineHeight, paddingY, 3);
        Assert.assertEquals("应产出 3 条 SEGMENTS 命令", 3, commands.size());

        for (int i = 1; i < commands.size(); i++) {
            int step = commands.get(i).getTop() - commands.get(i - 1).getTop();
            Assert.assertEquals("相邻行必须严格等距（行距不能被某一行独吞）", lineHeight, step);
        }
        int first = commands.get(0).getTop();
        int blockBottom = commands.get(commands.size() - 1).getTop() + fontSize;
        int bottomGap = (2 * paddingY + 3 * lineHeight) - blockBottom;
        Assert.assertTrue("多行块上下留白差必须 <=1px（上=" + first + " 下=" + bottomGap + "）",
                Math.abs(first - bottomGap) <= 1);
    }

    /** 前置自检：行框高必须真的大于 em-box，否则 CENTER 是空转。 */
    @Test
    public void lineBoxIsTallerThanEmBoxSoCenteringIsMeaningful() {
        int fontSize = ChatMarkdownSettings.getChatFontSizePx();
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        Assert.assertTrue("行框高必须大于字号，CENTER 才有可分的行距（字号=" + fontSize
                        + " 行框=" + lineHeight + "）",
                lineHeight > fontSize);
    }

    private PaintCommand paintFirstSegments(int fontSize, int lineHeight, int paddingY, int lineCount) {
        List<PaintCommand> commands = paintSegments(fontSize, lineHeight, paddingY, lineCount);
        Assert.assertFalse("应至少产出 1 条 SEGMENTS 命令", commands.isEmpty());
        return commands.get(0);
    }

    /** 复刻 ChatMessageList 非 accent 气泡的节点形状（column + 对称 padding + 每行段流）。 */
    private List<PaintCommand> paintSegments(int fontSize, int lineHeight, int paddingY, int lineCount) {
        SceneNode root = new SceneNode();
        SceneNode bubble = SceneNode.column()
                .setPreferredWidth(160)
                .setPadding(paddingY, ChatMarkdownSettings.getBubblePaddingX(), paddingY,
                        ChatMarkdownSettings.getBubblePaddingX());
        root.appendChild(bubble);
        for (int i = 0; i < lineCount; i++) {
            club.heiqi.uilib.font.layout.TextStyle style = new club.heiqi.uilib.font.layout.TextStyle();
            style.setColor(0xFFFFFFFF);
            List<TextSegment> segments = new ArrayList<TextSegment>();
            segments.add(new TextSegment("hello world", style));
            SceneNode line = new SceneNode()
                    .setSegments(segments)
                    .setFontSize(fontSize)
                    .setTextVerticalAlign(TextVerticalAlign.CENTER)
                    .setPreferredHeight(Math.max(1, lineHeight));
            bubble.appendChild(line);
        }
        layoutEngine.layout(root, new Constraints(400));
        LayoutBox box = (LayoutBox) bubble.getCachedLayout();
        Assert.assertNotNull("气泡必须完成布局", box);

        PaintPlan plan = paintEngine.paint(root).getPlan();
        List<PaintCommand> commands = new ArrayList<PaintCommand>();
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.SEGMENTS) {
                commands.add(command);
            }
        }
        return commands;
    }
}
