package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 正式按钮入口；激活和焦点权威仍由核心router负责。 */
public final class SceneButton {
    private SceneButton() {}

    public static SceneNode create(
            ScenePageContext context,
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            Runnable action) {
        return create(context, text, theme, action, SceneTextPolicy.WRAP);
    }

    public static SceneNode create(
            ScenePageContext context,
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            Runnable action,
            SceneTextPolicy policy) {
        return createWithIcon(context,text,theme,action,policy,null);
    }
    static SceneNode createModern(ScenePageContext context,ReadableSignal<String> text,ReadableSignal<SceneAppearance.Palette> colors,
            Runnable action,SceneAppearance.Role role,ReadableSignal<SceneIconButton.Symbol> icon){
        Objects.requireNonNull(context,"context").domain().checkWrite();
        return SceneTextControl.createModern(text,colors,role,icon).size(Size.natural(),Size.natural())
                .focusable(true).clickFocus(true).onClick(Objects.requireNonNull(action,"action"));
    }
    /** 包内组合仍使用同一文字、省略与提示控制器；图标只占用声明的文字前缀区域。 */
    static SceneNode createWithIcon(ScenePageContext context,ReadableSignal<String> text,ReadableSignal<SceneTheme> theme,
            Runnable action,SceneTextPolicy policy,ReadableSignal<SceneIconButton.Symbol> icon) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return SceneTextControl.create(text, theme, policy, true, icon)
                .size(Size.fill(1), Size.natural())
                .focusable(true)
                .clickFocus(true)
                .onClick(Objects.requireNonNull(action, "action"));
    }
}
