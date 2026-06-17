package club.heiqi.uilib.ui.scene.layout;

import java.util.Objects;

/**
 * 单个节点的布局结果缓存单元。
 *
 * <h3>坐标语义</h3>
 * <p>{@code x, y} 为<b>相对于父容器左上角</b>的局部像素坐标。
 * {@code width, height} 为本节点所占像素尺寸。</p>
 *
 * <p>不可变值对象，布局引擎计算后存入 {@code SceneNode#cachedLayout} 缓存槽。
 * 缓存有效的条件是节点 {@code selfLayoutDirty==false && descendantLayoutDirty==false}，
 * 此时可直接复用 LayoutBox 跳过重算（I7/I8）。</p>
 */
public final class LayoutBox {

    /** 相对父容器的 X 坐标（像素） */
    private final int x;

    /** 相对父容器的 Y 坐标（像素） */
    private final int y;

    /** 节点宽度（像素） */
    private final int width;

    /** 节点高度（像素） */
    private final int height;

    /**
     * 创建布局结果。
     *
     * @param x      相对父容器的 X 坐标（像素）
     * @param y      相对父容器的 Y 坐标（像素）
     * @param width  节点宽度（像素）
     * @param height 节点高度（像素）
     */
    public LayoutBox(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** @return 相对父 X 坐标（像素） */
    public int getX() {
        return x;
    }

    /** @return 相对父 Y 坐标（像素） */
    public int getY() {
        return y;
    }

    /** @return 节点宽度（像素） */
    public int getWidth() {
        return width;
    }

    /** @return 节点高度（像素） */
    public int getHeight() {
        return height;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LayoutBox)) return false;
        LayoutBox other = (LayoutBox) obj;
        return x == other.x && y == other.y && width == other.width && height == other.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height);
    }

    @Override
    public String toString() {
        return "LayoutBox{x=" + x + ", y=" + y + ", w=" + width + ", h=" + height + "}";
    }
}
