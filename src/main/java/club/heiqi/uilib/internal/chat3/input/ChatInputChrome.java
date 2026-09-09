package club.heiqi.uilib.internal.chat3.input;

import java.util.function.Supplier;

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
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 聊天输入框的包内外观装配；输入行为与 Typography 全部由 primitive 持有。
 *
 * <p><b>消费方向（G17/Input）</b>：本类消费通用主题接缝——输入表面取
 * {@link SceneThemes#surface(SceneRuntime, SceneTheme.Role) Role.INPUT} 角色配方，
 * 前景/caret/选区取主题语义色（契约 §2.8）；旧的聊天玻璃设置（开关/模糊/强度/底色/
 * 同心圆角/focus 描边/占位色）经 {@link LocalStyle} 转成同一配方的**局部覆盖**表达，
 * 优先级 = 显式聊天设置 &gt; 通用主题默认（契约 §3）。不新增第二份配置存储，
 * 也不把 chat3 反向 import 进 {@code ui.scene.theme}。</p>
 *
 * <p><b>属性归属</b>：background/border/borderWidth/cornerRadius/backdrop/surfaceElevation
 * 的唯一写入者是 {@link SceneSurfaceBinder}（经合并后的配方信号派生）；本类不再持有任何
 * 竞争绑定或静态色板写入者。{@link ChatMarkdownSettings} 是 volatile 配置而非 signal，
 * 按既有 UI 帧采样——值相等的快照阻断下游重算：聊天设置改变只重派生、不重建输入条，
 * 主题切换同样只重派生。文本前景、caret 与选区色消费主题只读信号。</p>
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

    /** 生产路径：聊天玻璃设置（volatile 帧采样）作为通用 INPUT 配方之上的局部覆盖。 */
    static ChatInputChrome attach(SceneRuntime rt, ReadableSignal<Boolean> enabled,
            SceneTextInputPrimitive.Result input) {
        return attach(rt, enabled, input, ChatInputChrome::sampleChatLocalStyle);
    }

    /**
     * 装配接缝：{@code chatLocalStyle} 为 null = 「无聊天局部设置」，输入框纯跟随通用
     * INPUT 主题配方；非 null = 按分量做局部覆盖（显式聊天设置优先于主题默认）。
     * 仅供测试与后续聊天装配复用，生产入口恒用 {@link #sampleChatLocalStyle()}。
     *
     * <p>单独 dispose 外观时必须连同 Computed、配方绑定与 Motion track 一起退订：
     * 在 mount 中随组件 Owner 清理，直接构建时随 runtime 根 Owner 清理。</p>
     */
    static ChatInputChrome attach(SceneRuntime rt, ReadableSignal<Boolean> enabled,
            SceneTextInputPrimitive.Result input, Supplier<LocalStyle> chatLocalStyle) {
        Owner[] scope = {null};
        if (Owner.current() != null) {
            scope[0] = Owner.current().createChild();
        } else {
            rt.__runRoot(() -> scope[0] = Owner.current().createChild());
        }
        ChatInputChrome chrome = new ChatInputChrome(scope[0]);
        try {
            scope[0].run(() -> bind(rt, enabled, input, chatLocalStyle));
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
            SceneTextInputPrimitive.Result input, Supplier<LocalStyle> chatLocalStyle) {
        SceneNode root = input.root();
        // 布局属性（字号/高度/padding/父宽填充）归控件自身，主题不接管（契约 §4）。
        root.setFontSize(INPUT_FONT_SIZE);
        root.setFillParentWidth(true);
        root.setPreferredHeight(INPUT_HEIGHT_PX);
        root.setPadding(INPUT_PADDING_Y_PX, INPUT_PADDING_X_PX, INPUT_PADDING_Y_PX, INPUT_PADDING_X_PX);

        // 构造期捕获通用输入配方与主题语义色（builder 内 resolve，effect 体内不再 resolve）。
        ReadableSignal<SceneSurfaceStyle> themedSurface = SceneThemes.surface(rt, SceneTheme.Role.INPUT);
        ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
        ReadableSignal<Integer> mutedForeground = SceneThemes.mutedForeground(rt);
        ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
        ReadableSignal<Integer> caretEdge = SceneThemes.borderFocus(rt);
        ReadableSignal<Integer> selectionBackground = SceneThemes.selectionBackground(rt);
        ReadableSignal<Integer> selectionForeground = SceneThemes.selectionForeground(rt);

        // 旧聊天设置 = 主题之上的局部覆盖层；volatile 配置按既有 UI 帧采样。
        LocalStyle initialLocal = chatLocalStyle == null ? LocalStyle.NONE : chatLocalStyle.get();
        ReadableSignal<LocalStyle> local = Computed.create(initialLocal, () -> {
            rt.__frameTimeNanos().get();
            return chatLocalStyle == null ? LocalStyle.NONE : chatLocalStyle.get();
        });
        SceneSurfaceStyle initialSurface = merge(themedSurface.get(), local.get());
        ReadableSignal<SceneSurfaceStyle> surface = Computed.create(initialSurface,
                () -> merge(themedSurface.get(), local.get()));

        // 唯一外观写入者：background/border/borderWidth/cornerRadius/backdrop/surfaceElevation
        // 全归表面绑定器从合并配方派生；聊天不再持有竞争的背景/边框/滤镜/圆角绑定。
        SceneSurfaceBinder.bind(rt, root, surface, enabled, rt.interactionState(root));
        // 构造期播种：同心圆角/边框宽是首帧 flush 前的几何合同（输入框挂进容器即成立），
        // 值与配方初值同源同值、只写这一次，首 flush 起由表面绑定器接管——不构成第二写入者。
        root.setCornerRadius(initialSurface.getCornerRadius());
        root.setBorderWidth(initialSurface.getBorderWidth());

        // 正文/禁用前景取主题语义色；占位色是显式聊天设置（无局部设置时跟随主题 mutedForeground）。
        ReadableSignal<Integer> textColor = Computed.create(() -> {
            if (Boolean.TRUE.equals(input.isPlaceholder().get())) {
                Integer placeholder = local.get().placeholderArgb();
                return placeholder != null ? placeholder : mutedForeground.get();
            }
            if (!Boolean.TRUE.equals(enabled.get())) {
                return disabledForeground.get();
            }
            return foreground.get();
        });
        rt.bind(textColor, input.prefixText()::setTextColor);
        rt.bind(textColor, input.suffixText()::setTextColor);
        // 选区/caret 色消费主题 selectionBackground/selectionForeground/borderFocus（契约 §2.8）。
        rt.bindComputed(() -> input.selection().get().isActive()
                ? selectionBackground.get() : TRANSPARENT, input.highlightText()::setBackgroundColor);
        rt.bindComputed(() -> input.selection().get().isActive()
                ? selectionForeground.get() : textColor.get(), input.highlightText()::setTextColor);

        // 两端槽位分别着色；选区失焦仍高亮，caret 可见性/闪烁完全读取 primitive。
        rt.__bindAnimatedColor(() -> Boolean.TRUE.equals(input.caretVisible().get())
                && input.selection().get().focusCp() == input.selection().get().startCp()
                        ? caretEdge.get() : TRANSPARENT,
                input.caret()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        rt.__bindAnimatedColor(() -> Boolean.TRUE.equals(input.caretVisible().get())
                && input.selection().get().isActive()
                && input.selection().get().focusCp() == input.selection().get().endCp()
                        ? caretEdge.get() : TRANSPARENT,
                input.caretAfter()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        SceneControlChrome.bindCursor(rt, root, enabled, SceneCursor.TEXT, SceneCursor.NOT_ALLOWED);
        rt.bind(enabled, value -> root.setHitTestable(Boolean.TRUE.equals(value)));
    }

    /** 生产覆盖层采样：把既有聊天设置（唯一配置存储）逐分量转成配方局部覆盖快照。 */
    private static LocalStyle sampleChatLocalStyle() {
        return new LocalStyle(Boolean.valueOf(ChatMarkdownSettings.isGlassEnabled()),
                Integer.valueOf(ChatMarkdownSettings.getGlassBlurRadiusPx()),
                Float.valueOf(ChatMarkdownSettings.getGlassLensStrength()),
                Integer.valueOf(ChatMarkdownSettings.getInputBackgroundArgb()),
                Integer.valueOf(ChatMarkdownSettings.getGlassInputAlpha()),
                Integer.valueOf(ChatMarkdownSettings.getInputFocusBorderArgb()),
                Integer.valueOf(ChatMarkdownSettings.getInputPlaceholderArgb()),
                Integer.valueOf(ChatMarkdownSettings.getInputCornerRadiusPx()));
    }

    /**
     * 主题 INPUT 配方 ⊕ 聊天局部覆盖：null 分量 = 该属性无显式聊天设置、保持主题值。
     * 聊天底色沿用既有语义——玻璃态时把 {@code glassInputAlpha} 合入底色 alpha，
     * 并把四态压平为同一底色（聊天输入框无状态变色，缘色透明、无浮雕、透镜乘子 1.0）。
     */
    private static SceneSurfaceStyle merge(SceneSurfaceStyle themed, LocalStyle patch) {
        if (patch.isNeutral()) {
            return themed;
        }
        SceneSurfaceStyle.Builder builder = themed.toBuilder();
        // 聊天既有表面属性是「帧采样立即派生」，无过渡动画（motionCannotOverwrite 合同）：
        // 局部覆盖层把配方过渡时长表达为 0（非正时长 = 立即应用，见 SceneMotionDriver）。
        builder.transitionMillis(0);
        if (patch.cornerRadiusPx() != null) {
            builder.cornerRadius(patch.cornerRadiusPx().intValue());
        }
        if (patch.focusBorderArgb() != null) {
            builder.focusEdge(patch.focusBorderArgb().intValue());
        }
        if (patch.glassEnabled() != null) {
            if (Boolean.TRUE.equals(patch.glassEnabled())) {
                builder.backdrop(chatGlass(themed, patch));
            } else {
                // 聊天玻璃总开关关闭 = 显式局部关滤镜（backdrop null 语义，契约 §2.2）。
                builder.backdrop(null);
            }
        }
        if (patch.backgroundArgb() != null) {
            int background = patch.backgroundArgb().intValue();
            if (Boolean.TRUE.equals(patch.glassEnabled()) && patch.glassInputAlpha() != null) {
                background = (background & 0x00FFFFFF) | (patch.glassInputAlpha().intValue() << 24);
            }
            SceneSurfaceStyle.StateStyle flat = new SceneSurfaceStyle.StateStyle(background,
                    TRANSPARENT, 0.0F, 1.0F);
            builder.idle(flat).hovered(flat).pressed(flat).disabled(flat);
        }
        return builder.build();
    }

    /** 聊天玻璃滤镜：blur/lens 是显式聊天设置；缺省（理论分支）保留主题材质档。 */
    private static UiBackdrop chatGlass(SceneSurfaceStyle themed, LocalStyle patch) {
        if (patch.blurRadiusPx() == null || patch.lensStrength() == null) {
            return themed.getBackdrop();
        }
        return UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN,
                patch.blurRadiusPx().intValue(), patch.lensStrength().floatValue());
    }

    /**
     * 聊天输入框的局部覆盖快照：既有聊天设置到配方语言的转译。
     * 每个分量 null = 该属性无显式聊天设置、跟随通用主题；{@link #NONE} = 纯主题档
     * （「无局部设置」形态，chrome 外观即通用 INPUT 配方对应值）。
     */
    @Desugar
    record LocalStyle(Boolean glassEnabled, Integer blurRadiusPx, Float lensStrength, Integer backgroundArgb,
            Integer glassInputAlpha, Integer focusBorderArgb, Integer placeholderArgb, Integer cornerRadiusPx) {

        static final LocalStyle NONE = new LocalStyle(null, null, null, null, null, null, null, null);

        boolean isNeutral() {
            return this.equals(NONE);
        }
    }
}
