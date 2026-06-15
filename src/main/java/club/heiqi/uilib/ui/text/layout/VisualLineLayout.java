package club.heiqi.uilib.ui.text.layout;

/**
 * 单条视觉行的布局快照。
 *
 * <p>一条逻辑行（以 {@code \n} 分隔的原始行）在按可用宽度软换行后，可能拆成多条视觉行。
 * 每条视觉行都对应一个 {@code VisualLineLayout}，记录它在文档坐标系中的纵向位置、在逻辑文本中的
 * 字符区间，以及该视觉行内每个码点边界的水平像素位置（前缀宽度向量 {@code boundaryXs}）。</p>
 *
 * <p>该类替代原先散落在 {@code DocumentTextAreaControl} 内的私有 {@code VisualLineMetrics}，
 * 成为 {@code DocumentTextAreaControl} / {@code DocumentCodeEditorControl} 共享的不可变值对象，
 * 由 {@link TextLayoutEngine} 统一构造。</p>
 *
 * <p>坐标约定：{@link #getVisualTop()} 与 {@link #resolveBoundaryX(int)} 均为相对文本内容盒原点的
 * 文档坐标，绘制时由控件再叠加视口/滚动偏移换算到屏幕坐标。</p>
 */
public final class VisualLineLayout {

    private final int logicalLineIndex;
    private final int visualTop;
    private final int visualStartIndex;
    private final int visualEndIndex;
    private final String text;
    private final int[] charOffsets;
    private final int[] boundaryXs;

    /**
     * 创建视觉行布局快照。
     *
     * @param logicalLineIndex 所属逻辑行索引
     * @param visualTop 该视觉行相对文本内容盒原点的纵向像素位置
     * @param visualStartIndex 该视觉行起始字符在整篇文本中的索引（含）
     * @param visualEndIndex 该视觉行结束字符在整篇文本中的索引（不含或行尾）
     * @param text 该视觉行的可见文本
     * @param charOffsets 码点边界在 {@code text} 内的字符偏移数组，长度为码点数 + 1
     * @param boundaryXs 与 {@code charOffsets} 对齐的前缀宽度向量，元素 i 等于前 i 个码点的显示宽度
     */
    public VisualLineLayout(int logicalLineIndex, int visualTop, int visualStartIndex, int visualEndIndex,
            String text, int[] charOffsets, int[] boundaryXs) {
        this.logicalLineIndex = logicalLineIndex;
        this.visualTop = visualTop;
        this.visualStartIndex = visualStartIndex;
        this.visualEndIndex = visualEndIndex;
        this.text = text == null ? "" : text;
        this.charOffsets = charOffsets;
        this.boundaryXs = boundaryXs;
    }

    /**
     * 获取所属逻辑行索引。
     *
     * @return 逻辑行索引
     */
    public int getLogicalLineIndex() {
        return logicalLineIndex;
    }

    /**
     * 获取该视觉行相对文本内容盒原点的纵向像素位置。
     *
     * @return 纵向像素位置
     */
    public int getVisualTop() {
        return visualTop;
    }

    /**
     * 获取该视觉行起始字符在整篇文本中的索引。
     *
     * @return 起始字符索引
     */
    public int getVisualStartIndex() {
        return visualStartIndex;
    }

    /**
     * 获取该视觉行结束字符在整篇文本中的索引。
     *
     * @return 结束字符索引
     */
    public int getVisualEndIndex() {
        return visualEndIndex;
    }

    /**
     * 获取该视觉行的可见文本。
     *
     * @return 可见文本
     */
    public String getText() {
        return text;
    }

    /**
     * 判断给定全局 caret 索引是否落在本视觉行内。
     *
     * @param targetCaretIndex 全局 caret 索引
     * @return 命中本视觉行返回 {@code true}
     */
    public boolean containsCaretIndex(int targetCaretIndex) {
        if (visualStartIndex == visualEndIndex) {
            return targetCaretIndex == visualStartIndex;
        }
        return targetCaretIndex >= visualStartIndex && targetCaretIndex < visualEndIndex;
    }

    /**
     * 解析本视觉行内指定局部字符偏移对应的水平像素位置。
     *
     * @param localCharOffset 相对本视觉行起点的局部字符偏移
     * @return 相对文本内容盒原点的水平像素位置
     */
    public int resolveBoundaryX(int localCharOffset) {
        int safeOffset = Math.max(0, Math.min(localCharOffset, text.length()));
        for (int index = 0; index < charOffsets.length; index++) {
            if (charOffsets[index] == safeOffset) {
                return boundaryXs[index];
            }
        }
        return boundaryXs[boundaryXs.length - 1];
    }

    /**
     * 在本视觉行内寻找与给定水平像素位置最近的 caret 索引。
     *
     * @param localX 相对文本内容盒原点的水平像素位置
     * @return 最接近的全局 caret 索引
     */
    public int resolveClosestCaretIndex(int localX) {
        int closestIndex = 0;
        int closestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < boundaryXs.length; index++) {
            int distance = Math.abs(boundaryXs[index] - localX);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = index;
            }
        }
        return visualStartIndex + charOffsets[closestIndex];
    }
}
