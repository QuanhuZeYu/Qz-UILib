package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.api.chat.ChatAction;
import club.heiqi.uilib.api.chat.ChatActionService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.view.ChatContainer;
import club.heiqi.uilib.internal.chat3.view.ChatHudWindow;
import club.heiqi.uilib.internal.chat3.view.ChatMessageList;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudPlacement;
import club.heiqi.uilib.ui.hud.api.HudToolbarLayer;
import club.heiqi.uilib.ui.hud.api.HudToolbarService;
import club.heiqi.uilib.ui.hud.api.HudToolbarSide;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneTooltip;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天外接工具栏「真实装配 + 真实布局盒 + 真实命中」回归（真机缺陷：工具栏与「编辑 HUD」
 * 入口完全不可见）。
 *
 * <h3>为什么必须有本类</h3>
 * <p>仅检查节点存在不能证明可见。历史缺陷是
 * {@code SceneRuntime.show} 的零高 anchor 在 ROW 里吃满主轴宽（无文本叶宽度取父约束宽），
 * 把动作行推到视口外：树里有「编辑 HUD」文本，布局盒却在 x=1240、命中次数 0。本类用
 * 真实 {@link ChatToolbar}（含内置「编辑 HUD」动作）+ 真实 {@link HudToolbarService}/
 * {@link HudToolbarLayer} + 真实 {@link ChatContainer}，走与 {@code ChatInputSurface} 相同的
 * root/viewport/{@code applyOuterPlacement} 流程，断言按钮的非零可见盒与真实命中。</p>
 *
 * <p>图标经预热缓存、scene paint/replay 验证，中文动作含义由真实 hover tooltip 解释；
 * FixedTextMeasurer 使聊天内容和 tooltip 的文本度量不依赖机器字体。</p>
 */
public class ChatToolbarGeometryTest {

    private static final int W = 854;
    private static final int H = 480;
    /** 聊天内容与 tooltip 使用确定的字体度量，图标布局独立于标签字数。 */
    private static final FixedTextMeasurer MEASURER = new FixedTextMeasurer(16, 16);

    private static final ChatMessageList.SegmentParser PARSER = new ChatMessageList.SegmentParser() {
        @Override public List<TextSegment> parse(String text, int baseColor) {
            TextStyle style = new TextStyle();
            style.setColor(baseColor);
            return Collections.singletonList(new TextSegment(text, style));
        }
    };

    private static final ChatLineLayouter.Measure MEASURE = new ChatLineLayouter.Measure() {
        @Override public float advance(String text, int fontSizePx) { return text.length() * 16.0F; }
        @Override public int epoch() { return 1; }
    };

    private SceneRuntime rt;
    private SceneLayoutEngine engine;
    private SceneNode root;
    private ChatContainer.Result container;
    private HudToolbarLayer.Result layer;
    private TestHost host;
    private ChatHudEditIntent.Sink sink;

    @Before
    public void setUp() {
        ChatToolbarTest.primeIconCache();
        ReactiveScheduler.get().reset();
        ChatActionService.getInstance().clear();
        HudToolbarService.getInstance().clear();
        ChatHudWindow.close();
        ChatHudWindow.setToolbarSide(HudToolbarSide.DEFAULT);
    }

    @After
    public void tearDown() {
        if (sink != null) {
            ChatHudEditIntent.detach(sink);
            sink = null;
        }
        if (rt != null) {
            rt.dispose();
            rt = null;
        }
        if (host != null) {
            ChatHudWindow.detachToolbarHost(host);
        }
        DocumentRemoteImageCache.getInstance().clearForTesting();
        ChatActionService.getInstance().clear();
        HudToolbarService.getInstance().clear();
        ChatHudWindow.close();
        ChatHudWindow.setToolbarSide(HudToolbarSide.DEFAULT);
        ReactiveScheduler.get().reset();
    }

    /** 测试宿主：真实编辑态信号 + 真实动作回调计数（等价 ChatInputSurface 的 Host 实现）。 */
    private static final class TestHost implements ChatToolbar.Host {
        final Signal<Boolean> editing = Signal.create(Boolean.FALSE);
        int finishCount;
        int cancelCount;
        int resetCurrentCount;
        int resetAllCount;

