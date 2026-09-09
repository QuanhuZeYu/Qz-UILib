package club.heiqi.uilib.ui.scene.form;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 构建标签、辅助说明与控件组成的安全表单行。
 *
 * <p>当前默认使用纵向布局，不要求调用方猜测标签像素宽度。标签和辅助说明由各自的
 * 裁剪槽承载，控件在独立内容槽中接收父布局下沉的实际可用宽度，因此窄视口下不会与
 * 标签相交。后续只有在布局 API 能按实际可用宽度可靠分流时，才应增加横向变体。</p>
 *
 * <p><b>两条文字外观路径（{@code textColor} 只有一个写入者，互斥不叠加）</b></p>
 * <ul>
 *   <li><b>默认路径</b>——带 {@link SceneRuntime} 的
 *       {@link #vertical(SceneRuntime, String, String, SceneNode)}：构建期经
 *       {@link FormThemes#resolve(SceneRuntime)} 捕获来源主题，标签文字取 {@code textColor}
 *       （主题 {@code foreground}）、辅助说明取 {@code mutedColor}（主题
 *       {@code mutedForeground}），各自由 {@code rt.bindComputed} 绑定为唯一写入者——
 *       不在绑定之前静态写死再覆盖，也不保留第二套写入者。来源主题切换
 *       （{@code SceneThemes.withTheme} 或 runtime 默认主题更新）只重派生文字色，
 *       不重建节点、不新增订阅。</li>
 *   <li><b>旧静态路径</b>——保留的 {@link #vertical(String, String, SceneNode)}：签名与语义
 *       与迁移前完全一致，不写文字色（沿用节点默认前景）、不订阅主题、不感知来源主题，
 *       旧调用点零改动。</li>
 * </ul>
 *
 * <p><b>不新增表面/玻璃</b>：本类只做「标签 + 控件」排版，不是卡片——两条路径都不调用表面
 * 绑定器，不写 {@code background}/{@code border}/{@code borderWidth}/{@code cornerRadius}/
 * {@code backdrop}/浮雕，符合契约「Label 文本本身不追加玻璃」。两条路径的节点结构、挂载
 * 顺序、间距、字号与命中语义完全一致；主题不接管布局。</p>
 */
public final class FormLabeledControl {
    private static final int GAP = 2;

    private FormLabeledControl() { }

    /**
     * 构建纵向标签控件行（旧静态路径：不写文字色、不订阅主题）。
     *
     * @param label 显示标签；为空时不创建标签槽
     * @param helper 可选辅助说明；为空时不创建说明槽
     * @param control 控件节点
     * @return 纵向、安全裁剪的表单行
     */
    public static SceneNode vertical(String label, String helper, SceneNode control) {
        return build(null, label, helper, control);
    }

    /**
     * 构建纵向标签控件行，标签与辅助说明文字跟随来源主题（默认路径）。
     *
     * <p><b>构造期调用</b>：必须在构建期（{@code mount/show/forEach/portal} 的 builder 内）
     * 调用——此时 {@code Owner.current()} 是来源作用域，{@link FormThemes#resolve(SceneRuntime)}
     * 才能捕获局部/页面主题。返回节点的文字色由主题派生绑定独占，来源主题切换只重派生文字色，
     * 不重建节点、不丢控件状态。</p>
     *
     * @param rt 场景运行时
     * @param label 显示标签；为空时不创建标签槽
     * @param helper 可选辅助说明；为空时不创建说明槽
     * @param control 控件节点
     * @return 纵向、安全裁剪、文字跟随来源主题的表单行
     */
    public static SceneNode vertical(SceneRuntime rt, String label, String helper, SceneNode control) {
        Objects.requireNonNull(rt, "rt");
        return build(rt, label, helper, control);
    }

    /**
     * 装配核心：两条文字路径共用同一结构与布局，只在「文字色写入者」上分流。
     *
     * @param rt      runtime；{@code null} = 旧静态路径（不建任何订阅）
     * @param label   显示标签；为空时不创建标签槽
     * @param helper  可选辅助说明；为空时不创建说明槽
     * @param control 控件节点
     * @return 纵向、安全裁剪的表单行
     */
    private static SceneNode build(SceneRuntime rt, String label, String helper, SceneNode control) {
        if (control == null) throw new IllegalArgumentException("control must not be null");
        SceneNode root = SceneNode.column();
        root.setGap(GAP);
        // 构造期捕获来源主题（Owner 上下文此刻有效）；null 表示旧静态路径，不建任何订阅。
        ReadableSignal<FormTheme> theme = rt == null ? null : FormThemes.resolve(rt);
        if (label != null && !label.isEmpty()) root.appendChild(textSlot(rt, theme, label, true));
        if (helper != null && !helper.isEmpty()) root.appendChild(textSlot(rt, theme, helper, false));
        SceneNode content = SceneNode.column();
        content.setClipChildren(true);
        content.appendChild(control);
        root.appendChild(content);
        return root;
    }

    /**
     * 构建占满可用宽度并裁剪溢出文本的只读槽。
     *
     * <p>{@code theme} 非 null 时绑定主题前景：标签取 {@code textColor}、辅助说明取
     * {@code mutedColor}；绑定是该节点 {@code textColor} 的唯一写入者，不在绑定之前静态
     * 写死再覆盖。{@code theme} 为 null 时（旧静态路径）不写文字色，沿用节点默认前景。</p>
     *
     * @param rt    runtime（与 {@code theme} 同为 null 或同非 null）
     * @param theme 表单主题信号；{@code null} = 旧静态路径
     * @param text  文本
     * @param label true = 标签（正文前景），false = 辅助说明（次要前景）
     * @return 只读文字槽
     */
    private static SceneNode textSlot(SceneRuntime rt, ReadableSignal<FormTheme> theme,
                                      String text, boolean label) {
        SceneNode slot = SceneNode.row();
        slot.setClipChildren(true);
        SceneNode node = new SceneNode();
        node.setText(text);
        node.setHitTestable(false);
        if (theme != null) {
            rt.bindComputed(() -> label ? theme.get().textColor() : theme.get().mutedColor(),
                    node::setTextColor);
        }
        slot.appendChild(node);
        return slot;
    }
}
