package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.container.experimental.model.EntryKey;
import club.heiqi.uilib.ui.container.experimental.model.ItemDescriptor;
import club.heiqi.uilib.ui.container.experimental.model.LongContainerSnapshot;
import club.heiqi.uilib.ui.container.experimental.model.LongEntrySnapshot;
import club.heiqi.uilib.ui.container.experimental.operation.LongContainerIntent;
import club.heiqi.uilib.ui.container.experimental.presentation.ItemPresentation;
import club.heiqi.uilib.ui.container.experimental.scene.SceneLongEntryGrid;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/** long Entry Grid 的 L3 semantic input 与 pending gate 集成测试。 */
public class LongEntryGridInteractionTest {

    private static final EntryKey KEY = new EntryKey("test", "entry");

    private SceneInteractionHarness harness;
    private SceneRuntime runtime;
    private SceneNode sceneRoot;
    private SceneNode card;
    private Signal<Boolean> pending;
    private Signal<Boolean> carriedEmpty;
    private List<LongContainerIntent> intents;
    private AtomicInteger bubbledClicks;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        sceneRoot = new SceneNode();
        pending = Signal.create(Boolean.FALSE);
        carriedEmpty = Signal.create(Boolean.TRUE);
        intents = new ArrayList<LongContainerIntent>();
        bubbledClicks = new AtomicInteger();
        Signal<LongContainerSnapshot> snapshot = Signal.create(new LongContainerSnapshot(
                Collections.singletonList(new LongEntrySnapshot(KEY,
                        new ItemDescriptor("iron", "test", new byte[0]), 128))));
        SceneLongEntryGrid.Props props = SceneLongEntryGrid.Props.builder()
                .snapshot(snapshot)
                .pending(pending)
                .carriedEmpty(carriedEmpty)
                .onIntent(intents::add)
                .presentation(item -> new ItemPresentation<SceneImageSource>(
                        null, item.typeId(), Collections.singletonList("tooltip")))
                .columns(1)
                .build();
        MountHandle handle = runtime.mount(sceneRoot, SceneLongEntryGrid.create(runtime, props));
        runtime.on(sceneRoot, SceneEventType.CLICK, (event, context) -> bubbledClicks.incrementAndGet());
        runtime.flush();
        card = handle.getRoot().__getChildren().get(0);
        harness.mountRoot(sceneRoot, 160, 100);
    }

    @After
    public void tearDown() {
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 左右键、carried、Shift 与 pending 必须只映射 semantic intent，且 Entry activation 不穿透。 */
    @Test
    public void pointerActivation_mapsSemanticIntentAndPendingConsumesNoOp() {
        harness.click(card);
        Assert.assertEquals(LongContainerIntent.takeStack(KEY), intents.get(0));

        click(card, SceneMouseButton.RIGHT, false);
        Assert.assertEquals(LongContainerIntent.takeHalfStack(KEY), intents.get(1));

        carriedEmpty.set(Boolean.FALSE);
        runtime.flush();
        harness.click(card);
        click(card, SceneMouseButton.RIGHT, false);
        Assert.assertEquals(LongContainerIntent.depositAll(), intents.get(2));
        Assert.assertEquals(LongContainerIntent.depositOne(), intents.get(3));

        click(card, SceneMouseButton.LEFT, true);
        Assert.assertEquals(LongContainerIntent.quickExtract(KEY), intents.get(4));

        pending.set(Boolean.TRUE);
        runtime.flush();
        int background = card.getBackgroundColor();
        harness.click(card);
        click(card, SceneMouseButton.MIDDLE, false);
        Assert.assertEquals("pending 与 middle 均应 consume/no-op", 5, intents.size());
        Assert.assertEquals("所有 Entry activation 都应停止向父节点冒泡", 0, bubbledClicks.get());

        harness.moveTo(card);
        Assert.assertNotEquals("pending 不应冻结 hover 投影", background, card.getBackgroundColor());
        harness.scroll(card, 1);
        Assert.assertEquals("scroll 不应产生 container intent", 5, intents.size());
    }

    private void click(SceneNode node, SceneMouseButton button, boolean shiftDown) {
        // 白盒回退（自定义 native/修饰键）：harness 固定 LEFT/无修饰键，无法覆盖 RIGHT/MIDDLE/Shift。
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int x = box.getX() + box.getWidth() / 2;
        int y = box.getY() + box.getHeight() / 2;
        route(ScenePointerAction.BUTTON_DOWN, x, y, button, shiftDown);
        route(ScenePointerAction.BUTTON_UP, x, y, button, shiftDown);
        runtime.flush();
    }

    private void route(ScenePointerAction action, int x, int y,
                       SceneMouseButton button, boolean shiftDown) {
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(action, x, y, button,
                0, 0, 0, false, shiftDown, false, false, 1000L));
        runtime.route(sceneRoot, builder.drainFrame(), 0, 0);
    }
}
