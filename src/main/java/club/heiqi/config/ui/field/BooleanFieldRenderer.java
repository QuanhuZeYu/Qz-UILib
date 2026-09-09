package club.heiqi.config.ui.field;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneToggle;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * BOOLEAN 字段渲染器：适配 {@link SceneToggle}。
 *
 * <p>Props.on 由 draftSignal 经 {@link Computed} 转 Boolean，
 * onChange（期望新值）调 {@link DraftSignalAdapter#onFieldEdit} 写回。</p>
 *
 * <p><b>外观归属（G15/Boolean 迁移后）</b>：本类零外观写入——字段卡片表面与语义色经
 * {@link FieldShellBinder} 装配、跟随当前来源主题（契约 §4 表面绑定器唯一写入者），
 * Toggle 表面/前景由 {@link SceneToggle} 内部主题派生自持；dirty/error 语义色与
 * 值编辑、草稿桥行为均不变。</p>
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

        // G15/Boolean 销账：本渲染器不再向字段壳装配喂显式旧主题快照（原 ConfigTheme.asFormTheme()
        // 无参调用，契约 §3 显式覆盖语义、G15/Theme 书面警示的 .get() 快照同类残留）。
        // 卡片表面（GROUP 配方）与标题/helper/error/dirty 语义色由 FieldShellBinder（G15/Support）
        // → FormFieldShell theme-aware 默认路径跟随当前来源主题，本文件零外观写入、零竞争绑定；
        // Toggle 自身外观由 SceneToggle（G05）自持。theme 形参为 binder 的源码兼容占位
        // （默认路径不消费，传 null 显式声明零消费），7 个 Renderer 实例迁移后由主代理统一收口删除。
        return FieldShellBinder.build(rt, spec, adapter,
                SceneToggle.create(rt, props), null);
    }
}
