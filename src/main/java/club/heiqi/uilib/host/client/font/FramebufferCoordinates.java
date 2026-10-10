package club.heiqi.uilib.host.client.font;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;

/** scene契约仅原生framebuffer像素；raw Mouse与GL scissor的Y方向在宿主边界转换一次。 */
public final class FramebufferCoordinates {
    private FramebufferCoordinates() {}

    public static int mouseY(int framebufferHeight, int rawY) {
        return Math.toIntExact((long) framebufferHeight - rawY - 1);
    }

    public static float[] orthographic(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("framebuffer不可为空");
        return new float[] {2f / width, 0, 0, 0, 0, -2f / height, 0, 0, 0, 0, 1, 0, -1, 1, 0, 1};
    }

    public static LayoutBox viewportClip(int width, int height, boolean scissor, int[] box) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("无效framebuffer尺寸");
        if (!scissor) return new LayoutBox(0, 0, width, height);
        long left = Math.max(0L, box[0]), right = Math.min((long) width, (long) box[0] + box[2]);
        long top = Math.max(0L, (long) height - box[1] - box[3]),
                bottom = Math.min((long) height, (long) height - box[1]);
        // 完全在framebuffer外仍产生可表达的空clip，不引入负尺寸或整数溢出。
        int x = (int) Math.min(width, left), y = (int) Math.min(height, top);
        return new LayoutBox(
                x, y, (int) Math.max(0, right - left), (int) Math.max(0, bottom - top));
    }
}
