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
    private final MailMotion actionMotion=new MailMotion();
    private int actionOpacity;
    int actionColor(int rgb) {return (actionOpacity<<24)|(rgb&0xffffff);}
    @Override public long nextPaintNanos(SceneNode n) {return actionMotion.nextPaintNanos();}
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
        SceneNode focus=n.runtime().focusRouter().focused().get();
        boolean keyboardFocus=belongs(focus,n)&&n.runtime().focusRouter().focusVisible(focus);
        boolean active=belongs(n.runtime().pointerRouter().hovered(g),n)||keyboardFocus;
        actionOpacity=actionMotion.opacity(active,keyboardFocus?0:150000000L);
        target.add(PaintCommand.rect(e.box,e.clip,background));
        if(opened.get())target.add(PaintCommand.rect(new LayoutBox(e.box.x,e.box.y,3,e.box.height),e.clip,p.accent));
        target.add(PaintCommand.rect(new LayoutBox(e.box.x,e.box.y+e.box.height-1,e.box.width,1),e.clip,
                p.outline));
    }
    @Override public void paintAfterChildren(SceneNode n,SceneGeometryView g,PaintPlanBuilder target) {
        if(belongs(n.runtime().pointerRouter().hovered(g),n)) {
            SceneGeometryView.Entry hover=g.get(n);if(hover!=null) {
                int edge=palette.get().outline;
                target.add(PaintCommand.rect(new LayoutBox(hover.box.x,hover.box.y,hover.box.width,1),hover.clip,edge));
                target.add(PaintCommand.rect(new LayoutBox(hover.box.x,hover.box.y+hover.box.height-1,hover.box.width,1),hover.clip,0x553c4043));
                target.add(PaintCommand.rect(new LayoutBox(hover.box.x,hover.box.y,1,hover.box.height),hover.clip,edge));
                target.add(PaintCommand.rect(new LayoutBox(hover.box.x+hover.box.width-1,hover.box.y,1,hover.box.height),hover.clip,edge));
            }
        }
        SceneNode focused=n.runtime().focusRouter().focused().get();
        if(!belongs(focused,n)||!n.runtime().focusRouter().focusVisible(focused))return;
        SceneGeometryView.Entry e=g.get(n);if(e==null)return;
        target.add(PaintCommand.rect(new LayoutBox(e.box.x,e.box.y,e.box.width,2),e.clip,palette.get().accent));
        target.add(PaintCommand.rect(new LayoutBox(e.box.x,e.box.y+e.box.height-2,e.box.width,2),e.clip,palette.get().accent));
    }
}
