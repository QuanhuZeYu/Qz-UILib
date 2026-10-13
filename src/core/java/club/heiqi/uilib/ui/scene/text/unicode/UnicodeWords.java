package club.heiqi.uilib.ui.scene.text.unicode;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import java.util.Arrays;
import java.util.Objects;
import java.util.TreeSet;

/** 当前语言的逻辑词段；只使用完整字素边界，不借用字体的视觉簇顺序。 */
public final class UnicodeWords {
    private UnicodeWords() {}

    /**
     * 返回方向上的逻辑词目标。调用方须保证 caret 位于 0..text.length()，
     * 且是当前 profile 下的完整字素边界；profile 须为当次非 null 的不可变规则快照。
     * language 须为有效语言标签，root/und 沿 TextPolicy 既有合同；direction 为 -1 或 1。
     */
    public static int target(String text, int caret, int direction,
            String language, UnicodeGraphemes.Profile profile) {
        if (direction != -1 && direction != 1)
            throw new IllegalArgumentException("direction 必须是 -1 或 1");
        int[] graphemes = profile.boundaries(text);
        BreakIterator words = BreakIterator.getWordInstance(
                ULocale.forLanguageTag(Objects.requireNonNull(language, "language")));
        words.setText(text);
        TreeSet<Integer> stops = new TreeSet<>();
        for (int b = words.first(); b != BreakIterator.DONE; b = words.next())
            if (Arrays.binarySearch(graphemes, b) >= 0) stops.add(b);
        // Unicode 空白与非空白的交界仍只落在完整字素边上；NBSP 也属于间隔。
        for (int b : graphemes)
            if (b > 0 && b < text.length()
                    && UCharacter.isUWhiteSpace(text.codePointBefore(b))
                            != UCharacter.isUWhiteSpace(text.codePointAt(b))) stops.add(b);
        int position = caret;
        if (direction < 0) {
            while (position > 0 && whitespace(text, stops.lower(position), position))
                position = stops.lower(position);
            return position == 0 ? 0 : stops.lower(position);
        }
        if (position < text.length()) position = stops.higher(position);
        while (position < text.length() && whitespace(text, position, stops.higher(position)))
            position = stops.higher(position);
        return position;
    }

    private static boolean whitespace(String text, int start, int end) {
        for (int p = start; p < end; ) {
            int code = text.codePointAt(p);
            if (!UCharacter.isUWhiteSpace(code)) return false;
            p += Character.charCount(code);
        }
        return true;
    }
}
