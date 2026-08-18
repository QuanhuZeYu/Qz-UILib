package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * SceneToast —— scene 非模态通知。
 *
 * <h3>能力</h3>
 * <ul>
 *   <li>{@link #show} 命令式投递：按 runtime 弱引用缓存单例 {@link Host}，多次 show 堆叠在
 *       屏幕底部（后到在下）；</li>
 *   <li>自动消失：帧时间（{@code runtime.__frameTimeNanos()}）驱动到期移除（真机每帧 tick，
 *       测试以 {@code __tickFrame} 驱动），默认 {@value #DEFAULT_DURATION_NANOS} 纳秒；</li>
 *   <li>非模态：overlay 整树 hitTestable=false，指针穿透到主树，不拦截输入、无关闭策略。</li>
 * </ul>
 */
public final class SceneToast {

    /** 默认展示时长（纳秒）：3s。 */
    public static final long DEFAULT_DURATION_NANOS = 3_000_000_000L;
    /** toast 内边距。 */
    private static final int TOAST_PAD_V = 8;
    /** toast 内边距。 */
    private static final int TOAST_PAD_H = 12;
    /** toast 背景（深色半透明）。 */
    private static final int TOAST_BG = 0xE6323036;

    /** runtime → Host 弱引用缓存（渲染线程约定单线程访问，加锁防御）。 */
    private static final Map<SceneRuntime, Host> HOSTS = Collections.synchronizedMap(new WeakHashMap<>());

    /** 纯静态工厂，禁止实例化。 */
    private SceneToast() {
    }

    /**
     * 展示一条默认时长（{@value #DEFAULT_DURATION_NANOS} 纳秒）的 toast。
     *
     * @param rt      场景运行时
     * @param message 通知文本
     */
    public static void show(SceneRuntime rt, String message) {
        show(rt, message, DEFAULT_DURATION_NANOS);
    }

    /**
     * 展示一条指定时长的 toast。
     *
     * @param rt            场景运行时
     * @param message       通知文本
     * @param durationNanos 展示时长（纳秒，≤0 按 1ns 处理）
     */
    public static void show(SceneRuntime rt, String message, long durationNanos) {
        if (rt == null) {
            throw new IllegalArgumentException("rt 不可为 null");
        }
        hostFor(rt).show(SceneTextUtils.nullSafe(message), Math.max(1, durationNanos));
    }

    /**
     * 获取（或创建）指定 runtime 的 toast host。
     */
    private static Host hostFor(SceneRuntime rt) {
        synchronized (HOSTS) {
            Host host = HOSTS.get(rt);
            if (host == null) {
                host = new Host(rt);
                HOSTS.put(rt, host);
            }
            return host;
        }
    }

    /**
     * 单条 toast 记录。
     *
     * @param id             唯一 id（forEach key）
     * @param message        通知文本
     * @param createdAtNanos 创建时刻帧时间（纳秒）
     * @param durationNanos  展示时长（纳秒）
     */
    @Desugar
    public record Entry(long id, String message, long createdAtNanos, long durationNanos) {
    }

    /**
     * 单 runtime toast host：持有 toast 列表信号与底部堆叠 overlay。
     *
     * <p>portal 可见性 = 列表非空；到期移除 effect 订阅帧时间信号，每次 tick 检查
     * 队首之后的所有条目（列表按创建序，先到者先到期）。</p>
     */
    public static final class Host {
        private final SceneRuntime rt;
        private final Signal<List<Entry>> entries = Signal.create(new ArrayList<>());
        private final AtomicLong idCounter = new AtomicLong();

        private Host(SceneRuntime rt) {
            this.rt = rt;
            rt.portalAnchored(
                    Computed.create(() -> Boolean.valueOf(!entries.get().isEmpty())),
                    () -> buildToastContainer(rt, entries),
                    OverlayDismissPolicy.NONE,
                    null,
                    null);
            // 到期移除：订阅帧时间；真机每帧 tick、测试 __tickFrame 驱动
            rt.bind(rt.__frameTimeNanos(), now -> {
                long t = now.longValue();
                List<Entry> current = entries.get();
                List<Entry> next = null;
                for (int i = 0; i < current.size(); i++) {
                    Entry entry = current.get(i);
                    if (t - entry.createdAtNanos() >= entry.durationNanos()) {
                        if (next == null) {
                            next = new ArrayList<>(current);
                        }
                        // remove(Object)：Entry record equals 含唯一 id，精确移除到期条目
                        next.remove(entry);
                    }
                }
                if (next != null) {
                    entries.set(next);
                }
            });
        }

        /**
         * 投递一条 toast（追加到堆叠尾部）。
         *
         * @param message       通知文本
         * @param durationNanos 展示时长（纳秒）
         */
        public void show(String message, long durationNanos) {
            List<Entry> next = new ArrayList<>(entries.get());
            next.add(new Entry(idCounter.incrementAndGet(), message,
                    rt.__frameTimeNanos().get().longValue(), durationNanos));
            entries.set(next);
        }

        /** @return 当前堆叠的 toast 条数（测试探针） */
        public int size() {
            return entries.get().size();
        }
    }

    /**
     * 构建 toast 堆叠 overlay root（全屏底部居中、整树不可命中）。
     */
    private static SceneNode buildToastContainer(SceneRuntime rt, Signal<List<Entry>> entries) {
        SceneNode container = SceneNode.column();
        container.setMainAxisAlign(MainAxisAlign.END);
        container.setCrossAxisAlign(CrossAxisAlign.CENTER);
        container.setHitTestable(false);
        container.setClipChildren(true);
        rt.forEach(container, entries, entry -> Long.valueOf(entry.id()),
                SceneToast::buildToast);
        return container;
    }

    /**
     * 构建单条 toast 节点。
     */
    private static SceneNode buildToast(Entry entry) {
        SceneNode toast = SceneNode.row();
        toast.setPadding(TOAST_PAD_V, TOAST_PAD_H, TOAST_PAD_V, TOAST_PAD_H);
        toast.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        toast.setBackgroundColor(TOAST_BG);
        toast.setHitTestable(false);

        SceneNode label = new SceneNode();
        label.setText(entry.message());
        label.setHitTestable(false);
        label.setTextColor(SceneChromeTokens.TEXT_PRIMARY);
        toast.appendChild(label);
        return toast;
    }
}
