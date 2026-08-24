package club.heiqi.uilib.font.layout;

import org.junit.Assert;
import org.junit.Test;

/**
 * TextStyle codeSpan 标记契约测试(T6b,设计稿 §3.5):copy 同步 / 默认值 / reset 复位。
 */
public class TextStyleCodeSpanTest {

    @Test
    public void defaultsAreOffAndNoColor() {
        TextStyle style = new TextStyle();
        Assert.assertFalse("默认非 code 段", style.isCodeSpan());
        Assert.assertEquals("默认无衬底色(0 = 未注入)", 0, style.getCodeBackgroundColor());
    }

    @Test
    public void copyCarriesCodeSpanAndBackgroundColor() {
        TextStyle style = new TextStyle();
        style.setCodeSpan(true);
        style.setCodeBackgroundColor(0x26FFFFFF);
        TextStyle copy = style.copy();
        Assert.assertTrue("copy 同步 codeSpan", copy.isCodeSpan());
        Assert.assertEquals("copy 同步衬底色", 0x26FFFFFF, copy.getCodeBackgroundColor());
        // 原样不受影响
        copy.setCodeSpan(false);
        copy.setCodeBackgroundColor(0);
        Assert.assertTrue("copy 独立于原样", style.isCodeSpan());
        Assert.assertEquals("copy 独立于原样(色)", 0x26FFFFFF, style.getCodeBackgroundColor());
    }

    @Test
    public void resetAllAndFormatResetClearCodeSpan() {
        TextStyle style = new TextStyle();
        style.setCodeSpan(true);
        style.setCodeBackgroundColor(0x26FFFFFF);
        style.resetAll(0xFFFFFFFF);
        Assert.assertFalse("resetAll 清 codeSpan", style.isCodeSpan());
        Assert.assertEquals("resetAll 清衬底色", 0, style.getCodeBackgroundColor());

        style.setCodeSpan(true);
        style.setCodeBackgroundColor(0x26FFFFFF);
        style.applyFormat('r', 0xFFFFFFFF);
        Assert.assertFalse("格式码 reset 清 codeSpan", style.isCodeSpan());
        Assert.assertEquals("格式码 reset 清衬底色", 0, style.getCodeBackgroundColor());

        style.setCodeSpan(true);
        style.setCodeBackgroundColor(0x26FFFFFF);
        style.applyFormat('c', 0xFFFFFFFF);
        Assert.assertFalse("颜色格式码清 codeSpan", style.isCodeSpan());
    }

    @Test
    public void toFormattingCodesIgnoresCodeSpan() {
        // code 是渲染标记不是格式码:序列化前缀必须不含 code 相关输出(既有语义不破坏)
        TextStyle style = new TextStyle();
        style.setCodeSpan(true);
        style.setCodeBackgroundColor(0x26FFFFFF);
        Assert.assertEquals("", style.toFormattingCodes(0xFFFFFFFF));
    }
}
