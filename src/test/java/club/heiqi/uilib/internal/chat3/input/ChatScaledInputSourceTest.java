package club.heiqi.uilib.internal.chat3.input;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.hud.api.HudToolbarLayer;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.host.lwjgl.MockPlatformStateReader;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/** 输入适配边界测试：物理按钮旁路事件经生产封帧转换后真实命中公共控件。 */
public class ChatScaledInputSourceTest {
    @Test
    public void scaledBottomRightPlacementKeepsLogicalLayoutInsideVisualViewport() {
        for (float scale : new float[] {0.5F, 1.5F, 2F}) {
            for (club.heiqi.uilib.ui.hud.api.HudToolbarSide side
                    : club.heiqi.uilib.ui.hud.api.HudToolbarSide.values()) {
                SceneInteractionHarness harness = SceneInteractionHarness.create();
                try {
                    int width = club.heiqi.uilib.internal.chat3.ChatMarkdownSettings.chatWidthFor(1200);
                    int height = club.heiqi.uilib.internal.chat3.ChatMarkdownSettings.containerHeightFor(900);
                    HudToolbarLayer.Result layer = HudToolbarLayer.mount(harness.getRuntime(),
                            HudToolbarSpec.builder(side).build(),
                            SceneNode.column().setPreferredWidth(width).setPreferredHeight(height),
                            rt -> SceneNode.row().setPreferredWidth(20).setPreferredHeight(28));
                    SceneNode root = SceneNode.column().setFillParentHeight(true);
                    root.appendChild(layer.root());
                    harness.getRuntime().flush();
                    for (int frame = 0; frame < 3; frame++) {
                        ChatInputSurface.applyOuterPlacement(layer, 1200, 900,
                                club.heiqi.uilib.ui.hud.api.HudPlacement.defaultOf(
                                        club.heiqi.uilib.ui.hud.api.HudAnchor.BOTTOM_RIGHT, 8),
                                club.heiqi.uilib.ui.hud.api.HudInsets.NONE, scale);
                        harness.mountRoot(root, (int) (1200 / scale), (int) (900 / scale));
                    }
                    AnchorRect box = SceneGeometry.absoluteBox(layer.root(), 0, 0);
                    Assert.assertEquals(layer.logicalOuterWidth(width), box.getWidth());
                    Assert.assertTrue("left stays on screen", Math.round(box.getX() * scale) >= 0);
                    Assert.assertTrue("right stays on screen", Math.round((box.getX() + box.getWidth()) * scale) <= 1200);
                    // 高度超视口时保留既有裁剪语义，只验证顶部没有负向漂移。
                    Assert.assertTrue("top stays on screen", Math.round(box.getY() * scale) >= 0);
                } finally {
                    harness.dispose();
                    ReactiveScheduler.get().reset();
                }
            }
        }
    }

    @Test
    public void queuedPhysicalClickUsesRenderScaleAndSurvivesSameFrameRoundTrip() {
        for (float scale : new float[] {0.5F, 1.5F, 2F}) {
            SceneInteractionHarness harness = SceneInteractionHarness.create();
            try {
                HudToolbarLayer.Result layer = HudToolbarLayer.mount(harness.getRuntime(),
                        HudToolbarSpec.builder().build(),
                        SceneNode.column().setPreferredWidth(200).setPreferredHeight(100),
                        rt -> SceneNode.row().setPreferredWidth(20).setPreferredHeight(28));
                harness.getRuntime().flush();
                harness.mountRoot(layer.root(), 800, 600);
                SceneNode plus = layer.toolbar().__getChildren().get(3);
                AnchorRect box = SceneGeometry.absoluteBox(plus, 0, 0);
                MockPlatformStateReader reader = new MockPlatformStateReader();
                reader.mouseX = Math.round((box.getX() + box.getWidth() / 2F) * scale);
                reader.mouseY = Math.round((box.getY() + box.getHeight() / 2F) * scale);
                ChatScaledInputSource source = new ChatScaledInputSource(reader);
                source.setExternalPointerMode(true);
                source.drainFrame();
                source.pushPointerButton(ScenePointerAction.BUTTON_DOWN, -1, -1, SceneMouseButton.LEFT, 10L);
                source.pushPointerButton(ScenePointerAction.BUTTON_UP, -1, -1, SceneMouseButton.LEFT, 20L);
                // 事件先排队、再采样本帧倍率：不能在回调时提前换算。
                source.setScale(scale);
                harness.getRuntime().route(layer.root(), source.drainFrame(), 0, 0);
                harness.getRuntime().flush();
                Assert.assertEquals(110, layer.scale().percent().get().intValue());
            } finally {
                harness.dispose();
                ReactiveScheduler.get().reset();
            }
        }
    }
}
