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
    public void inputTextTruncatesBeyond100Utf16Units() {
        // T8:ChatInputBar 传 SceneTextInput maxLength=100 + maxLengthUnit=UTF16(原版
        // maxStringLength 口径),一帧注入 120 字符只进 100(primitive 截断语义)
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
        Assert.assertEquals("输入被截断到 100 UTF-16 单元", 100, bar.inputText().get().length());
        harness.dispose();
    }

    @Test
    public void inputTextEmojiCountsTwoUtf16Units() {
        // T8:UTF-16 口径下 emoji 占 2 单元,100 单元 = 50 个 emoji(不切代理对)
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime rt = harness.getRuntime();
        ChatInputBar bar = new ChatInputBar(rt, "");
        SceneNode root = new SceneNode();
        root.appendChild(bar.root());
        harness.mountRoot(root, 400, 100);
        rt.requestFocus(bar.root());
        rt.flush();

        String emoji = new String(Character.toChars(0x1F600));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append(emoji);
        }
        harness.typeText(sb.toString());
        Assert.assertEquals("60 个 emoji 截断为 100 UTF-16 单元(50 个)", 100,
                bar.inputText().get().length());
        Assert.assertEquals("截断不切代理对", 50,
                bar.inputText().get().codePointCount(0, bar.inputText().get().length()));
        harness.dispose();
    }

    @Test
    public void slashPrefillMovesCaretToEndOnOpened() {
        // T2:斜杠开屏预填 "/" 后 caret 归行尾(原版 setText 后光标在末尾),输入落在 / 之后。
        // headless 不走 onOpened 全路径(vanilla 历史同步触 Minecraft 类初始化),直测拆出的对齐单元
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime rt = harness.getRuntime();
        ChatInputBar bar = new ChatInputBar(rt, "/");
        SceneNode root = new SceneNode();
        root.appendChild(bar.root());
        harness.mountRoot(root, 400, 100);
        bar.focusAndAlignCaret();
        rt.flush();

        harness.typeText("t");
        Assert.assertEquals("斜杠开屏后输入字符落在 / 之后", "/t", bar.inputText().get());
        harness.dispose();
    }

    @Test
    public void recalledHistoryMovesCaretToEnd() {
        // T3:历史回显只 set 文本不移动 caret → 继续输入会插到行首;回显后 caret 应归行尾
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime rt = harness.getRuntime();
        ChatInputBar bar = new ChatInputBar(rt, "");
        bar.recordSent("long history message");
        SceneNode root = new SceneNode();
        root.appendChild(bar.root());
        harness.mountRoot(root, 400, 100);
        rt.requestFocus(bar.root());
        rt.flush();

        bar.recallHistory(-1);
        rt.flush();
        Assert.assertEquals("Up 回显长历史", "long history message", bar.inputText().get());
        harness.typeText("X");
        Assert.assertEquals("回显后 caret 在行尾,继续输入追加", "long history messageX",
                bar.inputText().get());
        harness.dispose();
    }

    @Test
    public void emptySubmitDoesNotPolluteUpDownHistory() {
        // T4:空 Enter 只关屏不入发送历史(原版语义),不污染 Up/Down 回显
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        ChatInputBar bar = new ChatInputBar(rt, "   ");
        Assert.assertNull("仅空白提交不入历史", bar.submitText());
        bar.recallHistory(-1);
        rt.flush();
        Assert.assertEquals("空提交后 Up 无历史可回显", "", bar.inputText().get());
    }

    @Test
    public void nonEmptySubmitRecordsHistoryForUpDownRecall() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        ChatInputBar bar = new ChatInputBar(rt, " hello ");
        Assert.assertEquals("trim 后返回提交文本", "hello", bar.submitText());
        bar.inputText().set("");
        rt.flush();
        bar.recallHistory(-1);
        rt.flush();
        Assert.assertEquals("Up 回显最近发送", "hello", bar.inputText().get());
        bar.recallHistory(1);
        rt.flush();
        Assert.assertEquals("Down 回到底恢复暂存草稿", "", bar.inputText().get());
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
