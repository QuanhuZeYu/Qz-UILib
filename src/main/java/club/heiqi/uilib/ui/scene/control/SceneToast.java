package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 *       屏幕底部（后到在下）；portal/到期绑定经 {@code rt.__runRoot} 挂 root owner，
 *       页面切换不中断通知服务；</li>
 *   <li>自动消失：帧时间（{@code runtime.__frameTimeNanos()}）驱动到期（真机每帧 tick，
 *       测试以 {@code __tickFrame} 驱动），默认 {@value #DEFAULT_DURATION_NANOS} 纳秒；
 *       到期先进入 {@value #LEAVE_DURATION_NANOS} 纳秒退场淡出再移除；</li>
 *   <li>出现/退场动画：淡入 + 自下方 {@value #ENTER_OFFSET_Y}px 上移、淡出，纯帧时间驱动，
 *       仅写 opacity/presentationOffset（composite/geometry 级，不改布局与输入几何）；</li>
 *   <li>内容宽度：条目按内容收缩（{@link SceneNode.WidthSizing#SHRINK}）并在窗口水平居中，
 *       不再占满整行；容器 fillParentHeight 使底部堆叠真正生效；</li>
 *   <li>类型化：{@link Type} 区分 INFO/SUCCESS/WARNING/ERROR，条目带类型色点；</li>
 *   <li>非模态：overlay 整树 hitTestable=false，指针穿透到主树，不拦截输入、无关闭策略。</li>
 * </ul>
 */
public final class SceneToast {

    /** 默认展示时长（纳秒）：3s。 */
    public static final long DEFAULT_DURATION_NANOS = 3_000_000_000L;
    /** 出现动画时长（纳秒，与 {@code SceneChromeTokens.MOTION_STANDARD_MS} 一致）。 */
    public static final long ENTER_DURATION_NANOS = 160_000_000L;
    /** 退场动画时长（纳秒）。 */
    public static final long LEAVE_DURATION_NANOS = 160_000_000L;

    /** 出现动画 Y 位移（px，自下方上移到位）。 */
    private static final int ENTER_OFFSET_Y = 8;
    /** toast 内边距。 */
    private static final int TOAST_PAD_V = 8;
    /** toast 内边距。 */
    private static final int TOAST_PAD_H = 12;
    /** toast 背景（深色半透明）。 */
    private static final int TOAST_BG = 0xE6323036;
    /** 类型色点尺寸（px）。 */
    private static final int TYPE_DOT_SIZE = 8;
    /** 类型色点与文本间距。 */
    private static final int TYPE_DOT_GAP = 8;

    /** 类型强调色（INFO：主题强调淡紫；SUCCESS：Material green；WARNING：Amber；ERROR：Material red）。 */
    private static final int TYPE_COLOR_INFO = 0xFFD0BCFF;
    private static final int TYPE_COLOR_SUCCESS = 0xFF81C784;
    private static final int TYPE_COLOR_WARNING = 0xFFFBBF24;
    private static final int TYPE_COLOR_ERROR = 0xFFE57373;

    /** runtime → Host 弱引用缓存（渲染线程约定单线程访问，加锁防御）。 */
    private static final Map<SceneRuntime, Host> HOSTS = Collections.synchronizedMap(new WeakHashMap<>());

    /** 纯静态工厂，禁止实例化。 */
    private SceneToast() {
    }

    /** 通知类型。 */
    public enum Type {
        /** 普通信息。 */
        INFO,
        /** 成功反馈。 */
        SUCCESS,
        /** 警告。 */
        WARNING,
        /** 错误。 */
        ERROR
    }

    /**
     * 展示一条默认时长（{@value #DEFAULT_DURATION_NANOS} 纳秒）的 INFO toast。
     *
     * @param rt      场景运行时
     * @param message 通知文本
     */
    public static void show(SceneRuntime rt, String message) {
        show(rt, Type.INFO, message, DEFAULT_DURATION_NANOS);
    }

    /**
     * 展示一条指定时长的 INFO toast。
     *
     * @param rt            场景运行时
     * @param message       通知文本
     * @param durationNanos 展示时长（纳秒，≤0 按 1ns 处理）
     */
    public static void show(SceneRuntime rt, String message, long durationNanos) {
        show(rt, Type.INFO, message, durationNanos);
    }

    /**
     * 展示一条指定类型的 toast（默认时长）。
     *
     * @param rt      场景运行时
     * @param type    通知类型（null 按 INFO）
     * @param message 通知文本
     */
    public static void show(SceneRuntime rt, Type type, String message) {
        show(rt, type, message, DEFAULT_DURATION_NANOS);
    }

    /**
     * 展示一条指定类型与时长（纳秒，≤0 按 1ns 处理）的 toast。
     *
     * @param rt            场景运行时
     * @param type          通知类型（null 按 INFO）
     * @param message       通知文本
     * @param durationNanos 展示时长（纳秒）
     */
    public static void show(SceneRuntime rt, Type type, String message, long durationNanos) {
        if (rt == null) {
            throw new IllegalArgumentException("rt 不可为 null");
        }
        Type safeType = type == null ? Type.INFO : type;
        hostFor(rt).show(safeType, SceneTextUtils.nullSafe(message), Math.max(1, durationNanos));
    }

    /** 展示一条 INFO toast（默认时长）。 */
    public static void showInfo(SceneRuntime rt, String message) {
        show(rt, Type.INFO, message, DEFAULT_DURATION_NANOS);
    }

    /** 展示一条 INFO toast（指定时长）。 */
    public static void showInfo(SceneRuntime rt, String message, long durationNanos) {
        show(rt, Type.INFO, message, durationNanos);
    }

    /** 展示一条 SUCCESS toast（默认时长）。 */
    public static void showSuccess(SceneRuntime rt, String message) {
        show(rt, Type.SUCCESS, message, DEFAULT_DURATION_NANOS);
    }

    /** 展示一条 SUCCESS toast（指定时长）。 */
    public static void showSuccess(SceneRuntime rt, String message, long durationNanos) {
        show(rt, Type.SUCCESS, message, durationNanos);
    }

    /** 展示一条 WARNING toast（默认时长）。 */
    public static void showWarning(SceneRuntime rt, String message) {
        show(rt, Type.WARNING, message, DEFAULT_DURATION_NANOS);
    }

    /** 展示一条 WARNING toast（指定时长）。 */
    public static void showWarning(SceneRuntime rt, String message, long durationNanos) {
        show(rt, Type.WARNING, message, durationNanos);
    }

    /** 展示一条 ERROR toast（默认时长）。 */
    public static void showError(SceneRuntime rt, String message) {
        show(rt, Type.ERROR, message, DEFAULT_DURATION_NANOS);
    }

    /** 展示一条 ERROR toast（指定时长）。 */
    public static void showError(SceneRuntime rt, String message, long durationNanos) {
        show(rt, Type.ERROR, message, durationNanos);
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
     * @param type           通知类型
     * @param message        通知文本
     * @param createdAtNanos 创建时刻帧时间（纳秒）
     * @param durationNanos  展示时长（纳秒）
     * @param leavingAtNanos 进入退场动画的时刻帧时间（纳秒），0 表示尚未退场
     */
    @Desugar
    public record Entry(long id, Type type, String message,
                        long createdAtNanos, long durationNanos, long leavingAtNanos) {

        /** @return 标记在指定时刻进入退场动画的新 Entry（其余字段不变） */
        public Entry enteringLeave(long leavingAt) {
            return new Entry(id, type, message, createdAtNanos, durationNanos, leavingAt);
        }
    }

    /**
     * 单 runtime toast host：持有 toast 列表信号与底部堆叠 overlay。
     *
     * <p>portal 可见性 = 列表非空；每帧 tick 推进「展示 → 退场 → 移除」状态机并写动画属性。
     * 列表按创建序，先到者先到期。</p>
     */
    public static final class Host {
        private final SceneRuntime rt;
        private final Signal<List<Entry>> entries = Signal.create(new ArrayList<>());
        private final AtomicLong idCounter = new AtomicLong();
        /** entry id → toast 节点（条目移除时显式清理，避免弱引用装箱语义不可靠）。 */
        private final Map<Long, SceneNode> nodeByEntryId = Collections.synchronizedMap(new HashMap<>());

        private Host(SceneRuntime rt) {
            this.rt = rt;
            // runtime 级资源挂 root owner：页面切换不中断通知服务（portal/到期绑定与 runtime 同寿）
            rt.__runRoot(() -> {
                rt.portalAnchored(
                        Computed.create(() -> Boolean.valueOf(!entries.get().isEmpty())),
                        () -> buildToastContainer(rt, this),
                        OverlayDismissPolicy.NONE,
                        null,
                        null);
                rt.bind(rt.__frameTimeNanos(), now -> tick(now.longValue()));
            });
        }

        /**
         * 投递一条 toast（追加到堆叠尾部）。
         *
         * @param type          通知类型
         * @param message       通知文本
         * @param durationNanos 展示时长（纳秒）
         */
        public void show(Type type, String message, long durationNanos) {
            List<Entry> next = new ArrayList<>(entries.get());
            next.add(new Entry(idCounter.incrementAndGet(), type, message,
                    rt.__frameTimeNanos().get().longValue(), durationNanos, 0L));
            entries.set(next);
        }

        /** @return 当前堆叠的 toast 条数（测试探针） */
        public int size() {
            return entries.get().size();
        }

        /**
         * 每帧：推进到期/退场状态机并写动画属性。
         *
         * <p>构建式更新：next 从空开始逐条追加（跳过退场完成的、替换进入退场的），
         * 绝不边改边用原列表索引——remove 缩短列表后按原索引 set 会错位覆盖相邻条目
         * （曾致「原条目 + leaving 副本」同 id 双份 → forEach 重复 key 崩溃，
         * 见 ERROR-20260818）。</p>
         */
        private void tick(long nowNanos) {
            List<Entry> current = entries.get();
            if (current.isEmpty()) {
                return;
            }
            List<Entry> next = null;
            for (int i = 0; i < current.size(); i++) {
                Entry entry = current.get(i);
                long leavingAt = entry.leavingAtNanos();
                if (leavingAt > 0 && nowNanos - leavingAt >= LEAVE_DURATION_NANOS) {
                    // 退场动画完成 → 跳过即移除（不进入 next）
                    if (next == null) {
                        next = new ArrayList<>(current.subList(0, i));
                    }
                    nodeByEntryId.remove(Long.valueOf(entry.id()));
                    continue;
                }
                if (leavingAt == 0 && nowNanos - entry.createdAtNanos() >= entry.durationNanos()) {
                    // 展示时长耗尽 → 标记进入退场（视觉可读时长不变，退场为附加段）
                    entry = entry.enteringLeave(nowNanos);
                    if (next == null) {
                        next = new ArrayList<>(current.subList(0, i));
                    }
                    next.add(entry);
                } else if (next != null) {
                    next.add(entry);
                }
                applyAnimation(entry, nowNanos);
            }
            if (next != null) {
                entries.set(next);
            }
        }

        /**
         * 写单条 toast 的出现/退场动画属性（opacity 与 presentation offset）。
         */
        private void applyAnimation(Entry entry, long nowNanos) {
            SceneNode node = nodeByEntryId.get(Long.valueOf(entry.id()));
            if (node == null) {
                return;
            }
            float enter = progress(nowNanos - entry.createdAtNanos(), ENTER_DURATION_NANOS);
            float leave = entry.leavingAtNanos() > 0
                    ? progress(nowNanos - entry.leavingAtNanos(), LEAVE_DURATION_NANOS) : 0f;
            node.setOpacity(Math.min(enter, 1f - leave));
            node.__setPresentationOffsetY(Math.round(ENTER_OFFSET_Y * (1f - enter)));
        }

        private static float progress(long elapsedNanos, long durationNanos) {
            if (elapsedNanos <= 0L) {
                return 0f;
            }
            if (elapsedNanos >= durationNanos) {
                return 1f;
            }
            return (float) ((double) elapsedNanos / (double) durationNanos);
        }

        /**
         * 构建单条 toast 节点（forEach itemComponent，每 key 只调一次）。
         */
        SceneNode buildToast(Entry entry) {
            SceneNode toast = SceneNode.row();
            // 内容宽度收缩：在 column 容器 STRETCH 下不被拉满，由容器 cross CENTER 水平居中
            toast.setWidthSizing(SceneNode.WidthSizing.SHRINK);
            toast.setCrossAxisAlign(CrossAxisAlign.CENTER);
            toast.setGap(TYPE_DOT_GAP);
            toast.setPadding(TOAST_PAD_V, TOAST_PAD_H, TOAST_PAD_V, TOAST_PAD_H);
            toast.setCornerRadius(SceneChromeTokens.RADIUS_MD);
            toast.setBackgroundColor(TOAST_BG);
            toast.setHitTestable(false);
            // 初值与首帧动画一致：挂载 flush 前保持不可见，避免终态闪帧
            toast.setOpacity(0f);
            toast.__setPresentationOffsetY(ENTER_OFFSET_Y);

            SceneNode dot = new SceneNode();
            dot.setPreferredWidth(TYPE_DOT_SIZE);
            dot.setPreferredHeight(TYPE_DOT_SIZE);
            dot.setCornerRadius(TYPE_DOT_SIZE / 2);
            dot.setBackgroundColor(typeColor(entry.type()));
            dot.setHitTestable(false);
            toast.appendChild(dot);

            SceneNode label = new SceneNode();
            label.setText(entry.message());
            label.setHitTestable(false);
            label.setTextColor(SceneChromeTokens.TEXT_PRIMARY);
            toast.appendChild(label);

            nodeByEntryId.put(Long.valueOf(entry.id()), toast);
            return toast;
        }
    }

    /**
     * 构建 toast 堆叠 overlay root（全高、底部堆叠、水平居中、整树不可命中）。
     */
    private static SceneNode buildToastContainer(SceneRuntime rt, Host host) {
        SceneNode container = SceneNode.column();
        // fill 全高：MainAxisAlign.END 才有盈余可分配，底部堆叠真正生效
        container.setFillParentHeight(true);
        container.setMainAxisAlign(MainAxisAlign.END);
        container.setCrossAxisAlign(CrossAxisAlign.CENTER);
        container.setHitTestable(false);
        container.setClipChildren(true);
        rt.forEach(container, host.entries, entry -> Long.valueOf(entry.id()),
                host::buildToast);
        return container;
    }

    /** @return 通知类型对应的强调色 */
    private static int typeColor(Type type) {
        switch (type) {
            case SUCCESS:
                return TYPE_COLOR_SUCCESS;
            case WARNING:
                return TYPE_COLOR_WARNING;
            case ERROR:
                return TYPE_COLOR_ERROR;
            case INFO:
            default:
                return TYPE_COLOR_INFO;
        }
    }
}
