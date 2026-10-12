package club.heiqi.uilib.host.font.freetype;

import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontMetrics;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphRasterStyle;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeRun;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapedGlyph;
import club.heiqi.uilib.ui.scene.text.math.MathAxis;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap_Size;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FT_Size_Metrics;
import org.lwjgl.util.freetype.FreeType;
import org.lwjgl.util.harfbuzz.HarfBuzz;
import org.lwjgl.util.harfbuzz.hb_glyph_info_t;
import org.lwjgl.util.harfbuzz.hb_glyph_position_t;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 持有字体内存的固定字号 face；仅在所属服务的创建线程使用。无平台 GUI 依赖。 */
public final class NativeFontFace implements FontFace {
    private final NativeFontService owner;
    private ByteBuffer bytes;
    private FT_Face face;
    private long faceAddress;
    private long font;
    private final float pixelScale;
    private final FontMetrics metrics;
    private java.util.BitSet topAccentGlyphs;
    private final java.util.Map<Integer, InkBounds> radicalBars =
            new java.util.HashMap<Integer, InkBounds>();

    NativeFontFace(
            NativeFontService owner,
            long library,
            ByteBuffer source,
            long faceIndex,
            float emSize,
            java.util.Map<String, Float> axes) {
        this.owner = owner;
        Objects.requireNonNull(source, "source");
        if (!Float.isFinite(emSize) || emSize <= 0 || emSize > 32767)
            throw new IllegalArgumentException("Invalid emSize");
        if (!source.hasRemaining()) throw new IllegalArgumentException("Empty font");
        bytes = MemoryUtil.memAlloc(source.remaining());
        try {
            bytes.put(source.duplicate()).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer out = stack.mallocPointer(1);
                NativeFontService.check(
                        FreeType.FT_New_Memory_Face(library, bytes, faceIndex, out),
                        "FT_New_Memory_Face");
                faceAddress = out.get(0);
                face = FT_Face.create(faceAddress);
            }
            // FT实例在度量与HB创建前设置；同一个source轴身份也用于异步光栅重开。
            if (!axes.isEmpty())
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer out = stack.mallocPointer(1);
                    NativeFontService.check(FreeType.FT_Get_MM_Var(face, out), "FT_Get_MM_Var");
                    org.lwjgl.util.freetype.FT_MM_Var mm =
                            org.lwjgl.util.freetype.FT_MM_Var.create(out.get(0));
                    try {
                        org.lwjgl.CLongBuffer coordinates = stack.mallocCLong(mm.num_axis());
                        org.lwjgl.util.freetype.FT_Var_Axis.Buffer available = mm.axis();
                        for (int i = 0; i < mm.num_axis(); i++) {
                            org.lwjgl.util.freetype.FT_Var_Axis axis = available.get(i);
                            long tag = axis.tag();
                            String name =
                                    ""
                                            + (char) (tag >> 24 & 255)
                                            + (char) (tag >> 16 & 255)
                                            + (char) (tag >> 8 & 255)
                                            + (char) (tag & 255);
                            Float value = axes.get(name);
                            coordinates.put(
                                    i, value == null ? axis.def() : Math.round(value * 65536.0));
                        }
                        NativeFontService.check(
                                FreeType.FT_Set_Var_Design_Coordinates(face, coordinates),
                                "FT_Set_Var_Design_Coordinates");
                    } finally {
                        NativeFontService.check(
                                FreeType.FT_Done_MM_Var(library, mm), "FT_Done_MM_Var");
                    }
                }
            // 可缩放字体按 em 设置；纯 bitmap 字体选择最接近请求字号的真实 strike。
            if ((face.face_flags() & FreeType.FT_FACE_FLAG_SCALABLE) != 0) {
                // FT将小于1pt的字符尺寸提升到1pt；按真正请求值归一，避免小字号仍显示1px。
                long requestedSize = Math.max(64L, Math.round(emSize * 64.0));
                NativeFontService.check(
                        FreeType.FT_Set_Char_Size(face, 0, requestedSize, 72, 72),
                        "FT_Set_Char_Size");
                pixelScale = emSize / (requestedSize / 64.0f);
            } else {
                FT_Bitmap_Size.Buffer strikes = face.available_sizes();
                if (strikes == null || face.num_fixed_sizes() == 0)
                    throw new IllegalStateException("Font has no usable size");
                int best = 0;
                double distance = Double.POSITIVE_INFINITY;
                for (int i = 0; i < face.num_fixed_sizes(); i++) {
                    double d = Math.abs(strikes.get(i).y_ppem() / 64.0 - emSize);
                    if (d < distance) {
                        distance = d;
                        best = i;
                    }
                }
                NativeFontService.check(FreeType.FT_Select_Size(face, best), "FT_Select_Size");
                float strikeEm = strikes.get(best).y_ppem() / 64.0f;
                if (strikeEm <= 0) throw new IllegalStateException("Invalid bitmap strike em");
                pixelScale = emSize / strikeEm;
            }
            FT_Size_Metrics m = face.size().metrics();
            metrics =
                    new FontMetrics(
                            m.ascender() / 64.0f * pixelScale,
                            -m.descender() / 64.0f * pixelScale,
                            m.height() / 64.0f * pixelScale,
                            emSize);
            font = HarfBuzz.hb_ft_font_create_referenced(face.address());
            if (font == 0 || font == HarfBuzz.hb_font_get_empty())
                throw new IllegalStateException("Cannot create HarfBuzz font");
            HarfBuzz.hb_ft_font_set_load_flags(
                    font, FreeType.FT_LOAD_DEFAULT | FreeType.FT_LOAD_COLOR);
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private void checkOpen() {
        owner.checkOpen();
        if (face == null) throw new IllegalStateException("Native font face is closed");
    }

