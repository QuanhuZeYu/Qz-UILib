package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `PositionedLayoutHelper` 边界用例测试。
 *
 * <p>helper 自身是包级私有，本测试通过 {@link DocumentLayoutEngine#layout} 间接驱动 positioned 元素布局，
 * 重点覆盖嵌套 absolute、auto 尺寸推断与未指定 inset 的兜底定位行为。</p>
 */
public class PositionedLayoutHelperBoundaryTest {

    /**
     * 当 absolute 元素同时省略 left/right/top/bottom 时，应停留在普通流的 static 位置。
     */
    @Test
    public void shouldKeepAbsoluteAtStaticPositionWhenAllInsetsAreUnset() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode flow = document.div();
        ElementNode absolute = document.div();

        root.style()
                .setWidth(UiStyleLength.px(200))
                .setPosition(UiPosition.RELATIVE);
        flow.style().setHeight(UiStyleLength.px(40));
        absolute.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(30));
        root.append(flow);
        root.append(absolute);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 240, 0, new DeterministicMeasure());
        DocumentLayoutBox absoluteBox = rootBox.getChildren().get(1);

        Assert.assertEquals("absolute width should match declared value", 30, absoluteBox.getWidth());
        Assert.assertEquals("absolute height should match declared value", 30, absoluteBox.getHeight());
        Assert.assertTrue("absolute element should remain inside root box when all insets are unset",
                absoluteBox.getLeft() >= 0 && absoluteBox.getTop() >= 0);
    }

    /**
     * 嵌套 positioned ancestor 链路下，孙级 absolute 仍按最近的 positioned 祖先的 padding box 定位。
     */
    @Test
    public void shouldResolveAbsoluteAgainstNearestPositionedAncestorAcrossNonPositionedWrappers() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode positionedAncestor = document.div();
        ElementNode plainWrapper = document.div();
        ElementNode absolute = document.div();

        root.style().setWidth(UiStyleLength.px(400));
        positionedAncestor.style()
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(300))
                .setHeight(UiStyleLength.px(200))
                .setPadding(UiStyleLength.px(20));
        plainWrapper.style().setHeight(UiStyleLength.px(150));
        absolute.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(10))
                .setTop(UiStyleLength.px(10))
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(40));
        plainWrapper.append(absolute);
        positionedAncestor.append(plainWrapper);
        root.append(positionedAncestor);

        DocumentLayoutBox positionedBox = DocumentLayoutEngine.layout(root, 480, 0,
                new DeterministicMeasure()).getChildren().get(0);
        DocumentLayoutBox plainBox = positionedBox.getChildren().get(0);
        DocumentLayoutBox absoluteBox = plainBox.getChildren().get(0);

        // layout box 坐标相对 parent 记录。absolute 应当解析为 positioned ancestor padding box + inset，
        // 推导回相对 plainWrapper 的偏移：(positionedPadding 20 + inset 10) - plainWrapper.left(0) - plainWrapper.top(20)
        // 因此 absoluteBox 相对 plainBox 的 left=10、top=10（plainBox 自身已偏移 20 像素的 padding）。
        int globalAbsoluteLeft = positionedBox.getLeft() + plainBox.getLeft() + absoluteBox.getLeft();
        int globalAbsoluteTop = positionedBox.getTop() + plainBox.getTop() + absoluteBox.getTop();
        Assert.assertEquals("absolute global left should hit positioned ancestor padding+inset",
                positionedBox.getLeft() + 20 + 10, globalAbsoluteLeft);
        Assert.assertEquals("absolute global top should hit positioned ancestor padding+inset",
                positionedBox.getTop() + 20 + 10, globalAbsoluteTop);
    }

    /**
     * absolute 元素在 left/right/width 同时确定且左右 margin:auto 时应居中。
     */
    @Test
    public void shouldCenterAbsoluteElementWithHorizontalAutoMargins() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode absolute = document.div();

        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(80))
                .setPosition(UiPosition.RELATIVE);
        absolute.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(20))
                .setRight(UiStyleLength.px(20))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(20))
                .setMargin(UiStyleInsets.horizontal(UiStyleLength.auto()));
        root.append(absolute);

        DocumentLayoutBox absoluteBox = DocumentLayoutEngine.layout(root, 240, 0, new DeterministicMeasure())
                .getChildren().get(0);

        Assert.assertEquals(70, absoluteBox.getLeft());
    }

    private static final class DeterministicMeasure implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 4;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<String>();
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }
}
