package club.heiqi.uilib.internal.chat3.input;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.api.chat.ChatAction;
import club.heiqi.uilib.api.chat.ChatActionRegistration;
import club.heiqi.uilib.api.chat.ChatActionService;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.view.ChatHudWindow;
import club.heiqi.uilib.ui.hud.api.HudToolbarLayer;
import club.heiqi.uilib.ui.hud.api.HudToolbarSide;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdropEffect;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天工具栏契约：图标动作渲染/隐藏、编辑态切换到完成/取消/重置、
 * 内置动作经注册链到达当前屏；真实命中和 tooltip 行为见 ChatToolbarGeometryTest。
 */
public class ChatToolbarTest {

    /** 本用例绑定的工具栏宿主（@After 解绑，避免静态可见性信号污染同 JVM 其它测试）。 */
    private ChatToolbar.Host attachedHost;
    private SceneRuntime rt;

    /** 两个工具栏测试在挂载前预热全部图标，request 只命中 LOADED 条目，不启动下载。 */
    static void primeIconCache() {
        DocumentRemoteImageCache cache = DocumentRemoteImageCache.getInstance();
        cache.clearForTesting();
        for (String name : new String[] {"action", "edit", "finish", "cancel", "reset-current", "reset-all"}) {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(8, 8, 0xFFFFFFFF);
            cache.putForTesting(ChatToolbarIcons.urlFor(name), image);
        }
    }

    static SceneNode assertIconButton(SceneNode button, String name) {
        Assert.assertEquals("按钮只能有一个图标直接子节点", 1, button.__getChildren().size());
        Assert.assertTrue("按钮不保留文字标签", texts(button).isEmpty());
        Assert.assertEquals(24, button.getPreferredWidth());
        Assert.assertEquals(24, button.getPreferredHeight());
        Assert.assertEquals("每颗按钮保持固定实体轮廓", 8, button.getCornerRadius());
        Assert.assertNull("按钮根不设置变换，表面升降不影响命中", button.getTransform());
        SceneNode icon = button.__getChildren().get(0);
        Assert.assertEquals(16, icon.getPreferredWidth());
        Assert.assertEquals(16, icon.getPreferredHeight());
        Assert.assertTrue("图标必须使用 UILib 宿主图片源", icon.getImageSource() instanceof HostImageSource);
        HostImageSource source = (HostImageSource) icon.getImageSource();
        Assert.assertEquals(HostImageSource.Kind.BUFFERED_IMAGE, source.getKind());
        Assert.assertEquals("图标语义与缓存来源必须对应: " + name,
                "chat-toolbar:white:" + ChatToolbarIcons.urlFor(name), source.getImageKey());
        Assert.assertNotNull(source.getBufferedImage());
        Assert.assertEquals("缓存位图中的不透明像素必须进入图像源",
                0xFFFFFFFF, source.getBufferedImage().getRGB(8, 8));
        return icon;
    }

    static void assertIconRow(SceneNode toolbar, String... names) {
        Assert.assertEquals("可见动作数", names.length, toolbar.__getChildren().size());
        Assert.assertEquals("按钮之间露出背景的间隙", 6, toolbar.getGap());
        Assert.assertEquals("工具栏根没有背景底座", 0, toolbar.getBackgroundColor());
        Assert.assertEquals("工具栏根没有整条边框", 0, toolbar.getBorderWidth());
        Assert.assertNull("玻璃配方只挂在每颗按钮", toolbar.getBackdrop());
        for (int i = 0; i < names.length; i++) {
            assertIconButton(toolbar.__getChildren().get(i), names[i]);
        }
    }

    @Before
    public void setUp() {
        primeIconCache();
        ChatActionService.getInstance().clear();
        ChatHudWindow.setToolbarSide(HudToolbarSide.DEFAULT);
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        if (rt != null) {
            rt.dispose();
            rt = null;
        }
        DocumentRemoteImageCache.getInstance().clearForTesting();
        if (attachedHost != null) {
            ChatHudWindow.detachToolbarHost(attachedHost);
            attachedHost = null;
        }
        ChatHudWindow.setToolbarSide(HudToolbarSide.DEFAULT);
        ChatActionService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }

