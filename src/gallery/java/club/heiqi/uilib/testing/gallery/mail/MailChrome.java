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
    static SceneNode header(ScenePageContext p,MailViewState view,
            ReadableSignal<ThemeMode> mode,SceneNode search,Runnable onBack,Runnable onTheme) {
        ReadableSignal<SceneTheme> theme=view.theme;
        ReadableSignal<Boolean> collapsed=view.collapsed;
        SceneNode header = MailStyle.surface(theme, () -> 0x00000000, 0)
                .axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural());
        header.bind(NodeBinding.value(theme,(n,t) -> n.gap(MailStyle.px(t,12))
                .size(Size.fill(1),Size.natural().bounds(MailStyle.px(t,64),Integer.MAX_VALUE))));
        SceneNode brand = MailStyle.surface(theme, () -> 0x00000000,0).axis(SceneNode.Axis.ROW);
        brand.bind(NodeBinding.value(theme,(n,t) -> {
            boolean expanded=p.viewportWidth().get()>=Math.ceil(t.textStyle.logicalEm*70);
            n.padding(MailStyle.px(t,8)).gap(MailStyle.px(t,4))
                    .size(Size.fixed(MailStyle.px(t,expanded?244:156)),Size.natural());
        }));
        brand.add(SceneIconButton.create(p,SceneIconButton.Symbol.MENU,() -> collapsed.get()?"展开导航":"折叠导航",theme,view::toggleNavigation)
                .bind(NodeBinding.value(theme,(n,t) -> n.size(Size.fixed(MailStyle.px(t,48)),Size.fixed(MailStyle.px(t,48))))));
        SceneNode identity=MailStyle.surface(theme,() -> 0x00000000,0).size(Size.fill(1),Size.natural());
        if(onBack!=null)identity.text("场景概览").focusable(true).clickFocus(true).onClick(onBack);
        identity.add(MailStyle.single(p,() -> "QZ Mail",() -> theme.get().withTextStyle(theme.get().textStyle.withEm(theme.get().textStyle.logicalEm*1.4f)))
                .size(Size.fill(1),Size.natural()));brand.add(identity);
        header.add(brand).add(search);
        header.add(new SceneNode().size(Size.fill(1),Size.natural()));
        header.add(MailStyle.button(p,view::fontActionName,theme,view::toggleFont));
        if (onTheme != null) header.add(SceneIconButton.create(p,SceneIconButton.Symbol.THEME,
                () -> mode.get().isDark()?"预览浅色":"预览深色",theme,onTheme));
        header.add(new SceneNode().size(Size.fixed(4),Size.natural()));
        return header;
    }
    static SceneNode navigation(ScenePageContext p,MailViewState view,MailboxModel model) {
        return new MailNavigation(p,view)
                .primary("接收示例邮件",SceneIconButton.Symbol.MAIL_OPEN,model::receiveSample)
                .item("收件箱",SceneIconButton.Symbol.INBOX,() -> model.folder.get()==MailboxModel.Folder.INBOX,
                        () -> model.count(MailboxModel.Folder.INBOX),() -> model.setFolder(MailboxModel.Folder.INBOX))
                .item("星标邮件",SceneIconButton.Symbol.STAR,() -> model.folder.get()==MailboxModel.Folder.STARRED,
                        () -> model.count(MailboxModel.Folder.STARRED),() -> model.setFolder(MailboxModel.Folder.STARRED))
                .item("所有邮件",SceneIconButton.Symbol.ALL_MAIL,() -> model.folder.get()==MailboxModel.Folder.ALL,
                        () -> model.count(MailboxModel.Folder.ALL),() -> model.setFolder(MailboxModel.Folder.ALL))
                .footer("团队工作区").node;
    }
}
