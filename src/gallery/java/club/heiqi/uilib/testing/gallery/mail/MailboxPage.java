package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** Gmail 参照的本地工作台；完整和嵌入入口共享同一模型、页面声明与输入路由。 */
public final class MailboxPage {
    private MailboxPage() {}
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme) {
        return create(p,theme,p.themeMode());
    }
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme, ReadableSignal<ThemeMode> mode) {
        MailboxModel model=new MailboxModel(p.domain(),MailSamples.showcase());
        return create(p,theme,mode,model,true).own(model);
    }
    public static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme,
            ReadableSignal<ThemeMode> mode, Runnable onBack, Runnable onThemePreview) {
        MailboxModel model=new MailboxModel(p.domain(),MailSamples.showcase());
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
        MailViewState view=new MailViewState(p,base,mode,model.reading,onBack!=null);
        SceneNode root=new SceneNode().size(Size.fill(1),Size.fill(1)).own(view);
        root.bind(NodeBinding.value(view.palette,(n,colors) -> n.background(colors.canvas)));
        root.add(MailChrome.header(p,view,mode,MailSearch.create(p,view.theme,view.palette,model),onBack,onThemePreview));
        root.add(MailBody.create(view,MailChrome.navigation(p,view,model),MailWorkspace.create(p,view,model,paged)));
        // 稳定反馈槽由声明表达，不因收取/归档推动列表视口。
        SceneNode feedback=MailStyle.surface(view.theme,() -> 0,0,false).axis(SceneNode.Axis.ROW)
                .size(Size.fill(1),Size.natural()).padding(4);
        feedback.add(MailStyle.label(p,() -> model.feedback.get().isEmpty()?" ":model.feedback.get(),view.theme,.85f,true));
        root.add(feedback);
        return root;
    }

}
