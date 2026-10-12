package club.heiqi.uilib.testing.gallery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTextField.SubmitPolicy;
import club.heiqi.uilib.ui.scene.control.SceneTextPolicy;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Submission;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Status;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/** 编辑与结构任务；只声明业务状态，输入、文字簇、范围和几何均由正式 scene 管线负责。 */
final class InteractionScenarios {
    private static final String UNICODE = "工坊 Café e\u0301 · العربية 123 · עברית · 👩🏽‍💻 👨‍👩‍👧‍👦 🇨🇳";
    private InteractionScenarios() {}

    static SceneNode create(ScenePageContext context, TestGallery.Page page,
            ReadableSignal<SceneTheme> theme) {
        if (page == TestGallery.Page.EDITING) return editing(context, theme);
        if (page == TestGallery.Page.STRUCTURE) return structure(context, theme);
        throw new IllegalArgumentException("非编辑或结构场景：" + page);
    }

    private static SceneNode page() {
        return new SceneNode().size(Size.fill(1), Size.fill(1)).gap(16).scrollable(true).clip(true);
    }

    private static SceneNode stack() {
        return new SceneNode().size(Size.fill(1), Size.natural()).gap(8);
    }

    private static SceneNode action(ScenePageContext context, ReadableSignal<SceneTheme> theme,
            ReadableSignal<String> text, Runnable run) {
        return SceneButton.create(context, text, theme, run);
    }

    /** 工具仅保留自己作用区域内的编辑焦点；从其他按钮点击时仍按标准按钮转焦点。
     * 不能把同区工具也视作保焦目标，否则按钮之间仍会黏住焦点。 */
    private static SceneNode editorAction(ScenePageContext context, ReadableSignal<SceneTheme> theme,
            ReadableSignal<String> text, Runnable run, SceneNode region) {
        return action(context, theme, text, run).bind(NodeBinding.value(() -> {
            SceneNode focused = context.runtime().focusRouter().focused().get();
            if (focused == null || focused.control() == null || !focused.control().capturesPointer()) return true;
            for (SceneNode node = focused; node != null; node = node.parent())
                if (node == region) return false;
            return true;
        }, SceneNode::clickFocus));
    }

    private static void title(SceneNode node, ScenePageContext context,
            ReadableSignal<SceneTheme> theme, String text, int size) {
        node.add(GalleryStyle.label(context, () -> text, theme, size, false));
    }

    private static void guide(SceneNode node, ScenePageContext context,
            ReadableSignal<SceneTheme> theme, String text) {
        node.add(GalleryStyle.label(context, () -> text, theme, 13, true));
    }

    private static SceneNode editing(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        SceneNode page = page();
        title(page, context, theme, TestGallery.Page.EDITING.title, 26);
        guide(page, context, theme, "把交接文字当作真实草稿编辑。单行 Enter 请求交接；多行 Enter 换行，Ctrl+Enter 请求交接。Tab / Shift+Tab 导航，Shift+方向键或拖动扩展选区，Ctrl+A 全选。Ctrl+C / X / V 使用宿主剪贴板，Ctrl+Z 撤销，Ctrl+Y 或 Ctrl+Shift+Z 重做（以宿主映射为准）。");
        guide(page, context, theme, "在 Unicode 样本中点击、选区、粘贴、左右移动并删除组合重音、旗帜和家庭 emoji：普通移动与删除应保持完整用户字符；双向文字的显示和点击由正式排版决定。单行粘贴去掉 CR/LF，多行保留换行和末尾空行。");
        guide(page, context, theme, "真实 IME：切到中文输入法，先组字再按 Enter。第一次 Enter 只确认候选字；组字结束后下一次 Enter 才按字段规则执行。此页不生成模拟组字；没有真实宿主 IME / 剪贴板时，相关操作只能记为未提供能力，不能据模板插入判为通过。");

        Editor single = new Editor(context, UNICODE, SceneEditState.Mode.SINGLE_LINE, true);
        page.add(editorCard(context, theme, "延迟交接与外部修改", single, SubmitPolicy.CONFIRM));
        Editor notes = new Editor(context,
                "交接记录\n" + UNICODE + "\n下一班请核对材料。\n末尾空行保留：\n",
                SceneEditState.Mode.MULTI_LINE, false);
        page.add(editorCard(context, theme, "多行交接记录", notes, SubmitPolicy.CONFIRM));

        SceneNode policies = GalleryStyle.card(theme);
        title(policies, context, theme, "三种交接时机", 20);
        guide(policies, context, theme, "分别输入并比较已接受值。随输入会请求接受且保持编辑会话；确认交接由 Enter 完成；离开交接在正常切焦点时完成。只移动光标、选择或复制不会开启会话，禁用或卸载不是正常失焦交接。业务拒绝空白文字，完成交接时去掉首尾空白；随输入接受保留输入中的空格。");
        policyField(policies, context, theme, "随输入交接", SubmitPolicy.INPUT);
        policyField(policies, context, theme, "确认后交接", SubmitPolicy.CONFIRM);
        policyField(policies, context, theme, "正常离开时交接", SubmitPolicy.BLUR);
        page.add(GalleryStyle.frame(policies, theme));
        return page;
    }

