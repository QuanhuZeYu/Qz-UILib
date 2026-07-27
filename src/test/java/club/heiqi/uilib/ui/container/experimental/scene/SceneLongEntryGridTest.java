package club.heiqi.uilib.ui.container.experimental.scene;

import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.container.experimental.model.EntryKey;
import club.heiqi.uilib.ui.container.experimental.model.ItemDescriptor;
import club.heiqi.uilib.ui.container.experimental.model.LongContainerSnapshot;
import club.heiqi.uilib.ui.container.experimental.model.LongEntrySnapshot;
import club.heiqi.uilib.ui.container.experimental.presentation.ItemPresentation;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** SceneLongEntryGrid 的 confirmed projection 与 keyed identity 测试。 */
public class SceneLongEntryGridTest {

    private static final EntryKey A = new EntryKey("test", "a");
    private static final EntryKey B = new EntryKey("test", "b");
    private static final EntryKey C = new EntryKey("test", "c");

    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 同 key 更新必须复用卡片 identity，并从 confirmed signal 回查最新展示值。 */
    @Test
    public void snapshotUpdate_reconcilesByKeyAndRefreshesConfirmedPresentation() {
        Signal<LongContainerSnapshot> snapshot = Signal.create(container(
                entry(A, "iron", 12), entry(B, "gold", 34)));
        SceneNode host = new SceneNode();
        MountHandle handle = runtime.mount(host, SceneLongEntryGrid.create(runtime,
                props(snapshot).amountFormatter(value -> "amount=" + value).columns(2).build()));
        runtime.flush();
        SceneNode grid = handle.getRoot();
        SceneNode cardA = grid.__getChildren().get(0);
        SceneNode cardB = grid.__getChildren().get(1);

        snapshot.set(container(entry(B, "copper", Long.MAX_VALUE), entry(C, "tin", 7)));
        runtime.flush();

        Assert.assertEquals("删除 A、新增 C 后仍应保持两项", 2, grid.__getChildren().size());
        Assert.assertSame("同 key B reorder 后必须复用 SceneNode", cardB, grid.__getChildren().get(0));
        Assert.assertNotSame("新 key C 必须创建新卡片", cardA, grid.__getChildren().get(1));
        Assert.assertSame("Entry 必须直接挂在同一 Grid parent", grid,
                grid.__getChildren().get(0).__getParent());
        Assert.assertEquals("同 key descriptor 更新必须刷新名称", "copper", nameOf(cardB).getText());
        Assert.assertEquals("amount formatter 必须接收完整 long", "amount=" + Long.MAX_VALUE,
                amountOf(cardB).getText());
        Assert.assertEquals("presentation icon 必须随 confirmed descriptor 更新", "copper",
                ((TestImage) iconOf(cardB).getImageSource()).id);
    }

    /** Builder 必须拒绝缺失 required port 与非法几何参数。 */
    @Test
    public void propsBuilder_rejectsMissingPortsAndInvalidGeometry() {
        assertFailure(() -> SceneLongEntryGrid.Props.builder().build());
        Signal<LongContainerSnapshot> snapshot = Signal.create(container());
        assertFailure(() -> props(snapshot).columns(0).build());
        assertFailure(() -> props(snapshot).entryHeight(0).build());
        assertFailure(() -> props(snapshot).gap(-1).build());
    }

    private static SceneLongEntryGrid.Props.Builder props(Signal<LongContainerSnapshot> snapshot) {
        return SceneLongEntryGrid.Props.builder()
                .snapshot(snapshot)
                .pending(Signal.create(Boolean.FALSE))
                .carriedEmpty(Signal.create(Boolean.TRUE))
                .onIntent(intent -> { })
                .presentation(item -> new ItemPresentation<SceneImageSource>(
                        new TestImage(item.typeId()), item.typeId(),
                        Collections.singletonList("tooltip:" + item.typeId())));
    }

    private static LongEntrySnapshot entry(EntryKey key, String item, long amount) {
        return new LongEntrySnapshot(key, new ItemDescriptor(item, "test", new byte[0]), amount);
    }

    private static LongContainerSnapshot container(LongEntrySnapshot... entries) {
        return new LongContainerSnapshot(Arrays.asList(entries));
    }

    private static SceneNode iconOf(SceneNode card) {
        return card.__getChildren().get(0).__getChildren().get(0);
    }

    private static SceneNode nameOf(SceneNode card) {
        return card.__getChildren().get(0).__getChildren().get(1);
    }

    private static SceneNode amountOf(SceneNode card) {
        return card.__getChildren().get(1);
    }

    private static void assertFailure(Runnable action) {
        try {
            action.run();
            Assert.fail("应拒绝非法 Props");
        } catch (NullPointerException | IllegalArgumentException expected) {
            // 预期分支
        }
    }

    private static final class TestImage implements SceneImageSource {
        private final String id;

        private TestImage(String id) {
            this.id = id;
        }
    }
}
