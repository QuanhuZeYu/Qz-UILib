package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tab 补全状态机测试(T2):idle → awaiting → cycling 三态、两段式响应、
 * 循环正反、候选打印、stale 快照守卫、本地无网络兜底(R1 不本地先行)。
 */
public class ChatCompletionEngineTest {

    /** 记录型假宿主:完全 headless,不发真实网络/不触 Minecraft。 */
    private static final class FakeHost implements ChatCompletionEngine.Host {
        String text = "";
        boolean network = true;
        final List<String> sentRequests = new ArrayList<String>();
        final List<String> localNames = new ArrayList<String>();
        final List<List<String>> printed = new ArrayList<List<String>>();

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

        engine.onTab(1); // 等待态重复 Tab:原版语义重发 C14(覆盖请求快照)
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
    public void localFallbackSkipsCommandWhenNoNetwork() {
        FakeHost host = new FakeHost();
        host.network = false;
        host.localNames.add("Steve");
        host.text = "/tp st";
        ChatCompletionEngine engine = engine(host);

        engine.onTab(1);
        Assert.assertTrue("无网络命令补全无本地来源:不发不补", host.sentRequests.isEmpty());
        Assert.assertEquals("/tp st", host.text);
        Assert.assertTrue(host.printed.isEmpty());
    }

    @Test
    public void emptyTextDoesNothing() {
        FakeHost host = new FakeHost();
        ChatCompletionEngine engine = engine(host);
        engine.onTab(1);
        Assert.assertTrue(host.sentRequests.isEmpty());
        Assert.assertEquals("", host.text);
    }
}
