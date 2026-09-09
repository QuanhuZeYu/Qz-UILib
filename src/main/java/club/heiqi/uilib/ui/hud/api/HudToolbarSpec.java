package club.heiqi.uilib.ui.hud.api;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * HUD 外接工具栏规格（不可变；长度单位 = UILib logical px）。
 *
 * <h3>布局语义（宿主与打开态页面共用同一份解析）</h3>
 * <ul>
 *   <li>工具栏挂在 HUD 内容盒外侧 {@link #getSide()} 指定的边，与内容盒之间留
 *       {@link #getGap()} 的间隙，<b>永不与内容重叠</b>；</li>
 *   <li>沿挂载边方向（TOP/BOTTOM 为高、LEFT/RIGHT 为宽）工具栏恰占
 *       {@link #getThickness()}；垂直方向取工具栏自身内在尺寸与内容盒的较大者
 *       （工具栏不强制拉伸到内容尺寸，避免 SHRINK 容器宽度反馈）；</li>
 *   <li><b>外框</b> = 内容盒 + 该边上的（gap + thickness）。外框才是 placement / 测量 /
 *       clamp / 裁剪的输入：四边工具栏因此参与外框尺寸，既不遮挡主体，也不会被视口裁掉
 *       主体之外的自身；</li>
 *   <li>{@link #getVisible()} 为 false 时不挂载工具栏，外框退化为内容盒。</li>
 * </ul>
 *
 * <p>规格默认在工厂内容后追加公共缩放工具，可通过 {@link Builder#scaleControls(boolean)}
 * 关闭。缩放状态按 HUD 注册项隔离，在宿主边界成对转换绘制和输入；长度属性仍为缩放前 logical px。
 * 交叉轴对齐、溢出菜单、多工具栏并存留待有真实需求再扩展。</p>
 */
public final class HudToolbarSpec {

    /** 内容盒与工具栏之间的默认间隙（logical px）。 */
    public static final int DEFAULT_GAP_PX = 4;
    /**
     * 工具栏沿挂载边方向的默认厚度（logical px）。
     *
     * <p>28 = 单行紧凑按钮行的可先验高度（{@code ChatToolbar} 的固定行高与之同源）。
     * 宿主在 layout 之前就能算出外框高度，placement 不必等一帧实测。</p>
     */
    public static final int DEFAULT_THICKNESS_PX = 28;

    private final boolean scaleControls;
    private final HudToolbarSide side;
    private final int gap;
    private final int thickness;
    private final ReadableSignal<Boolean> visible;

    private HudToolbarSpec(Builder builder) {
        this.side = Objects.requireNonNull(builder.side, "side");
        if (builder.gap < 0) {
            throw new IllegalArgumentException("gap must be >= 0");
        }
        if (builder.thickness <= 0) {
            throw new IllegalArgumentException("thickness must be > 0");
        }
        this.scaleControls = builder.scaleControls;
        this.gap = builder.gap;
        this.thickness = builder.thickness;
        this.visible = Objects.requireNonNull(builder.visible, "visible");
    }

    /** 以默认边（{@link HudToolbarSide#DEFAULT}）创建 builder。 */
    public static Builder builder() {
        return new Builder(HudToolbarSide.DEFAULT);
    }

    /** 以指定边创建 builder。 */
    public static Builder builder(HudToolbarSide side) {
        return new Builder(side);
    }

    /** @return 挂载边 */
    public HudToolbarSide getSide() { return side; }

    /** @return 内容盒与工具栏之间的间隙（logical px，>= 0） */
    public int getGap() { return gap; }

    /** @return 工具栏沿挂载边方向的厚度（logical px，> 0） */
    public int getThickness() { return thickness; }

    /** @return 可见性（主线程读取的响应式状态；false = 不挂载、不占外框尺寸） */
    public ReadableSignal<Boolean> getVisible() { return visible; }

    /** 是否追加公共缩放工具（默认 true）；关闭只隐藏工具，不重置倍率。 */
    public boolean isScaleControls() { return scaleControls; }

    /** HUD 工具栏规格 builder。 */
    public static final class Builder {
        private boolean scaleControls = true;
        private HudToolbarSide side;
        private int gap = DEFAULT_GAP_PX;
        private int thickness = DEFAULT_THICKNESS_PX;
        private ReadableSignal<Boolean> visible = Signal.create(Boolean.TRUE);

        private Builder(HudToolbarSide side) {
            this.side = side;
        }

        /** 是否追加缩小、倍率复位、放大工具；默认加入。 */
        public Builder scaleControls(boolean value) { this.scaleControls = value; return this; }

        /** 设置挂载边。 */
        public Builder side(HudToolbarSide value) { this.side = value; return this; }

        /** 设置内容盒与工具栏之间的间隙（logical px）。 */
        public Builder gap(int value) { this.gap = value; return this; }

        /** 设置工具栏沿挂载边方向的厚度（logical px）。 */
        public Builder thickness(int value) { this.thickness = value; return this; }

        /** 设置可见性信号（false = 不挂载工具栏，外框退化为内容盒）。 */
        public Builder visible(ReadableSignal<Boolean> value) { this.visible = value; return this; }

        public HudToolbarSpec build() { return new HudToolbarSpec(this); }
    }
}
