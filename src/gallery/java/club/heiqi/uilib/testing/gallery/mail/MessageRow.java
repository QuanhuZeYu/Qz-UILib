package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneCheckBox;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import java.util.ArrayList;
import java.util.List;

/** 复合主体拥有完整动作名称和一个输入目标，标准子内容在同一几何中显示；独立动作仍是兄弟目标。 */
final class MessageRow {
    private MessageRow() {}
    static SceneNode create(ScenePageContext p, ReadableSignal<MailboxModel.Item> item,
            MailboxModel model, ReadableSignal<SceneTheme> theme, ReadableSignal<MailPalette> palette) {
        ReadableSignal<Boolean> checked = () -> model.checked.get().contains(item.get().id);
        ReadableSignal<SceneTheme> contentTheme = () -> theme.get().withTextStyle(theme.get().textStyle
                .withFontStyle(item.get().value.get().read ? FontStyle.NORMAL : new FontStyle(700, FontStyle.Posture.NORMAL)));
        SceneNode row = MailStyle.surface(theme, () -> checked.get() ? palette.get().selected
                : item.get().value.get().read ? palette.get().read : palette.get().unread, 0)
                .axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural());
        row.bind(NodeBinding.value(theme, (n,t) -> n.gap(0).padding(MailStyle.px(t,4))));
        SceneNode check = SceneCheckBox.create(p, () -> checked.get() ? SceneCheckBox.State.CHECKED : SceneCheckBox.State.UNCHECKED,
                () -> "选择邮件：" + item.get().value.get().subject, () -> MailStyle.buttonTheme(theme.get(),false),
                ignored -> model.toggleChecked(item.get().id), false);
        SceneNode star = SceneIconButton.create(p, SceneIconButton.Symbol.STAR,
                () -> (item.get().value.get().starred ? "取消星标：" : "添加星标：") + item.get().value.get().subject,
                () -> theme.get().withTextStyle(theme.get().textStyle.withColor(item.get().value.get().starred
                        ? 0xfff4b400 : palette.get().mutedText)), () -> model.toggleStar(item.get().id));
        for (SceneNode action : new SceneNode[]{check,star}) action.bind(NodeBinding.value(theme,
                (n,t) -> n.size(Size.fixed(MailStyle.px(t,32)),Size.fixed(MailStyle.px(t,32)))));
        row.add(check).add(star);
        SceneNode body = MailStyle.surface(theme, () -> 0x00000000, 0).axis(SceneNode.Axis.ROW)
                .size(Size.fill(1),Size.natural()).focusable(true).clickFocus(true).onClick(() -> model.open(item.get().id));
        body.bind(NodeBinding.value(() -> item.get().value.get().sender + "\n" + item.get().value.get().subject, SceneNode::text));
        body.bind(NodeBinding.value(theme, (n,t) -> n.gap(MailStyle.px(t,16))
                .size(Size.fill(1),Size.natural().bounds(MailStyle.px(t,32),Integer.MAX_VALUE))));
        SceneNode sender = MailStyle.single(p, () -> item.get().value.get().sender, contentTheme);
        sender.bind(NodeBinding.value(theme, (n,t) -> n.size(Size.fixed(MailStyle.px(t,180)),Size.natural())));
        body.add(sender);
        SceneNode summary = new SceneNode().size(Size.fill(1),Size.natural()).control(new SubjectLine());
        summary.add(MailStyle.single(p, () -> item.get().value.get().subject, contentTheme));
        summary.add(MailStyle.single(p, () -> " — " + item.get().value.get().preview,
                () -> theme.get().withTextStyle(theme.get().textStyle.withColor(palette.get().mutedText))));
        body.add(summary); row.add(body);
        SceneNode time = MailStyle.single(p, () -> item.get().value.get().time,
                () -> contentTheme.get().withTextStyle(contentTheme.get().textStyle.withEm(theme.get().textStyle.logicalEm * .8f)));
        time.bind(NodeBinding.value(theme,(n,t) -> n.size(Size.natural().bounds(MailStyle.px(t,64),Integer.MAX_VALUE),Size.natural())));
        row.add(time);
        SceneNode read = SceneIconButton.create(p, SceneIconButton.Symbol.MAIL,
                () -> "标为" + (item.get().value.get().read ? "未读：" : "已读：") + item.get().value.get().subject,
                () -> MailStyle.buttonTheme(theme.get(),false), () -> model.setRead(item.get().id,!item.get().value.get().read));
        read.bind(NodeBinding.value(theme,(n,t) -> n.size(Size.fixed(MailStyle.px(t,32)),Size.fixed(MailStyle.px(t,32)))));
        row.add(read);
        return row;
    }

    /** 主题先取得自然宽度，余量才给摘要；只测量直属子项，不读回布局或截取原字符串。 */
    private static final class SubjectLine implements SceneControl {
        @Override public TextLayout layoutText(SceneNode n, SceneTextMeasurer text, int width) { return text.layout("",n.style(),width); }
        @Override public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer text) { return new MeasuredSize(0,0); }
        private List<ChildPlacements.Entry> place(int width, int height, SceneChildMeasurer children) {
            List<ChildPlacements.Entry> entries = new ArrayList<>(); int x = 0;
            for (SceneNode child : children.flowChildren()) {
                MeasuredSize natural = children.measureChild(child,new Constraints(Integer.MAX_VALUE,Integer.MAX_VALUE));
                int w = Math.min(Math.max(0,width-x),natural.width);
                MeasuredSize actual = children.measureChild(child,new Constraints(w,Integer.MAX_VALUE));
                entries.add(new ChildPlacements.Entry(child,new LayoutBox(x,Math.max(0,(height-actual.height)/2),w,actual.height)));
                x += w;
            }
            return entries;
        }
        @Override public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer text, SceneChildMeasurer children) {
            int w=0,h=0;
            for (ChildPlacements.Entry e : place(c.availableWidth,0,children)) { w+=e.relativeBox.width; h=Math.max(h,e.relativeBox.height); }
            return new MeasuredSize(w,h);
        }
        @Override public ChildPlacements arrange(SceneNode n, LayoutBox box, SceneChildMeasurer children) {
            return new ChildPlacements(place(box.width,box.height,children));
        }
        @Override public void paint(SceneNode n, SceneGeometryView g, PaintPlanBuilder target) {}
    }
}
