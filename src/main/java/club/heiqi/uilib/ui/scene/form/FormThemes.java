package club.heiqi.uilib.ui.scene.form;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;


public final class FormThemes {

    /** 字段 label 字号：与 {@link FormTheme#defaultDark()} 既有值一致，主题不接管字号。 */
    private static final int FONT_LABEL = 16;
    /** helper text 字号：同上。 */
    private static final int FONT_HELPER = 13;
    /** error text 字号：同上。 */
    private static final int FONT_ERROR = 13;
    /** 多行字段默认视口高度：同上，与表单视口空间同源。 */
    private static final int LIST_HEIGHT = 220;

    /** 工具类，禁止实例化。 */
    private FormThemes() {
    }

    /**
     * 解析当前来源主题下的表单模板主题（主题切换自动重算，不重建节点）。
     *
     * <p><b>构造期调用</b>：与 {@link SceneThemes#resolve(SceneRuntime)} 同一纪律——必须在构建期
     * （{@code mount/show/forEach/portal} 的 builder 内）调用，此时 {@code Owner.current()} 是来源
     * 作用域，局部/页面主题才能被捕获。返回的信号在派生期只读构造期捕获的来源主题信号，
     * 不再依赖 Owner 上下文。</p>
     *
     * <p><b>初值</b>：来源主题的当前值在构造期于非追踪上下文（{@code Effect.untrack}）读一次，
     * 作为派生信号的初值，因此 {@code flush} 前读取也不会拿到 null；构造期不解引用未求值的
     * Computed，也不把来源主题登记成外层 effect 的依赖。</p>
     *
     * @param rt 目标 runtime，不可为 null
     * @return 表单主题只读信号（恒非 null，其值恒非 null）
     */
    public static ReadableSignal<FormTheme> resolve(SceneRuntime rt) {
        Objects.requireNonNull(rt, "rt");
        ReadableSignal<SceneTheme> theme = SceneThemes.resolve(rt);
        final FormTheme[] holder = new FormTheme[1];
        // 初值在非追踪上下文读取：构建期即可拿到非 null 值，且不污染外层 effect 依赖。
        Effect.untrack(() -> holder[0] = of(Objects.requireNonNull(theme.get(), "theme value")));
        final FormTheme initial = holder[0];
        return Computed.create(initial, () -> of(Objects.requireNonNull(theme.get(), "theme value")));
    }

    /**
     * 把主题值对象映射为表单模板主题的纯函数（便于测试与显式桥接，不读 Owner、不建信号）。
     *
     * <p>映射口径见类头表格；本方法对同一入参恒返回值相等的 {@link FormTheme}。布局与字号
     * 分量与主题无关，恒取既有常量，故主题切换只改颜色语义、不改页面几何。</p>
     *
     * @param theme 来源主题，不可为 null
     * @return 表单模板主题（新实例，不可变）
     */
    public static FormTheme of(SceneTheme theme) {
        Objects.requireNonNull(theme, "theme");
        SceneSurfaceStyle group = theme.surface(SceneTheme.Role.GROUP);
        int panelBackground = opaquePanelBackground(theme.surface(SceneTheme.Role.PANEL));
        return new FormTheme(
                group.getIdle().getTint(),          // cardBg
                group.getIdle().getEdge(),          // cardBorder
                theme.accent(),                     // cardBorderDirty
                theme.errorText(),                  // cardBorderError
                SceneChromeTokens.RADIUS_LG,        // cardRadius
                SceneChromeTokens.PAD_LG,           // cardPad
                SceneChromeTokens.GAP_MD,           // fieldGap
                theme.foreground(),                 // textColor
                theme.mutedForeground(),            // mutedColor
                theme.errorText(),                  // errorColor
                theme.accent(),                     // dirtyColor
                FONT_LABEL,                         // fontLabel
                FONT_HELPER,                        // fontHelper
                FONT_ERROR,                         // fontError
                SceneChromeTokens.INPUT_HEIGHT,     // inputHeight
                LIST_HEIGHT,                        // listHeight
                panelBackground,                    // rootBg
                panelBackground,                    // viewportBg
                theme.foreground()                  // titleColor
        );
    }

    /**
     * 面板角色的静态页底色：tint 自身不透明时采用，否则回落不透明替代底色。
     *
     * <p>页根/视口底色由 {@code FormPageShell} 直接写成节点背景色，没有滤镜承载半透明
     * tint（理由见类头）。{@link SceneTheme#withoutBackdrop()} 与 {@link SceneTheme#solidDark()}
     * 的 PANEL tint 本就不透明，走「采用」分支，两种档位下页底色都保持可读。</p>
     *
     * @param panel PANEL 角色配方，不可为 null
     * @return 不透明 ARGB 底色
     */
    private static int opaquePanelBackground(SceneSurfaceStyle panel) {
        int tint = panel.getIdle().getTint();
        return (tint >>> 24) == 0xFF ? tint : SceneTheme.FALLBACK_BG;
    }
}
