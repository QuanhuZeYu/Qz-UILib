package club.heiqi.config.ui.field;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 字段控件渲染器接口：把 {@link FieldSpec} + {@link DraftSignalAdapter} 适配成 scene 控件。
 *
 * <p>实现方在 {@link #render} 内部：</p>
 * <ol>
 *   <li>从 spec 读 label / helper / constraints；</li>
 *   <li>从 adapter 读 draftSignal(path)；</li>
 *   <li>构建控件 Props，调 {@code SceneXxx.create(rt, props)} 得 {@code Supplier<SceneNode>}；</li>
 *   <li>包一层 field shell（label + 控件 + error 提示 + dirty 标记）；</li>
 *   <li>用 {@code rt.bind(Invalidation.PAINT, errorSignal, node::setBorderColor)} 派生错误边框；</li>
 *   <li>返回 field shell SceneNode。</li>
 * </ol>
 *
 * <p>{@code render} 在 {@link SceneRuntime#mount} 作用域外但仍在构造期调用，
 * 内部 {@code rt.mount / rt.bind} 归根 Owner，随 runtime.dispose 一并清理。</p>
 */
public interface FieldRenderer {

    /**
     * 渲染字段控件，返回 field shell SceneNode。
     *
     * @param rt      场景运行时
     * @param spec    字段元数据
     * @param adapter 草稿 signal 适配器
     * @return field shell 节点（含 label + 控件 + error + dirty 标记）
     */
    SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter);
}
