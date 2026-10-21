package club.heiqi.uilib.host.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.StyleSpan;
import club.heiqi.uilib.ui.scene.text.StyledText;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.TextStyleOverride;
import club.heiqi.uilib.ui.scene.text.TextStyleOverrideSpan;

/** 宿主格式转换的唯一入口；核心接收中性的来源映射与属性意图。 */
public final class MinecraftFormat {
    private MinecraftFormat() {}

    public interface Context {
        int paletteColor(int index);

        /** 宿主显示码点快照；不改raw或来源范围，格式标记先于本转换消费。 */
        default int codePoint(int codePoint) { return codePoint; }

        TextEffects effects(
                boolean bold,
                boolean italic,
                boolean underline,
                boolean strike,
                boolean obfuscated);

        TextDecoration decoration();
    }

    public static AttributedText parse(String source, TextStyle base, Context context) {
        return parse(TextInput.string(source), source, base, context);
    }

    public static AttributedText parse(
            TextInput original, String raw, TextStyle base, Context context) {
        AttributedText value = styled(original, raw, context).resolve(base);
        List<StyleSpan> spans = new ArrayList<>();
        for (StyleSpan s : value.spans())
            spans.add(new StyleSpan(s.logicalStart, s.logicalEnd, hostStyle(s.style, context)));
        return new AttributedText(
                value.input,
                raw,
                hostStyle(reset().resolve(base), context),
                spans,
                hostStyle(value.trailingStyle, context),
                value.diagnostics());
    }

    private static TextStyle hostStyle(TextStyle style, Context context) {
        TextEffects e = style.effects;
        return new TextStyle(
                style.font,
                style.logicalEm,
                style.color,
                style.candidates(),
                context.effects(
                        style.fontStyle.weight >= 700,
                        style.fontStyle.posture != FontStyle.Posture.NORMAL,
                        e.underline,
                        e.strike,
                        e.obfuscated),
                context.decoration(),
                style.fontStyle);
    }

    public static StyledText styled(TextInput original, String raw, Context context) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(original, "original");
        StringBuilder text = new StringBuilder();
        ArrayList<Integer> starts = new ArrayList<>(), ends = new ArrayList<>();
        List<TextStyleOverrideSpan> spans = new ArrayList<>();
        List<TextInput.SourceSegment> gaps = new ArrayList<>();
        List<TextLayout.Diagnostic> diagnostics = new ArrayList<>();
        TextStyleOverride reset = reset(), state = reset;
        boolean bold = false, italic = false, underline = false, strike = false, obfuscated = false;
        int spanStart = 0;
        for (int i = 0; i < original.text.length(); ) {
            int cp = original.text.codePointAt(i), next = i + Character.charCount(cp);
            if (cp == 167 && next < original.text.length()) {
                int end = next + Character.charCount(original.text.codePointAt(next));
                int code =
                        "0123456789abcdefklmnor"
                                .indexOf(Character.toLowerCase(original.text.charAt(next)));
                if (text.length() > spanStart) {
                    spans.add(new TextStyleOverrideSpan(spanStart, text.length(), state));
                    spanStart = text.length();
                }
                gaps.add(new TextInput.SourceSegment(text.length(), text.length(), i, end, true));
                if (code < 16 || code == 21) {
                    bold = italic = underline = strike = obfuscated = false;
                    state = reset;
                    if (code < 16) {
                        state =
                                reset.toBuilder()
                                        .rgb(context.paletteColor(code < 0 ? 15 : code))
                                        .build();
                        if (code < 0)
                            diagnostics.add(
                                    new TextLayout.Diagnostic(
                                            TextLayout.Diagnostic.Kind.UNSUPPORTED,
                                            original.sourceStart(i),
                                            original.sourceEnd(end - 1),
                                            "未知§格式消费后恢复白色并清效果"));
                    }
                } else {
                    bold |= code == 17;
                    italic |= code == 20;
                    underline |= code == 19;
                    strike |= code == 18;
                    obfuscated |= code == 16;
                    state =
                            state.toBuilder()
                                    .weight(bold ? 700 : 400)
                                    .posture(
                                            italic
                                                    ? FontStyle.Posture.ITALIC
                                                    : FontStyle.Posture.NORMAL)
                                    .underline(underline)
                                    .strike(strike)
                                    .obfuscated(obfuscated)
                                    .build();
                }
                i = end;
                continue;
            }
            int mapped = context.codePoint(cp);
            if (!Character.isValidCodePoint(mapped) || mapped >= 0xD800 && mapped <= 0xDFFF)
                throw new IllegalArgumentException("宿主映射必须返回Unicode标量: " + mapped);
            text.appendCodePoint(mapped);
            // 显示码点可改变UTF16长度；每个输出单元仍指向原完整码点。
            for (int j = 0; j < Character.charCount(mapped); j++) {
                starts.add(i);
                ends.add(next);
            }
            i = next;
        }
        if (text.length() > spanStart)
            spans.add(new TextStyleOverrideSpan(spanStart, text.length(), state));
        int[] a = new int[starts.size()], b = new int[ends.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = starts.get(i);
            b[i] = ends.get(i);
        }
        return new StyledText(
                TextInput.fromMapped(original, text.toString(), a, b, gaps),
                raw,
                spans,
                state,
                diagnostics);
    }

    private static TextStyleOverride reset() {
        return TextStyleOverride.builder()
                .weight(400)
                .posture(FontStyle.Posture.NORMAL)
                .underline(false)
                .strike(false)
                .obfuscated(false)
                .decorationFromEm(true)
                .build();
    }
}
