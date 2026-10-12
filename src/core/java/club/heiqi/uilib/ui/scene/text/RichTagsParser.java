package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只处理本阶段 DSL；首次错误整段字面呈现，不发布半解析状态或修复结构。 */
final class RichTagsParser {
    private final TextInput original;
    private final String raw;
    private final StringBuilder logical = new StringBuilder(), canonical = new StringBuilder();
    private final List<Integer> starts = new ArrayList<Integer>(), ends = new ArrayList<Integer>();
    private final List<TextInput.SourceSegment> gaps = new ArrayList<TextInput.SourceSegment>();
    private final List<TextStyleOverrideSpan> spans = new ArrayList<TextStyleOverrideSpan>();
    private final List<TextContent.LinkSpan> links = new ArrayList<TextContent.LinkSpan>();
    private final List<InlineContent> inline = new ArrayList<>();
    private final Deque<Scope> scopes = new ArrayDeque<Scope>();
    private TextStyleOverride style;
    private TextContent.Link link;
    private static final String[][] ENTITIES = {{"&lt;", "<"}, {"&gt;", ">"}, {"&amp;", "&"}};
    private int position;

    private static final class Scope {
        final String tag;
        final TextStyleOverride style;
        final TextContent.Link link;
        final int start, end;

        Scope(String tag, TextStyleOverride style, TextContent.Link link, int start, int end) {
            this.tag = tag;
            this.style = style;
            this.link = link;
            this.start = start;
            this.end = end;
        }
    }

    private static final class SyntaxFailure extends RuntimeException {
        final int start, end;
        final TextLayout.Diagnostic.Kind kind;

        SyntaxFailure(int start, int end, String message, TextLayout.Diagnostic.Kind kind) {
            super(message);
            this.start = start;
            this.end = end;
            this.kind = kind;
        }
    }

    static final class Parsed {
        final ParagraphContent content;
        final String canonical;

        Parsed(StyledText text, List<TextContent.LinkSpan> links, String canonical) {
            this(text, links, canonical, Collections.<InlineContent>emptyList());
        }

        Parsed(
                StyledText text,
                List<TextContent.LinkSpan> links,
                String canonical,
                List<InlineContent> inline) {
            content = new ParagraphContent(text, links, inline);
            this.canonical = canonical;
        }
    }

    private RichTagsParser(TextInput input, String raw) {
        original = input;
        this.raw = raw;
        style = TextStyleOverride.INHERIT;
    }

    static Parsed parse(TextInput input, String raw) {
        RichTagsParser parser = new RichTagsParser(input, raw);
        try {
            return parser.run();
        } catch (SyntaxFailure failure) {
            TextLayout.Diagnostic diagnostic =
                    new TextLayout.Diagnostic(
                            failure.kind,
                            input.sourceRight(failure.start),
                            input.sourceLeft(failure.end),
                            failure.getMessage());
            List<TextStyleOverrideSpan> spans =
                    input.text.isEmpty()
                            ? Collections.<TextStyleOverrideSpan>emptyList()
                            : Collections.singletonList(
                                    new TextStyleOverrideSpan(
                                            0, input.text.length(), TextStyleOverride.INHERIT));
            return new Parsed(
                    new StyledText(
                            input,
                            raw,
                            spans,
                            TextStyleOverride.INHERIT,
                            Collections.singletonList(diagnostic)),
                    Collections.<TextContent.LinkSpan>emptyList(),
                    escape(input.text));
        }
    }

    private Parsed run() {
        while (position < original.text.length()) {
            int cp = original.text.codePointAt(position);
            if (cp == '<') tag();
            else if (cp == '&') {
                int start = position;
                String value = entity();
                append(value, start, position);
                canonical.append(escape(value));
            } else {
                int start = position;
                position += Character.charCount(cp);
                String value = original.text.substring(start, position);
                append(value, start, position);
                canonical.append(escape(value));
            }
        }
        if (!scopes.isEmpty()) {
            Scope unclosed = scopes.peekLast();
            fail(unclosed.start, unclosed.end, "标签未闭合：" + unclosed.tag);
        }
        int[] a = new int[starts.size()], b = new int[ends.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = starts.get(i);
            b[i] = ends.get(i);
        }
        TextInput mapped = TextInput.fromMapped(original, logical.toString(), a, b, gaps);
        return new Parsed(
                new StyledText(
                        mapped, raw, spans, style, Collections.<TextLayout.Diagnostic>emptyList()),
                links,
                canonical.toString(),
                inline);
    }

