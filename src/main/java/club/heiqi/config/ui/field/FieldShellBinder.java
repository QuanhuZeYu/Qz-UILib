package club.heiqi.config.ui.field;

import java.util.function.Supplier;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.uilib.ui.scene.form.FormFieldShell;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 字段外壳装配薄 helper：收口 8 个 {@link FieldRenderer} 重复的
 * 「{@code labelOf(spec)} + {@code spec.helper()} +
 *  {@code adapter.errorSignal(path)} + {@code adapter.dirtySignal(path)} +
 *  {@link FormFieldShell#build}」调用序列。
 *
 * <p><b>config 层专用工具</b>：本类吃 {@link FieldSpec} / {@link DraftSignalAdapter} 等 config 类型，
 * 把 FormFieldShell 需要的「拆解后的标题 / error signal / dirty signal / 控件槽」预填好再下沉，
 * <b>不得下沉到 {@code uilib.ui.scene.form}</b>——后者对外承诺零 config 依赖
 * （守 U1 / form 子包 {@code package-info} 零业务依赖契约 / scene 边界门禁断言 3）。
 * FormFieldShell 仍是只吃 {@code String}/{@code ReadableSignal}/{@code Supplier}/{@code FormTheme}
 * 的纯泛型组合层，本 helper 落在适配层避免污染其依赖面。</p>
 *
 * <p>两个重载对偶 {@link FormFieldShell#build} 的两个重载：
 * 单行字段（input / segmented / select / toggle）走默认 {@code theme.inputHeight()}，
 * 多行字段（simple_list / fontSort / characterRule）传显式 {@code controlHeight}（如 {@code theme.listHeight()}）。
 * 行为与原各 renderer 直接调 {@link FormFieldShell#build} 完全一致。</p>
 */
public final class FieldShellBinder {

    /** 工具类，禁止实例化 */
    private FieldShellBinder() {
    }

    /**
     * 装配字段外壳并挂载控件（按主题 inputHeight 设控件根 preferredHeight）。
     *
     * <p>等价于 {@link FormFieldShell#build} 的 7 参重载（控件高度回退
     * {@code theme.inputHeight()}），由本 helper 预先把 spec / adapter 拆解为
     * title / helper / errorSignal / dirtySignal 后下调。</p>
     *
     * @param rt        场景运行时
     * @param spec      字段元数据（取 path / label / helper）
     * @param adapter   草稿 signal 适配器（取 errorSignal / dirtySignal）
     * @param controlFn 控件构建函数（{@code SceneXxx.create(rt, props)} 产物）
     * @param theme     主题 token
     * @return 字段卡片节点（已挂载控件）
     */
    public static SceneNode build(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter,
                                  Supplier<SceneNode> controlFn, FormTheme theme) {
        return build(rt, spec, adapter, controlFn, theme, theme.inputHeight());
    }

    /**
     * 装配字段外壳并挂载控件，按字段自带高度设定控件根 preferredHeight。
     *
     * <p>等价于 {@link FormFieldShell#build} 的 8 参重载。多行字段（SIMPLE_LIST 等）传
     * {@code theme.listHeight()}，单行字段传 {@code theme.inputHeight()}。由本 helper 预先把
     * spec / adapter 拆解为 title / helper / errorSignal / dirtySignal 后下调。</p>
     *
     * @param rt            场景运行时
     * @param spec          字段元数据（取 path / label / helper）
     * @param adapter       草稿 signal 适配器（取 errorSignal / dirtySignal）
     * @param controlFn     控件构建函数（{@code SceneXxx.create(rt, props)} 产物）
     * @param theme         主题 token
     * @param controlHeight 控件根 preferredHeight；{@code <=0} 时不设，让控件/容器决定
     * @return 字段卡片节点（已挂载控件）
     */
    public static SceneNode build(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter,
                                  Supplier<SceneNode> controlFn, FormTheme theme, int controlHeight) {
        String path = spec.path();
        return FormFieldShell.build(rt,
                FieldRenderSupport.labelOf(spec), spec.helper(),
                adapter.errorSignal(path), adapter.dirtySignal(path),
                controlFn, theme, controlHeight);
    }
}