package club.heiqi.uilib.internal.chat3.view;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
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

    private static final ChatSceneController.SegmentParser PARSER =
            new ChatSceneController.SegmentParser() {
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
        Assert.assertNotNull("组头应带段流", bobGroup.__getChildren().get(0).getSegments());
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

        // POPPING 阶段:树已切为容器(mount 子 = 容器节点)
        SceneNode mount = root.__getChildren().get(0);
        Assert.assertEquals(1, mount.__getChildren().size());
        SceneNode container = mount.__getChildren().get(0);
        Assert.assertEquals(ChatMarkdownSettings.getContainerBgArgb(), container.getBackgroundColor());
        Assert.assertTrue("容器应裁剪内容", container.isClipChildren());
    }

    @Test
    public void containerScrollBindsHistoryOffset() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);

        controller.setChatOpen(true);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1);
        rt.flush();

        controller.history().scrollBy(3);
        controller.notifyDataChanged();
        rt.flush();

        SceneNode container = root.__getChildren().get(0).__getChildren().get(0);
        Assert.assertEquals(3 * ChatMarkdownSettings.getChatLineHeightPx(), container.getScrollOffsetY());
    }

    @Test
    public void hudFadeBakesAlphaIntoBubbleBackground() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);

        SceneNode bubble = hudGroups(root).get(0).__getChildren().get(1);
        Assert.assertEquals("初始全量 alpha", 0xE6, (bubble.getBackgroundColor() >>> 24) & 0xFF);

        // 淡出中段:alpha 128(截断)
        controller.tick(T0 + ChatMarkdownSettings.getHudTtlMillis()
                + ChatMarkdownSettings.getHudFadeMillis() / 2);
        rt.flush();
        // 组合语义:基础 alpha E6(230) × 淡出因子 128 → 115(0x73,整数截断)
        Assert.assertEquals("淡出中段 alpha 组合截断", 0x73, (bubble.getBackgroundColor() >>> 24) & 0xFF);
    }

    @Test
    public void containerUsesDynamicViewportSize() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        controller.setHostViewport(1600, 900);
        SceneNode root = build(controller, rt);

        Assert.assertEquals("窗口宽 = 视口宽 × 1/8",
                ChatMarkdownSettings.chatWidthFor(1600), root.getPreferredWidth());

        controller.setChatOpen(true);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1);
        rt.flush();

        SceneNode container = root.__getChildren().get(0).__getChildren().get(0);
        Assert.assertEquals("容器宽随视口", ChatMarkdownSettings.chatWidthFor(1600),
                container.getPreferredWidth());
        Assert.assertEquals("容器高 = 视口高 × 1/2", ChatMarkdownSettings.containerHeightFor(900),
                container.getPreferredHeight());
    }

    @Test
    public void hitTestReturnsComponentInContainerForm() {
        ChatSceneController controller = controller();
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hello"), 1, T0));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode root = build(controller, rt);

        controller.setChatOpen(true);
        controller.tick(T0 + ChatMarkdownSettings.getCollapseAnimMillis() + 1);
        rt.flush();
        controller.setHostViewport(400, 300);
        rt.flush(); // 视口变化触发的重建需 flush 后挂载
        LAYOUT.layout(root, new Constraints(400));

        // 树:root → mount → container → list → group → (组头, 消息节点)
        SceneNode container = root.__getChildren().get(0).__getChildren().get(0);
        SceneNode messageNode = container.__getChildren().get(0).__getChildren().get(0).__getChildren().get(1);

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
    public void expiredHudGroupsAreRemovedFromTree() {
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
    }
}