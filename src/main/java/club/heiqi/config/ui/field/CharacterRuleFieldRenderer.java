package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.font.config.FontCharacterRule;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneCheckbox;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.form.FormFieldShell;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 字符字体规则（characterFontRules）专用字段渲染器：把 YAML 仍以 simpleList 存储的
 * {@code List<String>} 草稿，在渲染层拆成「启用 / 选择器 / 字体名」三栏可编辑行，
 * 行内 parse 错误下方红字透出，无效规则也写回（不丢用户输入）。
 *
 * <h3>定位（用户拍板：渲染层字段拆分 + 选项 B 行树自建）</h3>
 * <ul>
 *   <li>YAML 不变：{@code characterFontRules} 在 schema 仍是 simpleList，
 *       真值 {@code List<String>} 由 DraftBuffer 持有，本类不触碰 schema/Authority。</li>
 *   <li>字段拆分走渲染层：本类在 render 时把每条 {@code String} 经
 *       {@link FontCharacterRule#parseLine} 拆成 {@link CharacterRuleItem}（enabled/selector/fontName +
 *       parseLine 派生的 errorMessage），让用户用 checkbox + 两个文本框编辑单条规则。</li>
 *   <li>行树自建（选项 B）：不向通用 {@code SceneSimpleList} 注入新形态，而是本类内部直接用
 *       {@link SceneRuntime#forEach} 建 keyed 行树，业务耦合 {@link FontCharacterRule} 留在
 *       config.ui.field 适配层，不污染 scene 控件层。</li>
 *   <li>无效规则也写回：parse 失败的行（缺 =、范围格式错等）按原 selector/fontName 投影回字符串，
 *       保证用户输入不丢失，错误信息在行下方红字提示。</li>
 * </ul>
 *
 * <h3>D2 本地 Signal 桥 + I5 keyed diff（与 SimpleListFieldRenderer 同源）</h3>
 * <p>不在每次 draft 变化时重映射 {@code List<String>→List<CharacterRuleItem>}——那样会重新分配 id，
 * 破坏 I5 keyed 复用（编辑第 2 行 selector 时第 1/3 行节点重建，丢输入焦点）。
 * 改为在 render 体内建<b>一个本地可写</b> {@code Signal<List<CharacterRuleItem>> localItems}
 * 作为 SSOT 桥：</p>
 * <ul>
 *   <li>仅在 render 体内首次从 draft 转 {@code List<CharacterRuleItem>} 初始化一次；</li>
 *   <li>此后行的增删改全部由本 renderer 内的 handler 对该 signal 操作，
 *       id 由 {@link CharacterRuleItem} 构造分配（add 分配新 id、edit 走 withXxx 同 id、
 *       delete 只移除对应 id），全程稳定。</li>
 * </ul>
 *
 * <h3>D2 外部 reset 回流守卫 + round-trip 规范化防抖动</h3>
 * <p>{@link DraftSignalAdapter#resetFieldToDefault} / {@link DraftSignalAdapter#resetToCurrent}
 * 会整体换 draft 内容，此时 id 全变、keyed 全重建是语义正确的。但控件自己写回 draft 触发的
 * draftSignal 变化<b>投影相等</b>，必须跳过重建——否则回环、id 抖动。</p>
 * <p>此外，round-trip（draft String → parse → item → toRaw → String）会规范化空白与格式
 * （例 {@code " a = font "} → {@code "a=font"}）。若直接用原始 incoming 与 projectValues 比对，
 * 会误判「同义但字面不同」为不等而重建 id——用户输入途中丢焦点。
 * 故守卫对 incoming 先做 {@link #normalize}（parse→toRaw 规范化），再与 projectValues 比对；
 * projectValues 本身就是 toRaw 输出，两侧同源，消除抖动。</p>
 * <p>守卫实现（守 R3：落 {@link SceneRuntime#bind} effect，不在 Supplier 体内 {@code .get()} 分支建树）：
 * 用 {@code rt.bind(draftSig, applier)} 订阅 draftSignal，applier 内做规范化后投影比对——
 * 规范化后不等时才 {@code localItems.set(toRuleItems(incoming))}。</p>
 *
 * <h3>D7 唯一翻译点</h3>
 * <p>本 renderer 是 {@code List<String>} ↔ {@code List<CharacterRuleItem>} 的唯一翻译点：
 * 初始 / reset 用 {@link #toRuleItems}；写回用 {@link #projectValues}（守 R7：onFieldEdit 后不回 set localItems，
 * 控件 handler 在 onFieldEdit 之前已 set localItems）。</p>
 *
 * <h3>合规守护</h3>
 * <ul>
 *   <li>R1：本类 {@code final class} + 仅 public 无参构造，无实例字段（与 SimpleListFieldRenderer 同形态）。</li>
 *   <li>R3：reset 守卫落 {@code rt.bind} effect。</li>
 *   <li>R4：行错误文本经 {@code rt.bind} 派生。</li>
 *   <li>R6：错误文本节点 {@code setHitTestable(false)}。</li>
 *   <li>R7：onFieldEdit 后不回 set localItems；handler 在调 onFieldEdit 前已 set。</li>
 *   <li>I5：{@code forEach} 用带 keyFn 重载（{@code CharacterRuleItem::getId}），
 *       不用 identity 重载，保证 copyWith 新对象引用变化时仍按 id 复用行节点。</li>
 *   <li>复用的 {@link SceneCheckbox} / {@link SceneTextInput} / {@link SceneButton} 自身已守 R1-R12。</li>
 * </ul>
 */
public final class CharacterRuleFieldRenderer implements FieldRenderer {

    /** 控件根纵向间距。 */
    private static final int ROOT_GAP = 6;
    /** 列表行间距。 */
    private static final int LIST_GAP = 4;
    /** 行内控件间距。 */
    private static final int ROW_GAP = 6;
    /** 行内列间距（错误文本与上方输入行）。 */
    private static final int ERROR_GAP = 2;
    /** 选择器输入框宽度。 */
    private static final int SELECTOR_WIDTH = 140;
    /** 字体名输入框宽度。 */
    private static final int FONTNAME_WIDTH = 180;
    /** 删除按钮固定宽度。 */
    private static final int DELETE_BUTTON_WIDTH = 28;

    /**
     * 创建渲染器实例。无实例字段（R1），由 {@code FieldRendererRegistry.registerPath} 覆盖注入。
     */
    public CharacterRuleFieldRenderer() {
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);
        final FormTheme theme = ConfigTheme.asFormTheme();

        // D2：本地 SSOT 桥 —— 仅首次从 draft 转 List<CharacterRuleItem>，后续增删改由本 renderer handler 自治 id
        List<String> initial = toDraftList(draftSig.get());
        final Signal<List<CharacterRuleItem>> localItems = Signal.create(toRuleItems(initial));

        // D2 外部 reset 守卫：监听 draftSignal，仅当其规范化投影与 localItems 当前投影不等时才重建
        // （reset 语义：整体换内容，id 全变、keyed 全重建是正确的）。
        // round-trip 规范化（normalize）：消除 " a = font " 与 "a=font" 字面差异导致的误重建（防抖动）。
        // 守 R3：守卫逻辑落 rt.bind（effect），不在 Supplier 体内 .get() 分支建树。
        rt.bind(draftSig, draftValue -> {
            List<String> incoming = toDraftList(draftValue);
            List<String> incomingNorm = normalize(incoming);
            List<String> currentProj = projectValues(localItems.get());
            if (!incomingNorm.equals(currentProj)) {
                localItems.set(toRuleItems(incoming));
            }
        });

        // 控件构建函数：交 FormFieldShell.mount 执行一次（R3）。
        // FormFieldShell 会按 listHeight 给控件根设 preferredHeight（字段自带高度）。
        return FormFieldShell.build(rt, labelOf(spec), spec.helper(),
                adapter.errorSignal(path), adapter.dirtySignal(path),
                () -> buildControl(rt, localItems, path, adapter, theme),
                theme, theme.listHeight());
    }

    /**
     * 构建控件根：列表视口（keyed 行树）+ 添加按钮。
     *
     * @param rt         场景运行时
     * @param localItems 本地 SSOT signal
     * @param path       字段路径
     * @param adapter    草稿 signal 适配器
     * @param theme      主题 token
     * @return 控件根节点
     */
    private static SceneNode buildControl(SceneRuntime rt,
                                          Signal<List<CharacterRuleItem>> localItems,
                                          String path,
                                          DraftSignalAdapter adapter,
                                          FormTheme theme) {
        SceneNode root = SceneNode.column();
        root.setGap(ROOT_GAP);

        SceneNode listViewport = SceneNode.column();
        listViewport.setGap(LIST_GAP);
        listViewport.setScrollable(true);
        listViewport.setClipChildren(true);
        listViewport.setFillParentHeight(true);
        listViewport.setFlexGrow(1);
        root.appendChild(listViewport);

        // I5 keyed diff：必须用带 keyFn 重载（CharacterRuleItem::getId），
        // 不能用 identity 重载 —— copyWith 产生新对象实例，identity 会判定引用变化重建行。
        Computed<List<CharacterRuleItem>> itemsComputed =
                Computed.create(() -> safeItems(localItems.get()));
        rt.forEach(listViewport, itemsComputed, CharacterRuleItem::getId,
                row -> buildRow(rt, localItems, path, adapter, row, theme));

        // 添加按钮：new CharacterRuleItem(true, "", "") 分配新 id
        SceneNode addButton = createTextButton(rt, "+ 添加规则", () -> {
            List<CharacterRuleItem> next = mutableItems(localItems.get());
            next.add(new CharacterRuleItem(true, "", ""));
            commit(localItems, adapter, path, next);
        });
        root.appendChild(addButton);

        return root;
    }

    /**
     * 构建单行编辑节点：行根（列）= 输入行（行）+ 行下方错误文本（条件渲染）。
     *
     * <p>每行的 Computed 都通过 {@code currentItem(localItems.get(), row)} 按 id 查最新值，
     * 即使 {@code row} 实例被 copyWith 替换，同 id 的最新值仍能找到（I5 行节点复用 + 数据最新）。</p>
     *
     * @param rt         场景运行时
     * @param localItems 本地 SSOT signal
     * @param path       字段路径
     * @param adapter    草稿 signal 适配器
     * @param row        行数据（id 在 keyed diff 复用期间恒定，构造只调一次）
     * @param theme      主题 token
     * @return 行根节点
     */
    private static SceneNode buildRow(SceneRuntime rt,
                                      Signal<List<CharacterRuleItem>> localItems,
                                      String path,
                                      DraftSignalAdapter adapter,
                                      CharacterRuleItem row,
                                      FormTheme theme) {
        SceneNode rowRoot = SceneNode.column();
        rowRoot.setGap(ERROR_GAP);

        SceneNode line = SceneNode.row();
        line.setCrossAxisAlign(CrossAxisAlign.CENTER);
        line.setGap(ROW_GAP);

        // 启用 checkbox：受控 checked = currentItem(...).isEnabled()，onChange → withEnabled
        ReadableSignal<Boolean> checkedSig =
                Computed.create(() -> currentItem(localItems.get(), row).isEnabled());
        SceneCheckbox.Props checkboxProps = new SceneCheckbox.Props(
                checkedSig,
                Signal.create(""),
                Signal.create(Boolean.TRUE),
                next -> replaceField(localItems, adapter, path, row,
                        currentItem(localItems.get(), row).withEnabled(Boolean.TRUE.equals(next))));
        SceneNode checkbox = SceneCheckbox.create(rt, checkboxProps).get();
        // checkbox 默认 widthSizing=FILL 会吞掉整行主轴空间，挤掉后续兄弟输入框；
        // 改为 SHRINK 让其只占内容宽度（box + label），让 selectorInput/fontNameInput/deleteButton 正常排布。
        checkbox.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        line.appendChild(checkbox);

        // 选择器输入：placeholder "a / U+0041 / a-z"，onChange → withSelector
        ReadableSignal<String> selectorSig =
                Computed.create(() -> currentItem(localItems.get(), row).getSelector());
        SceneTextInput.Props selectorProps = new SceneTextInput.Props(
                selectorSig,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "a / U+0041 / a-z",
                Integer.MAX_VALUE,
                SceneInputType.TEXT,
                next -> replaceField(localItems, adapter, path, row,
                        currentItem(localItems.get(), row).withSelector(next)));
        SceneNode selectorInput = SceneTextInput.create(rt, selectorProps).get();
        selectorInput.setPreferredWidth(SELECTOR_WIDTH);
        line.appendChild(selectorInput);

        // 字体名输入：placeholder "字体名"，onChange → withFontName
        ReadableSignal<String> fontNameSig =
                Computed.create(() -> currentItem(localItems.get(), row).getFontName());
        SceneTextInput.Props fontNameProps = new SceneTextInput.Props(
                fontNameSig,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "字体名",
                Integer.MAX_VALUE,
                SceneInputType.TEXT,
                next -> replaceField(localItems, adapter, path, row,
                        currentItem(localItems.get(), row).withFontName(next)));
        SceneNode fontNameInput = SceneTextInput.create(rt, fontNameProps).get();
        fontNameInput.setPreferredWidth(FONTNAME_WIDTH);
        line.appendChild(fontNameInput);

        // 删除按钮：按 id 移除该行
        SceneNode deleteButton = createTextButton(rt, "×", () -> {
            List<CharacterRuleItem> current = safeItems(localItems.get());
            List<CharacterRuleItem> next = new ArrayList<>(current.size());
            for (CharacterRuleItem item : current) {
                if (item.getId() != row.getId()) {
                    next.add(item);
                }
            }
            commit(localItems, adapter, path, next);
        });
        deleteButton.setPreferredWidth(DELETE_BUTTON_WIDTH);
        line.appendChild(deleteButton);

        rowRoot.appendChild(line);

        // 行下方错误文本：parse 派生的 errorMessage 非空时显示（rt.show 条件渲染）
        final Computed<String> errMsg =
                Computed.create(() -> currentItem(localItems.get(), row).getErrorMessage());
        final Computed<Boolean> errNonEmpty =
                Computed.create(() -> errMsg.get() != null && !errMsg.get().isEmpty());
        rt.show(rowRoot, errNonEmpty, () -> {
            SceneNode errNode = new SceneNode();
            errNode.setHitTestable(false); // 装饰节点命中穿透（守 R6 精神）
            errNode.setTextColor(theme.errorColor());
            errNode.setFontSize(theme.fontError());
            // 守 R4：错误文本经 rt.bind 派生
            rt.bind(errMsg, errNode::setText);
            return errNode;
        });

        return rowRoot;
    }

    /**
     * 替换同 id 行（编辑写回）。守 R7：先 localItems.set 再 onFieldEdit，onFieldEdit 后不回 set。
     *
     * @param localItems 本地 SSOT signal
     * @param adapter    草稿适配器
     * @param path       字段路径
     * @param row        原行（取 id）
     * @param replaced   替换后的新行（同 id）
     */
    private static void replaceField(Signal<List<CharacterRuleItem>> localItems,
                                     DraftSignalAdapter adapter, String path,
                                     CharacterRuleItem row, CharacterRuleItem replaced) {
        List<CharacterRuleItem> current = safeItems(localItems.get());
        List<CharacterRuleItem> next = new ArrayList<>(current.size());
        boolean changed = false;
        for (CharacterRuleItem item : current) {
            if (item.getId() == row.getId()) {
                next.add(replaced);
                changed = true;
            } else {
                next.add(item);
            }
        }
        if (changed) {
            commit(localItems, adapter, path, next);
        }
    }

    /**
     * 提交列表变更：先 localItems.set 不可变副本（驱动 keyed diff），再 onFieldEdit 写回 draft。
     *
     * <p>守 R7：onFieldEdit 后不回 set localItems —— 控件 handler 在调 onFieldEdit 前已 set。</p>
     *
     * @param localItems 本地 SSOT signal
     * @param adapter    草稿适配器
     * @param path       字段路径
     * @param next       下一版行列表
     */
    private static void commit(Signal<List<CharacterRuleItem>> localItems,
                               DraftSignalAdapter adapter, String path,
                               List<CharacterRuleItem> next) {
        List<CharacterRuleItem> immutable =
                Collections.unmodifiableList(new ArrayList<>(next));
        localItems.set(immutable);
        adapter.onFieldEdit(path, projectValues(immutable));
    }

    /**
     * 极简文本按钮：SceneNode.row + label + CLICK handler。
     *
     * <p>不使用 {@link SceneButton}（其 Props 标签为 signal，且含 primary/standard 变体外观，
     * 对删除/添加这种轻量行内按钮偏重）。自建极简按钮只承接 CLICK，外观用主题 token。</p>
     *
     * @param rt       场景运行时
     * @param label    按钮文本
     * @param onClick  点击回调
     * @return 按钮节点
     */
    private static SceneNode createTextButton(SceneRuntime rt, String label, Runnable onClick) {
        SceneNode button = SceneNode.row();
        button.setGap(ROW_GAP);
        button.setPadding(6);
        button.setCornerRadius(4);
        button.setCursor(club.heiqi.uilib.ui.scene.input.SceneCursor.POINTER);

        SceneNode labelNode = new SceneNode();
        labelNode.setHitTestable(false);
        labelNode.setText(label);
        button.appendChild(labelNode);

        rt.on(button, club.heiqi.uilib.ui.scene.input.SceneEventType.CLICK, (ev, ctx) -> {
            onClick.run();
            ctx.stopPropagation();
        });
        return button;
    }

    // ==================== 双向映射 ====================

    /**
     * draft 值 → {@code List<String>}（null / 非 List 兜底空 list）。
     *
     * @param value draft 原始值
     * @return 字符串列表
     */
    @SuppressWarnings("unchecked")
    static List<String> toDraftList(Object value) {
        if (value instanceof List) {
            List<String> out = new ArrayList<>(((List<Object>) value).size());
            for (Object o : (List<Object>) value) {
                out.add(o == null ? "" : String.valueOf(o));
            }
            return out;
        }
        return new ArrayList<>();
    }

    /**
     * {@code List<String>} → {@code List<CharacterRuleItem>}（首次建桥 / reset 重建用）。
     * 每条 String 经 {@link FontCharacterRule#parseLine} 派生 enabled/selector/fontName/errorMessage。
     *
     * @param draftList 字符串列表
     * @return 不可变 CharacterRuleItem 列表
     */
    static List<CharacterRuleItem> toRuleItems(List<String> draftList) {
        List<CharacterRuleItem> out = new ArrayList<>(draftList.size());
        for (String s : draftList) {
            out.add(CharacterRuleItem.fromRaw(s));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * {@code List<CharacterRuleItem>} → {@code List<String>} 投影（写回 draft / 守卫比对用）。
     * 每个 item 用 {@link #toRaw(CharacterRuleItem)} 拼回 String。
     *
     * @param items 行列表
     * @return 字符串列表
     */
    static List<String> projectValues(List<CharacterRuleItem> items) {
        int size = items == null ? 0 : items.size();
        List<String> out = new ArrayList<>(size);
        if (items != null) {
            for (CharacterRuleItem item : items) {
                out.add(toRaw(item));
            }
        }
        return out;
    }

    /**
     * 单条 CharacterRuleItem → 配置 String：空行（selector+fontName 皆空）写空串，
     * 否则按 {@link FontCharacterRule#toConfigValue} 拼回。
     *
     * <p>空行写空串与 YAML simpleList 中空白项的写入语义对齐，避免新空行产生 {@code "="} 字面值。</p>
     *
     * @param item 行数据
     * @return 配置文本
     */
    static String toRaw(CharacterRuleItem item) {
        if (item.isEmpty()) {
            return "";
        }
        return FontCharacterRule.toConfigValue(item.isEnabled(), item.getSelector(), item.getFontName());
    }

    /**
     * round-trip 规范化：每条 String 经 parse → toRaw 规范化，消除 {@code " a = font "} 与
     * {@code "a=font"} 的字面差异，用于 reset 守卫比对时与 projectValues 同源。
     *
     * @param raw 原始字符串列表
     * @return 规范化字符串列表
     */
    static List<String> normalize(List<String> raw) {
        List<String> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            // 用 parseLine（不展开逗号）保留 UI 单行语义；selector 含逗号时仍按一行处理
            FontCharacterRule parsed = FontCharacterRule.parseLine(s);
            // parseLine 已 trim selector/fontName，直接用其字段重建；toRaw 内部对空行再兜底空串。
            out.add(toRaw(new CharacterRuleItem(parsed.isEnabled(), parsed.getSelector(), parsed.getFontName())));
        }
        return out;
    }

    // ==================== 工具 ====================

    /**
     * null 安全行列表。
     *
     * @param items 行列表
     * @return 安全行列表
     */
    private static List<CharacterRuleItem> safeItems(List<CharacterRuleItem> items) {
        return items == null ? Collections.emptyList() : items;
    }

    /**
     * 复制列表为可变列表。
     *
     * @param items 原列表
     * @return 可变副本
     */
    private static List<CharacterRuleItem> mutableItems(List<CharacterRuleItem> items) {
        return items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    /**
     * 读取当前行快照（按 id 查）。
     *
     * @param items    行列表
     * @param fallback 兜底行（取 id）
     * @return 当前行或兜底行
     */
    private static CharacterRuleItem currentItem(List<CharacterRuleItem> items, CharacterRuleItem fallback) {
        for (CharacterRuleItem item : safeItems(items)) {
            if (item.getId() == fallback.getId()) {
                return item;
            }
        }
        return fallback;
    }

    /**
     * 复刻 SimpleListFieldRenderer 的标题回退：label 为空时回退 path。
     *
     * @param spec 字段元数据
     * @return 标题文本
     */
    private static String labelOf(FieldSpec spec) {
        String label = spec.label();
        return label == null || label.isEmpty() ? spec.path() : label;
    }

    /**
     * 字符字体规则行数据：携带稳定 id 供 keyed 列表复用节点，编辑字段时通过
     * {@code withXxx} 生成同 id 新对象，避免文本变化导致行重建（I5）。
     *
     * <p>errorMessage 在构造内派生（{@link FontCharacterRule#parseLine} round-trip，与 fromRaw/normalize 同源），
     * selector/fontName 任一变化后 withXxx 触发重算，作为行下方错误文本的数据源。</p>
     */
    static final class CharacterRuleItem {

        /** 行 id 分配器，用于 keyed 列表稳定身份。 */
        private static final AtomicLong NEXT_ITEM_ID = new AtomicLong(1L);

        /** 稳定行 id。 */
        private final long id;
        /** 是否启用（{@code disabled:} 前缀来源）。 */
        private final boolean enabled;
        /** 字符或范围选择器（如 {@code "a"} / {@code "U+0041"} / {@code "a-z"}）。 */
        private final String selector;
        /** 目标字体名。 */
        private final String fontName;
        /** parse 派生的错误说明，{@code null} 表示格式有效。 */
        private final String errorMessage;

        /**
         * 从原始配置字符串构建（首次建桥 / reset 重建用）：经 {@link FontCharacterRule#parseLine} 拆字段，
         * 分配新 id。
         *
         * @param raw 原始规则文本
         * @return 新行（含 parseLine 派生的 errorMessage）
         */
        static CharacterRuleItem fromRaw(String raw) {
            // 用 parseLine（不展开逗号）保留 UI 单行语义；selector 含逗号时仍按一行处理
            FontCharacterRule parsed = FontCharacterRule.parseLine(raw);
            return new CharacterRuleItem(NEXT_ITEM_ID.getAndIncrement(),
                    parsed.isEnabled(), parsed.getSelector(), parsed.getFontName());
        }

        /**
         * 创建新行（添加按钮用）：分配新 id，构造内 parse 派生 errorMessage。
         *
         * @param enabled  是否启用
         * @param selector 选择器
         * @param fontName 字体名
         */
        CharacterRuleItem(boolean enabled, String selector, String fontName) {
            this(NEXT_ITEM_ID.getAndIncrement(), enabled, selector, fontName);
        }

        /**
         * 创建带稳定 id 的行（构造内 parseLine round-trip 派生 errorMessage，与 fromRaw 同源）。
         *
         * @param id       稳定行 id
         * @param enabled  是否启用
         * @param selector 选择器
         * @param fontName 字体名
         */
        private CharacterRuleItem(long id, boolean enabled, String selector, String fontName) {
            this.id = id;
            this.enabled = enabled;
            this.selector = nullSafe(selector);
            this.fontName = nullSafe(fontName);
            // errorMessage 派生：用 parseLine 与 fromRaw/normalize 同源，统一"全段空 selector"裁决。
            // 否则 parse 在全段空（如 ",,=Font"）逗号展开后全段跳过 → 返回空 list → errorMessage=null，
            // 而 parseLine 视为 invalid "字符或范围不能为空"，两路径语义不一致（P-1 修复）。
            FontCharacterRule parsed = FontCharacterRule.parseLine(
                    FontCharacterRule.toConfigValue(this.enabled, this.selector, this.fontName)
            );
            this.errorMessage = parsed.getErrorMessage();
        }

        /**
         * @return 稳定行 id
         */
        long getId() {
            return id;
        }

        /**
         * @return 是否启用
         */
        boolean isEnabled() {
            return enabled;
        }

        /**
         * @return 选择器
         */
        String getSelector() {
            return selector;
        }

        /**
         * @return 字体名
         */
        String getFontName() {
            return fontName;
        }

        /**
         * @return parse 派生的错误说明，{@code null} 表示格式有效
         */
        String getErrorMessage() {
            return errorMessage;
        }

        /**
         * 判断是否空行（selector+fontName 皆空）：空行写回 YAML 时输出空串而非 {@code "="}。
         *
         * @return 是否空行
         */
        boolean isEmpty() {
            return selector.isEmpty() && fontName.isEmpty();
        }

        /**
         * 复制为同 id、新 enabled 的行（重算 errorMessage）。
         *
         * @param v 新 enabled
         * @return 同 id 新行
         */
        CharacterRuleItem withEnabled(boolean v) {
            return new CharacterRuleItem(id, v, selector, fontName);
        }

        /**
         * 复制为同 id、新 selector 的行（重算 errorMessage）。
         *
         * @param v 新 selector
         * @return 同 id 新行
         */
        CharacterRuleItem withSelector(String v) {
            return new CharacterRuleItem(id, enabled, v, fontName);
        }

        /**
         * 复制为同 id、新 fontName 的行（重算 errorMessage）。
         *
         * @param v 新 fontName
         * @return 同 id 新行
         */
        CharacterRuleItem withFontName(String v) {
            return new CharacterRuleItem(id, enabled, selector, v);
        }

        /** null 安全字符串。 */
        private static String nullSafe(String value) {
            return value == null ? "" : value;
        }
    }
}
