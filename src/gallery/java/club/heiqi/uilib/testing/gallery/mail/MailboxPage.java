package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneCheckBox;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.Collections;

/** Gmail 组织方式的本地工作台，所有操作使用 scene 正式输入及同一消息模型。 */
public final class MailboxPage {
    private MailboxPage() {}
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme) {
        MailboxModel model = new MailboxModel(p.domain(), MailSamples.messages(24));
        return create(p, theme, model).own(model);
    }
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme, ReadableSignal<ThemeMode> mode) {
        MailboxModel model = new MailboxModel(p.domain(), MailSamples.messages(24));
        return create(p, theme, mode, model, true).own(model);
    }
    /** 外部模型由调用方拥有，可用于同一业务的不同视图；页面不关闭它。 */
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> base, MailboxModel model) {
        return create(p,base,model,true);
    }
    /** 行为对照消费者可选择完整结果；普通案例使用显式分页浏览。 */
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> base, MailboxModel model, boolean paged) {
        return create(p, base, p.themeMode(), model, paged);
    }
    /** 局部预览明确递交模式；嵌入消费者默认跟随页面主题来源。 */
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> base,
            ReadableSignal<ThemeMode> mode, MailboxModel model, boolean paged) {
        Owner scope = p.domain().owner();
        Signal<Float> fontScale = p.domain().signal(1f);
        ReadableSignal<SceneTheme> theme = p.domain().computed(scope,
                () -> MailPalette.from(base.get().withTextStyle(base.get().textStyle.withEm(base.get().textStyle.logicalEm * fontScale.get())), mode.get()).theme);
        // 响应式策略消费宿主根空间与当前字号，不读取布局结果回写宽度。
        ReadableSignal<Boolean> wide = p.domain().computed(scope,
                () -> p.viewportWidth().get() >= Math.ceil(theme.get().textStyle.logicalEm * 66));
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(12).gap(12).own(scope);
        root.bind(NodeBinding.value(theme, (n,t) -> n.background(t.panel)));
        SceneNode heading = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(12);
        heading.add(MailStyle.label(p, () -> "收件箱", theme, 1.4f, false));
        heading.add(MailStyle.label(p, () -> "POST / 团队邮件", theme, 0.85f, true));
        heading.add(MailStyle.button(p, () -> fontScale.get() == 1f ? "放大文字" : "标准文字", theme,
                () -> fontScale.set(fontScale.get() == 1f ? 1.15f : 1f)));
        root.add(heading);

        SceneEditState query = new SceneEditState(p.domain(), model.query.get());
        SceneNode search = new SceneNode().size(Size.fill(1), Size.natural()).gap(4);
        search.add(MailStyle.label(p, () -> "搜索邮件", theme, 0.85f, true));
        SceneNode searchRow = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(8);
        searchRow.add(SceneTextField.create(p, query, theme, () -> false, submission -> {
            if (query.accept(submission, submission.text)) model.setQuery(submission.text);
        }, SceneTextField.SubmitPolicy.INPUT));
        searchRow.add(MailStyle.button(p, () -> "清空搜索", theme, () -> { query.reset(""); model.setQuery(""); }));
        search.add(searchRow); root.add(search);

        SceneNode body = new SceneNode().size(Size.fill(1), Size.fill(1)).gap(12);
        body.bind(NodeBinding.value(wide, (n,w) -> n.axis(w ? SceneNode.Axis.ROW : SceneNode.Axis.COLUMN)));
        SceneNode navigation = new SceneNode().gap(4);
        navigation.bind(NodeBinding.value(() -> wide.get() + ":" + theme.get().textStyle.logicalEm, (n,ignored) -> {
            boolean w = wide.get(); n.axis(w ? SceneNode.Axis.COLUMN : SceneNode.Axis.ROW)
                .size(w ? Size.fixed((int)Math.ceil(theme.get().textStyle.logicalEm * 10)) : Size.fill(1), w ? Size.fill(1) : Size.natural());
        }));
        for (MailboxModel.Folder folder : MailboxModel.Folder.values()) {
            String label = folder == MailboxModel.Folder.INBOX ? "收件箱" : folder == MailboxModel.Folder.STARRED ? "星标邮件" : "所有邮件";
            navigation.add(SceneButton.create(p, () -> (model.folder.get() == folder ? "● " : "") + label,
                    () -> MailStyle.buttonTheme(theme.get(), model.folder.get() == folder), () -> model.setFolder(folder))
                    .size(Size.natural(), Size.natural()));
        }
        body.add(navigation);
        SceneNode listPane = new SceneNode().size(Size.fill(2), Size.fill(1)).gap(8);
        listPane.bind(NodeBinding.value(() -> wide.get() || !model.reading.get(), SceneNode::visible));
        SceneNode tools = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(8);
        tools.add(MailStyle.label(p, () -> "邮件 · " + model.results.get().size() + " 封", theme, 1f, false).size(Size.fill(1), Size.natural()));
        tools.add(MailStyle.button(p, () -> "反向排序", theme, model::reverse));
        Signal<Integer> sequence = p.domain().signal(0);
        tools.add(MailStyle.button(p, () -> "接收示例邮件", theme, () -> {
            int next = sequence.get() + 1; sequence.set(next);
            model.receive(new MailboxModel.Message("received-" + next, "协作小组", "新的交接消息 " + next,
                    "新消息已经进入收件箱。", "你好，\n\n这是刚刚收到的交接记录。请在需要时将它加星，或归档到所有邮件。", "刚刚", false, false, true));
        }));
        listPane.add(tools);
        SceneNode bulk = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).gap(8);
        bulk.add(SceneCheckBox.create(p, () -> model.checked.get().isEmpty() ? SceneCheckBox.State.UNCHECKED
                : model.checked.get().size() == model.results.get().size() ? SceneCheckBox.State.CHECKED : SceneCheckBox.State.MIXED,
                () -> "全选全部筛选结果：" + model.results.get().size() + " 封", () -> MailStyle.buttonTheme(theme.get(),false), model::setAllChecked,false)
                .bind(NodeBinding.value(() -> !model.results.get().isEmpty(),SceneNode::enabled)));
        bulk.add(MailStyle.label(p, () -> model.checked.get().isEmpty() ? "选择邮件以批量整理" : "已选择 " + model.checked.get().size() + " 封",theme,.85f,true)
                .size(Size.fill(1),Size.natural()));
        bulk.add(SceneIconButton.create(p,SceneIconButton.Symbol.ARCHIVE,() -> "归档已选邮件",theme,model::archiveChecked)
                .bind(NodeBinding.value(() -> !model.checked.get().isEmpty(),SceneNode::enabled)));
        SceneNode bulkActions = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).gap(8);
        bulkActions.bind(NodeBinding.value(() -> !model.checked.get().isEmpty(),SceneNode::visible));
        bulkActions.add(MailStyle.button(p,() -> "标为已读",theme,() -> model.readChecked(true))
                .bind(NodeBinding.value(() -> !model.checked.get().isEmpty(),SceneNode::enabled)));
        bulkActions.add(MailStyle.button(p,() -> "标为未读",theme,() -> model.readChecked(false))
                .bind(NodeBinding.value(() -> !model.checked.get().isEmpty(),SceneNode::enabled)));
        bulkActions.add(MailStyle.button(p,() -> "取消选择",theme,() -> model.setAllChecked(false))
                .bind(NodeBinding.value(() -> !model.checked.get().isEmpty(),SceneNode::enabled)));
        listPane.add(bulk);
        listPane.add(bulkActions);
        SceneNode paging = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).gap(8);
        paging.add(MailStyle.label(p,() -> {
            int total=model.results.get().size(),from=total==0?0:model.pageIndex.get()*model.pageSize.get()+1;
            int to=Math.min(total,from+model.pageSize.get()-1);return from+"–"+to+" / "+total;
        },theme,.85f,true).size(Size.fill(1),Size.natural()));
        paging.add(MailStyle.button(p,() -> "上一页",theme,() -> model.movePage(-1))
            .bind(NodeBinding.value(() -> model.pageIndex.get()>0,SceneNode::enabled)));
        paging.add(MailStyle.button(p,() -> "下一页",theme,() -> model.movePage(1))
            .bind(NodeBinding.value(() -> (model.pageIndex.get()+1)*model.pageSize.get()<model.results.get().size(),SceneNode::enabled)));
        paging.add(MailStyle.button(p,() -> "每页 "+model.pageSize.get(),theme,() -> model.setPageSize(model.pageSize.get()==25?50:25)));
        listPane.add(paging);
        SceneNode list = new SceneNode().size(Size.fill(1), Size.fill(1));
        list.bind(NodeBinding.repeat(() -> Collections.singletonList(paged?model.pageIndex.get()+":"+model.pageSize.get():"ordinary"),
                key -> key, ignored -> {
                    SceneNode viewport=new SceneNode().size(Size.fill(1),Size.fill(1)).scrollable(true).clip(true).gap(1);
                    viewport.bind(NodeBinding.repeat(paged?model.pageItems:model.results,item->item.id,value->MessageRow.create(p,value,model,theme)));
                    return viewport;
                }));
        listPane.add(list);
        SceneNode empty = MailStyle.label(p, () -> "没有匹配的邮件，试试其他关键词。", theme, 1f, true);
        empty.bind(NodeBinding.value(() -> model.results.get().isEmpty(), SceneNode::visible)); listPane.add(empty);
        body.add(listPane);

        SceneNode reader = new SceneNode().size(Size.fill(3), Size.fill(1)).gap(12).padding(12);
        reader.bind(NodeBinding.value(model.reading, SceneNode::visible));
        reader.add(MailStyle.button(p, () -> "返回列表", theme, model::back));
        reader.add(MailStyle.label(p, () -> model.opened.get() == null ? "邮件已不存在" : model.opened.get().subject, theme, 1.35f, false));
        reader.add(MailStyle.label(p, () -> model.opened.get() == null ? "" : model.opened.get().sender + "  ·  " + model.opened.get().time, theme, 0.9f, true));
        SceneNode readingArea = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true).clip(true);
        readingArea.add(MailStyle.label(p, () -> model.opened.get() == null ? "请选择其他邮件继续阅读。" : model.opened.get().body, theme, 1f, false));
        reader.add(readingArea); body.add(reader); root.add(body);
        root.add(MailStyle.label(p, model.feedback, theme, 0.85f, true)
                .bind(NodeBinding.value(() -> !model.feedback.get().isEmpty(), SceneNode::visible)));
        return root;
    }
}