        @Override public ReadableSignal<Boolean> editing() { return editing; }
        @Override public ReadableSignal<Boolean> canResetCurrent() { return editing; }
        @Override public ReadableSignal<Boolean> canResetAll() { return editing; }
        @Override public void finishEdit() { finishCount++; editing.set(Boolean.FALSE); }
        @Override public void cancelEdit() { cancelCount++; editing.set(Boolean.FALSE); }
        @Override public void resetCurrent() { resetCurrentCount++; }
        @Override public void resetAll() { resetAllCount++; }
    }

    private void mount(HudToolbarSide side, ChatAction... actions) {
        for (ChatAction action : actions) {
            ChatActionService.getInstance().register(action);
        }
        ChatHudEditIntent.install();
        ChatHudWindow.setToolbarSide(side);
        HudToolbarService.getInstance().register(ChatHudWindow.HUD_ID, ChatHudWindow.chatToolbarSpec(),
                r -> ChatToolbar.mount(r, host, ChatHudWindow.getToolbarSide()));
        mountInstance();
    }

    /**
     * 装配一个「打开态聊天屏」实例：新 Host + 新 runtime + 新 container + 从同一全局注册表
     * 取规格/工厂装外接层。关闭再打开时重复调用本方法（注册表不重建）。
     */
    private void mountInstance() {
        host = new TestHost();
        ChatHudWindow.attachToolbarHost(host);
        rt = new SceneRuntime(MEASURER);
        engine = new SceneLayoutEngine(MEASURER);
        root = SceneNode.column().setHitTestable(true).setFillParentHeight(true).setPadding(0);
        ChatSceneController controller = new ChatSceneController(MEASURE,
                new ChatSceneController.SelfNameProvider() {
                    @Override public String selfName() { return "Alex"; }
                }, PARSER);
        container = ChatContainer.mount(rt, controller,
                new IdentityHashMap<SceneNode, ChatLineRecord>(), "");
        layer = HudToolbarService.getInstance().mountLayer(rt, ChatHudWindow.HUD_ID, container.root());
        root.appendChild(layer.root());
        rt.flush();
    }

    /** 关闭当前实例：解绑宿主 + 释放 container/runtime（真实屏幕重开前的销毁）。 */
    private void closeInstance() {
        ChatHudWindow.detachToolbarHost(host);
        container.dispose();
        rt.dispose();
        rt = null;
    }

    /** 一帧：同步视口 → 与生产同源的 applyOuterPlacement → flush → layout。 */
    private void frame() {
        frame(HudPlacement.defaultOf(HudAnchor.BOTTOM_LEFT, ChatMarkdownSettings.getChatMarginPx()));
    }

    private void frame(HudPlacement placement) {
        container.setViewport(W, H);
        ChatInputSurface.applyOuterPlacement(layer, W, H, placement, HudInsets.NONE);
        rt.flush();
        engine.layout(root, new Constraints(W, H));
        rt.__bridgeLayoutEpoch(engine.layoutEpoch());
        rt.flush();
    }

    private void frames(int count) {
        for (int i = 0; i < count; i++) {
            frame();
        }
    }

    private static ChatAction action(String id, String label, int order) {
        return ChatAction.builder(id).label(label).order(order)
                .visible(Signal.create(Boolean.TRUE))
                .enabled(Signal.create(Boolean.TRUE))
                .action(new Runnable() {
                    @Override public void run() { }
                }).build();
    }

    /** 普通项按 order/注册序，编辑项固定为完成、取消、恢复当前、恢复全部。 */
    private SceneNode buttonAt(int index) {
        return layer.toolbar().__getChildren().get(index);
    }

    /** 本类普通动作 order 均小于内置编辑项，故编辑项在最后。 */
    private SceneNode editButton() {
        return buttonAt(layer.toolbar().__getChildren().size() - 1);
    }

    private static AnchorRect box(SceneNode node) {
        return SceneGeometry.absoluteBox(node, 0, 0);
    }

