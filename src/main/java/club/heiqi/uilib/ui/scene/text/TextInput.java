package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 原输入范围保留在解码边界；显示替代不会写回业务文本。UTF8按maximal subpart推进。 */
public final class TextInput {
    public static final class InvalidRange {
        public final int start, end;
        InvalidRange(int start, int end) { this.start = start; this.end = end; }
    }
    public final String text;
    private final int[] sourceOffsets;
    private final List<InvalidRange> invalid;
    private TextInput(String text, int[] sourceOffsets, List<InvalidRange> invalid) {
        this.text = text; this.sourceOffsets = sourceOffsets;
        this.invalid = Collections.unmodifiableList(new ArrayList<>(invalid));
    }
    public int sourceOffset(int utf16Offset) { return sourceOffsets[utf16Offset]; }
    public List<InvalidRange> invalidRanges() { return invalid; }
    public static TextInput utf8(byte[] source) {
        Objects.requireNonNull(source, "source");
        StringBuilder text = new StringBuilder(); List<Integer> offsets = new ArrayList<>();
        List<InvalidRange> invalid = new ArrayList<>(); int p = 0;
        while (p < source.length) {
            int start = p, lead = source[p] & 255, count = lead < 128 ? 1
                : lead >= 0xc2 && lead <= 0xdf ? 2 : lead >= 0xe0 && lead <= 0xef ? 3
                : lead >= 0xf0 && lead <= 0xf4 ? 4 : 0;
            if (count == 0) { append(text, offsets, 0xfffd, start); invalid.add(new InvalidRange(p, ++p)); continue; }
            if (count == 1) { append(text, offsets, lead, start); p++; continue; }
            int cp = lead & (count == 2 ? 31 : count == 3 ? 15 : 7), consumed = 1;
            while (consumed < count && p + consumed < source.length) {
                int next = source[p + consumed] & 255;
                if (next < 0x80 || next > 0xbf || consumed == 1 &&
                    (lead == 0xe0 && next < 0xa0 || lead == 0xed && next >= 0xa0
                    || lead == 0xf0 && next < 0x90 || lead == 0xf4 && next >= 0x90)) break;
                cp = (cp << 6) | (next & 63); consumed++;
            }
            p += consumed;
            if (consumed != count) { append(text, offsets, 0xfffd, start); invalid.add(new InvalidRange(start, p)); }
            else append(text, offsets, cp, start);
        }
        offsets.add(source.length); return result(text, offsets, invalid);
    }
    public static TextInput string(String source) {
        Objects.requireNonNull(source, "source");
        StringBuilder text = new StringBuilder(); List<Integer> offsets = new ArrayList<>();
        List<InvalidRange> invalid = new ArrayList<>();
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i); int start = i;
            if (Character.isHighSurrogate(c) && i + 1 < source.length() && Character.isLowSurrogate(source.charAt(i + 1))) {
                append(text, offsets, Character.toCodePoint(c, source.charAt(i + 1)), start); i += 2;
            } else if (Character.isSurrogate(c)) {
                append(text, offsets, 0xfffd, start); invalid.add(new InvalidRange(i, ++i));
            } else { append(text, offsets, c, start); i++; }
        }
        offsets.add(source.length()); return result(text, offsets, invalid);
    }
    private static void append(StringBuilder text, List<Integer> offsets, int cp, int source) {
        text.appendCodePoint(cp); offsets.add(source); if (cp > 0xffff) offsets.add(source);
    }
    private static TextInput result(StringBuilder text, List<Integer> offsets, List<InvalidRange> invalid) {
        int[] values = new int[offsets.size()]; for (int i = 0; i < values.length; i++) values[i] = offsets.get(i);
        return new TextInput(text.toString(), values, invalid);
    }
}
