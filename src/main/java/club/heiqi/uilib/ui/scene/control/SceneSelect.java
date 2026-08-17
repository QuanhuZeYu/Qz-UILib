package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneSelect —— scene 新栈锚定浮层选择控件。
 *
 * <p>trigger 常驻主树，listbox 通过 {@link SceneRuntime#portalAnchored} 提升为 overlay root。
 * 选中值由外部 {@code selectedIndex} 唯一驱动；展开、收起、键盘高亮和滚动均以本地 signal 表达，
 * handler 只写 signal 或调用 {@code onSelect} 上抛期望下标，浮层显隐由 signal→portal 派生。</p>
 */
public final class SceneSelect {

    /** trigger 边框宽度（像素）。 */
    private static final int TRIGGER_BORDER_WIDTH = 1;
    /** listbox 边框宽度（像素）。 */
    private static final int LISTBOX_BORDER_WIDTH = 1;
    /**
     * trigger 内边距
     */
    private static final int TRIGGER_PADDING = SceneChromeTokens.PAD_MD;
    /**
     * item 内边距
     */
    private static final int ITEM_PADDING = SceneChromeTokens.PAD_MD;
    /**
     * trigger label 与箭头间距
     */
    private static final int TRIGGER_GAP = SceneChromeTokens.GAP_MD;
    /**
     * 圆角
     */
    private static final int TRIGGER_RADIUS = SceneChromeTokens.RADIUS_MD;
    /** listbox 圆角。 */
    private static final int LISTBOX_RADIUS = SceneChromeTokens.RADIUS_LG;

    /**
     * 纯静态工厂，禁止实例化。
     */
    private SceneSelect() {
    }

    /**
     * Select 输入契约 —— 多选项单选受控，浮层显隐由控件内部 signal 派生。
     *
     * @param selectedIndex 当前选中项下标，控件绝不修改
     * @param options       构建期固定选项文本，构造期防御性复制为不可变列表
     * @param enabled       是否启用
     * @param onSelect      选择回调，激活选项时上抛期望下标
     */
    @Desugar
    public record Props(
            ReadableSignal<Integer> selectedIndex,
            List<String> options,
            ReadableSignal<Boolean> enabled,
            Consumer<Integer> onSelect
    ) {
        public Props(ReadableSignal<Integer> selectedIndex,
                     List<String> options,
                     ReadableSignal<Boolean> enabled,
                     Consumer<Integer> onSelect) {
            this.selectedIndex = Objects.requireNonNull(selectedIndex, "selectedIndex");
            this.options = SceneListOps.immutableCopy(Objects.requireNonNull(options, "options"));
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
        }
    }

    /**
     * 工厂：构建 Select 组件函数。
     *
     * @param rt    场景运行时
     * @param props Select 输入契约
     * @return 组件函数，交 {@link SceneRuntime#mount} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSelectPrimitive.ListboxChrome chrome = new SceneSelectChrome(rt);
            SceneSelectPrimitive.Props primitiveProps = new SceneSelectPrimitive.Props(
                    props.selectedIndex(), props.options(), props.enabled(), props.onSelect(), chrome);
            SceneSelectPrimitive.Result result = SceneSelectPrimitive.create(rt, primitiveProps);

            SceneNode trigger = result.trigger();
            trigger.setPadding(TRIGGER_PADDING);
            trigger.setBorderWidth(TRIGGER_BORDER_WIDTH);
            trigger.setCornerRadius(TRIGGER_RADIUS);
            trigger.setCursor(SceneCursor.POINTER);

            SceneNode label = result.label();
            rt.bindComputed(() -> SceneStateColors.standardText(Boolean.TRUE.equals(props.enabled().get()), false),
                    label::setTextColor);

            SceneNode arrow = result.arrow();
            rt.bindComputed(() -> SceneStateColors.standardText(Boolean.TRUE.equals(props.enabled().get()), false),
                    arrow::setTextColor);

            SceneInteractionState is = rt.interactionState(trigger);
            rt.__bindAnimatedColor(() -> resolveTriggerBackground(
                            props.enabled().get(), is.pressed().get(), is.hovered().get()),
                    trigger::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
            SceneControlChrome.bindStandardBorder(rt, trigger, props.enabled(), is);
            SceneControlChrome.bindCursor(rt, trigger, props.enabled(), SceneCursor.POINTER, SceneCursor.DEFAULT);

            return trigger;
        };
    }

    /**
     * 解析 trigger 背景色。
     *
     * @param enabled 是否启用
     * @param pressed 是否按压
     * @param hovered 是否悬停
     * @return ARGB 背景色
     */
    private static int resolveTriggerBackground(Boolean enabled, Boolean pressed, Boolean hovered) {
        return SceneStateColors.standardBackground(
                Boolean.TRUE.equals(enabled), Boolean.TRUE.equals(hovered), Boolean.TRUE.equals(pressed));
    }

    /**
     * 解析 item 背景色，走 {@link SceneStateColors#listItemBackground} 查表，与其余控件口径一致。
     *
     * <p>三态语义：selected-only 保持透明，selected+hovered / selected+highlighted 走 ACCENT 变体，
     * 未选中 highlighted / hovered 走 Slate 提亮通道，default=透明。优先级由查表方法统一收口。</p>
     *
     * @param selected    是否选中
     * @param highlighted 是否键盘高亮
     * @param hovered     是否悬停
     * @return ARGB 背景色 token
     */
    private static int resolveItemBackground(boolean selected, boolean highlighted, Boolean hovered) {
        return SceneStateColors.listItemBackground(
                true, selected, highlighted, Boolean.TRUE.equals(hovered));
    }

    /**
     * 解析 item 文本色，确保只有 ACCENT 背景上的文本使用 on-accent 白字。
     *
     * @param selected    是否选中
     * @param highlighted 是否键盘高亮
     * @param hovered     是否悬停
     * @return ARGB 文本色 token
     */
    private static int resolveItemText(boolean selected, boolean highlighted, Boolean hovered) {
        return SceneStateColors.listItemText(
                true, selected, highlighted, Boolean.TRUE.equals(hovered));
    }

    /** SceneSelect 默认 listbox chrome 装配器。 */
    private static final class SceneSelectChrome implements SceneSelectPrimitive.ListboxChrome {
        /** 场景运行时，用于注册 PAINT 绑定。 */
        private final SceneRuntime rt;

        /**
         * 创建默认 listbox chrome 装配器。
         *
         * @param rt 场景运行时
         */
        private SceneSelectChrome(SceneRuntime rt) {
            this.rt = rt;
        }

        @Override
        public void decorateListbox(SceneNode listbox) {
            listbox.setBackgroundColor(SceneStateColors.inputBackground(true));
            listbox.setBorderWidth(LISTBOX_BORDER_WIDTH);
            listbox.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
            listbox.setCornerRadius(LISTBOX_RADIUS);
        }

        @Override
        public void decorateItem(SceneSelectPrimitive.ItemHandle handle) {
            handle.item().setPadding(ITEM_PADDING);
            handle.item().setCursor(SceneCursor.POINTER);
            rt.__bindAnimatedColor(() -> resolveItemBackground(
                            handle.selected().get(),
                            handle.highlighted().get(),
                            handle.interaction().hovered().get()),
                    handle.item()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
            rt.bindComputed(() -> resolveItemText(
                            handle.selected().get(),
                            handle.highlighted().get(),
                            handle.interaction().hovered().get()),
                    handle.label()::setTextColor);
        }
    }
}
