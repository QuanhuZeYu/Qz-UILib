package club.heiqi.uilib.ui.scene.paint;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link TextStyle} 文本内容模式字段契约测试。
 */
public class TextStyleTextModeTest {

    @Test
    public void shouldDefaultToRawMode() {
        TextStyle style = new TextStyle(0xFFFFFFFF, 14);

        Assert.assertEquals(TextStyle.TEXT_MODE_UILIB_RAW, style.getTextMode());
    }

    @Test
    public void shouldKeepExplicitMode() {
        Assert.assertEquals(TextStyle.TEXT_MODE_RICH_TAGS,
                new TextStyle(0xFFFFFFFF, 14, TextStyle.TEXT_MODE_RICH_TAGS).getTextMode());
        Assert.assertEquals(TextStyle.TEXT_MODE_MINECRAFT_FORMATTED,
                new TextStyle(0xFFFFFFFF, 14, TextStyle.TEXT_MODE_MINECRAFT_FORMATTED).getTextMode());
    }

    @Test
    public void shouldClampInvalidModeToRaw() {
        Assert.assertEquals(TextStyle.TEXT_MODE_UILIB_RAW, new TextStyle(0, 14, -1).getTextMode());
        Assert.assertEquals(TextStyle.TEXT_MODE_UILIB_RAW, new TextStyle(0, 14, 99).getTextMode());
    }

    @Test
    public void shouldIncludeModeInEquality() {
        TextStyle raw = new TextStyle(0, 14, TextStyle.TEXT_MODE_UILIB_RAW);
        TextStyle rich = new TextStyle(0, 14, TextStyle.TEXT_MODE_RICH_TAGS);

        Assert.assertFalse(raw.equals(rich));
        Assert.assertEquals(raw, new TextStyle(0, 14));
        Assert.assertEquals(rich, new TextStyle(0, 14, TextStyle.TEXT_MODE_RICH_TAGS));
        Assert.assertNotEquals(raw.hashCode(), rich.hashCode());
    }
}
