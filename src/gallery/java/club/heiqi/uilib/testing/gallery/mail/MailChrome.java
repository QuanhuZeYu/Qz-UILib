package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 完整邮箱外壳；返回及局部预览是调用方提供的真实意图，嵌入页不伪造退出动作。 */
final class MailChrome {
    private MailChrome() {}
    static SceneNode header(ScenePageContext p, ReadableSignal<SceneTheme> theme,
            ReadableSignal<ThemeMode> mode, SceneNode search, Runnable onBack,
            Runnable onTheme, ReadableSignal<Boolean> collapsed,Runnable onToggle, ReadableSignal<String> fontName, Runnable onFont) {
        SceneNode header = MailStyle.surface(theme, () -> 0x00000000, 0)
                .axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural());
        header.bind(NodeBinding.value(theme,(n,t) -> n.gap(MailStyle.px(t,12))
                .size(Size.fill(1),Size.natural().bounds(MailStyle.px(t,64),Integer.MAX_VALUE))));
        SceneNode brand = MailStyle.surface(theme, () -> 0x00000000,0).axis(SceneNode.Axis.ROW);
        brand.bind(NodeBinding.value(() -> p.viewportWidth().get()+":"+theme.get().textStyle.logicalEm,(n,value) -> {
            SceneTheme t=theme.get();boolean expanded=p.viewportWidth().get()>=Math.ceil(t.textStyle.logicalEm*70);
            n.padding(MailStyle.px(t,8)).gap(MailStyle.px(t,4))
                    .size(Size.fixed(MailStyle.px(t,expanded?244:156)),Size.natural());
        }));
        brand.add(SceneIconButton.create(p,SceneIconButton.Symbol.MENU,() -> collapsed.get()?"展开导航":"折叠导航",theme,onToggle)
                .bind(NodeBinding.value(theme,(n,t) -> n.size(Size.fixed(MailStyle.px(t,48)),Size.fixed(MailStyle.px(t,48))))));
        SceneNode identity=MailStyle.surface(theme,() -> 0x00000000,0).size(Size.fill(1),Size.natural());
        if(onBack!=null)identity.text("场景概览").focusable(true).clickFocus(true).onClick(onBack);
        identity.add(MailStyle.single(p,() -> "QZ Mail",() -> theme.get().withTextStyle(theme.get().textStyle.withEm(theme.get().textStyle.logicalEm*1.4f)))
                .size(Size.fill(1),Size.natural()));brand.add(identity);
        header.add(brand).add(search);
        header.add(new SceneNode().size(Size.fill(1),Size.natural()));
        header.add(MailStyle.button(p,fontName,theme,onFont));
        if (onTheme != null) header.add(SceneIconButton.create(p,SceneIconButton.Symbol.THEME,
                () -> mode.get().isDark()?"预览浅色":"预览深色",theme,onTheme));
        header.add(new SceneNode().size(Size.fixed(4),Size.natural()));
        return header;
    }
    static SceneNode navigation(ScenePageContext p, ReadableSignal<SceneTheme> theme,
            ReadableSignal<MailPalette> palette, MailboxModel model, ReadableSignal<Boolean> wide,ReadableSignal<Boolean> collapsed, Runnable receive) {
        ReadableSignal<Boolean> labeled=() -> !wide.get()||!collapsed.get();
        SceneNode nav = new SceneNode().gap(4);
        nav.bind(NodeBinding.value(() -> wide.get()+":"+collapsed.get() + ":" + theme.get().textStyle.logicalEm,(n,value) -> {
            SceneTheme t=theme.get(); boolean expanded=wide.get();
            n.axis(expanded?SceneNode.Axis.COLUMN:SceneNode.Axis.ROW).padding(0).gap(0)
                    .size(expanded?Size.fixed(MailStyle.px(t,collapsed.get()?72:256)):Size.fill(1),expanded?Size.fill(1):Size.natural());
        }));
        SceneNode receiveButton=MailStyle.surface(theme, () -> palette.get().receive,1.1f)
                .text("接收示例邮件").focusable(true).clickFocus(true).onClick(receive);
        receiveButton.control(new MailRaisedSurface(theme,() -> palette.get().receive,1.1f,null,80000000L));
        receiveButton.bind(NodeBinding.value(() -> theme.get().textStyle.logicalEm+":"+collapsed.get(),(n,v) -> {SceneTheme t=theme.get();n.padding(MailStyle.px(t,16))
                .size(collapsed.get()?Size.fixed(MailStyle.px(t,56)):Size.natural(),Size.natural().bounds(MailStyle.px(t,56),Integer.MAX_VALUE));}));
        receiveButton.axis(SceneNode.Axis.ROW).gap(12)
                .add(SceneIconButton.graphic(p,() -> SceneIconButton.Symbol.MAIL_OPEN,theme));
        receiveButton.add(MailStyle.single(p,() -> "接收示例邮件",theme).bind(NodeBinding.value(labeled,SceneNode::visible)));
        SceneNode receiveSlot=new SceneNode().padding(8).add(receiveButton);
        receiveSlot.bind(NodeBinding.value(theme,(n,t) -> n.padding(MailStyle.px(t,8))));
        receiveSlot.bind(NodeBinding.value(wide,SceneNode::visible));nav.add(receiveSlot);
        SceneNode separation=new SceneNode().size(Size.natural(),Size.fixed(8));
        separation.bind(NodeBinding.value(wide,SceneNode::visible));nav.add(separation);
        for (MailboxModel.Folder folder : MailboxModel.Folder.values()) {
            String label=folder==MailboxModel.Folder.INBOX?"收件箱":folder==MailboxModel.Folder.STARRED?"星标邮件":"所有邮件";
            SceneNode entry=MailStyle.surface(theme, () -> model.folder.get()==folder?palette.get().navigation:0x00000000,1.3f)
                    .axis(SceneNode.Axis.ROW).text(label).focusable(true).clickFocus(true).onClick(() -> model.setFolder(folder));
            entry.bind(NodeBinding.value(() -> wide.get()+":"+collapsed.get()+":"+theme.get().textStyle.logicalEm,(n,value) -> {
                SceneTheme t=theme.get();n.padding(MailStyle.px(t,4)).gap(0)
                        .size(wide.get()?Size.fixed(MailStyle.px(t,collapsed.get()?72:240)):Size.fill(1),Size.natural().bounds(MailStyle.px(t,32),Integer.MAX_VALUE));
            }));
            entry.add(new SceneNode().bind(NodeBinding.value(theme,(n,t) -> n.size(Size.fixed(MailStyle.px(t,12)),Size.natural()))).bind(NodeBinding.value(labeled,SceneNode::visible)));
            entry.add(SceneIconButton.graphic(p,() -> folder==MailboxModel.Folder.INBOX?SceneIconButton.Symbol.INBOX
                    :folder==MailboxModel.Folder.STARRED?SceneIconButton.Symbol.STAR:SceneIconButton.Symbol.ALL_MAIL,
                    () -> theme.get().withTextStyle(theme.get().textStyle.withColor(palette.get().mutedText)))
                    .bind(NodeBinding.value(() -> labeled.get()+":"+theme.get().textStyle.logicalEm,(n,v) -> {SceneTheme t=theme.get();n.size(Size.fixed(MailStyle.px(t,labeled.get()?28:64)),Size.fixed(MailStyle.px(t,24)));})));
            entry.add(new SceneNode().bind(NodeBinding.value(theme,(n,t) -> n.size(Size.fixed(MailStyle.px(t,20)),Size.natural()))).bind(NodeBinding.value(labeled,SceneNode::visible)));
            ReadableSignal<SceneTheme> entryTheme=() -> theme.get().withTextStyle(theme.get().textStyle
                    .withFontStyle(new club.heiqi.uilib.ui.scene.text.FontStyle(model.folder.get()==folder?700:400,
                            club.heiqi.uilib.ui.scene.text.FontStyle.Posture.NORMAL)));
            entry.add(MailStyle.single(p,() -> label,entryTheme).size(Size.fill(1),Size.natural()).bind(NodeBinding.value(labeled,SceneNode::visible)));
            entry.add(MailStyle.label(p,() -> Integer.toString(model.count(folder)),theme,.8f,true)
                    .size(Size.natural(),Size.natural()).bind(NodeBinding.value(labeled,SceneNode::visible)));
            nav.add(entry);
        }
        SceneNode filler=new SceneNode().size(Size.fill(1),Size.fill(1));
        filler.bind(NodeBinding.value(wide,SceneNode::visible));nav.add(filler);
        SceneNode caption=MailStyle.label(p,() -> "团队工作区",theme,.8f,true).padding(12);
        caption.bind(NodeBinding.value(() -> wide.get()&&labeled.get(),SceneNode::visible));nav.add(caption);
        return nav;
    }
}
