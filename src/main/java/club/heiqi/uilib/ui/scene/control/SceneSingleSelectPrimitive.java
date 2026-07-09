package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneSingleSelectPrimitive —— 无样式 N 选 1 受控行为核心。
 *
 * <p>该 primitive 只负责单选组/分段控件共有的结构、受控选择行为、焦点注册、
 * 选项文本布局绑定与交互态暴露，不设置任何颜色、尺寸、边框、圆角、padding、cursor 或 gap。</p>
 */
public final class SceneSingleSelectPrimitive {

    /**
     * 单选项排列方向。
     */
    public enum Orientation {
        /**
         * 纵向排列，使用上下方向键导航。
         */
        VERTICAL,
        /**
         * 横向排列，使用左右方向键导航。
         */
        HORIZONTAL
    }

    /**
     * 纯静态工厂，禁止实例化。
     */
    private SceneSingleSelectPrimitive() {
    }

    /**
     * SingleSelect primitive 输入契约 —— 当前选中项由外部只读 signal 驱动，交互经 onSelect 交还期望下标。
     *
     * @param selectedIndex 当前选中项下标
     * @param options       构建期固定选项文本，构造期防御性复制为不可变列表
     * @param enabled       是否启用
     * @param onSelect      选择回调，激活选项时上抛期望下标
     * @param orientation   排列与方向键导航方向
     */
    @Desugar
    public record Props(
        ReadableSignal<Integer> selectedIndex,
        List<String> options,
        ReadableSignal<Boolean> enabled,
        Consumer<Integer> onSelect,
        Orientation orientation
    ) {
        public Props(ReadableSignal<Integer> selectedIndex,
                     List<String> options,
                     ReadableSignal<Boolean> enabled,
                     Consumer<Integer> onSelect,
                     Orientation orientation) {
            this.selectedIndex = Objects.requireNonNull(selectedIndex, "selectedIndex");
            this.options = SceneListOps.immutableCopy(Objects.requireNonNull(options, "options"));
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
            this.orientation = Objects.requireNonNull(orientation, "orientation");
        }
    }

    /**
     * item 装饰句柄，暴露单个选项结构节点与样式所需响应式状态。
     *
     * @param item        选项交互单元
     * @param label       选项文本节点
     * @param index       选项下标
     * @param selected    当前 item 是否为选中项
     * @param interaction item 交互状态
     */
    @Desugar
    public record ItemHandle(
        SceneNode item,
        SceneNode label,
        int index,
        ReadableSignal<Boolean> selected,
        SceneInteractionState interaction
    ) {
    }

    /**
     * SingleSelect primitive 创建结果，暴露无样式根节点与所有 item 句柄。
     *
     * @param root  根节点
     * @param items 选项句柄列表
     */
    @Desugar
    public record Result(
        SceneNode root,
        List<ItemHandle> items
    ) {
    }

    /**
     * 创建无样式 SingleSelect primitive。
     *
     * @param rt    场景运行时
     * @param props primitive 输入契约
     * @return 创建结果，供 wrapper 挂载样式
     */
    public static Result create(SceneRuntime rt, Props props) {
        SceneNode root = new SceneNode();
        root.setFlexDirection(props.orientation() == Orientation.VERTICAL ? FlexDirection.COLUMN : FlexDirection.ROW);

        final int count = props.options().size();
        final List<SceneNode> itemNodes = new ArrayList<>(count);
        final List<ItemHandle> items = new ArrayList<>(count);

        for (int idx = 0; idx < count; idx++) {
            final int i = idx;

            SceneNode item = new SceneNode();

            SceneNode label = new SceneNode();
            label.setHitTestable(false);
            rt.bindText(label, Computed.create(() -> props.options().get(i)));

            SceneInteractionState interactionState = rt.interactionState(item);
            ItemHandle handle = new ItemHandle(
                item,
                label,
                i,
                Computed.create(() -> Boolean.valueOf(i == normalizeIndex(props.selectedIndex().get(), props.options().size()))),
                interactionState);
            items.add(handle);

            root.appendChild(item);
            itemNodes.add(item);

            rt.focusable(item, props.enabled());
            rt.on(item, SceneEventType.CLICK, (ev, ctx) -> {
                if (Boolean.TRUE.equals(props.enabled().get())) {
                    props.onSelect().accept(Integer.valueOf(i));
                }
            });
            rt.on(item, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                if (!Boolean.TRUE.equals(props.enabled().get())) {
                    return;
                }
                handleKeyDown(rt, ev.getKey(), props, i, itemNodes);
            });
        }

        return new Result(root, Collections.unmodifiableList(items));
    }

    /**
     * 处理单个 item 的键盘事件。
     *
     * @param key       当前按键
     * @param props     primitive 输入契约
     * @param index     当前 item 下标
     * @param itemNodes 全部 item 节点
     */
    private static void handleKeyDown(SceneRuntime rt, SceneKey key, Props props, int index, List<SceneNode> itemNodes) {
        if (key == SceneKey.ENTER || key == SceneKey.SPACE) {
            props.onSelect().accept(Integer.valueOf(index));
            return;
        }

        int size = props.options().size();
        int next = -1;
        boolean handled = false;
        if (props.orientation() == Orientation.VERTICAL) {
            if (key == SceneKey.ARROW_UP) {
                next = normalizeIndex(props.selectedIndex().get(), size) - 1;
                handled = true;
            } else if (key == SceneKey.ARROW_DOWN) {
                next = normalizeIndex(props.selectedIndex().get(), size) + 1;
                handled = true;
            }
        } else {
            if (key == SceneKey.ARROW_LEFT) {
                next = normalizeIndex(props.selectedIndex().get(), size) - 1;
                handled = true;
            } else if (key == SceneKey.ARROW_RIGHT) {
                next = normalizeIndex(props.selectedIndex().get(), size) + 1;
                handled = true;
            }
        }
        if (key == SceneKey.HOME) {
            next = 0;
            handled = true;
        } else if (key == SceneKey.END) {
            next = size - 1;
            handled = true;
        }
        if (!handled) {
            return;
        }
        if (next < 0) {
            next = 0;
        } else if (next > size - 1) {
            next = size - 1;
        }
        if (next >= 0 && next < size) {
            props.onSelect().accept(Integer.valueOf(next));
            rt.requestFocus(itemNodes.get(next));
        }
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
        if (raw < 0) {
            return 0;
        }
        if (raw >= size) {
            return size - 1;
        }
        return raw;
    }
}
