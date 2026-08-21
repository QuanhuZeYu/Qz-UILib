package club.heiqi.uilib.font.latex.node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.latex.LatexNode;

/**
 * 可伸缩括号节点 {@code \left<delim>...\right<delim>}。
 *
 * <p>delim 为显示字符（如 "("、"["、"\{"、"|"）；"." 表示无形（null）。</p>
 */
public final class LatexLeftRight extends LatexNode {

    private final String leftDelimiter;
    /** 内容段（\middle 分段，≥1 段；每段为节点列表的 Group 包装）。 */
    private final List<LatexNode> parts;
    /** 段间中间定界符（size = parts.size() − 1；元素可为 null 表示无形）。 */
    private final List<String> middleDelimiters;
    private final String rightDelimiter;

    /**
     * 创建伸缩括号节点（无 \middle）。
     *
     * @param leftDelimiter  左定界符显示字符（null 表示无形）
     * @param content        括号内容
     * @param rightDelimiter 右定界符显示字符（null 表示无形）
     */
    public LatexLeftRight(String leftDelimiter, LatexNode content, String rightDelimiter) {
        this(leftDelimiter, Collections.<LatexNode>singletonList(content),
                Collections.<String>emptyList(), rightDelimiter);
    }

    /**
     * 创建伸缩括号节点（\left...\middle...\right）。
     *
     * @param leftDelimiter     左定界符显示字符（null 表示无形）
     * @param parts             内容段（≥1；每段一个节点，通常为 Group）
     * @param middleDelimiters  段间定界符（size = parts.size() − 1；元素可为 null）
     * @param rightDelimiter    右定界符显示字符（null 表示无形）
     */
    public LatexLeftRight(String leftDelimiter, List<LatexNode> parts, List<String> middleDelimiters,
            String rightDelimiter) {
        super(Kind.LEFT_RIGHT);
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("parts 不能为空");
        }
        if (middleDelimiters != null && middleDelimiters.size() != parts.size() - 1) {
            throw new IllegalArgumentException("middleDelimiters 数量应为 parts.size() − 1");
        }
        this.leftDelimiter = leftDelimiter;
        this.parts = Collections.unmodifiableList(new ArrayList<LatexNode>(parts));
        this.middleDelimiters = middleDelimiters == null || middleDelimiters.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(middleDelimiters));
        this.rightDelimiter = rightDelimiter;
    }

    /** @return 左定界符；null 表示无形 */
    public String getLeftDelimiter() {
        return leftDelimiter;
    }

    /** @return 内容首段（无 \middle 时即全部内容；兼容旧语义） */
    public LatexNode getContent() {
        return parts.get(0);
    }

    /** @return 内容段（\middle 分段，≥1） */
    public List<LatexNode> getParts() {
        return parts;
    }

    /** @return 段间中间定界符（size = parts.size() − 1；元素可为 null） */
    public List<String> getMiddleDelimiters() {
        return middleDelimiters;
    }

    /** @return 右定界符；null 表示无形 */
    public String getRightDelimiter() {
        return rightDelimiter;
    }

    @Override
    public String toString() {
        return "LeftRight(" + leftDelimiter + ", parts=" + parts.size() + ", " + rightDelimiter + ")";
    }
}
