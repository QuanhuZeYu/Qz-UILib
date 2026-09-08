package club.heiqi.uilib.ui.hud.api;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;

/**
 * HUD 用户布局服务契约（规划《聊天工具栏与HUD布局编辑》P2 最小闭环）：
 * 默认放置与历史四角锚定数学同值、草稿提交/取消隔离、拖动偏移收敛、锚点方向换算。
 */
public class HudLayoutServiceTest {

    private static final String ID = "qzuilib:test";

    @Before
    public void setUp() {
        HudLayoutService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        HudLayoutService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }

    @Test
    public void defaultPlacementMatchesLegacyBottomLeftAnchor() {
        AnchorRect rect = HudLayoutResolver.resolve(
                HudPlacement.defaultOf(HudAnchor.BOTTOM_LEFT, 8), 400, 200, 100, 50, HudInsets.NONE);
        // 历史 SceneAnchorResolver.resolveViewport(false,true,...,margin=8)：
        // x = safeLeft + margin = 8；y = height - safeBottom - margin - contentHeight = 142
        Assert.assertEquals(8, rect.getX());
        Assert.assertEquals(142, rect.getY());
        Assert.assertEquals(100, rect.getWidth());
        Assert.assertEquals(50, rect.getHeight());
    }

    @Test
    public void draftIsInvisibleUntilCommit() {
        HudLayoutService service = HudLayoutService.getInstance();
        service.beginEdit();
        service.setDraft(ID, HudPlacement.of(HudAnchor.BOTTOM_LEFT, 40, 30));
        Assert.assertTrue(service.isEditing());
        // 编辑中生效值 = 草稿
        Assert.assertEquals(HudPlacement.of(HudAnchor.BOTTOM_LEFT, 40, 30), service.placement(ID));
        service.commitEdit();
        Assert.assertFalse(service.isEditing());
        Assert.assertEquals(HudPlacement.of(HudAnchor.BOTTOM_LEFT, 40, 30), service.placement(ID));
        Assert.assertTrue(service.hasCommittedOverride(ID));
    }

    @Test
    public void cancelDiscardsWholeSessionIncludingReset() {
        HudLayoutService service = HudLayoutService.getInstance();
        service.commit(ID, HudPlacement.of(HudAnchor.BOTTOM_LEFT, 12, 12));
        service.beginEdit();
        service.setDraft(ID, HudPlacement.of(HudAnchor.BOTTOM_LEFT, 99, 99));
        service.resetDraft(ID);
        Assert.assertNull(service.placement(ID));
        service.cancelEdit();
        // 取消后回到已提交布局，未提交的 reset 不生效
        Assert.assertEquals(HudPlacement.of(HudAnchor.BOTTOM_LEFT, 12, 12), service.placement(ID));
    }

    @Test
    public void committedResetRemovesOverride() {
        HudLayoutService service = HudLayoutService.getInstance();
        service.commit(ID, HudPlacement.of(HudAnchor.BOTTOM_LEFT, 12, 12));
        service.beginEdit();
        service.resetDraft(ID);
        service.commitEdit();
        Assert.assertNull(service.placement(ID));
        Assert.assertFalse(service.hasCommittedOverride(ID));
    }

    @Test
    public void clampKeepsWholeWindowInsideViewport() {
        HudPlacement clamped = HudLayoutResolver.clamp(
                HudPlacement.of(HudAnchor.BOTTOM_LEFT, 500, -20), 400, 200, 100, 50, HudInsets.NONE);
        Assert.assertEquals(300, clamped.getOffsetX());
        Assert.assertEquals(0, clamped.getOffsetY());
        AnchorRect rect = HudLayoutResolver.resolve(clamped, 400, 200, 100, 50, HudInsets.NONE);
        Assert.assertEquals(300, rect.getX());
        Assert.assertEquals(150, rect.getY());
        Assert.assertEquals(400, rect.getX() + rect.getWidth());
        Assert.assertEquals(200, rect.getY() + rect.getHeight());
    }

    @Test
    public void translateMeasuresFromAnchoredCorner() {
        // 左下锚点：右移增大左偏移；上移增大下偏移
        Assert.assertEquals(HudPlacement.of(HudAnchor.BOTTOM_LEFT, 18, 30),
                HudPlacement.of(HudAnchor.BOTTOM_LEFT, 8, 20).translate(10, -10));
        // 右下锚点：右移减小右偏移
        Assert.assertEquals(HudPlacement.of(HudAnchor.BOTTOM_RIGHT, 8, 30),
                HudPlacement.of(HudAnchor.BOTTOM_RIGHT, 18, 20).translate(10, -10));
    }

    @Test
    public void safeInsetsShiftAvailableArea() {
        AnchorRect rect = HudLayoutResolver.resolve(
                HudPlacement.defaultOf(HudAnchor.BOTTOM_LEFT, 8), 400, 200, 100, 50,
                new HudInsets(20, 10, 20, 10));
        Assert.assertEquals(28, rect.getX());
        Assert.assertEquals(132, rect.getY());
    }
}
