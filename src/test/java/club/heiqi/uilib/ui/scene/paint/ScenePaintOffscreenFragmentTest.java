package club.heiqi.uilib.ui.scene.paint;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;

/** 普通配置行没有 clipChildren；屏外行不得继续消耗文字需求和绘制重放。 */
public class ScenePaintOffscreenFragmentTest {
    private final FixedTextMeasurer measurer = new FixedTextMeasurer();
    private final SceneLayoutEngine layout = new SceneLayoutEngine(measurer);
    private final ScenePaintEngine painter = new ScenePaintEngine(measurer);

    private SceneNode viewport() {
        SceneNode node = SceneNode.column();
        node.setPreferredWidth(200);
        node.setPreferredHeight(40);
        node.setScrollable(true);
        return node;
    }

    private SceneNode row(SceneNode parent, String text) {
        SceneNode row = new SceneNode();
        row.setPreferredHeight(20);
        row.setText(text);
        row.setTextVerticalAlign(TextVerticalAlign.TOP);
        row.setBackgroundColor(0xFF334455);
        parent.appendChild(row);
        return row;
    }

    private PaintResult paint(SceneNode root) {
        layout.layout(root, new Constraints(200, 100));
        return painter.paint(root);
    }

    private boolean contains(PaintPlan plan, String text) {
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.TEXT && text.equals(command.getText())) return true;
        }
        return false;
    }

    @Test
    public void ordinaryOffscreenRowsDoNotGrowReplayPlan() {
        SceneNode small = viewport();
        SceneNode large = viewport();
        for (int i = 0; i < 20; i++) row(small, "field-" + i);
        for (int i = 0; i < 200; i++) row(large, "field-" + i);
        PaintPlan smallPlan = paint(small).getPlan();
        PaintPlan largePlan = paint(large).getPlan();
        Assert.assertTrue(contains(largePlan, "field-0"));
        Assert.assertFalse(contains(largePlan, "field-199"));
        Assert.assertEquals(smallPlan.getCommands(), largePlan.getCommands());
    }

    @Test
    public void scrollReusesFragmentsAndOffscreenEditsReappear() {
        SceneNode root = viewport();
        row(root, "first");
        row(root, "second");
        SceneNode last = row(root, "last");
        Assert.assertFalse(contains(paint(root).getPlan(), "last"));
        Object cached = last.getCachedPaint();
        root.setScrollOffsetY(40);
        PaintResult scrolled = paint(root);
        Assert.assertTrue(contains(scrolled.getPlan(), "last"));
        Assert.assertEquals(0, scrolled.getRegeneratedFragmentCount());
        Assert.assertSame(cached, last.getCachedPaint());
        root.setScrollOffsetY(0);
        last.setText("changed");
        Assert.assertFalse(contains(paint(root).getPlan(), "changed"));
        root.setScrollOffsetY(40);
        Assert.assertTrue(contains(paint(root).getPlan(), "changed"));
    }

    @Test
    public void overflowFromOffscreenParentStillPaints() {
        SceneNode root = viewport();
        row(root, "first");
        row(root, "second");
        SceneNode parent = row(root, "outside");
        SceneNode child = row(parent, "visible-child");
        child.__setPresentationOffsetY(-40);
        PaintPlan plan = paint(root).getPlan();
        Assert.assertFalse(contains(plan, "outside"));
        Assert.assertTrue(contains(plan, "visible-child"));
    }

    @Test
    public void ancestorTransformProtectsDescendantsFromUnprojectedCulling() {
        SceneNode root = viewport();
        row(root, "first");
        row(root, "second");
        SceneNode parent = SceneNode.column();
        parent.setTransform(Transform.translate(0, -40));
        root.appendChild(parent);
        SceneNode child = row(parent, "translated-child");
        child.setClipChildren(true);
        Assert.assertTrue(contains(paint(root).getPlan(), "translated-child"));
    }

    @Test
    public void fontEpochRefreshesBoundsEvenWithFixedNodeHeight() {
        final int[] height = {16};
        club.heiqi.uilib.ui.scene.text.SceneTextMeasurer changing =
                new club.heiqi.uilib.ui.scene.text.SceneTextMeasurer() {
                    public int measureWidth(String text, int size) { return 20; }
                    public int lineHeight(int size) { return height[0]; }
                    public int epoch() { return height[0]; }
                };
        SceneLayoutEngine changingLayout = new SceneLayoutEngine(changing);
        ScenePaintEngine changingPainter = new ScenePaintEngine(changing);
        SceneNode root = viewport();
        SceneNode text = row(root, "font-change");
        text.setPreferredHeight(1);
        root.setScrollOffsetY(30);
        changingLayout.layout(root, new Constraints(200, 100));
        Assert.assertFalse(contains(changingPainter.paint(root).getPlan(), "font-change"));
        height[0] = 60;
        changingLayout.layout(root, new Constraints(200, 100));
        Assert.assertTrue(contains(changingPainter.paint(root).getPlan(), "font-change"));
    }

    @Test
    public void textOverflowUsesFontHeightInsteadOfNodeHeight() {
        SceneNode root = viewport();
        SceneNode text = row(root, "overflow");
        text.setPreferredHeight(1);
        root.setScrollOffsetY(8);
        Assert.assertTrue(contains(paint(root).getPlan(), "overflow"));
    }
}
