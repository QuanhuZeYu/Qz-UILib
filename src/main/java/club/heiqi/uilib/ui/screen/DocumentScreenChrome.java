package club.heiqi.uilib.ui.screen;

/**
 * 文档页面宿主壳的尺寸策略。
 *
 * <p>本类型只承接屏幕级 root/page 留白公式，不携带任何全局上下文。</p>
 */
public final class DocumentScreenChrome {

    private final Insets rootPadding;
    private final Insets pagePadding;

    private DocumentScreenChrome(Insets rootPadding, Insets pagePadding) {
        this.rootPadding = rootPadding;
        this.pagePadding = pagePadding;
    }

    /**
     * 基于当前屏幕尺寸解析文档壳留白。
     *
     * @param width 屏幕宽度
     * @param height 屏幕高度
     * @return 解析后的文档壳
     */
    public static DocumentScreenChrome resolve(int width, int height) {
        int pageMargin = Math.max(24, width / 34);
        int topMargin = Math.max(28, height / 28);
        Insets rootPadding = Insets.of(pageMargin, topMargin, pageMargin, pageMargin);

        int pagePaddingX = clampValue(width / 48, 16, 28);
        int pagePaddingY = clampValue(height / 36, 14, 24);
        Insets pagePadding = Insets.of(pagePaddingX, pagePaddingY, pagePaddingX, pagePaddingY);
        return new DocumentScreenChrome(rootPadding, pagePadding);
    }

    /**
     * 创建填满宿主视口的无留白文档壳。
     *
     * <p>业务文档 screen 默认不套诊断页外边距，页面自己的留白应通过根元素样式声明。</p>
     *
     * @param width 屏幕宽度
     * @param height 屏幕高度
     * @return 无留白文档壳
     */
    public static DocumentScreenChrome fillViewport(int width, int height) {
        return new DocumentScreenChrome(Insets.of(0, 0, 0, 0), Insets.of(0, 0, 0, 0));
    }

    /**
     * 获取根视口留白。
     *
     * @return 根留白
     */
    public Insets getRootPadding() {
        return rootPadding;
    }

    /**
     * 获取页面壳留白。
     *
     * @return 页面壳留白
     */
    public Insets getPagePadding() {
        return pagePadding;
    }

    private static int clampValue(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * 小型不可变边距值对象。
     */
    public static final class Insets {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private Insets(int left, int top, int right, int bottom) {
            this.left = Math.max(0, left);
            this.top = Math.max(0, top);
            this.right = Math.max(0, right);
            this.bottom = Math.max(0, bottom);
        }

        public static Insets of(int left, int top, int right, int bottom) {
            return new Insets(left, top, right, bottom);
        }

        public int getLeft() {
            return left;
        }

        public int getTop() {
            return top;
        }

        public int getRight() {
            return right;
        }

        public int getBottom() {
            return bottom;
        }
    }
}
