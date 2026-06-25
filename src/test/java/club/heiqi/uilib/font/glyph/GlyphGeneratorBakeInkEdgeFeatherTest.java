package club.heiqi.uilib.font.glyph;

import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Test;

/**
 * ink 边缘羽化烘焙单元测试。
 *
 * <p>基于 {@code INK_FEATHER_RADIUS=1} 契约：distance=1 时 alpha=127 单值，
 * 仅写入 ink 子区外紧邻 1 像素 padding 圈。</p>
 */
public class GlyphGeneratorBakeInkEdgeFeatherTest {

    private static final int FEATHER_ALPHA = 127;
    private static final int FEATHER_PIXEL = (FEATHER_ALPHA << 24) | 0x00FFFFFF;

    /**
     * 羽化像素应只落在 ink 子区外 ≤1 像素 padding 圈，alpha=127，
     * 且不覆盖 ink 子区内像素与更远 padding 像素。
     */
    @Test
    public void shouldBakeFeatherOnlyInAdjacentPaddingRing() {
        // slot 10×10，ink 子区 [3,7)×[3,7)
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        int inkLeft = 3;
        int inkTop = 3;
        int inkWidth = 4;
        int inkHeight = 4;

        new GlyphGenerator(null, null).bakeInkEdgeFeather(image, inkLeft, inkTop, inkWidth, inkHeight);

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                boolean inInk = x >= inkLeft && x < inkLeft + inkWidth
                        && y >= inkTop && y < inkTop + inkHeight;
                int chebyshev = chebyshevToInk(x, y, inkLeft, inkTop, inkWidth, inkHeight);
                int pixel = image.getRGB(x, y);
                if (inInk) {
                    Assert.assertEquals("ink 子区内像素不应被覆盖", 0, (pixel >> 24) & 0xFF);
                } else if (chebyshev == 1) {
                    Assert.assertEquals("ink 外紧邻 1 像素圈应被烘焙为 alpha=127",
                            FEATHER_PIXEL, pixel);
                } else {
                    Assert.assertEquals("距离>1 的 padding 像素不应被写入", 0, (pixel >> 24) & 0xFF);
                }
            }
        }
    }

    /**
     * 羽化不应覆盖 ink 子区内已有的不透明字形像素。
     */
    @Test
    public void shouldNotOverwriteInkSubregionPixels() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        int inkLeft = 3;
        int inkTop = 3;
        int inkWidth = 4;
        int inkHeight = 4;
        // ink 子区内写入不透明白色字形像素
        int inkPixel = 0xFFFFFFFF;
        for (int y = inkTop; y < inkTop + inkHeight; y++) {
            for (int x = inkLeft; x < inkLeft + inkWidth; x++) {
                image.setRGB(x, y, inkPixel);
            }
        }

        new GlyphGenerator(null, null).bakeInkEdgeFeather(image, inkLeft, inkTop, inkWidth, inkHeight);

        for (int y = inkTop; y < inkTop + inkHeight; y++) {
            for (int x = inkLeft; x < inkLeft + inkWidth; x++) {
                Assert.assertEquals("ink 子区内字形像素应保持不变", inkPixel, image.getRGB(x, y));
            }
        }
    }

    /**
     * 羽化不应覆盖更不透明的已有像素（existingAlpha≥featherAlpha 时跳过），
     * 但应覆盖更透明的已有像素。
     */
    @Test
    public void shouldNotOverwriteMoreOpaquePixelsButOverwriteMoreTransparent() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        int inkLeft = 3;
        int inkTop = 3;
        int inkWidth = 4;
        int inkHeight = 4;
        // (2,3) 羽化圈写入 alpha=200（更不透明），(3,2) 写入 alpha=50（更透明）
        int moreOpaque = (200 << 24) | 0x00FFFFFF;
        int moreTransparent = (50 << 24) | 0x00FFFFFF;
        image.setRGB(2, 3, moreOpaque);
        image.setRGB(3, 2, moreTransparent);

        new GlyphGenerator(null, null).bakeInkEdgeFeather(image, inkLeft, inkTop, inkWidth, inkHeight);

        Assert.assertEquals("更不透明像素不应被覆盖", moreOpaque, image.getRGB(2, 3));
        Assert.assertEquals("更透明像素应被覆盖为羽化值", FEATHER_PIXEL, image.getRGB(3, 2));
    }

    /**
     * 彩色字形（emoji）走 shader 彩色路径直接用纹理 RGB，烘焙白色羽化会使边缘 RGB 向白色偏移。
     *
     * <p>验证 P1-1 决策链：containsColoredPixels 对彩色图像返回 true（generate 据此跳过烘焙）；
     * 反向证明若不跳过，bakeInkEdgeFeather 会向彩色图像 ink 外写入白色 RGB，造成白边。</p>
     */
    @Test
    public void shouldDetectColoredGlyphAndAvoidWhiteBleed() {
        BufferedImage colored = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        int inkLeft = 3;
        int inkTop = 3;
        int inkWidth = 4;
        int inkHeight = 4;
        // ink 子区内写入彩色不透明像素（红绿不等，非灰度）
        int coloredPixel = 0xFFFF0000;
        for (int y = inkTop; y < inkTop + inkHeight; y++) {
            for (int x = inkLeft; x < inkLeft + inkWidth; x++) {
                colored.setRGB(x, y, coloredPixel);
            }
        }

        GlyphGenerator generator = new GlyphGenerator(null, null);
        Assert.assertTrue("彩色图像应被识别为 coloredGlyph", generator.containsColoredPixels(colored));

        // 反向证明：若不跳过烘焙，ink 外羽化圈会被写入白色 RGB，导致彩色字形边缘向白色偏移
        generator.bakeInkEdgeFeather(colored, inkLeft, inkTop, inkWidth, inkHeight);
        int feathered = colored.getRGB(2, 3);
        Assert.assertEquals("不跳过时羽化圈写入白色 RGB", 0xFF, (feathered >> 16) & 0xFF);
        Assert.assertEquals("不跳过时羽化圈写入白色 RGB", 0xFF, (feathered >> 8) & 0xFF);
        Assert.assertEquals("不跳过时羽化圈写入白色 RGB", 0xFF, feathered & 0xFF);
        // 由此证明 generate 中 if(!coloredGlyph) 跳过烘焙是避免彩色字形白边的必要守卫
    }

    /**
     * 灰度（非彩色）图像应被识别为非 coloredGlyph，generate 会正常烘焙羽化。
     */
    @Test
    public void shouldTreatGrayscaleGlyphAsNonColored() {
        BufferedImage grayscale = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        int inkLeft = 3;
        int inkTop = 3;
        int inkWidth = 4;
        int inkHeight = 4;
        // ink 子区内写入灰度不透明像素（R=G=B）
        int grayPixel = 0xFF808080;
        for (int y = inkTop; y < inkTop + inkHeight; y++) {
            for (int x = inkLeft; x < inkLeft + inkWidth; x++) {
                grayscale.setRGB(x, y, grayPixel);
            }
        }

        GlyphGenerator generator = new GlyphGenerator(null, null);
        Assert.assertFalse("灰度图像不应被识别为 coloredGlyph", generator.containsColoredPixels(grayscale));
    }

    /**
     * 计算像素到 ink 子区的切比雪夫距离。
     */
    private static int chebyshevToInk(int x, int y, int inkLeft, int inkTop, int inkWidth, int inkHeight) {
        int inkRight = inkLeft + inkWidth;
        int inkBottom = inkTop + inkHeight;
        int dx = 0;
        if (x < inkLeft) {
            dx = inkLeft - x;
        } else if (x >= inkRight) {
            dx = x - (inkRight - 1);
        }
        int dy = 0;
        if (y < inkTop) {
            dy = inkTop - y;
        } else if (y >= inkBottom) {
            dy = y - (inkBottom - 1);
        }
        return Math.max(dx, dy);
    }
}
