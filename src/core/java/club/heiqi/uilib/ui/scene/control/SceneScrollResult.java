package club.heiqi.uilib.ui.scene.control;

/** 事件消费与视图变化是不同事实；端点保留消费不需要重排。 */
public enum SceneScrollResult {
    IGNORED(false, false), CONSUMED(true, false), CHANGED(true, true);
    public final boolean consumed, viewChanged;
    SceneScrollResult(boolean consumed, boolean viewChanged) { this.consumed = consumed; this.viewChanged = viewChanged; }
    public static SceneScrollResult moved(int pixels) { return pixels == 0 ? IGNORED : CHANGED; }
}
