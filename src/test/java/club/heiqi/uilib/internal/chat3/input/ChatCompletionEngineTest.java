package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tab 补全状态机测试(T2):idle → awaiting → cycling 三态、两段式响应、
 * 循环正反、候选打印、stale 快照守卫、本地无网络兜底(R1 不本地先行)、
 * 客户端命令本地候选(方案 A:请求缓存 → 本地在前合并去重 → 无网络兜底)、
 * 单候选不打印(原版 GuiChat:244)、非 Tab 键清循环态(原版 GuiChat:91)。
 */
public class ChatCompletionEngineTest {

    /** 记录型假宿主:完全 headless,不发真实网络/不触 Minecraft。 */
    private static final class FakeHost implements ChatCompletionEngine.Host {
        String text = "";
        boolean network = true;
        final List<String> sentRequests = new ArrayList<String>();
        final List<String> localNames = new ArrayList<String>();
        final List<String> localCommands = new ArrayList<String>();
        final List<List<String>> printed = new ArrayList<List<String>>();
        int localCommandCalls;

        @Override
        public String currentText() {
            return text;
        }

        @Override
        public void commit(String nextText) {
            text = nextText;
        }

        @Override
        public boolean networkAvailable() {
            return network;
        }

        @Override
        public void sendRequest(String t) {
            sentRequests.add(t);
        }

        @Override
        public List<String> localPlayerNames() {
            return localNames;
        }

        @Override
        public List<String> localCommandCompletions(String text) {
            localCommandCalls++;
            return new ArrayList<String>(localCommands);
        }

        @Override
        public void printCandidates(List<String> candidates) {
            printed.add(new ArrayList<String>(candidates));
        }
    }

    private static ChatCompletionEngine engine(FakeHost host) {
        return new ChatCompletionEngine(host);
    }

    @Test
    public void firstTabSendsRequestAndWaits() {
        FakeHost host = new FakeHost();
        host.text = "/tp ste";
        ChatCompletionEngine engine = engine(host);

        engine.onTab(1);
        Assert.assertEquals("首 Tab 发 C14 光标前全文(含 /)", Arrays.asList("/tp ste"), host.sentRequests);
        Assert.assertEquals("等待态不修改文本", "/tp ste", host.text);

        engine.onTab(1); // 等待态重复 Tab:原版语义重发 C14(I4:FIFO 追加请求,不覆盖既有请求快照)
        Assert.assertEquals(2, host.sentRequests.size());
        Assert.assertEquals(Arrays.asList("/tp ste", "/tp ste"), host.sentRequests);
        Assert.assertEquals("等待态不修改文本", "/tp ste", host.text);
    }

    @Test
    public void responseExtendsToCommonPrefixWithoutPrinting() {
        FakeHost host = new FakeHost();
        host.text = "/tp s";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);

