package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.TestResources;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapedGlyph;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphRasterStyle;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;

/** 真实FT/HB验收：不同字体原生位图、浮点em、可见墨迹包络、彩色副本和owned worker缓存。 */
public final class GlyphRasterStyleScenario extends TestApplication {
    private static final GlyphRasterStyle[] STYLES = {
        GlyphRasterStyle.NORMAL,
        new GlyphRasterStyle(1.25f, 0),
        new GlyphRasterStyle(0, 0.25f),
        new GlyphRasterStyle(1.25f, 0.25f)
    };

    private static FontSource source(File root, String name, boolean color) throws Exception {
        return new FontSource(
                name,
                1,
                "scenario:" + name,
                TestResources.bytes(new File(root, name).toPath()).array(),
                0,
                color,
                Collections.emptySet());
    }

    private static long alpha(GlyphBitmap bitmap) {
        byte[] pixels = bitmap.pixels();
        long sum = 0;
        for (int i = 3; i < pixels.length; i += 4) sum += pixels[i] & 255;
        return sum;
    }

    private static void checkInk(GlyphBitmap bitmap, InkBounds ink, String label) {
        byte[] pixels = bitmap.pixels();
        int visible = 0, colored = 0;
        for (int y = 0; y < bitmap.height; y++)
            for (int x = 0; x < bitmap.width; x++) {
                int i = (y * bitmap.width + x) * 4, a = pixels[i + 3] & 255;
                for (int c = 0; c < 3; c++)
                    require((pixels[i + c] & 255) <= a, label + " premult channel>alpha");
                if (a == 0) continue;
                visible++;
                if ((pixels[i] & 255) != (pixels[i + 1] & 255)
                        || (pixels[i + 1] & 255) != (pixels[i + 2] & 255)) colored++;
                if (ink.known) {
                    double left = bitmap.bearingX + x * (double) bitmap.pixelScale,
                            top = -bitmap.bearingY + y * (double) bitmap.pixelScale;
                    double tolerance = bitmap.pixelScale * 0.001;
                    require(
                            left >= ink.left - tolerance
                                    && top >= ink.top - tolerance
                                    && left + bitmap.pixelScale <= ink.right + tolerance
                                    && top + bitmap.pixelScale <= ink.bottom + tolerance,
                            label + " visible bitmap escapes known ink");
                }
            }
        require(visible > 0, label + " missing native pixels");
        if (bitmap.color) require(colored > 0, label + " color bitmap became white coverage");
    }

