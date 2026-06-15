package club.heiqi.uilib.ui.text.layout;

/**
 * 文本测量函数抽象。
 *
 * <p>该接口把“如何测量一段文本的显示宽度”从具体渲染上下文中解耦出来，供
 * {@link TextLayoutEngine} 在不直接依赖 {@code UiRenderContext} 的前提下完成视觉行布局与
 * 前缀宽度计算。控件侧通常用绑定到 {@code UILIB_RAW} 文本模式的渲染上下文测量适配本接口；
 * 单元测试可注入确定性的线性测量替身。</p>
 *
 * <p>实现必须满足：对同一段文本返回稳定且非负的宽度，且整串宽度等于其各前缀宽度的单调上界，
 * 即 {@code widthOf(text.substring(0, k))} 随 {@code k} 单调不减。{@link TextLayoutEngine}
 * 依赖该单调性把逐前缀 O(N²) 测量降为单趟 O(N) 前缀累加。</p>
 */
@FunctionalInterface
public interface TextMeasureFunction {

    /**
     * 测量一段文本在目标坐标系下的显示宽度。
     *
     * @param text 待测量文本；为 {@code null} 时按空串处理
     * @return 文本显示宽度（非负）
     */
    int widthOf(String text);

    /**
     * 计算一段文本按码点边界切分的前缀宽度向量。
     *
     * <p>返回数组长度为该文本的码点数 + 1，元素 {@code i} 等于该文本前 {@code i} 个码点子串的显示宽度，
     * 其中元素 0 恒为 0、末元素等于整串宽度。该向量是 {@link TextLayoutEngine} 完成 caret/选区命中
     * 与软换行的唯一测量来源。</p>
     *
     * <p>默认实现按码点边界逐次调用 {@link #widthOf(String)}。对线性可加的测量实现（如测试替身）该默认
     * 实现已与逐段测量数值一致；对生产环境的非线性（{@code ceil} + 缩放 + 取整）测量实现，调用方应覆盖本
     * 方法改用底层单趟累加，既保证 O(N)，又保证每个边界值与逐次 {@code widthOf(prefix)} 完全一致。</p>
     *
     * @param text 待测量文本；为 {@code null} 或空串时返回 {@code {0}}
     * @return 前缀宽度向量
     */
    default int[] prefixWidths(String text) {
        if (text == null || text.isEmpty()) {
            return new int[] {0};
        }
        int codePointCount = text.codePointCount(0, text.length());
        int[] widths = new int[codePointCount + 1];
        widths[0] = 0;
        int currentOffset = 0;
        for (int index = 1; index <= codePointCount; index++) {
            currentOffset = text.offsetByCodePoints(currentOffset, 1);
            widths[index] = widthOf(text.substring(0, currentOffset));
        }
        return widths;
    }
}
