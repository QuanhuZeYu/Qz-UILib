package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;

/**
 * ChatGeometry 契约测试:HUD 自底向上堆叠/左右对齐/系统居中/容器自上而下/命中检测。
 *
 * <p>度量 mock:等宽 4px/字符,§ 格式码对零宽;固定几何参数。</p>
 */
public class ChatGeometryTest {

    private static final int FONT = 13;
    private static final int LINE_H = 18;
    private static final int HEADER_FONT = 11;
    private static final int PAD_X = 10;
    private static final int PAD_Y = 6;
    private static final int INNER_GAP = 2;
    private static final int GROUP_GAP = 4;
    private static final int MARGIN = 10;
    private static final int WINDOW_W = 400;
    private static final int WINDOW_H = 300;
    private static final long NOW = 1_700_000_000_000L;

    private final ChatCardComposer composer = new ChatCardComposer(new ChatLineLayouter(fixedMeasure(), FONT));

    @Test
    public void hudStacksBottomUpWithLatestAtBottom() {
        List<ChatCardComposer.ComposedGroup> composed = composeTwoGroups();
        List<ChatGeometry.PositionedGroup> groups = ChatGeometry.layoutHud(composed, fixedGeometryMeasure(),
                WINDOW_W, WINDOW_H, MARGIN, PAD_X, PAD_Y, FONT, LINE_H, HEADER_FONT, INNER_GAP, GROUP_GAP);

        Assert.assertEquals(2, groups.size());
        ChatGeometry.PositionedGroup older = groups.get(0); // "hello"(旧)
        ChatGeometry.PositionedGroup newer = groups.get(1); // "longer message"(新)

        Assert.assertTrue("新组应在旧组下方", older.getY() < newer.getY());
        Assert.assertEquals("最新组底部钉在窗口底-边距", WINDOW_H - MARGIN, newer.getY() + newer.getHeight());
        Assert.assertEquals("组间间距", older.getY() + older.getHeight() + GROUP_GAP, newer.getY());
    }

    @Test
    public void hudAlignsLeftRightAndCenter() {
        // 他人组:左缘 = margin
        List<ChatLineRecord> others = Arrays.asList(
                new ChatLineRecord(new ChatComponentText("<Steve> hello"), 1, NOW - 1000));
        List<ChatCardComposer.ComposedGroup> composed = new ArrayList<ChatCardComposer.ComposedGroup>();
        composed.addAll(compose(others, "Alex"));

        // 自己组:右缘 = window - margin
        List<ChatLineRecord> self = Arrays.asList(
                new ChatLineRecord(new ChatComponentText("<Alex> me"), 2, NOW - 500));
        composed.addAll(compose(self, "Alex"));

        // 系统组:居中
        List<ChatLineRecord> system = Arrays.asList(
                new ChatLineRecord(new ChatComponentText("服务器已重启"), 3, NOW - 100));
        composed.addAll(compose(system, "Alex"));

        List<ChatGeometry.PositionedGroup> groups = ChatGeometry.layoutHud(composed, fixedGeometryMeasure(),
                WINDOW_W, WINDOW_H, MARGIN, PAD_X, PAD_Y, FONT, LINE_H, HEADER_FONT, INNER_GAP, GROUP_GAP);

        Assert.assertEquals(3, groups.size());
        ChatGeometry.PositionedGroup other = groups.get(0);
        ChatGeometry.PositionedGroup selfGroup = groups.get(1);
        ChatGeometry.PositionedGroup sys = groups.get(2);

        Assert.assertEquals("他人组左缘 = 边距", MARGIN, other.getX());
        Assert.assertEquals("自己组右缘 = 窗口宽-边距", WINDOW_W - MARGIN, selfGroup.getX() + selfGroup.getWidth());
        Assert.assertEquals("自己组 x 应大于他人组 x(靠右)", true, selfGroup.getX() > other.getX());
        // 系统组:文本宽 = 6 字符 * 4 = 24,居中于 [margin, window-margin]
        Assert.assertEquals("服务器已重启".length() * 4, sys.getWidth());
        int expectedCenterX = (MARGIN + WINDOW_W - MARGIN - sys.getWidth()) / 2;
        Assert.assertEquals(expectedCenterX, sys.getX());
    }

