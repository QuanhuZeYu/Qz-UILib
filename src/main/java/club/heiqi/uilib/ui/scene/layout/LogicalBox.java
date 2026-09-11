package club.heiqi.uilib.ui.scene.layout;

import com.github.bsideup.jabel.Desugar;

/**
 * LogicalBox —— 宿主视口的<b>逻辑像素</b>尺寸（scene 坐标空间的事实盒）。
 *
 * <p>它是「分辨率 × GUI Scale」在<b>宿主边界</b>合成后的唯一产物：layout / paint / replay /
 * 裁剪 / 输入全部共享这一份坐标事实（Qz-UILib AGENTS.md:28）。内部闭环拿到的永远只有本记录，
 * <b>拿不到</b>物理分辨率，也<b>拿不到</b> GUI Scale —— 两者都在宿主边界被折叠掉。</p>
 *
 * <p>不可变值对象：{@code equals/hashCode} 按值，便于作为 Signal 的载荷做同值去重
 * （窗口拖动时同一尺寸不会重复触发重派生）。</p>
 *
 * @param widthPx  逻辑宽（≥0）
 * @param heightPx 逻辑高（≥0）
 */
@Desugar
public record LogicalBox(int widthPx, int heightPx) {

    /** 空盒：宿主尚未给出尺寸（未渲染过 / 最小化）。 */
    public static final LogicalBox EMPTY = new LogicalBox(0, 0);

    /** 规范化：负值收敛到 0（渲染热路径不抛异常）。 */
    public LogicalBox {
        if (widthPx < 0) {
            widthPx = 0;
        }
        if (heightPx < 0) {
            heightPx = 0;
        }
    }

    /** @return 是否已有有效尺寸（宽高均 &gt; 0） */
    public boolean isPresent() {
        return widthPx > 0 && heightPx > 0;
    }

    /**
     * 按逻辑宽高构造。
     *
     * @param widthPx  逻辑宽
     * @param heightPx 逻辑高
     * @return 逻辑盒
     */
    public static LogicalBox of(int widthPx, int heightPx) {
        return new LogicalBox(widthPx, heightPx);
    }
}
