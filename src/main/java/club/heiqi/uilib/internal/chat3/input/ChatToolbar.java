package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.api.chat.ChatAction;
import club.heiqi.uilib.api.chat.ChatActionService;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
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
 * 这一行按钮的内容与行为。</p>
 *
 * <p>全部经 {@link SceneButton} + {@link Signal} + keyed list 渲染；动作只发布语义
 * （{@link ChatAction#run()}），执行失败仅影响当前动作。隐藏动作不占位、禁用动作仍显示。
 * 首版不做「更多」溢出菜单与图标，留待 P1 完整验收。</p>
 */
public final class ChatToolbar {

    private static final Logger LOG = LogManager.getLogger("QzUILib Chat3Toolbar");
    /** 工具栏四周左右内边距（与输入条区同源，保证左右对齐）。 */
    private static final int PADDING_X = 8;
    /** 紧凑按钮内边距（覆盖 SceneButton 默认 PAD_MD，避免工具栏过高）。 */
    private static final int BUTTON_PAD_Y = 2;
    private static final int BUTTON_PAD_X = 8;
    /**
     * 工具栏固定行高 = HUD 外接工具栏规格的默认厚度（唯一数值来源，避免两处 28 漂移）。
     *
     * <p>必须给外框一个可先验的厚度：宿主/打开态页面在 layout 之前就要算外框高度来
     * placement，工具栏不能等一帧实测。首版单行紧凑工具栏；多行/溢出菜单留待 P1 完整
     * 验收，届时厚度改为可先验的动态值。</p>
     */
    private static final int TOOLBAR_HEIGHT_PX = HudToolbarSpec.DEFAULT_THICKNESS_PX;

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
     * 装配工具栏行（普通态 / 编辑态互斥）。
     *
     * @param rt   宿主运行时
     * @param host 工具栏宿主端口
     * @return 工具栏根行（由容器插到输入条上方）
     */
    public static SceneNode mount(SceneRuntime rt, final Host host) {
        SceneNode root = SceneNode.row()
                .setHitTestable(true)
                .setCrossAxisAlign(CrossAxisAlign.CENTER)
                .setGap(4)
                .setPreferredHeight(TOOLBAR_HEIGHT_PX)
                .setPadding(0, PADDING_X, 0, PADDING_X);
        // 编辑态行：完成/取消/重置。显示顺序 = 声明顺序（show 的 anchor 占位）。
        rt.show(root, host.editing(), () -> buildEditRow(rt, host));
        // 普通态行：注册动作列表（编辑态隐藏，避免误触）。
        rt.show(root, Computed.create(() -> Boolean.valueOf(!Boolean.TRUE.equals(host.editing().get()))),
                () -> buildActionRow(rt));
        return root;
    }

    /** 注册动作行：可见性过滤 + order/注册序排序（forEach keyed diff）。 */
    private static SceneNode buildActionRow(SceneRuntime rt) {
        SceneNode row = SceneNode.row().setHitTestable(false).setCrossAxisAlign(CrossAxisAlign.CENTER).setGap(4);
        ReadableSignal<List<ChatAction>> actions = Computed.create(() -> {
            ChatActionService service = ChatActionService.getInstance();
            service.revision().get();
            List<ChatAction> all = service.actions();
            List<ChatAction> shown = new ArrayList<ChatAction>(all.size());
            for (ChatAction action : all) {
                if (Boolean.TRUE.equals(action.getVisible().get())) {
                    shown.add(action);
                }
            }
            return shown;
        });
        rt.forEach(row, actions, action -> action.getId(),
                action -> buildButton(rt, action.getLabel(), action.getEnabled(),
                        () -> runAction(action), SceneButtonVariant.STANDARD));
        return row;
    }

    /** 编辑态行：完成/取消/恢复当前默认/恢复全部默认。 */
    private static SceneNode buildEditRow(SceneRuntime rt, Host host) {
        SceneNode row = SceneNode.row().setHitTestable(false).setCrossAxisAlign(CrossAxisAlign.CENTER).setGap(4);
        row.appendChild(buildButton(rt, "完成", Signal.create(Boolean.TRUE), host::finishEdit,
                SceneButtonVariant.PRIMARY));
        row.appendChild(buildButton(rt, "取消", Signal.create(Boolean.TRUE), host::cancelEdit,
                SceneButtonVariant.STANDARD));
        row.appendChild(buildButton(rt, "恢复当前默认", host.canResetCurrent(), host::resetCurrent,
                SceneButtonVariant.STANDARD));
        row.appendChild(buildButton(rt, "恢复全部默认", host.canResetAll(), host::resetAll,
                SceneButtonVariant.STANDARD));
        return row;
    }

    /** 紧凑按钮（覆盖 SceneButton 默认内边距；标签信号为一次性文本，动态标签留待扩展）。 */
    private static SceneNode buildButton(SceneRuntime rt, String label, ReadableSignal<Boolean> enabled,
            Runnable onClick, SceneButtonVariant variant) {
        SceneNode button = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(label), enabled, onClick, variant)).get();
        button.setPadding(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
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
