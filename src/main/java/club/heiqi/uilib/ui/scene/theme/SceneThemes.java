package club.heiqi.uilib.ui.scene.theme;

import java.util.Objects;

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
}