    private static SceneNode editorCard(ScenePageContext context, ReadableSignal<SceneTheme> theme,
            String name, Editor editor, SubmitPolicy policy) {
        SceneNode card = GalleryStyle.card(theme);
        title(card, context, theme, name, 20);
        SceneNode field = editor.field(context, theme, policy);
        card.add(field);
        guide(card, context, theme, editor.deferred
                ? "操作：修改文字后按 Enter 或请求交接，继续输入，再分别接受或拒绝旧交接。预期：旧请求如实记录结果，不改新草稿、选区或会话，也不更新当前已接受值。空白交接会被业务拒绝。"
                : "操作：Enter 换行、上下移动、Home / End 到视觉行首尾，Ctrl+Home / End 到文档首尾；滚轮查看正文。预期：软换行不写入草稿，滚动不修改文字或选区，Ctrl+Enter 接受后完成本次编辑。");
        SceneNode actions = stack();
        actions.add(editorAction(context, theme, () -> "请求交接", editor::submit, field)
                .bind(NodeBinding.value(editor::canModify, SceneNode::enabled)));
        actions.add(editorAction(context, theme, () -> "插入 Unicode 模板（替换当前选区）",
                () -> editor.insert(UNICODE), field)
                .bind(NodeBinding.value(editor::canModify, SceneNode::enabled)));
        actions.add(editorAction(context, theme, () -> editor.readOnly.get() ? "恢复可编辑" : "设为只读",
                () -> editor.readOnly.set(!editor.readOnly.get()), field));
        actions.add(editorAction(context, theme, () -> editor.enabled.get() ? "禁用字段" : "启用字段",
                () -> editor.enabled.set(!editor.enabled.get()), field));
        actions.add(editorAction(context, theme, () -> "取消编辑并回到当前已接受值", editor::cancel, field));
        actions.add(editorAction(context, theme, () -> "重置基线为当前已接受值（清空历史）", editor::reset, field));
        actions.add(editorAction(context, theme, () -> "收到外部更新", editor::external, field));
        card.add(actions);
        guide(card, context, theme, "只读仍可选择、复制、Tab 到达和滚动，禁止修改与交接；禁用释放交互，Tab 跳过，不接受尾部点击，也不提交或丢弃草稿。模板是普通编辑，可撤销为一步；取消和重置基线清空历史，不能撤销回旧对象文字。取消 / 重置显式结束旧组字联系。");
        SceneNode conflict = stack().visible(false)
                .bind(NodeBinding.value(editor.conflict, SceneNode::visible));
        guide(conflict, context, theme, "外部已接受值与活跃草稿冲突。请选择保留草稿继续编辑，或使用外部值重置基线；收到更新本身不会覆盖活跃草稿。");
        conflict.add(editorAction(context, theme, () -> "保留我的草稿", () -> editor.conflict.set(false), field));
        conflict.add(editorAction(context, theme, () -> "使用外部已接受值", editor::reset, field));
        card.add(conflict);
        feedback(card, context, theme, editor);
        if (editor.deferred) {
            guide(card, context, theme, "待交接队列保留每一次原始请求。可以先完成后一次，再完成前一次；也可取消 / 重置后再完成旧交接。接受只更新仍对应当前草稿的业务值，拒绝保留草稿。已完成记录可显式清除。");
            SceneNode queue = stack().bind(NodeBinding.repeat(editor.requests, request -> request,
                    item -> requestCard(context, theme, editor, item.get(), field)));
            card.add(queue);
            card.add(editorAction(context, theme, () -> "清除已完成交接记录", editor::clearResolved, field));
        }
        return GalleryStyle.frame(card, theme);
    }

