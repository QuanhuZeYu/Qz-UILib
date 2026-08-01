package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * 单个 fontSort screen 的 presentation 协作者。
 *
 * <p>{@link #fullOrderSignal()} 是唯一响应式完整顺序真值；{@link #filteredSignal()} 只是只读投影，
 * 不承载第二份可写顺序。事件帧内另以 {@code immediateFullOrder} 镜像尚未 flush 的最终写入，避免
 * 连续索引编辑回读旧 signal；筛选、拖拽预览和索引编辑均在此协作者内映射，提交时一次性把完整
 * merged 列表交给现有 DraftSignalAdapter 回调。</p>
 */
public final class FontSortPresentation {

    private static final AtomicLong NEXT_ROW_ID = new AtomicLong(1L);

    private final List<String> discovered;
    private final Consumer<List<String>> commitConsumer;
    private final Signal<List<Row>> fullOrderSignal;
    /** 事件帧内立即可见的最终完整顺序；每次 replaceFull 与 signal 同源更新。 */
    private List<Row> immediateFullOrder;
    private final Signal<String> filterSignal;
    private final Signal<Boolean> draggingSignal;
    private final Signal<String> frozenFilterSignal;
    private final Computed<List<Row>> filteredSignal;
    /** 最近一次已反映进 presentation 的原始 draft；可落后于 DraftBuffer 的同帧同步写。 */
    private List<String> reflectedDraft;
    /** 事件帧内立即可见的拖拽门闩；signal 负责响应式投影，门闩负责冻结输入。 */
    private boolean dragActive;
    /** 拖拽开始时同步冻结的 filter；不能回读尚未 flush 的 frozenFilterSignal。 */
    private String dragFilter = "";
    private List<String> dragStartFull = Collections.emptyList();
    private List<String> dragStartVisible = Collections.emptyList();
    private List<String> dragStartDraft = Collections.emptyList();
    private boolean deferredDraftPending;
    private List<String> deferredDraft = Collections.emptyList();

    /**
     * 创建单屏 presentation。
     *
     * @param discovered frozen discovered snapshot
     * @param initialDraft 当前 draft
     * @param commitConsumer 完整列表提交回调
     */
    public FontSortPresentation(List<String> discovered, List<String> initialDraft,
                                Consumer<List<String>> commitConsumer) {
        if (commitConsumer == null) {
            throw new IllegalArgumentException("commitConsumer must not be null");
        }
        this.discovered = FontSortOrderModel.freezeDiscovered(discovered);
        this.commitConsumer = commitConsumer;
        this.reflectedDraft = immutableCopy(initialDraft);
        this.immediateFullOrder = rows(FontSortOrderModel.merge(this.discovered, initialDraft));
        this.fullOrderSignal = Signal.create(immediateFullOrder);
        this.filterSignal = Signal.create("");
        this.draggingSignal = Signal.create(Boolean.FALSE);
        this.frozenFilterSignal = Signal.create("");
        this.dragActive = false;
        this.filteredSignal = Computed.create(Collections.<Row>emptyList(), () -> {
            List<Row> source = fullOrderSignal.get();
            String filter = Boolean.TRUE.equals(draggingSignal.get())
                    ? frozenFilterSignal.get() : filterSignal.get();
            return filterRows(source, filter);
        });
    }

    /** @return 唯一完整顺序 signal（只读视图） */
    public ReadableSignal<List<Row>> fullOrderSignal() {
        return fullOrderSignal;
    }

    /** @return filter signal（只读视图） */
    public ReadableSignal<String> filterSignal() {
        return filterSignal;
    }

    /** @return filtered 只读投影 */
    public ReadableSignal<List<Row>> filteredSignal() {
        return filteredSignal;
    }

    /** 事件帧读取尚未 flush 的完整顺序所形成的当前可见行。 */
    List<Row> immediateFilteredRows() {
        String filter = dragActive ? dragFilter : filterSignal.get();
        return filterRows(immediateFullOrder, filter);
    }

    /** @return 当前完整顺序值 */
    public List<String> fullValues() {
        return values(immediateFullOrder);
    }

    /**
     * 更新筛选。拖拽期间拒绝变更，保证 visible target 不漂移。
     *
     * @param filter 筛选文本
     */
    public void setFilter(String filter) {
        if (dragActive) {
            return;
        }
        filterSignal.set(filter == null ? "" : filter);
    }

    /** 开始拖拽并冻结当时完整顺序与 filter。 */
    public void beginDrag() {
        if (dragActive) {
            return;
        }
        dragActive = true;
        dragStartFull = fullValues();
        dragStartDraft = reflectedDraft;
        String frozenFilter = filterSignal.get();
        dragFilter = frozenFilter;
        dragStartVisible = FontSortOrderModel.filter(dragStartFull, frozenFilter);
        frozenFilterSignal.set(frozenFilter);
        draggingSignal.set(Boolean.TRUE);
    }

    /** renderer 入口：同时冻结 DraftBuffer 的同步真值，用于识别尚未 flush 到 draft signal 的外部更新。 */
    void beginDrag(List<String> currentDraft) {
        if (dragActive) {
            return;
        }
        beginDrag();
        deferChangedAuthoritativeDraft(currentDraft);
    }

    /**
     * 预览筛选投影的新顺序，不写 draft。
     *
     * @param visibleAfter 当前 MOVE 后可见行顺序
     */
    public void previewVisible(List<Row> visibleAfter) {
        if (!dragActive) {
            return;
        }
        List<String> next = FontSortOrderModel.applyVisibleOrder(
                dragStartFull, dragStartVisible, values(visibleAfter));
        replaceFull(next, true);
    }

    /**
     * UP：按事件帧当场算出的最终可见顺序生成完整列表，变化时提交一次。
     *
     * @param finalVisible 最终可见行顺序，不从待 flush 的 preview signal 回读
     */
    public void finishDrag(List<Row> finalVisible) {
        try {
            if (applyDeferredDraft()) {
                return;
            }
            List<String> finalOrder = FontSortOrderModel.applyVisibleOrder(
                    dragStartFull, dragStartVisible, values(finalVisible));
            replaceFull(finalOrder, true);
            boolean changed = !dragStartFull.equals(finalOrder);
            if (changed) {
                commitConsumer.accept(finalOrder);
                reflectedDraft = immutableCopy(finalOrder);
            }
        } finally {
            resetDragState();
        }
    }

    /** renderer 入口：终止事件先同步核对 DraftBuffer 真值，再决定提交拖拽或采用外部更新。 */
    void finishDrag(List<Row> finalVisible, List<String> currentDraft) {
        deferChangedAuthoritativeDraft(currentDraft);
        finishDrag(finalVisible);
    }

    /**
     * 兼容旧调用方；新拖拽路径必须传入按 UP 坐标当场算出的最终 visible 顺序。
     *
     * @deprecated 使用 {@link #finishDrag(List)}，避免读取待 flush 的 preview 投影
     */
    @Deprecated
    public void finishDrag() {
        finishDrag(filteredSignal.get());
    }

    /** CANCEL：恢复 drag snapshot，不提交。 */
    public void cancelDrag() {
        try {
            if (applyDeferredDraft()) {
                return;
            }
            // 必须显式排入起始值，覆盖同一输入帧内尚未 flush 的 preview 写入。
            replaceFull(dragStartFull, true);
        } finally {
            resetDragState();
        }
    }

    /** renderer 入口：CANCEL 同样不得用旧起始快照覆盖尚未 flush 的外部 Draft 更新。 */
    void cancelDrag(List<String> currentDraft) {
        deferChangedAuthoritativeDraft(currentDraft);
        cancelDrag();
    }

    /**
     * 包级测试探针：读取拖拽结束后的瞬态，不构成公共 API。
     *
     * @return 当前拖拽瞬态快照
     */
    DragStateSnapshot __getDragStateForTest() {
        return new DragStateSnapshot(dragActive, Boolean.TRUE.equals(draggingSignal.get()),
                frozenFilterSignal.get(), dragStartFull, dragStartVisible, dragStartDraft,
                deferredDraftPending, deferredDraft);
    }

    /** 统一清理拖拽门闩、视觉 signal、冻结筛选值和起始快照。 */
    private void resetDragState() {
        dragActive = false;
        draggingSignal.set(Boolean.FALSE);
        // frozenFilter 只服务拖拽目标；结束后回到实时 filter，不改写 filterSignal。
        dragFilter = filterSignal.get();
        frozenFilterSignal.set(dragFilter);
        dragStartFull = Collections.emptyList();
        dragStartVisible = Collections.emptyList();
        dragStartDraft = Collections.emptyList();
        deferredDraftPending = false;
        deferredDraft = Collections.emptyList();
    }

    /**
     * 将一行移动到 1-based 全量位置并提交一次。
     *
     * @param row 要移动的行
     * @param oneBasedTarget 1-based 目标位置
     * @return 是否产生实际移动
     */
    public boolean moveRow(Row row, int oneBasedTarget) {
        if (row == null) {
            return false;
        }
        List<String> current = fullValues();
        List<String> next = FontSortOrderModel.moveToOneBased(current, row.getValue(), oneBasedTarget);
        if (current.equals(next)) {
            return false;
        }
        replaceFull(next);
        commitConsumer.accept(next);
        reflectedDraft = immutableCopy(next);
        return true;
    }

    /** 恢复 frozen discovered canonical 顺序，作为一次显式用户提交。 */
    public boolean restoreDefault() {
        List<String> next = FontSortOrderModel.merge(discovered, Collections.<String>emptyList());
        if (fullValues().equals(next)) {
            return false;
        }
        replaceFull(next);
        commitConsumer.accept(next);
        reflectedDraft = immutableCopy(next);
        return true;
    }

    /**
     * 外部 draft 变化时重建 merged presentation，不提交、不清理 adapter 冲突。
     *
     * @param draft 外部 draft 值
     */
    public void resetFromDraft(List<String> draft) {
        List<String> snapshot = immutableCopy(draft);
        if (dragActive) {
            deferredDraftPending = true;
            deferredDraft = snapshot;
            return;
        }
        reflectedDraft = snapshot;
        replaceFull(FontSortOrderModel.merge(discovered, snapshot));
    }

    /** 外部 draft 在拖拽期间变化时优先采用外部真值，不提交基于旧快照的排序。 */
    private boolean applyDeferredDraft() {
        if (!deferredDraftPending) {
            return false;
        }
        replaceFull(FontSortOrderModel.merge(discovered, deferredDraft), true);
        reflectedDraft = deferredDraft;
        return true;
    }

    /** 外部 adapter mutator 先同步写 DraftBuffer、后排队 signal；终止事件据此封住 flush 前覆盖窗口。 */
    private void deferChangedAuthoritativeDraft(List<String> currentDraft) {
        if (dragActive && !dragStartDraft.equals(immutableCopy(currentDraft))) {
            resetFromDraft(currentDraft);
        }
    }

    /** @return 1-based 当前行索引，行不存在返回 1 */
    public int oneBasedIndex(Row row) {
        if (row == null) {
            return 1;
        }
        List<Row> rows = immediateFullOrder;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) == row) {
                return i + 1;
            }
        }
        return 1;
    }

    private void replaceFull(List<String> next) {
        replaceFull(next, false);
    }

    private void replaceFull(List<String> next, boolean forceWrite) {
        List<Row> old = immediateFullOrder;
        Map<String, Row> existing = new HashMap<String, Row>();
        for (Row row : old) {
            existing.put(FontSortOrderModel.identity(row.getValue()), row);
        }
        List<Row> nextRows = new ArrayList<Row>();
        for (String value : next) {
            String key = FontSortOrderModel.identity(value);
            Row row = existing.get(key);
            nextRows.add(row == null ? new Row(value) : row);
        }
        List<Row> immutable = Collections.unmodifiableList(nextRows);
        if (forceWrite || !old.equals(immutable)) {
            immediateFullOrder = immutable;
            fullOrderSignal.set(immutable);
        }
    }

    private static List<Row> rows(List<String> values) {
        List<Row> result = new ArrayList<Row>(values.size());
        for (String value : values) {
            result.add(new Row(value));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Row> filterRows(List<Row> source, String filter) {
        List<String> visible = FontSortOrderModel.filter(values(source), filter);
        Map<String, Row> byIdentity = new HashMap<String, Row>();
        for (Row row : source) {
            byIdentity.put(FontSortOrderModel.identity(row.getValue()), row);
        }
        List<Row> result = new ArrayList<Row>(visible.size());
        for (String value : visible) {
            Row row = byIdentity.get(FontSortOrderModel.identity(value));
            if (row != null) {
                result.add(row);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> values(List<Row> rows) {
        List<String> result = new ArrayList<String>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (Row row : rows) {
                result.add(row.getValue());
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> immutableCopy(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(
                values == null ? Collections.<String>emptyList() : values));
    }

    /** 包级测试探针快照，避免测试反射读取拖拽瞬态。 */
    static final class DragStateSnapshot {
        final boolean dragActive;
        final boolean draggingSignal;
        final String frozenFilter;
        final List<String> dragStartFull;
        final List<String> dragStartVisible;
        final List<String> dragStartDraft;
        final boolean deferredDraftPending;
        final List<String> deferredDraft;

        DragStateSnapshot(boolean dragActive, boolean draggingSignal, String frozenFilter,
                          List<String> dragStartFull, List<String> dragStartVisible, List<String> dragStartDraft,
                          boolean deferredDraftPending, List<String> deferredDraft) {
            this.dragActive = dragActive;
            this.draggingSignal = draggingSignal;
            this.frozenFilter = frozenFilter;
            this.dragStartFull = dragStartFull;
            this.dragStartVisible = dragStartVisible;
            this.dragStartDraft = dragStartDraft;
            this.deferredDraftPending = deferredDraftPending;
            this.deferredDraft = deferredDraft;
        }
    }

    /** keyed fontSort 行。 */
    public static final class Row {
        private final long id = NEXT_ROW_ID.getAndIncrement();
        private final String value;

        private Row(String value) {
            this.value = FontSortOrderModel.displayName(value);
        }

        /** @return keyed diff 身份 */
        public long getId() {
            return id;
        }

        /** @return canonical 字体显示名称 */
        public String getValue() {
            return value;
        }
    }
}
