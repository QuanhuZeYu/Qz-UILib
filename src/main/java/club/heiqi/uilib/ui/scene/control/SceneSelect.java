package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;

/**
 * SceneSelect —— scene 新栈锚定浮层选择控件。
 *
 * <p>trigger 常驻主树，listbox 通过 {@link SceneRuntime#portalAnchored} 提升为 overlay root。
 * 选中值由外部 {@code selectedIndex} 唯一驱动；展开、收起、键盘高亮和滚动均以本地 signal 表达，
 * handler 只写 signal 或调用 {@code onSelect} 上抛期望下标，浮层显隐由 signal→portal 派生。</p>
 */
public final class SceneSelect {

    /**
     * trigger 默认背景色
     */
    private static final int TRIGGER_BG = 0xFF3A3A3A;
    /**
     * trigger hover 背景色
     */
    private static final int TRIGGER_BG_HOVER = 0xFF505050;
    /**
     * trigger pressed 背景色
     */
    private static final int TRIGGER_BG_PRESSED = 0xFF2A2A2A;
    /**
     * trigger disabled 背景色
     */
    private static final int TRIGGER_BG_DISABLED = 0xFF2F2F2F;
    /**
     * listbox 背景色
     */
    private static final int LISTBOX_BG = 0xFF1E293B;
    /**
     * item 默认背景色
     */
    private static final int ITEM_BG = 0x00000000;
    /**
     * item hover 背景色
     */
    private static final int ITEM_BG_HOVER = 0xFF334155;
    /**
     * item 高亮背景色
     */
    private static final int ITEM_BG_HIGHLIGHTED = 0xFF3B4E68;
    /**
     * item 选中背景色
     */
    private static final int ITEM_BG_SELECTED = 0xFF4A90D9;
    /**
     * 文本颜色
     */
    private static final int TEXT_ENABLED = 0xFFFFFFFF;
    /**
     * disabled 文本颜色
     */
    private static final int TEXT_DISABLED = 0xFF888888;
    /**
     * trigger 内边距
     */
    private static final int TRIGGER_PADDING = 6;
    /**
     * item 内边距
     */
    private static final int ITEM_PADDING = 6;
    /**
     * trigger label 与箭头间距
     */
    private static final int TRIGGER_GAP = 8;
    /**
     * 圆角
     */
    private static final int RADIUS = 4;

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
            this.options = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(options, "options")));
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
            Signal<Boolean> expanded = Signal.create(Boolean.FALSE);
            Signal<Integer> highlightedIndex = Signal.create(normalizeIndex(props.selectedIndex().get(), props.options().size()));

            SceneNode trigger = new SceneNode();
            trigger.setFlexDirection(FlexDirection.ROW);
            trigger.setCrossAxisAlign(CrossAxisAlign.CENTER);
            trigger.setGap(TRIGGER_GAP);
            trigger.setPadding(TRIGGER_PADDING);
            trigger.setCornerRadius(RADIUS);
            trigger.setWidthSizing(WidthSizing.SHRINK);
            trigger.setCursor(SceneCursor.POINTER);

            SceneNode label = new SceneNode();
            label.setHitTestable(false);
            rt.bindText(label, Computed.create(() -> selectedText(props)));
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> label.setTextColor(Boolean.TRUE.equals(e) ? TEXT_ENABLED : TEXT_DISABLED));

            SceneNode arrow = new SceneNode();
            arrow.setHitTestable(false);
            rt.bindText(arrow, Computed.create(() -> Boolean.TRUE.equals(expanded.get()) ? "▲" : "▼"));
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> arrow.setTextColor(Boolean.TRUE.equals(e) ? TEXT_ENABLED : TEXT_DISABLED));

            trigger.appendChild(label);
            trigger.appendChild(arrow);

            SceneInteractionState is = rt.interactionState(trigger);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveTriggerBackground(
                            props.enabled().get(), is.pressed().get(), is.hovered().get())),
                    trigger::setBackgroundColor);
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> trigger.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.DEFAULT));

            rt.on(trigger, SceneEventType.CLICK, (ev, ctx) -> {
                if (!Boolean.TRUE.equals(props.enabled().get())) {
                    return;
                }
                boolean next = !Boolean.TRUE.equals(expanded.get());
                expanded.set(Boolean.valueOf(next));
                if (next) {
                    highlightedIndex.set(Integer.valueOf(normalizeIndex(props.selectedIndex().get(), props.options().size())));
                }
                ctx.stopPropagation();
            });

            rt.focusable(trigger);
            rt.on(trigger, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                if (!Boolean.TRUE.equals(props.enabled().get())) {
                    return;
                }
                handleKeyDown(ev.getKey(), props, expanded, highlightedIndex, ctx::stopPropagation);
            });

            AnchorProvider anchorProvider = () -> SceneGeometry.absoluteBox(trigger, 0, 0);
            rt.portalAnchored(
                    expanded,
                    () -> buildListbox(rt, props, expanded, highlightedIndex),
                    OverlayDismissPolicy.DEFAULT,
                    () -> expanded.set(Boolean.FALSE),
                    anchorProvider);

            return trigger;
        };
    }

    /**
     * 构建 listbox overlay root。
     *
     * @param rt               场景运行时
     * @param props            Select 输入契约
     * @param expanded         展开态 signal
     * @param highlightedIndex 键盘高亮下标 signal
     * @return listbox 根节点
     */
    private static SceneNode buildListbox(SceneRuntime rt, Props props,
                                          Signal<Boolean> expanded,
                                          Signal<Integer> highlightedIndex) {
        SceneNode listbox = new SceneNode();
        listbox.setFlexDirection(FlexDirection.COLUMN);
        listbox.setWidthSizing(WidthSizing.SHRINK);
        listbox.setScrollable(true);
        listbox.setClipChildren(true);
        listbox.setBackgroundColor(LISTBOX_BG);
        listbox.setCornerRadius(RADIUS);

        Signal<Integer> scrollSignal = Signal.create(Integer.valueOf(0));
        rt.bind(Invalidation.COMPOSITE, scrollSignal, v -> listbox.setScrollOffsetY(v.intValue()));
        rt.on(listbox, SceneEventType.SCROLL, (ev, ctx) -> {
            LayoutBox box = (LayoutBox) listbox.getCachedLayout();
            if (box == null) {
                return;
            }
            int maxScrollY = maxScrollY(listbox, box.getHeight());
            int next = scrollSignal.get().intValue() - ev.getWheelDelta();
            scrollSignal.set(Integer.valueOf(clamp(next, 0, maxScrollY)));
            ctx.stopPropagation();
        });

        for (int idx = 0; idx < props.options().size(); idx++) {
            final int i = idx;
            SceneNode item = new SceneNode();
            item.setFlexDirection(FlexDirection.ROW);
            item.setPadding(ITEM_PADDING);
            item.setCursor(SceneCursor.POINTER);

            SceneInteractionState itemState = rt.interactionState(item);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveItemBackground(
                            i == normalizeIndex(props.selectedIndex().get(), props.options().size()),
                            i == normalizeIndex(highlightedIndex.get(), props.options().size()),
                            itemState.hovered().get())),
                    item::setBackgroundColor);

            SceneNode itemLabel = new SceneNode();
            itemLabel.setHitTestable(false);
            itemLabel.setText(props.options().get(i));
            itemLabel.setTextColor(TEXT_ENABLED);
            item.appendChild(itemLabel);

            rt.on(item, SceneEventType.CLICK, (ev, ctx) -> {
                props.onSelect().accept(Integer.valueOf(i));
                expanded.set(Boolean.FALSE);
                ctx.stopPropagation();
            });
            listbox.appendChild(item);
        }
        return listbox;
    }

    /**
     * 处理 trigger 键盘事件。
     *
     * @param key              当前按键
     * @param props            Select 输入契约
     * @param expanded         展开态 signal
     * @param highlightedIndex 高亮下标 signal
     * @param stopPropagation  停止传播命令
     */
    private static void handleKeyDown(SceneKey key, Props props,
                                      Signal<Boolean> expanded,
                                      Signal<Integer> highlightedIndex,
                                      Runnable stopPropagation) {
        boolean open = Boolean.TRUE.equals(expanded.get());
        int size = props.options().size();
        if (key == SceneKey.ARROW_DOWN) {
            if (!open) {
                expanded.set(Boolean.TRUE);
                highlightedIndex.set(Integer.valueOf(normalizeIndex(props.selectedIndex().get(), size)));
            } else {
                highlightedIndex.set(Integer.valueOf(clamp(highlightedIndex.get().intValue() + 1, 0, size - 1)));
            }
            stopPropagation.run();
        } else if (key == SceneKey.ARROW_UP) {
            if (!open) {
                expanded.set(Boolean.TRUE);
                highlightedIndex.set(Integer.valueOf(normalizeIndex(props.selectedIndex().get(), size)));
            } else {
                highlightedIndex.set(Integer.valueOf(clamp(highlightedIndex.get().intValue() - 1, 0, size - 1)));
            }
            stopPropagation.run();
        } else if (key == SceneKey.ENTER || key == SceneKey.SPACE) {
            if (open) {
                props.onSelect().accept(Integer.valueOf(normalizeIndex(highlightedIndex.get(), size)));
                expanded.set(Boolean.FALSE);
            } else {
                expanded.set(Boolean.TRUE);
                highlightedIndex.set(Integer.valueOf(normalizeIndex(props.selectedIndex().get(), size)));
            }
            stopPropagation.run();
        } else if (key == SceneKey.ESCAPE && open) {
            expanded.set(Boolean.FALSE);
            stopPropagation.run();
        }
    }

    /**
     * 读取当前选中文本。
     *
     * @param props Select 输入契约
     * @return 当前选中文本，越界时为空串
     */
    private static String selectedText(Props props) {
        Integer idxObj = props.selectedIndex().get();
        if (idxObj == null) {
            return "";
        }
        int idx = idxObj.intValue();
        if (idx < 0 || idx >= props.options().size()) {
            return "";
        }
        return props.options().get(idx);
    }

    /**
     * 归一化下标到合法选项范围。
     *
     * @param value 输入下标
     * @param size  选项数量
     * @return 合法下标；无选项时返回 0
     */
    private static int normalizeIndex(Integer value, int size) {
        if (size <= 0) {
            return 0;
        }
        int raw = value == null ? 0 : value.intValue();
        return clamp(raw, 0, size - 1);
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
        if (!Boolean.TRUE.equals(enabled)) {
            return TRIGGER_BG_DISABLED;
        }
        if (Boolean.TRUE.equals(pressed)) {
            return TRIGGER_BG_PRESSED;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return TRIGGER_BG_HOVER;
        }
        return TRIGGER_BG;
    }

    /**
     * 解析 item 背景色。
     *
     * @param selected    是否选中
     * @param highlighted 是否键盘高亮
     * @param hovered     是否悬停
     * @return ARGB 背景色
     */
    private static int resolveItemBackground(boolean selected, boolean highlighted, Boolean hovered) {
        if (selected) {
            return ITEM_BG_SELECTED;
        }
        if (highlighted) {
            return ITEM_BG_HIGHLIGHTED;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return ITEM_BG_HOVER;
        }
        return ITEM_BG;
    }

    /**
     * 计算 listbox 当前最大滚动距离。
     *
     * @param listbox        listbox 节点
     * @param viewportHeight 视口高度
     * @return 最大 Y 滚动值
     */
    private static int maxScrollY(SceneNode listbox, int viewportHeight) {
        int contentHeight = 0;
        for (SceneNode child : listbox.__getChildren()) {
            LayoutBox childBox = (LayoutBox) child.getCachedLayout();
            if (childBox != null) {
                contentHeight = Math.max(contentHeight, childBox.getY() + childBox.getHeight());
            }
        }
        return Math.max(0, contentHeight - viewportHeight);
    }

    /**
     * 将值裁剪到闭区间。
     *
     * @param value 输入值
     * @param min   最小值
     * @param max   最大值
     * @return 裁剪后的值
     */
    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
