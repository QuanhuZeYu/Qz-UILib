package club.heiqi.uilib.ui.base.values;

import club.heiqi.uilib.ui.style.values.UiStyleLength;

import java.util.Objects;

/**
 * CSS-like transform 值对象。
 *
 * <p>当前承载常用二维 transform 子属性：translate、scale、rotate 以及 transform-origin。
 * transform 只影响绘制与命中测试，不参与布局尺寸计算。</p>
 */
public final class UiTransform {

    private static final UiStyleLength DEFAULT_ORIGIN = UiStyleLength.percent(0.5F);
    private static final UiTransform IDENTITY = new UiTransform(0.0F, 0.0F, 1.0F, 1.0F, 0.0F,
            DEFAULT_ORIGIN, DEFAULT_ORIGIN);
    private static final float IDENTITY_EPSILON = 0.000001F;

    private final float translateX;
    private final float translateY;
    private final float scaleX;
    private final float scaleY;
    private final float rotateDegrees;
    private final UiStyleLength originX;
    private final UiStyleLength originY;

    private UiTransform(float translateX, float translateY, float scaleX, float scaleY, float rotateDegrees,
            UiStyleLength originX, UiStyleLength originY) {
        this.translateX = sanitize(translateX, 0.0F);
        this.translateY = sanitize(translateY, 0.0F);
        this.scaleX = sanitize(scaleX, 1.0F);
        this.scaleY = sanitize(scaleY, 1.0F);
        this.rotateDegrees = sanitize(rotateDegrees, 0.0F);
        this.originX = Objects.requireNonNull(originX, "originX");
        this.originY = Objects.requireNonNull(originY, "originY");
    }

    /**
     * 返回无变换值。
     *
     * @return 无变换值
     */
    public static UiTransform identity() {
        return IDENTITY;
    }

    /**
     * 创建 transform 值。
     *
     * @param translateX X 方向平移像素
     * @param translateY Y 方向平移像素
     * @param scaleX X 方向缩放倍率
     * @param scaleY Y 方向缩放倍率
     * @param rotateDegrees 顺时针旋转角度
     * @return transform 值
     */
    public static UiTransform of(float translateX, float translateY, float scaleX, float scaleY,
            float rotateDegrees) {
        return of(translateX, translateY, scaleX, scaleY, rotateDegrees, DEFAULT_ORIGIN, DEFAULT_ORIGIN);
    }

    /**
     * 创建 transform 值。
     *
     * @param translateX X 方向平移像素
     * @param translateY Y 方向平移像素
     * @param scaleX X 方向缩放倍率
     * @param scaleY Y 方向缩放倍率
     * @param rotateDegrees 顺时针旋转角度
     * @param originX 变换原点 X
     * @param originY 变换原点 Y
     * @return transform 值
     */
    public static UiTransform of(float translateX, float translateY, float scaleX, float scaleY,
            float rotateDegrees, UiStyleLength originX, UiStyleLength originY) {
        UiTransform transform = new UiTransform(translateX, translateY, scaleX, scaleY, rotateDegrees,
                originX, originY);
        return transform.isIdentity() && DEFAULT_ORIGIN.equals(originX) && DEFAULT_ORIGIN.equals(originY)
                ? IDENTITY : transform;
    }

    /**
     * 创建平移 transform。
     *
     * @param translateX X 方向平移像素
     * @param translateY Y 方向平移像素
     * @return transform 值
     */
    public static UiTransform translate(float translateX, float translateY) {
        return of(translateX, translateY, 1.0F, 1.0F, 0.0F);
    }

    /**
     * 创建缩放 transform。
     *
     * @param scaleX X 方向缩放倍率
     * @param scaleY Y 方向缩放倍率
     * @return transform 值
     */
    public static UiTransform scale(float scaleX, float scaleY) {
        return of(0.0F, 0.0F, scaleX, scaleY, 0.0F);
    }

    /**
     * 创建旋转 transform。
     *
     * @param rotateDegrees 顺时针旋转角度
     * @return transform 值
     */
    public static UiTransform rotate(float rotateDegrees) {
        return of(0.0F, 0.0F, 1.0F, 1.0F, rotateDegrees);
    }

    public float getTranslateX() {
        return translateX;
    }

    public float getTranslateY() {
        return translateY;
    }

    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public float getRotateDegrees() {
        return rotateDegrees;
    }

    public UiStyleLength getOriginX() {
        return originX;
    }

    public UiStyleLength getOriginY() {
        return originY;
    }

    /**
     * 设置平移分量。
     *
     * @param nextTranslateX X 方向平移像素
     * @param nextTranslateY Y 方向平移像素
     * @return 新 transform 值
     */
    public UiTransform withTranslate(float nextTranslateX, float nextTranslateY) {
        return of(nextTranslateX, nextTranslateY, scaleX, scaleY, rotateDegrees, originX, originY);
    }

