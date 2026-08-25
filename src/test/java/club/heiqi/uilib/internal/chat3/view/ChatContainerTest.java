package club.heiqi.uilib.internal.chat3.view;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * ChatContainer 契约测试(K3 缺陷 F6②):输入条区四周 8px 内边距(设计稿 §2.3/§6.2),
 * 输入框高 24px 钉在输入条区 40px 内,divider 到输入框顶恰好 8px。
 */
public class ChatContainerTest {

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
}
