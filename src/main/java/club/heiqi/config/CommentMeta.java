package club.heiqi.config;

/**
 * YAML 注释元数据，与 SnakeYAML 类型解耦。
 *
 * <p>用于 {@link ConfigNode} 携带注释信息，支持带 {@code #} 注释的 YAML 文件
 * round-trip 保真。本类不引用任何 {@code org.yaml.snakeyaml.*} 类型，
 * 避免 shadow relocate 后的类型泄漏；SnakeYAML 的 {@code CommentLine}
 * 仅在 {@link YamlConfigLoader}/{@link YamlConfigWriter} 内部与本类互相转换。</p>
 *
 * <p>多行块注释以单条 {@code CommentMeta} 表示，{@link #value} 用 {@code \n}
 * 连接各行；空行在 {@code value} 中表现为空字符串行（即 {@code "\n\n"} 中间为空行），
 * 写回时由 Writer 还原为 {@link CommentType#BLANK_LINE}。</p>
 */
public final class CommentMeta {

    /** 注释类型 */
    public enum CommentType {
        /** 块注释（节点上方独立行，{@code # xxx}） */
        BLOCK,
        /** 内联注释（节点同行 {@code # xxx}） */
        IN_LINE,
        /** 空行 */
        BLANK_LINE;
    }

    private final CommentType type;
    /** 注释文本（不含 {@code #} 前缀和换行；多行块注释用 {@code \n} 连接，空行为空字符串） */
    private final String value;
    /** 列位置（0-based，保留用，目前 Writer 不依赖具体列号） */
    private final int column;

    /**
     * 构造注释元数据。
     *
     * @param type   注释类型
     * @param value  注释文本（不含 {@code #} 前缀）
     * @param column 列位置
     */
    public CommentMeta(CommentType type, String value, int column) {
        this.type = type;
        this.value = value;
        this.column = column;
    }

    /** 块注释工厂 */
    public static CommentMeta block(String value, int column) {
        return new CommentMeta(CommentType.BLOCK, value, column);
    }

    /** 内联注释工厂 */
    public static CommentMeta inline(String value, int column) {
        return new CommentMeta(CommentType.IN_LINE, value, column);
    }

    /** 空行工厂 */
    public static CommentMeta blankLine(int column) {
        return new CommentMeta(CommentType.BLANK_LINE, null, column);
    }

    public CommentType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getColumn() {
        return column;
    }
}
