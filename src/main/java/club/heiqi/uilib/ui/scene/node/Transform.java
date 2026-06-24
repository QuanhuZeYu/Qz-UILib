package club.heiqi.uilib.ui.scene.node;

/**
 * COMPOSITE 级变换值对象 —— 承载 transform 完整 2D 矩阵分量（方案甲）。
 *
 * <p>持有 translate（浮点平移）、rotateDegrees（绕 Z 顺时针旋转角度）、scaleX/scaleY（缩放倍率）、
 * originXRatio/originYRatio（变换原点比率，相对节点 box 的归一化坐标）。本期方案甲<b>不含 skew</b>。
 * 与 opacity 同为合成级属性，{@link SceneNode#setTransform(Transform)} 走 composite 级失效通路
 * （markComposite），绝不触发 layout 重排或 fragment 重建（守宪章信条五分级失效铁律）。</p>
 *
 * <h3>不可变设计</h3>
 * <p>全部字段为 {@code final}，构造后不可修改，确保线程安全且避免意外修改触发遗漏脏标记。
 * 提供 equals/hashCode（含全部分量），供 {@link SceneNode#setTransform} 做值去重，
 * 避免无变化时无谓标脏。</p>
 *
 * <h3>浮点语义（还债）</h3>
 * <p>translateX/translateY 为<b>浮点</b>语义，由渲染层 GL 矩阵栈直接消费做顶点变换，
 * 不再像旧 offset 通路那样 {@code Math.round} 整数量化，亚像素平移与缩放/旋转可平滑推进。</p>
 *
 * <h3>变换原点</h3>
 * <p>originXRatio/originYRatio 默认 {@code 0.5/0.5}（box 中心），与渲染层 transform-origin
 * 默认中心一致。渲染层据此 ratio 在绝对边界盒内解析出原点绝对坐标，做 origin 三明治变换。</p>
 */
public class Transform {

    /** 恒等变换判定的浮点容差 */
    private static final float IDENTITY_EPSILON = 1e-6f;

    /** X 轴平移量（浮点像素，由 GL 矩阵直接消费，不整数量化） */
    public final float translateX;
    /** Y 轴平移量（浮点像素，由 GL 矩阵直接消费，不整数量化） */
    public final float translateY;
    /** 绕 Z 轴顺时针旋转角度（度），默认 0 */
    public final float rotateDegrees;
    /** X 轴缩放倍率，默认 1.0 */
    public final float scaleX;
    /** Y 轴缩放倍率，默认 1.0 */
    public final float scaleY;
    /** 变换原点 X 比率（相对 box 宽度归一化，0=左边、1=右边），默认 0.5（中心） */
    public final float originXRatio;
    /** 变换原点 Y 比率（相对 box 高度归一化，0=上边、1=下边），默认 0.5（中心） */
    public final float originYRatio;

    /**
     * 全分量构造器。
     *
     * @param translateX    X 轴平移量（浮点像素）
     * @param translateY    Y 轴平移量（浮点像素）
     * @param rotateDegrees 绕 Z 轴顺时针旋转角度（度）
     * @param scaleX        X 轴缩放倍率
     * @param scaleY        Y 轴缩放倍率
     * @param originXRatio  变换原点 X 比率（box 归一化坐标）
     * @param originYRatio  变换原点 Y 比率（box 归一化坐标）
     */
    public Transform(float translateX, float translateY, float rotateDegrees,
                     float scaleX, float scaleY, float originXRatio, float originYRatio) {
        this.translateX = translateX;
        this.translateY = translateY;
        this.rotateDegrees = rotateDegrees;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.originXRatio = originXRatio;
        this.originYRatio = originYRatio;
    }

    /**
     * 仅平移的便捷构造器（rotate=0、scale=1、origin=中心）。
     *
     * @param translateX X 轴平移量（浮点像素）
     * @param translateY Y 轴平移量（浮点像素）
     */
    public Transform(float translateX, float translateY) {
        this(translateX, translateY, 0.0f, 1.0f, 1.0f, 0.5f, 0.5f);
    }

    /**
     * 创建平移变换（origin 默认中心）。
     *
     * @param translateX X 轴平移量
     * @param translateY Y 轴平移量
     * @return 平移变换
     */
    public static Transform translate(float translateX, float translateY) {
        return new Transform(translateX, translateY, 0.0f, 1.0f, 1.0f, 0.5f, 0.5f);
    }

    /**
     * 创建旋转变换（绕 box 中心，rotate 单位为度）。
     *
     * @param rotateDegrees 绕 Z 轴顺时针旋转角度（度）
     * @return 旋转变换
     */
    public static Transform rotate(float rotateDegrees) {
        return new Transform(0.0f, 0.0f, rotateDegrees, 1.0f, 1.0f, 0.5f, 0.5f);
    }

    /**
     * 创建缩放变换（绕 box 中心）。
     *
     * @param scaleX X 轴缩放倍率
     * @param scaleY Y 轴缩放倍率
     * @return 缩放变换
     */
    public static Transform scale(float scaleX, float scaleY) {
        return new Transform(0.0f, 0.0f, 0.0f, scaleX, scaleY, 0.5f, 0.5f);
    }

    /**
     * 判定是否为恒等变换（无任何视觉效果）。
     *
     * <p>恒等条件：rotate≈0 && scaleX≈1 && scaleY≈1 && translate≈0。origin 不影响
     * 恒等判定（无 rotate/scale 时 origin 对结果无作用）。绘制引擎据此走快速路径：
     * 恒等变换不产 PUSH_TRANSFORM/POP_TRANSFORM 边界命令（类比 opacity≈1.0 快速路径）。</p>
     *
     * @return 是否为恒等变换
     */
    public boolean isIdentity() {
        return Math.abs(translateX) <= IDENTITY_EPSILON
            && Math.abs(translateY) <= IDENTITY_EPSILON
            && Math.abs(rotateDegrees) <= IDENTITY_EPSILON
            && Math.abs(scaleX - 1.0f) <= IDENTITY_EPSILON
            && Math.abs(scaleY - 1.0f) <= IDENTITY_EPSILON;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transform)) return false;
        Transform that = (Transform) o;
        return Float.compare(that.translateX, translateX) == 0
            && Float.compare(that.translateY, translateY) == 0
            && Float.compare(that.rotateDegrees, rotateDegrees) == 0
            && Float.compare(that.scaleX, scaleX) == 0
            && Float.compare(that.scaleY, scaleY) == 0
            && Float.compare(that.originXRatio, originXRatio) == 0
            && Float.compare(that.originYRatio, originYRatio) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(translateX);
        result = 31 * result + Float.floatToIntBits(translateY);
        result = 31 * result + Float.floatToIntBits(rotateDegrees);
        result = 31 * result + Float.floatToIntBits(scaleX);
        result = 31 * result + Float.floatToIntBits(scaleY);
        result = 31 * result + Float.floatToIntBits(originXRatio);
        result = 31 * result + Float.floatToIntBits(originYRatio);
        return result;
    }

    @Override
    public String toString() {
        return "Transform{translateX=" + translateX
            + ", translateY=" + translateY
            + ", rotateDegrees=" + rotateDegrees
            + ", scaleX=" + scaleX
            + ", scaleY=" + scaleY
            + ", originXRatio=" + originXRatio
            + ", originYRatio=" + originYRatio
            + '}';
    }
}
