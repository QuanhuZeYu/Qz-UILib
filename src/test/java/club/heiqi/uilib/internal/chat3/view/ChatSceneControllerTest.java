package club.heiqi.uilib.internal.chat3.view;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
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
    public void hudBudgetPathKeepsFullAlphaWhileVisible() {
        // 新机制:预算路径(非 persist)合成期 alpha 恒满(淡出撤出合成,由渲染层按
        // HUD 可见时钟每帧驱动);本测试验证 compose 输出预算注入与恒满 alpha
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);

            SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);
            Assert.assertEquals("预算路径合成期 alpha 恒满", 0xF2,
                    (bubble.getBackgroundColor() >>> 24) & 0xFF);
            // 预算注入:budget = 默认 TTL,起点 = 首次进入时的可见时钟(首帧 flush = 0)
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups =
                    controller.groupsSignal();
            Assert.assertEquals("预算 = 默认 TTL", ChatMarkdownSettings.getHudTtlMillis(),
                    groups.get().get(0).getBudgetMillis());
            Assert.assertEquals("可见起点 = 首次进入帧", 0L,
                    groups.get().get(0).getHudVisibleStartMillis());

            // 预算窗口内推进帧(可见时钟累计):alpha 仍满(不在合成期烘焙淡出)
            controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis() / 2);
            rt.flush();
            SceneNode bubbleAfter = hudGroups(root).get(0).__getChildren().get(1);
            Assert.assertEquals("预算窗口内 alpha 仍满", 0xF2,
                    (bubbleAfter.getBackgroundColor() >>> 24) & 0xFF);
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

        // 期望原点与实现同源(SceneAnchorResolver 视口锚定数学,host 未运行时即此路径)，
        // 不再在测试里手算第二份公式。
        AnchorRect rootBox = SceneGeometry.absoluteBox(root, 0, 0);
        club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver.ResolvedViewport origin =
                club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver.resolveViewport(
                        false, true, 400, 300, rootBox.getWidth(), rootBox.getHeight(),
                        ChatMarkdownSettings.getChatMarginPx(), 0, 0, 0, 0, 0);
        AnchorRect box = SceneGeometry.absoluteBox(messageNode, origin.getX(), origin.getY());

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

    /**
     * HUD 渐入衔接(2026-08-29 用户设计语义「关闭 - 关闭动画 - 关闭完成 - HUD渐入动画 -
     * 动画完成」):关闭聊天框(HUD 衔接重建)后根级 opacity 从 0 开始
     * {@link club.heiqi.uilib.internal.chat3.view.Animator#emergeIn(float)}(sqrt 先快后慢)渐入
     * (替代关屏瞬间气泡跳现=闪烁观感;2026-08-29 真机取证:原 easeOutCubic 前段近乎不可见,
     * 长渐入被感知为「消失-出现」,改 sqrt 快速浮现后缓慢稳定),完成即复位(后续 HUD 帧恒 1 快速路径);
     * 非衔接路径(HUD 稳定)恒 1,零参与。
     */
    @Test
    public void hudFadeInPlaysAfterCloseTransition() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            Assert.assertEquals("HUD 稳定(非衔接)恒 1", 1.0F, root.getOpacity(), 0.001F);

            // 打开聊天框(容器形态)
            controller.setChatOpen(true);
            controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1);
            controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1
                    + ChatMarkdownSettings.getPopAnimMillis());
            rt.flush();
            Assert.assertEquals("容器稳定 opacity=1", 1.0F, root.getOpacity(), 0.001F);

            // 关闭完成(forceHud 真机路径)→ 衔接重建帧:渐入起点 opacity=0
            controller.closeToHudImmediately();
            long frame = T0 + 3000L;
            controller.tick(frame);
            rt.flush();
            Assert.assertEquals("关闭完成首帧 = 渐入起点 0", 0.0F, root.getOpacity(), 0.001F);

            // 渐入中段:emergeIn(0.5) = sqrt(0.5) ≈ 0.7071(先快后慢:中段已过 ~71%)
            controller.tick(frame + ChatMarkdownSettings.getHudFadeInAnimMillis() / 2);
            rt.flush();
            Assert.assertEquals("渐入中段 = emergeIn(0.5)=sqrt(0.5)", 0.70711F, root.getOpacity(), 0.001F);

            // 完成:恒 1,且复位(后续帧仍 1)
            controller.tick(frame + ChatMarkdownSettings.getHudFadeInAnimMillis() + 1);
            rt.flush();
            Assert.assertEquals("渐入完成 opacity=1", 1.0F, root.getOpacity(), 0.001F);
            controller.tick(frame + ChatMarkdownSettings.getHudFadeInAnimMillis() + 1000L);
            rt.flush();
            Assert.assertEquals("完成复位后恒 1(快速路径)", 1.0F, root.getOpacity(), 0.001F);
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * 窗体过渡期内容冻结(2026-08-29 窗体动画抽象):渐入(根级 opacity 0→1)进行中
     * 到达的新消息组不合成(composeAll 返回稳态快照)——树/布局/enter 一律不响应,
     * 窗体整体动画独占画面;渐入完成后冻结解除,积压消息一次性应用(forEach diff
     * 差量挂载),新组按稳态 enter 正常入场(enter=true)。
     */
    @Test
    public void fadeInPeriodFreezesContentUntilComplete() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> old"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();

            // 打开 → 容器
            controller.setChatOpen(true);
            controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1);
            controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1
                    + ChatMarkdownSettings.getPopAnimMillis());
            // 关闭衔接 → 渐入开始(过渡窗口开启)
            controller.closeToHudImmediately();
            long frame = T0 + 3000L;
            controller.tick(frame);
            rt.flush();
            Assert.assertEquals("渐入起点 0", 0.0F, root.getOpacity(), 0.001F);

            // 渐入中段(200ms < 400ms):消息到达 → 冻结(不合成,列表仍旧)
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Cara> mid"), 2, T0 + 3100L));
            controller.notifyDataChanged();
            controller.tick(frame + ChatMarkdownSettings.getHudFadeInAnimMillis() / 2);
            rt.flush();
            Assert.assertEquals("渐入中段仍进行(<1,sqrt(0.5))", 0.70711F, root.getOpacity(), 0.001F);
            Assert.assertEquals("渐入期间消息冻结(列表仍旧 1 组)", 1, groups.get().size());

            // 渐入完成 → 冻结解除,积压一次性应用,新组按稳态 enter 入场
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Alex> live"), 3, T0 + 4000L));
            controller.notifyDataChanged();
            controller.tick(frame + ChatMarkdownSettings.getHudFadeInAnimMillis() + 100L);
            rt.flush();
            Assert.assertEquals("渐入完成 opacity=1", 1.0F, root.getOpacity(), 0.001F);
            Assert.assertEquals("解冻后积压消息一次性应用(3 组)", 3, groups.get().size());
            Assert.assertEquals("解冻后新组 enter=true(稳态入场)", true,
                    groups.get().get(2).isEnterOnMount());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * 容器信号恒全量(2026-08-31 真机「打开动画文字瞬间刷出」回归锚点):预算耗尽被 HUD
     * 过滤剔除的消息,在打开方向 COLLAPSING 阶段(isHudPhase=true)仍应出现在容器信号中
     * ——容器列表挂容器信号,打开瞬间即全量呈现,文字随容器弹出动画一同淡入,不再等待
     * POPPING 切换才物化。
     */
    @Test
    public void containerSignalStaysFullCompositionDuringCollapsing() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Bob> old"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);

            // HUD 可见时钟逐帧推进(首帧定锚不累计,其后每帧 +1000ms):14 帧 = 可见 13000
            // > 预算 12000 + 淡出 800,过期队首弹出(与 expiredHudGroupsAreRemovedFromTree 同节奏)
            for (int i = 1; i <= 14; i++) {
                controller.tick(T0 + i * 1000L);
                rt.flush();
            }
            Assert.assertEquals("预算耗尽后 HUD 信号剔除该组", 0,
                    controller.groupsSignal().get().size());

            // 打开 → COLLAPSING(isHudPhase=true,HUD 信号仍过滤;容器信号恒全量)
            // setChatOpen 以当前 frameMillis(T0+14000)锚定 COLLAPSING,推进须从锚点之后起算
            // (collapse=150ms:14050 帧进度 1/3 仍 COLLAPSING)
            controller.setChatOpen(true);
            controller.tick(T0 + 14_050L);
            rt.flush();
            Assert.assertEquals("COLLAPSING 阶段 HUD 信号仍过滤(0 组)", 0,
                    controller.groupsSignal().get().size());
            Assert.assertEquals("COLLAPSING 阶段容器信号全量(1 组)", 1,
                    controller.containerGroupsSignal().get().size());

            // POPPING 后 HUD 信号也切全量,两者一致(容器列表文字自此可见,无延迟);
            // phaseSignal 帧末提交,POPPING 开始帧读旧值(COLLAPSING),再推进一帧后提交生效
            controller.tick(T0 + 14_000L + ChatMarkdownSettings.getCollapseAnimMillis() + 1L);
            controller.tick(T0 + 14_000L + ChatMarkdownSettings.getCollapseAnimMillis() + 2L);
            rt.flush();
            Assert.assertEquals("POPPING 后 HUD 信号全量(1 组)", 1,
                    controller.groupsSignal().get().size());
            Assert.assertEquals("POPPING 后容器信号仍全量(1 组)", 1,
                    controller.containerGroupsSignal().get().size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * 容器信号冻结快照独立(2026-08-31):关闭衔接渐入期间(fadeInActive)过渡冻结生效时,
     * 容器信号返回容器稳态快照引用(关闭动画期间容器内容冻结,引用稳定零重建);
     * 解冻后按 contentVersion 重算一次性应用。与 HUD 信号快照相互独立。
     */
    @Test
    public void containerSignalFreezesDuringTransition() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Bob> old"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> containerGroups =
                    controller.containerGroupsSignal();

            // 打开 → 容器稳定(容器信号 1 组,记录稳态快照引用)
            controller.setChatOpen(true);
            controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1L);
            controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1L
                    + ChatMarkdownSettings.getPopAnimMillis());
            rt.flush();
            List<ChatCardComposer.ComposedGroup> steady = containerGroups.get();
            Assert.assertEquals("容器稳态 1 组", 1, steady.size());

            // 关闭衔接 → 渐入开始(过渡窗口开启,fadeInActive → 冻结)
            controller.closeToHudImmediately();
            long frame = T0 + 3000L;
            controller.tick(frame);
            rt.flush();

            // 渐入中段消息到达:容器信号冻结,返回稳态快照引用(仍旧 1 组)
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Cara> mid"), 2, T0 + 3100L));
            controller.notifyDataChanged();
            controller.tick(frame + ChatMarkdownSettings.getHudFadeInAnimMillis() / 2);
            rt.flush();
            List<ChatCardComposer.ComposedGroup> frozen = containerGroups.get();
            Assert.assertSame("冻结期容器信号返回稳态快照引用", steady, frozen);
            Assert.assertEquals("冻结期容器信号仍旧 1 组", 1, frozen.size());

            // 渐入完成 → 解冻,积压消息一次性应用(2 组)
            controller.tick(frame + ChatMarkdownSettings.getHudFadeInAnimMillis() + 100L);
            rt.flush();
            Assert.assertEquals("解冻后容器信号一次性应用(2 组)", 2,
                    containerGroups.get().size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    @Test
    public void expiredHudGroupsAreRemovedFromTree() {
        // 新时钟驱动:可见时钟帧间 delta 夹取 1s,预算+淡出窗(默认 12s+0.8s)需逐帧
        // 推进(每帧 ≤1000ms);预算耗尽 + 淡出窗结束 → 队首弹出、结构级移除
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            Assert.assertEquals(1, hudGroups(root).size());

            // 逐帧推进:首帧仅定锚(HudVisibleClock 首帧不累计),其后每帧 +1000ms;
            // 14 帧 = 可见 13000 > 预算 12000 + 淡出 800(13 帧仅 12000,淡出窗未走完)
            for (int i = 1; i <= 14; i++) {
                controller.tick(T0 + 1000L * i);
                rt.flush();
            }
            Assert.assertEquals("过期组应从树中移除", 0, hudGroups(root).size());
            // 不复活:继续推帧仍空
            controller.tick(T0 + 15_000L);
            rt.flush();
            Assert.assertEquals("过期移除后不复活", 0, hudGroups(root).size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    // ==================== TB1:HUD 常驻消息(默认开启) ====================

    /** 常驻模式:消息不因预算过期移除(可见时钟逐帧越过预算+淡出窗口仍常驻)。 */
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

        // 可见时钟逐帧越过预算+淡出窗口(13 帧 = 13000ms)与更长窗口(30 帧):
        // 常驻模式不触发过期移除
        for (int i = 1; i <= 13; i++) {
            controller.tick(T0 + 1000L * i);
            rt.flush();
        }
        Assert.assertEquals("常驻模式:预算过期不移除", 1, hudGroups(root).size());
        for (int i = 14; i <= 30; i++) {
            controller.tick(T0 + 1000L * i);
            rt.flush();
        }
        Assert.assertEquals("常驻模式:更长时间后仍不移除", 1, hudGroups(root).size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /** 常驻模式:可见时钟越过预算+淡出窗口气泡 alpha 仍满(不淡出);enter 出生动画保留。 */
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

        // 逐帧推进越过预算窗口(13 帧 = 13000ms)/更长窗口:常驻模式无淡出烘焙,
        // alpha 恒为气泡基础 alpha F2
        for (int i = 1; i <= 13; i++) {
            controller.tick(T0 + 1000L * i);
            rt.flush();
        }
        Assert.assertEquals("常驻模式:越过预算窗 alpha 仍满", 0xF2,
                (bubble.getBackgroundColor() >>> 24) & 0xFF);
        controller.tick(T0 + 30_000L);
        rt.flush();
        Assert.assertEquals("常驻模式:更长窗口 alpha 仍满", 0xF2,
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
        // 容器→HUD 衔接 → 渐入起点(2026-08-29 HUD 渐入动画语义:关闭完成不跳现)
        Assert.assertEquals("CLOSING 结束回 HUD = 渐入起点 0", 0.0F, root.getOpacity(), 0.001F);
    }

    // ==================== HUD 显示预算机制(L3) ====================

    /**
     * 预算冻结:消息只在 HUD 可见时消耗预算。聊天框打开(容器阶段)期间 wall-clock
     * 大幅越过预算+淡出窗,可见时钟不推进;关闭后用尽剩余预算继续渲染。
     */
    @Test
    public void hudBudgetFreezesWhileChatOpen() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        Assert.assertEquals(1, hudGroups(root).size());

        // HUD 可见 5 帧(首帧定锚不累计,可见时钟 = 4000):预算消耗 4000/12000,消息仍在
        for (int i = 1; i <= 5; i++) {
            controller.tick(T0 + 1000L * i);
            rt.flush();
        }
        Assert.assertEquals(1, hudGroups(root).size());

        // 打开聊天:粗帧 1s 直接跳过 COLLAPSING(150ms)→ POPPING;容器阶段可见时钟冻结
        controller.setChatOpen(true);
        controller.tick(T0 + 6000L); // COLLAPSING→POPPING
        controller.tick(T0 + 7000L); // POPPING→CONTAINER
        rt.flush();
        // 容器阶段长时间推进(wall 越过预算+淡出窗):HUD 树清空(容器由输入屏绘制)
        for (int i = 8; i <= 27; i++) {
            controller.tick(T0 + 1000L * i);
            rt.flush();
        }
        SceneNode mount = root.__getChildren().get(0);
        Assert.assertEquals("打开后 HUD 树清空", 0, mount.__getChildren().size());

        // 关闭聊天(CLOSING 不推进可见时钟)→ 回 HUD:预算冻结,消息完整回归(不因
        // 打开期间的 wall-clock 过期——旧机制下 27s 墙钟早已移除)
        controller.setChatOpen(false);
        controller.tick(T0 + 28_000L);
        rt.flush();
        Assert.assertEquals("打开期间预算冻结:关闭后消息仍在树中", 1, hudGroups(root).size());

        // 关闭后继续渲染:HUD 可见期间 clock 继续累计(关闭帧 +1000,其后 12 帧 ×1000
        // = 13000 ≥ 预算 12000 + 淡出 800),剩余预算用尽后队首弹出移除
        for (int i = 29; i <= 40; i++) {
            controller.tick(T0 + 1000L * i);
            rt.flush();
        }
        Assert.assertEquals("剩余预算用尽后移除", 0, hudGroups(root).size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * 队首弹出:两独立组(不同发送者),最旧组预算耗尽+淡出窗结束 → 只移除最旧组
     * (队首弹出,一帧收敛、结构级),新组不受牵连;继续推帧,新组过期后同样移除(不复活)。
     */
    @Test
    public void hudHeadExpiryPopsOldestGroupOnly() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> first"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        Assert.assertEquals(1, hudGroups(root).size());

        // 组1 已进入(可见起点 = 0);推进 3 帧后追加组2(不同发送者 → 独立组,起点更晚)
        for (int i = 1; i <= 3; i++) {
            controller.tick(T0 + 1000L * i);
            rt.flush();
        }
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> second"), 2, T0 + 500L));
        controller.notifyDataChanged();
        controller.tick(T0 + 4000L);
        rt.flush();
        Assert.assertEquals("两独立组", 2, hudGroups(root).size());

        // 推帧至最旧组过期(可见时钟 13000 ≥ 12000+800,首帧定锚不累计):队首弹出,
        // 新组(组2 起点 = 3000,可见 13000-3000=10000 < 12800)不受牵连
        for (int i = 5; i <= 14; i++) {
            controller.tick(T0 + 1000L * i);
            rt.flush();
        }
        Assert.assertEquals("最旧组过期后移除,新组保留", 1, hudGroups(root).size());

        // 新组尚未过期(clock 16000,elapsed 12000,淡出窗未结束):不复活、不误删
        controller.tick(T0 + 16_000L);
        rt.flush();
        Assert.assertEquals("新组未过淡出窗仍保留(不复活最旧组)", 1, hudGroups(root).size());

        // 新组过期(clock 16000 ≥ start 3000 + 12800,帧间 delta 夹取 1s:
        // 18000 帧 delta 2000→1000,hud 仅 15000,淡出窗未走完)→ 19000 帧 hud 16000 弹出
        controller.tick(T0 + 18_000L);
        rt.flush();
        controller.tick(T0 + 19_000L);
        rt.flush();
        Assert.assertEquals("新组过期后树清空", 0, hudGroups(root).size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * enterOnMount 门控:组首次以 HUD 形态合成 true(播放入场),重挂载(打开-关闭
     * 树重建)/组增长重建置 false(动画不重播);全新组仍 true。读取合成列表断言。
     */
    @Test
    public void hudEnterOnMountPlaysOnlyOnFirstHudSynthesis() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);
        ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();

        Assert.assertEquals("首次合成 enterOnMount=true(播放入场)", true,
                groups.get().get(0).isEnterOnMount());

        // 打开-关闭循环:HUD 树重建(重挂载),同组不再播放入场
        controller.setChatOpen(true);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1
                + ChatMarkdownSettings.getPopAnimMillis());
        controller.setChatOpen(false);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1
                + ChatMarkdownSettings.getPopAnimMillis()
                + ChatMarkdownSettings.getClosingAnimMillis() + 1);
        rt.flush();
        Assert.assertEquals("重挂载(同组)enterOnMount=false", false,
                groups.get().get(0).isEnterOnMount());

        // 新组进入(不同发送者):全新组 true,旧组保持 false
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Alex> new"), 2, T0 + 2000L));
        controller.notifyDataChanged();
        controller.tick(T0 + 3000L);
        rt.flush();
        Assert.assertEquals("新旧两组同树", 2, groups.get().size());
        Assert.assertEquals("旧组重挂载仍 false", false, groups.get().get(0).isEnterOnMount());
        Assert.assertEquals("新组首合成为 true", true, groups.get().get(1).isEnterOnMount());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * 关闭衔接抑制(2026-08-29 真机「关闭聊天框时闪烁」根因之一):容器形态期间到达
     * 的新消息组,关闭聊天框 HUD 树衔接重建时整批稳态直接出现(enterOnMount=false,
     * 不重播 180ms 入场动画——opacity 0→1 + translateY 8→0 在关屏瞬间的闪现源);
     * 之后 HUD 形态实时到达的新组照常播放入场(true)。
     */
    @Test
    public void closeTransitionSuppressesEnterForGroupsSeenInContainer() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> old"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();
            Assert.assertEquals("首合成 = 1 组", 1, groups.get().size());

            // 打开聊天框(容器形态),期间 Bob 新消息到达(用户打字时别人说话)
            controller.setChatOpen(true);
            controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1);
            controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1
                    + ChatMarkdownSettings.getPopAnimMillis());
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Cara> during"), 2, T0 + 1000L));
            controller.notifyDataChanged();
            controller.tick(T0 + 2000L);
            rt.flush();
            // 容器形态全量合成两批(容器路径不登记 enterOnMount 门控)
            Assert.assertEquals("容器形态 = 2 组", 2, groups.get().size());

            // 关闭聊天框 → HUD 衔接重建(真机路径 = 输入屏动画完成回调 forceHud 直接切
            // HUD,不经机器 CLOSING 空窗):容器期到达的组稳态出现(不播入场动画)
            controller.closeToHudImmediately();
            controller.tick(T0 + 3000L);
            rt.flush();
            Assert.assertEquals("关闭衔接 = 2 组", 2, groups.get().size());
            Assert.assertEquals("关闭衔接:容器期到达组 enterOnMount=false(稳态,防闪烁)", false,
                    groups.get().get(1).isEnterOnMount());

            // HUD 稳定后实时新消息照常入场
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Alex> live"), 3, T0 + 3000L));
            controller.notifyDataChanged();
            controller.tick(T0 + 4000L);
            rt.flush();
            Assert.assertEquals("实时新消息仍入场(true)", true,
                    groups.get().get(2).isEnterOnMount());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    // ==================== D6:enterOnMount 组 key 漂移(容量裁剪删组首行 → 组首条 seq 变化) ====================

    /**
     * D6 根因复现:历史容量裁剪删除显示中合并组的最旧行 → 重新分组后组首条 seq 变化 →
     * groupKey 漂移 → 组重建;旧门控(按组首条 seq 是否登记)把新首条判为「从未合成」
     * → enterOnMount=true,在屏老组重播 180ms 入场动画。
     *
     * <p>新判定 = 「组内是否存在任一已登记 seq」:裁剪后组内剩余行 seq 均曾登记 →
     * enterOnMount=false,组 key 漂移重建但不播动画。</p>
     */
    @Test
    public void trimmedGroupHeadDoesNotReplayEnter() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            seedHistory(controller, 100); // 100 条同发送者(Bob),合并为 1 组(seq 1..100)
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();

            Assert.assertEquals("首合成为 1 组", 1, groups.get().size());
            Assert.assertEquals("首合成 enterOnMount=true(播放入场)", true,
                    groups.get().get(0).isEnterOnMount());
            Long keyBefore = ChatMessageList.groupKey(groups.get().get(0));

            // 第 101 条(同发送者)→ 历史容量裁剪(容量 100)删最旧首行(seq 1)→ 组首条漂移
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Bob> message 100"), 101, T0 - 500L));
            controller.notifyDataChanged();
            controller.tick(T0);
            rt.flush();

            List<ChatCardComposer.ComposedGroup> after = groups.get();
            Assert.assertEquals("裁剪后仍 1 组(剩余行同发送者同窗)", 1, after.size());
            Assert.assertEquals("裁剪删首行后 groupKey 漂移(组首条 seq 变化)", false,
                    keyBefore.equals(ChatMessageList.groupKey(after.get(0))));
            Assert.assertEquals("组内剩余行 seq 均曾登记 → 重建但不重播入场", false,
                    after.get(0).isEnterOnMount());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * D6 组合场景(增长 + 裁剪):首合登记 {a..} → 增长 d(同发送者续发,每次 HUD 合成
     * 无条件登记组内全部 seq)→ 裁剪删光旧行只剩 {d}——d 已登记 → 不重播。
     *
     * <p>若门控仅在 enter=true 时登记,增长合成(enter=false)不会登记 d,裁剪后剩
     * {d} 未登记 → 误播;无条件登记全部是此场景的关键。</p>
     */
    @Test
    public void trimAfterGrowthStillSuppressesEnter() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            seedHistory(controller, 100); // seq 1..100
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();
            Assert.assertEquals("首合成 enterOnMount=true", true,
                    groups.get().get(0).isEnterOnMount());

            // 增长:第 101 条(同发送者)触发容量裁剪删 seq 1 → 组 {2..101},老行已登记 → 不重播
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Bob> growth"), 101, T0 - 500L));
            controller.notifyDataChanged();
            controller.tick(T0);
            rt.flush();
            Assert.assertEquals("增长 + 裁剪后 enterOnMount=false", false,
                    groups.get().get(0).isEnterOnMount());

            // 裁剪删光旧行(seq 1..100)只剩增长消息(seq 101):d 在增长合成时已无条件登记 → 不重播
            for (int id = 1; id <= 100; id++) {
                controller.history().deleteById(id);
            }
            controller.notifyDataChanged();
            controller.tick(T0 + 1000L);
            rt.flush();
            Assert.assertEquals("旧行删光后仍 1 组(单行)", 1, groups.get().size());
            Assert.assertEquals("组内仅剩曾登记的增长消息 → 不重播入场", false,
                    groups.get().get(0).isEnterOnMount());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * D6 场景:裁剪删光某组(组 seq 残留于门控集合)→ 新发送者新组首次合成仍播放入场
     * (集合只增不减,残留 seq 不影响新组判定);再次裁光 → 再新组同样播放。
     */
    @Test
    public void trimmedAwayGroupGoneThenFreshGroupReplays() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            seedHistory(controller, 100); // Bob 组(seq 1..100)
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();
            Assert.assertEquals("Bob 首合成 enterOnMount=true", true,
                    groups.get().get(0).isEnterOnMount());

            // 灌 100 条 Cara → 逐条 append 即时裁剪,Bob 旧行被裁光(渲染侧一次合成)
            for (int i = 0; i < 100; i++) {
                controller.history().append(new ChatLineRecord(
                        new ChatComponentText("<Cara> flood " + i), 1000 + i, T0 - 500L + i));
            }
            controller.notifyDataChanged();
            controller.tick(T0 + 1000L);
            rt.flush();
            List<ChatCardComposer.ComposedGroup> afterTrim = groups.get();
            Assert.assertEquals("Bob 组被裁光,只剩 Cara 组", 1, afterTrim.size());
            Assert.assertEquals("Cara 组首合成(全新 seq,残留不干扰)= true", true,
                    afterTrim.get(0).isEnterOnMount());

            // 再次灌 100 条 Dana 裁光 Cara 组 → Dana 组首合成仍播放
            for (int i = 0; i < 100; i++) {
                controller.history().append(new ChatLineRecord(
                        new ChatComponentText("<Dana> flood " + i), 2000 + i, T0 + 3000L + i));
            }
            controller.notifyDataChanged();
            controller.tick(T0 + 4000L);
            rt.flush();
            List<ChatCardComposer.ComposedGroup> afterTrim2 = groups.get();
            Assert.assertEquals("Cara 组被裁光,只剩 Dana 组", 1, afterTrim2.size());
            Assert.assertEquals("Dana 组首合成 = true", true,
                    afterTrim2.get(0).isEnterOnMount());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * D6 回归加固(关闭衔接 + 增长):容器期到达的组合多条消息,关闭衔接 HUD 重建时
     * 整批组内全部 seq 预登记 → 稳态不播入场;HUD 稳定后同组增长(续发)仍不重播。
     */
    @Test
    public void containerSeenGroupStillSuppressedOnCloseTransition() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> old"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();
            Assert.assertEquals("首合成 = 1 组", 1, groups.get().size());

            // 打开聊天框(容器形态),期间 Cara 连发 2 条(合并 1 组)
            controller.setChatOpen(true);
            controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1);
            controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1
                    + ChatMarkdownSettings.getPopAnimMillis());
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Cara> one"), 2, T0 + 1000L));
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Cara> two"), 3, T0 + 1500L));
            controller.notifyDataChanged();
            controller.tick(T0 + 2000L);
            rt.flush();
            Assert.assertEquals("容器形态 = 2 组", 2, groups.get().size());

            // 关闭衔接 → HUD 重建:容器期到达组(组内 2 条 seq 全部预登记)稳态出现,不播入场
            controller.closeToHudImmediately();
            controller.tick(T0 + 3000L);
            rt.flush();
            Assert.assertEquals("关闭衔接 = 2 组", 2, groups.get().size());
            Assert.assertEquals("关闭衔接:容器期到达组 enterOnMount=false(稳态,防闪烁)", false,
                    groups.get().get(1).isEnterOnMount());

            // HUD 稳定后同组增长(续发第 3 条):组内老行 seq 已登记 → 重建仍不重播
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Cara> three"), 4, T0 + 3500L));
            controller.notifyDataChanged();
            controller.tick(T0 + 4000L);
            rt.flush();
            Assert.assertEquals("增长重建仍 false", false,
                    groups.get().get(1).isEnterOnMount());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    // ==================== S1:冻结窗口只认关闭方向(2026-09 打开方向被误冻结) ====================

    /** 打开聊天 → 容器稳定,机器开放置在固定 frameMillis。 */
    private static void openToContainer(ChatSceneController controller, SceneRuntime rt, long frame) {
        controller.setChatOpen(true);
        controller.tick(frame + ChatMarkdownSettings.getCollapseAnimMillis() + 1L);
        controller.tick(frame + ChatMarkdownSettings.getCollapseAnimMillis() + 1L
                + ChatMarkdownSettings.getPopAnimMillis());
        rt.flush();
    }

    /**
     * S1 触发路径①:渐入窗口内(CLOSING 后渐入尚未耗尽)机器处于打开方向
     * (COLLAPSING/POPPING/CONTAINER,均满足旧判定 currentPhase != HUD → 误冻结)时,
     * 不得冻结——composeAll 返回实时全量而非陈旧稳态快照,渐入通道残留立即复位。
     */
    @Test
    public void openDuringFadeInWindowIsNotFrozenAndNoFadeInResidue() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();

            // 打开 → 容器稳定(frameMillis=T0+401)→ 关闭衔接:窗口起点 = T0+401(540ms)
            openToContainer(controller, rt, T0);
            controller.closeToHudImmediately();
            controller.tick(T0 + 410L); // HUD 衔接重建帧:渐入起点 = T0+410
            rt.flush();
            Assert.assertEquals("窗口起点 = closeToHudImmediately 时刻", T0 + 401L,
                    controller.__transitionStartMillisForTest());

            // 渐入中段(T0+610,进度 0.5)按 T 重开:COLLAPSING(打开方向),
            // 旧判定「窗口内 && phase != HUD」误冻结;新判定只认 CLOSING 方向
            controller.tick(T0 + 610L);
            rt.flush();
            controller.setChatOpen(true);
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Cara> live"), 2, T0 + 620L));
            controller.notifyDataChanged();
            controller.tick(T0 + 620L);
            rt.flush();
            Assert.assertEquals("渐入窗内重开不冻结:消息实时应用(2 组)", 2, groups.get().size());
            Assert.assertEquals("打开方向窗口保留(未兑现不提前作废)", T0 + 401L,
                    controller.__transitionStartMillisForTest());

            // 窗口整点耗尽帧(T0+941,机器恰好 POPPING 锚点帧):窗口复位、无渐入残留。
            // 渐入残留通道已复位恒 1 → opacity == 纯 animOpacity(POPPING p=0 → 0)
            controller.tick(T0 + 941L);
            rt.flush();
            Assert.assertEquals("打开方向窗口耗尽帧不冻结", 2, groups.get().size());
            Assert.assertEquals("打开方向无渐入残留:opacity=纯 animOpacity(POPPING p=0)", 0.0F,
                    root.getOpacity(), 0.001F);
            Assert.assertEquals("窗口耗尽复位", -1L, controller.__transitionStartMillisForTest());

            // POPPING 完成 → CONTAINER 稳定:opacity=1(渐入残留通道恒 1 快速路径)
            controller.tick(T0 + 941L + ChatMarkdownSettings.getPopAnimMillis());
            rt.flush();
            Assert.assertEquals("CONTAINER 稳定 opacity=1(无残留)", 1.0F, root.getOpacity(), 0.001F);
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * S1 触发路径②:CLOSING 中按 T 重开(pendingOpen 挂起)——CLOSING 挂起期窗口保留
     * (不回归 f4b1af36「过渡期消息打穿」),兑现进 COLLAPSING 帧即作废,打开方向实时。
     */
    @Test
    public void pendingOpenKeepsFreezeDuringClosingThenUnfreezesAtCollapsing() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();

            // 打开 → 容器稳定 → 自然关闭:CLOSING 起点 = T0+401,输入屏 requestClose 开窗
            openToContainer(controller, rt, T0);
            long closeAt = T0 + 401L;
            controller.setChatOpen(false);
            controller.beginCloseTransition(closeAt); // 真机路径:屏幕 requestClose 补开窗口
            controller.tick(closeAt);
            rt.flush();
            Assert.assertEquals("CLOSING 起点开窗", closeAt, controller.__transitionStartMillisForTest());

            // CLOSING 中段按 T 重开:pendingOpen 挂起(CLOSING 不可打断);挂起期窗口保留,
            // 消息仍冻结(不回归 f4b1af36「过渡期消息打穿」)
            controller.setChatOpen(true);
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Cara> mid"), 2, T0 + 430L));
            controller.notifyDataChanged();
            long mid = closeAt + ChatMarkdownSettings.getClosingAnimMillis() / 2;
            controller.tick(mid);
            rt.flush();
            Assert.assertEquals("挂起期窗口保留", closeAt, controller.__transitionStartMillisForTest());
            Assert.assertEquals("挂起期消息冻结(仍旧 1 组)", 1, groups.get().size());

            // CLOSING 完成帧:兑现进 COLLAPSING(打开方向)——窗口作废,解冻一次性应用
            long redemption = closeAt + ChatMarkdownSettings.getClosingAnimMillis();
            controller.tick(redemption);
            rt.flush();
            Assert.assertEquals("兑现帧窗口作废", -1L, controller.__transitionStartMillisForTest());
            Assert.assertEquals("兑现后积压消息一次性应用(2 组)", 2, groups.get().size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * S1 触发路径③(打开方向重入不冻结):容器稳定时旧关闭窗口被错误顶替开启,
     * CONTAINER 阶段不得冻结——实时全量(旧判定 currentPhase != HUD 误冻结)。
     */
    @Test
    public void staleCloseWindowDoesNotFreezeContainerPhase() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();

            // 打开 → 容器稳定(T0+401)→ 旧关闭窗口被顶替开启:窗口起点 = T0+401
            openToContainer(controller, rt, T0);
            controller.closeToHudImmediately();
            controller.tick(T0 + 410L); // HUD 衔接重建帧
            rt.flush();
            Assert.assertEquals("窗口起点 = closeToHudImmediately 时刻", T0 + 401L,
                    controller.__transitionStartMillisForTest());

            // 窗口内重新打开:机器进入 COLLAPSING(打开方向),CONTAINER 稳定后旧窗口未耗尽
            // (T0+401 起 540ms,耗尽帧 T0+941)——旧判定 phase != HUD 误冻结(陈旧稳态快照);
            // 新判定只认 CLOSING 方向
            controller.setChatOpen(true);
            controller.tick(T0 + 610L); // COLLAPSING 完成 → POPPING(锚点 T0+610)
            controller.tick(T0 + 850L); // POPPING 完成 → CONTAINER
            rt.flush();
            Assert.assertEquals("CONTAINER 阶段窗口尚未耗尽(旧判定的误冻结前提)", T0 + 401L,
                    controller.__transitionStartMillisForTest());

            // CONTAINER 期消息到达:实时应用(误冻结下仍返回旧稳态快照 1 组)
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Cara> live"), 2, T0 + 880L));
            controller.notifyDataChanged();
            controller.tick(T0 + 880L);
            rt.flush();
            Assert.assertEquals("CONTAINER 期消息实时应用(2 组)", 2, groups.get().size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * S1 边界:COLLAPSING 反悔(方向-1,不冻结):渐入窗内重开进 COLLAPSING 后立即
     * 反悔(CLOSING 方向),窗口期关闭方向仍冻结;反向播放完回 HUD,窗口保留。
     */
    @Test
    public void collapsingReverseDoesNotFreeze() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();

            // 打开 → 容器稳定(T0+401)→ 关闭衔接开窗(起点 T0+401)
            openToContainer(controller, rt, T0);
            controller.closeToHudImmediately();
            controller.tick(T0 + 410L);
            rt.flush();
            Assert.assertEquals("窗口起点 = closeToHudImmediately 时刻", T0 + 401L,
                    controller.__transitionStartMillisForTest());

            // 窗口内重开 → COLLAPSING(锚点 T0+610):打开方向实时
            controller.setChatOpen(true);
            controller.tick(T0 + 610L);
            rt.flush();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Cara> live"), 2, T0 + 630L));
            controller.notifyDataChanged();
            controller.tick(T0 + 630L);
            rt.flush();
            Assert.assertEquals("COLLAPSING 阶段消息实时应用(2 组)", 2, groups.get().size());

            // COLLAPSING 反悔(方向-1,仍 COLLAPSING 反向播放):不冻结,消息仍实时
            controller.setChatOpen(false);
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Dave> live2"), 3, T0 + 650L));
            controller.notifyDataChanged();
            controller.tick(T0 + 650L);
            rt.flush();
            Assert.assertEquals("反悔(方向-1)不冻结:消息仍实时应用(3 组)", 3, groups.get().size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * S1 边界:窗口整点耗尽帧(now - start == closing + fadeIn)不冻结、窗口复位;
     * 渐入整点完成帧(progress >= 1)opacity 快速路径恒 1、无残留。
     */
    @Test
    public void windowExhaustionBoundaryFrameNotFrozen() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();

            // 打开 → 容器稳定(T0+401)→ 关闭衔接:窗口起点 = T0+401(540ms)
            openToContainer(controller, rt, T0);
            controller.closeToHudImmediately();
            controller.tick(T0 + 410L); // HUD 衔接重建帧:渐入起点 = T0+410
            rt.flush();
            Assert.assertEquals("窗口起点 = closeToHudImmediately 时刻", T0 + 401L,
                    controller.__transitionStartMillisForTest());

            // 窗口整点耗尽帧(T0+941):窗口复位;渐入进度 (941-410)/400 > 1 → 快速路径恒 1
            controller.tick(T0 + 941L);
            rt.flush();
            Assert.assertEquals("窗口整点耗尽复位", -1L, controller.__transitionStartMillisForTest());
            Assert.assertEquals("渐入整点完成帧 opacity=1(无残留)", 1.0F, root.getOpacity(), 0.001F);

            // 整点耗尽帧解冻:冻结期到达的消息一次性应用
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Cara> live"), 2, T0 + 900L));
            controller.notifyDataChanged();
            controller.tick(T0 + 941L + 1000L);
            rt.flush();
            Assert.assertEquals("窗口耗尽后消息实时应用(2 组)", 2, groups.get().size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * S1 边界:CLOSING 自然完成且 pendingOpen 兑现帧 == 窗口耗尽帧(tick C 幂等):
     * 兑现进 COLLAPSING(打开方向)不冻结,窗口作废。
     */
    @Test
    public void redemptionFrameAtWindowExhaustionIsIdempotent() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();

            // 打开 → 容器稳定(T0+401)→ 自然关闭(CLOSING 起点 T0+401)
            openToContainer(controller, rt, T0);
            long closeAt = T0 + 401L;
            controller.setChatOpen(false);
            controller.tick(closeAt);
            rt.flush();

            // CLOSING 中段按 T 重开:pendingOpen 挂起;输入屏 requestClose 补开窗口
            controller.setChatOpen(true);
            controller.beginCloseTransition(closeAt);
            long mid = closeAt + ChatMarkdownSettings.getClosingAnimMillis() / 2;
            controller.tick(mid);
            rt.flush();
            Assert.assertEquals("挂起期窗口保留(不回归 f4b1af36)", closeAt,
                    controller.__transitionStartMillisForTest());

            // 兑现帧(T0+541)= 窗口耗尽帧(T0+401 + 540):tick C 幂等作废,
            // COLLAPSING 兑现进打开方向 → 不冻结
            long redemption = closeAt + ChatMarkdownSettings.getClosingAnimMillis();
            controller.tick(redemption);
            rt.flush();
            Assert.assertEquals("兑现帧==窗口耗尽帧:窗口作废", -1L,
                    controller.__transitionStartMillisForTest());

            // 兑现后打开方向消息实时应用
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Cara> live"), 2, T0 + 600L));
            controller.notifyDataChanged();
            controller.tick(T0 + 600L);
            rt.flush();
            Assert.assertEquals("兑现帧打开方向消息实时应用(2 组)", 2, groups.get().size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    // ==================== S2:pendingOpen 兑现 rebuildTree 权威信号 ====================

    /**
     * S2:pendingOpen 兑现(forceHud 路径)不预登记 enterOnMount、不设渐入——
     * 打开方向(COLLAPSING)首合成 enterOnMount=true 播 enter,渐入残留通道复位。
     */
    @Test
    public void pendingOpenRedemptionPlaysEnterAndSkipsFadeIn() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();
            Assert.assertEquals("首合成 enterOnMount=true", true, groups.get().get(0).isEnterOnMount());

            // 打开 → 容器稳定(T0+401);容器期新消息到达(从未 HUD 合成过)
            openToContainer(controller, rt, T0);
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Cara> during"), 2, T0 + 420L));
            controller.notifyDataChanged();
            controller.tick(T0 + 450L);
            rt.flush();
            Assert.assertEquals("容器期全量 2 组", 2, groups.get().size());

            // 自然关闭(CLOSING 起点 T0+450)→ CLOSING 中按 T 重开:pendingOpen 挂起
            controller.setChatOpen(false);
            controller.setChatOpen(true);
            long redemption = T0 + 450L + ChatMarkdownSettings.getClosingAnimMillis();
            controller.tick(redemption); // CLOSING 完成 → 兑现进 COLLAPSING
            rt.flush();
            Assert.assertEquals("兑现进 COLLAPSING", DisplayStateMachine.Phase.COLLAPSING,
                    controller.__phaseForTest());

            // S2 修复:兑现帧不预登记(hudEverFirstSeqs 未整批登记)→ 容器期新组
            // 首合成 enterOnMount=true 播 enter;不设渐入 → opacity 无残留
            Assert.assertEquals("兑现帧:容器期新组首合成 enter=true(播 enter)", true,
                    groups.get().get(1).isEnterOnMount());
            Assert.assertEquals("兑现帧不设渐入:opacity=1(无残留)", 1.0F, root.getOpacity(), 0.001F);
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * S2 锁定关闭衔接不回归:普通关闭(无 pendingOpen)整批预登记 enterOnMount=false
     * + 渐入照旧。
     */
    @Test
    public void normalCloseTransitionStillPreRegistersAndFadesIn() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = build(controller, rt);
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups = controller.groupsSignal();

            openToContainer(controller, rt, T0);
            controller.closeToHudImmediately();
            controller.tick(T0 + 410L); // HUD 衔接重建帧:渐入起点 = T0+410
            rt.flush();
            Assert.assertEquals("普通关闭衔接:预登记 enterOnMount=false", false,
                    groups.get().get(0).isEnterOnMount());
            Assert.assertEquals("普通关闭衔接:渐入起点 opacity=0", 0.0F, root.getOpacity(), 0.001F);

            // 渐入中段仍在渐入(进度 (610-410)/400 = 0.5)
            controller.tick(T0 + 610L);
            rt.flush();
            Assert.assertEquals("渐入中段 opacity=sqrt(0.5)", 0.70711F, root.getOpacity(), 0.001F);
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }

    /**
     * A2 收口回归(改写自「自锁回归」2026-08-28):物化职责已归宿主帧循环——空窗 flush
     * 照常、paint 跳过(SceneHudPipelineTest.emptyWindowStillMaterializesSignalsAndSelfHeals
     * 锁宿主半边)。本用例锁 controller 半边:tick 不再内置 flush,宿主帧(此处以
     * rt.flush 模拟其物化半步)驱动物化。
     */
    @Test
    public void hudTreeMaterializesViaHostFrameNotTickFlush() {
        boolean persisted = ChatMarkdownSettings.isHudPersistMessages();
        ChatMarkdownSettings.setHudPersistMessages(false);
        try {
            ChatSceneController controller = controller();
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode root = controller.buildContent(rt); // 空树挂载(等价宿主空窗 settle 前)
            controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
            controller.notifyDataChanged();
            controller.tick(T0); // tick 只推时钟/状态机,不再 flush
            Assert.assertEquals("tick 不再内置 flush:物化等宿主帧", 0, hudGroups(root).size());
            rt.flush(); // 模拟宿主空窗 settle 的物化半步
            Assert.assertEquals("宿主帧 flush 即物化组节点", 1, hudGroups(root).size());
        } finally {
            ChatMarkdownSettings.setHudPersistMessages(persisted);
        }
    }
}