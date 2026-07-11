package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneButtonVariant;
import club.heiqi.uilib.ui.scene.control.SceneDragReorder;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

/**
 * fontSort 专用排序渲染器。
 *
 * <p>renderer 在构造时接收当次 screen-open 冻结的 discovered snapshot。打开、合并、筛选和
 * MOVE 预览都只操作 {@link FontSortPresentation} 的 signal；首次成功拖拽、合法索引移动或
 * 显式恢复默认才经 {@link DraftSignalAdapter#onFieldEdit} 提交完整 merged 列表。玩家不能
 * 添加字体、删除字体或改名。</p>
 */
public final class FontSortFieldRenderer implements FieldRenderer {

    /** 顶部筛选栏高度。 */
    private static final int FILTER_BAR_HEIGHT = 30;
    /** 清空按钮固定宽度。 */
    private static final int CLEAR_BUTTON_SIZE = 30;
    /** 筛选栏与列表间距。 */
    private static final int ROOT_GAP = 6;
    /** 列表行固定高度。 */
    private static final int ROW_HEIGHT = 30;
    /** 拖拽把手与索引输入之间的间距。 */
    private static final int ROW_GAP = 6;
    /** 1-based 索引输入固定宽度。 */
    private static final int INDEX_WIDTH = 56;
    /** 字体行 id 读取器，维持 keyed diff。 */
    private static final ToLongFunction<FontSortPresentation.Row> ROW_ID =
            FontSortPresentation.Row::getId;
    /** SceneRuntime keyed reconciler 使用的 boxed key 函数。 */
    private static final java.util.function.Function<FontSortPresentation.Row, Long> ROW_KEY =
            row -> Long.valueOf(row.getId());

    /** screen-open 时冻结的发现顺序。 */
    private final List<String> discoveredSnapshot;

    /**
     * 创建无发现字体的兼容 renderer。
     */
    public FontSortFieldRenderer() {
        this(Collections.<String>emptyList());
    }

    /**
     * 创建带 frozen discovered snapshot 的 renderer。
     *
     * @param discoveredSnapshot screen-open 时捕获的发现顺序
     */
    public FontSortFieldRenderer(List<String> discoveredSnapshot) {
        this.discoveredSnapshot = FontSortOrderModel.freezeDiscovered(discoveredSnapshot);
    }

    /**
     * 兼容旧接入方：Supplier 只在 renderer 构造时读取一次，render 期不会重新发现字体。
     *
     * @param discoveredSnapshotProvider 构造期发现快照源
     * @deprecated 使用 {@link #FontSortFieldRenderer(List)}，以明确 snapshot 生命周期
     */
    @Deprecated
    public FontSortFieldRenderer(Supplier<List<String>> discoveredSnapshotProvider) {
        this(discoveredSnapshotProvider == null ? Collections.<String>emptyList()
                : discoveredSnapshotProvider.get());
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSignal = adapter.draftSignal(path);
        final List<String> initialDraft = toDraftList(draftSignal.get());
        final FontSortPresentation presentation = new FontSortPresentation(
                discoveredSnapshot, initialDraft,
                next -> adapter.onFieldEdit(path, next));

        // 外部 reset/reload 只重算 merged presentation；Effect.untrack 防止 reset 读取 full order
        // 形成 draft→presentation→draft 的订阅环。这里不清理冲突，owner 边界仍由 adapter/manager 守护。
        rt.bind(draftSignal, value -> Effect.untrack(
                () -> presentation.resetFromDraft(toDraftList(value))));

        FormTheme theme = ConfigTheme.asFormTheme();
        return FieldShellBinder.build(rt, spec, adapter,
                () -> buildControl(rt, presentation, theme), theme, theme.listHeight());
    }