    private static ChatToolbar.Host host(final Signal<Boolean> editing) {
        return new ChatToolbar.Host() {
            @Override public ReadableSignal<Boolean> editing() { return editing; }
            @Override public ReadableSignal<Boolean> canResetCurrent() { return Signal.create(Boolean.FALSE); }
            @Override public ReadableSignal<Boolean> canResetAll() { return Signal.create(Boolean.FALSE); }
            @Override public void finishEdit() { }
            @Override public void cancelEdit() { }
            @Override public void resetCurrent() { }
            @Override public void resetAll() { }
        };
    }

    private static ChatAction action(String id, String label, int order) {
        return ChatAction.builder(id).label(label).order(order)
                .visible(Signal.create(Boolean.TRUE))
                .enabled(Signal.create(Boolean.TRUE))
                .action(new Runnable() {
                    @Override public void run() { }
                }).build();
    }

    /** 用于断言图标按钮无残留标签，以及 tooltip 浮层中的用户文本。 */
    static List<String> texts(SceneNode node) {
        List<String> result = new ArrayList<String>();
        collect(node, result);
        return result;
    }

    private static void collect(SceneNode node, List<String> sink) {
        if (node.getText() != null && !node.getText().isEmpty()) {
            sink.add(node.getText());
        }
        for (SceneNode child : node.__getChildren()) {
            collect(child, sink);
        }
    }

    @Test
    public void rendersRegisteredActionsAndSwitchesToEditRow() {
        ChatActionRegistration registration = ChatActionService.getInstance()
                .register(action("test:action", "测试动作", 1));
        rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        Signal<Boolean> editing = Signal.create(Boolean.FALSE);
        SceneNode toolbar = ChatToolbar.mount(rt, host(editing));
        rt.flush();

        assertIconRow(toolbar, "action");

        editing.set(Boolean.TRUE);
        rt.flush();
        assertIconRow(toolbar, "finish", "cancel", "reset-current", "reset-all");
        editing.set(Boolean.FALSE);
        rt.flush();
        assertIconRow(toolbar, "action");

        registration.close();
    }

    @Test
    public void everyButtonGetsItsOwnGlassRecipeWithCappedBlurAndDisabledLens() {
        boolean savedGlass = ChatMarkdownSettings.isGlassEnabled();
        int savedBlur = ChatMarkdownSettings.getGlassBlurRadiusPx();
        float savedLens = ChatMarkdownSettings.getGlassLensStrength();
        try {
            ChatMarkdownSettings.setGlassEnabled(true);
            ChatMarkdownSettings.setGlassLensStrength(0.8F);
            for (int blur : new int[] {0, 3, 20}) {
                ChatMarkdownSettings.setGlassBlurRadiusPx(blur);
                rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
                SceneNode toolbar = ChatToolbar.mount(rt, host(Signal.create(Boolean.TRUE)));
                rt.flush();
                assertIconRow(toolbar, "finish", "cancel", "reset-current", "reset-all");
                for (int i = 0; i < toolbar.__getChildren().size(); i++) {
                    SceneNode button = toolbar.__getChildren().get(i);
                    Assert.assertNotNull("启用与禁用按钮都各持玻璃配方", button.getBackdrop());
                    Assert.assertEquals(Math.min(6, blur), button.getBackdrop().getBlurRadius());
                    Assert.assertSame(UiGlassMaterial.DARK_THIN, button.getBackdrop().getEffect().getMaterial());
                    Assert.assertEquals(UiBackdropEffect.Family.LIQUID_GLASS,
                            button.getBackdrop().getEffect().getFamily());
                    Assert.assertEquals(0.8F * (i < 2 ? 0.85F : 0.65F),
                            button.getBackdrop().getEffect().getLensStrength(), 0.0001F);
                    Assert.assertEquals(i < 2 ? 0.5F : 0.35F, button.__getSurfaceElevation(), 0.0001F);
                }
                rt.dispose();
                rt = null;
            }
            ChatMarkdownSettings.setGlassEnabled(false);
            rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            SceneNode toolbar = ChatToolbar.mount(rt, host(Signal.create(Boolean.TRUE)));
            rt.flush();
            assertIconRow(toolbar, "finish", "cancel", "reset-current", "reset-all");
            for (SceneNode button : toolbar.__getChildren()) {
                Assert.assertNull("关闭滤镜后每颗按钮释放配方", button.getBackdrop());
                Assert.assertTrue("关闭滤镜仍保留实体", button.__getSurfaceElevation() >= 0.0F);
            }
        } finally {
            ChatMarkdownSettings.setGlassEnabled(savedGlass);
            ChatMarkdownSettings.setGlassBlurRadiusPx(savedBlur);
            ChatMarkdownSettings.setGlassLensStrength(savedLens);
        }
    }

