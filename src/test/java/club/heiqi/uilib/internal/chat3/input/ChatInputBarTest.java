package club.heiqi.uilib.internal.chat3.input;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * ChatInputBar 契约测试(K3 缺陷 F6):输入框高 24px(输入条区 40 - 四周 8×2)、
 * 内 padding (2,10,2,10)、底色 = 设计令牌 bg-input 0xFF1E232A(覆盖 SceneTextInput
 * 通用 BG_PRESSED 0xFF211F26)、圆角 8、宽度填满父宽。
 */
public class ChatInputBarTest {

    private static final int BG_INPUT = ChatMarkdownSettings.getInputBackgroundArgb();

    @Test
    public void inputRootPinsHeightPaddingAndWidthToDesign() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        ChatInputBar bar = new ChatInputBar(rt, "");
        SceneNode root = bar.root();

        Assert.assertEquals("输入框高 24(40 - 8×2)", 24, root.getPreferredHeight());
        Assert.assertEquals("内 padding 上 2", 2, root.getPaddingTop());
        Assert.assertEquals("内 padding 右 10", 10, root.getPaddingRight());
        Assert.assertEquals("内 padding 下 2", 2, root.getPaddingBottom());
        Assert.assertEquals("内 padding 左 10", 10, root.getPaddingLeft());
        Assert.assertEquals("输入字号 font-input 14", 14, root.getFontSize());
        Assert.assertTrue("填满父宽(§6.2 fillParentWidth)", root.isFillParentWidth());
        Assert.assertEquals("圆角 r-md 8", ChatMarkdownSettings.getInputCornerRadiusPx(),
                root.getCornerRadius());
    }

    @Test
    public void inputTextTruncatesBeyond100CodePoints() {
        // T5:ChatInputBar 传 SceneTextInput maxLength=100(原版 maxStringLength 口径),
        // 一帧注入 120 字符只进 100(primitive 截断语义)
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime rt = harness.getRuntime();
        ChatInputBar bar = new ChatInputBar(rt, "");
        SceneNode root = new SceneNode();
        root.appendChild(bar.root());
        harness.mountRoot(root, 400, 100);
        rt.requestFocus(bar.root());
        rt.flush();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            sb.append('x');
        }
        harness.typeText(sb.toString());
        Assert.assertEquals("输入被截断到 100 码点", 100, bar.inputText().get().length());
        harness.dispose();
    }

    @Test
    public void placeholderTextAndColorFollowChat3Design() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        ChatInputBar bar = new ChatInputBar(rt, "");
        rt.flush();
        SceneNode root = bar.root();
        // SceneTextInputPrimitive 结构:第一个子节点 = prefixText(空值未聚焦时显示 placeholder)
        SceneNode prefix = root.__getChildren().get(0);
        Assert.assertEquals("placeholder 文案「输入消息…」(设计稿 §3.2)", "输入消息…", prefix.getText());
        Assert.assertEquals("placeholder 色 = text-input-placeholder 0xFF6E757E",
                ChatMarkdownSettings.getInputPlaceholderArgb(), prefix.getTextColor());
        Assert.assertEquals("设计令牌定值 0xFF6E757E", 0xFF6E757E, prefix.getTextColor());
    }

    @Test
    public void inputBackgroundIsDesignTokenAfterFlush() {
        // K3 实测 (33,31,38) = SceneTextInput 通用 BG_PRESSED 0xFF211F26;覆盖绑定
        // (注册晚于控件内部绑定,帧末批量提交)必须把底色钉回设计令牌 (30,35,42)
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        ChatInputBar bar = new ChatInputBar(rt, "");
        rt.flush();
        Assert.assertEquals("输入底色 = bg-input 0xFF1E232A", BG_INPUT,
                bar.root().getBackgroundColor());
        Assert.assertEquals("设计令牌 RGB = (30,35,42)", 30, (BG_INPUT >> 16) & 0xFF);
        Assert.assertEquals(35, (BG_INPUT >> 8) & 0xFF);
        Assert.assertEquals(42, BG_INPUT & 0xFF);
    }
}
