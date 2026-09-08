package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.api.chat.ChatAction;
import club.heiqi.uilib.api.chat.ChatActionService;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.hud.api.HudToolbarSide;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.control.SceneButtonPrimitive;
import club.heiqi.uilib.ui.scene.control.SceneButtonVariant;
import club.heiqi.uilib.ui.scene.control.SceneLiquidGlassStyle;
import club.heiqi.uilib.ui.scene.control.SceneTooltip;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天工具栏内容组件（L3 组件层）：
 * 一行紧凑按钮，普通态渲染 {@link ChatActionService} 注册的动作，编辑态切换为
 * 「完成 / 取消 / 恢复当前默认 / 恢复全部默认」。
 *
 * <p><b>挂载位置不属于本组件</b>：工具栏不插在聊天容器内部，
 * 而是由 HUD 级 {@link club.heiqi.uilib.ui.hud.api.HudToolbarService} 为
 * {@code qzuilib:chat3} 注册规格与工厂，{@link club.heiqi.uilib.ui.hud.api.HudToolbarLayer}
 * 把它挂在聊天内容盒外侧一条边（默认下边），厚度与间隙参与外框测量/放置。本类只负责
 * 这一组按钮的内容与行为。</p>
 *
 * <p><b>形态随挂载边</b>：{@link HudToolbarSide#isHorizontalEdge()} 决定根容器方向——
 * 水平边（TOP/BOTTOM）是 ROW 单行，竖直边（LEFT/RIGHT）是 COLUMN 竖列；沿边方向的厚度由
 * 规格钉死，交叉轴由内容/外接层决定。四边共用方形图标按钮，名称与说明由悬停提示展示。</p>
 *
 * <h3>为什么不用 rt.show 切编辑态/普通态</h3>
 * <p>{@code SceneRuntime.show} 用零尺寸 anchor 占位，但那个 anchor 只是「零高」——
 * 无文本叶的宽度仍取父约束宽（{@link club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing}
 * 对无子叶不生效）。放在 ROW 里就是吃满主轴宽，把后面的内容推到视口外（真机现象：整条
 * 工具栏不可见）。故本类改走 {@link SceneRuntime#forEach} 单一 keyed 动作列表：编辑态/普通态
 * 由同一个列表信号切换，容器子节点全由协调器管理，天然无占位锚点。</p>
 *
 * <p>全部经 {@link SceneButtonPrimitive} + {@link Signal} + keyed list 渲染；动作只发布语义
 * （{@link ChatAction#run()}），执行失败仅影响当前动作。隐藏动作不占位、禁用动作仍显示。</p>
 */
public final class ChatToolbar {

    private static final Logger LOG = LogManager.getLogger("QzUILib Chat3Toolbar");
    /** 工具栏四周内边距（与输入条区同源，保证左右对齐）。 */
    private static final int PADDING_X = 8;
    /** 图标命中盒与内容盒固定，异步图片就绪不触发布局跳动。 */
    private static final int BUTTON_SIZE_PX = 24;
    private static final int ICON_SIZE_PX = 16;
    /**
     * 水平边工具栏固定行高 = HUD 外接工具栏规格的默认厚度（唯一数值来源，避免两处 28 漂移）。
     *
     * <p>必须给外框一个可先验的厚度：宿主/打开态页面在 layout 之前就要算外框高度来
     * placement，工具栏不能等一帧实测。首版单行紧凑工具栏；多行/溢出菜单留待后续。</p>
     */
    private static final int TOOLBAR_HEIGHT_PX = HudToolbarSpec.DEFAULT_THICKNESS_PX;
    /** 竖直边与水平边共用图标条厚度，标签长度不再影响条宽。 */
    public static final int VERTICAL_THICKNESS_PX = TOOLBAR_HEIGHT_PX;
    /** 编辑态「完成 / 取消」恒可用（语义与行为一致，不随草稿状态变化）。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = Signal.create(Boolean.TRUE);

    /** 工具栏宿主端口：编辑态信号 + 编辑动作 + 重置可用性。 */
    public interface Host {
        /** @return 是否处于 HUD 编辑子模式 */
        ReadableSignal<Boolean> editing();
        /** @return 「恢复当前默认」是否可用 */
        ReadableSignal<Boolean> canResetCurrent();
        /** @return 「恢复全部默认」是否可用 */
        ReadableSignal<Boolean> canResetAll();
        /** 完成编辑并提交会话。 */
        void finishEdit();
        /** 取消编辑并丢弃会话。 */
        void cancelEdit();
        /** 恢复当前项默认（属于草稿）。 */
        void resetCurrent();
        /** 恢复全部默认（属于草稿）。 */
        void resetAll();
    }

    /** 工具栏项（普通态动作 / 编辑态命令共用一个 keyed 列表，key 决定节点复用）。 */
    private static final class Item {
        final String key;
        final String tooltip;
        final String icon;
        final ReadableSignal<Boolean> enabled;
        final Runnable onClick;
        final SceneButtonVariant variant;

        Item(String key, String tooltip, String icon, ReadableSignal<Boolean> enabled, Runnable onClick,
                SceneButtonVariant variant) {
            this.key = key;
            this.tooltip = tooltip;
            this.icon = icon;
            this.enabled = enabled;
            this.onClick = onClick;
            this.variant = variant;
        }
    }

    private ChatToolbar() {
    }

    /**
     * 惰性宿主（恒非编辑态、编辑动作为空操作）：供不接入编辑子模式的调用方与 headless 测试使用。
     *
     * @return 惰性宿主
     */
    public static Host inertHost() {
        final ReadableSignal<Boolean> never = Signal.create(Boolean.FALSE);
        return new Host() {
            @Override public ReadableSignal<Boolean> editing() { return never; }
            @Override public ReadableSignal<Boolean> canResetCurrent() { return never; }
            @Override public ReadableSignal<Boolean> canResetAll() { return never; }
            @Override public void finishEdit() { }
            @Override public void cancelEdit() { }
            @Override public void resetCurrent() { }
            @Override public void resetAll() { }
        };
    }

    /**
     * 按默认挂载边（{@link HudToolbarSide#DEFAULT}）装配工具栏（便捷重载）。
     *
     * @param rt   宿主运行时
     * @param host 工具栏宿主端口
     * @return 工具栏根（由外接层挂到内容盒外侧）
     */
    public static SceneNode mount(SceneRuntime rt, final Host host) {
        return mount(rt, host, HudToolbarSide.DEFAULT);
    }

    /**
     * 按挂载边装配工具栏（普通态动作 / 编辑态命令共用一个 keyed 列表）。
     *
     * @param rt   宿主运行时
     * @param host 工具栏宿主端口
     * @param side 挂载边（null 视作默认边）；决定根容器方向与按钮收缩策略
     * @return 工具栏根（由外接层挂到内容盒外侧）
     */
    public static SceneNode mount(SceneRuntime rt, final Host host, HudToolbarSide side) {
        final HudToolbarSide effective = side == null ? HudToolbarSide.DEFAULT : side;
        final boolean horizontal = effective.isHorizontalEdge();
        SceneNode root = horizontal ? SceneNode.row() : SceneNode.column();
        boolean glass = ChatMarkdownSettings.isGlassEnabled();
        root.setHitTestable(true).setGap(4)
                .setCrossAxisAlign(CrossAxisAlign.CENTER)
                .setBorderWidth(1)
                .setBorderColor(ChatMarkdownSettings.getContainerBorderArgb())
                .setCornerRadius(ChatMarkdownSettings.getContainerCornerRadius())
                .setBackdrop(glass ? UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN,
                        ChatMarkdownSettings.getGlassBlurRadiusPx(),
                        ChatMarkdownSettings.getGlassLensStrength()) : null)
                .setBackgroundColor(glass
                        ? (ChatMarkdownSettings.getContainerBgArgb() & 0x00FFFFFF)
                                | (ChatMarkdownSettings.getGlassContainerAlpha() << 24)
                        : ChatMarkdownSettings.getContainerBgArgb());
        if (horizontal) {
            root.setPreferredHeight(TOOLBAR_HEIGHT_PX).setPadding(1, PADDING_X, 1, PADDING_X);
        } else {
            root.setPreferredWidth(VERTICAL_THICKNESS_PX).setPadding(PADDING_X, 1, PADDING_X, 1);
        }
        // 单一 keyed 列表：编辑态/普通态是同一个列表信号的两个分支（无 show 占位锚点）。
        ReadableSignal<List<Item>> items = Computed.create(() -> buildItems(host));
        if (horizontal) {
            // 方形按钮有确定尺寸，直接从动作列表声明条宽。右锚点首帧给出的约束可能很窄，
            // 若只用 SHRINK，固定宽子项不会随约束变化，条宽会继续复用被夹窄的旧布局盒。
            rt.bindComputed(() -> {
                int count = items.get().size();
                return count * BUTTON_SIZE_PX + Math.max(0, count - 1) * root.getGap()
                        + root.getPaddingLeft() + root.getPaddingRight();
            }, root::setPreferredWidth);
        }
        rt.forEach(root, items, item -> item.key, item -> buildButton(rt, item, root.getBackdrop()));
        return root;
    }

    /** 当前应显示的工具栏项（编辑态 = 完成/取消/重置；普通态 = 可见动作按 order/注册序）。 */
    private static List<Item> buildItems(Host host) {
        List<Item> items = new ArrayList<Item>();
        if (Boolean.TRUE.equals(host.editing().get())) {
            items.add(new Item("edit:finish", "完成", "finish", ALWAYS_ENABLED, host::finishEdit,
                    SceneButtonVariant.PRIMARY));
            items.add(new Item("edit:cancel", "取消", "cancel", ALWAYS_ENABLED, host::cancelEdit,
                    SceneButtonVariant.STANDARD));
            items.add(new Item("edit:reset-current", "恢复当前默认", "reset-current", host.canResetCurrent(),
                    host::resetCurrent, SceneButtonVariant.STANDARD));
            items.add(new Item("edit:reset-all", "恢复全部默认", "reset-all", host.canResetAll(),
                    host::resetAll, SceneButtonVariant.STANDARD));
            return items;
        }
        ChatActionService service = ChatActionService.getInstance();
        service.revision().get();
        for (ChatAction action : service.actions()) {
            if (Boolean.TRUE.equals(action.getVisible().get())) {
                String detail = action.getTooltip();
                String tooltip = detail == null || detail.trim().isEmpty() || detail.equals(action.getLabel())
                        ? action.getLabel() : action.getLabel() + "\n" + detail;
                String icon = ChatHudEditIntent.ACTION_ID.equals(action.getId()) ? "edit" : "action";
                items.add(new Item("action:" + action.getId(), tooltip, icon, action.getEnabled(),
                        () -> runAction(action), SceneButtonVariant.STANDARD));
            }
        }
        return items;
    }

    /** 复用按钮交互原语；玻璃外观统一交给专属样式，内容与动作留在工具栏。 */
    private static SceneNode buildButton(SceneRuntime rt, Item item, UiBackdrop backdrop) {
        SceneButtonPrimitive.Result primitive = SceneButtonPrimitive.create(rt,
                new SceneButtonPrimitive.Props(Signal.create(""), item.enabled, item.onClick));
        SceneNode button = primitive.root();
        button.removeChild(primitive.label());
        button.setPreferredWidth(BUTTON_SIZE_PX).setPreferredHeight(BUTTON_SIZE_PX)
                .setWidthSizing(SceneNode.WidthSizing.SHRINK)
                .setPadding(3);
        SceneNode icon = new SceneNode().setHitTestable(false)
                .setPreferredWidth(ICON_SIZE_PX).setPreferredHeight(ICON_SIZE_PX);
        button.appendChild(icon);
        ChatToolbarIcons.attach(rt, icon, item.icon);
        SceneLiquidGlassStyle.bindButton(rt, button, icon, item.enabled, item.variant, backdrop);
        // 禁用项仍显示说明；点击/键盘激活由 primitive 的 enabled 信号约束。
        SceneTooltip.attach(rt, SceneTooltip.Props.of(button, Signal.create(item.tooltip)));
        return button;
    }

    /** 动作异常隔离：单动作失败不影响工具栏与聊天输入。 */
    private static void runAction(ChatAction action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            LOG.warn("聊天工具栏动作执行失败: id={}", action.getId(), failure);
        }
    }
}