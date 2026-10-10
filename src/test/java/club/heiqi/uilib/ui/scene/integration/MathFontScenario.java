package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.MathAssembly;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.MathData;
import club.heiqi.uilib.ui.scene.text.math.MathFont;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.lwjgl.util.harfbuzz.HarfBuzz;
import org.lwjgl.util.harfbuzz.OpenType;
import org.lwjgl.util.harfbuzz.hb_ot_math_glyph_variant_t;
import org.lwjgl.util.harfbuzz.hb_ot_math_glyph_part_t;

/** 真实 FT/HB 数学字体场景；独立 raw 探针核对单位、variant、光栅及资源生命周期，无 MC/GL。 */
public final class MathFontScenario extends TestApplication {
    private static final String RESOURCE = "assets/qz_uilib/fonts/math/STIXTwoMath-Regular.otf";
    private byte[] bytes;

    public static void main(String[] args) throws Exception {
        new MathFontScenario().run(args);
    }

    @Override
    protected void setup() throws Exception {
        bytes = resource(RESOURCE);
    }

    @Override
    protected void runTests() throws Exception {
        scenario("官方字体MATH与完整许可", this::verifyResource);
        scenario("原始HB常量单位及真实variant光栅", this::verifyNative);
        scenario("真实assembly连接与多em大型根号圆花括号", this::verifyAssemblies);
        scenario("零extender非法连接及等量伸展边界", this::verifyAssemblyEdges);
        scenario("显示算子建议高度与严格伸缩分离", this::verifyDisplayOperators);
        scenario("具名隔离缺字超限及显式链边界", this::verifyBoundaries);
        scenario("损坏候选资源诊断与后继数学字形", this::verifyResourceWarnings);
        scenario("真实face-LRU与source换代重载关闭", this::verifyLifetime);
    }

    private void verifyResource() throws Exception {
        StringBuilder hash = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
            hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        require(
                hash.toString()
                        .equals("3a5f3f26f40d5698b3c62dd085d48d6663696a3f80825aab8b553d5097518e8c"),
                "随包字体不是固定官方 v2.13b171");
        int math = table(bytes, "MATH");
        require(math > 0, "随包字体没有 MATH 表");
        ByteBuffer sfnt = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        require(sfnt.getInt(math) == 0x00010000, "MATH 版本不为 1.0");
        String license =
                new String(resource("assets/qz_uilib/fonts/math/OFL.txt"), StandardCharsets.UTF_8);
        require(
                license.startsWith("Copyright 2001-2021 The STIX Fonts Project Authors")
                        && license.contains("SIL OPEN FONT LICENSE Version 1.1")
                        && license.contains("TERMINATION")
                        && license.trim().endsWith("OTHER DEALINGS IN THE FONT SOFTWARE."),
                "官方完整许可缺失");
        System.out.println("STIX v2.13b171 SHA256=" + hash + " MATH offset=" + math);
    }

    private void verifyNative() throws Exception {
        try (FontTextService service = new FontTextService(new LwjglFontEngine())) {
            // 先由正式作用域加载官方 FT/HB 绑定，raw 探针随后使用同一实际 native 版本。
            for (float em : new float[] {32.3f, 0.75f}) {
                MathFont math = service.mathFont(style(em));
                require(math != null, "默认构造未加载具名数学资源");
                try (RawMath raw = new RawMath(bytes, em)) {
                    int constants = table(bytes, "MATH");
                    ByteBuffer sfnt = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
                    constants += sfnt.getShort(constants + 4) & 65535;
                    near(
                            math.constant(MathFont.Constant.SCRIPT_PERCENT_SCALE_DOWN),
                            sfnt.getShort(constants),
                            "script百分比被按位置量缩放");
                    near(
                            math.constant(MathFont.Constant.SCRIPT_SCRIPT_PERCENT_SCALE_DOWN),
                            sfnt.getShort(constants + 2),
                            "scriptscript百分比被按位置量缩放");
                    near(
                            math.constant(MathFont.Constant.RADICAL_DEGREE_BOTTOM_RAISE_PERCENT),
                            sfnt.getShort(constants + 212) & 65535,
                            "根指数百分比被缩放");
                    for (MathFont.Constant constant : MathFont.Constant.values()) {
                        int nativeValue = OpenType.hb_ot_math_get_constant(raw.font, constant.id);
                        near(
                                math.constant(constant),
                                constant.percentage() ? nativeValue : nativeValue * raw.scale,
                                "MATH常量单位 " + constant);
                    }
                    System.out.println(
                            "MATH em="
                                    + em
                                    + " axis="
                                    + math.constant(MathFont.Constant.AXIS_HEIGHT)
                                    + " script="
                                    + math.constant(MathFont.Constant.SCRIPT_PERCENT_SCALE_DOWN)
                                    + " scriptscript="
                                    + math.constant(
                                            MathFont.Constant.SCRIPT_SCRIPT_PERCENT_SCALE_DOWN));
                    verifyVariant(service, math, raw, em, 0x222b);
                    verifyVariant(service, math, raw, em, 0x221a);
                }
            }
        }
    }

