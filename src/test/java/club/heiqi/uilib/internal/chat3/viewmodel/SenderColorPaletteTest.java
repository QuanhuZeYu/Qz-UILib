package club.heiqi.uilib.internal.chat3.viewmodel;

import org.junit.Assert;
import org.junit.Test;

/**
 * SenderColorPalette 契约测试:哈希稳定/空名白色/主题蓝常量。
 */
public class SenderColorPaletteTest {

    @Test
    public void shouldBeStableForSameName() {
        Assert.assertEquals(SenderColorPalette.colorFor("Steve"),
                SenderColorPalette.colorFor("Steve"));
    }

    @Test
    public void shouldReturnWhiteForEmptyName() {
        Assert.assertEquals(0xFFFFFFFF, SenderColorPalette.colorFor(null));
        Assert.assertEquals(0xFFFFFFFF, SenderColorPalette.colorFor(""));
    }

    @Test
    public void shouldReturnFullyOpaqueColors() {
        for (String name : new String[] { "a", "b", "Steve", "Alex", "player1", "player2", "player3" }) {
            int color = SenderColorPalette.colorFor(name);
            Assert.assertEquals("名字色应为不透明", 0xFF, (color >>> 24) & 0xFF);
        }
    }

    @Test
    public void shouldExposeSelfGray() {
        Assert.assertEquals(0xFF9AA0A6, SenderColorPalette.SELF_NAME_ARGB);
    }
}
