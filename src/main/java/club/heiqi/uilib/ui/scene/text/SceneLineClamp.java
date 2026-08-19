package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.List;

/**
 * scene 文本行截断工具：maxLines 限行 + 可选末行省略号。

 * <p>绘制（ScenePaintEngine）与布局（SizingCalculator）共用本工具，保证截断口径一致：
 * 拆行结果超过 {@code maxLines} 时保留前 N 行；{@code ellipsis} 且 wrap 宽度有效时，
 * 末行经富文本感知裁剪后追加省略号（宽度恰好不超行宽）。</p>

 * <p>省略号仅在「确有内容被截掉」（行数超出 maxLines）时出现；行数恰好等于 maxLines 时
 * 不做任何改写（与 CSS line-clamp 语义一致）。</p>
 */
public final class SceneLineClamp {

    /** 省略号字符（U+2026）。 */
    public static final String ELLIPSIS = "\u2026";

    private SceneLineClamp() {
    }

    /**
     * 按 maxLines 截断拆行结果；ellipsis 时对末行追加省略号。
     *
     * @param lines      拆行结果
     * @param maxLines   最大行数（&lt;=0 不限）
     * @param ellipsis   是否在末行追加省略号（仅 wrap 宽度有效时生效）
     * @param measurer   文本度量（省略号宽度与富文本感知裁剪）
     * @param fontSizePx UI 像素字号
     * @param wrapWidth  换行宽度（UI 像素；&lt;=0 视为不换行，省略号不生效）
     * @param textMode   内容模式编码（与 {@link SceneTextMeasurer#splitLines} 一致）
     * @return 截断后的行列表（不改写输入）
     */
    public static List<String> clamp(List<String> lines, int maxLines, boolean ellipsis,
            SceneTextMeasurer measurer, int fontSizePx, int wrapWidth, int textMode) {
        if (maxLines <= 0 || lines.size() <= maxLines) {
            return lines;
        }
        List<String> kept = new ArrayList<String>(lines.subList(0, maxLines));
        if (ellipsis && wrapWidth > 0) {
            String last = kept.get(maxLines - 1);
            kept.set(maxLines - 1, ellipsizeLast(last, measurer, fontSizePx, wrapWidth, textMode));
        }
        return kept;
    }

    /**
     * 给末行追加省略号：预留省略号宽度后按行宽裁剪，末尾拼接省略号。
     *
     * @param last       末行文本（可含富文本标签）
     * @param measurer   文本度量
     * @param fontSizePx UI 像素字号
     * @param wrapWidth  换行宽度
     * @param textMode   内容模式编码
     * @return 带省略号的末行文本
     */
    private static String ellipsizeLast(String last, SceneTextMeasurer measurer, int fontSizePx,
            int wrapWidth, int textMode) {
        int ellipsisWidth = measurer.measureWidth(ELLIPSIS, fontSizePx);
        int available = wrapWidth - ellipsisWidth;
        if (available <= 0) {
            // 行宽连省略号都放不下：只显示省略号
            return ELLIPSIS;
        }
        String trimmed = measurer.trimToWidth(last == null ? "" : last, fontSizePx, available, textMode);
        return trimmed + ELLIPSIS;
    }
}