    private void verifyVariant(
            FontTextService service, MathFont math, RawMath raw, float em, int cp) {
        int base = FreeType.FT_Get_Char_Index(raw.face, cp);
        List<MathData.Variant> variants = raw.variants(base);
        require(variants.size() > 1, "官方数学符号没有多个真实variant");
        MathData.Variant largest = variants.get(0);
        for (MathData.Variant variant : variants)
            if (variant.verticalAdvance > largest.verticalAdvance) largest = variant;
        TextLayout layout = math.stretch(cp, largest.verticalAdvance);
        TextLayout.Glyph glyph = onlyGlyph(layout);
        require(glyph.glyphId == largest.glyphId && glyph.glyphId != base, "伸缩结果不是实际非基础variant ID");
        int rawAdvance = HarfBuzz.hb_font_get_glyph_h_advance(raw.font, glyph.glyphId);
        near(glyph.xAdvance, rawAdvance * raw.scale, "竖向advance冒充横向advance");
        require(Math.abs(glyph.xAdvance - largest.verticalAdvance) > 0.01f, "探针输入无法区分横向与竖向advance");
        require(
                glyph.size == em && glyph.logicalEm == em && glyph.rasterToLayout == 1,
                "variant任意缩放或字号身份丢失");
        require(
                glyph.x + glyph.inkBounds.left >= 0
                        && glyph.x + glyph.inkBounds.right <= layout.advanceWidth,
                "variant负bearing或右侧墨迹未纳宽度");
        TextLayout.Line line = layout.lines().get(0);
        near(line.ascent, Math.max(0, -glyph.inkBounds.top), "variant上extent不是墨迹");
        near(line.descent, Math.max(0, glyph.inkBounds.bottom), "variant下extent不是墨迹");
        near(line.baseline, line.ascent, "variant基线错位");
        near(
                math.italicCorrection(layout),
                OpenType.hb_ot_math_get_glyph_italics_correction(raw.font, glyph.glyphId)
                        * raw.scale,
                "倾斜修正不是实际variant值");
        require(layout.generation == service.generation(), "数学布局代际丢失");
        GlyphKey key = GlyphKey.of(glyph);
        require(
                key.glyphId == glyph.glyphId && key.source == glyph.source && key.size == em,
                "variant未沿既有GlyphKey");
        try (FontSession raster = new LwjglFontEngine().openSession();
                FontFace face = key.source.openFace(raster, key.size)) {
            GlyphBitmap bitmap = face.rasterize(key.glyphId, key.rasterStyle);
            boolean visible = false;
            for (int i = 3; i < bitmap.pixels().length; i += 4)
                if ((bitmap.pixels()[i] & 255) != 0) {
                    visible = true;
                    break;
                }
            require(visible, "真实variant光栅没有可见像素");
        }
        System.out.println(
                "variant U+"
                        + Integer.toHexString(cp)
                        + " em="
                        + em
                        + " base="
                        + base
                        + " glyph="
                        + glyph.glyphId
                        + " vertical="
                        + largest.verticalAdvance
                        + " raw_h_advance="
                        + rawAdvance
                        + " horizontal="
                        + glyph.xAdvance
                        + " ink="
                        + glyph.inkBounds.left
                        + ","
                        + glyph.inkBounds.top
                        + ","
                        + glyph.inkBounds.right
                        + ","
                        + glyph.inkBounds.bottom);
        final float beyond = largest.verticalAdvance + Math.max(1, em);
        if (raw.assembly(base) == null)
            expect(UnsupportedOperationException.class, () -> math.stretch(cp, beyond));
        else
            require(
                    math.stretch(cp, beyond).lines().get(0).glyphs().size() > 1,
                    "超过完整variant后没有使用真实assembly");
    }

    private void verifyAssemblies() throws Exception {
        try (FontTextService service = new FontTextService(new LwjglFontEngine())) {
            for (float em : new float[] {0.75f, 18, 32.3f, 64}) {
                MathFont math = service.mathFont(style(em));
                FontSource source = onlyGlyphSource(math.text("x"));
                try (RawMath raw = new RawMath(bytes, em);
                        FontSession session = new LwjglFontEngine().openSession();
                        FontFace face = source.openFace(session, em)) {
                    for (int cp : new int[] {'(', ')', '{', '}', 0x221a, 0x222b}) {
                        int base = FreeType.FT_Get_Char_Index(raw.face, cp);
                        MathData.Assembly expected = raw.assembly(base);
                        MathData.Assembly snapshot = face.mathAssembly(base);
                        require(expected != null && snapshot != null, "正式字体没有需要的assembly");
                        near(
                                snapshot.minOverlap,
                                expected.minOverlap,
                                "minOverlap没有按logical 26.6缩放");
                        near(
                                snapshot.italicCorrection,
                                expected.italicCorrection,
                                "assembly italic单位错误");
                        require(snapshot.parts().size() == expected.parts().size(), "部件快照数量错误");
                        int[] designOrder = assemblyGlyphOrder(bytes, base);
                        require(designOrder.length == expected.parts().size(), "原始SFNT部件数量不一致");
                        if (cp == 0x222b)
                            require(expected.italicCorrection > 0, "真实非零assembly italic反例未命中");
                        for (int i = 0; i < expected.parts().size(); i++) {
                            MathData.Part actual = snapshot.parts().get(i),
                                    part = expected.parts().get(i);
                            require(
                                    actual.glyphId == part.glyphId
                                            && actual.glyphId == designOrder[i]
                                            && actual.extender == part.extender,
                                    "HB底到顶部件顺序或extender身份错误");
                            near(actual.startConnector, part.startConnector, "startConnector单位错误");
                            near(actual.endConnector, part.endConnector, "endConnector单位错误");
                            near(actual.fullAdvance, part.fullAdvance, "fullAdvance单位错误");
                        }
                        expect(UnsupportedOperationException.class, () -> snapshot.parts().clear());
                        List<MathData.Variant> variants = raw.variants(base);
                        float maximum = 0;
                        for (MathData.Variant variant : variants)
                            maximum = Math.max(maximum, variant.verticalAdvance);
                        require(
                                onlyGlyph(math.stretch(cp, maximum)).glyphId
                                        == variants.get(variants.size() - 1).glyphId,
                                "完整variant可用时错误进入assembly");
                        for (float target :
                                new float[] {
                                    maximum + em * .25f,
                                    maximum + em,
                                    Math.max(maximum + em, 8 * em),
                                    16 * em
                                }) {
                            TextLayout layout = math.stretch(cp, target);
                            verifyAssemblyLayout(math, raw, face, expected, layout, em, cp, target);
                        }
                    }
                    expect(
                            UnsupportedOperationException.class,
                            () -> math.stretch('(', Float.MAX_VALUE));
                }
            }
        }
    }

