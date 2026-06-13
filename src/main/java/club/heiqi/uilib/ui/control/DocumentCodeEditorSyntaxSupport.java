package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 源码编辑器语法支持抽象。
 *
 * <p>定义 {@link Language} 枚举、{@link TokenKind}、{@link SyntaxToken}、{@link SyntaxResult}
 * 几个不可变数据载体，以及 {@link SyntaxSupport} 接口与 JSON/YAML/PLAIN 三种内置实现。</p>
 *
 * <p>本类是「尽力而为」的轻量词法分析器：识别常见的 key/string/number/boolean/null/punct/comment
 * token 用于高亮显示，遇到无法识别的字符序列时把对应行号加入 {@link SyntaxResult#errorLines}，
 * 但仍返回已识别的 token 列表，<b>不抛异常</b>。完整语法校验由调用方自行负责（5-C 的
 * RawEditorPropertyBinding 会调用真正的 JSON/YAML 解析器做最终校验）。</p>
 */
public final class DocumentCodeEditorSyntaxSupport {

    private DocumentCodeEditorSyntaxSupport() {
    }

    /**
     * 获取指定语言的语法支持实例。
     *
     * @param language 语言枚举；null 视为 {@link Language#PLAIN}
     * @return 语法支持实例，永不返回 null
     */
    public static SyntaxSupport forLanguage(Language language) {
        if (language == null) {
            return new PlainSyntaxSupport();
        }
        switch (language) {
            case JSON:
                return new JsonSyntaxSupport();
            case YAML:
                return new YamlSyntaxSupport();
            case PLAIN:
            default:
                return new PlainSyntaxSupport();
        }
    }

    /**
     * 源码编辑器支持的语言枚举。
     */
    public enum Language {
        /** JSON 语言。 */
        JSON,
        /** YAML 语言。 */
        YAML,
        /** 纯文本，不做语法分析。 */
        PLAIN
    }

    /**
     * 语法 token 类别，用于染色映射。
     */
    public enum TokenKind {
        /** 字典 key（如 JSON 的 "key" 或 YAML 的 key:）。 */
        KEY,
        /** 字符串字面量。 */
        STRING,
        /** 数字字面量。 */
        NUMBER,
        /** 布尔字面量 true/false。 */
        BOOLEAN,
        /** null 字面量。 */
        NULL,
        /** 标点符号：{ } [ ] : , - 等。 */
        PUNCT,
        /** 注释（YAML 的 #...）。 */
        COMMENT,
        /** 其他普通文本，未命中任何词法。 */
        PLAIN
    }

    /**
     * 不可变语法 token。
     */
    public static final class SyntaxToken {

        private final int lineIndex;
        private final int startColumn;
        private final int length;
        private final TokenKind kind;

        private SyntaxToken(int lineIndex, int startColumn, int length, TokenKind kind) {
            this.lineIndex = lineIndex;
            this.startColumn = startColumn;
            this.length = length;
            this.kind = kind;
        }

        /**
         * 创建语法 token。
         *
         * @param lineIndex 行索引（0-based）
         * @param startColumn 起始列（0-based，按 char 计数）
         * @param length token 长度（按 char 计数）
         * @param kind token 类别
         * @return 不可变 token
         */
        public static SyntaxToken of(int lineIndex, int startColumn, int length, TokenKind kind) {
            return new SyntaxToken(lineIndex, Math.max(0, startColumn), Math.max(0, length),
                    kind == null ? TokenKind.PLAIN : kind);
        }

        /** 返回行索引（0-based）。 */
        public int getLineIndex() {
            return lineIndex;
        }

        /** 返回起始列（0-based）。 */
        public int getStartColumn() {
            return startColumn;
        }

        /** 返回 token 长度。 */
        public int getLength() {
            return length;
        }

        /** 返回 token 类别。 */
        public TokenKind getKind() {
            return kind;
        }
    }

    /**
     * 不可变语法分析结果。
     */
    public static final class SyntaxResult {

        private final List<SyntaxToken> tokens;
        private final Set<Integer> errorLines;
        private final String errorMessage;

        /** 空结果常量，无 token、无错误行、无错误文案。 */
        public static final SyntaxResult EMPTY = new SyntaxResult(Collections.<SyntaxToken>emptyList(),
                Collections.<Integer>emptySet(), "");

        private SyntaxResult(List<SyntaxToken> tokens, Set<Integer> errorLines, String errorMessage) {
            this.tokens = Collections.unmodifiableList(new ArrayList<SyntaxToken>(tokens));
            this.errorLines = Collections.unmodifiableSet(new LinkedHashSet<Integer>(errorLines));
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        /**
         * 创建语法分析结果。
         *
         * @param tokens token 列表
         * @param errorLines 错误行集合
         * @param errorMessage 错误文案
         * @return 不可变结果
         */
        public static SyntaxResult of(List<SyntaxToken> tokens, Set<Integer> errorLines, String errorMessage) {
            return new SyntaxResult(tokens == null ? Collections.<SyntaxToken>emptyList() : tokens,
                    errorLines == null ? Collections.<Integer>emptySet() : errorLines, errorMessage);
        }

        /** 返回 token 列表（不可变）。 */
        public List<SyntaxToken> getTokens() {
            return tokens;
        }

        /** 返回错误行集合（不可变，按插入顺序）。 */
        public Set<Integer> getErrorLines() {
            return errorLines;
        }

        /** 返回错误文案，可能为空串。 */
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * 语法支持接口。
     */
    public interface SyntaxSupport {

        /**
         * 分析文本，返回 token 与错误行集合，<b>不抛异常</b>。
         *
         * @param text 文本；为 null 时视为空串
         * @return 分析结果，永不返回 null
         */
        SyntaxResult analyze(String text);

        /**
         * 返回当前实现对应的语言枚举。
         *
         * @return 语言枚举
         */
        Language getLanguage();
    }

    /** PLAIN 语言实现：不做任何词法分析，返回空 token。 */
    private static final class PlainSyntaxSupport implements SyntaxSupport {

        @Override
        public SyntaxResult analyze(String text) {
            return SyntaxResult.EMPTY;
        }

        @Override
        public Language getLanguage() {
            return Language.PLAIN;
        }
    }

    /** JSON 轻量词法分析实现。 */
    private static final class JsonSyntaxSupport implements SyntaxSupport {

        @Override
        public SyntaxResult analyze(String text) {
            return new LineScanner(text).scanJson();
        }

        @Override
        public Language getLanguage() {
            return Language.JSON;
        }
    }

    /** YAML 轻量词法分析实现。 */
    private static final class YamlSyntaxSupport implements SyntaxSupport {

        @Override
        public SyntaxResult analyze(String text) {
            return new LineScanner(text).scanYaml();
        }

        @Override
        public Language getLanguage() {
            return Language.YAML;
        }
    }

    /**
     * 逐行扫描器，复用 JSON 与 YAML 词法逻辑。
     *
     * <p>不支持多行字符串（JSON/YAML 都有，但完整支持需要状态机跨行；这里仅做单行内的近似识别，
     * 用于染色提示），因此跨行字符串会被识别为「字符串开始/结束」两段 STRING token，足够
     * 高亮使用，但完整校验仍依赖真正的解析器。</p>
     */
    private static final class LineScanner {

        private final String[] lines;
        private final List<SyntaxToken> tokens = new ArrayList<SyntaxToken>();
        private final Set<Integer> errorLines = new LinkedHashSet<Integer>();

        LineScanner(String text) {
            String safe = text == null ? "" : text;
            // 统一按 \n 切分；\r\n 由 split 自然去掉 \r
            String normalized = safe.replace("\r\n", "\n").replace('\r', '\n');
            lines = normalized.split("\n", -1);
        }

        SyntaxResult scanJson() {
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                scanJsonLine(lineIndex, lines[lineIndex]);
            }
            return SyntaxResult.of(tokens, errorLines, "");
        }

        SyntaxResult scanYaml() {
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                scanYamlLine(lineIndex, lines[lineIndex]);
            }
            return SyntaxResult.of(tokens, errorLines, "");
        }

        private void scanJsonLine(int lineIndex, String line) {
            int column = 0;
            int length = line.length();
            while (column < length) {
                char ch = line.charAt(column);
                if (Character.isWhitespace(ch)) {
                    column++;
                    continue;
                }
                if (ch == '"') {
                    int end = findJsonStringEnd(line, column);
                    if (end < 0) {
                        // 字符串未闭合：标记错误行，记录到行尾
                        tokens.add(SyntaxToken.of(lineIndex, column, length - column, TokenKind.STRING));
                        errorLines.add(lineIndex);
                        return;
                    }
                    // 判断 key 还是 string：紧跟非空白字符为 ':' 则为 key
                    int searchFrom = end + 1;
                    TokenKind kind = nextNonWhitespaceIs(line, searchFrom, ':') ? TokenKind.KEY : TokenKind.STRING;
                    tokens.add(SyntaxToken.of(lineIndex, column, searchFrom - column, kind));
                    column = searchFrom;
                    continue;
                }
                if (ch == '{' || ch == '}' || ch == '[' || ch == ']' || ch == ':' || ch == ',') {
                    tokens.add(SyntaxToken.of(lineIndex, column, 1, TokenKind.PUNCT));
                    column++;
                    continue;
                }
                int wordEnd = readWord(line, column);
                if (wordEnd <= column) {
                    // readWord 未匹配任何字符：消费 1 字符避免死循环
                    tokens.add(SyntaxToken.of(lineIndex, column, 1, TokenKind.PLAIN));
                    errorLines.add(lineIndex);
                    column++;
                    continue;
                }
                String word = line.substring(column, wordEnd);
                String lower = word.toLowerCase(Locale.ENGLISH);
                if ("true".equals(lower) || "false".equals(lower)) {
                    tokens.add(SyntaxToken.of(lineIndex, column, word.length(), TokenKind.BOOLEAN));
                } else if ("null".equals(lower)) {
                    tokens.add(SyntaxToken.of(lineIndex, column, word.length(), TokenKind.NULL));
                } else if (isNumberLike(word)) {
                    tokens.add(SyntaxToken.of(lineIndex, column, word.length(), TokenKind.NUMBER));
                } else if (!word.isEmpty()) {
                    // JSON 不应该出现裸单词，标记错误行但仍然记录 token
                    tokens.add(SyntaxToken.of(lineIndex, column, word.length(), TokenKind.PLAIN));
                    errorLines.add(lineIndex);
                }
                column = wordEnd;
            }
        }

        private void scanYamlLine(int lineIndex, String line) {
            int length = line.length();
            int column = 0;
            // 跳过前导空格（YAML 用缩进表示层级，不染色）
            while (column < length && (line.charAt(column) == ' ' || line.charAt(column) == '\t')) {
                column++;
            }
            if (column >= length) {
                return;
            }
            char ch = line.charAt(column);
            // 注释
            if (ch == '#') {
                tokens.add(SyntaxToken.of(lineIndex, column, length - column, TokenKind.COMMENT));
                return;
            }
            // 列表项前导 -
            if (ch == '-' && (column + 1 >= length || Character.isWhitespace(line.charAt(column + 1)))) {
                tokens.add(SyntaxToken.of(lineIndex, column, 1, TokenKind.PUNCT));
                column++;
                while (column < length && (line.charAt(column) == ' ' || line.charAt(column) == '\t')) {
                    column++;
                }
                if (column >= length) {
                    return;
                }
                ch = line.charAt(column);
            }
            // 试图识别 key: value 形式
            int keyEnd = findYamlKeyColon(line, column);
            if (keyEnd > column) {
                tokens.add(SyntaxToken.of(lineIndex, column, keyEnd - column, TokenKind.KEY));
                int colonIndex = keyEnd;
                tokens.add(SyntaxToken.of(lineIndex, colonIndex, 1, TokenKind.PUNCT));
                column = colonIndex + 1;
                while (column < length && (line.charAt(column) == ' ' || line.charAt(column) == '\t')) {
                    column++;
                }
            }
            // 剩余作为值词法
            scanYamlValue(lineIndex, line, column);
        }

        private void scanYamlValue(int lineIndex, String line, int startColumn) {
            int length = line.length();
            int column = startColumn;
            while (column < length) {
                char ch = line.charAt(column);
                if (Character.isWhitespace(ch)) {
                    column++;
                    continue;
                }
                if (ch == '#') {
                    tokens.add(SyntaxToken.of(lineIndex, column, length - column, TokenKind.COMMENT));
                    return;
                }
                if (ch == '"' || ch == '\'') {
                    int end = findQuotedEnd(line, column, ch);
                    if (end < 0) {
                        tokens.add(SyntaxToken.of(lineIndex, column, length - column, TokenKind.STRING));
                        return;
                    }
                    tokens.add(SyntaxToken.of(lineIndex, column, end + 1 - column, TokenKind.STRING));
                    column = end + 1;
                    continue;
                }
                if (ch == '{' || ch == '}' || ch == '[' || ch == ']' || ch == ',') {
                    tokens.add(SyntaxToken.of(lineIndex, column, 1, TokenKind.PUNCT));
                    column++;
                    continue;
                }
                if (ch == ':' && (column + 1 >= length || Character.isWhitespace(line.charAt(column + 1)))) {
                    tokens.add(SyntaxToken.of(lineIndex, column, 1, TokenKind.PUNCT));
                    column++;
                    continue;
                }
                int wordEnd = readYamlScalarEnd(line, column);
                if (wordEnd <= column) {
                    // 防御：未匹配任何字符时消费 1 字符避免死循环
                    tokens.add(SyntaxToken.of(lineIndex, column, 1, TokenKind.PLAIN));
                    column++;
                    continue;
                }
                String word = line.substring(column, wordEnd);
                String lower = word.toLowerCase(Locale.ENGLISH);
                if ("true".equals(lower) || "false".equals(lower)) {
                    tokens.add(SyntaxToken.of(lineIndex, column, word.length(), TokenKind.BOOLEAN));
                } else if ("null".equals(lower) || "~".equals(word)) {
                    tokens.add(SyntaxToken.of(lineIndex, column, word.length(), TokenKind.NULL));
                } else if (isNumberLike(word)) {
                    tokens.add(SyntaxToken.of(lineIndex, column, word.length(), TokenKind.NUMBER));
                } else if (!word.isEmpty()) {
                    tokens.add(SyntaxToken.of(lineIndex, column, word.length(), TokenKind.STRING));
                }
                column = wordEnd;
            }
        }

        /** 寻找 JSON 字符串结束引号的位置（含转义），未找到返回 -1。 */
        private static int findJsonStringEnd(String line, int startQuote) {
            int index = startQuote + 1;
            while (index < line.length()) {
                char ch = line.charAt(index);
                if (ch == '\\') {
                    index += 2;
                    continue;
                }
                if (ch == '"') {
                    return index;
                }
                index++;
            }
            return -1;
        }

        /** 寻找 YAML 引号字符串结束位置，未找到返回 -1。 */
        private static int findQuotedEnd(String line, int startQuote, char quote) {
            int index = startQuote + 1;
            while (index < line.length()) {
                char ch = line.charAt(index);
                if (quote == '"' && ch == '\\') {
                    index += 2;
                    continue;
                }
                if (ch == quote) {
                    return index;
                }
                index++;
            }
            return -1;
        }

        /** 判断 line 从 fromIndex 起第一个非空白字符是否为 expected。 */
        private static boolean nextNonWhitespaceIs(String line, int fromIndex, char expected) {
            for (int index = fromIndex; index < line.length(); index++) {
                char ch = line.charAt(index);
                if (Character.isWhitespace(ch)) {
                    continue;
                }
                return ch == expected;
            }
            return false;
        }

        /** 读 JSON 裸单词（字母、数字、+、-、.、_），返回结束列。 */
        private static int readWord(String line, int start) {
            int index = start;
            while (index < line.length()) {
                char ch = line.charAt(index);
                if (Character.isLetterOrDigit(ch) || ch == '+' || ch == '-' || ch == '.' || ch == '_') {
                    index++;
                    continue;
                }
                break;
            }
            return index;
        }

        /** 读 YAML scalar 结束位置：到空白、#、,、:、[、]、{、} 为止。 */
        private static int readYamlScalarEnd(String line, int start) {
            int index = start;
            while (index < line.length()) {
                char ch = line.charAt(index);
                if (Character.isWhitespace(ch) || ch == ',' || ch == '#' || ch == ':' || ch == '[' || ch == ']'
                        || ch == '{' || ch == '}') {
                    break;
                }
                index++;
            }
            return index;
        }

        /** 简易数字判断：支持整数、小数、负数、科学计数法。 */
        private static boolean isNumberLike(String word) {
            if (word == null || word.isEmpty()) {
                return false;
            }
            boolean seenDigit = false;
            boolean seenDot = false;
            boolean seenExp = false;
            int begin = 0;
            if (word.charAt(0) == '+' || word.charAt(0) == '-') {
                begin = 1;
            }
            for (int index = begin; index < word.length(); index++) {
                char ch = word.charAt(index);
                if (ch >= '0' && ch <= '9') {
                    seenDigit = true;
                    continue;
                }
                if (ch == '.' && !seenDot && !seenExp) {
                    seenDot = true;
                    continue;
                }
                if ((ch == 'e' || ch == 'E') && seenDigit && !seenExp) {
                    seenExp = true;
                    if (index + 1 < word.length()
                            && (word.charAt(index + 1) == '+' || word.charAt(index + 1) == '-')) {
                        index++;
                    }
                    continue;
                }
                return false;
            }
            return seenDigit;
        }

        /** 在 YAML 行中查找 key 后的冒号位置，要求冒号后是空白或行尾。未找到返回 startColumn。 */
        private static int findYamlKeyColon(String line, int startColumn) {
            int index = startColumn;
            while (index < line.length()) {
                char ch = line.charAt(index);
                if (ch == ':') {
                    if (index + 1 >= line.length() || Character.isWhitespace(line.charAt(index + 1))) {
                        return index;
                    }
                }
                if (ch == '#' || ch == ',' || ch == '[' || ch == ']' || ch == '{' || ch == '}') {
                    return startColumn;
                }
                index++;
            }
            return startColumn;
        }
    }
}
