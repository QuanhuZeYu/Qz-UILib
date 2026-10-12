package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 原输入范围保留在解码边界；显示替代不会写回业务文本。UTF8按maximal subpart推进。 */
public final class TextInput {
    public static final class InvalidRange {
        public final int start, end;

        InvalidRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public final String text;
    private final int[] sourceOffsets;
    private final List<InvalidRange> invalid;

    public enum SourceUnit {
        UTF16,
        BYTE
    }

    public final SourceUnit sourceUnit;
    private final int[] starts, ends;
    private final int sourceLength;
    private final List<SourceSegment> segments;

    public static final class SourceSegment {
        public final int logicalStart, logicalEnd, sourceStart, sourceEnd;
        public final boolean syntax;

        public SourceSegment(
                int logicalStart, int logicalEnd, int sourceStart, int sourceEnd, boolean syntax) {
            if (logicalStart < 0
                    || logicalEnd < logicalStart
                    || sourceStart < 0
                    || sourceEnd < sourceStart) throw new IllegalArgumentException("无效来源片段");
            this.logicalStart = logicalStart;
            this.logicalEnd = logicalEnd;
            this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd;
            this.syntax = syntax;
        }
    }

    private TextInput(
            String text, int[] sourceOffsets, List<InvalidRange> invalid, SourceUnit unit) {
        this.text = text;
        this.sourceOffsets = sourceOffsets;
        this.sourceUnit = unit;
        sourceLength = sourceOffsets[text.length()];
        starts = new int[text.length()];
        ends = new int[text.length()];
        ArrayList<SourceSegment> pieces = new ArrayList<SourceSegment>();
        for (int i = 0; i < text.length(); ) {
            int next = i + Character.charCount(text.codePointAt(i));
            for (int j = i; j < next; j++) {
                starts[j] = sourceOffsets[i];
                ends[j] = sourceOffsets[next];
            }
            pieces.add(new SourceSegment(i, next, sourceOffsets[i], sourceOffsets[next], false));
            i = next;
        }
        segments = Collections.unmodifiableList(pieces);
        this.invalid = Collections.unmodifiableList(new ArrayList<>(invalid));
    }

    private TextInput(
            String text,
            int[] starts,
            int[] ends,
            int sourceLength,
            SourceUnit unit,
            List<InvalidRange> invalid,
            List<SourceSegment> pieces) {
        this.text = text;
        this.starts = starts.clone();
        this.ends = ends.clone();
        this.sourceLength = sourceLength;
        sourceUnit = unit;
        sourceOffsets = new int[text.length() + 1];
        System.arraycopy(starts, 0, sourceOffsets, 0, starts.length);
        sourceOffsets[text.length()] = sourceLength;
        this.invalid = Collections.unmodifiableList(new ArrayList<InvalidRange>(invalid));
        segments = Collections.unmodifiableList(new ArrayList<SourceSegment>(pieces));
    }

    public int sourceStart(int offset) {
        return starts[offset];
    }

    public int sourceEnd(int offset) {
        return ends[offset];
    }

    public int sourceLeft(int boundary) {
        return boundary == 0 ? 0 : ends[boundary - 1];
    }

    public int sourceRight(int boundary) {
        return boundary == text.length() ? sourceLength : starts[boundary];
    }

    public List<SourceSegment> sourceSegments() {
        return segments;
    }

