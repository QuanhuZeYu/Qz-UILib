package club.heiqi.uilib.ui.scene.text.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 数学 token 的位置始终为原输入的 UTF-16 范围，控制词尾空白归该 token。 */
public final class MathLexer {
    private MathLexer() {}

    public static final class Token {
        public final String text;
        public final int sourceStart, sourceEnd;
        public final String definitionSource;
        public final int definitionStart, definitionEnd;

        /** 实际生成的环境声明身份，与来源位置无关；非环境token为-1。 */
        public final int environmentOrdinal;

        final boolean noexpand, treatAsRelax;

        Token(
                String text,
                int start,
                int end,
                String origin,
                int ds,
                int de,
                boolean noexpand,
                boolean relax) {
            this(text, start, end, origin, ds, de, noexpand, relax, -1);
        }

        private Token(
                String text,
                int start,
                int end,
                String origin,
                int ds,
                int de,
                boolean noexpand,
                boolean relax,
                int environmentOrdinal) {
            this.environmentOrdinal = environmentOrdinal;
            this.text = text;
            this.sourceStart = start;
            this.sourceEnd = end;
            this.definitionSource = origin;
            this.definitionStart = ds;
            this.definitionEnd = de;
            this.noexpand = noexpand;
            this.treatAsRelax = relax;
        }

        Token(String text, int start, int end, String source) {
            this(text, start, end, source, start, end, false, false);
        }

        Token at(Token call) {
            return new Token(
                    text,
                    call.sourceStart,
                    call.sourceEnd,
                    definitionSource,
                    definitionStart,
                    definitionEnd,
                    noexpand,
                    treatAsRelax,
                    environmentOrdinal);
        }

        Token flags(boolean protect, boolean relax) {
            return new Token(
                    text,
                    sourceStart,
                    sourceEnd,
                    definitionSource,
                    definitionStart,
                    definitionEnd,
                    protect,
                    relax,
                    environmentOrdinal);
        }

        Token environment(int ordinal) {
            return new Token(
                    text,
                    sourceStart,
                    sourceEnd,
                    definitionSource,
                    definitionStart,
                    definitionEnd,
                    noexpand,
                    treatAsRelax,
                    ordinal);
        }

        Token text(String value) {
            return new Token(
                    value,
                    sourceStart,
                    sourceEnd,
                    definitionSource,
                    definitionStart,
                    definitionEnd,
                    noexpand,
                    treatAsRelax,
                    environmentOrdinal);
        }
    }

    static final class Failure extends RuntimeException {
        final Token token;

        Failure(Token token, String message) {
            super(message);
            this.token = token;
        }
    }

    static boolean letter(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '@';
    }

    static boolean space(char c) {
        return c == ' ' || c == '\r' || c == '\n' || c == '\t';
    }

    public static List<Token> lex(String source) {
        Objects.requireNonNull(source, "source");
        List<Token> tokens = new ArrayList<Token>();
        Scanner scanner = new Scanner(source);
        Token token;
        while ((token = scanner.next(true)) != null) tokens.add(token);
        return Collections.unmodifiableList(tokens);
    }

    /** URL 的 catcode 必须在读取其源码前切换，不能先把整式注释吞掉。 */
    static final class Scanner {
        final String source;
        int i;

        Scanner(String source) {
            this.source = source;
        }

        Token next(boolean comments) {
            while (i < source.length()) {
                int start = i;
                char ch = source.charAt(i);
                if (comments && ch == '%') {
                    int nl = source.indexOf('\n', i + 1);
                    i = nl < 0 ? source.length() : nl + 1;
                    continue;
                }
                String text;
                if (space(ch)) {
                    while (i < source.length() && space(source.charAt(i))) i++;
                    text = " ";
                } else if (ch == '\\') text = controlSequence(start);
                else text = unicodeAtom(start);
                return new Token(text, start, i, source);
            }
            return null;
        }

        /** 控制词、控制空格和单字符控制符共用起点及尾空白来源规则。 */
        private String controlSequence(int start) {
            i++;
            if (i == source.length()) throw fail(source, start, i, "未完成的控制序列");
            int verbEnd = verbatimEnd();
            if (verbEnd >= 0) {
                i = verbEnd;
                return source.substring(start, i);
            }
            if (letter(source.charAt(i))) {
                while (i < source.length() && letter(source.charAt(i))) i++;
                String text = source.substring(start, i);
                while (i < source.length() && space(source.charAt(i))) i++;
                return text;
            }
            if (space(source.charAt(i))) {
                controlSpace();
                return "\\ ";
            }
            if (Character.isSurrogate(source.charAt(i)))
                // 拒绝补充字符控制符时，诊断仍覆盖完整 UTF-16 代理对。
                throw fail(
                        source,
                        start,
                        i + Character.charCount(source.codePointAt(i)),
                        "控制符必须是BMP字符");
            i++;
            return source.substring(start, i);
        }

        /** 只在存在完整定界正文时消费 verb；否则继续普通控制词词法。 */
        private int verbatimEnd() {
            if (!source.startsWith("verb", i)) return -1;
            int delimiter = i + 4;
            boolean starred = delimiter < source.length() && source.charAt(delimiter) == '*';
            if (starred) delimiter++;
            if (delimiter == source.length()) return -1;
            char ch = source.charAt(delimiter);
            if (!starred && (ch == '*' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z'))
                return -1;
            int end = source.indexOf(ch, delimiter + 1);
            return end < 0 ? -1 : end + 1;
        }

        private void controlSpace() {
            // KaTeX 的控制空格允许一次换行及其相邻空格。
            if (source.charAt(i) == '\n') i++;
            else {
                while (i < source.length() && " \r\t".indexOf(source.charAt(i)) >= 0) i++;
                if (i < source.length() && source.charAt(i) == '\n') i++;
            }
            while (i < source.length() && " \r\t".indexOf(source.charAt(i)) >= 0) i++;
        }

        /** 一个 Unicode 标量及紧随其后的组合符是同一个数学 token。 */
        private String unicodeAtom(int start) {
            int cp = source.codePointAt(i);
            if (cp < 32
                    || cp >= 0xd800 && cp <= 0xdfff
                    || cp >= 0xe000 && cp <= 0xf8ff
                    || cp == 0x2028
                    || cp == 0x2029)
                throw fail(source, start, i + Character.charCount(cp), "非法数学字符");
            i += Character.charCount(cp);
            while (i < source.length()
                    && source.charAt(i) >= 0x0300
                    && source.charAt(i) <= 0x036f) i++;
            return source.substring(start, i);
        }
    }

    private static Failure fail(String s, int start, int end, String message) {
        return new Failure(new Token("", start, end, s), message);
    }
}
