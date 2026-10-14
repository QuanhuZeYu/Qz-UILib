package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.util.Arrays;
import java.util.List;

/** 行背景消费既有叶目标事实；关联画面不改变hover/pressed输入权威。 */
final class MailRowSurface extends MailSurface {
    private final ReadableSignal<MailPalette> palette;
    private final ReadableSignal<Boolean> checked,read,opened;
    MailRowSurface(ReadableSignal<SceneTheme> theme, ReadableSignal<MailPalette> palette,
            ReadableSignal<Boolean> checked, ReadableSignal<Boolean> read, ReadableSignal<Boolean> opened) {
        super(theme,() -> 0x00000000,0);this.palette=palette;this.checked=checked;this.read=read;this.opened=opened;
    }
    @Override public List<ReadableSignal<?>> paintDependencies(SceneNode n) {return Arrays.asList(palette,checked,read,opened);}
    private static boolean belongs(SceneNode target,SceneNode row) {
        for(SceneNode current=target;current!=null;current=current.parent())if(current==row)return true;return false;
    }
    @Override public void paint(SceneNode n, SceneGeometryView g, PaintPlanBuilder target) {
        SceneGeometryView.Entry e=g.get(n);if(e==null)return;MailPalette p=palette.get();
        int background=read.get()?p.read:p.unread;
        if(checked.get())background=p.selected;
        boolean pressed=false;
        for(SceneNode child:n.children())if(n.runtime().pointerRouter().pressed(child)){pressed=true;break;}
        if(pressed)background=checked.get()?p.selectedPressed:p.pressed;
        else if(belongs(n.runtime().pointerRouter().hovered(g),n))background=checked.get()?p.selectedHover:p.hover;
        target.add(PaintCommand.rect(e.box,e.clip,background));
        if(opened.get())target.add(PaintCommand.rect(new LayoutBox(e.box.x,e.box.y,3,e.box.height),e.clip,p.accent));
        target.add(PaintCommand.rect(new LayoutBox(e.box.x,e.box.y+e.box.height-1,e.box.width,1),e.clip,
                (p.outline&0xffffff)|0x33000000));
    }
    @Override public void paintAfterChildren(SceneNode n,SceneGeometryView g,PaintPlanBuilder target) {
        if(!belongs(n.runtime().focusRouter().focused().get(),n))return;
        SceneGeometryView.Entry e=g.get(n);if(e==null)return;
        target.add(PaintCommand.rect(new LayoutBox(e.box.x,e.box.y,e.box.width,2),e.clip,palette.get().accent));
        target.add(PaintCommand.rect(new LayoutBox(e.box.x,e.box.y+e.box.height-2,e.box.width,2),e.clip,palette.get().accent));
    }
}