    @Test
    public void containerLaysOutTopDownFromZero() {
        List<ChatCardComposer.ComposedGroup> composed = composeTwoGroups();
        List<ChatGeometry.PositionedGroup> groups = ChatGeometry.layoutContainer(composed, fixedGeometryMeasure(),
                WINDOW_W, PAD_X, PAD_Y, FONT, LINE_H, HEADER_FONT, INNER_GAP, GROUP_GAP);

        Assert.assertEquals(2, groups.size());
        ChatGeometry.PositionedGroup older = groups.get(0);
        ChatGeometry.PositionedGroup newer = groups.get(1);
        Assert.assertEquals(0, older.getY());
        Assert.assertEquals(older.getHeight() + GROUP_GAP, newer.getY());
    }

    @Test
    public void hitTestReturnsRecordInsideMessageRect() {
        List<ChatCardComposer.ComposedGroup> composed = composeTwoGroups();
        List<ChatGeometry.PositionedGroup> groups = ChatGeometry.layoutHud(composed, fixedGeometryMeasure(),
                WINDOW_W, WINDOW_H, MARGIN, PAD_X, PAD_Y, FONT, LINE_H, HEADER_FONT, INNER_GAP, GROUP_GAP);

        ChatGeometry.PositionedMessage message = groups.get(1).getMessages().get(0);
        int px = message.getX() + 2;
        int py = message.getY() + 2;
        ChatLineRecord hit = ChatGeometry.hitTest(groups, px, py);
        Assert.assertNotNull(hit);
        Assert.assertEquals("longer message", hit.getPlainText().replaceFirst("^<[^>]+> ?", ""));

        Assert.assertNull("矩形外不应命中", ChatGeometry.hitTest(groups, 0, 0));
    }

    @Test
    public void bubbleWidthFollowsContentAndPadding() {
        List<ChatCardComposer.ComposedGroup> composed = composeTwoGroups();
        List<ChatGeometry.PositionedGroup> groups = ChatGeometry.layoutHud(composed, fixedGeometryMeasure(),
                WINDOW_W, WINDOW_H, MARGIN, PAD_X, PAD_Y, FONT, LINE_H, HEADER_FONT, INNER_GAP, GROUP_GAP);

        // "longer message" = 14 字符 * 4 = 56;组头 "Bob HH:mm" 宽 9 字符 = 36 → 组宽 = 内容 + 2*PAD_X
        ChatGeometry.PositionedGroup newer = groups.get(1);
        int contentW = 14 * 4;
        int headerW = ("Bob " + ChatClock.formatTime(NOW - 500)).length() * 4;
        Assert.assertEquals(Math.max(contentW, headerW) + 2 * PAD_X, newer.getWidth());
    }

    private List<ChatCardComposer.ComposedGroup> composeTwoGroups() {
        // 不同发送者 → 两个独立组(旧:Steve;新:Bob)
        List<ChatLineRecord> records = Arrays.asList(
                new ChatLineRecord(new ChatComponentText("<Bob> longer message"), 2, NOW - 500),
                new ChatLineRecord(new ChatComponentText("<Steve> hello"), 1, NOW - 1000));
        return compose(records, "Alex");
    }

    private List<ChatCardComposer.ComposedGroup> compose(List<ChatLineRecord> recordsNewestFirst, String selfName) {
        List<MessageGroupModel> groups = new MessageGrouper().group(recordsNewestFirst, selfName);
        List<ChatCardComposer.ComposedGroup> composed = new ArrayList<ChatCardComposer.ComposedGroup>();
        int maxLine = WINDOW_W - 2 * MARGIN - 2 * PAD_X;
        for (MessageGroupModel group : groups) {
            composed.add(composer.compose(group, NOW, maxLine, true));
        }
        return composed;
    }

    private static ChatLineLayouter.Measure fixedMeasure() {
        return new ChatLineLayouter.Measure() {
            @Override
            public float advance(String text, int fontSizePx) {
                int effective = 0;
                for (int i = 0; i < text.length(); i++) {
                    if (text.charAt(i) == '\u00a7' && i + 1 < text.length()) {
                        i++;
                        continue;
                    }
                    effective++;
                }
                return effective * 4;
            }

            @Override
            public int epoch() {
                return 0;
            }
        };
    }

    private static ChatGeometry.Measure fixedGeometryMeasure() {
        return new ChatGeometry.Measure() {
            @Override
            public float advance(String text, int fontSizePx) {
                int effective = 0;
                for (int i = 0; i < text.length(); i++) {
                    if (text.charAt(i) == '\u00a7' && i + 1 < text.length()) {
                        i++;
                        continue;
                    }
                    effective++;
                }
                return effective * 4;
            }
        };
    }
}
