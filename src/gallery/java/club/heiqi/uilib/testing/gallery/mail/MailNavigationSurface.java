package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import java.util.Arrays;
import java.util.List;

/** 同一正式外盒的两侧圆角独立变化，装饰仍只展开为现有RECT命令。 */
final class MailNavigationSurface extends MailSurface {
    private final ReadableSignal<Float> expansion;
    private final ReadableSignal<SceneTheme> theme;
    private final ReadableSignal<Integer> color;
    MailNavigationSurface(ReadableSignal<SceneTheme> theme,ReadableSignal<Integer> color,ReadableSignal<Float> expansion) {
        super(theme,color,16f/14f);this.theme=theme;this.color=color;this.expansion=expansion;
    }
    @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) {return Arrays.asList(theme,color,expansion);}
    @Override protected void paintBackground(SceneNode node,SceneGeometryView.Entry e,PaintPlanBuilder target,int color,int radius) {
        LayoutBox b=e.box;int half=b.width/2;
        LayoutBox left=new LayoutBox(b.x,b.y,half,b.height),right=new LayoutBox(b.x+half,b.y,b.width-half,b.height);
        if(e.clip!=null) {left=left.intersect(e.clip);right=right.intersect(e.clip);}
        ScenePaintShapes.rounded(target,b,left,color,Math.round(radius*(1-expansion.get())));
        ScenePaintShapes.rounded(target,b,right,color,radius);
    }
}
