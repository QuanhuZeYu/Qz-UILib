package club.heiqi.uilib.font.util;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link DefaultFontOrderHints} 的平台提示测试。
 */
public class DefaultFontOrderHintsTest {

    /**
     * 验证 Windows 首启优先提示常见中日韩与符号字体。
     */
    @Test
    public void shouldPreferWindowsMultilingualFonts() {
        String[] hints = DefaultFontOrderHints.resolveForOsName("Windows 11");

        Assert.assertTrue(Arrays.asList(hints).indexOf("Microsoft YaHei")
                < Arrays.asList(hints).indexOf("Dialog"));
        Assert.assertTrue(Arrays.asList(hints).contains("Segoe UI Emoji"));
    }

    /**
     * 验证 Linux 首启优先提示 Noto / 思源 / 文泉驿这类多语种字体。
     */
    @Test
    public void shouldPreferLinuxMultilingualFonts() {
        String[] hints = DefaultFontOrderHints.resolveForOsName("Linux");

        Assert.assertTrue(Arrays.asList(hints).indexOf("Noto Sans CJK SC")
                < Arrays.asList(hints).indexOf("Dialog"));
        Assert.assertTrue(Arrays.asList(hints).contains("WenQuanYi Micro Hei"));
    }
}
