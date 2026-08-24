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
        Assert.assertEquals("设计稿 §2.1 text-name-self", 0xFFAAB3BC, SenderColorPalette.SELF_NAME_ARGB);
    }

    @Test
    public void shouldMatchDesignPalette() {
        // 设计稿 §2.1 name-1..7(暗底调优整体提亮降饱和,7 色板全量替换)
        java.util.Set<Integer> design = new java.util.HashSet<Integer>(java.util.Arrays.asList(
                0xFFFF6B64, 0xFFFF9E57, 0xFFC07BF8, 0xFF6BCB77, 0xFF4DD0E1, 0xFF6FA8FF, 0xFFF06292));
        // "0".."6" 哈希取模恰好覆盖 7 个槽位(不依赖具体映射顺序)
        java.util.Set<Integer> actual = new java.util.HashSet<Integer>();
        for (String name : new String[] { "0", "1", "2", "3", "4", "5", "6" }) {
            actual.add(Integer.valueOf(SenderColorPalette.colorFor(name)));
        }
        Assert.assertEquals("色板应与设计稿一一对应", design, actual);
    }
}