    private static void policyField(SceneNode node, ScenePageContext context,
            ReadableSignal<SceneTheme> theme, String label, SubmitPolicy policy) {
        Editor editor = new Editor(context, label + "初稿", SceneEditState.Mode.SINGLE_LINE, false);
        title(node, context, theme, label, 15);
        node.add(editor.field(context, theme, policy));
        feedback(node, context, theme, editor);
    }

    private static void feedback(SceneNode node, ScenePageContext context,
            ReadableSignal<SceneTheme> theme, Editor editor) {
        node.add(GalleryStyle.label(context, () -> "当前草稿：" + editor.state.text().get(), theme, 14, false));
        node.add(GalleryStyle.label(context, () -> "已接受值：" + editor.accepted.get(), theme, 14, false));
        node.add(GalleryStyle.label(context, () -> {
            SceneEditState.Snapshot s = editor.state.snapshot().get();
            String phase = s.composing ? "输入法正在组字" : s.active ? "正在编辑" : "未在编辑";
            String result = s.error == null ? "" : "；本次拒绝：" + s.error;
            Submission last = editor.last.get();
            String pending = last == null ? "尚未请求交接" : status(last);
            return phase + "；最近交接：" + pending + result;
        }, theme, 13, true));
        node.add(GalleryStyle.label(context, editor.notice, theme, 13, true));
    }

    private static String status(Submission request) {
        switch (request.status().get().status) {
            case ACCEPTED: return "已接受";
            case REJECTED: return "已拒绝：" + request.status().get().reason;
            default: return "等待处理";
        }
    }

    private static SceneNode requestCard(ScenePageContext context, ReadableSignal<SceneTheme> theme,
            Editor editor, Submission request, SceneNode field) {
        SceneNode row = stack();
        row.add(GalleryStyle.label(context, () -> "交接文字：" + request.text, theme, 14, false));
        row.add(GalleryStyle.label(context, () -> "结果：" + status(request), theme, 13, true));
        row.add(editorAction(context, theme, () -> "接受这次交接", () -> editor.resolve(request, true), field)
                .bind(NodeBinding.value(() -> request.status().get().status == Status.PENDING,
                        SceneNode::enabled)));
        row.add(editorAction(context, theme, () -> "拒绝这次交接", () -> editor.resolve(request, false), field)
                .bind(NodeBinding.value(() -> request.status().get().status == Status.PENDING,
                        SceneNode::enabled)));
        return row;
    }

    /** 人工延迟仅推迟业务完成，不重造请求或冒充宿主组字；保留真正的 Submission。 */
    private static final class Editor {
        final SceneEditState state;
        final Signal<String> accepted, notice;
        final Signal<Boolean> readOnly, enabled, conflict;
        final Signal<Submission> last;
        final Signal<List<Submission>> requests;
        final boolean deferred;
        private boolean alternateExternal;

        Editor(ScenePageContext context, String text, SceneEditState.Mode mode, boolean deferred) {
            state = new SceneEditState(context.domain(), text, mode);
            accepted = context.domain().signal(text);
            notice = context.domain().signal("");
            readOnly = context.domain().signal(false);
            enabled = context.domain().signal(true);
            conflict = context.domain().signal(false);
            last = context.domain().signal(null);
            requests = context.domain().signal(Collections.emptyList());
            this.deferred = deferred;
        }

        SceneNode field(ScenePageContext context, ReadableSignal<SceneTheme> theme, SubmitPolicy policy) {
            SceneNode node = state.mode() == SceneEditState.Mode.MULTI_LINE
                    ? SceneTextArea.create(context, state, theme, readOnly, this::receive, policy)
                    : SceneTextField.create(context, state, theme, readOnly, this::receive, policy);
            node.bind(NodeBinding.value(enabled, SceneNode::enabled));
            if (state.mode() == SceneEditState.Mode.MULTI_LINE)
                node.bind(NodeBinding.value(theme, (target, value) -> target.size(Size.fill(1),
                        Size.fixed(value.textStyle.size * 7 + value.padding * 2))));
            return node;
        }

