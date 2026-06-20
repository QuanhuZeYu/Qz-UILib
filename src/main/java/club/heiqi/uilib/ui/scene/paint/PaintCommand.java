package club.heiqi.uilib.ui.scene.paint;

import java.util.Objects;

/**
 * 绘制命令纯数据契约 —— 数据层与渲染层之间的唯一合同。
 *
 * <h3>坐标语义：相对节点局部原点（方案 A）</h3>
 * <p>命令中存储的坐标是<b>相对于所属节点局部原点</b>的坐标（通常从 {@code (0,0)} 起）。
 * 组装 PaintPlan 时，通过 {@link #translatedBy(int, int)} 叠加节点的绝对偏移
 * 得到最终屏幕坐标。这使得 fragment 可跨帧复用（节点位置变化 → fragment 引用不变、
 * 仅叠加的 offset 变），符合 COMPOSITE 级"位置变化不重绘"精神。</p>
 *
 * <h3>核心红线（宪章信条六/I6）</h3>
 * <p>本类是数据层与渲染层的<strong>契约对象</strong>，禁止持有任何 SceneNode / 节点引用。
 * 所有坐标、颜色、文本、样式在<strong>构建期固化</strong>进命令字段，回放期<strong>零节点反查</strong>。</p>
 *
 * <p><strong>反面教材</strong>：旧 {@code DocumentPaintCommand} 持有 {@code ElementNode element} 和
 * {@code ComputedStyle elementStyle} 字段，回放渲染时还反查节点取 scroll/style——这污染了契约线，
 * 让渲染层间接认识了 DOM。新模型必须避免这种设计。</p>
 *
 * <h3>不可变性</h3>
 * <p>所有字段均为 {@code final}，通过静态工厂方法构造。构造完成后即不可变，线程安全，
 * 未来可跨线程双缓冲（符合宪章空间换时间国策）。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 创建背景命令
 * PaintCommand bg = PaintCommand.background(0, 0, 100, 50, 0xAARRGGBB);
 *
 * // 创建文本命令
 * TextStyle style = new TextStyle(0xFFFFFFFF, 14);
 * PaintCommand txt = PaintCommand.text(10, 10, "Hello", style);
 * }</pre>
 */
public final class PaintCommand {

    /** 命令类型 */
    private final PaintCommandType type;

    // === 几何（相对所属节点局部原点的坐标，组装期叠加 offset 得绝对坐标） ===

    /** 左边界（像素，相对节点局部原点） */
    private final int left;
    /** 上边界（像素，相对节点局部原点） */
    private final int top;
    /** 右边界（像素），等于 left + width */
    private final int right;
    /** 下边界（像素），等于 top + height */
    private final int bottom;

    // === 背景 ===

    /** 背景色（ARGB 格式），非 BACKGROUND 命令时默认为 0 */
    private final int color;

    // === 文本 ===

    /** 文本内容，非 TEXT 命令时默认为 {@code ""}（空字符串，不用 null） */
    private final String text;

    /** 文本样式，非 TEXT 命令时默认为 {@code null} */
    private final TextStyle textStyle;

    // === 效果 ===

    /** 整体透明度，范围 [0.0, 1.0]，默认 1.0f（不透明） */
    private final float opacity;

    // === 圆角 / 边框（Phase 4，任务 B） ===

    /** 圆角半径（像素，0=直角）。BACKGROUND/BORDER/CLIP_PUSH 共用，其余命令默认 0 */
    private final int cornerRadius;

    /** 边框宽度（像素）。仅 BORDER 命令有意义，其余命令默认 0 */
    private final int borderWidth;

    // ========== 私有构造器 ==========

