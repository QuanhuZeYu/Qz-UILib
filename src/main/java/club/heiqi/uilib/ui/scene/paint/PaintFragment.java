package club.heiqi.uilib.ui.scene.paint;

import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.scene.control.SceneListOps;

/**
 * 单节点绘制片段缓存单元。
 *
 * <p>每个场景节点在绘制阶段产出一组绘制命令，封装为 {@code PaintFragment}。
 * 后续可配合节点级 paint 脏标记实现缓存复用：若节点的
 * {@code selfPaintDirty} 未标，其绘制属性不变，对应的 PaintFragment 可跳过重建直接复用。</p>
 *
 * <p>本类不可变：构造时做防御性拷贝，对外暴露不可变视图。</p>
 */
public final class PaintFragment {

    /** 该节点产出的绘制命令列表（不可变视图） */
    private final List<PaintCommand> commands;

    // 相对绘制范围随 fragment 一起失效；滚动只比较当前偏移，不重新度量文字。
    private final long paintTop;
    private final long paintBottom;

    /**
     * 创建绘制片段。
     *
     * @param commands 绘制命令列表（防御性拷贝，调用后外部修改不影响本对象）
     */
    public PaintFragment(List<PaintCommand> commands) {
        this(commands, null);
    }

    PaintFragment(List<PaintCommand> commands,
            club.heiqi.uilib.ui.scene.text.SceneTextMeasurer measurer) {
        Objects.requireNonNull(commands, "commands");
        this.commands = SceneListOps.immutableCopy(commands);
        long top = Long.MAX_VALUE;
        long bottom = Long.MIN_VALUE;
        for (PaintCommand command : commands) {
            long commandBottom = command.getBottom();
            switch (command.getType()) {
                case TEXT:
                    if (measurer == null || command.getTextStyle() == null) {
                        top = Long.MIN_VALUE;
                        bottom = Long.MAX_VALUE;
                        break;
                    }
                    TextStyle style = command.getTextStyle();
                    commandBottom = (long) command.getTop() + measurer.lineHeight(
                            command.getText(), style.getFontSize(), style.getMode());
                    // 文本宽度不是墨迹边界（斜体可横向溢出），这里只用行高做纵向剔除。
                    // 行高取字体度量而非节点高度或自定义行距，避免裁掉溢出节点盒的文字。
                    top = Math.min(top, command.getTop());
                    bottom = Math.max(bottom, commandBottom);
                    break;
                case BACKGROUND:
                case BORDER:
                case BACKDROP:
                case ROUNDED_BAND:
                case IMAGE:
                    top = Math.min(top, command.getTop());
                    bottom = Math.max(bottom, commandBottom);
                    break;
                case LINK_REGION:
                    break; // 命中数据仍留在缓存中，不产生像素。
                default:
                    // 段流/未知命令没有可信边界，保持原样重放；不得按节点盒猜测。
                    top = Long.MIN_VALUE;
                    bottom = Long.MAX_VALUE;
                    break;
            }
        }
        this.paintTop = top;
        this.paintBottom = bottom;
    }

    boolean intersectsVerticalClip(int offsetY, int clipTop, int clipBottom) {
        if (paintTop > paintBottom) return false;
        if (paintTop == Long.MIN_VALUE || paintBottom == Long.MAX_VALUE) return true;
        return paintBottom + offsetY > clipTop && paintTop + offsetY < clipBottom;
    }

    /**
     * 返回该节点产出的绘制命令列表。
     *
     * @return 不可变命令列表
     */
    public List<PaintCommand> getCommands() {
        return commands;
    }

    /**
     * 返回命令数量。
     *
     * @return 命令条数
     */
    public int size() {
        return commands.size();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PaintFragment)) {
            return false;
        }
        PaintFragment other = (PaintFragment) obj;
        return commands.equals(other.commands);
    }

    @Override
    public int hashCode() {
        return commands.hashCode();
    }

    @Override
    public String toString() {
        return "PaintFragment{size=" + commands.size() + "}";
    }
}
