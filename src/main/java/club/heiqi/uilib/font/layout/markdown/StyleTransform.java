package club.heiqi.uilib.font.layout.markdown;

import club.heiqi.uilib.font.layout.TextStyle;

/**
 * 样式变换链（包内，C6a 能力①）：块级样式叠加的唯一抽象。
 *
 * <p><b>为什么是函数而不是样式值</b>：{@code MarkdownDocument} 扁平化沿树传递「块级样式」
 * （标题粗体/字号增量、引用斜体/色）。String 路把变换结果值一路传值即可；span 流入口
 * （{@code MarkdownDocument.parse(List)}）每个字符的基础样式来自行内样式锚点
 * （{@link MarkdownSpan#getBaseStyle()}），块级叠加必须在<b>出段那一刻</b>逐区间施加——
 * 传值会把 span 基础色洗成 caller 基础色（或反向丢失块级色），只有传变换保得住语义。</p>
 *
 * <p>实现约定：{@link #apply(TextStyle)} 可在入参实例上原位变异并返回它（调用方恒传拷贝，
 * 与 {@code MarkdownDocument#headingStyle} 旧语义一致）；链组合 = 先 {@code a} 后 {@code b}
 * 的函数复合，叠加顺序与旧 walk 逐层 {@code quoteStyle(style)} / {@code headingStyle(style)}
 * 的先后关系逐位相同。</p>
 *
 * <p>本类型不引入任何颜色语义之外的新规则，也不感知任何宿主格式码——纯样式函数。</p>
 */
interface StyleTransform {

    /**
     * 把本变换施加到样式上。
     *
     * @param style 已拷贝的待叠加样式（原位变异可，不得为 null）
     * @return 叠加后的样式（可为同一实例）
     */
    TextStyle apply(TextStyle style);

    /**
     * 链组合：先施加 {@code first}、再施加 {@code second}（任一为 null 取另一个；双 null 为 null）。
     *
     * @param first  外层（先施加）变换，可为 null
     * @param second 内层（后施加）变换，可为 null
     * @return 复合变换；null = 恒等（无叠加）
     */
    static StyleTransform compose(StyleTransform first, StyleTransform second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return new StyleTransform() {
            @Override
            public TextStyle apply(TextStyle style) {
                return second.apply(first.apply(style));
            }
        };
    }
}
