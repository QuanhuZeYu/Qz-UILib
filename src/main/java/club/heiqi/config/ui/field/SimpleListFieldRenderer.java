package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
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
 *       被拖行中心越过相邻行中线时重排。fontSort 当前使用专用 renderer，但共享同一
 *       {@link club.heiqi.uilib.ui.scene.control.SceneDragReorder} 行为工具。</li>
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
 *   <li>可用于业务在 draft 为空时展示构造期冻结的候选列表；fontSort 的同类语义当前由专用
 *       {@link FontSortFieldRenderer} 收口。</li>
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
 *
 * <h3>外观归属（G15/SimpleList 迁移后口径）</h3>
 * <p>本类零直接外观写入（契约 §4「一个属性只有一个写入者」）：</p>
 * <ul>
 *   <li>字段卡片表面（background/border/borderWidth/cornerRadius/backdrop/surfaceElevation）与
 *       标题/helper/error/dirty 语义色经 {@link FieldShellBinder}（G15/Support）→
 *       {@code FormFieldShell} theme-aware 默认路径消费来源主题（GROUP 角色配方），本类不复制。</li>
 *   <li>列表底座（viewport GROUP 配方）、行内 {@code SceneTextInput}（INPUT）、添加/删除按钮
 *       （BUTTON_STANDARD / BUTTON_DANGER）、列表标题前景与行的 accent 轻量状态覆盖，全部由已
 *       主题化的 {@link SceneSimpleList} 本体（G12）自持——本类只组 Props 消费，不复制样式、
 *       不给卡片或底座叠第二层表面，行按契约 §4.1 与 G13 裁决保持轻量口径（不各自装滤镜）。</li>
 *   <li>多行视口高度经 {@link #LIST_VIEWPORT_HEIGHT} 构造期直读的 {@code controlHeight} 纯 int
 *       布局入参（契约 §4「padding/尺寸/布局属性归控件自身，主题不接管布局」；G15/Support
 *       衔接要点 3：listHeight 纯 int 路径安全）。<b>G15/收口</b>：该值换源为
 *       {@code FormTheme.defaultDark().listHeight()} 同源常量（CharRule 先例，与旧
 *       {@code ConfigTheme.asFormTheme().listHeight()} 逐值相等），binder 的 theme 兼容占位
 *       形参亦已随收口从签名删除——本类不再持有任何整主题快照。</li>
 * </ul>
 * <p>列表增删 / 选择 / 拖拽 / 草稿桥 / dirty / error 行为与迁移前零改动。</p>
 */
public final class SimpleListFieldRenderer implements FieldRenderer {

    /**
     * 列表视口高度（{@code FieldShellBinder.build} 的 controlHeight 纯 int 布局入参，契约 §4.2、
     * G15/Support 衔接要点 3）：G15/收口（CharRule 先例）换源取
     * {@code FormTheme.defaultDark().listHeight()} 同源值（与 {@code ConfigTheme.asFormTheme()}
     * 缓存的同一 {@code FormTheme.defaultDark()} 常量分量逐值相等，listHeight=220），构造期直读
     * 纯 int、不持整主题对象——消除旧 {@code asFormTheme()} 快照消费，默认渲染输出逐属性不变。
     */
    private static final int LIST_VIEWPORT_HEIGHT = FormTheme.defaultDark().listHeight();

    /**
     * 是否启用行拖拽排序。false（无参构造默认）表示不建把手、不响应拖拽（向后兼容）；
     * true 时每行行首渲染拖拽把手，被拖行中心越过相邻行中线时重排。
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
        // G15/SimpleList 销账：Props 只含数据与行为入参（标题/占位文案、回调、min/max、draggable），
        // 无任何样式/色值分量——表面与前景全部归已主题化的 SceneSimpleList 本体（G12）自持。
        SceneSimpleList.Props props = SceneSimpleList.Props.builder(localItems)
                .label(FieldRenderSupport.labelOf(spec))
                .placeholder("")
                .onItemsChanged(items -> bridge.commit(path, adapter, items,
                        DraftListBridge.CommitMode.CONTROL_ALREADY_SET))
                .maxItems(0)
                .minItems(0)
                .draggable(draggable)
                .build();

        // G15/SimpleList 销账 + G15/收口（本文件原唯一 ConfigTheme/FormTheme 快照触点）：
        // ① controlHeight 是纯 int 布局入参（契约 §4：主题不接管布局；G15/Support 衔接要点 3），
        //    收口后按 CharRule 先例换源为 LIST_VIEWPORT_HEIGHT 同源常量直读，不再经
        //    ConfigTheme.asFormTheme() 整主题快照取值；
        // ② binder 的 theme 兼容占位形参已随 G15/收口从签名删除，本调用点无主题入参。
        // 卡片表面（GROUP 配方）与标题/helper/error/dirty 语义色由 binder → FormFieldShell
        // theme-aware 默认路径跟随来源主题，本类零直接外观写入、零竞争绑定。
        return FieldShellBinder.build(rt, spec, adapter,
                SceneSimpleList.create(rt, props), LIST_VIEWPORT_HEIGHT);
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
