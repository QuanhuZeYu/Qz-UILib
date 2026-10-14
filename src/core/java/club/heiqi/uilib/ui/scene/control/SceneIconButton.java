package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.util.Objects;

/** 向量图标及完整名称；不依赖宿主图标字体或缺字占位。 */
public final class SceneIconButton {
    public enum Symbol { STAR, ARCHIVE, MAIL, ARROW_LEFT, MORE }
    private SceneIconButton() {}
    public static SceneNode create(ScenePageContext page, Symbol symbol, ReadableSignal<String> name,
                                   ReadableSignal<SceneTheme> theme, Runnable action) {
        Objects.requireNonNull(symbol);
        return SceneSymbolControl.node(page,name,new Control(theme,symbol),action);
    }
    private static final class Control extends SceneSymbolControl {
        private final Symbol shape;
        Control(ReadableSignal<SceneTheme> theme, Symbol shape) { super(theme,false);this.shape=shape; }
        @Override void symbol(LayoutBox box, PaintPlanBuilder target, LayoutBox clip, int color) {
            float x=box.x,y=box.y,w=box.width,h=box.height;
            if (shape == Symbol.STAR) {
                float[] px={.5f,.61f,.95f,.68f,.78f,.5f,.22f,.32f,.05f,.39f};
                float[] py={.02f,.34f,.36f,.57f,.93f,.73f,.93f,.57f,.36f,.34f};
                for(int i=0;i<px.length;i++) { int j=(i+1)%px.length;target.add(PaintCommand.line(x+w*px[i],y+h*py[i],x+w*px[j],y+h*py[j],1.5f,clip,color)); }
            } else if(shape == Symbol.MORE) {
                for(int i=0;i<3;i++) target.add(PaintCommand.rect(new LayoutBox((int)(x+w*.45f),(int)(y+h*(.15f+i*.3f)),2,2),clip,color));
            } else if(shape == Symbol.ARROW_LEFT) {
                target.add(PaintCommand.line(x+w*.85f,y+h*.5f,x+w*.15f,y+h*.5f,1.5f,clip,color));
                target.add(PaintCommand.line(x+w*.15f,y+h*.5f,x+w*.45f,y+h*.2f,1.5f,clip,color));
                target.add(PaintCommand.line(x+w*.15f,y+h*.5f,x+w*.45f,y+h*.8f,1.5f,clip,color));
            } else {
                LayoutBox outline=new LayoutBox((int)x,(int)(y+h*.15f),(int)w,Math.max(0,(int)(h*.7f)));
                SceneControlPaint.border(target,outline,clip,color);
                if(shape == Symbol.MAIL) {
                    target.add(PaintCommand.line(x,y+h*.15f,x+w*.5f,y+h*.55f,1.5f,clip,color));
                    target.add(PaintCommand.line(x+w*.5f,y+h*.55f,x+w,y+h*.15f,1.5f,clip,color));
                } else target.add(PaintCommand.line(x+w*.3f,y+h*.4f,x+w*.7f,y+h*.4f,1.5f,clip,color));
            }
        }
    }
}
