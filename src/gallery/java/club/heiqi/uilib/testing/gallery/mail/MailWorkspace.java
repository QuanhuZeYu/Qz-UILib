package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.Collections;

/** 工作区拥有列表/阅读组合规则，业务页面无需同步可见性、滚动、分页挂载与分隔线。 */
final class MailWorkspace {
    private MailWorkspace() {}
    static SceneNode create(ScenePageContext p,MailViewState view,MailboxModel model,boolean paged) {
        ReadableSignal<SceneTheme> theme=view.theme;
        ReadableSignal<MailPalette> palette=view.palette;
        ReadableSignal<Boolean> wide=view.wide,split=view.split,compact=view.compact;
        Runnable receive=model::receiveSample;
        SceneNode workspace=MailStyle.surface(theme,() -> palette.get().surface,1.1f)
                .axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.fill(1)).clip(true);
        SceneNode listPane=new SceneNode().size(Size.fill(2),Size.fill(1));
        listPane.bind(NodeBinding.value(() -> split.get()||!model.reading.get(),SceneNode::visible));
        listPane.add(MailToolbar.create(p,theme,model,wide,receive));
        listPane.add(MailCategories.create(p,theme,palette,model,wide));
        SceneNode list=new SceneNode().size(Size.fill(1),Size.fill(1));
        list.bind(NodeBinding.repeat(() -> Collections.singletonList(paged?model.pageIndex.get()+":"+model.pageSize.get():"ordinary"),key -> key,ignored -> {
            SceneNode viewport=new SceneNode().size(Size.fill(1),Size.fill(1)).scrollable(true).clip(true);
            MessageRow.TimeWidths timeWidths=new MessageRow.TimeWidths(paged?model.pageItems:model.results);
            viewport.bind(NodeBinding.repeat(paged?model.pageItems:model.results,item -> item.id,value -> MessageRow.create(p,value,model,theme,palette,compact,timeWidths)));
            return viewport;
        }));listPane.add(list);
        listPane.add(MailStyle.label(p,() -> "没有匹配的邮件，试试其他关键词。",theme,1f,true)
                .bind(NodeBinding.value(() -> model.results.get().isEmpty(),SceneNode::visible)));
        workspace.add(listPane);
        SceneNode divider=new SceneNode().size(Size.fixed(1),Size.fill(1));
        divider.bind(NodeBinding.value(() -> split.get()&&model.reading.get(),SceneNode::visible));
        divider.bind(NodeBinding.value(palette,(n,v) -> n.background(v.outline)));
        workspace.add(divider).add(MailReader.create(p,theme,palette,model));
        return workspace;
    }
}
