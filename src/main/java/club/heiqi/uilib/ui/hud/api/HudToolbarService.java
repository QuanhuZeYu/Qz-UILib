package club.heiqi.uilib.ui.hud.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * HUD 外接工具栏注册表（每个 HUD 至多一条工具栏；候选公共 API）。
 *
 * <p>把"某个 HUD 的外侧挂一条工具栏"声明在 HUD 级别，而不是写死在某个容器组件里：
 * 宿主 {@code SceneHudHost} 与打开态页面（聊天输入屏）都从本表取同一份
 * {@link HudToolbarSpec} 与工厂，经 {@link HudToolbarLayer} 装配，四边语义、厚度与可见性
 * 只有一处真相。聊天 HUD（{@code qzuilib:chat3}）是首个使用者。</p>
 *
 * <p>契约：</p>
 * <ul>
 *   <li>同一 hudId 重复注册明确拒绝（不静默覆盖）；注册与注销限客户端主线程；</li>
 *   <li>跨宿主生命周期保持注册，宿主每帧按 {@link #revision()} 判断是否需要重建已保留窗口；</li>
 *   <li>工厂只在装配时调用一次，返回 null 视为装配失败（由调用方隔离）；</li>
 *   <li>工具栏可见性属于规格的 {@link HudToolbarSpec#getVisible()}，注册表不做二次可见性判断；</li>
 *   <li><b>缩放不属于工具栏</b>：每 HUD 倍率由统一缩放注册表（{@link #scale(String)}）持有，
 *       未注册外接工具栏的 HUD 同样拥有自己的倍率；工具栏注册与注销不重置倍率。</li>
 * </ul>
 */
public final class HudToolbarService {

    private static final HudToolbarService INSTANCE = new HudToolbarService();

    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
    /** 注册表版本（增删时 +1；宿主据此重建已保留窗口）。 */
    private final Signal<Integer> revision = Signal.create(Integer.valueOf(0));
    private int revisionValue;

    private HudToolbarService() {
    }

    /** @return 全局注册表单例 */
    public static HudToolbarService getInstance() {
        return INSTANCE;
    }

    /**
     * 注册某 HUD 的外接工具栏。
     *
     * @param hudId   目标 HUD id（非空白；与 {@link HudSpec#getId()} 同域）
     * @param spec    工具栏规格（不可为 null）
     * @param factory 工具栏工厂（不可为 null）
     * @return 幂等注销句柄
     */
    public synchronized HudRegistration register(String hudId, HudToolbarSpec spec,
            HudWindowFactory factory) {
        requireId(hudId);
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(factory, "factory");
        if (entries.containsKey(hudId)) {
            throw new IllegalArgumentException("duplicate HUD toolbar id: " + hudId);
        }
        final Entry entry = new Entry(spec, factory);
        entries.put(hudId, entry);
        bump();
        return new Registration(new Runnable() {
            @Override
            public void run() {
                remove(entry);
            }
        });
    }

    /** @return 该 HUD 是否已注册外接工具栏 */
    public synchronized boolean hasToolbar(String hudId) {
        return hudId != null && entries.containsKey(hudId);
    }

    /** @return 该 HUD 的工具栏规格；未注册返回 null */
    public synchronized HudToolbarSpec spec(String hudId) {
        Entry entry = hudId == null ? null : entries.get(hudId);
        return entry == null ? null : entry.spec;
    }

    /** @return 该 HUD 的工具栏工厂；未注册返回 null */
    public synchronized HudWindowFactory factory(String hudId) {
        Entry entry = hudId == null ? null : entries.get(hudId);
        return entry == null ? null : entry.factory;
    }

    /**
     * 每 HUD 统一缩放状态（惰性创建，限客户端主线程读取与写入）。
     *
     * <p>缩放是 HUD 自身能力，**不依赖外接工具栏注册**：未注册工具栏的 HUD 同样拿到自己的
     * 倍率，宿主与打开态/编辑态页面读同一份状态。注册或注销工具栏都不会重置倍率
     * （倍率生命周期独立于工具栏）；整体清理见 {@link #clear()}。</p>
     *
     * @param hudId 目标 HUD id
     * @return 该 HUD 的统一缩放状态；hudId 为 null/空白时返回 null
     */
    public synchronized HudScaleState scale(String hudId) {
        return HudScaleRegistry.getInstance().get(hudId);
    }

    /** @return 注册表版本（增删时 +1） */
    public ReadableSignal<Integer> revision() {
        return revision;
    }

    /**
     * 按注册表装配外接层；未注册时返回直通结果（外框 = 内容）。
     *
     * @param rt      宿主场景运行时
     * @param hudId   目标 HUD id
     * @param content 内容根
     * @return 挂载结果
     */
    public synchronized HudToolbarLayer.Result mountLayer(SceneRuntime rt, String hudId,
            SceneNode content) {
        Entry entry = hudId == null ? null : entries.get(hudId);
        if (entry == null) {
            return HudToolbarLayer.passthrough(content);
        }
        return HudToolbarLayer.mount(rt, entry.spec, content, entry.factory,
                HudScaleRegistry.getInstance().get(hudId));
    }

    /** 清空工具栏注册表与统一缩放状态（测试与整体关闭用）；已返回句柄失效。 */
    public synchronized void clear() {
        HudScaleRegistry.getInstance().clear();
        if (entries.isEmpty()) {
            return;
        }
        entries.clear();
        bump();
    }

    private synchronized void remove(Entry entry) {
        if (entries.containsValue(entry)) {
            entries.values().remove(entry);
            bump();
        }
    }

    private static void requireId(String hudId) {
        if (hudId == null || hudId.trim().isEmpty()) {
            throw new IllegalArgumentException("hudId must not be blank");
        }
    }

    private void bump() {
        revisionValue++;
        revision.set(Integer.valueOf(revisionValue));
    }

    private static final class Entry {
        final HudToolbarSpec spec;
        final HudWindowFactory factory;
        Entry(HudToolbarSpec spec, HudWindowFactory factory) {
            this.spec = spec;
            this.factory = factory;
        }
    }

    /** 幂等注销句柄（与 {@link HudRegistration} 同语义）。 */
    private static final class Registration implements HudRegistration {
        private final Runnable closer;
        private final AtomicBoolean closed = new AtomicBoolean();
        Registration(Runnable closer) { this.closer = closer; }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) {
                closer.run();
            }
        }
        @Override public boolean isClosed() { return closed.get(); }
    }
}