    /** 预期连接来自独立raw HB，按原点差反推实际overlap，不能由生产算法自证。 */
    private void verifyAssemblyLayout(
            MathFont math,
            RawMath raw,
            FontFace face,
            MathData.Assembly assembly,
            TextLayout layout,
            float em,
            int cp,
            float target) {
        require(layout.lines().size() == 1, "assembly不是单行文本事实");
        TextLayout.Line line = layout.lines().get(0);
        List<TextLayout.Glyph> glyphs = line.glyphs();
        require(glyphs.size() > 1, "大型符号仍是单一字形");
        int fixed = 0, extenders = 0;
        for (MathData.Part part : assembly.parts()) {
            if (part.extender) extenders++;
            else fixed++;
        }
        require(extenders > 0 && (glyphs.size() - fixed) % extenders == 0, "多个extender没有等次数增长");
        int repeats = (glyphs.size() - fixed) / extenders;
        List<MathData.Part> expanded = new ArrayList<MathData.Part>();
        for (MathData.Part part : assembly.parts())
            for (int i = 0, copies = part.extender ? repeats : 1; i < copies; i++)
                expanded.add(part);
        float tolerance = Math.max(.0001f, target * .000001f);
        float top = 0, bottom = 0, left = 0, right = 0;
        float[] overlaps = new float[glyphs.size() - 1];
        for (int i = 0; i < glyphs.size(); i++) {
            TextLayout.Glyph glyph = glyphs.get(i);
            MathData.Part part = expanded.get(i);
            require(glyph.glyphId == part.glyphId, "部件没有按底到顶顺序重复");
            require(
                    glyph.size == em
                            && glyph.logicalEm == em
                            && glyph.rasterToLayout == 1
                            && !glyph.replacement
                            && !glyph.color,
                    "assembly缩放轮廓或丢失真实字形身份");
            require(glyph.source.equals(glyphs.get(0).source), "assembly混入其他字体source");
            near(glyph.x, glyphs.get(0).x, "部件按ink各自硬对齐而非共用字形横向原点");
            near(
                    glyph.xAdvance,
                    HarfBuzz.hb_font_get_glyph_h_advance(raw.font, glyph.glyphId) * raw.scale,
                    "部件横向advance不是raw HB事实");
            InkBounds ink = face.glyphInkBounds(part.glyphId);
            near(glyph.inkBounds.left, ink.left, "部件left ink被改写");
            near(glyph.inkBounds.top, ink.top, "部件top ink被改写");
            near(glyph.inkBounds.right, ink.right, "部件right ink被改写");
            near(glyph.inkBounds.bottom, ink.bottom, "部件bottom ink被改写");
            top = Math.min(top, glyph.y - line.baseline + ink.top);
            bottom = Math.max(bottom, glyph.y - line.baseline + ink.bottom);
            left = Math.min(left, ink.left);
            right = Math.max(right, Math.max(glyph.xAdvance, ink.right));
            require(
                    glyph.sourceStart == 0
                            && glyph.sourceEnd == Character.charCount(cp)
                            && glyph.clusterIndex == 0,
                    "部件未保留原符号来源/cluster");
            GlyphKey key = GlyphKey.of(glyph);
            require(
                    key.glyphId == part.glyphId
                            && key.source.equals(glyph.source)
                            && key.size == em,
                    "部件没有进入正式GlyphKey身份");
            if (i > 0) {
                MathData.Part lower = expanded.get(i - 1);
                float distance = glyphs.get(i - 1).y - glyph.y;
                float overlap = lower.fullAdvance - distance;
                float maximum = Math.min(lower.endConnector, part.startConnector);
                require(
                        distance >= -tolerance
                                && overlap + tolerance >= assembly.minOverlap
                                && overlap <= maximum + tolerance,
                        "连接原点没有遵守fullAdvance和合法overlap: overlap="
                                + overlap
                                + " min="
                                + assembly.minOverlap
                                + " max="
                                + maximum);
                overlaps[i - 1] = overlap;
            }
        }
        float fullHeight =
                glyphs.get(0).y
                        - glyphs.get(glyphs.size() - 1).y
                        + expanded.get(expanded.size() - 1).fullAdvance;
        require(fullHeight + tolerance >= target, "assembly实际advance未覆盖目标");
        require(Math.abs(line.ascent + top) <= tolerance, "assembly上extent不是所有真实ink并集");
        require(Math.abs(line.descent - bottom) <= tolerance, "assembly下extent不是所有真实ink并集");
        near(layout.advanceWidth, right - left, "assembly横向extent丢失advance或ink");
        near(math.italicCorrection(layout), assembly.italicCorrection, "assembly italic错误取了末部件的修正");
        if (cp == '{' || cp == '}')
            for (int i = 0; i < overlaps.length; i++)
                require(
                        Math.abs(overlaps[i] - overlaps[overlaps.length - 1 - i]) <= tolerance,
                        "花括号两半连接伸展不对称");
        System.out.println(
                "assembly U+"
                        + Integer.toHexString(cp)
                        + " em="
                        + em
                        + " target="
                        + target
                        + " full="
                        + fullHeight
                        + " ink="
                        + layout.advanceHeight
                        + " glyphs="
                        + glyphs.size()
                        + " repeats="
                        + repeats
                        + " minOverlap="
                        + assembly.minOverlap
                        + " overlaps="
                        + Arrays.toString(overlaps));
    }

