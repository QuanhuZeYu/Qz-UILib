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
 * <p>{@link #fullOrderSignal()} 是唯一完整顺序真值；{@link #filteredSignal()} 只是只读投影，
 * 不承载第二份可写顺序。筛选、拖拽预览和索引编辑均在此协作者内映射，提交时一次性把完整
 * merged 列表交给现有 DraftSignalAdapter 回调。</p>
 */
public final class FontSortPresentation {

    private static final AtomicLong NEXT_ROW_ID = new AtomicLong(1L);

    private final List<String> discovered;
    private final Consumer<List<String>> commitConsumer;
    private final Signal<List<Row>> fullOrderSignal;
    private final Signal<String> filterSignal;
    private final Signal<Boolean> draggingSignal;
    private final Signal<String> frozenFilterSignal;
    private final Computed<List<Row>> filteredSignal;
    /** 事件帧内立即可见的拖拽门闩；signal 负责响应式投影，门闩负责冻结输入。 */
    private boolean dragActive;
    private List<String> dragStartFull = Collections.emptyList();

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
        this.fullOrderSignal = Signal.create(rows(FontSortOrderModel.merge(this.discovered, initialDraft)));
        this.filterSignal = Signal.create("");
        this.draggingSignal = Signal.create(Boolean.FALSE);
        this.frozenFilterSignal = Signal.create("");
        this.dragActive = false;
        this.filteredSignal = Computed.create(Collections.<Row>emptyList(), () -> {
            List<Row> source = fullOrderSignal.get();
            String filter = Boolean.TRUE.equals(draggingSignal.get())
                    ? frozenFilterSignal.get() : filterSignal.get();
            List<String> values = values(source);
            List<String> visible = FontSortOrderModel.filter(values, filter);
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

    /** @return 当前完整顺序值 */
    public List<String> fullValues() {
        return values(fullOrderSignal.get());
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
        frozenFilterSignal.set(filterSignal.get());
        draggingSignal.set(Boolean.TRUE);
    }

    /**
     * 预览筛选投影的新顺序，不写 draft。
     *
     * @param visibleAfter 当前 MOVE 后可见行顺序
     */
    public void previewVisible(List<Row> visibleAfter) {
        List<Row> visibleBefore = filteredSignal.get();
        List<String> next = FontSortOrderModel.applyVisibleOrder(
                fullValues(), values(visibleBefore), values(visibleAfter));
        replaceFull(next);
    }

    /** UP：若完整顺序确实变化则提交一次，否则回到静默 no-op。 */
    public void finishDrag() {
        List<String> finalOrder = fullValues();
        boolean changed = !dragStartFull.equals(finalOrder);
        dragActive = false;
        draggingSignal.set(Boolean.FALSE);
        if (changed) {
            commitConsumer.accept(finalOrder);
        }
        dragStartFull = Collections.emptyList();
    }

    /** CANCEL：恢复 drag snapshot，不提交。 */
    public void cancelDrag() {
        replaceFull(dragStartFull);
        dragActive = false;
        draggingSignal.set(Boolean.FALSE);
        dragStartFull = Collections.emptyList();
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
        return true;
    }

    /**
     * 外部 draft 变化时重建 merged presentation，不提交、不清理 adapter 冲突。
     *
     * @param draft 外部 draft 值
     */
    public void resetFromDraft(List<String> draft) {
        replaceFull(FontSortOrderModel.merge(discovered, draft));
    }

    /** @return 1-based 当前行索引，行不存在返回 1 */
    public int oneBasedIndex(Row row) {
        if (row == null) {
            return 1;
        }
        List<Row> rows = fullOrderSignal.get();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) == row) {
                return i + 1;
            }
        }
        return 1;
    }

    private void replaceFull(List<String> next) {
        List<Row> old = fullOrderSignal.get();
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
        if (!old.equals(immutable)) {
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

    private static List<String> values(List<Row> rows) {
        List<String> result = new ArrayList<String>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (Row row : rows) {
                result.add(row.getValue());
            }
        }
        return Collections.unmodifiableList(result);
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
