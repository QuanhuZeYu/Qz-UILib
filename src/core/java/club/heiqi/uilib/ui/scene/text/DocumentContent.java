package club.heiqi.uilib.ui.scene.text;

import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 共同文档的不可变块声明；结构与来源不持有节点、资源或测量。 */
public final class DocumentContent {
    public static final class Block {
        public enum Kind {
            PARAGRAPH,
            HEADING,
            QUOTE,
            LIST,
            LIST_ITEM,
            CODE,
            RULE,
            MATH
        }

        public final Kind kind;
        public final TextContent paragraph;
        public final MathContent math;
        public final MathStyle style;
        public final int headingLevel, orderedStart, sourceStart, sourceEnd;
        public final boolean ordered, tight;
        public final String codeInfo;
        private final List<Block> children;

        private Block(
                Kind kind,
                TextContent paragraph,
                MathContent math,
                MathStyle style,
                List<Block> children,
                int headingLevel,
                boolean ordered,
                int orderedStart,
                boolean tight,
                String codeInfo,
                int sourceStart,
                int sourceEnd) {
            if (sourceStart < 0 || sourceEnd < sourceStart)
                throw new IllegalArgumentException("无效块来源");
            this.kind = kind;
            this.paragraph = paragraph;
            this.math = math;
            this.style = style;
            this.children = copy(children);
            this.headingLevel = headingLevel;
            this.ordered = ordered;
            this.orderedStart = orderedStart;
            this.tight = tight;
            this.codeInfo = codeInfo;
            this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd;
        }

        private static Block text(
                Kind kind, TextContent text, int level, String info, int start, int end) {
            return new Block(
                    kind,
                    Objects.requireNonNull(text, "text"),
                    null,
                    null,
                    Collections.<Block>emptyList(),
                    level,
                    false,
                    1,
                    false,
                    info,
                    start,
                    end);
        }

        public static Block paragraph(TextContent text) {
            return paragraph(text, 0, 0);
        }

        public static Block paragraph(TextContent text, int start, int end) {
            return text(Kind.PARAGRAPH, text, 0, "", start, end);
        }

        public static Block heading(TextContent text, int level, int start, int end) {
            if (level < 1 || level > 6) throw new IllegalArgumentException("标题层级必须为1至6");
            return text(Kind.HEADING, text, level, "", start, end);
        }

        public static Block code(TextContent text, String info, int start, int end) {
            return text(Kind.CODE, text, 0, Objects.requireNonNull(info, "info"), start, end);
        }

        private static Block container(
                Kind kind,
                List<Block> children,
                boolean ordered,
                int number,
                boolean tight,
                int start,
                int end) {
            return new Block(
                    kind, null, null, null, children, 0, ordered, number, tight, "", start, end);
        }

        public static Block quote(List<Block> children, int start, int end) {
            return container(Kind.QUOTE, children, false, 1, false, start, end);
        }

        public static Block list(
                List<Block> children,
                boolean ordered,
                int orderedStart,
                boolean tight,
                int start,
                int end) {
            return container(Kind.LIST, children, ordered, orderedStart, tight, start, end);
        }

        public static Block listItem(List<Block> children, int start, int end) {
            return container(Kind.LIST_ITEM, children, false, 1, false, start, end);
        }

        public static Block rule(int start, int end) {
            return container(
                    Kind.RULE, Collections.<Block>emptyList(), false, 1, false, start, end);
        }

        public static Block formula(MathContent math, MathStyle style) {
            return formula(math, style, 0, 0);
        }

        public static Block formula(MathContent math, MathStyle style, int start, int end) {
            return new Block(
                    Kind.MATH,
                    null,
                    Objects.requireNonNull(math, "math"),
                    Objects.requireNonNull(style, "style"),
                    Collections.<Block>emptyList(),
                    0,
                    false,
                    1,
                    false,
                    "",
                    start,
                    end);
        }

        public List<Block> children() {
            return children;
        }
    }

    private final List<Block> blocks;
    private final TextInput input;
    private final List<TextLayout.Diagnostic> diagnostics;

    private static List<Block> copy(List<Block> blocks) {
        ArrayList<Block> copy = new ArrayList<>(Objects.requireNonNull(blocks, "blocks"));
        for (Block block : copy) Objects.requireNonNull(block, "block");
        return Collections.unmodifiableList(copy);
    }

    public DocumentContent(List<Block> blocks) {
        this(blocks, null, Collections.<TextLayout.Diagnostic>emptyList());
    }

    /** input 为入口原声明；手工组成且没有共同源码的文档可为 null。 */
    public DocumentContent(
            List<Block> blocks, TextInput input, List<TextLayout.Diagnostic> diagnostics) {
        this.blocks = copy(blocks);
        this.input = input;
        ArrayList<TextLayout.Diagnostic> copy =
                new ArrayList<>(Objects.requireNonNull(diagnostics, "diagnostics"));
        for (TextLayout.Diagnostic d : copy) Objects.requireNonNull(d, "diagnostic");
        this.diagnostics = Collections.unmodifiableList(copy);
    }

    public static DocumentContent paragraph(TextContent text) {
        return new DocumentContent(Collections.singletonList(Block.paragraph(text)));
    }

    public List<Block> blocks() {
        return blocks;
    }

    public TextInput input() {
        return input;
    }

    public List<TextLayout.Diagnostic> diagnostics() {
        return diagnostics;
    }
}
