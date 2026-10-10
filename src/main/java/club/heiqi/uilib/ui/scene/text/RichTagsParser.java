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
    private final TextContent declaration;
    private final TextInput original;
    private final String raw;
    private final StringBuilder logical = new StringBuilder(), canonical = new StringBuilder();
    private final List<Integer> starts = new ArrayList<Integer>(), ends = new ArrayList<Integer>();
    private final List<TextInput.SourceSegment> gaps = new ArrayList<TextInput.SourceSegment>();
    private final List<TextStyleOverrideSpan> spans = new ArrayList<TextStyleOverrideSpan>();
    private final List<TextContent.LinkSpan> links = new ArrayList<TextContent.LinkSpan>();
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
        final StyledText text;
        final List<TextContent.LinkSpan> links;
        final String canonical;

        Parsed(StyledText text, List<TextContent.LinkSpan> links, String canonical) {
            this.text = text;
            this.links = Collections.unmodifiableList(new ArrayList<>(links));
            this.canonical = canonical;
        }

        TextContent.Resolved resolve(TextStyle base) {
            return new TextContent.Resolved(text.resolve(base), links, canonical);
        }
    }

    private RichTagsParser(TextContent declaration, TextInput input, String raw) {
        this.declaration = declaration;
        original = input;
        this.raw = raw;
        style = TextStyleOverride.INHERIT;
    }

    static Parsed parse(TextContent declaration, TextInput input, String raw) {
        RichTagsParser parser = new RichTagsParser(declaration, input, raw);
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
                canonical.toString());
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

    private void tag() {
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
        if (!supported(name)) unsupported(start, position, "本阶段不支持标签：" + name);
        if (closing) {
            if (selfClosing
                    || !attributes.isEmpty()
                    || scopes.isEmpty()
                    || !scopes.peek().tag.equals(name)) fail(start, position, "闭合标签错配：" + name);
            Scope scope = scopes.pop();
            style = scope.style;
            link = scope.link;
            gap(start, position);
            canonical.append("</").append(name).append('>');
            return;
        }
        if (name.equals("br")) {
            if (!selfClosing || !attributes.isEmpty()) fail(start, position, "换行必须为 <br/>");
            append("\n", start, position);
            canonical.append("<br/>");
            return;
        }
        if (selfClosing) fail(start, position, "成对标签不能自闭合：" + name);
        String expected =
                name.equals("color") || name.equals("size")
                        ? "value"
                        : name.equals("a") ? "href" : null;
        if (expected == null
                ? !attributes.isEmpty()
                : attributes.size() != 1 || !attributes.containsKey(expected))
            fail(start, position, "无效或缺失属性：" + name);
        TextStyleOverride.Builder next = style.toBuilder();
        if (name.equals("b")) next.weight(700);
        if (name.equals("i")) next.posture(FontStyle.Posture.ITALIC);
        if (name.equals("u")) next.underline(true).decorationFromEm(true);
        if (name.equals("s")) next.strike(true).decorationFromEm(true);
        if (name.equals("color")) {
            String value = attributes.get("value");
            if (!value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
                fail(start, position, "颜色必须为 #RRGGBB 或 #AARRGGBB");
            long color = Long.parseLong(value.substring(1), 16);
            next.argb((int) (value.length() == 7 ? color | 0xff000000L : color));
        }
        if (name.equals("size")) {
            try {
                next.em(Float.parseFloat(attributes.get("value")));
            } catch (IllegalArgumentException invalid) {
                fail(start, position, "无效 logical px 字号");
            }
        }
        scopes.push(new Scope(name, style, link, start, position));
        style = next.build();
        if (name.equals("a")) link = declaration.linkDeclaration(start, attributes.get("href"));
        gap(start, position);
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

    private boolean supported(String name) {
        return name.equals("b")
                || name.equals("i")
                || name.equals("u")
                || name.equals("s")
                || name.equals("color")
                || name.equals("size")
                || name.equals("br")
                || name.equals("a");
    }

    private void gap(int start, int end) {
        gaps.add(new TextInput.SourceSegment(logical.length(), logical.length(), start, end, true));
    }

    private String name() {
        int start = position;
        while (position < original.text.length()
                && original.text.charAt(position) >= 'a'
                && original.text.charAt(position) <= 'z') position++;
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
