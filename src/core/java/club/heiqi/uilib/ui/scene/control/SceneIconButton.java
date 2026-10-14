package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 标准图形及完整名称；实际大小 coverage 共用普通 PaintPlan，不依赖宿主图标字体。 */
public final class SceneIconButton {
    public enum Symbol { STAR, STAR_FILLED, ARCHIVE, MAIL, MAIL_OPEN, SEARCH, CLOSE,
        CHEVRON_LEFT, CHEVRON_RIGHT, INBOX, ALL_MAIL, THEME, SORT, CHECK, MINUS, CHECK_BOX }
    private SceneIconButton() {}
    public static SceneNode create(ScenePageContext page, Symbol symbol, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Runnable action) {
        Objects.requireNonNull(symbol);return create(page,() -> symbol,name,theme,action);
    }
    /** 只改变标记，不替换动作节点或改变命中与焦点身份。 */
    public static SceneNode create(ScenePageContext page, ReadableSignal<Symbol> symbol, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Runnable action) {
        return SceneSymbolControl.node(page,name,new Control(theme,symbol),action);
    }
    /** 复合动作内的纯装饰，使用相同标记但不建立额外的输入目标。 */
    public static SceneNode graphic(ScenePageContext page, ReadableSignal<Symbol> symbol, ReadableSignal<SceneTheme> theme) {
        return SceneSymbolControl.graphic(page,new Control(theme,symbol));
    }
    private static final class Control extends SceneSymbolControl {
        private final ReadableSignal<Symbol> shape;
        Control(ReadableSignal<SceneTheme> theme, ReadableSignal<Symbol> shape) {super(theme,false);this.shape=Objects.requireNonNull(shape);}
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) {return Arrays.asList(theme,shape);}
        @Override void symbol(LayoutBox box, PaintPlanBuilder target, LayoutBox clip, int color) {
            SceneIconGlyph.paint(Objects.requireNonNull(shape.get(),"图标标记"),box,target,clip,color);
        }
    }
}
