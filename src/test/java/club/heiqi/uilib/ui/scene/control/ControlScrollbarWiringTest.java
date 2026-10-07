package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.testkit.SceneTestEnvironments;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;


public class ControlScrollbarWiringTest {

    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = SceneTestEnvironments.runtime(new FixedTextMeasurer(8, 16));
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /**
     * 在 root 直接子中定位 stackHost：FlexDirection==ROW 且存在 isScrollable 的直接子。
     *
     * <p>该特征对 4 控件统一成立——stackHost(row) 直接含 scrollable viewport；
     * KeyValueMap 的 header(row) 直接子是 cell（非 scrollable），不会被误命中。</p>
     *
     * @param root 控件 create 返回的根节点
     * @return stackHost 节点
     */
    private static SceneNode findStackHost(SceneNode root) {
        for (SceneNode child : root.__getChildren()) {
            if (child.getFlexDirection() == FlexDirection.ROW) {
                for (SceneNode grandchild : child.__getChildren()) {
                    if (grandchild.isScrollable()) {
                        return child;
                    }
                }
            }
        }
        return null;
    }

    // ==================== SimpleList ====================

    @Test
    public void simpleList_stackHostHasTwoChildren_whenShowScrollbarTrue() {
        SceneSimpleList.Props props = SceneSimpleList.Props
                .builder(Signal.create(new ArrayList<SceneSimpleList.ListItem>()))
                .showScrollbar(true)
                .build();
        SceneNode root = SceneSimpleList.create(runtime, props).get();
        SceneNode stackHost = findStackHost(root);
        Assert.assertNotNull("SimpleList 应建出 stackHost", stackHost);
        Assert.assertEquals("showScrollbar=true 时 stackHost 应含 [viewport, scrollbar] 共 2 子",
                2, stackHost.__getChildren().size());
    }

    @Test
    public void simpleList_stackHostHasOneChild_whenShowScrollbarFalse() {
        SceneSimpleList.Props props = SceneSimpleList.Props
                .builder(Signal.create(new ArrayList<SceneSimpleList.ListItem>()))
                .build();
        SceneNode root = SceneSimpleList.create(runtime, props).get();
        SceneNode stackHost = findStackHost(root);
        Assert.assertNotNull("SimpleList 应建出 stackHost", stackHost);
        Assert.assertEquals("showScrollbar=false 时 stackHost 应仅含 viewport 共 1 子",
                1, stackHost.__getChildren().size());
    }

    // ==================== ObjectField ====================

    @Test
    public void objectField_stackHostHasTwoChildren_whenShowScrollbarTrue() {
        Map<String, Object> empty = new LinkedHashMap<String, Object>();
        SceneObjectField.Props props = SceneObjectField.Props
                .builder(Signal.create(empty))
                .showScrollbar(true)
                .build();
        SceneNode root = SceneObjectField.create(runtime, props).get();
        SceneNode stackHost = findStackHost(root);
        Assert.assertNotNull("ObjectField 应建出 stackHost", stackHost);
        Assert.assertEquals("showScrollbar=true 时 stackHost 应含 [viewport, scrollbar] 共 2 子",
                2, stackHost.__getChildren().size());
    }

    @Test
    public void objectField_stackHostHasOneChild_whenShowScrollbarFalse() {
        Map<String, Object> empty = new LinkedHashMap<String, Object>();
        SceneObjectField.Props props = SceneObjectField.Props
                .builder(Signal.create(empty))
                .build();
        SceneNode root = SceneObjectField.create(runtime, props).get();
        SceneNode stackHost = findStackHost(root);
        Assert.assertNotNull("ObjectField 应建出 stackHost", stackHost);
        Assert.assertEquals("showScrollbar=false 时 stackHost 应仅含 viewport 共 1 子",
                1, stackHost.__getChildren().size());
    }

    // ==================== KeyValueMap ====================

    @Test
    public void keyValueMap_stackHostHasTwoChildren_whenShowScrollbarTrue() {
        SceneKeyValueMap.Props props = SceneKeyValueMap.Props
                .builder(Signal.create(new ArrayList<KeyValueRow>()))
                .showScrollbar(true)
                .build();
        SceneNode root = SceneKeyValueMap.create(runtime, props).get();
        SceneNode stackHost = findStackHost(root);
        Assert.assertNotNull("KeyValueMap 应建出 stackHost", stackHost);
        Assert.assertEquals("showScrollbar=true 时 stackHost 应含 [viewport, scrollbar] 共 2 子",
                2, stackHost.__getChildren().size());
    }

    @Test
    public void keyValueMap_stackHostHasOneChild_whenShowScrollbarFalse() {
        SceneKeyValueMap.Props props = SceneKeyValueMap.Props
                .builder(Signal.create(new ArrayList<KeyValueRow>()))
                .build();
        SceneNode root = SceneKeyValueMap.create(runtime, props).get();
        SceneNode stackHost = findStackHost(root);
        Assert.assertNotNull("KeyValueMap 应建出 stackHost", stackHost);
        Assert.assertEquals("showScrollbar=false 时 stackHost 应仅含 viewport 共 1 子",
                1, stackHost.__getChildren().size());
    }

    // ==================== DataTable ====================

    @Test
    public void dataTable_stackHostHasTwoChildren_whenShowScrollbarTrue() {
        SceneDataTable.Props props = SceneDataTable.Props
                .builder(Signal.create(new ArrayList<SceneDataTable.Row>()))
                .columns(Collections.singletonList(SceneDataTable.Column.text("h", 100)))
                .showScrollbar(true)
                .build();
        SceneNode root = SceneDataTable.create(runtime, props).get();
        SceneNode stackHost = findStackHost(root);
        Assert.assertNotNull("DataTable 应建出 stackHost", stackHost);
        Assert.assertEquals("showScrollbar=true 时 stackHost 应含 [viewport, scrollbar] 共 2 子",
                2, stackHost.__getChildren().size());
    }

    @Test
    public void dataTable_stackHostHasOneChild_whenShowScrollbarFalse() {
        SceneDataTable.Props props = SceneDataTable.Props
                .builder(Signal.create(new ArrayList<SceneDataTable.Row>()))
                .columns(Collections.singletonList(SceneDataTable.Column.text("h", 100)))
                .build();
        SceneNode root = SceneDataTable.create(runtime, props).get();
        SceneNode stackHost = findStackHost(root);
        Assert.assertNotNull("DataTable 应建出 stackHost", stackHost);
        Assert.assertEquals("showScrollbar=false 时 stackHost 应仅含 viewport 共 1 子",
                1, stackHost.__getChildren().size());
    }
}
