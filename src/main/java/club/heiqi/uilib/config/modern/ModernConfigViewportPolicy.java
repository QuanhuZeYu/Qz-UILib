package club.heiqi.uilib.config.modern;

/** 现代配置宿主与 headless 契约测试共享的 viewport 策略。 */
final class ModernConfigViewportPolicy {

    static final int MIN_PANEL_HEIGHT = 360;

    static final int MIN_WORLD_REVEAL = 96;

    private static final float WORLD_PANEL_RATIO = 0.8f;

    private ModernConfigViewportPolicy() {
    }

    static boolean shouldRenderDefaultBackground(boolean worldOpen) {
        return !worldOpen;
    }

    static int resolveSurfaceHeight(int framebufferHeight, boolean worldOpen) {
        int height = Math.max(0, framebufferHeight);
        if (!worldOpen || height < MIN_PANEL_HEIGHT + MIN_WORLD_REVEAL) {
            return height;
        }
        int proportionalHeight = Math.round(height * WORLD_PANEL_RATIO);
        return Math.max(MIN_PANEL_HEIGHT, Math.min(proportionalHeight, height - MIN_WORLD_REVEAL));
    }
}
