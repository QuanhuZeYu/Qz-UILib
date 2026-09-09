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

/**
 * 表单模板族主题桥：把当前来源 {@link SceneTheme} 派生为表单模板消费的 {@link FormTheme}。
 *
 * <p>表单模板族（{@code FormPageShell}/{@code FormFieldShell}/{@code FormActionBar}）吃的是
 * 单一 {@link FormTheme} 值对象，而全库默认外观由 {@link SceneThemes} 的作用域主题驱动。
 * 本类是两者之间唯一的解析桥：不传显式 {@code FormTheme} 的调用方经 {@link #resolve(SceneRuntime)}
 * 取「当前来源主题派生出的表单主题」，主题切换自动重算；显式传 {@code FormTheme} 的旧调用
 * 语义完全不变（{@link FormTheme#defaultDark()} 保持原值，未被本桥改写）。</p>
 *
 * <h2>派生映射（按契约 §4.1 角色与语义色）</h2>
 *
 * <table border="1">
 *   <caption>FormTheme 分量 → 主题来源</caption>
 *   <tr><th>分量</th><th>来源</th></tr>
 *   <tr><td>{@code cardBg} / {@code cardBorder}</td>
 *       <td>{@code surface(rt, Role.GROUP)} 的 idle tint / edge</td></tr>
 *   <tr><td>{@code cardBorderDirty}</td><td>主题 {@code accent()}</td></tr>
 *   <tr><td>{@code cardBorderError}</td><td>主题 {@code errorText()}</td></tr>
 *   <tr><td>{@code textColor} / {@code titleColor}</td><td>主题 {@code foreground()}</td></tr>
 *   <tr><td>{@code mutedColor}</td><td>主题 {@code mutedForeground()}</td></tr>
 *   <tr><td>{@code errorColor}</td><td>主题 {@code errorText()}</td></tr>
 *   <tr><td>{@code dirtyColor}</td><td>主题 {@code accent()}</td></tr>
 *   <tr><td>{@code rootBg} / {@code viewportBg}</td>
 *       <td>{@code surface(rt, Role.PANEL)} 的 idle tint；该 tint 不透明时直接采用，
 *           否则回落 {@link SceneTheme#FALLBACK_BG} 不透明替代底色</td></tr>
 *   <tr><td>{@code cardRadius} / {@code cardPad} / {@code fieldGap} / {@code inputHeight}</td>
 *       <td>既有 {@link SceneChromeTokens} 常量（主题不接管布局）</td></tr>
 *   <tr><td>{@code fontLabel} / {@code fontHelper} / {@code fontError} / {@code listHeight}</td>
 *       <td>{@link FormTheme#defaultDark()} 既有值（主题不接管字号与视口高度）</td></tr>
 * </table>
 *
 * <p><b>底色不透明化的理由</b>：{@code rootBg}/{@code viewportBg} 由
 * {@code FormPageShell} 直接写进 {@code SceneNode.setBackgroundColor}，节点上<b>没有</b>
 * 玻璃滤镜承载半透明 tint；把液态玻璃档的 PANEL idle tint（如 {@code 0x14EAF7FF}，
 * alpha≈8%）原样当页底色会让世界画面直接透出、标题不可读。故只在 tint 自身不透明
 * （{@code alpha == 0xFF}）时采用它，否则用 {@link SceneTheme#FALLBACK_BG}——即
 * {@code SceneTheme.withoutBackdrop()} 给面板角色的同一替代底色，语义与契约 §5 一致。</p>
 *
 * <p><b>零 config 依赖</b>：本类不 import 任何 {@code club.heiqi.config.*}；config 侧的
 * {@code ConfigTheme.asFormTheme()} 仍是「显式主题」入口，与本桥并存不互斥。</p>
 *
 * <p><b>分层</b>：本类位于 {@code ui.scene.form}，只依赖 {@code ui.reactive}、
 * {@code ui.scene.paint}、{@code ui.scene.runtime}、{@code ui.scene.theme}，
 * 不 import {@code ui.scene.control}、{@code internal.*}，也不依赖 Minecraft/LWJGL
 * （{@code theme} 包亦不反向依赖本包，无环）。</p>
 *
 * @see SceneThemes
 * @see FormTheme
 */
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
