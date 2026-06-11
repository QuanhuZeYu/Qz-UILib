package club.heiqi.uilib.font.page;

import org.junit.Assert;
import org.junit.Test;

/**
 * 字符页可变 slot 装箱测试。
 */
public class GlyphPageVariableSlotPackingTest {

    /**
     * 不同尺寸的 slot 应按 shelf packing 记录真实位置与大小。
     */
    @Test
    public void shouldAllocateVariableSlotsWithRealBounds() {
        GlyphPage page = new GlyphPage(1, 0, 128, 64);

        GlyphPage.GlyphSlot first = page.allocateSlot(30, 40);
        GlyphPage.GlyphSlot second = page.allocateSlot(50, 20);
        GlyphPage.GlyphSlot third = page.allocateSlot(70, 30);

        Assert.assertEquals(0, first.getX());
        Assert.assertEquals(0, first.getY());
        Assert.assertEquals(30, first.getWidth());
        Assert.assertEquals(40, first.getHeight());
        Assert.assertEquals(31, second.getX());
        Assert.assertEquals(0, second.getY());
        Assert.assertEquals(0, third.getX());
        Assert.assertEquals(41, third.getY());
    }

    /**
     * 超出纹理页边界的 slot 不应被当前页接收。
     */
    @Test
    public void shouldRejectSlotLargerThanTexture() {
        GlyphPage page = new GlyphPage(1, 0, 64, 64);

        Assert.assertFalse(page.canAllocate(65, 10));
        Assert.assertFalse(page.canAllocate(10, 65));
        Assert.assertTrue(page.canAllocate(64, 64));
    }
}