        boolean canModify() {
            return enabled.get() && !readOnly.get() && !state.snapshot().get().composing;
        }

        void submit() {
            if (canModify()) receive(state.request(true));
            else notice.set("请先完成输入法组字，并确保字段可编辑。");
        }

        void insert(String text) {
            if (canModify()) state.replaceSelection(text, false);
        }

        void receive(Submission request) {
            last.set(request);
            if (conflict.get()) {
                state.reject(request, "请先选择保留草稿或使用外部值");
                notice.set("外部修改等待显式选择，尚未接受这次交接。");
                return;
            }
            if (deferred) {
                List<Submission> next = new ArrayList<>(requests.get());
                next.add(request);
                requests.set(next);
                notice.set("交接已排队；可继续编辑，再处理这次原始交接。");
            } else resolve(request, true);
        }

        void resolve(Submission request, boolean accept) {
            if (request.status().get().status != Status.PENDING) return;
            if (accept && conflict.get()) {
                notice.set("请先选择保留草稿或使用外部值，再接受待交接记录。");
                return;
            }
            String normalized = request.complete ? request.text.trim() : request.text;
            boolean current;
            if (accept && !request.text.trim().isEmpty()) {
                current = state.accept(request, normalized);
                // 本场景的业务权威规则：旧完成只留请求记录，不覆盖当前业务值。
                if (current) {
                    accepted.set(normalized);
                    conflict.set(false);
                }
                notice.set(current ? "已接受交接；撤销仍只修改草稿。" : "旧交接已接受；当前草稿与已接受值保持。");
            } else {
                String reason = accept ? "交接文字不能为空" : "复核未通过，请补充交接内容";
                current = state.reject(request, reason);
                notice.set(current ? "已拒绝：" + reason : "旧交接已拒绝；原因只归这次旧交接，当前草稿保持。");
            }
        }

        void external() {
            alternateExternal = !alternateExternal;
            String text = alternateExternal ? "外部交接：材料已经到齐" : "外部交接：等待下一班复核";
            boolean active = state.snapshot().get().active;
            accepted.set(text);
            state.synchronizeExternal(text);
            conflict.set(active);
            notice.set(active ? "外部值已更新；活跃草稿等待你的选择。" : "无编辑会话，已同步外部值。");
        }

        void cancel() {
            state.cancel();
            state.synchronizeExternal(accepted.get());
            conflict.set(false);
            notice.set("已取消编辑并同步当前已接受值；旧组字和历史不恢复。");
        }

        void reset() {
            state.reset(accepted.get());
            conflict.set(false);
            notice.set("已重置基线；旧交接可记录结果，但不能写回新基线。");
        }

        void clearResolved() {
            List<Submission> pending = new ArrayList<>();
            for (Submission request : requests.get())
                if (request.status().get().status == Status.PENDING) pending.add(request);
            requests.set(pending);
        }
    }

