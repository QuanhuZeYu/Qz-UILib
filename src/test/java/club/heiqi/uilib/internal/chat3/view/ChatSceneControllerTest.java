package club.heiqi.uilib.internal.chat3.view;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCardComposer;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * ChatSceneController 渲染层测试(S3b):双形态树结构/对齐/容器滚动绑定/淡出烘焙/过期移除。
 */
public class ChatSceneControllerTest {

    private static final long T0 = 1_700_000_000_000L;

    private static final SceneLayoutEngine LAYOUT = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));

    /** 每字符 2px 的确定性度量(§ 格式码对零宽)。 */
    private static final ChatLineLayouter.Measure FIXED = new ChatLineLayouter.Measure() {
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
            return effective * 2.0F;
        }

        @Override
        public int epoch() {
            return 1;
        }
    };

    private static final ChatMessageList.SegmentParser PARSER =
            new ChatMessageList.SegmentParser() {
        @Override
        public List<TextSegment> parse(String text, int baseColor) {
            TextStyle style = new TextStyle();
            style.setColor(baseColor);
            return Collections.singletonList(new TextSegment(text, style));
        }
    };

    private static ChatSceneController controller() {
        return new ChatSceneController(FIXED, new ChatSceneController.SelfNameProvider() {
            @Override
            public String selfName() {
                return "Alex";
            }
        }, PARSER);
    }

    private static SceneNode build(ChatSceneController controller, SceneRuntime rt) {
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        return root;
    }

    /** root → mount → list → 组节点列表(HUD 树)。 */
    private static List<SceneNode> hudGroups(SceneNode root) {
        SceneNode mount = root.__getChildren().get(0);
        SceneNode list = mount.__getChildren().get(0);
        return list.__getChildren();
    }

    @Test
    public void hudTreeBuildsGroupsWithAlignment() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0 - 1000));
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> hi"), 2, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);

        List<SceneNode> groups = hudGroups(root);
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals("他人组左对齐", AlignSelf.START, groups.get(0).getAlignSelf());
        Assert.assertEquals("自己组右对齐", AlignSelf.END, groups.get(1).getAlignSelf());

        SceneNode bobGroup = groups.get(0);
        Assert.assertEquals(2, bobGroup.__getChildren().size());
        SceneNode bobHeaderRow = bobGroup.__getChildren().get(0);
        Assert.assertEquals("组头 row = 名字 + 时间双节点", 2, bobHeaderRow.__getChildren().size());
        Assert.assertNotNull("名字段应带段流", bobHeaderRow.__getChildren().get(0).getSegments());
        SceneNode bobBubble = bobGroup.__getChildren().get(1);
        Assert.assertEquals("他人气泡深灰", ChatMarkdownSettings.getBubbleOtherArgb(),
                bobBubble.getBackgroundColor());
    }

    @Test
    public void systemGroupIsCenteredWithoutBubble() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("[公告] 维护"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);

        List<SceneNode> groups = hudGroups(root);
        Assert.assertEquals(1, groups.size());
        Assert.assertEquals("系统组居中", AlignSelf.CENTER, groups.get(0).getAlignSelf());
        SceneNode systemMessage = groups.get(0).__getChildren().get(0);
        Assert.assertEquals("系统消息无气泡背景", 0, systemMessage.getBackgroundColor());
    }

    @Test
    public void containerTreeBuildsOnChatOpen() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);

        controller.setChatOpen(true);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1);
        rt.flush();

        // POPPING 起:HUD 树清空(整窗隐藏,容器由输入屏幕绘制——避免双容器)
        SceneNode mount = root.__getChildren().get(0);
        Assert.assertEquals("打开后 HUD 树应为空", 0, mount.__getChildren().size());
    }

    @Test
    public void containerListMountsToExternalRegistry() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));

        // 外部容器列表:复用 ChatMessageList(容器形态)挂到调用方节点
        SceneNode list = SceneNode.column().setHitTestable(false);
        Map<SceneNode, ChatLineRecord> registry = new IdentityHashMap<SceneNode, ChatLineRecord>();
        ChatMessageList renderer = new ChatMessageList(PARSER);
        SceneListHandle handle = renderer.mount(rt, list, controller.groupsSignal(),
                ChatMessageList.Style.container(), registry, controller.frameMillisSignal());
        rt.flush();

        Assert.assertEquals("外部注册表应登记消息节点", 1, registry.size());
        handle.dispose();
    }

    @Test
    public void scrollOffsetSmoothsToHistoryScroll() {
        ChatSceneController controller = controller();
        controller.history().scrollBy(3);
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        // 平滑起点:动画刚开始,显示仍在底(120ms easeOutQuad,设计稿 §5.1)
        controller.tick(T0);
        rt.flush();
        Assert.assertEquals("平滑起点显示在底", 0, controller.scrollOffsetPx().intValue());
        // 中段(60/120):easeOut(0.5)=0.75 → 3×0.75=2.25 行 → round(2.25×18)=41
        controller.tick(T0 + 60L);
        rt.flush();
        Assert.assertEquals("平滑中段 = 3 行的 75%", 41, controller.scrollOffsetPx().intValue());
        // 完成:3 行 × 行高
        controller.tick(T0 + 120L);
        rt.flush();
        Assert.assertEquals("平滑完成 = 3 行", 3 * ChatMarkdownSettings.getChatLineHeightPx(),
                controller.scrollOffsetPx().intValue());
    }

    /** 灌入 count 条历史消息(时间倒序,index 0 = 最新)。 */
    private static void seedHistory(ChatSceneController controller, int count) {
        for (int i = 0; i < count; i++) {
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Bob> message " + i), i + 1, T0 - (count - i) * 1000L));
        }
    }

    /** 距底 ≤ ceil(36/18)=2 行(贴着底部):新消息到达 → 目标自动到底(贴底跟随),不计未读。 */
    @Test
    public void newMessageNearBottomSticksToBottom() {
        ChatSceneController controller = controller();
        seedHistory(controller, 10);
        controller.history().scrollBy(2);
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        controller.tick(T0);
        rt.flush();
        Assert.assertEquals("距底 2 行:目标归底", 0, controller.scrollOffsetPx().intValue());

        controller.history().append(new ChatLineRecord(new ChatComponentText("new"), 99, T0 + 1000L));
        controller.notifyDataChanged();
        controller.tick(T0 + 1000L);
        rt.flush();
        Assert.assertEquals("贴底跟随:显示保持底部", 0, controller.scrollOffsetPx().intValue());
        Assert.assertEquals("贴底新消息不计未读", 0, controller.unreadSignal().get().intValue());
    }

    /** 离开底部(距底 >2 行)的新消息:未读 +1(批到达按增量),不打断阅读。 */
    @Test
    public void newMessageAboveBottomIncrementsUnread() {
        ChatSceneController controller = controller();
        seedHistory(controller, 10);
        controller.history().scrollBy(5);
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        controller.tick(T0);
        rt.flush();
        controller.scrollOffsetPx(); // 触发滚动重算(历史基线校准:历史消息不误计)
        rt.flush();
        Assert.assertEquals("仅滚动不产生未读", 0, controller.unreadSignal().get().intValue());

        controller.history().append(new ChatLineRecord(new ChatComponentText("new1"), 99, T0 + 1000L));
        controller.notifyDataChanged();
        controller.tick(T0 + 1000L);
        rt.flush();
        controller.scrollOffsetPx();
        rt.flush();
        Assert.assertEquals("离开底部新消息 +1", 1, controller.unreadSignal().get().intValue());

        controller.history().append(new ChatLineRecord(new ChatComponentText("new2"), 98, T0 + 2000L));
        controller.notifyDataChanged();
        controller.tick(T0 + 2000L);
        rt.flush();
        controller.scrollOffsetPx();
        rt.flush();
        Assert.assertEquals("又一条 +1", 2, controller.unreadSignal().get().intValue());
    }

    /** 滚动回底(距底 ≤2 行)→ 未读清零(设计稿 §5.1:回底即已读)。 */
    @Test
    public void scrollBackToBottomClearsUnread() {
        ChatSceneController controller = controller();
        seedHistory(controller, 10);
        controller.history().scrollBy(5);
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        controller.tick(T0);
        rt.flush();
        controller.scrollOffsetPx(); // 基线校准
        rt.flush();
        controller.history().append(new ChatLineRecord(new ChatComponentText("new"), 99, T0 + 1000L));
        controller.notifyDataChanged();
        controller.tick(T0 + 1000L);
        rt.flush();
        controller.scrollOffsetPx();
        rt.flush();
        Assert.assertEquals("离开底部新消息 +1", 1, controller.unreadSignal().get().intValue());

        // 滚轮回底(距底 0 ≤ 2):未读清零
        controller.history().scrollBy(-5);
        controller.notifyDataChanged();
        controller.tick(T0 + 2000L);
        rt.flush();
        controller.scrollOffsetPx();
        rt.flush();
        Assert.assertEquals("回底未读清零", 0, controller.unreadSignal().get().intValue());
    }

    /** 拖动接管闭环:snapTo 直通(拖动手感即时)→ releaseDrag 恢复 120ms 平滑(滚轮/回底)。 */
    @Test
    public void dragSnapToPassesThroughThenReleaseRestoresSmoothing() {
        ChatSceneController controller = controller();
        seedHistory(controller, 10);
        controller.history().scrollBy(5);
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        controller.tick(T0);
        rt.flush();
        Assert.assertEquals("平滑起点在底", 0, controller.scrollOffsetPx().intValue());

        // 拖动开始:snapTo 当前显示行(0)→ 直通
        controller.smoothScroll().snapTo(0);
        // 拖动 MOVE:目标 6 行 → 直通中显示直接到位(无 120ms 延迟)
        controller.history().scrollBy(1);
        controller.notifyDataChanged();
        controller.tick(T0 + 30L);
        rt.flush();
        Assert.assertEquals("直通中显示=目标", 6 * ChatMarkdownSettings.getChatLineHeightPx(),
                controller.scrollOffsetPx().intValue());

        // 拖动结束后的滚轮(非拖动来源):releaseDrag → 再次滚动恢复平滑
        controller.smoothScroll().releaseDrag();
        controller.history().scrollBy(-3); // 目标 6→3
        controller.notifyDataChanged();
        controller.tick(T0 + 60L);
        rt.flush();
        Assert.assertEquals("释放后起点=当前显示 6 行", 6 * ChatMarkdownSettings.getChatLineHeightPx(),
                controller.scrollOffsetPx().intValue());
        controller.tick(T0 + 60L + 120L);
        rt.flush();
        Assert.assertEquals("释放后平滑完成=3 行", 3 * ChatMarkdownSettings.getChatLineHeightPx(),
                controller.scrollOffsetPx().intValue());
    }

    @Test
    public void hudFadeBakesAlphaIntoBubbleBackground() {
        // TB1:常驻模式默认开启(无淡出);本测试验证旧 TTL 淡出行为 → 临时关闭常驻
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);

            SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);
            Assert.assertEquals("初始全量 alpha", 0xF2, (bubble.getBackgroundColor() >>> 24) & 0xFF);

            // 淡出中段:easeInQuad p=0.5 → 淡出因子 191(floor(255×0.75))
            controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis()
                    + ChatMarkdownSettings.getHudFadeMillis() / 2);
            rt.flush();
            // 组合语义:基础 alpha F2(242) × 淡出因子 191 → 181(0xB5,整数截断)
            Assert.assertEquals("淡出中段 alpha 组合截断", 0xB5, (bubble.getBackgroundColor() >>> 24) & 0xFF);
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    @Test
    public void hudRootUsesDynamicViewportWidth() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        controller.setHostViewport(1600, 900);
        SceneNode root = build(controller, rt);

        Assert.assertEquals("窗口宽 = 视口宽 × 1/4(360 封顶)",
                ChatMarkdownSettings.chatWidthFor(1600), root.getPreferredWidth());
    }

    @Test
    public void hitTestReturnsComponentInHudTree() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        // T7 回归:chatWidthFor 新增 <360 → 视口×0.5 窄屏分支后,buildContent 时未设置视口
        // (=0)会得到 1px 根宽,命中盒随之 1px 宽;视口必须先行注入(与真机接线层每帧先
        // setHostViewport 再触发布局的时序一致)。
        controller.setHostViewport(400, 300);
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        LAYOUT.layout(root, new Constraints(400));

        // HUD 树:root → mount → list → group → (组头, 消息节点)
        SceneNode messageNode = hudGroups(root).get(0).__getChildren().get(1);

        AnchorRect rootBox = SceneGeometry.absoluteBox(root, 0, 0);
        int margin = ChatMarkdownSettings.getChatMarginPx();
        int rootAbsX = margin;
        int rootAbsY = 300 - margin - rootBox.getHeight();
        AnchorRect box = SceneGeometry.absoluteBox(messageNode, rootAbsX, rootAbsY);

        IChatComponent hit = controller.hitTest(box.getX() + 2, box.getY() + 2);
        Assert.assertNotNull("消息矩形内应命中", hit);
        Assert.assertEquals("<Bob> hello", hit.getUnformattedText());

        Assert.assertNull("矩形外不应命中", controller.hitTest(0, 0));
    }

    @Test
    public void hudGroupEnterAnimatesOpacityAndTranslateY() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        SceneNode group = hudGroups(root).get(0);

        // 出生帧(progress=0):translateY=+8、opacity=0(设计稿 §4.1:enter 180ms easeOutCubic 双通道)
        controller.tick(T0);
        rt.flush();
        Assert.assertEquals("enter 起点 opacity=0", 0.0F, group.getOpacity(), 0.001F);
        Assert.assertEquals("enter 起点 translateY=+8", 8.0F, group.getTransform().translateY, 0.001F);

        // 中段(progress=0.5):easeOutCubic(0.5)=0.875 → opacity=0.875、translateY=8×(1-0.875)=1
        controller.tick(T0 + ChatMarkdownSettings.getEnterAnimMillis() / 2);
        rt.flush();
        Assert.assertEquals("enter 中段 opacity=easeOutCubic(0.5)", 0.875F, group.getOpacity(), 0.001F);
        Assert.assertEquals("enter 中段 translateY=8×(1-0.875)", 1.0F, group.getTransform().translateY, 0.001F);

        // 结束帧:opacity=1、transform 恒等(渲染快速路径:零 PUSH_OPACITY/PUSH_TRANSFORM)
        controller.tick(T0 + ChatMarkdownSettings.getEnterAnimMillis());
        rt.flush();
        Assert.assertEquals("enter 结束 opacity=1", 1.0F, group.getOpacity(), 0.001F);
        Assert.assertTrue("enter 结束 transform 恒等", group.getTransform().isIdentity());
    }

    @Test
    public void hudRootOpacityFollowsStateMachine() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);

        // HUD 稳定:opacity=1
        controller.tick(T0);
        rt.flush();
        Assert.assertEquals("HUD 稳定 opacity=1", 1.0F, root.getOpacity(), 0.001F);

        // 打开 → COLLAPSING(收起 HUD 气泡):opacity 1→0,与 translate 同 progress(p=0.5 → 1-easeOut(0.5)=0.25)
        controller.setChatOpen(true);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() / 2);
        rt.flush();
        Assert.assertEquals("COLLAPSING 中段 opacity=0.25", 0.25F, root.getOpacity(), 0.001F);

        // 边界 tick:COLLAPSING 时长耗尽,状态机切到 POPPING 并锚定 start(本帧 progress=0,不断言)
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis());

        // POPPING(容器弹出):opacity 0→1,p=0.5 → easeOutBack(0.5)=1.005 但 opacity 通道 clamp01 → 1.0
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis()
                + ChatMarkdownSettings.getPopAnimMillis() / 2);
        rt.flush();
        Assert.assertEquals("POPPING 中段 opacity=clamp01(1.005)=1.0", 1.0F, root.getOpacity(), 0.001F);

        // CONTAINER 稳定:opacity=1
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis()
                + ChatMarkdownSettings.getPopAnimMillis());
        rt.flush();
        Assert.assertEquals("CONTAINER 稳定 opacity=1", 1.0F, root.getOpacity(), 0.001F);

        // 关闭 → CLOSING:opacity 1→0(p=0.5 → 1-easeOut(0.5)=0.25;closing 独立时长 140ms)
        controller.setChatOpen(false);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis()
                + ChatMarkdownSettings.getPopAnimMillis()
                + ChatMarkdownSettings.getClosingAnimMillis() / 2);
        rt.flush();
        Assert.assertEquals("CLOSING 中段 opacity=0.25", 0.25F, root.getOpacity(), 0.001F);
    }

    @Test
    public void expiredHudGroupsAreRemovedFromTree() {
        // TB1:常驻模式(TTL 移除关闭);本测试验证旧 TTL 移除行为 → 临时关闭常驻
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            Assert.assertEquals(1, hudGroups(root).size());

            // 完全过期(存活 + 淡出结束)
            controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis()
                    + ChatMarkdownSettings.getHudFadeMillis() + 1);
            rt.flush();
            Assert.assertEquals("过期组应从树中移除", 0, hudGroups(root).size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    // ==================== TB1:HUD 常驻消息(默认开启) ====================

    /** 常驻模式:消息不因 TTL 过期移除(多次 tick 越过存活+淡出窗口仍常驻)。 */
    @Test
    public void persistedHudMessagesSurviveTtl() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(true);
        try {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        Assert.assertEquals(1, hudGroups(root).size());

        // 完全过期(存活 + 淡出结束)+ 更长窗口:常驻模式不触发过期移除
        controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis()
                + ChatMarkdownSettings.getHudFadeMillis() + 1);
        rt.flush();
        Assert.assertEquals("常驻模式:TTL 过期不移除", 1, hudGroups(root).size());
        controller.tick(T0 + 10 * ChatMarkdownSettings.getHudTtlMillis());
        rt.flush();
        Assert.assertEquals("常驻模式:更长时间后仍不移除", 1, hudGroups(root).size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /** 常驻模式:越过 TTL 淡出窗口气泡 alpha 仍满(不淡出);enter 出生动画保留。 */
    @Test
    public void persistedHudMessagesKeepFullAlphaAcrossTtl() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(true);
        try {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);

        // 淡出中段/结束:常驻模式无淡出烘焙,alpha 恒为气泡基础 alpha F2
        controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis()
                + ChatMarkdownSettings.getHudFadeMillis() / 2);
        rt.flush();
        Assert.assertEquals("常驻模式:淡出中段 alpha 仍满", 0xF2,
                (bubble.getBackgroundColor() >>> 24) & 0xFF);
        controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis()
                + ChatMarkdownSettings.getHudFadeMillis());
        rt.flush();
        Assert.assertEquals("常驻模式:淡出结束 alpha 仍满", 0xF2,
                (bubble.getBackgroundColor() >>> 24) & 0xFF);
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /** 常驻模式:50% 视口高裁剪仍生效(与 hudStackHeightTrimsOldestGroups 同口径,刷屏 20 组 → 2 组)。 */
    @Test
    public void persistedHudMessagesStillTrimByHeight() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(true);
        try {
        ChatSceneController controller = controller();
        controller.setHostViewport(320, 400);
        seedFloodHistory(controller, 20);
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        controller.tick(T0);
        rt.flush();

        // 常驻模式下高度裁剪照常收敛到上限约束(2 组)
        Assert.assertEquals("常驻模式高度裁剪仍生效", 2, hudGroups(root).size());

        // 越过 TTL 后仍只受高度裁剪约束(无 TTL 清空;被裁组不复活)
        controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis()
                + ChatMarkdownSettings.getHudFadeMillis() + 1);
        rt.flush();
        Assert.assertEquals("常驻模式:树中仍为高度裁剪结果", 2, hudGroups(root).size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /** 常驻语义:打开聊天 → 关闭后消息完整回归 HUD 树(不做任何移除,wall clock 越过 TTL 亦然)。 */
    @Test
    public void persistedHudMessagesReturnAfterChatOpenCloseLoop() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(true);
        try {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        Assert.assertEquals("初始 HUD 树 1 组", 1, hudGroups(root).size());

        // 打开:COLLAPSING → POPPING 起容器阶段,HUD 树清空(容器由输入屏幕绘制)
        controller.setChatOpen(true);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1);
        rt.flush();
        SceneNode mount = root.__getChildren().get(0);
        Assert.assertEquals("打开后 HUD 树清空", 0, mount.__getChildren().size());

        // 容器稳定
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1
                + ChatMarkdownSettings.getPopAnimMillis());
        rt.flush();

        // 关闭:CLOSING → HUD 树重建;期间 wall clock 越过 TTL+淡出窗口,常驻模式不移除
        long closedAt = T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1
                + ChatMarkdownSettings.getPopAnimMillis()
                + ChatMarkdownSettings.getClosingAnimMillis() + 1;
        controller.setChatOpen(false);
        controller.tick(closedAt);
        rt.flush();
        Assert.assertEquals("关闭后消息完整回归 HUD 树", 1, hudGroups(root).size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /** 重复字符串(count 个 c;Java 8 无 String.repeat)。 */
    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    /** 灌入 20 组刷屏历史:每组 1 条 160 字符单发送者消息(独立发送者防合并,时刻全在 TTL 内)。 */
    private static void seedFloodHistory(ChatSceneController controller, int groupCount) {
        String body = repeat('x', 160);
        // 时刻正序 append(最旧先入):ChatHistory 头插后快照 index 0 = 最新时刻,与
        // MessageGrouper「index 0 = 最新」语义一致,组序列按时间正序输出
        // (T7 回归:先前逆序 append 使快照 index 0 = 最旧,trim 反向累计从最旧组起,
        // 阈值只推进到被裁倒数第二组,刷屏裁剪结果 18 组而非 2 组)。
        for (int i = 1; i <= groupCount; i++) {
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Bob" + i + "> " + body), i, T0 - (groupCount - i) * 400L));
        }
        controller.notifyDataChanged();
    }

    /**
     * HUD 堆叠高度上限(设计稿 §3.1):刷屏历史超 0.5H 时最旧组立即剔除(结构级、不等 TTL),
     * 树中组数收敛到上限约束;再 tick 幂等不漂移。
     *
     * <p>口径:视口 320×400 → chatWidth = 0.5×320 = 160(§5.5 极窄分支),maxLine = 140;
     * FIXED 度量 2px/字符 → 70 字符/行 → 160 字符 = 3 行 → 组高 = 16(组头行高)+2×5+3×18 = 80;
     * 0.5×400 = 200px → 2 组 = 164 ≤ 200,3 组 = 248 &gt; 200。</p>
     */
    @Test
    public void hudStackHeightTrimsOldestGroups() {
        ChatSceneController controller = controller();
        controller.setHostViewport(320, 400);
        seedFloodHistory(controller, 20);
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        controller.tick(T0);
        rt.flush();

        // 上限约束(同口径):2 组 ≤ 0.5H,3 组超限(组头按实际渲染行高 16 计,P3-9)
        int groupHeight = ChatMarkdownSettings.getChatHeaderRowHeightPx()
                + 2 * ChatMarkdownSettings.getBubblePaddingY()
                + 3 * ChatMarkdownSettings.getChatLineHeightPx();
        int gap = ChatMarkdownSettings.getGroupGapHudPx();
        int maxHeight = (int) Math.round(400 * ChatMarkdownSettings.getHudMaxHeightRatio());
        Assert.assertTrue("2 组应在上限内", 2 * groupHeight + gap <= maxHeight);
        Assert.assertTrue("3 组应超上限", 3 * groupHeight + 2 * gap > maxHeight);

        List<SceneNode> groups = hudGroups(root);
        Assert.assertEquals("刷屏 20 组裁到上限约束内", 2, groups.size());
        Assert.assertTrue("最旧组已移除:树中第 1 组应为 Bob19(历史倒数第二)",
                "§lBob19".equals(groups.get(0).__getChildren().get(0).__getChildren().get(0)
                        .getSegments().get(0).getText()));
        Assert.assertTrue("最新组保留在底:树中末组应为 Bob20",
                "§lBob20".equals(groups.get(1).__getChildren().get(0).__getChildren().get(0)
                        .getSegments().get(0).getText()));

        // 幂等:无新数据再 tick,树不变(裁剪阈值只进不退,不振荡)
        controller.tick(T0 + 1000L);
        rt.flush();
        Assert.assertEquals("再 tick 树组数不变", 2, hudGroups(root).size());
    }

    /** 最新单组自身超限(超长消息,10 行 206px > 0.5×400=200):至少保留最新一组,不空屏。 */
    @Test
    public void hudHeightTrimKeepsNewestGroupWhenSingleGroupOverflows() {
        ChatSceneController controller = controller();
        controller.setHostViewport(320, 400);
        // 640 字符 → 1280px ÷ 140px/行 → 10 行 → 组高 = 16(组头行高)+2×5+10×18 = 206 > 200(0.5H)
        controller.history().append(new ChatLineRecord(
                new ChatComponentText("<Bob> " + repeat('x', 640)), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        controller.tick(T0);
        rt.flush();
        Assert.assertEquals("最新单组超限仍保留(不空屏)", 1, hudGroups(root).size());
    }

    /** 高度裁剪仅作用于 HUD 形态:容器形态组列表全量(20 组),不受裁剪阈值影响。 */
    @Test
    public void hudHeightTrimDoesNotAffectContainer() {
        ChatSceneController controller = controller();
        controller.setHostViewport(320, 400);
        seedFloodHistory(controller, 20);
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        controller.tick(T0);
        rt.flush();
        Assert.assertTrue("HUD 高度裁剪应已生效", hudGroups(root).size() < 20);

        // 打开聊天 → POPPING(容器阶段):HUD 树清空,组数据源全量(applyTtl=false 无过滤)
        controller.setChatOpen(true);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1);
        // T7 回归:groupsSignal() 每次新建 Computed,初值为 null、首次 flush 才物化;
        // 必须在 flush 前创建并消费同一实例,flush 后新建再 get() 会得到 null。
        ReadableSignal<List<ChatCardComposer.ComposedGroup>> containerGroups =
                controller.groupsSignal();
        rt.flush();
        Assert.assertEquals("容器形态不受高度裁剪影响(全量)", 20,
                containerGroups.get().size());
    }

    /** 形态动画三段式(设计稿 §4.1):根 transform 双通道——COLLAPSING 与 POPPING 对称反向、CLOSING 独立。 */
    @Test
    public void rootTransformFollowsStateMachine() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);

        // HUD 稳定:恒等(渲染快速路径)
        controller.tick(T0);
        rt.flush();
        Assert.assertTrue("HUD 稳定 transform 恒等", root.getTransform().isIdentity());

        // COLLAPSING 中段(与 POPPING 对称反向):p=0.5,easeOut(0.5)=0.75
        // translateY 0→+24 = 18、scale 1→0.96 = 0.97、opacity 1→0 = 0.25,origin 左下(0,1)
        controller.setChatOpen(true);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() / 2);
        rt.flush();
        Transform t = root.getTransform();
        Assert.assertEquals("COLLAPSING translateY=24×0.75", 18.0F, t.translateY, 0.001F);
        Assert.assertEquals("COLLAPSING scaleX=1−0.04×0.75", 0.97F, t.scaleX, 0.001F);
        Assert.assertEquals("COLLAPSING scaleY=1−0.04×0.75", 0.97F, t.scaleY, 0.001F);
        Assert.assertEquals("COLLAPSING origin 左", 0.0F, t.originXRatio, 0.001F);
        Assert.assertEquals("COLLAPSING origin 下", 1.0F, t.originYRatio, 0.001F);
        Assert.assertEquals("COLLAPSING opacity=1−0.75", 0.25F, root.getOpacity(), 0.001F);

        // 边界 tick:COLLAPSING 时长耗尽切 POPPING(本帧 progress=0,不断言)
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis());

        // POPPING 中段:easeOutBack(0.5,c=1.04 默认)=1.005(overshoot,允许 >1)
        // translateY=24×(1−1.005)=−0.12(轻微超调负向再回弹)、scale=0.96+0.04×1.005=1.0002、
        // opacity=clamp01(1.005)=1.0(opacity 通道不允许超 1)
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis()
                + ChatMarkdownSettings.getPopAnimMillis() / 2);
        rt.flush();
        t = root.getTransform();
        Assert.assertEquals("POPPING translateY=24×(1−1.005)", -0.12F, t.translateY, 0.001F);
        Assert.assertEquals("POPPING scaleX=0.96+0.04×1.005", 1.0002F, t.scaleX, 0.001F);
        Assert.assertEquals("POPPING scaleY=0.96+0.04×1.005", 1.0002F, t.scaleY, 0.001F);
        Assert.assertEquals("POPPING origin 左", 0.0F, t.originXRatio, 0.001F);
        Assert.assertEquals("POPPING origin 下", 1.0F, t.originYRatio, 0.001F);
        Assert.assertEquals("POPPING opacity=clamp01(1.005)=1.0", 1.0F, root.getOpacity(), 0.001F);

        // CONTAINER 稳定:恒等 + opacity=1
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis()
                + ChatMarkdownSettings.getPopAnimMillis());
        rt.flush();
        Assert.assertTrue("CONTAINER 稳定 transform 恒等", root.getTransform().isIdentity());
        Assert.assertEquals("CONTAINER 稳定 opacity=1", 1.0F, root.getOpacity(), 0.001F);

        // CLOSING 中段(closing 独立时长 140ms):p=0.5,easeOut(0.5)=0.75
        // translateY 0→+12 = 9(下滑消失)、scale 不参与恒 1、opacity 1→0 = 0.25
        controller.setChatOpen(false);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis()
                + ChatMarkdownSettings.getPopAnimMillis()
                + ChatMarkdownSettings.getClosingAnimMillis() / 2);
        rt.flush();
        t = root.getTransform();
        Assert.assertEquals("CLOSING translateY=12×0.75", 9.0F, t.translateY, 0.001F);
        Assert.assertEquals("CLOSING scaleX 不参与", 1.0F, t.scaleX, 0.001F);
        Assert.assertEquals("CLOSING scaleY 不参与", 1.0F, t.scaleY, 0.001F);
        Assert.assertEquals("CLOSING opacity=0.25", 0.25F, root.getOpacity(), 0.001F);

        // CLOSING 结束 → HUD:恒等 + opacity=1
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis()
                + ChatMarkdownSettings.getPopAnimMillis()
                + ChatMarkdownSettings.getClosingAnimMillis());
        rt.flush();
        Assert.assertTrue("CLOSING 结束回 HUD transform 恒等", root.getTransform().isIdentity());
        Assert.assertEquals("CLOSING 结束回 HUD opacity=1", 1.0F, root.getOpacity(), 0.001F);
    }
}