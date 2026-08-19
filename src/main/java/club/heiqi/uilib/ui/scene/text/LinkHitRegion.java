package club.heiqi.uilib.ui.scene.text;

import java.util.Objects;

/**
 * 链接命中区域 —— link 命中数据的正式化载体（审查报告 §8 B2-5）。
 *
 * <p>由绘制引擎从 {@link TextLinePlan} 的行内相对区域投影产出（叠加 textLeft 与行顶），
 * 坐标<b>相对节点局部原点</b>（与输入事件的 localPointer 同坐标系），缓存于节点；
 * 控件层命中测试直接读本结构，不再强转 PaintFragment 遍历绘制命令流。</p>
 */
public final class LinkHitRegion {

    /** 左边界（节点局部像素，含） */
    private final int left;

    /** 上边界（节点局部像素，含） */
    private final int top;

    /** 右边界（节点局部像素，不含） */
    private final int right;

    /** 下边界（节点局部像素，不含） */
    private final int bottom;

    /** 链接 URL */
    private final String url;

    /**
     * 创建链接命中区域。
     *
     * @param left   左边界（含）
     * @param top    上边界（含）
     * @param right  右边界（不含）
     * @param bottom 下边界（不含）
     * @param url    链接 URL（非 null）
     */
    public LinkHitRegion(int left, int top, int right, int bottom, String url) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.url = Objects.requireNonNull(url, "url");
    }

    /** @return 左边界（含） */
    public int getLeft() {
        return left;
    }

    /** @return 上边界（含） */
    public int getTop() {
        return top;
    }

    /** @return 右边界（不含） */
    public int getRight() {
        return right;
    }

    /** @return 下边界（不含） */
    public int getBottom() {
        return bottom;
    }

    /** @return 链接 URL */
    public String getUrl() {
        return url;
    }

    /**
     * 矩形包含判定（左闭右开/上闭下开，与 LINK_REGION 命令命中语义一致）。
     *
     * @param x 节点局部 X
     * @param y 节点局部 Y
     * @return 是否命中本区域
     */
    public boolean contains(int x, int y) {
        return x >= left && x < right && y >= top && y < bottom;
    }
}