    private void verifyAssemblyEdges() {
        // 零次候选没有部件，必须继续加 extender；不能因这个候选不存在拒绝构建。
        MathData.Part extender = new MathData.Part(2, 4, 4, 10, true);
        MathData.Assembly onlyExtender =
                new MathData.Assembly(Collections.singletonList(extender), 2, 3);
        MathAssembly.Result grown = MathAssembly.build(onlyExtender, 15);
        require(grown.placements().size() == 2, "零部件候选没有进入有效extender构建");
        near(grown.verticalAdvance, 16, "最小合法overlap构建高度错误");
        near(grown.placements().get(1).offset, 6, "fullAdvance/overlap原点错误");

        // 两个连接容量不同：先达到最小overlap的连接停止，其余继续等量伸展。
        MathData.Assembly unequal =
                new MathData.Assembly(
                        Arrays.asList(
                                new MathData.Part(1, 0, 3, 10, false),
                                new MathData.Part(2, 3, 6, 10, false),
                                new MathData.Part(3, 6, 0, 10, false)),
                        2,
                        0);
        MathAssembly.Result saturated = MathAssembly.build(unequal, 24);
        near(saturated.verticalAdvance, 24, "不同连接容量求解错误");
        near(saturated.placements().get(1).offset, 8, "首连接未在最小overlap停止");
        near(saturated.placements().get(2).offset, 14, "其余连接没有继续均分伸展");
        expect(UnsupportedOperationException.class, () -> MathAssembly.build(unequal, 27));

        MathData.Part bar = new MathData.Part(2, 4, 4, 8, true);
        MathData.Assembly brace =
                new MathData.Assembly(
                        Arrays.asList(
                                new MathData.Part(1, 0, 3, 10, false),
                                bar,
                                new MathData.Part(3, 3, 3, 6, false),
                                bar,
                                new MathData.Part(4, 3, 0, 10, false)),
                        2,
                        0);
        MathAssembly.Result symmetric = MathAssembly.build(brace, 41);
        require(symmetric.placements().size() == 7, "多extender没有按相等次数增长");
        near(symmetric.verticalAdvance, 41, "对称构建未到目标");
        near(symmetric.placements().get(1).offset, 7.5f, "对称连接分配错误");
        near(symmetric.placements().get(6).offset, 31, "对称连接最终原点错误");

        MathData.Assembly invalid =
                new MathData.Assembly(
                        Arrays.asList(
                                new MathData.Part(1, 0, 1, 10, false),
                                extender,
                                new MathData.Part(3, 4, 0, 10, false)),
                        2,
                        0);
        expect(UnsupportedOperationException.class, () -> MathAssembly.build(invalid, 30));
        MathData.Assembly noGrowth =
                new MathData.Assembly(
                        Collections.singletonList(new MathData.Part(1, 2, 2, 2, true)), 2, 0);
        expect(UnsupportedOperationException.class, () -> MathAssembly.build(noGrowth, 3));
        expect(UnsupportedOperationException.class, () -> MathAssembly.build(onlyExtender, 100000));
        expect(
                UnsupportedOperationException.class,
                () -> MathAssembly.build(onlyExtender, Float.MAX_VALUE));
    }

