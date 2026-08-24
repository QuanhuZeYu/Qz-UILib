package club.heiqi.uilib.internal.chat3.view;

import java.util.function.Consumer;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.Binding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天 3.0 容器滚动条辅助：以 chat3 视觉（透明轨道 + 4px 可视滑块 + 12px 隐性命中带）
 * 装配 {@link SceneScrollbar}，并挂自动隐藏状态机（column opacity 派生）。
 *
 * <h3>定位：chat3 专用滚动条工厂（薄壳）</h3>
 * <p>滚动位置权威源与写入路径完全由调用方给出（scrollOffsetSignal / setScrollOffset），
 * 本类只负责：① 按 chat3 设计规格调 {@link SceneScrollbar#create}（自定义 hover/drag 三态色、
 * 命中带/可视宽分离）；② 在 column 上挂「活跃显示 → 静止淡出」自动隐藏派生。</p>
 *
 * <h3>自动隐藏状态机（T3，挂在 column opacity 上，wall-clock）</h3>
 * <ul>
 *   <li><b>活跃</b> = scrollOffsetSignal 值变化（滚动） || column 命中区 hovered || column pressed（拖拽）。
 *       任一为真 → 「活跃帧」：lastActiveMillis=now 且 opacity 目标 1.0；</li>
 *   <li><b>静止</b>：距最后活跃帧 {@code hideMillis}（
 *       {@link ChatMarkdownSettings#getScrollbarAutoHideMillis()}，1200ms）内保持满显；
 *       之后 300ms easeInQuad 淡出至 0；</li>
 *   <li><b>淡入</b>：活跃结束后 {@link #FADE_IN_MS}（150ms）内按 easeOut 从 0 爬升至 1；
 *       活跃帧本身（含滚动中/悬停中/拖拽中）直接输出 1.0——滚动/悬停全程满显，无脉动；</li>
 *   <li><b>拖拽（pressed）永不隐藏</b>：计算时 pressed 恒输出 1.0。</li>
 * </ul>
 *
 * <p><b>已知边缘行为（单锚点模型）</b>：活跃事件帧（滚动事件/悬停进入/悬浮中每帧）
 * 输出 1.0；活跃停止后的紧邻帧 since≈1 帧，会按淡入段 easeOut 回落一次后重爬
 * （时间上约 1 帧闪落 + 150ms 重淡入），随后进入完整保显周期。这是「单一 lastActive
 * 时间戳 + 恒定活跃帧满显」模型的固有折中（墙钟、可重入、幂等；bind 重跑无副作用）。</p>
 *
 * <h3>纯函数</h3>
 * <p>{@link #computeOpacity(long, long, boolean, long, long, long)} 为无副作用静态纯函数：
 * 输入 now/lastActive/dragging/时长常量 → 输出 opacity（[0,1]），JUnit 直接覆盖
 * 激活淡入 / 静止淡出 / 拖拽不隐藏 / 滚动续期四行为。</p>
 *
 * <h3>挂载契约</h3>
 * <p>返回值 {@link Result#column()} 追加到「与消息视口同级的 ROW 容器」（右缘，右内边距由
 * 调用方 margin 控制）；调用方在屏幕关闭时调 {@link Result#dispose()} 释放自动隐藏绑定。</p>
 */
public final class ChatScrollbar {

    /** 滚动条宽（px，设计稿：细条 4）。 */
    private static final int BAR_WIDTH = 4;
    /** 滑块最小高（px，避免内容过多时滑块缩到不可见）。 */
    private static final int MIN_THUMB_HEIGHT = 24;
    /** 隐性命中带宽（px）：column 宽 = 12，可视滑块仅 4px 贴右缘，左扩 8px 为命中区。 */
    private static final int HIT_BAND_WIDTH = 12;
    /** 滑块可视宽（px）。 */
    private static final int THUMB_VISUAL_WIDTH = 4;
    /** 滑块悬停态色（设计稿 scrollbar-thumb-hover，40% 白）。 */
    private static final int HOVER_COLOR = 0x66FFFFFF;
    /** 滑块拖拽态色（设计稿 scrollbar-thumb-drag，50% 白）。 */
    private static final int DRAG_COLOR = 0x80FFFFFF;
    /** 淡入时长（ms，活跃后 easeOut 渐显）。 */
    static final long FADE_IN_MS = 150L;
    /** 淡出时长（ms，静止后 easeInQuad 渐隐）。 */
    static final long FADE_OUT_MS = 300L;

    private ChatScrollbar() {
    }

    /**
     * 滚动条装配结果：column/thumb 节点 + 自动隐藏绑定（调用方 dispose 释放）。
     */
    public static final class Result {

        private final SceneScrollbar.Result bar;
        private final Binding hideBinding;

        private Result(SceneScrollbar.Result bar, Binding hideBinding) {
            this.bar = bar;
            this.hideBinding = hideBinding;
        }

        /** @return 滚动条列节点（追加到与视口同级的 ROW 容器） */
        public SceneNode column() {
            return bar.column();
        }

        /** @return 滑块节点（测试探针） */
        public SceneNode thumb() {
            return bar.thumb();
        }

        /** 释放自动隐藏绑定（屏幕关闭时）。 */
        public void dispose() {
            if (!hideBinding.isDisposed()) {
                hideBinding.dispose();
            }
        }
    }

    /**
     * 工厂：按 chat3 规格装配滚动条并挂自动隐藏状态机（无拖动接管回调版本）。
     *
     * @param rt                 宿主场景运行时
     * @param viewport           消息视口节点（可滚动，setScrollOffsetY 受体）
     * @param scrollOffsetSignal 滚动偏移只读显示源（px；与容器滚动绑定同一 Computed）
     * @param setScrollOffset    滚动偏移写入回调（px → 写回滚动权威，与滚轮路径同源）
     * @param frameMillis        帧时钟信号（每渲染帧推进，自动隐藏 wall-clock 驱动）
     * @return 装配结果（column 挂载 + dispose）
     */
    public static Result create(SceneRuntime rt, SceneNode viewport,
            ReadableSignal<Integer> scrollOffsetSignal, Consumer<Integer> setScrollOffset,
            ReadableSignal<Long> frameMillis) {
        return create(rt, viewport, scrollOffsetSignal, setScrollOffset, frameMillis, null);
    }

    /**
     * 工厂：按 chat3 规格装配滚动条并挂自动隐藏状态机。
     *
     * @param rt                 宿主场景运行时
     * @param viewport           消息视口节点（可滚动，setScrollOffsetY 受体）
     * @param scrollOffsetSignal 滚动偏移只读显示源（px；与容器滚动绑定同一 Computed）
     * @param setScrollOffset    滚动偏移写入回调（px → 写回滚动权威，与滚轮路径同源）
     * @param frameMillis        帧时钟信号（每渲染帧推进，自动隐藏 wall-clock 驱动）
     * @param onDragStart        拖动开始回调（接收当前显示偏移 px；T5b：宿主借此
     *                           {@code smoothScroll().snapTo} 取消平滑进入直通，拖动手感即时；
     *                           可为 null）
     * @return 装配结果（column 挂载 + dispose）
     */
    public static Result create(SceneRuntime rt, SceneNode viewport,
            ReadableSignal<Integer> scrollOffsetSignal, Consumer<Integer> setScrollOffset,
            ReadableSignal<Long> frameMillis, Consumer<Integer> onDragStart) {
        SceneScrollbar.Result bar = SceneScrollbar.create(rt, new SceneScrollbar.Props(
                viewport, scrollOffsetSignal, setScrollOffset,
                /* trackColor */ 0, // 透明轨道
                ChatMarkdownSettings.getScrollbarThumbArgb(), // idle 色走 chat3 色板
                BAR_WIDTH, MIN_THUMB_HEIGHT, onDragStart,
                Integer.valueOf(HOVER_COLOR), Integer.valueOf(DRAG_COLOR),
                HIT_BAND_WIDTH, THUMB_VISUAL_WIDTH));
        Binding hideBinding = bindAutoHide(rt, bar.column(), scrollOffsetSignal, frameMillis);
        return new Result(bar, hideBinding);
    }

    /**
     * 自动隐藏状态机绑定：每帧（frameMillis 驱动）判定活跃 → 派生 column opacity。
     *
     * <p>实现细节：derivation 内更新 lastActiveMillis/lastScroll 闭包状态（幂等：
     * 重跑写入同值；可重入：无外部副作用），活跃帧短路输出 1.0（滚动中/悬停中/拖拽中
     * 全程满显），静止帧委托 {@link #computeOpacity} 纯函数。</p>
     *
     * <p>拖拽标志取 <b>column</b> 的 pressed：现状命中架构（SceneScrollbar BUG1 修复）下
     * thumb 视觉区按下投递 column（capture target=column），thumb pressed 在拖动中恒 false，
     * 只有 column pressed 能表达拖动状态。</p>
     *
     * @param rt                 场景运行时
     * @param column             滚动条列节点（opacity 应用目标）
     * @param scrollOffsetSignal 滚动偏移显示源（变化 = 滚动事件）
     * @param frameMillis        帧时钟信号
     * @return 自动隐藏绑定（dispose 释放）
     */
    static Binding bindAutoHide(SceneRuntime rt, SceneNode column,
            ReadableSignal<Integer> scrollOffsetSignal, ReadableSignal<Long> frameMillis) {
        // ★ 时序契约：create 阶段立即调 hovered()/pressed() 触发懒创建（同 SceneScrollbar）
        SceneInteractionState state = rt.interactionState(column);
        final ReadableSignal<Boolean> hovered = state.hovered();
        final ReadableSignal<Boolean> pressed = state.pressed();
        final int[] lastScroll = { Integer.MIN_VALUE };
        final long[] lastActiveMillis = { 0L };
        final long hideMillis = ChatMarkdownSettings.getScrollbarAutoHideMillis();
        return rt.bindComputed(() -> {
                int scroll = scrollOffsetSignal.get().intValue();
                long now = frameMillis.get().longValue();
                boolean hover = Boolean.TRUE.equals(hovered.get());
                boolean press = Boolean.TRUE.equals(pressed.get());
                boolean scrolled = scroll != lastScroll[0];
                if (scrolled) {
                    lastScroll[0] = scroll;
                }
                boolean active = scrolled || hover || press;
                if (active) {
                    lastActiveMillis[0] = now;
                    // 活跃帧：目标 1.0（滚动/悬停/拖拽全程满显；淡入段从活跃结束后起算）
                    return Float.valueOf(1.0F);
                }
                return Float.valueOf(computeOpacity(now, lastActiveMillis[0], press,
                        FADE_IN_MS, hideMillis, FADE_OUT_MS));
            },
            opacity -> column.setOpacity(opacity.floatValue()));
    }

    /**
     * 自动隐藏目标 opacity 纯函数（T3 状态机核心，无副作用、幂等）。
     *
     * <p>输入 now/lastActive/dragging + 时长常量 → 输出 [0,1] opacity：</p>
     * <ul>
     *   <li>{@code dragging} → 1.0（拖拽中永不隐藏）；</li>
     *   <li>{@code now <= lastActive}（活跃帧）→ 1.0（满显，淡入段起点）；</li>
     *   <li>{@code since ∈ (0, fadeIn]} → easeOut 从 0 爬升（激活淡入，150ms）；</li>
     *   <li>{@code since ∈ (fadeIn, hide]} → 1.0（保显期，静止续期窗口）；</li>
     *   <li>{@code since > hide} → easeInQuad 淡出至 0（静止 hideMillis 后开始，fadeOut 内完成）。</li>
     * </ul>
     *
     * @param nowMillis        当前墙钟（ms）
     * @param lastActiveMillis 最近活跃帧时刻（ms；滚动事件/悬停/拖拽帧更新）
     * @param dragging         是否拖拽中（pressed）
     * @param fadeInMillis     淡入时长（ms）
     * @param hideMillis       静止保显时长（ms，{@link ChatMarkdownSettings#getScrollbarAutoHideMillis()}）
     * @param fadeOutMillis    淡出时长（ms）
     * @return 目标 opacity（[0,1]）
     */
    static float computeOpacity(long nowMillis, long lastActiveMillis, boolean dragging,
            long fadeInMillis, long hideMillis, long fadeOutMillis) {
        if (dragging) {
            return 1.0F; // 拖拽中永不隐藏
        }
        long since = nowMillis - lastActiveMillis;
        if (since <= 0L) {
            return 1.0F; // 活跃帧：目标 1（淡入段起点）
        }
        if (since < fadeInMillis) {
            return Animator.easeOut((float) since / (float) fadeInMillis);
        }
        if (since < hideMillis) {
            return 1.0F; // 保显期
        }
        long sinceHide = since - hideMillis;
        if (sinceHide >= fadeOutMillis) {
            return 0.0F; // 淡出完成
        }
        return 1.0F - Animator.easeInQuad((float) sinceHide / (float) fadeOutMillis);
    }
}
