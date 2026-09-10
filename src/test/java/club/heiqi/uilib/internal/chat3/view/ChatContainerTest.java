package club.heiqi.uilib.internal.chat3.view;

import java.util.Map;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * ChatContainer 契约测试(K3 缺陷 F6②):输入条区四周 8px 内边距(设计稿 §2.3/§6.2),
 * 输入框高 24px 钉在输入条区 40px 内,divider 到输入框顶恰好 8px。
 *
 * <p>另立法：工具栏不得再挂进容器内部（P1/P2 增量迁移为 HUD 级外接层）。</p>
 */
public class ChatContainerTest {

    /**
     * 本类断言的是「非玻璃态」下设计令牌正确落到节点，故显式关闭聊天玻璃。
     *
     * <p>玻璃默认开启会把气泡/输入底色换成半透明档（alpha 由 glass*Alpha 决定），
     * 与本类的令牌等值断言冲突。@After 复位避免静态开关污染同 JVM 其它测试。</p>
     */
    @Before
    public void disableChatGlassForDesignTokenAssertions() {
        ChatMarkdownSettings.setGlassEnabled(false);
    }

    @After
    public void restoreChatGlassDefault() {
        ChatMarkdownSettings.setGlassEnabled(true);
    }


    /** 测试帧时钟基准(wall millis;SmoothScroller 动画起点/终点驱动)。 */
    private static final long T0 = 1_000_000L;

