package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * SceneDataTableEditorChrome —— DataTable 编辑槽（TextInput/Select）与下拉浮层视觉样式收敛器（纯静态工具）。
 *
 * <h3>定位：DataTable 编辑槽壳样式层，对标 Compose TextFieldDefaults</h3>
 * <p>{@link SceneDataTable} 的 TextInput/Select 列编辑槽与 Select 下拉浮层共享一套嵌入式深色槽
 * 专用色板（{@code EDIT_*}/{@code LISTBOX_*}/{@code ITEM_*}）与装配样板（边框圆角、caret/箭头着色、
 * hover/focus 状态色派生、cursor 绑定）。本类把这套样式从 DataTable 主类抽出，收成静态工具方法 +
 * 浮层 chrome 装配器，对标 Jetpack Compose {@code TextFieldDefaults} 静态样式对象范式：
 * 数据模型（Row/Column/Props）留主类守 API 兼容，壳样式层独立收口。</p>
 *
 * <h3>为何放 control 包（守 R12 / I6）</h3>
 * <p>helper 依赖 {@link SceneRuntime}（runtime 层）与 {@link SceneInteractionState}（input 层），
 * 若放 paint 层会破坏宪章不变量 I6「渲染层不出现 signal/组件概念」。control 层本就依赖
 * runtime/input/node/paint/reactive，放此包与同范式的 {@link SceneControlChrome} 同包同范式，
 * 不引入任何新的非法依赖方向。本类只承担「壳样式装配」，不夹 DataTable 的行/列/单元格行为核心，
 * 守 R12。</p>
 *
 * <h3>守 R1（静态工具零实例字段）</h3>
 * <p>类为 {@code public final} + {@code private} 构造器，零实例字段、零静态可变状态。
 * 唯一的成员类 {@link DataTableListboxChrome} 是浮层 chrome 装配器实例（每次 select 列渲染
 * 时按需 new，持有 {@link SceneRuntime} 引用用于注册 PAINT 绑定），不属于工具类自身的实例状态。</p>
 *
 * <h3>Boolean 解包</h3>
 * <p>对 signal 值统一用 {@code Boolean.TRUE.equals(x)} 防御性解包，与 DataTable 原编辑槽装配
 * 口径一字不差，行为零变。</p>
 *
 * @see SceneDataTable
 * @see SceneControlChrome
 */
public final class SceneDataTableEditorChrome {

    /** 单元格文字颜色（无 chrome token 对应，暂保留：嵌入式深色槽专用文本色，比 TEXT_PRIMARY 更亮，不强行统一）。 */
    private static final int TEXT_COLOR = 0xFFEAF1FF;
    /** 编辑输入槽默认底色（无 chrome token 对应，暂保留：测试断言锁定）。 */
    private static final int EDIT_SLOT_BG = 0xFF0F1A2E;
    /** 编辑输入槽 hover/聚焦底色（无 chrome token 对应，暂保留：嵌入式深色槽专用色，不强行收口）。 */
    private static final int EDIT_SLOT_BG_HOVER = 0xFF16243D;
    /** 编辑输入槽默认边框色（无 chrome token 对应，暂保留：嵌入式深色槽专用色，不强行收口）。 */
    private static final int EDIT_BORDER = 0xFF3E5575;
    /** 编辑输入槽 hover 边框色（无 chrome token 对应，暂保留：嵌入式深色槽专用色，不强行收口）。 */
    private static final int EDIT_BORDER_HOVER = 0xFF5A7299;
    /** 编辑输入槽聚焦边框色。 */
    private static final int EDIT_BORDER_FOCUS = SceneChromeTokens.BORDER_FOCUS;
    /** 编辑输入槽 caret 可见色。 */
    private static final int EDIT_CARET = SceneChromeTokens.BORDER_FOCUS;
    /** 编辑输入槽 caret 隐藏色。 */
    private static final int EDIT_CARET_HIDDEN = 0x00000000;
    /** 编辑输入槽 placeholder 文本色。 */
    private static final int EDIT_PLACEHOLDER = SceneChromeTokens.TEXT_DISABLED;
    /** Select 箭头默认色（无 chrome token 对应，暂保留：嵌入式深色槽专用色，不强行收口）。 */
    private static final int EDIT_ARROW = 0xFFAEC4E8;
    /** Select 箭头展开色。 */
    private static final int EDIT_ARROW_FOCUS = SceneChromeTokens.BORDER_FOCUS;
    /** 编辑输入槽圆角半径（无 chrome token 对应，暂保留：chip 视觉，depth-2 圆角小于 RADIUS_SM，不强行收口）。 */
    private static final int EDIT_SLOT_RADIUS = 2;
    /** 编辑输入槽边框宽度。 */
    private static final int EDIT_SLOT_BORDER_W = 1;
    /** 编辑输入槽横向内边距。 */
    private static final int EDIT_SLOT_PAD_H = 4;
    /** 下拉浮层背景色。 */
    private static final int LISTBOX_BG = SceneChromeTokens.BG_PRESSED;
    /** 下拉浮层圆角半径。 */
    private static final int LISTBOX_RADIUS = SceneChromeTokens.RADIUS_MD;
    /** 下拉浮层边框色（无 chrome token 对应，暂保留：与 EDIT_BORDER 同值，嵌入式深色槽专用色，不强行收口）。 */
    private static final int LISTBOX_BORDER = 0xFF3E5575;
    /** 下拉选中项背景色。 */
    private static final int ITEM_BG_SELECTED = SceneChromeTokens.STANDARD_SELECTED;
    /** 下拉键盘高亮项背景色（无 chrome token 对应，暂保留：视觉边界变化点，单元独立 chip 高亮，不强行收口）。 */
    private static final int ITEM_BG_HIGHLIGHTED = 0xFF3B4E68;
    /** 下拉 hover 项背景色。 */
    private static final int ITEM_BG_HOVER = SceneChromeTokens.BG_DEFAULT;
    /** 下拉默认项背景色。 */
    private static final int ITEM_BG_DEFAULT = 0x00000000;
    /** 下拉选项内边距。 */
    private static final int ITEM_PADDING = SceneChromeTokens.PAD_MD;

