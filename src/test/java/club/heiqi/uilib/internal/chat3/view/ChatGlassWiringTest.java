package club.heiqi.uilib.internal.chat3.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Test;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatHistory;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCardComposer;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGroupModel;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.ChatComponentText;

/**
 * 聊天液态玻璃接线测试（用户裁决 2026-09-02：聊天框与聊天 HUD 上 Liquid Glass）。
 *
 * <p>只验"设置项存在"没有意义——本类锁的是<strong>节点真的带上了玻璃</strong>：
 * 气泡节点必须携带 backdrop 声明、底色 alpha 必须降到半透明档（否则 95% 实心会把
 * 玻璃完全遮住，等于没接）；关闭时必须干净回退（backdrop=null、alpha 回实心），
 * 保证逃生舱可用。</p>
 */
public class ChatGlassWiringTest {

    private static final long T0 = 1_700_000_000_000L;

    private static final ChatLineLayouter.Measure FIXED = new ChatLineLayouter.Measure() {
        @Override
        public float advance(String text, int fontSizePx) {
            return text.length() * 4.0F;
        }

        @Override
        public int epoch() {
            return 0;
        }
    };

    private static final ChatMessageList.SegmentParser PARSER = new ChatMessageList.SegmentParser() {
        @Override
        public List<TextSegment> parse(String text, int baseColor) {
            TextStyle style = new TextStyle();
            style.setColor(baseColor);
            return java.util.Collections.singletonList(new TextSegment(text, style));
        }
    };

    @After
    public void restoreGlassDefault() {
        ChatMarkdownSettings.setGlassEnabled(true);
    }

    private static ChatSceneController controller() {
        return new ChatSceneController(FIXED, new ChatSceneController.SelfNameProvider() {
            @Override
            public String selfName() {
                return "Alex";
            }
        }, PARSER);
    }

    /** root → mount → list → 组节点。 */
    private static List<SceneNode> hudGroups(SceneNode root) {
        SceneNode mount = root.__getChildren().get(0);
        SceneNode list = mount.__getChildren().get(0);
        return list.__getChildren();
    }

    private SceneNode firstBubble() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = controller.buildContent(rt);
        rt.flush();
        List<SceneNode> groupChildren = hudGroups(root).get(0).__getChildren();
        // 组结构：组头 + 气泡
        return groupChildren.get(1);
    }

    /** 玻璃开启：气泡必须真的带上 backdrop 声明，且底色降到半透明。 */
    @Test
    public void bubbleCarriesBackdropWhenGlassEnabled() {
        ChatMarkdownSettings.setGlassEnabled(true);
        SceneNode bubble = firstBubble();

        UiBackdrop backdrop = bubble.getBackdrop();
        assertNotNull("玻璃开启时气泡必须携带 backdrop 声明", backdrop);
        assertNotNull("backdrop 必须带材质档", backdrop.getEffect());
        assertNotNull("backdrop 必须带底材质", backdrop.getEffect().getMaterial());
        assertEquals("模糊半径取设置值(用户定 8)", ChatMarkdownSettings.getGlassBlurRadiusPx(),
                backdrop.getBlurRadius());
        assertEquals("液态强度取设置值(用户定 0.5)", ChatMarkdownSettings.getGlassLensStrength(),
                backdrop.getEffect().getLensStrength(), 1.0e-6F);
        assertTrue("必须是 Liquid Glass 家族", backdrop.getEffect().isLiquid());
        // 浅色正文要靠黑 tint 压背景才保对比，故打底必须是 DARK 系
        assertTrue("聊天玻璃必须打底 DARK 系材质: " + backdrop.getEffect().getMaterial().name(),
                backdrop.getEffect().getMaterial().dark());

        int alpha = (bubble.getBackgroundColor() >>> 24) & 0xFF;
        assertEquals("气泡底 alpha 必须降到玻璃档(否则实心会遮住玻璃)",
                ChatMarkdownSettings.getGlassBubbleAlpha(), alpha);
        assertTrue("玻璃态气泡必须半透明", alpha < 0xF2);
    }

    /** 逃生舱：关闭玻璃必须干净回退到实心 + 无 backdrop。 */
    @Test
    public void bubbleFallsBackToOpaqueWhenGlassDisabled() {
        ChatMarkdownSettings.setGlassEnabled(false);
        SceneNode bubble = firstBubble();

        assertNull("关闭后不得残留 backdrop 声明", bubble.getBackdrop());
        int alpha = (bubble.getBackgroundColor() >>> 24) & 0xFF;
        assertEquals("关闭后气泡底必须回实心设计令牌", 0xF2, alpha);
    }

    /**
     * 聊天框（容器形态）的两块面板必须各自带上玻璃：容器外框与输入条。
     *
     * <p>刻意分别断言到具体节点，而不是"树里存在某个带 backdrop 的节点"——
     * 后者会被容器自身满足，输入条漏接也照样绿（本测试初版正是这个虚断言）。
     * 用户要的是"聊天框和聊天HUD都改"，输入条漏接就是没做完。</p>
     */
    @Test
    public void containerAndInputBarEachCarryGlass() {
        ChatMarkdownSettings.setGlassEnabled(true);
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hi"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        ChatContainer.Result result = ChatContainer.mount(rt, controller,
                new java.util.HashMap<SceneNode, ChatLineRecord>(), "draft");
        rt.flush();
        try {
            UiBackdrop container = result.root().getBackdrop();
            assertNotNull("容器外框必须挂 backdrop（聊天框主体）", container);
            assertTrue("容器必须是 Liquid Glass 家族", container.getEffect().isLiquid());
            assertEquals("容器 alpha 必须降到玻璃档",
                    ChatMarkdownSettings.getGlassContainerAlpha(),
                    (result.root().getBackgroundColor() >>> 24) & 0xFF);

            SceneNode inputNode = result.bar().root();
            UiBackdrop input = inputNode.getBackdrop();
            assertNotNull("输入条必须单独挂 backdrop（不得只给容器接）", input);
            assertEquals("输入条玻璃半径取设置值",
                    ChatMarkdownSettings.getGlassBlurRadiusPx(), input.getBlurRadius());
            assertTrue("输入条同样打底 DARK 系(浅色文字)", input.getEffect().getMaterial().dark());
        } finally {
            result.dispose();
        }
    }
}