    private static SceneNode structure(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        SceneNode content = page();
        guide(content, context, theme, "操作都沿同一 scene：重排后继续编辑原订单，再用 Tab 按新逻辑顺序导航；隐藏、禁用或切换对象后，旧交互不得落到占据原位置的对象。滚轮和裁剪使用正式视口，小窗口操作按钮纵向排列。");
        Orders orders = new Orders(context);
        SceneNode listCard = GalleryStyle.card(theme);
        title(listCard, context, theme, "订单队列", 20);
        guide(listCard, context, theme, "展开甲订单并编辑备注，再新增、删行、反转顺序或更新首项进度。预期：持续存在的订单保留自己的展开、草稿和合法焦点，操作读取更新后的进度；删除会结束旧行交互。恢复最近删除的订单明确复用业务持有的草稿，但创建新行，不恢复旧焦点或组字。");
        SceneNode rows = stack().scrollable(true).clip(true)
                .bind(NodeBinding.value(theme, (node, value) -> node.size(Size.fill(1),
                        Size.fixed(value.textStyle.size * 24))))
                .bind(NodeBinding.repeat(orders.items, order -> order.key,
                        item -> orderRow(context, theme, orders, item)));
        listCard.add(editorAction(context, theme, () -> "新增订单", orders::add, rows));
        listCard.add(editorAction(context, theme, () -> "反转订单顺序", orders::reverse, rows));
        listCard.add(editorAction(context, theme, () -> "更新首项进度", orders::updateFirst, rows));
        listCard.add(editorAction(context, theme, () -> "恢复最近删除的订单", orders::restore, rows)
                .bind(NodeBinding.value(() -> orders.removed.get() != null, SceneNode::enabled)));
        listCard.add(GalleryStyle.label(context, orders.notice, theme, 13, true));
        listCard.add(rows);
        guide(listCard, context, theme, "滚到队尾再删行：范围缩小后应立即限制偏移，反向滚动立即响应；裁掉的行不命中。按住行按钮再删除或禁用目标时，释放尾部不能激活邻行；下一次完整点击才执行动作。");
        content.add(GalleryStyle.frame(listCard, theme));

        Editor first = new Editor(context, "甲对象独立草稿", SceneEditState.Mode.SINGLE_LINE, false);
        Editor second = new Editor(context, "乙对象独立草稿", SceneEditState.Mode.SINGLE_LINE, false);
        Signal<Editor> selected = context.domain().signal(first);
        Signal<Boolean> shown = context.domain().signal(true);
        Signal<Boolean> enabled = context.domain().signal(true);
        Signal<Boolean> readOnly = context.domain().signal(false);
        Signal<Boolean> focusable = context.domain().signal(true);
        SceneNode qualifications = GalleryStyle.card(theme);
        title(qualifications, context, theme, "对象切换与交互资格", 20);
        guide(qualifications, context, theme, "先聚焦对象并输入，再切换下方状态。隐藏 / 禁用 / 不可聚焦释放旧交互；恢复资格不夺回焦点。只读允许选择与复制，终止修改性组字；切换对象明确替换字段，保留两份独立草稿，不迁移焦点或旧输入联系。");
        SceneNode holder = stack().bind(NodeBinding.value(shown, SceneNode::visible))
                .bind(NodeBinding.value(enabled, SceneNode::enabled))
                .bind(NodeBinding.repeat(() -> Collections.singletonList(selected.get()), editor -> editor,
                        item -> SceneTextField.create(context, item.get().state, theme, readOnly,
                                item.get()::receive).bind(NodeBinding.value(focusable, SceneNode::focusable))));
        qualifications.add(holder);
        qualifications.add(editorAction(context, theme, () -> shown.get() ? "隐藏当前字段" : "显示当前字段",
                () -> shown.set(!shown.get()), holder));
        qualifications.add(editorAction(context, theme, () -> enabled.get() ? "禁用当前字段" : "启用当前字段",
                () -> enabled.set(!enabled.get()), holder));
        qualifications.add(editorAction(context, theme, () -> readOnly.get() ? "恢复当前字段编辑" : "当前字段只读",
                () -> readOnly.set(!readOnly.get()), holder));
        qualifications.add(editorAction(context, theme, () -> focusable.get() ? "取消当前字段聚焦资格" : "恢复聚焦资格",
                () -> focusable.set(!focusable.get()), holder));
        qualifications.add(editorAction(context, theme, () -> selected.get() == first ? "切换到乙对象" : "切换到甲对象",
                () -> selected.set(selected.get() == first ? second : first), holder));
        qualifications.add(GalleryStyle.label(context, () -> "甲草稿：" + first.state.text().get()
                + "；乙草稿：" + second.state.text().get(), theme, 14, false));
        content.add(GalleryStyle.frame(qualifications, theme));
        content.add(movement(context, theme));
        SceneNode help = GalleryStyle.card(theme);
        title(help, context, theme, "完整说明提示", 20);
        guide(help, context, theme, "悬停或 Tab 到下面的省略按钮：正式被动提示应显示完整文字，不抢焦点、不改 Tab 范围、不拦指针。缩小窗口、切换主题和滚动后，提示与裁剪应使用当前几何。");
        Signal<String> tipFeedback = context.domain().signal("");
        help.add(SceneButton.create(context, () -> "完整说明：订单重排保持原订单草稿；真正删除结束原行交互；只读可选择复制；禁用不提交；提示只是阅读辅助，不建立额外输入范围。",
                theme, () -> tipFeedback.set("已阅读完整说明"), SceneTextPolicy.ELLIPSIS_SINGLE_LINE)
                .size(Size.fill(1).bounds(0, 300), Size.natural()));
        help.add(GalleryStyle.label(context, tipFeedback, theme, 13, true));
        content.add(GalleryStyle.frame(help, theme));
        return scopes(context, theme, content, enabled);
    }

