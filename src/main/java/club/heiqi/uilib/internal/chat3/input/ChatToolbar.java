package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.api.chat.ChatAction;
import club.heiqi.uilib.api.chat.ChatActionService;
import club.heiqi.uilib.ui.hud.api.HudToolbarSide;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneButtonVariant;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天工具栏内容组件（L3 组件层，规划《聊天工具栏与HUD布局编辑》P1/P2）：
 * 一行紧凑按钮，普通态渲染 {@link ChatActionService} 注册的动作，编辑态切换为
 * 「完成 / 取消 / 恢复当前默认 / 恢复全部默认」。
 *
 * <p><b>挂载位置不属于本组件</b>：自 P1/P2 增量起，工具栏不再插在聊天容器内部，
 * 而是由 HUD 级 {@link club.heiqi.uilib.ui.hud.api.HudToolbarService} 为
 * {@code qzuilib:chat3} 注册规格与工厂，{@link club.heiqi.uilib.ui.hud.api.HudToolbarLayer}
 * 把它挂在聊天内容盒外侧一条边（默认下边），厚度与间隙参与外框测量/放置。本类只负责
 * 这一组按钮的内容与行为。</p>
 *
 * <p><b>形态随挂载边</b>：{@link HudToolbarSide#isHorizontalEdge()} 决定根容器方向——
 * 水平边（TOP/BOTTOM）是 ROW 单行，竖直边（LEFT/RIGHT）是 COLUMN 竖列；沿边方向的厚度由
 * 规格钉死，交叉轴由内容/外接层决定。文本按钮在竖直边下必须给足条宽，否则标签被裁。</p>
 *
 * <h3>为什么不用 rt.show 切编辑态/普通态</h3>
 * <p>{@code SceneRuntime.show} 用零尺寸 anchor 占位，但那个 anchor 只是「零高」——
 * 无文本叶的宽度仍取父约束宽（{@link club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing}
 * 对无子叶不生效）。放在 ROW 里就是吃满主轴宽，把后面的内容推到视口外（真机现象：整条
 * 工具栏不可见）。故本类改走 {@link SceneRuntime#forEach} 单一 keyed 动作列表：编辑态/普通态
 * 由同一个列表信号切换，容器子节点全由协调器管理，天然无占位锚点。</p>
 *
 * <p>全部经 {@link SceneButton} + {@link Signal} + keyed list 渲染；动作只发布语义
 * （{@link ChatAction#run()}），执行失败仅影响当前动作。隐藏动作不占位、禁用动作仍显示。</p>
 */
public final class ChatToolbar {

    private static final Logger LOG = LogManager.getLogger("QzUILib Chat3Toolbar");
    /** 工具栏四周内边距（与输入条区同源，保证左右对齐）。 */
    private static final int PADDING_X = 8;
    /** 紧凑按钮内边距（覆盖 SceneButton 默认 PAD_MD，避免工具栏过高）。 */
    private static final int BUTTON_PAD_Y = 2;
    private static final int BUTTON_PAD_X = 8;
    /**
     * 水平边工具栏固定行高 = HUD 外接工具栏规格的默认厚度（唯一数值来源，避免两处 28 漂移）。
     *
     * <p>必须给外框一个可先验的厚度：宿主/打开态页面在 layout 之前就要算外框高度来
     * placement，工具栏不能等一帧实测。首版单行紧凑工具栏；多行/溢出菜单留待后续。</p>
     */
    private static final int TOOLBAR_HEIGHT_PX = HudToolbarSpec.DEFAULT_THICKNESS_PX;
    /**
     * 竖直边工具栏条宽（LEFT/RIGHT；规格 thickness 用它）。
     *
     * <p>竖直边下按钮竖排、文字仍是横排，28 只能放图标，放文本必裁，故竖直边改用此条宽。
     * 取值按内置最长标签「恢复全部默认」在默认字号（16px）全角字宽 + 按钮内边距与描边估算，
     * 由 {@code ChatToolbarGeometryTest} 钉死内置标签落在条宽内。<b>第三方注册的超长标签
     * 仍可能被按钮裁剪</b>（按钮 {@code clipChildren}）——这是固定条宽预算的已知边界，
     * 需要自适应条宽时应改为按标签实测宽动态声明。</p>
     */
    public static final int VERTICAL_THICKNESS_PX = 128;
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
        final String label;
        final ReadableSignal<Boolean> enabled;
        final Runnable onClick;
        final SceneButtonVariant variant;

        Item(String key, String label, ReadableSignal<Boolean> enabled, Runnable onClick,
                SceneButtonVariant variant) {
            this.key = key;
            this.label = label;
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
        root.setHitTestable(true).setGap(4);
        if (horizontal) {
            root.setCrossAxisAlign(CrossAxisAlign.CENTER)
                    .setPreferredHeight(TOOLBAR_HEIGHT_PX)
                    .setPadding(0, PADDING_X, 0, PADDING_X);
        } else {
            // 竖直边：按钮交叉轴拉满条宽（文字居中），根高由按钮堆叠决定
            root.setPadding(PADDING_X, 0, PADDING_X, 0);
        }
        // 单一 keyed 列表：编辑态/普通态是同一个列表信号的两个分支（无 show 占位锚点）。
        ReadableSignal<List<Item>> items = Computed.create(() -> buildItems(host));
        rt.forEach(root, items, item -> item.key, item -> buildButton(rt, item, horizontal));
        return root;
    }

    /** 当前应显示的工具栏项（编辑态 = 完成/取消/重置；普通态 = 可见动作按 order/注册序）。 */
    private static List<Item> buildItems(Host host) {
        List<Item> items = new ArrayList<Item>();
        if (Boolean.TRUE.equals(host.editing().get())) {
            items.add(new Item("edit:finish", "完成", ALWAYS_ENABLED, host::finishEdit,
                    SceneButtonVariant.PRIMARY));
            items.add(new Item("edit:cancel", "取消", ALWAYS_ENABLED, host::cancelEdit,
                    SceneButtonVariant.STANDARD));
            items.add(new Item("edit:reset-current", "恢复当前默认", host.canResetCurrent(),
                    host::resetCurrent, SceneButtonVariant.STANDARD));
            items.add(new Item("edit:reset-all", "恢复全部默认", host.canResetAll(),
                    host::resetAll, SceneButtonVariant.STANDARD));
            return items;
        }
        ChatActionService service = ChatActionService.getInstance();
        service.revision().get();
        for (ChatAction action : service.actions()) {
            if (Boolean.TRUE.equals(action.getVisible().get())) {
                items.add(new Item("action:" + action.getId(), action.getLabel(), action.getEnabled(),
                        () -> runAction(action), SceneButtonVariant.STANDARD));
            }
        }
        return items;
    }

    /** 紧凑按钮（覆盖 SceneButton 默认内边距；水平边收缩到文本内在宽，竖直边拉满条宽）。 */
    private static SceneNode buildButton(SceneRuntime rt, Item item, boolean horizontal) {
        SceneNode button = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(item.label), item.enabled, item.onClick, item.variant)).get();
        button.setPadding(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
        if (horizontal) {
            // ★ SceneNode 默认 widthSizing=FILL：在 SHRINK 工具栏行里每个按钮会被拉成整行宽，
            //   并把 SHRINK 外框反向反馈成视口宽（工具栏比内容宽）。按钮必须收缩到文本内在宽。
            button.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        }
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