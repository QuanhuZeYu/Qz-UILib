package club.heiqi.uilib.ui.scene.runtime;

import java.util.function.Supplier;

import club.heiqi.uilib.font.layout.FontSizeLimits;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 字号入口绑定：把一个「构造期定值 / 运行期信号」桥到某个 {@link SceneNode} 的层 2 槽
 * （{@link SceneNode#setFontScope(int)}）。
 *
 * <p><b>唯一效应点</b>：每个入口最多 1 个 {@link Effect}（不是每个文字节点 1 个），
 * 且重复 bind 先释放旧 effect 再建新的 —— 幂等替换语义。由此「每调用一次叠一个 effect」
 * 在机制上不可能出现。</p>
 *
 * <h3>只持 Effect，不持 Binding</h3>
 * <p>{@link Binding} 的构造器是包私有，只由 {@link SceneRuntime#bind} 产出；入口侧用公共的
 * {@link Owner#createEffect(Runnable)} 即可拿到幂等替换所需的句柄。</p>
 *
 * <h3>写入语义（跨册会签）</h3>
 * <ul>
 *   <li>{@link #set(int)}：构建期定值；越界抛 {@link IllegalArgumentException}；
 *       <b>先释放既有信号订阅</b>再写声明（后写者胜出）。</li>
 *   <li>{@link #bind(ReadableSignal)}：运行期信号；<b>参数 null = 完全 no-op</b>
 *       （既不建绑定也不清除既有声明）；值 {@code null} = 该声明缺失，回落到下一层。</li>
 *   <li>{@link #clear()}：清除本入口的声明并释放 effect，目标回落下一层。</li>
 * </ul>
 */
final class FontSizeBinding {

    /** 目标来源：返回当前生效目标；返回 null 表示目标尚未构建。 */
    private final Supplier<SceneNode> targetSource;

    /** 声明归属作用域（effect 生命周期）。 */
    private final Owner owner;

    /** retarget() 显式指定的目标。 */
    private SceneNode explicitTarget;

    /** 是否已调用过 retarget()；false 时回落到 targetSource。 */
    private boolean explicitTargetSet;

    /** 当前声明值；null = 未声明。 */
    private Integer declared;

    /** 唯一效应点；null = 无信号驱动。 */
    private Effect effect;

    /** 信号路径因越界被钳制的次数（诊断与守卫用）。 */
    private int clampedFromSignalCount;

    /**
     * 构造绑定。
     *
     * @param owner        声明归属作用域，不可为 null
     * @param targetSource 目标来源（返回当前目标，可为 null 表示尚未构建），不可为 null
     */
    FontSizeBinding(Owner owner, Supplier<SceneNode> targetSource) {
        this.owner = owner;
        this.targetSource = targetSource;
    }

    /**
     * 构建期定值入口：同步写；越界抛 {@link IllegalArgumentException}。
     *
     * <p><b>先释放既有信号订阅再写声明</b>（后写者胜出）：一旦本入口写入定值，之前 bind 过的
     * 信号不再影响本入口 —— 否则两个来源同时驱动、语义不可判定。释放必须在同值去重<b>之前</b>
     * 执行（值相同也要解除订阅）。</p>
     *
     * @param fontSizePx UI 逻辑像素字号
     * @throws IllegalArgumentException 越界（合法区间由 {@link FontSizeLimits} 唯一定义）
     */
    void set(int fontSizePx) {
        int valid = FontSizeLimits.requireValidFontSize(fontSizePx);
        if (effect != null) {
            effect.dispose();
            effect = null;
        }
        if (declared != null && declared.intValue() == valid) {
            return;
        }
        declared = Integer.valueOf(valid);
        apply();
    }

    /**
     * 运行期信号入口：先播种当前值（首帧正确），再建唯一 effect；幂等替换。
     *
     * <p><b>null 判定必须最先执行</b>：参数 null = 完全 no-op —— 既不建绑定，
     * 也不清除既有声明、不释放既有 effect。</p>
     *
     * @param signal 字号信号；null = 完全 no-op
     */
    void bind(ReadableSignal<Integer> signal) {
        if (signal == null) {
            return;
        }
        if (effect != null) {
            effect.dispose();
            effect = null;
        }
        setFromSignal(signal.get());
        effect = owner.createEffect(() -> setFromSignal(signal.get()));
    }

    /**
     * 清除本入口的声明：本入口不再向目标节点施加字号，目标回落下一层。
     *
     * <p>同时释放 effect —— 否则信号下一次变化会把声明重新写回，语义自相矛盾。</p>
     */
    void clear() {
        if (effect != null) {
            effect.dispose();
            effect = null;
        }
        declared = null;
        SceneNode target = target();
        if (target != null) {
            target.resetFontScope();
        }
    }

    /**
     * 切换或指定目标（浮层内容构建边界）。
     *
     * @param target 新目标；null = 清除显式目标并回落到 targetSource
     */
    void retarget(SceneNode target) {
        this.explicitTargetSet = true;
        this.explicitTarget = target;
        if (declared != null) {
            apply();
        }
    }

    /** @return 当前声明值；null = 未声明。 */
    Integer declared() {
        return declared;
    }

    /** @return 本源是否由信号驱动（供优先级溯源与诊断使用）。 */
    boolean isSignalDriven() {
        return effect != null;
    }

    /** @return 信号路径因越界被钳制的次数。 */
    int clampedFromSignalCount() {
        return clampedFromSignalCount;
    }

    /**
     * 信号路径写值：{@code null} = 该声明缺失 → 清除声明回落下一层；
     * 越界 → 钳制到域内并计数（effect 体内不得 fail-fast）。
     */
    private void setFromSignal(Integer value) {
        if (value == null) {
            declared = null;
            SceneNode target = target();
            if (target != null) {
                target.resetFontScope();
            }
            return;
        }
        int raw = value.intValue();
        int valid = FontSizeLimits.clampFontSize(raw);
        if (valid != raw) {
            clampedFromSignalCount++;
        }
        if (declared != null && declared.intValue() == valid) {
            return;
        }
        declared = Integer.valueOf(valid);
        apply();
    }

    private SceneNode target() {
        return explicitTargetSet ? explicitTarget : targetSource.get();
    }

    private void apply() {
        SceneNode target = target();
        if (target == null || declared == null) {
            return;
        }
        target.setFontScope(declared.intValue());
    }
}
