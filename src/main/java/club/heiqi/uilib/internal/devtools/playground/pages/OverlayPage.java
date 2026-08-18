package club.heiqi.uilib.internal.devtools.playground.pages;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneContextMenu;
import club.heiqi.uilib.ui.scene.control.SceneDialog;
import club.heiqi.uilib.ui.scene.control.SceneToast;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 浮层演示页（Dialog / Toast / ContextMenu）。
 *
 * <p>覆盖：SceneDialog 多按钮（NORMAL/PRIMARY/DANGER、closesDialog 语义、ESC 关闭、
 * 全屏模态遮罩 + 窗口中心卡片 + 出现/退场动画）与 alert/confirm 命令式 API、
 * SceneToast 命令式投递与不同时长堆叠（类型化入口 + 底部居中 + 淡入淡出动画）、
 * SceneContextMenu 右键命令式打开与菜单项启停/分隔线。页面内置动作日志回显所有浮层回调。</p>
 */
public final class OverlayPage implements PlaygroundPage {

    /** 对话框可见性（受控源，onDismiss 回写 false）。 */
    private final Signal<Boolean> dialogVisible = Signal.create(Boolean.FALSE);
    /** 动作日志（浮层回调回显）。 */
    private final Signal<String> log = Signal.create("");

    @Override
    public String id() {
        return "overlay";
    }

    @Override
    public String title() {
        return "浮层";
    }

    @Override
    public String description() {
        return "SceneDialog 多按钮/危险操作、SceneToast 时长堆叠、SceneContextMenu 右键菜单";
    }

