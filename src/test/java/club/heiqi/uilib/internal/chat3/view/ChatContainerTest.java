package club.heiqi.uilib.internal.chat3.view;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * ChatContainer 契约测试(K3 缺陷 F6②):输入条区四周 8px 内边距(设计稿 §2.3/§6.2),
 * 输入框高 24px 钉在输入条区 40px 内,divider 到输入框顶恰好 8px。
 */
public class ChatContainerTest {

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

        // containerNode 子顺序 = [listRow, divider, barRow](挂载顺序 + insertBefore)
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
}
