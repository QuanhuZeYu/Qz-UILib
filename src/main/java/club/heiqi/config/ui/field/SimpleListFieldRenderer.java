package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList.ListItem;
import club.heiqi.uilib.ui.scene.form.FormFieldShell;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * SIMPLE_LIST 字段渲染器：把 {@code List<String>} 草稿适配成 {@link SceneSimpleList}。
 *
 * <h3>D2 本地 Signal 桥 + 控件 id 自治（最关键）</h3>
 * <p>不在每次 draft 变化时重映射 {@code List<String>→List<ListItem>}——那样会重新分配 id，
 * 破坏 I5 keyed 复用。改为在 render 体内建<b>一个本地可写</b> {@code Signal<List<ListItem>> localItems}
 * 作为 SSOT 桥：</p>
 * <ul>
 *   <li>仅在 render 体内首次从 draft 转 {@code List<ListItem>} 初始化一次；</li>
 *   <li>此后行的增删改全部由 {@link SceneSimpleList} 内部对该 signal 操作，
 *       id 由控件生命周期自治（add 分配新 id、edit 走 copyWith 同 id、delete 只移除对应 id），全程稳定。</li>
 * </ul>
 *
 * <h3>外部 reset 回流守卫</h3>
 * <p>{@link DraftSignalAdapter#resetFieldToDefault} / {@link DraftSignalAdapter#resetToCurrent}
 * 会整体换 draft 内容，此时 id 全变、keyed 全重建是语义正确的。但控件自己写回 draft 触发的
 * draftSignal 变化<b>投影相等</b>，必须跳过重建——否则回环、id 抖动。</p>
 * <p>守卫实现（守 R3：落 {@link SceneRuntime#bind} effect，不在 Supplier 体内 {@code .get()} 分支建树）：
 * 用 {@code rt.bind(draftSig, applier)} 订阅 draftSignal，applier 内做值相等投影比对——
 * 当 draft 的 {@code List<String>} 与 localItems 当前投影（{@code map ListItem.getValue}）不等时
 * 才 {@code localItems.set(toListItems(...))}；相等时跳过。</p>
 *
 * <h3>D7 唯一翻译点</h3>
 * <p>{@link SceneSimpleList} 只认 {@link ListItem}，schema/Authority 只认 {@code List<String>}，
 * 本渲染器是唯一翻译点：</p>
 * <ul>
 *   <li>初始 / reset：{@code List<String> → List<ListItem>}（{@code new ListItem(value)}）</li>
 *   <li>写回（守 R7：onItemsChanged 内不回 set localItems）：{@code List<ListItem> → List<String>}
 *       调 {@link DraftSignalAdapter#onFieldEdit}</li>
 * </ul>
 */
public final class SimpleListFieldRenderer implements FieldRenderer {

    /** 纯静态工厂语义，但实现接口需实例化；无实例字段 */
    public SimpleListFieldRenderer() {
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);

        // D2：本地 SSOT 桥 —— 仅首次从 draft 转 List<ListItem>，后续增删改由控件自治 id
        List<String> initial = toDraftList(draftSig.get());
        final Signal<List<ListItem>> localItems = Signal.create(toListItems(initial));

        // D2 外部 reset 守卫：监听 draftSignal，仅当其投影与 localItems 当前投影不等时才重建
        // （reset 语义：整体换内容，id 全变、keyed 全重建是正确的）。
        // 投影相等时跳过 —— 控件自己写回 draft 触发的 draftSignal 变化投影相等 → 跳过 → 不回环、不抖动。
        // 守 R3：守卫逻辑落 rt.bind（effect），不在 Supplier 体内 .get() 分支建树。
        rt.bind(draftSig, draftValue -> {
            List<String> incoming = toDraftList(draftValue);
            List<String> currentProjection = projectValues(localItems.get());
            if (!incoming.equals(currentProjection)) {
                localItems.set(toListItems(incoming));
            }
        });

        // D7：renderer 是唯一翻译点。onItemsChanged 把 List<ListItem> → List<String> 写回 draft。
        // 守 R7：不回 set localItems（控件在回调前已 set，回 set 冗余/冲突）。
        SceneSimpleList.Props props = new SceneSimpleList.Props(
                localItems,
                labelOf(spec),
                "",
                items -> adapter.onFieldEdit(path, projectValues(items)),
                0,
                0);

        return FormFieldShell.build(rt, labelOf(spec), spec.helper(),
                adapter.errorSignal(path), adapter.dirtySignal(path),
                SceneSimpleList.create(rt, props), ConfigTheme.asFormTheme());
    }

    /**
     * draft 值 → {@code List<String>}（null / 非 List 兜底空 list）。
     *
     * @param value draft 原始值
     * @return 字符串列表
     */
    @SuppressWarnings("unchecked")
    private static List<String> toDraftList(Object value) {
        if (value instanceof List) {
            List<String> out = new ArrayList<String>(((List<Object>) value).size());
            for (Object o : (List<Object>) value) {
                out.add(o == null ? "" : String.valueOf(o));
            }
            return out;
        }
        return new ArrayList<String>();
    }

    /**
     * {@code List<String>} → {@code List<ListItem>}（首次建桥 / reset 重建用）。
     *
     * @param draftList 字符串列表
     * @return 不可变行列表
     */
    private static List<ListItem> toListItems(List<String> draftList) {
        List<ListItem> out = new ArrayList<ListItem>(draftList.size());
        for (String s : draftList) {
            out.add(new ListItem(s));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * {@code List<ListItem>} → {@code List<String>} 投影（写回 draft / 守卫比对用）。
     *
     * @param items 行列表，可为 null
     * @return 字符串列表
     */
    private static List<String> projectValues(List<ListItem> items) {
        int size = items == null ? 0 : items.size();
        List<String> out = new ArrayList<String>(size);
        if (items != null) {
            for (ListItem item : items) {
                out.add(item.getValue());
            }
        }
        return out;
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