    private static final ChatLineLayouter.Measure FIXED = new ChatLineLayouter.Measure() {
        @Override
        public float advance(String text, int fontSizePx) {
            int effective = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '§' && i + 1 < text.length()) {
                    i++;
                    continue;
                }
                effective++;
            }
            return effective * 4.0F;
        }

        @Override
        public int epoch() {
            return 0;
        }
    };

    private static ChatSceneController controller() {
        return new ChatSceneController(FIXED, new ChatSceneController.SelfNameProvider() {
            @Override
            public String selfName() {
                return "Alex";
            }
        }, new ChatMessageList.SegmentParser() {
            @Override
            public java.util.List<club.heiqi.uilib.font.layout.TextSegment> parse(String text, int baseColor) {
                return java.util.Collections.emptyList();
            }
        });
    }

    @Test
    public void inputRowCarriesEightPixelPaddingAnd24PxInput() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hi"), 1, 0L));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        Map<SceneNode, ChatLineRecord> registry = new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
        ChatContainer.Result result = ChatContainer.mount(rt, controller, registry, "");
        rt.flush();

        // containerNode 子顺序 = [listRow, divider, barRow]：工具栏自 P1/P2 增量起不再挂在
        // 容器内部（改为 HUD 级外接层），divider→输入框间距契约不变
        SceneNode container = result.root();
        Assert.assertEquals(3, container.__getChildren().size());
        SceneNode divider = container.__getChildren().get(1);
        SceneNode barRow = container.__getChildren().get(2);

        Assert.assertEquals("divider 高 1px", 1, divider.getPreferredHeight());
        Assert.assertEquals("输入条区高 40", ChatMarkdownSettings.getInputBarHeightPx(),
                barRow.getPreferredHeight());
        Assert.assertEquals("输入条区四周 8px 内边距(上)", 8, barRow.getPaddingTop());
        Assert.assertEquals("输入条区四周 8px 内边距(右)", 8, barRow.getPaddingRight());
        Assert.assertEquals("输入条区四周 8px 内边距(下)", 8, barRow.getPaddingBottom());
        Assert.assertEquals("输入条区四周 8px 内边距(左)", 8, barRow.getPaddingLeft());

        // 输入框:高 24 = 40 - 8×2,divider 底到输入框顶 = barRow 上 padding 8px
        SceneNode input = result.bar().root();
        Assert.assertEquals("输入框高 24(40 - 2×8)", 24, input.getPreferredHeight());
        Assert.assertEquals("输入框底色 = 设计令牌 bg-input", ChatMarkdownSettings.getInputBackgroundArgb(),
                input.getBackgroundColor());
    }

    /**
     * 立法：聊天工具栏必须经 HUD 级外接层挂载，不得回退到容器内部插行。
     *
     * <p>源码级守卫（与 SceneHudPipelineTest 的宿主栈守卫同型）：容器一旦重新 import /
     * 调用 ChatToolbar，工具栏就又变成"固定在聊天容器内部"，四边可配置与外框尺寸参与
     * placement 两条语义同时失效。</p>
     */
    @Test
    public void containerMustNotMountToolbarInternally() throws Exception {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/club/heiqi/uilib/internal/chat3/view/ChatContainer.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        Assert.assertFalse("ChatContainer 不得再引用 ChatToolbar（工具栏属 HUD 级外接层）",
                source.contains("ChatToolbar"));
    }

    @Test
    public void containerContentPaddingIsTenTenFourTen() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hi"), 1, 0L));
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        Map<SceneNode, ChatLineRecord> registry = new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
        ChatContainer.Result result = ChatContainer.mount(rt, controller, registry, "");
        rt.flush();

        SceneNode container = result.root();
        // 设计稿 §2.3/§6.2:容器内容区上 10/左右 10/下 4(下留给滚动条视觉余量);
        // 不再复用 bubblePadding(5,10,5,10)
        Assert.assertEquals("容器内容区上 10", 10, container.getPaddingTop());
        Assert.assertEquals("容器内容区右 10", 10, container.getPaddingRight());
        Assert.assertEquals("容器内容区下 4(滚动条视觉余量)", 4, container.getPaddingBottom());
        Assert.assertEquals("容器内容区左 10", 10, container.getPaddingLeft());
    }

    /**
     * 视口滚动方向契约(真机「滚轮方向反」修复,聊天↔scene 倒置映射):
     * history.scrollBy 正向 = 滚轮向上 = 向旧消息 → 视口 scrollOffsetY 减小;
     * scroll=0 贴底 → 视口偏移 = maxScrollY(内容底部最新);向上超滚 clamp 到顶部 0。
     */
    @Test
    public void viewportScrollOffsetInvertsChatScrollDirection() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        // 容器形态(chatOpen=true):tick 不跑 HUD TTL 裁剪/高度裁剪,消息不被过滤;
        // 时间戳取 T0 附近(相邻 1ms 并组),避免 HUD 过期阈值误裁内容
        controller.setChatOpen(true);
        for (int i = 0; i < 20; i++) {
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<Bob> message number " + i), 1, T0 + i));
        }
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        Map<SceneNode, ChatLineRecord> registry = new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
        ChatContainer.Result result = ChatContainer.mount(rt, controller, registry, "");
        result.setViewport(400, 300);
        rt.flush();
        // 测试无帧管线:手动 layout + 桥接 layoutDoneSignal(真机由 SceneFramePipeline SETTLE 桥接)
        layoutEngine.layout(result.root(), new Constraints(400, 300));
        rt.__setLayoutDoneEpoch(layoutEngine.layoutEpoch());
        rt.flush();

        // 树结构:containerNode = [listRow, divider, barRow];listRow = [listViewport, scrollbarColumn]
        SceneNode listViewport = result.root().__getChildren().get(0).__getChildren().get(0);
        int maxScroll = SceneGeometry.maxScrollY(listViewport);
        Assert.assertTrue("20 条消息应溢出视口(maxScroll > 0)", maxScroll > 0);

        // scroll = 0(贴底最新)→ 视口偏移 = maxScrollY(内容贴底)
        Assert.assertEquals("scroll=0 贴底:视口偏移 = maxScrollY", maxScroll,
                listViewport.getScrollOffsetY());

        // 滚轮向上 → history.scrollBy(+3)(向旧消息)→ 视口偏移减小 = 内容向旧消息方向滚动
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        controller.history().scrollBy(3);
        controller.notifyDataChanged();
        controller.tick(T0);
        rt.flush();
        controller.tick(T0 + 120L);
        rt.flush();
        Assert.assertEquals("滚轮向上 scrollBy(+3):视口偏移 = maxScrollY - 3 行",
                maxScroll - 3 * lineHeight, listViewport.getScrollOffsetY());

        // 滚轮向下 → scrollBy(-3)(回新消息)→ 视口偏移增大回底
        controller.history().scrollBy(-3);
        controller.notifyDataChanged();
        controller.tick(T0 + 120L);
        rt.flush();
        controller.tick(T0 + 240L);
        rt.flush();
        Assert.assertEquals("滚轮向下 scrollBy(-3):视口偏移回 maxScrollY(贴底最新)",
                maxScroll, listViewport.getScrollOffsetY());

        // 向上超滚 clamp:视口偏移下限 0(顶部最旧),不出现负偏移
        controller.history().scrollBy(1000);
        controller.notifyDataChanged();
        controller.tick(T0 + 240L);
        rt.flush();
        controller.tick(T0 + 360L);
        rt.flush();
        Assert.assertEquals("向上超滚 clamp:视口偏移 = 0(顶部最旧)", 0,
                listViewport.getScrollOffsetY());
    }

    /**
     * 行域滚动上限必须由真实几何回填进**权威**(真机反馈:到顶后继续滚,后台记录仍在累加,
     * 向下要先消费掉这些看不见的死值才能动)。
     *
     * <p>旧实现把上限委托给 {@code viewportScrollPx} 的投影 clamp(源码注释原话"上限由视口
     * 偏移 clamp 折算保证"):渲染到顶就停,行域却无界增长。本用例走完整挂载 + 布局纪元桥接,
     * 断言权威自身读数,并与真实几何对账 —— 上限既不能大(又回到累加)也不能小(顶部留死区)。</p>
     */
    @Test
    public void overscrollUpMustStopAtGeometryCeilingInTheAuthority() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.setChatOpen(true);
        Mounted mounted = mountMixed(controller, 30, 12);
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        int ceiling = (int) Math.ceil(
                SceneGeometry.maxScrollY(mounted.listViewport) / (double) lineHeight);
        Assert.assertTrue("前提:内容确实溢出视口", ceiling > 2);
        Assert.assertEquals("布局完成后上限必须已回填进权威",
                ceiling, controller.history().getMaxScrollOffset());

        controller.history().scrollBy(ceiling * 5);
        Assert.assertEquals("超滚必须被行域上限截住,不得累加死值",
                ceiling, controller.history().getScroll());
        controller.notifyDataChanged();
        controller.tick(T0);
        mounted.rt.flush();
        controller.tick(T0 + 240L);
        mounted.rt.flush();
        Assert.assertEquals("上限处正好看到最旧一行(上限偏小会留死区)", 0,
                mounted.listViewport.getScrollOffsetY());

        // 关键体感:往回滚第一格必须真的动,不必先消费看不见的死值
        controller.history().scrollBy(-1);
        controller.notifyDataChanged();
        controller.tick(T0 + 240L);
        mounted.rt.flush();
        controller.tick(T0 + 480L);
        mounted.rt.flush();
        Assert.assertTrue("回滚一格必须立刻产生位移",
                mounted.listViewport.getScrollOffsetY() > 0);
    }

    // ==================== V7 方案甲:行域唯一权威 + 假想几何投影 + 真实几何 clamp ====================

    /** 挂载句柄(控制器/运行时/消息视口,三测试共用)。 */
    private static final class Mounted {
        final ChatSceneController controller;
        final SceneRuntime rt;
        final SceneNode listViewport;

        Mounted(ChatSceneController controller, SceneRuntime rt, SceneNode listViewport) {
            this.controller = controller;
            this.rt = rt;
            this.listViewport = listViewport;
        }
    }

    /**
     * V7 混合内容装载:玩家组(16px 组头 + 18px 正文行)+ 系统消息(16px 正文行,无组头),
     * 交替发送者强制各自成组 → 真实布局几何(系统 16 / 组头 16 / 正文 18)非 18px 整倍。
     *
     * @param controller  已 setHostViewport/setChatOpen 的控制器
     * @param playerGroups 玩家消息条数(交替发送者,每条一组)
     * @param systemCount  系统消息条数(每条一组,无组头)
     * @return 挂载句柄(已 layout + flush,scroll=0 贴底)
     */
    private static Mounted mountMixed(ChatSceneController controller, int playerGroups,
            int systemCount) {
        long t = T0;
        int id = 1;
        String[] senders = { "Bob", "Eve", "Carl" };
        for (int i = 0; i < playerGroups; i++) {
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("<" + senders[i % senders.length] + "> message " + i),
                    id++, t++));
        }
        for (int i = 0; i < systemCount; i++) {
            controller.history().append(new ChatLineRecord(
                    new ChatComponentText("Server announces event " + i), id++, t++));
        }
        controller.notifyDataChanged();
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        Map<SceneNode, ChatLineRecord> registry = new java.util.IdentityHashMap<SceneNode, ChatLineRecord>();
        ChatContainer.Result result = ChatContainer.mount(rt, controller, registry, "");
        result.setViewport(400, 300);
        rt.flush();
        layoutEngine.layout(result.root(), new Constraints(400, 300));
        rt.__setLayoutDoneEpoch(layoutEngine.layoutEpoch());
        rt.flush();
        // 树结构:containerNode = [listRow, divider, barRow];listRow = [listViewport, scrollbarColumn]
        SceneNode listViewport = result.root().__getChildren().get(0).__getChildren().get(0);
        return new Mounted(controller, rt, listViewport);
    }

    /**
     * V7 方案甲契约①:底部恒等在真实几何非 18px 整倍(系统 16/组头 16/正文 18 混排)时
     * 依然成立;18px 行量子换算精确;距底 ≤2 行属贴底跟随语义(行域目标归底)。
     *
     * <p>chatPx(行×18px 假想投影)与 maxScrollY(真实内容几何)是两个域,viewportScrollPx
     * 双向 clamp(chatPx ∈ [0, maxScrollY])保证贴底恒等(chatPx=0 → 视口偏移 = maxScrollY)
     * 不依赖内容是否 18px 整倍,且顶部超滚不产生负偏移。</p>
     */
    @Test
    public void viewportOffsetKeepsBottomIdentityWithMixedLineHeights() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.setChatOpen(true);
        Mounted m = mountMixed(controller, 6, 3);
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        int maxScroll = SceneGeometry.maxScrollY(m.listViewport);
        Assert.assertTrue("混合内容应溢出视口(maxScrollY > 0)", maxScroll > 0);
        Assert.assertTrue("混合行高(系统 16/组头 16/正文 18)真实几何应非 18px 整倍",
                maxScroll % lineHeight != 0);

        // ① 底部恒等:scroll=0(贴底)→ 视口偏移 == maxScrollY,混合行高下同样成立
        Assert.assertEquals("scroll=0 贴底:视口偏移 = maxScrollY(底部恒等)", maxScroll,
                m.listViewport.getScrollOffsetY());

        // ② 行量子:scrollBy(+3)(> 距底 2 行阈值,不触发贴底跟随)→ 恰好 3×18px
        controller.history().scrollBy(3);
        controller.notifyDataChanged();
        controller.tick(T0);
        m.rt.flush();
        controller.tick(T0 + 120L);
        m.rt.flush();
        Assert.assertEquals("scrollBy(+3):视口偏移 = maxScrollY - 3 行(18px 行量子,非整倍几何下仍精确)",
                maxScroll - 3 * lineHeight, m.listViewport.getScrollOffsetY());

        // ③ 回底:滚动恒等恢复(贴底)
        controller.history().scrollBy(-3);
        controller.notifyDataChanged();
        controller.tick(T0 + 120L);
        m.rt.flush();
        controller.tick(T0 + 240L);
        m.rt.flush();
        Assert.assertEquals("scrollBy(-3) 回底:视口偏移恢复 maxScrollY", maxScroll,
                m.listViewport.getScrollOffsetY());

        // ④ 距底 ≤2 行 = 贴底跟随(行域目标归底,既有设计语义):scrollBy(1) 仍钉在底部
        controller.history().scrollBy(1);
        controller.notifyDataChanged();
        controller.tick(T0 + 240L);
        m.rt.flush();
        controller.tick(T0 + 360L);
        m.rt.flush();
        Assert.assertEquals("距底 ≤2 行:贴底跟随钉在底部(行域目标归 0)", maxScroll,
                m.listViewport.getScrollOffsetY());

        // ⑤ 顶部超滚 clamp:无下溢
        controller.history().scrollBy(1000);
        controller.notifyDataChanged();
        controller.tick(T0 + 360L);
        m.rt.flush();
        controller.tick(T0 + 480L);
        m.rt.flush();
        Assert.assertEquals("超滚 clamp 到顶部 0", 0, m.listViewport.getScrollOffsetY());
        Assert.assertTrue("视口偏移恒 >= 0(无下溢)", m.listViewport.getScrollOffsetY() >= 0);
    }

    /**
     * V7 方案甲契约②:拖动折算 round(chatPx/行高) 与投影 round(display × 行高) 互逆——
     * 拖动目标(视口偏移 scene px)经行域回写再投影后,线域可达点(chatPx = k×行高)逐像素
     * 无损;非整倍偏移误差 ≤ 17px(方案甲顶部死区/取整上界,实测最坏 ≤ 9);距底 ≤2 行
     * 样本落在贴底跟随语义(钉在底部)。
     *
     * <p>折算契约与 ChatContainer.setScrollOffset 同式:chatPx = maxScrollY - offsetPx →
     * round(chatPx/行高) → history.scrollBy 行域回写 + notifyDataChanged(onDragStart snapTo
     * 直通 → display 恒整数);投影链 = scrollOffsetPx(round(display × 行高)) →
     * viewportScrollPx(maxScrollY - clamp(chatPx))。断言「喂 offset 后视口偏移」与拖动目标
     * 的偏差。</p>
     */
    @Test
    public void dragRoundTripIsLosslessInLineDomain() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.setChatOpen(true);
        Mounted m = mountMixed(controller, 12, 6);
        int maxScroll = SceneGeometry.maxScrollY(m.listViewport);
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        // 贴底跟随阈值(行):与 ChatSceneController.nearBottomLineThreshold 同口径 ceil(36/行高)
        int followThreshold = (int) Math.ceil(36.0D / lineHeight);
        Assert.assertTrue("混合内容应溢出视口", maxScroll > 0);
        Assert.assertTrue("真实几何应非 18px 整倍", maxScroll % lineHeight != 0);

        long now = T0;
        // 系列 A:线域可达偏移(chatPx = k×行高 → round 精确)→ 视口偏移与拖动目标逐像素一致
        int exactCount = 0;
        int followA = 0;
        for (int k = 0; k * lineHeight <= maxScroll; k++) {
            int offsetPx = maxScroll - k * lineHeight;
            int actual = applyDragConversion(m, offsetPx, now);
            now += 120L;
            if (k <= followThreshold) {
                Assert.assertEquals("距底 ≤2 行:贴底跟随钉在底部", maxScroll, actual);
                followA++;
            } else {
                Assert.assertEquals("线域可达偏移无损:视口偏移 == 拖动目标 offset=" + offsetPx,
                        offsetPx, actual);
                exactCount++;
            }
        }
        Assert.assertTrue("线域精确样本应覆盖非贴底区", exactCount >= 3);
        Assert.assertTrue("线域样本应覆盖贴底跟随区", followA >= 1);

        // 系列 B:非 18px 整倍偏移(步长 7 扫全距)→ 折算误差 ≤ 17px(方案甲死区上界)
        int boundSamples = 0;
        int followB = 0;
        for (int offsetPx = 0; offsetPx <= maxScroll; offsetPx += 7) {
            int chatPx = maxScroll - offsetPx;
            int targetLines = (int) Math.round(chatPx / (double) lineHeight);
            int actual = applyDragConversion(m, offsetPx, now);
            now += 120L;
            if (targetLines <= followThreshold) {
                Assert.assertEquals("贴底区:钉在底部", maxScroll, actual);
                followB++;
            } else {
                int err = Math.abs(actual - offsetPx);
                Assert.assertTrue("非整倍折算误差 ≤17(方案甲死区上界): offset=" + offsetPx
                        + " actual=" + actual + " err=" + err, err <= 17);
                boundSamples++;
            }
        }
        Assert.assertTrue("非整倍样本应覆盖主体区", boundSamples >= 5);
        Assert.assertTrue("非整倍样本应覆盖贴底区", followB >= 1);
    }

    /** 模拟拖动回调折算(scene px → 行域 → 投影),与 ChatContainer setScrollOffset/onDragStart 同式。 */
    private static int applyDragConversion(Mounted m, int offsetPx, long nowMillis) {
        int maxScroll = SceneGeometry.maxScrollY(m.listViewport);
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        int chatPx = Math.max(0, maxScroll - offsetPx);
        int targetLines = (int) Math.round(chatPx / (double) lineHeight);
        // onDragStart:拖动接管 → snapTo 直通(display 恒等于目标行,投影 round(整数×18) 无损)
        m.controller.smoothScroll().snapTo(targetLines);
        // setScrollOffset:px → 行域回写(与滚轮/回底共享 history.scrollBy 通道)
        int current = m.controller.history().getScroll();
        m.controller.history().scrollBy(targetLines - current);
        m.controller.notifyDataChanged();
        m.controller.tick(nowMillis);
        m.rt.flush();
        m.controller.tick(nowMillis + 120L);
        m.rt.flush();
        return m.listViewport.getScrollOffsetY();
    }

    /**
     * V7 方案甲契约③:巨大 scrollBy(远超内容)后视口偏移恒 >= 0 且 clamp 到顶部 0(无下溢)。
     */
    @Test
    public void clampedTopDoesNotUnderflow() {
        ChatSceneController controller = controller();
        controller.setHostViewport(400, 300);
        controller.setChatOpen(true);
        Mounted m = mountMixed(controller, 6, 3);
        Assert.assertTrue("混合内容应溢出视口", SceneGeometry.maxScrollY(m.listViewport) > 0);
        controller.history().scrollBy(10000);
        controller.notifyDataChanged();
        controller.tick(T0);
        m.rt.flush();
        controller.tick(T0 + 120L);
        m.rt.flush();
        int offset = m.listViewport.getScrollOffsetY();
        Assert.assertTrue("巨大 scrollBy 后视口偏移不得为负(无下溢):" + offset, offset >= 0);
        Assert.assertEquals("巨大 scrollBy 后 clamp 到顶部 0", 0, offset);
    }

    // ==================== G17/Container：默认液态玻璃口径（主题兜底 + 聊天设置局部覆盖） ====================

    /** 挂一条消息的容器并 flush（玻璃配方用例共用装载）。 */
    private static ChatContainer.Result mountGlassedContainer(SceneRuntime rt,
            ChatSceneController controller) {
        controller.setHostViewport(400, 300);
        controller.history().append(new ChatLineRecord(new ChatComponentText("<Bob> hi"), 1, 0L));
        controller.notifyDataChanged();
        ChatContainer.Result result = ChatContainer.mount(rt, controller,
                new java.util.IdentityHashMap<SceneNode, ChatLineRecord>(), "");
        rt.flush();
        return result;
    }

    /** 节点子树 PaintPlan 内 BACKDROP 命令数（每颗采样滤镜的表面恰好贡献 1 条）。 */
    private static int backdropCount(ScenePaintEngine engine, SceneNode node) {
        PaintPlan plan = engine.paint(node).getPlan();
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    private static int firstIndexOfType(PaintPlan plan, PaintCommandType type) {
        java.util.List<PaintCommand> commands = plan.getCommands();
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 用例①+③（主题侧）：聊天设置未覆盖的配方字段 = 所属主题 PANEL 角色档（通用主题兜底，
     * chat3→theme 方向 import）；主题切换只重派生——节点身份不变、effect 不增长、面板不重建，
     * 且聊天设置覆盖字段（底色/滤镜/圆角）不随主题改写（局部覆盖第一优先级）。
     *
     * <p>G20 收敛：外框改为「静态表面」表达——过渡时长与三个非 idle 状态档不再扮演主题兜底字段
     * （迁移前由「绕开绑定器、只写 idle」隐式表达，现由四态同值 + 零过渡显式表达），本用例改为
     * 断言其恒定值；其余主题兜底字段（聚焦缘色 / 内容抬升 / 禁用透明度）断言语义不变。</p>
     */
    @Test
    public void themeSuppliesFieldsChatSettingsDoNotCover() {
        boolean savedGlass = ChatMarkdownSettings.isGlassEnabled();
        try {
            ChatMarkdownSettings.setGlassEnabled(true);
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassLight());
            SceneThemes.install(rt, pageTheme);
            ChatContainer.Result result = mountGlassedContainer(rt, controller());

            SceneSurfaceStyle recipe = result.surfaceRecipe().get();
            SceneSurfaceStyle lightPanel = SceneTheme.liquidGlassLight().surface(SceneTheme.Role.PANEL);
            Assert.assertNotEquals("测试前提：深浅主题 PANEL 缘色档不同",
                    SceneTheme.liquidGlassDark().surface(SceneTheme.Role.PANEL).getFocusEdge(),
                    lightPanel.getFocusEdge());
            // 主题兜底字段：聚焦缘色 / 内容抬升 / 禁用透明度（配方仍以 PANEL 档兜底）
            Assert.assertEquals("聚焦缘色随主题 PANEL 档",
                    lightPanel.getFocusEdge(), recipe.getFocusEdge());
            Assert.assertEquals("contentLift 随主题 PANEL 档",
                    lightPanel.getContentLift(), recipe.getContentLift(), 0.0F);
            Assert.assertEquals("disabledOpacity 随主题 PANEL 档",
                    lightPanel.getDisabledOpacity(), recipe.getDisabledOpacity(), 0.0F);
            // G20/P-05 静态表面：容器不可命中（hitTestable=false），交互态本就不激活；四态同值 +
            // 零过渡使配方输出恒定，与迁移前「绕开绑定器、只写 idle」的普通绘制通道逐值等价。
            Assert.assertEquals("静态表面：过渡时长恒 0（等价迁移前普通绘制通道）",
                    0, recipe.getTransitionMillis());
            Assert.assertEquals("静态表面：悬停档 = idle 档", recipe.getIdle(), recipe.getHovered());
            Assert.assertEquals("静态表面：按下档 = idle 档", recipe.getIdle(), recipe.getPressed());
            Assert.assertEquals("静态表面：禁用档 = idle 档", recipe.getIdle(), recipe.getDisabled());
            Assert.assertTrue("P-05：浮雕豁免位在配方上显式声明（不再是绕开绑定器的隐式例外）",
                    recipe.isReliefDisabled());
            // 聊天设置覆盖字段：与主题档可辨（浅色 PANEL 圆角=16，聊天设置=20）
            Assert.assertEquals("圆角 = 聊天设置局部覆盖",
                    ChatMarkdownSettings.getContainerCornerRadius(), recipe.getCornerRadius());
            Assert.assertNotEquals("局部覆盖优先于主题圆角",
                    lightPanel.getCornerRadius(), recipe.getCornerRadius());

            // 主题更新：只重派生
            SceneNode container = result.root();
            SceneNode listRow = container.__getChildren().get(0);
            int tintBefore = container.getBackgroundColor();
            int radiusBefore = container.getCornerRadius();
            UiBackdrop backdropBefore = container.getBackdrop();
            int effectsBefore = ReactiveTestProbe.registeredEffectCount();
            pageTheme.set(SceneTheme.liquidGlassDark());
            rt.flush();

            SceneSurfaceStyle darkPanel = SceneTheme.liquidGlassDark().surface(SceneTheme.Role.PANEL);
            Assert.assertEquals("主题字段随主题重派生",
                    darkPanel.getFocusEdge(), result.surfaceRecipe().get().getFocusEdge());
            SceneSurfaceStyle rederived = result.surfaceRecipe().get();
            Assert.assertTrue("静态表面 + 浮雕豁免在主题重派生后保持", rederived.isReliefDisabled());
            Assert.assertEquals("静态表面：重派生后悬停档仍 = idle 档",
                    rederived.getIdle(), rederived.getHovered());
            Assert.assertSame("主题切换不重建外框节点", container, result.root());
            Assert.assertSame("主题切换不重建滚动区行", listRow, result.root().__getChildren().get(0));
            Assert.assertEquals("聊天覆盖字段（底色）不随主题变", tintBefore, container.getBackgroundColor());
            Assert.assertEquals("聊天覆盖字段（圆角）不随主题变", radiusBefore, container.getCornerRadius());
            Assert.assertEquals("聊天覆盖字段（滤镜）不随主题变", backdropBefore, container.getBackdrop());
            Assert.assertEquals("主题切换不新增订阅",
                    effectsBefore, ReactiveTestProbe.registeredEffectCount());
            result.dispose();
            rt.dispose();
        } finally {
            ChatMarkdownSettings.setGlassEnabled(savedGlass);
        }
    }

    /**
     * 用例②：既有聊天玻璃设置 = 局部覆盖（第一优先级），开关两端可辨——开 = 保持既有玻璃观感
     * （材质字段逐项取设置值而非主题档），关 = 实色令牌逃生舱（配置语义不变，不落主题无滤镜档）。
     */
    @Test
    public void chatGlassSettingsOverrideThemeAtBothSwitchEnds() {
        boolean savedGlass = ChatMarkdownSettings.isGlassEnabled();
        try {
            ChatMarkdownSettings.setGlassEnabled(true);
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            // 装一个与聊天设置材质档完全不同的主题：局部覆盖若失效（优先级写反）当场可辨
            SceneThemes.install(rt, Signal.create(SceneTheme.liquidGlassLight()));
            ChatContainer.Result result = mountGlassedContainer(rt, controller());
            SceneNode container = result.root();

            int expectedOn = (ChatMarkdownSettings.getContainerBgArgb() & 0x00FFFFFF)
                    | (ChatMarkdownSettings.getGlassContainerAlpha() << 24);
            UiBackdrop backdrop = container.getBackdrop();
            Assert.assertNotNull("设置开：外框必须带玻璃", backdrop);
            Assert.assertTrue("设置开：聊天玻璃仍是 Liquid Glass 家族", backdrop.getEffect().isLiquid());
            Assert.assertSame("材质 = 聊天设置 DARK_THIN 系（覆盖主题 THIN 档）",
                    UiGlassMaterial.DARK_THIN, backdrop.getEffect().getMaterial());
            Assert.assertEquals("模糊半径 = 聊天设置（非主题 PANEL 的 10）",
                    ChatMarkdownSettings.getGlassBlurRadiusPx(), backdrop.getBlurRadius());
            Assert.assertEquals("透镜强度 = 聊天设置（非主题 PANEL 的 0.6）",
                    ChatMarkdownSettings.getGlassLensStrength(),
                    backdrop.getEffect().getLensStrength(), 0.0001F);
            Assert.assertEquals("底色 = 令牌 RGB × 玻璃 alpha 档（既有观感逐项保持）",
                    expectedOn, container.getBackgroundColor());
            Assert.assertEquals("描边色 = 聊天设置令牌",
                    ChatMarkdownSettings.getContainerBorderArgb(), container.getBorderColor());
            Assert.assertEquals("描边宽 = 既有 1px", 1, container.getBorderWidth());
            Assert.assertEquals("圆角 = 聊天设置（20，覆盖主题的 16）",
                    ChatMarkdownSettings.getContainerCornerRadius(), container.getCornerRadius());
            Assert.assertEquals("外框保持普通绘制（不装浮雕 = 大面板暗边不回归）",
                    -1.0F, container.__getSurfaceElevation(), 0.0F);

            // 稳态帧（设置无变化）：唯一写入者纪律——竞争静态写入者会把半透明档顶回实心令牌
            rt.__tickFrame(9L);
            rt.flush();
            Assert.assertEquals("稳态帧后底色仍为玻璃半透明档（无竞争写入者回写）",
                    expectedOn, container.getBackgroundColor());
            Assert.assertNotNull("稳态帧后滤镜声明仍在", container.getBackdrop());

            // 开关另一端：关 = 实色令牌逃生舱（既有断言语义不变）
            ChatMarkdownSettings.setGlassEnabled(false);
            rt.__tickFrame(1L);
            rt.flush();
            Assert.assertNull("关：不得残留 backdrop 声明", container.getBackdrop());
            Assert.assertEquals("关：外框回实心令牌底色",
                    ChatMarkdownSettings.getContainerBgArgb(), container.getBackgroundColor());
            Assert.assertEquals("关：描边保持令牌",
                    ChatMarkdownSettings.getContainerBorderArgb(), container.getBorderColor());
            Assert.assertEquals("关：圆角保持设置",
                    ChatMarkdownSettings.getContainerCornerRadius(), container.getCornerRadius());

            // 再开：重派生回玻璃档（全程不重建面板）
            ChatMarkdownSettings.setGlassEnabled(true);
            rt.__tickFrame(2L);
            rt.flush();
            Assert.assertNotNull("再开：玻璃随设置变化重派生", container.getBackdrop());
            Assert.assertEquals(expectedOn, container.getBackgroundColor());
            Assert.assertSame("开关全程不重建外框节点", container, result.root());
            result.dispose();
            rt.dispose();
        } finally {
            ChatMarkdownSettings.setGlassEnabled(savedGlass);
        }
    }

    /**
     * 用例③（设置侧）：自定义玻璃参数变更只重派生——模糊/强度/容器 alpha 逐项可辨地落到外框，
     * 节点身份不变、effect 不增长、面板不重建；参数还原后同样只重派生。
     */
    @Test
    public void settingsChangeReDerivesPanelSurfaceWithoutRebuild() {
        boolean savedGlass = ChatMarkdownSettings.isGlassEnabled();
        int savedBlur = ChatMarkdownSettings.getGlassBlurRadiusPx();
        float savedLens = ChatMarkdownSettings.getGlassLensStrength();
        int savedAlpha = ChatMarkdownSettings.getGlassContainerAlpha();
        try {
            ChatMarkdownSettings.setGlassEnabled(true);
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            ChatContainer.Result result = mountGlassedContainer(rt, controller());
            SceneNode container = result.root();
            SceneNode listRow = container.__getChildren().get(0);
            SceneNode listViewport = listRow.__getChildren().get(0);
            Assert.assertEquals("默认档模糊 = 设置值", savedBlur, container.getBackdrop().getBlurRadius());

            int effectsBefore = ReactiveTestProbe.registeredEffectCount();
            ChatMarkdownSettings.setGlassBlurRadiusPx(20);
            ChatMarkdownSettings.setGlassLensStrength(0.9F);
            ChatMarkdownSettings.setGlassContainerAlpha(0x30);
            rt.__tickFrame(1L);
            rt.flush();

            Assert.assertEquals("自定义模糊逐项落到节点", 20, container.getBackdrop().getBlurRadius());
            Assert.assertEquals("自定义透镜强度逐项落到节点",
                    0.9F, container.getBackdrop().getEffect().getLensStrength(), 0.0001F);
            Assert.assertEquals("自定义玻璃 alpha 逐项落到节点",
                    0x30, (container.getBackgroundColor() >>> 24) & 0xFF);
            Assert.assertEquals("RGB 通道仍来自容器底色令牌",
                    ChatMarkdownSettings.getContainerBgArgb() & 0x00FFFFFF,
                    container.getBackgroundColor() & 0x00FFFFFF);
            Assert.assertSame("设置变更不重建外框", container, result.root());
            Assert.assertSame("设置变更不重建滚动区行", listRow, result.root().__getChildren().get(0));
            Assert.assertSame("设置变更不重建消息视口", listViewport,
                    result.root().__getChildren().get(0).__getChildren().get(0));
            Assert.assertEquals("设置变更不新增订阅",
                    effectsBefore, ReactiveTestProbe.registeredEffectCount());

            // 还原参数：同样只重派生，且 effect 数仍不增长
            ChatMarkdownSettings.setGlassBlurRadiusPx(savedBlur);
            ChatMarkdownSettings.setGlassLensStrength(savedLens);
            ChatMarkdownSettings.setGlassContainerAlpha(savedAlpha);
            rt.__tickFrame(2L);
            rt.flush();
            Assert.assertEquals(savedBlur, container.getBackdrop().getBlurRadius());
            Assert.assertEquals(savedAlpha, (container.getBackgroundColor() >>> 24) & 0xFF);
            Assert.assertEquals("还原参数仍不新增订阅",
                    effectsBefore, ReactiveTestProbe.registeredEffectCount());
            result.dispose();
            rt.dispose();
        } finally {
            ChatMarkdownSettings.setGlassEnabled(savedGlass);
            ChatMarkdownSettings.setGlassBlurRadiusPx(savedBlur);
            ChatMarkdownSettings.setGlassLensStrength(savedLens);
            ChatMarkdownSettings.setGlassContainerAlpha(savedAlpha);
        }
    }

    /**
     * 用例④：面板恰一颗滤镜（BACKDROP 在节点底色之前、底色半透明不遮玻璃、BORDER 普通绘制
     * 通道保持——未切浮雕 ROUNDED_BAND 档），容器自身 chrome 子项零重复采样。
     */
    @Test
    public void panelCarriesExactlyOneFilterAndChromeChildrenSampleNothing() {
        boolean savedGlass = ChatMarkdownSettings.isGlassEnabled();
        try {
            ChatMarkdownSettings.setGlassEnabled(true);
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
            ChatSceneController controller = controller();
            ChatContainer.Result result = mountGlassedContainer(rt, controller);
            result.setViewport(400, 300);
            rt.flush();
            SceneLayoutEngine layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
            layoutEngine.layout(result.root(), new Constraints(400, 300));
            ScenePaintEngine engine = new ScenePaintEngine(new FixedTextMeasurer(8, 16));

            SceneNode container = result.root();
            SceneNode listRow = container.__getChildren().get(0);
            SceneNode listViewport = listRow.__getChildren().get(0);
            SceneNode scrollbarColumn = listRow.__getChildren().get(1);
            SceneNode divider = container.__getChildren().get(1);
            SceneNode barRow = container.__getChildren().get(2);

            // 面板自身恰一颗：其 fragment 最先落图 = BACKDROP → BACKGROUND(半透明) → BORDER
            Assert.assertNotNull("玻璃开：外框声明滤镜", container.getBackdrop());
            PaintPlan plan = engine.paint(container).getPlan();
            int backdropAt = firstIndexOfType(plan, PaintCommandType.BACKDROP);
            int backgroundAt = firstIndexOfType(plan, PaintCommandType.BACKGROUND);
            int borderAt = firstIndexOfType(plan, PaintCommandType.BORDER);
            Assert.assertTrue("首个 BACKDROP 存在", backdropAt >= 0);
            Assert.assertTrue("BACKDROP 之后才发节点底色（半透明底叠玻璃之上，无不透明底盖）",
                    backdropAt < backgroundAt);
            Assert.assertTrue("外框底色保持半透明（玻璃透得出来）",
                    ((plan.getCommands().get(backgroundAt).getColor() >>> 24) & 0xFF) < 255);
            Assert.assertTrue("BORDER（普通绘制描边）仍在（未切浮雕档）", borderAt >= 0);
            Assert.assertEquals("描边命令色 = 聊天设置令牌",
                    ChatMarkdownSettings.getContainerBorderArgb(),
                    plan.getCommands().get(borderAt).getColor());
            // 浮雕检查只钉容器外框自身 fragment（父节点自身命令是先序前缀，止于其 BORDER）；
            // 后代之中的输入条 chrome 在 G17/Input 迁移后合法走浮雕通道（台账裁决②），不在此约束内。
            for (int i = 0; i <= borderAt; i++) {
                Assert.assertNotSame("容器外框自身无 ROUNDED_BAND 浮雕命令（大面板暗边不回归）",
                        PaintCommandType.ROUNDED_BAND, plan.getCommands().get(i).getType());
            }
            Assert.assertEquals("外框 surfaceElevation 保持未绑定默认 -1（普通绘制）",
                    -1.0F, container.__getSurfaceElevation(), 0.0F);

            // 容器 chrome 子项零重复采样：不声明、不产 BACKDROP 命令
            Assert.assertNull(listRow.getBackdrop());
            Assert.assertNull(listViewport.getBackdrop());
            Assert.assertNull(scrollbarColumn.getBackdrop());
            Assert.assertNull(divider.getBackdrop());
            Assert.assertNull(barRow.getBackdrop());
            Assert.assertEquals("滚动条列零采样", 0, backdropCount(engine, scrollbarColumn));
            Assert.assertEquals("分隔线零采样", 0, backdropCount(engine, divider));
            Assert.assertEquals("滚动区行内 BACKDROP 恰为气泡自身既有玻璃（容器不再加层）",
                    1, backdropCount(engine, listRow));
            result.dispose();
            rt.dispose();
        } finally {
            ChatMarkdownSettings.setGlassEnabled(savedGlass);
        }
    }

    /**
     * 卸载回收（配方桥三件套）：
     * ① 配方 Computed 的 recompute 单元随 {@code Result.dispose()} 注销——卸载后聊天设置再变
     *    （由第二个 runtime 的帧观察发布新快照），旧配方读数必须冻结在卸载时刻；
     * ② 表面写入绑定注销——卸载后旧外框不被鬼写入（模糊仍是卸载前的设置档，不跟第二轮）；
     * ③ 每轮「挂载→卸载」的 effect 水位增量恒定——本实例不新增 per-cycle 泄漏（聊天栈其他
     *    组件的既有水位不在本实例义务内，这里只钉「不再增长」）。
     */
    @Test
    public void disposeReclaimsRecipeAndSettingsBindings() {
        boolean savedGlass = ChatMarkdownSettings.isGlassEnabled();
        int savedBlur = ChatMarkdownSettings.getGlassBlurRadiusPx();
        try {
            ChatMarkdownSettings.setGlassEnabled(true);
            int before = ReactiveTestProbe.registeredEffectCount();
            SceneRuntime rt1 = new SceneRuntime(new FixedTextMeasurer(8, 16));
            ChatContainer.Result result1 = mountGlassedContainer(rt1, controller());
            Assert.assertTrue("挂载应注册响应式绑定（含配方与设置观察）",
                    ReactiveTestProbe.registeredEffectCount() > before);
            SceneNode container1 = result1.root();
            SceneSurfaceStyle recipeBeforeDispose = result1.surfaceRecipe().get();
            Assert.assertEquals("卸载前配方 = 当前设置模糊档", savedBlur,
                    recipeBeforeDispose.getBackdrop().getBlurRadius());
            result1.dispose();
            rt1.dispose();
            int afterFirstCycle = ReactiveTestProbe.registeredEffectCount();

            // 第二轮：先改设置再挂新实例——新观察器会把新快照发布进共享 Signal；
            // 若旧配方/旧绑定未注销，就会在旧读数与旧节点上复活。
            ChatMarkdownSettings.setGlassBlurRadiusPx(20);
            SceneRuntime rt2 = new SceneRuntime(new FixedTextMeasurer(8, 16));
            ChatContainer.Result result2 = mountGlassedContainer(rt2, controller());
            rt2.__tickFrame(1L);
            rt2.flush();
            Assert.assertEquals("新实例按新设置派生", 20,
                    result2.root().getBackdrop().getBlurRadius());
            Assert.assertEquals("卸载后旧配方冻结（recompute 单元已注销）",
                    recipeBeforeDispose, result1.surfaceRecipe().get());
            Assert.assertEquals("卸载后旧外框不被鬼写入", savedBlur,
                    container1.getBackdrop().getBlurRadius());
            result2.dispose();
            rt2.dispose();
            Assert.assertEquals("两轮挂载-卸载水位增量恒定（本实例不新增 per-cycle 泄漏）",
                    afterFirstCycle - before,
                    ReactiveTestProbe.registeredEffectCount() - afterFirstCycle);
        } finally {
            ChatMarkdownSettings.setGlassEnabled(savedGlass);
            ChatMarkdownSettings.setGlassBlurRadiusPx(savedBlur);
        }
    }
}
