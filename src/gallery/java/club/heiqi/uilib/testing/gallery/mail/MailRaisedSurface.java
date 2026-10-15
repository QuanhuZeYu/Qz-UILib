package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;

/** 搜索聚焦与主动作悬停的有限阴影，不改变父布局或命中。 */
final class MailRaisedSurface extends MailSurface {
    private final ReadableSignal<SceneTheme> theme;
    private final ReadableSignal<Integer> color;
    private final ReadableSignal<Boolean> raised;
    private final float radius;
    private final long duration;
    private final MailMotion background=new MailMotion(),shadow=new MailMotion();
    MailRaisedSurface(ReadableSignal<SceneTheme> theme,ReadableSignal<Integer> color,float radius,
            ReadableSignal<Boolean> raised,long duration) {
        super(theme,color,radius);this.theme=theme;this.color=color;this.radius=radius;this.raised=raised;this.duration=duration;
    }
    @Override public void paint(SceneNode n,SceneGeometryView geometry,PaintPlanBuilder target) {
        SceneGeometryView.Entry e=geometry.get(n);if(e==null)return;
        boolean active=raised==null?n.runtime().pointerRouter().hovered(geometry)==n:raised.get();
        int opacity=shadow.opacity(active,duration),r=Math.round(theme.get().textStyle.logicalEm*radius);
        if(opacity>0)for(int spread=3;spread>0;spread--) {
            int alpha=opacity*(spread==1?18:spread==2?9:4)/255;
            ScenePaintShapes.rounded(target,new LayoutBox(e.box.x-spread,e.box.y+1-spread,e.box.width+spread*2,e.box.height+spread*2),
                    e.clip,(alpha<<24)|0x3c4043,r+spread);
        }
        int fill=background.color(color.get(),duration);
        if(n.clickAction()!=null&&n.runtime().pointerRouter().pressed(n))fill=theme.get().pressed;
        ScenePaintShapes.rounded(target,e.box,e.clip,fill,r);
    }
    @Override public long nextPaintNanos(SceneNode n) {return Math.min(background.nextPaintNanos(),shadow.nextPaintNanos());}
}
