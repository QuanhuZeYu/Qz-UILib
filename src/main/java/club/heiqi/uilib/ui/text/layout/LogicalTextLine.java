package club.heiqi.uilib.ui.text.layout;

/**
 * 逻辑文本行。
 *
 * <p>表示原始文本中以 {@code \n} 分隔的一行，记录其在整篇文本中的字符区间与行内容。
 * 该类是 {@code DocumentTextAreaControl} / {@code DocumentCodeEditorControl} 共享的逻辑行模型，
 * 替代两个控件各自维护的私有 {@code LogicalLine}。视觉行（软换行后的显示行）由
 * {@link TextLayoutEngine} 在逻辑行基础上派生。</p>
 */
public final class LogicalTextLine {

    private final int startIndex;
    private final int endIndex;
    private final String text;

    /**
     * 创建逻辑文本行。
     *
     * @param startIndex 行首字符在整篇文本中的索引（含）
     * @param endIndex 行尾字符在整篇文本中的索引（不含换行符）
     * @param text 行内容（不含换行符）
     */
    public LogicalTextLine(int startIndex, int endIndex, String text) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.text = text == null ? "" : text;
    }

    /**
     * 获取行首字符索引。
     *
     * @return 行首字符索引
     */
    public int getStartIndex() {
        return startIndex;
    }

    /**
     * 获取行尾字符索引。
     *
     * @return 行尾字符索引
     */
    public int getEndIndex() {
        return endIndex;
    }

    /**
     * 获取行内容。
     *
     * @return 行内容
     */
    public String getText() {
        return text;
    }
}