    private void append(String value, int start, int end) {
        int offset = logical.length();
        logical.append(value);
        for (int i = 0; i < value.length(); i++) {
            starts.add(start);
            ends.add(end);
        }
        int finish = logical.length();
        if (!spans.isEmpty() && spans.get(spans.size() - 1).style.equals(style)) {
            TextStyleOverrideSpan previous = spans.remove(spans.size() - 1);
            spans.add(new TextStyleOverrideSpan(previous.start, finish, style));
        } else spans.add(new TextStyleOverrideSpan(offset, finish, style));
        if (link != null) {
            if (!links.isEmpty()
                    && links.get(links.size() - 1).link == link
                    && links.get(links.size() - 1).end == offset) {
                TextContent.LinkSpan previous = links.remove(links.size() - 1);
                links.add(new TextContent.LinkSpan(previous.start, finish, link));
            } else links.add(new TextContent.LinkSpan(offset, finish, link));
        }
    }

    private String entity() {
        int start = position;
        for (String[] entity : ENTITIES) {
            if (original.text.startsWith(entity[0], position)) {
                position += entity[0].length();
                return entity[1];
            }
        }
        fail(start, codePointEnd(start), "未知或无效实体，仅支持 lt/gt/amp");
        return null;
    }

    /** 词法输出仅描述一个完整标签，不改变正文、作用域或声明。end 是标签后的 UTF16 游标。 */
    private static final class TagToken {
        final int start, end;
        final String name;
        final Map<String, String> attributes;
        final boolean closing, selfClosing;

        TagToken(
                int start,
                int end,
                String name,
                Map<String, String> attributes,
                boolean closing,
                boolean selfClosing) {
            this.start = start;
            this.end = end;
            this.name = name;
            this.attributes = attributes;
            this.closing = closing;
            this.selfClosing = selfClosing;
        }
    }

    private void tag() {
        TagToken token = readTag();
        if (!supported(token.name)) unsupported(token.start, token.end, "本阶段不支持标签：" + token.name);
        if (token.closing) acceptClosing(token);
        else if (token.name.equals("br")) acceptBreak(token);
        else if (token.name.equals("latex")) acceptFormula(token);
        else if (token.name.equals("img")) acceptImage(token);
        else openScope(token);
    }

    /** 进入时 position 指向 <；成功仅消费词法，退出时 position == token.end。 */
    private TagToken readTag() {
        int start = position++;
        boolean closing = take('/');
        String name = name();
        if (name.isEmpty()) fail(start, codePointEnd(position), "缺少小写标签名");
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        boolean selfClosing = false;
        while (true) {
            boolean separated = whitespace();
            if (take('>')) break;
            if (take('/')) {
                if (!take('>')) fail(start, codePointEnd(position), "自闭合标签缺少 >");
                selfClosing = true;
                break;
            }
            if (closing || !separated) fail(start, codePointEnd(position), "无效标签或属性分隔");
            int attributeStart = position;
            String attribute = name();
            if (attribute.isEmpty()) fail(attributeStart, codePointEnd(position), "属性必须具名且小写");
            whitespace();
            if (!take('=')) fail(attributeStart, codePointEnd(position), "属性缺少 =");
            whitespace();
            if (!take('"')) fail(attributeStart, codePointEnd(position), "属性值必须用双引号");
            StringBuilder value = new StringBuilder();
            while (position < original.text.length() && original.text.charAt(position) != '"') {
                if (original.text.charAt(position) == '&') value.append(entity());
                else {
                    int cp = original.text.codePointAt(position);
                    if (cp == '<' || cp == '>')
                        fail(position, codePointEnd(position), "属性尖括号须使用实体");
                    value.appendCodePoint(cp);
                    position += Character.charCount(cp);
                }
            }
            if (!take('"')) fail(attributeStart, position, "属性双引号未闭合");
            if (attributes.put(attribute, value.toString()) != null)
                fail(attributeStart, position, "属性重复：" + attribute);
        }
        return new TagToken(start, position, name, attributes, closing, selfClosing);
    }

    /** 匹配后恢复进入作用域前的 style/link；闭合语法只形成 gap 和 canonical。 */
    private void acceptClosing(TagToken token) {
        int start = token.start;
        String name = token.name;
        Map<String, String> attributes = token.attributes;
        boolean selfClosing = token.selfClosing;

        if (selfClosing
                || !attributes.isEmpty()
                || scopes.isEmpty()
                || !scopes.peek().tag.equals(name)) fail(start, token.end, "闭合标签错配：" + name);
        Scope scope = scopes.pop();
        style = scope.style;
        link = scope.link;
        gap(start, token.end);
        canonical.append("</").append(name).append('>');
    }

