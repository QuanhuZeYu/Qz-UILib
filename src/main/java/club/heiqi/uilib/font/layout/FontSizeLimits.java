package club.heiqi.uilib.font.layout;

/**
 * 字号域边界 —— 全库唯一的字号校验常量与归一函数。
 *
 * <p>取代此前分散的字面量：{@code RichTextTagParser.MAX_FONT_SIZE_PX = 256} /
 * {@code MIN_FONT_SIZE_PX = 1}，以及任何新出现的字号上下界字面量。
 * 富文本解析、{@link club.heiqi.uilib.ui.scene.node.SceneNode} 的字号 setter、
 * 用户缩放出口共用本类。</p>
 *
 * <p>分层理由：{@code font.layout} 不依赖 {@code ui.scene}（I10 接缝纯净），而
 * {@code SceneNode} 已依赖本包（{@code font.layout.TextSegment}），故常量落在 font 域
 * 既不产生新的包边、也不把 font 层反向绑到 scene 层。</p>
 */
public final class FontSizeLimits {

    /** 默认字号（UI 逻辑像素）；与节点层 4b 兜底同源。 */
    public static final int DEFAULT_FONT_SIZE_PX = 16;

    /** 最小字号。 */
    public static final int MIN_FONT_SIZE_PX = 1;

    /** 最大字号；沿用既有 256 作为全库唯一上限。 */
    public static final int MAX_FONT_SIZE_PX = 256;

    private FontSizeLimits() {
    }

    /**
     * 归一一个字号到 {@code [MIN, MAX]}。
     *
     * <p>用于两类<b>不得 fail-fast</b> 的路径：① 信号运行期值（effect 内抛异常会打断 flush）；
     * ② 解析出口的缩放结果（声明值 × fontScale）。信号路径因越界被钳制的次数由入口侧计数，
     * 供诊断与守卫读取。</p>
     *
     * @param fontSizePx 原始字号
     * @return 域内字号
     */
    public static int clampFontSize(int fontSizePx) {
        return Math.max(MIN_FONT_SIZE_PX, Math.min(MAX_FONT_SIZE_PX, fontSizePx));
    }

    /**
     * 调用点参数校验：越界直接抛 {@link IllegalArgumentException}。
     *
     * <p>用于「业务或控件显式传入的 int 字号」（字号入口的构建期定值、控件回落值登记等）：
     * 传错就是代码缺陷，必须快速失败，不得静默钳制改变语义。</p>
     *
     * @param fontSizePx 原始字号
     * @return 原值（便于链式使用）
     * @throws IllegalArgumentException 越界时
     */
    public static int requireValidFontSize(int fontSizePx) {
        if (fontSizePx < MIN_FONT_SIZE_PX || fontSizePx > MAX_FONT_SIZE_PX) {
            throw new IllegalArgumentException("fontSizePx 越界: " + fontSizePx
                    + "，合法区间 [" + MIN_FONT_SIZE_PX + ", " + MAX_FONT_SIZE_PX + "]");
        }
        return fontSizePx;
    }
}
