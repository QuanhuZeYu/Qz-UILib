package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

/** 页面作者的实际接入反例：新增自有导航项只提供语义来源和动作，不复制布局/动效机械代码。 */
public final class MailNavigationDeclaration {
    private MailNavigationDeclaration() {}
    public static void verify(FontTextService fonts) {
        ReactiveDomain domain=ReactiveDomain.direct();
        final class Host implements SceneHost {
            int width=1400;
            PaintPlan paint;
            Signal<String> name;
            Signal<SceneIconButton.Symbol> icon;
            Signal<Integer> count;
            Signal<Boolean> selected;
            SceneNode navigation;
            public int width() {return width;}
            public int height() {return 850;}
            public long metricsRevision() {return fonts.generation();}
            public void replay(PaintPlan value) {paint=value;}
        }
        Host host=new Host();
        try(ScenePageSession page=ScenePageSession.open(domain,host,fonts,p -> {
            MailViewState view=new MailViewState(p,p.theme(),p.themeMode(),() -> false,false);
            host.name=domain.signal("我的工作区");host.icon=domain.signal(SceneIconButton.Symbol.STAR);
            host.count=domain.signal(3);host.selected=domain.signal(false);
            host.navigation=new MailNavigation(p,view)
                    .item(host.name,host.icon,host.selected,host.count,() -> host.selected.set(!host.selected.get()))
                    .footer("声明示例").node;
            p.mountOwned(MailBody.create(view,host.navigation,new SceneNode().size(Size.fill(1),Size.fill(1))).own(view));
        })) {
            page.frame();
            final int[] point=new int[2];
            domain.execute(() -> {
                SceneGeometryView.Entry entry=page.pipeline().current().geometry.ordered().stream()
                        .filter(e -> e.node.focusable()&&e.node.text().equals("我的工作区")).findFirst().get();
                LayoutBox hit=entry.clip==null?entry.box:entry.box.intersect(entry.clip);
                point[0]=hit.x+hit.width/2;point[1]=hit.y+hit.height/2;
            });
            page.pointer(ScenePointerEvent.down(0,point[0],point[1]));page.pointer(ScenePointerEvent.up(0,point[0],point[1]));page.frame();
            domain.execute(() -> {
                if(!host.selected.get())throw new AssertionError("额外导航项没有通过正式物理点击发布动作");
                host.name.set("动态工作区");host.icon.set(SceneIconButton.Symbol.ALL_MAIL);host.count.set(12);
            });page.frame();
            if(host.paint.commands().stream().noneMatch(c -> c.text!=null&&c.text.logicalText.equals("动态工作区"))
                    ||host.paint.commands().stream().noneMatch(c -> c.text!=null&&c.text.logicalText.equals("12")))
                throw new AssertionError("声明来源改变后标签或计数没有自动更新");
            host.width=720;page.frame();
            domain.execute(() -> {
                if(page.pipeline().current().geometry.get(host.navigation).box.width!=host.width)
                    throw new AssertionError("额外导航项没有沿默认窄窗策略适配");
            });
        }
    }
}