    private static SceneNode orderRow(ScenePageContext context, ReadableSignal<SceneTheme> theme,
            Orders orders, ReadableSignal<Order> item) {
        // 同一稳定业务键持续绑定同一独立状态；展示与动作始终读取 repeat 提供的当前条目。
        Editor editor = item.get().editor;
        SceneNode row = GalleryStyle.card(theme);
        row.add(GalleryStyle.label(context, () -> item.get().name + " · " + item.get().stage, theme, 16, false));
        row.add(editorAction(context, theme, () -> (item.get().expanded.get() ? "收起" : "展开") + item.get().name,
                () -> item.get().expanded.set(!item.get().expanded.get()), row));
        SceneNode detail = stack().bind(NodeBinding.value(() -> item.get().expanded.get(), SceneNode::visible));
        detail.add(editor.field(context, theme, SubmitPolicy.CONFIRM));
        feedback(detail, context, theme, editor);
        row.add(detail);
        row.add(editorAction(context, theme, () -> "记录" + item.get().name + "当前进度",
                () -> orders.notice.set("已记录：" + item.get().name + " · " + item.get().stage), row));
        row.add(editorAction(context, theme, () -> "把" + item.get().name + "排到队首",
                () -> orders.toFront(item.get()), row));
        row.add(editorAction(context, theme, () -> "删除" + item.get().name, () -> orders.remove(item.get()), row));
        return GalleryStyle.frame(row, theme);
    }

    private static final class Order {
        final int key;
        final String name, stage;
        final Editor editor;
        final Signal<Boolean> expanded;
        Order(int key, String name, String stage, Editor editor, Signal<Boolean> expanded) {
            this.key = key;
            this.name = name;
            this.stage = stage;
            this.editor = editor;
            this.expanded = expanded;
        }
        Order stage(String value) { return new Order(key, name, value, editor, expanded); }
    }

    private static final class Orders {
        final ScenePageContext context;
        final Signal<List<Order>> items;
        final Signal<Order> removed;
        final Signal<String> notice;
        private int nextKey = 1;
        Orders(ScenePageContext context) {
            this.context = context;
            items = context.domain().signal(Arrays.asList(order("甲订单"), order("乙订单"), order("丙订单")));
            removed = context.domain().signal(null);
            notice = context.domain().signal("请先展开一行并编辑备注，再改变队列。");
        }
        private Order order(String name) {
            return new Order(nextKey++, name, "等待复核",
                    new Editor(context, name + "备注：" + UNICODE, SceneEditState.Mode.SINGLE_LINE, false),
                    context.domain().signal(false));
        }
        void add() {
            List<Order> next = new ArrayList<>(items.get());
            next.add(order("新增订单 " + nextKey));
            items.set(next);
        }
        void reverse() {
            List<Order> next = new ArrayList<>(items.get());
            Collections.reverse(next);
            items.set(next);
        }
        void updateFirst() {
            List<Order> next = new ArrayList<>(items.get());
            if (next.isEmpty()) return;
            Order first = next.get(0);
            next.set(0, first.stage(first.stage.equals("等待复核") ? "复核完成" : "等待复核"));
            items.set(next);
        }
        void toFront(Order order) {
            List<Order> next = new ArrayList<>(items.get());
            next.removeIf(value -> value.key == order.key);
            next.add(0, order);
            items.set(next);
        }
        void remove(Order order) {
            List<Order> next = new ArrayList<>(items.get());
            next.removeIf(value -> value.key == order.key);
            removed.set(order);
            items.set(next);
            notice.set("已删除" + order.name + "；独立草稿由业务保留，旧行交互结束。");
        }
        void restore() {
            Order order = removed.get();
            if (order == null) return;
            List<Order> next = new ArrayList<>(items.get());
            next.add(order);
            items.set(next);
            removed.set(null);
            notice.set("已重新创建" + order.name + "的行并显式绑定原草稿；旧焦点和组字不恢复。");
        }
    }

