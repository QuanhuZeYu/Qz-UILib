package club.heiqi.uilib.ui.scene.form;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneButtonVariant;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

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
 * row (ROW, preferredHeight, gap)
 *   ├ 恢复默认 (enabled 恒 true 或 restoreEnabled)
 *   ├ spacer (flexGrow=1)
 *   ├ 取消 (enabled = cancelEnabled / dirty)
 *   └ 保存 (enabled = canSave, primary)
 * </pre>
 */
public final class FormActionBar {

    /** 按钮默认宽度（像素）。 */
    private static final int BUTTON_WIDTH = 110;
    /** 操作条默认高度（像素），与 ConfigTheme.ACTION_BAR_HEIGHT 同档。 */
    private static final int DEFAULT_HEIGHT = 36;
    /** 按钮间距默认值。 */
    private static final int DEFAULT_GAP = SceneChromeTokens.GAP_MD;

    /** 工具类，禁止实例化。 */
    private FormActionBar() {
    }

    /**
     * 构建表单操作条（恢复默认 / 取消 / 保存）。
     *
     * <p>恢复默认按钮 enabled 恒 true；取消由 {@code cancelEnabled} 控制；
     * 保存由 {@code canSave} 控制并使用 primary 变体。</p>
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
        if (rt == null) {
            throw new IllegalArgumentException("rt must not be null");
        }
        if (onRestore == null || onCancel == null || onSave == null) {
            throw new IllegalArgumentException("action callbacks must not be null");
        }
        if (restoreEnabled == null || cancelEnabled == null || canSave == null) {
            throw new IllegalArgumentException("enabled signals must not be null");
        }
        // theme 预留扩展（配色/字号），当前布局常量已内置；静默引用防 unused 告警
        if (theme == null) {
            throw new IllegalArgumentException("theme must not be null");
        }

        SceneNode row = SceneNode.row();
        row.setPreferredHeight(barHeight > 0 ? barHeight : DEFAULT_HEIGHT);
        row.setGap(gap > 0 ? gap : DEFAULT_GAP);

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