    private void verifyDisplayOperators() throws Exception {
        try (FontTextService service = new FontTextService(new LwjglFontEngine())) {
            for (float em : new float[] {32.3f, 0.75f}) {
                MathFont math = service.mathFont(style(em));
                try (RawMath raw = new RawMath(bytes, em)) {
                    float preferred =
                            OpenType.hb_ot_math_get_constant(
                                            raw.font,
                                            MathFont.Constant.DISPLAY_OPERATOR_MIN_HEIGHT.id)
                                    * raw.scale;
                    near(
                            math.constant(MathFont.Constant.DISPLAY_OPERATOR_MIN_HEIGHT),
                            preferred,
                            "大算子建议高度不是raw MATH值");
                    for (int cp : new int[] {0x2211, 0x220f, 0x222b}) {
                        int base = FreeType.FT_Get_Char_Index(raw.face, cp);
                        List<MathData.Variant> variants = raw.variants(base);
                        MathData.Variant largest = null, expected = null;
                        for (MathData.Variant variant : variants) {
                            if (largest == null
                                    || variant.verticalAdvance > largest.verticalAdvance)
                                largest = variant;
                            if (variant.verticalAdvance >= preferred
                                    && (expected == null
                                            || variant.verticalAdvance < expected.verticalAdvance))
                                expected = variant;
                        }
                        require(largest != null, "探针缺少真实大算子variant");
                        if (cp == 0x222b) require(expected != null, "积分应有满足建议高度的变体");
                        else
                            require(
                                    expected == null && preferred > largest.verticalAdvance,
                                    "sum/prod建议高度应超所有真实variant");
                        if (expected == null) expected = largest;
                        TextLayout displayed = math.displayOperator(cp, preferred);
                        TextLayout.Glyph glyph = onlyGlyph(displayed);
                        require(glyph.glyphId == expected.glyphId, "大算子不是所需真实完整variant");
                        near(
                                glyph.xAdvance,
                                HarfBuzz.hb_font_get_glyph_h_advance(raw.font, glyph.glyphId)
                                        * raw.scale,
                                "大算子横advance不是raw HB值");
                        require(
                                glyph.size == em
                                        && glyph.logicalEm == em
                                        && glyph.rasterToLayout == 1,
                                "大算子被强行缩放");
                        GlyphKey key = GlyphKey.of(glyph);
                        require(
                                key.source == glyph.source && key.glyphId == expected.glyphId,
                                "大算子绘制身份未沿真实source/glyph");
                        TextLayout.Line line = displayed.lines().get(0);
                        near(line.ascent, Math.max(0, -glyph.inkBounds.top), "大算子墨迹上extent错误");
                        near(line.descent, Math.max(0, glyph.inkBounds.bottom), "大算子墨迹下extent错误");
                        if (cp != 0x222b)
                            expect(
                                    UnsupportedOperationException.class,
                                    () -> math.stretch(cp, preferred));
                        else
                            require(
                                    onlyGlyph(math.stretch(cp, preferred)).glyphId
                                            == expected.glyphId,
                                    "积分严格伸缩选择与真实满足变体不一致");
                        require(
                                onlyGlyph(math.displayOperator(cp, 0)).glyphId
                                        == variants.get(0).glyphId,
                                "小建议高度没有选择最小真实variant");
                        System.out.println(
                                "display operator U+"
                                        + Integer.toHexString(cp)
                                        + " em="
                                        + em
                                        + " preferred="
                                        + preferred
                                        + " maximum="
                                        + largest.verticalAdvance
                                        + " glyph="
                                        + glyph.glyphId
                                        + " horizontal="
                                        + glyph.xAdvance);
                    }
                    int base = FreeType.FT_Get_Char_Index(raw.face, 'x');
                    require(raw.variants(base).isEmpty(), "base字形反例意外含variants");
                    require(
                            onlyGlyph(math.displayOperator('x', preferred)).glyphId == base,
                            "无variant时没有使用合法base");
                    expect(
                            UnsupportedOperationException.class,
                            () -> math.stretch('x', preferred * 10));
                    expect(
                            UnsupportedOperationException.class,
                            () -> math.displayOperator(0x10ffff, preferred));
                    expect(
                            IllegalArgumentException.class,
                            () -> math.displayOperator(0x2211, Float.NaN));
                }
            }
        }
    }

    private void verifyBoundaries() throws Exception {
        try (FontTextService service = new FontTextService(new LwjglFontEngine())) {
            List<FontSource> defaults = service.defaultCandidates();
            for (FontSource source : defaults)
                require(!source.id.equals("stix-two-math"), "数学字体污染默认链");
            TextStyle requested = style(24);
            MathFont math = service.mathFont(requested);
            require(math != null, "具名math不可用");
            TextLayout ordinary = service.layout("123", requested, 1000);
            require(!onlyGlyphSource(ordinary).id.equals("stix-two-math"), "普通文本被数学默认覆盖");
            TextLayout text = math.text("x+123");
            require(onlyGlyphSource(text).id.equals("stix-two-math"), "数学文字未使用MATH source");
            expect(
                    UnsupportedOperationException.class,
                    () -> math.text(new String(Character.toChars(0x10ffff))));
            expect(UnsupportedOperationException.class, () -> math.stretch(0x10ffff, 20));
            expect(IllegalArgumentException.class, () -> math.stretch(0x222b, Float.NaN));
            TextStyle explicitNormal =
                    new TextStyle("default", 24, -1, Collections.singletonList(defaults.get(0)));
            require(service.mathFont(explicitNormal) == null, "显式普通链被偷偷追加math");
            require(
                    service.mathFont(
                                    new TextStyle(
                                            "default", 24, -1, Collections.<FontSource>emptyList()))
                            == null,
                    "显式空链被偷偷追加math");
            TextStyle explicitMath =
                    new TextStyle(
                            "default", 24, -1, Collections.singletonList(onlyGlyphSource(text)));
            require(service.mathFont(explicitMath) != null, "显式MATH链未生效");
            service.register("math", Collections.singletonList(defaults.get(0)));
            require(service.mathFont(requested) == null, "无MATH具名链被普通字体冒充");
            expect(
                    UnsupportedOperationException.class,
                    () -> math.constant(MathFont.Constant.AXIS_HEIGHT));
            service.register("math", Collections.<FontSource>emptyList());
            require(service.mathFont(requested) == null, "空math具名链被默认链冒充");
            expect(UnsupportedOperationException.class, () -> math.text("x"));
        }
    }

