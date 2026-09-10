package club.heiqi.uilib.ui.scene.runtime;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * 受控浮层 portal 句柄。
 *
 * <p>句柄持有 portal 自身 Owner。调用 {@link #dispose()} 会停止 visible effect，卸载当前 overlay root，
 * 并回收 overlay builder 内注册的 bind/on/effect；重复调用安全。</p>
 *
 * <h3>浮层字号入口</h3>
 * <p>portal 与挂载式控件不同：它没有在挂载时就存在的控件根，内容由 {@code visible} 驱动懒建。因此字号入口
 * 放在句柄上（{@link #fontSize(int)} / {@link #fontSize(ReadableSignal)}），与
 * {@link MountHandle#fontSize(int)} 对称——编写者写法一致：</p>
 * <pre>{@code
 * ScenePortalHandle handle = SceneDialog.create(rt, props);
 * handle.fontSize(14);            // 构建期
 * handle.fontSize(sizeSignal);    // 运行时可调
 * }</pre>
 * <p>字号是浮层内容根节点的 {@code SceneNode} 属性（与 padding/尺寸同类，不进主题通道）；内容里的自绘文字
 * 与按字号算出来的几何由各浮层实现负责跟随。信号为 null 或值为 null 时内容沿用节点默认字号。</p>
 */
public final class ScenePortalHandle {

    /** portal 生命周期根作用域。 */
    private final Owner portalOwner;
    /** 浮层内容字号（UI 像素）；null = 未指定，内容沿用节点默认字号。 */
    private final Signal<Integer> fontSizePx = Signal.create(null);

    /**
     * 构造 portal 句柄。
     *
     * @param portalOwner portal 生命周期根作用域，不可为 null
     */
    ScenePortalHandle(Owner portalOwner) {
        this.portalOwner = Objects.requireNonNull(portalOwner, "portalOwner");
    }

    /**
     * 设置浮层内容字号（构建期定值）。
     *
     * @param fontSizePx UI 像素字号
     * @return 本句柄（链式）
     */
    public ScenePortalHandle fontSize(int fontSizePx) {
        this.fontSizePx.set(Integer.valueOf(fontSizePx));
        return this;
    }

    /**
     * 设置浮层内容字号（运行时可调）。
     *
     * <p>桥接到本句柄的字号信号后，内容里的文字随信号变化；信号为 null 时不写，内容保持既有字号。</p>
     *
     * @param fontSize UI 像素字号信号；null = 不指定
     * @return 本句柄（链式）
     */
    public ScenePortalHandle fontSize(ReadableSignal<Integer> fontSize) {
        if (fontSize == null) {
            return this;
        }
        Integer initial = fontSize.get();
        if (initial != null) {
            fontSizePx.set(initial);
        }
        portalOwner.createEffect(() -> {
            Integer next = fontSize.get();
            if (next != null) {
                fontSizePx.set(next);
            }
        });
        return this;
    }

    /**
     * 内部通道：浮层内容构建时读取字号源（未指定时为 null，内容沿用节点默认字号）。
     *
     * @return 浮层字号只读信号
     */
    public ReadableSignal<Integer> __fontSize() {
        return fontSizePx;
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
