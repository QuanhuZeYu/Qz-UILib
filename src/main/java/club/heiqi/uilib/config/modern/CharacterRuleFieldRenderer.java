package club.heiqi.uilib.config.modern;

import static club.heiqi.uilib.ui.scene.control.SceneTextUtils.nullSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.field.DraftListBridge;
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.config.ui.field.FieldShellBinder;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.font.config.FontCharacterRule;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneAutocomplete;
import club.heiqi.uilib.ui.scene.control.SceneAutocompletePrimitive;
import club.heiqi.uilib.ui.scene.control.SceneCheckbox;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

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
 *       uilib 配置接入层（本包），不污染 scene 控件层。</li>
 *   <li>无效规则也写回：parse 失败的行（缺 =、范围格式错等）按原 selector/fontName 投影回字符串，
 *       保证用户输入不丢失，错误信息在行下方红字提示。</li>
 * </ul>
 *
 * <h3>D2 本地 Signal 桥（{@link DraftListBridge}）+ I5 keyed diff</h3>
 * <p>本地 SSOT 与 reset 守卫统一由 {@link DraftListBridge} 托管：首次从 draft 转
 * {@code List<CharacterRuleItem>}；reset 时 untrack 读投影 + normalize 比对；
 * commit 走 {@link DraftListBridge.CommitMode#SET_THEN_EDIT}。</p>
 *
 * <h3>合规守护</h3>
 * <ul>
 *   <li>R1：本类 {@code final class} + 仅 public 无参构造，无实例字段。</li>
 *   <li>R3：reset 守卫落 {@code rt.bind} effect（桥内）。</li>
 *   <li>R4：行错误文本经 {@code rt.bind} 派生。</li>
 *   <li>R6：错误文本节点 {@code setHitTestable(false)}。</li>
 *   <li>R7：onFieldEdit 后不回 set localItems；handler 在调 onFieldEdit 前已 set。</li>
 *   <li>I5：{@code forEach} 用带 keyFn 重载（{@code CharacterRuleItem::getId}）。</li>
 *   <li>字体名输入用成品 {@link SceneAutocomplete}（内置 chrome），不再内联 chrome 样板。</li>
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

        List<String> initial = toDraftList(draftSig.get());
        // D2：DraftListBridge 统一 localItems + reset 守卫（normalize 策略 + untrack 投影）
        final DraftListBridge<CharacterRuleItem> bridge = DraftListBridge.create(
                rt, draftSig, initial,
                CharacterRuleFieldRenderer::toDraftList,
                CharacterRuleFieldRenderer::toRuleItems,
                CharacterRuleFieldRenderer::projectValues,
                CharacterRuleFieldRenderer::normalize);

        return FieldShellBinder.build(rt, spec, adapter,
                () -> buildControl(rt, bridge, path, adapter, theme),
                theme, theme.listHeight());
    }

    /**
     * 构建控件根：列表视口（keyed 行树 + 滚动条）+ 添加按钮。
     *
     * @param rt      场景运行时
     * @param bridge  草稿列表桥
     * @param path    字段路径
     * @param adapter 草稿 signal 适配器
     * @param theme   主题 token
     * @return 控件根节点
     */
    private static SceneNode buildControl(SceneRuntime rt,
                                          DraftListBridge<CharacterRuleItem> bridge,
                                          String path,
                                          DraftSignalAdapter adapter,
                                          FormTheme theme) {
        Signal<List<CharacterRuleItem>> localItems = bridge.localItems();
        SceneNode root = SceneNode.column();
        root.setGap(ROOT_GAP);

        SceneNode listViewport = SceneNode.column();
        listViewport.setGap(LIST_GAP);
        listViewport.setScrollable(true);
        listViewport.setClipChildren(true);
        listViewport.setFillParentHeight(true);
        listViewport.setFlexGrow(1);

        // 与 FontSort 对齐：视口右侧叠加 SceneScrollbar
        SceneNode stackHost = SceneNode.row();
        stackHost.setFillParentHeight(true);
        stackHost.appendChild(listViewport);
        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, listViewport);
        SceneScrollbar.Result scrollbar = SceneScrollbar.createDefault(rt, listViewport, scrollSignal);
        stackHost.appendChild(scrollbar.column());
        root.appendChild(stackHost);

        // I5 keyed diff：必须用带 keyFn 重载（CharacterRuleItem::getId）
        Computed<List<CharacterRuleItem>> itemsComputed =
                Computed.create(() -> safeItems(localItems.get()));
        rt.forEach(listViewport, itemsComputed, CharacterRuleItem::getId,
                row -> buildRow(rt, bridge, path, adapter, row, theme));

        // 添加按钮：new CharacterRuleItem(true, "", "") 分配新 id
        SceneNode addButton = createTextButton(rt, "+ 添加规则", () -> {
            List<CharacterRuleItem> next = mutableItems(localItems.get());
            next.add(new CharacterRuleItem(true, "", ""));
            bridge.commit(path, adapter, next, DraftListBridge.CommitMode.SET_THEN_EDIT);
        });
        root.appendChild(addButton);

        return root;
    }

    /**
     * 构建单行编辑节点：行根（列）= 输入行（行）+ 行下方错误文本（条件渲染）。
     *
     * @param rt      场景运行时
     * @param bridge  草稿列表桥
     * @param path    字段路径
     * @param adapter 草稿 signal 适配器
     * @param row     行数据（id 在 keyed diff 复用期间恒定）
     * @param theme   主题 token
     * @return 行根节点
     */
    private static SceneNode buildRow(SceneRuntime rt,
                                      DraftListBridge<CharacterRuleItem> bridge,
                                      String path,
                                      DraftSignalAdapter adapter,
                                      CharacterRuleItem row,
                                      FormTheme theme) {
        Signal<List<CharacterRuleItem>> localItems = bridge.localItems();
        SceneNode rowRoot = SceneNode.column();
        rowRoot.setGap(ERROR_GAP);

        SceneNode line = SceneNode.row();
        line.setCrossAxisAlign(CrossAxisAlign.CENTER);
        line.setGap(ROW_GAP);

        // 启用 checkbox
        ReadableSignal<Boolean> checkedSig =
                Computed.create(() -> currentItem(localItems.get(), row).isEnabled());
        SceneCheckbox.Props checkboxProps = new SceneCheckbox.Props(
                checkedSig,
                Signal.create(""),
                Signal.create(Boolean.TRUE),
                next -> replaceField(bridge, adapter, path, row,
                        currentItem(localItems.get(), row).withEnabled(Boolean.TRUE.equals(next))));
        SceneNode checkbox = SceneCheckbox.create(rt, checkboxProps).get();
        checkbox.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        line.appendChild(checkbox);

        // 选择器输入
        ReadableSignal<String> selectorSig =
                Computed.create(() -> currentItem(localItems.get(), row).getSelector());
        SceneTextInput.Props selectorProps = new SceneTextInput.Props(
                selectorSig,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "a / U+0041 / a-z",
                Integer.MAX_VALUE,
                SceneInputType.TEXT,
                next -> replaceField(bridge, adapter, path, row,
                        currentItem(localItems.get(), row).withSelector(next)));
        SceneNode selectorInput = SceneTextInput.create(rt, selectorProps).get();
        selectorInput.setPreferredWidth(SELECTOR_WIDTH);
        line.appendChild(selectorInput);

        // 字体名：成品 SceneAutocomplete（内置 chrome + listbox 装饰）
        ReadableSignal<String> fontNameSig =
                Computed.create(() -> currentItem(localItems.get(), row).getFontName());
        List<String> fontCandidates = fontNameCandidateSnapshot();
        SceneAutocomplete.Props fontNameProps = new SceneAutocomplete.Props(
                fontNameSig,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "字体名",
                Integer.MAX_VALUE,
                fontCandidates,
                SceneAutocompletePrimitive.MatchMode.CONTAINS,
                8,
                next -> replaceField(bridge, adapter, path, row,
                        currentItem(localItems.get(), row).withFontName(next)),
                next -> replaceField(bridge, adapter, path, row,
                        currentItem(localItems.get(), row).withFontName(next)));
        SceneNode fontNameInput = SceneAutocomplete.create(rt, fontNameProps).get();
        fontNameInput.setPreferredWidth(FONTNAME_WIDTH);
        line.appendChild(fontNameInput);

        // 删除按钮
        SceneNode deleteButton = createTextButton(rt, "×", () -> {
            List<CharacterRuleItem> current = safeItems(localItems.get());
            List<CharacterRuleItem> next = new ArrayList<>(current.size());
            for (CharacterRuleItem item : current) {
                if (item.getId() != row.getId()) {
                    next.add(item);
                }
            }
            bridge.commit(path, adapter, next, DraftListBridge.CommitMode.SET_THEN_EDIT);
        });
        deleteButton.setPreferredWidth(DELETE_BUTTON_WIDTH);
        line.appendChild(deleteButton);

        rowRoot.appendChild(line);

        // 行下方错误文本
        final Computed<String> errMsg =
                Computed.create(() -> currentItem(localItems.get(), row).getErrorMessage());
        final Computed<Boolean> errNonEmpty =
                Computed.create(() -> errMsg.get() != null && !errMsg.get().isEmpty());
        rt.show(rowRoot, errNonEmpty, () -> {
            SceneNode errNode = new SceneNode();
            errNode.setHitTestable(false);
            errNode.setTextColor(theme.errorColor());
            errNode.setFontSize(theme.fontError());
            rt.bind(errMsg, errNode::setText);
            return errNode;
        });

        return rowRoot;
    }

    /**
     * 替换同 id 行（编辑写回）。守 R7：先 set 再 onFieldEdit。
     *
     * @param bridge   草稿列表桥
     * @param adapter  草稿适配器
     * @param path     字段路径
     * @param row      原行（取 id）
     * @param replaced 替换后的新行（同 id）
     */
    private static void replaceField(DraftListBridge<CharacterRuleItem> bridge,
                                     DraftSignalAdapter adapter, String path,
                                     CharacterRuleItem row, CharacterRuleItem replaced) {
        List<CharacterRuleItem> current = safeItems(bridge.localItems().get());
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
            bridge.commit(path, adapter, next, DraftListBridge.CommitMode.SET_THEN_EDIT);
        }
    }

    /**
     * 极简文本按钮：SceneNode.row + label + CLICK handler。
     *
     * @param rt      场景运行时
     * @param label   按钮文本
     * @param onClick 点击回调
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

    /**
     * 取字体名候选快照。
     *
     * @return 字体名候选不可变列表
     */
    private static List<String> fontNameCandidateSnapshot() {
        String[] snapshot = FontConfig.getFontSortSnapshot();
        if (snapshot == null || snapshot.length == 0) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(snapshot));
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
     * {@code List<String>} → {@code List<CharacterRuleItem>}。
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
     * {@code List<CharacterRuleItem>} → {@code List<String>} 投影。
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
     * 单条 CharacterRuleItem → 配置 String。
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
     * round-trip 规范化：parse → toRaw，用于 reset 守卫比对。
     *
     * @param raw 原始字符串列表
     * @return 规范化字符串列表
     */
    static List<String> normalize(List<String> raw) {
        List<String> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            FontCharacterRule parsed = FontCharacterRule.parseLine(s);
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
     * @param fallback 兜底行
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
     * 字符字体规则行数据：携带稳定 id 供 keyed 列表复用节点。
     */
    static final class CharacterRuleItem {

        /** 行 id 分配器。 */
        private static final AtomicLong NEXT_ITEM_ID = new AtomicLong(1L);

        /** 稳定行 id。 */
        private final long id;
        /** 是否启用。 */
        private final boolean enabled;
        /** 字符或范围选择器。 */
        private final String selector;
        /** 目标字体名。 */
        private final String fontName;
        /** parse 派生的错误说明，{@code null} 表示格式有效。 */
        private final String errorMessage;

        /**
         * 从原始配置字符串构建。
         *
         * @param raw 原始规则文本
         * @return 新行
         */
        static CharacterRuleItem fromRaw(String raw) {
            FontCharacterRule parsed = FontCharacterRule.parseLine(raw);
            return new CharacterRuleItem(NEXT_ITEM_ID.getAndIncrement(),
                    parsed.isEnabled(), parsed.getSelector(), parsed.getFontName());
        }

        /**
         * 创建新行（添加按钮用）。
         *
         * @param enabled  是否启用
         * @param selector 选择器
         * @param fontName 字体名
         */
        CharacterRuleItem(boolean enabled, String selector, String fontName) {
            this(NEXT_ITEM_ID.getAndIncrement(), enabled, selector, fontName);
        }

        /**
         * 创建带稳定 id 的行。
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
            FontCharacterRule parsed = FontCharacterRule.parseLine(
                    FontCharacterRule.toConfigValue(this.enabled, this.selector, this.fontName)
            );
            this.errorMessage = parsed.getErrorMessage();
        }

        /** @return 稳定行 id */
        long getId() {
            return id;
        }

        /** @return 是否启用 */
        boolean isEnabled() {
            return enabled;
        }

        /** @return 选择器 */
        String getSelector() {
            return selector;
        }

        /** @return 字体名 */
        String getFontName() {
            return fontName;
        }

        /** @return parse 派生的错误说明，{@code null} 表示格式有效 */
        String getErrorMessage() {
            return errorMessage;
        }

        /**
         * @return 是否空行
         */
        boolean isEmpty() {
            return selector.isEmpty() && fontName.isEmpty();
        }

        /**
         * @param v 新 enabled
         * @return 同 id 新行
         */
        CharacterRuleItem withEnabled(boolean v) {
            return new CharacterRuleItem(id, v, selector, fontName);
        }

        /**
         * @param v 新 selector
         * @return 同 id 新行
         */
        CharacterRuleItem withSelector(String v) {
            return new CharacterRuleItem(id, enabled, v, fontName);
        }

        /**
         * @param v 新 fontName
         * @return 同 id 新行
         */
        CharacterRuleItem withFontName(String v) {
            return new CharacterRuleItem(id, enabled, selector, v);
        }
    }
}
