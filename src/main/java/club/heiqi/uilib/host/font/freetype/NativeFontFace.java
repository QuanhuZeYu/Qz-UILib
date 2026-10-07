package club.heiqi.uilib.host.font.freetype;

import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontMetrics;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphRasterStyle;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeRun;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapedGlyph;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

/** 持有字体内存的固定字号 face；仅在所属服务的创建线程使用。无平台 GUI 依赖。 */
public final class NativeFontFace implements FontFace {
    private final NativeFontService owner;
    private ByteBuffer bytes;
    private FT_Face face;
    private long faceAddress;
    private long font;
    private final float pixelScale;
    private final FontMetrics metrics;

    NativeFontFace(NativeFontService owner, long library, ByteBuffer source, long faceIndex, float emSize) {
        this.owner = owner;
        Objects.requireNonNull(source, "source");
        if (!Float.isFinite(emSize) || emSize <= 0 || emSize > 32767) throw new IllegalArgumentException("Invalid emSize");
        if (!source.hasRemaining()) throw new IllegalArgumentException("Empty font");
        bytes = MemoryUtil.memAlloc(source.remaining());
        try {
            bytes.put(source.duplicate()).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer out = stack.mallocPointer(1);
                NativeFontService.check(FreeType.FT_New_Memory_Face(library, bytes, faceIndex, out), "FT_New_Memory_Face");
                faceAddress = out.get(0);
                face = FT_Face.create(faceAddress);
            }
            // 可缩放字体按 em 设置；纯 bitmap 字体选择最接近请求字号的真实 strike。
            if ((face.face_flags() & FreeType.FT_FACE_FLAG_SCALABLE) != 0) {
                // FT将小于1pt的字符尺寸提升到1pt；按真正请求值归一，避免小字号仍显示1px。
                long requestedSize = Math.max(64L, Math.round(emSize * 64.0));
                NativeFontService.check(FreeType.FT_Set_Char_Size(face, 0, requestedSize, 72, 72), "FT_Set_Char_Size");
                pixelScale = emSize / (requestedSize / 64.0f);
            } else {
                FT_Bitmap_Size.Buffer strikes = face.available_sizes();
                if (strikes == null || face.num_fixed_sizes() == 0) throw new IllegalStateException("Font has no usable size");
                int best = 0;
                double distance = Double.POSITIVE_INFINITY;
                for (int i = 0; i < face.num_fixed_sizes(); i++) {
                    double d = Math.abs(strikes.get(i).y_ppem() / 64.0 - emSize);
                    if (d < distance) { distance = d; best = i; }
                }
                NativeFontService.check(FreeType.FT_Select_Size(face, best), "FT_Select_Size");
                float strikeEm = strikes.get(best).y_ppem() / 64.0f;
                if (strikeEm <= 0) throw new IllegalStateException("Invalid bitmap strike em");
                pixelScale = emSize / strikeEm;
            }
            FT_Size_Metrics m = face.size().metrics();
            metrics = new FontMetrics(m.ascender() / 64.0f * pixelScale, -m.descender() / 64.0f * pixelScale,
                    m.height() / 64.0f * pixelScale, emSize);
            font = HarfBuzz.hb_ft_font_create_referenced(face.address());
            if (font == 0 || font == HarfBuzz.hb_font_get_empty()) throw new IllegalStateException("Cannot create HarfBuzz font");
            HarfBuzz.hb_ft_font_set_load_flags(font, FreeType.FT_LOAD_DEFAULT | FreeType.FT_LOAD_COLOR);
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void checkOpen() {
        owner.checkOpen();
        if (face == null) throw new IllegalStateException("Native font face is closed");
    }

    public FontMetrics metrics() { checkOpen(); return metrics; }
    /** 墨迹的保守像素界，用于需求裁剪；不执行FT_Render_Glyph或生成像素缓存。 */
    public InkBounds glyphInkBounds(int glyphId) { return glyphInkBounds(glyphId, GlyphRasterStyle.NORMAL); }
    public InkBounds glyphInkBounds(int glyphId, GlyphRasterStyle style) {
        Objects.requireNonNull(style, "style");
        InkBounds raw = rawInkBounds(glyphId);
        if (!raw.known || style.normal() || raw.right <= raw.left || raw.bottom <= raw.top) return raw;
        double[] e = style.envelope(raw.left, raw.top, raw.right, raw.bottom, pixelScale);
        float left = (float) (e[0] * pixelScale), top = (float) (e[1] * pixelScale);
        float right = (float) (e[2] * pixelScale), bottom = (float) (e[3] * pixelScale);
        if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right) || !Float.isFinite(bottom))
            throw new IllegalArgumentException("字形变体墨迹界溢出");
        return new InkBounds(true, left, top, right, bottom);
    }
    private InkBounds rawInkBounds(int glyphId) {
        checkOpen();
        if (glyphId < 0 || glyphId >= face.num_glyphs()) throw new IllegalArgumentException("Invalid glyph ID");
        NativeFontService.check(FreeType.FT_Load_Glyph(face, glyphId, FreeType.FT_LOAD_DEFAULT | FreeType.FT_LOAD_COLOR), "FT_Load_Glyph bounds");
        FT_GlyphSlot slot = face.glyph();
        if (slot.format() == FreeType.FT_GLYPH_FORMAT_BITMAP) {
            return new InkBounds(true, slot.bitmap_left() * pixelScale, -slot.bitmap_top() * pixelScale,
                (slot.bitmap_left() + slot.bitmap().width()) * pixelScale, (-slot.bitmap_top() + slot.bitmap().rows()) * pixelScale);
        }
        // COLR/SVG等字形的paint可能伸出outline metrics，未验证格式不得用经验界裁掉。
        if (slot.format() != FreeType.FT_GLYPH_FORMAT_OUTLINE || (face.face_flags() & FreeType.FT_FACE_FLAG_COLOR) != 0)
            return InkBounds.UNKNOWN;
        org.lwjgl.util.freetype.FT_Glyph_Metrics m = slot.metrics();
        float left = (float) Math.floor(m.horiBearingX() / 64.0) - 1;
        float top = (float) Math.floor(-m.horiBearingY() / 64.0) - 1;
        float right = (float) Math.ceil((m.horiBearingX() + m.width()) / 64.0) + 1;
        float bottom = (float) Math.ceil((-m.horiBearingY() + m.height()) / 64.0) + 1;
        return new InkBounds(true, left * pixelScale, top * pixelScale, right * pixelScale, bottom * pixelScale);
    }
    public int glyphIndex(int codePoint) {
        checkOpen();
        if (!Character.isValidCodePoint(codePoint)) throw new IllegalArgumentException("Invalid code point");
        return FreeType.FT_Get_Char_Index(face, codePoint);
    }

    public ShapeRun shape(String text, ShapeDirection direction, String language) {
        return shape(text, 0, text.length(), direction, language, null, true, true);
    }

    /**
     * 只 shape [start,end)，完整 text 为前后文。边界及 cluster 为原串 UTF-16 索引，不能切开代理对。
     * script 为可空 ISO 15924 标记；空时 HB 猜测。beginning/endOfText 指真正文本边界，非普通 run 边界。
     * 输出保持 HB 顺序和向上为正的 Y；所有 advance/offset 均为请求 emSize 下的浮点单位。
     */
    public ShapeRun shape(String text, int start, int end, ShapeDirection direction, String language,
            String script, boolean beginningOfText, boolean endOfText) {
        checkOpen();
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(direction, "direction");
        if (start < 0 || end < start || end > text.length() || splitSurrogate(text, start) || splitSurrogate(text, end)) {
            throw new IllegalArgumentException("Invalid UTF-16 run range");
        }
        long buffer = HarfBuzz.hb_buffer_create();
        if (buffer == 0) throw new IllegalStateException("Cannot create HarfBuzz buffer");
        try {
            // CharSequence绑定把完整上下文放入64KiB MemoryStack；长段落即使只shape短run也会溢出。
            // 使用独立native缓冲；HB在add时复制文字/上下文，不保留此地址。
            ByteBuffer utf16 = MemoryUtil.memUTF16(text, false);
            try { HarfBuzz.hb_buffer_add_utf16(buffer, utf16, start, end - start); }
            finally { MemoryUtil.memFree(utf16); }
            HarfBuzz.hb_buffer_set_direction(buffer, direction == ShapeDirection.LTR ? HarfBuzz.HB_DIRECTION_LTR : HarfBuzz.HB_DIRECTION_RTL);
            if (language != null) HarfBuzz.hb_buffer_set_language(buffer, HarfBuzz.hb_language_from_string(language));
            if (script != null) HarfBuzz.hb_buffer_set_script(buffer, HarfBuzz.hb_script_from_string(script));
            HarfBuzz.hb_buffer_set_flags(buffer, (beginningOfText ? HarfBuzz.HB_BUFFER_FLAG_BOT : 0)
                    | (endOfText ? HarfBuzz.HB_BUFFER_FLAG_EOT : 0));
            HarfBuzz.hb_buffer_guess_segment_properties(buffer);
            HarfBuzz.hb_shape(font, buffer, null);
            if (!HarfBuzz.hb_buffer_allocation_successful(buffer)) throw new OutOfMemoryError("HarfBuzz buffer allocation");
            int count = HarfBuzz.hb_buffer_get_length(buffer);
            if (count == 0) return ShapeRun.EMPTY;
            hb_glyph_info_t.Buffer infos = HarfBuzz.hb_buffer_get_glyph_infos(buffer);
            hb_glyph_position_t.Buffer positions = HarfBuzz.hb_buffer_get_glyph_positions(buffer);
            List<ShapedGlyph> result = new ArrayList<ShapedGlyph>(count);
            float scale = pixelScale / 64.0f;
            for (int i = 0; i < count; i++) {
                hb_glyph_info_t info = infos.get(i);
                hb_glyph_position_t p = positions.get(i);
                result.add(new ShapedGlyph(info.codepoint(), info.cluster(), p.x_advance() * scale, p.y_advance() * scale,
                        p.x_offset() * scale, p.y_offset() * scale));
            }
            return new ShapeRun(result);
        } finally { HarfBuzz.hb_buffer_destroy(buffer); }
    }

    private static boolean splitSurrogate(String text, int index) {
        return index > 0 && index < text.length() && Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index));
    }

    /** 灰度/mono 输出白色 premultiplied coverage；BGRA 保留原生 premultiplied 颜色并重排为 RGBA。 */
    public GlyphBitmap rasterize(int glyphId) { return rasterize(glyphId, GlyphRasterStyle.NORMAL); }
    public GlyphBitmap rasterize(int glyphId, GlyphRasterStyle style) {
        Objects.requireNonNull(style, "style");
        checkOpen();
        NativeFontService.check(FreeType.FT_Load_Glyph(face, glyphId, FreeType.FT_LOAD_DEFAULT | FreeType.FT_LOAD_COLOR), "FT_Load_Glyph");
        FT_GlyphSlot slot = face.glyph();
        if (slot.format() != FreeType.FT_GLYPH_FORMAT_BITMAP) {
            NativeFontService.check(FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_NORMAL), "FT_Render_Glyph");
        }
        return FreeTypeBitmap.copy(slot.bitmap(), slot.bitmap_left() * pixelScale, slot.bitmap_top() * pixelScale, pixelScale).styled(style);
    }

    @Override public void close() {
        owner.checkThread();
        try {
            try {
                if (font != 0) { long owned = font; font = 0; HarfBuzz.hb_font_destroy(owned); }
            } finally {
                if (faceAddress != 0) {
                    long owned = faceAddress; faceAddress = 0; face = null;
                    NativeFontService.check(FreeType.nFT_Done_Face(owned), "FT_Done_Face");
                }
            }
        } finally {
            if (bytes != null) { ByteBuffer owned = bytes; bytes = null; MemoryUtil.memFree(owned); }
            owner.forget(this);
        }
    }

}
