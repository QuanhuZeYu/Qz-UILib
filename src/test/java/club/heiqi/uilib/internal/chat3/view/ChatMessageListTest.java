package club.heiqi.uilib.internal.chat3.view;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatHistory;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCardComposer;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCardComposer.ComposedGroup;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGrouper;
import club.heiqi.uilib.internal.chat3.viewmodel.SenderColorPalette;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGroupModel;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * ChatMessageList 契约测试:组 key 唯一性(T1)+ 组头双节点/圆角分级/accent 强调条(T4b,设计稿 §3.3/§6.1)。
 *
 * <p>防历史回归:原版 messageId 真机恒 0 不可用作身份;组 key 必须走
 * 进程内唯一序列号(sequenceId)。</p>
 */
public class ChatMessageListTest {

    private static final long T0 = 1_700_000_000_000L;

    private static final ChatLineLayouter.Measure FIXED = new ChatLineLayouter.Measure() {
        @Override
        public float advance(String text, int fontSizePx) {
            int effective = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '§' && i + 1 < text.length()) {
                    i++;
                    continue;
                }
                effective++;
            }
            return effective * 4.0F;
        }

        @Override
        public int epoch() {
            return 0;
        }
    };

    /** 段解析:支持 §l 粗体(模拟 TextLayoutService 的格式码语义,T4b 组头名字段断言用)。 */
    private static final ChatMessageList.SegmentParser PARSER = new ChatMessageList.SegmentParser() {
        @Override
        public List<TextSegment> parse(String text, int baseColor) {
            List<TextSegment> out = new java.util.ArrayList<TextSegment>();
            TextStyle style = new TextStyle();
            style.setColor(baseColor);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '§' && i + 1 < text.length()) {
                    char code = Character.toLowerCase(text.charAt(i + 1));
                    i++;
                    if (builder.length() > 0) {
                        out.add(new TextSegment(builder.toString(), style.copy()));
                        builder.setLength(0);
                    }
                    if (code == 'l') {
                        style.setFontType(FontType.BOLD);
                    } else if (code == 'r') {
                        style = new TextStyle();
                        style.setColor(baseColor);
                    }
                    continue;
                }
                builder.append(c);
            }
            if (builder.length() > 0) {
                out.add(new TextSegment(builder.toString(), style.copy()));
            }
            return out;
        }
    };

    private ComposedGroup compose(MessageGroupModel group) {
        ChatCardComposer composer = new ChatCardComposer(new ChatLineLayouter(FIXED,
                ChatMarkdownSettings.getChatFontSizePx()));
        return composer.compose(group, T0 + 60_000L, 1_000, true);
    }

    private static ChatSceneController controller() {
        return new ChatSceneController(FIXED, new ChatSceneController.SelfNameProvider() {
            @Override
            public String selfName() {
                return "Alex";
            }
        }, PARSER);
    }

    /** root → mount → list → 组节点列表(HUD 树)。 */
    private static List<SceneNode> hudGroups(SceneNode root) {
        SceneNode mount = root.__getChildren().get(0);
        SceneNode list = mount.__getChildren().get(0);
        return list.__getChildren();
    }

    private static void assertCorners(SceneNode node, int tl, int tr, int br, int bl) {
        Assert.assertEquals("左上圆角", tl, node.getCornerRadiusTopLeft());
        Assert.assertEquals("右上圆角", tr, node.getCornerRadiusTopRight());
        Assert.assertEquals("右下圆角", br, node.getCornerRadiusBottomRight());
        Assert.assertEquals("左下圆角", bl, node.getCornerRadiusBottomLeft());
    }

    // ==================== T1:组 key 唯一性 ====================

    @Test
    public void groupKeyIsUniqueWhenMessageIdIsAlwaysZero() {
        ChatHistory history = new ChatHistory();
        history.append(new ChatLineRecord(new ChatComponentText("<Alice> one"), 0, T0 + 1000));
        history.append(new ChatLineRecord(new ChatComponentText("<Bob> two"), 0, T0 + 2000));
        history.append(new ChatLineRecord(new ChatComponentText("<Carol> three"), 0, T0 + 3000));

        List<MessageGroupModel> groups = new MessageGrouper().group(history.snapshot(), "Alex");
        Assert.assertEquals(3, groups.size());
        Set<Long> keys = new HashSet<Long>();
        for (MessageGroupModel group : groups) {
            Long key = ChatMessageList.groupKey(compose(group));
            Assert.assertFalse("key 不得为 0(messageId 恒 0 回归点)", key.longValue() == 0L);
            keys.add(key);
        }
        Assert.assertEquals("3 组 key 必须互不相同", 3, keys.size());
    }

    @Test
    public void sequenceIdsAreAssignedPerAppend() {
        ChatHistory history = new ChatHistory();
        history.append(new ChatLineRecord(new ChatComponentText("<Alice> one"), 1, T0 + 1000));
        history.append(new ChatLineRecord(new ChatComponentText("<Bob> two"), 2, T0 + 2000));
        history.append(new ChatLineRecord(new ChatComponentText("<Carol> three"), 3, T0 + 3000));

        List<ChatLineRecord> snap = history.snapshot();
        Set<Long> seen = new HashSet<Long>();
        for (ChatLineRecord record : snap) {
            Assert.assertTrue("入史后序列号必须非 0", record.getSequenceId() > 0L);
            seen.add(Long.valueOf(record.getSequenceId()));
        }
        Assert.assertEquals("每条记录序列号唯一", 3, seen.size());
    }

    @Test
    public void groupKeyIsStableAcrossRecomposition() {
        ChatHistory history = new ChatHistory();
        history.append(new ChatLineRecord(new ChatComponentText("<Alice> hi"), 0, T0));
        history.append(new ChatLineRecord(new ChatComponentText("<Alice> there"), 0, T0 + 1000));

        List<MessageGroupModel> groups = new MessageGrouper().group(history.snapshot(), "Alex");
        Assert.assertEquals(1, groups.size());
        Long first = ChatMessageList.groupKey(compose(groups.get(0)));
        Long second = ChatMessageList.groupKey(compose(groups.get(0)));
        Assert.assertEquals("同内容组 key 稳定", first, second);
        Assert.assertTrue(first.longValue() > 0L);
    }

    @Test
    public void groupKeyChangesWhenGroupGrows() {
        ChatHistory history = new ChatHistory();
        history.append(new ChatLineRecord(new ChatComponentText("<Bob> first"), 0, T0));
        Long keyBefore = ChatMessageList.groupKey(compose(
                new MessageGrouper().group(history.snapshot(), "Alex").get(0)));

        history.append(new ChatLineRecord(new ChatComponentText("<Bob> second"), 0, T0 + 1000));
        List<MessageGroupModel> after = new MessageGrouper().group(history.snapshot(), "Alex");
        Assert.assertEquals("相邻同名仍并为一组", 1, after.size());
        Long keyAfter = ChatMessageList.groupKey(compose(after.get(0)));

        Assert.assertNotEquals("组增长 key 应变(行数入 key)", keyBefore, keyAfter);
    }

    // ==================== T4b:组头双节点 ====================

    @Test
    public void groupHeaderSplitsNameAndTimeNodes() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();

        Assert.assertEquals("他人组结构 = 组头 row + 气泡", 2, hudGroups(root).get(0).__getChildren().size());
        SceneNode headerRow = hudGroups(root).get(0).__getChildren().get(0);
        List<SceneNode> parts = headerRow.__getChildren();
        Assert.assertEquals("他人组头 = 名字节点 + 时间节点", 2, parts.size());

        SceneNode nameNode = parts.get(0);
        Assert.assertEquals("名字字号 font-name 12", ChatMarkdownSettings.getNameFontSizePx(),
                nameNode.getFontSize());
        List<TextSegment> nameSegs = nameNode.getSegments();
        Assert.assertEquals(1, nameSegs.size());
        Assert.assertEquals("Bob", nameSegs.get(0).getText());
        Assert.assertEquals("名字段加粗(§l 前缀)", FontType.BOLD, nameSegs.get(0).getStyle().getFontType());
        Assert.assertEquals("名字色 = 组内发送者配色", SenderColorPalette.colorFor("Bob"),
                nameSegs.get(0).getStyle().getColor());

        SceneNode timeNode = parts.get(1);
        Assert.assertEquals("时间戳字号 font-meta 10", ChatMarkdownSettings.getTimestampFontSizePx(),
                timeNode.getFontSize());
        Assert.assertEquals("时间戳色 = text-timestamp", ChatMarkdownSettings.getTimeTextArgb(),
                timeNode.getSegments().get(0).getStyle().getColor());
    }

    @Test
    public void groupHeaderNodesCarry16PxHeightAndLayOutInColumn() {
        // K3 缺陷 1:组头文本节点缺 preferredHeight → 行高塌 0,文本被气泡背景覆盖("幽影")
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));

        SceneNode group = hudGroups(root).get(0);
        SceneNode headerRow = group.__getChildren().get(0);
        SceneNode nameNode = headerRow.__getChildren().get(0);
        SceneNode timeNode = headerRow.__getChildren().get(1);
        Assert.assertEquals("名字节点钉组头行高(设计稿 §3.3 组头 16)", 16, nameNode.getPreferredHeight());
        Assert.assertEquals("时间节点钉组头行高", 16, timeNode.getPreferredHeight());

        LayoutBox headerBox = (LayoutBox) headerRow.getCachedLayout();
        Assert.assertEquals("组头行布局高 16(不再塌陷为 0)", 16, headerBox.getHeight());

        // headerRow 参与列布局:组高 = 组头 16 + 组头→首气泡 3(P3-3 两级 gap)+ 气泡(行高 18 + 上下 padding 5×2 = 28)
        LayoutBox groupBox = (LayoutBox) group.getCachedLayout();
        Assert.assertEquals("组头参与列布局,组高含组头行", 16 + 3 + 28, groupBox.getHeight());
        LayoutBox bubbleBox = (LayoutBox) group.__getChildren().get(1).getCachedLayout();
        Assert.assertTrue("气泡 y 在组头之下(组头不再被气泡覆盖)", bubbleBox.getY() >= 16);
    }

    @Test
    public void selfGroupHeaderHasTimeOnlyByDefault() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> hi"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();

        SceneNode headerRow = hudGroups(root).get(0).__getChildren().get(0);
        Assert.assertEquals("自己组 showSelfName=false → 无名字节点", 1, headerRow.__getChildren().size());
        Assert.assertEquals("仅时间节点(font-meta 10)", ChatMarkdownSettings.getTimestampFontSizePx(),
                headerRow.__getChildren().get(0).getFontSize());
    }

    // ==================== T4b:圆角分级 ====================

    @Test
    public void bubbleCornersFollowGroupPositionLadder() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> one"), 1, T0));
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> two"), 2, T0 + 1000));
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> three"), 3, T0 + 2000));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();

        List<SceneNode> groupChildren = hudGroups(root).get(0).__getChildren();
        Assert.assertEquals("三消息他人组结构 = 组头 + 3 气泡", 4, groupChildren.size());
        // 首消息:上两角 r-lg,下两角 r-inner
        assertCorners(groupChildren.get(1), 12, 12, 4, 4);
        // 中间消息:四角全 r-inner
        assertCorners(groupChildren.get(2), 4, 4, 4, 4);
        // 尾消息:上两角 r-inner,下两角 r-lg 但尾巴角(他人左下)保持 r-inner → (4,4,12,4)
        assertCorners(groupChildren.get(3), 4, 4, 12, 4);
    }

    @Test
    public void selfGroupTailKeepsBottomRightInnerCorner() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> one"), 1, T0));
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> two"), 2, T0 + 1000));
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> three"), 3, T0 + 2000));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();

        List<SceneNode> groupChildren = hudGroups(root).get(0).__getChildren();
        Assert.assertEquals("三消息自己组结构 = 组头 + 3 气泡", 4, groupChildren.size());
        assertCorners(groupChildren.get(1), 12, 12, 4, 4);
        assertCorners(groupChildren.get(2), 4, 4, 4, 4);
        // 尾消息:尾巴角在自己右下 → (4,4,4,12)
        assertCorners(groupChildren.get(3), 4, 4, 4, 12);
    }

    @Test
    public void singleMessageBubbleUsesUniformLgRadius() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> solo"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();

        List<SceneNode> groupChildren = hudGroups(root).get(0).__getChildren();
        assertCorners(groupChildren.get(1), 12, 12, 12, 12);
    }

    // ==================== T4b:方案A accent 强调条 ====================

    @Test
    public void selfAccentBubbleHasAccentBarInRow() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> hi"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();

        List<SceneNode> groupChildren = hudGroups(root).get(0).__getChildren();
        Assert.assertEquals("自己组结构 = 组头 + 气泡", 2, groupChildren.size());
        SceneNode bubble = groupChildren.get(1);
        List<SceneNode> row = bubble.__getChildren();
        Assert.assertEquals("accent 行结构 = 内容列 + 强调条", 2, row.size());
        Assert.assertTrue("内容列承载行段", row.get(0).__getChildren().size() >= 1);

        SceneNode accentBar = row.get(1);
        Assert.assertEquals("强调条宽 2px", 2, accentBar.getPreferredWidth());
        Assert.assertEquals("强调条背景 = accent-bar-self", ChatMarkdownSettings.getAccentBarSelfArgb(),
                accentBar.getBackgroundColor());
        Assert.assertEquals("强调条圆角 2", 2, accentBar.getCornerRadius());
        Assert.assertFalse("强调条不可命中", accentBar.isHitTestable());
    }

    @Test
    public void otherAndClassicBubblesHaveNoAccentBar() throws Exception {
        // 他人组:气泡保持 column,padding 在气泡自身,子节点全是行段(无强调条背景)
        ChatSceneController otherController = controller();
        otherController.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hi"), 1, T0));
        otherController.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode otherRoot = otherController.buildContent(rt);
        rt.flush();
        SceneNode otherBubble = hudGroups(otherRoot).get(0).__getChildren().get(1);
        Assert.assertTrue(otherBubble.__getChildren().size() >= 1);
        for (SceneNode child : otherBubble.__getChildren()) {
            Assert.assertEquals("他人气泡子节点(行段)不应有强调条背景", 0, child.getBackgroundColor());
        }

        // classic:临时注入 selfBubbleStyle=CLASSIC(静态配置,反射改 + finally 恢复),自己气泡同样无强调条
        Field field = ChatMarkdownSettings.class.getDeclaredField("selfBubbleStyle");
        field.setAccessible(true);
        Object previous = field.get(null);
        try {
            field.set(null, ChatMarkdownSettings.SelfBubbleStyle.CLASSIC);
            ChatSceneController classicController = controller();
            classicController.history().append(new ChatLineRecord(new ChatComponentText("<Alex> hi"), 1, T0));
            classicController.notifyDataChanged();
            SceneRuntime rtClassic = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode classicRoot = classicController.buildContent(rtClassic);
            rtClassic.flush();
            SceneNode classicBubble = hudGroups(classicRoot).get(0).__getChildren().get(1);
            for (SceneNode child : classicBubble.__getChildren()) {
                Assert.assertEquals("classic 自己气泡子节点(行段)不应有强调条背景", 0, child.getBackgroundColor());
            }
        } finally {
            field.set(null, previous);
        }
    }

    // ==================== K3 缺陷 2:自己气泡右对齐 + 按内容收缩 + accent 贴右内缘 ====================

    @Test
    public void selfGroupAlignsEndAndBubbleShrinksToContent() {
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300); // chatWidthFor(400) = 160
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> hi"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));

        SceneNode group = hudGroups(root).get(0);
        Assert.assertEquals("自己组交叉轴 END", AlignSelf.END, group.getAlignSelf());
        Assert.assertEquals("组节点 SHRINK 不被交叉轴拉伸吞掉",
                SceneNode.WidthSizing.SHRINK, group.getWidthSizing());

        SceneNode bubble = group.__getChildren().get(1);
        LayoutBox bubbleBox = (LayoutBox) bubble.getCachedLayout();
        // "hi" 2 码点 × 4px + padding 20 + accent 2 = 30 ≪ 视口宽 160(恒占 maxWidth 回归点)
        Assert.assertEquals("气泡按内容收缩(不再恒占 0.85 上限宽)", 30, bubbleBox.getWidth());

        LayoutBox groupBox = (LayoutBox) group.getCachedLayout();
        Assert.assertEquals("自己组右对齐:组右缘贴视口内容右缘", 160,
                groupBox.getX() + groupBox.getWidth());

        // accent 贴气泡右内缘:x + 宽 == 气泡宽(不再落右缘外侧 2px)
        SceneNode accentBar = bubble.__getChildren().get(1);
        LayoutBox accentBox = (LayoutBox) accentBar.getCachedLayout();
        Assert.assertEquals("强调条右缘 == 气泡右缘(贴右内缘)", bubbleBox.getWidth(),
                accentBox.getX() + accentBox.getWidth());
        Assert.assertEquals("强调条宽 2px", 2, accentBox.getWidth());
    }

    @Test
    public void otherGroupAlignsStartAndBubbleShrinksToContent() {
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));

        SceneNode group = hudGroups(root).get(0);
        Assert.assertEquals("他人组交叉轴 START", AlignSelf.START, group.getAlignSelf());
        SceneNode bubble = group.__getChildren().get(1);
        LayoutBox bubbleBox = (LayoutBox) bubble.getCachedLayout();
        // "hello" 5 码点 × 4px + padding 20 = 40
        Assert.assertEquals("他人气泡同样按内容收缩", 40, bubbleBox.getWidth());
        LayoutBox groupBox = (LayoutBox) group.getCachedLayout();
        Assert.assertEquals("他人组左对齐", 0, groupBox.getX());
    }

    @Test
    public void longSelfMessageClampsBubbleToMaxWidthAndKeepsAccentInside() {
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        // maxBubble = round((160 - 2×10) × 0.85) = 119;行切分宽 = 160-20 = 140(35 字符/行)
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Alex> " + longMessageBody()), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));

        SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);
        LayoutBox bubbleBox = (LayoutBox) bubble.getCachedLayout();
        Assert.assertEquals("长消息气泡钳到 0.85 上限宽", 119, bubbleBox.getWidth());
        // accent 仍在气泡右内缘;行节点宽 ≤ 气泡内可用宽(119-20-2=97),不溢出气泡
        SceneNode accentBar = bubble.__getChildren().get(1);
        LayoutBox accentBox = (LayoutBox) accentBar.getCachedLayout();
        Assert.assertEquals("长消息强调条右缘 == 气泡右缘", 119,
                accentBox.getX() + accentBox.getWidth());
        SceneNode firstLine = bubble.__getChildren().get(0).__getChildren().get(0);
        Assert.assertEquals("行节点钳到气泡内可用宽", 97,
                ((LayoutBox) firstLine.getCachedLayout()).getWidth());
    }

    @Test
    public void accentBarFadesWithGroupAlpha() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> hi"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        SceneNode accentBar = hudGroups(root).get(0).__getChildren().get(1).__getChildren().get(1);

        // 初始不透明(基础 alpha FF)
        Assert.assertEquals("强调条初始满 alpha", 0xFF, (accentBar.getBackgroundColor() >>> 24) & 0xFF);

        // HUD 淡出中段:easeInQuad p=0.5 → 淡出因子 191 → 强调条 alpha = floor(255×191/255) = 191
        controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis()
                + ChatMarkdownSettings.getHudFadeMillis() / 2);
        rt.flush();
        Assert.assertEquals("强调条随组淡出同步降 alpha", 0xBF,
                (accentBar.getBackgroundColor() >>> 24) & 0xFF);
    }

    // ==================== T6a:URL 链接化 + 链接 hover(设计稿 §3.5/§5.2) ====================

    /** 段宽度度量:每码点 4px,与 FIXED.advance 同口径(链接命中区域行内定位用)。 */
    private static final ChatMessageList.SegmentMeasurer FIXED_MEASURER =
            new ChatMessageList.SegmentMeasurer() {
                @Override
                public float widthOf(TextSegment segment, int fontSizePx) {
                    if (segment.isLatex()) {
                        return 0.0F;
                    }
                    return segment.getText().codePointCount(0, segment.getText().length()) * 4.0F;
                }
            };

    /** 链接化形态 controller(注入段宽度度量 → 启用 URL 自动链接)。 */
    private static ChatSceneController linkController() {
        return new ChatSceneController(FIXED,
                new ChatSceneController.SelfNameProvider() {
                    @Override
                    public String selfName() {
                        return "Alex";
                    }
                }, PARSER, FIXED_MEASURER);
    }

    // ==================== T8:单条消息 8 行截断(设计稿 §5.4,验收 22) + latex 段流后处理 ====================

    /** 320 字符:视口 400 → chatWidth=160 → maxLine=140(4px/字符 → 35 字符/行) → 10 行,超 8 行上限。 */
    private static String longMessageBody() {
        StringBuilder sb = new StringBuilder(320);
        for (int i = 0; i < 320; i++) {
            sb.append('x');
        }
        return sb.toString();
    }

    /** root → 组节点气泡的行节点列表(HUD 树,单消息他人组)。 */
    private static List<SceneNode> hudLineNodesOfFirstGroup(SceneNode root) {
        SceneNode group = hudGroups(root).get(0);
        SceneNode bubble = group.__getChildren().get(1);
        return bubble.__getChildren();
    }

    @Test
    public void hudLineNodesCarryMaxLinesAndEllipsisAndClampToEight() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Bob> " + longMessageBody()), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();

        List<SceneNode> lineNodes = hudLineNodesOfFirstGroup(root);
        Assert.assertEquals("HUD 单条消息 10 行截断为 8 行", 8, lineNodes.size());
        for (SceneNode lineNode : lineNodes) {
            Assert.assertEquals("HUD 行节点 maxLines=8", 8, lineNode.getMaxLines());
            Assert.assertTrue("HUD 行节点省略号语义开启", lineNode.isEllipsis());
        }
        // 末行段流文本以省略号收尾(截断发生在 L2 displayLines,段流原样携带)
        List<TextSegment> lastSegments = lineNodes.get(7).getSegments();
        Assert.assertEquals(1, lastSegments.size());
        Assert.assertTrue("末行以省略号收尾",
                lastSegments.get(0).getText().endsWith(ChatCardComposer.ELLIPSIS));
    }

    @Test
    public void containerLineNodesCarryNoClamp() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode list = SceneNode.column().setHitTestable(false);
        Map<SceneNode, ChatLineRecord> registry = new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
        ChatMessageList renderer = new ChatMessageList(PARSER);
        SceneListHandle handle = renderer.mount(rt, list, controller.groupsSignal(),
                ChatMessageList.Style.container(), registry, controller.frameMillisSignal());
        rt.flush();

        SceneNode group = list.__getChildren().get(0);
        SceneNode bubble = group.__getChildren().get(1);
        SceneNode lineNode = bubble.__getChildren().get(0);
        Assert.assertEquals("容器形态行节点不设 maxLines", 0, lineNode.getMaxLines());
        Assert.assertFalse("容器形态行节点无省略号", lineNode.isEllipsis());
        handle.dispose();
    }

    @Test
    public void lateXPostProcessorRunsInsideParseCached() {
        // 注入段流后处理(模拟 latex 行高约束产物:普通段 → latex 段 + 字号 11):
        // 证明 parseCached 后处理链(段解析 → 后处理 → 行节点段流)贯通。
        ChatMessageList.SegmentPostProcessor processor = new ChatMessageList.SegmentPostProcessor() {
            @Override
            public List<TextSegment> postProcess(List<TextSegment> segments, int baseFontSizePx) {
                List<TextSegment> out = new java.util.ArrayList<TextSegment>(segments.size());
                for (TextSegment segment : segments) {
                    if (segment.isLatex()) {
                        out.add(segment);
                        continue;
                    }
                    TextStyle style = segment.getStyle().copy();
                    style.setFontSizePx(11);
                    out.add(TextSegment.forLatex("x^2", style));
                }
                return out;
            }
        };
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Bob> formula"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode list = SceneNode.column().setHitTestable(false);
        Map<SceneNode, ChatLineRecord> registry = new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
        ChatMessageList renderer = new ChatMessageList(PARSER, null, processor);
        SceneListHandle handle = renderer.mount(rt, list, controller.groupsSignal(),
                ChatMessageList.Style.container(), registry, controller.frameMillisSignal());
        rt.flush();

        SceneNode group = list.__getChildren().get(0);
        SceneNode bubble = group.__getChildren().get(1);
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals(1, segments.size());
        Assert.assertTrue("后处理产物进入行节点段流", segments.get(0).isLatex());
        Assert.assertEquals(11, segments.get(0).getStyle().resolveEffectiveFontSizePx(13));
        handle.dispose();
    }

    /** 布局 + 提取单消息他人组:返回 [气泡节点, 首个行节点, 控制器, runtime, root]。 */
    private static Object[] layoutSingleOtherGroup(ChatSceneController controller) {
        // T7 回归:chatWidthFor 新增 <360 → 视口×0.5 窄屏分支后,未设置视口(=0)会得到 1px 根宽
        // 与 maxLine=1(逐字符折行),linkify/hover 全部失效;必须先注入视口再建树(与真机接线层时序一致)。
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> see http://a.co x"),
                1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));
        SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);
        SceneNode lineNode = bubble.__getChildren().get(0);
        return new Object[] { bubble, lineNode, controller, rt, root };
    }

    @Test
    public void renderedLineSegmentsAreUrlLinkifiedByDefault() {
        Object[] parts = layoutSingleOtherGroup(linkController());
        SceneNode lineNode = (SceneNode) parts[1];
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("前缀 + URL + 后缀三段", 3, segments.size());
        TextSegment link = segments.get(1);
        Assert.assertEquals("http://a.co", link.getStyle().getLink());
        Assert.assertEquals("链接默认色 text-link", ChatMarkdownSettings.getLinkArgb(),
                link.getStyle().getColor());
        Assert.assertFalse("链接默认无下划线(设计稿 §3.5)", link.getStyle().isUnderline());
        Assert.assertEquals("仅该行重解析(行文本 + hover 态缓存键)", "http://a.co", link.getText());
    }

    @Test
    public void linkHoverRebuildsOnlyHoveredLineWithBrightenedUnderlinedSegmentsAndHandCursor() {
        Object[] parts = layoutSingleOtherGroup(linkController());
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = (SceneNode) parts[1];
        ChatSceneController controller = (ChatSceneController) parts[2];
        SceneRuntime rt = (SceneRuntime) parts[3];

        ChatMessageList.LinkHoverDriver driver =
                controller.messageList().__linkHoverDriverOf(bubble);
        Assert.assertNotNull("含链接消息必须装配 hover 驱动器", driver);

        // 命中 URL 段中心(行内 x=10+16+24,y=行顶+9)→ 行 hover 目标置位 + 手型即时生效
        driver.onPointerMove(10 + 16 + 24, 5 + 9);
        Assert.assertEquals("手型光标", SceneCursor.POINTER, bubble.getCursor());
        Assert.assertTrue("仅该行 hover 置位", driver.lineHoveredForTest()[0]);

        // P2-4:80ms easeOutQuad 颜色插值——首帧锚定,elapsed=40 → easeOut(0.5)=0.75 中间态
        controller.tick(T0 + 80L);
        rt.flush();
        controller.tick(T0 + 120L);
        rt.flush();
        List<TextSegment> mid = lineNode.getSegments();
        Assert.assertEquals(3, mid.size());
        Assert.assertEquals("中间态 = 0.75 通道插值(≠两端色)", ChatCardComposer.interpolateArgb(
                ChatMarkdownSettings.getLinkArgb(), ChatMarkdownSettings.getLinkHoverArgb(), 0.75F),
                mid.get(1).getStyle().getColor());
        Assert.assertNotEquals("中间态不是默认色", ChatMarkdownSettings.getLinkArgb(),
                mid.get(1).getStyle().getColor());
        Assert.assertNotEquals("中间态不是终点色", ChatMarkdownSettings.getLinkHoverArgb(),
                mid.get(1).getStyle().getColor());
        Assert.assertTrue("hover 加下划线(样式位随目标态)", mid.get(1).getStyle().isUnderline());
        Assert.assertEquals("link 字段保留", "http://a.co", mid.get(1).getStyle().getLink());

        // 插值完成(elapsed=80)→ 终点 hover 色
        controller.tick(T0 + 160L);
        rt.flush();
        List<TextSegment> hover = lineNode.getSegments();
        Assert.assertEquals("hover 提亮色 text-link-hover", ChatMarkdownSettings.getLinkHoverArgb(),
                hover.get(1).getStyle().getColor());
        Assert.assertTrue("hover 加下划线", hover.get(1).getStyle().isUnderline());

        // 移到非链接区 → 目标复位;反向插值归零后恢复默认段流与光标(仅影响该行,零残留)
        driver.onPointerMove(10 + 2, 5 + 9);
        Assert.assertEquals(SceneCursor.DEFAULT, bubble.getCursor());
        Assert.assertFalse(driver.lineHoveredForTest()[0]);
        controller.tick(T0 + 240L);
        rt.flush();
        controller.tick(T0 + 320L);
        rt.flush();
        List<TextSegment> restored = lineNode.getSegments();
        Assert.assertEquals("link 段恢复默认色", ChatMarkdownSettings.getLinkArgb(),
                restored.get(1).getStyle().getColor());
        Assert.assertFalse("下划线移除", restored.get(1).getStyle().isUnderline());
    }

    @Test
    public void linkHoverClearsWhenPointerLeavesBubbleDirectly() {
        // 回归:指针从链接直接移出气泡(onPointerLeave,不走 onPointerMove 未命中路径)时,
        // lineHovered 必须清空,否则 bake 仍选 hoverBases → 行段流残留提亮 + 下划线。
        Object[] parts = layoutSingleOtherGroup(linkController());
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = (SceneNode) parts[1];
        ChatSceneController controller = (ChatSceneController) parts[2];
        SceneRuntime rt = (SceneRuntime) parts[3];
        ChatMessageList.LinkHoverDriver driver =
                controller.messageList().__linkHoverDriverOf(bubble);

        // 命中 URL 段中心(行内 x=10+16+24,y=行顶+9)→ 行 hover + 手型;插值 80ms 完成
        driver.onPointerMove(10 + 16 + 24, 5 + 9);
        Assert.assertTrue("行 hover 置位", driver.lineHoveredForTest()[0]);
        controller.tick(T0 + 80L);
        rt.flush();
        controller.tick(T0 + 160L);
        rt.flush();
        Assert.assertEquals("hover 提亮色生效", ChatMarkdownSettings.getLinkHoverArgb(),
                lineNode.getSegments().get(1).getStyle().getColor());

        // 指针直接移出气泡 → 目标清空;反向插值归零后行段流恢复默认色 + 光标复位 + hover 位清空
        driver.onPointerLeave();
        Assert.assertFalse("行 hover 清空", driver.lineHoveredForTest()[0]);
        controller.tick(T0 + 240L);
        rt.flush();
        controller.tick(T0 + 320L);
        rt.flush();
        List<TextSegment> restored = lineNode.getSegments();
        Assert.assertEquals("link 段恢复默认色", ChatMarkdownSettings.getLinkArgb(),
                restored.get(1).getStyle().getColor());
        Assert.assertFalse("下划线移除", restored.get(1).getStyle().isUnderline());
        Assert.assertEquals("link 字段保留(默认段流)", "http://a.co",
                restored.get(1).getStyle().getLink());
        Assert.assertEquals("气泡光标复位", SceneCursor.DEFAULT, bubble.getCursor());
    }

    @Test
    public void linkHitRegionExpandsTwoPxVerticalAndOnePxHorizontal() {
        Object[] parts = layoutSingleOtherGroup(linkController());
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = (SceneNode) parts[1];
        ChatSceneController controller = (ChatSceneController) parts[2];
        SceneRuntime rt = (SceneRuntime) parts[3];
        ChatMessageList.LinkHoverDriver driver =
                controller.messageList().__linkHoverDriverOf(bubble);

        // URL 段 = 行内 x[16, 16+44)("http://a.co" 11 码点 × 4px), 行盒相对气泡 = (10, 5)
        // 命中区 = [16-1, 16+44+1)
        // 命中判定探针改用 resolveUrl(P2-4 起颜色走插值,不再即时切换,几何语义同源)
        // 左扩 1px 命中;左扩 1px 之外不命中
        Assert.assertEquals("左扩 1px 命中", "http://a.co",
                driver.resolveUrl(10 + 16 - 1, 5 + 9));
        Assert.assertNull("左扩 1px 之外不命中", driver.resolveUrl(10 + 16 - 2, 5 + 9));
        // 右边界:右缘(不含)命中;右缘 +1 不命中
        Assert.assertEquals("右缘(不含)命中", "http://a.co",
                driver.resolveUrl(10 + 16 + 44, 5 + 9));
        Assert.assertNull("右缘外 1px 不命中", driver.resolveUrl(10 + 16 + 44 + 1, 5 + 9));
        // 上扩 2px:行顶 -2 命中; -3 不命中
        Assert.assertEquals("上扩 2px 命中", "http://a.co",
                driver.resolveUrl(10 + 40, 5 - 2));
        Assert.assertNull("上扩之外不命中", driver.resolveUrl(10 + 40, 5 - 3));
        // 下扩 2px:行底 +1 命中(行高 18,底 23); +2 不命中
        Assert.assertEquals("下扩 2px 命中", "http://a.co",
                driver.resolveUrl(10 + 40, 5 + 18 + 1));
        Assert.assertNull("下扩之外不命中", driver.resolveUrl(10 + 40, 5 + 18 + 2));
    }

    // ==================== K3 三轮:C 系统消息 font-system 12/16 + A2 系统行不钳宽 + B 系统链接 hover 清理 ====================

    @Test
    public void systemMessageLineNodesCarrySystemFontSizeAndLineHeight() {
        // 设计稿 §2.2/§3.4:font-system 12/16(真机实测系统消息误用 body 13/18);
        // 修复后系统行节点 fontSize=12、preferredHeight=16,气泡行节点保持 13/18
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("[公告] see http://a.co ok"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        SceneNode systemMessage = hudGroups(root).get(0).__getChildren().get(0);
        SceneNode lineNode = systemMessage.__getChildren().get(0);
        Assert.assertEquals("系统消息字号 font-system 12",
                ChatMarkdownSettings.getSystemFontSizePx(), lineNode.getFontSize());
        Assert.assertEquals("系统消息行高 16",
                ChatMarkdownSettings.getSystemLineHeightPx(), lineNode.getPreferredHeight());

        // 对照:他人气泡行节点保持 body 13/18
        ChatSceneController other = linkController();
        other.setHostViewport(400, 300);
        other.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hi"), 1, T0));
        other.notifyDataChanged();
        SceneRuntime rt2 = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode otherRoot = other.buildContent(rt2);
        rt2.flush();
        SceneNode bubbleLine = hudGroups(otherRoot).get(0).__getChildren().get(1)
                .__getChildren().get(0);
        Assert.assertEquals("气泡行字号保持 body 13", ChatMarkdownSettings.getChatFontSizePx(),
                bubbleLine.getFontSize());
        Assert.assertEquals("气泡行高保持 18", ChatMarkdownSettings.getChatLineHeightPx(),
                bubbleLine.getPreferredHeight());
    }

    @Test
    public void systemMessageLineWidthIsNotClampedToBubbleContentWidth() {
        // K3 摘要第 4 条:系统消息行 pinned width 被钳到 maxBubble−2×paddingX(=99@视口400),
        // 行实宽 140 却被钉 99 → 居中几何错位;修复后系统行钉实宽(气泡行钳宽语义不变)
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        // 无空格长串:字符级断行,首行 35 字符 × 4px = 140(不会被词边界回退打断)
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "[公告]" + longMessageBody()), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));
        SceneNode systemMessage = hudGroups(root).get(0).__getChildren().get(0);
        SceneNode lineNode = systemMessage.__getChildren().get(0);
        Assert.assertEquals("系统行钉实宽(不钳 99)", 140,
                ((LayoutBox) lineNode.getCachedLayout()).getWidth());
        Assert.assertEquals("系统组按实宽收缩居中", 140,
                ((LayoutBox) systemMessage.getCachedLayout()).getWidth());
    }

    @Test
    public void systemMessageLinkHoverClearsWhenPointerLeavesViaRouter() {
        // K3 三轮 B:系统消息无气泡 hover 绑定 → 指针离开后 lineHovered 残留,
        // URL 行 stuck hover(真机 L1 恒 hover 色 + 下划线);修复后 hovered=false 清驱动
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("[公告] see http://a.co ok"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));
        SceneNode systemMessage = hudGroups(root).get(0).__getChildren().get(0);
        SceneNode lineNode = systemMessage.__getChildren().get(0);
        ChatMessageList.LinkHoverDriver driver =
                controller.messageList().__linkHoverDriverOf(systemMessage);
        Assert.assertNotNull("含 URL 系统消息装配 hover 驱动器", driver);

        // 经输入路由命中链接(消息盒居中 x=(160-92)/2=34;链接行内 x=36..80)
        AnchorRect box = SceneGeometry.absoluteBox(systemMessage, 0, 0);
        movePointer(rt, root, box.getX() + 36 + 22, box.getY() + 9);
        Assert.assertEquals("手型光标", SceneCursor.POINTER, systemMessage.getCursor());
        // P2-4:链接提亮 80ms 插值完成后再断言终点色
        controller.tick(T0 + 80L);
        rt.flush();
        controller.tick(T0 + 160L);
        rt.flush();
        Assert.assertEquals("命中后 hover 提亮", ChatMarkdownSettings.getLinkHoverArgb(),
                lineNode.getSegments().get(1).getStyle().getColor());
        Assert.assertTrue("命中后加下划线", lineNode.getSegments().get(1).getStyle().isUnderline());

        // 指针移出消息节点(空白区)→ hovered=false → 驱动清理;反向插值归零后断言
        movePointer(rt, root, 200, 280);
        Assert.assertEquals("光标复位", SceneCursor.DEFAULT, systemMessage.getCursor());
        controller.tick(T0 + 240L);
        rt.flush();
        controller.tick(T0 + 320L);
        rt.flush();
        Assert.assertEquals("离开后恢复系统原色", ChatMarkdownSettings.getSystemTextArgb(),
                lineNode.getSegments().get(1).getStyle().getColor());
        Assert.assertFalse("离开后下划线移除",
                lineNode.getSegments().get(1).getStyle().isUnderline());
        Assert.assertFalse("行 hover 位清空(无 stuck)", driver.lineHoveredForTest()[0]);
    }

    @Test
    public void systemMessageWithoutUrlStillAssemblesNoLinkHoverDriver() {
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300); // T7 回归:未设视口 = 0 → 1px 根宽逐字符折行
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("[公告] 服务器将于 23:00 维护"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));
        SceneNode bubble = hudGroups(root).get(0).__getChildren().get(0);
        Assert.assertNull("无 URL 的系统消息仍不装配链接 hover",
                controller.messageList().__linkHoverDriverOf(bubble));
    }

    @Test
    public void systemMessageUrlIsLinkifiedWithOriginalColorAndHoverable() {
        // F5 用户拍板:系统消息裸 URL 也链接化(命中区 + hover/tooltip/cursor + 点击回投),
        // 颜色保留 URL 原 § 格式色(此处 = systemTextArgb),不强制 0xFF7AB8F5;气泡仍统一链接色。
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300); // T7 回归:未设视口 = 0 → 1px 根宽逐字符折行
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("[公告] see http://a.co ok"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));
        SceneNode bubble = hudGroups(root).get(0).__getChildren().get(0);
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("前缀 + URL + 后缀三段", 3, segments.size());
        Assert.assertEquals("http://a.co", segments.get(1).getStyle().getLink());
        Assert.assertEquals("URL 保留系统消息原色(非统一链接色)",
                ChatMarkdownSettings.getSystemTextArgb(), segments.get(1).getStyle().getColor());
        Assert.assertNotEquals("不强制 0xFF7AB8F5", ChatMarkdownSettings.getLinkArgb(),
                segments.get(1).getStyle().getColor());
        Assert.assertFalse("默认无下划线", segments.get(1).getStyle().isUnderline());

        // 命中区装配:系统消息含链接行同样建 hover 驱动器,命中 → 提亮 + 下划线 + 手型。
        // URL 行内起点 = "[公告] see " 9 码点 × 4px = 36,中心 x = 36 + 22 = 58。
        ChatMessageList.LinkHoverDriver driver = controller.messageList().__linkHoverDriverOf(bubble);
        Assert.assertNotNull("含 URL 的系统消息装配链接 hover 驱动器", driver);
        driver.onPointerMove(58, 9);
        Assert.assertEquals("手型光标", SceneCursor.POINTER, bubble.getCursor());
        // P2-4:80ms 插值完成后再断言终点色
        controller.tick(T0 + 80L);
        rt.flush();
        controller.tick(T0 + 160L);
        rt.flush();
        Assert.assertEquals("hover 提亮 + 下划线(命中反馈,与气泡一致)",
                ChatMarkdownSettings.getLinkHoverArgb(), lineNode.getSegments().get(1).getStyle().getColor());
        Assert.assertTrue(lineNode.getSegments().get(1).getStyle().isUnderline());
        driver.onPointerMove(2, 9);
        controller.tick(T0 + 240L);
        rt.flush();
        controller.tick(T0 + 320L);
        rt.flush();
        Assert.assertEquals("移出恢复系统消息原色", ChatMarkdownSettings.getSystemTextArgb(),
                lineNode.getSegments().get(1).getStyle().getColor());
    }

    // ==================== T6a:气泡 hover 3% 白叠加(设计稿 §2.1/§4.3,经真实输入链路) ====================

    private static void movePointer(SceneRuntime rt, SceneNode root, int x, int y) {
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE, x, y, SceneMouseButton.NONE,
                0, 0, 0, false, false, false, false, 1_000_000L));
        rt.route(root, builder.drainFrame(), 0, 0);
        rt.flush();
    }

    @Test
    public void bubbleHoverMixesWhiteBackgroundThroughInputRouter() {
        Object[] parts = layoutSingleOtherGroup(linkController());
        SceneNode bubble = (SceneNode) parts[0];
        ChatSceneController controller = (ChatSceneController) parts[2];
        SceneRuntime rt = (SceneRuntime) parts[3];
        SceneNode root = (SceneNode) parts[4];

        // 初始 = 他人气泡底色
        Assert.assertEquals("初始气泡底", ChatMarkdownSettings.getBubbleOtherArgb(),
                bubble.getBackgroundColor());

        // 移到气泡内左下角(链接区外)→ hover 目标置位;P2-4:100ms easeOutQuad 插值
        AnchorRect box = SceneGeometry.absoluteBox(bubble, 0, 0);
        movePointer(rt, root, box.getX() + 5, box.getY() + box.getHeight() - 5);
        // 首帧锚定 → elapsed=50 → easeOut(0.5)=0.75 中间态
        controller.tick(T0 + 100L);
        rt.flush();
        controller.tick(T0 + 150L);
        rt.flush();
        int mid = bubble.getBackgroundColor();
        Assert.assertEquals("中间态 = 0.75 通道插值", ChatCardComposer.interpolateArgb(
                ChatMarkdownSettings.getBubbleOtherArgb(),
                ChatCardComposer.hoveredBubbleColor(ChatMarkdownSettings.getBubbleOtherArgb()),
                0.75F), mid);
        Assert.assertNotEquals("中间态 ≠ 常态色", ChatMarkdownSettings.getBubbleOtherArgb(), mid);
        // 插值完成(elapsed=100)→ hover 底色(3% 白叠加,纯函数预计算常量)
        controller.tick(T0 + 200L);
        rt.flush();
        Assert.assertEquals("气泡 hover 底色(3% 白叠加)", 0xF22B3139, bubble.getBackgroundColor());

        // 移出气泡 → 反向插值归零后恢复
        movePointer(rt, root, 200, 280);
        controller.tick(T0 + 300L);
        rt.flush();
        controller.tick(T0 + 400L);
        rt.flush();
        Assert.assertEquals("移出后恢复", ChatMarkdownSettings.getBubbleOtherArgb(),
                bubble.getBackgroundColor());
    }

    @Test
    public void bubbleHoverColorAlsoFadesWithGroupAlpha() throws Exception {
        Object[] parts = layoutSingleOtherGroup(linkController());
        SceneNode bubble = (SceneNode) parts[0];
        ChatSceneController controller = (ChatSceneController) parts[2];
        SceneRuntime rt = (SceneRuntime) parts[3];
        SceneNode root = (SceneNode) parts[4];

        // 放大淡出窗口(反射改 + finally 恢复):P2-4 hover 插值需要帧推进,而 fade alpha
        // 随帧连续变化——fade=10000 下各 tick 时刻 alpha 精确可算(整数 floor 语义)
        Field fadeField = ChatMarkdownSettings.class.getDeclaredField("hudFadeMillis");
        fadeField.setAccessible(true);
        Object previousFade = fadeField.get(null);
        try {
            fadeField.set(null, Long.valueOf(10_000L));
            long ttl = ChatMarkdownSettings.getHudTtlMillis();
            // HUD 淡出中段:alpha = floor(255×(1-p²)) p=0.5 → 191
            controller.tick(T0 + ttl + 5000L);
            rt.flush();

            // 气泡 hover 目标置位:alpha=191 时 bake(progress 0)= fadeColor(基础色,191)= 0xB5242B33
            AnchorRect box = SceneGeometry.absoluteBox(bubble, 0, 0);
            movePointer(rt, root, box.getX() + 5, box.getY() + box.getHeight() - 5);
            Assert.assertEquals("hover 目标置位但 progress 0:正常色 × alpha191", 0xB5242B33,
                    bubble.getBackgroundColor());

            // 首帧锚定(alpha=188)
            controller.tick(T0 + ttl + 5100L);
            rt.flush();
            // 插值完成 elapsed=100(alpha=186):hover 色 × 淡出 alpha 组合
            controller.tick(T0 + ttl + 5200L);
            rt.flush();
            Assert.assertEquals("hover 色 × 淡出 alpha186 组合", 0xB02B3139,
                    bubble.getBackgroundColor());
            Assert.assertEquals("RGB 保留 hover 提亮分量", 0x2B3139,
                    bubble.getBackgroundColor() & 0xFFFFFF);

            // 移出 → 反向插值归零(alpha=180)后恢复淡出后的正常色
            movePointer(rt, root, 200, 280);
            controller.tick(T0 + ttl + 5300L);
            rt.flush();
            controller.tick(T0 + ttl + 5400L);
            rt.flush();
            Assert.assertEquals("淡出中移出恢复正常 bake(基础色 × alpha180)", 0xAA242B33,
                    bubble.getBackgroundColor());
        } finally {
            fadeField.set(null, previousFade);
        }
    }

    // ==================== T6b:行内 code + 引用行(设计稿 §3.5) ====================

    /** 布局 + 提取单消息他人组:返回 [气泡节点, 行节点序列, 控制器]。 */
    private static Object[] layoutSingleOtherBubble(ChatSceneController controller) {
        // 与 layoutSingleOtherGroup 同因:先注入视口再建树(T7 chatWidthFor 窄屏分支,
        // 未设视口 = 0 → 1px 根宽 + maxLine=1 逐字符折行,行/引用结构断言全崩)。
        controller.setHostViewport(400, 300);
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));
        SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);
        return new Object[] { bubble, root, rt };
    }

    @Test
    public void backtickPairsCarryCodeSpanStyleBits() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob> run `gradle build` now"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode lineNode = (SceneNode) parts[0];
        Assert.assertEquals("单行气泡", 1, lineNode.__getChildren().size());
        List<TextSegment> segments = lineNode.__getChildren().get(0).getSegments();
        Assert.assertEquals("前缀 + code + 后缀三段", 3, segments.size());
        Assert.assertEquals("run ", segments.get(0).getText());
        Assert.assertEquals("gradle build", segments.get(1).getText());
        Assert.assertTrue("code 段样式位", segments.get(1).getStyle().isCodeSpan());
        Assert.assertEquals("code 段衬底色(设计稿 bg-code)",
                ChatMarkdownSettings.getCodeBackgroundArgb(),
                segments.get(1).getStyle().getCodeBackgroundColor());
        Assert.assertFalse("前缀段非 code", segments.get(0).getStyle().isCodeSpan());
        Assert.assertFalse("后缀段非 code", segments.get(2).getStyle().isCodeSpan());
    }

    @Test
    public void quoteLineBuildsBarPlusTextRowWithSecondaryColor() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> > quoted"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        Assert.assertEquals("引用行 = row 容器", 1, bubble.__getChildren().size());
        SceneNode quoteRow = bubble.__getChildren().get(0);
        List<SceneNode> rowParts = quoteRow.__getChildren();
        Assert.assertEquals("引用行结构 = 竖条 + 文本双节点", 2, rowParts.size());

        SceneNode bar = rowParts.get(0);
        Assert.assertEquals("竖条宽 2px(设计稿 §3.5)", 2, bar.getPreferredWidth());
        Assert.assertEquals("竖条色 bar-quote", ChatMarkdownSettings.getQuoteBarArgb(),
                bar.getBackgroundColor());
        Assert.assertEquals("竖条圆角 1", 1, bar.getCornerRadius());
        Assert.assertTrue("竖条撑满行高(多行共享连续竖条)", bar.isFillParentHeight());
        Assert.assertFalse("竖条不可命中", bar.isHitTestable());
        Assert.assertEquals("竖条与文本间距 6px", 6, quoteRow.getGap());

        SceneNode textNode = rowParts.get(1);
        List<TextSegment> segments = textNode.getSegments();
        Assert.assertEquals("参照行前缀剥离", 1, segments.size());
        Assert.assertEquals("quoted", segments.get(0).getText());
        Assert.assertEquals("引用行文字降 text-secondary",
                ChatMarkdownSettings.getTextSecondaryArgb(), segments.get(0).getStyle().getColor());
    }

    @Test
    public void quoteLineWithoutSpaceAfterGtAlsoStripsPrefix() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> >bare"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode textNode = bubble.__getChildren().get(0).__getChildren().get(1);
        Assert.assertEquals("> 后无空格同样剥前缀", "bare",
                textNode.getSegments().get(0).getText());
    }

    @Test
    public void consecutiveQuoteLinesEachBuildOwnContiguousBar() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> > one\n> two"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        Assert.assertEquals("连续引用行为 2 个 row", 2, bubble.__getChildren().size());
        for (SceneNode row : bubble.__getChildren()) {
            List<SceneNode> rowParts = row.__getChildren();
            Assert.assertEquals("每行 = 竖条 + 文本", 2, rowParts.size());
            Assert.assertEquals("竖条色 bar-quote", ChatMarkdownSettings.getQuoteBarArgb(),
                    rowParts.get(0).getBackgroundColor());
        }
        // 块内多行共享连续竖条:相邻行行高 18px 无缝衔接即视觉连续(行间无 gap 不再额外处理)
        Assert.assertEquals("首行文本剥离 > 前缀", "one",
                bubble.__getChildren().get(0).__getChildren().get(1).getSegments().get(0).getText());
        Assert.assertEquals("次行文本剥离 > 前缀", "two",
                bubble.__getChildren().get(1).__getChildren().get(1).getSegments().get(0).getText());
    }

    @Test
    public void quoteLineTextStillParticipatesInLinkify() {
        ChatSceneController linkController = linkController();
        linkController.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob> > see http://a.co"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(linkController);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode textNode = bubble.__getChildren().get(0).__getChildren().get(1);
        List<TextSegment> segments = textNode.getSegments();
        Assert.assertTrue("引用行剥前缀后照常链接化",
                segments.size() >= 2 && segments.get(1).getStyle().getLink() != null);
        Assert.assertEquals("http://a.co", segments.get(1).getStyle().getLink());
    }

    @Test
    public void quoteLineInsideAccentBubbleKeepsRowLayout() {
        // 方案A accent 自己气泡:内容列内引用行保持 row[竖条 + 文本],强调条仍在行末
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> > own"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        List<SceneNode> row = bubble.__getChildren();
        Assert.assertEquals("accent 行 = 内容列 + 强调条", 2, row.size());
        SceneNode contentNode = row.get(0);
        Assert.assertEquals("内容列 = 引用 row", 1, contentNode.__getChildren().size());
        SceneNode quoteRow = contentNode.__getChildren().get(0);
        Assert.assertEquals("引用结构 = 竖条 + 文本", 2, quoteRow.__getChildren().size());
        Assert.assertEquals("竖条色 bar-quote", ChatMarkdownSettings.getQuoteBarArgb(),
                quoteRow.__getChildren().get(0).getBackgroundColor());
    }

    // ==================== P3-3:两级 gap(组头→首气泡 3 / 组内相邻 2) ====================

    @Test
    public void groupHeaderToBubbleGapIsThreeAndInnerGapIsTwo() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> one"), 1, T0));
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> two"), 2, T0 + 1000));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));

        SceneNode group = hudGroups(root).get(0);
        SceneNode headerRow = group.__getChildren().get(0);
        SceneNode firstBubble = group.__getChildren().get(1);
        SceneNode secondBubble = group.__getChildren().get(2);
        // margin 探针:组头下 margin 3、非首条消息上 margin 2(不再统一 setGap 2)
        Assert.assertEquals("组头与首气泡间距 sp-2=3(headerRow marginBottom)", 3,
                headerRow.getMarginBottom());
        Assert.assertEquals("首条消息无上 margin", 0, firstBubble.getMarginTop());
        Assert.assertEquals("组内相邻消息间距 sp-1=2(消息 marginTop)", 2, secondBubble.getMarginTop());

        // 布局几何:首气泡顶 = 组头底 + 3;次气泡顶 = 首气泡底 + 2
        LayoutBox headerBox = (LayoutBox) headerRow.getCachedLayout();
        LayoutBox firstBox = (LayoutBox) firstBubble.getCachedLayout();
        LayoutBox secondBox = (LayoutBox) secondBubble.getCachedLayout();
        Assert.assertEquals("首气泡顶 = 组头底 + 3", headerBox.getY() + headerBox.getHeight() + 3,
                firstBox.getY());
        Assert.assertEquals("次气泡顶 = 首气泡底 + 2", firstBox.getY() + firstBox.getHeight() + 2,
                secondBox.getY());
    }

    // ==================== P3-6:code 段 font-code 12px ====================

    @Test
    public void codeSegmentsCarryFontCodeTwelvePx() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob> run `gradle build` now"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("前缀 + code + 后缀三段", 3, segments.size());
        Assert.assertEquals("code 段字号 = font-code 12",
                ChatMarkdownSettings.getCodeFontSizePx(),
                segments.get(1).getStyle().resolveEffectiveFontSizePx(
                        ChatMarkdownSettings.getChatFontSizePx()));
        Assert.assertEquals("普通段回落正文 13", ChatMarkdownSettings.getChatFontSizePx(),
                segments.get(0).getStyle().resolveEffectiveFontSizePx(
                        ChatMarkdownSettings.getChatFontSizePx()));
        Assert.assertEquals("行节点字号保持正文 13", ChatMarkdownSettings.getChatFontSizePx(),
                lineNode.getFontSize());
    }

    // ==================== C 拍板:行级 markdown 规则(§3.5/§10.1) ====================

    @Test
    public void unorderedListLineRendersBulletPrefixWithBodyColor() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> - item"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("bullet + 内容两段", 2, segments.size());
        Assert.assertEquals("前缀渲染为「• 」", "• ", segments.get(0).getText());
        Assert.assertEquals("前缀用正文色", 0xFFFFFFFF, segments.get(0).getStyle().getColor());
        Assert.assertEquals("内容去标记", "item", segments.get(1).getText());
    }

    @Test
    public void unorderedListIndentMapsLeadingSpacesPerLevel() {
        ChatSceneController controller = controller();
        // 4 前导空格 = 2 级 → bullet 段前缀含 4 个空格(2 空格=1 级的简单映射)
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob>     - deep"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals(2, segments.size());
        Assert.assertEquals("4 前导空格 = 2 级缩进(每级 2 空格)", "    • ", segments.get(0).getText());
        Assert.assertEquals("deep", segments.get(1).getText());
    }

    @Test
    public void orderedListLineKeepsNumberAndIsUnchanged() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> 1. first"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("有序列表保留序号原样", 1, segments.size());
        Assert.assertEquals("1. first", segments.get(0).getText());
    }

    @Test
    public void blockMathLineRendersOwnLatexSegmentWithFourPxMargins() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> $$x^2$$"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("块级公式 = 单个 latex 段", 1, segments.size());
        Assert.assertTrue("latex 段", segments.get(0).isLatex());
        Assert.assertEquals("TeX 源剥 $$ 边界", "x^2", segments.get(0).getLatexSource());
        Assert.assertEquals("上下各 4px 间距(上 margin)", 4, lineNode.getMarginTop());
        Assert.assertEquals("上下各 4px 间距(下 margin)", 4, lineNode.getMarginBottom());
        Assert.assertEquals("左右无 margin(左对齐不居中)", 0, lineNode.getMarginLeft());
    }

    @Test
    public void inlineMathOnlyLineAlsoRendersAsBlockMath() {
        // "$...$" 独占行(整行恰好一对 $ 包裹)→ 同样按块级公式渲染
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> $x^2$"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        Assert.assertTrue("latex 段", lineNode.getSegments().get(0).isLatex());
        Assert.assertEquals("x^2", lineNode.getSegments().get(0).getLatexSource());
        Assert.assertEquals(4, lineNode.getMarginTop());
        Assert.assertEquals(4, lineNode.getMarginBottom());
    }

    @Test
    public void normalLinesAreUnaffectedByMarkdownRules() {
        ChatSceneController controller = controller();
        // 行首连字符但无空格 / 行内 $ 不独占 → 全部原样
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob> -not-list\nfoo $x$ bar"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        List<SceneNode> lineNodes = bubble.__getChildren();
        Assert.assertEquals("两行", 2, lineNodes.size());
        List<TextSegment> first = lineNodes.get(0).getSegments();
        Assert.assertEquals(1, first.size());
        Assert.assertEquals("-not-list", first.get(0).getText());
        List<TextSegment> second = lineNodes.get(1).getSegments();
        Assert.assertEquals(1, second.size());
        Assert.assertEquals("foo $x$ bar", second.get(0).getText());
    }

    @Test
    public void listRuleDoesNotApplyInsideCodeSpans() {
        // 行首反引号 → 不命中列表规则;code 段内文本不被行级规则触碰
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob> `- a` plain"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("code + 普通两段(无 bullet 前缀)", 2, segments.size());
        Assert.assertTrue("code 段", segments.get(0).getStyle().isCodeSpan());
        Assert.assertEquals("- a", segments.get(0).getText());
        Assert.assertEquals(" plain", segments.get(1).getText());
    }

    @Test
    public void listContentWithLinkStillLinkifies() {
        // 列表内容照常走 code 切分 + 链接化链路(bullet 段在前,链接命中区含 bullet 偏移)
        ChatSceneController linkController = linkController();
        linkController.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob> - see http://a.co"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(linkController);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("bullet + 前缀 + URL 三段", 3, segments.size());
        Assert.assertEquals("• ", segments.get(0).getText());
        Assert.assertEquals("see ", segments.get(1).getText());
        Assert.assertEquals("http://a.co", segments.get(2).getStyle().getLink());
        Assert.assertEquals("链接默认色 text-link", ChatMarkdownSettings.getLinkArgb(),
                segments.get(2).getStyle().getColor());
    }

    // ==================== C 拍板回归:真机同款行首颜色码(vision-exp 五轮截图) ====================

    /** 真机同款消息组件:原版 chat.type.text translation 形态,发送者与消息本体各带独立
     *  颜色码(§f&lt;Bob&gt; §f- item),去前缀后行首恒残留 §f。 */
    private static final class SiblingStyledComponent implements IChatComponent {

        private final String plain;
        private final String formatted;

        SiblingStyledComponent(String plain, String formatted) {
            this.plain = plain;
            this.formatted = formatted;
        }

        @Override
        public IChatComponent setChatStyle(ChatStyle style) {
            return this;
        }

        @Override
        public ChatStyle getChatStyle() {
            return null;
        }

        @Override
        public IChatComponent appendText(String text) {
            return this;
        }

        @Override
        public IChatComponent appendSibling(IChatComponent component) {
            return this;
        }

        @Override
        public String getUnformattedTextForChat() {
            return plain;
        }

        @Override
        public String getUnformattedText() {
            return plain;
        }

        @Override
        public String getFormattedText() {
            return formatted;
        }

        @Override
        public List<IChatComponent> getSiblings() {
            return Collections.emptyList();
        }

        @Override
        public IChatComponent createCopy() {
            return new SiblingStyledComponent(plain, formatted);
        }

        @Override
        public Iterator<IChatComponent> iterator() {
            return Collections.<IChatComponent>emptyList().iterator();
        }
    }

    @Test
    public void singleLineListMessageWithLeadingColorCodeRendersBullet() {
        // 真机同款:玩家消息(chat.type.text translation)去 "<名字> " 前缀后行首残留 §f,
        // classify 未剥行首格式码时行级规则全部失效,渲染字面 "- item"(vision-exp 截图回归)
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new SiblingStyledComponent(
                "<Bob> - item", "§f<Bob> §f- item"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("bullet + 内容两段", 2, segments.size());
        Assert.assertEquals("前缀渲染为「• 」", "• ", segments.get(0).getText());
        Assert.assertEquals("内容去标记", "item", segments.get(1).getText());
    }

    @Test
    public void singleLineBlockMathWithLeadingColorCodeRendersLatex() {
        // 真机同款:$$ 独占行行首残留 §f → 块级公式应照常走 LaTeX 渲染链
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new SiblingStyledComponent(
                "<Bob> $$x^2$$", "§f<Bob> §f$$x^2$$"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("块级公式 = 单个 latex 段", 1, segments.size());
        Assert.assertTrue("latex 段", segments.get(0).isLatex());
        Assert.assertEquals("TeX 源剥 $$ 边界", "x^2", segments.get(0).getLatexSource());
        Assert.assertEquals("上下各 4px 间距", 4, lineNode.getMarginTop());
        Assert.assertEquals("上下各 4px 间距", 4, lineNode.getMarginBottom());
    }

    @Test
    public void singleLineCodeWithLeadingColorCodeCarriesCodeSpan() {
        // 真机同款:单行 "`System.out.println(42)`" 行首残留 §f → 行内 code 切分照常,
        // code 段带衬底标记与 font-code 字号(§f 是零宽格式码,不进段文本)
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new SiblingStyledComponent(
                "<Bob> `System.out.println(42)`",
                "§f<Bob> §f`System.out.println(42)`"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("单段 code", 1, segments.size());
        Assert.assertTrue("code 段标记", segments.get(0).getStyle().isCodeSpan());
        Assert.assertEquals("code 内容", "System.out.println(42)", segments.get(0).getText());
        Assert.assertEquals("code 衬底色", ChatMarkdownSettings.getCodeBackgroundArgb(),
                segments.get(0).getStyle().getCodeBackgroundColor());
        Assert.assertEquals("code 段字号 font-code 12", ChatMarkdownSettings.getCodeFontSizePx(),
                segments.get(0).getStyle().resolveEffectiveFontSizePx(
                        ChatMarkdownSettings.getChatFontSizePx()));
    }

    @Test
    public void systemMessageLineRulesAreExcludedEvenWithLeadingColorCode() {
        // 系统消息不套行级规则(§3.5 排版规则仅作用于气泡内):文本行首为 "- " 也保持字面;
        // 系统组无组头,groupNode 子节点 = [messageNode](他人组为 [headerRow, messageNode])
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("- 系统公告"), 1, T0));
        controller.setHostViewport(400, 300);
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        SceneNode systemMessage = hudGroups(root).get(0).__getChildren().get(0);
        SceneNode lineNode = systemMessage.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("系统消息无 bullet 前缀", 1, segments.size());
        Assert.assertEquals("- 系统公告", segments.get(0).getText());
    }
}
