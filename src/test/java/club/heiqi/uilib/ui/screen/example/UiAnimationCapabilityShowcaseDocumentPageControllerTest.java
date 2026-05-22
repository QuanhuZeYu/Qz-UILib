package club.heiqi.uilib.ui.screen.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiAnimationCapabilityShowcaseDocumentPageController` 的页面集成契约测试。
 */
public class UiAnimationCapabilityShowcaseDocumentPageControllerTest {

    /**
     * 验证动画能力成功展示页会挂接独立 HTML-like 文档并渲染关键内容。
     */
    @Test
    public void shouldBuildAnimationCapabilityShowcaseDocumentTree() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(0), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(fixture.controller.getHtmlLikeDocumentWidget().isViewportRootScrollingEnabled());

        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        ElementNode root = widget.getDocument().getRootElement();
        Assert.assertEquals("animation-capability-showcase", root.getAttribute("data-diagnostic-page"));

        List<String> texts = collectDocumentTexts(widget);
        Assert.assertTrue(containsText(texts, "动画能力成功展示"));
        Assert.assertTrue(containsText(texts, "REVIEW-20260521"));
        Assert.assertTrue(containsText(texts, "Phase 3"));
        Assert.assertTrue(containsText(texts, "触发 transition"));
        Assert.assertTrue(containsText(texts, "命令式 animate()"));
        Assert.assertTrue(containsText(texts, "Opacity 单独展示"));
        Assert.assertTrue(containsText(texts, "功能预期行为总表"));
        Assert.assertTrue(containsText(texts, "Timing Function 对照"));
        Assert.assertTrue(containsText(texts, "steps(5,start)"));
        Assert.assertTrue(containsText(texts, "Effect 实验区"));
        Assert.assertTrue(containsText(texts, "Backdrop blur"));
        Assert.assertTrue(containsText(texts, "Layout 动画实验区"));
        Assert.assertTrue(containsText(texts, "切换 layout 动画"));
        Assert.assertTrue(containsText(texts, "切换 rotate+layout 组合模拟"));
        Assert.assertTrue(containsText(texts, "不是 rotate 本身"));
        Assert.assertTrue(containsText(texts, "预期：整组透明度合成"));
        Assert.assertTrue(containsText(texts, "当前边界"));

        Assert.assertFalse(widget.getDocument().getKeyframes("phasePulse").getFloatTracks()
                .containsKey(DocumentAnimationProperty.OPACITY));
        Assert.assertTrue(widget.getDocument().getKeyframes("opacityBreath").getFloatTracks()
                .containsKey(DocumentAnimationProperty.OPACITY));
        Assert.assertTrue(widget.getDocument().getKeyframes("timingSlide").getFloatTracks()
                .containsKey(DocumentAnimationProperty.TRANSLATE_X));
        Assert.assertTrue(widget.getDocument().getKeyframes("backdropPulse").getFloatTracks()
                .containsKey(DocumentAnimationProperty.BACKDROP_BLUR_RADIUS));
        assertAnimationStage(root, "transition-transform");
        assertAnimationStage(root, "keyframe-transform");
        assertAnimationStage(root, "imperative-transform");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layoutViewportRoot(root, 980, 660,
                fixture.textMeasureService);
        Assert.assertTrue(rootBox.getChildren().size() >= 6);
    }

    /**
     * 验证指定动画舞台会保留可见溢出，避免 transform 视觉内容被自身容器裁掉。
     *
     * @param root 文档根元素
     * @param key 舞台标识
     */
    private static void assertAnimationStage(ElementNode root, String key) {
        ElementNode stage = findElementByAttribute(root, "data-animation-stage", key);
        Assert.assertNotNull("缺少动画舞台：" + key, stage);
        Assert.assertEquals(UiOverflow.VISIBLE, stage.style().getOverflowX());
        Assert.assertEquals(UiOverflow.VISIBLE, stage.style().getOverflowY());
    }

    /**
     * 收集文档树中的全部文本。
     *
     * @param widget HTML-like 文档组件
     * @return 文本列表
     */
    private static List<String> collectDocumentTexts(HtmlLikeDocumentWidget widget) {
        List<String> texts = new ArrayList<String>();
        if (widget == null || widget.getDocument() == null) {
            return texts;
        }
        collectTextsFromNode(widget.getDocument().getRootElement(), texts);
        return texts;
    }

    /**
     * 递归收集节点文本。
     *
     * @param node 当前节点
     * @param texts 文本输出列表
     */
    private static void collectTextsFromNode(DocumentNode node, List<String> texts) {
        if (node.getNodeType() == DocumentNodeType.TEXT) {
            String text = ((TextNode) node).getText();
            if (text != null && !text.isEmpty()) {
                texts.add(text);
            }
        }
        if (node.getNodeType() == DocumentNodeType.ELEMENT) {
            ElementNode element = (ElementNode) node;
            for (DocumentNode child : element.getChildren()) {
                collectTextsFromNode(child, texts);
            }
        }
    }

    /**
     * 按属性查找第一个匹配元素。
     *
     * @param node 当前节点
     * @param attributeName 属性名
     * @param expectedValue 期望值
     * @return 匹配元素；找不到时返回 null
     */
    private static ElementNode findElementByAttribute(DocumentNode node, String attributeName, String expectedValue) {
        if (node.getNodeType() != DocumentNodeType.ELEMENT) {
            return null;
        }
        ElementNode element = (ElementNode) node;
        if (expectedValue.equals(element.getAttribute(attributeName))) {
            return element;
        }
        for (DocumentNode child : element.getChildren()) {
            ElementNode matched = findElementByAttribute(child, attributeName, expectedValue);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    /**
     * 判断文本列表是否包含指定片段。
     *
     * @param texts 文本列表
     * @param expectedSnippet 期望片段
     * @return 是否命中
     */
    private static boolean containsText(List<String> texts, String expectedSnippet) {
        for (String text : texts) {
            if (text != null && text.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 页面控制器测试夹具。
     */
    private static final class TestFixture {

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final UiAnimationCapabilityShowcaseDocumentPageController controller =
                new UiAnimationCapabilityShowcaseDocumentPageController(documentUi, pageSurface);
    }

    /**
     * 供测试使用的确定性文本测量服务。
     */
    private static final class DeterministicTextMeasureService implements TextMeasureService {

        /**
         * 返回固定字形纪元。
         */
        @Override
        public int getEpoch() {
            return 1;
        }

        /**
         * 按字符数返回固定宽度。
         */
        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        /**
         * 返回固定行高。
         */
        @Override
        public int getLineHeight() {
            return 9;
        }

        /**
         * 测试中不裁剪文本。
         */
        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            return text == null ? "" : text;
        }

        /**
         * 测试中按单行返回文本。
         */
        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
