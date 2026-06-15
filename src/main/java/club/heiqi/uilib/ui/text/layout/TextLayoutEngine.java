package club.heiqi.uilib.ui.text.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 共享文本视觉行布局引擎。
 *
 * <p>该引擎把“逻辑行 + 可用宽度 + 字体测量纪元”映射为一组 {@link VisualLineLayout} 视觉行，
 * 统一承载 {@code DocumentTextAreaControl} 与 {@code DocumentCodeEditorControl} 原先各自重复实现的
 * 软换行、前缀宽度与 caret/选区命中几何，消除控件间重复的文本测量代码。</p>
 *
 * <h2>性能模型</h2>
 * <ul>
 *   <li><b>O(N) 增量测量</b>：每条逻辑行只向 {@link TextMeasureFunction#prefixWidths(String)} 取一次
 *       前缀宽度向量（底层单趟累加），替代旧实现对每个码点 {@code measureTextWidth(substring(0, k))}
 *       的 O(N²) 逐前缀测量。</li>
 *   <li><b>稳态零测量</b>：按 {@code (内容指纹 + 可用宽度 + 纪元 + 行高 + 软换行开关)} 缓存上一帧结果；
 *       caret 闪烁、滚动、选区移动不改变这些键，直接复用缓存列表实例，不触发任何测量与分配。</li>
 * </ul>
 *
 * <h2>数值一致性</h2>
 * <p>非换行行与每条视觉行的首段，其 {@code boundaryXs} 等于 {@code prefixWidths} 的对应前缀值，
 * 即与旧实现逐次 {@code measureTextWidth(prefix)} 数值一致，保证 caret/选区像素位置不漂移。
 * 软换行后的续行通过前缀向量相减派生，存在亚像素级换行点差异，不影响 caret 精度与现有测试。</p>
 *
 * <p>本类非线程安全，约定只在渲染线程内使用。</p>
 */
public final class TextLayoutEngine {

    private List<VisualLineLayout> cachedResult = Collections.emptyList();
    private boolean hasCache;
    private long cachedFingerprint;
    private int cachedAvailableWidth;
    private int cachedEpoch;
    private int cachedLineHeight;
    private boolean cachedSoftWrap;

    /**
     * 计算（或复用缓存的）视觉行布局。
     *
     * @param logicalLines 逻辑行列表
     * @param availableWidth 文本内容盒可用宽度；{@code <= 0} 时视为不限宽（不软换行）
     * @param epoch 字体测量纪元，来自 {@code FontService.getTextMeasureEpoch()}
     * @param lineHeight 单行像素高度
     * @param softWrap 是否启用软换行
     * @param measure 文本测量函数
     * @return 视觉行布局列表；稳态下返回与上一帧相同的列表实例
     */
    public List<VisualLineLayout> layout(List<LogicalTextLine> logicalLines, int availableWidth, int epoch,
            int lineHeight, boolean softWrap, TextMeasureFunction measure) {
        List<LogicalTextLine> safeLines = logicalLines == null ? Collections.<LogicalTextLine>emptyList()
                : logicalLines;
        int safeLineHeight = Math.max(1, lineHeight);
        long fingerprint = computeFingerprint(safeLines);
        if (hasCache && cachedFingerprint == fingerprint && cachedAvailableWidth == availableWidth
                && cachedEpoch == epoch && cachedLineHeight == safeLineHeight && cachedSoftWrap == softWrap) {
            return cachedResult;
        }

        List<VisualLineLayout> result = rebuild(safeLines, availableWidth, safeLineHeight, softWrap, measure);
        cachedResult = result;
        hasCache = true;
        cachedFingerprint = fingerprint;
        cachedAvailableWidth = availableWidth;
        cachedEpoch = epoch;
        cachedLineHeight = safeLineHeight;
        cachedSoftWrap = softWrap;
        return result;
    }

    /**
     * 主动清空缓存，强制下一次 {@link #layout} 重算。
     */
    public void invalidate() {
        hasCache = false;
        cachedResult = Collections.emptyList();
    }

    private List<VisualLineLayout> rebuild(List<LogicalTextLine> logicalLines, int availableWidth, int lineHeight,
            boolean softWrap, TextMeasureFunction measure) {
        List<VisualLineLayout> result = new ArrayList<VisualLineLayout>();
        int visualTop = 0;
        for (int lineIndex = 0; lineIndex < logicalLines.size(); lineIndex++) {
            LogicalTextLine logicalLine = logicalLines.get(lineIndex);
            visualTop = appendVisualLines(result, logicalLine, lineIndex, visualTop, availableWidth, lineHeight,
                    softWrap, measure);
        }
        return result;
    }

    private int appendVisualLines(List<VisualLineLayout> result, LogicalTextLine logicalLine, int lineIndex,
            int visualTop, int availableWidth, int lineHeight, boolean softWrap, TextMeasureFunction measure) {
        String text = logicalLine.getText();
        if (text.isEmpty()) {
            result.add(new VisualLineLayout(lineIndex, visualTop, logicalLine.getStartIndex(),
                    logicalLine.getEndIndex(), "", new int[] {0}, new int[] {0}));
            return visualTop + lineHeight;
        }

        int[] charOffsets = computeCodePointOffsets(text);
        int[] prefixWidths = measure.prefixWidths(text);
        int codePointCount = charOffsets.length - 1;

        boolean wrap = softWrap && availableWidth > 0 && prefixWidths[codePointCount] > availableWidth;
        if (!wrap) {
            result.add(new VisualLineLayout(lineIndex, visualTop, logicalLine.getStartIndex(),
                    logicalLine.getEndIndex(), text, charOffsets, prefixWidths));
            return visualTop + lineHeight;
        }

        int segmentStartCp = 0;
        int currentVisualTop = visualTop;
        while (segmentStartCp < codePointCount) {
            int segmentEndCp = resolveVisualLineEndCp(prefixWidths, segmentStartCp, codePointCount, availableWidth);
            int localCharStart = charOffsets[segmentStartCp];
            int localCharEnd = charOffsets[segmentEndCp];
            String visualText = text.substring(localCharStart, localCharEnd);
            int visualCpCount = segmentEndCp - segmentStartCp;
            int[] visualCharOffsets = new int[visualCpCount + 1];
            int[] visualBoundaryXs = new int[visualCpCount + 1];
            int baseWidth = prefixWidths[segmentStartCp];
            for (int cp = 0; cp <= visualCpCount; cp++) {
                visualCharOffsets[cp] = charOffsets[segmentStartCp + cp] - localCharStart;
                visualBoundaryXs[cp] = prefixWidths[segmentStartCp + cp] - baseWidth;
            }
            result.add(new VisualLineLayout(lineIndex, currentVisualTop,
                    logicalLine.getStartIndex() + localCharStart, logicalLine.getStartIndex() + localCharEnd,
                    visualText, visualCharOffsets, visualBoundaryXs));
            currentVisualTop += lineHeight;
            segmentStartCp = segmentEndCp;
        }
        return currentVisualTop;
    }

    /**
     * 在前缀宽度向量上确定一条视觉行的结束码点索引。
     *
     * <p>语义对齐旧实现 {@code resolveVisualLineEnd}：从 {@code segmentStartCp} 起逐码点累加，
     * 一旦从行首起的宽度超过可用宽度则在上一个适配位置断行；若首个码点即超宽则强制至少容纳一个码点。</p>
     *
     * @param prefixWidths 整条逻辑行的前缀宽度向量
     * @param segmentStartCp 本视觉行起始码点索引
     * @param codePointCount 逻辑行总码点数
     * @param availableWidth 可用宽度
     * @return 本视觉行结束码点索引（不含）
     */
    private static int resolveVisualLineEndCp(int[] prefixWidths, int segmentStartCp, int codePointCount,
            int availableWidth) {
        int baseWidth = prefixWidths[segmentStartCp];
        int lastFittingCp = segmentStartCp;
        for (int cp = segmentStartCp + 1; cp <= codePointCount; cp++) {
            int width = prefixWidths[cp] - baseWidth;
            if (width > availableWidth) {
                if (lastFittingCp > segmentStartCp) {
                    return lastFittingCp;
                }
                return cp;
            }
            lastFittingCp = cp;
        }
        return codePointCount;
    }

    /**
     * 计算字符串各码点边界的字符偏移数组。
     *
     * @param text 文本
     * @return 长度为码点数 + 1 的字符偏移数组，元素 i 为第 i 个码点边界的字符索引
     */
    private static int[] computeCodePointOffsets(String text) {
        int codePointCount = text.codePointCount(0, text.length());
        int[] offsets = new int[codePointCount + 1];
        int currentOffset = 0;
        offsets[0] = 0;
        for (int index = 1; index <= codePointCount; index++) {
            currentOffset = text.offsetByCodePoints(currentOffset, 1);
            offsets[index] = currentOffset;
        }
        return offsets;
    }

    private static long computeFingerprint(List<LogicalTextLine> logicalLines) {
        long hash = 1125899906842597L;
        hash = hash * 31 + logicalLines.size();
        for (int index = 0; index < logicalLines.size(); index++) {
            LogicalTextLine line = logicalLines.get(index);
            hash = hash * 31 + line.getStartIndex();
            hash = hash * 31 + line.getEndIndex();
            hash = hash * 31 + line.getText().hashCode();
        }
        return hash;
    }
}
