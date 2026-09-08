package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.api.chat.ChatAction;
import club.heiqi.uilib.api.chat.ChatActionRegistration;
import club.heiqi.uilib.api.chat.ChatActionService;
import club.heiqi.uilib.internal.chat3.view.ChatHudWindow;
import club.heiqi.uilib.ui.hud.api.HudToolbarLayer;
import club.heiqi.uilib.ui.hud.api.HudToolbarSide;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天工具栏契约（规划《聊天工具栏与HUD布局编辑》P1/P2 最小闭环）：
 * 注册动作渲染/排序/隐藏、编辑态切换到完成/取消/重置、内置动作经注册链到达当前屏。
 */
public class ChatToolbarTest {

    /** 本用例绑定的工具栏宿主（@After 解绑，避免静态可见性信号污染同 JVM 其它测试）。 */
    private ChatToolbar.Host attachedHost;

    @Before
    public void setUp() {
        ChatActionService.getInstance().clear();
        ChatHudWindow.setToolbarSide(HudToolbarSide.DEFAULT);
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
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

    /** 深度收集树中所有非空文本（按钮标签经 bindText 在 flush 后落值）。 */
    private static List<String> texts(SceneNode node) {
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
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        Signal<Boolean> editing = Signal.create(Boolean.FALSE);
        SceneNode toolbar = ChatToolbar.mount(rt, host(editing));
        rt.flush();

        Assert.assertTrue("普通态应渲染注册动作", texts(toolbar).contains("测试动作"));
        Assert.assertFalse("普通态不应出现编辑动作", texts(toolbar).contains("完成"));

        editing.set(Boolean.TRUE);
        rt.flush();
        List<String> editTexts = texts(toolbar);
        Assert.assertTrue("编辑态应出现完成", editTexts.contains("完成"));
        Assert.assertTrue("编辑态应出现取消", editTexts.contains("取消"));
        Assert.assertTrue("编辑态应出现恢复当前默认", editTexts.contains("恢复当前默认"));
        Assert.assertTrue("编辑态应出现恢复全部默认", editTexts.contains("恢复全部默认"));
        Assert.assertFalse("编辑态隐藏注册动作", editTexts.contains("测试动作"));

        registration.close();
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
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode toolbar = ChatToolbar.mount(rt, host(Signal.create(Boolean.FALSE)));
        rt.flush();
        Assert.assertFalse("visible=false 的动作不渲染", texts(toolbar).contains("隐藏动作"));
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
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode content = SceneNode.column().setPreferredWidth(200).setPreferredHeight(100);
        HudToolbarLayer.Result layer = HudToolbarLayer.mount(rt, spec, content,
                r -> ChatToolbar.mount(r, host(Signal.create(Boolean.FALSE))));
        rt.flush();
        Assert.assertNotNull(layer.toolbar());
        Assert.assertNotSame("工具栏必须挂在聊天内容盒之外", content, layer.root());
        Assert.assertTrue("聊天动作经外接层渲染", texts(layer.toolbar()).contains("图层动作"));
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
