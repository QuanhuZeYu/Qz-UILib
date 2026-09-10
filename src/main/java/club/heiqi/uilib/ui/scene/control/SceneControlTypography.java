package club.heiqi.uilib.ui.scene.control;

import java.util.concurrent.atomic.AtomicReference;

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
        this.fontSize = fontSizeOf(rt, root);
    }

    /**
     * 控件根字号的只读信号：追踪布局纪元后重读 root 字号。
     *
     * <p>给「文字节点建在辅助方法里、拿不到本实例」的控件用——把这条信号当参数传下去，
     * 各文字节点用 {@link #applyFontSize} 绑定即可，不必逐层传递本实例。</p>
     *
     * <p>必须在 Owner 作用域内调用（{@link Computed} 归属该作用域）。</p>
     *
     * @param rt   场景运行时
     * @param root 控件根节点（字号真值所在）
     * @return 根字号只读信号
     */
    static ReadableSignal<Integer> fontSizeOf(SceneRuntime rt, SceneNode root) {
        if (Owner.current() != null) {
            return createFontSize(rt, root);
        }
        // 在 mount 回调之外调用控件工厂时（如 portal 型控件的 create 方法体）与 rt.bind 一样
        // 归 runtime 根 Owner，避免独立 Computed 泄漏。
        AtomicReference<ReadableSignal<Integer>> holder = new AtomicReference<ReadableSignal<Integer>>();
        rt.__runRoot(() -> holder.set(createFontSize(rt, root)));
        return holder.get();
    }

    /** 纯文本控件只需传播字号；默认 SceneRuntime 可由外部布局/绘制引擎提供度量，挂载不应因此新增对度量器的要求。 */
    private static ReadableSignal<Integer> createFontSize(SceneRuntime rt, SceneNode root) {
        return Computed.create(Integer.valueOf(root.getFontSize()), () -> {
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

    /**
     * 内部绑定工具：把「控件根字号」信号绑到节点（构建期播种当前值 + 运行期绑定）。
     *
     * <p><b>编写者入口不在这里</b>——公开入口是
     * {@link club.heiqi.uilib.ui.scene.runtime.MountHandle#fontSize(int)} /
     * {@link club.heiqi.uilib.ui.scene.runtime.MountHandle#fontSize(ReadableSignal)}（或直接
     * {@code root.setFontSize(n)}）。本方法供**控件内部**把控件根字号传播到自绘文字节点。</p>
     *
     * <p>root 是本类的唯一字号真值，本方法只写 root；文本叶、caret 与度量仍走
     * {@link #bindText}/{@link #bindCaret} 的既有通道，不另开传播路径。</p>
     *
     * <p>播种保证首帧就取到编写者给的字号，不依赖第一轮布局完成后的重算；
     * 信号为 null（未指定）或值为 null 时一律不写 root，节点保持自身默认字号——
     * 不传字号的老调用方视觉零变化。</p>
     *
     * @param rt       场景运行时
     * @param root     控件根节点（字号真值所在）
     * @param fontSize 字号信号（UI 像素）；null = 不指定
     */
    /**
     * 构建期取有效字号：未指定（信号为 null 或值为 null）时回落控件内部默认值。
     *
     * <p>给不走 {@link #applyFontSize} 的控件用——它们的字号参与几何测量（段宽、条高），
     * 必须在建树时就知道字号才能算出正确的构建期尺寸。</p>
     *
     * @param fontSize 字号信号；null = 未指定
     * @param fallback 未指定时的内部默认字号
     * @return 有效字号（UI 像素）
     */
    static int fontSizeOrDefault(ReadableSignal<Integer> fontSize, int fallback) {
        return fontSizeOrDefault(fontSize == null ? null : fontSize.get(), fallback);
    }

    /**
     * 取有效字号：值缺失时回落控件内部默认值（运行期 applier 用同一口径，避免两处不一致）。
     *
     * @param fontSize 字号值；null = 未指定
     * @param fallback 未指定时的内部默认字号
     * @return 有效字号（UI 像素）
     */
    static int fontSizeOrDefault(Integer fontSize, int fallback) {
        return fontSize == null ? fallback : fontSize.intValue();
    }

    static void applyFontSize(SceneRuntime rt, SceneNode root, ReadableSignal<Integer> fontSize) {
        if (fontSize == null) {
            return;
        }
        Integer initial = fontSize.get();
        if (initial != null) {
            root.setFontSize(initial.intValue());
        }
        rt.bind(fontSize, current -> {
            if (current != null) {
                root.setFontSize(current.intValue());
            }
        });
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
