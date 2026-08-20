package club.heiqi.uilib.font.latex.node;

import club.heiqi.uilib.font.latex.LatexNode;

/**
 * 可伸缩括号节点 {@code \left<delim>...\right<delim>}。
 *
 * <p>delim 为显示字符（如 "("、"["、"\{"、"|"）；"." 表示无形（null）。</p>
 */
public final class LatexLeftRight extends LatexNode {

    private final String leftDelimiter;
    private final LatexNode content;
    private final String rightDelimiter;

    /**
     * 创建伸缩括号节点。
     *
     * @param leftDelimiter  左定界符显示字符（null 表示无形）
     * @param content        括号内容
     * @param rightDelimiter 右定界符显示字符（null 表示无形）
     */
    public LatexLeftRight(String leftDelimiter, LatexNode content, String rightDelimiter) {
        super(Kind.LEFT_RIGHT);
        if (content == null) {
            throw new IllegalArgumentException("content 不能为空");
        }
        this.leftDelimiter = leftDelimiter;
        this.content = content;
        this.rightDelimiter = rightDelimiter;
    }

    /** @return 左定界符；null 表示无形 */
    public String getLeftDelimiter() {
        return leftDelimiter;
    }

    public LatexNode getContent() {
        return content;
    }

    /** @return 右定界符；null 表示无形 */
    public String getRightDelimiter() {
        return rightDelimiter;
    }

    @Override
    public String toString() {
        return "LeftRight(" + leftDelimiter + ", " + content + ", " + rightDelimiter + ")";
    }
}
