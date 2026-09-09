package club.heiqi.uilib.ui.hud.api;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneGlassButtonStyle;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

import static org.junit.Assert.*;

/** 公共按钮换肤不重建工具栏、不改写业务子树或缩放状态。 */
public class HudToolbarAppearanceTest {
    @Before public void before() { ReactiveScheduler.get().reset(); }
    @After public void after() { ReactiveScheduler.get().reset(); }

    @Test public void defaultRecipePreservesExistingPublicButtonGlass() {
        SceneGlassButtonStyle style = HudToolbarSpec.builder().build().getPublicButtonStyle().get();
        assertEquals(UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN, 6, 1.0f), style.getBackdrop());
        assertEquals(8, style.getCornerRadius());
    }

    @Test public void liveRecipeUpdatesExistingButtonsAndPreservesContentProperties() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneGlassButtonStyle glass = SceneGlassButtonStyle.builder()
                .backdrop(UiBackdrop.liquidGlass(UiGlassMaterial.THIN, 3, 0.8f)).build();
        Signal<SceneGlassButtonStyle> style = Signal.create(glass);
        SceneNode custom = SceneNode.row().setPreferredWidth(30).setPreferredHeight(20)
                .setBackgroundColor(0xFF123456);
        try {
            HudToolbarLayer.Result layer = HudToolbarLayer.mount(rt,
                    HudToolbarSpec.builder().publicButtonStyle(style).build(),
                    SceneNode.column().setPreferredWidth(200).setPreferredHeight(100), runtime -> custom);
            rt.flush();
            SceneNode plus = layer.toolbar().__getChildren().get(3);
            SceneNode motionRoot = plus.__getChildren().get(0);
            SceneNode label = motionRoot.__getChildren().get(0);
            int originalTextColor = label.getTextColor();
            Transform labelTransform = Transform.translate(1.0f, 0.0f);
            label.setTransform(labelTransform).setOpacity(0.7f);
            layer.scale().setPercent(150);
            style.set(glass.toBuilder().backdrop(null).cornerRadius(4).foreground(0xFF102030).build());
            rt.flush();
            for (SceneNode button : layer.toolbar().__getChildren().subList(1, 4)) {
                assertNull("显式关闭滤镜，保留公共工具", button.getBackdrop());
                assertEquals(4, button.getCornerRadius());
                assertEquals(0xFF102030, button.__getChildren().get(0).__getChildren().get(0).getTextColor());
            }
            assertSame(plus, layer.toolbar().__getChildren().get(3));
            assertEquals(150, layer.scale().percent().get().intValue());
            assertSame(custom, layer.toolbar().__getChildren().get(0));
            assertEquals(0xFF123456, custom.getBackgroundColor());
            assertSame(labelTransform, label.getTransform());
            assertEquals(0.7f, label.getOpacity(), 0.0f);
            assertNotNull(motionRoot.getTransform());

            style.set(glass);
            rt.flush();
            assertNotNull(plus.getBackdrop());
            assertSame(UiGlassMaterial.THIN, plus.getBackdrop().getEffect().getMaterial());
            assertEquals(originalTextColor, label.getTextColor());
            rt.dispose();
            style.set(glass.toBuilder().backdrop(null).build());
            ReactiveScheduler.get().flush();
            assertNotNull("runtime释放后配方不再写入旧节点", plus.getBackdrop());
        } finally { rt.dispose(); }
    }
}