    @Test
    public void hiddenActionDoesNotOccupyToolbar() {
        ChatActionService.getInstance().register(ChatAction.builder("test:hidden")
                .label("隐藏动作").order(0)
                .visible(Signal.create(Boolean.FALSE))
                .enabled(Signal.create(Boolean.TRUE))
                .action(new Runnable() {
                    @Override public void run() { }
                }).build());
        rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode toolbar = ChatToolbar.mount(rt, host(Signal.create(Boolean.FALSE)));
        rt.flush();
        assertIconRow(toolbar);
    }

    /**
     * 聊天 HUD 声明的是 HUD 级外接工具栏（不是容器内部行）：规格默认值正确、
     * 工具栏在内容盒之外渲染、外框高度计入厚度，且"聊天配置边位"生效。
     */
    @Test
    public void chatHudDeclaresExternalToolbarLayerAndHonorsConfiguredSide() {
        attachedHost = host(Signal.create(Boolean.FALSE));
        ChatHudWindow.attachToolbarHost(attachedHost);
        HudToolbarSpec spec = ChatHudWindow.chatToolbarSpec();
        Assert.assertEquals("聊天工具栏默认挂下边", HudToolbarSide.DEFAULT, spec.getSide());
        Assert.assertEquals(HudToolbarSpec.DEFAULT_THICKNESS_PX, spec.getThickness());
        Assert.assertEquals(HudToolbarSpec.DEFAULT_GAP_PX, spec.getGap());
        Assert.assertTrue("打开态聊天屏工具栏可见", Boolean.TRUE.equals(spec.getVisible().get()));

        ChatActionService.getInstance().register(action("test:layer", "图层动作", 1));
        rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode content = SceneNode.column().setPreferredWidth(200).setPreferredHeight(100);
        HudToolbarLayer.Result layer = HudToolbarLayer.mount(rt, spec, content,
                r -> ChatToolbar.mount(r, host(Signal.create(Boolean.FALSE))));
        rt.flush();
        Assert.assertNotNull(layer.toolbar());
        Assert.assertNotSame("工具栏必须挂在聊天内容盒之外", content, layer.root());
        assertIconRow(layer.toolbar(), "action");
        Assert.assertEquals("外框高 = 内容 + gap + 厚度",
                100 + spec.getGap() + spec.getThickness(), layer.outerHeight(100));

        ChatHudWindow.setToolbarSide(HudToolbarSide.RIGHT);
        Assert.assertEquals("聊天配置边位必须反映到规格",
                HudToolbarSide.RIGHT, ChatHudWindow.chatToolbarSpec().getSide());
    }

    @Test
    public void builtinEditActionReachesAttachedSink() {
        ChatHudEditIntent.install();
        final boolean[] entered = new boolean[] {false};
        ChatHudEditIntent.Sink sink = new ChatHudEditIntent.Sink() {
            @Override public void requestEnterEdit() { entered[0] = true; }
        };
        ChatHudEditIntent.attach(sink);
        ChatAction builtin = null;
        for (ChatAction candidate : ChatActionService.getInstance().actions()) {
            if (ChatHudEditIntent.ACTION_ID.equals(candidate.getId())) {
                builtin = candidate;
            }
        }
        Assert.assertNotNull("内置编辑动作必须经公共注册表可查", builtin);
        builtin.run();
        Assert.assertTrue("动作只发布意图，由当前屏消费", entered[0]);

        ChatHudEditIntent.detach(sink);
        builtin.run(); // 无活动屏：安全空操作
        Assert.assertTrue(entered[0]);
    }
}
