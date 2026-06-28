package club.heiqi.config.ui.field;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * STRING 字段渲染器：适配 {@link SceneTextInput}。
 *
 * <p>Props.value 由 draftSignal 经 {@link Computed} 转 String，
 * onChange 调 {@link DraftSignalAdapter#onFieldEdit} 写回 DraftBuffer。</p>
 */
public final class StringFieldRenderer implements FieldRenderer {

    /** 纯静态工厂语义，但实现接口需实例化；无实例字段 */
    public StringFieldRenderer() {
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);

        // draftSignal<Object> → ReadableSignal<String>
        ReadableSignal<String> stringValue = Computed.create(() -> {
            Object v = draftSig.get();
            return v == null ? "" : String.valueOf(v);
        });

        int maxLength = spec.constraints() != null && spec.constraints().maxLength() >= 0
                ? spec.constraints().maxLength() : Integer.MAX_VALUE;
        String placeholder = spec.helper() != null ? spec.helper() : "";

        SceneTextInput.Props props = new SceneTextInput.Props(
                stringValue,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                placeholder,
                maxLength,
                SceneInputType.TEXT,
                next -> adapter.onFieldEdit(path, next));

        return FieldShell.build(rt, spec, adapter, SceneTextInput.create(rt, props));
    }
}
