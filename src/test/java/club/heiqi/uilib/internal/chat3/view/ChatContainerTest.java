package club.heiqi.uilib.internal.chat3.view;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * ChatContainer 契约测试(K3 缺陷 F6②):输入条区四周 8px 内边距(设计稿 §2.3/§6.2),
 * 输入框高 24px 钉在输入条区 40px 内,divider 到输入框顶恰好 8px。
 */
public class ChatContainerTest {

    /** 测试帧时钟基准(wall millis;SmoothScroller 动画起点/终点驱动)。 */
    private static final long T0 = 1_000_000L;

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

    private static ChatSceneController controller() {
        return new ChatSceneController(FIXED, new ChatSceneController.SelfNameProvider() {
            @Override
            public String selfName() {
                return "Alex";
            }
        }, new ChatMessageList.SegmentParser() {
            @Override
            public java.util.List<club.heiqi.uilib.font.layout.TextSegment> parse(String text, int baseColor) {
                return java.util.Collections.emptyList();
            }
        });
    }

    @Test
    public void inputRowCarriesEightPixelPaddingAnd24PxInput() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hi"), 1, 0L));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        Map<SceneNode, ChatLineRecord> registry = new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
        ChatContainer.Result result = ChatContainer.mount(rt, controller, registry, "");
        rt.flush();

        // containerNode 子顺序 = [listRow, divider, barRow](挂载顺序 + insertBefore)
        SceneNode container = result.root();
        Assert.assertEquals(3, container.__getChildren().size());
        SceneNode divider = container.__getChildren().get(1);
        SceneNode barRow = container.__getChildren().get(2);

        Assert.assertEquals("divider 高 1px", 1, divider.getPreferredHeight());
        Assert.assertEquals("输入条区高 40", ChatMarkdownSettings.getInputBarHeightPx(),
                barRow.getPreferredHeight());
        Assert.assertEquals("输入条区四周 8px 内边距(上)", 8, barRow.getPaddingTop());
        Assert.assertEquals("输入条区四周 8px 内边距(右)", 8, barRow.getPaddingRight());
        Assert.assertEquals("输入条区四周 8px 内边距(下)", 8, barRow.getPaddingBottom());
        Assert.assertEquals("输入条区四周 8px 内边距(左)", 8, barRow.getPaddingLeft());

        // 输入框:高 24 = 40 - 8×2,divider 底到输入框顶 = barRow 上 padding 8px
        SceneNode input = result.bar().root();
        Assert.assertEquals("输入框高 24(40 - 2×8)", 24, input.getPreferredHeight());
        Assert.assertEquals("输入框底色 = 设计令牌 bg-input", ChatMarkdownSettings.getInputBackgroundArgb(),
                input.getBackgroundColor());
    }

    @Test
    public void containerContentPaddingIsTenTenFourTen() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hi"), 1, 0L));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        Map<SceneNode, ChatLineRecord> registry = new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
        ChatContainer.Result result = ChatContainer.mount(rt, controller, registry, "");
        rt.flush();

        SceneNode container = result.root();
        // 设计稿 §2.3/§6.2:容器内容区上 10/左右 10/下 4(下留给滚动条视觉余量);
        // 不再复用 bubblePadding(5,10,5,10)
        Assert.assertEquals("容器内容区上 10", 10, container.getPaddingTop());
        Assert.assertEquals("容器内容区右 10", 10, container.getPaddingRight());
        Assert.assertEquals("容器内容区下 4(滚动条视觉余量)", 4, container.getPaddingBottom());
        Assert.assertEquals("容器内容区左 10", 10, container.getPaddingLeft());
    }

    /**
     * 视口滚动方向契约(真机「滚轮方向反」修复,聊天↔scene 倒置映射):
     * history.scrollBy 正向 = 滚轮向上 = 向旧消息 → 视口 scrollOffsetY 减小;
     * scroll=0 贴底 → 视口偏移 = maxScrollY(内容底部最新);向上超滚 clamp 到顶部 0。
     */
    @Test
    public void viewportScrollOffsetInvertsChatScrollDirection() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        // 容器形态(chatOpen=true):tick 不跑 HUD TTL 裁剪/高度裁剪,消息不被过滤;
        // 时间戳取 T0 附近(相邻 1ms 并组),避免 HUD 过期阈值误裁内容
        controller.setChatOpen(true);
        for (int i = 0; i < 20; i++) {
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Bob> message number " + i), 1, T0 + i));
        }
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        Map<SceneNode, ChatLineRecord> registry = new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
        ChatContainer.Result result = ChatContainer.mount(rt, controller, registry, "");
        result.setViewport(400, 300);
        rt.flush();
        // 测试无帧管线:手动 layout + 桥接 layoutDoneSignal(真机由 SceneFramePipeline SETTLE 桥接)
        layoutEngine.layout(result.root(), new Constraints(400, 300));
        rt.__setLayoutDoneEpoch(layoutEngine.layoutEpoch());
        rt.flush();

        // 树结构:containerNode = [listRow, divider, barRow];listRow = [listViewport, scrollbarColumn]
        SceneNode listViewport = result.root().__getChildren().get(0).__getChildren().get(0);
        int maxScroll = SceneGeometry.maxScrollY(listViewport);
        Assert.assertTrue("20 条消息应溢出视口(maxScroll > 0)", maxScroll > 0);

        // scroll = 0(贴底最新)→ 视口偏移 = maxScrollY(内容贴底)
        Assert.assertEquals("scroll=0 贴底:视口偏移 = maxScrollY", maxScroll,
                listViewport.getScrollOffsetY());

        // 滚轮向上 → history.scrollBy(+3)(向旧消息)→ 视口偏移减小 = 内容向旧消息方向滚动
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        controller.history().scrollBy(3);
        controller.notifyDataChanged();
        controller.tick(T0);
        rt.flush();
        controller.tick(T0 + 120L);
        rt.flush();
        Assert.assertEquals("滚轮向上 scrollBy(+3):视口偏移 = maxScrollY - 3 行",
                maxScroll - 3 * lineHeight, listViewport.getScrollOffsetY());

        // 滚轮向下 → scrollBy(-3)(回新消息)→ 视口偏移增大回底
        controller.history().scrollBy(-3);
        controller.notifyDataChanged();
        controller.tick(T0 + 120L);
        rt.flush();
        controller.tick(T0 + 240L);
        rt.flush();
        Assert.assertEquals("滚轮向下 scrollBy(-3):视口偏移回 maxScrollY(贴底最新)",
                maxScroll, listViewport.getScrollOffsetY());

        // 向上超滚 clamp:视口偏移下限 0(顶部最旧),不出现负偏移
        controller.history().scrollBy(1000);
        controller.notifyDataChanged();
        controller.tick(T0 + 240L);
        rt.flush();
        controller.tick(T0 + 360L);
        rt.flush();
        Assert.assertEquals("向上超滚 clamp:视口偏移 = 0(顶部最旧)", 0,
                listViewport.getScrollOffsetY());
    }
}
