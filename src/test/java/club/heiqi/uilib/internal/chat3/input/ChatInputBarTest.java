package club.heiqi.uilib.internal.chat3.input;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

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
