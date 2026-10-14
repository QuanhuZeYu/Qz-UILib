package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneCheckBox;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Collections;

/** 行主体与次级动作分别命中；稳定id指向当前数据，行号不承担业务身份。 */
final class MessageRow {
    private MessageRow() {}
    static SceneNode create(ScenePageContext p, ReadableSignal<MailboxModel.Item> item,
                            MailboxModel model, ReadableSignal<SceneTheme> theme) {
        SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(8);
        ReadableSignal<Boolean> checked = () -> model.checked.get().contains(item.get().id);
        row.bind(NodeBinding.value(theme, (n,t) -> n.padding(Math.max(6,t.padding / 2))));
        row.bind(NodeBinding.value(() -> MailStyle.buttonTheme(theme.get(),checked.get()).button,SceneNode::background));
        row.add(SceneCheckBox.create(p, () -> checked.get() ? SceneCheckBox.State.CHECKED : SceneCheckBox.State.UNCHECKED,
                () -> "选择邮件：" + item.get().value.get().subject, () -> MailStyle.buttonTheme(theme.get(),false),
                ignored -> model.toggleChecked(item.get().id),false));
        row.add(SceneIconButton.create(p,SceneIconButton.Symbol.STAR,
                () -> (item.get().value.get().starred ? "取消星标：" : "添加星标：") + item.get().value.get().subject,
                () -> {
                    SceneTheme t=MailStyle.buttonTheme(theme.get(),false);
                    return t.withTextStyle(t.textStyle.withColor(item.get().value.get().starred ? 0xffb77708 : t.textStyle.color));
                }, () -> model.toggleStar(item.get().id)));
        SceneNode open = SceneButton.create(p, () -> {
            MailboxModel.Message m=item.get().value.get(); return m.sender + "\n" + m.subject;
        }, () -> {
            SceneTheme t=MailStyle.buttonTheme(theme.get(),item.get().id.equals(model.openedId.get()));
            return t.withTextStyle(t.textStyle.withFontStyle(item.get().value.get().read ? FontStyle.NORMAL : new FontStyle(600,FontStyle.Posture.NORMAL)));
        }, () -> model.open(item.get().id));
        row.add(open);
        SceneNode tail=new SceneNode().size(Size.natural(),Size.natural()).gap(4);
        tail.add(MailStyle.label(p, () -> item.get().value.get().time,theme,.85f,true).size(Size.natural(),Size.natural()));
        tail.add(SceneIconButton.create(p,SceneIconButton.Symbol.MAIL,
                () -> "标为" + (item.get().value.get().read ? "未读：" : "已读：") + item.get().value.get().subject,
                () -> MailStyle.buttonTheme(theme.get(),false), () -> model.setRead(item.get().id,!item.get().value.get().read)));
        row.add(tail);
        SceneNode preview=MailStyle.label(p, () -> item.get().value.get().preview,theme,.85f,true);
        // 摘要放在主体之后的自然行；同一正文操作保持明确的点击区域。
        SceneNode wrapper=new SceneNode().size(Size.fill(1),Size.natural()).gap(2).control(new Divider(theme)).add(row).add(preview);
        return wrapper;
    }
    private static final class Divider implements SceneControl {
        private final ReadableSignal<SceneTheme> theme;
        Divider(ReadableSignal<SceneTheme> theme){this.theme=theme;}
        @Override public MeasuredSize measure(SceneNode n,Constraints c,SceneTextMeasurer t){return new MeasuredSize(0,0);}
        @Override public MeasuredSize measure(SceneNode n,Constraints c,SceneTextMeasurer t,SceneChildMeasurer children){
            int w=0,h=0;for(SceneNode child:children.flowChildren()){
                MeasuredSize m=children.measureChild(child,new Constraints(c.availableWidth,Integer.MAX_VALUE));w=Math.max(w,m.width);h+=m.height;
            }return new MeasuredSize(w,h+n.gap());
        }
        @Override public ChildPlacements arrange(SceneNode n,LayoutBox box,SceneChildMeasurer children){
            java.util.List<ChildPlacements.Entry> entries=new java.util.ArrayList<>();int y=n.padding();
            for(SceneNode child:children.flowChildren()){
                MeasuredSize m=children.measureChild(child,new Constraints(Math.max(0,box.width-n.padding()*2),Integer.MAX_VALUE));
                entries.add(new ChildPlacements.Entry(child,new LayoutBox(n.padding(),y,Math.max(0,box.width-n.padding()*2),m.height)));y+=m.height+n.gap();
            }return new ChildPlacements(entries);
        }
        @Override public java.util.List<ReadableSignal<?>> paintDependencies(SceneNode n){return Collections.singletonList(theme);}
        @Override public void paint(SceneNode n,SceneGeometryView g,PaintPlanBuilder target){
            SceneGeometryView.Entry e=g.get(n);if(e!=null)target.add(PaintCommand.rect(new LayoutBox(e.box.x,e.box.y+e.box.height-1,e.box.width,1),e.clip,theme.get().button));
        }
    }
}
