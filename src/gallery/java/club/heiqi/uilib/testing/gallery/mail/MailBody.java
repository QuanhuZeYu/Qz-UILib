package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/** 导航实际宽度与占位分别表达；悬停展开覆盖列表，正式排列序同时决定绘制与命中。 */
final class MailBody extends MailSurface {
    private final ReadableSignal<SceneTheme> theme;
    private final ReadableSignal<Boolean> wide,collapsed;
    private final SceneNode navigation,workspace,gutter;
    private MailBody(MailViewState view,SceneNode navigation,SceneNode workspace,SceneNode gutter) {
        super(view.theme,() -> 0,0,false);theme=view.theme;wide=view.wide;collapsed=view.collapsed;
        this.navigation=navigation;this.workspace=workspace;this.gutter=gutter;
    }
    static SceneNode create(MailViewState view,SceneNode navigation,SceneNode workspace) {
        SceneNode gutter=new SceneNode().bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(view.theme,
                (n,t) -> n.size(Size.fixed(MailStyle.px(t,16)),Size.fill(1))))
                .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(view.wide,SceneNode::visible));
        return new SceneNode().control(new MailBody(view,navigation,workspace,gutter)).size(Size.fill(1),Size.fill(1))
                .bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(view.wide,(n,value) -> n.axis(value?SceneNode.Axis.ROW:SceneNode.Axis.COLUMN)))
                .add(navigation).add(workspace).add(gutter);
    }
    @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) {return Arrays.asList(theme,wide,collapsed);}
    @Override public ChildPlacements arrange(SceneNode node,LayoutBox box,SceneChildMeasurer children) {
        if(!wide.get())return super.arrange(node,box,children);
        int reserved=MailStyle.px(theme.get(),collapsed.get()?72:256);
        int gap=gutter.width().constrain(gutter.width().value);
        int navWidth=navigation.width().constrain(navigation.width().value);
        int workWidth=workspace.width().constrain(Math.max(0,box.width-reserved-gap));
        List<ChildPlacements.Entry> placed=new ArrayList<>();
        placed.add(new ChildPlacements.Entry(workspace,new LayoutBox(reserved,0,workWidth,workspace.height().constrain(box.height))));
        placed.add(new ChildPlacements.Entry(gutter,new LayoutBox(reserved+workWidth,0,gap,gutter.height().constrain(box.height))));
        placed.add(new ChildPlacements.Entry(navigation,new LayoutBox(0,0,navWidth,navigation.height().constrain(box.height))));
        return new ChildPlacements(placed);
    }
}
