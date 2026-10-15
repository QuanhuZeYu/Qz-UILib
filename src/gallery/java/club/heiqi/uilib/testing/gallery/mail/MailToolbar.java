package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneCheckBox;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 操作与分页保持同一工具区域；选择状态只替换可用动作，不额外推下一整条列表。 */
final class MailToolbar {
    private MailToolbar() {}
    static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> base, MailboxModel model,
            ReadableSignal<Boolean> wide, Runnable receive) {
        ReadableSignal<SceneTheme> theme=() -> base.get().withTextStyle(base.get().textStyle.withColor(base.get().disabledText));
        ReadableSignal<Boolean> roomy=() -> wide.get()&&!model.reading.get();
        SceneNode tools=MailStyle.surface(theme,() -> 0x00000000,0).size(Size.fill(1),Size.natural());
        tools.bind(NodeBinding.value(() -> roomy.get()+":"+theme.get().textStyle.logicalEm,(n,value) -> {
            SceneTheme t=theme.get();n.axis(roomy.get()?SceneNode.Axis.ROW:SceneNode.Axis.COLUMN)
                    .padding(MailStyle.px(t,4)).gap(0)
                    .size(Size.fill(1),Size.natural().bounds(MailStyle.px(t,48),Integer.MAX_VALUE));
        }));
        SceneNode actions=MailStyle.surface(theme,() -> 0x00000000,0).axis(SceneNode.Axis.ROW).gap(4).size(Size.fill(1),Size.natural());
        SceneNode check=SceneCheckBox.create(p,() -> model.checked.get().isEmpty()?SceneCheckBox.State.UNCHECKED
                :model.checked.get().size()==model.results.get().size()?SceneCheckBox.State.CHECKED:SceneCheckBox.State.MIXED,
                () -> "全选全部筛选结果："+model.results.get().size()+" 封",theme,model::setAllChecked,false);
        actions.add(cell(check,theme).bind(NodeBinding.value(() -> !model.results.get().isEmpty(),SceneNode::enabled)));
        SceneNode normal=MailStyle.surface(theme,() -> 0x00000000,0).axis(SceneNode.Axis.ROW).gap(4).size(Size.natural(),Size.natural());
        normal.bind(NodeBinding.value(() -> model.checked.get().isEmpty(),SceneNode::visible));
        normal.add(icon(p,SceneIconButton.Symbol.SORT,() -> "反向排序",theme,model::reverse));
        normal.add(icon(p,SceneIconButton.Symbol.MAIL_OPEN,() -> "接收示例邮件",theme,receive)
                .bind(NodeBinding.value(() -> !wide.get(),SceneNode::visible)));actions.add(normal);
        SceneNode bulk=MailStyle.surface(theme,() -> 0x00000000,0).axis(SceneNode.Axis.ROW).gap(4).size(Size.natural(),Size.natural());
        bulk.bind(NodeBinding.value(() -> !model.checked.get().isEmpty(),SceneNode::visible));
        bulk.add(icon(p,SceneIconButton.Symbol.ARCHIVE,() -> "归档已选邮件",theme,model::archiveChecked));
        bulk.add(icon(p,SceneIconButton.Symbol.MAIL_OPEN,() -> "标为已读",theme,() -> model.readChecked(true)));
        bulk.add(icon(p,SceneIconButton.Symbol.MAIL,() -> "标为未读",theme,() -> model.readChecked(false)));
        bulk.add(icon(p,SceneIconButton.Symbol.CLOSE,() -> "取消选择",theme,() -> model.setAllChecked(false)));actions.add(bulk);
        actions.add(MailStyle.label(p,() -> model.checked.get().isEmpty()?"邮件 · "+model.results.get().size()+" 封"
                :"已选择 "+model.checked.get().size()+" 封",theme,.85f,true).size(Size.fill(1),Size.natural()));
        SceneNode pages=MailStyle.surface(theme,() -> 0x00000000,0).axis(SceneNode.Axis.ROW).gap(4).size(Size.natural(),Size.natural());
        pages.add(MailStyle.label(p,() -> range(model),theme,.8f,true).size(Size.natural(),Size.natural()));
        pages.add(icon(p,SceneIconButton.Symbol.CHEVRON_LEFT,() -> "上一页",theme,() -> model.movePage(-1))
                .bind(NodeBinding.value(() -> model.pageIndex.get()>0,SceneNode::enabled)));
        pages.add(icon(p,SceneIconButton.Symbol.CHEVRON_RIGHT,() -> "下一页",theme,() -> model.movePage(1))
                .bind(NodeBinding.value(() -> (model.pageIndex.get()+1)*model.pageSize.get()<model.results.get().size(),SceneNode::enabled)));
        pages.add(MailStyle.button(p,() -> "每页 "+model.pageSize.get(),theme,() -> model.setPageSize(model.pageSize.get()==25?50:25)));
        tools.add(actions).add(pages);return tools;
    }
    private static SceneNode cell(SceneNode node,ReadableSignal<SceneTheme> theme) {
        return node.bind(NodeBinding.value(theme,(n,t) -> n.size(Size.fixed(MailStyle.px(t,32)),Size.fixed(MailStyle.px(t,32)))));
    }
    private static SceneNode icon(ScenePageContext p,SceneIconButton.Symbol shape,ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme,Runnable action) {return cell(SceneIconButton.create(p,shape,name,theme,action),theme);}
    private static String range(MailboxModel model) {
        int total=model.results.get().size(),from=total==0?0:model.pageIndex.get()*model.pageSize.get()+1;
        return from+"–"+Math.min(total,from+model.pageSize.get()-1)+" / "+total;
    }
}
