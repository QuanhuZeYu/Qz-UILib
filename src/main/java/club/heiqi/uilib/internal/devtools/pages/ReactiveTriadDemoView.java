package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.component.UiComponentRuntime;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.control.ReactiveControlBindings;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 声明式三基石（条件 {@code show} / 列表 {@code forEach} / 文本 {@code bindText}）的<b>纯组件层</b>视图。
 *
 * <p>本类只依赖 {@link UiDocument}（④DOM）+ {@link UiComponentRuntime}（③组件层）+ 控件，<b>不认识 GuiScreen /
 * 渲染后端</b>——因此可脱离 Minecraft 宿主独立构造、单元测试（与 {@code ModernConfigSearchFilter} 同样的
 * 「组件而非屏幕」定位）。{@link ReactiveTriadDemoScreen} 只是它的 GuiScreen 宿主壳。</p>
 *
 * <p><b>纯 signal 驱动（信条一/I1）</b>：全部界面变化只经由改 signal 触发，不存在第二条改 UI 的路径。</p>
 * <ul>
 *   <li><b>{@code forEach}</b>（I5/I7）：任务行以 {@code 任务id + 完成态} 为 key，增删 / 打乱 / 切换完成时
 *       只增删移动变化行，稳定行复用 DOM 节点不重建。</li>
 *   <li><b>{@code bindText}</b>：底部「共 N 项 · 已完成 M 项」由 {@link Computed} 从任务 signal 派生并自动刷新。</li>
 *   <li><b>{@code show}</b>（I7）：① 列表为空时由派生条件显示空态提示；② 开关控制说明区块显隐，稳定时不重建。</li>
 * </ul>
 *
 * <p><b>生命周期</b>：{@code forEach}/{@code show}/{@code bindText} 的 effect 归属 {@code runtime} 根作用域，由宿主
 * {@code runtime.dispose()}（经 widget.close()）回收。构造期创建的 {@link Computed}（无 Owner 作用域）须由宿主
 * 调用 {@link #dispose()} 单独清理，否则其 recompute effect 残留全局调度器被持续重跑。</p>
 */
final class ReactiveTriadDemoView {

    private final UiDocument document;
    private final UiComponentRuntime runtime;
    private final ElementNode rootElement;

    /** 任务列表 signal：界面唯一数据源（I1），驱动 forEach 与两个派生条件/文本。 */
    private final Signal<List<Task>> tasksSignal;
    /** 说明区块显隐开关 signal：驱动 show 条件渲染。 */
    private final Signal<Boolean> detailsVisibleSignal;
    /** 派生：列表是否为空（驱动空态提示的 show）。 */
    private final Computed<Boolean> emptyStateCondition;
    /** 派生：「共 N 项 · 已完成 M 项」计数文本（驱动 bindText）。 */
    private final Computed<String> countTextSource;

    private final DocumentButtonControl addButton;
    private final DocumentButtonControl removeButton;
    private final DocumentButtonControl shuffleButton;
    private final DocumentToggleSwitchControl detailsToggle;

    /**
     * 构造期建立的事件绑定句柄（按钮 action / 开关变更）：随 {@code runtime.dispose()} 的根作用域自动退订，
     * 同时由 {@link #clearHandlers()} 做防御性显式退订（幂等，与自动退订不冲突）。
     */
    private final List<UiComponentRuntime.Binding> constructionBindings = new ArrayList<UiComponentRuntime.Binding>();

    /** 下一个任务的稳定 id（递增分配，保证 forEach key 唯一稳定）。 */
    private int nextTaskId;

    /**
     * 构造视图并完成声明式组装（三基石登记 + 控件事件接线）。
     *
     * @param document 所属文档
     * @param runtime  组件运行时（提供 forEach/show/bindText）
     */
    ReactiveTriadDemoView(UiDocument document, UiComponentRuntime runtime) {
        this.document = document;
        this.runtime = runtime;
        this.rootElement = document.div();

        List<Task> initial = new ArrayList<Task>();
        initial.add(new Task(nextTaskId++, "阅读 NORTH_STAR 宪章", true));
        initial.add(new Task(nextTaskId++, "实现 forEach 列表协调", true));
        initial.add(new Task(nextTaskId++, "真机验证 show / bindText", false));
        this.tasksSignal = Signal.create(Collections.unmodifiableList(initial));
        this.detailsVisibleSignal = Signal.create(Boolean.FALSE);

        // 派生源必须在 bindText / show 之前创建（注册顺序即粗略拓扑序：Computed 先于其下游消费方）。
        this.countTextSource = Computed.create(this::deriveCountText);
        this.emptyStateCondition = Computed.create(this::deriveEmptyState);

        this.addButton = createButton("添加任务", 0xFF2563EB, 0xFF1D4ED8);
        this.removeButton = createButton("移除末项", 0xFF7C3AED, 0xFF6D28D9);
        this.shuffleButton = createButton("打乱顺序", 0xFFF59E0B, 0xFFD97706);
        this.detailsToggle = new DocumentToggleSwitchControl(document)
                .setTrackColors(0xFF475569, 0xFF22C55E, 0xFF334155);

        wireHandlers();
        assembleDeclarativeUi();
    }

    /**
     * 返回视图根元素，供宿主注入文档流。
     *
     * @return 根元素
     */
    ElementNode getRootElement() {
        return rootElement;
    }

    /**
     * 释放构造期创建的派生源（{@link Computed}）。
     *
     * <p>三基石的 effect 归属 {@code runtime} 根作用域、由 {@code runtime.dispose()} 回收；本方法只补清两个
     * 构造期 Computed（它们不在任何 Owner 作用域内，否则其 recompute effect 泄漏到全局调度器）。</p>
     */
    void dispose() {
        countTextSource.dispose();
        emptyStateCondition.dispose();
    }

    // ── 声明式 UI 组装（三基石全程只读/订阅 signal，不命令式增删节点） ──────────────

    /**
     * 组装整页声明式结构：标题 → 操作按钮行 → 说明开关行 → {@code show} 说明区块 →
     * {@code forEach} 任务列表 + {@code show} 空态提示 → {@code bindText} 计数页脚。
     */
    private void assembleDeclarativeUi() {
        rootElement.setAttribute("data-reactive-demo", "root");
        rootElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(12))
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(0xFF0F172A);

        appendHeader(rootElement);
        appendActionRow(rootElement);
        appendDetailsToggleRow(rootElement);
        appendDetailsSection(rootElement);
        appendTaskList(rootElement);
        appendCountFooter(rootElement);
    }

    /** 标题与说明文本（静态结构，无需 signal）。 */
    private void appendHeader(ElementNode parent) {
        ElementNode title = document.div();
        title.style().setTextColor(0xFFF8FAFC).setFontSize(UiStyleLength.px(14));
        title.appendText("声明式三基石 demo（show / forEach / bindText）");
        parent.append(title);

        ElementNode subtitle = document.div();
        subtitle.style().setTextColor(0xFF94A3B8);
        subtitle.appendText("全部界面变化只经由改 signal 触发——纯 UI = f(state)。");
        parent.append(subtitle);
    }

    /** 操作按钮行：添加 / 移除 / 打乱（点击仅写 signal）。 */
    private void appendActionRow(ElementNode parent) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setRowGap(UiStyleLength.px(8));
        row.append(addButton.getElement());
        row.append(removeButton.getElement());
        row.append(shuffleButton.getElement());
        parent.append(row);
    }

    /** 「显示说明」开关行（toggle 改 detailsVisibleSignal，驱动下方 show）。 */
    private void appendDetailsToggleRow(ElementNode parent) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8));
        ElementNode label = document.div();
        label.style().setTextColor(0xFFE2E8F0);
        label.appendText("显示说明区块");
        row.append(label);
        row.append(detailsToggle.getElement());
        parent.append(row);
    }

    /**
     * {@code show} 基石①：说明区块。{@code detailsVisibleSignal} 为真时挂载，为假时卸载；稳定值不重建（I7）。
     */
    private void appendDetailsSection(ElementNode parent) {
        runtime.show(parent, detailsVisibleSignal, doc -> {
            ElementNode panel = doc.div();
            panel.setAttribute("data-reactive-demo", "details");
            panel.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.COLUMN)
                    .setRowGap(UiStyleLength.px(4))
                    .setPadding(UiStyleLength.px(10))
                    .setBackgroundColor(0xFF1E293B)
                    .setBorderColor(0xFF334155)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderRadius(UiStyleLength.px(10))
                    .setTextColor(0xFFCBD5E1);
            panel.appendText("show：条件渲染——开关翻转才挂载/卸载本区块，稳定时不重建。");
            ElementNode line2 = doc.div();
            line2.style().setTextColor(0xFF93C5FD);
            line2.appendText("forEach：列表按 key 协调，增删/打乱只动变化行。");
            panel.append(line2);
            ElementNode line3 = doc.div();
            line3.style().setTextColor(0xFF93C5FD);
            line3.appendText("bindText：底部计数由 Computed 从任务 signal 派生。");
            panel.append(line3);
            return panel;
        });
    }

    /**
     * {@code forEach} 基石②：任务列表容器 + {@code show} 基石③：空态提示（并存于同一父节点，各订各自的源）。
     */
    private void appendTaskList(ElementNode parent) {
        ElementNode listContainer = document.div();
        listContainer.setAttribute("data-reactive-demo", "task-list");
        listContainer.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6));
        parent.append(listContainer);

        runtime.forEach(listContainer, tasksSignal,
                ReactiveTriadDemoView::taskRowKey,
                (doc, task) -> createTaskRow(doc, task));

        runtime.show(listContainer, emptyStateCondition, doc -> {
            ElementNode hint = doc.div();
            hint.setAttribute("data-reactive-demo", "empty-hint");
            hint.style()
                    .setPadding(UiStyleLength.px(10))
                    .setTextColor(0xFF64748B);
            hint.appendText("（任务清单为空——点击「添加任务」新增一项）");
            return hint;
        });
    }

    /**
     * {@code bindText} 基石：页脚计数文本，绑定到 {@link #countTextSource}（Computed 派生），随任务 signal 自动刷新。
     */
    private void appendCountFooter(ElementNode parent) {
        ElementNode footer = document.div();
        footer.setAttribute("data-reactive-demo", "count-footer");
        footer.style()
                .setTextColor(0xFF38BDF8)
                .setPadding(UiStyleLength.px(4));
        TextNode countText = footer.appendText("");
        runtime.bindText(countText, countTextSource);
        parent.append(footer);
    }

    /** 创建单个任务行。每个 key 只构建一次（I3）；点击切换该任务完成态（改 signal）。 */
    private ElementNode createTaskRow(UiDocument doc, Task task) {
        final int taskId = task.getId();
        ElementNode row = doc.div();
        row.setAttribute("data-reactive-demo", "task-row");
        row.setAttribute("data-task-id", Integer.toString(taskId));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(task.isDone() ? 0xFF14532D : 0xFF162132)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(8));

        ElementNode marker = doc.div();
        marker.style().setTextColor(task.isDone() ? 0xFF4ADE80 : 0xFF64748B);
        marker.appendText(task.isDone() ? "[x]" : "[ ]");
        row.append(marker);

        ElementNode label = doc.div();
        label.style().setTextColor(0xFFF8FAFC);
        label.appendText(task.getLabel());
        row.append(label);

        DocumentButtonControl toggleButton =
                createButton(task.isDone() ? "标记未完成" : "标记完成", 0xFF334155, 0xFF1E293B);
        // 在 forEach 的「项作用域」内调用：on 内部 Owner.current() 取到该 item owner，
        // 行被移除时 owner.dispose() 自动 setActionHandler(null)，根除潜在悬挂监听器（信条一/I1）。
        ReactiveControlBindings.onAction(runtime, toggleButton, () -> toggleTaskDone(taskId));
        row.append(toggleButton.getElement());
        return row;
    }

    // ── signal 写入（输入半环：事件 → 改 signal；本 demo 用最小内联写入，不引入额外封装） ──────

    /** 添加一项任务到列表末尾（构造新列表后 set，触发 forEach 增量增行 + 计数重算）。 */
    void addTask() {
        List<Task> next = new ArrayList<Task>(tasksSignal.get());
        next.add(new Task(nextTaskId++, "新任务 #" + nextTaskId, false));
        tasksSignal.set(Collections.unmodifiableList(next));
    }

    /** 移除列表末项（列表为空时无操作）。 */
    void removeLastTask() {
        List<Task> current = tasksSignal.get();
        if (current.isEmpty()) {
            return;
        }
        List<Task> next = new ArrayList<Task>(current);
        next.remove(next.size() - 1);
        tasksSignal.set(Collections.unmodifiableList(next));
    }

    /** 打乱列表顺序（确定性「末项移到首位」，便于真机观察；证明 forEach 的 LIS 最小移动）。 */
    void shuffleTasks() {
        List<Task> current = tasksSignal.get();
        if (current.size() < 2) {
            return;
        }
        List<Task> next = new ArrayList<Task>(current);
        Task last = next.remove(next.size() - 1);
        next.add(0, last);
        tasksSignal.set(Collections.unmodifiableList(next));
    }

    /**
     * 切换指定任务的完成态：原地替换为完成态翻转的新 Task 实例（id 不变，完成态变）。
     * key 含完成态 → forEach 只重建该行；其余行复用（守 I5/I7）。
     *
     * @param taskId 目标任务 id
     */
    void toggleTaskDone(int taskId) {
        List<Task> current = tasksSignal.get();
        List<Task> next = new ArrayList<Task>(current.size());
        boolean changed = false;
        for (Task task : current) {
            if (task.getId() == taskId) {
                next.add(new Task(task.getId(), task.getLabel(), !task.isDone()));
                changed = true;
            } else {
                next.add(task);
            }
        }
        if (changed) {
            tasksSignal.set(Collections.unmodifiableList(next));
        }
    }

    /** 设置说明区块显隐（供 toggle 事件与测试调用）。 */
    void setDetailsVisible(boolean visible) {
        detailsVisibleSignal.set(Boolean.valueOf(visible));
    }

    // ── 派生函数（Computed 在追踪上下文中调用，读取 tasksSignal 自动建立依赖） ──────────────

    /** 派生计数文本：「共 N 项 · 已完成 M 项」。 */
    private String deriveCountText() {
        List<Task> tasks = tasksSignal.get();
        int total = tasks.size();
        int done = 0;
        for (Task task : tasks) {
            if (task.isDone()) {
                done++;
            }
        }
        return "共 " + total + " 项 · 已完成 " + done + " 项";
    }

    /** 派生空态条件：列表为空时为真（驱动空态提示 show）。 */
    private Boolean deriveEmptyState() {
        return Boolean.valueOf(tasksSignal.get().isEmpty());
    }

    // ── 控件构建与事件接线 ─────────────────────────────────────────────────────────

    private DocumentButtonControl createButton(String label, int normalColor, int activeColor) {
        return new DocumentButtonControl(document, label)
                .setBackgroundColors(normalColor, activeColor, 0xFF334155)
                .setTextColors(0xFFFFFFFF, 0xFFA0AEC0);
    }

    private void wireHandlers() {
        constructionBindings.add(ReactiveControlBindings.onAction(runtime, addButton, this::addTask));
        constructionBindings.add(ReactiveControlBindings.onAction(runtime, removeButton, this::removeLastTask));
        constructionBindings.add(ReactiveControlBindings.onAction(runtime, shuffleButton, this::shuffleTasks));
        constructionBindings.add(ReactiveControlBindings.onToggle(runtime, detailsToggle, this::setDetailsVisible));
    }

    /**
     * 防御性显式退订构造期建立的控件绑定（宿主关闭时调用）。
     *
     * <p>这些绑定已归属 {@code runtime} 根作用域、会随 {@code runtime.dispose()}（经 widget.close()）自动退订；
     * 本方法逐个 {@code binding.dispose()} 做提前/兜底退订——退订幂等，与根作用域自动退订不冲突（信条一收口）。</p>
     */
    void clearHandlers() {
        for (UiComponentRuntime.Binding binding : constructionBindings) {
            binding.dispose();
        }
    }

    /**
     * 任务行的 keyed 列表 key：任务 id + 完成态。
     *
     * <p>id 唯一标识任务（增删/打乱时身份稳定，行复用不重建）；附带完成态使切换完成时 key 变、
     * forEach <b>只重建该行</b>（完成态属行内展示，id 不变时 forEach 默认复用旧节点不会刷新它）。</p>
     *
     * @param task 任务
     * @return 唯一 key
     */
    static String taskRowKey(Task task) {
        return task.getId() + (task.isDone() ? "#done" : "#todo");
    }

    /** 不可变任务数据：稳定 id + 标签 + 完成态。 */
    static final class Task {
        private final int id;
        private final String label;
        private final boolean done;

        Task(int id, String label, boolean done) {
            this.id = id;
            this.label = label;
            this.done = done;
        }

        int getId() {
            return id;
        }

        String getLabel() {
            return label;
        }

        boolean isDone() {
            return done;
        }
    }
}
