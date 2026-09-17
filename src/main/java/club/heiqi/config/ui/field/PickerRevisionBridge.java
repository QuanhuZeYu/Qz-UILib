package club.heiqi.config.ui.field;

import java.util.function.LongSupplier;

import club.heiqi.config.ui.editor.PickerCandidateSource;
import club.heiqi.config.ui.editor.PickerEnvironment;
import club.heiqi.config.ui.editor.PickerSourceVersion;
import club.heiqi.uilib.ui.env.UiEnvironment;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * PickerRevisionBridge —— 候选源版本号的「推 / 拉」桥（UILib 侧，每帧常数次读取）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §2.4（A3/A9/Z-4/S-12）。每帧两件事：</p>
 * <ol>
 *   <li><b>推</b>：读环境代际（{@link club.heiqi.uilib.ui.env.LocaleEnvironment#nameEpoch()} /
 *       {@link club.heiqi.uilib.ui.env.ResourceEnvironment#resourceEpoch()}，均 O(1) long 读，
 *       来自<b>构造注入的宿主环境端口</b>），与桥内上次值比对；
 *       <b>有变化才</b>调 {@link PickerCandidateSource#onEnvironmentChanged(long, long)}
 *       （主线程、同步、无分配）。环境代际只经此通道下行——候选源不轮询、不自注册 reload listener；</li>
 *   <li><b>拉</b>：读 {@link PickerCandidateSource#version()} 三段，与上次发布值比对，
 *       <b>变化才</b> {@code set} 到 {@link #versionSignal()} → 依赖它的 Computed 自动重算（无逐帧 if 门控）。</li>
 * </ol>
 *
 * <p>读点复用既有帧时间信号（{@link SceneRuntime#__frameTimeNanos()}），经 {@link #bindTo(SceneRuntime)}
 * 挂载；每帧成本 = 2 次 long 读 + 1 次 SPI 调用 + 3 段 long 比对，<b>桥自身零新分配</b>
 * （只有版本真变化时才构造一次 {@link PickerSourceVersion} 并 set）。</p>
 *
 * <p><b>不预热</b>（A10）：本桥只读版本号，不触发候选物化；候选重建由「首个窗口/查询请求」在源内部惰性发生。</p>
 */
public final class PickerRevisionBridge {

    private final PickerCandidateSource source;
    private final LongSupplier nameEpoch;
    private final LongSupplier resourceEpoch;
    private final LongSupplier tierGeneration;
    /** 是否发布过至少一次（首帧必须发布源的真实版本，此后无变化不再 set）。 */
    private boolean hasPublished;
    /** 已发布到 signal 的三段（拆开存，热路径不构造对象）。 */
    private long publishedRegistry;
    private long publishedName;
    private long publishedIcon;
    /** 已推送的环境代际（null = 尚未推送基线）。 */
    private PickerEnvironment pushedEnvironment;
    private int pushCount;
    private int pullPublishCount;
    private final Signal<PickerSourceVersion> versionSignal;

    /**
     * 创建桥（环境代际与分级代际经 supplier 注入，便于单测）。
     *
     * @param source        候选源（非 null）
     * @param nameEpoch     文本代际读取器（非 null）
     * @param resourceEpoch 资源代际读取器（非 null）
     * @param tierGeneration 分级代际读取器（非 null）
     */
    public PickerRevisionBridge(PickerCandidateSource source, LongSupplier nameEpoch,
                                LongSupplier resourceEpoch, LongSupplier tierGeneration) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (nameEpoch == null || resourceEpoch == null || tierGeneration == null) {
            throw new IllegalArgumentException("epoch suppliers must not be null");
        }
        this.source = source;
        this.nameEpoch = nameEpoch;
        this.resourceEpoch = resourceEpoch;
        this.tierGeneration = tierGeneration;
        // 初值 = 全零：首帧「拉」必发布一次源的真实版本（消费者据此建立初始依赖），此后无变化不再 set。
        this.versionSignal = Signal.create(PickerSourceVersion.initial());
    }

    /**
     * 生产形态：环境代际取宿主环境端口的两个域，分级代际取分级表。
     *
     * <p>环境由装配点传入而非在此直读进程单例：直读会让本桥在 headless 出图 / 测试里无法替换环境
     * 事实（配置界面在无头进程里同样会装配到本桥）。端口缺席值与「未安装语言 / 资源服务」逐位等价
     * （{@code nameEpoch}=0、{@code resourceEpoch}=0），故生产行为不变。</p>
     *
     * @param source      候选源
     * @param environment 宿主环境端口（不可为 null；无环境事实传 {@link UiEnvironment#empty()}）
     * @return 桥
     */
    public static PickerRevisionBridge forSource(PickerCandidateSource source, UiEnvironment environment) {
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null；"
                    + "无环境事实请传 UiEnvironment.empty()");
        }
        return new PickerRevisionBridge(source,
                environment.locale()::nameEpoch,
                environment.resources()::resourceEpoch,
                ItemRenderTierRegistry::tierGeneration);
    }

    /**
     * 挂到宿主帧信号（每帧推进时执行 {@link #tick()}）。
     *
     * <p>effect 归属调用方 Owner 作用域，随组件/屏卸载回收；宿主未推进帧时间的环境（headless 单测）
     * 不自动 tick，需显式调用 {@link #tick()}。</p>
     *
     * @param rt 场景运行时（非 null）
     */
    public void bindTo(SceneRuntime rt) {
        if (rt == null) {
            throw new IllegalArgumentException("rt must not be null");
        }
        rt.bind(rt.__frameTimeNanos(), nanos -> tick());
    }

    /**
     * 每帧动作：先推环境代际（有变化才推），再拉源版本（有变化才发布）。主线程调用。
     */
    public void tick() {
        PickerSourceGuard.requireMainThread("version");
        pushEnvironment();
        pullVersion();
    }

    /** 推：环境代际有变化才下行（首次 tick 推一次基线，保证源与桥对「上次摄入值」有共同起点）。 */
    private void pushEnvironment() {
        long name = nameEpoch.getAsLong();
        long resource = resourceEpoch.getAsLong();
        PickerEnvironment previous = pushedEnvironment;
        if (previous != null && previous.nameEpoch() == name && previous.resourceEpoch() == resource) {
            return;
        }
        pushedEnvironment = new PickerEnvironment(name, resource);
        pushCount++;
        source.onEnvironmentChanged(name, resource);
    }

    /** 拉：三段有任何变化才发布（拆开比较，热路径零分配）。 */
    private void pullVersion() {
        PickerSourceVersion current = source.version();
        if (current == null) {
            return;
        }
        long registry = current.registry();
        long name = current.name();
        long icon = current.icon();
        if (hasPublished && registry == publishedRegistry && name == publishedName && icon == publishedIcon) {
            return;
        }
        hasPublished = true;
        publishedRegistry = registry;
        publishedName = name;
        publishedIcon = icon;
        pullPublishCount++;
        versionSignal.set(new PickerSourceVersion(registry, name, icon));
    }

    /** @return 已发布版本的可读信号（Computed 依赖它自动重算） */
    public ReadableSignal<PickerSourceVersion> versionSignal() {
        return versionSignal;
    }

    /** @return 最近一次发布的三段版本（未发布过时为零值） */
    public PickerSourceVersion version() {
        return new PickerSourceVersion(publishedRegistry, publishedName, publishedIcon);
    }

    /**
     * @return 当前四段合成代际：三段取已发布版本，tier 段读分级表当前代际（O(1)）
     */
    public PickerGeneration generation() {
        return new PickerGeneration(publishedRegistry, publishedName, publishedIcon, tierGeneration.getAsLong());
    }

    /** @return 已下行推送次数（诊断/A-04 证据：不推送则恒为 1，即仅有基线推送） */
    public int pushCount() {
        return pushCount;
    }

    /** @return 已发布到 signal 的次数（诊断/A-04 证据：版本不变则不增长） */
    public int publishCount() {
        return pullPublishCount;
    }
}
