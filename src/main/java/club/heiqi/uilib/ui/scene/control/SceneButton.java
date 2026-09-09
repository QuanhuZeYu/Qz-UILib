package club.heiqi.uilib.ui.scene.control;

import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneButton —— scene 控件层参考实现，第 0 段地基总验收试金石。
 *
 * <h3>定位：缺口浓缩器试金石，非真实迁移目标</h3>
 * <p>本控件用一个文件撞齐 scene 全部新地基能力：水平居中 flex（ROW + 主/交叉轴 CENTER）、
 * padding、边框、胶囊圆角、子节点裁剪（overflow:hidden）、非白文字色、四态背景切换。
 * 证明「裸 SceneNode + 新属性槽 + SceneRuntime」真能拼出完整控件，并确立后续所有控件
 * 照抄的契约范本（契约红线见 {@code package-info.java} R1-R5）。</p>
 *
 * <h3>外观归属：表面绑定是唯一写入者</h3>
 * <p>背景/边框/边框宽/圆角/滤镜/实体高度由 {@link SceneSurfaceBinder} 从配方信号派生，
 * 本控件不再静态设色、不再叠加第二套实色动画（守「一个属性只有一个外观写入者」）。
 * 默认配方来自 {@link SceneThemes} 的按钮角色（未显式传 {@link Props#surface()} 时），
 * 显式配方优先于主题；{@code SceneTheme.solidDark()} 可表达旧实色观感。</p>
 *
 * <h3>组件函数形态（信条一）</h3>
 * <p>纯静态工厂 + 私有构造，控件类自身无任何实例字段（强制无状态）。
 * {@link #create} 返回 {@code Supplier<SceneNode>}，交 {@link SceneRuntime#mount} 执行一次（I3）：
 * 建树 + 设静态样式 + 绑定响应式派生。不是 fluent builder，不是持有节点的 setter 对象。</p>
 */
public final class SceneButton {

    /**
     * 内边距（像素）
     */
    private static final int PADDING = SceneChromeTokens.PAD_MD;

    /**
     * 纯静态工厂，禁止实例化（强制无状态，契约 R1）
     */
    private SceneButton() {
    }

    /**
     * Button 输入契约 —— 全部只读 signal + 输出回调（契约 R2）。
     *
     * @param label   文本内容（响应式只读）
     * @param enabled 是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onClick 动作输出回调，点击或 Enter/Space 激活时触发
     * @param variant 视觉变体，STANDARD 玻璃中性 / PRIMARY 强调 / DANGER 危险
     * @param surface 显式表面配方（可为 null = 跟随当前主题的按钮角色）
     */
    @Desugar
    public record Props(
        ReadableSignal<String> label,
        ReadableSignal<Boolean> enabled,
        Runnable onClick,
        SceneButtonVariant variant,
        ReadableSignal<SceneSurfaceStyle> surface
    ) {
        /**
         * 兼容三参构造器：variant 默认 STANDARD、配方跟随主题，保持旧调用方零改动。
         *
         * @param label   文本内容
         * @param enabled 启用信号
         * @param onClick 点击回调
         */
        public Props(ReadableSignal<String> label, ReadableSignal<Boolean> enabled, Runnable onClick) {
            this(label, enabled, onClick, SceneButtonVariant.STANDARD, null);
        }

        /**
         * 兼容四参构造器：配方跟随主题。
         *
         * @param label   文本内容
         * @param enabled 启用信号
         * @param onClick 点击回调
         * @param variant 视觉变体
         */
        public Props(ReadableSignal<String> label, ReadableSignal<Boolean> enabled, Runnable onClick,
                SceneButtonVariant variant) {
            this(label, enabled, onClick, variant, null);
        }
    }

    /**
     * 工厂：构建按钮组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（I3）：
     * 只建 SceneNode 树 + 设静态属性 + {@code rt.bind/bindText/on/focusable}，
     * 动态外观全落配方信号派生，交互只经 {@code on} 调回调（契约 R3/R4/R5）。</p>
     *
     * @param rt    场景运行时（提供 bind/on/interactionState/focusable）
     * @param props 按钮输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneButtonPrimitive.Props primitiveProps = new SceneButtonPrimitive.Props(
                props.label(), props.enabled(), props.onClick());
            SceneButtonPrimitive.Result result = SceneButtonPrimitive.create(rt, primitiveProps);
            SceneNode root = result.root();
            SceneInteractionState interaction = result.interaction();
            root.setPadding(PADDING);

            final SceneButtonVariant variant = props.variant() == null
                    ? SceneButtonVariant.STANDARD : props.variant();
            ReadableSignal<SceneSurfaceStyle> surface = props.surface() != null
                    ? props.surface()
                    : SceneThemes.surface(rt, role(variant));

            // 唯一外观写入者：表面绑定独占 background/border/borderWidth/cornerRadius/backdrop/
            // surfaceElevation；motionRoot=label 承担内容微移与禁用透明度，业务内容保留自身变换。
            SceneSurfaceBinder.bind(rt, root, result.label(), surface, props.enabled(), interaction);
            // 文字色：配方前景优先（按钮角色默认已带主题语义色），否则回落正文色。
            SceneSurfaceBinder.bindForeground(rt, result.label(), surface, SceneChromeTokens.TEXT_PRIMARY);

            SceneControlChrome.bindCursor(rt, root, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);

            return root;
        };
    }

    /** variant → 主题按钮角色映射。 */
    private static SceneTheme.Role role(SceneButtonVariant variant) {
        switch (variant) {
            case PRIMARY: return SceneTheme.Role.BUTTON_PRIMARY;
            case DANGER: return SceneTheme.Role.BUTTON_DANGER;
            default: return SceneTheme.Role.BUTTON_STANDARD;
        }
    }
}
