package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneDragReorder;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

/**
 * fontSort 专用只读排序渲染器。
 *
 * <p>字段真值仍是 {@code List<String>}，本类只负责把它渲染为可拖拽排序的字体名列表：
 * 行内只有拖拽把手与字体名文本，不提供输入框、添加按钮或删除按钮。发现态预填充语义沿用
 * {@link SimpleListFieldRenderer}：draft 为空且发现源非空时经
 * {@link DraftSignalAdapter#seedPresentation} 只更新 UI 展示（不写 DraftBuffer），保持 dirty=false；
 * 用户拖拽调序后才写回 draft 并标脏。</p>
 */
public final class FontSortFieldRenderer implements FieldRenderer {

    /** 控件根纵向间距。 */
    private static final int ROOT_GAP = 6;
    /** 列表行间距。 */
    private static final int LIST_GAP = 6;
    /** 行内控件间距。 */
    private static final int ROW_GAP = 6;
    /** 字体行卡片固定高度。 */
    private static final int ROW_CARD_HEIGHT = 36;
    /** 字体行卡片 idle 背景色。 */
    private static final int ROW_CARD_BG_IDLE = 0xFF152238;
    /** 字体行卡片 hover 背景色。 */
    private static final int ROW_CARD_BG_HOVER = 0xFF1E2E4A;
    /** 字体行卡片边框色。 */
    private static final int ROW_CARD_BORDER = 0xFF2F4D87;
    /** 行 id 分配器，用于 keyed 列表稳定身份。 */
    private static final AtomicLong NEXT_ITEM_ID = new AtomicLong(1L);
    /** fontSort 行 id 读取器。 */
    private static final ToLongFunction<FontSortItem> FONT_SORT_ITEM_ID = item -> item.getId();

    /** 发现态预填充源，null 表示不预填充。 */
    private final Supplier<List<String>> prefillWhenEmpty;

    /**
     * 创建无预填充源的 fontSort 渲染器。
     */
    public FontSortFieldRenderer() {
        this(null);
    }

    /**
     * 创建带发现态预填充源的 fontSort 渲染器。
     *
     * @param prefillWhenEmpty 预填充源；null 表示不预填充
     */
    public FontSortFieldRenderer(Supplier<List<String>> prefillWhenEmpty) {
        this.prefillWhenEmpty = prefillWhenEmpty;
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);
        final FormTheme theme = ConfigTheme.asFormTheme();

        List<String> initial = toDraftList(draftSig.get());
        if (initial.isEmpty() && prefillWhenEmpty != null) {
            List<String> prefill = prefillWhenEmpty.get();
            if (prefill != null && !prefill.isEmpty()) {
                // presentation seed：只展示，不写 DraftBuffer / 不进 candidate
                adapter.seedPresentation(path, new ArrayList<String>(prefill));
                initial = new ArrayList<String>(prefill);
            }
        }

        // D2：DraftListBridge 统一 localItems + reset 守卫（untrack 投影；presentation 感知）
        final DraftListBridge<FontSortItem> bridge = DraftListBridge.create(
                rt, draftSig, initial,
                FontSortFieldRenderer::toDraftList,
                FontSortFieldRenderer::toItems,
                FontSortFieldRenderer::projectValues,
                null,
                adapter,
                path);
        final Signal<List<FontSortItem>> localItems = bridge.localItems();

