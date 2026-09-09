package club.heiqi.uilib.internal.chat3.view;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
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
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * ChatMessageList 契约测试:组 key 唯一性(T1)+ 组头双节点/圆角分级/accent 强调条(T4b,设计稿 §3.3/§6.1)。
 *
 * <p>防历史回归:原版 messageId 真机恒 0 不可用作身份;组 key 必须走
 * 进程内唯一序列号(sequenceId)。</p>
 */
public class ChatMessageListTest {

    /**
     * 本类断言的是「非玻璃态」下设计令牌正确落到节点，故显式关闭聊天玻璃。
     *
     * <p>玻璃默认开启会把气泡/输入底色换成半透明档（alpha 由 glass*Alpha 决定），
     * 与本类的令牌等值断言冲突。@After 复位避免静态开关污染同 JVM 其它测试。</p>
     */
    @Before
    public void disableChatGlassForDesignTokenAssertions() {
        ChatMarkdownSettings.setGlassEnabled(false);
    }

    @After
    public void restoreChatGlassDefault() {
        ChatMarkdownSettings.setGlassEnabled(true);
    }


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
    public void selfGroupBubblesAlignRightEdgesRegardlessOfWidth() {
        // 2026-08-29 真机取证:组内气泡按内容收缩(SHRINK)+ 父 crossAxisAlign 默认
        // STRETCH 且 SHRINK 豁免后 crossPos=0 左贴 → 同组多气泡右缘参差
        // (截图 accent 条 x=315/338/344)。修复:messageNode 显式 AlignSelf(align)。
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> hi"), 1, T0));
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Alex> a much longer message"), 1, T0 + 1000));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));

        SceneNode group = hudGroups(root).get(0);
        SceneNode shortBubble = group.__getChildren().get(1);
        SceneNode longBubble = group.__getChildren().get(2);
        LayoutBox shortBox = (LayoutBox) shortBubble.getCachedLayout();
        LayoutBox longBox = (LayoutBox) longBubble.getCachedLayout();
        Assert.assertTrue("短气泡与长气泡宽度不同(收缩语义保留)",
                shortBox.getWidth() < longBox.getWidth());
        Assert.assertEquals("两条气泡右缘对齐(贴组右缘)",
                longBox.getX() + longBox.getWidth(),
                shortBox.getX() + shortBox.getWidth());
        LayoutBox groupBox = (LayoutBox) group.getCachedLayout();
        // 气泡 getX 是组内坐标;组右缘 = 组 x(容器坐标) + 组宽 → 等效断言 = 气泡右缘贴组宽
        Assert.assertEquals("对齐基准 = 组宽(组右缘)", groupBox.getWidth(),
                shortBox.getX() + shortBox.getWidth());
        Assert.assertEquals("组贴视图内容右缘(160)", 160,
                groupBox.getX() + groupBox.getWidth());
        // accent 条(子 1)随气泡右移,仍贴气泡右内缘
        SceneNode shortAccent = shortBubble.__getChildren().get(1);
        LayoutBox shortAccentBox = (LayoutBox) shortAccent.getCachedLayout();
        Assert.assertEquals("短气泡强调条右缘 == 短气泡右缘", shortBox.getWidth(),
                shortAccentBox.getX() + shortAccentBox.getWidth());
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
        // TB1:常驻模式默认开启(无淡出);本测试验证旧 TTL 淡出行为 → 临时关闭常驻
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> hi"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = controller.buildContent(rt);
            rt.flush();
            SceneNode accentBar = hudGroups(root).get(0).__getChildren().get(1).__getChildren().get(1);

            // 初始不透明(基础 alpha FF)
            Assert.assertEquals("强调条初始满 alpha", 0xFF, (accentBar.getBackgroundColor() >>> 24) & 0xFF);

            // 新可见时钟驱动:首帧只锚定不累计,预算 12000 逐帧推进(帧间 delta ≤1000ms 夹取),
            // 12 帧 = 预算耗尽(p=0 仍满 255),再 +400ms 进入淡出 p=0.5 → 因子 191
            controller.tick(T0);
            for (int i = 1; i <= 12; i++) {
                controller.tick(T0 + 1000L * i);
                rt.flush();
            }
            controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis()
                    + ChatMarkdownSettings.getHudFadeMillis() / 2);
            rt.flush();
            Assert.assertEquals("强调条随组淡出同步降 alpha(p=0.5 因子 191)", 0xBF,
                    (accentBar.getBackgroundColor() >>> 24) & 0xFF);
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    // ==================== TB1:常驻模式(默认开启)关闭 TTL 淡出但保留 enter 动画 ====================

