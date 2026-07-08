package club.heiqi.uilib.ui.base.cascade;

import club.heiqi.uilib.ui.layout.DocumentLayoutEdges;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.values.UiBorderRadius;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 分角圆角解析工具。
 */
public final class UiBorderRadiusResolver {

    private UiBorderRadiusResolver() {}

    /**
     * 已解析的四角圆角值。
     */
    public static final class ResolvedCornerRadii {

        private final int topLeft;
        private final int topRight;
        private final int bottomRight;
        private final int bottomLeft;

        private ResolvedCornerRadii(int topLeft, int topRight, int bottomRight, int bottomLeft) {
            this.topLeft = Math.max(0, topLeft);
            this.topRight = Math.max(0, topRight);
            this.bottomRight = Math.max(0, bottomRight);
            this.bottomLeft = Math.max(0, bottomLeft);
        }

        /**
         * 创建四角值。
         *
         * @param topLeft 左上角
         * @param topRight 右上角
         * @param bottomRight 右下角
         * @param bottomLeft 左下角
         * @return 已解析四角值
         */
        public static ResolvedCornerRadii of(int topLeft, int topRight, int bottomRight, int bottomLeft) {
            return new ResolvedCornerRadii(topLeft, topRight, bottomRight, bottomLeft);
        }

        /**
         * 创建统一值。
         *
         * @param radius 统一半径
         * @return 已解析四角值
         */
        public static ResolvedCornerRadii uniform(int radius) {
            int resolved = Math.max(0, radius);
            return new ResolvedCornerRadii(resolved, resolved, resolved, resolved);
        }

        public int getTopLeft() {
            return topLeft;
        }

        public int getTopRight() {
            return topRight;
        }

        public int getBottomRight() {
            return bottomRight;
        }

        public int getBottomLeft() {
            return bottomLeft;
        }

        /**
         * 判断四角是否一致。
         *
         * @return 是否统一
         */
        public boolean isUniform() {
            return topLeft == topRight && topRight == bottomRight && bottomRight == bottomLeft;
        }

        /**
         * 返回统一值（仅当四角相同有意义）。
         *
         * @return 统一圆角值
         */
        public int getUniformRadius() {
            return topLeft;
        }

        /**
         * 返回放缩后的四角值。
         *
         * @param scale 放缩系数
         * @return 放缩后值
         */
        public ResolvedCornerRadii scale(float scale) {
            if (scale >= 0.999F) {
                return this;
            }
            return new ResolvedCornerRadii(Math.round(topLeft * scale), Math.round(topRight * scale),
                    Math.round(bottomRight * scale), Math.round(bottomLeft * scale));
        }

        /**
         * 四角同时向外扩展。
         *
         * @param amount 扩展量
         * @return 扩展后的圆角
         */
        public ResolvedCornerRadii outset(int amount) {
            int resolvedAmount = Math.max(0, amount);
            return new ResolvedCornerRadii(topLeft + resolvedAmount, topRight + resolvedAmount,
                    bottomRight + resolvedAmount, bottomLeft + resolvedAmount);
        }

        /**
         * 对四角减去边框厚度，得到内侧圆角。
         *
         * @param border 边框厚度
         * @return 内侧圆角
         */
        public ResolvedCornerRadii inset(DocumentLayoutEdges border) {
            if (border == null) {
                return this;
            }
            return new ResolvedCornerRadii(
                    Math.max(0, topLeft - Math.max(border.getLeft(), border.getTop())),
                    Math.max(0, topRight - Math.max(border.getRight(), border.getTop())),
                    Math.max(0, bottomRight - Math.max(border.getRight(), border.getBottom())),
                    Math.max(0, bottomLeft - Math.max(border.getLeft(), border.getBottom())));
        }
    }

    /**
     * 解析元素分角圆角。
     *
     * @param style 计算样式
     * @param width 盒宽
     * @param height 盒高
     * @return 已解析圆角
     */
    public static ResolvedCornerRadii resolve(ComputedStyle style, int width, int height) {
        return resolve(style, width, height, null);
    }

    /**
     * 解析元素分角圆角，允许用统一动画值覆盖。
     *
     * @param style 计算样式
     * @param width 盒宽
     * @param height 盒高
     * @param animatedUniformRadius 统一动画值；为 null 时不覆盖
     * @return 已解析圆角
     */
    public static ResolvedCornerRadii resolve(ComputedStyle style, int width, int height,
            Integer animatedUniformRadius) {
        if (style == null) {
            return ResolvedCornerRadii.uniform(0);
        }
        int safeWidth = Math.max(0, width);
        int safeHeight = Math.max(0, height);
        int limit = Math.min(safeWidth, safeHeight);
        if (animatedUniformRadius != null) {
            return scaleToFit(ResolvedCornerRadii.uniform(animatedUniformRadius.intValue()), safeWidth, safeHeight);
        }
        UiBorderRadius borderRadiusCorners = style.getBorderRadiusCorners();
        ResolvedCornerRadii resolved = borderRadiusCorners != null
                ? ResolvedCornerRadii.of(resolveLength(borderRadiusCorners.getTopLeft(), limit),
                        resolveLength(borderRadiusCorners.getTopRight(), limit),
                        resolveLength(borderRadiusCorners.getBottomRight(), limit),
                        resolveLength(borderRadiusCorners.getBottomLeft(), limit))
                : ResolvedCornerRadii.uniform(resolveLength(style.getBorderRadius(), limit));
        return scaleToFit(resolved, safeWidth, safeHeight);
    }

    /**
     * 将分角圆角限制在当前盒尺寸内。
     *
     * @param radii 圆角值
     * @param width 盒宽
     * @param height 盒高
     * @return 放缩后的圆角
     */
    public static ResolvedCornerRadii scaleToFit(ResolvedCornerRadii radii, int width, int height) {
        if (radii == null) {
            return ResolvedCornerRadii.uniform(0);
        }
        int safeWidth = Math.max(0, width);
        int safeHeight = Math.max(0, height);
        if (safeWidth <= 0 || safeHeight <= 0) {
            return ResolvedCornerRadii.uniform(0);
        }
        float scale = 1.0F;
        int topSum = radii.topLeft + radii.topRight;
        int bottomSum = radii.bottomLeft + radii.bottomRight;
        int leftSum = radii.topLeft + radii.bottomLeft;
        int rightSum = radii.topRight + radii.bottomRight;
        if (topSum > safeWidth && topSum > 0) {
            scale = Math.min(scale, safeWidth / (float) topSum);
        }
        if (bottomSum > safeWidth && bottomSum > 0) {
            scale = Math.min(scale, safeWidth / (float) bottomSum);
        }
        if (leftSum > safeHeight && leftSum > 0) {
            scale = Math.min(scale, safeHeight / (float) leftSum);
        }
        if (rightSum > safeHeight && rightSum > 0) {
            scale = Math.min(scale, safeHeight / (float) rightSum);
        }
        return radii.scale(scale);
    }

    /**
     * 解析长度到像素。
     *
     * @param length 样式长度
     * @param availableSpace 可用空间
     * @return 像素值
     */
    public static int resolveLength(UiStyleLength length, int availableSpace) {
        if (length == null) {
            return 0;
        }
        return Math.max(0, length.resolve(Math.max(0, availableSpace), 0));
    }
}
