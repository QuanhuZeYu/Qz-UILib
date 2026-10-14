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
            Runnable onTheme, ReadableSignal<String> fontName, Runnable onFont) {
        SceneNode header = MailStyle.surface(theme, () -> 0x00000000, 0)
                .axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural());
        header.bind(NodeBinding.value(theme,(n,t) -> n.gap(MailStyle.px(t,12))
                .size(Size.fill(1),Size.natural().bounds(MailStyle.px(t,64),Integer.MAX_VALUE))));
        SceneNode brand = MailStyle.surface(theme, () -> 0x00000000,0).axis(SceneNode.Axis.ROW);
        brand.bind(NodeBinding.value(() -> p.viewportWidth().get()+":"+theme.get().textStyle.logicalEm,(n,value) -> {
            SceneTheme t=theme.get();boolean expanded=p.viewportWidth().get()>=Math.ceil(t.textStyle.logicalEm*70);
            n.padding(MailStyle.px(t,12)).gap(MailStyle.px(t,12))
                    .size(Size.fixed(MailStyle.px(t,expanded?228:156)),Size.natural());
        }));
        if (onBack != null) brand.add(SceneIconButton.create(p,SceneIconButton.Symbol.CHEVRON_LEFT,() -> "场景概览",theme,onBack));
        brand.add(MailStyle.single(p,() -> "QZ Mail",() -> theme.get().withTextStyle(theme.get().textStyle.withEm(theme.get().textStyle.logicalEm*1.4f)))
                .size(Size.fill(1),Size.natural()));
        header.add(brand).add(search);
        header.add(new SceneNode().size(Size.fill(1),Size.natural()));
        header.add(MailStyle.button(p,fontName,theme,onFont));
        if (onTheme != null) header.add(SceneIconButton.create(p,SceneIconButton.Symbol.THEME,
                () -> mode.get().isDark()?"预览浅色":"预览深色",theme,onTheme));
        header.add(new SceneNode().size(Size.fixed(4),Size.natural()));
        return header;
    }
    static SceneNode navigation(ScenePageContext p, ReadableSignal<SceneTheme> theme,
            ReadableSignal<MailPalette> palette, MailboxModel model, ReadableSignal<Boolean> wide, Runnable receive) {
        SceneNode nav = new SceneNode().gap(4);
        nav.bind(NodeBinding.value(() -> wide.get() + ":" + theme.get().textStyle.logicalEm,(n,value) -> {
            SceneTheme t=theme.get(); boolean expanded=wide.get();
            n.axis(expanded?SceneNode.Axis.COLUMN:SceneNode.Axis.ROW).padding(MailStyle.px(t,8)).gap(MailStyle.px(t,4))
                    .size(expanded?Size.fixed(MailStyle.px(t,240)):Size.fill(1),expanded?Size.fill(1):Size.natural());
        }));
        SceneNode receiveButton=MailStyle.surface(theme, () -> palette.get().selected,1.1f)
                .text("接收示例邮件").focusable(true).clickFocus(true).onClick(receive);
        receiveButton.bind(NodeBinding.value(theme,(n,t) -> n.padding(MailStyle.px(t,16))
                .size(Size.natural(),Size.natural().bounds(MailStyle.px(t,56),Integer.MAX_VALUE))));
        receiveButton.axis(SceneNode.Axis.ROW).gap(12)
                .add(SceneIconButton.graphic(p,() -> SceneIconButton.Symbol.MAIL_OPEN,theme));
        receiveButton.add(MailStyle.single(p,() -> "接收示例邮件",theme));
        receiveButton.bind(NodeBinding.value(wide,SceneNode::visible));nav.add(receiveButton);
        SceneNode separation=new SceneNode().size(Size.natural(),Size.fixed(8));
        separation.bind(NodeBinding.value(wide,SceneNode::visible));nav.add(separation);
        for (MailboxModel.Folder folder : MailboxModel.Folder.values()) {
            String label=folder==MailboxModel.Folder.INBOX?"收件箱":folder==MailboxModel.Folder.STARRED?"星标邮件":"所有邮件";
            SceneNode entry=MailStyle.surface(theme, () -> model.folder.get()==folder?palette.get().selected:0x00000000,1.3f)
                    .axis(SceneNode.Axis.ROW).text(label).focusable(true).clickFocus(true).onClick(() -> model.setFolder(folder));
            entry.bind(NodeBinding.value(() -> wide.get()+":"+theme.get().textStyle.logicalEm,(n,value) -> {
                SceneTheme t=theme.get();n.padding(MailStyle.px(t,8)).gap(MailStyle.px(t,8))
                        .size(wide.get()?Size.fill(1):Size.fill(1),Size.natural());
            }));
            entry.add(SceneIconButton.graphic(p,() -> folder==MailboxModel.Folder.INBOX?SceneIconButton.Symbol.INBOX
                    :folder==MailboxModel.Folder.STARRED?SceneIconButton.Symbol.STAR:SceneIconButton.Symbol.ALL_MAIL,theme));
            entry.add(MailStyle.single(p,() -> label,theme).size(Size.fill(1),Size.natural()));
            entry.add(MailStyle.label(p,() -> Integer.toString(model.count(folder)),theme,.8f,true)
                    .size(Size.natural(),Size.natural()));
            nav.add(entry);
        }
        SceneNode filler=new SceneNode().size(Size.fill(1),Size.fill(1));
        filler.bind(NodeBinding.value(wide,SceneNode::visible));nav.add(filler);
        SceneNode caption=MailStyle.label(p,() -> "团队工作区",theme,.8f,true).padding(12);
        caption.bind(NodeBinding.value(wide,SceneNode::visible));nav.add(caption);
        return nav;
    }
}
