package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneDataTableEditorChrome —— DataTable 编辑槽（TextInput/Select）与下拉浮层的主题化装配入口。
 *
 * <h3>定位（液态玻璃迁移后）</h3>
 * <p>{@link SceneDataTable} 的默认路径已改为<b>直接复用</b>已主题化的 {@link SceneTextInput} 与
 * {@link SceneSelect}（INPUT 表面 + OVERLAY 弹出底座由它们自持），本类不再是默认外观写入者。
 * 本类保留原有公共签名，作为「自行装配 primitive 的调用方」的兼容入口：所有外观都从主题派生
 * （{@link SceneSurfaceBinder} 独占 background/border/borderWidth/cornerRadius/backdrop/
 * surfaceElevation，文字/caret/选区取主题语义色），不再持有任何静态色板常量。</p>
 *
 * <h3>属性归属</h3>
 * <p>表面属性唯一写入者是 {@link SceneSurfaceBinder}；文本前景是 {@code bindComputed} /
 * {@code bindForeground} 单点绑定；cursor 保持 {@link SceneControlChrome#bindCursor}。
 * 同一节点只绑定一次，不与任何旧实色绑定竞争。</p>
 *
 * <h3>守 R1（静态工具零实例字段）</h3>
 * <p>类为 {@code public final} + {@code private} 构造器，零实例字段、零静态可变状态。
 * 唯一的成员类 {@link DataTableListboxChrome} 是浮层 chrome 装配器实例（每次 select 列渲染
 * 时按需 new，持有 {@link SceneRuntime} 引用用于注册绑定），不属于工具类自身的实例状态。</p>
 *
 * <h3>Boolean 解包</h3>
 * <p>对 signal 值统一用 {@code Boolean.TRUE.equals(x)} 防御性解包，与 DataTable 原编辑槽装配
 * 口径一字不差，行为零变。</p>
 *
 * @see SceneDataTable
 * @see SceneTextInput
 * @see SceneSelect
 * @see SceneControlChrome
 */
public final class SceneDataTableEditorChrome {

    /** 编辑输入槽横向内边距（纯布局，不参与外观归属）。 */
    private static final int EDIT_SLOT_PAD_H = 4;
    /** 下拉选项内边距（纯布局）。 */
    private static final int ITEM_PADDING = SceneChromeTokens.PAD_MD;
    /** 不可见态（caret/选区未激活）：全透明，纯 PAINT 切换不重排。 */
    private static final int TRANSPARENT = SceneChromeTokens.TRANSPARENT;
    /** 候选行 hover 覆盖强度：主题 accent 的低透明度轻量覆盖（与 SceneSelect 同口径）。 */
    private static final int ITEM_HOVER_ALPHA = 0x1F;
    /** 候选行选中覆盖强度：明显强于 hover，选中不只靠透明度区分。 */
    private static final int ITEM_SELECTED_ALPHA = 0x33;
    /** 候选行选中且 hover 的覆盖强度：强调色加深。 */
    private static final int ITEM_SELECTED_HOVER_ALPHA = 0x4C;
    /** 候选行键盘高亮覆盖强度：主题选区背景的轻量覆盖。 */
    private static final int ITEM_HIGHLIGHT_ALPHA = 0x59;
    /** 恒真启用信号：弹出浮层不参与 disabled 语义（禁用时不展开）。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

    /** 纯静态工具类，禁止实例化。 */
    private SceneDataTableEditorChrome() {
    }

    /**
     * 装配 DataTable TextInput 编辑槽视觉（兼容入口）。
     *
     * <p>布局只设内边距与高度；INPUT 表面（含滤镜、边框、圆角、实体高度）由
     * {@link SceneSurfaceBinder} 独占；正文/占位/禁用前景与 caret、选区色取主题语义色；
     * cursor 保持既有绑定。默认路径请直接复用 {@link SceneTextInput}。</p>
     *
     * @param rt            场景运行时
     * @param result        TextInput primitive 创建结果
     * @param contentHeight 单元格内容高度
     * @param enabled       是否启用
     */
    public static void decorateTextInputEditor(SceneRuntime rt, SceneTextInputPrimitive.Result result,
                                               int contentHeight, ReadableSignal<Boolean> enabled) {
        SceneNode root = result.root();
        root.setPadding(0, EDIT_SLOT_PAD_H, 0, EDIT_SLOT_PAD_H);
        root.setPreferredHeight(contentHeight);

        SceneInteractionState interaction = rt.interactionState(root);
        // 时序契约：构造期声明关心，Router 后续写入才会落到已创建的 signal。
        interaction.hovered();
        interaction.pressed();
        interaction.focused();
        ReadableSignal<SceneSurfaceStyle> surface = SceneThemes.surface(rt, SceneTheme.Role.INPUT);
        SceneSurfaceBinder.bind(rt, root, surface, enabled, interaction);

        ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
        ReadableSignal<Integer> mutedForeground = SceneThemes.mutedForeground(rt);
        ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
        rt.bindComputed(() -> resolveEditTextColor(result.isPlaceholder().get(), enabled.get(),
                        foreground.get(), mutedForeground.get(), disabledForeground.get()),
                result.prefixText()::setTextColor);
        rt.bindComputed(() -> resolveEditTextColor(result.isPlaceholder().get(), enabled.get(),
                        foreground.get(), mutedForeground.get(), disabledForeground.get()),
                result.suffixText()::setTextColor);

        // caret 双槽位：focus 在选区哪一端，哪端着色（色值取主题聚焦色）。
        ReadableSignal<Integer> caretColor = SceneThemes.borderFocus(rt);
        rt.__bindAnimatedColor(() -> Boolean.TRUE.equals(result.caretVisible().get())
                        && result.selection().get().focusCp() == result.selection().get().startCp()
                        ? caretColor.get() : TRANSPARENT,
                result.caret()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        rt.__bindAnimatedColor(() -> Boolean.TRUE.equals(result.caretVisible().get())
                        && result.selection().get().isActive()
                        && result.selection().get().focusCp() == result.selection().get().endCp()
                        ? caretColor.get() : TRANSPARENT,
                result.caretAfter()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);

        // 选区高亮：激活即显示（失焦保留选区可见），背景/前景取主题选区语义色。
        ReadableSignal<Integer> selectionBackground = SceneThemes.selectionBackground(rt);
        ReadableSignal<Integer> selectionForeground = SceneThemes.selectionForeground(rt);
        rt.bindComputed(() -> Boolean.TRUE.equals(result.selection().get().isActive())
                        ? selectionBackground.get() : TRANSPARENT,
                result.highlightText()::setBackgroundColor);
        rt.bindComputed(() -> Boolean.TRUE.equals(result.selection().get().isActive())
                        ? selectionForeground.get()
                        : resolveEditTextColor(result.isPlaceholder().get(), enabled.get(),
                                foreground.get(), mutedForeground.get(), disabledForeground.get()),
                result.highlightText()::setTextColor);
        SceneControlChrome.bindCursor(rt, root, enabled, SceneCursor.TEXT, SceneCursor.DEFAULT);
    }

    /**
     * 装配 DataTable Select 编辑槽视觉（兼容入口）。
     *
     * <p>布局只设内边距与高度；INPUT 表面由 {@link SceneSurfaceBinder} 独占；选中值文本与箭头
     * 取主题语义前景；cursor 保持既有绑定。默认路径请直接复用 {@link SceneSelect}。</p>
     *
     * @param rt            场景运行时
     * @param result        Select primitive 创建结果
     * @param contentHeight 单元格内容高度
     * @param enabled       是否启用
     */
    public static void decorateSelectEditor(SceneRuntime rt, SceneSelectPrimitive.Result result,
                                            int contentHeight, ReadableSignal<Boolean> enabled) {
        SceneNode trigger = result.trigger();
        trigger.setPadding(0, EDIT_SLOT_PAD_H, 0, EDIT_SLOT_PAD_H);
        trigger.setPreferredHeight(contentHeight);

        SceneInteractionState interaction = rt.interactionState(trigger);
        interaction.hovered();
        interaction.pressed();
        interaction.focused();
        SceneSurfaceBinder.bind(rt, trigger, SceneThemes.surface(rt, SceneTheme.Role.INPUT),
                enabled, interaction);

        ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
        ReadableSignal<Integer> mutedForeground = SceneThemes.mutedForeground(rt);
        ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
        ReadableSignal<Integer> focusEdge = SceneThemes.borderFocus(rt);
        rt.bindComputed(() -> Boolean.TRUE.equals(enabled.get())
                        ? foreground.get() : disabledForeground.get(),
                result.label()::setTextColor);
        rt.bindComputed(() -> resolveSelectArrowColor(enabled.get(), result.expanded().get(),
                        mutedForeground.get(), disabledForeground.get(), focusEdge.get()),
                result.arrow()::setTextColor);
        SceneControlChrome.bindCursor(rt, trigger, enabled, SceneCursor.POINTER, SceneCursor.DEFAULT);
    }

    /**
     * 解析编辑槽底色（兼容纯函数，取库默认主题的 INPUT 配方）。
     *
     * @param focused 是否聚焦或展开
     * @param hovered 是否 hover
     * @return ARGB 底色
     */
    public static int resolveEditSlotBackground(Boolean focused, Boolean hovered) {
        SceneSurfaceStyle input = SceneThemes.DEFAULT.surface(SceneTheme.Role.INPUT);
        return Boolean.TRUE.equals(focused) || Boolean.TRUE.equals(hovered)
                ? input.getHovered().getTint() : input.getIdle().getTint();
    }

    /**
     * 解析编辑槽边框色（兼容纯函数，取库默认主题的 INPUT 配方与聚焦缘色）。
     *
     * @param focused 是否聚焦或展开
     * @param hovered 是否 hover
     * @return ARGB 边框色
     */
    public static int resolveEditBorder(Boolean focused, Boolean hovered) {
        SceneSurfaceStyle input = SceneThemes.DEFAULT.surface(SceneTheme.Role.INPUT);
        if (Boolean.TRUE.equals(focused)) {
            return input.getFocusEdge();
        }
        return Boolean.TRUE.equals(hovered) ? input.getHovered().getEdge() : input.getIdle().getEdge();
    }

    /**
     * 解析编辑槽文本色（兼容纯函数，取库默认主题语义色）。
     *
     * @param placeholder 是否 placeholder
     * @param enabled     是否启用
     * @return ARGB 文本色
     */
    public static int resolveEditTextColor(Boolean placeholder, Boolean enabled) {
        return resolveEditTextColor(placeholder, enabled,
                SceneThemes.DEFAULT.foreground(),
                SceneThemes.DEFAULT.mutedForeground(),
                SceneThemes.DEFAULT.disabledForeground());
    }

    /**
     * 解析编辑槽文本色（主题语义色版）。
     *
     * @param placeholder        是否 placeholder
     * @param enabled            是否启用
     * @param foreground         主题正文前景
     * @param mutedForeground    主题次要/占位前景
     * @param disabledForeground 主题禁用前景
     * @return ARGB 文本色
     */
    private static int resolveEditTextColor(Boolean placeholder, Boolean enabled,
                                            int foreground, int mutedForeground, int disabledForeground) {
        if (!Boolean.TRUE.equals(enabled)) {
            return disabledForeground;
        }
        return Boolean.TRUE.equals(placeholder) ? mutedForeground : foreground;
    }

    /**
     * 解析 Select 箭头色（兼容纯函数，取库默认主题语义色）。
     *
     * @param enabled  是否启用
     * @param expanded 是否展开
     * @return ARGB 文本色
     */
    public static int resolveSelectArrowColor(Boolean enabled, Boolean expanded) {
        return resolveSelectArrowColor(enabled, expanded,
                SceneThemes.DEFAULT.mutedForeground(),
                SceneThemes.DEFAULT.disabledForeground(),
                SceneThemes.DEFAULT.borderFocus());
    }

    /**
     * 解析 Select 箭头色（主题语义色版）。
     *
     * @param enabled            是否启用
     * @param expanded           是否展开
     * @param mutedForeground    主题次要前景
     * @param disabledForeground 主题禁用前景
     * @param focusEdge          主题聚焦缘色
     * @return ARGB 文本色
     */
    private static int resolveSelectArrowColor(Boolean enabled, Boolean expanded,
                                               int mutedForeground, int disabledForeground, int focusEdge) {
        if (!Boolean.TRUE.equals(enabled)) {
            return disabledForeground;
        }
        return Boolean.TRUE.equals(expanded) ? focusEdge : mutedForeground;
    }

    /**
     * 解析 Select 是否按聚焦态显示。
     *
     * @param expanded 是否展开
     * @param focused  是否聚焦
     * @return 聚焦态显示标记
     */
    public static Boolean selectFocused(Boolean expanded, Boolean focused) {
        return Boolean.valueOf(Boolean.TRUE.equals(expanded) || Boolean.TRUE.equals(focused));
    }

    /**
     * 解析下拉选项背景色（兼容纯函数，取库默认主题强调色与选区背景的轻量覆盖）。
     *
     * @param selected    是否选中
     * @param highlighted 是否键盘高亮
     * @param hovered     是否 hover
     * @return ARGB 背景色
     */
    public static int resolveItemBackground(boolean selected, boolean highlighted, Boolean hovered) {
        return resolveItemBackground(selected, highlighted, hovered,
                SceneThemes.DEFAULT.accent(), SceneThemes.DEFAULT.selectionBackground());
    }

    /**
     * 解析下拉选项背景色：键盘高亮 &gt; 选中 &gt; hover &gt; 透明（浮层玻璃底可见）。
     *
     * @param selected            是否选中
     * @param highlighted         是否键盘高亮
     * @param hovered             是否 hover
     * @param accent              主题强调色
     * @param selectionBackground 主题选区背景色
     * @return ARGB 背景色
     */
    private static int resolveItemBackground(boolean selected, boolean highlighted, Boolean hovered,
                                             int accent, int selectionBackground) {
        if (highlighted) {
            return tint(selectionBackground, ITEM_HIGHLIGHT_ALPHA);
        }
        if (selected) {
            return tint(accent, Boolean.TRUE.equals(hovered) ? ITEM_SELECTED_HOVER_ALPHA : ITEM_SELECTED_ALPHA);
        }
        if (Boolean.TRUE.equals(hovered)) {
            return tint(accent, ITEM_HOVER_ALPHA);
        }
        return TRANSPARENT;
    }

    /**
     * 保留色 RGB、替换 alpha 通道（轻量覆盖用）。
     *
     * @param argb  源色
     * @param alpha 目标 alpha（0..255）
     * @return 替换 alpha 后的 ARGB
     */
    private static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * 创建 DataTable Select 下拉浮层 chrome 装配器（兼容入口）。
     *
     * @param rt 场景运行时
     * @return 浮层 chrome 装配器实例
     */
    public static DataTableListboxChrome createListboxChrome(SceneRuntime rt) {
        return new DataTableListboxChrome(rt);
    }

    /** DataTable Select 下拉浮层 chrome 装配器（兼容入口；默认路径由 SceneSelect 自持）。 */
    public static final class DataTableListboxChrome implements SceneSelectPrimitive.ListboxChrome {
        /** 场景运行时，用于注册主题绑定。 */
        private final SceneRuntime rt;

        /**
         * 创建下拉浮层 chrome 装配器。
         *
         * @param rt 场景运行时
         */
        public DataTableListboxChrome(SceneRuntime rt) {
            this.rt = rt;
        }

        @Override
        public void decorateListbox(SceneNode listbox) {
            // 弹出底座：OVERLAY 配方画在浮层根（唯一外观写入者），延迟打开时继承来源主题。
            SceneSurfaceBinder.bind(rt, listbox, SceneThemes.surface(rt, SceneTheme.Role.OVERLAY),
                    ALWAYS_ENABLED, rt.interactionState(listbox));
        }

        @Override
        public void decorateItem(SceneSelectPrimitive.ItemHandle handle) {
            handle.item().setPadding(ITEM_PADDING);
            handle.item().setCursor(SceneCursor.POINTER);
            // 行只做轻量状态覆盖：不逐项采样滤镜，默认透明露出浮层玻璃底。
            ReadableSignal<Integer> accent = SceneThemes.accent(rt);
            ReadableSignal<Integer> selectionBackground = SceneThemes.selectionBackground(rt);
            rt.__bindAnimatedColor(() -> resolveItemBackground(
                            Boolean.TRUE.equals(handle.selected().get()),
                            Boolean.TRUE.equals(handle.highlighted().get()),
                            handle.interaction().hovered().get(),
                            accent.get(), selectionBackground.get()),
                    handle.item()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            rt.bind(foreground, handle.label()::setTextColor);
        }
    }
}
