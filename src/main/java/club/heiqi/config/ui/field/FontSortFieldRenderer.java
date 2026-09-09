package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
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
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * fontSort 专用排序渲染器。
 *
 * <p>renderer 在构造时接收当次 screen-open 冻结的 discovered snapshot。打开、合并、筛选和
 * MOVE 预览都只操作 {@link FontSortPresentation} 的 signal；首次成功拖拽、合法索引移动或
 * 显式恢复默认才经 {@link DraftSignalAdapter#onFieldEdit} 提交完整 merged 列表。玩家不能
 * 添加字体、删除字体或改名。</p>
 *
 * <p><b>G15/FontSort 外观口径</b>（契约 §4/§4.1/§4.2）：字段卡片表面与 dirty/error 语义色经
 * {@link FieldShellBinder} 下沉的 FormFieldShell theme-aware 默认路径派生（GROUP 角色），本类不复制；
 * 行结构为「拖拽把手 + 1-based 索引输入 + 字体名」——把手由 {@code SceneDragReorder}（G12）自持
 * INDICATOR tint 档与图标前景，筛选框/索引输入框由 {@code SceneTextInput}（G04）自持 INPUT 配方，
 * 清空按钮由 {@code SceneButton}（G03）自持 BUTTON_STANDARD 配方，滚动条由 {@code SceneScrollbar}
 * （G07）自持，本类一律只组 Props、不叠加第二层表面/边框/滤镜。字体名行标签与空结果提示是本类
 * 仅有的两处文本前景写入点，已改经 {@link SceneThemes#foreground(SceneRuntime)} /
 * {@link SceneThemes#mutedForeground(SceneRuntime)} 主题信号绑定（构建期捕获、effect 内应用，
 * 无 {@code .get()} 快照，契约 §4「textColor 唯一写入者 = 主题前景绑定」）。{@code listHeight}/
 * {@code fontLabel}/{@code fontHelper} 是纯 int 布局/排版常量（契约 §4：主题不接管布局；
 * G15/Support 衔接要点 3）。<b>G15/收口</b>：曾喂给 binder 的 {@code ConfigTheme.asFormTheme()}
 * 兼容占位实参与本类整主题局部快照已随签名收口删除，上述常量换源为
 * {@code FormTheme.defaultDark()} 同源常量构造期直读纯 int（CharRule 先例，逐值相等，默认渲染
 * 输出不变）。排序事务、拖拽提交、索引编辑 authority 协商与 dirty/error 行为零改动。</p>
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
    /**
     * 列表视口高度（controlHeight 纯 int 布局入参 + 派生 stackHost 高度共用，契约 §4.2、
     * G15/Support 衔接要点 3）：G15/收口换源取 {@code FormTheme.defaultDark().listHeight()}
     * 同源值（CharRule 先例，与旧 {@code ConfigTheme.asFormTheme().listHeight()} 逐值相等）。
     */
    private static final int LIST_VIEWPORT_HEIGHT = FormTheme.defaultDark().listHeight();
    /** 行标签字号（纯 int 排版常量，契约 §4.2 主题不接管排版）：换源 defaultDark 同源值。 */
    private static final int FONT_LABEL_SIZE = FormTheme.defaultDark().fontLabel();
    /** 空提示字号（纯 int 排版常量，契约 §4.2 主题不接管排版）：换源 defaultDark 同源值。 */
    private static final int FONT_HELPER_SIZE = FormTheme.defaultDark().fontHelper();
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
        final Supplier<List<String>> currentDraft = () -> toDraftList(adapter.draft().getDraft(path));
        final Supplier<List<String>> currentValue = () -> toDraftList(adapter.draft().getCurrent(path));

        // 外部 reset/reload 只重算 merged presentation；Effect.untrack 防止 reset 读取 full order
        // 形成 draft→presentation→draft 的订阅环。这里不清理冲突，owner 边界仍由 adapter/manager 守护。
        rt.bind(draftSignal, value -> Effect.untrack(
                () -> presentation.resetFromDraft(toDraftList(value))));

        // G15/FontSort 销账 + G15/收口：曾持有的 ConfigTheme.asFormTheme() 整主题快照已拆除——
        // ① 卡片表面/dirty/error 语义色走 FieldShellBinder → FormFieldShell theme-aware 派生
        //    （binder 的 theme 兼容占位形参已随收口从签名删除，见 FieldShellBinder 类头）；
        // ② listHeight 纯 int 控件高度换源为 LIST_VIEWPORT_HEIGHT 同源常量（契约 §4 布局入参，
        //    G15/Support 衔接要点 3、CharRule 先例）。
        // 旧主题色值（textColor/mutedColor）不再从这里取，行标签与空提示前景改经 SceneThemes 信号。
        return FieldShellBinder.build(rt, spec, adapter,
                () -> buildControl(rt, presentation, currentDraft, currentValue),
                LIST_VIEWPORT_HEIGHT);
    }

    /** 构建稳定高度的筛选栏 + viewport + scrollbar。 */
    private static SceneNode buildControl(SceneRuntime rt, FontSortPresentation presentation,
                                          Supplier<List<String>> currentDraft,
                                          Supplier<List<String>> currentValue) {
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
        // 视口高 = LIST_VIEWPORT_HEIGHT 同源常量派生的纯 int 布局入参（契约 §4：主题不接管布局；
        // G15/Support 衔接要点 3、CharRule 先例），非外观写入点
        stackHost.setPreferredHeight(
                Math.max(0, LIST_VIEWPORT_HEIGHT - FILTER_BAR_HEIGHT - ROOT_GAP));
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
        rt.show(viewport, noResults, () -> emptyResult(rt));
        rt.forEach(rowsContainer, presentation.filteredSignal(), ROW_KEY,
                row -> buildRow(rt, presentation, currentDraft, currentValue,
                        rowsContainer, viewport, scrollSignal, row));
        stackHost.appendChild(viewport);
        stackHost.appendChild(scrollbar.column());
        root.appendChild(stackHost);
        return root;
    }

    /**
     * 空结果提示是 viewport 内紧凑次要文本，不改变外层固定高度。
     *
     * <p>G15/FontSort：前景经来源主题 {@code mutedForeground} 信号绑定（构建期在 show 内容
     * builder 内捕获、effect 内应用，主题切换只重派生不重建节点，契约 §4/§4.1）；字号是
     * FONT_HELPER_SIZE 同源排版常量（契约 §4：主题不接管布局，G15/收口换源注依据）。</p>
     */
    private static SceneNode emptyResult(SceneRuntime rt) {
        SceneNode node = new SceneNode();
        node.setPreferredHeight(ROW_HEIGHT);
        node.setText("无匹配字体");
        // 次要前景唯一来源 = 来源主题 mutedForeground 信号（替换旧 theme.mutedColor() 静态取色）
        rt.bind(SceneThemes.mutedForeground(rt), node::setTextColor);
        // 字号 = FONT_HELPER_SIZE 同源排版常量（契约 §4.2：主题不接管布局；G15/收口换源注依据）
        node.setFontSize(FONT_HELPER_SIZE);
        node.setHitTestable(false);
        return node;
    }

    /** 构建单行：拖拽把手 + 固定宽全局索引 + 字体名。 */
    private static SceneNode buildRow(SceneRuntime rt, FontSortPresentation presentation,
                                      Supplier<List<String>> currentDraft,
                                      Supplier<List<String>> currentValue,
                                      SceneNode rowViewport, SceneNode scrollViewport,
                                      Signal<Integer> scrollSignal,
                                      FontSortPresentation.Row row) {
        SceneNode line = SceneNode.row();
        line.setCrossAxisAlign(CrossAxisAlign.CENTER);
        line.setGap(ROW_GAP);
        line.setPreferredHeight(ROW_HEIGHT);
        line.setClipChildren(true);

        SceneNode handle = SceneDragReorder.buildHandle(
                rt, rowViewport, scrollViewport, scrollSignal, row.getId(),
                presentation::immediateFilteredRows, ROW_ID,
                presentation::previewVisible,
                next -> presentation.finishDrag(next, currentDraft.get()),
                ignored -> presentation.cancelDrag(currentDraft.get()),
                () -> presentation.beginDrag(currentDraft.get()));
        line.appendChild(handle);

        String initialIndex = Integer.toString(presentation.oneBasedIndex(row));
        Signal<String> indexText = Signal.create(initialIndex);
        final AtomicReference<String> immediateIndexText = new AtomicReference<String>(initialIndex);
        ReadableSignal<String> immediateIndexValue = () -> {
            indexText.get();
            return immediateIndexText.get();
        };
        SceneTextInput.Props indexProps = new SceneTextInput.Props(
                immediateIndexValue,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "",
                10,
                SceneInputType.TEXT,
                value -> {
                    immediateIndexText.set(value);
                    indexText.set(value);
                });
        SceneNode indexInput = SceneTextInput.create(rt, indexProps).get();
        indexInput.setPreferredWidth(INDEX_WIDTH);
        indexInput.setPreferredHeight(ROW_HEIGHT);
        line.appendChild(indexInput);

        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        label.setFlexGrow(1);
        label.setText(row.getValue());
        // 正文前景唯一来源 = 来源主题 foreground 信号（替换旧 theme.textColor() 静态取色；
        // 构建期在 forEach 项 builder 内捕获、effect 内应用，契约 §4/§4.1）
        rt.bind(SceneThemes.foreground(rt), label::setTextColor);
        // 字号 = FONT_LABEL_SIZE 同源排版常量（契约 §4.2：主题不接管布局；G15/收口换源注依据）
        label.setFontSize(FONT_LABEL_SIZE);
        line.appendChild(label);

        final boolean[] focusActive = {false};
        final AtomicReference<List<String>> focusStartOrder =
                new AtomicReference<List<String>>(Collections.<String>emptyList());
        final AtomicReference<List<String>> focusStartDraft =
                new AtomicReference<List<String>>(Collections.<String>emptyList());
        final AtomicReference<List<String>> focusStartCurrent =
                new AtomicReference<List<String>>(Collections.<String>emptyList());
        final Runnable captureAuthority = () -> {
            focusStartOrder.set(presentation.fullValues());
            focusStartDraft.set(currentDraft.get());
            focusStartCurrent.set(currentValue.get());
        };
        final Runnable restoreCanonicalIndex = () -> {
            String canonical = Integer.toString(presentation.oneBasedIndex(row));
            immediateIndexText.set(canonical);
            indexText.set(canonical);
        };
        final BooleanSupplier authorityUnchanged = () ->
                focusStartOrder.get().equals(presentation.fullValues())
                        && focusStartDraft.get().equals(currentDraft.get())
                        && focusStartCurrent.get().equals(currentValue.get());
        final Runnable adoptExternalAuthority = () -> {
            presentation.resetFromDraft(currentDraft.get());
            restoreCanonicalIndex.run();
            captureAuthority.run();
        };
        final Runnable finishIndexEdit = () -> {
            if (!focusActive[0]) {
                return;
            }
            focusActive[0] = false;
            if (authorityUnchanged.getAsBoolean()) {
                immediateIndexText.set(commitIndex(
                        presentation, row, immediateIndexText.get(), indexText));
            } else {
                adoptExternalAuthority.run();
            }
        };
        rt.on(indexInput, SceneEventType.FOCUS_GAINED, (event, context) -> Effect.untrack(() -> {
            focusActive[0] = true;
            restoreCanonicalIndex.run();
            captureAuthority.run();
        }));
        rt.on(indexInput, SceneEventType.FOCUS_LOST,
                (event, context) -> Effect.untrack(finishIndexEdit));
        rt.on(indexInput, SceneEventType.KEY_DOWN, (event, context) -> {
            if (event.getKeyAction() != SceneKeyAction.PRESSED) {
                return;
            }
            if (event.getKey() == SceneKey.ENTER) {
                Effect.untrack(() -> {
                    if (authorityUnchanged.getAsBoolean()) {
                        immediateIndexText.set(commitIndex(
                                presentation, row, immediateIndexText.get(), indexText));
                        captureAuthority.run();
                    } else {
                        adoptExternalAuthority.run();
                    }
                });
                context.stopPropagation();
            } else if (event.getKey() == SceneKey.ESCAPE) {
                restoreCanonicalIndex.run();
                context.stopPropagation();
            }
        });
        rt.bind(presentation.fullOrderSignal(), ignored -> {
            Effect.untrack(() -> {
                boolean authorityChanged = !focusStartOrder.get().equals(presentation.fullValues())
                        || !focusStartDraft.get().equals(currentDraft.get())
                        || !focusStartCurrent.get().equals(currentValue.get());
                if (!focusActive[0] || authorityChanged) {
                    restoreCanonicalIndex.run();
                    if (focusActive[0]) {
                        captureAuthority.run();
                    }
                }
            });
        });
        Owner owner = Owner.current();
        if (owner != null) {
            // Owner cleanup 逆序执行；最后登记以在 handler/focusable 注销前完成未决索引提交。
            owner.onCleanup(() -> Effect.untrack(finishIndexEdit));
        }
        return line;
    }

    private static String commitIndex(FontSortPresentation presentation,
                                      FontSortPresentation.Row row, String immediateText,
                                      Signal<String> indexText) {
        int current = presentation.oneBasedIndex(row);
        Integer target = FontSortOrderModel.parseOneBasedTarget(immediateText,
                presentation.fullValues().size());
        if (target == null) {
            String canonical = Integer.toString(current);
            indexText.set(canonical);
            return canonical;
        }
        presentation.moveRow(row, target.intValue());
        String canonical = Integer.toString(presentation.oneBasedIndex(row));
        indexText.set(canonical);
        return canonical;
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