    private void acceptBreak(TagToken token) {
        int start = token.start;
        Map<String, String> attributes = token.attributes;
        boolean selfClosing = token.selfClosing;

        if (!selfClosing || !attributes.isEmpty()) fail(start, token.end, "换行必须为 <br/>");
        append("\n", start, token.end);
        canonical.append("<br/>");
    }

    /** 从 token.end 捕获数学正文；提交一个占位符，其来源覆盖完整成对标签。 */
    private void acceptFormula(TagToken token) {
        int start = token.start;
        Map<String, String> attributes = token.attributes;
        boolean selfClosing = token.selfClosing;

        if (selfClosing
                || attributes.size() > 1
                || !attributes.isEmpty() && !attributes.containsKey("math-style"))
            fail(start, token.end, "latex仅接受math-style及成对闭合");
        String mode = attributes.getOrDefault("math-style", "text");
        club.heiqi.uilib.ui.scene.text.math.MathStyle mathStyle;
        switch (mode) {
            case "display":
                mathStyle = club.heiqi.uilib.ui.scene.text.math.MathStyle.DISPLAY;
                break;
            case "text":
                mathStyle = club.heiqi.uilib.ui.scene.text.math.MathStyle.TEXT;
                break;
            case "script":
                mathStyle = club.heiqi.uilib.ui.scene.text.math.MathStyle.SCRIPT;
                break;
            case "scriptscript":
                mathStyle = club.heiqi.uilib.ui.scene.text.math.MathStyle.SCRIPT_SCRIPT;
                break;
            default:
                fail(start, token.end, "未知数学样式：" + mode);
                return;
        }
        int body = token.end, close = original.text.indexOf("</latex>", body);
        if (close < 0) fail(start, token.end, "latex缺少闭合标签");
        // MathContent 的诊断是正文 substring 内的局部 UTF16；原子来源则沿文档 sourceUnit 覆盖标签全段。
        String formula = original.text.substring(body, close);
        position = close + 8;
        inline.add(
                new InlineContent(
                        logical.length(),
                        original.sourceRight(start),
                        original.sourceLeft(position),
                        club.heiqi.uilib.ui.scene.text.math.MathContent.parse(formula),
                        mathStyle));
        append("\ufffc", start, position);
        // 数学正文按捕获语义原样往返，不把尖括号或与号送回DSL实体解析。
        canonical.append(original.text, start, position);
    }

    /** 属性验证完成后提交一个原子声明；不改变外层 style/link 或作用域。 */
    private void acceptImage(TagToken token) {
        int start = token.start;
        Map<String, String> attributes = token.attributes;
        boolean selfClosing = token.selfClosing;

        if (!selfClosing || !attributes.containsKey("src") || attributes.get("src").isEmpty())
            fail(start, token.end, "img须自闭合且声明src");
        for (String key : attributes.keySet())
            if (!key.equals("src")
                    && !key.equals("alt")
                    && !key.equals("width")
                    && !key.equals("height")
                    && !key.equals("fit")) fail(start, token.end, "未知图像属性：" + key);
        Integer width = imageSize(attributes.get("width"), start, token.end),
                height = imageSize(attributes.get("height"), start, token.end);
        club.heiqi.uilib.ui.scene.image.ImageOptions.Fit fit;
        switch (attributes.getOrDefault("fit", "contain")) {
            case "contain":
                fit = club.heiqi.uilib.ui.scene.image.ImageOptions.Fit.CONTAIN;
                break;
            case "cover":
                fit = club.heiqi.uilib.ui.scene.image.ImageOptions.Fit.COVER;
                break;
            case "stretch":
                fit = club.heiqi.uilib.ui.scene.image.ImageOptions.Fit.STRETCH;
                break;
            case "original":
                fit = club.heiqi.uilib.ui.scene.image.ImageOptions.Fit.NATIVE;
                break;
            default:
                fail(start, token.end, "未知图像适配");
                return;
        }
        inline.add(
                new InlineContent(
                        logical.length(),
                        original.sourceRight(start),
                        original.sourceLeft(token.end),
                        attributes.get("src"),
                        attributes.getOrDefault("alt", ""),
                        width,
                        height,
                        fit));
        append("\ufffc", start, token.end);
        canonical.append(original.text, start, token.end);
    }