    private void verifyResourceWarnings() throws Exception {
        try (FontTextService service = new FontTextService(new LwjglFontEngine())) {
            FontSource validMath = onlyGlyphSource(service.mathFont(style(24)).text("x"));
            String absent = "assets/qz_uilib/fonts/math/absent-review-font.otf";
            FontSource missing =
                    FontSource.fromResource("math-resource-review-missing", 1, absent, false);
            // 中间可加载但无 MATH 的字体只是能力不匹配，不应伪造资源 warning。
            service.register(
                    "math", Arrays.asList(missing, service.defaultCandidates().get(0), validMath));
            MathFont math = service.mathFont(style(24));
            require(math != null, "失败候选阻断后继有效数学字体");
            for (int attempt = 0; attempt < 2; attempt++) {
                TextLayout text = math.text("x");
                require(onlyGlyphSource(text).equals(validMath), "后继真实数学文字未生成");
                requireResourceWarning(text, missing.id, absent);
            }
            TextLayout stretched = math.stretch(0x222b, 40);
            require(onlyGlyph(stretched).glyphId == 1699, "warning阻断真实积分variant");
            requireResourceWarning(stretched, missing.id, absent);
            requireResourceWarning(
                    math.displayOperator(
                            0x2211, math.constant(MathFont.Constant.DISPLAY_OPERATOR_MIN_HEIGHT)),
                    missing.id,
                    absent);
            MathFont explicit =
                    service.mathFont(
                            new TextStyle("default", 24, -1, Arrays.asList(missing, validMath)));
            require(explicit != null, "显式链资源失败阻断后继数学候选");
            requireResourceWarning(explicit.text("x"), missing.id, absent);
            require(
                    service.mathFont(
                                    new TextStyle(
                                            "default", 24, -1, Collections.singletonList(missing)))
                            == null,
                    "失败显式链被偷偷扩展");
            service.register("math", Collections.singletonList(missing));
            require(service.mathFont(style(24)) == null, "失败具名链被偷偷扩展");
        }
    }

    private static void requireResourceWarning(TextLayout layout, String candidateId, String path) {
        int warnings = 0;
        for (TextLayout.Diagnostic diagnostic : layout.diagnostics()) {
            if (diagnostic.kind == TextLayout.Diagnostic.Kind.FONT_RESOURCE) {
                warnings++;
                require(
                        diagnostic.message.contains(candidateId)
                                && diagnostic.message.contains(path)
                                && diagnostic.message.contains("字体资源不存在"),
                        "资源warning丢失原候选身份或原因");
                System.out.println("MATH resource warning=" + diagnostic.message);
            }
        }
        require(warnings == 1, "资源尝试诊断丢失、重复或误报无MATH字体: " + warnings);
    }

    private void verifyLifetime() throws Exception {
        RecordingEngine engine = new RecordingEngine();
        try (FontTextService service =
                new FontTextService(Collections.<FontSource>emptyList(), engine, 1, 8, 256)) {
            MathFont math = service.mathFont(style(32));
            require(math != null, "小LRU数学字体不可用");
            FontFace originalFace = engine.opened.get(0);
            TextLayout original = math.stretch(0x222b, 40);
            GlyphKey oldKey = GlyphKey.of(onlyGlyph(original));
            TextLayout originalAssembly = math.stretch(0x222b, 512);
            GlyphKey oldPartKey = GlyphKey.of(originalAssembly.lines().get(0).glyphs().get(0));
            int opened = engine.opened.size();
            MathFont otherSize = service.mathFont(style(21));
            require(otherSize != null && service.cachedFaceCount() == 1, "没有触发真实face淘汰");
            expect(IllegalStateException.class, originalFace::hasMathData);
            float axis = math.constant(MathFont.Constant.AXIS_HEIGHT);
            require(axis > 0 && engine.opened.size() > opened + 1, "端口持有被关闭face或未重开");
            near(
                    math.italicCorrection(original),
                    math.italicCorrection(math.stretch(0x222b, 40)),
                    "LRU后真实修正不一致");
            near(
                    math.italicCorrection(originalAssembly),
                    math.italicCorrection(math.stretch(0x222b, 512)),
                    "LRU后assembly修正丢失");
            long before = service.generation();
            service.register(
                    "math",
                    Collections.singletonList(
                            FontSource.fromResource("stix-two-math", 2, RESOURCE, false)));
            TextLayout replaced = math.text("x");
            require(
                    replaced.generation > before && onlyGlyphSource(replaced).revision == 2,
                    "旧MathFont没有重新解析具名source代际");
            TextLayout stretched = math.stretch(0x222b, 40);
            require(!oldKey.equals(GlyphKey.of(onlyGlyph(stretched))), "新旧source污染glyph key");
            expect(IllegalArgumentException.class, () -> math.italicCorrection(original));
            expect(IllegalArgumentException.class, () -> math.italicCorrection(originalAssembly));
            TextLayout replacedAssembly = math.stretch(0x222b, 512);
            require(
                    !oldPartKey.equals(
                            GlyphKey.of(replacedAssembly.lines().get(0).glyphs().get(0))),
                    "assembly新旧source混入同glyph身份");
            // 旧布局source仍是不可变字节；换代不能让既有绘制事实失去光栅资格。
            try (FontSession session = new LwjglFontEngine().openSession();
                    FontFace face = oldKey.source.openFace(session, oldKey.size)) {
                require(face.rasterize(oldKey.glyphId).width > 0, "旧source换代后不可重开");
                List<MathData.Variant> copy = face.mathVariants(face.glyphIndex(0x222b));
                MathData.Assembly assemblyCopy = face.mathAssembly(face.glyphIndex('('));
                face.close();
                require(copy.size() > 1 && copy.get(0).glyphId > 0, "variant副本依赖native生命周期");
                require(
                        assemblyCopy != null
                                && assemblyCopy.parts().get(0).glyphId > 0
                                && MathAssembly.build(assemblyCopy, 512).verticalAdvance >= 512,
                        "assembly快照依赖native生命周期");
                expect(IllegalStateException.class, () -> face.mathAssembly(oldKey.glyphId));
                expect(IllegalStateException.class, face::hasMathData);
                expect(IllegalStateException.class, () -> face.mathConstant(5));
                expect(
                        IllegalStateException.class,
                        () -> face.glyphHorizontalAdvance(oldKey.glyphId));
            }
            try (FontSession session = new LwjglFontEngine().openSession();
                    FontFace face = oldPartKey.source.openFace(session, oldPartKey.size)) {
                require(face.rasterize(oldPartKey.glyphId).width > 0, "换代后旧部件source不可光栅");
            }
            FontFace beforeReload = engine.opened.get(engine.opened.size() - 1);
            long registered = service.generation();
            service.reload(service.defaultCandidates());
            expect(IllegalStateException.class, beforeReload::hasMathData);
            TextLayout reloaded = math.text("x");
            require(
                    reloaded.generation > registered && onlyGlyphSource(reloaded).revision == 2,
                    "reload没有清理face/布局，或覆盖独立math链");
            TextLayout afterReload = math.stretch(0x222b, 512);
            require(
                    afterReload.generation == service.generation()
                            && onlyGlyphSource(afterReload).revision == 2,
                    "旧wrapper重载后assembly代际错误");
            service.close();
            expect(IllegalStateException.class, () -> math.constant(MathFont.Constant.AXIS_HEIGHT));
            System.out.println(
                    "LRU真实open="
                            + engine.opened.size()
                            + " oldSource="
                            + oldKey.source
                            + " newSource="
                            + onlyGlyphSource(reloaded)
                            + " generation="
                            + reloaded.generation);
        }
    }

