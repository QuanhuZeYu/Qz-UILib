package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.List;

/** 普通轴向容器与所属层共用的尺寸分配；只消费当批子测量，不保存几何。 */
public final class SceneFlowLayout {
    private SceneFlowLayout() {}
    static int cross(Size size, int desired, int available) {
        return size.constrain(size.mode == Size.Mode.FIXED ? size.value
                : size.mode == Size.Mode.FILL ? Math.max(0, available) : desired);
    }
    public static MeasuredSize measure(SceneNode node, Constraints body, SceneChildMeasurer children) {
        List<SceneNode> nodes = children.flowChildren();
        List<int[]> measured = new ArrayList<>();
        boolean row = node.axis() == SceneNode.Axis.ROW;
        for (SceneNode child : nodes) {
            MeasuredSize m = children.measureChild(child, new Constraints(body.availableWidth,
                    row ? body.availableHeight : Integer.MAX_VALUE));
            measured.add(new int[] {m.width, m.height});
        }
        int gaps = Math.max(0, nodes.size() - 1) * node.gap();
        // 有限高度的 COLUMN 在测量期也分配 FILL，才能向父自然宽传播图像等真实比例。
        int[] allocated = row ? allocate(children, nodes, measured, Math.max(0, body.availableWidth - gaps), true)
                : body.availableHeight != Integer.MAX_VALUE
                    ? allocate(children, nodes, measured, Math.max(0, body.availableHeight - gaps), false) : null;
        int sum = 0, maximum = 0;
        for (int i = 0; i < nodes.size(); i++) {
            SceneNode child = nodes.get(i);
            int ah = row ? body.availableHeight : allocated == null ? Integer.MAX_VALUE : allocated[i];
            MeasuredSize h = children.measureChild(child, new Constraints(body.availableWidth, ah));
            int cw = row ? allocated[i] : cross(children.sizeIntent(child, SceneNode.Axis.ROW), h.width, body.availableWidth);
            MeasuredSize actual = children.measureChild(child, new Constraints(cw, ah));
            int ch = !row && allocated != null ? allocated[i]
                    : children.sizeIntent(child, SceneNode.Axis.COLUMN).constrain(children.sizeIntent(child, SceneNode.Axis.COLUMN).mode == Size.Mode.FIXED ? children.sizeIntent(child, SceneNode.Axis.COLUMN).value : actual.height);
            sum += row ? cw : ch;
            maximum = Math.max(maximum, row ? ch : cw);
        }
        return new MeasuredSize(row ? sum + gaps : maximum, row ? maximum : sum + gaps);
    }
    public static ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
        List<SceneNode> nodes = children.flowChildren();
        List<int[]> measured = new ArrayList<>();
        int iw = Math.max(0, box.width - node.padding() * 2), ih = Math.max(0, box.height - node.padding() * 2);
        boolean row = node.axis() == SceneNode.Axis.ROW;
        for (SceneNode child : nodes) {
            MeasuredSize m = children.measureChild(child, new Constraints(iw, row ? ih : Integer.MAX_VALUE));
            measured.add(new int[] {m.width, m.height});
        }
        int[] allocated = allocate(children, nodes, measured, Math.max(0, (row ? iw : ih)
                - Math.max(0, nodes.size() - 1) * node.gap()), row);
        List<ChildPlacements.Entry> placed = new ArrayList<>();
        int cursor = 0;
        for (int i = 0; i < nodes.size(); i++) {
            SceneNode child = nodes.get(i);
            int ah = row ? ih : allocated[i];
            MeasuredSize h = children.measureChild(child, new Constraints(iw, ah));
            int cw = row ? allocated[i] : cross(children.sizeIntent(child, SceneNode.Axis.ROW), h.width, iw);
            MeasuredSize actual = children.measureChild(child, new Constraints(cw, ah));
            int ch = row ? cross(children.sizeIntent(child, SceneNode.Axis.COLUMN), actual.height, ih) : allocated[i];
            placed.add(new ChildPlacements.Entry(child, new LayoutBox(node.padding() + (row ? cursor : 0),
                    node.padding() + (row ? 0 : cursor), cw, ch)));
            cursor += allocated[i] + node.gap();
        }
        return new ChildPlacements(placed);
    }
    /** 有界水位分配：固定占用保留，触及边界的填充退出，余数沿子序分配。 */
    static int[] allocate(
            SceneChildMeasurer children, List<SceneNode> nodes, List<int[]> measured, int available, boolean row) {
        int[] result = new int[nodes.size()];
        List<Integer> active = new ArrayList<>();
        int remaining = available;
        for (int i = 0; i < nodes.size(); i++) {
            Size size = row ? children.sizeIntent(nodes.get(i), SceneNode.Axis.ROW) : children.sizeIntent(nodes.get(i), SceneNode.Axis.COLUMN);
            if (size.mode == Size.Mode.FILL) active.add(i);
            else {
                result[i] =
                        size.constrain(
                                size.mode == Size.Mode.FIXED
                                        ? size.value
                                        : measured.get(i)[row ? 0 : 1]);
                remaining -= result[i];
            }
        }
        if (!active.isEmpty()) {
            long minimum = 0, maximum = 0;
            double upper = 0;
            for (int i : active) {
                Size size = row ? children.sizeIntent(nodes.get(i), SceneNode.Axis.ROW) : children.sizeIntent(nodes.get(i), SceneNode.Axis.COLUMN);
                minimum += size.min;
                maximum += size.max;
                upper = Math.max(upper, (double) size.max / size.value);
            }
            long target = Math.max(minimum, Math.min(maximum, Math.max(0, remaining)));
            double lower = 0;
            // 求所有上下界的共同水位；临时触及 min 的项仍可接回 max 释放的空间。
            for (int step = 0; step < 80; step++) {
                double water = (lower + upper) / 2, total = 0;
                for (int i : active) {
                    Size size = row ? children.sizeIntent(nodes.get(i), SceneNode.Axis.ROW) : children.sizeIntent(nodes.get(i), SceneNode.Axis.COLUMN);
                    total += Math.max(size.min, Math.min(size.max, water * size.value));
                }
                if (total < target) lower = water;
                else upper = water;
            }
            long fixed = 0, freeWeights = 0;
            List<Integer> free = new ArrayList<>();
            for (int i : active) {
                Size size = row ? children.sizeIntent(nodes.get(i), SceneNode.Axis.ROW) : children.sizeIntent(nodes.get(i), SceneNode.Axis.COLUMN);
                double share = upper * size.value;
                if (share <= size.min) {
                    result[i] = size.min;
                    fixed += result[i];
                } else if (share >= size.max) {
                    result[i] = size.max;
                    fixed += result[i];
                } else {
                    free.add(i);
                    freeWeights += size.value;
                }
            }
            // 水位只用于识别约束区间；自由项用整数有理分配，避免浮点整数边界错分余数。
            for (int i : free) {
                Size size = row ? children.sizeIntent(nodes.get(i), SceneNode.Axis.ROW) : children.sizeIntent(nodes.get(i), SceneNode.Axis.COLUMN);
                result[i] = size.constrain((int) ((target - fixed) * size.value / freeWeights));
            }
            long distributed = 0;
            for (int i : active) distributed += result[i];
            long rest = target - distributed;
            for (int i : active) {
                Size size = row ? children.sizeIntent(nodes.get(i), SceneNode.Axis.ROW) : children.sizeIntent(nodes.get(i), SceneNode.Axis.COLUMN);
                if (rest > 0 && result[i] < size.max) {
                    result[i]++;
                    rest--;
                }
            }
        }
        return result;
    }
}
