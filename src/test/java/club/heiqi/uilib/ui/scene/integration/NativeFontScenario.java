package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.TestResources;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapedGlyph;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeContext;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontMetrics;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;

/**
 * 测试机真实 FreeType/HarfBuzz 场景，无 MC、Forge 或 AWT。
 * 参数为字体目录，包含 NotoSansArabic.ttf、NotoSansDevanagari.ttf、NotoSansThai.ttf、NotoColorEmoji.ttf。
 * 输出真实 glyph/cluster、metrics、彩色像素与尺寸数据，并检查内存所有权、缩放、线程和关闭契约。
 */
public final class NativeFontScenario extends TestApplication {

    private static void verifyTextInput() {
        String original = "中文 العربية हिन्दी ภาษาไทย 😀 👩‍💻";
        byte[] encoded = original.getBytes(StandardCharsets.UTF_8);
        byte[] saved = encoded.clone();
        TextInput input = TextInput.utf8(encoded);
        require(
                input.text.equals(original) && input.invalidRanges().isEmpty(),
                "正常多语 UTF-8 roundtrip 失败");
        require(Arrays.equals(saved, encoded), "UTF-8 解码修改了来源字节");
        int utf16 = 0, bytes = 0;
        while (utf16 < original.length()) {
            int count = Character.charCount(original.codePointAt(utf16));
            for (int i = 0; i < count; i++)
                require(input.sourceOffset(utf16 + i) == bytes, "正常多语来源 byte offset 错误");
            bytes +=
                    original.substring(utf16, utf16 + count)
                            .getBytes(StandardCharsets.UTF_8)
                            .length;
            utf16 += count;
        }
        require(input.sourceOffset(original.length()) == encoded.length, "UTF-8 终点来源错误");
        malformedUtf8(
                "ED A0 80",
                new byte[] {(byte) 0xed, (byte) 0xa0, (byte) 0x80},
                "���",
                new int[] {0, 1, 2, 3},
                new int[][] {{0, 1}, {1, 2}, {2, 3}});
        malformedUtf8(
                "E1 80 truncated",
                new byte[] {(byte) 0xe1, (byte) 0x80},
                "�",
                new int[] {0, 2},
                new int[][] {{0, 2}});
        malformedUtf8(
                "F4 90 80 80 out-of-range",
                new byte[] {(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80},
                "����",
                new int[] {0, 1, 2, 3, 4},
                new int[][] {{0, 1}, {1, 2}, {2, 3}, {3, 4}});
        String lowSurrogate = "a\uDC00😀z";
        TextInput fromString = TextInput.string(lowSurrogate);
        require(fromString.text.equals("a�😀z"), "孤立低代理未替换为 U+FFFD");
        int[] offsets = {0, 1, 2, 2, 4, 5};
        for (int i = 0; i < offsets.length; i++)
            require(fromString.sourceOffset(i) == offsets[i], "String 代理来源索引错误");
        require(
                fromString.invalidRanges().size() == 1
                        && fromString.invalidRanges().get(0).start == 1
                        && fromString.invalidRanges().get(0).end == 2,
                "低代理非法来源范围错误");
        require(lowSurrogate.charAt(1) == '\uDC00', "原始 String 被显示替代污染");
        System.out.println(
                "PASS TextInput 多语UTF-8 roundtrip、maximal subpart replacement、原byte/UTF-16来源与孤立低代理");
    }

    private static void malformedUtf8(
            String label, byte[] source, String expected, int[] offsets, int[][] ranges) {
        byte[] saved = source.clone();
        TextInput decoded = TextInput.utf8(source);
        require(decoded.text.equals(expected), label + " replacement 数量错误");
        require(Arrays.equals(source, saved), label + " 来源字节被修改");
        require(decoded.invalidRanges().size() == ranges.length, label + " 非法范围数量错误");
        for (int i = 0; i < offsets.length; i++)
            require(decoded.sourceOffset(i) == offsets[i], label + " 来源索引错误");
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < ranges.length; i++) {
            TextInput.InvalidRange range = decoded.invalidRanges().get(i);
            require(
                    range.start == ranges[i][0] && range.end == ranges[i][1],
                    label + " maximal subpart 范围错误");
            actual.append('[').append(range.start).append(',').append(range.end).append(") ");
        }
        System.out.println(
                "TextInput "
                        + label
                        + " replacements="
                        + decoded.text.length()
                        + " ranges="
                        + actual);
    }

    public static void main(String[] args) throws Exception {
        new NativeFontScenario().run(args);
    }

