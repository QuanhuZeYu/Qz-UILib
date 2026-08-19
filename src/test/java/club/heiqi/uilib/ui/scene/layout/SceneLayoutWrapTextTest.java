package club.heiqi.uilib.ui.scene.layout;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * 文本叶 wrap 感知布局测试：拆行后逐行行高求和的高度、maxTextWidth 内容宽、非 wrap 旧口径零回归。
 */
public class SceneLayoutWrapTextTest {

    @Test
    public void shouldSizeWrapTextLeafByPerLineHeights() {
        WrapMeasurer measurer = new WrapMeasurer();
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);

        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("AAAABBBBB");
        textNode.setMaxTextWidth(40);
        textNode.setPadding(5);
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(200, 200));

        LayoutBox box = (LayoutBox) textNode.getCachedLayout();
        Assert.assertNotNull(box);
        // 高度 = 逐行行高求和（16+24）+ 上下 padding 10
        Assert.assertEquals(50, box.getHeight());
        // 宽度：wrap 内容宽为 maxTextWidth（约束链路内宽 40，宽不超过父约束 200）
        Assert.assertTrue(box.getWidth() >= 40);
        Assert.assertTrue(box.getWidth() <= 200);
    }

    @Test
    public void shouldKeepNonWrapLeafHeightByLogicalLines() {
        WrapMeasurer measurer = new WrapMeasurer();
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);

        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("a\nb");
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(200, 200));

        LayoutBox box = (LayoutBox) textNode.getCachedLayout();
        Assert.assertNotNull(box);
        // 非 wrap：逻辑行数 2 × 统一行高 16（旧口径零回归）
        Assert.assertEquals(32, box.getHeight());
        // 垂直栈文本叶宽为 fill 语义（=约束宽 200）
        Assert.assertEquals(200, box.getWidth());
    }

    @Test
    public void shouldSizeNonWrapRichTextByMaxSpanLineHeight() {
        WrapMeasurer measurer = new WrapMeasurer();
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);

        SceneNode root = new SceneNode();
        SceneNode textNode = new SceneNode();
        textNode.setText("A<size=32>x</size>");
        textNode.setTextContentMode(2);
        root.appendChild(textNode);

        layoutEngine.layout(root, new Constraints(200, 200));

        LayoutBox box = (LayoutBox) textNode.getCachedLayout();
        Assert.assertNotNull(box);
        // 非 wrap 富文本：行高按行内最大显式字号（fake 返回 30），大字 span 不再撑破行框
        Assert.assertEquals(30, box.getHeight());
    }

    @Test
    public void shouldSizeWrapTextLeafInsideRowWithSiblings() {
        WrapMeasurer measurer = new WrapMeasurer();
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);

        SceneNode root = new SceneNode();
        SceneNode row = SceneNode.row(0);
        SceneNode wrapText = new SceneNode();
        wrapText.setText("AAAABBBBB");
        wrapText.setMaxTextWidth(40);
        SceneNode sibling = new SceneNode();
        sibling.setPreferredWidth(30);
        sibling.setPreferredHeight(20);
        row.appendChild(wrapText);
        row.appendChild(sibling);
        root.appendChild(row);

        layoutEngine.layout(root, new Constraints(300, 300));

        // wrap 文本叶在 ROW 内高度按逐行行高求和（16+24=40）；兄弟 20 高不受影响
        LayoutBox wrapBox = (LayoutBox) wrapText.getCachedLayout();
        Assert.assertNotNull(wrapBox);
        Assert.assertEquals(40, wrapBox.getHeight());
        LayoutBox siblingBox = (LayoutBox) sibling.getCachedLayout();
        Assert.assertNotNull(siblingBox);
        Assert.assertEquals(20, siblingBox.getHeight());
    }

    /** wrap 感知测量替身：固定拆两行、行高按行文本定制。 */
    private static final class WrapMeasurer implements SceneTextMeasurer {

        @Override
        public int measureWidth(String text, int fontSizePx) {
            return (text == null ? 0 : text.length()) * 8;
        }

        @Override
        public int lineHeight(int fontSizePx) {
            return 16;
        }

        @Override
        public int epoch() {
            return 0;
        }

        @Override
        public List<String> splitLines(String text, int fontSizePx, int wrapWidth, int textMode) {
            return Arrays.asList("AAAA", "BB");
        }

        @Override
        public int lineHeight(String text, int fontSizePx, int textMode) {
            if (text != null && text.contains("<size")) {
                return 30;
            }
            if ("BB".equals(text)) {
                return 24;
            }
            return 16;
        }
    }
}
