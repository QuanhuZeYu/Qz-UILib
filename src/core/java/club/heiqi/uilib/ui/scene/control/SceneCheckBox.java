package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 显示三态，发布布尔意图；全选范围由业务决定，沿标准按钮输入合同。 */
public final class SceneCheckBox {
    public enum State { UNCHECKED, CHECKED, MIXED }
    private SceneCheckBox() {}
    public static SceneNode create(ScenePageContext page, ReadableSignal<State> state, ReadableSignal<String> name,
                                   ReadableSignal<SceneTheme> theme, Consumer<Boolean> change) {
        return create(page,state,name,theme,change,true);
    }
    public static SceneNode create(ScenePageContext page, ReadableSignal<State> state, ReadableSignal<String> name,
                                   ReadableSignal<SceneTheme> theme, Consumer<Boolean> change, boolean showLabel) {
        Objects.requireNonNull(state); Objects.requireNonNull(change);
        return SceneSymbolControl.node(page,name,new Control(theme,showLabel,state),
                () -> change.accept(Objects.requireNonNull(state.get(),"复选状态") != State.CHECKED));
    }
    private static final class Control extends SceneSymbolControl {
        private final ReadableSignal<State> state;
        Control(ReadableSignal<SceneTheme> theme, boolean showLabel, ReadableSignal<State> state) { super(theme,showLabel); this.state=state; }
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return java.util.Arrays.asList(theme,state); }
        @Override void symbol(LayoutBox box, PaintPlanBuilder target, LayoutBox clip, int color) {
            SceneIconGlyph.paint(SceneIconButton.Symbol.CHECK_BOX,box,target,clip,color);
            State s=Objects.requireNonNull(state.get(),"复选状态");
            if(s==State.MIXED)SceneIconGlyph.paint(SceneIconButton.Symbol.MINUS,box,target,clip,color);
            else if(s==State.CHECKED)SceneIconGlyph.paint(SceneIconButton.Symbol.CHECK,box,target,clip,color);
        }
    }
}