    private static String describe(SceneNode node) {
        AnchorRect r = box(node);
        return "[" + r.getX() + "," + r.getY() + " " + r.getWidth() + "x" + r.getHeight() + "]";
    }

    /** 断言节点盒非零、在给定父盒内、且在视口内。 */
    private static void assertVisibleInside(SceneNode node, AnchorRect containerBox, String what) {
        AnchorRect b = box(node);
        Assert.assertTrue(what + " 必须有非零可见盒: " + describe(node),
                b.getWidth() > 0 && b.getHeight() > 0);
        Assert.assertTrue(what + " 必须落在父盒 " + containerBox + " 内: " + b,
                b.getX() >= containerBox.getX() && b.getY() >= containerBox.getY()
                        && b.getX() + b.getWidth() <= containerBox.getX() + containerBox.getWidth()
                        && b.getY() + b.getHeight() <= containerBox.getY() + containerBox.getHeight());
        Assert.assertTrue(what + " 必须落在视口内: " + b,
                b.getX() >= 0 && b.getY() >= 0 && b.getX() + b.getWidth() <= W
                        && b.getY() + b.getHeight() <= H);
    }

    /** 真实路由：在节点中心注入单个指针事件帧。 */
    private void routePointer(SceneNode node, ScenePointerAction action) {
        AnchorRect b = box(node);
        int cx = b.getX() + Math.max(1, b.getWidth()) / 2;
        int cy = b.getY() + Math.max(1, b.getHeight()) / 2;
        routePointer(cx, cy, action);
    }

