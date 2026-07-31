package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * SceneControlChrome —— 跨控件 chrome bind 样板收敛器（纯静态工具）。
 *
 * <h3>定位：control 包共用静态 helper，非控件</h3>
 * <p>交互控件层高频出现三段一字不差的响应式 chrome 绑定样板：
 * 标准边框（{@link SceneStateColors#standardBorder}）、cursor 切换、可选中背景
 * （{@link SceneStateColors#selectedBackground}/{@link SceneStateColors#standardBackground} 四态）。
 * 本类把它们收成 3 个静态 helper，消除控件层重复样板，降低抄写错配风险。</p>
 *
 * <h3>为何放 control 包而非 paint 包（守 I6）</h3>
 * <p>helper 依赖 {@link SceneRuntime}（runtime 层）与 {@link SceneInteractionState}（input 层），
 * 若放 paint 层会破坏宪章不变量 I6「渲染层不出现 signal/组件概念」——paint 层只允许纯静态
 * 查表（如 {@link SceneStateColors}），不允许反向依赖 runtime/input。control 层本就依赖
 * runtime/input/node/paint/reactive，放此包不引入任何新的非法依赖方向。</p>
 *
 * <h3>守 R4 / I4 / I1</h3>
 * <ul>
 *   <li><b>R4</b>：颜色 helper 薄包装 {@link SceneRuntime#__bindAnimatedColor}，cursor 仍走标准
 *       bind；外观始终由 signal 派生，不在 handler 里命令式 setXxx。</li>
 *   <li><b>I4</b>：失效级别由目标 setter 内部自动决定（{@code setBorderColor/setBackgroundColor}
 *       打 PAINT、{@code setCursor} 打对应级），helper 不手选级别、不改失效语义。</li>
 *   <li><b>I1</b>：派生仍是 signal→effect，不引入命令式绕道。</li>
 * </ul>
 *
 * <h3>Boolean 解包</h3>
 * <p>对 signal 值统一用 {@code Boolean.TRUE.equals(x)} 防御性解包为基本类型，与各控件原样板
 * 口径一致；{@link SceneInteractionState} 的 hovered/pressed/focused signal 初值为
 * {@code Boolean.FALSE} 且只写 boolean，正常不会为 null，防御性解包仅为对齐原语义。</p>
 */
public final class SceneControlChrome {

    /** 纯静态工具类，禁止实例化。 */
    private SceneControlChrome() {
    }

    /**
     * 绑定标准边框三态（disabled/focused/default）到节点 borderColor。
     *
     * <p>等价样板：
     * <pre>{@code
     * rt.__bindAnimatedColor(() -> SceneStateColors.standardBorder(
     *         Boolean.TRUE.equals(enabled.get()),
     *         Boolean.TRUE.equals(interaction.focused().get())),
     *     node::setBorderColor, SceneChromeTokens.MOTION_FAST_MS);
     * }</pre>
     * Config runtime 以 fast Motion 插值，其它 runtime 保持立即应用；setter
     * {@code setBorderColor} 自动打 PAINT 级（I4）。</p>
     *
     * @param rt          场景运行时
     * @param node        目标节点（borderColor 写入槽）
     * @param enabled     是否启用信号
     * @param interaction 交互态容器（读 focused）
     */
    public static void bindStandardBorder(SceneRuntime rt, SceneNode node,
            ReadableSignal<Boolean> enabled, SceneInteractionState interaction) {
        rt.__bindAnimatedColor(() -> SceneStateColors.standardBorder(
                Boolean.TRUE.equals(enabled.get()),
                Boolean.TRUE.equals(interaction.focused().get())),
            node::setBorderColor,
            SceneChromeTokens.MOTION_FAST_MS);
    }

    /**
     * 绑定 cursor 二态切换：enabled 用 enabledCursor，否则用 disabledCursor。
     *
     * <p>等价样板：
     * <pre>{@code
     * rt.bind(enabled,
     *     e -> node.setCursor(Boolean.TRUE.equals(e) ? enabledCursor : disabledCursor));
     * }</pre>
     * cursor 对按控件语义参数化（如 POINTER/NOT_ALLOWED、TEXT/NOT_ALLOWED、POINTER/DEFAULT），
     * setter {@code setCursor} 内部自动打出对应失效级别（I4）。</p>
     *
     * @param rt             场景运行时（提供 bind）
     * @param node           目标节点（cursor 写入槽）
     * @param enabled        是否启用信号
     * @param enabledCursor  启用时光标
     * @param disabledCursor 禁用时光标
     */
    public static void bindCursor(SceneRuntime rt, SceneNode node,
            ReadableSignal<Boolean> enabled, SceneCursor enabledCursor, SceneCursor disabledCursor) {
        rt.bind(enabled, e -> node.setCursor(Boolean.TRUE.equals(e) ? enabledCursor : disabledCursor));
    }

    /**
     * 绑定可选中背景四态：selected 走 {@link SceneStateColors#selectedBackground}（ACCENT 通道），
     * 未选中走 {@link SceneStateColors#standardBackground}（Slate 灰通道），优先级
     * disabled > pressed > hover > default。
     *
     * <p>等价样板：
     * <pre>{@code
     * rt.__bindAnimatedColor(() -> Boolean.TRUE.equals(selected.get())
     *         ? SceneStateColors.selectedBackground(en, hovered, pressed)
     *         : SceneStateColors.standardBackground(en, hovered, pressed),
     *     node::setBackgroundColor, SceneChromeTokens.MOTION_STANDARD_MS);
     * }</pre>
     * Config runtime 以 standard Motion 插值，其它 runtime 保持立即应用；setter
     * {@code setBackgroundColor} 自动打 PAINT 级（I4）。</p>
     *
     * <p><b>selected 来源统一收 {@code ReadableSignal<Boolean>}</b>：Checkbox 传 {@code props.checked()}、
     * Toggle 传 {@code props.on()}、段式控件（NavList/Segmented/Tab/RadioGroup）传 {@code handle.selected()}。
     * Button 不走此 helper（其 primary 是编译期 boolean 非 signal，且为试金石参考实现）。</p>
     *
     * @param rt          场景运行时
     * @param node        目标节点（backgroundColor 写入槽）
     * @param enabled     是否启用信号
     * @param selected    是否选中信号（受控源）
     * @param interaction 交互态容器（读 hovered/pressed）
     */
    public static void bindSelectableBackground(SceneRuntime rt, SceneNode node,
            ReadableSignal<Boolean> enabled, ReadableSignal<Boolean> selected,
            SceneInteractionState interaction) {
        rt.__bindAnimatedColor(() -> {
            boolean en = Boolean.TRUE.equals(enabled.get());
            boolean sel = Boolean.TRUE.equals(selected.get());
            boolean hovered = Boolean.TRUE.equals(interaction.hovered().get());
            boolean pressed = Boolean.TRUE.equals(interaction.pressed().get());
            return sel
                ? SceneStateColors.selectedBackground(en, hovered, pressed)
                : SceneStateColors.standardBackground(en, hovered, pressed);
        }, node::setBackgroundColor, SceneChromeTokens.MOTION_STANDARD_MS);
    }
}
