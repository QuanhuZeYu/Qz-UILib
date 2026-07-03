package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.config.schema.FieldConstraints;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.form.FormFieldShell;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneSegmented;
import club.heiqi.uilib.ui.scene.control.SceneSelect;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * CHOICE 字段渲染器：≤4 项用 {@link SceneSegmented}，>4 项用 {@link SceneSelect}。
 *
 * <p>selectedIndex 由 draft 值（String）映射到 choices 索引，
 * onSelect 调 {@link DraftSignalAdapter#onFieldEdit} 写回 choices[index]。</p>
 */
public final class ChoiceFieldRenderer implements FieldRenderer {

    /** 分段选择阈值：≤4 项用 Segmented，>4 项用 Select */
    private static final int SEGMENTED_THRESHOLD = 4;

    /** 纯静态工厂语义，但实现接口需实例化；无实例字段 */
    public ChoiceFieldRenderer() {
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);
        FieldConstraints c = spec.constraints();
        List<String> choices = c != null && c.choices() != null
                ? c.choices() : Collections.<String>emptyList();
        List<String> options = new ArrayList<String>(choices);

        ReadableSignal<Integer> selectedIndex = Computed.create(() -> {
            Object v = draftSig.get();
            String s = v == null ? "" : String.valueOf(v);
            int idx = options.indexOf(s);
            return Integer.valueOf(idx >= 0 ? idx : 0);
        });

        if (options.size() <= SEGMENTED_THRESHOLD) {
            SceneSegmented.Props props = new SceneSegmented.Props(
                    selectedIndex,
                    options,
                    Signal.create(Boolean.TRUE),
                    index -> adapter.onFieldEdit(path, options.get(index)));
            return FormFieldShell.build(rt, labelOf(spec), spec.helper(),
                    adapter.errorSignal(path), adapter.dirtySignal(path),
                    SceneSegmented.create(rt, props), ConfigTheme.asFormTheme());
        }

        SceneSelect.Props props = new SceneSelect.Props(
                selectedIndex,
                options,
                Signal.create(Boolean.TRUE),
                index -> adapter.onFieldEdit(path, options.get(index)));
        return FormFieldShell.build(rt, labelOf(spec), spec.helper(),
                adapter.errorSignal(path), adapter.dirtySignal(path),
                SceneSelect.create(rt, props), ConfigTheme.asFormTheme());
    }

    /**
     * 复刻原 FieldShell 的标题回退：label 为空时回退 path。
     *
     * @param spec 字段元数据
     * @return 标题文本
     */
    private static String labelOf(FieldSpec spec) {
        String label = spec.label();
        return label == null || label.isEmpty() ? spec.path() : label;
    }
}
