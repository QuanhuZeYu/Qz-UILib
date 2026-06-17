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

    /** 表示某个维度不做约束的哨兵值 */
    public static final int UNCONSTRAINED = -1;

    /** 父容器提供的可用宽度（像素，int） */
    private final int availableWidth;

    /** 父容器提供的可用高度（像素，int），{@link #UNCONSTRAINED} 表示无高度约束 */
    private final int availableHeight;

    /**
     * 创建仅含宽度约束的布局约束（向后兼容）。
     *
     * <p>高度置为 {@link #UNCONSTRAINED}，保持现有 29 处调用点行为不变。</p>
     *
     * @param availableWidth 可用宽度（像素）
     */
    public Constraints(int availableWidth) {
        this(availableWidth, UNCONSTRAINED);
    }

    /**
     * 创建含宽高约束的布局约束。
     *
     * @param availableWidth  可用宽度（像素）
     * @param availableHeight 可用高度（像素），传 {@link #UNCONSTRAINED} 表示不做高度约束
     */
    public Constraints(int availableWidth, int availableHeight) {
        this.availableWidth = availableWidth;
        this.availableHeight = availableHeight;
    }

    /** @return 可用宽度（像素） */
    public int getAvailableWidth() {
        return availableWidth;
    }

    /** @return 可用高度（像素），可能为 {@link #UNCONSTRAINED}（-1） */
    public int getAvailableHeight() {
        return availableHeight;
    }

    /** @return 是否有高度约束（availableHeight >= 0） */
    public boolean hasHeightConstraint() {
        return availableHeight >= 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Constraints)) return false;
        Constraints other = (Constraints) obj;
        return availableWidth == other.availableWidth
                && availableHeight == other.availableHeight;
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(availableWidth) + Integer.hashCode(availableHeight);
    }

    @Override
    public String toString() {
        return "Constraints{availableWidth=" + availableWidth
                + ", availableHeight=" + availableHeight + "}";
    }
}
