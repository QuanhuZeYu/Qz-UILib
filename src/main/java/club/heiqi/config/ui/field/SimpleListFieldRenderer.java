package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList.ListItem;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * SIMPLE_LIST 字段渲染器：把 {@code List<String>} 草稿适配成 {@link SceneSimpleList}。
 *
 * <h3>draggable 模式（P3）</h3>
 * <p>本类带 {@link #draggable} 标志位：</p>
 * <ul>
 *   <li>默认无参构造 {@code draggable=false}：不建拖拽把手、不响应拖拽，
 *       {@link FieldRendererRegistry#defaultRegistry()} 注册的就是该形态（向后兼容）。</li>
 *   <li>{@code new SimpleListFieldRenderer(true)}：启用行拖拽排序，每行行首渲染拖拽把手，
 *       按档 A 越界跳变语义重排。当前用于 {@code fontSystem.fontSort} 字段——
 *       由 uilib 接入层经 {@link FieldRendererRegistry#registerPath} 挂覆盖实例。</li>
 * </ul>
 *
 * <h3>prefillWhenEmpty 发现态预填充（A' / ses_0cad66abdffe）</h3>
 * <p>可选 {@link #prefillWhenEmpty} 源（构造注入，{@code null} 表示不预填充，向后兼容）：
 * 当 draft 首次读取为空（{@code List<String>.isEmpty()}）且源非空时，
 * 把源值经 {@link DraftSignalAdapter#seedFieldBaseline} 同时写入 draft + current，
 * 使该字段 dirty=false——UI 立即展示派生值，但保存按钮不点亮，用户不显式编辑就不写盘。</p>
 * <ul>
 *   <li>典型场景：fontSort 字段首次打开时 yaml 为空 list，但 FontConfig 已发现全量字体，
 *       预填充让用户立即看到可用字体列表。</li>
 *   <li>业务中立性：本渲染器不硬编码 FontConfig 依赖，{@link Supplier} 由 uilib 接入层注入
 *       （参照 {@link CharacterRuleFieldRenderer} 候选源接入先例）。</li>
 *   <li>守 I3：预填充在 render 体首段一次性执行（首次建桥），不进 effect/Computed
 *       （后者会变副作用反模式）。</li>
 * </ul>
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

    /**
     * 是否启用行拖拽排序。false（无参构造默认）表示不建把手、不响应拖拽（向后兼容）；
     * true 时每行行首渲染拖拽把手，按档 A 越界跳变语义重排。
     */
    private final boolean draggable;

    /**
     * 发现态预填充源（A'）。{@code null} 表示不预填充（无参 / 单参构造默认，向后兼容）；
     * 非 null 时，render 体首段若 draft 为空且源非空，把源值同时写入 draft + current 抹平 dirty。
     *
     * <p>final + 构造注入，守 R1（renderer 零可变内部状态），与 {@link #draggable} 同性质。
     * 业务中立：本字段是通用 {@link Supplier}，不硬编码 FontConfig 依赖。</p>
     */
    private final Supplier<List<String>> prefillWhenEmpty;

    /**
     * 创建非拖拽形态（{@code draggable=false}，向后兼容）。
     *
     * <p>{@link FieldRendererRegistry#defaultRegistry()} 注册的是该形态，
     * 现有调用方保持行为不变。</p>
     */
    public SimpleListFieldRenderer() {
        this(false, null);
    }

    /**
     * 创建指定拖拽形态的渲染器（不预填充，向后兼容）。
     *
     * @param draggable true 启用行拖拽排序（fontSort 字段使用）；false 表示非拖拽形态
     */
    public SimpleListFieldRenderer(boolean draggable) {
        this(draggable, null);
    }

    /**
     * 创建指定拖拽形态 + 发现态预填充源的渲染器。
     *
     * <p>用于 fontSort 等需要"打开即展示已发现列表"的字段：draft 为空时用 prefillWhenEmpty
     * 预填充，抹平 dirty（不触发保存），用户显式编辑才写盘。</p>
     *
     * @param draggable       true 启用行拖拽排序
     * @param prefillWhenEmpty 预填充源（null 表示不预填充，行为等同单参构造）
     */
    public SimpleListFieldRenderer(boolean draggable, Supplier<List<String>> prefillWhenEmpty) {
        this.draggable = draggable;
        this.prefillWhenEmpty = prefillWhenEmpty;
    }

    /**
     * @return 是否启用行拖拽排序（供 path 覆盖注入路径的 resolve 单测断言形态差异）
     */
    public boolean draggable() {
        return draggable;
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);

        // D2：本地 SSOT 桥 —— 仅首次从 draft 转 List<ListItem>，后续增删改由控件自治 id
        List<String> initial = toDraftList(draftSig.get());

        // A' 发现态预填充（ses_0cad66abdffe）：
        // draft 首读为空 且有 prefill 源 且源非空 → 把源值同时写入 draft + current 抹平 dirty。
        // 守 I3：预填充在 render 体首段一次性执行（首次建桥时调一次），
        // 严禁放进 effect/Computed（会变副作用反模式）。
        // 守 I1：经 seedFieldBaseline → sig.set，无命令式改节点。
        // 语义：用户不显式编辑就 dirty=false → 保存按钮不点亮 → Authority/yaml 保持空。
        if (initial.isEmpty() && prefillWhenEmpty != null) {
            List<String> prefill = prefillWhenEmpty.get();
            if (prefill != null && !prefill.isEmpty()) {
                // 写 draft + current 抹平 dirty，同步 signal 让 UI 读到新值
                adapter.seedFieldBaseline(path, new ArrayList<String>(prefill));
                initial = new ArrayList<String>(prefill);
            }
        }

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
        // P3：通过 Builder 传 draggable，true 时控件行首渲染拖拽把手（fontSort 形态）。
        SceneSimpleList.Props props = SceneSimpleList.Props.builder(localItems)
                .label(FieldRenderSupport.labelOf(spec))
                .placeholder("")
                .onItemsChanged(items -> adapter.onFieldEdit(path, projectValues(items)))
                .maxItems(0)
                .minItems(0)
                .draggable(draggable)
                .build();

        FormTheme theme = ConfigTheme.asFormTheme();
        return FieldShellBinder.build(rt, spec, adapter,
                SceneSimpleList.create(rt, props), theme, theme.listHeight());
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
}
