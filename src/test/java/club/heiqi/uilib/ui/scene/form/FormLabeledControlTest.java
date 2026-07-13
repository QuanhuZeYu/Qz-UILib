package club.heiqi.uilib.ui.scene.form;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** {@link FormLabeledControl} 的纯 scene headless 几何契约。 */
public class FormLabeledControlTest {
    private static final int VIEWPORT_WIDTH = 240;
    private static final int VIEWPORT_HEIGHT = 300;
    private static final String LONG_ENGLISH =
            "minimumRemainingDurabilityBeforeAutomaticToolReplacement";
    private static final String LONG_CHINESE =
            "工具剩余耐久度低于此阈值时自动停止并更换备用工具";

    /** 长中英文在不同字宽/行高下均须保持声明顺序，且全部受 240px 视口约束。 */
    @Test
    public void verticalWrapsLongTextWithoutOverlapOrViewportOverflow() {
        assertLongTextGeometry(LONG_ENGLISH, new FixedTextMeasurer(8, 16));
        assertLongTextGeometry(LONG_ENGLISH, new FixedTextMeasurer(13, 20));
        assertLongTextGeometry(LONG_CHINESE, new FixedTextMeasurer(8, 16));
        assertLongTextGeometry(LONG_CHINESE, new FixedTextMeasurer(13, 20));
    }

    private static void assertLongTextGeometry(String text, FixedTextMeasurer measurer) {
        SceneNode control = sizedControl(137, 29);
        SceneNode form = FormLabeledControl.vertical(text, text + text, control);

        layout(form, measurer);

        SceneNode label = form.__getChildren().get(0);
        SceneNode helper = form.__getChildren().get(1);
        assertTrue(bottom(label) <= y(helper));
        assertTrue(bottom(helper) <= y(control));
        assertInsideViewport(form, label, helper, control);
        assertEquals("控件固有宽度应由控件自己声明", 137, box(control).getWidth());
        assertEquals("wrapper 宽度来自视口约束，不得固化为控件宽度", VIEWPORT_WIDTH, box(form).getWidth());
    }

    /** label/helper 均可缺省，且不同控件固有尺寸不会被 wrapper 写死。 */
    @Test
    public void verticalAllowsOptionalTextAndPreservesDifferentControlIntrinsicSizes() {
        assertOptionalTextGeometry(null, null, 61, 17);
        assertOptionalTextGeometry("label", null, 103, 23);
        assertOptionalTextGeometry(null, "helper", 149, 31);
    }

    private static void assertOptionalTextGeometry(String label, String helper, int width, int height) {
        SceneNode control = sizedControl(width, height);
        SceneNode form = FormLabeledControl.vertical(label, helper, control);
        layout(form, new FixedTextMeasurer(8, 16));
        assertEquals(width, box(control).getWidth());
        assertEquals(height, box(control).getHeight());
        assertEquals(VIEWPORT_WIDTH, box(form).getWidth());
        assertInsideViewport(form, control);
        assertEquals((label == null ? 0 : 1) + (helper == null ? 0 : 1) + 1,
                form.__getChildren().size());
    }

    private static SceneNode sizedControl(int width, int height) {
        SceneNode control = new SceneNode();
        control.setPreferredWidth(width);
        control.setPreferredHeight(height);
        return control;
    }

    private static void layout(SceneNode root, FixedTextMeasurer measurer) {
        new SceneLayoutEngine(measurer).layout(root, new Constraints(VIEWPORT_WIDTH, VIEWPORT_HEIGHT));
    }

    private static void assertInsideViewport(SceneNode form, SceneNode... nodes) {
        assertNodeInsideViewport(form);
        for (SceneNode node : nodes) {
            assertNodeInsideViewport(node);
        }
    }

    private static void assertNodeInsideViewport(SceneNode node) {
        assertTrue(x(node) >= 0);
        assertTrue(right(node) <= VIEWPORT_WIDTH);
        assertTrue(y(node) >= 0);
        assertTrue(bottom(node) <= VIEWPORT_HEIGHT);
    }

    private static LayoutBox box(SceneNode node) {
        return (LayoutBox) node.getCachedLayout();
    }

    private static int x(SceneNode node) {
        int value = 0;
        for (SceneNode current = node; current != null; current = current.__getParent()) {
            value += box(current).getX();
        }
        return value;
    }

    private static int y(SceneNode node) {
        int value = 0;
        for (SceneNode current = node; current != null; current = current.__getParent()) {
            value += box(current).getY();
        }
        return value;
    }

    private static int right(SceneNode node) {
        return x(node) + box(node).getWidth();
    }

    private static int bottom(SceneNode node) {
        return y(node) + box(node).getHeight();
    }
}
