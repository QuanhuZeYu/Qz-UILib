package club.heiqi.uilib.ui.render;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * 背景滤镜档位的<b>进程级唯一持有者</b>——「配置 → 信号 → 订阅方重派生」链路的中间段。
 *
 * <h3>两种读法的分工（同一份状态、两条消费通道）</h3>
 * <ul>
 *   <li>{@link #current()}：渲染热路径读法。读一个 volatile 字段，<b>零分配、不建立依赖追踪</b>、
 *       写入即刻可见（不经调度器）。{@code UiBackdropFilterRenderer} 与
 *       {@code BackdropBlurPolicy.resolveEnabled} 每帧都会读它，故绝不能在这里触发订阅注册。</li>
 *   <li>{@link #quality()}：订阅通道。值经 {@link Signal#set(Object)} 进
 *       {@link club.heiqi.uilib.ui.reactive.ReactiveScheduler}，宿主帧末 flush 生效并只通知
 *       真正变化的订阅方（主题侧据此重派生 {@code withoutBackdrop()} 配方，无需重建节点）。</li>
 * </ul>
 *
 * <p>两条通道的值在 {@link #applyConfigured(String)} 内一次写入：热路径立即按新档位渲染，
 * 订阅方在帧末完成重派生。也就是说档位切换的观感收敛发生在下一帧、且不需要重开页面——
 * 这是「配置 → 进程级信号 → 订阅方重派生」的既定语义，不是延迟生效的缺陷。</p>
 *
 * <h3>唯一写入口</h3>
 * <p>{@code config.modern.ConfigValueBridge} 在三条既有配置通道（启动加载、保存回调、磁盘重载）
 * 上调用 {@link #applyConfigured(String)}，与 {@code PickerDensityPreferences} 的接线形态一致；
 * 本类不监视文件、不逐帧轮询，也不持有任何节点/面板引用（与进程同寿命，无需释放）。</p>
 *
 * <h3>失效与回落</h3>
 * <p>非法值回落 {@link BackdropQuality#FULL} 并留 WARN：坏配置不得让 UI 起不来，也不得静默吞掉。
 * 同值写入被 {@code current()} 的相等判断拦下，不产生空转的信号写入。</p>
 */
public final class BackdropQualityService {

    /** 配置项全路径（与 schema 声明处一致，唯一真值来源）。 */
    public static final String CONFIG_PATH = "general.backdropQuality";

    /** 进程级唯一实例：与进程同寿命，不随配置页开关增删。 */
    private static final BackdropQualityService INSTANCE = new BackdropQualityService();

    /** 订阅通道载体；初值 = 默认档 FULL（未配置时的行为与引入档位前一致）。 */
    private final Signal<BackdropQuality> qualitySignal = Signal.create(BackdropQuality.FULL);

    /** 热路径读法的事实来源：写入立即生效，不等待调度器 flush。 */
    private volatile BackdropQuality current = BackdropQuality.FULL;

    private BackdropQualityService() {
    }

    /**
     * 获取进程级唯一实例。
     *
     * @return 服务实例
     */
    public static BackdropQualityService getInstance() {
        return INSTANCE;
    }

    /**
     * 回灌配置值（唯一写入口）。解析越界回落 {@link BackdropQuality#FULL} 并留 WARN；幂等。
     *
     * @param raw 配置里的档位名，可为 {@code null}
     */
    public void applyConfigured(String raw) {
        BackdropQuality parsed = BackdropQuality.parse(raw);
        warnIfUnparsable(raw, parsed);
        if (parsed == current) {
            // 幂等：同值不重排信号写入，避免配置页反复保存造成的空转重派生。
            return;
        }
        current = parsed;
        qualitySignal.set(parsed);
    }

    /**
     * 每帧热路径读取当前档位（零分配、不建立依赖追踪；写入即刻可见）。
     *
     * @return 非 null 当前档位
     */
    public BackdropQuality current() {
        return current;
    }

    /**
     * 档位订阅信号（供主题等订阅方动态重派生）；帧末 flush 后生效，返回进程级同一实例。
     *
     * @return 非 null 只读信号
     */
    public ReadableSignal<BackdropQuality> quality() {
        return qualitySignal;
    }

    /**
     * 测试隔离：把进程级状态复位为 {@link BackdropQuality#FULL}。
     *
     * <p>包内可见——生产唯一写入口是 {@link #applyConfigured(String)}；订阅通道的复位同样经调度器，
     * 调用方需自行 flush 后再断言。</p>
     */
    void resetForTest() {
        current = BackdropQuality.FULL;
        qualitySignal.set(BackdropQuality.FULL);
    }

    /** 非法取值 WARN：只报一次（每次配置回灌一次），消息里带原值便于现场定位手改配置。 */
    private static void warnIfUnparsable(String raw, BackdropQuality parsed) {
        if (raw == null) {
            return;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || parsed.configValue().equalsIgnoreCase(trimmed)) {
            return;
        }
        MyMod.LOG.warn("配置项 {} 的值 \"{}\" 不是合法档位（full/eco/solid），"
                + "已回落 full（完整档，观感与引入档位前一致）", CONFIG_PATH, raw);
    }
}