    /** 先验证属性并派生样式，再保存旧 style/link；标签只形成 gap 和 canonical。 */
    private void openScope(TagToken token) {
        int start = token.start;
        String name = token.name;
        Map<String, String> attributes = token.attributes;
        boolean selfClosing = token.selfClosing;
        if (selfClosing) fail(start, token.end, "成对标签不能自闭合：" + name);
        String expected =
                name.equals("color") || name.equals("size") || name.equals("spacing")
                        ? "value"
                        : name.equals("a") ? "href" : null;
        if (name.equals("mark")) {
            if (attributes.size() > 1 || !attributes.isEmpty() && !attributes.containsKey("color"))
                fail(start, token.end, "mark仅支持可选color属性");
        } else if (expected == null
                ? !attributes.isEmpty()
                : attributes.size() != 1 || !attributes.containsKey(expected))
            fail(start, token.end, "无效或缺失属性：" + name);
        TextStyleOverride.Builder next = style.toBuilder();
        if (name.equals("b")) next.weight(700);
        if (name.equals("i")) next.posture(FontStyle.Posture.ITALIC);
        if (name.equals("u")) next.underline(true).decorationFromEm(true);
        if (name.equals("s")) next.strike(true).decorationFromEm(true);
        if (name.equals("sup")) next.superscript();
        if (name.equals("sub")) next.subscript();
        if (name.equals("spacing")) {
            try { next.letterSpacing(Float.parseFloat(attributes.get("value"))); }
            catch (IllegalArgumentException invalid) { fail(start, token.end, "无效 logical px 字距"); }
        }
        if (name.equals("mark")) {
            if (attributes.containsKey("color")) next.highlight(color(attributes.get("color"), start, token.end));
            else next.highlight();
        }
        if (name.equals("color")) {
            next.argb(color(attributes.get("value"), start, token.end));
        }
        if (name.equals("size")) {
            try {
                next.em(Float.parseFloat(attributes.get("value")));
            } catch (IllegalArgumentException invalid) {
                fail(start, token.end, "无效 logical px 字号");
            }
        }
        scopes.push(new Scope(name, style, link, start, token.end));
        style = next.build();
        if (name.equals("a")) link = new TextContent.Link(attributes.get("href"));
        gap(start, token.end);
        canonical.append('<').append(name);
        for (Map.Entry<String, String> attribute : attributes.entrySet())
            canonical
                    .append(' ')
                    .append(attribute.getKey())
                    .append("=\"")
                    .append(escape(attribute.getValue()))
                    .append('"');
        canonical.append('>');
    }

    private int color(String value, int start, int end) {
        if (!value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
            fail(start, end, "颜色必须为 #RRGGBB 或 #AARRGGBB");
        long parsed = Long.parseLong(value.substring(1), 16);
        return (int) (value.length() == 7 ? parsed | 0xff000000L : parsed);
    }

    private Integer imageSize(String value, int start, int end) {
        if (value == null) return null;
        try {
            double number = Double.parseDouble(value);
            if (!Double.isFinite(number) || number < 0 || number > Integer.MAX_VALUE)
                throw new NumberFormatException();
            return (int) Math.ceil(number);
        } catch (NumberFormatException invalid) {
            fail(start, end, "无效图像logical px尺寸");
            return null;
        }
    }

    private boolean supported(String name) {
        return name.equals("b")
                || name.equals("i")
                || name.equals("u")
                || name.equals("s")
                || name.equals("mark")
                || name.equals("sup")
                || name.equals("sub")
                || name.equals("spacing")
                || name.equals("color")
                || name.equals("size")
                || name.equals("br")
                || name.equals("a")
                || name.equals("latex")
                || name.equals("img");
    }

    private void gap(int start, int end) {
        gaps.add(new TextInput.SourceSegment(logical.length(), logical.length(), start, end, true));
    }

    private String name() {
        int start = position;
        while (position < original.text.length()
                && (original.text.charAt(position) >= 'a' && original.text.charAt(position) <= 'z'
                        || original.text.charAt(position) == '-')) position++;
        return original.text.substring(start, position);
    }

    private boolean whitespace() {
        int start = position;
        while (position < original.text.length()
                && " \t\r\n".indexOf(original.text.charAt(position)) >= 0) position++;
        return position != start;
    }

    private boolean take(char value) {
        if (position < original.text.length() && original.text.charAt(position) == value) {
            position++;
            return true;
        }
        return false;
    }

    private int codePointEnd(int offset) {
        return offset >= original.text.length()
                ? original.text.length()
                : offset + Character.charCount(original.text.codePointAt(offset));
    }

    private void fail(int start, int end, String message) {
        throw new SyntaxFailure(start, end, message, TextLayout.Diagnostic.Kind.INVALID_INPUT);
    }

    private void unsupported(int start, int end, String message) {
        throw new SyntaxFailure(start, end, message, TextLayout.Diagnostic.Kind.UNSUPPORTED);
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