    @Override
    public Supplier<SceneNode> build(final SceneRuntime rt) {
        return () -> {
            SceneNode root = SceneNode.column();
            root.setFillParentWidth(true);
            root.setGap(10);

            // ===== 卡片1：Dialog =====
            SceneNode dialogCard = PlaygroundKit.card();
            dialogCard.appendChild(PlaygroundKit.title("Dialog（模态对话框）"));
            SceneNode dialogRow = PlaygroundKit.row(8);
            PlaygroundKit.primaryButton(rt, dialogRow, "打开危险操作确认",
                    () -> dialogVisible.set(Boolean.TRUE));
            dialogCard.appendChild(dialogRow);
            SceneNode dialogApiRow = PlaygroundKit.row(8);
            PlaygroundKit.button(rt, dialogApiRow, "alert 单按钮",
                    () -> SceneDialog.alert(rt, "保存完成", "数据已保存到本地。",
                            () -> log.set("alert：已确认")));
            PlaygroundKit.button(rt, dialogApiRow, "confirm 双按钮",
                    () -> SceneDialog.confirm(rt, "删除确认", "删除后不可恢复，确定继续？",
                            () -> log.set("confirm：确定删除"), () -> log.set("confirm：已取消")));
            dialogCard.appendChild(dialogApiRow);
            dialogCard.appendChild(PlaygroundKit.hint(
                    "模态：全屏遮罩拦截指针、卡片窗口中心对齐、Tab 环限定对话框内、ESC/取消关闭、出现/退场淡入淡出；"
                            + "按钮含 取消 / 再想想（closesDialog=false，只触发回调不关闭）/ 删除（DANGER）。"));
            SceneDialog.Props dialogProps = new SceneDialog.Props(
                    dialogVisible,
                    "删除演示数据",
                    "此操作不可撤销（演示用途），确定继续？",
                    Arrays.asList(
                            SceneDialog.Button.of("取消", null),
                            new SceneDialog.Button("再想想", SceneDialog.ButtonKind.NORMAL, false,
                                    () -> SceneToast.show(rt, "对话框保持打开（closesDialog=false 示例）")),
                            new SceneDialog.Button("删除", SceneDialog.ButtonKind.DANGER, true,
                                    () -> {
                                        log.set("已执行删除（演示）");
                                        SceneToast.show(rt, "危险操作已执行（演示）");
                                    })),
                    () -> dialogVisible.set(Boolean.FALSE));
            // 对话框 portal 的 effect 归属页面 mount Owner，页面卸载时自动回收；
            // 可见性完全由 dialogVisible 信号驱动（R8 受控源）。
            SceneDialog.create(rt, dialogProps);

            // ===== 卡片2：Toast =====
            SceneNode toastCard = PlaygroundKit.card();
            toastCard.appendChild(PlaygroundKit.title("Toast（非模态通知，底部堆叠 + 类型化 + 动画）"));
            SceneNode toastRow = PlaygroundKit.row(8);
            PlaygroundKit.button(rt, toastRow, "短 Toast（1.5s）",
                    () -> SceneToast.show(rt, "短通知：1.5 秒后消失", 1_500_000_000L));
            PlaygroundKit.button(rt, toastRow, "普通 Toast（3s）",
                    () -> SceneToast.show(rt, "普通通知：3 秒后消失", SceneToast.DEFAULT_DURATION_NANOS));
            PlaygroundKit.button(rt, toastRow, "长 Toast（10s）",
                    () -> SceneToast.show(rt, "长通知：10 秒后消失，便于观察堆叠与到期", 10_000_000_000L));
            toastCard.appendChild(toastRow);
            SceneNode toastTypeRow = PlaygroundKit.row(8);
            PlaygroundKit.button(rt, toastTypeRow, "成功",
                    () -> SceneToast.showSuccess(rt, "保存成功", 2_000_000_000L));
            PlaygroundKit.button(rt, toastTypeRow, "警告",
                    () -> SceneToast.showWarning(rt, "磁盘空间不足", 3_000_000_000L));
            PlaygroundKit.button(rt, toastTypeRow, "错误",
                    () -> SceneToast.showError(rt, "网络连接失败", 4_000_000_000L));
            toastCard.appendChild(toastTypeRow);
            toastCard.appendChild(PlaygroundKit.hint(
                    "快速连点不同按钮：底部堆叠、内容宽度收缩水平居中（不再占满全宽）、出现淡入+上移、"
                            + "到期先淡出再移除、各自按帧时间独立到期（非模态，不拦截指针）。"));

            // ===== 卡片3：ContextMenu =====
            SceneNode menuCard = PlaygroundKit.card();
            menuCard.appendChild(PlaygroundKit.title("ContextMenu（右键上下文菜单）"));
            SceneNode menuPanel = SceneNode.column();
            menuPanel.setPreferredHeight(48);
            menuPanel.setFillParentWidth(true);
            menuPanel.setPadding(10);
            menuPanel.setBorderWidth(1);
            menuPanel.setBorderColor(PlaygroundKit.BORDER);
            menuPanel.setCornerRadius(club.heiqi.uilib.ui.scene.paint.SceneChromeTokens.RADIUS_MD);
            menuPanel.setBackgroundColor(PlaygroundKit.PANEL_BG);
            menuPanel.setCrossAxisAlign(club.heiqi.uilib.ui.scene.layout.CrossAxisAlign.CENTER);
            menuPanel.appendChild(PlaygroundKit.hint("在此区域点击右键打开上下文菜单"));
            menuCard.appendChild(menuPanel);
            rt.on(menuPanel, SceneEventType.POINTER_DOWN, (event, context) -> {
                if (event.getButton() != SceneMouseButton.RIGHT) {
                    return;
                }
                int x = context.getRawPointerX() - context.getTreeRootAbsX();
                int y = context.getRawPointerY() - context.getTreeRootAbsY();
                SceneContextMenu.open(rt, x, y, buildMenuItems());
            });
            SceneNode menuLog = PlaygroundKit.text("", PlaygroundKit.MUTED, 12);
            menuCard.appendChild(menuLog);
            rt.bind(Computed.create(() -> log.get().isEmpty() ? "动作日志：（暂无，右键面板试试）" : "动作日志：" + log.get()),
                    menuLog::setText);

            root.appendChild(dialogCard);
            root.appendChild(toastCard);
            root.appendChild(menuCard);
            return root;
        };
    }

    /**
     * 构建右键菜单项（含启停与分隔线演示）。
     *
     * @return 菜单项列表
     */
    private List<SceneContextMenu.MenuItem> buildMenuItems() {
        SceneContextMenu.MenuItem copyTime = SceneContextMenu.MenuItem.of(
                "复制当前时间",
                () -> {
                    String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    log.set("已复制当前时间：" + time);
                });
        SceneContextMenu.MenuItem clearLog = SceneContextMenu.MenuItem.of("清零日志", () -> log.set(""));
        SceneContextMenu.MenuItem disabledItem = SceneContextMenu.MenuItem.of("禁用项（不可选）", false, null);
        SceneContextMenu.MenuItem divider = SceneContextMenu.MenuItem.divider();
        SceneContextMenu.MenuItem close = SceneContextMenu.MenuItem.of(
                "选择即关闭（演示）",
                () -> log.set("菜单项已激活并自动关闭"));
        return Arrays.asList(copyTime, clearLog, disabledItem, divider, close);
    }
}
