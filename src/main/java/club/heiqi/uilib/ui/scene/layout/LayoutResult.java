package club.heiqi.uilib.ui.scene.layout;

import java.util.Collections;
import java.util.Set;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * layout() 返回的不可变结果，携带 I7/I8 测试探针。
 *
 * <p>本类是 Display List 契约线阶段 1 的产物：把原本散落在 {@link SceneLayoutEngine}
 * 实例字段中的「本次 layout 重算次数 / 重算节点集合 / 约束被迫重算节点集合」探针
 * 打包成 per-call 不可变交付物，使引擎逐步走向无状态化（守 NORTH_STAR I7/I8）。</p>
 *
 * <h3>不可变契约</h3>
 * <ul>
 *   <li>{@link #relayoutCount}：本次 layout 中因 selfLayoutDirty 触发的重算次数（I7 语义）。</li>
 *   <li>{@link #relayoutedNodes}：本次 layout 中因 selfLayoutDirty 被重算的节点集合（I7 断言）。</li>
 *   <li>{@link #constraintRelayoutedNodes}：本次 layout 中因「收到的约束变化」被迫重算自身尺寸、
 *       但自身未脏的节点集合（如深层 fill 节点感知父高、Grid 感知父宽变化；
 *       与 relayoutedNodes 严格分离）。</li>
 * </ul>
 *
 * <p>所有集合返回不可变视图，调用方不可修改。阶段 2 子树并行化后，
 * 每个 worker 产出的 LayoutResult 可独立合并，无共享可变状态。</p>
 */
public final class LayoutResult {

    /** 本次 layout 的重算次数（selfLayoutDirty 触发，I7 语义）。 */
    private final int relayoutCount;

    /** 本次 layout 中因 selfLayoutDirty 被重算的节点集合。 */
    private final Set<SceneNode> relayoutedNodes;

    /** 本次 layout 中因约束变化被迫重算、但自身未脏的节点集合。 */
    private final Set<SceneNode> constraintRelayoutedNodes;

    /**
     * 创建 layout 结果。
     *
     * @param relayoutCount            重算次数
     * @param relayoutedNodes           因 selfLayoutDirty 被重算的节点集合（非 null）
     * @param constraintRelayoutedNodes 因约束变化被迫重算的节点集合（非 null）
     */
    public LayoutResult(int relayoutCount, Set<SceneNode> relayoutedNodes,
                        Set<SceneNode> constraintRelayoutedNodes) {
        if (relayoutedNodes == null || constraintRelayoutedNodes == null) {
            throw new IllegalArgumentException("节点集合不可为 null");
        }
        this.relayoutCount = relayoutCount;
        this.relayoutedNodes = relayoutedNodes;
        this.constraintRelayoutedNodes = constraintRelayoutedNodes;
    }

    /** @return 本次 layout 的重算次数（I7 测试探针） */
    public int getRelayoutCount() {
        return relayoutCount;
    }

    /** @return 因 selfLayoutDirty 被重算的节点集合的不可变视图（I7 测试探针） */
    public Set<SceneNode> getRelayoutedNodes() {
        return Collections.unmodifiableSet(relayoutedNodes);
    }

    /** @return 因约束变化被迫重算的节点集合的不可变视图（I7 测试探针） */
    public Set<SceneNode> getConstraintRelayoutedNodes() {
        return Collections.unmodifiableSet(constraintRelayoutedNodes);
    }
}
