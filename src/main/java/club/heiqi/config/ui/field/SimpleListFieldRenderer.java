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
 * <p><b>保存契约</b>：runtime 侧 SIMPLE_LIST 严格要求 {@code List} 且每个元素为
 * <strong>非 null String</strong>（null 元素 fail-closed INVALID）。本渲染器写回路径
 * 经 {@link #projectValues} 只产出 String；展示读路径 {@link #toDraftList} 对异常
 * null 元素兜底为 {@code ""} 仅防 UI NPE，<strong>不得</strong>依赖该兜底通过 save。</p>
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
 * <h3>prefillWhenEmpty 发现态预填充（局部只读初值，守 I3）</h3>
 * <p>可选 {@link #prefillWhenEmpty} 源（构造注入，{@code null} 表示不预填充，向后兼容）：
 * 当 draft 首次读取为空（{@code List<String>.isEmpty()}）且源非空时，
 * prefill 仅作为 renderer/bridge 的<strong>局部只读初始投影</strong>，
 * <strong>不</strong>调用 {@code seedPresentation}、不写 DraftBuffer current/draft/base、
 * 也不写全局 adapter signal（render 构建期禁止 Signal.set / adapter seed / validation 清理）。
 * dirty=false——保存其他字段时列表不落 YAML；用户首次<strong>真实控件交互</strong>
 *（SceneSimpleList 删除/编辑/拖拽经 onItemsChanged → onFieldEdit）才写入 draft。
 * 端到端回归须走真实列表控件 API，禁止直接 adapter.onFieldEdit 代替首次列表交互。</p>
 * <ul>
 *   <li>典型场景：fontSort 字段首次打开时 yaml 为空 list，但 FontConfig 已发现全量字体，
 *       预填充让用户立即看到可用字体列表。</li>
 *   <li>业务中立性：本渲染器不硬编码 FontConfig 依赖，{@link Supplier} 由 uilib 接入层注入
 *       （参照 uilib.config.modern 下 CharacterRuleFieldRenderer 候选源接入先例）。</li>
 *   <li>守 I3：render 体零副作用；prefill 只赋局部 {@code initial} 变量。</li>
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
     * 非 null 时，render 体首段若 draft 为空且源非空，仅赋<strong>局部只读</strong> initial 投影，
     * 不写 DraftBuffer / adapter signal / validation（守 I3）。
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

        // A' 发现态预填充（局部只读初值，守 I3）：
        // draft 首读为空 且有 prefill 源 且源非空 → 只赋局部 initial，不写 adapter/DraftBuffer/Signal。
        // dirty=false，保存其他字段时列表不落 YAML；用户首次编辑/删除/拖拽经 onFieldEdit 写入 draft。
        // render 构建期禁止 Signal.set、adapter seed、validation/feedback 清理。
        if (initial.isEmpty() && prefillWhenEmpty != null) {
            List<String> prefill = prefillWhenEmpty.get();
            if (prefill != null && !prefill.isEmpty()) {
                initial = new ArrayList<String>(prefill);
            }
        }

        // D2：DraftListBridge 统一 localItems + reset 守卫（untrack 投影；局部 prefill 保护）
        final DraftListBridge<ListItem> bridge = DraftListBridge.create(
                rt, draftSig, initial,
                SimpleListFieldRenderer::toDraftList,
                SimpleListFieldRenderer::toListItems,
                SimpleListFieldRenderer::projectValues,
                null,
                adapter,
                path);
        final Signal<List<ListItem>> localItems = bridge.localItems();


        // D7：renderer 是唯一翻译点。onItemsChanged 把 List<ListItem> → List<String> 写回 draft。
        // 守 R7：控件已 set，只 onFieldEdit（CONTROL_ALREADY_SET），不二次 set localItems。
        // P3：通过 Builder 传 draggable，true 时控件行首渲染拖拽把手（fontSort 形态）。
        SceneSimpleList.Props props = SceneSimpleList.Props.builder(localItems)
                .label(FieldRenderSupport.labelOf(spec))
                .placeholder("")
                .onItemsChanged(items -> bridge.commit(path, adapter, items,
                        DraftListBridge.CommitMode.CONTROL_ALREADY_SET))
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
     * <p>异常 null 元素展示兜底为 {@code ""}；save 路径仍严格拒绝 null（见类 Javadoc）。</p>
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
