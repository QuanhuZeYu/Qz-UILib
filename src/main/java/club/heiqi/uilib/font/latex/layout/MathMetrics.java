package club.heiqi.uilib.font.latex.layout;

/**
 * 数学布局度量注入接口：布局层不直接依赖字体引擎，度量经此接口进入（纯 JVM 可测）。
 *
 * <p>实现（M3）由 TextLayoutService 适配（advance 复用 measureCodepointWidth、
 * ascent/descent 复用内部度量路径）。</p>
 */
public interface MathMetrics {

    /**
     * 文本推进宽度（逐码点求和；空格等零宽符按现有分类语义）。
     *
     * @param text   显示文本（可多个码点）
     * @param sizePx 像素字号
     * @return 推进宽度
     */
    float advance(String text, float sizePx);

    /**
     * 指定字号的字体 ascent（基线上方高度）。
     */
    float ascent(float sizePx);

    /**
     * 指定字号的字体 descent（基线下方深度）。
     */
    float descent(float sizePx);

    /**
     * 指定字号的 x-height（小写 x 高度，TeX 布局算法关键参数：根号间隙、
     * 上下标约束、重音基准）。
     */
    float xHeight(float sizePx);

    /**
     * 斜体校正量（em）：数学变量斜体字形视觉右倾，上标需右移此量避开斜体笔画
     * （TeX Char.italic 的几何斜切近似 = 斜切角 × x-height）。
     *
     * <p>默认实现返回 0（mock 度量与直体场景）；真机度量由 {@code TextLayoutService}
     * 按斜切几何给出。</p>
     *
     * @param text   文本（布局侧仅对单字符数学变量调用）
     * @param sizePx 字号
     * @return 斜体校正量（px）
     */
    default float italicCorrection(String text, float sizePx) {
        return 0.0F;
    }

    /**
     * 字形 ink 宽度（px）：单字符字形的可见墨水宽度（左右留白剥离后的宽度）。
     *
     * <p>排版推进（advance）与可见墨水（ink）是两套口径：斜体剪切后 ink 右缘超出
     * advance、根号字形 ink 右缘则小于 advance。规则线端点必须对齐 ink 边界才视觉精准。
     * 默认实现回退 advance（mock 度量与无 ink 数据的场景）。</p>
     *
     * @param text   单字符文本（布局侧仅对根号/定界符等基元字形调用）
     * @param sizePx 字号
     * @return ink 宽度（px）
     */
    default float inkWidth(String text, float sizePx) {
        return advance(text, sizePx);
    }

    /**
     * 斜体视觉右越量（px）：几何斜切后字形 ink 右缘超出排版推进的量
     * （≈ tan(斜角) × ink 高）。规则线右端需外扩此量覆盖斜体笔画。
     *
     * <p>默认实现返回 0（mock 度量与直体场景）；真机度量由 {@code TextLayoutService}
     * 按斜切几何给出。</p>
     *
     * @param text   文本（布局侧仅对单字符数学变量调用）
     * @param sizePx 字号
     * @return 视觉右越量（px）
     */
    default float italicOverhang(String text, float sizePx) {
        return 0.0F;
    }

    /**
     * 字形 ink 中心相对盒基线的偏移（px，正值 = 基线上方；实现口径与 layoutFence 一致）。
     *
     * <p>伸缩定界符/大运算符按数学轴居中时必须以 ink 中心为锚：ink 在字格内的分布并不
     * 对称，按字体盒度量（ascent/descent）居中会让缩放字形视觉中心偏离轴（实测 0.3em+）。
     * 默认实现回退盒度量中心 (ascent − descent)/2（mock 与无 ink 数据场景，旧行为不变）。</p>
     *
     * @param text   单字符文本（布局侧仅对定界符/大运算符等基元字形调用）
     * @param sizePx 字号
     * @return ink 中心相对基线的偏移（px，正值向上）
     */
    default float inkCenterOffsetY(String text, float sizePx) {
        return (ascent(sizePx) - descent(sizePx)) / 2.0F;
    }

    /**
     * 字形 ink 高度（px）：单字符字形的可见墨水总高（顶部到墨水底）。
     *
     * <p>大运算符（\sum \int 等）limits 上下标以符号 ink 顶/底为锚：ink 高与字体盒
     * ascent 差异显著（如 cmex 大运算符字形全在基线以下），按盒度量会让上下限位置
     * 随行高漂移。默认实现回退字体盒总高（mock 与无 ink 数据场景，旧行为不变）。</p>
     *
     * @param text   单字符文本（布局侧仅对大运算符符号调用）
     * @param sizePx 字号
     * @return ink 总高（px）
     */
    default float inkHeight(String text, float sizePx) {
        return ascent(sizePx) + descent(sizePx);
    }
}