    private PaintCommand(PaintCommandType type, int left, int top, int right, int bottom,
                         int color, String text, TextStyle textStyle, float opacity,
                         int cornerRadius, int borderWidth) {
        this.type = Objects.requireNonNull(type, "type");
        this.left = left;
        this.top = top;
        this.right = Math.max(left, right);
        this.bottom = Math.max(top, bottom);
        this.color = color;
        this.text = text == null ? "" : text;
        this.textStyle = textStyle;
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        this.cornerRadius = Math.max(0, cornerRadius);
        this.borderWidth = Math.max(0, borderWidth);
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建背景填充命令。
     *
     * @param left   左边界（像素）
     * @param top    上边界（像素）
     * @param right  右边界（像素）
     * @param bottom 下边界（像素）
     * @param color  背景色（ARGB 格式，如 0xAARRGGBB）
     * @return 背景绘制命令
     */
    public static PaintCommand background(int left, int top, int right, int bottom, int color) {
        return new PaintCommand(PaintCommandType.BACKGROUND, left, top, right, bottom,
                color, null, null, 1.0f, 0, 0);
    }

    /**
     * 创建带圆角的背景填充命令（Phase 4，任务 B）。
     *
     * @param left         左边界（像素）
     * @param top          上边界（像素）
     * @param right        右边界（像素）
     * @param bottom       下边界（像素）
     * @param color        背景色（ARGB 格式，如 0xAARRGGBB）
     * @param cornerRadius 圆角半径（像素，0=直角）
     * @return 带圆角的背景绘制命令
     */
    public static PaintCommand background(int left, int top, int right, int bottom, int color,
                                          int cornerRadius) {
        return new PaintCommand(PaintCommandType.BACKGROUND, left, top, right, bottom,
                color, null, null, 1.0f, cornerRadius, 0);
    }

    /**
     * 创建边框绘制命令（Phase 4，任务 B）。
     *
     * <p>第 0 段裁决：边框不占布局空间（box-sizing: border-box 简化），
     * 只是绘制层属性。cornerRadius&gt;0 时回放器走圆角描边路径。</p>
     *
     * @param left         左边界（像素）
     * @param top          上边界（像素）
     * @param right        右边界（像素）
     * @param bottom       下边界（像素）
     * @param color        边框色（ARGB 格式）
     * @param borderWidth  边框宽度（像素）
     * @param cornerRadius 圆角半径（像素，0=直角）
     * @return 边框绘制命令
     */
    public static PaintCommand border(int left, int top, int right, int bottom, int color,
                                      int borderWidth, int cornerRadius) {
        return new PaintCommand(PaintCommandType.BORDER, left, top, right, bottom,
                color, null, null, 1.0f, cornerRadius, borderWidth);
    }

    /**
     * 创建文本绘制命令。
     *
     * @param left  左边界（像素，基线对齐点 x）
     * @param top   上边界（像素，基线对齐点 y）
     * @param text  文本内容
     * @param style 文本样式（颜色、字号等）
     * @return 文本绘制命令
     */
    public static PaintCommand text(int left, int top, String text, TextStyle style) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
        return new PaintCommand(PaintCommandType.TEXT, left, top, left, top,
                0, text, style, 1.0f, 0, 0);
    }

    /**
     * 创建「进入 group opacity 合成作用域」边界命令（Phase 3B）。
     *
     * <p>携带绝对屏幕区域 + 该层局部 opacity。回放器遇此命令调用
     * {@code ctx.pushPaintContext(left, top, right, bottom, opacity)}。
     * 嵌套相乘由渲染层离屏层栈天然完成，<b>opacity 必须传该层局部值（如父 0.5、子 0.5），
     * 绝不传累计值 0.25</b>（否则与 group 合成语义双重衰减）。</p>
     *
     * @param left    绝对左边界（像素）
     * @param top     绝对上边界（像素）
     * @param right   绝对右边界（像素）
     * @param bottom  绝对下边界（像素）
     * @param opacity 该层局部不透明度 [0,1]
     * @return PUSH_OPACITY 边界命令
     */
    public static PaintCommand pushOpacity(int left, int top, int right, int bottom, float opacity) {
        return new PaintCommand(PaintCommandType.PUSH_OPACITY, left, top, right, bottom,
                0, null, null, opacity, 0, 0);
    }

    /**
     * 创建「退出 group opacity 合成作用域」边界命令（Phase 3B）。
     *
     * <p>无坐标无 opacity 语义，回放器遇此命令调用 {@code ctx.popPaintContext()}。
     * 与 {@link #pushOpacity} 由绘制引擎递归骨架保证严格配对。</p>
     *
     * @return POP_OPACITY 边界命令
     */
    public static PaintCommand popOpacity() {
        return new PaintCommand(PaintCommandType.POP_OPACITY, 0, 0, 0, 0,
                0, null, null, 1.0f, 0, 0);
    }

    /**
     * 创建「进入裁剪作用域」边界命令（Phase 4，任务 B）。
     *
     * <p>携带绝对屏幕区域 + 圆角半径。回放器遇此命令调用
     * {@code ctx.pushClip(left, top, right, bottom, cornerRadius)}。裁剪作用域包住
     * 「本节点命令 + 全部后代命令」，由绘制引擎递归骨架保证与 {@link #clipPop()} 严格配对。</p>
     *
     * @param left         绝对左边界（像素）
     * @param top          绝对上边界（像素）
     * @param right        绝对右边界（像素）
     * @param bottom       绝对下边界（像素）
     * @param cornerRadius 圆角半径（像素，0=矩形裁剪）
     * @return CLIP_PUSH 边界命令
     */
    public static PaintCommand clipPush(int left, int top, int right, int bottom, int cornerRadius) {
        return new PaintCommand(PaintCommandType.CLIP_PUSH, left, top, right, bottom,
                0, null, null, 1.0f, cornerRadius, 0);
    }