    private static void sameShape(List<ShapedGlyph> expected, List<ShapedGlyph> actual) {
        require(expected.size() == actual.size(), "prepared 上下文字形数量改变");
        for (int i = 0; i < expected.size(); i++) {
            ShapedGlyph a = expected.get(i), b = actual.get(i);
            require(a.glyphId == b.glyphId && a.cluster == b.cluster
                            && a.xAdvance == b.xAdvance && a.yAdvance == b.yAdvance
                            && a.xOffset == b.xOffset && a.yOffset == b.yOffset,
                    "prepared 上下文改变字形、UTF-16 cluster 或真实定位");
        }
    }

    private static void reject(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            require(type.isInstance(failure), "拒绝原因不符: " + failure);
            return;
        }
        throw new AssertionError("缺少预期拒绝: " + type.getName());
    }

    private void preparedContexts() throws Exception {
        String text = "ببب हिन्दी 😀";
        int indicStart = text.indexOf("हिन्दी"), indicEnd = indicStart + "हिन्दी".length();
        ShapeContext shared = service.prepareShapeContext(text);
        try (FontFace arabic = service.openFace(TestResources.font(directory, "NotoSansArabic"), 0, 32);
                FontFace indic = service.openFace(TestResources.font(directory, "NotoSansDevanagari"), 0, 32)) {
            try (ShapeContext owned = shared) {
                List<ShapedGlyph> middle = arabic.shape(
                        owned, 1, 2, ShapeDirection.RTL, "ar", "Arab", false, false);
                sameShape(arabic.shape(text, 1, 2, ShapeDirection.RTL, "ar", "Arab", false, false), middle);
                List<ShapedGlyph> whole = arabic.shape(
                        text, 0, 3, ShapeDirection.RTL, "ar", "Arab", true, false);
                ShapedGlyph medial = whole.stream().filter(g -> g.cluster == 1).findFirst().orElse(null);
                require(medial != null && middle.size() == 1 && middle.get(0).glyphId == medial.glyphId
                                && middle.get(0).glyphId != arabic.shape("ب", ShapeDirection.RTL, "ar").get(0).glyphId,
                        "短 run 没有保留两侧 Arabic 连接上下文");
                sameShape(indic.shape(text, indicStart, indicEnd, ShapeDirection.LTR, "hi", "Deva", false, false),
                        indic.shape(owned, indicStart, indicEnd, ShapeDirection.LTR, "hi", "Deva", false, false));
                for (boolean bot : new boolean[] {false, true})
                    for (boolean eot : new boolean[] {false, true})
                        sameShape(arabic.shape(text, 0, 3, ShapeDirection.RTL, "ar", "Arab", bot, eot),
                                arabic.shape(owned, 0, 3, ShapeDirection.RTL, "ar", "Arab", bot, eot));
                reject(IllegalArgumentException.class, () -> arabic.shape(
                        owned, text.length() - 1, text.length(), ShapeDirection.LTR, "und", null, false, true));
                reject(IllegalStateException.class, () -> service.prepareShapeContext(text));
                try (FontSession foreign = new LwjglFontEngine().openSession();
                        FontFace face = foreign.openFace(TestResources.font(directory, "NotoSansArabic"), 0, 32)) {
                    reject(IllegalArgumentException.class, () -> face.shape(
                            owned, 0, 3, ShapeDirection.RTL, "ar", "Arab", true, false));
                }
                arabic.close();
                try (FontFace reopened = service.openFace(TestResources.font(directory, "NotoSansArabic"), 0, 24)) {
                    sameShape(reopened.shape(text, 1, 2, ShapeDirection.RTL, "ar", "Arab", false, false),
                            reopened.shape(owned, 1, 2, ShapeDirection.RTL, "ar", "Arab", false, false));
                }
            }
            reject(IllegalStateException.class, shared::text);
            reject(IllegalStateException.class, () -> indic.shape(
                    shared, indicStart, indicEnd, ShapeDirection.LTR, "hi", "Deva", false, false));
            shared.close();
            try (ShapeContext next = service.prepareShapeContext(text)) {
                require(next.text().equals(text), "上下文释放后 session 无法继续准备");
            }
        } finally {
            shared.close();
        }
    }

    private Path directory;
    private FontSession service;

    @Override
    protected void setup() throws Exception {
        directory = TestResources.argumentPath(arguments(), 0, "build/font-native-probe");
        service = own(new LwjglFontEngine().openSession());
    }

    @Override
    protected void runTests() throws Exception {
        scenario("多语输入与非法来源映射", NativeFontScenario::verifyTextInput);
        String[] names = {
            "NotoSansArabic",
            "NotoSansDevanagari",
            "NotoSansThai",
            "NotoColorEmoji",
            "NotoColorEmoji"
        };
        String[] texts = {"العربية", "हिन्दी", "ภาษาไทย", "😀", "👩‍💻"};
        String[] languages = {"ar", "hi", "th", "und", "und"};
        for (int i = 0; i < texts.length; i++) {
            final int index = i;
            scenario(
                    "实际整形像素与内存所有权-" + names[index] + "-" + i,
                    () -> {
                        ByteBuffer source = TestResources.font(directory, names[index]);
                        try (FontFace face = service.openFace(source, 0, 32.3f)) {
                            // 原缓冲立即改写，验证 FT memory face 使用服务持有的独立副本。
                            Arrays.fill(source.array(), (byte) 0);
                            ShapeDirection direction =
                                    index == 0 ? ShapeDirection.RTL : ShapeDirection.LTR;
                            String context = "xx" + texts[index] + "yy";
                            List<ShapedGlyph> run =
                                    face.shape(
                                            context,
                                            2,
                                            2 + texts[index].length(),
                                            direction,
                                            languages[index],
                                            null,
                                            false,
                                            false);
                            require(!run.isEmpty(), "shape 结果为空");
                            if (index < 3) {
                                String script = new String[] {"Arab", "Deva", "Thai"}[index];
                                require(face.supportsScript(script), "实际脚本布局事实丢失: " + script);
                            }
                            require(face.hasColorGlyph(run.get(0).glyphId) == (index >= 3),
                                    "逐 glyph 的非渲染颜色事实与实际字体不符");
                            float advance = 0;
                            StringBuilder glyphs = new StringBuilder();
                            for (ShapedGlyph glyph : run) {
                                require(glyph.glyphId != 0, "出现缺字");
                                require(
                                        glyph.cluster >= 2
                                                && glyph.cluster < 2 + texts[index].length(),
                                        "cluster 未使用原串 UTF-16 索引");
                                advance += glyph.xAdvance;
                                glyphs.append(glyph.glyphId)
                                        .append('@')
                                        .append(glyph.cluster)
                                        .append(' ');
                            }
                            GlyphBitmap bitmap = face.rasterize(run.get(0).glyphId);
                            require(
                                    bitmap.pixels().length == bitmap.width * bitmap.height * 4,
                                    "像素尺寸错误");
                            require(bitmap.pixelsBuffer().isReadOnly(), "像素缓冲可写");
                            byte[] pixels = bitmap.pixels();
                            int visiblePixels = 0;
                            boolean chromatic = false;
                            for (int p = 0; p < pixels.length; p += 4) {
                                int alpha = pixels[p + 3] & 255;
                                if (alpha > 0) visiblePixels++;
                                for (int c = 0; c < 3; c++)
                                    require(
                                            (pixels[p + c] & 255) <= alpha,
                                            "颜色未 premultiply alpha");
                                chromatic |=
                                        pixels[p] != pixels[p + 1]
                                                || pixels[p + 1] != pixels[p + 2];
                            }
                            if (index >= 3)
                                require(
                                        bitmap.color && chromatic && visiblePixels > 0,
                                        "emoji 未输出真实彩色像素");
                            FontMetrics metrics = face.metrics();
                            require(
                                    metrics.ascent > 0 && metrics.lineHeight > 0 && advance > 0,
                                    "真实字体 metrics 错误");
                            System.out.println(
                                    names[index]
                                            + " text="
                                            + texts[index]
                                            + " glyphs="
                                            + run.size()
                                            + " ids@clusters="
                                            + glyphs
                                            + " advance="
                                            + advance
                                            + " metrics="
                                            + metrics.ascent
                                            + ","
                                            + metrics.descent
                                            + ","
                                            + metrics.lineHeight
                                            + " bitmap="
                                            + bitmap.width
                                            + "x"
                                            + bitmap.height
                                            + " bearing="
                                            + bitmap.bearingX
                                            + ","
                                            + bitmap.bearingY
                                            + " scale="
                                            + bitmap.pixelScale
                                            + " draw="
                                            + bitmap.width * bitmap.pixelScale
                                            + "x"
                                            + bitmap.height * bitmap.pixelScale
                                            + " color="
                                            + bitmap.color
                                            + " visiblePixels="
                                            + visiblePixels);
                        }
                    });
        }
        scenario(
                "彩色strike缩放比例",
                () -> {
                    try (FontFace small =
                                    service.openFace(
                                            TestResources.font(directory, "NotoColorEmoji"),
                                            0,
                                            16.15f);
                            FontFace large =
                                    service.openFace(
                                            TestResources.font(directory, "NotoColorEmoji"),
                                            0,
                                            32.3f)) {
                        GlyphBitmap a = small.rasterize(small.glyphIndex(0x1f600));
                        GlyphBitmap b = large.rasterize(large.glyphIndex(0x1f600));
                        float pixelRatio = b.pixelScale / a.pixelScale;
                        float metricRatio = large.metrics().lineHeight / small.metrics().lineHeight;
                        float advanceRatio =
                                large.shape("😀", ShapeDirection.LTR, "und").get(0).xAdvance
                                        / small.shape("😀", ShapeDirection.LTR, "und")
                                                .get(0)
                                                .xAdvance;
                        require(Math.abs(pixelRatio - 2) < 0.00001f, "strike 像素比例错误");
                        require(Math.abs(metricRatio - 2) < 0.00001f, "strike metrics 比例错误");
                        require(Math.abs(advanceRatio - 2) < 0.00001f, "strike shape 比例错误");
                        System.out.println(
                                "strike ratios pixels="
                                        + pixelRatio
                                        + " metrics="
                                        + metricRatio
                                        + " advance="
                                        + advanceRatio);
                    }
                });
        scenario(
                "无效字体失败后服务仍可用",
                () -> {
                    for (int i = 0; i < 3; i++) {
                        try {
                            service.openFace(ByteBuffer.wrap(new byte[] {1, 2, 3}), 0, 16);
                            throw new AssertionError("无效字体被接受");
                        } catch (IllegalStateException expected) {
                            // 验证失败后同一服务还可创建真实 face。
                        }
                    }
                });
        scenario(
                "小于1em的真实FT归一",
                () -> {
                    try (FontFace one =
                                    service.openFace(
                                            TestResources.font(directory, "NotoSansArabic"), 0, 1);
                            FontFace half =
                                    service.openFace(
                                            TestResources.font(directory, "NotoSansArabic"),
                                            0,
                                            0.5f)) {
                        require(
                                half.metrics().lineHeight == one.metrics().lineHeight * 0.5f,
                                "FT最小1pt必须按请求em归一");
                        GlyphBitmap full = one.rasterize(one.glyphIndex(0x627));
                        GlyphBitmap reduced = half.rasterize(half.glyphIndex(0x627));
                        require(reduced.pixelScale == full.pixelScale * 0.5f, "小于1px的真实光栅缩放错误");
                        System.out.println("PASS em<1实际FT尺寸归一：" + reduced.pixelScale);
                    }
                });
        scenario("prepared上下文跨run/face、连接、边界与定界释放", this::preparedContexts);
        scenario(
                "长上下文线程归属与关闭契约",
                () -> {
                    final FontFace open =
                            service.openFace(
                                    TestResources.font(directory, "NotoSansArabic"), 0, 16);
                    // 完整段落超过LWJGL栈编码容量，仍可shape短run，且cluster必须保留原文位置。
                    char[] padding = new char[40000];
                    Arrays.fill(padding, 'x');
                    String longContext = new String(padding) + "العربية";
                    List<ShapedGlyph> longRun =
                            open.shape(
                                    longContext,
                                    padding.length,
                                    longContext.length(),
                                    ShapeDirection.RTL,
                                    "ar",
                                    null,
                                    false,
                                    true);
                    try (ShapeContext prepared = service.prepareShapeContext(longContext)) {
                        sameShape(longRun, open.shape(prepared, padding.length, longContext.length(),
                                ShapeDirection.RTL, "ar", null, false, true));
                    }
                    require(!longRun.isEmpty(), "长段落短run整形为空");
                    for (ShapedGlyph glyph : longRun)
                        require(
                                glyph.cluster >= padding.length && glyph.glyphId != 0,
                                "长段落源文位置或字形错误");
                    System.out.println(
                            "PASS 超64KiB UTF16上下文短run：" + longContext.length() + " units");
                    final ShapeContext pending = service.prepareShapeContext(longContext);
                    final Throwable[] failure = new Throwable[1];
                    Thread foreign =
                            new Thread(
                                    () -> {
                                        try {
                                            reject(IllegalStateException.class, open::metrics);
                                            reject(IllegalStateException.class, pending::text);
                                            reject(IllegalStateException.class, pending::close);
                                            reject(IllegalStateException.class, () -> open.shape(
                                                    pending, padding.length, longContext.length(),
                                                    ShapeDirection.RTL, "ar", null, false, true));
                                        } catch (Throwable unexpected) {
                                            failure[0] = unexpected;
                                        }
                                    });
                    foreign.start();
                    foreign.join();
                    if (failure[0] != null) throw new AssertionError(failure[0]);
                    service.close();
                    service.close();
                    reject(IllegalStateException.class, pending::text);
                    pending.close();
                    open.close();
                    try {
                        open.metrics();
                        throw new AssertionError("关闭后的 face 可访问");
                    } catch (IllegalStateException expected) {
                        // service close 已关闭全部未主动关闭的 face；重复 close 合法。
                    }
                    System.out.println(
                            "PASS 字体内存所有权、UTF-16上下文、真实脚本shape、彩色premultiplied、strike缩放、异常清理、线程和关闭契约");
                });
    }
}