    private static void image(File file, GlyphBitmap bitmap) throws Exception {
        BufferedImage image =
                new BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB_PRE);
        byte[] p = bitmap.pixels();
        int[] values = new int[bitmap.width * bitmap.height];
        for (int i = 0; i < values.length; i++)
            values[i] =
                    ((p[i * 4 + 3] & 255) << 24)
                            | ((p[i * 4] & 255) << 16)
                            | ((p[i * 4 + 1] & 255) << 8)
                            | (p[i * 4 + 2] & 255);
        image.getRaster().setDataElements(0, 0, bitmap.width, bitmap.height, values);
        ImageIO.write(image, "png", file);
    }

    /** 不重写滤波算法：只验收整数位移、不相交副本应保留原生每个颜色通道。 */
    private static void disjointCopy(FontFace face, int glyphId, GlyphBitmap original) {
        int distance = original.width + 4;
        GlyphBitmap copy =
                face.rasterize(glyphId, new GlyphRasterStyle(distance * original.pixelScale, 0));
        byte[] before = original.pixels(), after = copy.pixels();
        int dx = Math.round((original.bearingX - copy.bearingX) / original.pixelScale);
        int dy = Math.round((copy.bearingY - original.bearingY) / original.pixelScale);
        for (int y = 0; y < original.height; y++)
            for (int x = 0; x < original.width; x++)
                for (int c = 0; c < 4; c++) {
                    int expected = before[(y * original.width + x) * 4 + c] & 255;
                    int first = after[((y + dy) * copy.width + x + dx) * 4 + c] & 255;
                    int second = after[((y + dy) * copy.width + x + dx + distance) * 4 + c] & 255;
                    require(
                            Math.abs(expected - first) <= 1 && Math.abs(expected - second) <= 1,
                            "disjoint COPY lost native color");
                }
    }

    private static void nativeCase(
            FontSource source, float em, String text, ShapeDirection direction, File output)
            throws Exception {
        try (FontSession service = new LwjglFontEngine().openSession();
                FontFace face = source.openFace(service, em)) {
            List<ShapedGlyph> shaped = face.shape(text, direction, null);
            require(!shaped.isEmpty(), "shape empty");
            int checked = 0;
            for (ShapedGlyph glyph : shaped) {
                GlyphBitmap normal = face.rasterize(glyph.glyphId);
                if (alpha(normal) == 0) continue;
                GlyphBitmap explicit = face.rasterize(glyph.glyphId, GlyphRasterStyle.NORMAL);
                require(
                        normal.width == explicit.width
                                && normal.height == explicit.height
                                && normal.bearingX == explicit.bearingX
                                && normal.bearingY == explicit.bearingY
                                && Arrays.equals(normal.pixels(), explicit.pixels()),
                        "NORMAL changed legacy bitmap");
                long baseAlpha = alpha(normal);
                for (int i = 0; i < STYLES.length; i++) {
                    GlyphBitmap bitmap = face.rasterize(glyph.glyphId, STYLES[i]);
                    checkInk(
                            bitmap,
                            face.glyphInkBounds(glyph.glyphId, STYLES[i]),
                            source.id + "/" + em + "/" + i);
                    require(
                            bitmap.color == normal.color && bitmap.pixelScale == normal.pixelScale,
                            "variant changed native scale/color identity");
                    if (i == 1)
                        require(
                                alpha(bitmap) >= baseAlpha,
                                "bold COPY reduced original alpha coverage");
                    if (checked == 0)
                        image(
                                new File(
                                        output,
                                        source.id.replace('/', '-').replace('.', '-')
                                                + "-"
                                                + em
                                                + "-"
                                                + i
                                                + ".png"),
                                bitmap);
                }
                checkInk(
                        face.rasterize(glyph.glyphId, new GlyphRasterStyle(0, -0.25f)),
                        face.glyphInkBounds(glyph.glyphId, new GlyphRasterStyle(0, -0.25f)),
                        "negative shear");
                disjointCopy(face, glyph.glyphId, normal);
                checked++;
            }
            require(checked > 0, "all native glyphs blank");
            int id = shaped.get(0).glyphId;
            boolean rejected = false;
            try {
                face.rasterize(id, new GlyphRasterStyle(Float.MAX_VALUE, Float.MAX_VALUE));
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected, "extreme variant allocated instead of rejecting");
            List<ShapedGlyph> after = face.shape(text, direction, null);
            require(after.size() == shaped.size(), "variant mutated shaping face");
            for (int i = 0; i < after.size(); i++) {
                ShapedGlyph a = shaped.get(i), b = after.get(i);
                require(
                        a.glyphId == b.glyphId
                                && a.cluster == b.cluster
                                && a.xAdvance == b.xAdvance
                                && a.yAdvance == b.yAdvance
                                && a.xOffset == b.xOffset
                                && a.yOffset == b.yOffset,
                        "variant mutated HB identity/position");
            }
            System.out.println(
                    "native source="
                            + source.id
                            + " em="
                            + em
                            + " glyphs="
                            + checked
                            + " scale="
                            + face.rasterize(id).pixelScale
                            + " normalStable=true knownInkContainsPixels=true color="
                            + source.color);
        }
    }

    private static void waitReady(AsyncGlyphRasterizer worker, List<GlyphKey> keys)
            throws Exception {
        long end = System.nanoTime() + 10000000000L;
        for (; ; ) {
            boolean all = true;
            for (GlyphKey key : keys) all &= worker.peek(key) != null;
            if (all) return;
            require(
                    worker.workerFailure() == null && worker.failures().isEmpty(),
                    "worker failed " + worker.workerFailure() + " " + worker.failures());
            require(System.nanoTime() < end, "variant worker timed out");
            worker.drainReady(32);
            Thread.sleep(2);
        }
    }

    private void cacheCase(FontSource source, File fonts) throws Exception {
        int glyphId;
        long normalBytes;
        try (FontSession service = new LwjglFontEngine().openSession();
                FontFace face = source.openFace(service, 18.375f)) {
            glyphId = face.glyphIndex('A');
            GlyphBitmap b = face.rasterize(glyphId);
            normalBytes = (long) b.width * b.height;
            require(!b.color && b.byteSize() == normalBytes, "正式单色位图未按覆盖率计费");
        }
        AsyncGlyphRasterizer worker =
                new AsyncGlyphRasterizer(
                        new AsyncGlyphRasterizer.Budget(
                                16, 16, 16, 4 * 1024 * 1024, 2, 64 * 1024 * 1024, 8, 64),
                        new LwjglFontEngine());
        onClose(
                () ->
                        require(
                                worker.awaitClose(10000) && worker.stats().faces == 0,
                                "owned native worker close"));
        own(worker);
        {
            List<GlyphKey> keys = new ArrayList<GlyphKey>();
            for (GlyphRasterStyle style : STYLES) {
                GlyphKey key = new GlyphKey(source, 18.375f, glyphId, style);
                keys.add(key);
                for (int i = 0; i < 20; i++)
                    worker.request(new GlyphKey(source, 18.375f, glyphId, style));
            }
            require(new java.util.HashSet<GlyphKey>(keys).size() == 4, "variants alias in key");
            TextLayout.Glyph declared =
                    new TextLayout.Glyph(
                            source,
                            18.375f,
                            glyphId,
                            0,
                            1,
                            0,
                            1,
                            0,
                            0,
                            0,
                            0,
                            false,
                            false,
                            InkBounds.UNKNOWN,
                            9.1875f,
                            0.5f,
                            STYLES[3],
                            null,
                            0);
            require(
                    GlyphKey.of(declared).equals(keys.get(3)),
                    "GlyphKey.of lost rasterStyle/actual em");
            waitReady(worker, keys);
            long sum = 0;
            for (GlyphKey key : keys) {
                GlyphBitmap b = worker.peek(key);
                require(!b.color && b.byteSize() == (long) b.width * b.height, "单色变体未保持覆盖率存储");
                sum += (long) b.width * b.height;
                worker.request(key);
            }
            AsyncGlyphRasterizer.Stats stats = worker.stats();
            require(
                    stats.rasterized == 4
                            && stats.faces == 1
                            && stats.cacheEntries == 4
                            && stats.bitmapBytes == sum,
                    "variant cache/face/byte budget wrong");
            byte[] old = worker.peek(keys.get(0)).pixels();
            FontSource newer =
                    new FontSource(
                            source.id,
                            2,
                            "newer",
                            TestResources.bytes(new File(fonts, source.id).toPath()).array(),
                            0,
                            false,
                            Collections.emptySet());
            GlyphKey newerKey = new GlyphKey(newer, 18.375f, glyphId);
            require(!keys.get(0).equals(newerKey), "source revision aliased");
            worker.request(newerKey);
            waitReady(worker, Collections.singletonList(newerKey));
            require(
                    worker.stats().rasterized == 5
                            && worker.stats().cacheEntries == 5
                            && worker.stats().faces == 2,
                    "new source reused old cache/face");
            require(
                    Arrays.equals(old, worker.peek(keys.get(0)).pixels()),
                    "new source mutated old pixels");
            System.out.println(
                    "sourceGenerations cacheEntries="
                            + worker.stats().cacheEntries
                            + " faces="
                            + worker.stats().faces
                            + " oldSnapshotStable=true");
            System.out.println(
                    "cache variants=4 faces=1 rasterized="
                            + stats.rasterized
                            + " deduplicated="
                            + stats.deduplicated
                            + " bytes="
                            + sum);
        }
        AsyncGlyphRasterizer limited =
                new AsyncGlyphRasterizer(
                        new AsyncGlyphRasterizer.Budget(
                                4, 4, 4, Math.max(4, normalBytes), 1, 64 * 1024 * 1024, 4, 64),
                        new LwjglFontEngine());
        onClose(() -> require(limited.awaitClose(10000), "limited worker close"));
        own(limited);
        {
            GlyphKey large = new GlyphKey(source, 18.375f, glyphId, new GlyphRasterStyle(256, 0));
            limited.request(large);
            long end = System.nanoTime() + 10000000000L;
            while (limited.failures().isEmpty()) {
                require(System.nanoTime() < end, "budget rejection timeout");
                Thread.sleep(2);
            }
            require(
                    limited.peek(large) == null
                            && limited.stats().bitmapBytes == 0
                            && limited.workerFailure() == null,
                    "expanded bitmap bypassed budget");
            System.out.println("variantBudget rejectedExpandedBitmap=true bytes=0");
        }
    }

    public static void main(String[] args) throws Exception {
        new GlyphRasterStyleScenario().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        String[] args = arguments();
        File fonts =
                TestResources.argumentPath(args, 0, "src/core/resources/assets/qz_uilib/fonts")
                        .toFile();
        File output =
                TestResources.outputDirectory(args, 1, "build/font-raster-style-probe/output")
                        .toFile();
        scenario(
                "变体参数校验",
                () -> {
                    require(
                            GlyphRasterStyle.NORMAL.equals(new GlyphRasterStyle(-0f, -0f)),
                            "signed zero style");
                    boolean invalid = false;
                    try {
                        new GlyphRasterStyle(-1, 0);
                    } catch (IllegalArgumentException expected) {
                        invalid = true;
                    }
                    require(invalid, "negative offset");
                });
        FontSource unifont = source(fonts, "unifont/unifont-16.0.04.otf", false);
        scenario(
                "Unifont浮点em变体",
                () -> nativeCase(unifont, 18.375f, "A中", ShapeDirection.LTR, output));
        scenario(
                "Arabic原生变体",
                () ->
                        nativeCase(
                                source(fonts, "noto/NotoSansArabic.ttf", false),
                                24.375f,
                                "سلام",
                                ShapeDirection.RTL,
                                output));
        scenario(
                "Indic原生变体",
                () ->
                        nativeCase(
                                source(fonts, "noto/NotoSansDevanagari.ttf", false),
                                20.375f,
                                "क्षि",
                                ShapeDirection.LTR,
                                output));
        scenario(
                "Arabic小于1em原生变体",
                () ->
                        nativeCase(
                                source(fonts, "noto/NotoSansArabic.ttf", false),
                                0.375f,
                                "س",
                                ShapeDirection.RTL,
                                output));
        scenario(
                "彩色Emoji原生变体",
                () ->
                        nativeCase(
                                source(fonts, "noto/NotoColorEmoji.ttf", true),
                                23.375f,
                                "😀",
                                ShapeDirection.LTR,
                                output));
        scenario("异步worker变体缓存及预算", () -> cacheCase(unifont, fonts));
        System.out.println(
                "GLYPH_RASTER_STYLE_SCENARIO_PASS nativeVariants premultColor floatEm knownInk normalBytes faceReuse keyDedup boundedCache ownedWorkerClose");
    }
}
