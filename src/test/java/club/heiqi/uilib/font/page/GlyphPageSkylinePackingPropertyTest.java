package club.heiqi.uilib.font.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

/**
 * skyline bottom-left 装箱性质测试：任意分配序列下 slot 两两不重叠（含 gap 扩展）、不越界、索引连续。
 */
public class GlyphPageSkylinePackingPropertyTest {

    @Test
    public void deterministicMixedSequenceNeverOverlaps() {
        GlyphPage page = new GlyphPage(1, 0, 512, 64);
        int[][] sizes = { { 9, 9 }, { 10, 18 }, { 20, 25 }, { 76, 76 }, { 30, 12 }, { 64, 40 }, { 12, 30 } };
        List<GlyphPage.GlyphSlot> slots = new ArrayList<GlyphPage.GlyphSlot>();
        int index = 0;
        int misses = 0;
        while (misses < sizes.length * 2) {
            int[] size = sizes[index % sizes.length];
            index++;
            if (!page.canAllocate(size[0], size[1])) {
                misses++;
                continue;
            }
            misses = 0;
            GlyphPage.GlyphSlot slot = page.allocateSlot(size[0], size[1]);
            Assert.assertEquals(slots.size(), slot.getSlotIndex());
            slots.add(slot);
        }
        assertNoOverlap(slots, 512);
        Assert.assertTrue("混合序列至少应分配若干 slot", slots.size() > 20);
    }

    @Test
    public void randomSequenceNeverOverlaps() {
        Random random = new Random(1234567L);
        GlyphPage page = new GlyphPage(1, 0, 256, 64);
        List<GlyphPage.GlyphSlot> slots = new ArrayList<GlyphPage.GlyphSlot>();
        for (int attempt = 0; attempt < 8000; attempt++) {
            int width = 1 + random.nextInt(60);
            int height = 1 + random.nextInt(60);
            if (!page.canAllocate(width, height)) {
                continue;
            }
            String skylineBefore = page.describeSkyline();
            GlyphPage.GlyphSlot slot = page.allocateSlot(width, height);
            Assert.assertEquals(slots.size(), slot.getSlotIndex());
            for (int k = 0; k < slots.size(); k++) {
                GlyphPage.GlyphSlot earlier = slots.get(k);
                boolean overlap = earlier.getX() < slot.getX() + slot.getWidth() + 1
                        && slot.getX() < earlier.getX() + earlier.getWidth() + 1
                        && earlier.getY() < slot.getY() + slot.getHeight() + 1
                        && slot.getY() < earlier.getY() + earlier.getHeight() + 1;
                Assert.assertFalse("新 slot (" + slot.getX() + "," + slot.getY() + "," + slot.getWidth() + "x"
                        + slot.getHeight() + ") 与旧 slot " + k + " (" + earlier.getX() + "," + earlier.getY() + ","
                        + earlier.getWidth() + "x" + earlier.getHeight() + ") 重叠，放置前 skyline=" + skylineBefore
                        + "，放置后 skyline=" + page.describeSkyline(), overlap);
            }
            slots.add(slot);
        }
        assertNoOverlap(slots, 256);
        Assert.assertTrue("随机尺寸序列应至少容纳理论密度下限", slots.size() > 30);
    }

    @Test
    public void rollbackRestoresExactPosition() {
        GlyphPage page = new GlyphPage(1, 0, 128, 64);
        page.allocateSlot(30, 40);
        page.allocateSlot(50, 20);
        GlyphPage.SlotReservation reservation = page.reserveSlot(70, 30);
        reservation.commit();
        GlyphPage.GlyphSlot before = reservation.getSlot();
        reservation.rollback();
        GlyphPage.GlyphSlot reused = page.allocateSlot(70, 30);

        Assert.assertEquals(before.getSlotIndex(), reused.getSlotIndex());
        Assert.assertEquals(before.getX(), reused.getX());
        Assert.assertEquals(before.getY(), reused.getY());
    }

    @Test
    public void tinySlotDensePageKeepsSlotsUniqueAndInPage() {
        GlyphPage page = new GlyphPage(1, 0, 1024, 64);
        List<GlyphPage.GlyphSlot> slots = new ArrayList<GlyphPage.GlyphSlot>();
        while (page.canAllocate(9, 9)) {
            GlyphPage.GlyphSlot slot = page.allocateSlot(9, 9);
            Assert.assertEquals(slots.size(), slot.getSlotIndex());
            slots.add(slot);
        }
        assertNoOverlap(slots, 1024);
        Assert.assertTrue("9×9 密集页应容纳数千 slot", slots.size() > 2000);
    }

    private static void assertNoOverlap(List<GlyphPage.GlyphSlot> slots, int textureSize) {
        for (int i = 0; i < slots.size(); i++) {
            GlyphPage.GlyphSlot a = slots.get(i);
            Assert.assertTrue("slot " + i + " X 越界", a.getX() >= 0);
            Assert.assertTrue("slot " + i + " Y 越界", a.getY() >= 0);
            Assert.assertTrue("slot " + i + " 右缘越界", a.getX() + a.getWidth() <= textureSize);
            Assert.assertTrue("slot " + i + " 下缘越界", a.getY() + a.getHeight() <= textureSize);
            for (int j = i + 1; j < slots.size(); j++) {
                GlyphPage.GlyphSlot b = slots.get(j);
                boolean overlap = a.getX() < b.getX() + b.getWidth() + 1
                        && b.getX() < a.getX() + a.getWidth() + 1
                        && a.getY() < b.getY() + b.getHeight() + 1
                        && b.getY() < a.getY() + a.getHeight() + 1;
                Assert.assertFalse("slot " + i + " (" + a.getX() + "," + a.getY() + "," + a.getWidth()
                        + "x" + a.getHeight() + ") 与 slot " + j + " (" + b.getX() + "," + b.getY() + ","
                        + b.getWidth() + "x" + b.getHeight() + ") 重叠", overlap);
            }
        }
    }
}