    public FontMetrics metrics() {
        checkOpen();
        return metrics;
    }

    /** 墨迹的保守像素界，用于需求裁剪；不执行FT_Render_Glyph或生成像素缓存。 */
    public InkBounds glyphInkBounds(int glyphId) {
        return glyphInkBounds(glyphId, GlyphRasterStyle.NORMAL);
    }

    public InkBounds glyphInkBounds(int glyphId, GlyphRasterStyle style) {
        Objects.requireNonNull(style, "style");
        InkBounds raw = rawInkBounds(glyphId);
        if (!raw.known || style.normal() || raw.right <= raw.left || raw.bottom <= raw.top)
            return raw;
        double[] e = style.envelope(raw.left, raw.top, raw.right, raw.bottom, pixelScale);
        float left = (float) (e[0] * pixelScale), top = (float) (e[1] * pixelScale);
        float right = (float) (e[2] * pixelScale), bottom = (float) (e[3] * pixelScale);
        if (!Float.isFinite(left)
                || !Float.isFinite(top)
                || !Float.isFinite(right)
                || !Float.isFinite(bottom)) throw new IllegalArgumentException("字形变体墨迹界溢出");
        return new InkBounds(true, left, top, right, bottom);
    }

    private InkBounds rawInkBounds(int glyphId) {
        checkOpen();
        if (glyphId < 0 || glyphId >= face.num_glyphs())
            throw new IllegalArgumentException("Invalid glyph ID");
        NativeFontService.check(
                FreeType.FT_Load_Glyph(
                        face, glyphId, FreeType.FT_LOAD_DEFAULT | FreeType.FT_LOAD_COLOR),
                "FT_Load_Glyph bounds");
        FT_GlyphSlot slot = face.glyph();
        if (slot.format() == FreeType.FT_GLYPH_FORMAT_BITMAP) {
            return new InkBounds(
                    true,
                    slot.bitmap_left() * pixelScale,
                    -slot.bitmap_top() * pixelScale,
                    (slot.bitmap_left() + slot.bitmap().width()) * pixelScale,
                    (-slot.bitmap_top() + slot.bitmap().rows()) * pixelScale);
        }
        // COLR/SVG等字形的paint可能伸出outline metrics，未验证格式不得用经验界裁掉。
        if (slot.format() != FreeType.FT_GLYPH_FORMAT_OUTLINE
                || (face.face_flags() & FreeType.FT_FACE_FLAG_COLOR) != 0) return InkBounds.UNKNOWN;
        org.lwjgl.util.freetype.FT_Glyph_Metrics m = slot.metrics();
        float left = (float) Math.floor(m.horiBearingX() / 64.0) - 1;
        float top = (float) Math.floor(-m.horiBearingY() / 64.0) - 1;
        float right = (float) Math.ceil((m.horiBearingX() + m.width()) / 64.0) + 1;
        float bottom = (float) Math.ceil((-m.horiBearingY() + m.height()) / 64.0) + 1;
        return new InkBounds(
                true, left * pixelScale, top * pixelScale, right * pixelScale, bottom * pixelScale);
    }

