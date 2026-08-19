package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * 文本行计划 —— 拆行 + clamp + 逐行行高 + 行内链接区域的一次性产物（审查报告 §8 B2-4）。
 *
 * <p>布局阶段（{@code SizingCalculator.leafTextHeight}）构建并缓存于节点，绘制阶段消费，
 * 消除 ScenePaintEngine 与 SizingCalculator 各自拆行/截断（含省略号富文本裁剪）的双份执行；
 * 链接区域解析同样只在布局阶段执行一次，绘制层仅做坐标投影。</p>
 *
 * <p>不可变：构建后行列表/行高/链接区域全部固化；绘制层投影产出
 * {@link LinkHitRegion} 缓存于节点，供控件层命中测试。</p>
 */
public final class TextLinePlan {

    /** clamp 后显示行列表（不改写输入） */
    private final List<String> lines;

    /** 每行最终行高（经 lineHeightResolver 解析后，UI 像素，至少 1） */
    private final int[] lineHeights;

    /** 每行行内链接区域（行内相对坐标，投影由绘制层负责） */
    private final List<List<TextLinkRegion>> linkRegionsPerLine;

    /** 行块总高（Σ lineHeights） */
    private final int totalHeight;

    private TextLinePlan(List<String> lines, int[] lineHeights,
            List<List<TextLinkRegion>> linkRegionsPerLine) {
        this.lines = lines;
        this.lineHeights = lineHeights;
        this.linkRegionsPerLine = linkRegionsPerLine;
        int total = 0;
        for (int height : lineHeights) {
            total += height;
        }
        this.totalHeight = total;
    }

    /**
     * 构建文本行计划：拆行 → maxLines clamp（可选省略号）→ 逐行行高解析 → 逐行链接区域。
     *
     * @param measurer           文本度量
     * @param text               文本内容（可为 null，按空串处理）
     * @param fontSizePx         UI 像素字号
     * @param wrapWidth          换行宽度（UI 像素；{@code <=0} 不换行）
     * @param textMode           内容模式（非 null）
     * @param maxLines           最大行数（{@code <=0} 不限）
     * @param ellipsis           是否在截断末行追加省略号
     * @param lineHeightResolver 行高解析器（如 {@code node::resolveLineHeight}；输入自动行高，输出最终行高）
     * @return 不可变文本行计划
     */
    public static TextLinePlan build(SceneTextMeasurer measurer, String text, int fontSizePx, int wrapWidth,
            SceneTextMode textMode, int maxLines, boolean ellipsis, IntUnaryOperator lineHeightResolver) {
        String safeText = text == null ? "" : text;
        List<String> split = measurer.splitLines(safeText, fontSizePx, wrapWidth, textMode);
        List<String> clamped = SceneLineClamp.clamp(split, maxLines, ellipsis, measurer, fontSizePx,
                wrapWidth, textMode);
        int[] heights = new int[clamped.size()];
        List<List<TextLinkRegion>> linkRegions = new ArrayList<List<TextLinkRegion>>(clamped.size());
        for (int index = 0; index < clamped.size(); index++) {
            String line = clamped.get(index);
            int autoHeight = measurer.lineHeight(line, fontSizePx, textMode);
            heights[index] = Math.max(1, lineHeightResolver.applyAsInt(autoHeight));
            linkRegions.add(measurer.linkRegions(line, fontSizePx, textMode));
        }
        return new TextLinePlan(clamped, heights, linkRegions);
    }

    /** @return clamp 后显示行列表（不可变视图，元素顺序稳定） */
    public List<String> getLines() {
        return lines;
    }

    /** @return 每行最终行高（UI 像素，与 getLines() 逐位对应） */
    public int[] getLineHeights() {
        return lineHeights;
    }

    /** @return 每行行内链接区域（与 getLines() 逐位对应；行内相对坐标） */
    public List<List<TextLinkRegion>> getLinkRegionsPerLine() {
        return linkRegionsPerLine;
    }

    /** @return 行块总高（UI 像素，Σ 行高） */
    public int getTotalHeight() {
        return totalHeight;
    }
}
