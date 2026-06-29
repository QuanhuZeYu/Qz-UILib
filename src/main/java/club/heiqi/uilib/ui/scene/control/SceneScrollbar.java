package club.heiqi.uilib.ui.scene.control;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * SceneScrollbar —— scene 控件库纵向滚动条控件，叠加在可滚动视口右侧反映滚动位置。
 *
 * <h3>定位：纯派生显示控件（契约 R4 外观随状态经 bind 派生）</h3>
 * <p>滚动条不持有任何交互状态，也不自己维护滚动位置——它只<b>读</b> viewport 的几何
 * （LayoutBox，只读 I11 逃生舱①）与外部传入的 {@code scrollSignal}，派生 thumb 的几何
 * （高度 + Y 偏移）并经 bind 写入 thumb 节点属性。滚动位置唯一权威源是外部 scrollSignal
 * （由 {@link club.heiqi.uilib.ui.scene.component.SceneScrolls#attach} 创建并维护）。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * column (COLUMN, preferredWidth=barWidth, fillParentHeight, bg=trackColor, clipChildren=true, cornerRadius)
 *   └─ thumb (preferredWidth=barWidth, preferredHeight=动态, bg=thumbColor, cornerRadius,
 *             transform.translateY=动态)   ← COMPOSITE 级平移，零重排
 * </pre>
 * <p>column 由调用方 appendChild 到与 viewport 同级的 ROW 容器（viewport 右侧独立列），
 * fillParentHeight 使 column 高度与 viewport 等高（ROW 交叉轴 STRETCH）。</p>
 *
 * <h3>派生几何算法</h3>
 * <ul>
 *   <li><b>content 总高</b> = viewport 可见高 + maxScrollY（{@link SceneGeometry#maxScrollY} 闭式）。</li>
 *   <li><b>thumb 高</b> = max(viewHeight² / contentHeight, minThumbHeight)；无溢出时 thumb 占满 track。</li>
 *   <li><b>thumb Y</b> = (trackHeight - thumbHeight) * (scrollOffset / maxScroll)，无溢出时为 0。</li>
 * </ul>
 *
 * <h3>失效级别（守 I7 / I4 双轨核对）</h3>
 * <ul>
 *   <li><b>thumb 位置</b>用 {@link Transform#translate(float, float)}（COMPOSITE 级）平移，
 *       由声明 COMPOSITE 的 bind 写入——滚动时只标 compositeDirty，零重排零重绘（守信条五）。
 *       声明级别与 {@code setTransform} 实际打出级别一致，I4 双轨核对成立。</li>
 *   <li><b>thumb 高度</b>用 {@code setPreferredHeight}（LAYOUT 级），由声明 LAYOUT 的 bind 写入。
 *       值在几何不变时恒定，setter 去重不标脏；几何变化（content 高度变）时才标 LAYOUT，
 *       下一帧 layout 处理（滞后一帧，可接受）。声明级别与 {@code setPreferredHeight} 实际打出级别一致。</li>
 *   <li><b>订阅源</b>：LAYOUT bind 只订阅 {@code contentChangedSignal}（content 高度变化才重算 height）；
 *       COMPOSITE bind 订阅 {@code scrollSignal} + {@code contentChangedSignal}
 *       （滚动位置变化重算 translateY，content 变化也重算因 translateY 依赖 trackRange）。
 *       滚动时只有 COMPOSITE bind 跑，LAYOUT bind 不跑——按需重算，守 I7。</li>
 * </ul>
 *
 * <h3>contentChangedSignal 契约</h3>
 * <p>调用方传入一个在「content 高度可能变化」时被 bump 的只读 signal（如 ConfigScreen 的
 * {@code activeSectionSignal}，section 切换时 content 高度会变）。scrollbar 据此重算派生几何。
 * <b>已知边缘情况</b>：section 切换时该 signal 立即 set，但 content 实际高度要等下一帧 layout 跑完
 * 才生效，故 effect 重跑读到的是旧 LayoutBox——scrollbar 几何滞后一帧。section 切换是低频事件，
 * 滞后一帧可接受；这比每帧无条件 bump+flush 破坏 I7/I9 的旧方案好得多。
 * 窗口 resize 导致 viewport 尺寸变化的边缘情况暂未覆盖（无对应 signal），留 TODO。</p>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 常量 + 节点引用 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / 无交互态（纯显示，R5/R6 不适用）。</p>
 *
 * <h3>守不变量</h3>
 * <ul>
 *   <li><b>I6</b>：paint 层只读 thumb 节点属性（transform/preferredHeight/bg），不读 signal/组件；
 *       effect 在数据层写 node 属性，契约线不破。</li>
 *   <li><b>I7</b>：滚动只触发 COMPOSITE 级 transform 变化，零重排；preferredHeight 去重保证干净帧零开销。</li>
 *   <li><b>I11 逃生舱①</b>：effect body 读 viewport LayoutBox（只读几何，不写节点、不标脏）。</li>
 * </ul>
 */
public final class SceneScrollbar {

    /**
     * 纯静态工厂，禁止实例化（强制无状态，契约 R1）。
     */
    private SceneScrollbar() {
    }

    /**
     * Scrollbar 输入契约 —— 纯只读受控源 + 视口节点引用 + 视觉常量（契约 R2）。
     *
     * @param viewport      被反映滚动位置的可滚动视口节点（isScrollable==true，构建期固定引用）
     * @param scrollSignal  滚动偏移受控源（由 SceneScrolls.attach 创建，唯一滚动位置权威）
     * @param contentChangedSignal content 高度可能变化时被 bump 的只读 signal（如 section 切换 signal）；
     *                       scrollbar 据此重算 thumb 几何；不可为 null
     * @param trackColor    轨道背景色（ARGB），0 表示透明轨道
     * @param thumbColor    滑块背景色（ARGB）
     * @param barWidth      滚动条宽度（像素，建议 2-6）
     * @param minThumbHeight 滑块最小高度（像素，避免内容过多时滑块消失）
     */
    @Desugar
    public record Props(
        SceneNode viewport,
        ReadableSignal<Integer> scrollSignal,
        ReadableSignal<?> contentChangedSignal,
        int trackColor,
        int thumbColor,
        int barWidth,
        int minThumbHeight
    ) {
    }

    /**
     * Scrollbar 创建结果，暴露列节点与滑块节点供调用方挂载与测试探针。
     *
     * @param column 滚动条列节点（调用方 appendChild 到与 viewport 同级的 ROW 容器）
     * @param thumb  滑块节点（已挂载到 column，几何由 bind 派生）
     */
    @Desugar
    public record Result(
        SceneNode column,
        SceneNode thumb
    ) {
    }

    /**
     * 工厂：构建 Scrollbar 组件函数。
     *
     * <p>返回的 {@code Result} 含 column 节点（调用方负责挂到 viewport 同级 ROW 容器右侧）。
     * thumb 已挂载到 column 内，几何由内部 bind effect 派生，调用方无需手动定位。</p>
     *
     * @param rt    场景运行时
     * @param props Scrollbar 输入契约
     * @return 创建结果（column + thumb 节点引用）
     */
    public static Result create(SceneRuntime rt, Props props) {
        int barWidth = props.barWidth();
        int radius = Math.max(1, barWidth / 2);

        // 滚动条列：固定宽，填满父高（与 viewport 等高），轨道背景，裁剪滑块超出部分
        SceneNode column = new SceneNode();
        column.setFlexDirection(FlexDirection.COLUMN);
        column.setPreferredWidth(barWidth);
        column.setFillParentHeight(true);
        column.setClipChildren(true);
        if (props.trackColor() != 0) {
            column.setBackgroundColor(props.trackColor());
        }
        column.setCornerRadius(radius);
        column.setHitTestable(false);

        // 滑块：固定宽，高度与 Y 偏移由 bind 派生
        SceneNode thumb = new SceneNode();
        thumb.setPreferredWidth(barWidth);
        thumb.setPreferredHeight(barWidth); // 初始占位，effect 物化后覆盖
        thumb.setBackgroundColor(props.thumbColor());
        thumb.setCornerRadius(radius);
        thumb.setHitTestable(false);
        column.appendChild(thumb);

        // TODO(M3 真机验证)：column + thumb 均 setHitTestable(false)，鼠标悬在 scrollbar 4px 列上滚轮时，
        // hit-test 穿透 column（hitTestable=false 返回空）→ 命中 scrollContainer（ROW 父）→ SCROLL 事件
        // dispatch 到 scrollContainer 后 bubble 向 root，但 viewport 是 scrollContainer 的子节点（兄弟于 column），
        // 不是祖先，故 SCROLL 不会冒泡到 viewport 的 SceneScrolls handler → 「在滚动条上滚不动内容」。
        // 可行修法（需真机验证后定夺）：
        //   A. column setHitTestable(true) + 注册 SCROLL handler 转发滚轮到 scrollSignal（破坏纯显示控件定位）；
        //   B. 把 scrollbar 移到 viewport 内部右侧绝对定位叠加（scene layout 是 flex 流式，不支持绝对定位叠加，需扩 layout）；
        //   C. 在 scrollContainer 上注册 SCROLL 转发 handler（污染 ConfigScreen，uilib 通用组件不应感知调用方）。
        // 本批次不强行修，留真机验证后选方案。

        // ---- LAYOUT bind：只订阅 contentChangedSignal，算 thumb 高度，写 setPreferredHeight（LAYOUT 级）----
        // height 公式 = max(vpHeight²/contentHeight, minThumb)，不依赖 scrollOffset，故不订阅 scrollSignal。
        // contentChangedSignal 变化（section 切换）时重跑读最新 LayoutBox 重算 height。
        rt.bind(Invalidation.LAYOUT,
            Computed.create(() -> {
                props.contentChangedSignal().get(); // 订阅 content 高度变化
                Object cached = props.viewport().getCachedLayout();
                if (!(cached instanceof LayoutBox)) {
                    return barWidth; // flush 前 layout 未跑时兜底
                }
                LayoutBox vpBox = (LayoutBox) cached;
                int vpHeight = vpBox.getHeight();
                int maxScroll = SceneGeometry.maxScrollY(props.viewport());
                if (maxScroll <= 0) {
                    return vpHeight; // 无溢出：thumb 占满 track
                }
                int contentHeight = vpHeight + maxScroll;
                int thumbH = vpHeight * vpHeight / contentHeight;
                if (thumbH < props.minThumbHeight()) {
                    thumbH = props.minThumbHeight();
                }
                if (thumbH > vpHeight) {
                    thumbH = vpHeight;
                }
                return thumbH;
            }),
            (Integer h) -> thumb.setPreferredHeight(h.intValue()));

        // ---- COMPOSITE bind：订阅 scrollSignal + contentChangedSignal，算 thumb Y 偏移，写 setTransform（COMPOSITE 级）----
        // translateY = (trackHeight - thumbHeight) * (scrollOffset / maxScroll)，依赖 scrollOffset + content 高度。
        // 滚动时只有本 bind 跑（scrollSignal 变），LAYOUT bind 不跑——按需重算守 I7。
        rt.bind(Invalidation.COMPOSITE,
            Computed.create(() -> {
                int scrollOffset = props.scrollSignal().get().intValue();
                props.contentChangedSignal().get(); // content 变化时 translateY 也要重算（trackRange 变）
                Object cached = props.viewport().getCachedLayout();
                if (!(cached instanceof LayoutBox)) {
                    return 0; // flush 前 layout 未跑时兜底
                }
                LayoutBox vpBox = (LayoutBox) cached;
                int vpHeight = vpBox.getHeight();
                int maxScroll = SceneGeometry.maxScrollY(props.viewport());
                if (maxScroll <= 0) {
                    return 0; // 无溢出：thumbTop=0
                }
                int contentHeight = vpHeight + maxScroll;
                int thumbH = vpHeight * vpHeight / contentHeight;
                if (thumbH < props.minThumbHeight()) {
                    thumbH = props.minThumbHeight();
                }
                if (thumbH > vpHeight) {
                    thumbH = vpHeight;
                }
                int trackRange = vpHeight - thumbH;
                return trackRange * scrollOffset / maxScroll;
            }),
            (Integer y) -> thumb.setTransform(Transform.translate(0f, (float) y.intValue())));

        return new Result(column, thumb);
    }

    /**
     * 默认滑块颜色（ACCENT 蓝，与选中态同色，暗示当前滚动位置）。
     */
    public static final int DEFAULT_THUMB_COLOR = SceneChromeTokens.ACCENT;
    /**
     * 默认轨道颜色（半透明白，约 20% 不透明度，在任意底色上微亮可见）。
     */
    public static final int DEFAULT_TRACK_COLOR = 0x33FFFFFF;
    /**
     * 默认滚动条宽度（像素，细条）。
     */
    public static final int DEFAULT_BAR_WIDTH = 4;
    /**
     * 默认滑块最小高度（像素，避免内容过多时滑块缩到不可见）。
     */
    public static final int DEFAULT_MIN_THUMB_HEIGHT = 20;
}