    /** 参数坐标是original解码后的UTF16；组合为原byte/UTF16坐标，完整保留syntax gap。 */
    public static TextInput fromMapped(
            TextInput original,
            String logical,
            int[] decodedStarts,
            int[] decodedEnds,
            List<SourceSegment> syntaxGaps) {
        if (decodedStarts.length != logical.length() || decodedEnds.length != logical.length())
            throw new IllegalArgumentException("来源数组长度错误");
        int[] starts = new int[logical.length()], ends = new int[logical.length()];
        ArrayList<SourceSegment> pieces = new ArrayList<SourceSegment>();
        int previous = 0, previousStart = -1;
        for (int i = 0; i < logical.length(); ) {
            int next = i + Character.charCount(logical.codePointAt(i)),
                    s = decodedStarts[i],
                    e = decodedEnds[i];
            // 一个源实体/Tab可展开多个逻辑字符；仅允许紧邻共享同一完整源范围。
            boolean sharedSource = s == previousStart && e == previous;
            if (s < previous && !sharedSource
                    || e <= s
                    || e > original.text.length()
                    || s > 0 && Character.isLowSurrogate(original.text.charAt(s))
                    || e < original.text.length()
                            && Character.isLowSurrogate(original.text.charAt(e)))
                throw new IllegalArgumentException("来源非单调或切代理对");
            for (int j = i; j < next; j++) {
                if (decodedStarts[j] != s || decodedEnds[j] != e)
                    throw new IllegalArgumentException("代理对必须共享完整来源");
                starts[j] = original.sourceStart(s);
                ends[j] = original.sourceEnd(e - 1);
            }
            if (sharedSource) {
                SourceSegment prior = pieces.remove(pieces.size() - 1);
                pieces.add(
                        new SourceSegment(
                                prior.logicalStart,
                                next,
                                prior.sourceStart,
                                prior.sourceEnd,
                                false));
            } else {
                pieces.add(new SourceSegment(i, next, starts[i], ends[i], false));
            }
            previousStart = s;
            previous = e;
            i = next;
        }
        for (SourceSegment gap : syntaxGaps) {
            if (!gap.syntax
                    || gap.logicalStart != gap.logicalEnd
                    || gap.logicalEnd > logical.length()
                    || gap.sourceEnd > original.text.length())
                throw new IllegalArgumentException("无效syntax gap");
            pieces.add(
                    new SourceSegment(
                            gap.logicalStart,
                            gap.logicalEnd,
                            original.sourceRight(gap.sourceStart),
                            original.sourceLeft(gap.sourceEnd),
                            true));
        }
        Collections.sort(pieces, (a, b) -> Integer.compare(a.sourceStart, b.sourceStart));
        int consumed = 0;
        for (SourceSegment s : pieces) {
            if (s.sourceStart != consumed) throw new IllegalArgumentException("来源片段需完整且不重叠");
            consumed = s.sourceEnd;
        }
        if (consumed != original.sourceLength) throw new IllegalArgumentException("来源片段未完整覆盖输入");
        return new TextInput(
                logical,
                starts,
                ends,
                original.sourceLength,
                original.sourceUnit,
                original.invalid,
                pieces);
    }

    public int sourceOffset(int utf16Offset) {
        return sourceOffsets[utf16Offset];
    }

    public List<InvalidRange> invalidRanges() {
        return invalid;
    }

    public static TextInput utf8(byte[] source) {
        Objects.requireNonNull(source, "source");
        StringBuilder text = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        List<InvalidRange> invalid = new ArrayList<>();
        int p = 0;
        while (p < source.length) {
            int start = p,
                    lead = source[p] & 255,
                    count =
                            lead < 128
                                    ? 1
                                    : lead >= 0xc2 && lead <= 0xdf
                                            ? 2
                                            : lead >= 0xe0 && lead <= 0xef
                                                    ? 3
                                                    : lead >= 0xf0 && lead <= 0xf4 ? 4 : 0;
            if (count == 0) {
                append(text, offsets, 0xfffd, start);
                invalid.add(new InvalidRange(p, ++p));
                continue;
            }
            if (count == 1) {
                append(text, offsets, lead, start);
                p++;
                continue;
            }
            int cp = lead & (count == 2 ? 31 : count == 3 ? 15 : 7), consumed = 1;
            while (consumed < count && p + consumed < source.length) {
                int next = source[p + consumed] & 255;
                if (next < 0x80
                        || next > 0xbf
                        || consumed == 1
                                && (lead == 0xe0 && next < 0xa0
                                        || lead == 0xed && next >= 0xa0
                                        || lead == 0xf0 && next < 0x90
                                        || lead == 0xf4 && next >= 0x90)) break;
                cp = (cp << 6) | (next & 63);
                consumed++;
            }
            p += consumed;
            if (consumed != count) {
                append(text, offsets, 0xfffd, start);
                invalid.add(new InvalidRange(start, p));
            } else append(text, offsets, cp, start);
        }
        offsets.add(source.length);
        return result(text, offsets, invalid, SourceUnit.BYTE);
    }

    public static TextInput string(String source) {
        Objects.requireNonNull(source, "source");
        StringBuilder text = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        List<InvalidRange> invalid = new ArrayList<>();
        for (int i = 0; i < source.length(); ) {
            char c = source.charAt(i);
            int start = i;
            if (Character.isHighSurrogate(c)
                    && i + 1 < source.length()
                    && Character.isLowSurrogate(source.charAt(i + 1))) {
                append(text, offsets, Character.toCodePoint(c, source.charAt(i + 1)), start);
                i += 2;
            } else if (Character.isSurrogate(c)) {
                append(text, offsets, 0xfffd, start);
                invalid.add(new InvalidRange(i, ++i));
            } else {
                append(text, offsets, c, start);
                i++;
            }
        }
        offsets.add(source.length());
        return result(text, offsets, invalid, SourceUnit.UTF16);
    }

    private static void append(StringBuilder text, List<Integer> offsets, int cp, int source) {
        text.appendCodePoint(cp);
        offsets.add(source);
        if (cp > 0xffff) offsets.add(source);
    }

    private static TextInput result(
            StringBuilder text,
            List<Integer> offsets,
            List<InvalidRange> invalid,
            SourceUnit unit) {
        int[] values = new int[offsets.size()];
        for (int i = 0; i < values.length; i++) values[i] = offsets.get(i);
        return new TextInput(text.toString(), values, invalid, unit);
    }
}