        engine.onResponse(new String[] { "steve", "steve2", "stella" });
        Assert.assertEquals("两段式一:扩为公共前缀,保留 /", "/tp ste", host.text);
        Assert.assertTrue("不弹列表", host.printed.isEmpty());
    }

    @Test
    public void responseEntersCyclingWhenAtCommonPrefix() {
        FakeHost host = new FakeHost();
        host.text = "/tp ste";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);

        // 第一段:词 "ste" 短于公共前缀 "steve" → 扩为公共前缀、不弹列表、回 idle
        engine.onResponse(new String[] { "steve", "steve2" });
        Assert.assertEquals("扩展为公共前缀", "/tp steve", host.text);
        Assert.assertTrue("扩展段不打印", host.printed.isEmpty());

        // 再次 Tab 重新请求;词已处于公共前缀 → 进入循环态
        engine.onTab(1);
        engine.onResponse(new String[] { "steve", "steve2" });
        Assert.assertEquals("进入循环态:首候选入框(与公共前缀相同)", "/tp steve", host.text);
        Assert.assertEquals("候选列表打印一次", 1, host.printed.size());
        Assert.assertEquals(Arrays.asList("steve", "steve2"), host.printed.get(0));

        engine.onTab(1);
        Assert.assertEquals("循环 Tab 依次替换", "/tp steve2", host.text);
        engine.onTab(1);
        Assert.assertEquals("到尾回卷 0", "/tp steve", host.text);
        Assert.assertEquals("每次循环覆盖打印(同 id 覆盖不刷屏)", 3, host.printed.size());
    }

    @Test
    public void shiftTabCyclesBackward() {
        FakeHost host = new FakeHost();
        host.text = "st";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        engine.onResponse(new String[] { "steve", "stella", "stone" });
        Assert.assertEquals("st" + "eve" + " ", "steve", host.text);

        engine.onTab(-1); // Shift+Tab:0 → 2
        Assert.assertEquals("stone", host.text);
        engine.onTab(-1);
        Assert.assertEquals("stella", host.text);
        engine.onTab(-1);
        Assert.assertEquals("反向到首回卷末尾", "steve", host.text);
    }

    @Test
    public void staleResponseIsDroppedWhenTextChanged() {
        FakeHost host = new FakeHost();
        host.text = "/tp ste";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        Assert.assertEquals(1, host.sentRequests.size());

        engine.onTextEdited(); // 用户继续输入
        host.text = "/tp stev";
        engine.onResponse(new String[] { "steve", "stella" });
        Assert.assertEquals("快照不匹配/非等待态:响应丢弃", "/tp stev", host.text);
        Assert.assertTrue(host.printed.isEmpty());
    }

    @Test
    public void responseDroppedWhenNotAwaiting() {
        FakeHost host = new FakeHost();
        host.text = "/tp ste";
        ChatCompletionEngine engine = engine(host);
        engine.onResponse(new String[] { "steve" });
        Assert.assertEquals("无请求时的响应丢弃", "/tp ste", host.text);
    }

    @Test
    public void emptyResponseKeepsText() {
        FakeHost host = new FakeHost();
        host.text = "/tp ste";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        engine.onResponse(new String[0]);
        Assert.assertEquals("/tp ste", host.text);
        engine.onTab(1); // 回 idle:再次 Tab 重新请求
        Assert.assertEquals(2, host.sentRequests.size());
    }

    @Test
    public void editingDuringCyclingResetsToIdle() {
        FakeHost host = new FakeHost();
        host.text = "st";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        engine.onResponse(new String[] { "steve", "stone" });
        Assert.assertEquals("steve", host.text);

        engine.onTextEdited();
        host.text = "hello st";
        engine.onTab(1);
        Assert.assertEquals("循环态已清:重新走请求路径", 2, host.sentRequests.size());
    }

    @Test
    public void nonTabKeyClearsCyclingEvenWithoutTextChange() {
        FakeHost host = new FakeHost();
        host.text = "st";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        engine.onResponse(new String[] { "steve", "stone" });
        Assert.assertEquals("steve", host.text);

        // 方向键/Home/End 只移动光标、不改变文本(不触发 onChange)→ 原版 GuiChat:91 任何非 Tab 键清循环
        engine.onTextEdited();
        engine.onTab(1);
        Assert.assertEquals("文本未变但循环态已清:Tab 重新走请求路径(C14 #2)", 2, host.sentRequests.size());
        Assert.assertEquals("等待态不修改文本", "steve", host.text);
    }

    @Test
    public void localFallbackMatchesCaseInsensitiveWithoutNetwork() {
        FakeHost host = new FakeHost();
        host.network = false;
        host.localNames.addAll(Arrays.asList("Steve", "Alex", "stella"));
        host.text = "ST"; // 大写输入:本地兜底大小写不敏感匹配
        ChatCompletionEngine engine = engine(host);

        engine.onTab(1);
        Assert.assertTrue("本地兜底不发 C14", host.sentRequests.isEmpty());
        Assert.assertEquals("第一段:折叠公共前缀扩展(保留首候选 case)", "Ste", host.text);
        Assert.assertTrue("扩展段不打印", host.printed.isEmpty());

        engine.onTab(1);
        Assert.assertEquals("第二段:已处公共前缀入循环,首候选入框", "Steve", host.text);
        Assert.assertEquals(Arrays.asList("Steve", "stella"), host.printed.get(0));

        engine.onTab(1);
        Assert.assertEquals("循环依次替换", "stella", host.text);
        engine.onTab(1);
        Assert.assertEquals("到尾回卷 0", "Steve", host.text);
    }

    @Test
    public void emptyTextDoesNothing() {
        FakeHost host = new FakeHost();
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        Assert.assertTrue(host.sentRequests.isEmpty());
        Assert.assertEquals("", host.text);
    }

    // ==================== 客户端命令本地候选(方案 A) ====================

    @Test
    public void commandNameCompletionMergesLocalOnResponse() {
        FakeHost host = new FakeHost();
        host.text = "/qzu";
        host.localCommands.add("/qzuilib");
        ChatCompletionEngine engine = engine(host);

        engine.onTab(1);
        Assert.assertEquals("网络可用仍发 C14(保持 RTT 体感)", Arrays.asList("/qzu"), host.sentRequests);
        Assert.assertEquals("本地候选绝不先行:等待态文本不变", "/qzu", host.text);
        Assert.assertEquals("请求时同步算本地候选", 1, host.localCommandCalls);

        engine.onResponse(new String[0]); // 服务端空应答 → 合并本地候选
        Assert.assertEquals("补命令名直达 /qzuilib(第一段公共前缀,不打印)", "/qzuilib", host.text);
        Assert.assertTrue(host.printed.isEmpty());
    }

    @Test
    public void subcommandCompletionMergesLocalOnResponse() {
        FakeHost host = new FakeHost();
        host.text = "/qzuilib te";
        host.localCommands.add("test");
        ChatCompletionEngine engine = engine(host);

        engine.onTab(1);
        engine.onResponse(new String[0]);
        Assert.assertEquals("补子命令(候选不含 /)", "/qzuilib test", host.text);
        Assert.assertTrue("公共前缀直达不打印", host.printed.isEmpty());
    }

    @Test
    public void noNetworkSlashFallsBackToLocalCommands() {
        FakeHost host = new FakeHost();
        host.network = false;
        host.text = "/qzu";
        host.localCommands.add("/qzuilib");
        ChatCompletionEngine engine = engine(host);

        engine.onTab(1);
        Assert.assertTrue("无网络不发 C14", host.sentRequests.isEmpty());
        Assert.assertEquals("/ 开头无网络兜底:本地命令候选直达", "/qzuilib", host.text);
        Assert.assertTrue("直达段不打印", host.printed.isEmpty());

        // 子命令多候选:无网络兜底同样走两段式 + 循环打印
        host.localCommands.clear();
        host.localCommands.add("test");
        host.localCommands.add("modernconfig");
        host.text = "/qzuilib te";
        engine.onTab(1);
        Assert.assertEquals("循环首候选入框", "/qzuilib test", host.text);
        Assert.assertEquals(Arrays.asList("test", "modernconfig"), host.printed.get(0));
        engine.onTab(1);
        Assert.assertEquals("循环替换", "/qzuilib modernconfig", host.text);
    }

    @Test
    public void mergedCandidatesLocalFirstAndDeduped() {
        FakeHost host = new FakeHost();
        host.text = "/tp ste";
        host.localCommands.add("steve");
        host.localCommands.add("stella");
        ChatCompletionEngine engine = engine(host);

        engine.onTab(1);
        engine.onResponse(new String[] { "stella", "stone" });
        // 合并 = dedupe(concat(local, server)),本地在前:["steve","stella","stone"](stella 去重)
        Assert.assertEquals("循环首候选 = 本地第一个", "/tp steve", host.text);
        Assert.assertEquals("打印顺序本地在前且去重",
                Arrays.asList("steve", "stella", "stone"), host.printed.get(0));
    }

    @Test
    public void staleResponseDropsOldLocalOptions() {
        FakeHost host = new FakeHost();
        host.text = "/qzu";
        host.localCommands.add("/qzuilib");
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        Assert.assertEquals(1, host.localCommandCalls);

        engine.onTextEdited(); // 用户继续输入 → 本地候选与请求快照同步清
        host.text = "/qzuilib tes";
        engine.onResponse(new String[] { "qzuilib" });
        Assert.assertEquals("编辑后旧响应丢弃", "/qzuilib tes", host.text);
        Assert.assertTrue(host.printed.isEmpty());
        Assert.assertEquals("编辑本身不触发本地候选重算", 1, host.localCommandCalls);

        engine.onTab(1);
        Assert.assertEquals("重新请求按新文本重算本地候选", 2, host.localCommandCalls);
    }

    @Test
    public void secondTabDuringAwaitingAppendsRequestWithoutOverwriting() {
        FakeHost host = new FakeHost();
        host.text = "/tp ste";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);

        engine.onTab(1); // I4:AWAITING 期重复 Tab 追加请求(FIFO 排队),绝不覆盖既有请求快照
        Assert.assertEquals(Arrays.asList("/tp ste", "/tp ste"), host.sentRequests);

        // 响应1:空应答(无候选可应用),文本不变 → 请求2 快照仍匹配
        engine.onResponse(new String[0]);
        Assert.assertEquals("/tp ste", host.text);

        // 响应2 匹配自己的快照 → 正常应用;旧单槽实现会把这份响应当多余响应丢弃(红)
        engine.onResponse(new String[] { "steve", "steve2" });
        Assert.assertEquals("第二份响应仍按 FIFO 消费并应用", "/tp steve", host.text);
        Assert.assertTrue("公共前缀扩展不打印", host.printed.isEmpty());
    }

    @Test
    public void firstResponseMatchingEditedTextIsAppliedBySnapshot() {
        FakeHost host = new FakeHost();
        host.text = "/tp ste";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1); // 请求1 快照 A = "/tp ste"

        // 文本继续编辑为 B(直接改真值,不调 onTextEdited:模拟清队时序缝隙,正是 I4 要防的错配)
        host.text = "/tp stev";
        engine.onTab(1); // 请求2 快照 B = "/tp stev",FIFO 追加(不覆盖请求1)
        Assert.assertEquals("每次 Tab 各发一份请求", 2, host.sentRequests.size());

        // 响应1(A 的候选)先到,快照 A != 当前 B → 丢弃
        engine.onResponse(new String[] { "steve", "stella" });
        Assert.assertEquals("响应1 快照 A 不匹配当前文本:丢弃", "/tp stev", host.text);
        Assert.assertTrue("丢弃路径不打印", host.printed.isEmpty());

        // 响应2(B 的候选)后到,快照 B == 当前文本 → 应用(当前词 "stev" → 公共前缀 "steve")
        engine.onResponse(new String[] { "steve", "stella" });
        Assert.assertEquals("响应2 快照 B 匹配当前文本:正常应用", "/tp steve", host.text);
    }

    @Test
    public void queueSelfAlignsWhenHeadDropped() {
        FakeHost host = new FakeHost();
        host.text = "/tp ste";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1); // A
        host.text = "/tp stev";
        engine.onTab(1); // B
        host.text = "/tp stevi";
        engine.onTab(1); // C
        Assert.assertEquals(3, host.sentRequests.size());

        // 当前文本 = C 的文本;响应1 队首 A 快照不匹配 → 丢弃并继续出队
        engine.onResponse(new String[] { "steve" });
        Assert.assertEquals("队首过期丢弃,队列保持自对齐", "/tp stevi", host.text);

        // 响应2 队首 B 仍不匹配 → 丢弃
        engine.onResponse(new String[] { "steven" });
        Assert.assertEquals("/tp stevi", host.text);

        // 响应3 队首 C 匹配 → 应用;当前词 "stevi" 与单候选 "stevie" 公共前缀 → 入框
        engine.onResponse(new String[] { "stevie" });
        Assert.assertEquals("丢弃队首后队列自对齐:响应3 对到 C", "/tp stevie", host.text);
    }

    @Test
    public void onTextEditedClearsAllPendingRequests() {
        FakeHost host = new FakeHost();
        host.text = "/tp ste";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        engine.onTab(1);
        Assert.assertEquals(2, host.sentRequests.size());

        engine.onTextEdited(); // I4:清空全部 pending,而非仅清最后一份请求快照
        Assert.assertEquals("编辑后回到 idle,不残留 pending", 2, host.sentRequests.size());

        engine.onResponse(new String[] { "steve", "stella" });
        Assert.assertEquals("清队后任何迟到响应都被丢弃", "/tp ste", host.text);
        Assert.assertTrue(host.printed.isEmpty());
        engine.onResponse(new String[] { "steve" });
        Assert.assertEquals("/tp ste", host.text);

        engine.onTab(1); // idle 后再次 Tab 走全新请求
        Assert.assertEquals(3, host.sentRequests.size());
        engine.onResponse(new String[] { "steve", "stella" });
        Assert.assertEquals("清队后新请求响应正常应用", "/tp steve", host.text);
    }

    @Test
    public void awaitingWithEmptyPendingDropsToIdle() {
        FakeHost host = new FakeHost();
        host.text = "/tp ste";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);

        host.text = "/tp stev";
        engine.onResponse(new String[] { "steve" }); // 唯一 pending 已出队(快照不匹配丢弃)→ idle
        engine.onResponse(new String[] { "steve" }); // 防御:多余响应不 NPE、不碰文本
        Assert.assertEquals("/tp stev", host.text);

        engine.onTab(1); // idle 后正常重新请求
        Assert.assertEquals(2, host.sentRequests.size());
        engine.onResponse(new String[] { "steve" });
        Assert.assertEquals("/tp steve", host.text);
    }

    @Test
    public void networkDropDuringAwaitingClearsPendingAndFallsBackLocally() {
        FakeHost host = new FakeHost();
        host.text = "/qzu";
        host.localCommands.add("/qzuilib");
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        Assert.assertEquals(Arrays.asList("/qzu"), host.sentRequests);

        // 断线:此前发出的 C14 已随连接作废;AWAITING 期再次 Tab 检测无网络 → 清 pending + 本地兜底
        host.network = false;
        engine.onTab(1);
        Assert.assertEquals("断线后不再发 C14", 1, host.sentRequests.size());
        Assert.assertEquals("本地命令候选直接兜底(第一段直达)", "/qzuilib", host.text);

        engine.onResponse(new String[] { "server-only" }); // 旧请求残留响应:无 pending → 丢弃
        Assert.assertEquals("/qzuilib", host.text);
        Assert.assertTrue(host.printed.isEmpty());
    }

    @Test
    public void nonSlashTextDoesNotQueryLocalCommands() {
        FakeHost host = new FakeHost();
        host.text = "ste";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        Assert.assertEquals("非 / 文本照常发 C14", Arrays.asList("ste"), host.sentRequests);
        Assert.assertEquals("非 / 文本不触发本地命令候选", 0, host.localCommandCalls);

        FakeHost offline = new FakeHost();
        offline.network = false;
        offline.localNames.add("Steve");
        offline.text = "ste";
        ChatCompletionEngine offlineEngine = engine(offline);
        offlineEngine.onTab(1);
        Assert.assertEquals("无网络非 / 文本走玩家名兜底,不查本地命令", 0, offline.localCommandCalls);
    }

    // ==================== 单候选不打印(原版 GuiChat:244) ====================

    @Test
    public void singleCandidateCompletesWithoutPrinting() {
        FakeHost host = new FakeHost();
        host.text = "/tp steve";
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        engine.onResponse(new String[] { "steve" });
        Assert.assertEquals("单候选仍替换入框", "/tp steve", host.text);
        Assert.assertTrue("单候选不打印(原版 size>1 才打印)", host.printed.isEmpty());

        engine.onTab(1);
        Assert.assertEquals("单候选循环态 Tab 不变", "/tp steve", host.text);
        Assert.assertTrue("单候选循环态不打印", host.printed.isEmpty());
    }

    @Test
    public void singleCandidateLocalCompletionDoesNotPrint() {
        FakeHost host = new FakeHost();
        host.text = "/qzu";
        host.localCommands.add("/qzuilib");
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        engine.onResponse(new String[0]);
        Assert.assertEquals("本地单候选直达", "/qzuilib", host.text);
        Assert.assertTrue("本地单候选不打印", host.printed.isEmpty());
    }
}