    @Override
    public InkBounds glyphOutlineBounds(int glyphId) {
        checkGlyph(glyphId);
        NativeFontService.check(
                FreeType.FT_Load_Glyph(
                        face, glyphId, FreeType.FT_LOAD_DEFAULT | FreeType.FT_LOAD_COLOR),
                "FT_Load_Glyph outline bounds");
        FT_GlyphSlot slot = face.glyph();
        if (slot.format() != FreeType.FT_GLYPH_FORMAT_OUTLINE
                || (face.face_flags() & FreeType.FT_FACE_FLAG_COLOR) != 0) return InkBounds.UNKNOWN;
        org.lwjgl.util.freetype.FT_Glyph_Metrics m = slot.metrics();
        float scale = pixelScale / 64f;
        return new InkBounds(
                true,
                m.horiBearingX() * scale,
                -m.horiBearingY() * scale,
                (m.horiBearingX() + m.width()) * scale,
                (-m.horiBearingY() + m.height()) * scale);
    }

    public InkBounds glyphRadicalBarBounds(int glyphId) {
        checkOpen();
        checkGlyph(glyphId);
        InkBounds cached = radicalBars.get(glyphId);
        if (cached != null) return cached;
        InkBounds result = radicalBarBounds(glyphId);
        radicalBars.put(glyphId, result);
        return result;
    }

    public float glyphRadicalBarSampleX(int glyphId) {
        InkBounds bar = glyphRadicalBarBounds(glyphId);
        if (!bar.known) return Float.NaN;
        // 像素中心属于当前FT栅格，非整数em还必须映回该face的pixelScale。
        float sample =
                ((float) Math.floor((bar.left + bar.right) / (2 * pixelScale)) + .5f) * pixelScale;
        return sample > bar.left && sample < bar.right ? sample : Float.NaN;
    }

