package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 整棵场景树的绘制计划 —— Display List 的顶层载体。
 *
 * <p>{@code PaintPlan} 是数据层产出的最终绘制命令序列，渲染层只按顺序消费
 * 其中的 {@link PaintCommand}，不认识任何上游概念（宪章信条六/I6）。
 * 这是数据层与渲染层之间唯一的合同交付物。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * PaintPlan plan = new PaintPlan();
 * plan.addCommand(PaintCommand.background(0, 0, 100, 50, 0xFFFFFFFF));
 * plan.addCommand(PaintCommand.text(5, 10, "Hello", style));
 *
 * // 或从片段导入
 * plan.addFragment(fragment);
 *
 * // 渲染层：顺序消费扁平化命令列表
 * for (PaintCommand cmd : plan.getCommands()) {
 *     renderer.draw(cmd);
 * }
 * }</pre>
 */
public final class PaintPlan {

    /** 扁平化的有序绘制命令列表 */
    private final List<PaintCommand> commands;

    /**
     * 创建空的绘制计划。
     */
    public PaintPlan() {
        this.commands = new ArrayList<>();
    }

    /**
     * 追加单条绘制命令。
     *
     * @param command 绘制命令
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addCommand(PaintCommand command) {
        Objects.requireNonNull(command, "command");
        commands.add(command);
        return this;
    }

    /**
     * 追加一个节点绘制片段的所有命令，叠加绝对偏移后存入命令序列。
     *
     * <p>fragment 内的命令存储相对节点局部原点的坐标（方案 A），
     * 本方法将每条命令通过 {@link PaintCommand#translatedBy(int, int)}
     * 叠加 (offsetX, offsetY) 后得到最终屏幕绝对坐标。</p>
     *
     * @param fragment 节点绘制片段（命令为相对坐标）
     * @param offsetX  节点在屏幕上的绝对 X 偏移
     * @param offsetY  节点在屏幕上的绝对 Y 偏移
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addFragment(PaintFragment fragment, int offsetX, int offsetY) {
        Objects.requireNonNull(fragment, "fragment");
        for (PaintCommand cmd : fragment.getCommands()) {
            commands.add(cmd.translatedBy(offsetX, offsetY));
        }
        return this;
    }

    /**
     * 追加一个节点绘制片段的所有命令（offset 为 0 的便捷方法）。
     *
     * @param fragment 节点绘制片段
     * @return 当前计划（支持链式调用）
     */
    public PaintPlan addFragment(PaintFragment fragment) {
        return addFragment(fragment, 0, 0);
    }

    /**
     * 返回扁平化的、供回放器顺序消费的命令序列。
     *
     * <p>渲染层只认识这个列表，每条命令自身就是绘制操作的完整描述，
     * 无需反查任何上游概念。</p>
     *
     * @return 不可变命令列表
     */
    public List<PaintCommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    /**
     * 返回命令总条数。
     *
     * @return 命令数量
     */
    public int size() {
        return commands.size();
    }

    /**
     * 清空所有命令。
     */
    public void clear() {
        commands.clear();
    }

    @Override
    public String toString() {
        return "PaintPlan{size=" + commands.size() + "}";
    }
}
