package club.heiqi.uilib.ui.scene.node;

/**
 * COMPOSITE 级变换占位类。
 *
 * <p>当前持最小字段集（translateX, translateY），供 {@link SceneNode#setTransform(Transform)}
 * 打出合成级失效标记。后续 Phase 将扩展为完整的 2D/3D 变换矩阵（rotation、scale、skew 等）。</p>
 *
 * <p>设计为不可变类：构造后字段不可修改，确保线程安全且避免意外修改触发遗漏脏标记。</p>
 */
public class Transform {
    /** X 轴平移量 */
    public final float translateX;
    /** Y 轴平移量 */
    public final float translateY;

    /**
     * 构造一个平移变换。
     *
     * @param translateX X 轴平移量
     * @param translateY Y 轴平移量
     */
    public Transform(float translateX, float translateY) {
        this.translateX = translateX;
        this.translateY = translateY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transform)) return false;
        Transform that = (Transform) o;
        return Float.compare(that.translateX, translateX) == 0
            && Float.compare(that.translateY, translateY) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(translateX);
        result = 31 * result + Float.floatToIntBits(translateY);
        return result;
    }
}