    /** 纯静态工具类，禁止实例化。 */
    private SceneDataTableEditorChrome() {
    }

    /**
     * 装配 DataTable TextInput 编辑槽视觉。
     *
     * <p>设置边框/圆角/内边距/高度静态属性，并注册 caret 可见性与 hover 态派生的
     * 背景色、边框色、caret 色、前后缀文本色绑定，最后绑定 TEXT/DEFAULT 光标切换。</p>
     *
     * @param rt            场景运行时
     * @param result        TextInput primitive 创建结果
     * @param contentHeight 单元格内容高度
     * @param enabled       是否启用
     */
    public static void decorateTextInputEditor(SceneRuntime rt, SceneTextInputPrimitive.Result result,
                                               int contentHeight, ReadableSignal<Boolean> enabled) {
        SceneNode root = result.root();
        root.setBorderWidth(EDIT_SLOT_BORDER_W);
        root.setCornerRadius(EDIT_SLOT_RADIUS);
        root.setPadding(0, EDIT_SLOT_PAD_H, 0, EDIT_SLOT_PAD_H);
        root.setPreferredHeight(contentHeight);

        SceneInteractionState interaction = rt.interactionState(root);
        rt.bindComputed(() -> resolveEditSlotBackground(result.caretVisible().get(), interaction.hovered().get()),
                root::setBackgroundColor);
        rt.bindComputed(() -> resolveEditBorder(result.caretVisible().get(), interaction.hovered().get()),
                root::setBorderColor);
        rt.bindComputed(() -> Boolean.TRUE.equals(result.caretVisible().get()) ? EDIT_CARET : EDIT_CARET_HIDDEN,
                result.caret()::setBackgroundColor);
        rt.bindComputed(() -> resolveEditTextColor(result.isPlaceholder().get(), enabled.get()),
                result.prefixText()::setTextColor);
        rt.bindComputed(() -> resolveEditTextColor(result.isPlaceholder().get(), enabled.get()),
                result.suffixText()::setTextColor);
        SceneControlChrome.bindCursor(rt, root, enabled, SceneCursor.TEXT, SceneCursor.DEFAULT);
    }

    /**
     * 装配 DataTable Select 编辑槽视觉。
     *
     * <p>设置 trigger 节点的边框/圆角/内边距/高度静态属性，并注册展开+聚焦态派生的
     * 背景色、边框色、箭头色绑定，按 enabled 切换标签文本色，最后绑定 POINTER/DEFAULT 光标。</p>
     *
     * @param rt            场景运行时
     * @param result        Select primitive 创建结果
     * @param contentHeight 单元格内容高度
     * @param enabled       是否启用
     */
    public static void decorateSelectEditor(SceneRuntime rt, SceneSelectPrimitive.Result result,
                                            int contentHeight, ReadableSignal<Boolean> enabled) {
        SceneNode trigger = result.trigger();
        trigger.setBorderWidth(EDIT_SLOT_BORDER_W);
        trigger.setCornerRadius(EDIT_SLOT_RADIUS);
        trigger.setPadding(0, EDIT_SLOT_PAD_H, 0, EDIT_SLOT_PAD_H);
        trigger.setPreferredHeight(contentHeight);

        SceneInteractionState interaction = rt.interactionState(trigger);
        rt.bindComputed(() -> resolveEditSlotBackground(selectFocused(result.expanded().get(), interaction.focused().get()),
                        interaction.hovered().get()),
                trigger::setBackgroundColor);
        rt.bindComputed(() -> resolveEditBorder(selectFocused(result.expanded().get(), interaction.focused().get()),
                        interaction.hovered().get()),
                trigger::setBorderColor);
        rt.bind(enabled,
                e -> result.label().setTextColor(Boolean.TRUE.equals(e) ? TEXT_COLOR : EDIT_PLACEHOLDER));
        rt.bindComputed(() -> resolveSelectArrowColor(enabled.get(), result.expanded().get()),
                result.arrow()::setTextColor);
        SceneControlChrome.bindCursor(rt, trigger, enabled, SceneCursor.POINTER, SceneCursor.DEFAULT);
    }

