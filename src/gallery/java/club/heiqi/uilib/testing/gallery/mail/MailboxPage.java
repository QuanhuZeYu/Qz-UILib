package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneCheckBox;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.Collections;

/** Gmail 参照的本地工作台；完整和嵌入入口共享同一模型、页面声明与输入路由。 */
public final class MailboxPage {
    private MailboxPage() {}
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme) {
        return create(p,theme,p.themeMode());
    }
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme, ReadableSignal<ThemeMode> mode) {
        MailboxModel model=new MailboxModel(p.domain(),MailSamples.messages(24));
        return create(p,theme,mode,model,true).own(model);
    }
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme,
            ReadableSignal<ThemeMode> mode, Runnable onBack, Runnable onThemePreview) {
        MailboxModel model=new MailboxModel(p.domain(),MailSamples.messages(24));
        return create(p,theme,mode,model,true,onBack,onThemePreview).own(model);
    }
    /** 外部模型由调用方拥有，页面不关闭共享业务状态。 */
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> base, MailboxModel model) {
        return create(p,base,model,true);
    }
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> base, MailboxModel model, boolean paged) {
        return create(p,base,p.themeMode(),model,paged);
    }
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> base,
            ReadableSignal<ThemeMode> mode, MailboxModel model, boolean paged) {
        return create(p,base,mode,model,paged,null,null);
    }
    private static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> base,
            ReadableSignal<ThemeMode> mode, MailboxModel model, boolean paged, Runnable onBack, Runnable onThemePreview) {
        Owner scope=p.domain().owner();Signal<Float> fontScale=p.domain().signal(1f);
        ReadableSignal<MailPalette> palette=p.domain().computed(scope, () -> MailPalette.from(base.get().withTextStyle(
                base.get().textStyle.withEm(base.get().textStyle.logicalEm*fontScale.get())),mode.get()));
        ReadableSignal<SceneTheme> theme=p.domain().computed(scope, () -> palette.get().theme);
        ReadableSignal<Boolean> wide=p.domain().computed(scope, () -> p.viewportWidth().get()>=Math.ceil(theme.get().textStyle.logicalEm*66));
        SceneNode root=new SceneNode().size(Size.fill(1),Size.fill(1)).own(scope);
        root.bind(NodeBinding.value(palette,(n,v) -> n.background(v.canvas)));
        SceneEditState query=new SceneEditState(p.domain(),model.query.get());
        SceneNode search=MailStyle.surface(theme,() -> palette.get().read,1.5f).axis(SceneNode.Axis.ROW)
                .size(Size.fill(3).bounds(0,720),Size.natural()).padding(4).gap(8);
        search.add(MailStyle.label(p,() -> "搜索邮件",theme,.9f,true).size(Size.natural(),Size.natural()));
        search.add(SceneTextField.create(p,query,() -> theme.get().withTextStyle(theme.get().textStyle),() -> false,submission -> {
            if(query.accept(submission,submission.text))model.setQuery(submission.text);
        },SceneTextField.SubmitPolicy.INPUT));
        search.add(MailStyle.button(p,() -> "清空搜索",theme,() -> {query.reset("");model.setQuery("");}));
        root.add(MailChrome.header(p,theme,mode,search,onBack,onThemePreview,
                () -> fontScale.get()==1f?"放大文字":"标准文字",() -> fontScale.set(fontScale.get()==1f?1.25f:1f)));
        Signal<Integer> sequence=p.domain().signal(0);
        Runnable receive=() -> {int next=sequence.get()+1;sequence.set(next);
            model.receive(new MailboxModel.Message("received-"+next,"协作小组","新的交接消息 "+next,
                    "新消息已经进入收件箱。","你好，\n\n这是刚刚收到的交接记录。请在需要时将它加星，或归档到所有邮件。","刚刚",false,false,true));};
        SceneNode body=new SceneNode().size(Size.fill(1),Size.fill(1));
        body.bind(NodeBinding.value(wide,(n,v) -> n.axis(v?SceneNode.Axis.ROW:SceneNode.Axis.COLUMN)));
        body.add(MailChrome.navigation(p,theme,palette,model,wide,receive));
        SceneNode workspace=MailStyle.surface(theme,() -> palette.get().surface,1.1f)
                .axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.fill(1)).clip(true);
        SceneNode listPane=new SceneNode().size(Size.fill(2),Size.fill(1));
        listPane.bind(NodeBinding.value(() -> wide.get()||!model.reading.get(),SceneNode::visible));
        SceneNode tools=MailStyle.surface(theme,() -> 0x00000000,0).axis(SceneNode.Axis.ROW)
                .size(Size.fill(1),Size.natural()).gap(8).padding(4);
        tools.bind(NodeBinding.value(theme,(n,t) -> n.size(Size.fill(1),Size.natural().bounds(MailStyle.px(t,48),Integer.MAX_VALUE))));
        tools.add(SceneCheckBox.create(p,() -> model.checked.get().isEmpty()?SceneCheckBox.State.UNCHECKED
                :model.checked.get().size()==model.results.get().size()?SceneCheckBox.State.CHECKED:SceneCheckBox.State.MIXED,
                () -> "全选全部筛选结果："+model.results.get().size()+" 封",theme,model::setAllChecked,false)
                .bind(NodeBinding.value(() -> !model.results.get().isEmpty(),SceneNode::enabled)));
        tools.add(MailStyle.label(p,() -> model.checked.get().isEmpty()?"邮件 · "+model.results.get().size()+" 封"
                :"已选择 "+model.checked.get().size()+" 封",theme,.85f,true).size(Size.fill(1),Size.natural()));
        SceneNode normal=MailStyle.surface(theme,() -> 0x00000000,0).axis(SceneNode.Axis.ROW).gap(4).size(Size.natural(),Size.natural());
        normal.bind(NodeBinding.value(() -> model.checked.get().isEmpty(),SceneNode::visible));
        normal.add(MailStyle.button(p,() -> "反向排序",theme,model::reverse));
        // 紧凑导航仍提供样本事件，完整侧栏时入口归主按钮。
        normal.add(MailStyle.button(p,() -> "接收示例邮件",theme,receive).bind(NodeBinding.value(() -> !wide.get(),SceneNode::visible)));
        tools.add(normal);
        SceneNode bulk=MailStyle.surface(theme,() -> 0x00000000,0).axis(SceneNode.Axis.ROW).gap(4).size(Size.natural(),Size.natural());
        bulk.bind(NodeBinding.value(() -> !model.checked.get().isEmpty(),SceneNode::visible));
        bulk.add(SceneIconButton.create(p,SceneIconButton.Symbol.ARCHIVE,() -> "归档已选邮件",theme,model::archiveChecked));
        bulk.add(MailStyle.button(p,() -> "标为已读",theme,() -> model.readChecked(true)));
        bulk.add(MailStyle.button(p,() -> "标为未读",theme,() -> model.readChecked(false)));
        bulk.add(MailStyle.button(p,() -> "取消选择",theme,() -> model.setAllChecked(false)));tools.add(bulk);
        tools.add(MailStyle.label(p,() -> range(model),theme,.8f,true).size(Size.natural(),Size.natural()));
        tools.add(MailStyle.button(p,() -> "上一页",theme,() -> model.movePage(-1))
                .bind(NodeBinding.value(() -> model.pageIndex.get()>0,SceneNode::enabled)));
        tools.add(MailStyle.button(p,() -> "下一页",theme,() -> model.movePage(1))
                .bind(NodeBinding.value(() -> (model.pageIndex.get()+1)*model.pageSize.get()<model.results.get().size(),SceneNode::enabled)));
        tools.add(MailStyle.button(p,() -> "每页 "+model.pageSize.get(),theme,() -> model.setPageSize(model.pageSize.get()==25?50:25)));
        listPane.add(tools);
        SceneNode list=new SceneNode().size(Size.fill(1),Size.fill(1));
        list.bind(NodeBinding.repeat(() -> Collections.singletonList(paged?model.pageIndex.get()+":"+model.pageSize.get():"ordinary"),key -> key,ignored -> {
            SceneNode viewport=new SceneNode().size(Size.fill(1),Size.fill(1)).scrollable(true).clip(true);
            viewport.bind(NodeBinding.repeat(paged?model.pageItems:model.results,item -> item.id,value -> MessageRow.create(p,value,model,theme,palette)));
            return viewport;
        }));listPane.add(list);
        listPane.add(MailStyle.label(p,() -> "没有匹配的邮件，试试其他关键词。",theme,1f,true)
                .bind(NodeBinding.value(() -> model.results.get().isEmpty(),SceneNode::visible)));
        workspace.add(listPane);
        SceneNode reader=new SceneNode().size(Size.fill(3),Size.fill(1)).gap(12).padding(20);
        reader.bind(NodeBinding.value(model.reading,SceneNode::visible));
        reader.add(MailStyle.button(p,() -> "返回列表",theme,model::back));
        reader.add(MailStyle.label(p,() -> model.opened.get()==null?"邮件已不存在":model.opened.get().subject,theme,1.35f,false));
        reader.add(MailStyle.label(p,() -> model.opened.get()==null?"":model.opened.get().sender+"  ·  "+model.opened.get().time,theme,.9f,true));
        SceneNode readingArea=new SceneNode().size(Size.fill(1),Size.fill(1)).scrollable(true).clip(true);
        readingArea.add(MailStyle.label(p,() -> model.opened.get()==null?"请选择其他邮件继续阅读。":model.opened.get().body,theme,1f,false));
        reader.add(readingArea);workspace.add(reader);body.add(workspace);
        body.add(new SceneNode().size(Size.fixed(16),Size.fill(1)).bind(NodeBinding.value(wide,SceneNode::visible)));
        root.add(body);
        root.add(MailStyle.label(p,model.feedback,theme,.85f,true)
                .bind(NodeBinding.value(() -> !model.feedback.get().isEmpty(),SceneNode::visible)));
        root.add(new SceneNode().size(Size.natural(),Size.fixed(16)));
        return root;
    }
    private static String range(MailboxModel model) {
        int total=model.results.get().size(),from=total==0?0:model.pageIndex.get()*model.pageSize.get()+1;
        return from+"–"+Math.min(total,from+model.pageSize.get()-1)+" / "+total;
    }
}
