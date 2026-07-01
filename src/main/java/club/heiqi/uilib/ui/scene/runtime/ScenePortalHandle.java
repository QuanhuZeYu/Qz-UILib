package club.heiqi.uilib.ui.scene.runtime;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.Owner;

/**
 * 受控浮层 portal 句柄。
 *
 * <p>句柄持有 portal 自身 Owner。调用 {@link #dispose()} 会停止 visible effect，卸载当前 overlay root，
 * 并回收 overlay builder 内注册的 bind/on/effect；重复调用安全。</p>
 */
public final class ScenePortalHandle {

    /** portal 生命周期根作用域。 */
    private final Owner portalOwner;

    /**
     * 构造 portal 句柄。
     *
     * @param portalOwner portal 生命周期根作用域，不可为 null
     */
    ScenePortalHandle(Owner portalOwner) {
        this.portalOwner = Objects.requireNonNull(portalOwner, "portalOwner");
    }

    /** 停止 portal 响应并移除当前浮层。 */
    public void dispose() {
        portalOwner.dispose();
    }

    /** @return portal 是否已释放 */
    public boolean isDisposed() {
        return portalOwner.isDisposed();
    }
}
