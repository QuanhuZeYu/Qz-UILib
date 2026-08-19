package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.TextStyle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLinkRegion;

/**
 * SceneLabel 链接点击交互测试：onLinkClick 回调经 CLICK 命中 LINK_REGION 触发。
 */
public class SceneLabelLinkClickTest {

    @Test
    public void shouldInvokeLinkClickOnRegionHit() {
        LinkMeasurer measurer = new LinkMeasurer();
        SceneInteractionHarness harness = SceneInteractionHarness.create(measurer);
        SceneRuntime rt = harness.getRuntime();

        SceneNode sceneRoot = SceneNode.column();
        AtomicReference<String> clicked = new AtomicReference<String>();
        SceneLabel.Props props = new SceneLabel.Props(
                Signal.create("看<a=https://a.b>链接</a>尾"),
                0xFFFFFFFF, 16, TextStyle.TEXT_MODE_RICH_TAGS,
                TextHorizontalAlign.LEFT, TextVerticalAlign.TOP,
                0, 0.0D, 0, 0, false, clicked::set);
        rt.mount(sceneRoot, SceneLabel.create(rt, props));
        rt.flush();
        harness.mountRoot(sceneRoot, 400, 200);
        new ScenePaintEngine(measurer).paint(sceneRoot);

        // fake region：(8,0)-(24,16) 相对 label 局部；label 位于根 (0,0)，行高 16
        harness.clickAt(16, 8);

        Assert.assertEquals("https://a.b", clicked.get());
    }

    @Test
    public void shouldSetActiveLinkUrlOnHover() {
        LinkMeasurer measurer = new LinkMeasurer();
        SceneInteractionHarness harness = SceneInteractionHarness.create(measurer);
        SceneRuntime rt = harness.getRuntime();

        SceneNode sceneRoot = SceneNode.column();
        AtomicReference<String> clicked = new AtomicReference<String>();
        SceneLabel.Props props = new SceneLabel.Props(
                Signal.create("看<a=https://a.b>链接</a>尾"),
                0xFFFFFFFF, 16, TextStyle.TEXT_MODE_RICH_TAGS,
                TextHorizontalAlign.LEFT, TextVerticalAlign.TOP,
                0, 0.0D, 0, 0, false, clicked::set);
        SceneNode labelRoot = rt.mount(sceneRoot, SceneLabel.create(rt, props)).getRoot();
        rt.flush();
        harness.mountRoot(sceneRoot, 400, 200);
        new ScenePaintEngine(measurer).paint(sceneRoot);

        // 悬停命中链接区域 → activeLinkUrl 写入（绘制层据此画高亮背景）
        harness.moveAt(16, 8);
        Assert.assertEquals("https://a.b", labelRoot.getActiveLinkUrl());

        // 移出链接区域（仍在节点内）→ 清空
        harness.moveAt(60, 8);
        Assert.assertNull(labelRoot.getActiveLinkUrl());
    }

    @Test
    public void shouldNotInvokeOnMiss() {
        LinkMeasurer measurer = new LinkMeasurer();
        SceneInteractionHarness harness = SceneInteractionHarness.create(measurer);
        SceneRuntime rt = harness.getRuntime();

        SceneNode sceneRoot = SceneNode.column();
        AtomicReference<String> clicked = new AtomicReference<String>();
        SceneLabel.Props props = new SceneLabel.Props(
                Signal.create("看<a=https://a.b>链接</a>尾"),
                0xFFFFFFFF, 16, TextStyle.TEXT_MODE_RICH_TAGS,
                TextHorizontalAlign.LEFT, TextVerticalAlign.TOP,
                0, 0.0D, 0, 0, false, clicked::set);
        rt.mount(sceneRoot, SceneLabel.create(rt, props));
        rt.flush();
        harness.mountRoot(sceneRoot, 400, 200);
        new ScenePaintEngine(measurer).paint(sceneRoot);

        // 点在 label 内但链接区域外（region 8..24 之外）
        harness.clickAt(60, 8);

        Assert.assertNull(clicked.get());
    }

    /** 组合 FixedTextMeasurer 并预置固定链接区域的测量替身。 */
    private static final class LinkMeasurer implements SceneTextMeasurer {

        private final FixedTextMeasurer delegate = new FixedTextMeasurer();

        @Override
        public int measureWidth(String text, int fontSizePx) {
            return delegate.measureWidth(text, fontSizePx);
        }

        @Override
        public int lineHeight(int fontSizePx) {
            return delegate.lineHeight(fontSizePx);
        }

        @Override
        public int ascent(int fontSizePx) {
            return delegate.ascent(fontSizePx);
        }

        @Override
        public int descent(int fontSizePx) {
            return delegate.descent(fontSizePx);
        }

        @Override
        public int lineGap(int fontSizePx) {
            return delegate.lineGap(fontSizePx);
        }

        @Override
        public int epoch() {
            return delegate.epoch();
        }

        @Override
        public List<String> splitLines(String text, int fontSizePx, int wrapWidth, int textMode) {
            if (wrapWidth > 0) {
                return java.util.Collections.singletonList(text == null ? "" : text);
            }
            return java.util.Arrays.asList((text == null ? "" : text).split("\n"));
        }

        @Override
        public List<TextLinkRegion> linkRegions(String line, int fontSizePx, int textMode) {
            return Arrays.asList(new TextLinkRegion(8, 16, "https://a.b"));
        }
    }
}