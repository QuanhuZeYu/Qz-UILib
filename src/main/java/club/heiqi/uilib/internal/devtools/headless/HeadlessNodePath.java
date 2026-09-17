package club.heiqi.uilib.internal.devtools.headless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 节点地址：把一个节点表达成「从某个已登记树根出发的子节点下标序列」。
 *
 * <h3>为什么是下标路径而不是 UUID</h3>
 * <p>框架层没有节点身份（无 id / tag / userData），而 agent 要的「按可见文本点某个控件」需要一条
 * <b>跨调用稳定</b>的地址。下标路径满足这一点且不需要给框架加身份：</p>
 * <ul>
 *   <li><b>稳定</b>：同一页面、同一尺寸、同一环境下的两次出图，同一节点的路径相同（出图是确定性的）；</li>
 *   <li><b>可复现</b>：路径写进脚本后跨进程有效 —— 而 {@code System.identityHashCode} 只在单次
 *       JVM 内成立，不能写进脚本；</li>
 *   <li><b>结构即事实</b>：路径只记录结构位置，不记录字号/尺寸等会随环境变化的值。</li>
 * </ul>
 *
 * <h3>为什么必须带根序号</h3>
 * <p>一个 runtime 可以登记多棵树根（主树 + 各浮层），路径单独看不足以定位。根序号是登记顺序
 * （{@link #rootIndex()}）—— 同一装配路径下顺序确定。</p>
 *
 * <h3>语法</h3>
 * <pre>
 *   r0           第 0 个树根
 *   r0/3/1       第 0 个树根的第 3 个子节点的第 1 个子节点
 *   r1/0         第 1 个树根（浮层）的第 0 个子节点
 * </pre>
 * <p>写作 r&lt;根序号&gt;[/&lt;子下标&gt;]…：可读、可打印、可直接进输入脚本。</p>
 */
public final class HeadlessNodePath {

    private final int rootIndex;
    private final List<Integer> childIndexes;

    private HeadlessNodePath(int rootIndex, List<Integer> childIndexes) {
        this.rootIndex = rootIndex;
        this.childIndexes = Collections.unmodifiableList(childIndexes);
    }

    /**
     * 某棵树的根（空路径）。
     *
     * @param rootIndex 根序号
     * @return 根地址
     */
    public static HeadlessNodePath root(int rootIndex) {
        return of(rootIndex, null);
    }

    /**
     * 由根序号与子下标序列构造。
     *
     * @param rootIndex     根序号（>= 0）
     * @param childIndexes  子下标序列；null = 根
     * @return 地址
     */
    public static HeadlessNodePath of(int rootIndex, List<Integer> childIndexes) {
        if (rootIndex < 0) {
            throw new IllegalArgumentException("根序号不可为负：" + rootIndex);
        }
        List<Integer> copy = new ArrayList<Integer>();
        if (childIndexes != null) {
            for (Integer index : childIndexes) {
                if (index == null || index.intValue() < 0) {
                    throw new IllegalArgumentException("子下标不可为负：" + index);
                }
                copy.add(index);
            }
        }
        return new HeadlessNodePath(rootIndex, copy);
    }

    /**
     * 解析地址文本。
     *
     * @param text 形如 {@code r0} / {@code r0/3/1}
     * @return 地址
     * @throws HeadlessFailure 文本不是合法地址
     */
    public static HeadlessNodePath parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY, "节点地址不可为空");
        }
        String[] parts = text.trim().split("/");
        String head = parts[0].trim();
        if (!head.startsWith("r") || head.length() < 2) {
            throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY,
                    "节点地址必须以 r<根序号> 开头：" + text);
        }
        int rootIndex;
        try {
            rootIndex = Integer.parseInt(head.substring(1));
        } catch (NumberFormatException e) {
            throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY, "根序号不是整数：" + text);
        }
        List<Integer> childIndexes = new ArrayList<Integer>();
        for (int i = 1; i < parts.length; i++) {
            try {
                childIndexes.add(Integer.valueOf(Integer.parseInt(parts[i].trim())));
            } catch (NumberFormatException e) {
                throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY, "子下标不是整数：" + text);
            }
        }
        return of(rootIndex, childIndexes);
    }

    /** @return 根序号（登记顺序） */
    public int rootIndex() {
        return rootIndex;
    }

    /** @return 子下标序列（不可变；根为空列表） */
    public List<Integer> childIndexes() {
        return childIndexes;
    }

    /**
     * 沿路径解析到节点。
     *
     * <p>越界即失败并带出「同层有几个子节点」这一可操作事实 —— agent 拿到它就能改下标重试，
     * 而不是只看到「没找到」。</p>
     *
     * @param root 该根序号的树根
     * @return 目标节点
     * @throws HeadlessFailure 根缺失或路径越界
     */
    public SceneNode resolve(SceneNode root) {
        if (root == null) {
            throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY, "根不存在：" + this);
        }
        SceneNode current = root;
        for (int depth = 0; depth < childIndexes.size(); depth++) {
            List<SceneNode> children = current.__getChildren();
            int index = childIndexes.get(depth).intValue();
            if (index >= children.size()) {
                throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY,
                        "地址越界：" + this + " 在第 " + depth + " 层要下标 " + index
                                + "，但该节点只有 " + children.size() + " 个子节点");
            }
            current = children.get(index);
        }
        return current;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("r").append(rootIndex);
        for (Integer index : childIndexes) {
            sb.append('/').append(index.intValue());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HeadlessNodePath)) {
            return false;
        }
        HeadlessNodePath that = (HeadlessNodePath) other;
        return rootIndex == that.rootIndex && childIndexes.equals(that.childIndexes);
    }

    @Override
    public int hashCode() {
        return rootIndex * 31 + childIndexes.hashCode();
    }
}
