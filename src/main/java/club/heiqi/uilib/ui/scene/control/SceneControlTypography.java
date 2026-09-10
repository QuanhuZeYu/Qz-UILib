package club.heiqi.uilib.ui.scene.control;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
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

    private SceneControlTypography(SceneRuntime rt, SceneNode root) {
        this.rt = rt;
        this.root = root;
        this.owner = Owner.current();
        // 纯文本控件只需传播字号；默认 SceneRuntime 可由外部布局/绘制引擎提供度量，
        // Button 挂载不应因此新增对 runtime 度量器的要求。
        this.fontSize = Computed.create(Integer.valueOf(root.getFontSize()), () -> {
            rt.layoutDoneSignal().get();
            return Integer.valueOf(root.getFontSize());
        });
    }

    static SceneControlTypography attach(SceneRuntime rt, SceneNode root) {
        if (Owner.current() != null) {
            return new SceneControlTypography(rt, root);
        }
        // 直接调用 primitive 工厂时与 rt.bind 一样归 runtime 根 Owner，避免独立 Computed 泄漏。
        SceneControlTypography[] result = {null};
        rt.__runRoot(() -> result[0] = new SceneControlTypography(rt, root));
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