    private static TextStyle style(float em) {
        return new TextStyle("default", em, -1, null, TextEffects.NORMAL, TextDecoration.NONE);
    }

    private static TextLayout.Glyph onlyGlyph(TextLayout layout) {
        require(
                layout.lines().size() == 1 && layout.lines().get(0).glyphs().size() == 1,
                "variant不是单一真实glyph");
        return layout.lines().get(0).glyphs().get(0);
    }

    private static FontSource onlyGlyphSource(TextLayout layout) {
        FontSource source = null;
        for (TextLayout.Line line : layout.lines())
            for (TextLayout.Glyph glyph : line.glyphs()) {
                if (source == null) source = glyph.source;
                require(source.equals(glyph.source) && !glyph.replacement, "出现额外source或缺字替代");
            }
        require(source != null, "未生成真实字形");
        return source;
    }

    private static void near(float actual, float expected, String message) {
        require(
                Float.isFinite(actual) && Math.abs(actual - expected) <= 0.0001f,
                message + ": actual=" + actual + " expected=" + expected);
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            require(type.isInstance(error), "错误类型不符: " + error);
            return;
        }
        throw new AssertionError("未明确报告 " + type.getSimpleName());
    }

    private static byte[] resource(String path) throws Exception {
        try (InputStream in = MathFontScenario.class.getClassLoader().getResourceAsStream(path)) {
            require(in != null, "缺少随包资源 " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int n; (n = in.read(chunk)) != -1; ) out.write(chunk, 0, n);
            return out.toByteArray();
        }
    }

    private static int table(byte[] bytes, String tag) {
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int count = data.getShort(4) & 65535;
        for (int i = 0; i < count; i++) {
            int offset = 12 + i * 16;
            if (new String(bytes, offset, 4, StandardCharsets.US_ASCII).equals(tag)) {
                require(data.getInt(offset + 12) > 0, "字体表为空 " + tag);
                return data.getInt(offset + 8);
            }
        }
        return 0;
    }

    /** 原始SFNT设计顺序独立校准HB方向，避免生产和raw探针一起错误反转。 */
    private static int[] assemblyGlyphOrder(byte[] bytes, int glyph) {
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int math = table(bytes, "MATH");
        int variants = math + (data.getShort(math + 8) & 65535);
        int coverage = variants + (data.getShort(variants + 2) & 65535);
        int format = data.getShort(coverage) & 65535;
        int count = data.getShort(coverage + 2) & 65535, index = -1;
        for (int i = 0; i < count; i++) {
            if (format == 1) {
                if ((data.getShort(coverage + 4 + 2 * i) & 65535) == glyph) index = i;
            } else {
                require(format == 2, "原始MATH coverage格式非法");
                int record = coverage + 4 + 6 * i;
                int start = data.getShort(record) & 65535, end = data.getShort(record + 2) & 65535;
                if (glyph >= start && glyph <= end)
                    index = (data.getShort(record + 4) & 65535) + glyph - start;
            }
        }
        require(index >= 0, "原始SFNT没有对应竖向构建");
        int construction = variants + (data.getShort(variants + 10 + 2 * index) & 65535);
        int offset = data.getShort(construction) & 65535;
        require(offset > 0, "原始SFNT没有assembly");
        int assembly = construction + offset;
        int[] glyphs = new int[data.getShort(assembly + 4) & 65535];
        for (int i = 0; i < glyphs.length; i++)
            glyphs[i] = data.getShort(assembly + 6 + 10 * i) & 65535;
        return glyphs;
    }

    /** 仅观测真实native打开和关闭；不模拟face或MATH返回值。 */
    private static final class RecordingEngine implements FontEngine {
        final List<FontFace> opened = new ArrayList<FontFace>();

        public FontSession openSession() {
            final FontSession delegate = new LwjglFontEngine().openSession();
            return new FontSession() {
                public boolean supportsVariations() {
                    return delegate.supportsVariations();
                }

                public FontFace openFace(ByteBuffer source, long index, float em) {
                    return openFace(source, index, em, Collections.<String, Float>emptyMap());
                }

                public FontFace openFace(
                        ByteBuffer source, long index, float em, Map<String, Float> axes) {
                    FontFace face = delegate.openFace(source, index, em, axes);
                    opened.add(face);
                    return face;
                }

                public void close() {
                    delegate.close();
                }
            };
        }
    }

    /** 独立FT/HB原始探针，故意不调用生产mathConstant/variant/advance方法作预期值。 */
    private static final class RawMath implements AutoCloseable {
        private long library, font;
        private FT_Face face;
        private ByteBuffer memory;
        final float scale;

        RawMath(byte[] bytes, float em) {
            long size = Math.max(64L, Math.round(em * 64.0));
            scale = em / (size / 64.0f) / 64.0f;
            try {
                memory = MemoryUtil.memAlloc(bytes.length);
                memory.put(bytes).flip();
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer out = stack.mallocPointer(1);
                    check(FreeType.FT_Init_FreeType(out));
                    library = out.get(0);
                    check(FreeType.FT_New_Memory_Face(library, memory, 0, out));
                    face = FT_Face.create(out.get(0));
                    check(FreeType.FT_Set_Char_Size(face, 0, size, 72, 72));
                    font = HarfBuzz.hb_ft_font_create_referenced(face.address());
                    require(font != 0, "raw HB font创建失败");
                    HarfBuzz.hb_ft_font_set_load_flags(
                            font, FreeType.FT_LOAD_DEFAULT | FreeType.FT_LOAD_COLOR);
                    require(
                            OpenType.hb_ot_math_has_data(HarfBuzz.hb_font_get_face(font)),
                            "raw HB没有MATH数据");
                }
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        MathData.Assembly assembly(int glyph) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer count = stack.ints(0), italic = stack.ints(0);
                int total =
                        OpenType.hb_ot_math_get_glyph_assembly(
                                font, glyph, HarfBuzz.HB_DIRECTION_TTB, 0, count, null, italic);
                if (total == 0) return null;
                List<MathData.Part> result = new ArrayList<MathData.Part>();
                try (hb_ot_math_glyph_part_t.Buffer values =
                        hb_ot_math_glyph_part_t.malloc(total)) {
                    count.put(0, total);
                    OpenType.hb_ot_math_get_glyph_assembly(
                            font, glyph, HarfBuzz.HB_DIRECTION_TTB, 0, count, values, italic);
                    for (int i = 0; i < count.get(0); i++) {
                        hb_ot_math_glyph_part_t part = values.get(i);
                        result.add(
                                new MathData.Part(
                                        part.glyph(),
                                        part.start_connector_length() * scale,
                                        part.end_connector_length() * scale,
                                        part.full_advance() * scale,
                                        (part.flags() & 1) != 0));
                    }
                }
                return new MathData.Assembly(
                        result,
                        OpenType.hb_ot_math_get_min_connector_overlap(
                                        font, HarfBuzz.HB_DIRECTION_TTB)
                                * scale,
                        italic.get(0) * scale);
            }
        }

        List<MathData.Variant> variants(int glyph) {
            List<MathData.Variant> result = new ArrayList<MathData.Variant>();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer count = stack.ints(0);
                int total =
                        OpenType.hb_ot_math_get_glyph_variants(
                                font, glyph, HarfBuzz.HB_DIRECTION_TTB, 0, count, null);
                try (hb_ot_math_glyph_variant_t.Buffer values =
                        hb_ot_math_glyph_variant_t.malloc(total)) {
                    count.put(0, total);
                    OpenType.hb_ot_math_get_glyph_variants(
                            font, glyph, HarfBuzz.HB_DIRECTION_TTB, 0, count, values);
                    for (int i = 0; i < count.get(0); i++)
                        result.add(
                                new MathData.Variant(
                                        values.get(i).glyph(), values.get(i).advance() * scale));
                }
            }
            return result;
        }

        private static void check(int code) {
            require(code == 0, "raw FT error=" + code);
        }

        public void close() {
            if (font != 0) {
                HarfBuzz.hb_font_destroy(font);
                font = 0;
            }
            if (face != null) {
                check(FreeType.FT_Done_Face(face));
                face = null;
            }
            if (library != 0) {
                check(FreeType.FT_Done_FreeType(library));
                library = 0;
            }
            if (memory != null) {
                MemoryUtil.memFree(memory);
                memory = null;
            }
        }
    }
}