    /**
     * 创建「退出裁剪作用域」边界命令（Phase 4，任务 B）。
     *
     * <p>无坐标语义，回放器遇此命令调用 {@code ctx.popClip()}。
     * 与 {@link #clipPush} 由绘制引擎递归骨架保证严格配对。</p>
     *
     * @return CLIP_POP 边界命令
     */
    public static PaintCommand clipPop() {
        return new PaintCommand(PaintCommandType.CLIP_POP, 0, 0, 0, 0,
                0, null, null, 1.0f, 0, 0);
    }

    // ========== Getter ==========

    /** @return 命令类型 */
    public PaintCommandType getType() {
        return type;
    }

    /** @return 左边界（像素，相对节点局部原点） */
    public int getLeft() {
        return left;
    }

    /** @return 上边界（像素，相对节点局部原点） */
    public int getTop() {
        return top;
    }

    /** @return 右边界（像素，相对节点局部原点） */
    public int getRight() {
        return right;
    }

    /** @return 下边界（像素，相对节点局部原点） */
    public int getBottom() {
        return bottom;
    }

    /** @return 背景色（ARGB 格式） */
    public int getColor() {
        return color;
    }

    /** @return 文本内容（非文本命令返回空字符串） */
    public String getText() {
        return text;
    }

    /** @return 文本样式（非文本命令返回 null） */
    public TextStyle getTextStyle() {
        return textStyle;
    }

    /** @return 整体透明度 [0, 1] */
    public float getOpacity() {
        return opacity;
    }

    /** @return 圆角半径（像素，0=直角）。BACKGROUND/BORDER/CLIP_PUSH 有意义 */
    public int getCornerRadius() {
        return cornerRadius;
    }

    /** @return 边框宽度（像素）。仅 BORDER 命令有意义 */
    public int getBorderWidth() {
        return borderWidth;
    }

    // ========== 坐标平移 ==========

    /**
     * 返回本命令坐标平移 (dx, dy) 后的新命令。
     *
     * <p>用于 {@link PaintPlan} 组装时，将 fragment 内的相对坐标叠加节点绝对偏移
     * 得到最终屏幕坐标。零偏移时返回自身引用（不可变，安全复用）。</p>
     *
     * @param dx X 方向平移量（像素）
     * @param dy Y 方向平移量（像素）
     * @return 平移后的新命令；dx==0 && dy==0 时返回自身
     */
    PaintCommand translatedBy(int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return this;
        }
        // 合成/裁剪边界命令（PUSH/POP_OPACITY、CLIP_PUSH/CLIP_POP）由绘制引擎递归骨架
        // 直接产出绝对坐标，绝不经 fragment 相对坐标通路二次平移；防御性返回自身。
        if (type == PaintCommandType.PUSH_OPACITY || type == PaintCommandType.POP_OPACITY
                || type == PaintCommandType.CLIP_PUSH || type == PaintCommandType.CLIP_POP) {
            return this;
        }
        return new PaintCommand(type, left + dx, top + dy, right + dx, bottom + dy,
                color, text, textStyle, opacity, cornerRadius, borderWidth);
    }

    // ========== equals / hashCode / toString ==========

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PaintCommand)) {
            return false;
        }
        PaintCommand other = (PaintCommand) obj;
        return type == other.type
                && left == other.left
                && top == other.top
                && right == other.right
                && bottom == other.bottom
                && color == other.color
                && Objects.equals(text, other.text)
                && Objects.equals(textStyle, other.textStyle)
                && Float.compare(opacity, other.opacity) == 0
                && cornerRadius == other.cornerRadius
                && borderWidth == other.borderWidth;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, left, top, right, bottom, color, text, textStyle, opacity,
                cornerRadius, borderWidth);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PaintCommand{type=").append(type);
        if (type == PaintCommandType.BACKGROUND) {
            sb.append(", left=").append(left)
              .append(", top=").append(top)
              .append(", right=").append(right)
              .append(", bottom=").append(bottom)
              .append(", color=").append(Integer.toHexString(color));
            if (cornerRadius != 0) {
                sb.append(", cornerRadius=").append(cornerRadius);
            }
        } else if (type == PaintCommandType.TEXT) {
            sb.append(", left=").append(left)
              .append(", top=").append(top)
              .append(", text='").append(text).append('\'')
              .append(", textStyle=").append(textStyle);
        } else if (type == PaintCommandType.BORDER) {
            sb.append(", left=").append(left)
              .append(", top=").append(top)
              .append(", right=").append(right)
              .append(", bottom=").append(bottom)
              .append(", color=").append(Integer.toHexString(color))
              .append(", borderWidth=").append(borderWidth)
              .append(", cornerRadius=").append(cornerRadius);
        } else if (type == PaintCommandType.CLIP_PUSH) {
            sb.append(", left=").append(left)
              .append(", top=").append(top)
              .append(", right=").append(right)
              .append(", bottom=").append(bottom)
              .append(", cornerRadius=").append(cornerRadius);
        }
        if (opacity != 1.0f) {
            sb.append(", opacity=").append(opacity);
        }
        return sb.append('}').toString();
    }
}
