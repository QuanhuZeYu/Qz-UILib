package club.heiqi.uilib.ui.scene.overlay;

/**
 * 一次浮层注册的句柄。
 *
 * <p>句柄只负责从 {@link SceneOverlayHost} 摘除对应 entry。{@link #dispose()} 幂等，
 * 重复调用不会重复修改 host，也不会清理浮层子树 Owner。</p>
 */
public final class OverlayHandle {

    private final SceneOverlayHost host;
    private final SceneOverlayHost.Entry entry;
    private boolean disposed;

    OverlayHandle(SceneOverlayHost host, SceneOverlayHost.Entry entry) {
        this.host = host;
        this.entry = entry;
    }

    /**
     * 摘除本句柄对应的浮层 entry。
     *
     * <p>本方法幂等：首次调用会从 host 移除 entry，后续调用直接返回。</p>
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        host.remove(entry);
    }

    /** @return 当前句柄是否已释放 */
    public boolean isDisposed() {
        return disposed;
    }

    /** @return 本句柄对应的浮层 entry */
    public SceneOverlayHost.Entry getEntry() {
        return entry;
    }
}
