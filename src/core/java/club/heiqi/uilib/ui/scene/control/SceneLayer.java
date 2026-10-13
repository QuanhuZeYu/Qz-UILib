package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 直属逻辑父节点是来源；核心协调所属层位置、裁剪与呈现顺序。 */
public final class SceneLayer {
    public enum Participation { INTERACTIVE, PASSIVE }
    public static final class Policy {
        public final int gap;
        public final Participation participation;
        public Policy(int gap, Participation participation) {
            if (gap < 0) throw new IllegalArgumentException("所属层 gap 不能为负");
            this.gap = gap;
            this.participation = Objects.requireNonNull(participation, "participation");
        }
    }
    private SceneLayer() {}
    /** 外部策略仍归声明者；visible/ownedChildren 表达打开与结束。 */
    public static SceneNode create(ScenePageContext context, ReadableSignal<Policy> policy) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return new SceneNode().axis(SceneNode.Axis.COLUMN).size(Size.natural(), Size.natural())
                .control(new Control(Objects.requireNonNull(policy, "policy")));
    }
    private static final class Control implements SceneControl {
        private final ReadableSignal<Policy> policy;
        Control(ReadableSignal<Policy> policy) { this.policy = policy; }
        public Policy layerPolicy(SceneNode node) {
            return Objects.requireNonNull(policy.get(), "layer policy value: node=" + node.id());
        }
        public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(policy); }
        public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) { return Collections.singletonList(policy); }
        public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) {
            throw new IllegalStateException("所属层测量需要当批子测量能力: node=" + node.id());
        }
        public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) {
            layerPolicy(node);
            return SceneFlowLayout.measure(node, body, children);
        }
        public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            layerPolicy(node);
            return SceneFlowLayout.arrange(node, box, children);
        }
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if ((node.background() >>> 24) != 0) target.add(PaintCommand.rect(entry.box, entry.clip, node.background()));
        }
    }
}
