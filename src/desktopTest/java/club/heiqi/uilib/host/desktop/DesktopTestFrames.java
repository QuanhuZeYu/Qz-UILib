package club.heiqi.uilib.host.desktop;

/** 正式帧驱动及字形连续稳定等待；等待上限只属于实机验收，不改变生产调度。 */
final class DesktopTestFrames {
    private DesktopTestFrames() {}
    static void frames(DesktopWindow window, int count) {
        for (int i = 0; i < count; i++) {
            if (window.shouldClose()) throw new AssertionError("验收窗口提前关闭");
            window.frame();
        }
    }
    static void settle(DesktopWindow window) {
        long deadline = System.nanoTime() + 5000000000L;
        int stable = 0;
        do {
            frames(window, 1);
            if (window.fonts.rasterizer().stats().pending == 0
                    && window.fonts.rasterizer().stats().ready == 0
                    && window.fonts.presentationStats().uploadCalls == 0) stable++;
            else stable = 0;
            if (stable >= 4) break;
        } while (System.nanoTime() < deadline);
        if (stable < 4 || window.fonts.rasterizer().stats().errors != 0
                || window.fonts.rasterizer().workerFailure() != null)
            throw new AssertionError("可见字形未连续稳定或存在raster错误");
    }
}
