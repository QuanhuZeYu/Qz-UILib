package club.heiqi.uilib.ui.scene.control;

/** 局部内容视口的纯范围运算；位置仍由所属控件管理，不参与输入路由。 */
final class SceneControlViewport {
    private SceneControlViewport() {}

    static int clamp(int offset, int maximum) {
        return Math.max(0, Math.min(offset, maximum));
    }

    static int scrolled(int offset, int maximum, int delta, int step) {
        return (int) Math.max(0L, Math.min((long) maximum, offset - (long) delta * step));
    }
}