    private static SceneNode movement(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        SceneNode card = GalleryStyle.card(theme);
        title(card, context, theme, "跨列移动同一交接卡", 20);
        guide(card, context, theme, "先在交接卡输入并选择文字，再移动到另一列。预期：合法范围内保持同一节点、草稿、选区和焦点；移入禁用列则释放交互，不隐含交接，重新启用不抢焦点。两列在窄窗口按正式网格纵向排列。");
        SceneNode left = GalleryStyle.card(theme), right = GalleryStyle.card(theme);
        title(left, context, theme, "待办列", 16);
        title(right, context, theme, "完成列", 16);
        Signal<SceneNode> destination = context.domain().signal(left);
        Signal<Boolean> rightEnabled = context.domain().signal(true);
        right.bind(NodeBinding.value(rightEnabled, SceneNode::enabled));
        Editor editor = new Editor(context, "随卡移动的独立交接草稿", SceneEditState.Mode.SINGLE_LINE, false);
        SceneNode moving = stack().bind(NodeBinding.move(destination));
        moving.add(editor.field(context, theme, SubmitPolicy.CONFIRM));
        feedback(moving, context, theme, editor);
        left.add(moving);
        card.add(editorAction(context, theme, () -> "移到待办列", () -> destination.set(left), moving));
        card.add(editorAction(context, theme, () -> "移到完成列", () -> destination.set(right), moving));
        card.add(editorAction(context, theme, () -> rightEnabled.get() ? "禁用完成列" : "启用完成列",
                () -> rightEnabled.set(!rightEnabled.get()), moving));
        card.add(GalleryStyle.label(context, () -> "交接卡位于：" + (destination.get() == left ? "待办列" : "完成列"), theme, 13, true));
        card.add(GalleryStyle.grid().add(GalleryStyle.frame(left, theme)).add(GalleryStyle.frame(right, theme)));
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode scopes(ScenePageContext context, ReadableSignal<SceneTheme> theme,
            SceneNode content, Signal<Boolean> backgroundEnabled) {
        Signal<Boolean> outerOpen = context.domain().signal(false), innerOpen = context.domain().signal(false);
        Signal<Boolean> outerEnabled = context.domain().signal(true), explicitReturn = context.domain().signal(false);
        Editor outerEditor = new Editor(context, "外层复核备注", SceneEditState.Mode.SINGLE_LINE, false);
        SceneNode outerField = outerEditor.field(context, theme, SubmitPolicy.CONFIRM)
                .bind(NodeBinding.value(outerEnabled, SceneNode::enabled));
        SceneNode outer = page().padding(20).visible(false)
                .bind(NodeBinding.value(outerOpen, SceneNode::visible))
                .bind(NodeBinding.value(theme, (node, value) -> node.background(value.panel)));
        SceneNode inner = page().padding(20).modal(true).visible(false)
                .bind(NodeBinding.value(innerOpen, SceneNode::visible))
                .bind(NodeBinding.value(theme, (node, value) -> node.background(value.panel)));
        Runnable closeOuter = () -> { innerOpen.set(false); outerOpen.set(false); };
        Runnable closeInner = () -> innerOpen.set(false);
        inner.onEscape(() -> { closeInner.run(); return true; });
        title(outer, context, theme, "外层交接复核", 24);
        guide(outer, context, theme, "当前只有外层可交互，后台订单、导航和文字输入被隔离。Tab 在有效外层控件中循环；打开内层后外层也被隔离。ESC 先交组字，再关闭当前范围；已消费事件不继续关闭外层。");
        outer.add(outerField);
        feedback(outer, context, theme, outerEditor);
        SceneNode openInner = editorAction(context, theme, () -> "打开内层确认", () -> innerOpen.set(true), outerField);
        outer.add(openInner);
        outer.add(action(context, theme, () -> explicitReturn.get() ? "返回内层前的焦点" : "声明返回到打开内层按钮",
                () -> explicitReturn.set(!explicitReturn.get())));
        outer.add(editorAction(context, theme, () -> outerEnabled.get() ? "禁用外层备注" : "启用外层备注",
                () -> outerEnabled.set(!outerEnabled.get()), outerField));
        outer.add(action(context, theme, () -> backgroundEnabled.get() ? "禁用后台对象字段" : "启用后台对象字段",
                () -> backgroundEnabled.set(!backgroundEnabled.get())));
        outer.add(action(context, theme, () -> "关闭外层复核", closeOuter));
        title(inner, context, theme, "内层确认", 24);
        guide(inner, context, theme, "关闭内层只返回外层。返回依次核验显式恢复目标、原焦点、初始目标、当前有效首项；原目标失效再恢复资格不复活旧恢复记录。禁用外层备注后返回，可观察实际回退按钮。");
        SceneNode innerClose = action(context, theme, () -> "关闭内层，返回外层", closeInner);
        inner.initialFocus(innerClose).add(innerClose);
        inner.add(action(context, theme, () -> outerEnabled.get() ? "禁用外层备注再观察回退" : "恢复外层备注资格",
                () -> outerEnabled.set(!outerEnabled.get())));
        inner.add(action(context, theme, () -> "结束整个复核范围", closeOuter));
        SceneNode scopeCard = GalleryStyle.card(theme);
        title(scopeCard, context, theme, "范围隔离与返回", 20);
        guide(scopeCard, context, theme, "先聚焦后台字段，再点击打开复核：进入外层初始备注，内层关闭回外层，外层关闭尝试恢复后台原焦点。若后台目标失效，按工作台范围的当前有效首项回退，可能是导航按钮；不自动复活旧焦点。Tab / Shift+Tab 跳过隐藏和禁用目标、保留只读字段，并显露视口外目标。");
        scopeCard.add(editorAction(context, theme, () -> "打开交接复核", () -> outerOpen.set(true), content));
        scopeCard.add(GalleryStyle.label(context, () -> {
            SceneNode focused = context.runtime().focusRouter().focused().get();
            return focused == null ? "当前没有有效焦点" : focused == outerField ? "焦点在外层备注"
                    : focused == innerClose ? "焦点在内层返回按钮" : "焦点在工作台当前有效控件";
        }, theme, 13, true));
        // 范围入口放在正文首段，背景不隐藏，便于观察正式返回资格。
        content.size(Size.fill(1), Size.natural()).scrollable(false).clip(false);
        SceneNode body = page();
        title(body, context, theme, TestGallery.Page.STRUCTURE.title, 26);
        body.add(GalleryStyle.frame(scopeCard, theme)).add(content);
        // 所有覆盖层都是同一逻辑树、同一几何和唯一焦点权威；仅通过正式控件扩展声明叠放。
        SceneNode outerLayer = new SceneNode().size(Size.fill(1), Size.fill(1)).control(new Layers())
                .modal(true).visible(false).initialFocus(outerField)
                .bind(NodeBinding.value(outerOpen, SceneNode::visible))
                .bind(NodeBinding.value(explicitReturn,
                        (node, value) -> node.restoreFocus(value ? openInner : null)))
                .onEscape(() -> { closeOuter.run(); return true; });
        outerLayer.add(outer).add(inner);
        return new SceneNode().size(Size.fill(1), Size.fill(1)).control(new Layers()).add(body).add(outerLayer);
    }

    /** 正式扩展的叠放排列：子节点测量仍调用框架，不建立独立布局或输入运行时。 */
    private static final class Layers implements SceneControl {
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text,
                SceneChildMeasurer children) {
            int width = 0, height = 0;
            for (SceneNode child : node.children()) if (child.visible()) {
                MeasuredSize size = children.measureChild(child, limits);
                width = Math.max(width, size.width);
                height = Math.max(height, size.height);
            }
            return new MeasuredSize(width, height);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated) {
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            for (SceneNode child : node.children()) if (child.visible())
                placed.add(new ChildPlacements.Entry(child, new LayoutBox(0, 0, allocated.width, allocated.height)));
            return new ChildPlacements(placed);
        }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
    }
}
