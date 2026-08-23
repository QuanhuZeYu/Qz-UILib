package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;

/**
 * SEGMENTS 富文本绘制命令端到端契约测试(S1:UILib 核心扩展)。
 *
 * <p>覆盖:工厂契约(不可变拷贝/字号/空流拒绝)、translatedBy 身份保持、equals 身份语义、
 * 绘制引擎产出(段流优先于文本、TOP 对齐、字号透传)、PAINT 脏标记与 fragment 重建、
 * 回放器 → RecordingRenderBackend 的 offset 应用。</p>
 */
public class SceneSegmentsCommandTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer();
    private final SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
    private final ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);

    /** 构造两个不同颜色的段(用 font 层 TextStyle,全限定名避免与 scene TextStyle 撞名)。 */
    private static List<TextSegment> twoSegments(int colorA, int colorB) {
        club.heiqi.uilib.font.layout.TextStyle styleA = new club.heiqi.uilib.font.layout.TextStyle();
        styleA.setColor(colorA);
        club.heiqi.uilib.font.layout.TextStyle styleB = new club.heiqi.uilib.font.layout.TextStyle();
        styleB.setColor(colorB);
        List<TextSegment> list = new ArrayList<TextSegment>();
        list.add(new TextSegment("Hello ", styleA));
        list.add(new TextSegment("World", styleB));
        return list;
    }

    /** 在 root 下挂一行段流节点并布局,返回该行节点。 */
    private SceneNode addLine(SceneNode root, List<TextSegment> segments) {
        SceneNode line = new SceneNode();
        line.setSegments(segments);
        line.setFontSize(12);
        line.setPreferredWidth(120);
        line.setPreferredHeight(16);
        line.setTextVerticalAlign(TextVerticalAlign.TOP);
        root.appendChild(line);
        layoutEngine.layout(root, new Constraints(300));
        return line;
    }

    @Test
    public void shouldBuildSegmentsCommandWithImmutableCopyAndFontSize() {
        List<TextSegment> source = twoSegments(0xFFFF0000, 0xFF00FF00);
        PaintCommand cmd = PaintCommand.segments(source, 3, 5, 12);
        Assert.assertEquals("命令类型", PaintCommandType.SEGMENTS, cmd.getType());
        Assert.assertEquals("left", 3, cmd.getLeft());
        Assert.assertEquals("top", 5, cmd.getTop());
        Assert.assertEquals("基准字号", 12, cmd.getTextStyle().getFontSize());
        Assert.assertEquals("段数", 2, cmd.getSegments().size());
        // 按引用保存(契约:调用方传入不可变列表)——translatedBy/fragment 复用依赖身份保持
        Assert.assertSame("按引用保存", source, cmd.getSegments());
        // 空段流拒绝
        try {
            PaintCommand.segments(new ArrayList<TextSegment>(), 0, 0, 12);
            Assert.fail("空段流应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    @Test
    public void shouldTranslateSegmentsCommandKeepingIdentity() {
        PaintCommand cmd = PaintCommand.segments(twoSegments(1, 2), 10, 20, 12);
        PaintCommand moved = cmd.translatedBy(5, 7);
        Assert.assertEquals("left 平移", 15, moved.getLeft());
        Assert.assertEquals("top 平移", 27, moved.getTop());
        Assert.assertSame("段流保持引用身份(布局缓存复用语义)", cmd.getSegments(), moved.getSegments());
        Assert.assertSame("零偏移返回自身", cmd, cmd.translatedBy(0, 0));
    }

    @Test
    public void shouldCompareSegmentsByIdentity() {
        PaintCommand a = PaintCommand.segments(twoSegments(1, 2), 0, 0, 12);
        PaintCommand b = PaintCommand.segments(twoSegments(1, 2), 0, 0, 12);
        Assert.assertNotEquals("不同列表实例应不等(身份语义)", a, b);
        PaintCommand c = PaintCommand.segments(a.getSegments(), 0, 0, 12);
        Assert.assertEquals("同一列表实例应相等", a, c);
        Assert.assertEquals("hashCode 一致", a.hashCode(), c.hashCode());
    }

    @Test
    public void shouldPaintSegmentsNodeToSingleSegmentsCommand() {
        SceneNode root = new SceneNode();
        SceneNode line = addLine(root, twoSegments(0xFFFFFFFF, 0xFFAAAAAA));
        List<PaintCommand> commands = paintEngine.paint(root).getPlan().getCommands();
        Assert.assertEquals("命令数", 1, commands.size());
        PaintCommand cmd = commands.get(0);
        Assert.assertEquals("类型", PaintCommandType.SEGMENTS, cmd.getType());
        Assert.assertEquals("left(TOP 对齐/padding 0)", 0, cmd.getLeft());
        Assert.assertEquals("top", 0, cmd.getTop());
        Assert.assertEquals("基准字号透传", 12, cmd.getTextStyle().getFontSize());
        Assert.assertEquals("段数", 2, cmd.getSegments().size());
    }

    @Test
    public void shouldPreferSegmentsOverText() {
        SceneNode root = new SceneNode();
        SceneNode line = new SceneNode();
        line.setText("plain text 不应产出 TEXT 命令");
        line.setSegments(twoSegments(1, 2));
        line.setFontSize(12);
        line.setPreferredWidth(120);
        line.setPreferredHeight(16);
        line.setTextVerticalAlign(TextVerticalAlign.TOP);
        root.appendChild(line);
        layoutEngine.layout(root, new Constraints(300));
        List<PaintCommand> commands = paintEngine.paint(root).getPlan().getCommands();
        Assert.assertEquals("命令数", 1, commands.size());
        Assert.assertEquals("段流优先", PaintCommandType.SEGMENTS, commands.get(0).getType());
    }

    @Test
    public void shouldMarkPaintDirtyOnSegmentsChangeAndReuseOnEquivalentList() {
        SceneNode root = new SceneNode();
        List<TextSegment> first = twoSegments(1, 2);
        SceneNode line = addLine(root, first);
        paintEngine.paint(root);
        Object frag1 = line.getCachedPaint();
        Assert.assertNotNull("应有 fragment", frag1);
        // 元素身份等价的列表:去重不标脏 → 零重建
        line.setSegments(first);
        PaintResult r1 = paintEngine.paint(root);
        Assert.assertSame("等价列表复用 fragment", frag1, line.getCachedPaint());
        Assert.assertEquals("零重生成", 0, r1.getRegeneratedFragmentCount());
        // 新样式实例列表(淡出重建语义):PAINT 脏 → 重建
        line.setSegments(twoSegments(3, 4));
        PaintResult r2 = paintEngine.paint(root);
        Assert.assertNotSame("新段流重建 fragment", frag1, line.getCachedPaint());
        Assert.assertTrue("重生成计数 >= 1", r2.getRegeneratedFragmentCount() >= 1);
    }

    @Test
    public void shouldReplaySegmentsWithOffset() {
        PaintPlan plan = new PaintPlan();
        plan.addCommand(PaintCommand.segments(twoSegments(1, 2), 4, 6, 12));
        RecordingRenderBackend rec = new RecordingRenderBackend();
        new ScenePaintReplayer().replay(plan, rec, 30, 40);
        Assert.assertEquals("调用数", 1, rec.getCallCount());
        RecordingRenderBackend.RenderCall call = rec.getCall(0);
        Assert.assertEquals("方法名", "drawSegments", call.methodName());
        Assert.assertEquals("x+offset", 34, call.getInt(1));
        Assert.assertEquals("y+offset", 46, call.getInt(2));
        Assert.assertEquals("fontSize", 12, call.getInt(3));
        Assert.assertEquals("段流透传", 2, ((List<?>) call.args()[0]).size());
    }
}