    /** 构建稳定高度的筛选栏 + viewport + scrollbar。 */
    private static SceneNode buildControl(SceneRuntime rt, FontSortPresentation presentation,
                                          FormTheme theme) {
        SceneNode root = SceneNode.column();
        root.setGap(ROOT_GAP);

        SceneNode filterBar = SceneNode.row();
        filterBar.setPreferredHeight(FILTER_BAR_HEIGHT);
        filterBar.setCrossAxisAlign(CrossAxisAlign.CENTER);

        SceneTextInput.Props filterProps = new SceneTextInput.Props(
                presentation.filterSignal(),
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "",
                Integer.MAX_VALUE,
                SceneInputType.TEXT,
                presentation::setFilter);
        SceneNode filterInput = SceneTextInput.create(rt, filterProps).get();
        filterInput.setPreferredHeight(FILTER_BAR_HEIGHT);
        filterInput.setFlexGrow(1);
        filterBar.appendChild(filterInput);

        SceneButton.Props clearProps = new SceneButton.Props(
                Signal.create("\u00d7"),
                Signal.create(Boolean.TRUE),
                () -> presentation.setFilter(""),
                SceneButtonVariant.STANDARD);
        SceneNode clearButton = SceneButton.create(rt, clearProps).get();
        clearButton.setPreferredWidth(CLEAR_BUTTON_SIZE);
        clearButton.setPreferredHeight(CLEAR_BUTTON_SIZE);
        filterBar.appendChild(clearButton);
        root.appendChild(filterBar);

        SceneNode stackHost = SceneNode.row();
        stackHost.setPreferredHeight(Math.max(0, theme.listHeight() - FILTER_BAR_HEIGHT - ROOT_GAP));
        stackHost.setFillParentHeight(true);
        SceneNode viewport = SceneNode.column();
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setFillParentHeight(true);
        viewport.setFlexGrow(1);
        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);
        SceneScrollbar.Result scrollbar = SceneScrollbar.createDefault(rt, viewport, scrollSignal);
        scrollbar.column().setPreferredWidth(SceneScrollbar.DEFAULT_BAR_WIDTH);
        SceneNode rowsContainer = SceneNode.column();
        viewport.appendChild(rowsContainer);
        Computed<Boolean> noResults = Computed.create(() ->
                Boolean.valueOf(presentation.filteredSignal().get().isEmpty()));
        rt.show(viewport, noResults, () -> emptyResult(theme));
        rt.forEach(rowsContainer, presentation.filteredSignal(), ROW_KEY,
                row -> buildRow(rt, presentation, rowsContainer, viewport, scrollSignal, row, theme));
        stackHost.appendChild(viewport);
        stackHost.appendChild(scrollbar.column());
        root.appendChild(stackHost);
        return root;
    }

    /** 空结果提示是 viewport 内紧凑次要文本，不改变外层固定高度。 */
    private static SceneNode emptyResult(FormTheme theme) {
        SceneNode node = new SceneNode();
        node.setPreferredHeight(ROW_HEIGHT);
        node.setText("无匹配字体");
        node.setTextColor(theme.mutedColor());
        node.setFontSize(theme.fontHelper());
        node.setHitTestable(false);
        return node;
    }

    /** 构建单行：拖拽把手 + 固定宽全局索引 + 字体名。 */
    private static SceneNode buildRow(SceneRuntime rt, FontSortPresentation presentation,
                                      SceneNode rowViewport, SceneNode scrollViewport,
                                      Signal<Integer> scrollSignal,
                                      FontSortPresentation.Row row, FormTheme theme) {
        SceneNode line = SceneNode.row();
        line.setCrossAxisAlign(CrossAxisAlign.CENTER);
        line.setGap(ROW_GAP);
        line.setPreferredHeight(ROW_HEIGHT);
        line.setClipChildren(true);

        SceneNode handle = SceneDragReorder.buildHandle(
                rt, rowViewport, scrollViewport, scrollSignal, row.getId(),
                presentation.filteredSignal(), ROW_ID,
                presentation::previewVisible,
                ignored -> presentation.finishDrag(),
                ignored -> presentation.cancelDrag(),
                presentation::beginDrag);
        line.appendChild(handle);

        Signal<String> indexText = Signal.create(Integer.toString(presentation.oneBasedIndex(row)));
        SceneTextInput.Props indexProps = new SceneTextInput.Props(
                indexText,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "",
                10,
                SceneInputType.TEXT,
                indexText::set);
        SceneNode indexInput = SceneTextInput.create(rt, indexProps).get();
        indexInput.setPreferredWidth(INDEX_WIDTH);
        indexInput.setPreferredHeight(ROW_HEIGHT);
        line.appendChild(indexInput);

        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        label.setFlexGrow(1);
        label.setText(row.getValue());
        label.setTextColor(theme.textColor());
        label.setFontSize(theme.fontLabel());
        line.appendChild(label);

        SceneInteractionState indexInteraction = rt.interactionState(indexInput);
        final boolean[] focusSeen = {false};
        rt.bind(indexInteraction.focused(), focused -> {
            boolean isFocused = Boolean.TRUE.equals(focused);
            if (isFocused) {
                focusSeen[0] = true;
            } else if (focusSeen[0]) {
                focusSeen[0] = false;
                commitIndex(presentation, row, indexText);
            }
        });
        rt.on(indexInput, SceneEventType.KEY_DOWN, (event, context) -> {
            if (event.getKeyAction() != SceneKeyAction.PRESSED) {
                return;
            }
            if (event.getKey() == SceneKey.ENTER) {
                commitIndex(presentation, row, indexText);
                context.stopPropagation();
            } else if (event.getKey() == SceneKey.ESCAPE) {
                indexText.set(Integer.toString(presentation.oneBasedIndex(row)));
                context.stopPropagation();
            }
        });
        rt.bind(presentation.fullOrderSignal(), ignored -> {
            Effect.untrack(() -> {
                if (!Boolean.TRUE.equals(indexInteraction.focused().get())) {
                    indexText.set(Integer.toString(presentation.oneBasedIndex(row)));
                }
            });
        });
        return line;
    }

    private static void commitIndex(FontSortPresentation presentation,
                                    FontSortPresentation.Row row, Signal<String> indexText) {
        int current = presentation.oneBasedIndex(row);
        Integer target = FontSortOrderModel.parseOneBasedTarget(indexText.get(),
                presentation.fullValues().size());
        if (target == null) {
            indexText.set(Integer.toString(current));
            return;
        }
        presentation.moveRow(row, target.intValue());
        // moveRow 的目标已 clamp；写回 canonical 当前索引，Enter 后 blur 会成为 no-op。
        indexText.set(Integer.toString(target.intValue()));
    }

    @SuppressWarnings("unchecked")
    private static List<String> toDraftList(Object value) {
        if (!(value instanceof List)) {
            return new ArrayList<String>();
        }
        List<Object> values = (List<Object>) value;
        List<String> result = new ArrayList<String>(values.size());
        for (Object item : values) {
            result.add(item == null ? "" : String.valueOf(item));
        }
        return result;
    }
}
