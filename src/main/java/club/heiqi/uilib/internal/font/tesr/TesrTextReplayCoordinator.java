package club.heiqi.uilib.internal.font.tesr;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.internal.font.tesr.angelica.AngelicaTesrBatchProbe;

/**
 * 宿主 TESR 批量提交窗口内的世界文字延后回放协调器。
 *
 * <h3>为什么需要它</h3>
 * <p>宿主（Angelica）把 TESR 几何（如告示牌木板）排队，在遍历结束后统一提交；而本库替换字体后，
 * 字形是在 {@code drawString} 调用点内立即提交的。两条提交通道不同源，程序顺序不保证落屏顺序：
 * 木板晚于字形落屏时，因字形按原版语义不写深度，木板会在自身轮廓内无条件覆盖字形，只在深度缓冲
 * 已有更近几何处让字形残留——表现为「文字被物体挡住才可见」或「完全不显示」。</p>
 *
 * <p>本类把处于宿主批量窗口内的世界文字捕获下来，改到宿主提交批次之后回放（回放用捕获时刻的
 * 投影/模型视图矩阵），使字形不早于宿主几何落屏。</p>
 *
 * <h3>fail-open 契约</h3>
 * <ul>
 *   <li>宿主回放钩子未安装（无 Angelica、版本不匹配、Mixin 未应用）时 {@code hostHookInstalled}
 *       恒为 false，本类不捕获任何文字，行为退回「调用点内立即绘制」——无 Angelica 也能正常运行。</li>
 *   <li>宿主探针不可用时同样不捕获。</li>
 *   <li>宿主未在帧内提交（异常、批次被丢弃）时，帧边界丢弃滞留项并留一次 WARN，不静默永久丢字。</li>
 * </ul>
 *
 * <p>本类只经 {@link ReplaySink} 与字体接入层交互，不直接依赖适配器实现；矩阵读取经
 * {@link MatrixReader} 注入，headless 场地可注入桩实现。</p>
 */
public final class TesrTextReplayCoordinator {

    /** 宿主探针：判断宿主当前是否处于「几何已排队、尚未提交」的窗口。 */
    public interface HostProbe {

        /**
         * 查询宿主是否有未提交的 TESR 几何。
         *
         * @return true 表示宿主处于批量提交窗口内
         */
        boolean hasPendingDeferredGeometry();
    }

    /** 回放出口：由字体接入层实现，用捕获时的矩阵重新绘制该条文字。 */
    public interface ReplaySink {

        /**
         * 回放一条捕获的世界文字。
         *
         * @param text 捕获项
         */
        void replay(DeferredText text);
    }

    /** 矩阵读取出口：默认读固定管线，headless 场地可注入桩实现。 */
    public interface MatrixReader {

        /**
         * 读取当前模型视图矩阵。
         *
         * @param target 长度 16 的目标数组（列主序）
         */
        void readModelview(float[] target);

        /**
         * 读取当前投影矩阵。
         *
         * @param target 长度 16 的目标数组（列主序）
         */
        void readProjection(float[] target);
    }

    /** 一条被延后的世界文字绘制请求（自包含：文本、参数与捕获时刻的矩阵）。 */
    public static final class DeferredText {

        /** 文本内容。 */
        public final String text;
        /** 起始 X（字体局部单位）。 */
        public final int x;
        /** 起始 Y（字体局部单位）。 */
        public final int y;
        /** 文本颜色（ARGB）。 */
        public final int color;
        /** 是否绘制阴影。 */
        public final boolean dropShadow;
        /** 换行宽度；{@code < 0} 表示单行 drawString，否则为 drawSplitString 的换行宽度。 */
        public final int wrapWidth;
        /** 捕获时刻的模型视图矩阵（16 元素，列主序）。 */
        public final float[] modelview = new float[16];
        /** 捕获时刻的投影矩阵（16 元素，列主序）。 */
        public final float[] projection = new float[16];

