package club.heiqi.uilib.ui.scene.paint;

import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.scene.control.SceneListOps;

/**
 * 单节点绘制片段缓存单元。
 *
 * <p>每个场景节点在绘制阶段产出一组绘制命令，封装为 {@code PaintFragment}。
 * 后续可配合节点级 paint 脏标记实现缓存复用（宪章 I8）：若节点的
 * {@code selfPaintDirty} 未标，其绘制属性不变，对应的 PaintFragment 可跳过重建直接复用。</p>
 *
 * <p>本类不可变：构造时做防御性拷贝，对外暴露不可变视图。</p>
 */
public final class PaintFragment {

    /** 该节点产出的绘制命令列表（不可变视图） */
    private final List<PaintCommand> commands;

    /**
     * 创建绘制片段。
     *
     * @param commands 绘制命令列表（防御性拷贝，调用后外部修改不影响本对象）
     */
    public PaintFragment(List<PaintCommand> commands) {
        Objects.requireNonNull(commands, "commands");
        this.commands = SceneListOps.immutableCopy(commands);
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
