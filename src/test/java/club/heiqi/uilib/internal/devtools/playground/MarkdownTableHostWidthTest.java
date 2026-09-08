package club.heiqi.uilib.internal.devtools.playground;

import org.junit.Assert;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 表格实际宿主宽的关系锁；home ROW clamp 仅观测，不把未裁定 C10 当通过前置。 */
public class MarkdownTableHostWidthTest {
    private int savedWidthMissBudget;
    @Before public void stableMetrics() {
        savedWidthMissBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
    }
    @After public void restoreMetrics() {
        FontConfig.widthCacheMissBudgetPerWindow = savedWidthMissBudget;
    }
    @Test
    public void homeRowsDoNotChangeMarkdownHostWidth() {
        ReactiveScheduler.get().reset();
        TestPlaygroundHost host = new TestPlaygroundHost(null);
        try {
            int markdown = -1;
            for (int i = 0; i < PlaygroundPageRegistry.defaultPages().size(); i++) {
                if ("markdown".equals(PlaygroundPageRegistry.defaultPages().get(i).id())) markdown = i;
            }
            Assert.assertTrue(markdown >= 0);
            for (int width : new int[]{360, 720, 1080, 720}) {
                host.__getActivePageSignal().set(0);
                settle(host, width);
                SceneNode home = host.__getDisplayedPageRoot();
                int homeWidth = box(home).getWidth();
                int overflow = rowOverflow(home);
                host.__getActivePageSignal().set(markdown);
                settle(host, width);
                Assert.assertEquals("markdown", host.__getDisplayedPageId());
                SceneNode page = host.__getDisplayedPageRoot();
                Assert.assertEquals("相同宿主宽不受前页ROW内容反推", homeWidth, box(page).getWidth());
                SceneNode card = page.__getChildren().get(page.__getChildren().size() - 1);
                SceneNode body = card.__getChildren().get(1);
                int available = box(card).getWidth() - card.getPaddingLeft() - card.getPaddingRight();
                Assert.assertEquals("实际卡片内容宽下传表格", available, box(body).getWidth());
                Assert.assertFalse("layoutDone桥接后必须真正装配表格", body.__getChildren().isEmpty());
                int stable = box(body).getWidth();
                settle(host, width);
                Assert.assertEquals("多次布局不因表格固有宽回流而摆动", stable, box(body).getWidth());
                System.out.println("TABLE_HOST canvas=" + width + " page=" + homeWidth
                        + " tableAvailable=" + available + " homeRowOverflow=" + overflow);
            }
        } finally {
            host.dispose();
            ReactiveScheduler.get().reset();
        }
    }

    private static void settle(TestPlaygroundHost host, int width) {
        for (int i = 0; i < 4; i++) {
            host.__getRuntime().flush();
            host.getLayoutEngine().layout(host.__getRoot(), new Constraints(width, 520));
            host.__getRuntime().__setLayoutDoneEpoch(host.getLayoutEngine().layoutEpoch());
        }
        host.__getRuntime().flush();
    }
    private static LayoutBox box(SceneNode node) { return (LayoutBox) node.getCachedLayout(); }
    private static int rowOverflow(SceneNode node) {
        int count = 0;
        if (node.getFlexDirection() == FlexDirection.ROW && !node.isScrollableX()) {
            for (SceneNode child : node.__getChildren()) {
                if (box(child).getX() + box(child).getWidth() > box(node).getWidth()) count++;
            }
        }
        for (SceneNode child : node.__getChildren()) count += rowOverflow(child);
        return count;
    }
}
