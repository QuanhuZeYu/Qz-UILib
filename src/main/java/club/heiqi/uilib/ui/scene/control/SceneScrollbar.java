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
 * <h3>失效级别（守 I7）</h3>
 * <ul>
 *   <li><b>thumb 位置</b>用 {@link Transform#translate(float, float)}（COMPOSITE 级）平移，
 *       滚动时只标 compositeDirty，零重排零重绘（守信条五）。</li>
 *   <li><b>thumb 高度</b>用 {@code setPreferredHeight}（LAYOUT 级），但值在几何不变时恒定，
 *       setter 去重不标脏；几何变化（content 高度变）时才标 LAYOUT，下一帧 layout 处理（滞后一帧，可接受）。</li>
 *   <li><b>布局纪元订阅</b>：effect 同时订阅 scrollSignal 与 {@link SceneRuntime#layoutEpochSignal()}，
 *       后者由宿主每帧 layout 后 bump，确保 content 高度变化（如 section 切换）时 effect 重跑读最新 LayoutBox。</li>
 * </ul>
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
     * @param trackColor    轨道背景色（ARGB），0 表示透明轨道
     * @param thumbColor    滑块背景色（ARGB）
     * @param barWidth      滚动条宽度（像素，建议 2-6）
     * @param minThumbHeight 滑块最小高度（像素，避免内容过多时滑块消失）
     */
    @Desugar
    public record Props(
        SceneNode viewport,
        ReadableSignal<Integer> scrollSignal,
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
     * 滑块派生几何值对象（Computed 产物，供 bind applier 写入 thumb 属性）。
     */
    @Desugar
    private record ThumbGeom(int height, int translateY) {
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

        // 派生 effect：订阅 scrollSignal + layoutEpoch，读 viewport LayoutBox（只读 I11逃生舱①）
        // 算 thumb 高度 + Y 偏移，写 preferredHeight（去重）+ transform（COMPOSITE）
        rt.bind(Invalidation.COMPOSITE,
            Computed.create(() -> {
                // 订阅 scrollSignal（滚动位置）+ layoutEpoch（几何变化驱动重算）
                int scrollOffset = props.scrollSignal().get().intValue();
                int epoch = rt.layoutEpochSignal().get().intValue();
                // 读 viewport 最新 LayoutBox（只读几何，I11 逃生舱①；flush 前 layout 未跑时为 null）
                Object cached = props.viewport().getCachedLayout();
                if (!(cached instanceof LayoutBox)) {
                    return new ThumbGeom(barWidth, 0);
                }
                LayoutBox vpBox = (LayoutBox) cached;
                int vpHeight = vpBox.getHeight();
                int maxScroll = SceneGeometry.maxScrollY(props.viewport());
                if (maxScroll <= 0) {
                    // 无溢出：thumb 占满 track，位置 0
                    return new ThumbGeom(vpHeight, 0);
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
                int thumbTop = trackRange * scrollOffset / maxScroll;
                return new ThumbGeom(thumbH, thumbTop);
            }),
            geom -> {
                thumb.setPreferredHeight(geom.height());
                thumb.setTransform(Transform.translate(0f, (float) geom.translateY()));
            });

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