    /**
     * 设置缩放分量。
     *
     * @param nextScaleX X 方向缩放倍率
     * @param nextScaleY Y 方向缩放倍率
     * @return 新 transform 值
     */
    public UiTransform withScale(float nextScaleX, float nextScaleY) {
        return of(translateX, translateY, nextScaleX, nextScaleY, rotateDegrees, originX, originY);
    }

    /**
     * 设置旋转分量。
     *
     * @param nextRotateDegrees 顺时针旋转角度
     * @return 新 transform 值
     */
    public UiTransform withRotate(float nextRotateDegrees) {
        return of(translateX, translateY, scaleX, scaleY, nextRotateDegrees, originX, originY);
    }

    /**
     * 设置变换原点。
     *
     * @param nextOriginX 变换原点 X
     * @param nextOriginY 变换原点 Y
     * @return 新 transform 值
     */
    public UiTransform withTransformOrigin(UiStyleLength nextOriginX, UiStyleLength nextOriginY) {
        return of(translateX, translateY, scaleX, scaleY, rotateDegrees,
                Objects.requireNonNull(nextOriginX, "nextOriginX"), Objects.requireNonNull(nextOriginY,
                        "nextOriginY"));
    }

    /**
     * 判断 transform 是否为无变换。
     *
     * @return 是否无变换
     */
    public boolean isIdentity() {
        return Math.abs(translateX) <= IDENTITY_EPSILON
                && Math.abs(translateY) <= IDENTITY_EPSILON
                && Math.abs(scaleX - 1.0F) <= IDENTITY_EPSILON
                && Math.abs(scaleY - 1.0F) <= IDENTITY_EPSILON
                && Math.abs(normalizeDegrees(rotateDegrees)) <= IDENTITY_EPSILON;
    }

    /**
     * 解析变换原点 X 坐标。
     *
     * @param width border box 宽度
     * @return 原点 X 偏移
     */
    public float resolveOriginX(int width) {
        return originX.resolve(Math.max(0, width), Math.max(0, width) / 2);
    }

    /**
     * 解析变换原点 Y 坐标。
     *
     * @param height border box 高度
     * @return 原点 Y 偏移
     */
    public float resolveOriginY(int height) {
        return originY.resolve(Math.max(0, height), Math.max(0, height) / 2);
    }

    /**
     * 将视觉坐标反向映射回未变换坐标。
     *
     * @param x 视觉 X
     * @param y 视觉 Y
     * @param left border box 左边界
     * @param top border box 上边界
     * @param width border box 宽度
     * @param height border box 高度
     * @return 未变换坐标；不可逆时返回 null
     */
    public Point inverseTransformPoint(float x, float y, float left, float top, int width, int height) {
        if (Math.abs(scaleX) <= IDENTITY_EPSILON || Math.abs(scaleY) <= IDENTITY_EPSILON) {
            return null;
        }
        float originAbsX = left + resolveOriginX(width);
        float originAbsY = top + resolveOriginY(height);
        double radians = Math.toRadians(rotateDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float translatedX = x - originAbsX - translateX;
        float translatedY = y - originAbsY - translateY;
        float rotatedX = translatedX * cos + translatedY * sin;
        float rotatedY = -translatedX * sin + translatedY * cos;
        return new Point(originAbsX + rotatedX / scaleX, originAbsY + rotatedY / scaleY);
    }

    /**
     * 判断两个 transform 在绘制语义上是否相同。
     *
     * @param other 另一个 transform
     * @return 是否相同
     */
    public boolean sameTransform(UiTransform other) {
        return equals(other);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UiTransform)) {
            return false;
        }
        UiTransform other = (UiTransform) obj;
        return Float.compare(translateX, other.translateX) == 0
                && Float.compare(translateY, other.translateY) == 0
                && Float.compare(scaleX, other.scaleX) == 0
                && Float.compare(scaleY, other.scaleY) == 0
                && Float.compare(rotateDegrees, other.rotateDegrees) == 0
                && originX.equals(other.originX)
                && originY.equals(other.originY);
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(translateX);
        result = 31 * result + Float.floatToIntBits(translateY);
        result = 31 * result + Float.floatToIntBits(scaleX);
        result = 31 * result + Float.floatToIntBits(scaleY);
        result = 31 * result + Float.floatToIntBits(rotateDegrees);
        result = 31 * result + originX.hashCode();
        result = 31 * result + originY.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "UiTransform{"
                + "translateX=" + translateX
                + ", translateY=" + translateY
                + ", scaleX=" + scaleX
                + ", scaleY=" + scaleY
                + ", rotateDegrees=" + rotateDegrees
                + ", originX=" + originX
                + ", originY=" + originY
                + '}';
    }

    private static float sanitize(float value, float fallback) {
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : value;
    }

    private static float normalizeDegrees(float degrees) {
        float normalized = degrees % 360.0F;
        if (normalized > 180.0F) {
            normalized -= 360.0F;
        } else if (normalized < -180.0F) {
            normalized += 360.0F;
        }
        return normalized;
    }

    /**
     * 二维坐标点。
     */
    public static final class Point {

        private final float x;
        private final float y;

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }
    }
}
