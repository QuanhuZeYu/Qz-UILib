package club.heiqi.uilib.internal.devtools.playground.pages;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneContextMenu;
import club.heiqi.uilib.ui.scene.control.SceneDialog;
import club.heiqi.uilib.ui.scene.control.SceneToast;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 浮层演示页（Dialog / Toast / ContextMenu）。
 *
 * <p>覆盖：SceneDialog 多按钮（NORMAL/PRIMARY/DANGER、closesDialog 语义、ESC 关闭、
 * 全屏模态遮罩 + 窗口中心卡片 + 出现/退场动画）与 alert/confirm 命令式 API、
 * SceneToast 命令式投递与不同时长堆叠（类型化入口 + 底部居中 + 淡入淡出动画）、
 * SceneContextMenu 右键命令式打开与菜单项启停/分隔线。页面内置动作日志回显所有浮层回调。</p>
 *
 * <p><b>外观归属（G16/OverlayPage）</b>：本页默认外观统一消费来源主题——卡片与右键演示区容器的
 * 表面（background/border/borderWidth/cornerRadius/backdrop/surfaceElevation）由
 * {@link SceneSurfaceBinder} 独占写入（角色配方由 {@link SceneThemes#surface} 解析）；节标题与
 * 说明文字复用公共文本构件 {@link PlaygroundKit#title(String)}/{@link PlaygroundKit#hint(String)}
 * （宿主内经 {@code installRuntime} 接缝默认跟随来源主题正文/次要前景），动作日志取
 * {@link SceneThemes#mutedForeground}；旧的静态取色（{@code PlaygroundKit.PANEL_BG/BORDER/MUTED}
 * 与 {@code SceneChromeTokens.RADIUS_MD}）已删除。Dialog/Toast/ContextMenu 的<b>本体外观仍由各自
 * 已主题化的控件负责</b>，本页只触发演示动作，不复制其样式、也不二次包一层玻璃；布局（内边距/
 * 高度/交叉轴对齐）仍归本页，主题不接管布局。</p>
 */
public final class OverlayPage implements PlaygroundPage {

    /** 常开 enabled 信号：右键演示区容器不参与 disabled 语义（表面绑定器只关心恒真）。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

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
            menuPanel.setCrossAxisAlign(CrossAxisAlign.CENTER);
            // 右键演示区容器的表面（background/border/borderWidth/cornerRadius/backdrop/实体高度）
            // 唯一写入者是表面绑定器：角色配方取来源主题 GROUP（内容底座，与公共卡片同角色）。
            // 旧的静态写入者（setBorderWidth(1)/PlaygroundKit.BORDER/RADIUS_MD/PANEL_BG）已删；
            // 布局（高度/内边距/交叉轴对齐）仍归本页，主题不接管布局。
            SceneInteractionState regionInteraction = rt.interactionState(menuPanel);
            // 时序契约：Router 对未创建的 signal 短路，故构建期先声明关心，保证 hover 能驱动配方状态档。
            regionInteraction.hovered();
            regionInteraction.pressed();
            regionInteraction.focused();
            SceneSurfaceBinder.bind(rt, menuPanel, SceneThemes.surface(rt, SceneTheme.Role.GROUP),
                    ALWAYS_ENABLED, regionInteraction);
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
            // 动作日志读数：12px 次要前景，取来源主题（与页内其它文字同一主题路径）。
            SceneNode menuLog = PlaygroundKit.text(rt, "", SceneThemes.mutedForeground(rt), 12);
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
