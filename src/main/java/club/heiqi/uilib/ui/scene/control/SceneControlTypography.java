package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 控件内部文本与 caret 的统一字体策略：root 是唯一字号真值，度量来自 SceneRuntime。
 *
 * <p>在 primitive 的 mount Owner 内 attach；动态行在各自 Owner 内绑定叶节点，
 * 卸载时由既有 Computed / rt.bind 生命周期退订，不持有跨行节点列表。</p>
 */
final class SceneControlTypography {

    @Desugar
    record Metrics(int fontSizePx, int lineHeightPx, int measureEpoch) {
    }

    private final SceneRuntime rt;
    private final SceneNode root;
    private final Owner owner;
    private final ReadableSignal<Integer> fontSize;
    private ReadableSignal<Metrics> metrics;

    private SceneControlTypography(SceneRuntime rt, SceneNode root, ReadableSignal<Integer> fontSizeSource) {
        this.rt = rt;
        this.root = root;
        this.owner = Owner.current();
        if (fontSizeSource != null && !root.isFontSizeExplicit()) {
            // 主题供值：构造期在非追踪上下文播种，避免在 effect 体内 attach 时把来源登记为外层 effect 的依赖。
            final int[] holder = new int[1];
            Effect.untrack(() -> holder[0] = Objects.requireNonNull(fontSizeSource.get(),
                    "fontSizeSource value").intValue());
            root.__writeThemeFontSize(holder[0]);
        }
        // 纯文本控件只需传播字号；默认 SceneRuntime 可由外部布局/绘制引擎提供度量，
        // Button 挂载不应因此新增对 runtime 度量器的要求。
        // root 始终是唯一字号真值：主题供值与控件自身写入都经它生效。
        this.fontSize = Computed.create(Integer.valueOf(root.getFontSize()), () -> {
            rt.layoutDoneSignal().get();
            return Integer.valueOf(root.getFontSize());
        });
        if (fontSizeSource != null) {
            // 只在控件尚未自己设过字号时跟随主题：setFontSize 一经调用，主题即不再覆盖本节点。
            rt.bind(fontSizeSource, current -> {
                if (!root.isFontSizeExplicit()) {
                    root.__writeThemeFontSize(current.intValue());
                }
            });
        }
    }

    static SceneControlTypography attach(SceneRuntime rt, SceneNode root) {
        return attach(rt, root, null);
    }

    /**
     * 带主题字号来源的挂载：{@code fontSizeSource} 非 null 时由它供值，但<b>节点自身的显式字号优先</b>。
     *
     * <p>语义与表面配方的「显式优先」同口径：root 始终是唯一字号真值（{@code fontSize} 派生追踪 root），
     * 主题只在节点<b>尚未被 {@link SceneNode#setFontSize} 写过</b>时替它播种并跟随；控件一旦自己设过字号，
     * 主题即不再覆盖。因此既有的「外部写 root → 文本/度量/caret 跟随」契约不变。</p>
     *
     * <p>传 null 等价于 {@link #attach(SceneRuntime, SceneNode)}。</p>
     *
     * @param fontSizeSource 主题字号来源（如 {@code SceneThemes.fontSize(rt, FontSlot.BASE)}）；可为 null，
     *                       非 null 时其构造期值不得为 null
     */
    static SceneControlTypography attach(SceneRuntime rt, SceneNode root, ReadableSignal<Integer> fontSizeSource) {
        if (Owner.current() != null) {
            return new SceneControlTypography(rt, root, fontSizeSource);
        }
        // 直接调用 primitive 工厂时与 rt.bind 一样归 runtime 根 Owner，避免独立 Computed 泄漏。
        SceneControlTypography[] result = {null};
        rt.__runRoot(() -> result[0] = new SceneControlTypography(rt, root, fontSizeSource));
        return result[0];
    }

    ReadableSignal<Metrics> metrics() {
        if (metrics == null) {
            // 首次请求可能来自动态行；共享度量源必须归 attach 的 Owner，不能随该行卸载。
            owner.run(() -> metrics = Computed.create(sample(rt, root), () -> {
                rt.layoutDoneSignal().get();
                return sample(rt, root);
            }));
        }
        return metrics;
    }

    void bindText(SceneNode node) {
        node.setFontSize(fontSize.get().intValue());
        rt.bind(fontSize, current -> node.setFontSize(current.intValue()));
    }

    void bindCaret(SceneNode node) {
        ReadableSignal<Metrics> source = metrics();
        applyCaret(node, source.get());
        rt.bind(source, current -> applyCaret(node, current));
    }

    private static void applyCaret(SceneNode node, Metrics current) {
        node.setFontSize(current.fontSizePx());
        node.setPreferredHeight(current.lineHeightPx());
    }

    private static Metrics sample(SceneRuntime rt, SceneNode root) {
        int fontSizePx = root.getFontSize();
        return new Metrics(fontSizePx, rt.lineHeight(fontSizePx), rt.textMeasureEpoch());
    }
}