        return FieldShellBinder.build(rt, spec, adapter,
                () -> buildControl(rt, bridge, path, adapter, theme),
                theme, theme.listHeight());
    }

    /**
     * 构建只读字体排序控件。
     *
     * @param rt         场景运行时
     * @param localItems 本地字体行列表
     * @param path       字段路径
     * @param adapter    草稿适配器
     * @param theme      主题 token
     * @return 控件根节点
     */
    private static SceneNode buildControl(SceneRuntime rt,
                                          DraftListBridge<FontSortItem> bridge,
                                          String path,
                                          DraftSignalAdapter adapter,
                                          FormTheme theme) {
        Signal<List<FontSortItem>> localItems = bridge.localItems();
        SceneNode root = SceneNode.column();
        root.setGap(ROOT_GAP);

        SceneNode viewport = SceneNode.column();
        viewport.setGap(LIST_GAP);
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setFillParentHeight(true);
        viewport.setFlexGrow(1);

        SceneNode stackHost = SceneNode.row();
        stackHost.setFillParentHeight(true);
        stackHost.appendChild(viewport);

        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);
        SceneScrollbar.Result scrollbar = SceneScrollbar.createDefault(rt, viewport, scrollSignal);
        stackHost.appendChild(scrollbar.column());
        root.appendChild(stackHost);

        Computed<List<FontSortItem>> itemsComputed = Computed.create(() -> safeItems(localItems.get()));
        rt.forEach(viewport, itemsComputed, FontSortItem::getId,
                row -> buildRow(rt, bridge, path, adapter, viewport, scrollSignal, row, theme));
        return root;
    }

    /**
     * 构建字体名只读行。
     */
    private static SceneNode buildRow(SceneRuntime rt,
                                      DraftListBridge<FontSortItem> bridge,
                                      String path,
                                      DraftSignalAdapter adapter,
                                      SceneNode viewport,
                                      Signal<Integer> scrollSignal,
                                      FontSortItem row,
                                      FormTheme theme) {
        SceneNode line = SceneNode.row();
        line.setCrossAxisAlign(CrossAxisAlign.CENTER);
        line.setGap(ROW_GAP);
        line.setPreferredHeight(ROW_CARD_HEIGHT);
        line.setPadding(0, SceneChromeTokens.PAD_MD, 0, SceneChromeTokens.PAD_MD);
        line.setBackgroundColor(ROW_CARD_BG_IDLE);
        line.setBorderWidth(1);
        line.setBorderColor(ROW_CARD_BORDER);
        line.setCornerRadius(SceneChromeTokens.RADIUS_MD);

        SceneNode handle = buildDragHandle(rt, bridge, path, adapter, viewport, scrollSignal, row);
        SceneInteractionState lineInteraction = rt.interactionState(line);
        SceneInteractionState handleInteraction = rt.interactionState(handle);
        rt.bindComputed(() -> {
            boolean lineHovered = Boolean.TRUE.equals(lineInteraction.hovered().get());
            boolean handleHovered = Boolean.TRUE.equals(handleInteraction.hovered().get());
            boolean handlePressed = Boolean.TRUE.equals(handleInteraction.pressed().get());
            return lineHovered || handleHovered || handlePressed ? ROW_CARD_BG_HOVER : ROW_CARD_BG_IDLE;
        }, line::setBackgroundColor);

        line.appendChild(handle);

        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        label.setText(row.getValue());
        label.setTextColor(theme.textColor());
        label.setFontSize(theme.fontLabel());
        line.appendChild(label);
        return line;
    }

    /**
     * 构建拖拽把手并注册排序事件。
     */
    private static SceneNode buildDragHandle(SceneRuntime rt,
                                             DraftListBridge<FontSortItem> bridge,
                                             String path,
                                             DraftSignalAdapter adapter,
                                             SceneNode viewport,
                                             Signal<Integer> scrollSignal,
                                             FontSortItem row) {
        final long dragId = row.getId();
        Signal<List<FontSortItem>> localItems = bridge.localItems();
        Consumer<List<FontSortItem>> commit = next ->
                bridge.commit(path, adapter, next, DraftListBridge.CommitMode.SET_THEN_EDIT);
        return SceneDragReorder.buildHandle(rt, viewport, scrollSignal, dragId, localItems, FONT_SORT_ITEM_ID,
                next -> localItems.set(immutableItems(next)), commit, snapshot -> localItems.set(immutableItems(snapshot)));
    }

    private static List<FontSortItem> immutableItems(List<FontSortItem> items) {
        return Collections.unmodifiableList(new ArrayList<FontSortItem>(safeItems(items)));
    }

    @SuppressWarnings("unchecked")
    private static List<String> toDraftList(Object value) {
        if (value instanceof List) {
            List<String> out = new ArrayList<String>(((List<Object>) value).size());
            for (Object item : (List<Object>) value) {
                out.add(item == null ? "" : String.valueOf(item));
            }
            return out;
        }
        return new ArrayList<String>();
    }

    private static List<FontSortItem> toItems(List<String> values) {
        List<FontSortItem> out = new ArrayList<FontSortItem>(values.size());
        for (String value : values) {
            out.add(new FontSortItem(value));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<String> projectValues(List<FontSortItem> items) {
        List<FontSortItem> safe = safeItems(items);
        List<String> out = new ArrayList<String>(safe.size());
        for (FontSortItem item : safe) {
            out.add(item.getValue());
        }
        return out;
    }

    private static List<FontSortItem> safeItems(List<FontSortItem> items) {
        return items == null ? Collections.<FontSortItem>emptyList() : items;
    }

    /** fontSort 行数据，id 稳定用于 keyed diff。 */
    private static final class FontSortItem {

        private final long id;
        private final String value;

        private FontSortItem(String value) {
            this.id = NEXT_ITEM_ID.getAndIncrement();
            this.value = value == null ? "" : value;
        }

        private long getId() {
            return id;
        }

        private String getValue() {
            return value;
        }
    }
}
