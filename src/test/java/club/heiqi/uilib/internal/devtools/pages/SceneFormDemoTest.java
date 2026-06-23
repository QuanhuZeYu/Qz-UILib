package club.heiqi.uilib.internal.devtools.pages;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneFormHostWidget 组装测试。
 *
 * <p>覆盖配置表单 demo 的 P1-a 外壳、字段校验、canSave 派生与 draft/current
 * 双副本保存恢复行为，不连接真实配置模块。</p>
 */
public class SceneFormDemoTest {

    private static final int CANVAS_WIDTH = 520;
    private static final int CANVAS_HEIGHT = 420;

    private SceneFormHostWidget host;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private SceneNode root;
    private SceneNode viewport;
    private SceneNode content;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new SceneFormHostWidget(null);
        runtime = host.__getRuntime();
        layoutEngine = host.__getLayoutEngine();
        root = host.__getRoot();
        viewport = host.__getViewport();
        content = host.__getContent();
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 跑一次布局，模拟 drawSelf 中的 layout 阶段。 */
    private void doLayout() {
        layoutEngine.layout(root, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** flush 响应式队列，确保测试读取到最新派生值。 */
    private void flush() {
        runtime.flush();
    }

    /** 视口应是 root 中唯一 fill 子，并吃掉固定标题、状态和操作条后的剩余高度。 */
    @Test
    public void viewportShouldBeOnlyFillChildAndContainThreeFieldCards() {
        doLayout();

        int fillChildCount = 0;
        for (SceneNode child : root.__getChildren()) {
            if (child.isFillParentHeight()) {
                fillChildCount++;
                Assert.assertSame("唯一 fillParentHeight 子节点应为 viewport", viewport, child);
            }
        }
        Assert.assertEquals("root 应只有一个 fillParentHeight 子节点", 1, fillChildCount);
        Assert.assertEquals("content 应包含三张字段卡片", 3, content.__getChildren().size());

        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        Assert.assertNotNull("viewport 应已布局", viewportBox);
        int fixedHeight = 0;
        int fixedSiblings = 0;
        for (SceneNode child : root.__getChildren()) {
            if (child == viewport) {
                continue;
            }
            fixedHeight += child.getPreferredHeight();
            fixedSiblings++;
        }
        int expectedHeight = CANVAS_HEIGHT - root.getPaddingTop() - root.getPaddingBottom()
                - fixedHeight - root.getGap() * fixedSiblings;
        Assert.assertEquals("viewport 应吃 root 剩余高度", expectedHeight, viewportBox.getHeight());
    }

    /** 字段错误与 canSave 应完全由 draft/current 双副本和校验派生。 */
    @Test
    public void validationShouldDriveCanSave() {
        Assert.assertFalse("默认无脏改动，canSave=false", host.__getCanSave().get().booleanValue());

        host.__getNameDraft().set("");
        flush();
        Assert.assertEquals("名称不能为空", host.__getNameError().get());
        Assert.assertFalse(host.__getCanSave().get().booleanValue());

        host.__getNameDraft().set("Alex_1");
        flush();
        Assert.assertEquals("", host.__getNameError().get());
        Assert.assertTrue(host.__getCanSave().get().booleanValue());

        host.__getDistanceDraft().set("abc");
        flush();
        Assert.assertEquals("请输入整数", host.__getDistanceError().get());
        Assert.assertFalse(host.__getCanSave().get().booleanValue());

        host.__getDistanceDraft().set("4");
        host.__getFancyDraft().set(Boolean.TRUE);
        flush();
        Assert.assertEquals("花哨画质下渲染距离至少 8", host.__getDistanceError().get());
        Assert.assertFalse(host.__getCanSave().get().booleanValue());

        host.__getDistanceDraft().set("8");
        flush();
        Assert.assertEquals("", host.__getDistanceError().get());
        Assert.assertTrue(host.__getCanSave().get().booleanValue());
    }

    /** 保存写回 current，取消回滚 draft，恢复默认只写 draft 不直写 current。 */
    @Test
    public void saveCancelAndRestoreDefaultsShouldKeepDraftCurrentContract() {
        host.__getNameDraft().set("Alex_1");
        host.__getDistanceDraft().set("12");
        host.__getFancyDraft().set(Boolean.TRUE);
        flush();
        Assert.assertTrue(host.__getCanSave().get().booleanValue());

        host.__saveChanges();
        flush();
        Assert.assertEquals("Alex_1", host.__getNameCurrent().get());
        Assert.assertEquals("12", host.__getDistanceCurrent().get());
        Assert.assertTrue(host.__getFancyCurrent().get().booleanValue());
        Assert.assertFalse("保存后 draft/current 一致，canSave=false", host.__getCanSave().get().booleanValue());

        host.__getNameDraft().set("Case2");
        host.__getDistanceDraft().set("16");
        host.__getFancyDraft().set(Boolean.FALSE);
        flush();
        Assert.assertTrue(host.__getCanSave().get().booleanValue());

        host.__cancelChanges();
        flush();
        Assert.assertEquals("Alex_1", host.__getNameDraft().get());
        Assert.assertEquals("12", host.__getDistanceDraft().get());
        Assert.assertTrue(host.__getFancyDraft().get().booleanValue());
        Assert.assertFalse(host.__getCanSave().get().booleanValue());

        host.__restoreDefaults();
        flush();
        Assert.assertEquals("Steve", host.__getNameDraft().get());
        Assert.assertEquals("8", host.__getDistanceDraft().get());
        Assert.assertFalse(host.__getFancyDraft().get().booleanValue());
        Assert.assertEquals("Alex_1", host.__getNameCurrent().get());
        Assert.assertEquals("12", host.__getDistanceCurrent().get());
        Assert.assertTrue(host.__getFancyCurrent().get().booleanValue());
        Assert.assertTrue(host.__getCanSave().get().booleanValue());
    }
}
