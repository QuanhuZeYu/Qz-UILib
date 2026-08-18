package club.heiqi.uilib.font.page;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/**
 * glyph result 到 upload plan 的像素快照往返测试（P1-D 拷贝链精简）。
 */
public class GlyphUploadPlanPixelRoundTripTest {

    @Test
    public void planPreservesPixelsWithoutIntermediateImage() {
        GlyphRequestToken token = new GlyphRequestToken(1, 1L, 'A', FontType.NORMAL);
        BufferedImage source = new BufferedImage(8, 6, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(2, 1, 0xFF112233);
        source.setRGB(5, 4, 0x80AABBCC);
        GlyphInfo glyphInfo = new GlyphInfo('A', 8, 8, 8.0F, 0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 8, 6, 1, 1, 6, 0, 0,
                true, false);
        GlyphGenerationResult result = new GlyphGenerationResult(token, source, glyphInfo);

        GlyphUploadPlan plan = GlyphUploadPlan.from(result);

        Assert.assertEquals(8L * 6L * 4L, plan.getBitmapBytes());
        BufferedImage rebuilt = plan.createImage();
        Assert.assertEquals(8, rebuilt.getWidth());
        Assert.assertEquals(6, rebuilt.getHeight());
        Assert.assertEquals(0xFF112233, rebuilt.getRGB(2, 1));
        Assert.assertEquals(0x80AABBCC, rebuilt.getRGB(5, 4));
        Assert.assertEquals(0, rebuilt.getRGB(0, 0));
    }

    @Test
    public void resultPixelSnapshotIsIndependent() {
        GlyphRequestToken token = new GlyphRequestToken(1, 1L, 'A', FontType.NORMAL);
        BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(1, 1, 0xFF123456);
        GlyphInfo glyphInfo = new GlyphInfo('A', 8, 8, 8.0F, 0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 4, 4, 1, 1, 4, 0, 0,
                true, false);
        GlyphGenerationResult result = new GlyphGenerationResult(token, source, glyphInfo);

        byte[] first = result.copyRgbaPixels();
        Arrays.fill(first, (byte) 0);
        BufferedImage image = result.getImage();

        Assert.assertEquals("修改快照副本不影响 result 内部像素", 0xFF123456, image.getRGB(1, 1));
    }

    @Test
    public void mismatchedSlotSizeIsRejected() {
        GlyphRequestToken token = new GlyphRequestToken(1, 1L, 'A', FontType.NORMAL);
        GlyphInfo glyphInfo = new GlyphInfo('A', 8, 8, 8.0F, 0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 8, 8, 1, 1, 8, 0, 0,
                true, false);
        GlyphGenerationResult result = new GlyphGenerationResult(token,
                new BufferedImage(6, 6, BufferedImage.TYPE_INT_ARGB), glyphInfo);

        try {
            GlyphUploadPlan.from(result);
            Assert.fail("像素与 slot 尺寸不一致必须拒绝");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("不一致"));
        }
    }
}
