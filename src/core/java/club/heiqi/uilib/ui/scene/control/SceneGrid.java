package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 普通响应网格；子项身份沿现有声明，排列行为见 specs/scene/2026-10-13-uilib-layout-spec.md。 */
public final class SceneGrid {
    /** 不可变 logical px 策略；可用空间由核心提供，调用方无需计算列数或子盒。 */
    public static final class Policy {
        public final int minimumColumnWidth, columnGap, rowGap;

        public Policy(int minimumColumnWidth, int columnGap, int rowGap) {
            if (minimumColumnWidth <= 0 || columnGap < 0 || rowGap < 0)
                throw new IllegalArgumentException("网格最小列宽必须为正，间距不能为负");
            this.minimumColumnWidth = minimumColumnWidth;
            this.columnGap = columnGap;
            this.rowGap = rowGap;
        }
    }

    private SceneGrid() {}

    /** 默认填充宽、自然高且不裁剪、不滚动；外部策略来源仍归调用方。 */
    public static SceneNode create(ScenePageContext context, ReadableSignal<Policy> policy) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return new SceneNode().axis(SceneNode.Axis.COLUMN).size(Size.fill(1), Size.natural())
                .control(new Control(Objects.requireNonNull(policy, "policy")));
    }

    private static final class Control implements SceneControl {
        private final ReadableSignal<Policy> policy;

        Control(ReadableSignal<Policy> policy) { this.policy = policy; }

        @Override
        public List<ReadableSignal<?>> measureDependencies(SceneNode node) {
            return Collections.singletonList(policy);
        }

        @Override
        public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }

        @Override
        public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text,
                                    SceneChildMeasurer children) {
            return layout(node, body.availableWidth, children, null, 0);
        }

        @Override
        public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            int padding = node.padding();
            layout(node, Math.max(0, allocated.width - padding * 2), children, placed, padding);
            return new ChildPlacements(placed);
        }

        private MeasuredSize layout(SceneNode node, int availableWidth, SceneChildMeasurer children,
                                    List<ChildPlacements.Entry> placed, int padding) {
            List<SceneNode> visible = new ArrayList<>();
            for (SceneNode child : children.flowChildren()) if (child.visible()) visible.add(child);
            if (visible.isEmpty()) return new MeasuredSize(0, 0);
            Policy current = Objects.requireNonNull(policy.get(), "policy value");
            int columns = Math.min(visible.size(), Math.max(1,
                    (int) (((long) availableWidth + current.columnGap)
                            / ((long) current.minimumColumnWidth + current.columnGap))));
            int gaps = (columns - 1) * current.columnGap;
            int naturalWidth = columns * current.minimumColumnWidth + gaps;
            // NATURAL 在测量和排列都使用最小列槽，不能先用父满宽测矮了再收窄。
            int width = node.width().mode == Size.Mode.NATURAL
                    ? naturalWidth : Math.max(availableWidth, naturalWidth);
            int baseWidth = (width - gaps) / columns, remainder = (width - gaps) % columns;
            int y = 0;
            for (int start = 0; start < visible.size(); start += columns) {
                int count = Math.min(columns, visible.size() - start), rowHeight = 0;
                int[] widths = new int[count], heights = new int[count];
                for (int column = 0; column < count; column++) {
                    SceneNode child = visible.get(start + column);
                    int slot = baseWidth + (column < remainder ? 1 : 0);
                    MeasuredSize intrinsic = children.measureChild(child,
                            new Constraints(slot, Integer.MAX_VALUE));
                    widths[column] = children.sizeIntent(child, SceneNode.Axis.ROW).mode == Size.Mode.FILL
                            ? children.sizeIntent(child, SceneNode.Axis.ROW).constrain(slot) : intrinsic.width;
                    // 未确定行高时不给 FILL 高度分配父剩余空间；真实高度按最终合法宽度求解。
                    MeasuredSize actual = children.measureChild(child,
                            new Constraints(widths[column], Integer.MAX_VALUE));
                    heights[column] = actual.height;
                    rowHeight = Math.max(rowHeight, actual.height);
                }
                int x = 0;
                for (int column = 0; column < count; column++) {
                    SceneNode child = visible.get(start + column);
                    int height = children.sizeIntent(child, SceneNode.Axis.COLUMN).mode == Size.Mode.FILL
                            ? children.sizeIntent(child, SceneNode.Axis.COLUMN).constrain(rowHeight) : heights[column];
                    if (placed != null) placed.add(new ChildPlacements.Entry(child,
                            new LayoutBox(padding + x, padding + y, widths[column], height)));
                    // 最后一行沿完整网格的槽宽，硬子项溢出不改变下一列位置。
                    x += baseWidth + (column < remainder ? 1 : 0) + current.columnGap;
                }
                y += rowHeight;
                if (start + count < visible.size()) y += current.rowGap;
            }
            return new MeasuredSize(width, y);
        }

        @Override
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
    }
}