    private InkBounds radicalBarBounds(int glyphId) {
        if (!hasMathData() || !FreeType.FT_IS_SCALABLE(face)) return InkBounds.UNKNOWN;
        float rule = mathConstant(51);
        NativeFontService.check(
                FreeType.FT_Load_Glyph(face, glyphId, FreeType.FT_LOAD_DEFAULT), "FT_Load_Glyph");
        org.lwjgl.util.freetype.FT_Outline outline = face.glyph().outline();
        if (outline.n_points() == 0) return InkBounds.UNKNOWN;
        org.lwjgl.util.freetype.FT_Vector.Buffer points = outline.points();
        ByteBuffer tags = outline.tags();
        java.nio.ShortBuffer contours = outline.contours();
        float unit = pixelScale / 64f;
        float top = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < outline.n_points(); i++) top = Math.min(top, -points.get(i).y() * unit);
        // slot metrics 是像素包络，不能用其 top+rule 截纹理；从真实 hinted 水平轮廓边找横线。
        int start = 0;
        for (int contour = 0; contour < outline.n_contours(); contour++) {
            int end = contours.get(contour);
            for (int i = start; i <= end; i++) {
                int j = i == end ? start : i + 1;
                if ((tags.get(i) & 3) != 1 || (tags.get(j) & 3) != 1) continue;
                if (-points.get(i).y() * unit == top && -points.get(j).y() * unit == top)
                    right = Math.max(right, Math.max(points.get(i).x(), points.get(j).x()) * unit);
            }
            start = end + 1;
        }
        if (!Float.isFinite(right)) return InkBounds.UNKNOWN;
        start = 0;
        for (int contour = 0; contour < outline.n_contours(); contour++) {
            int end = contours.get(contour);
            for (int i = start; i <= end; i++) {
                int j = i == end ? start : i + 1;
                if ((tags.get(i) & 3) != 1 || (tags.get(j) & 3) != 1) continue;
                float y = -points.get(i).y() * unit;
                float x1 = points.get(i).x() * unit, x2 = points.get(j).x() * unit;
                // MATH 常量与 FT hint 后边有最多两个 26.6 单位的量化差，不能假定字体像素位置。
                if (y != -points.get(j).y() * unit
                        || Math.abs(y - top - rule) > 2 * unit
                        || Math.max(x1, x2) != right) continue;
                float left = Math.min(x1, x2);
                if (!(left < right)) continue;
                boolean pure = true;
                for (int k = 0; k < outline.n_points(); k++)
                    if (points.get(k).x() * unit > left && -points.get(k).y() * unit > y)
                        pure = false;
                if (pure) return new InkBounds(true, left, top, right, y);
            }
            start = end + 1;
        }
        return InkBounds.UNKNOWN;
    }

    public int glyphIndex(int codePoint) {
        checkOpen();
        if (!Character.isValidCodePoint(codePoint))
            throw new IllegalArgumentException("Invalid code point");
        return FreeType.FT_Get_Char_Index(face, codePoint);
    }

    public ShapeRun shape(String text, ShapeDirection direction, String language) {
        return shape(text, 0, text.length(), direction, language, null, true, true);
    }

    /**
     * 只 shape [start,end)，完整 text 为前后文。边界及 cluster 为原串 UTF-16 索引，不能切开代理对。 script 为可空 ISO 15924
     * 标记；空时 HB 猜测。beginning/endOfText 指真正文本边界，非普通 run 边界。 输出保持 HB 顺序和向上为正的 Y；所有 advance/offset 均为请求
     * emSize 下的浮点单位。
     */
    public ShapeRun shape(
            String text,
            int start,
            int end,
            ShapeDirection direction,
            String language,
            String script,
            boolean beginningOfText,
            boolean endOfText) {
        checkOpen();
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(direction, "direction");
        if (start < 0
                || end < start
                || end > text.length()
                || splitSurrogate(text, start)
                || splitSurrogate(text, end)) {
            throw new IllegalArgumentException("Invalid UTF-16 run range");
        }
        long buffer = HarfBuzz.hb_buffer_create();
        if (buffer == 0) throw new IllegalStateException("Cannot create HarfBuzz buffer");
        try {
            // CharSequence绑定把完整上下文放入64KiB MemoryStack；长段落即使只shape短run也会溢出。
            // 使用独立native缓冲；HB在add时复制文字/上下文，不保留此地址。
            ByteBuffer utf16 = MemoryUtil.memUTF16(text, false);
            try {
                HarfBuzz.hb_buffer_add_utf16(buffer, utf16, start, end - start);
            } finally {
                MemoryUtil.memFree(utf16);
            }
            HarfBuzz.hb_buffer_set_direction(
                    buffer,
                    direction == ShapeDirection.LTR
                            ? HarfBuzz.HB_DIRECTION_LTR
                            : HarfBuzz.HB_DIRECTION_RTL);
            if (language != null)
                HarfBuzz.hb_buffer_set_language(buffer, HarfBuzz.hb_language_from_string(language));
            if (script != null)
                HarfBuzz.hb_buffer_set_script(buffer, HarfBuzz.hb_script_from_string(script));
            HarfBuzz.hb_buffer_set_flags(
                    buffer,
                    (beginningOfText ? HarfBuzz.HB_BUFFER_FLAG_BOT : 0)
                            | (endOfText ? HarfBuzz.HB_BUFFER_FLAG_EOT : 0));
            HarfBuzz.hb_buffer_guess_segment_properties(buffer);
            HarfBuzz.hb_shape(font, buffer, null);
            if (!HarfBuzz.hb_buffer_allocation_successful(buffer))
                throw new OutOfMemoryError("HarfBuzz buffer allocation");
            int count = HarfBuzz.hb_buffer_get_length(buffer);
            if (count == 0) return ShapeRun.EMPTY;
            hb_glyph_info_t.Buffer infos = HarfBuzz.hb_buffer_get_glyph_infos(buffer);
            hb_glyph_position_t.Buffer positions = HarfBuzz.hb_buffer_get_glyph_positions(buffer);
            List<ShapedGlyph> result = new ArrayList<ShapedGlyph>(count);
            float scale = pixelScale / 64.0f;
            for (int i = 0; i < count; i++) {
                hb_glyph_info_t info = infos.get(i);
                hb_glyph_position_t p = positions.get(i);
                result.add(
                        new ShapedGlyph(
                                info.codepoint(),
                                info.cluster(),
                                p.x_advance() * scale,
                                p.y_advance() * scale,
                                p.x_offset() * scale,
                                p.y_offset() * scale));
            }
            return new ShapeRun(result);
        } finally {
            HarfBuzz.hb_buffer_destroy(buffer);
        }
    }

    public boolean hasMathData() {
        checkOpen();
        return org.lwjgl.util.harfbuzz.OpenType.hb_ot_math_has_data(
                HarfBuzz.hb_font_get_face(font));
    }

    private void checkMath() {
        if (!hasMathData()) throw new UnsupportedOperationException("字体没有 OpenType MATH 表");
    }

    public float mathConstant(int constant) {
        checkMath();
        if (constant < 0 || constant > 55) throw new IllegalArgumentException("无效 MATH 常量编号");
        int value = org.lwjgl.util.harfbuzz.OpenType.hb_ot_math_get_constant(font, constant);
        // 三个百分比是无量纲整数，HB 不按 font scale 缩放，不能按 26.6 位置量除64。
        return constant == 0 || constant == 1 || constant == 55
                ? value
                : value * (pixelScale / 64.0f);
    }

    public List<club.heiqi.uilib.ui.scene.text.font.nativefont.MathData.Variant> mathVariants(
            int glyphId) {
        return mathVariants(glyphId, MathAxis.VERTICAL);
    }

    public List<club.heiqi.uilib.ui.scene.text.font.nativefont.MathData.Variant> mathVariants(
            int glyphId, MathAxis axis) {
        checkMath();
        checkGlyph(glyphId);
        int direction = mathDirection(axis);
        List<club.heiqi.uilib.ui.scene.text.font.nativefont.MathData.Variant> result =
                new ArrayList<club.heiqi.uilib.ui.scene.text.font.nativefont.MathData.Variant>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer count = stack.ints(0);
            int total =
                    org.lwjgl.util.harfbuzz.OpenType.hb_ot_math_get_glyph_variants(
                            font, glyphId, direction, 0, count, null);
            if (total == 0) return Collections.emptyList();
            // 数据先复制到 Java 值；返回结果不持有 stack 或被 LRU 关闭的 face。
            try (org.lwjgl.util.harfbuzz.hb_ot_math_glyph_variant_t.Buffer variants =
                    org.lwjgl.util.harfbuzz.hb_ot_math_glyph_variant_t.malloc(total)) {
                count.put(0, total);
                org.lwjgl.util.harfbuzz.OpenType.hb_ot_math_get_glyph_variants(
                        font, glyphId, direction, 0, count, variants);
                for (int i = 0; i < count.get(0); i++) {
                    org.lwjgl.util.harfbuzz.hb_ot_math_glyph_variant_t variant = variants.get(i);
                    result.add(
                            new club.heiqi.uilib.ui.scene.text.font.nativefont.MathData.Variant(
                                    variant.glyph(),
                                    variant.advance() * (pixelScale / 64.0f),
                                    axis));
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public club.heiqi.uilib.ui.scene.text.font.nativefont.MathData.Assembly mathAssembly(
            int glyphId) {
        return mathAssembly(glyphId, MathAxis.VERTICAL);
    }

    public club.heiqi.uilib.ui.scene.text.font.nativefont.MathData.Assembly mathAssembly(
            int glyphId, MathAxis axis) {
        checkMath();
        checkGlyph(glyphId);
        int direction = mathDirection(axis);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer count = stack.ints(0), italic = stack.ints(0);
            int total =
                    org.lwjgl.util.harfbuzz.OpenType.hb_ot_math_get_glyph_assembly(
                            font, glyphId, direction, 0, count, null, italic);
            if (total == 0) return null;
            java.util.List<club.heiqi.uilib.ui.scene.text.font.nativefont.MathData.Part> result =
                    new ArrayList<club.heiqi.uilib.ui.scene.text.font.nativefont.MathData.Part>();
            float scale = pixelScale / 64.0f;
            try (org.lwjgl.util.harfbuzz.hb_ot_math_glyph_part_t.Buffer parts =
                    org.lwjgl.util.harfbuzz.hb_ot_math_glyph_part_t.malloc(total)) {
                count.put(0, total);
                org.lwjgl.util.harfbuzz.OpenType.hb_ot_math_get_glyph_assembly(
                        font, glyphId, direction, 0, count, parts, italic);
                // HB 保留 OpenType 记录顺序：横向左到右、竖向底到顶，不因方向反转。
                for (int i = 0; i < count.get(0); i++) {
                    org.lwjgl.util.harfbuzz.hb_ot_math_glyph_part_t part = parts.get(i);
                    result.add(
                            new club.heiqi.uilib.ui.scene.text.font.nativefont.MathData.Part(
                                    part.glyph(),
                                    part.start_connector_length() * scale,
                                    part.end_connector_length() * scale,
                                    part.full_advance() * scale,
                                    (part.flags() & 1) != 0));
                }
            }
            return new club.heiqi.uilib.ui.scene.text.font.nativefont.MathData.Assembly(
                    result,
                    org.lwjgl.util.harfbuzz.OpenType.hb_ot_math_get_min_connector_overlap(
                                    font, direction)
                            * scale,
                    italic.get(0) * scale,
                    axis);
        }
    }

    private static int mathDirection(MathAxis axis) {
        return Objects.requireNonNull(axis, "axis") == MathAxis.HORIZONTAL
                ? HarfBuzz.HB_DIRECTION_LTR
                : HarfBuzz.HB_DIRECTION_TTB;
    }

    public float mathTopAccentAttachment(int glyphId) {
        checkGlyph(glyphId);
        if (!hasMathData()) return Float.NaN;
        if (topAccentGlyphs == null) topAccentGlyphs = readTopAccentCoverage();
        // HB 对未覆盖字形会返回半个横向 advance；它是默认策略，不是真实附件点记录。
        if (!topAccentGlyphs.get(glyphId)) return Float.NaN;
        return org.lwjgl.util.harfbuzz.OpenType.hb_ot_math_get_glyph_top_accent_attachment(
                        font, glyphId)
                * (pixelScale / 64.0f);
    }

    /** 只复制 coverage 存在性；值及 device/variation 调整仍由同一 HB font 读取。 */
    private java.util.BitSet readTopAccentCoverage() {
        java.util.BitSet covered = new java.util.BitSet();
        long blob =
                HarfBuzz.hb_face_reference_table(
                        HarfBuzz.hb_font_get_face(font), HarfBuzz.hb_tag_from_string("MATH"));
        try {
            ByteBuffer data = HarfBuzz.hb_blob_get_data(blob);
            if (data == null || data.remaining() < 10) return covered;
            data = data.duplicate().order(java.nio.ByteOrder.BIG_ENDIAN);
            int info = data.getShort(6) & 65535;
            if (info == 0) return covered;
            int attachment = data.getShort(info + 2) & 65535;
            if (attachment == 0) return covered;
            attachment += info;
            int coverageOffset = data.getShort(attachment) & 65535;
            if (coverageOffset == 0) return covered;
            int valueCount = data.getShort(attachment + 2) & 65535;
            // 核查整个 MathValueRecord 数组，不能把只有 coverage 的损坏表视为有效数据。
            if ((long) attachment + 4 + (long) valueCount * 4 > data.limit())
                throw new UnsupportedOperationException("MATH top-accent 记录越界");
            int coverage = attachment + coverageOffset;
            int format = data.getShort(coverage) & 65535;
            int count = data.getShort(coverage + 2) & 65535;
            for (int i = 0; i < count; i++) {
                if (format == 1) {
                    int glyph = data.getShort(coverage + 4 + i * 2) & 65535;
                    if (i < valueCount) covered.set(glyph);
                } else if (format == 2) {
                    int record = coverage + 4 + i * 6;
                    int start = data.getShort(record) & 65535;
                    int end = data.getShort(record + 2) & 65535;
                    int first = data.getShort(record + 4) & 65535;
                    for (int glyph = start;
                            glyph <= end && first + glyph - start < valueCount;
                            glyph++) covered.set(glyph);
                } else throw new UnsupportedOperationException("MATH top-accent coverage 格式非法");
            }
            return covered;
        } catch (IndexOutOfBoundsException error) {
            throw new UnsupportedOperationException("MATH top-accent coverage 越界", error);
        } finally {
            HarfBuzz.hb_blob_destroy(blob);
        }
    }

    private void checkGlyph(int glyphId) {
        checkOpen();
        if (glyphId <= 0 || glyphId >= face.num_glyphs())
            throw new IllegalArgumentException("无效数学 glyph ID");
    }

    public float mathItalicCorrection(int glyphId) {
        checkMath();
        checkGlyph(glyphId);
        return org.lwjgl.util.harfbuzz.OpenType.hb_ot_math_get_glyph_italics_correction(
                        font, glyphId)
                * (pixelScale / 64.0f);
    }

    public float glyphHorizontalAdvance(int glyphId) {
        checkGlyph(glyphId);
        return HarfBuzz.hb_font_get_glyph_h_advance(font, glyphId) * (pixelScale / 64.0f);
    }

    private static boolean splitSurrogate(String text, int index) {
        return index > 0
                && index < text.length()
                && Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index));
    }

    /** 灰度/mono 输出白色 premultiplied coverage；BGRA 保留原生 premultiplied 颜色并重排为 RGBA。 */
    public GlyphBitmap rasterize(int glyphId) {
        return rasterize(glyphId, GlyphRasterStyle.NORMAL);
    }

    public GlyphBitmap rasterize(int glyphId, GlyphRasterStyle style) {
        Objects.requireNonNull(style, "style");
        checkOpen();
        NativeFontService.check(
                FreeType.FT_Load_Glyph(
                        face, glyphId, FreeType.FT_LOAD_DEFAULT | FreeType.FT_LOAD_COLOR),
                "FT_Load_Glyph");
        FT_GlyphSlot slot = face.glyph();
        if (slot.format() != FreeType.FT_GLYPH_FORMAT_BITMAP) {
            NativeFontService.check(
                    FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_NORMAL),
                    "FT_Render_Glyph");
        }
        return FreeTypeBitmap.copy(
                        slot.bitmap(),
                        slot.bitmap_left() * pixelScale,
                        slot.bitmap_top() * pixelScale,
                        pixelScale)
                .styled(style);
    }

    @Override
    public void close() {
        owner.checkThread();
        try {
            try {
                if (font != 0) {
                    long owned = font;
                    font = 0;
                    HarfBuzz.hb_font_destroy(owned);
                }
            } finally {
                if (faceAddress != 0) {
                    long owned = faceAddress;
                    faceAddress = 0;
                    face = null;
                    NativeFontService.check(FreeType.nFT_Done_Face(owned), "FT_Done_Face");
                }
            }
        } finally {
            if (bytes != null) {
                ByteBuffer owned = bytes;
                bytes = null;
                MemoryUtil.memFree(owned);
            }
            owner.forget(this);
        }
    }
}
