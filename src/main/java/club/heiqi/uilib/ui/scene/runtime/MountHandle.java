package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 组件挂载句柄，由 {@link SceneRuntime#mount} 返回。
 *
 * <p>持有挂载作用域与根节点引用，提供：
 * <ul>
 *   <li>{@link #getRoot()}：获取组件产出的根 {@link SceneNode}</li>
 *   <li>{@link #fontSize(int)} / {@link #fontSize(ReadableSignal)}：控件字号的统一入口</li>
 *   <li>{@link #clearFontSize()}：撤回本句柄的字号声明（目标回落下一层）</li>
 *   <li>{@link #dispose()}：卸载组件、递归回收该作用域内所有 effect 并自动从父节点摘除（I3）</li>
 * </ul>
 *
 * <h3>入口语义（契约 §4「控件字号入口」）</h3>
 * <p>字号真值是「一条解析链 + 一个声明槽」：本句柄把声明写到<b>控件根节点的层 2 槽</b>
 * （{@link SceneNode#setFontScope(int)}），由 {@link FontSizeBinding} 承担
 * <b>唯一 effect + 幂等替换</b>——重复调用不叠加订阅（R1），{@code int} 调用释放既有信号订阅
 * （后写者胜出，R2），{@code int} 同步生效、{@code signal} 同步播种后随帧末（R4），
 * 参数 {@code null} 为完全 no-op（R5）。</p>
 *
 * <p>层 2 声明沿父链继承：控件自己画的文字、按字号算出的几何，以及业务 {@code appendChild}
 * 进来的未声明子节点都跟随；需要切断继承时在目标节点<b>再声明一层</b>（层 1/层 2），
 * 而不是"取消继承"。</p>
 *
 * <p>{@link #dispose()} 之后任何写入口抛 {@link IllegalStateException}（R8 快速失败）；
 * {@link #getRoot()} / {@link #isDisposed()} 等只读入口仍合法。</p>
 */
public class MountHandle {

    private final Owner scope;
    private final SceneNode root;
    /** 本入口唯一的声明绑定（唯一 effect + 幂等替换）。 */
    private final FontSizeBinding fontBinding;

    MountHandle(Owner scope, SceneNode root) {
        this.scope = scope;
        this.root = root;
        this.fontBinding = new FontSizeBinding(scope, () -> root);
    }

    /** @return 组件产出的根节点 */
    public SceneNode getRoot() {
        return root;
    }

    /**
     * 设置控件字号（构建期定值，同步生效）。
     *
     * <p>写层 2 声明（{@link SceneNode#setFontScope(int)}）并<b>释放此前 bind 过的信号订阅</b>
     * ——后写者胜出：一旦写入定值，旧信号不再影响本入口。</p>
     *
     * @param fontSizePx UI 逻辑像素字号；越界抛 {@link IllegalArgumentException}
     * @return 本句柄（链式）
     * @throws IllegalArgumentException 字号越界（合法区间见 FontSizeLimits）
     * @throws IllegalStateException    本句柄已 {@link #dispose()}
     */
    public MountHandle fontSize(int fontSizePx) {
        requireAlive();
        fontBinding.set(fontSizePx);
        return this;
    }

    /**
     * 设置控件字号（运行时可调）。
     *
     * <p>先同步播种当前值保证首帧正确，再在挂载作用域内建立<b>唯一</b> effect——重复调用为替换语义，
     * 不叠加订阅；组件卸载时随作用域一并退订。信号值 {@code null} = 该声明缺失（回落下一层），
     * 参数 {@code null} = 完全不指定（no-op，不动既有声明与订阅）。</p>
     *
     * @param fontSize UI 逻辑像素字号信号；null = 不指定
     * @return 本句柄（链式）
     * @throws IllegalStateException 本句柄已 {@link #dispose()}
     */
    public MountHandle fontSize(ReadableSignal<Integer> fontSize) {
        requireAlive();
        fontBinding.bind(fontSize);
        return this;
    }

    /**
     * 撤回本句柄的字号声明（幂等）：控件根回落更远祖先的作用域声明 / runtime 默认 / 自有回落值。
     *
     * @return 本句柄（链式）
     * @throws IllegalStateException 本句柄已 {@link #dispose()}
     */
    public MountHandle clearFontSize() {
        requireAlive();
        fontBinding.clear();
        return this;
    }

    /** @return 本句柄是否已释放（{@link #dispose()} 后为 true） */
    public boolean isDisposed() {
        return scope.isDisposed();
    }

    /**
     * 卸载组件：递归销毁该作用域内所有子 Owner / effect / cleanup 回调，
     * 并从父节点摘除根节点。
     */
    public void dispose() {
        scope.dispose();
    }

    /** R8：已释放句柄上的写入口快速失败（不再静默 no-op）。 */
    private void requireAlive() {
        if (scope.isDisposed()) {
            throw new IllegalStateException("MountHandle 已 dispose：fontSize/clearFontSize 不可调用");
        }
    }
}
