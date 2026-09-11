package club.heiqi.uilib.resource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IReloadableResourceManager;

/**
 * ResourceReloadService —— UILib 统一的「资源包重载」失效总线（进程单例）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §2.1 #1（A9：Miner 不自行注册 reload listener）。
 * 现状 UILib 全仓唯一 reload listener 只服务字体（{@code MixinFontRenderer.onResourceManagerReload}
 * → FontService），语言切换与资源包重载在 UILib 侧<b>没有任何通用通道</b>；本类是该缺口的正式通道：
 * 不新增 mixin、不改字体通道，二者并行不合并。</p>
 *
 * <h3>语义</h3>
 * <ul>
 *   <li>{@link #resourceEpoch()}：单调不减的 {@code long}，每次重载回调 +1；资源代际的<b>唯一</b>发布点
 *       （图标缓存清空、候选源 {@code iconRevision} 摄入都以它为准）。</li>
 *   <li>监听列表有界：随 Owner 注销（消费者在 Owner 作用域注册时经
 *       {@code SceneRuntime.__onCleanup} 归还）；服务自身只持一个 long + 一份监听列表，无每帧成本。</li>
 *   <li>回调在主线程（1.7.10 重载链在客户端主线程完成），监听器同步顺序执行；单个监听器异常被隔离，
 *       不影响代际推进与其它监听器。</li>
 * </ul>
 *
 * <h3>注册与幂等（S-05）</h3>
 * <p>注册路径：{@code Minecraft.getResourceManager()}（静态返回 {@code IResourceManager}）→
 * {@code instanceof IReloadableResourceManager} → {@code registerReloadListener}。
 * <b>1.7.10 的 {@code SimpleReloadableResourceManager.registerReloadListener} 会立刻回调一次</b>，
 * 故实例注册成功后 {@code resourceEpoch} 立即从 0 变 1（初始态可见），本类据此保证幂等：
 * 重复注册直接返回 false，不重复挂载、不重复计数。</p>
 */
public final class ResourceReloadService implements IResourceManagerReloadListener {

    /** 资源重载监听器（消费资源代际）。 */
    public interface Listener {

        /**
         * @param resourceEpoch 重载后的资源代际（单调不减）
         */
        void onResourceReload(long resourceEpoch);
    }

    private static final Logger LOG = LogManager.getLogger("QzUiLib/ResourceReload");

    private static final ResourceReloadService INSTANCE = new ResourceReloadService();

    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private volatile long resourceEpoch;
    private volatile boolean registered;

    private ResourceReloadService() {
    }

    /** @return 进程单例 */
    public static ResourceReloadService getInstance() {
        return INSTANCE;
    }

    /** @return 当前资源代际（单调不减；O(1) long 读，可每帧读） */
    public long resourceEpoch() {
        return resourceEpoch;
    }

    /** @return 是否已挂到客户端资源管理器 */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * 注册监听器（重复注册同实例为幂等空操作）。
     *
     * @param listener 监听器；null 忽略
     */
    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 注销监听器。
     *
     * @param listener 监听器
     */
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** @return 当前监听器数量（诊断/有界性探针） */
    public int listenerCount() {
        return listeners.size();
    }

    /**
     * 客户端装配期调用：把本服务挂到客户端资源管理器（幂等）。
     *
     * @return 是否已注册（无客户端实例或资源管理器不可重载时返回 false）
     */
    public boolean registerToClient() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                return false;
            }
            IResourceManager manager = minecraft.getResourceManager();
            if (!(manager instanceof IReloadableResourceManager)) {
                return false;
            }
            return register((IReloadableResourceManager) manager);
        } catch (Throwable unavailable) {
            // headless 测试 JVM 下 Minecraft 的静态初始化会失败（LWJGL DisplayMode 链接缺失），
            // 专用服务端 JVM 下客户端类整体缺席：本方法只探测「有没有可重载资源管理器」，
            // 探测不到即未注册（不抛、不阻断其它引导步骤）。
            LOG.warn("资源重载通道未注册（无客户端运行时）：{}", unavailable.toString());
            return false;
        }
    }

    /**
     * 注册到指定可重载资源管理器（幂等；供客户端装配与测试共用）。
     *
     * @param manager 可重载资源管理器
     * @return 本次是否完成了注册
     */
    public boolean register(IReloadableResourceManager manager) {
        if (manager == null || registered) {
            return false;
        }
        registered = true;
        try {
            // 1.7.10 注册即同步回调一次本服务的 onResourceManagerReload（幂等前提见类 javadoc）。
            manager.registerReloadListener(this);
            return true;
        } catch (RuntimeException exception) {
            registered = false;
            LOG.warn("资源重载通道注册失败（保持未注册，不影响字体通道）", exception);
            return false;
        }
    }

    /**
     * 资源重载回调：O(1) 推进代际后同步通知监听器（不在此重建任何缓存——重建由首个消费者惰性触发）。
     *
     * @param resourceManager 重载后的资源管理器（可为 null，本服务不使用其内容）
     */
    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        long next;
        synchronized (this) {
            resourceEpoch++;
            next = resourceEpoch;
        }
        for (Listener listener : listeners) {
            try {
                listener.onResourceReload(next);
            } catch (RuntimeException exception) {
                LOG.warn("资源重载监听器异常（已隔离，不影响代际推进）", exception);
            }
        }
    }

    /** 测试用：清空代际与监听器（不影响已挂载的注册状态）。 */
    public void __resetForTests() {
        synchronized (this) {
            resourceEpoch = 0L;
        }
        listeners.clear();
    }

    /** 测试用：复位注册状态（允许在无客户端环境下重复做注册幂等验证）。 */
    public void __resetRegistrationForTests() {
        registered = false;
    }
}
