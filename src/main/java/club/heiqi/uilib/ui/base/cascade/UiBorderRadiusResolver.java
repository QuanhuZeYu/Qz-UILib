package club.heiqi.uilib.ui.base.cascade;

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
        public ResolvedCornerRadii inset(int left, int top, int right, int bottom) {
            return new ResolvedCornerRadii(
                    Math.max(0, topLeft - Math.max(left, top)),
                    Math.max(0, topRight - Math.max(right, top)),
                    Math.max(0, bottomRight - Math.max(right, bottom)),
                    Math.max(0, bottomLeft - Math.max(left, bottom)));
        }
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

}
