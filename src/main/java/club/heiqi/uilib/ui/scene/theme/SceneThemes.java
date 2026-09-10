package club.heiqi.uilib.ui.scene.theme;

import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 主题作用域解析与安装。
 *
 * <p><b>优先级</b>（唯一权威）：
 * {@code 显式控件配方 > 显式局部/页面主题 > runtime 默认主题 > UILib 默认液态玻璃主题}。</p>
 *
 * <p><b>传播机制</b>：主题信号作为 {@link Owner} 的作用域上下文（{@link Owner#setScope}）挂在
 * 来源作用域上，子作用域沿父链继承。{@code SceneRuntime} 的 mount / show / forEach / portal
 * 四条构建路径都在「来源 Owner 的子作用域」内执行 builder，因此延迟构建、列表后插入、
 * 浮层重开都天然继承来源主题，无需在运行时里额外传参。</p>
 *
 * <p><b>独立 runtime 隔离</b>：runtime 默认主题安装在各自 {@code rootOwner} 的作用域上，
 * 两个 runtime 互不可见；库默认用只读常量信号，任何调用方都无法写入它。</p>
 *
 * <p><b>构造期解析约定</b>：{@link #resolve} 与 {@link #surface} 依赖调用时的
 * {@link Owner#current()}，必须在构建期（mount/show/forEach/portal 的 builder 内）调用。
 * 它们返回的信号在派生期只读上游主题信号，不再依赖 Owner 上下文——这是「主题更新驱动外观」
 * 与「effect 执行期没有 Owner 上下文」两件事能同时成立的关键。</p>
 */
public final class SceneThemes {

    /** 作用域上下文键：只在本类内使用，避免与其他模块的键冲突。 */
    private static final Object THEME_KEY = new Object();

    /** 库默认主题（深色液态玻璃档）。 */
    public static final SceneTheme DEFAULT = SceneTheme.liquidGlassDark();

    /** 库默认只读信号：任何调用方都无法写入，杜绝跨 runtime 串色。 */
    private static final ReadableSignal<SceneTheme> DEFAULT_THEME = new ReadableSignal<SceneTheme>() {
        @Override
        public SceneTheme get() {
            return DEFAULT;
        }
    };

    private SceneThemes() {
    }

    /**
     * 把主题安装为指定 runtime 的默认主题（写到该 runtime 的根作用域）。
     *
     * <p>同一 runtime 可重复安装，后安装者覆盖前者；安装动作只影响此后构建的控件与已绑定的
     * 主题派生（已绑定的派生会随信号更新而重算，不重建节点）。</p>
     *
     * @param rt    目标 runtime，不可为 null
     * @param theme 主题信号，不可为 null；其值在消费时不得为 null
     */
    public static void install(SceneRuntime rt, ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(theme, "theme");
        rt.__runRoot(() -> {
            Owner owner = Owner.current();
            if (owner != null) {
                owner.setScope(THEME_KEY, theme);
            }
        });
    }

    /**
     * 解析当前外观来源主题：先沿 {@link Owner} 父链找局部/页面主题，再回落该 runtime 的
     * 默认主题，最后回落库默认。返回的信号恒非 null，且其值在正常使用下非 null。
     *
     * <p>无当前 Owner 时（effect 体内、非构建期）经 runtime 根作用域读取默认主题，
     * 因此不会误取另一个 runtime 的默认值。</p>
     *
     * @param rt 目标 runtime（用于归属校验与根作用域回退），不可为 null
     * @return 主题只读信号
     */
    public static ReadableSignal<SceneTheme> resolve(SceneRuntime rt) {
        Objects.requireNonNull(rt, "rt");
        Owner current = Owner.current();
        if (current != null) {
            ReadableSignal<SceneTheme> scoped = findTheme(current);
            if (scoped != null) {
                return scoped;
            }
        }
        final ReadableSignal<SceneTheme>[] holder = new ReadableSignal[1];
        rt.__runRoot(() -> holder[0] = findTheme(Owner.current()));
        return holder[0] != null ? holder[0] : DEFAULT_THEME;
    }

    @SuppressWarnings("unchecked")
    private static ReadableSignal<SceneTheme> findTheme(Owner owner) {
        return owner == null ? null : owner.findScope(THEME_KEY, ReadableSignal.class);
    }

    /**
     * 在当前 Owner 下建立一个携带局部主题的子作用域，并立即执行 {@code body}。
     *
     * <p>body 内构建的控件（含其 show/forEach/portal 延迟内容）继承该主题，直到作用域被销毁。
     * 嵌套调用形成更窄的覆盖。无当前 Owner 时（例如在 effect 体内直接调用）退化为直接执行
     * body，使用 runtime 默认主题——控件构建应始终发生在 builder 内，不依赖该退化路径。</p>
     *
     * @param theme 局部主题信号，不可为 null
     * @param body  构建逻辑，不可为 null
     */
    public static void withTheme(ReadableSignal<SceneTheme> theme, Runnable body) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(body, "body");
        Owner current = Owner.current();
        if (current == null) {
            body.run();
            return;
        }
        Owner scope = current.createChild();
        scope.setScope(THEME_KEY, theme);
        scope.run(body);
    }

    /**
     * 当前来源主题下某角色的配方信号（主题切换时自动重算，不重建节点）。
     *
     * <p>必须在构建期调用：构造期捕获来源主题信号，派生期只读该信号。</p>
     *
     * @param rt   目标 runtime，不可为 null
     * @param role 材质角色，不可为 null
     * @return 配方只读信号
     */
    public static ReadableSignal<SceneSurfaceStyle> surface(SceneRuntime rt, SceneTheme.Role role) {
        Objects.requireNonNull(role, "role");
        ReadableSignal<SceneTheme> theme = resolve(rt);
        final SceneTheme[] holder = new SceneTheme[1];
        // 初值在非追踪上下文读取：避免在 effect 体内调用时把主题登记成外层 effect 的依赖。
        Effect.untrack(() -> holder[0] = Objects.requireNonNull(theme.get(), "theme value"));
        SceneTheme initial = holder[0];
        return Computed.create(initial.surface(role), () -> {
            SceneTheme value = Objects.requireNonNull(theme.get(), "theme value");
            return value.surface(role);
        });
    }

    /**
     * 正文前景的只读派生（主题切换自动重算）。
     *
     * @param rt 目标 runtime，不可为 null
     * @return 前景色信号
     */
    public static ReadableSignal<Integer> foreground(SceneRuntime rt) {
        return color(rt, theme -> theme.foreground());
    }

    /**
     * 次要/占位前景的只读派生。
     *
     * @param rt 目标 runtime，不可为 null
     * @return 次要前景色信号
     */
    public static ReadableSignal<Integer> mutedForeground(SceneRuntime rt) {
        return color(rt, theme -> theme.mutedForeground());
    }

    /**
     * 禁用前景的只读派生。
     *
     * @param rt 目标 runtime，不可为 null
     * @return 禁用前景色信号
     */
    public static ReadableSignal<Integer> disabledForeground(SceneRuntime rt) {
        return color(rt, theme -> theme.disabledForeground());
    }

    /**
     * 强调底前景的只读派生。
     *
     * @param rt 目标 runtime，不可为 null
     * @return 强调底前景色信号
     */
    public static ReadableSignal<Integer> onAccentForeground(SceneRuntime rt) {
        return color(rt, theme -> theme.onAccentForeground());
    }

    /**
     * 强调色的只读派生。
     *
     * @param rt 目标 runtime，不可为 null
     * @return 强调色信号
     */
    public static ReadableSignal<Integer> accent(SceneRuntime rt) {
        return color(rt, theme -> theme.accent());
    }

    /**
     * 聚焦边框色的只读派生。
     *
     * @param rt 目标 runtime，不可为 null
     * @return 聚焦边框色信号
     */
    public static ReadableSignal<Integer> borderFocus(SceneRuntime rt) {
        return color(rt, theme -> theme.borderFocus());
    }

    /**
     * 默认边框色的只读派生。
     *
     * <p>与 {@link #borderFocus} 配套：需要「非聚焦态也跟随主题」的边框消费点用它，
     * 不要静态取 {@code SceneChromeTokens.BORDER_DEFAULT}——后者与深色档同值，
     * 在浅色档下不会跟随。</p>
     *
     * @param rt 目标 runtime，不可为 null
     * @return 默认边框色信号
     */
    public static ReadableSignal<Integer> borderDefault(SceneRuntime rt) {
        return color(rt, theme -> theme.borderDefault());
    }

    /**
     * 禁用边框色的只读派生。
     *
     * @param rt 目标 runtime，不可为 null
     * @return 禁用边框色信号
     */
    public static ReadableSignal<Integer> borderDisabled(SceneRuntime rt) {
        return color(rt, theme -> theme.borderDisabled());
    }

    /**
     * 文本选区背景的只读派生。
     *
     * @param rt 目标 runtime，不可为 null
     * @return 选区背景色信号
     */
    public static ReadableSignal<Integer> selectionBackground(SceneRuntime rt) {
        return color(rt, theme -> theme.selectionBackground());
    }

    /**
     * 文本选区前景的只读派生。
     *
     * @param rt 目标 runtime，不可为 null
     * @return 选区前景色信号
     */
    public static ReadableSignal<Integer> selectionForeground(SceneRuntime rt) {
        return color(rt, theme -> theme.selectionForeground());
    }

    /**
     * 警告文本前景的只读派生（G19/P-02 收编：各控件不再自建 warningText 私有派生）。
     *
     * <p>本槽是<b>文本级</b>语义色：深色档 {@code 0xFFFBBF24} 对 PANEL 名义底
     * （{@code 0xFF2B2930}）约 8.60:1、浅色档 {@code 0xFF8B5000} 约 6.15:1，达 WCAG AA。
     * 警告提示文字、徽章前景等「读出来」的内容用它；危险动作<b>底色</b>用
     * {@link #danger(SceneRuntime)}，勿混用。</p>
     *
     * @param rt 目标 runtime，不可为 null
     * @return 警告前景色信号
     */
    public static ReadableSignal<Integer> warningText(SceneRuntime rt) {
        return color(rt, theme -> theme.warningText());
    }

    /**
     * 错误文本前景的只读派生（G19/P-02 收编：KeyValueMap 的构造期 resolve + effect 内
     * 取值等规避写法全部改走本入口）。
     *
     * <p>本槽是<b>文本级</b>语义色：深色档 {@code 0xFFFFB4AB} 对 PANEL 名义底约 8.46:1、
     * 浅色档 {@code 0xFF8C1D18} 约 8.66:1，达 WCAG AA。校验错误文字等「读出来」的错误
     * 语义用它；半透明弱提示底色（如 KeyValueMap 错误行）可取本槽 RGB 再自行降 alpha，
     * 但纯色底请用 {@link #danger(SceneRuntime)} 并保证前景对比。</p>
     *
     * @param rt 目标 runtime，不可为 null
     * @return 错误前景色信号
     */
    public static ReadableSignal<Integer> errorText(SceneRuntime rt) {
        return color(rt, theme -> theme.errorText());
    }

    /**
     * 危险动作<b>表面/徽章底图色</b>的只读派生——不是文本色！
     *
     * <p>深色档 {@code 0xFF7F1D1D} 对 PANEL 名义底对比度仅约 <b>1.43:1</b>（G19 实算），
     * 远低于文本可读阈值：本槽只适合做危险元素底色、徽章底、描边等着色面，其上文字必须
     * 另行保证对比（如 {@link #onAccentForeground(SceneRuntime)} 系浅色前景）。需要「错误
     * 文字」语义时用 {@link #errorText(SceneRuntime)}，两者是不同 role，禁止互相顶替。</p>
     *
     * @param rt 目标 runtime，不可为 null
     * @return 危险底图色信号
     */
    public static ReadableSignal<Integer> danger(SceneRuntime rt) {
        return color(rt, theme -> theme.danger());
    }

    /**
     * 「成功」语义前景的只读派生。<b>当前委托 {@link #accent(SceneRuntime)}（G19/P-02
     * 裁决：临时借道，非最终观感定档）。</b>
     *
     * <p><b>裁决理由</b>：{@code SceneTheme} 的冻结分量清单（契约 §2.3）没有 success 槽；
     * 增设分量需要一对「对三档名义底均 ≥4.5:1 且不引入新色板来源」的干净绿值——G19 用
     * WCAG 实算扫描候选（M3 green80/green40/green500 均过 dark 档 6.30~8.24:1 但对浅色档
     * 名义底仅 1.66~2.17:1；深色绿 {@code 0xFF006C34} 反之过 light 6.25:1 不过 dark 2.18:1），
     * 单一值无解、双值属自造色板（契约 §5 起步色板无绿），故本入口<b>暂委托 accent</b>，
     * 与 G15/Shell「成功态用 accent」既有裁决同源同值。届时若主代理批准增设
     * {@code SceneTheme.successText()} 分量，只改本方法实现为
     * {@code color(rt, theme -> theme.successText())}，各消费点（Toast 成功色点、Shell 成功
     * 反馈等）经本入口自动跟随，无需逐控件再收编。</p>
     *
     * <p><b>对比度警示</b>：深色档 accent（{@code 0xFF4F378B}）对 PANEL 名义底仅约 1.54:1，
     * 成功语义用于<b>正文级长文本</b>时可读性不足，现阶段适合色点、徽标、短反馈文本的强调
     * 着色；宿主如需成功正文文本，优先走 {@link #foreground(SceneRuntime)} 并另加非颜色
     * 区分（图标/文案）。</p>
     *
     * @param rt 目标 runtime，不可为 null
     * @return 成功语义前景信号（现值恒等于同主题 accent）
     */
    public static ReadableSignal<Integer> successText(SceneRuntime rt) {
        return color(rt, SceneTheme::accent);
    }

    /**
     * 当前来源主题下某字体槽位的字号派生（主题切换自动重算，不重建节点）。
     *
     * <p>与语义色派生同形状：构造期捕获来源主题信号，初值在非追踪上下文读取，派生期只读该信号。
     * 消费方按用途取槽（见 {@link SceneTheme.FontSlot}），不自行定数值。</p>
     *
     * <p><b>这是唯一的排版度量主题通道</b>：字号影响布局几何与绘制输出，属契约 §4 单列的
     * 「排版度量」行——主题可经本入口接管字号，但不得接管 padding、尺寸等几何布局属性。</p>
     *
     * @param rt   目标 runtime，不可为 null
     * @param slot 字体槽位，不可为 null
     * @return 字号信号（UI 像素，恒 &gt; 0）
     */
    public static ReadableSignal<Integer> fontSize(SceneRuntime rt, SceneTheme.FontSlot slot) {
        Objects.requireNonNull(slot, "slot");
        return color(rt, theme -> theme.fontSize(slot));
    }

    /**
     * 当前来源主题的主题级行距倍数派生（主题切换自动重算）。
     *
     * <p>值为 {@code 0} 表示本主题不接管行距，消费方保持自动行高；非 0 时按
     * {@code ceil(自动行高 × 倍数)} 生效（交由 {@code SceneNode#setLineHeightMultiplier} 解释，
     * 本入口不做第二套算术）。与 {@link #fontSize} 同属契约 §4 的「排版度量」行。</p>
     *
     * @param rt 目标 runtime，不可为 null
     * @return 行距倍数信号（有限且 &gt;= 0）
     */
    public static ReadableSignal<Double> lineHeightMultiplier(SceneRuntime rt) {
        ReadableSignal<SceneTheme> theme = resolve(rt);
        final double[] holder = new double[1];
        Effect.untrack(() -> holder[0] = Objects.requireNonNull(theme.get(), "theme value")
                .lineHeightMultiplier());
        final double initial = holder[0];
        return Computed.create(Double.valueOf(initial), () -> Double.valueOf(
                Objects.requireNonNull(theme.get(), "theme value").lineHeightMultiplier()));
    }

    /**
     * 选中/未选中切换的角色配方：未选中用 role 配方，选中态用主题强调色替换 tint 的 RGB
     * （保留原 alpha 与 edge/elevation/lens/圆角），disabled 仍走 role 的禁用档。
     *
     * <p>选中不能只靠透明度区分；本方法让各控件共享同一套选中外观，不各自拼色。</p>
     *
     * @param rt       目标 runtime，不可为 null
     * @param role     材质角色，不可为 null
     * @param selected 是否选中，不可为 null
     * @return 配方只读信号
     */
    public static ReadableSignal<SceneSurfaceStyle> selectableSurface(SceneRuntime rt, SceneTheme.Role role,
            ReadableSignal<Boolean> selected) {
        Objects.requireNonNull(selected, "selected");
        ReadableSignal<SceneSurfaceStyle> base = surface(rt, role);
        ReadableSignal<SceneTheme> theme = resolve(rt);
        final SceneSurfaceStyle[] holder = new SceneSurfaceStyle[1];
        Effect.untrack(() -> holder[0] = resolveSelectable(
                Objects.requireNonNull(base.get(), "surface"),
                Boolean.TRUE.equals(selected.get()),
                Objects.requireNonNull(theme.get(), "theme value")));
        return Computed.create(holder[0], () -> resolveSelectable(
                Objects.requireNonNull(base.get(), "surface"),
                Boolean.TRUE.equals(selected.get()),
                Objects.requireNonNull(theme.get(), "theme value")));
    }

    private static SceneSurfaceStyle resolveSelectable(SceneSurfaceStyle style, boolean selected, SceneTheme theme) {
        if (!selected) {
            return style;
        }
        return accentOf(style, theme);
    }

    /**
     * 强调版角色配方：tint 的 RGB 换成主题强调色、alpha 取 {@link #SELECTED_TINT_ALPHA}，
     * edge/elevation/lens/圆角保持角色配方。
     *
     * @param rt   目标 runtime，不可为 null
     * @param role 材质角色，不可为 null
     * @return 配方只读信号
     */
    public static ReadableSignal<SceneSurfaceStyle> accentSurface(SceneRuntime rt, SceneTheme.Role role) {
        ReadableSignal<SceneSurfaceStyle> base = surface(rt, role);
        ReadableSignal<SceneTheme> theme = resolve(rt);
        final SceneSurfaceStyle[] holder = new SceneSurfaceStyle[1];
        Effect.untrack(() -> holder[0] = accentOf(
                Objects.requireNonNull(base.get(), "surface"),
                Objects.requireNonNull(theme.get(), "theme value")));
        return Computed.create(holder[0], () -> accentOf(
                Objects.requireNonNull(base.get(), "surface"),
                Objects.requireNonNull(theme.get(), "theme value")));
    }

    /**
     * 角色配方的字段级派生：以当前来源主题的某角色配方为基线，经 {@code override} 变换后作为新配方。
     * 用于「主题基线 + 局部覆盖」——消费方只改自己管辖的字段，其余属性仍随主题。
     *
     * <p>必须在构建期调用（同 {@link #surface}）：构造期捕获来源主题信号并注入初值，派生期只读上游。</p>
     *
     * <p><b>{@code override} 必须是纯函数</b>：它在派生 effect 内执行，内部读到的任何信号都会自动
     * 注册为失效源——这正是「业务设置变化」与「主题变化」能作为两个独立失效源共存的原因。读信号
     * 之外的外部可变状态会让重算时机不可预测；这类取值应先包成 {@link ReadableSignal} 再在函数内读。</p>
     *
     * <p>返回 {@link Computed} 而非 {@link ReadableSignal}：{@code Computed} 是其子类型，调用方按
     * {@code ReadableSignal} 消费不受影响，同时保留 {@link Computed#dispose()}，供自持 {@link Owner}
     * 作用域的场景显式回收 recompute 单元。</p>
     *
     * @param rt       目标 runtime，不可为 null
     * @param role     材质角色，不可为 null
     * @param override 基线的字段级变换，不可为 null 且必须为纯函数
     * @return 派生配方信号（构造期已注入初值）
     */
    public static Computed<SceneSurfaceStyle> derivedSurface(SceneRuntime rt, SceneTheme.Role role,
            UnaryOperator<SceneSurfaceStyle> override) {
        Objects.requireNonNull(override, "override");
        ReadableSignal<SceneSurfaceStyle> base = surface(rt, role);
        final SceneSurfaceStyle[] holder = new SceneSurfaceStyle[1];
        // 初值在非追踪上下文读取：避免在 effect 体内调用时把主题登记成外层 effect 的依赖（同 surface）。
        Effect.untrack(() -> holder[0] = override.apply(Objects.requireNonNull(base.get(), "surface")));
        return Computed.create(holder[0], () -> override.apply(Objects.requireNonNull(base.get(), "surface")));
    }

    private static SceneSurfaceStyle accentOf(SceneSurfaceStyle style, SceneTheme theme) {
        return style.toBuilder()
                .idle(tinted(style.getIdle(), theme.accent()))
                .hovered(tinted(style.getHovered(), theme.accentHover()))
                .pressed(tinted(style.getPressed(), theme.accentPressed()))
                .build();
    }

    /**
     * 选中/强调表面的染色强度：角色配方自带的 alpha（如 INDICATOR 的 0x0E≈5.5%）只适合
     * 未选中态，选中态必须用更高 alpha 才能读出「已选中」，否则只剩色相差。
     */
    private static final int SELECTED_TINT_ALPHA = 0x59;

    private static SceneSurfaceStyle.StateStyle tinted(SceneSurfaceStyle.StateStyle state, int argb) {
        return new SceneSurfaceStyle.StateStyle((SELECTED_TINT_ALPHA << 24) | (argb & 0xFFFFFF),
                state.getEdge(), state.getElevation(), state.getLensFactor());
    }

    /** 语义色派生的公共实现：构造期捕获来源主题，初值在非追踪上下文读取。 */
    private static ReadableSignal<Integer> color(SceneRuntime rt, ToIntFunction<SceneTheme> extractor) {
        Objects.requireNonNull(extractor, "extractor");
        ReadableSignal<SceneTheme> theme = resolve(rt);
        final int[] holder = new int[1];
        Effect.untrack(() -> holder[0] = extractor.applyAsInt(Objects.requireNonNull(theme.get(), "theme value")));
        final int initial = holder[0];
        return Computed.create(Integer.valueOf(initial),
                () -> Integer.valueOf(extractor.applyAsInt(Objects.requireNonNull(theme.get(), "theme value"))));
    }
}
