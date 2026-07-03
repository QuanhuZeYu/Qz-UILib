package club.heiqi.config.ui.field;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.form.FormFieldShell;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneToggle;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * BOOLEAN 字段渲染器：适配 {@link SceneToggle}。
 *
 * <p>Props.on 由 draftSignal 经 {@link Computed} 转 Boolean，
 * onChange（期望新值）调 {@link DraftSignalAdapter#onFieldEdit} 写回。</p>
 */
public final class BooleanFieldRenderer implements FieldRenderer {

    /** 纯静态工厂语义，但实现接口需实例化；无实例字段 */
    public BooleanFieldRenderer() {
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);

        ReadableSignal<Boolean> boolValue = Computed.create(() -> {
            Object v = draftSig.get();
            return Boolean.TRUE.equals(v);
        });

        // toggle label 传空串：标题由 FormFieldShell header title 承载，避免 toggle label 与 header title 重复
        SceneToggle.Props props = new SceneToggle.Props(
                boolValue,
                Signal.create(""),
                Signal.create(Boolean.TRUE),
                next -> adapter.onFieldEdit(path, next));

        return FormFieldShell.build(rt, labelOf(spec), spec.helper(),
                adapter.errorSignal(path), adapter.dirtySignal(path),
                SceneToggle.create(rt, props), ConfigTheme.asFormTheme());
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
