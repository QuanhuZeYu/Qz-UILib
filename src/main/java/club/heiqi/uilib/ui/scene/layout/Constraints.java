package club.heiqi.uilib.ui.scene.layout;

/**
 * 布局约束最小值对象，描述父容器提供给子节点的可用空间。
 *
 * <p>当前是最小形态：仅含可用宽度（块级垂直堆叠够用），后续扩展
 * 可用高度、最小/最大宽高、宽高比等约束。</p>
 *
 * <p>不可变值对象。</p>
 */
public final class Constraints {

    /** 父容器提供的可用宽度（像素，int） */
    private final int availableWidth;

    /**
     * 创建布局约束。
     *
     * @param availableWidth 可用宽度（像素）
     */
    public Constraints(int availableWidth) {
        this.availableWidth = availableWidth;
    }

    /** @return 可用宽度（像素） */
    public int getAvailableWidth() {
        return availableWidth;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Constraints)) return false;
        Constraints other = (Constraints) obj;
        return availableWidth == other.availableWidth;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(availableWidth);
    }

    @Override
    public String toString() {
        return "Constraints{availableWidth=" + availableWidth + "}";
    }
}
