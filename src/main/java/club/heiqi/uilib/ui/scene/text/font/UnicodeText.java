package club.heiqi.uilib.ui.scene.text.font;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.BitSet;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout.Diagnostic;

/** 控制可视化与 Unicode 分段；所有范围回到 TextInput，显示串不成为新的 source。 */
final class UnicodeText {
    final TextInput input;
    final String display;
    final int[] logical;
    final List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    UnicodeText(TextInput input) {
        this.input = input;
        for (TextInput.InvalidRange r : input.invalidRanges())
            diagnostics.add(new Diagnostic(Diagnostic.Kind.INVALID_INPUT, r.start, r.end, "无效输入替代为 U+FFFD"));
        StringBuilder out = new StringBuilder(); List<Integer> offsets = new ArrayList<Integer>();
        for (int i = 0; i < input.text.length();) {
            int start = i, cp = input.text.codePointAt(i); i += Character.charCount(cp);
            if (cp == 13) {
                if (i < input.text.length() && input.text.charAt(i) == 10) i++;
                cp = 10;
            } else if (cp == 0x2028 || cp == 0x2029) cp = 10;
            else if ((cp < 32 && cp != 9 && cp != 10) || cp >= 0x7f && cp <= 0x9f) {
                diagnostics.add(new Diagnostic(Diagnostic.Kind.CONTROL_MARKER, input.sourceOffset(start), input.sourceOffset(i),
                    "控制符 U+" + Integer.toHexString(cp).toUpperCase(java.util.Locale.ROOT) + " 显示标记"));
                cp = cp < 32 ? 0x2400 + cp : cp == 127 ? 0x2421 : 0x2426;
            }
            out.appendCodePoint(cp); offsets.add(start);
            if (cp > 0xffff) offsets.add(start);
        }
        offsets.add(input.text.length()); display = out.toString(); logical = new int[offsets.size()];
        for (int i = 0; i < logical.length; i++) logical[i] = offsets.get(i);
    }
    int source(int offset) { return input.sourceOffset(logical[offset]); }
    List<Unit> units(int start, int end, String language, EmojiData emojiData) {
        String text = display.substring(start, end);
        BreakIterator breaks = BreakIterator.getCharacterInstance(ULocale.forLanguageTag(language)); breaks.setText(text);
        TreeSet<Integer> boundaries = new TreeSet<Integer>();
        for (int b = breaks.first(); b != BreakIterator.DONE; b = breaks.next()) boundaries.add(b);
        // ICU76 未知的新版本 RGI 序列按锁定的数据整体匹配，保留 ICU 已知字素边界。
        for (int a = 0; a < text.length();) {
            int sequenceEnd = emojiData.sequenceEnd(text, a, text.length());
            if (sequenceEnd > a) boundaries.subSet(a, false, sequenceEnd, false).clear();
            Integer next = boundaries.higher(a); if (next == null) break; a = next;
        }
        List<Unit> result = new ArrayList<Unit>(); int previousScript = UScript.COMMON;
        for (int a = 0; a < text.length();) {
            int b = boundaries.higher(a);
            int script = UScript.COMMON;
            boolean emoji = false, vs15 = false, vs16 = false, emojiLike = false;
            for (int i = a; i < b;) {
                int cp = text.codePointAt(i); i += Character.charCount(cp);
                int s = UScript.getScript(cp);
                if (s != UScript.COMMON && s != UScript.INHERITED && script == UScript.COMMON) script = s;
                emoji |= emojiData.isDefaultEmoji(cp) || cp == 0x20e3;
                emojiLike |= emojiData.isEmoji(cp);
                vs15 |= cp == 0xfe0e; vs16 |= cp == 0xfe0f;
            }
            // 独立标记的 inherited script 沿前一实际 script；普通空格/标点不变成连接范围。
            int first = text.codePointAt(a);
            if (script == UScript.COMMON && (UCharacter.getType(first) == UCharacter.NON_SPACING_MARK
                    || UCharacter.getType(first) == UCharacter.COMBINING_SPACING_MARK)) script = previousScript;
            if (script != UScript.COMMON) previousScript = script;
            result.add(new Unit(start + a, start + b, script, !vs15 && (vs16 && emojiLike || emoji), emojiLike));
            a = b;
        }
        resolveConnectingScripts(result);
        return result;
    }
    /** Common tatweel/连接控制必须留在必要 word 脚本范围；空格仍是范围边界。 */
    private void resolveConnectingScripts(List<Unit> units) {
        int[] following = new int[units.size()]; int script = UScript.COMMON;
        for (int i = units.size() - 1; i >= 0; i--) {
            Unit u = units.get(i); following[i] = script;
            if (UCharacter.isUWhiteSpace(display.codePointAt(u.start))) script = UScript.COMMON;
            else if (u.script != UScript.COMMON && u.script != UScript.INHERITED) script = u.script;
        }
        script = UScript.COMMON;
        BitSet extensions = new BitSet();
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i); int cp = display.codePointAt(u.start);
            if (UCharacter.isUWhiteSpace(cp)) { script = UScript.COMMON; continue; }
            if (u.script == UScript.COMMON && !u.emojiLike) {
                boolean causer = UCharacter.getIntPropertyValue(cp, UProperty.JOINING_TYPE) == UCharacter.JoiningType.JOIN_CAUSING;
                int type = UCharacter.getType(cp);
                if (causer || type == UCharacter.NON_SPACING_MARK || type == UCharacter.COMBINING_SPACING_MARK) {
                    extensions.clear(); UScript.getScriptExtensions(cp, extensions);
                    if (script != UScript.COMMON && (extensions.get(script) || UScript.getScript(cp) == UScript.INHERITED)) u.script = script;
                    else if (following[i] != UScript.COMMON && (extensions.get(following[i]) || UScript.getScript(cp) == UScript.INHERITED)) u.script = following[i];
                }
            }
            if (u.script != UScript.COMMON && u.script != UScript.INHERITED) script = u.script;
        }
    }
    /** 应急断行也保留LB11/12/12a，GL前的SP/BA/HY例外不能误当成WJ双方禁断。 */
    static boolean prohibitsBreak(String text, int boundary) {
        int left = UCharacter.getIntPropertyValue(text.codePointBefore(boundary), UProperty.LINE_BREAK);
        int right = UCharacter.getIntPropertyValue(text.codePointAt(boundary), UProperty.LINE_BREAK);
        if (left == UCharacter.LineBreak.WORD_JOINER || right == UCharacter.LineBreak.WORD_JOINER) return true;
        if (left == UCharacter.LineBreak.GLUE) return true;
        return right == UCharacter.LineBreak.GLUE && left != UCharacter.LineBreak.SPACE
            && left != UCharacter.LineBreak.BREAK_AFTER && left != UCharacter.LineBreak.HYPHEN;
    }
    static boolean invisible(int cp) {
        return UCharacter.hasBinaryProperty(cp, UProperty.DEFAULT_IGNORABLE_CODE_POINT)
            || UCharacter.hasBinaryProperty(cp, UProperty.BIDI_CONTROL);
    }
    static boolean visible(String text, int start, int end) {
        for (int i = start; i < end;) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            if (!invisible(cp)) return true;
        }
        return false;
    }
    static boolean complex(int script) {
        String s = UScript.getShortName(script);
        return "Arab Syrc Thaa Nkoo Mand Adlm Deva Beng Guru Gujr Orya Taml Telu Knda Mlym Sinh Thai Laoo Mymr Khmr Bali Java".contains(s)
            && script != UScript.COMMON && script != UScript.INHERITED;
    }
    static final class Unit {
        final int start, end;
        int script;
        final boolean emoji, emojiLike;
        FontSource source;
        boolean replacement;
        int replacementId;
        Unit(int start, int end, int script, boolean emoji, boolean emojiLike) {
            this.start = start; this.end = end; this.script = script; this.emoji = emoji; this.emojiLike = emojiLike;
        }
    }
}
