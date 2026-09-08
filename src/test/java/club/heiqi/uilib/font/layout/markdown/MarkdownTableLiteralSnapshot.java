package club.heiqi.uilib.font.layout.markdown;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/** 历史捕获器：只调用 UILib 既有解析、段流和行缝，禁用新禁表模式自比。 */
final class MarkdownTableLiteralSnapshot {
    private static final String TICK = String.valueOf((char) 0x60);
    static final String[] NAMES = {
        "basic", "paragraph-upgrade", "setext", "quote-lazy", "list", "escaped-pipe",
        "inline", "multiple", "paragraph-table-paragraph", "span-explicit-color", "nested-list", "geometry"
    };
    static final String[] SOURCES = {
        "A|B\n-| -\nx|y",
        "pre\nA|B\n-| -",
        "A\n---",
        "> A|B\n> -| -\n> x|y\nlazy|tail",
        "- A|B\n  -| -\n  x|y\n- after",
        "A\\|B|C\n---|---\nx\\|y|z",
        "**bold**|[link](https://example.org/a)\n---|---\n" + TICK + "code" + TICK
            + "|$x^2$\n~~strike~~|*italic*",
        "A|B\n---|---\nx|y\n\nC|D\n:---|---:\nu|v",
        "before\n\nA|B\n---|---\nx|y\n\nafter",
        "> **A**|B\n> :---|---:\n> [x](https://example.org)|" + TICK + "y" + TICK + "\nplain",
        "- outer\n  - A|B\n    ---|---\n    x|y\n\n    tail",
        "> A|B\n> ---|---\n> x|y\n\n" + TICK + TICK + TICK + "java\nA|B\n---|---\n"
            + TICK + TICK + TICK + "\n\n---"
    };

    static TextStyle base() {
        TextStyle style = new TextStyle();
        style.setColor(0xFFBACADA);
        style.setFontSizePx(17);
        style.setLetterSpacing(0.5F);
        return style;
    }

    static MarkdownDocument document(int index) {
        if (!NAMES[index].equals("span-explicit-color")) return MarkdownDocument.parse(SOURCES[index]);
        TextStyle explicit = base();
        explicit.setColor(0xFF123456);
        explicit.setUnderline(true);
        explicit.setMarkColor(0x33445566);
        TextStyle other = base();
        other.setColor(0xFFABCDEF);
        other.setStrikethrough(true);
        return MarkdownDocument.parseSpans(Arrays.asList(
            new MarkdownSpan("> **A**|", explicit),
            new MarkdownSpan("B\n> :---|---:\n> [x](https://example.org)|", other),
            new MarkdownSpan(TICK + "y" + TICK + "\nplain", explicit)));
    }

    static String snapshot(int index) throws Exception {
        MarkdownDocument doc = document(index);
        return NAMES[index] + "\nsource=" + encode(SOURCES[index])
            + "\nsegments=" + encode(doc.toSegments(base()))
            + "\nlines=" + encode(doc.toLayoutLines(null, base())) + "\n";
    }

    /**
     * 递归记录历史公开 getter，按名称排序；除已批准新增的 TextSegment.getLatexMathStyle
     * 外，新增 getter 仍使旧 fixture 失配。该新增字段在降级锁中独立断言，不重录历史 fixture。
     * 样式/链接/code/latex、行 id/链/几何全部展开，不以 toString 代替数据。
     * 浮点按原始位记录，字符串以 UTF-16 转义保留每一个字符与换行。
     */
    static String encode(Object value) throws Exception {
        if (value == null) return "null";
        if (value instanceof String) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : ((String) value).toCharArray()) {
                if (c == '\\' || c == '"') out.append('\\').append(c);
                else if (c < 32 || c > 126) out.append(String.format("\\u%04x", (int) c));
                else out.append(c);
            }
            return out.append('"').toString();
        }
        if (value instanceof Float) return "float:" + Integer.toHexString(Float.floatToRawIntBits((Float) value));
        if (value instanceof Double) return "double:" + Long.toHexString(Double.doubleToRawLongBits((Double) value));
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Enum<?>) return ((Enum<?>) value).name();
        if (value instanceof List<?>) {
            List<String> values = new ArrayList<String>();
            for (Object element : (List<?>) value) values.add(encode(element));
            return "[" + String.join(",", values) + "]";
        }
        if (!(value instanceof TextSegment || value instanceof TextStyle || value instanceof MarkdownLayoutLine)) {
            throw new AssertionError("Unserialized consumer type: " + value.getClass());
        }
        Method[] methods = value.getClass().getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        List<String> fields = new ArrayList<String>();
        for (Method method : methods) {
            String name = method.getName();
            // 只排除这个确切的新增零参 getter；其他类型/名称/历史字段一律仍参与 oracle。
            if (method.getDeclaringClass() == TextSegment.class && name.equals("getLatexMathStyle")
                    && method.getParameterTypes().length == 0) continue;
            if (method.getParameterTypes().length == 0 && !Modifier.isStatic(method.getModifiers())
                && !name.equals("getClass") && (name.startsWith("get") || name.startsWith("is"))) {
                fields.add(name + "=" + encode(method.invoke(value)));
            }
        }
        if (fields.isEmpty()) throw new AssertionError("No consumer fields: " + value.getClass());
        return value.getClass().getSimpleName() + "{" + String.join(",", fields) + "}";
    }

    /** 仅供工作站历史捕获脚本调用；JUnit 不会写入或更新 fixture。 */
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < NAMES.length; i++) System.out.print(snapshot(i));
    }
}
