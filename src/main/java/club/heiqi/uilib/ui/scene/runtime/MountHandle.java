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
 *   <li>{@link #dispose()}：卸载组件、递归回收该作用域内所有 effect 并自动从父节点摘除（I3）</li>
 * </ul>
 *
 * <h3>为什么字号入口在句柄上</h3>
 * <p>字号是节点的 LAYOUT+PAINT 属性，与 padding / 尺寸同类（契约 §4 属性归属表同一行），不是控件参数。
 * 控件 wrapper 节点是「控件内文字」的字号真值——控件自己画的文字（内部 label / caret / 文本度量）跟随它，
 * 业务方 {@code appendChild} 进来的子控件是独立控件、各管各的（不做子树继承）。该口径对齐 Qt
 * {@code QWidget::setFont} / Swing {@code JComponent::setFont}：字号是基类属性，在基类做一次，
 * 而不是给每个控件各开一个入口。</p>
 *
 * <p>因此这里给出全控件统一的字号入口——任何控件写法相同：
 * {@code rt.mount(parent, SceneToast.create(rt, props)).fontSize(14)}。部分高频控件另有
 * {@code Props.fontSize} / {@code Builder.fontSizePx} 构建期语法糖，写的是同一个 root，不存在第二套真值。</p>
 */
public class MountHandle {

    private final Owner scope;
    private final SceneNode root;

    MountHandle(Owner scope, SceneNode root) {
        this.scope = scope;
        this.root = root;
    }

    /** @return 组件产出的根节点 */
    public SceneNode getRoot() {
        return root;
    }

    /**
     * 设置控件内文字字号（构建期定值）。
     *
     * <p>等价于 {@code getRoot().setFontSize(fontSizePx)}；控件自己画的文字与按字号算出来的几何
     * 由控件实现负责跟随（这是控件的实现责任，不是调用方要操心的事）。</p>
     *
     * @param fontSizePx UI 像素字号
     * @return 本句柄（链式）
     */
    public MountHandle fontSize(int fontSizePx) {
        root.setFontSize(fontSizePx);
        return this;
    }

    /**
     * 设置控件内文字字号（运行时可调）。
     *
     * <p>先播种当前值保证首帧正确，再在挂载作用域内建立 effect——信号变化时同帧生效，
     * 组件卸载时随作用域一并退订（不会留下 orphan effect）。信号或其值为 null 时不写 root，
     * 节点保持既有字号。</p>
     *
     * @param fontSize UI 像素字号信号；null = 不指定
     * @return 本句柄（链式）
     */
    public MountHandle fontSize(ReadableSignal<Integer> fontSize) {
        if (fontSize == null) {
            return this;
        }
        applyFontSize(fontSize.get());
        scope.createEffect(() -> applyFontSize(fontSize.get()));
        return this;
    }

    /** 写入字号；值为 null 时不动 root（不以缺失值覆盖既有字号）。 */
    private void applyFontSize(Integer value) {
        if (value != null) {
            root.setFontSize(value.intValue());
        }
    }

    /**
     * 卸载组件：递归销毁该作用域内所有子 Owner / effect / cleanup 回调，
     * 并从父节点摘除根节点。
     */
    public void dispose() {
        scope.dispose();
    }
}