    @Test
    public void persistedModeSkipsTtlFadeButKeepsEnterAnim() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(true);
        try {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> hi"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        SceneNode group = hudGroups(root).get(0);
        SceneNode accentBar = group.__getChildren().get(1).__getChildren().get(1);

        // 出生 enter 动画保留(与 hudGroupEnterAnimatesOpacityAndTranslateY 同源):起点 opacity=0
        controller.tick(T0);
        rt.flush();
        Assert.assertEquals("常驻模式 enter 动画保留(起点 opacity=0)", 0.0F, group.getOpacity(), 0.001F);

        // 越过 TTL 淡出中段/结束:PAINT 级 fade 烘焙关闭,强调条 alpha 恒满(基础 FF,不随组淡出)
        controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis()
                + ChatMarkdownSettings.getHudFadeMillis() / 2);
        rt.flush();
        Assert.assertEquals("常驻模式:淡出中段强调条仍满 alpha", 0xFF,
                (accentBar.getBackgroundColor() >>> 24) & 0xFF);
        controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis()
                + ChatMarkdownSettings.getHudFadeMillis());
        rt.flush();
        Assert.assertEquals("常驻模式:淡出结束强调条仍满 alpha", 0xFF,
                (accentBar.getBackgroundColor() >>> 24) & 0xFF);
        // enter 动画完成归 1
        Assert.assertEquals("常驻模式 enter 动画完成 opacity=1", 1.0F, group.getOpacity(), 0.001F);
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    // ==================== HUD 显示时长机制:入场门控 + 可见时钟淡出 ====================

    /**
     * 新机制:仅组首次以 HUD 形态合成(isEnterOnMount=true)挂入场绑定;
     * 同组连发消息(组增长重建)后 isEnterOnMount=false → 不重播 enter,
     * 组节点恒稳态渲染(opacity=1)——修复连发消息整组闪烁。
     */
    @Test
    public void enterOnMountFalseGroupStaysSteadyWhenGroupGrows() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> one"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();

        // 首次 HUD 合成:isEnterOnMount=true → 入场绑定生效,出生帧 opacity=0
        controller.tick(T0);
        rt.flush();
        SceneNode first = hudGroups(root).get(0);
        Assert.assertEquals("首合组挂入场绑定(起点 opacity=0)", 0.0F, first.getOpacity(), 0.001F);
        controller.tick(T0 + ChatMarkdownSettings.getEnterAnimMillis());
        rt.flush();
        Assert.assertEquals("入场动画完成 opacity=1", 1.0F, first.getOpacity(), 0.001F);

        // 同发送者 + 相邻时间窗(≤120s)追加第二条 → 组 key 变化 → 整组重建(节点全新)
        long rebuildAt = T0 + ChatMarkdownSettings.getEnterAnimMillis() + 1000L;
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Alex> two"), 1, rebuildAt));
        controller.notifyDataChanged();
        controller.tick(rebuildAt);
        rt.flush();
        SceneNode rebuilt = hudGroups(root).get(0);
        Assert.assertNotSame("组增长 → 整组重建(新节点)", first, rebuilt);
        // 旧行为(无条件挂入场绑定)此刻 opacity=0;新机制门控跳过 → 恒 1(稳态,不重播)
        Assert.assertEquals("重建组 isEnterOnMount=false:不重播入场动画,opacity 恒 1", 1.0F,
                rebuilt.getOpacity(), 0.001F);

        // 基准继续推进:仍稳态,不回拉动画起点
        controller.tick(rebuildAt + ChatMarkdownSettings.getEnterAnimMillis());
        rt.flush();
        Assert.assertEquals("后续帧仍稳态 opacity=1", 1.0F, rebuilt.getOpacity(), 0.001F);
    }

    /**
     * 新机制:hudVisible 注入时 HUD 淡出 alpha 走 {@link ChatCardComposer#hudAlpha}
     * (每条消息显示预算 + 可见起点,仅 HUD 实际渲染时按可见时钟消耗):预算内恒满 →
     * 淡出中段与纯函数逐位一致 → 预算+淡出窗结束归零;入场门控与时钟注入互不影响。
     */
    @Test
    public void hudVisibleInjectionDrivesFadeThroughHudAlpha() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> hi"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            // 注入可见时钟(生产侧由 TC 维护,每帧仅值变化时 set;测试手动推进,
            // 初始 0 = 可见时钟起点,与控制器侧生命周期登记的起点同坐标系)
            Signal<Long> hudVisible = Signal.create(Long.valueOf(0L));
            SceneNode list = SceneNode.column().setHitTestable(false);
            Map<SceneNode, ChatLineRecord> registry =
                    new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
            SceneListHandle handle = controller.messageList().mount(rt, list,
                    controller.groupsSignal(), ChatMessageList.Style.hud(), registry,
                    controller.frameMillisSignal(), hudVisible);
            controller.tick(T0);
            rt.flush();

            SceneNode group = list.__getChildren().get(0);
            // 首合:入场绑定照常挂(isEnterOnMount 门控与时钟注入互不影响)
            Assert.assertEquals("首合组入场绑定仍生效(起点 opacity=0)", 0.0F,
                    group.getOpacity(), 0.001F);
            // 合成组冻结接口:显示预算与可见起点(渲染闭包与信号缓存同一组实例)
            ComposedGroup composed = controller.groupsSignal().get().get(0);
            long start = composed.getHudVisibleStartMillis();
            long budget = composed.getBudgetMillis();
            Assert.assertTrue("首合已登记可见起点(非 -1)", start >= 0L);
            long fade = ChatMarkdownSettings.getHudFadeMillis();
            SceneNode accentBar = group.__getChildren().get(1).__getChildren().get(1);

            // 预算内:alpha 恒满(预算未消耗)
            hudVisible.set(Long.valueOf(start));
            rt.flush();
            Assert.assertEquals("预算内 alpha 恒 255(未消耗)", 0xFF,
                    (accentBar.getBackgroundColor() >>> 24) & 0xFF);

            // 淡出中段:与 hudAlpha 纯函数(同源契约)逐位一致,且确实低于满值(递减)
            long midVisible = start + budget + fade / 2L;
            int expectedMid = ChatCardComposer.hudAlpha(start, budget, fade, midVisible);
            Assert.assertTrue("淡出中段 alpha 应递减(<255)", expectedMid < 0xFF);
            hudVisible.set(Long.valueOf(midVisible));
            rt.flush();
            Assert.assertEquals("淡出中段 alpha == hudAlpha 纯函数", expectedMid,
                    (accentBar.getBackgroundColor() >>> 24) & 0xFF);

            // 预算 + 淡出窗耗尽:归零
            hudVisible.set(Long.valueOf(start + budget + fade));
            rt.flush();
            Assert.assertEquals("预算+淡出窗结束 alpha=0", 0,
                    (accentBar.getBackgroundColor() >>> 24) & 0xFF);
            handle.dispose();
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
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

    /**
     * M5 视觉行换行替身:与 {@link #FIXED}(4px/码点)同度量的确定性段流换行——
     * {@code \n} 硬断、F6 空文本占位段产空行、超宽逐码点硬断、行尾空白丢弃。
     * 保持「composer 切行宽 == 渲染换行宽 == 命中/钳宽度量」的测试内同源前提
     * (生产路 = MarkdownPainter.wrapLines + FontService 度量同源,不注入本替身)。
     */
    private static final ChatMessageList.SegmentFlowWrapper FIXED_WRAP =
            new ChatMessageList.SegmentFlowWrapper() {
                @Override
                public List<List<TextSegment>> wrap(List<TextSegment> flat, int maxWidthPx,
                        int fontSizePx) {
                    List<List<TextSegment>> lines = new ArrayList<List<TextSegment>>();
                    List<TextSegment> current = new ArrayList<TextSegment>();
                    double limit = maxWidthPx <= 0 ? Double.MAX_VALUE : maxWidthPx;
                    double width = 0.0D;
                    for (TextSegment segment : flat) {
                        String text = segment.getText();
                        if (segment.isLatex()) {
                            current.add(segment);
                            continue;
                        }
                        if (text.isEmpty()) {
                            // F6 块边界占位段:先收当前行,再产一个空显示行
                            width = flushLine(lines, current, width);
                            List<TextSegment> blank = new ArrayList<TextSegment>();
                            blank.add(segment);
                            lines.add(blank);
                            continue;
                        }
                        if ("\n".equals(text)) {
                            width = flushLine(lines, current, width);
                            continue;
                        }
                        for (int i = 0; i < text.length(); i++) {
                            char ch = text.charAt(i);
                            if (ch == '\n') {
                                // 段文本内嵌软/硬换行(L1 段落 joinedLines)同样硬断,与 L2 口径一致
                                width = flushLine(lines, current, width);
                                continue;
                            }
                            double cw = 4.0D;
                            if (width + cw > limit && !current.isEmpty()) {
                                width = flushLine(lines, current, width);
                            }
                            appendChar(current, ch, segment.getStyle());
                            width += cw;
                        }
                    }
                    flushLine(lines, current, width);
                    if (lines.isEmpty()) {
                        lines.add(new ArrayList<TextSegment>());
                    }
                    List<List<TextSegment>> out = new ArrayList<List<TextSegment>>(lines.size());
                    for (List<TextSegment> line : lines) {
                        out.add(Collections.unmodifiableList(line));
                    }
                    return Collections.unmodifiableList(out);
                }

                /** 逐字符并入行尾段(样式同引用即合并,新样式开新段)。 */
                private void appendChar(List<TextSegment> line, char ch, TextStyle style) {
                    if (!line.isEmpty()) {
                        TextSegment last = line.get(line.size() - 1);
                        if (last.getStyle() == style) {
                            line.set(line.size() - 1, new TextSegment(last.getText() + ch, style));
                            return;
                        }
                    }
                    line.add(new TextSegment(String.valueOf(ch), style));
                }

                /** 收行:行尾空白丢弃;空行不产(首行除外由调用端兜)。返回 0(新行宽度)。 */
                private double flushLine(List<List<TextSegment>> lines, List<TextSegment> current,
                        double width) {
                    if (width <= 0.0D && current.isEmpty()) {
                        return 0.0D;
                    }
                    while (!current.isEmpty()) {
                        TextSegment last = current.get(current.size() - 1);
                        String trimmed = rtrim(last.getText());
                        if (trimmed.isEmpty()) {
                            current.remove(current.size() - 1);
                            continue;
                        }
                        if (trimmed.length() != last.getText().length()) {
                            current.set(current.size() - 1, new TextSegment(trimmed, last.getStyle()));
                        }
                        break;
                    }
                    lines.add(new ArrayList<TextSegment>(current));
                    current.clear();
                    return 0.0D;
                }

                private String rtrim(String s) {
                    int end = s.length();
                    while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
                        end--;
                    }
                    return s.substring(0, end);
                }
            };

    /** 链接化形态 controller(注入段宽度度量 → 启用 URL 自动链接;M5 另注入同源度量换行)。 */
    private static ChatSceneController linkController() {
        return new ChatSceneController(FIXED,
                new ChatSceneController.SelfNameProvider() {
                    @Override
                    public String selfName() {
                        return "Alex";
                    }
                }, PARSER, FIXED_MEASURER, FIXED_WRAP);
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


    /**
     * 玩家气泡接缝门：T3b 仍维持字面。快照来自 6c7637e512d7d2ce5a641b4d819580f730c23357，
     * 捕获真实 printMarkdown / chat.type.text → composer → pipeline → 消息节点。
     * FIXED_WRAP 仅用于冻结确定性历史输出；另测不注换行替身的真实 L2 路径。
     * 不以当前 toLayoutLines 或当前 pipeline 计算期望，不提供运行时更新快照开关。
     */
    // T3a 显式表格转由 ChatMarkdownTableConsumerTest 验收；两份 true_* 快照只保留历史证据。
    @Test
    public void explicitTableHistoricalEvidenceRemainsFrozen() throws Exception {
        String[][] evidence = {
                {"table-literal-true_true.snapshot", "9e533da55e5ba5ffed4769d362d11335b720d49c57706ef74606fc31f46b6fdc"},
                {"table-literal-true_false.snapshot", "8323f443d3a2f7af19e6bcd0aac1dfcaee178f93fea7a8d8655bc3ca62e182ee"}
        };
        for (String[] item : evidence) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream input = ChatMessageListTest.class.getResourceAsStream(item[0])) {
                Assert.assertNotNull("历史证据不得删除: " + item[0], input);
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            }
            StringBuilder actual = new StringBuilder();
            for (byte value : digest.digest()) actual.append(String.format("%02x", value & 0xff));
            Assert.assertEquals("历史证据不得重录: " + item[0], item[1], actual.toString());
        }
    }

    @Test
    public void tablePlayerBubbleKeepsHistoricalLiteralHudAndContainer() throws Exception {
        assertTableLiteralHistory(false);
    }

    private static String tableLockSource() {
        StringBuilder source = new StringBuilder("| A | B |\n| --- | --- |\n");
        for (int row = 0; row < 12; row++) {
            source.append("| **row").append(row).append("** | ");
            for (int n = 0; n < 20; n++) {
                source.append("word ");
            }
            source.append("|\n");
        }
        return source.append("\nafter-table").toString();
    }

    private static void assertTableLiteralHistory(boolean explicit) throws Exception {
        String source = tableLockSource();
        List<club.heiqi.uilib.font.layout.markdown.MarkdownTableModel> tables =
                club.heiqi.uilib.font.layout.markdown.MarkdownDocument.parse(source)
                        .toTableModels(new TextStyle());
        Assert.assertEquals("反空跑：样本必须被识别为一个 TABLE", 1, tables.size());
        Assert.assertEquals("反空跑：表头两列", 2, tables.get(0).getHeader().getCells().size());
        Assert.assertEquals("反空跑：十二个数据行属于 TABLE", 12, tables.get(0).getRows().size());
        for (boolean hud : new boolean[] {true, false}) {
            SceneNode message = tableConsumerMessage(source, explicit, hud, true);
            String snapshot = tableNodeSnapshot(message);
            String resource = "table-literal-" + explicit + "_" + hud + ".snapshot";
            try (java.io.InputStream input = ChatMessageListTest.class.getResourceAsStream(resource)) {
                Assert.assertNotNull("固定历史快照不可缺席: " + resource, input);
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    bytes.write(buffer, 0, count);
                }
                Assert.assertEquals("历史消费者可见输出必须逐字节等价: " + resource,
                        new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8), snapshot);
            }
            assertLiteralTableFloor(message, hud);
        }
    }

    @Test
    public void tablePlayerBubbleKeepsLiteralOutputThroughRealL2() throws Exception {
        for (boolean explicit : new boolean[] {false}) {
            SceneNode hud = tableConsumerMessage(tableLockSource(), explicit, true, false);
            SceneNode container = tableConsumerMessage(tableLockSource(), explicit, false, false);
            assertLiteralTableFloor(hud, true);
            assertLiteralTableFloor(container, false);
            Assert.assertTrue("窄列必须实际软折（超出源物理行数）",
                    container.__getChildren().size() > tableLockSource().split("\n").length);
        }
    }

    private static void assertLiteralTableFloor(SceneNode message, boolean hud) {
        List<SceneNode> rows = message.__getChildren();
        StringBuilder all = new StringBuilder();
        for (SceneNode row : rows) {
            if (row.getSegments() != null) {
                for (TextSegment segment : row.getSegments()) {
                    all.append(segment.getText());
                }
            }
        }
        Assert.assertTrue("表头 pipe 必须仍是字面", all.toString().contains("| A | B |"));
        Assert.assertTrue("delimiter 不可被 TABLE 布局吞掉", all.toString().contains("| --- | --- |"));
        if (hud) {
            Assert.assertEquals("真实消费者 HUD 八行预算", ChatCardComposer.HUD_MAX_LINES, rows.size());
            Assert.assertTrue("跨预算末行省略", all.toString().endsWith(ChatCardComposer.ELLIPSIS));
            Assert.assertFalse("HUD 必须真截断尾文", all.toString().contains("after-table"));
        } else {
            Assert.assertTrue("展开容器保留完整尾文", all.toString().endsWith("after-table"));
            Assert.assertTrue("展开容器超过 HUD 预算", rows.size() > ChatCardComposer.HUD_MAX_LINES);
            for (SceneNode row : rows) {
                Assert.assertEquals(0, row.getMaxLines());
                Assert.assertFalse(row.isEllipsis());
            }
        }
    }

    private static SceneNode tableConsumerMessage(String source, boolean explicit, boolean hud,
            boolean fixedWrap) throws Exception {
        final ChatSceneController controller = fixedWrap ? linkController()
                : new ChatSceneController(FIXED, selfAlex(), PARSER,
                        ChatSceneController.uiLibSegmentMeasurer());
        controller.setHostViewport(400, 300);
        if (explicit) {
            club.heiqi.uilib.api.chat.ChatAccess access = club.heiqi.uilib.api.chat.ChatAccess.getInstance();
            Field sinkField = access.getClass().getDeclaredField("markdownSink");
            sinkField.setAccessible(true);
            Object previous = sinkField.get(access);
            access.setMarkdownSink(component -> controller.history().append(new ChatLineRecord(component, 1, T0)));
            try {
                access.printMarkdown(source);
            } finally {
                sinkField.set(access, previous);
            }
        } else {
            controller.history().append(new ChatLineRecord(new net.minecraft.util.ChatComponentTranslation(
                    "chat.type.text", new Object[] {new ChatComponentText("Bob"), new ChatComponentText(source)}),
                    1, T0));
        }
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode group;
        SceneListHandle handle = null;
        if (hud) {
            SceneNode root = controller.buildContent(rt);
            rt.flush();
            group = hudGroups(root).get(0);
        } else {
            SceneNode list = SceneNode.column();
            ChatMessageList renderer = new ChatMessageList(PARSER,
                    fixedWrap ? FIXED_MEASURER : ChatSceneController.uiLibSegmentMeasurer(),
                    null, fixedWrap ? FIXED_WRAP : null);
            renderer.setBubbleMaxWidthPx(ChatSceneController.bubbleMaxWidthPxFor(400));
            handle = renderer.mount(rt, list, controller.groupsSignal(), ChatMessageList.Style.container(),
                    new java.util.IdentityHashMap<SceneNode, ChatLineRecord>(), controller.frameMillisSignal());
            rt.flush();
            group = list.__getChildren().get(0);
        }
        Assert.assertEquals("两路组头形状独立", explicit ? 1 : 2, group.__getChildren().size());
        SceneNode message = group.__getChildren().get(explicit ? 0 : 1);
        Assert.assertEquals("printMarkdown 无气泡；玩家保留气泡内衬", explicit ? 0
                : ChatMarkdownSettings.getBubblePaddingX(), message.getPaddingLeft());
        if (handle != null) {
            handle.dispose();
        }
        return message;
    }

    private static String tableNodeSnapshot(SceneNode node) throws Exception {
        StringBuilder out = new StringBuilder();
        tableNodeSnapshot(node, out, 0);
        return out.toString();
    }

    private static void tableNodeSnapshot(SceneNode node, StringBuilder out, int depth) throws Exception {
        out.append(depth).append(':').append(node.getBackgroundColor()).append(':')
                .append(node.getPaddingLeft()).append(',').append(node.getPaddingRight()).append(':')
                .append(node.getMaxWidth()).append(':').append(node.getPreferredWidth()).append(':')
                .append(node.getPreferredHeight()).append(':').append(node.getFontSize()).append(':')
                .append(node.getMaxLines()).append(':').append(node.isEllipsis()).append('\n');
        if (node.getSegments() != null) {
            for (TextSegment segment : node.getSegments()) {
                TextStyle style = segment.getStyle();
                out.append(segment.getText()).append('|').append(style.getColor()).append('|')
                        .append(style.getFontType()).append('|').append(style.getFontSizePx()).append('|')
                        .append(style.getLink()).append('|').append(style.isUnderline()).append('|')
                        .append(style.isStrikethrough()).append('|').append(style.isItalic()).append('|')
                        .append(segment.isLatex()).append('\n');
            }
        }
        for (SceneNode child : node.__getChildren()) {
            tableNodeSnapshot(child, out, depth + 1);
        }
    }

    // ==================== C8 通道③：markdown 系统行的渲染路由 ====================

    private static final String MARKDOWN_KEY =
            club.heiqi.uilib.api.chat.ChatAccess.MARKDOWN_CHAT_KEY;

    /**
     * 锁 8（渲染路由 + 呈现形状）：markdown 记录 ⇒ 组走 markdown 管道（非系统 § 路）。
     * 判别三连：** 出粗体段（§ 路 parser 不产 markdown 粗体）、§a 字面存活（本测试
     * PARSER 会吞未识别 § 码对——markdown 路不吞 ⇒ 反证）、零 "uilib.markdown" key 字面
     * （短路在取文本之前，语言表查找压根没发生）。形状三连：组节点无组头（子节点 1 个
     * = 消息节点本体）、无气泡（背景 0/无 padding/无圆角）、左对齐（AlignSelf.START）。
     */
    @Test
    public void markdownRecordRoutesThroughPipelineWithLeftPlainShape() {
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        String section = String.valueOf((char) 0x00A7);
        controller.history().append(new ChatLineRecord(
                new net.minecraft.util.ChatComponentTranslation(MARKDOWN_KEY,
                        new Object[] {"**b**" + section + "a 公告 http://a.co"}), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        SceneNode group = hudGroups(root).get(0);
        Assert.assertEquals("markdown 组 = 左对齐(不居中)", AlignSelf.START, group.getAlignSelf());
        Assert.assertEquals("无组头:组直挂唯一消息节点", 1, group.__getChildren().size());
        SceneNode messageNode = group.__getChildren().get(0);
        Assert.assertEquals("无气泡背景", 0, messageNode.getBackgroundColor());
        Assert.assertEquals("无气泡左内衬", 0, messageNode.getPaddingLeft());
        Assert.assertEquals("无气泡右内衬", 0, messageNode.getPaddingRight());
        Assert.assertEquals("无圆角", 0, messageNode.getCornerRadius());

        List<SceneNode> lineNodes = messageNode.__getChildren();
        Assert.assertEquals("单逻辑行", 1, lineNodes.size());
        StringBuilder text = new StringBuilder();
        boolean bold = false;
        for (TextSegment segment : lineNodes.get(0).getSegments()) {
            text.append(segment.getText());
            if (segment.getStyle().getFontType() == FontType.BOLD) {
                bold = true;
            }
        }
        Assert.assertTrue("** 定界必须经 markdown 解析出粗体段(系统 § 路不产此形状): " + text, bold);
        Assert.assertTrue("§a 字面存活(markdown 路零 § 机制): " + text, text.indexOf(section) >= 0);
        Assert.assertFalse("key 字面不得上屏: " + text, text.toString().contains(MARKDOWN_KEY));
    }

    /**
     * 锁 8 同源段（行序列与非注入路径同源）：视图喂进 pipeline 的字符串 = args[0] 原文、
     * 定行宽 = 系统行口径的 compose maxLine（chatWidth − 2×paddingX）、字号 = font-system
     * ——与直连消费者拿同一串/同宽/同字号调管道（非注入路径）在替身换行下逐行等值。
     * 长度刻意不触发 HUD 8 行截断（截断语义另有既有锁钉），保证比对落在同一层产物上。
     */
    @Test
    public void markdownLineSequenceMatchesDirectPipelineCallSameStringSameWidth() {
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        StringBuilder body = new StringBuilder("第一行 ");
        for (int i = 0; i < 60; i++) {
            body.append("字").append(i);
        }
        String md = body.toString();
        controller.history().append(new ChatLineRecord(
                new net.minecraft.util.ChatComponentTranslation(MARKDOWN_KEY, new Object[] {md}),
                1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        SceneNode messageNode = hudGroups(root).get(0).__getChildren().get(0);
        List<SceneNode> viewLines = messageNode.__getChildren();

        ChatMarkdownPipeline direct = new ChatMarkdownPipeline();
        int maxLine = Math.max(1, ChatMarkdownSettings.chatWidthFor(400)
                - 2 * ChatMarkdownSettings.getBubblePaddingX());
        List<ChatMarkdownPipeline.RenderedLine> sameSource = direct.layout(md,
                ChatMarkdownSettings.getSystemTextArgb(), maxLine,
                ChatMarkdownSettings.getSystemFontSizePx(), null, FIXED_WRAP);
        Assert.assertTrue("反 ∅：内容足够长必然多行，实测 " + viewLines.size(),
                viewLines.size() > 1);
        Assert.assertTrue("工况自检（反空跑）：本例必须不触 HUD 截断，实测 " + viewLines.size(),
                viewLines.size() < ChatCardComposer.HUD_MAX_LINES);
        Assert.assertEquals("同串同宽同行数(注入内容与直连消费者同一管道)",
                sameSource.size(), viewLines.size());
        for (int i = 0; i < sameSource.size(); i++) {
            StringBuilder expect = new StringBuilder();
            for (TextSegment segment : sameSource.get(i).segments()) {
                expect.append(segment.getText());
            }
            StringBuilder got = new StringBuilder();
            for (TextSegment segment : viewLines.get(i).getSegments()) {
                got.append(segment.getText());
            }
            Assert.assertEquals("第 " + i + " 行逐字同源", expect.toString(), got.toString());
        }
    }

    /**
     * 锁 8 补丁（HUD 8 行截断平价）：markdown 系统行与气泡行同走 clampHudLines——
     * 超 8 行时视图取前 8 行、末行补省略号（§5.4 验收 22 语义），与非注入路径直调
     * 管道 + clamp 的产物逐行等值。配上一条「不触截断」的同源锁合成完整口径。
     */
    @Test
    public void markdownRowHonoursHudEightLineClampExactlyLikePipeline() {
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 240; i++) {
            body.append("字").append(i);
        }
        String md = body.toString();
        controller.history().append(new ChatLineRecord(
                new net.minecraft.util.ChatComponentTranslation(MARKDOWN_KEY, new Object[] {md}),
                1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        List<SceneNode> viewLines = hudGroups(root).get(0).__getChildren().get(0)
                .__getChildren();
        Assert.assertEquals("HUD 形态恒 ≤ 8 视觉行", ChatCardComposer.HUD_MAX_LINES,
                viewLines.size());
        StringBuilder tail = new StringBuilder();
        for (TextSegment segment : viewLines.get(7).getSegments()) {
            tail.append(segment.getText());
        }
        Assert.assertTrue("末行带省略号(截断语义与气泡行同源): " + tail,
                tail.toString().endsWith(ChatCardComposer.ELLIPSIS));
    }
    /** 对照锁（防误锁）：普通系统行仍居中、仍走 § 解析路（既有行为一字未动）。 */
    @Test
    public void ordinarySystemRowStillCenteredVanillaPath() {
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("[公告] 维护通知"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        SceneNode group = hudGroups(root).get(0);
        Assert.assertEquals(AlignSelf.CENTER, group.getAlignSelf());
        SceneNode messageNode = group.__getChildren().get(0);
        String text = "";
        for (TextSegment segment : messageNode.__getChildren().get(0).getSegments()) {
            text += segment.getText();
        }
        Assert.assertTrue("普通系统行走 § 路: " + text,
                text.contains("[公告] 维护通知"));
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

    // ==================== 跨显示行长 URL(真机缺陷:后半截不是链接) ====================

    /** 会被字符硬断成两行的长 URL(行宽上限 35 字符 × 4px,串长 59)。 */
    private static final String LONG_URL =
            "https://github.com/GTNewHorizons/GT-New-Horizons-odpack/issues";

    @Test
    public void urlBrokenAcrossDisplayLinesStaysOneLinkWithCompleteUrl() {
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Bob> " + LONG_URL), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));
        SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);
        List<SceneNode> lineNodes = bubble.__getChildren();

        // 前提 1:URL 必须真的被断成多行;前提 2:续行自身不含 scheme(否则本测试空转)
        Assert.assertTrue("前提:长 URL 应被硬断成多个显示行,实际 " + lineNodes.size() + " 行",
                lineNodes.size() >= 2);
        int last = lineNodes.size() - 1;
        String tailLine = lineText(lineNodes.get(last));
        Assert.assertFalse("前提:续行不应自带 scheme:" + tailLine, tailLine.contains("://"));

        for (int i = 0; i < lineNodes.size(); i++) {
            List<TextSegment> segments = lineNodes.get(i).getSegments();
            int linkSegments = 0;
            for (TextSegment segment : segments) {
                if (segment.getStyle().getLink() != null) {
                    linkSegments++;
                }
            }
            Assert.assertEquals("第 " + (i + 1) + " 行的 URL 片段必须挂 link(含无 scheme 的续行)",
                    1, linkSegments);
        }
        Assert.assertEquals("续行不得保留 § 原色(修复前真机表现:URL 中途从链接蓝变 §6 金)",
                lineNodes.get(0).getSegments().get(0).getStyle().getColor(),
                lineNodes.get(last).getSegments().get(0).getStyle().getColor());

        // 命中/tooltip 口径:每一行都必须回投**完整** URL
        ChatMessageList.LinkHoverDriver driver =
                controller.messageList().__linkHoverDriverOf(bubble);
        Assert.assertNotNull("跨行 URL 消息必须装配 hover 驱动器", driver);
        AnchorRect bubbleBox = SceneGeometry.absoluteBox(bubble, 0, 0);
        for (int i = 0; i < lineNodes.size(); i++) {
            AnchorRect box = SceneGeometry.absoluteBox(lineNodes.get(i), 0, 0);
            Assert.assertEquals("第 " + (i + 1) + " 行命中必须回投完整 URL(修复前:续行返回 null,"
                            + "首行返回半截)", LONG_URL,
                    driver.resolveUrl(box.getX() - bubbleBox.getX() + 2,
                            box.getY() - bubbleBox.getY() + 9));
        }
    }

    @Test
    public void hoveringAnyLineOfBrokenUrlLightsWholeUrl() {
        // 用户口径:「选中的一整条链接都应该变色，目前跨行会断」
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Bob> " + LONG_URL), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));
        SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);
        List<SceneNode> lineNodes = bubble.__getChildren();
        Assert.assertTrue("前提:URL 需被断成多行,实际 " + lineNodes.size() + " 行",
                lineNodes.size() >= 2);
        ChatMessageList.LinkHoverDriver driver =
                controller.messageList().__linkHoverDriverOf(bubble);
        AnchorRect bubbleBox = SceneGeometry.absoluteBox(bubble, 0, 0);

        // 从中间一行命中 → 每一行都必须一起亮
        int probe = lineNodes.size() / 2;
        AnchorRect probeBox = SceneGeometry.absoluteBox(lineNodes.get(probe), 0, 0);
        driver.onPointerMove(probeBox.getX() - bubbleBox.getX() + 2,
                probeBox.getY() - bubbleBox.getY() + 9);
        boolean[] hovered = driver.lineHoveredForTest();
        for (int i = 0; i < lineNodes.size(); i++) {
            Assert.assertEquals("命中第 " + (probe + 1) + " 行时，第 " + (i + 1)
                    + " 行也必须亮（整条链接一体）", true, hovered[i]);
        }
        // 离开后必须全清，不得有行残留
        driver.onPointerLeave();
        for (int i = 0; i < lineNodes.size(); i++) {
            Assert.assertFalse("离开后第 " + (i + 1) + " 行 hover 残留", hovered[i]);
        }
    }

    @Test
    public void wordWrappedPlainWordIsNotStitchedOntoPrecedingCompleteUrl() {
        // 闸门反向守卫:行末是一段**完整** URL、下一行是另一个词时不得接链。
        // 该情形能否出现由 continuesWord 决定,故直接锁 layouter 标记 + 渲染层无链段。
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Bob> aaaaaaaaaa http://a.co/bbbbbbbbbbbb ccccccccccc"),
                1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));
        SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);
        List<SceneNode> lineNodes = bubble.__getChildren();
        Assert.assertTrue("前提:应切成多行,实际 " + lineNodes.size() + " 行",
                lineNodes.size() >= 2);
        for (SceneNode lineNode : lineNodes) {
            for (TextSegment segment : lineNode.getSegments()) {
                String link = segment.getStyle().getLink();
                if (link != null) {
                    Assert.assertTrue("任何链接段的 URL 值都必须以 scheme 开头,不得混入被误接的词:"
                            + link, link.startsWith("http://a.co/"));
                    Assert.assertFalse("不得把后续词接进 URL:" + link, link.contains("cccc"));
                }
            }
        }
    }

    /** 行节点段流拼接文本(不含 § 格式码)。 */
    private static String lineText(SceneNode lineNode) {
        StringBuilder builder = new StringBuilder();
        for (TextSegment segment : lineNode.getSegments()) {
            builder.append(segment.getText());
        }
        return builder.toString();
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
        // TB1:常驻模式默认开启(无淡出);本测试验证旧 TTL 淡出 × hover 组合 → 临时关闭常驻
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
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
                // 新可见时钟驱动:预算按可见帧累计(首帧锚定、帧间 delta ≤1000ms);
                // 18 帧 ×1s = 可见 18000ms → 超预算 12000 进入淡出 5000ms → p=0.5 → alpha 191
                for (int i = 1; i <= 18; i++) {
                    controller.tick(T0 + 1000L * i);
                    rt.flush();
                }

                // 气泡 hover 目标置位:alpha=191 时 bake(progress 0)= fadeColor(基础色,191)= 0xB5242B33
                AnchorRect box = SceneGeometry.absoluteBox(bubble, 0, 0);
                movePointer(rt, root, box.getX() + 5, box.getY() + box.getHeight() - 5);
                Assert.assertEquals("hover 目标置位但 progress 0:正常色 × alpha191", 0xB5242B33,
                        bubble.getBackgroundColor());

                // 帧推进 +100ms:淡出 el=5100 → alpha 188(锚定 hover 插值起点)
                controller.tick(T0 + 18_100L);
                rt.flush();
                // 插值完成 +100ms:el=5200 → alpha 186 → hover 色 × 淡出 alpha 组合
                controller.tick(T0 + 18_200L);
                rt.flush();
                Assert.assertEquals("hover 色 × 淡出 alpha186 组合", 0xB02B3139,
                        bubble.getBackgroundColor());
                Assert.assertEquals("RGB 保留 hover 提亮分量", 0x2B3139,
                        bubble.getBackgroundColor() & 0xFFFFFF);

                // 移出 → 反向插值归零(alpha=180)后恢复淡出后的正常色
                movePointer(rt, root, 200, 280);
                controller.tick(T0 + 18_300L); // el=5300:反向插值首帧(alpha 183)
                rt.flush();
                controller.tick(T0 + 18_400L); // el=5400 → alpha 180
                rt.flush();
                Assert.assertEquals("淡出中移出恢复正常 bake(基础色 × alpha180)", 0xAA242B33,
                        bubble.getBackgroundColor());
            } finally {
                fadeField.set(null, previousFade);
            }
        } finally {
            // 恢复测试前的常驻配置(与 fade 反射恢复分离,各自独立 finally)
            ChatMarkdownSettings.setHudPersistMessages(persisted);
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

    // ==================== M7 方案乙：块几何进真机（行身份驱动，文本零改动） ====================

    @Test
    public void nestedQuoteLevelTwoBuildsNestedBarRows() {
        // 「>> deep」= quoteLevel 2 → row[bar, row[bar, 文本]]：每层 8px 水平缩进 + 各自竖条。
        // 反向断言（几何不写进文本）：文本段前导必须无空格——缩进只存在于行盒/图元。
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> >> deep"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        Assert.assertEquals("引用行仍 1 个外层 row", 1, bubble.__getChildren().size());
        SceneNode outer = bubble.__getChildren().get(0);
        Assert.assertEquals("外层 = 竖条 + 内层 row", 2, outer.__getChildren().size());
        SceneNode outerBar = outer.__getChildren().get(0);
        Assert.assertEquals("外层竖条宽 2", 2, outerBar.getPreferredWidth());
        Assert.assertEquals("外层竖条色", ChatMarkdownSettings.getQuoteBarArgb(),
                outerBar.getBackgroundColor());
        SceneNode inner = outer.__getChildren().get(1);
        Assert.assertEquals("内层 = 竖条 + 文本", 2, inner.__getChildren().size());
        Assert.assertEquals("内层竖条色", ChatMarkdownSettings.getQuoteBarArgb(),
                inner.__getChildren().get(0).getBackgroundColor());
        List<TextSegment> segments = inner.__getChildren().get(1).getSegments();
        Assert.assertEquals("deep", segments.get(0).getText());
        Assert.assertFalse("缩进绝不写成前导空格（几何走行盒）",
                segments.get(0).getText().startsWith(" "));
    }

    @Test
    public void thematicBreakBubbleLineIsSolidRuleNotDashes() {
        // 有意差异登记（规划 §二之三 M7 注记）：chat3 旧路「---」是字面文本行；M7 经
        // setThematicBreakText("") 既有旋钮关掉文本，行身份 RULE 用背景条表达——
        // 「文字变横线」是用户裁定的 B 有新行为，非回退。
        // C3b2（2026-09-06 对齐裁定）：样本改用 *** ——CommonMark 里紧邻段落的 --- 已判 setext
        // 标题下划线（不再产分隔线），*** 与 === 不同、恒为分隔线，本例要钉的「文字变横线」
        // 判据与 3 显示行形态因此保持不变（setext 侧的正向钉死见 L1 块层测试）。
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Bob> 上句" + (char) 0x0A + "***" + (char) 0x0A + "下句"),
                        1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        Assert.assertEquals("3 显示行", 3, bubble.__getChildren().size());
        SceneNode rule = bubble.__getChildren().get(1);
        Assert.assertTrue("横线行零文本段（不再是字面 dash）",
                rule.getSegments() == null || rule.getSegments().isEmpty());
        Assert.assertTrue("横线有厚度: " + rule.getPreferredHeight(),
                rule.getPreferredHeight() >= 1);
        Assert.assertNotEquals("横线有颜色", 0, rule.getBackgroundColor());
        Assert.assertEquals("首行仍文本行", "上句",
                bubble.__getChildren().get(0).getSegments().get(0).getText());
        Assert.assertEquals("次行仍文本行", "下句",
                bubble.__getChildren().get(2).getSegments().get(0).getText());
        for (SceneNode node : bubble.__getChildren()) {
            if (node.getSegments() != null) {
                for (TextSegment segment : node.getSegments()) {
                    Assert.assertFalse("任何行不得再出现字面 dash 横线文本: " + segment.getText(),
                            segment.getText().matches("-{3,}"));
                }
            }
        }
    }

    @Test
    public void fencedCodeBubbleLinesCarryBlockBackdrop() {
        // 围栏两源行各钉 CODE 底色（同色相接 = 整段底色）；文本内容一字不少（可见文本不变）
        char tick = (char) 0x60;
        String fence = String.valueOf(tick) + tick + tick;
        ChatSceneController controller = controller();
        String nl = String.valueOf((char) 0x0A);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Bob> " + fence + nl + "int a = 1;" + nl + fence), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode codeLine = null;
        for (SceneNode node : bubble.__getChildren()) {
            if (node.getSegments() != null && !node.getSegments().isEmpty()
                    && "int a = 1;".equals(node.getSegments().get(0).getText())) {
                codeLine = node;
            }
        }
        Assert.assertNotNull("围栏源行成显示行且文本一字不改", codeLine);
        Assert.assertNotEquals("CODE 行带块底色", 0, codeLine.getBackgroundColor());
        Assert.assertEquals("底色 = 出货口径 codeBackgroundArgb",
                ChatMarkdownSettings.getCodeBackgroundArgb(), codeLine.getBackgroundColor());
    }

    @Test
    public void fencedCodeBubbleLineBoxesAreVerticallySeamless() {
        // M9 纵向口径证据：聊天面板的内容列 gap=0 且行节点盒高==行高，故同块 CODE 行的底色盒
        // **首尾相接**（y[i+1] == y[i] + h[i]），不存在页面上那条 8px 缝。这条断言把「聊天面板
        // 本来就没有该缺陷」钉成机器事实——若日后有人给内容列加 gap，这里立刻红。
        char tick = (char) 0x60;
        String fence = String.valueOf(tick) + tick + tick;
        String nl = String.valueOf((char) 0x0A);
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob> " + fence + nl + "int a = 1;" + nl + "int bb = 2222;" + nl + fence), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        List<SceneNode> codeBoxes = new ArrayList<SceneNode>();
        for (SceneNode node : bubble.__getChildren()) {
            if (node.getSegments() != null && !node.getSegments().isEmpty()
                    && node.getBackgroundColor() == ChatMarkdownSettings.getCodeBackgroundArgb()) {
                codeBoxes.add(node);
            }
        }
        Assert.assertTrue("反 ∅ 地板：至少 2 条 CODE 行盒，实测 " + codeBoxes.size(),
                codeBoxes.size() >= 2);
        for (int i = 1; i < codeBoxes.size(); i++) {
            LayoutBox prev = (LayoutBox) codeBoxes.get(i - 1).getCachedLayout();
            LayoutBox cur = (LayoutBox) codeBoxes.get(i).getCachedLayout();
            Assert.assertNotNull("行盒必须已布局", cur);
            Assert.assertEquals("聊天面板同块 CODE 行底色盒必须首尾相接（无缝）: 第 " + i
                            + " 行 y=" + cur.getY() + " 上一行底 y=" + (prev.getY() + prev.getHeight()),
                    prev.getY() + prev.getHeight(), cur.getY());
        }
    }

    /**
     * M10c（2026-09-05 裁定：列表正文列落到聊天面）L3 实测锁。本条 javadoc 的上一版
     * （「行位置由引用嵌套结构决定、不得施加该偏移」「续行与标记行盒左缘重合」）钉的是
     * M10b 落地前的世界，<b>已被裁定推翻，就地改写成新事实</b>：
     *
     * <p>新口径——正文列<b>必须</b>施加，且<b>只能施加一次</b>：消费端唯一合法量是差值
     * {@code leftInsetPx - quoteLevel × indentStepPx}（引用份额由嵌套 row 结构另行表达，
     * 整值施加=把引用缩进算两遍）。本锁三面：① 续行<b>内容左缘</b>（行盒 x + 左内衬）与
     * 标记行内容左缘之差 == 测试内<b>独立量出</b>的标记段推进宽（逐码点 resolveAdvance 求和
     * 取 ceil，禁从接缝/管道读回自证）；② 该差值 == 接缝反解值（管道逐字透传 leftInsetPx+
     * indentStepPx 的贯通证据）且 != 2×列（钉「没算两遍」）；③ 非列表段落行内容左缘与标记行
     * 差恒 0（正文列不得泄漏到普通段）。长续行仍不得顶出气泡左右缘（钳宽同扣 listExtra 的
     * 下游证据）。</p>
     *
     * <p>语料「短标记行 + 长懒延续 + 空行后普通段对照」：族归属按位置算，不依赖软折断点。
     * 反 ∅ 地板：总行数 &ge; 4、续行族 &ge; 2（度量突变到不折时地板先红，不静默空跑）。</p>
     */
    @Test
    public void markdownListContinuationGetsNoDoubleOffsetAndFitsBubble() {
        String nl = String.valueOf((char) 0x0A);
        String bodyA = "甲项短首行"; // 标记行短：恒单行，族界干净
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            builder.append("续行长正文"); // 100 字：兜底大字形(~19px)≈6 视觉行、注册后小字形(~3px)≈2 行，
                                          // 两种度量模式下「续行族 >= 2」与「HUD 8 行截断」都同时成立。
        }
        String bodyB = builder.toString();
        // 生产形装配：度量 = 真机同款 uiLibSegmentMeasurer（与 L2 换行同源，钳宽才真触发），
        // 不注换行替身。视图侧正文列施加在「有度量注入」块内（与钉宽/钳宽同块）。
        ChatSceneController controller = new ChatSceneController(FIXED, selfAlex(), PARSER,
                ChatSceneController.uiLibSegmentMeasurer());
        // 钳宽工况是生产自带的：控制器每次建树按生产提取式
        // ChatSceneController.bubbleMaxWidthPxFor(viewport) 同步 maxBubbleWidthPx
        //（A3 起该式为唯一钳制源，本测试不再镜像公式），而 L2 换行用的是更宽的
        // wrapWidthPx——宽续行必然撞钳宽分支。
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob> - " + bodyA + nl + bodyB), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        List<SceneNode> rows = new ArrayList<SceneNode>();
        for (SceneNode node : bubble.__getChildren()) {
            if (node.getSegments() != null && !node.getSegments().isEmpty()) {
                rows.add(node);
            }
        }
        int markerIdx = -1;
        int contIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            String text = rows.get(i).getSegments().get(0).getText();
            if (markerIdx < 0 && text.charAt(0) == '•') {
                markerIdx = i;
            }
            if (contIdx < 0 && text.startsWith("续行")) {
                contIdx = i;
            }
        }
        Assert.assertTrue("标记行必须在气泡里: rows=" + rows.size(), markerIdx >= 0);
        Assert.assertTrue("懒延续行必须排在标记行之后", contIdx > markerIdx);
        Assert.assertTrue("反 ∅ 地板：气泡总行数 >= 3，实测 " + rows.size(), rows.size() >= 3);
        int continuationLines = rows.size() - contIdx;
        Assert.assertTrue("反 ∅ 地板：续行族视觉行 >= 2（长续行必须真软折），实测 "
                + continuationLines, continuationLines >= 2);
        SceneNode markerRow = rows.get(markerIdx);
        SceneNode continuationRow = rows.get(contIdx);

        // 独立 oracle：测试自造标记段「圆点+空格」逐码点 resolveAdvance 求和取 ceil
        // （在 L2 消费 chat 字体号上量，与被测写入路径、与接缝读值都无共享实现）。
        int oracle = oracleMarkerAdvance();
        int shift = contentLeft(continuationRow) - contentLeft(markerRow);
        Assert.assertEquals("续行内容左缘差必须 == 独立量出的正文列（M10c 生效面）",
                oracle, shift);
        Assert.assertTrue("续行偏移 != 2×列——引用份额绝不允许被算两遍: shift=" + shift,
                shift != 2 * oracle && oracle > 0);
        Assert.assertEquals("标记行自带左内衬恒 0（正文列只落在续行上）",
                0, markerRow.getPaddingLeft());
        Assert.assertEquals("续行左内衬恰为一份正文列", oracle,
                continuationRow.getPaddingLeft());

        // 接缝贯通交叉校验：管道须把 leftInsetPx 与 indentStepPx 同数带到消费端，
        // 消费端反解 (leftInsetPx − ql×step) 与视图实测差一致（两路一真值，非自证——
        // oracle 才是判据源，这里钉「读的是同一份数」）。
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> seam = pipeline.layout(
                "- " + bodyA + nl + bodyB, 0xFFFFFFFF, 4000,
                ChatMarkdownSettings.getChatFontSizePx(), null, null);
        ChatMarkdownPipeline.RenderedLine seamCont = null;
        for (ChatMarkdownPipeline.RenderedLine line : seam) {
            if (!line.segments().isEmpty() && line.segments().get(0).getText().startsWith("续行")) {
                seamCont = line;
                break;
            }
        }
        Assert.assertNotNull("接缝里必须有懒延续视觉行", seamCont);
        int seamExtra = Math.max(0,
                seamCont.leftInsetPx() - seamCont.quoteLevel() * seamCont.indentStepPx());
        Assert.assertEquals("视图实测差 == 接缝反解值（管道逐字透传两字段）", shift, seamExtra);
        Assert.assertEquals("接缝反解值 == 独立 oracle", oracle, seamExtra);

        // 长续行不被推出气泡（钳宽同扣 listExtra 的下游证据）：节点级判据「行盒宽
        // （preferred + 左右内衬）≤ 气泡内宽」——布局引擎会把子盒裁进可用宽，光看
        // 盒坐标差抓不到钳宽漏扣（MUT 实测），必须直接核记账值与内衬之和。
        LayoutBox bubbleBox = (LayoutBox) bubble.getCachedLayout();
        // 内宽分母恒取生产提取式：maxBubble = ChatSceneController.bubbleMaxWidthPxFor(400)
        //（A3 提取后测试与生产同源，不再镜像公式；镜像即第二把尺，漂移只会测成恒真或假红），
        // inner = maxBubble - 2padX。
        // 不用 bubbleBox 反推：SHRINK 盒宽只按子节点 preferred 聚合，不计子内衬（实测如此），
        // 拿它当分母会把「pref+padding ≤ inner」这一钳宽契约测成恒真。
        int padX = ChatMarkdownSettings.getBubblePaddingX();
        int maxBubble = ChatSceneController.bubbleMaxWidthPxFor(400);
        int bubbleInnerW = maxBubble - 2 * padX;
        Assert.assertTrue("钳宽工况自检（反 ∅：inner 必须真小于换行宽，否则本断言空转）: "
                + bubbleInnerW + " < " + ChatMarkdownSettings.chatWidthFor(400),
                bubbleInnerW < ChatMarkdownSettings.chatWidthFor(400));
        for (SceneNode node : rows) {
            int boxW = node.getPreferredWidth() + node.getPaddingLeft() + node.getPaddingRight();
            Assert.assertTrue("行账面值（preferred+左右内衬）不得超气泡内宽（钉「钳宽同扣 listExtra」）: "
                    + "line=" + boxW + " inner=" + bubbleInnerW
                    + " <" + node.getSegments().get(0).getText() + "…>",
                    boxW <= bubbleInnerW);
        }
        for (SceneNode node : bubble.__getChildren()) {
            LayoutBox box = (LayoutBox) node.getCachedLayout();
            if (box == null) {
                continue;
            }
            Assert.assertTrue("行盒右缘不得越过气泡右缘: line=" + box.getX() + "+"
                            + box.getWidth() + " bubble=" + (bubbleBox.getX() + bubbleBox.getWidth()),
                    box.getX() + box.getWidth() <= bubbleBox.getX() + bubbleBox.getWidth());
            Assert.assertTrue("行盒左缘不得越过气泡左缘", box.getX() >= bubbleBox.getX());
        }
    }

    /**
     * M10c 引用组合锁：「引用内的列表续行」行盒左缘 = 结构嵌套给出的引用偏移 + <b>一份</b>
     * 正文列，<b>不得</b> = 引用偏移 + leftInsetPx（整值施加会把引用份额算两遍——chat3 的
     * 引用缩进由每行 row[竖条, 内容] 嵌套结构另行表达，与页面分组容器口径不同、同源同一数）。
     * 正文列仍由独立 oracle（逐码点 resolveAdvance）量出；地板：引用列表族 >= 2 行。
     */
    @Test
    public void markdownListInsideQuoteAppliesColumnOnceOnTopOfStructuralIndent() {
        String nl = String.valueOf((char) 0x0A);
        String cont = "甲项懒延续正文";
        for (int i = 0; i < 11; i++) {
            cont = cont + "甲项懒延续正文"; // 96 字：两种度量模式下都必然软折 >=2 行且不超 HUD 截断
        }
        ChatSceneController controller = new ChatSceneController(FIXED, selfAlex(), PARSER,
                ChatSceneController.uiLibSegmentMeasurer());
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob> > - 甲项短首行" + nl + ">   " + cont), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        // 引用行结构：bubble → quoteRow → [竖条, 行节点]；行节点才是段流盒。
        List<SceneNode> lineNodes = new ArrayList<SceneNode>();
        for (SceneNode row : bubble.__getChildren()) {
            if (row.getSegments() != null && !row.getSegments().isEmpty()) {
                lineNodes.add(row); // 非引用行（若有）
                continue;
            }
            for (int c = 0; c < row.__getChildren().size(); c++) {
                SceneNode child = row.__getChildren().get(c);
                if (child.getSegments() != null && !child.getSegments().isEmpty()) {
                    lineNodes.add(child);
                }
            }
        }
        // 族计数按「标记行之后的全部视觉行」——软折片段行的开头字符不可预测，不能按前缀数。
        SceneNode markerRow = null;
        int markerAt = -1;
        int firstContAt = -1;
        for (int i = 0; i < lineNodes.size(); i++) {
            String text = lineNodes.get(i).getSegments().get(0).getText();
            if (markerRow == null && text.charAt(0) == '•') {
                markerRow = lineNodes.get(i);
                markerAt = i;
            } else if (markerRow != null && firstContAt < 0
                    && text.startsWith("甲项懒延续")) {
                firstContAt = i;
            }
        }
        Assert.assertNotNull("引用列表标记行必须在气泡里", markerRow);
        Assert.assertTrue("引用内懒延续首行必须在气泡里", firstContAt > markerAt);
        int contFamily = lineNodes.size() - firstContAt;
        Assert.assertTrue("反 ∅ 地板：引用内懒延续视觉行 >= 2，实测 " + contFamily,
                contFamily >= 2);
        int oracle = oracleMarkerAdvance();
        SceneNode first = lineNodes.get(firstContAt);
        Assert.assertEquals("引用内续行内容左缘 == 标记行内容左缘 + 一份正文列"
                        + "（结构引用偏移两边同额抵消）",
                contentLeft(markerRow) + oracle, contentLeft(first));
        // 反「整值施加」：接缝里该行的 leftInsetPx = 引用份额 + 列；若消费端误把整值当 padding，
        // 内容左缘会多移 quoteLevel×indentStepPx——用 != 显式钉死这一路走不通。
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> seam = pipeline.layout(
                "> - 甲项短首行" + nl + ">   " + cont, 0xFFFFFFFF, 4000,
                ChatMarkdownSettings.getChatFontSizePx(), null, null);
        ChatMarkdownPipeline.RenderedLine seamCont = null;
        for (ChatMarkdownPipeline.RenderedLine line : seam) {
            if (!line.segments().isEmpty()
                    && line.segments().get(0).getText().startsWith("甲项懒延续")) {
                seamCont = line;
                break;
            }
        }
        Assert.assertNotNull("接缝里必须有引用内懒延续行", seamCont);
        Assert.assertTrue("前置自检：引用行接缝 leftInsetPx 必须含引用份额（ql×step>0）: "
                        + seamCont.leftInsetPx(),
                seamCont.quoteLevel() * seamCont.indentStepPx() > 0);
        Assert.assertTrue("不得 = 引用偏移 + leftInsetPx（引用份额算两遍的红线）",
                contentLeft(first) != contentLeft(markerRow) + seamCont.leftInsetPx());
        // 正对照：标记行内容左缘 == 结构引用偏移（它自己 padding 恒 0，不叠第二份）。
        Assert.assertEquals("引用内列表标记行不得吃正文列", 0, markerRow.getPaddingLeft());
        // 全盒仍不得越出气泡左右缘。
        LayoutBox bubbleBox = (LayoutBox) bubble.getCachedLayout();
        for (SceneNode node : bubble.__getChildren()) {
            LayoutBox box = (LayoutBox) node.getCachedLayout();
            if (box == null) {
                continue;
            }
            Assert.assertTrue("引用行盒右缘不得越过气泡右缘",
                    box.getX() + box.getWidth() <= bubbleBox.getX() + bubbleBox.getWidth());
            Assert.assertTrue("引用行盒左缘不得越过气泡左缘", box.getX() >= bubbleBox.getX());
        }
    }

    /**
     * M10c 正对照（防泄漏）：<b>非列表</b>段落的两条逻辑行——次行是普通 TEXT 续行，
     * 内容左缘与首行重合（正文列/左内衬不得渗进无列表身份的块）。语料两行都短，
     * 任何字体度量模式下都不软折，判据与注册时序无关。
     */
    @Test
    public void plainParagraphContinuationKeepsColumnOrigin() {
        String nl = String.valueOf((char) 0x0A);
        ChatSceneController controller = new ChatSceneController(FIXED, selfAlex(), PARSER,
                ChatSceneController.uiLibSegmentMeasurer());
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob> 普通段落首行甲" + nl + "普通段落次行乙"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        List<SceneNode> rows = new ArrayList<SceneNode>();
        for (SceneNode node : bubble.__getChildren()) {
            if (node.getSegments() != null && !node.getSegments().isEmpty()) {
                rows.add(node);
            }
        }
        Assert.assertEquals("两行短段落必须恰两行（样本失效先红）", 2, rows.size());
        Assert.assertEquals("非列表续行不得吃正文列：左缘差恒 0",
                contentLeft(rows.get(0)), contentLeft(rows.get(1)));
        Assert.assertEquals("两行左内衬都恒 0", 0, rows.get(0).getPaddingLeft());
        Assert.assertEquals("两行左内衬都恒 0", 0, rows.get(1).getPaddingLeft());
    }

    private static ChatSceneController.SelfNameProvider selfAlex() {
        return new ChatSceneController.SelfNameProvider() {
            @Override
            public String selfName() {
                return "Alex";
            }
        };
    }

    /** 内容左缘 = 行盒 x + 左内衬（padding 平移文字不平移盒，M9 页面已证实此语义）。 */
    private static int contentLeft(SceneNode node) {
        LayoutBox box = (LayoutBox) node.getCachedLayout();
        Assert.assertNotNull("行盒必须已布局", box);
        return box.getX() + node.getPaddingLeft();
    }

    /**
     * 独立 oracle：测试自造「圆点+空格」段，逐码点 {@code TextLayoutService.resolveAdvance}
     * 求和取 ceil——与气泡同款字号（chat 字体号），与被测写入路径零共享实现。
     */
    private static int oracleMarkerAdvance() {
        TextStyle style = new TextStyle();
        style.setColor(0xFFFFFFFF);
        TextSegment marker = new TextSegment("• ", style);
        club.heiqi.uilib.font.layout.TextLayoutService svc =
                club.heiqi.uilib.font.FontService.getInstance().getTextLayoutService();
        double width = 0.0D;
        String text = marker.getText();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += svc.resolveAdvance(cp, style, ChatMarkdownSettings.getChatFontSizePx());
            i += Character.charCount(cp);
        }
        return (int) Math.ceil(width);
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

    /**
     * C1a（2026-09-06 对齐裁定，CommonMark 0.30 §4.4）使本用例旧前提作废：块起点前导
     * >=4 空格的「    - deep」不论是否命中列表标记，现一律落<b>缩进代码块</b>——
     * M5 F2 深缩进独立列表机制（readDeepList/baseLevel）已退役，「深缩进起步的单一顶层
     * 项」不再存在。改钉新主流行为：<b>缩进代码块的字面段不产生列表正文列/内衬</b>
     * （身份 = CODE 衬底行、左偏移恒 0，"-" 是代码内容而非标记）。嵌套项的祖先列仍由
     * listMarkerChain 给出（下方正对照，反证上面的 0 不是恒真）。
     */
    @Test
    public void indentedCodeAtBlockStartCarriesNoListBodyColumn() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob>     - deep"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("单一字面段（不再是「标记 + 正文」两段的列表形态）",
                1, segments.size());
        Assert.assertEquals("剥至多 4 前导空格后余文按代码内容原样（'-' 不是标记）",
                "- deep", segments.get(0).getText());
        Assert.assertEquals("CODE 身份带块底色（与围栏同款出货口径）",
                ChatMarkdownSettings.getCodeBackgroundArgb(), lineNode.getBackgroundColor());
        // 正文列/内衬唯一真相 = RenderedLine.leftInsetPx（气泡路 listExtra 由它反解）：
        // 无列表 ⇒ 恒 0。旧 F2 文本代理世界会在这里凭空造出 4 空格缩进。
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> code = pipeline.layout(
                "    - deep", 0xFFFFFFFF, 4000,
                ChatMarkdownSettings.getChatFontSizePx(), null, null);
        Assert.assertEquals("4 空格缩进行 = 单行缩进代码块", 1, code.size());
        Assert.assertTrue("行身份 = CODE（不是 LIST）", code.get(0).isCode());
        Assert.assertEquals("缩进代码字面段不产生列表正文列（leftInset 恒 0）",
                0, code.get(0).leftInsetPx());
        // 正对照：真有父项的嵌套项，其标记行由链给出祖先列（接缝 inset>0）
        List<ChatMarkdownPipeline.RenderedLine> flat = pipeline.layout(
                "- top" + String.valueOf((char) 0x0A) + "  - deep", 0xFFFFFFFF, 4000,
                ChatMarkdownSettings.getChatFontSizePx(), null, null);
        int markers = 0;
        boolean nestedMarkerShifted = false;
        for (ChatMarkdownPipeline.RenderedLine line : flat) {
            if (!line.segments().isEmpty()
                    && line.segments().get(0).getText().equals("• ")) {
                markers++;
                if (line.leftInsetPx() > 0) {
                    nestedMarkerShifted = true;
                }
            }
        }
        Assert.assertEquals("恰两条标记段（反 ∅）", 2, markers);
        Assert.assertTrue("正对照：嵌套标记行必须由链给出祖先列（inset>0）", nestedMarkerShifted);
    }

    @Test
    public void orderedListLineKeepsNumberAndIsUnchanged() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> 1. first"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        SceneNode lineNode = bubble.__getChildren().get(0);
        List<TextSegment> segments = lineNode.getSegments();
        // M5 期望变更(非放宽):L1 块层承接有序列表后,序号恒为独立标记段(规划 §二之二 M2
        // 「有序保留源序号原文 + 空格」+ 门禁 P09 PARITY 以「相邻同款式段合并」为等价口径,
        // 合并后与旧单段逐字段全等)。可见文本/样式/宽度与旧行为零差,变的是段边界。
        Assert.assertEquals("序号标记 + 内容两段(合并后=旧单段口径)", 2, segments.size());
        Assert.assertEquals("序号原样保留", "1. ", segments.get(0).getText());
        Assert.assertEquals("内容去标记", "first", segments.get(1).getText());
        Assert.assertEquals("两段同款式(与旧单段等价前提)", segments.get(0).getStyle().getColor(),
                segments.get(1).getStyle().getColor());
    }

    @Test
    public void blockMathLineUsesCenteredDisplayContent() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> $$x^2$$"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        settleDisplayContent(parts);
        assertDisplayContentBubble(bubble);
    }

    private static void settleDisplayContent(Object[] parts) {
        SceneRuntime rt = (SceneRuntime) parts[2];
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        for (int pass = 0; pass < 5; pass++) {
            engine.layout((SceneNode) parts[1], new Constraints(400, 300));
            rt.__setLayoutDoneEpoch(engine.layoutEpoch());
            rt.flush();
        }
    }

    private static SceneNode findDisplayContentNode(SceneNode node,
            java.util.function.Predicate<SceneNode> predicate) {
        if (predicate.test(node)) return node;
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findDisplayContentNode(child, predicate);
            if (found != null) return found;
        }
        return null;
    }

    private static void assertDisplayContentBubble(SceneNode bubble) {
        SceneNode viewport = findDisplayContentNode(bubble, node -> node.isScrollable() && node.isScrollableX());
        Assert.assertNotNull("数学使用既有双轴 Content viewport", viewport);
        Assert.assertTrue("Content viewport 裁剪子树", viewport.isClipChildren());
        SceneNode lineNode = findDisplayContentNode(viewport, node -> {
            if (node.getSegments() == null) return false;
            for (TextSegment segment : node.getSegments()) if (segment.isLatex()) return true;
            return false;
        });
        Assert.assertNotNull("递归寻找 Content 中实际 SEGMENTS 叶子", lineNode);
        List<TextSegment> segments = lineNode.getSegments();
        Assert.assertEquals("块级公式 = 单个 latex 段", 1, segments.size());
        Assert.assertTrue(segments.get(0).isLatex());
        Assert.assertEquals("x^2", segments.get(0).getLatexSource());
        Assert.assertEquals(club.heiqi.uilib.font.latex.MathStyleOverride.DISPLAY,
                segments.get(0).getLatexMathStyle());
        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        LayoutBox formulaBox = (LayoutBox) lineNode.getCachedLayout();
        Assert.assertNotNull(viewportBox);
        Assert.assertNotNull(formulaBox);
        int left = lineNode.getMarginLeft();
        int right = viewportBox.getWidth() - viewport.getPaddingLeft() - viewport.getPaddingRight()
                - left - formulaBox.getWidth();
        Assert.assertTrue("可容纳公式在正文列有居中留白", left > 0);
        Assert.assertEquals("两侧留白仅有像素取整误差", left, right, 2.0);
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
        // 行首连字符但无空格 / 行内 $ 不独占 → 不成列表、不成块级公式(不套 4px 间距)
        controller.history().append(new ChatLineRecord(new ChatComponentText(
                "<Bob> -not-list\nfoo $x$ bar"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        List<SceneNode> lineNodes = bubble.__getChildren();
        Assert.assertEquals("两行", 2, lineNodes.size());
        List<TextSegment> first = lineNodes.get(0).getSegments();
        Assert.assertEquals(1, first.size());
        Assert.assertEquals("-not-list", first.get(0).getText());
        Assert.assertEquals("非列表行无「• 」前缀", 0, lineNodes.get(0).getMarginTop());
        List<TextSegment> second = lineNodes.get(1).getSegments();
        // M5 期望变更(行为增强非丢失):行内 $x$ 由 L1 行内解析成 latex 原子段(规划 §二
        // 「行内 = 复活既有裁定」$ / $$ 语法面;门禁 N08 记 NEW)。本用例钉的意图不变:
        // 行内公式独占行判据仍成立 —— 该行不产块级公式的 4px 上下间距。段流可见文本逐字符保序。
        Assert.assertTrue("行内公式原子段存在", second.size() >= 2);
        boolean hasLatex = false;
        StringBuilder visible = new StringBuilder();
        for (TextSegment segment : second) {
            if (segment.isLatex()) {
                hasLatex = true;
                Assert.assertEquals("x", segment.getLatexSource());
                continue;
            }
            visible.append(segment.getText());
        }
        Assert.assertTrue("行内 $x$ 渲染为公式原子", hasLatex);
        Assert.assertEquals("非公式文本保序", "foo  bar", visible.toString());
        Assert.assertEquals("不独占行不套块级公式间距", 0, lineNodes.get(1).getMarginTop());
        Assert.assertEquals("不独占行不套块级公式间距", 0, lineNodes.get(1).getMarginBottom());
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
    //
    // C7 划界后的读法:本组用例的组件是**非 vanilla 形**桩(plain/formatted 两形,不是
    // chat.type.text 翻译组件)⇒ 走正则兜底通道;兜底通道现在也从 getPlainText() 取本体,
    // 行首 § 残渣随「从 formatted 上切片」这一动作一并消失 ⇒ 块级规则(列表/块公式/行内 code)
    // 在纯文本上照常命中。真机 vanilla 玩家消息更干净:内容直取结构参数 args[1],而原版服务端
    // 逐字符拒收 §。旧机制(C6b 的 § → span 输入转换)本就是为了让这些 § 残渣不坏块规则才
    // 存在——从源头断开后不再需要，故本组用例的期望值一字未改而成立理由变了。

    /** 非 vanilla 形组件桩:发送者与消息本体各带独立颜色码(§f&lt;Bob&gt; §f- item)，
     *  plain 形无 §、formatted 形有 §——用来验兜底通道吃的是 plain 源。 */
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
        // formatted 分支含 §f，但聊天内容读取 unformatted 源，仍进入新 DISPLAY Content。
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new SiblingStyledComponent(
                "<Bob> $$x^2$$", "§f<Bob> §f$$x^2$$"), 1, T0));
        Object[] parts = layoutSingleOtherBubble(controller);
        SceneNode bubble = (SceneNode) parts[0];
        settleDisplayContent(parts);
        assertDisplayContentBubble(bubble);
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


    // ==================== 点击命中链接（scene CLICK → 节点身份 + URL） ====================
    // 注意：这些用例一律喂**气泡局部坐标**，与 hover 用例同口径。曾经的版本在这里
    // 先 `box.getX() + 局部` 再让被测代码减回去 —— 加减互抵，缺陷存在时照样
    // 全绿（假绿测试）。不要再把屏幕坐标引进点击路径。

    @Test
    public void linkClickDeliversUrlAndServerComponentImmediately() {
        Object[] parts = layoutSingleOtherGroup(linkController());
        SceneNode bubble = (SceneNode) parts[0];
        ChatSceneController controller = (ChatSceneController) parts[2];
        List<ChatLinkClick> seen = captureClicks(controller);
        ChatMessageList.LinkHoverDriver driver =
                controller.messageList().__linkHoverDriverOf(bubble);
        // 链接段 = 行内 x[16, 60)（"http://a.co" 11 码点 × 4px），行盒相对气泡 (10, 5)
        driver.onLinkClick(SceneMouseButton.LEFT, 10 + 16 + 24, 5 + 9);
        Assert.assertEquals("一次点击恰好一次投递", 1, seen.size());
        ChatLinkClick hit = seen.get(0);
        Assert.assertEquals("http://a.co", hit.url());
        Assert.assertNotNull("必须同时交出服务端组件（原版 clickEvent 优先级更高）",
                hit.component());
        Assert.assertTrue(hit.component().getFormattedText().contains("Bob"));
    }

    /** 装一个记录型点击出口,返回收到的投递序列。 */
    private static List<ChatLinkClick> captureClicks(ChatSceneController controller) {
        final List<ChatLinkClick> seen = new ArrayList<ChatLinkClick>();
        controller.setMessageLinkClickHandler(click -> seen.add(click));
        return seen;
    }

    @Test
    public void linkClickAndHoverResolveTheSameUrlAtTheSamePoint() {
        Object[] parts = layoutSingleOtherGroup(linkController());
        SceneNode bubble = (SceneNode) parts[0];
        ChatSceneController controller = (ChatSceneController) parts[2];
        ChatMessageList.LinkHoverDriver driver =
                controller.messageList().__linkHoverDriverOf(bubble);
        // 命中盒左右各扩 1px（与 linkHitRegionExpandsTwoPxVerticalAndOnePxHorizontal 同口径）
        int[][] probes = new int[][] { { 10 + 16 + 24, 5 + 9 }, { 10 + 16 - 1, 5 + 9 },
                { 10 + 2, 5 + 9 }, { 10 + 16 - 2, 5 + 9 } };
        List<ChatLinkClick> seen = captureClicks(controller);
        for (int[] p : probes) {
            Assert.assertEquals("点亮区域与可点区域必须同源 (x=" + p[0] + ")",
                    driver.resolveUrl(p[0], p[1]),
                    resolveClick(seen, driver, p[0], p[1]));
        }
    }

    @Test
    public void clickOffLinkStillDeliversComponentForServerClickEvent() {
        Object[] parts = layoutSingleOtherGroup(linkController());
        SceneNode bubble = (SceneNode) parts[0];
        ChatSceneController controller = (ChatSceneController) parts[2];
        List<ChatLinkClick> seen = captureClicks(controller);
        ChatMessageList.LinkHoverDriver driver =
                controller.messageList().__linkHoverDriverOf(bubble);
        driver.onLinkClick(SceneMouseButton.LEFT, 10 + 2, 5 + 9);
        Assert.assertEquals(1, seen.size());
        Assert.assertNull("非链接区不得有 URL", seen.get(0).url());
        Assert.assertNotNull("仍要交出组件：服务端 clickEvent 优先级高于我们的链接跨度",
                seen.get(0).component());
    }

    private static String resolveClick(List<ChatLinkClick> seen,
            ChatMessageList.LinkHoverDriver driver, int localX, int localY) {
        int before = seen.size();
        driver.onLinkClick(SceneMouseButton.LEFT, localX, localY);
        return seen.size() > before ? seen.get(seen.size() - 1).url() : null;
    }

    @Test
    public void clickingAnyLineOfBrokenUrlResolvesCompleteUrl() {
        // 点击路径必须拿到**完整**地址：续行片段拼不进 URL、首行只有半截，都会打开错页面
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Bob> " + LONG_URL), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));
        SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);
        List<SceneNode> lineNodes = bubble.__getChildren();
        Assert.assertTrue("前提:URL 需被断成多行,实际 " + lineNodes.size() + " 行",
                lineNodes.size() >= 2);
        ChatMessageList.LinkHoverDriver driver =
                controller.messageList().__linkHoverDriverOf(bubble);
        List<ChatLinkClick> seen = captureClicks(controller);
        AnchorRect bubbleBox = SceneGeometry.absoluteBox(bubble, 0, 0);
        for (int i = 0; i < lineNodes.size(); i++) {
            // 只取**相对气泡**的局部偏移(同一 scene 空间内做差),不叠加气泡自身的绝对原点:
            // 框架交给 handler 的本来就是节点局部值
            AnchorRect lb = SceneGeometry.absoluteBox(lineNodes.get(i), 0, 0);
            driver.onLinkClick(SceneMouseButton.LEFT, lb.getX() - bubbleBox.getX() + 2,
                    lb.getY() - bubbleBox.getY() + 9);
            Assert.assertEquals("点击第 " + (i + 1) + " 行都必须解析出完整 URL", LONG_URL,
                    seen.get(i).url());
        }
    }

    /**
     * 端到端:真实 DOWN+UP 经 SceneInputRouter 合成 CLICK,送达 chat3 的链接 handler。
     *
     * <p>其余点击用例直接调 driver 方法,证的是**几何**;本条证的是**接线** —— handler
     * 确实注册在能收到 CLICK 的节点上。只测前者的话,handler 漏注册、节点不可命中、事件被
     * 子节点消费,三种接线断裂全都照样绿(本仓「测了个寂寞」教训的同型)。</p>
     */
    @Test
    public void realPointerClickOnLinkReachesChatClickHandler() {
        ChatSceneController controller = linkController();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Bob> see http://a.co x"), 1, T0));
        controller.notifyDataChanged();
        SceneInteractionHarness harness =
                SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        List<ChatLinkClick> seen = captureClicks(controller);
        try {
            SceneNode root = controller.buildContent(harness.getRuntime());
            // 信号批量提交在建树与布局之间:不 flush 则子节点尚未挂上
            harness.getRuntime().flush();
            harness.mountRoot(root, 400, 300);
            SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);
            AnchorRect box = SceneGeometry.absoluteBox(bubble, 0, 0);
            harness.clickAt(box.getX() + 10 + 16 + 24, box.getY() + 5 + 9);
            Assert.assertEquals("一次真实点击必须当场投递一次(旧实现在 POINTER_DOWN 取账,"
                    + "这一笔要等下一次点击才生效 = 「要点第二下」)", 1, seen.size());
            Assert.assertEquals("http://a.co", seen.get(0).url());
            // 第二下:必须投递第二笔,而不是重复第一笔
            harness.clickAt(box.getX() + 10 + 16 + 24, box.getY() + 5 + 9);
            Assert.assertEquals(2, seen.size());
            Assert.assertEquals("http://a.co", seen.get(1).url());
            // 点气泡内非链接处:投递 url=null(交回服务端语义),不得复用上一笔 URL
            harness.clickAt(box.getX() + 2, box.getY() + 2);
            Assert.assertEquals(3, seen.size());
            Assert.assertNull("残留 URL 会让我们重开上一次点过的链接", seen.get(2).url());
        } finally {
            harness.dispose();
        }
    }

    @Test
    public void nonLeftClickDeliversNothing() {
        Object[] parts = layoutSingleOtherGroup(linkController());
        SceneNode bubble = (SceneNode) parts[0];
        ChatSceneController controller = (ChatSceneController) parts[2];
        List<ChatLinkClick> seen = captureClicks(controller);
        ChatMessageList.LinkHoverDriver driver =
                controller.messageList().__linkHoverDriverOf(bubble);
        // 右键/中键命中同一链接:宿主只认左键。若也投递,在旧的暂存实现里会留下
        // 无人消费的一笔,被下一次「落在所有消息之外」的左键当成刚点的链接(幽灵打开)
        driver.onLinkClick(SceneMouseButton.RIGHT, 10 + 16 + 24, 5 + 9);
        driver.onLinkClick(SceneMouseButton.MIDDLE, 10 + 16 + 24, 5 + 9);
        Assert.assertEquals("非左键不得投递", 0, seen.size());
        driver.onLinkClick(SceneMouseButton.LEFT, 10 + 16 + 24, 5 + 9);
        Assert.assertEquals(1, seen.size());
    }

    /**
     * 锁:点击不得在 POINTER_DOWN 回调里消费。
     *
     * <p>scene 的 CLICK 由 SceneInputRouter 在 POINTER_UP 合成;若屏幕在
     * {@code mouseClicked}(= DOWN)里取账,同一次点击必然取到空 —— 真机表现就是
     * 「链接要点第二下才有效」。点击只能由 CLICK 事件当场投递。</p>
     */
    @Test
    public void clickMustNotBeConsumedAtPointerDown() throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get(
                "src/main/java/club/heiqi/uilib/internal/chat3/input/ChatInputScreen.java");
        String raw = new String(java.nio.file.Files.readAllBytes(path),
                java.nio.charset.StandardCharsets.UTF_8);
        // 只看代码:注释里正解释着"为什么不能这么做",提这些名字是应该的
        StringBuilder code = new StringBuilder();
        for (String line : raw.split("\r?\n")) {
            String t = line.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                continue;
            }
            code.append(t).append('\n');
        }
        String src = code.toString();
        // 判据是"重写并在此消费",不是"提到这个词"——本类注释里正解释着为什么不能这么做
        Assert.assertFalse("ChatInputScreen 不得再重写 mouseClicked 来消费点击(慢一拍缺陷的成因)",
                src.contains("void mouseClicked("));
        Assert.assertFalse("点击消费入口不得回到屏幕层",
                src.contains("handleLineClick") || src.contains("onSceneLinkClick")
                        || src.contains("takePendingLinkClick"));
        // 生产接线也要锁:端到端用例自己装 handler,证不了宿主(surface)真的装了出口。
        // 少了这一行,点击在测试里全绿、在真机上依旧毫无反应。
        java.nio.file.Path surfacePath = java.nio.file.Paths.get(
                "src/main/java/club/heiqi/uilib/internal/chat3/input/ChatInputSurface.java");
        String surface = new String(java.nio.file.Files.readAllBytes(surfacePath),
                java.nio.charset.StandardCharsets.UTF_8);
        Assert.assertTrue("ChatInputSurface 必须注册链接点击出口(生产接线)",
                surface.contains("setMessageLinkClickHandler"));
    }

    /**
     * 锁:点击路径不得再收屏幕坐标。
     *
     * <p>guiScale &gt; 1 时 MC 回调坐标(scaled 逻辑像素)与 scene 几何(物理像素)不可无损
     * 换算,任何"收 screenX/screenY 再减绝对盒"的命中 API 都会静默全空 —— 现象就是
     * 「点了链接没反应」。唯一安全的判据是 scene CLICK 交出的节点身份。</p>
     */
    @Test
    public void clickPathMustNotExposeScreenCoordinateHitApi() throws Exception {
        Class<?>[] subjects = new Class<?>[] { ChatMessageList.class, ChatSceneController.class };
        for (int s = 0; s < subjects.length; s++) {
            java.lang.reflect.Method[] methods = subjects[s].getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                java.lang.reflect.Method m = methods[i];
                Class<?>[] params = m.getParameterTypes();
                boolean screenHitApi = params.length == 2
                        && params[0] == int.class && params[1] == int.class
                        && (m.getName().startsWith("resolveLinkUrl")
                                || m.getName().startsWith("resolveAtScreen"));
                Assert.assertFalse(subjects[s].getSimpleName() + "." + m.getName()
                        + "(int, int) 又成了屏幕坐标命中入口", screenHitApi);
            }
        }
    }

    // ==================== G17/Bubble:气泡底色/材质/圆角 = 配方局部覆盖接缝 ====================

    /** 玻璃态合成器(与配方唯一合并处同式):把设置 alpha 合入语义底色 RGB 通道。 */
    private static int compositedAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    /** 与聊天既有取值全面冲突的通用 GROUP 配方主题:钉「显式聊天设置 > 通用主题」不被刷平。 */
    private static SceneTheme competingBubbleTheme() {
        SceneSurfaceStyle themedGroup = SceneSurfaceStyle.builder()
                .backdrop(UiBackdrop.liquidGlass(UiGlassMaterial.THIN, 20, 0.9F))
                .cornerRadius(6)
                .borderWidth(2)
                .idle(new SceneSurfaceStyle.StateStyle(0xFF403020, 0x66FFFFFF, 0.5F, 0.5F))
                .hovered(new SceneSurfaceStyle.StateStyle(0xFF463626, 0x80FFFFFF, 1.0F, 0.7F))
                .pressed(new SceneSurfaceStyle.StateStyle(0xFF3A2C1C, 0x4DFFFFFF, 0.0F, 0.4F))
                .disabled(new SceneSurfaceStyle.StateStyle(0xFF2A2420, 0x33FFFFFF, 0.35F, 0.3F))
                .build();
        return SceneTheme.builder().surface(SceneTheme.Role.GROUP, themedGroup).build();
    }

    /** 与第一档逐值不同的第二主题:主题切换重派生的可观测载体(配方相等会被记忆化)。 */
    private static SceneTheme alternateBubbleTheme() {
        SceneSurfaceStyle themedGroup = SceneSurfaceStyle.builder()
                .backdrop(UiBackdrop.liquidGlass(UiGlassMaterial.THIN, 21, 0.8F))
                .cornerRadius(3)
                .borderWidth(2)
                .idle(new SceneSurfaceStyle.StateStyle(0xFF102930, 0x66FFFFFF, 0.5F, 0.5F))
                .hovered(new SceneSurfaceStyle.StateStyle(0xFF183138, 0x80FFFFFF, 1.0F, 0.7F))
                .pressed(new SceneSurfaceStyle.StateStyle(0xFF0A2128, 0x4DFFFFFF, 0.0F, 0.4F))
                .disabled(new SceneSurfaceStyle.StateStyle(0xFF202420, 0x33FFFFFF, 0.35F, 0.3F))
                .build();
        return SceneTheme.builder().surface(SceneTheme.Role.GROUP, themedGroup).build();
    }

    /** 组节点的表观气泡/消息节点 = 最后一个子节点(组头在前;系统组无组头时即消息节点)。 */
    private static SceneNode lastChild(SceneNode group) {
        List<SceneNode> children = group.__getChildren();
        return children.get(children.size() - 1);
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

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
    }

    /**
     * ① 气泡语义色区分保持 + 反向钉住:自己/他人各取聊天设置独立通道(互不相同、均非主题色),
     * 系统消息无气泡表面;装上全面冲突的通用 GROUP 主题并推进多帧后,底色/圆角/材质仍由聊天
     * 配方供给(把某语义气泡刷成主题统一色即红;后续帧覆写型竞争绑定也会被钉住)。
     */
    @Test
    public void bubbleSemanticColorsStayDistinctAndUndefeatedByConflictingTheme() {
        boolean savedGlass = ChatMarkdownSettings.isGlassEnabled();
        try {
            ChatMarkdownSettings.setGlassEnabled(true);
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneThemes.install(rt, Signal.create(competingBubbleTheme()));
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> hi"), 2, T0 + 60_000L));
            controller.history().append(new ChatLineRecord(new ChatComponentText("[公告] 维护通知"), 3, T0 + 120_000L));
            controller.notifyDataChanged();
            SceneNode root = controller.buildContent(rt);
            rt.flush();
            rt.__tickFrame(1L);
            rt.flush();
            rt.__tickFrame(2L);
            rt.flush();

            List<SceneNode> groups = hudGroups(root);
            Assert.assertEquals("他人/自己/系统三组", 3, groups.size());
            SceneNode otherBubble = lastChild(groups.get(0));
            SceneNode selfBubble = lastChild(groups.get(1));
            SceneNode systemNode = lastChild(groups.get(2));

            int expectedOther = compositedAlpha(ChatMarkdownSettings.getBubbleOtherArgb(),
                    ChatMarkdownSettings.getGlassBubbleAlpha());
            int expectedSelf = compositedAlpha(ChatMarkdownSettings.getBubbleSelfArgb(),
                    ChatMarkdownSettings.getGlassBubbleAlpha());
            Assert.assertEquals("他人气泡 = 聊天设置 other 通道合成玻璃 alpha(非主题 tint)",
                    expectedOther, otherBubble.getBackgroundColor());
            Assert.assertEquals("自己气泡 = 聊天设置 self 通道合成玻璃 alpha(非主题 tint)",
                    expectedSelf, selfBubble.getBackgroundColor());
            Assert.assertNotEquals("自己/他人气泡不得刷成同一色", expectedSelf, expectedOther);
            SceneSurfaceStyle themedGroup = competingBubbleTheme().surface(SceneTheme.Role.GROUP);
            Assert.assertNotEquals("两语义色均不得等于主题 GROUP tint", themedGroup.getIdle().getTint(), expectedOther);
            Assert.assertNotEquals(themedGroup.getIdle().getTint(), expectedSelf);
            Assert.assertEquals("系统消息无气泡底色(现状语义保持)", 0, systemNode.getBackgroundColor());
            Assert.assertNull("系统消息不得被装玻璃", systemNode.getBackdrop());
            // 材质/圆角同由聊天配方管辖:主题冲突档为 THIN/20/圆角 6,聊天档为 DARK_REGULAR/8/12。
            UiBackdrop backdrop = otherBubble.getBackdrop();
            Assert.assertNotNull("玻璃开启时气泡必须带滤镜", backdrop);
            Assert.assertEquals(UiGlassMaterial.DARK_REGULAR, backdrop.getEffect().getMaterial());
            Assert.assertEquals(ChatMarkdownSettings.getGlassBlurRadiusPx(), backdrop.getBlurRadius());
            assertCorners(otherBubble, 12, 12, 12, 12);
        } finally {
            ChatMarkdownSettings.setGlassEnabled(savedGlass);
        }
    }

    /**
     * ①′ 优先级逐分量同框:注入的局部配方只显式设置 自己/他人底色 + 外圆角,玻璃开关/模糊/
     * 强度/内圆角均缺省 → 滤镜取主题档、颜色与外圆角取聊天档(局部覆盖不是全有全无)。
     */
    @Test
    public void partialChatPatchOverridesThemePerComponent() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneThemes.install(rt, Signal.create(competingBubbleTheme()));
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        controller.messageList().__setBubbleLocalStyle(new Supplier<ChatMessageList.BubbleLocalStyle>() {
            @Override
            public ChatMessageList.BubbleLocalStyle get() {
                return new ChatMessageList.BubbleLocalStyle(null, null, null, null,
                        Integer.valueOf(0xEE112233), Integer.valueOf(0xEE445566),
                        Integer.valueOf(9), null);
            }
        });
        SceneNode root = controller.buildContent(rt);
        rt.flush();

        SceneNode bubble = lastChild(hudGroups(root).get(0));
        SceneSurfaceStyle themedGroup = competingBubbleTheme().surface(SceneTheme.Role.GROUP);
        Assert.assertEquals("显式聊天底色分量压过主题(玻璃开关缺省 → 不合成 alpha)",
                0xEE445566, bubble.getBackgroundColor());
        Assert.assertEquals("显式聊天外圆角压过主题圆角 6", 9, bubble.getCornerRadiusTopLeft());
        Assert.assertEquals("未管辖分量回主题:滤镜 = 主题 GROUP backdrop",
                themedGroup.getBackdrop().getBlurRadius(), bubble.getBackdrop().getBlurRadius());
        Assert.assertEquals(themedGroup.getBackdrop().getEffect().getMaterial(),
                bubble.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("内圆角缺省 → 跟随配方单值(四角同一外档)", 9, bubble.getCornerRadiusBottomRight());
    }

    /**
     * ② 表面归属事实与滤镜计数:列表容器(listParent)本无底表面——背景归 ChatContainer/
     * ChatHudWindow 宿主(G17/Container 实例),本组件不得为其新增表面;「装」的一侧 =
     * 纯主题形态下气泡逐项等于 GROUP 配方值、每颗气泡恰 1 条 BACKDROP(无第二层);聊天玻璃
     * 关闭的一侧 = 整树零 BACKDROP、底色回不透明设计令牌。
     */
    @Test
    public void listContainerCarriesNoSurfaceAndBubbleGlassFollowsRecipeExactlyOnce() {
        boolean savedGlass = ChatMarkdownSettings.isGlassEnabled();
        try {
            ChatMarkdownSettings.setGlassEnabled(true);
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> one"), 1, T0));
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> two"), 2, T0 + 1000));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode list = SceneNode.column().setHitTestable(false);
            Map<SceneNode, ChatLineRecord> registry =
                    new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
            SceneListHandle handle = controller.messageList().mount(rt, list,
                    controller.groupsSignal(), ChatMessageList.Style.container(), registry,
                    controller.frameMillisSignal());
            rt.flush();
            FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
            new SceneLayoutEngine(measurer).layout(list, new Constraints(400, 300));
            ScenePaintEngine engine = new ScenePaintEngine(measurer);

            // 列表容器无底表面:不新增背景/滤镜/圆角/边框(归属 Container 宿主实例)。
            Assert.assertNull("列表容器不得被新增表面", list.getBackdrop());
            Assert.assertEquals("列表容器零底色", 0, list.getBackgroundColor());
            Assert.assertEquals("列表容器零圆角", 0, list.getCornerRadius());
            Assert.assertEquals("列表容器零边框", 0, list.getBorderWidth());

            SceneNode group = list.__getChildren().get(0);
            SceneNode firstBubble = group.__getChildren().get(1);
            SceneNode lastBubble = group.__getChildren().get(2);
            // 「装」侧:气泡逐项 = 聊天配方值 + 每颗恰 1 条 BACKDROP(无第二层滤镜)。
            int expected = compositedAlpha(ChatMarkdownSettings.getBubbleOtherArgb(),
                    ChatMarkdownSettings.getGlassBubbleAlpha());
            Assert.assertEquals(expected, firstBubble.getBackgroundColor());
            Assert.assertEquals(expected, lastBubble.getBackgroundColor());
            Assert.assertNotNull(firstBubble.getBackdrop());
            Assert.assertNotNull(lastBubble.getBackdrop());
            for (int i = 1; i <= 2; i++) {
                Assert.assertNull("行子节点不得各自再采样背景(零第二层滤镜)",
                        group.__getChildren().get(i).__getChildren().get(0).getBackdrop());
            }
            PaintPlan plan = engine.paint(list).getPlan();
            Assert.assertEquals("玻璃开:恰每颗气泡一条 BACKDROP", 2,
                    countType(plan.getCommands(), PaintCommandType.BACKDROP));

            // 「不装」侧:关玻璃 → 只重派生(同节点),零 BACKDROP、底色回不透明令牌。
            ChatMarkdownSettings.setGlassEnabled(false);
            rt.__tickFrame(1L);
            rt.flush();
            Assert.assertSame("设置变更不重建组", group, list.__getChildren().get(0));
            Assert.assertSame("设置变更不重生气泡", firstBubble, list.__getChildren().get(0).__getChildren().get(1));
            Assert.assertNull(firstBubble.getBackdrop());
            Assert.assertEquals("关玻璃回实心设计令牌", ChatMarkdownSettings.getBubbleOtherArgb(),
                    firstBubble.getBackgroundColor());
            Assert.assertEquals("关玻璃:整树零 BACKDROP", 0,
                    countType(engine.paint(list).getPlan().getCommands(), PaintCommandType.BACKDROP));
            handle.dispose();
        } finally {
            ChatMarkdownSettings.setGlassEnabled(savedGlass);
        }
    }

    /**
     * ②′ 纯主题形态(测试接缝注入「无聊天局部设置」):气泡表面逐项 = 通用 GROUP 配方,
     * 且整树每颗气泡恰 1 条 BACKDROP(主题滤镜直通、无第二层)。
     */
    @Test
    public void plainThemeOnlyBubbleMatchesGroupRecipeItemByItem() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneThemes.install(rt, Signal.create(competingBubbleTheme()));
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        controller.messageList().__setBubbleLocalStyle(null);
        SceneNode list = SceneNode.column().setHitTestable(false);
        Map<SceneNode, ChatLineRecord> registry = new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
        controller.messageList().mount(rt, list, controller.groupsSignal(),
                ChatMessageList.Style.container(), registry, controller.frameMillisSignal());
        rt.flush();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        new SceneLayoutEngine(measurer).layout(list, new Constraints(400, 300));
        SceneNode bubble = lastChild(list.__getChildren().get(0));
        SceneSurfaceStyle themed = competingBubbleTheme().surface(SceneTheme.Role.GROUP);
        Assert.assertEquals("底色 = 主题 GROUP idle tint", themed.getIdle().getTint(), bubble.getBackgroundColor());
        Assert.assertEquals("滤镜 = 主题 GROUP backdrop", themed.getBackdrop().getBlurRadius(),
                bubble.getBackdrop().getBlurRadius());
        Assert.assertEquals(themed.getBackdrop().getEffect().getMaterial(),
                bubble.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("圆角 = 主题 GROUP 单值(四角同档)", themed.getCornerRadius(),
                bubble.getCornerRadiusTopLeft());
        PaintPlan plan = new ScenePaintEngine(measurer).paint(list).getPlan();
        Assert.assertEquals("每颗气泡恰一颗滤镜(无第二层)", 1,
                countType(plan.getCommands(), PaintCommandType.BACKDROP));
    }

    /**
     * ③ 主题/聊天设置变更只重派生:节点身份不变、effect 数不增长;聊天形态对主题换值免疫
     * (优先级聊天 > 主题),设置变更则精确跟值——反向钉「重派生而非重建」。
     */
    @Test
    public void themeAndChatSettingsChangesOnlyRederiveBubbleSurface() {
        boolean savedGlass = ChatMarkdownSettings.isGlassEnabled();
        int savedBlur = ChatMarkdownSettings.getGlassBlurRadiusPx();
        int savedAlpha = ChatMarkdownSettings.getGlassBubbleAlpha();
        try {
            ChatMarkdownSettings.setGlassEnabled(true);
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            Signal<SceneTheme> themeSignal = Signal.create(competingBubbleTheme());
            SceneThemes.install(rt, themeSignal);
            SceneNode root = controller.buildContent(rt);
            rt.flush();
            SceneNode group = hudGroups(root).get(0);
            SceneNode bubble = lastChild(group);
            int effectsAfterMount = ReactiveTestProbe.registeredEffectCount();

            // 聊天设置变更 → 同节点重派生:blur 与 alpha 精确跟值。
            ChatMarkdownSettings.setGlassBlurRadiusPx(17);
            ChatMarkdownSettings.setGlassBubbleAlpha(0x60);
            rt.__tickFrame(1L);
            rt.flush();
            Assert.assertSame("设置变更不重建组节点", group, hudGroups(root).get(0));
            Assert.assertSame("设置变更不重生气泡节点", bubble, lastChild(hudGroups(root).get(0)));
            Assert.assertEquals(17, bubble.getBackdrop().getBlurRadius());
            Assert.assertEquals(0x60, (bubble.getBackgroundColor() >>> 24) & 0xFF);
            Assert.assertEquals(compositedAlpha(ChatMarkdownSettings.getBubbleOtherArgb(), 0x60),
                    bubble.getBackgroundColor());

            // 主题变更(聊天全权管辖)→ 外观不动、effect 不增长;残留按帧竞争绑定会在后续帧
            // 把主题值刷回来,故多推几帧再断言。
            themeSignal.set(alternateBubbleTheme());
            rt.__tickFrame(2L);
            rt.flush();
            rt.__tickFrame(3L);
            rt.flush();
            Assert.assertSame(bubble, lastChild(hudGroups(root).get(0)));
            Assert.assertEquals(17, bubble.getBackdrop().getBlurRadius());
            Assert.assertEquals(compositedAlpha(ChatMarkdownSettings.getBubbleOtherArgb(), 0x60),
                    bubble.getBackgroundColor());
            Assert.assertEquals("变更只重派生,不新增订阅", effectsAfterMount,
                    ReactiveTestProbe.registeredEffectCount());
        } finally {
            ChatMarkdownSettings.setGlassEnabled(savedGlass);
            ChatMarkdownSettings.setGlassBlurRadiusPx(savedBlur);
            ChatMarkdownSettings.setGlassBubbleAlpha(savedAlpha);
        }
    }

    /**
     * ③′ 纯主题形态的主题切换同样只重派生:节点身份不变、effect 不增长,配方逐项随新主题更新
     * (证明主题接缝真实存在、不是死代码;与 ③ 的生产形态互补)。
     */
    @Test
    public void themeSwitchRederivesPlainThemeOnlyBubbleToNewRecipe() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        Signal<SceneTheme> themeSignal = Signal.create(competingBubbleTheme());
        SceneThemes.install(rt, themeSignal);
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        controller.messageList().__setBubbleLocalStyle(null);
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        SceneNode group = hudGroups(root).get(0);
        SceneNode bubble = lastChild(group);
        int effectsAfterMount = ReactiveTestProbe.registeredEffectCount();

        themeSignal.set(alternateBubbleTheme());
        rt.__tickFrame(1L);
        rt.flush();
        SceneSurfaceStyle alt = alternateBubbleTheme().surface(SceneTheme.Role.GROUP);
        Assert.assertSame(group, hudGroups(root).get(0));
        Assert.assertSame(bubble, lastChild(hudGroups(root).get(0)));
        Assert.assertEquals(alt.getIdle().getTint(), bubble.getBackgroundColor());
        Assert.assertEquals(alt.getCornerRadius(), bubble.getCornerRadiusTopLeft());
        Assert.assertEquals(alt.getBackdrop().getBlurRadius(), bubble.getBackdrop().getBlurRadius());
        Assert.assertEquals("主题切换不新增订阅", effectsAfterMount,
                ReactiveTestProbe.registeredEffectCount());
    }

    /**
     * ④ 源码守卫:气泡表面值单一来源——玻璃/颜色/圆角设置只在配方采样器读取;滤镜只在配方层
     * 构造一次;无静态色板与字面量直写;消费方向锁:通用主题包绝不 import internal(反向依赖
     * 禁止,契约 §2)。
     */
    @Test
    public void bubbleSurfaceValuesAreSingleSourcedAndThemeDependencyIsOneWay() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/club/heiqi/uilib/internal/chat3/view/ChatMessageList.java")),
                StandardCharsets.UTF_8);
        Assert.assertEquals("玻璃开关只准配方采样器读一次", 1, countOccurrences(source, "isGlassEnabled()"));
        Assert.assertEquals("模糊半径只准配方采样器读一次", 1, countOccurrences(source, "getGlassBlurRadiusPx()"));
        Assert.assertEquals("透镜强度只准配方采样器读一次", 1, countOccurrences(source, "getGlassLensStrength()"));
        Assert.assertEquals("玻璃 alpha 只准配方采样器读一次", 1, countOccurrences(source, "getGlassBubbleAlpha()"));
        Assert.assertEquals("自己气泡色只准配方采样器读一次", 1, countOccurrences(source, "getBubbleSelfArgb()"));
        Assert.assertEquals("他人气泡色只准配方采样器读一次", 1, countOccurrences(source, "getBubbleOtherArgb()"));
        Assert.assertEquals("大圆角设置只准配方采样器读一次", 1, countOccurrences(source, "getBubbleCornerRadius()"));
        Assert.assertEquals("内圆角设置只准配方采样器读一次", 1,
                countOccurrences(source, "getBubbleInnerCornerRadiusPx()"));
        Assert.assertEquals("滤镜只在配方层构造一次(禁止第二处造玻璃)", 1,
                countOccurrences(source, "UiBackdrop.liquidGlass("));
        Assert.assertEquals("底色 alpha 合成只在配方合并处一次", 1,
                countOccurrences(source, "0x00FFFFFF"));
        Assert.assertTrue("存在通用主题配方消费接缝", source.contains("SceneThemes.surface("));
        Assert.assertTrue("表面值统一经配方合并处", source.contains("mergeBubbleSurface("));
        Assert.assertTrue("气泡表面唯一写入链 = 配方重派生绑定", source.contains("bake.updateSurface("));
        Assert.assertFalse("不得残留静态色板 SceneStateColors", source.contains("SceneStateColors"));
        Assert.assertFalse("不得残留 SceneChromeTokens 查表", source.contains("SceneChromeTokens"));
        Assert.assertFalse("setBackgroundColor 不得带静态字面量", source.contains("setBackgroundColor(0x"));
        Assert.assertFalse("setCornerRadius 不得绕过配方直读设置",
                source.contains("setCornerRadius(ChatMarkdownSettings"));

        // 方向锁:theme 包绝不 import internal/chat3(通用主题不感知聊天,契约 §4.1)。
        try (Stream<Path> walk = Files.walk(Paths.get("src/main/java/club/heiqi/uilib/ui/scene/theme"))) {
            for (Path file : (Iterable<Path>) walk.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String themeSource = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                Assert.assertFalse("通用主题不得 import internal/chat3: " + file,
                        themeSource.matches("(?s).*import\\s+club\\.heiqi\\.uilib\\.internal\\..*"));
            }
        }
    }

    /**
     * ⑤ 消息渲染既有合同保持:hover 叠加仍按配方底色 + 3% 白推进(bake 唯一底色写入链,
     * 配方重派生不与其竞争);markdown CODE 行底色、accent 强调条、引用竖条等语义色不随
     * 气泡配方/主题变化(越界刷内容色即红)。
     */
    @Test
    public void hoverBakeAndContentSemanticColorsSurviveRecipeSeam() {
        boolean savedGlass = ChatMarkdownSettings.isGlassEnabled();
        try {
            ChatMarkdownSettings.setGlassEnabled(true);
            ChatSceneController controller = linkController();
            controller.setHostViewport(400, 300);
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Bob> hello http://a.co"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneThemes.install(rt, Signal.create(competingBubbleTheme()));
            SceneNode root = controller.buildContent(rt);
            rt.flush();
            new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(root, new Constraints(400, 300));
            SceneNode bubble = lastChild(hudGroups(root).get(0));
            int recipeBase = compositedAlpha(ChatMarkdownSettings.getBubbleOtherArgb(),
                    ChatMarkdownSettings.getGlassBubbleAlpha());
            Assert.assertEquals(recipeBase, bubble.getBackgroundColor());

            // hover 满程 = 配方底色 3% 白叠加(既有交互合同,数值逐位一致)。
            AnchorRect box = SceneGeometry.absoluteBox(bubble, 0, 0);
            movePointer(rt, root, box.getX() + 5, box.getY() + box.getHeight() - 5);
            controller.tick(T0 + 100L);
            rt.flush();
            controller.tick(T0 + 400L);
            rt.flush();
            Assert.assertEquals("hover 稳态 = 配方底色 + 3% 白",
                    ChatCardComposer.hoveredBubbleColor(recipeBase), bubble.getBackgroundColor());
        } finally {
            ChatMarkdownSettings.setGlassEnabled(savedGlass);
        }
    }

    /** ⑤′ 内容语义色在玻璃 + 冲突主题下逐位不变(CODE 围栏底/accent 条/引用条均非气泡表面)。 */
    @Test
    public void contentSemanticColorsStayOutsideBubbleRecipe() {
        boolean savedGlass = ChatMarkdownSettings.isGlassEnabled();
        try {
            ChatMarkdownSettings.setGlassEnabled(true);
            char tick = (char) 0x60;
            String fence = String.valueOf(tick) + tick + tick;
            String nl = String.valueOf((char) 0x0A);
            ChatSceneController controller = controller();
            controller.setHostViewport(400, 300);
            controller.history().append(new ChatLineRecord(new ChatComponentText(
                    "<Bob> " + fence + nl + "int a = 1;" + nl + fence), 1, T0));
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Alex> hi"), 2, T0 + 60_000L));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneThemes.install(rt, Signal.create(competingBubbleTheme()));
            SceneNode root = controller.buildContent(rt);
            rt.flush();
            Assert.assertEquals("玻璃 alpha 合成不得越界进 CODE 行底色(主题/配方都刷不动它)",
                    ChatMarkdownSettings.getCodeBackgroundArgb(),
                    findLineNodeWithText(root, "int a = 1;").getBackgroundColor());

            // accent 强调条:自己气泡语义标记,设置色 + 几何圆角,与气泡配方/主题无涉。
            List<SceneNode> groups = hudGroups(root);
            SceneNode selfBubble = lastChild(groups.get(groups.size() - 1));
            SceneNode accentBar = selfBubble.__getChildren().get(1);
            Assert.assertEquals(ChatMarkdownSettings.getAccentBarSelfArgb(), accentBar.getBackgroundColor());
            Assert.assertEquals(2, accentBar.getCornerRadius());
        } finally {
            ChatMarkdownSettings.setGlassEnabled(savedGlass);
        }
    }

    /** 深度优先找首个段流文本含指定内容的行节点(夹具内短消息唯一)。 */
    private static SceneNode findLineNodeWithText(SceneNode root, String text) {
        if (root.getSegments() != null) {
            StringBuilder builder = new StringBuilder();
            for (TextSegment segment : root.getSegments()) {
                builder.append(segment.getText());
            }
            if (builder.indexOf(text) >= 0) {
                return root;
            }
        }
        for (SceneNode child : root.__getChildren()) {
            SceneNode hit = findLineNodeWithText(child, text);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }
}
