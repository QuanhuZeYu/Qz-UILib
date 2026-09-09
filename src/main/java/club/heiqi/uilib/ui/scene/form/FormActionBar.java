package club.heiqi.uilib.ui.scene.form;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneButtonVariant;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 表单操作条共享工厂：恢复默认（左）/ spacer / 取消 + 保存（右，primary）。
 *
 * <p>从 {@code ConfigScreen.createActionBar} 与 demo 操作条提炼下沉的通用布局，
 * 左右分区 + flexGrow spacer 撑开中间，供任意表单页复用。</p>
 *
 * <p><b>零 config 依赖</b>：本类只吃 {@link ReadableSignal} / {@link Runnable} /
 * {@link FormTheme}，不感知任何 config 业务类型。handler 只写 signal 或调 Runnable
 * （守 I1/I11）；create 体只跑一次（守 R3）。</p>
 *
 * <pre>
 * row (ROW, preferredHeight, gap, TOOLBAR 表面)
 *   ├ 恢复默认 (enabled 恒 true 或 restoreEnabled)
 *   ├ spacer (flexGrow=1)
 *   ├ 取消 (enabled = cancelEnabled / dirty)
 *   └ 保存 (enabled = canSave, primary)
 * </pre>
 *
 * <p><b>两条外观路径（一个属性只有一个写入者，互斥不叠加）</b></p>
 * <ul>
 *   <li><b>默认路径</b>——不传 {@link FormTheme} 的重载：操作条 root 表面（background/borderColor/
 *       borderWidth/cornerRadius/backdrop/surfaceElevation）经 {@link SceneSurfaceBinder#bind}
 *       消费来源主题的 {@link SceneTheme.Role#TOOLBAR} 配方，绑定器是这些属性的唯一写入者；
 *       操作条恒启用（禁用语义在各按钮自身的 enabled 信号），构建期先声明关心
 *       hover/pressed/focus 再绑定。条上文字即按钮文案，只读复用已主题化的
 *       {@link SceneButton}（standard/primary 变体走各自 BUTTON_* 角色配方，其前景即主题
 *       {@code foreground}/{@code onAccentForeground}），本类不复制、不覆盖其任何样式。
 *       来源主题切换只重派生外观，不重建节点。</li>
 *   <li><b>显式路径</b>——保留的 {@link FormTheme} 重载：语义与迁移前完全一致，操作条 root
 *       不写任何外观属性（theme 仍为扩展位、布局常量内置），不装滤镜、不订阅主题；
 *       caller 自身的静态设色（如 {@code ConfigScreen} 挂回后设底色/圆角）继续独占生效，
 *       不与主题派生竞争。</li>
 * </ul>
 * <p>两条路径的布局（高度/内边距/间距/对齐/子序）与按钮回调、enabled 语义完全一致。</p>
 */
public final class FormActionBar {

    /** 按钮默认宽度（像素）。 */
    private static final int BUTTON_WIDTH = 110;
    /** 操作条默认高度（像素），与 ConfigTheme.ACTION_BAR_HEIGHT 同档。 */
    private static final int DEFAULT_HEIGHT = 36;
    /** 按钮间距默认值。 */
    private static final int DEFAULT_GAP = SceneChromeTokens.GAP_MD;

    /** 操作条 root 恒定启用（条体无禁用态；禁用语义在各按钮自身的 enabled 信号）。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

    /** 工具类，禁止实例化。 */
    private FormActionBar() {
    }

    // ==================== 默认路径：外观跟随来源主题（不传 FormTheme） ====================

    /**
     * 构建跟随来源主题的表单操作条（恢复默认 / 取消 / 保存），用主流默认尺寸。
     *
     * <p>操作条 root 表面取 {@link SceneThemes#surface} 的 {@link SceneTheme.Role#TOOLBAR}
     * 配方（染色/边框/圆角/浮雕/滤镜全归 {@link SceneSurfaceBinder}）；恢复默认按钮 enabled
     * 恒 true；取消由 {@code cancelEnabled} 控制；保存由 {@code canSave} 控制并使用 primary
     * 变体。来源主题切换（{@link SceneThemes#withTheme}）只重派生外观，不重建节点。</p>
     *
     * @param rt            场景运行时
     * @param onRestore     恢复默认回调，不可为 null
     * @param cancelEnabled 取消按钮启用态（通常 = dirty）
     * @param onCancel      取消回调，不可为 null
     * @param canSave       保存按钮启用态（通常 = dirty && !hasError）
     * @param onSave        保存回调，不可为 null
     * @return 操作条根节点
     */
    public static SceneNode build(SceneRuntime rt,
                                  Runnable onRestore,
                                  ReadableSignal<Boolean> cancelEnabled,
                                  Runnable onCancel,
                                  ReadableSignal<Boolean> canSave,
                                  Runnable onSave) {
        return build(rt,
                Signal.create(Boolean.TRUE), onRestore,
                cancelEnabled, onCancel,
                canSave, onSave,
                DEFAULT_HEIGHT, DEFAULT_GAP, BUTTON_WIDTH, SceneChromeTokens.BUTTON_HEIGHT);
    }

    /**
     * 构建跟随来源主题的表单操作条（完整尺寸参数，含恢复默认 enabled）。
     *
     * @param rt             场景运行时
     * @param restoreEnabled 恢复默认按钮启用态
     * @param onRestore      恢复默认回调
     * @param cancelEnabled  取消按钮启用态
     * @param onCancel       取消回调
     * @param canSave        保存按钮启用态
     * @param onSave         保存回调
     * @param barHeight      操作条 preferredHeight
     * @param gap            子节点间距
     * @param buttonWidth    按钮 preferredWidth
     * @param buttonHeight   按钮 preferredHeight
     * @return 操作条根节点
     */
    public static SceneNode build(SceneRuntime rt,
                                  ReadableSignal<Boolean> restoreEnabled,
                                  Runnable onRestore,
                                  ReadableSignal<Boolean> cancelEnabled,
                                  Runnable onCancel,
                                  ReadableSignal<Boolean> canSave,
                                  Runnable onSave,
                                  int barHeight,
                                  int gap,
                                  int buttonWidth,
                                  int buttonHeight) {
        return buildCore(rt, restoreEnabled, onRestore, cancelEnabled, onCancel,
                canSave, onSave, barHeight, gap, buttonWidth, buttonHeight, true);
    }

    // ==================== 显式路径：旧 FormTheme 重载（语义不变） ====================

    /**
     * 构建表单操作条（恢复默认 / 取消 / 保存）。
     *
     * <p>恢复默认按钮 enabled 恒 true；取消由 {@code cancelEnabled} 控制；
     * 保存由 {@code canSave} 控制并使用 primary 变体。</p>
     *
     * <p>显式 {@link FormTheme} 路径：操作条 root 不写任何外观属性（不装滤镜、不订阅主题），
     * 语义与迁移前一致；caller 挂回后对 root 的静态设色继续独占生效。</p>
     *
     * @param rt             场景运行时
     * @param onRestore      恢复默认回调，不可为 null
     * @param cancelEnabled  取消按钮启用态（通常 = dirty）
     * @param onCancel       取消回调，不可为 null
     * @param canSave        保存按钮启用态（通常 = dirty && !hasError）
     * @param onSave         保存回调，不可为 null
     * @param theme          主题 token（当前仅作扩展位；高度/间距用内置常量）
     * @return 操作条根节点
     */
    public static SceneNode build(SceneRuntime rt,
                                  Runnable onRestore,
                                  ReadableSignal<Boolean> cancelEnabled,
                                  Runnable onCancel,
                                  ReadableSignal<Boolean> canSave,
                                  Runnable onSave,
                                  FormTheme theme) {
        return build(rt,
                Signal.create(Boolean.TRUE), onRestore,
                cancelEnabled, onCancel,
                canSave, onSave,
                theme, DEFAULT_HEIGHT, DEFAULT_GAP, BUTTON_WIDTH, SceneChromeTokens.BUTTON_HEIGHT);
    }

    /**
     * 构建表单操作条（完整参数，含恢复默认 enabled 与尺寸）。
     *
     * <p>显式 {@link FormTheme} 路径：语义与迁移前完全一致——theme 仅作扩展位（配色/字号），
     * 布局常量内置，操作条 root 不写外观属性、不装滤镜、不订阅主题；需要默认玻璃外观的
     * caller 改用不传 theme 的默认路径重载。</p>
     *
     * @param rt              场景运行时
     * @param restoreEnabled  恢复默认按钮启用态
     * @param onRestore       恢复默认回调
     * @param cancelEnabled   取消按钮启用态
     * @param onCancel        取消回调
     * @param canSave         保存按钮启用态
     * @param onSave          保存回调
     * @param theme           主题 token（扩展位）
     * @param barHeight       操作条 preferredHeight
     * @param gap             子节点间距
     * @param buttonWidth     按钮 preferredWidth
     * @param buttonHeight    按钮 preferredHeight
     * @return 操作条根节点
     */
    public static SceneNode build(SceneRuntime rt,
                                  ReadableSignal<Boolean> restoreEnabled,
                                  Runnable onRestore,
                                  ReadableSignal<Boolean> cancelEnabled,
                                  Runnable onCancel,
                                  ReadableSignal<Boolean> canSave,
                                  Runnable onSave,
                                  FormTheme theme,
                                  int barHeight,
                                  int gap,
                                  int buttonWidth,
                                  int buttonHeight) {
        // theme 预留扩展（配色/字号），当前布局常量已内置；显式路径不订阅主题，静默引用防 unused 告警
        if (theme == null) {
            throw new IllegalArgumentException("theme must not be null");
        }
        return buildCore(rt, restoreEnabled, onRestore, cancelEnabled, onCancel,
                canSave, onSave, barHeight, gap, buttonWidth, buttonHeight, false);
    }

    // ==================== 共用装配 ====================

    /**
     * 操作条装配核心：两条外观路径共用同一结构、布局与按钮挂载，只在「外观写入者」上分流。
     *
     * @param rt             场景运行时
     * @param restoreEnabled 恢复默认按钮启用态
     * @param onRestore      恢复默认回调
     * @param cancelEnabled  取消按钮启用态
     * @param onCancel       取消回调
     * @param canSave        保存按钮启用态
     * @param onSave         保存回调
     * @param barHeight      操作条 preferredHeight
     * @param gap            子节点间距
     * @param buttonWidth    按钮 preferredWidth
     * @param buttonHeight   按钮 preferredHeight
     * @param themedSurface  true = root 表面跟随来源主题 TOOLBAR 配方重派生；
     *                       false = 显式路径，root 零外观写入（迁移前语义）
     * @return 操作条根节点
     */
    private static SceneNode buildCore(SceneRuntime rt,
                                       ReadableSignal<Boolean> restoreEnabled,
                                       Runnable onRestore,
                                       ReadableSignal<Boolean> cancelEnabled,
                                       Runnable onCancel,
                                       ReadableSignal<Boolean> canSave,
                                       Runnable onSave,
                                       int barHeight,
                                       int gap,
                                       int buttonWidth,
                                       int buttonHeight,
                                       boolean themedSurface) {
        if (rt == null) {
            throw new IllegalArgumentException("rt must not be null");
        }
        if (onRestore == null || onCancel == null || onSave == null) {
            throw new IllegalArgumentException("action callbacks must not be null");
        }
        if (restoreEnabled == null || cancelEnabled == null || canSave == null) {
            throw new IllegalArgumentException("enabled signals must not be null");
        }

        SceneNode row = SceneNode.row();
        row.setPreferredHeight(barHeight > 0 ? barHeight : DEFAULT_HEIGHT);
        row.setGap(gap > 0 ? gap : DEFAULT_GAP);

        if (themedSurface) {
            // 默认路径：染色/边框/圆角/浮雕/滤镜全归表面绑定器（TOOLBAR 配方），
            // 它是这六项属性的唯一写入者；显式路径零写入，caller 静态设色继续独占。
            bindToolbarSurface(rt, row);
        }

        // 左：恢复默认
        mountButton(rt, row, "恢复默认", restoreEnabled, onRestore, false, buttonWidth, buttonHeight);

        // 中：spacer 撑开剩余宽度
        SceneNode spacer = new SceneNode();
        spacer.setFlexGrow(1);
        spacer.setHitTestable(false);
        row.appendChild(spacer);

        // 右：取消 + 保存（保存最右末位，primary）
        mountButton(rt, row, "取消更改", cancelEnabled, onCancel, false, buttonWidth, buttonHeight);
        mountButton(rt, row, "保存", canSave, onSave, true, buttonWidth, buttonHeight);

        return row;
    }

    /**
     * 把来源主题的 TOOLBAR 配方绑到操作条 root：染色/边框/圆角/浮雕/滤镜全归表面绑定器。
     *
     * <p>操作条恒定启用（条体无禁用态）。构建期先声明关心 hover/pressed/focus：
     * Router 对未创建的 signal 直接短路，不声明则后续事件永远驱动不了配方状态档
     * （与 {@code FormPageShell}/{@code FormFieldShell} 同构）。</p>
     *
     * @param rt  场景运行时
     * @param row 操作条 root 节点
     */
    private static void bindToolbarSurface(SceneRuntime rt, SceneNode row) {
        ReadableSignal<SceneSurfaceStyle> toolbar = SceneThemes.surface(rt, SceneTheme.Role.TOOLBAR);
        SceneInteractionState interaction = rt.interactionState(row);
        interaction.hovered();
        interaction.pressed();
        interaction.focused();
        SceneSurfaceBinder.bind(rt, row, toolbar, ALWAYS_ENABLED, interaction);
    }

    /**
     * 挂载按钮到操作条。
     *
     * @param rt           场景运行时
     * @param parent       父节点
     * @param label        按钮文案
     * @param enabled      启用态
     * @param onClick      点击回调
     * @param primary      是否 primary 变体
     * @param buttonWidth  宽度
     * @param buttonHeight 高度
     */
    private static void mountButton(SceneRuntime rt, SceneNode parent, String label,
                                    ReadableSignal<Boolean> enabled, Runnable onClick,
                                    boolean primary, int buttonWidth, int buttonHeight) {
        SceneButton.Props props = new SceneButton.Props(
                Signal.create(label), enabled, onClick,
                primary ? SceneButtonVariant.PRIMARY : SceneButtonVariant.STANDARD);
        MountHandle handle = rt.mount(parent, SceneButton.create(rt, props));
        SceneNode root = handle.getRoot();
        if (root != null) {
            if (buttonWidth > 0) {
                root.setPreferredWidth(buttonWidth);
            }
            if (buttonHeight > 0) {
                root.setPreferredHeight(buttonHeight);
            }
        }
    }
}