    /**
     * 解析编辑槽底色。
     *
     * @param focused 是否聚焦或展开
     * @param hovered 是否 hover
     * @return ARGB 底色
     */
    public static int resolveEditSlotBackground(Boolean focused, Boolean hovered) {
        if (Boolean.TRUE.equals(focused) || Boolean.TRUE.equals(hovered)) {
            return EDIT_SLOT_BG_HOVER;
        }
        return EDIT_SLOT_BG;
    }

    /**
     * 解析编辑槽边框色。
     *
     * @param focused 是否聚焦或展开
     * @param hovered 是否 hover
     * @return ARGB 边框色
     */
    public static int resolveEditBorder(Boolean focused, Boolean hovered) {
        if (Boolean.TRUE.equals(focused)) {
            return EDIT_BORDER_FOCUS;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return EDIT_BORDER_HOVER;
        }
        return EDIT_BORDER;
    }

    /**
     * 解析编辑槽文本色。
     *
     * @param placeholder 是否 placeholder
     * @param enabled     是否启用
     * @return ARGB 文本色
     */
    public static int resolveEditTextColor(Boolean placeholder, Boolean enabled) {
        if (!Boolean.TRUE.equals(enabled) || Boolean.TRUE.equals(placeholder)) {
            return EDIT_PLACEHOLDER;
        }
        return TEXT_COLOR;
    }

    /**
     * 解析 Select 箭头色。
     *
     * @param enabled  是否启用
     * @param expanded 是否展开
     * @return ARGB 文本色
     */
    public static int resolveSelectArrowColor(Boolean enabled, Boolean expanded) {
        if (!Boolean.TRUE.equals(enabled)) {
            return EDIT_PLACEHOLDER;
        }
        if (Boolean.TRUE.equals(expanded)) {
            return EDIT_ARROW_FOCUS;
        }
        return EDIT_ARROW;
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
     * 解析下拉选项背景色。
     *
     * @param selected    是否选中
     * @param highlighted 是否键盘高亮
     * @param hovered     是否 hover
     * @return ARGB 背景色
     */
    public static int resolveItemBackground(boolean selected, boolean highlighted, Boolean hovered) {
        if (selected) {
            return ITEM_BG_SELECTED;
        }
        if (highlighted) {
            return ITEM_BG_HIGHLIGHTED;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return ITEM_BG_HOVER;
        }
        return ITEM_BG_DEFAULT;
    }

    /**
     * 创建 DataTable Select 下拉浮层 chrome 装配器。
     *
     * @param rt 场景运行时
     * @return 浮层 chrome 装配器实例
     */
    public static DataTableListboxChrome createListboxChrome(SceneRuntime rt) {
        return new DataTableListboxChrome(rt);
    }

    /** DataTable Select 下拉浮层 chrome 装配器。 */
    public static final class DataTableListboxChrome implements SceneSelectPrimitive.ListboxChrome {
        /** 场景运行时，用于注册 PAINT 绑定。 */
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
            listbox.setBackgroundColor(LISTBOX_BG);
            listbox.setCornerRadius(LISTBOX_RADIUS);
            listbox.setBorderWidth(EDIT_SLOT_BORDER_W);
            listbox.setBorderColor(LISTBOX_BORDER);
        }

        @Override
        public void decorateItem(SceneSelectPrimitive.ItemHandle handle) {
            handle.item().setPadding(ITEM_PADDING);
            handle.item().setCursor(SceneCursor.POINTER);
            rt.bindComputed(() -> resolveItemBackground(
                            handle.selected().get(),
                            handle.highlighted().get(),
                            handle.interaction().hovered().get()),
                    handle.item()::setBackgroundColor);
            handle.label().setTextColor(TEXT_COLOR);
        }
    }
}
