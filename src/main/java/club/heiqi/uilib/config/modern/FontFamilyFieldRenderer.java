package club.heiqi.uilib.config.modern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.config.ui.field.FieldRenderSupport;
import club.heiqi.config.ui.field.FieldShellBinder;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 单字体族选择字段渲染器：六槽字体指派（西文/中文 × 正常/粗体/斜体）共用的 GUI 选择控件。
 *
 * <p>不是文本输入：与 {@code fontSort} 同形的「筛选框 + 可滚动字体列表」，但为<b>单选</b>——
 * 列表首项是「自动 / 不指派」，其余为当前已发现字体族（{@link FontConfig#getFontSortSnapshot()}），
 * 点击某行即经 {@link DraftSignalAdapter#onFieldEdit} 写回该字体族名（点首行写回空串）。
 * 当前选中项以行首圆点标记。空串语义 = 不指派、退回自动字体排序。</p>
 *
 * <p>候选在 renderer 构造时冻结（与 {@code fontSort} 的 screen-open 快照一致），列表内筛选
 * 只切换可见子集，不重新发现字体。</p>
 */
public final class FontFamilyFieldRenderer implements FieldRenderer {

    /** 顶部筛选栏高度。 */
    private static final int FILTER_BAR_HEIGHT = 30;
    /** 筛选栏与列表间距。 */
    private static final int ROOT_GAP = 6;
    /** 列表行固定高度。 */
    private static final int ROW_HEIGHT = 26;
    /** 列表视口高度（纯 int 布局入参，与 fontSort 同源常量）。 */
    private static final int LIST_VIEWPORT_HEIGHT = FormTheme.defaultDark().listHeight();
    /** 行标签字号（纯 int 排版常量）。 */
    private static final int FONT_LABEL_SIZE = FormTheme.defaultDark().fontLabel();
    /** 「自动」行的显示文本。 */
    private static final String AUTO_LABEL = "自动 / 不指派";
    /** 「自动」行写回值（空串 = 未指派）。 */
    private static final String AUTO_VALUE = "";
    /** 选中标记 / 未选中标记。 */
    private static final String SELECTED_MARK = "\u25cf ";
    private static final String UNSELECTED_MARK = "\u25cb ";
    /** keyed diff 的稳定 key：自动行 0，候选行 = 候选下标 + 1。 */
    private static final Function<Choice, Long> CHOICE_KEY = choice -> Long.valueOf(choice.id);

    /** 构造期冻结的已发现字体族快照。 */
    private final List<String> candidates;

    /**
     * 创建空候选的选择器（字体尚未发现时）。
     */
    public FontFamilyFieldRenderer() {
        this(Collections.<String>emptyList());
    }

    /**
     * 创建带候选快照的选择器。
     *
     * @param candidates 已发现字体族名快照
     */
    public FontFamilyFieldRenderer(List<String> candidates) {
        this.candidates = candidates == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(candidates));
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);
        final ReadableSignal<String> valueSig = FieldRenderSupport.toStringSignal(draftSig);
        final Signal<String> filter = Signal.create("");
        return FieldShellBinder.build(rt, spec, adapter,
                () -> buildControl(rt, adapter, path, valueSig, filter),
                LIST_VIEWPORT_HEIGHT);
    }

    /**
     * 构建控件根：筛选栏 + 可滚动字体行列表。
     *
     * @param rt       场景运行时
     * @param adapter  草稿适配器
     * @param path     字段路径
     * @param valueSig 当前值信号
     * @param filter   筛选文本信号
     * @return 控件根节点
     */
    private SceneNode buildControl(SceneRuntime rt, DraftSignalAdapter adapter, String path,
                                   ReadableSignal<String> valueSig, Signal<String> filter) {
        SceneNode root = SceneNode.column();
        root.setGap(ROOT_GAP);

        SceneNode filterBar = SceneNode.row();
        filterBar.setPreferredHeight(FILTER_BAR_HEIGHT);
        filterBar.setCrossAxisAlign(CrossAxisAlign.CENTER);
        SceneTextInput.Props filterProps = new SceneTextInput.Props(
                filter,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "筛选字体…",
                Integer.MAX_VALUE,
                SceneInputType.TEXT,
                filter::set);
        SceneNode filterInput = SceneTextInput.create(rt, filterProps).get();
        filterInput.setPreferredHeight(FILTER_BAR_HEIGHT);
        filterInput.setFlexGrow(1);
        filterBar.appendChild(filterInput);
        root.appendChild(filterBar);

        SceneNode stackHost = SceneNode.row();
        stackHost.setPreferredHeight(Math.max(0, LIST_VIEWPORT_HEIGHT - FILTER_BAR_HEIGHT - ROOT_GAP));
        stackHost.setFillParentHeight(true);
        SceneNode viewport = SceneNode.column();
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setFillParentHeight(true);
        viewport.setFlexGrow(1);
        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);
        SceneScrollbar.Result scrollbar = SceneScrollbar.createDefault(rt, viewport, scrollSignal);
        scrollbar.column().setPreferredWidth(SceneScrollbar.DEFAULT_BAR_WIDTH);

        SceneNode rows = SceneNode.column();
        viewport.appendChild(rows);
        Computed<List<Choice>> choices = Computed.create(() -> filteredChoices(filter.get()));
        rt.forEach(rows, choices, CHOICE_KEY, choice -> buildRow(rt, adapter, path, valueSig, choice));

        stackHost.appendChild(viewport);
        stackHost.appendChild(scrollbar.column());
        root.appendChild(stackHost);
        return root;
    }

    /**
     * 按筛选文本构造可见候选：自动行恒在首位，其余按大小写不敏感包含匹配。
     *
     * @param query 筛选文本
     * @return 可见选项列表
     */
    private List<Choice> filteredChoices(String query) {
        List<Choice> out = new ArrayList<Choice>(candidates.size() + 1);
        out.add(new Choice(0L, AUTO_VALUE, AUTO_LABEL));
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ENGLISH);
        for (int index = 0; index < candidates.size(); index++) {
            String name = candidates.get(index);
            if (needle.isEmpty() || name.toLowerCase(Locale.ENGLISH).contains(needle)) {
                out.add(new Choice(index + 1L, name, name));
            }
        }
        return out;
    }

    /**
     * 构建单行：圆点选中标记 + 字体名；点击写回。
     *
     * @param rt       场景运行时
     * @param adapter  草稿适配器
     * @param path     字段路径
     * @param valueSig 当前值信号
     * @param choice   行数据
     * @return 行节点
     */
    private static SceneNode buildRow(SceneRuntime rt, DraftSignalAdapter adapter, String path,
                                      ReadableSignal<String> valueSig, Choice choice) {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(ROW_HEIGHT);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setCursor(SceneCursor.POINTER);

        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        label.setFontSize(FONT_LABEL_SIZE);
        Computed<String> text = Computed.create(() -> (isSelected(valueSig.get(), choice.value)
                ? SELECTED_MARK : UNSELECTED_MARK) + choice.label);
        rt.bind(text, label::setText);
        rt.bind(SceneThemes.foreground(rt), label::setTextColor);
        row.appendChild(label);

        rt.on(row, SceneEventType.CLICK, (event, context) -> {
            adapter.onFieldEdit(path, choice.value);
            context.stopPropagation();
        });
        return row;
    }

    private static boolean isSelected(String current, String candidate) {
        String left = current == null ? "" : current.trim();
        String right = candidate == null ? "" : candidate.trim();
        return left.equalsIgnoreCase(right);
    }

    /** 列表行：稳定 key + 写回值 + 显示文本。 */
    private static final class Choice {

        private final long id;
        private final String value;
        private final String label;

        private Choice(long id, String value, String label) {
            this.id = id;
            this.value = value;
            this.label = label;
        }
    }
}
