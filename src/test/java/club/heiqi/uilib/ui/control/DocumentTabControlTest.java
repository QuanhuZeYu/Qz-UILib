package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;

/**
 * `DocumentTabControl` 的基础行为契约测试。
 */
public class DocumentTabControlTest {

    /**
     * 验证标签内容懒加载并复用缓存。
     */
    @Test
    public void shouldBuildTabContentLazilyAndReuseCache() {
        UiDocument document = UiDocument.create();
        final int[] buildCount = new int[] { 0 };
        DocumentTabControl tabs = new DocumentTabControl(document)
                .addTab("常规", builder("常规内容", buildCount))
                .addTab("高级", builder("高级内容", buildCount));

        Assert.assertEquals(2, tabs.getTabCount());
        Assert.assertEquals(-1, tabs.getActiveIndex());
        Assert.assertEquals(0, buildCount[0]);

        tabs.setActiveIndex(0);
        Assert.assertEquals(1, buildCount[0]);
        tabs.setActiveIndex(1);
        Assert.assertEquals(2, buildCount[0]);
        tabs.setActiveIndex(0);

        Assert.assertEquals(2, buildCount[0]);
        Assert.assertEquals(0, tabs.getActiveIndex());
    }

    /**
     * 验证点击标签会切换活动页并触发事件。
     */
    @Test
    public void shouldActivateTabOnClick() {
        UiDocument document = UiDocument.create();
        final List<DocumentTabChangeEvent> events = new ArrayList<DocumentTabChangeEvent>();
        DocumentTabControl tabs = new DocumentTabControl(document)
                .addTab("常规", builder("常规内容", new int[] { 0 }))
                .addTab("高级", builder("高级内容", new int[] { 0 }))
                .setActiveIndex(0)
                .setChangeHandler(new DocumentTabChangeHandler() {
                    @Override
                    public void onTabChanged(DocumentTabChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode secondTab = tabAt(tabs, 1);

        Assert.assertTrue(secondTab.getClickHandler().onClick(new DocumentElementClickEvent(secondTab, secondTab,
                0, 0, 0, 1L)));

        Assert.assertEquals(1, tabs.getActiveIndex());
        Assert.assertEquals(1, events.size());
        Assert.assertEquals("高级", events.get(0).getActiveLabel());
        Assert.assertFalse(events.get(0).isKeyboardTriggered());
    }

    /**
     * 验证左右方向键切换标签并请求移动焦点。
     */
    @Test
    public void shouldActivateTabWithKeyboard() {
        UiDocument document = UiDocument.create();
        final List<DocumentTabChangeEvent> events = new ArrayList<DocumentTabChangeEvent>();
        DocumentTabControl tabs = new DocumentTabControl(document)
                .addTab("常规", builder("常规内容", new int[] { 0 }))
                .addTab("高级", builder("高级内容", new int[] { 0 }))
                .setActiveIndex(0)
                .setChangeHandler(new DocumentTabChangeHandler() {
                    @Override
                    public void onTabChanged(DocumentTabChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode tabBar = (ElementNode) tabs.getElement().getChildren().get(0);
        DocumentElementKeyEvent keyEvent = keyEvent(tabAt(tabs, 0), tabBar, Keyboard.KEY_RIGHT, 1L);

        Assert.assertTrue(tabBar.getKeyHandler().onKey(keyEvent));

        Assert.assertEquals(1, tabs.getActiveIndex());
        Assert.assertSame(tabAt(tabs, 1), keyEvent.getPendingFocusTarget());
        Assert.assertEquals(1, events.size());
        Assert.assertTrue(events.get(0).isKeyboardTriggered());
    }

    /**
     * 验证 tablist 只让当前 roving tab 进入顺序焦点遍历。
     */
    @Test
    public void shouldExposeRovingTabIndex() {
        UiDocument document = UiDocument.create();
        DocumentTabControl tabs = new DocumentTabControl(document)
                .addTab("常规", builder("常规内容", new int[] { 0 }))
                .addTab("高级", builder("高级内容", new int[] { 0 }))
                .addTab("审查", builder("审查内容", new int[] { 0 }))
                .setActiveIndex(0);

        Assert.assertEquals("0", tabAt(tabs, 0).getAttribute("tabindex"));
        Assert.assertEquals("-1", tabAt(tabs, 1).getAttribute("tabindex"));
        Assert.assertEquals("-1", tabAt(tabs, 2).getAttribute("tabindex"));

        tabs.setActiveIndex(2);

        Assert.assertEquals("-1", tabAt(tabs, 0).getAttribute("tabindex"));
        Assert.assertEquals("-1", tabAt(tabs, 1).getAttribute("tabindex"));
        Assert.assertEquals("0", tabAt(tabs, 2).getAttribute("tabindex"));
    }

    /**
     * 验证 rebuildTab 会清除缓存并重建活动页内容。
     */
    @Test
    public void shouldRebuildActiveTabContent() {
        UiDocument document = UiDocument.create();
        final int[] buildCount = new int[] { 0 };
        DocumentTabControl tabs = new DocumentTabControl(document)
                .addTab("常规", builder("常规内容", buildCount))
                .setActiveIndex(0);

        tabs.rebuildTab(0);

        Assert.assertEquals(2, buildCount[0]);
    }

    /**
     * 验证清空标签页会重置活动索引。
     */
    @Test
    public void shouldClearTabsAndResetActiveIndex() {
        UiDocument document = UiDocument.create();
        DocumentTabControl tabs = new DocumentTabControl(document)
                .addTab("常规", builder("常规内容", new int[] { 0 }))
                .setActiveIndex(0);

        tabs.clearTabs();

        Assert.assertEquals(0, tabs.getTabCount());
        Assert.assertEquals(-1, tabs.getActiveIndex());
    }

    /**
     * 验证禁用时点击标签不会切换活动页或派发事件。
     */
    @Test
    public void shouldIgnoreClickWhenDisabled() {
        UiDocument document = UiDocument.create();
        final List<DocumentTabChangeEvent> events = new ArrayList<DocumentTabChangeEvent>();
        DocumentTabControl tabs = new DocumentTabControl(document)
                .addTab("常规", builder("常规内容", new int[] { 0 }))
                .addTab("高级", builder("高级内容", new int[] { 0 }))
                .setActiveIndex(0)
                .setEnabled(false)
                .setChangeHandler(new DocumentTabChangeHandler() {
                    @Override
                    public void onTabChanged(DocumentTabChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode secondTab = tabAt(tabs, 1);

        Assert.assertFalse(secondTab.getClickHandler().onClick(new DocumentElementClickEvent(secondTab, secondTab,
                0, 0, 0, 1L)));

        Assert.assertEquals(0, tabs.getActiveIndex());
        Assert.assertTrue(events.isEmpty());
    }

    /**
     * 验证禁用时键盘方向键不会切换标签或派发事件。
     */
    @Test
    public void shouldIgnoreKeyboardWhenDisabled() {
        UiDocument document = UiDocument.create();
        final List<DocumentTabChangeEvent> events = new ArrayList<DocumentTabChangeEvent>();
        DocumentTabControl tabs = new DocumentTabControl(document)
                .addTab("常规", builder("常规内容", new int[] { 0 }))
                .addTab("高级", builder("高级内容", new int[] { 0 }))
                .setActiveIndex(0)
                .setEnabled(false)
                .setChangeHandler(new DocumentTabChangeHandler() {
                    @Override
                    public void onTabChanged(DocumentTabChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode tabBar = (ElementNode) tabs.getElement().getChildren().get(0);

        Assert.assertFalse(tabBar.getKeyHandler().onKey(keyEvent(tabAt(tabs, 0), tabBar, Keyboard.KEY_RIGHT, 1L)));

        Assert.assertEquals(0, tabs.getActiveIndex());
        Assert.assertTrue(events.isEmpty());
    }

    /**
     * 验证 tab 与 panel 通过 aria-controls / aria-labelledby 互相关联。
     */
    @Test
    public void shouldExposeAriaControlsAndLabelledBy() {
        UiDocument document = UiDocument.create();
        DocumentTabControl tabs = new DocumentTabControl(document)
                .addTab("常规", builder("常规内容", new int[] { 0 }))
                .addTab("高级", builder("高级内容", new int[] { 0 }))
                .setActiveIndex(0);

        ElementNode firstTab = tabAt(tabs, 0);
        ElementNode panel = (ElementNode) tabs.getElement().getChildren().get(1);

        Assert.assertNotNull(firstTab.getId());
        Assert.assertNotNull(panel.getId());
        Assert.assertEquals(panel.getId(), firstTab.getAttribute("aria-controls"));
        Assert.assertEquals(firstTab.getId(), panel.getAttribute("aria-labelledby"));

        tabs.setActiveIndex(1);
        Assert.assertEquals(tabAt(tabs, 1).getId(), panel.getAttribute("aria-labelledby"));
    }

    private static DocumentTabContentBuilder builder(final String text, final int[] buildCount) {
        return new DocumentTabContentBuilder() {
            @Override
            public void build(ElementNode panel, UiDocument document) {
                buildCount[0]++;
                panel.appendText(text + " #" + buildCount[0]);
            }
        };
    }

    private static ElementNode tabAt(DocumentTabControl tabs, int index) {
        ElementNode tabBar = (ElementNode) tabs.getElement().getChildren().get(0);
        return (ElementNode) tabBar.getChildren().get(index);
    }

    private static DocumentElementKeyEvent keyEvent(ElementNode target, ElementNode currentTarget, int keyCode,
            long timeNanos) {
        return new DocumentElementKeyEvent(target, currentTarget, new UiKeyEvent(keyCode, 0, 0,
                UiKeyEvent.Action.PRESSED, false, false, false, false, timeNanos));
    }
}
