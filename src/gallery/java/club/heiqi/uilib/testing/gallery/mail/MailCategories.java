package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 分类内容与计数均来自模型，装饰沿同一正式几何，不把标签带变成第二份邮箱。 */
final class MailCategories {
    private MailCategories() {}
    static SceneNode create(ScenePageContext p,ReadableSignal<SceneTheme> theme,
            ReadableSignal<MailPalette> palette,MailboxModel model,ReadableSignal<Boolean> wide) {
        ReadableSignal<Boolean> roomy=() -> wide.get()&&!model.reading.get();
        SceneNode tabs=MailStyle.surface(theme,() -> 0x00000000,0).axis(SceneNode.Axis.ROW)
                .size(Size.fill(1),Size.natural());
        tabs.bind(NodeBinding.value(() -> model.folder.get()==MailboxModel.Folder.INBOX&&model.query.get().trim().isEmpty(),SceneNode::visible));
        for(MailboxModel.Category category:MailboxModel.Category.values()) {
            String label=category==MailboxModel.Category.PRIMARY?"主要":category==MailboxModel.Category.PROMOTIONS?"推广":"社交";
            ReadableSignal<Boolean> selected=() -> model.category.get()==category;
            ReadableSignal<SceneTheme> tabTheme=() -> theme.get().withTextStyle(theme.get().textStyle
                    .withColor(selected.get()?palette.get().accent:palette.get().mutedText));
            SceneNode tab=MailStyle.surface(tabTheme,() -> 0x00000000,0).axis(SceneNode.Axis.ROW)
                    .text("分类："+label).focusable(true).clickFocus(true).onClick(() -> model.setCategory(category));
            tab.control(new TabSurface(tabTheme,palette,selected));
            tab.bind(NodeBinding.value(() -> roomy.get()+":"+theme.get().textStyle.logicalEm,(n,value) -> {
                SceneTheme t=theme.get();n.padding(MailStyle.px(t,12)).gap(MailStyle.px(t,16))
                        .size(roomy.get()?Size.fixed(MailStyle.px(t,244)):Size.fill(1),
                                Size.natural().bounds(MailStyle.px(t,56),Integer.MAX_VALUE));
            }));
            tab.add(SceneIconButton.graphic(p,() -> category==MailboxModel.Category.PRIMARY?SceneIconButton.Symbol.INBOX
                    :category==MailboxModel.Category.PROMOTIONS?SceneIconButton.Symbol.TAG:SceneIconButton.Symbol.PEOPLE,tabTheme));
            tab.add(MailStyle.single(p,() -> label,tabTheme));
            SceneNode badge=MailStyle.surface(theme,() -> palette.get().navigation,.5f).padding(3);
            badge.add(MailStyle.label(p,() -> model.unread(category)+" 封未读",theme,.78f,false));
            badge.bind(NodeBinding.value(() -> category!=MailboxModel.Category.PRIMARY&&!model.reading.get()&&model.unread(category)>0,SceneNode::visible));
            tab.add(badge);tabs.add(tab);
        }
        tabs.add(new SceneNode().size(Size.fill(1),Size.natural()).bind(NodeBinding.value(roomy,SceneNode::visible)));
        return tabs;
    }
    private static final class TabSurface extends MailSurface {
        private final ReadableSignal<MailPalette> palette;
        private final ReadableSignal<Boolean> selected;
        TabSurface(ReadableSignal<SceneTheme> theme,ReadableSignal<MailPalette> palette,ReadableSignal<Boolean> selected) {
            super(theme,() -> 0x00000000,0);this.palette=palette;this.selected=selected;
        }
        @Override public java.util.List<ReadableSignal<?>> paintDependencies(SceneNode n) {
            return java.util.Arrays.asList(palette,selected);
        }
        @Override public void paint(SceneNode n,SceneGeometryView geometry,PaintPlanBuilder target) {
            super.paint(n,geometry,target);
            SceneGeometryView.Entry e=geometry.get(n);if(e==null)return;
            target.add(PaintCommand.rect(new LayoutBox(e.box.x,e.box.y+e.box.height-1,e.box.width,1),e.clip,palette.get().outline));
            if(selected.get())target.add(PaintCommand.rect(new LayoutBox(e.box.x,e.box.y+e.box.height-3,e.box.width,3),e.clip,palette.get().accent));
        }
    }
}
