package club.heiqi.uilib.internal.chat3.input;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.control.SceneControlChrome;
import club.heiqi.uilib.ui.scene.control.SceneTextInputPrimitive;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天输入框的包内外观装配；输入行为与 Typography 全部由 primitive 持有。
 *
 * <p>背景与边框各自只有本类的一个绑定写入者，不再叠加通用输入框外观。
 * 节点声明交给既有 PaintCommand 管线；本类不接触宿主绘制或原版 GUI。</p>
 */
final class ChatInputChrome {

    private static final int INPUT_FONT_SIZE = 14;
    private static final int INPUT_HEIGHT_PX = 24;
    private static final int INPUT_PADDING_X_PX = 10;
    private static final int INPUT_PADDING_Y_PX = 2;
    private static final int TRANSPARENT = 0x00000000;

    private final Owner owner;

    private ChatInputChrome(Owner owner) {
        this.owner = owner;
    }

    static ChatInputChrome attach(SceneRuntime rt, ReadableSignal<Boolean> enabled,
            SceneTextInputPrimitive.Result input) {
        // 单独 dispose 外观时必须连同 Computed 与 Motion track 一起退订。
        // 在 mount 中随组件 Owner 清理，直接构建时随 runtime 根 Owner 清理。
        Owner[] scope = {null};
        if (Owner.current() != null) {
            scope[0] = Owner.current().createChild();
        } else {
            rt.__runRoot(() -> scope[0] = Owner.current().createChild());
        }
        ChatInputChrome chrome = new ChatInputChrome(scope[0]);
        try {
            scope[0].run(() -> bind(rt, enabled, input));
        } catch (RuntimeException | Error failure) {
            chrome.dispose();
            throw failure;
        }
        return chrome;
    }

    void dispose() {
        owner.dispose();
    }

    private static void bind(SceneRuntime rt, ReadableSignal<Boolean> enabled,
            SceneTextInputPrimitive.Result input) {
        SceneNode root = input.root();
        root.setFontSize(INPUT_FONT_SIZE);
        root.setFillParentWidth(true);
        root.setPreferredHeight(INPUT_HEIGHT_PX);
        root.setPadding(INPUT_PADDING_Y_PX, INPUT_PADDING_X_PX, INPUT_PADDING_Y_PX, INPUT_PADDING_X_PX);
        root.setBorderWidth(1);
        root.setCornerRadius(ChatMarkdownSettings.getInputCornerRadiusPx());

        // Settings 是 volatile 配置，不是 signal；按既有 UI 帧采样。
        // 值相等的快照阻断下游重算，不重建输入树，也不在 caret 闪烁时重建 backdrop。
        ReadableSignal<Appearance> appearance = Computed.create(Appearance.sample(), () -> {
            rt.__frameTimeNanos().get();
            return Appearance.sample();
        });
        rt.bindComputed(() -> appearance.get().backgroundArgb(), root::setBackgroundColor);
        rt.bindComputed(() -> Boolean.TRUE.equals(rt.interactionState(root).focused().get())
                ? appearance.get().focusBorderArgb() : TRANSPARENT, root::setBorderColor);
        rt.bindComputed(() -> appearance.get().cornerRadiusPx(), root::setCornerRadius);
        rt.bindComputed(() -> {
            Appearance current = appearance.get();
            return current.glassEnabled() ? UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN,
                    current.blurRadiusPx(), current.lensStrength()) : null;
        }, root::setBackdrop);

        ReadableSignal<Integer> textColor = Computed.create(() ->
                Boolean.TRUE.equals(input.isPlaceholder().get()) ? appearance.get().placeholderArgb()
                        : SceneStateColors.standardText(Boolean.TRUE.equals(enabled.get()), false));
        rt.bind(textColor, input.prefixText()::setTextColor);
        rt.bind(textColor, input.suffixText()::setTextColor);
        rt.bindComputed(() -> input.selection().get().isActive()
                ? SceneChromeTokens.SELECTION_BG : TRANSPARENT, input.highlightText()::setBackgroundColor);
        rt.bindComputed(() -> input.selection().get().isActive()
                ? SceneChromeTokens.SELECTION_TEXT : textColor.get(), input.highlightText()::setTextColor);

        // 两端槽位分别着色；选区失焦仍高亮，caret 可见性/闪烁完全读取 primitive。
        rt.__bindAnimatedColor(() -> Boolean.TRUE.equals(input.caretVisible().get())
                && input.selection().get().focusCp() == input.selection().get().startCp()
                        ? SceneChromeTokens.BORDER_FOCUS : TRANSPARENT,
                input.caret()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        rt.__bindAnimatedColor(() -> Boolean.TRUE.equals(input.caretVisible().get())
                && input.selection().get().isActive()
                && input.selection().get().focusCp() == input.selection().get().endCp()
                        ? SceneChromeTokens.BORDER_FOCUS : TRANSPARENT,
                input.caretAfter()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        SceneControlChrome.bindCursor(rt, root, enabled, SceneCursor.TEXT, SceneCursor.NOT_ALLOWED);
        rt.bind(enabled, value -> root.setHitTestable(Boolean.TRUE.equals(value)));
    }

    /** 只包含输入框消费的外观值，忽略无关 settings 更新。 */
    @Desugar
    private record Appearance(boolean glassEnabled, int backgroundArgb, int focusBorderArgb,
            int placeholderArgb, int cornerRadiusPx, int blurRadiusPx, float lensStrength) {

        static Appearance sample() {
            boolean glass = ChatMarkdownSettings.isGlassEnabled();
            int background = ChatMarkdownSettings.getInputBackgroundArgb();
            if (glass) {
                background = (background & 0x00FFFFFF) | (ChatMarkdownSettings.getGlassInputAlpha() << 24);
            }
            return new Appearance(glass, background, ChatMarkdownSettings.getInputFocusBorderArgb(),
                    ChatMarkdownSettings.getInputPlaceholderArgb(), ChatMarkdownSettings.getInputCornerRadiusPx(),
                    glass ? ChatMarkdownSettings.getGlassBlurRadiusPx() : 0,
                    glass ? ChatMarkdownSettings.getGlassLensStrength() : 0.0F);
        }
    }
}
