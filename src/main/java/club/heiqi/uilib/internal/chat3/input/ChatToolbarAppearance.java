package club.heiqi.uilib.internal.chat3.input;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.control.SceneGlassButtonStyle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 聊天动作与公共缩放按钮共用的配方；仅桥接既有进程设置，不持有节点或 runtime。
 *
 * <p><b>双路径（G17/Toolbar 液态玻璃口径，契约 §3 优先级 + §7.3「HUD 工具栏保持显式配方与
 * 缩放语义」）</b>：</p>
 * <ul>
 *   <li><b>默认路径</b>（{@link #recipe}）——聊天动作按钮跟随当前主题（{@link SceneThemes#surface}
 *       按按钮材质角色给出基线）；既有聊天玻璃设置（开关/模糊/强度）按第一优先级<b>逐项只覆盖
 *       「滤镜」这一个字段</b>：设置开 = DARK_THIN 系玻璃按设置模糊/强度，设置关 = {@code null}
 *       显式关闭滤镜（契约 §2.2）；底色/缘色/四态/圆角/边框宽/过渡等设置未管辖的字段全部随所属
 *       主题。主题切换与设置变更是两个独立失效源，任一变化只重派生、不重建节点。</li>
 *   <li><b>显式旧路径</b>（{@link #style()}）——公共缩放按钮经
 *       {@code HudToolbarSpec.getPublicButtonStyle()} 消费的按钮专用配方保持原样：不订阅主题、
 *       不叠加通用主题轨道，仅按既有语义桥接聊天玻璃设置（设置变更仍经帧观察按值去重传播）。</li>
 * </ul>
 *
 * <p>消费方向恒为 chat3 → ui.scene.theme；通用主题零 import chat3（契约 §4.1）。</p>
 */
public final class ChatToolbarAppearance {
    // 设置可能从 UI 线程之外写入；只在现有 UI 帧内发布 Signal 通知。
    // Signal 按配方值去重，无变化帧不会唤醒按钮样式，也不会新增计时器。
    private static final Signal<UiBackdrop> OBSERVED_BACKDROP = Signal.create(readBackdrop());
    private static final ReadableSignal<SceneGlassButtonStyle> STYLE = () -> {
        OBSERVED_BACKDROP.get();
        // 同步读取设置，保证构建期和首次 flush 前也拿到当前配方。
        return SceneGlassButtonStyle.builder().backdrop(readBackdrop()).build();
    };

    private ChatToolbarAppearance() {}

    /**
     * 显式旧路径配方（公共缩放按钮经 {@code HudToolbarSpec} 消费）：只桥接聊天玻璃设置，
     * <b>不订阅主题</b>、不做主题派生——保持既有的静态取值与帧观察传播语义原样
     * （契约 §7.3「HUD 工具栏保持显式配方」）。动作按钮不再消费本信号，改走 {@link #recipe}。
     *
     * @return 按钮专用配方信号（值仅由聊天玻璃设置决定）
     */
    public static ReadableSignal<SceneGlassButtonStyle> style() { return STYLE; }

    /**
     * 默认路径动作按钮配方：主题按钮角色配方为基线 + 聊天玻璃设置对「滤镜」字段的逐项局部覆盖。
     *
     * <p>必须在构建期调用（{@code forEach} 项 builder 内，此时 {@code Owner.current()} 是项作用域）：
     * 构造期经 {@link SceneThemes#surface} 捕获来源主题信号，派生期只读上游——契约 §10「构造期
     * 捕获主题、派生期不依赖 Owner 上下文」纪律。无初值 {@link Computed}：绑定器构造期不解引用，
     * 首次 flush 求值；其 recompute 单元自动归属当前 Owner（施工手册 §3「卸载即回收」），
     * 按钮项随 keyed 列表卸载时一并回收，不留 orphan effect。主题与设置任一失效源变化都只
     * 重派生本配方：节点身份不变、不重建按钮、不改倍率/布局预算（契约 §4.1 HUD 工具栏行）。</p>
     *
     * @param rt   宿主场景运行时（主题作用域解析入口）
     * @param role 按钮材质角色（由按钮变体按契约 §2.6 映射：STANDARD/PRIMARY/DANGER）
     * @return 表面配方信号（滤镜字段恒为聊天设置值，其余字段随主题）
     */
    static ReadableSignal<SceneSurfaceStyle> recipe(SceneRuntime rt, SceneTheme.Role role) {
        final ReadableSignal<SceneSurfaceStyle> themed = SceneThemes.surface(rt, role);
        return Computed.create(() -> {
            OBSERVED_BACKDROP.get(); // 失效源①：聊天玻璃设置帧观察（按值去重）
            // 失效源②：主题；聊天设置只管滤镜字段，其余属性由主题角色配方供给。
            return themed.get().toBuilder().backdrop(readBackdrop()).build();
        });
    }

    /** 随当前挂载 Owner/runtime 清理；多个投放共享设置通知，不共享交互状态。 */
    static void observe(SceneRuntime rt) {
        rt.bind(rt.__frameTimeNanos(), frame -> OBSERVED_BACKDROP.set(readBackdrop()));
    }

    private static UiBackdrop readBackdrop() {
        return ChatMarkdownSettings.isGlassEnabled()
                ? UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN,
                        Math.min(6, ChatMarkdownSettings.getGlassBlurRadiusPx()),
                        ChatMarkdownSettings.getGlassLensStrength())
                : null;
    }
}
