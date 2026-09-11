package club.heiqi.uilib.ui.scene.runtime;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 受控浮层 portal 句柄。
 *
 * <p>句柄持有 portal 自身 Owner。调用 {@link #dispose()} 会停止 visible effect，卸载当前 overlay root，
 * 并回收 overlay builder 内注册的 bind/on/effect；重复调用安全。</p>
 *
 * <h3>浮层字号入口</h3>
 * <p>portal 没有在挂载时就存在的控件根（内容由 {@code visible} 驱动懒建），因此字号入口落在句柄上
 * （{@link #fontSize(int)} / {@link #fontSize(ReadableSignal)}），与 {@link MountHandle#fontSize(int)}
 * 对称——编写者写法一致（口径也一致：<b>同一条父链解析链，不是第二套真值</b>）：</p>
 * <pre>{@code
 * ScenePortalHandle handle = SceneDialog.create(rt, props);
 * handle.fontSize(14);            // 构建期
 * handle.fontSize(sizeSignal);    // 运行时可调
 * }</pre>
 *
 * <p><b>声明落点 = 浮层内容根节点的层 2</b>：内容懒建完成后，{@code ScenePortalRenderer} 经
 * {@link #__onContentRoot(SceneNode)} 把声明写到内容根（{@link SceneNode#setFontScope(int)}，
 * 不是层 1 显式值）；内容里的自绘文字与按字号算出的几何（{@code setFontSizeMetric}）沿父链继承/重算，
 * 无需逐点接线。内容卸载时回调 {@code null}，声明留在句柄里（{@code FontSizeBinding} 的目标可重定向），
 * 下次打开自动补落。</p>
 *
 * <p><b>不存在独立浮层通道</b>：入口对象只持一个 {@code FontSizeBinding}（唯一 effect + 幂等替换），
 * 内部通道 {@code __fontSize}（旧侧信道）已删除（改由 {@link #__onContentRoot(SceneNode)} +
 * {@code FontSizeBinding.retarget} 在挂载边界补落；源码守卫钉住其不得回归）。</p>
 *
 * <p>入口语义与 {@link MountHandle} 完全一致：唯一 effect + 幂等替换、{@code int} 同步且释放旧订阅、
 * {@code signal} 同步播种、参数 {@code null} 为 no-op；{@link #dispose()} 之后写入口抛
 * {@link IllegalStateException}。</p>
 */
public final class ScenePortalHandle {

    /** portal 生命周期根作用域。 */
    private final Owner portalOwner;

    /** 本入口唯一的声明绑定（唯一 effect + 幂等替换）。 */
    private final FontSizeBinding fontBinding;

    /** 当前浮层内容根；null = 尚未构建（声明已记下，内容出现时补落）。 */
    private SceneNode contentRoot;

    /**
     * 构造 portal 句柄。
     *
     * @param portalOwner portal 生命周期根作用域，不可为 null
     */
    ScenePortalHandle(Owner portalOwner) {
        this.portalOwner = Objects.requireNonNull(portalOwner, "portalOwner");
        this.fontBinding = new FontSizeBinding(portalOwner, () -> contentRoot);
    }

    /**
     * 设置浮层内容字号（构建期定值，同步生效）。
     *
     * @param fontSizePx UI 逻辑像素字号；越界抛 {@link IllegalArgumentException}
     * @return 本句柄（链式）
     * @throws IllegalArgumentException 字号越界（合法区间见 FontSizeLimits）
     * @throws IllegalStateException    本句柄已 {@link #dispose()}
     */
    public ScenePortalHandle fontSize(int fontSizePx) {
        requireAlive();
        fontBinding.set(fontSizePx);
        return this;
    }

    /**
     * 设置浮层内容字号（运行时可调；幂等替换，不叠加 effect）。
     *
     * @param fontSize UI 逻辑像素字号信号；null = 不指定
     * @return 本句柄（链式）
     * @throws IllegalStateException 本句柄已 {@link #dispose()}
     */
    public ScenePortalHandle fontSize(ReadableSignal<Integer> fontSize) {
        requireAlive();
        fontBinding.bind(fontSize);
        return this;
    }

    /**
     * 撤回本句柄的字号声明（幂等）：内容根回落更远祖先 / runtime 默认 / 自有回落值。
     *
     * @return 本句柄（链式）
     * @throws IllegalStateException 本句柄已 {@link #dispose()}
     */
    public ScenePortalHandle clearFontSize() {
        requireAlive();
        fontBinding.clear();
        return this;
    }

    /**
     * 内部通道：浮层内容构建边界回调（由 {@code ScenePortalRenderer} 在内容挂/卸时调用一次）。
     *
     * <p>内容出现时把已记下的层 2 声明补落到新内容根；内容卸载时清空目标引用（声明保留，
     * 下次打开补落）。双下划线表示 internal bridge，业务不调用。</p>
     *
     * @param root 新的内容根；null = 内容已卸载
     */
    void __onContentRoot(SceneNode root) {
        this.contentRoot = root;
        fontBinding.retarget(root);
    }

    /** 停止 portal 响应并移除当前浮层。 */
    public void dispose() {
        portalOwner.dispose();
    }

    /** @return portal 是否已释放 */
    public boolean isDisposed() {
        return portalOwner.isDisposed();
    }

    /** R8：已释放句柄上的写入口快速失败。 */
    private void requireAlive() {
        if (portalOwner.isDisposed()) {
            throw new IllegalStateException(
                    "ScenePortalHandle 已 dispose：fontSize/clearFontSize 不可调用");
        }
    }
}