    private void routePointer(int cx, int cy, ScenePointerAction action) {
        InputFrameBuilder fb = new InputFrameBuilder(cx, cy);
        fb.push(RawInputEvent.ofPointer(action, cx, cy,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame inputFrame = fb.drainFrame();
        rt.route(root, inputFrame, 0, 0);
        rt.flush();
    }

    private void assertTooltipText(String... lines) {
        Assert.assertEquals("悬停动作只展示一个 tooltip", 1, rt.getOverlayHost().size());
        SceneNode tooltip = rt.getOverlayHost().bottomFirst().get(0).getRoot();
        Assert.assertEquals(Arrays.asList(lines), ChatToolbarTest.texts(tooltip));
    }

    /** 通过帧采样推进真实 tooltip 延时，无 sleep、无直接写 hover 信号。 */
    private void completeTooltipDelay() {
        rt.__sampleMotion(0L);
        rt.__sampleMotion(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(SceneTooltip.DEFAULT_DELAY_MILLIS));
        rt.flush();
    }

    /** 命中探针：在节点中心注入 POINTER_DOWN，返回命中该节点的次数。 */
    private int pressCenter(SceneNode node) {
        final int[] hits = new int[1];
        rt.on(node, SceneEventType.POINTER_DOWN, (event, ctx) -> hits[0]++);
        routePointer(node, ScenePointerAction.BUTTON_DOWN);
        return hits[0];
    }

    /** 真实点击：DOWN + UP 两帧，由路由器合成 CLICK（控件回调路径）。 */
    private void clickCenter(SceneNode node) {
        routePointer(node, ScenePointerAction.BUTTON_DOWN);
        routePointer(node, ScenePointerAction.BUTTON_UP);
    }

    /**
     * 红绿回归（真机缺陷）：默认下边工具栏的「编辑 HUD」按钮必须非零可见且可命中，
     * 外框实测尺寸必须与放置口径一致。
     */
    @Test
    public void bottomToolbarButtonsAreVisibleAndHittable() {
        mount(HudToolbarSide.BOTTOM, action("test:one", "测试动作", 1));
        frames(3);

        Assert.assertTrue("工具栏必须挂在内容盒之外", layer.toolbar() != null);
        Assert.assertSame("工具栏必须是外框的直接子节点", layer.root(), layer.toolbar().__getParent());
        Assert.assertTrue(layer.isVisible());
        Assert.assertEquals("工具栏与聊天容器共用玻璃参数",
                container.root().getBackdrop(), layer.toolbar().getBackdrop());
        Assert.assertEquals("工具栏与聊天容器共用背景",
                container.root().getBackgroundColor(), layer.toolbar().getBackgroundColor());
        Assert.assertEquals("工具栏与聊天容器共用圆角",
                container.root().getCornerRadius(), layer.toolbar().getCornerRadius());

        AnchorRect wrapper = box(layer.root());
        AnchorRect toolbar = box(layer.toolbar());
        AnchorRect content = box(layer.content());
        Assert.assertEquals("工具栏行高 = 规格厚度", HudToolbarSpec.DEFAULT_THICKNESS_PX,
                toolbar.getHeight());
        Assert.assertTrue("工具栏必须在内容盒下方", toolbar.getY() >= content.getY() + content.getHeight());
        Assert.assertTrue("工具栏必须在视口内: " + toolbar,
                toolbar.getX() >= 0 && toolbar.getY() >= 0
                        && toolbar.getX() + toolbar.getWidth() <= W
                        && toolbar.getY() + toolbar.getHeight() <= H);
        Assert.assertEquals("外框宽必须与放置口径一致",
                ChatInputSurface.outerWidthFor(layer, W), wrapper.getWidth());
        Assert.assertEquals("外框高必须与放置口径一致",
                ChatInputSurface.outerHeightFor(layer, H), wrapper.getHeight());

        ChatToolbarTest.assertIconRow(layer.toolbar(), "action", "edit");
        for (SceneNode button : layer.toolbar().__getChildren()) {
            assertVisibleInside(button, toolbar, "图标按钮");
            Assert.assertEquals(24, box(button).getWidth());
            Assert.assertEquals(24, box(button).getHeight());
            Assert.assertEquals("图标按钮中心必须真实命中", 1, pressCenter(button));
        }
    }

    /**
     * 真实「编辑 HUD」动作链：点按钮 → ChatAction → ChatHudEditIntent → Sink → 编辑态，
     * 编辑态出现「完成 / 取消」且都可命中；点「完成」回调生效并回到普通态。
     */
    @Test
    public void editActionChainSwitchesRowAndFinishCancelAreHittable() {
        mount(HudToolbarSide.BOTTOM, action("test:one", "测试动作", 1));
        frames(3);
        final int[] enterEditCount = new int[1];
        sink = new ChatHudEditIntent.Sink() {
            @Override public void requestEnterEdit() {
                enterEditCount[0]++;
                host.editing.set(Boolean.TRUE);
            }
        };
        ChatHudEditIntent.attach(sink);

        clickCenter(editButton());
        Assert.assertEquals("点击「编辑 HUD」必须真实命中并发布意图到当前屏", 1, enterEditCount[0]);
        frame();

        ChatToolbarTest.assertIconRow(layer.toolbar(), "finish", "cancel", "reset-current", "reset-all");
        String[] labels = {"完成", "取消", "恢复当前默认", "恢复全部默认"};
        for (int i = 0; i < labels.length; i++) {
            routePointer(buttonAt(i), ScenePointerAction.MOVE);
            assertTooltipText(labels[i]);
        }
        clickCenter(buttonAt(2));
        clickCenter(buttonAt(3));
        Assert.assertEquals("恢复当前默认回调", 1, host.resetCurrentCount);
        Assert.assertEquals("恢复全部默认回调", 1, host.resetAllCount);

        AnchorRect toolbar = box(layer.toolbar());
        SceneNode finish = buttonAt(0);
        SceneNode cancel = buttonAt(1);
        assertVisibleInside(finish, toolbar, "按钮「完成」");
        assertVisibleInside(cancel, toolbar, "按钮「取消」");
        assertVisibleInside(buttonAt(2), toolbar, "按钮「恢复当前默认」");
        assertVisibleInside(buttonAt(3), toolbar, "按钮「恢复全部默认」");
        clickCenter(finish);
        Assert.assertEquals("「完成」回调必须执行", 1, host.finishCount);
        Assert.assertEquals("完成后退出编辑态", Boolean.FALSE, host.editing.get());
        frame();
        ChatToolbarTest.assertIconRow(layer.toolbar(), "action", "edit");

        host.editing.set(Boolean.TRUE);
        frame();
        clickCenter(buttonAt(1));
        Assert.assertEquals("「取消」回调必须执行", 1, host.cancelCount);
    }

    /**
     * 竖直边（LEFT/RIGHT）必须真实纵向排布：28px 条宽容纳 24px 按钮与 16px 图标，可命中。
     */
    @Test
    public void verticalSidesStackButtonsWithoutClippingIcons() {
        for (HudToolbarSide side : new HudToolbarSide[] {HudToolbarSide.LEFT, HudToolbarSide.RIGHT}) {
            tearDown();
            setUp();
            mount(side, action("test:one", "测试动作", 1));
            host.editing.set(Boolean.TRUE);
            frames(3);

            AnchorRect toolbar = box(layer.toolbar());
            Assert.assertEquals(HudToolbarSpec.DEFAULT_THICKNESS_PX, ChatToolbar.VERTICAL_THICKNESS_PX);
            Assert.assertEquals("竖直边条宽 = 默认规格厚度",
                    ChatToolbar.VERTICAL_THICKNESS_PX, toolbar.getWidth());
            Assert.assertTrue("竖直边工具栏必须在视口内: " + toolbar,
                    toolbar.getX() >= 0 && toolbar.getY() >= 0
                            && toolbar.getX() + toolbar.getWidth() <= W
                            && toolbar.getY() + toolbar.getHeight() <= H);
            Assert.assertEquals("竖直边外框宽必须与放置口径一致",
                    ChatInputSurface.outerWidthFor(layer, W), box(layer.root()).getWidth());

            SceneNode finish = buttonAt(0);
            SceneNode cancel = buttonAt(1);
            SceneNode resetCurrent = buttonAt(2);
            SceneNode resetAll = buttonAt(3);
            AnchorRect[] buttons = {box(finish), box(cancel), box(resetCurrent), box(resetAll)};
            for (int i = 1; i < buttons.length; i++) {
                Assert.assertTrue("竖直边必须竖排（按钮 " + i + " 不得与上一个重叠）: "
                        + buttons[i - 1] + " vs " + buttons[i],
                        buttons[i].getY() >= buttons[i - 1].getY() + buttons[i - 1].getHeight());
            }
            for (SceneNode button : new SceneNode[] {finish, cancel, resetCurrent, resetAll}) {
                AnchorRect b = box(button);
                Assert.assertEquals(24, b.getWidth());
                Assert.assertEquals(24, b.getHeight());
                assertVisibleInside(button, toolbar, "竖直图标按钮");
                SceneNode icon = button.__getChildren().get(0);
                Assert.assertEquals(16, box(icon).getWidth());
                Assert.assertEquals(16, box(icon).getHeight());
                assertVisibleInside(icon, b, "图标");
            }
            Assert.assertEquals("竖直边「完成」必须可命中", 1, pressCenter(finish));
        }
    }

    /**
     * 已知缺陷回归：水平工具栏比内容宽时，外框必须按工具栏加宽；右锚点下整条工具栏必须仍在视口内。
     */
    @Test
    public void rightAnchoredToolbarWiderThanContentStaysInsideViewport() {
        List<ChatAction> actions = new ArrayList<ChatAction>();
        for (int i = 0; i < 20; i++) {
            actions.add(action("test:wide" + i, "动作标签" + i, i));
        }
        mount(HudToolbarSide.BOTTOM, actions.toArray(new ChatAction[0]));
        frames(3);

        AnchorRect toolbar = box(layer.toolbar());
        int contentWidth = ChatMarkdownSettings.chatWidthFor(W);
        Assert.assertTrue("用例前提：工具栏必须比内容宽（工具栏 " + toolbar.getWidth()
                + " vs 内容 " + contentWidth + "）", toolbar.getWidth() > contentWidth);
        Assert.assertEquals("外框宽必须按实测工具栏加宽",
                toolbar.getWidth(), ChatInputSurface.outerWidthFor(layer, W));

        frame(HudPlacement.of(HudAnchor.BOTTOM_RIGHT, ChatMarkdownSettings.getChatMarginPx(),
                ChatMarkdownSettings.getChatMarginPx()));
        AnchorRect wrapper = box(layer.root());
        Assert.assertTrue("右锚点下工具栏必须仍在视口内: " + wrapper,
                wrapper.getX() >= 0 && wrapper.getX() + wrapper.getWidth() <= W);
        Assert.assertEquals("外框宽必须与实测外框一致",
                ChatInputSurface.outerWidthFor(layer, W), wrapper.getWidth());
    }

    /**
     * 初始帧 → 稳定 + 切入编辑首帧：挂载后第一帧按钮就必须可见可命中（不是"flush 多帧才可见"），
     * 列表切换（进入编辑）当帧 flush 后同样如此。
     */
    @Test
    public void firstFrameAndEditTransitionAreAlreadyVisibleAndHittable() {
        mount(HudToolbarSide.BOTTOM, action("test:one", "测试动作", 1));
        frame(); // 仅一帧

        Assert.assertTrue("初始帧工具栏必须可见", layer.isVisible());
        AnchorRect toolbar = box(layer.toolbar());
        Assert.assertTrue("初始帧工具栏必须有非零盒: " + describe(layer.toolbar()),
                toolbar.getWidth() > 0 && toolbar.getHeight() > 0);
        SceneNode editButton = editButton();
        assertVisibleInside(editButton, toolbar, "初始帧按钮「编辑 HUD」");
        Assert.assertEquals("初始帧「编辑 HUD」必须可命中", 1, pressCenter(editButton));

        frame(); // 稳定帧
        Assert.assertEquals("稳定后外框宽必须与放置口径一致",
                ChatInputSurface.outerWidthFor(layer, W), box(layer.root()).getWidth());
        Assert.assertEquals("稳定后外框高必须与放置口径一致",
                ChatInputSurface.outerHeightFor(layer, H), box(layer.root()).getHeight());

        host.editing.set(Boolean.TRUE);
        frame(); // 切入编辑仅一帧
        AnchorRect editToolbar = box(layer.toolbar());
        SceneNode finish = buttonAt(0);
        assertVisibleInside(finish, editToolbar, "切入编辑首帧按钮「完成」");
        Assert.assertEquals("切入编辑首帧「完成」必须可命中", 1, pressCenter(finish));
    }

    /**
     * 右锚点 + 工具栏比内容宽：<b>首帧</b>工具栏尚无实测内在尺寸，放置退回内容宽（右锚点下
     * 可能溢出视口，属已知一帧滞后）；<b>第二帧起</b>实测内在尺寸参与放置，必须完全落入视口。
     *
     * <p>本用例只钉稳定态，不假装首帧就正确；首帧溢出由打开动画（pop 期 opacity 0→1）掩盖。</p>
     */
    @Test
    public void rightAnchoredWideToolbarStableFromSecondFrame() {
        List<ChatAction> actions = new ArrayList<ChatAction>();
        for (int i = 0; i < 20; i++) {
            actions.add(action("test:wide" + i, "动作标签" + i, i));
        }
        mount(HudToolbarSide.BOTTOM, actions.toArray(new ChatAction[0]));
        HudPlacement right = HudPlacement.of(HudAnchor.BOTTOM_RIGHT,
                ChatMarkdownSettings.getChatMarginPx(), ChatMarkdownSettings.getChatMarginPx());

        frame(right); // 首帧：无实测盒，退回内容宽
        frame(right); // 第二帧：实测内在尺寸参与放置
        AnchorRect wrapper = box(layer.root());
        Assert.assertTrue("稳定后右锚点工具栏必须完全在视口内: " + wrapper,
                wrapper.getX() >= 0 && wrapper.getX() + wrapper.getWidth() <= W);
        Assert.assertEquals("稳定后外框宽必须与实测一致",
                ChatInputSurface.outerWidthFor(layer, W), wrapper.getWidth());
        Assert.assertEquals("稳定后「编辑 HUD」仍可命中", 1, pressCenter(editButton()));
    }

    /** 六种图标均必须进入 scene paint → replay，且绘制矩形与真实 16px 布局盒一致。 */
    @Test
    public void toolbarIconsAreActuallyPaintedInsideViewport() {
        mount(HudToolbarSide.BOTTOM, action("test:one", "测试动作", 1));
        frame();
        assertIconsPainted("action", "edit");
        host.editing.set(Boolean.TRUE);
        frame();
        assertIconsPainted("finish", "cancel", "reset-current", "reset-all");
    }

    private void assertIconsPainted(String... names) {
        ChatToolbarTest.assertIconRow(layer.toolbar(), names);
        RecordingRenderBackend backend = new RecordingRenderBackend();
        new ScenePaintReplayer().replay(new ScenePaintEngine(MEASURER).paint(root).getPlan(), backend);
        List<RecordingRenderBackend.RenderCall> images = new ArrayList<RecordingRenderBackend.RenderCall>();
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) {
            if ("drawImage".equals(call.methodName())) {
                images.add(call);
            }
        }
        Assert.assertEquals("每个可见动作必须绘制一个真实 image", names.length, images.size());
        for (int i = 0; i < names.length; i++) {
            SceneNode icon = ChatToolbarTest.assertIconButton(buttonAt(i), names[i]);
            AnchorRect iconBox = box(icon);
            assertVisibleInside(icon, box(buttonAt(i)), "绘制图标 " + names[i]);
            RecordingRenderBackend.RenderCall call = images.get(i);
            Assert.assertSame("paint/replay 保留图像源", icon.getImageSource(), call.args()[0]);
            Assert.assertEquals(iconBox.getX(), call.getInt(1));
            Assert.assertEquals(iconBox.getY(), call.getInt(2));
            Assert.assertEquals(iconBox.getX() + 16, call.getInt(3));
            Assert.assertEquals(iconBox.getY() + 16, call.getInt(4));
        }
    }

    /**
     * 真实「关闭 → 再打开聊天屏」：销毁旧 container/runtime，<b>同一全局注册表</b>上新建
     * Host + runtime + container + 外接层，编辑动作链必须照常工作（真机「再打开后工具栏消失」
     * 的实例级回归；只 toggle visible 信号不算重开）。
     */
    @Test
    public void reopenWithFreshInstanceStillShowsAndWorksToolbar() {
        mount(HudToolbarSide.BOTTOM, action("test:one", "测试动作", 1));
        frames(3);
        Assert.assertTrue("初次打开必须可见", layer.isVisible());
        Assert.assertEquals("初次打开必须可命中", 1, pressCenter(editButton()));

        // 关闭：解绑宿主 + 销毁旧实例（container/runtime）；注册表保持不变
        closeInstance();
        Assert.assertTrue("注册表必须跨实例保留", HudToolbarService.getInstance().hasToolbar(ChatHudWindow.HUD_ID));

        // 再打开：全新 Host + runtime + container + layer，同一全局注册表
        mountInstance();
        frames(2);
        Assert.assertTrue("再打开必须重新挂载", layer.isVisible());
        AnchorRect toolbar = box(layer.toolbar());
        SceneNode editButton = editButton();
        assertVisibleInside(editButton, toolbar, "再打开后的按钮「编辑 HUD」");

        final int[] enterEditCount = new int[1];
        sink = new ChatHudEditIntent.Sink() {
            @Override public void requestEnterEdit() {
                enterEditCount[0]++;
                host.editing.set(Boolean.TRUE);
            }
        };
        ChatHudEditIntent.attach(sink);
        clickCenter(editButton);
        Assert.assertEquals("再打开后「编辑 HUD」动作必须到达新屏", 1, enterEditCount[0]);
        frame();
        SceneNode finish = buttonAt(0);
        assertVisibleInside(finish, box(layer.toolbar()), "再打开后编辑态按钮「完成」");
        clickCenter(finish);
        Assert.assertEquals("再打开后「完成」必须生效", 1, host.finishCount);
    }

    @Test
    public void tooltipExplainsDisabledActionWithoutActivatingAndClosesOnLeave() {
        Signal<Boolean> enabled = Signal.create(Boolean.FALSE);
        final int[] activations = new int[1];
        ChatAction disabled = ChatAction.builder("test:disabled").label("测试动作")
                .tooltip("当前不可用").order(1).enabled(enabled)
                .visible(Signal.create(Boolean.TRUE)).action(() -> activations[0]++).build();
        mount(HudToolbarSide.BOTTOM, disabled);
        rt.__enableMotion();
        frame();
        SceneNode button = buttonAt(0);
        routePointer(button, ScenePointerAction.MOVE);
        Assert.assertEquals("延时未到不显示提示", 0, rt.getOverlayHost().size());
        completeTooltipDelay();
        assertTooltipText("测试动作", "当前不可用");
        clickCenter(button);
        Assert.assertEquals("禁用动作不能激活", 0, activations[0]);
        assertTooltipText("测试动作", "当前不可用");
        routePointer(0, 0, ScenePointerAction.MOVE);
        Assert.assertEquals("离开按钮立即关闭 tooltip", 0, rt.getOverlayHost().size());
        enabled.set(Boolean.TRUE);
        frame();
        clickCenter(button);
        Assert.assertEquals("可用性信号恢复后同一按钮可以真实激活", 1, activations[0]);
    }

    @Test
    public void registeredActionsKeepOrderAndRegistrationOrderWithGenericIcons() {
        List<String> calls = new ArrayList<String>();
        ChatAction later = ChatAction.builder("test:later").label("后注册前排序")
                .visible(Signal.create(Boolean.TRUE)).enabled(Signal.create(Boolean.TRUE))
                .order(-1).action(() -> calls.add("first")).build();
        ChatAction tiedFirst = ChatAction.builder("test:tied-first").label("同序先注册")
                .visible(Signal.create(Boolean.TRUE)).enabled(Signal.create(Boolean.TRUE))
                .order(1).action(() -> calls.add("second")).build();
        ChatAction tiedSecond = ChatAction.builder("test:tied-second").label("同序后注册")
                .visible(Signal.create(Boolean.TRUE)).enabled(Signal.create(Boolean.TRUE))
                .order(1).action(() -> calls.add("third")).build();
        mount(HudToolbarSide.BOTTOM, tiedFirst, tiedSecond, later);
        frame();
        ChatToolbarTest.assertIconRow(layer.toolbar(), "action", "action", "action", "edit");
        for (int i = 0; i < 3; i++) {
            clickCenter(buttonAt(i));
        }
        Assert.assertEquals(Arrays.asList("first", "second", "third"), calls);
        routePointer(editButton(), ScenePointerAction.MOVE);
        assertTooltipText("编辑 HUD", "拖动聊天框，调整 HUD 布局");
    }

    /** 四边可用性：任一边的外框实测尺寸都必须等于放置口径，且整体在视口内。 */
    @Test
    public void everySideKeepsOuterBoxConsistentAndInsideViewport() {
        for (HudToolbarSide side : HudToolbarSide.values()) {
            tearDown();
            setUp();
            mount(side, action("test:one", "测试动作", 1));
            frames(3);

            AnchorRect wrapper = box(layer.root());
            Assert.assertTrue(side + " 工具栏必须可见", layer.isVisible());
            Assert.assertEquals(side + " 外框宽必须与放置口径一致",
                    ChatInputSurface.outerWidthFor(layer, W), wrapper.getWidth());
            Assert.assertEquals(side + " 外框高必须与放置口径一致",
                    ChatInputSurface.outerHeightFor(layer, H), wrapper.getHeight());
            Assert.assertTrue(side + " 外框必须在视口内: " + wrapper,
                    wrapper.getX() >= 0 && wrapper.getY() >= 0
                            && wrapper.getX() + wrapper.getWidth() <= W
                            && wrapper.getY() + wrapper.getHeight() <= H);
            Assert.assertEquals(side + " 「编辑 HUD」必须可命中", 1, pressCenter(editButton()));
        }
    }
}