        DeferredText(String text, int x, int y, int color, boolean dropShadow, int wrapWidth) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.dropShadow = dropShadow;
            this.wrapWidth = wrapWidth;
        }
    }

    /** 默认矩阵读取：直接查固定管线当前矩阵。 */
    private static final class GlMatrixReader implements MatrixReader {

        private final ThreadLocal<FloatBuffer> scratch = new ThreadLocal<FloatBuffer>() {
            @Override
            protected FloatBuffer initialValue() {
                return BufferUtils.createFloatBuffer(16);
            }
        };

        @Override
        public void readModelview(float[] target) {
            read(GL11.GL_MODELVIEW_MATRIX, target);
        }

        @Override
        public void readProjection(float[] target) {
            read(GL11.GL_PROJECTION_MATRIX, target);
        }

        private void read(int pname, float[] target) {
            FloatBuffer buffer = scratch.get();
            buffer.clear();
            GL11.glGetFloat(pname, buffer);
            buffer.get(target);
        }
    }

    private static final MatrixReader GL_MATRIX_READER = new GlMatrixReader();

    private static final ThreadLocal<Deque<DeferredText>> PENDING = new ThreadLocal<Deque<DeferredText>>() {
        @Override
        protected Deque<DeferredText> initialValue() {
            return new ArrayDeque<DeferredText>();
        }
    };

    /** 宿主回放钩子是否已安装（由可选 Mixin 握手置位；恒 false 时本类完全停用）。 */
    private static volatile boolean hostHookInstalled;
    /** 回放出口；为 null 时禁止捕获（无出口的捕获等于丢字）。 */
    private static volatile ReplaySink replaySink;
    /** 宿主探针。 */
    private static volatile HostProbe hostProbe = new AngelicaTesrBatchProbe();
    /** 矩阵读取出口。 */
    private static volatile MatrixReader matrixReader = GL_MATRIX_READER;
    /** 丢弃告警只出一次的标志。 */
    private static volatile boolean dropWarned;

    private TesrTextReplayCoordinator() {}

    /**
     * 安装回放出口（由字体接入层在调用器初始化时调用）。
     *
     * @param sink 回放出口，可为 null（停用捕获）
     */
    public static void installReplaySink(ReplaySink sink) {
        replaySink = sink;
    }

    /**
     * 安装宿主探针。
     *
     * @param probe 探针；null 表示无宿主能力（停用捕获）
     */
    public static void installHostProbe(HostProbe probe) {
        hostProbe = probe;
    }

    /**
     * 安装矩阵读取出口。
     *
     * @param reader 读取器；null 表示回落固定管线读取
     */
    public static void installMatrixReader(MatrixReader reader) {
        matrixReader = reader == null ? GL_MATRIX_READER : reader;
    }

    /** 由可选 Mixin 握手：宿主回放钩子已安装。 */
    public static void markHostHookInstalled() {
        hostHookInstalled = true;
    }

    /**
     * 宿主回放钩子是否已安装。
     *
     * @return 是否已安装
     */
    public static boolean isHostHookInstalled() {
        return hostHookInstalled;
    }

    /**
     * 当前调用是否应转入延后捕获。
     *
     * @param uiDeferredScopeActive 是否处于本库 UI 延迟批处理边界内（UI 文本必须保持即时）
     * @return true 表示应捕获而非立即绘制
     */
    public static boolean shouldCapture(boolean uiDeferredScopeActive) {
        if (!hostHookInstalled || replaySink == null || uiDeferredScopeActive) {
            return false;
        }
        HostProbe probe = hostProbe;
        return probe != null && probe.hasPendingDeferredGeometry();
    }

    /**
     * 捕获一条世界文字绘制请求（读取当前投影/模型视图矩阵）。
     *
     * @param text       文本
     * @param x          起始 X
     * @param y          起始 Y
     * @param color      颜色
     * @param dropShadow 是否阴影
     * @param wrapWidth  换行宽度；{@code < 0} 为单行
     */
    public static void capture(String text, int x, int y, int color, boolean dropShadow, int wrapWidth) {
        DeferredText item = new DeferredText(text, x, y, color, dropShadow, wrapWidth);
        matrixReader.readModelview(item.modelview);
        matrixReader.readProjection(item.projection);
        PENDING.get().addLast(item);
    }

    /**
     * 宿主提交批次后的回放入口（由可选 Mixin 在宿主提交点调用）。
     *
     * <p>宿主仍有未提交几何（延迟管线分支）时不回放，等下一次提交点——否则会重新落到几何之前。</p>
     */
    public static void replayAfterHostCommit() {
        markHostHookInstalled();
        HostProbe probe = hostProbe;
        if (probe != null && probe.hasPendingDeferredGeometry()) {
            return;
        }
        Deque<DeferredText> pending = PENDING.get();
        if (pending.isEmpty()) {
            return;
        }
        ReplaySink sink = replaySink;
        if (sink == null) {
            pending.clear();
            return;
        }
        while (!pending.isEmpty()) {
            DeferredText item = pending.pollFirst();
            try {
                sink.replay(item);
            } catch (RuntimeException exception) {
                pending.clear();
                warnOnce("字体世界文字延后回放失败，已丢弃本帧滞留项并回即时绘制：{}", exception.toString());
                return;
            } catch (Error error) {
                pending.clear();
                throw error;
            }
        }
    }

    /** 帧边界：丢弃宿主未提交而滞留的捕获项（fail-open，回即时绘制）。 */
    public static void onFrameBoundary() {
        Deque<DeferredText> pending = PENDING.get();
        if (pending.isEmpty()) {
            return;
        }
        int dropped = pending.size();
        pending.clear();
        warnOnce("宿主未在本帧提交 TESR 批次，已丢弃 {} 项延后文字并回即时绘制", Integer.valueOf(dropped));
    }

    /**
     * 当前线程待回放条目数（诊断与测试用）。
     *
     * @return 条目数
     */
    public static int pendingCount() {
        return PENDING.get().size();
    }

    /** 复位全部静态状态（测试用；生产路径不调用）。 */
    public static void resetForTest() {
        hostHookInstalled = false;
        replaySink = null;
        hostProbe = new AngelicaTesrBatchProbe();
        matrixReader = GL_MATRIX_READER;
        dropWarned = false;
        PENDING.get().clear();
    }

    private static void warnOnce(String message, Object argument) {
        if (dropWarned) {
            return;
        }
        dropWarned = true;
        MyMod.LOG.warn(message, argument);
    }
}
