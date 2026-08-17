package club.heiqi.uilib.ui.scene.layout;

import java.util.Objects;

import com.github.bsideup.jabel.Desugar;

/**
 * 网格布局规格 —— 描述网格容器如何把等宽子项按固定列数（或由可用宽推算列数）排列并自动换行。
 *
 * <p>不可变值对象，与 {@link GridLayouter}（定位协作者）/ {@link GridLayouts}（门面）配套使用。
 * 坐标、尺寸均为 logical px，与 flex 布局同一坐标事实。</p>
 *
 * <ul>
 *   <li>{@code columns <= 0}：列数由可用内宽推算
 *       {@code max(1, floor((innerWidth + gapX) / (cellWidth + gapX)))}；</li>
 *   <li>{@code cellHeight <= 0}：行高按内容（取该行子节点自然高的最大值），
 *       否则所有单元高统一为 {@code cellHeight}。</li>
 * </ul>
 */
@Desugar
public record GridSpec(
        int columns,
        int cellWidth,
        int cellHeight,
        int gapX,
        int gapY,
        MainAxisAlign mainAxisAlign,
        CrossAxisAlign crossAxisAlign) {

    /**
     * 创建网格布局规格。
     *
     * @param columns        固定列数，&lt;=0 表示按可用宽推算
     * @param cellWidth      单元宽（UI 像素，&gt;0）
     * @param cellHeight     单元高（UI 像素，&lt;=0 表示行高按内容）
     * @param gapX           列间距（UI 像素，&gt;=0）
     * @param gapY           行间距（UI 像素，&gt;=0）
     * @param mainAxisAlign  网格块在容器主轴（纵向）上的对齐
     * @param crossAxisAlign 网格块在容器交叉轴（横向）上的对齐；STRETCH 按 START 处理
     */
    public GridSpec {
        if (cellWidth <= 0) {
            throw new IllegalArgumentException("cellWidth 必须 > 0");
        }
        if (gapX < 0 || gapY < 0) {
            throw new IllegalArgumentException("gap 不可为负数");
        }
        mainAxisAlign = mainAxisAlign == null ? MainAxisAlign.START : mainAxisAlign;
        crossAxisAlign = crossAxisAlign == null ? CrossAxisAlign.START : crossAxisAlign;
    }

    /** 常用形态：固定列数、两端对齐 START。 */
    public static GridSpec of(int columns, int cellWidth, int cellHeight, int gapX, int gapY) {
        return new GridSpec(columns, cellWidth, cellHeight, gapX, gapY,
                MainAxisAlign.START, CrossAxisAlign.START);
    }

    /** 自动列数形态：列数由可用内宽推算。 */
    public static GridSpec autoColumns(int cellWidth, int cellHeight, int gapX, int gapY) {
        return new GridSpec(0, cellWidth, cellHeight, gapX, gapY,
                MainAxisAlign.START, CrossAxisAlign.START);
    }

    /** @return 是否为自动列数模式 */
    public boolean isAutoColumns() {
        return columns <= 0;
    }

    /** @return 行高是否按内容 */
    public boolean isContentRows() {
        return cellHeight <= 0;
    }

    /** 归一化后的有效主轴对齐。 */
    public MainAxisAlign effectiveMainAxisAlign() {
        return Objects.requireNonNull(mainAxisAlign);
    }

    /** 归一化后的有效交叉轴对齐（STRETCH 归一为 START）。 */
    public CrossAxisAlign effectiveCrossAxisAlign() {
        return crossAxisAlign == CrossAxisAlign.STRETCH ? CrossAxisAlign.START : crossAxisAlign;
    }
}
