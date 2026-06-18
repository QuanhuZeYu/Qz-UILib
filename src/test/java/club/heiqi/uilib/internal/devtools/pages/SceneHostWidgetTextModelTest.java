package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * SceneHostWidget 文本模型同帧多事件回归测试（Bug2 后续修复）。
 *
 * <h3>背景</h3>
 * <p>SDL onTextEvent 在同一帧 push 多个 TEXT 事件时，route 在 flush 之前连续调用 N 次 handler；
 * 旧实现 handler 读 {@code inputTextSignal.get()} 累加，因 reactive Signal 在 flush 前 get 恒返回旧值，
 * 同帧多事件互相覆盖（如"好好好"只剩一个"好"）。修复改用私有字段作即时权威读写源，signal 只单向派生。</p>
 *
 * <h3>注入范式</h3>
 * <p>null 退化模式构造 SceneHostWidget（不触发 LWJGL 反射）→ requestFocus 目标文本框 →
 * InputFrameBuilder 造帧 push TEXT/KEY 事件 → drainFrame → runtime.route → runtime.flush →
 * 断言字段模型与 signal 终值。</p>
 */
public class SceneHostWidgetTextModelTest {

    private SceneHostWidget host;
    private SceneRuntime runtime;
    private SceneNode root;
    private long timeSeq;

    @Before
    public void setUp() {
        // null 输入源 → 退化模式，不注入 LWJGL cursor 后端（沙箱安全）
        host = new SceneHostWidget(null);
        runtime = host.__getRuntime();
        root = host.__getRoot();
        timeSeq = 1_000_000_000L;
    }

    // ==================== 辅助：造帧 + 路由 + flush ====================

    /** 递增时间戳，避免同帧重复时间 */
    private long nextTime() {
        timeSeq += 1_000L;
        return timeSeq;
    }

    /**
     * 将一组原始事件封板为一帧，路由到聚焦节点，并 flush。
     *
     * @param events 同帧内顺序 push 的原始事件
     */
    private void routeFrame(RawInputEvent... events) {
        InputFrameBuilder builder = new InputFrameBuilder(0, 0);
        for (RawInputEvent e : events) {
            builder.push(e);
        }
        SceneInputFrame frame = builder.drainFrame();
        runtime.route(root, frame, 0, 0);
        runtime.flush();
    }

    /** 构造 TEXT 原始事件 */
    private RawInputEvent text(String s) {
        return RawInputEvent.ofText(s, nextTime());
    }

    /** 构造 BACKSPACE KEY 原始事件 */
    private RawInputEvent backspace() {
        return RawInputEvent.ofKey(SceneKey.BACKSPACE, SceneKeyAction.PRESSED,
                false, false, false, false,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, nextTime());
    }

    /** 聚焦文本框① */
    private void focusInput1() {
        runtime.requestFocus(host.__getTextInput1());
    }

    /** 聚焦文本框② */
    private void focusInput2() {
        runtime.requestFocus(host.__getTextInput2());
    }

    // ==================== 用例 ====================

    /**
     * 1（核心回归）：同帧 3 个"好" TEXT 事件 → flush 后字段/signal 均为"好好好"。
     * 直击根因：旧实现同帧多 TEXT 互相覆盖只剩一个"好"。
     */
    @Test
    public void sameFrameThreeChineseCharsAllAccumulated() {
        focusInput1();
        routeFrame(text("好"), text("好"), text("好"));

        Assert.assertEquals("字段模型应累积 3 个好", "好好好", host.__getInputModel1());
        Assert.assertEquals("signal 终值应与字段一致", "好好好", host.__getInputSignal1());
    }

    /**
     * 2：同帧 emoji 单事件 + 后续帧 BMP → 累积正确（不回归首轮 emoji 修复）。
     */
    @Test
    public void emojiThenBmpAccumulatesCorrectly() {
        focusInput1();
        // 第一帧：完整 emoji（单 TEXT 事件，承载 codepoint > 0xFFFF）
        routeFrame(text("\uD83D\uDE00")); // 😀
        Assert.assertEquals("emoji 累积", "\uD83D\uDE00", host.__getInputModel1());

        // 第二帧：BMP 字符
        routeFrame(text("a"));
        Assert.assertEquals("emoji + BMP 累积", "\uD83D\uDE00a", host.__getInputModel1());
        Assert.assertEquals("signal 一致", "\uD83D\uDE00a", host.__getInputSignal1());
    }

    /**
     * 3：同帧多次 BACKSPACE → 删多个 codepoint。
     */
    @Test
    public void sameFrameMultipleBackspaceDeletesMultiple() {
        focusInput1();
        // 先铺底 5 个 BMP 字符
        routeFrame(text("abcde"));
        Assert.assertEquals("abcde", host.__getInputModel1());

        // 同帧 3 次 BACKSPACE
        routeFrame(backspace(), backspace(), backspace());
        Assert.assertEquals("应删 3 个字符", "ab", host.__getInputModel1());
        Assert.assertEquals("signal 一致", "ab", host.__getInputSignal1());
    }

    /**
     * 4：同帧 [TEXT"好", BACKSPACE] → flush 后 = "好"。
     * 验证 route 先分发 TEXT 后分发 KEY（router 内 text 循环先于 key 循环）+ 字段中转即时生效。
     */
    @Test
    public void sameFrameTextThenBackspaceLeavesOriginal() {
        focusInput1();
        // 铺底 1 个字符，便于 BACKSPACE 有内容可删
        routeFrame(text("好"));
        Assert.assertEquals("好", host.__getInputModel1());

        // 同帧：再输入 1 个"好"（TEXT 先分发）→ "好好"，再 BACKSPACE（KEY 后分发）→ "好"
        routeFrame(text("好"), backspace());
        Assert.assertEquals("TEXT 先于 KEY：先 +好 再删 1 → 回到单个好", "好", host.__getInputModel1());
        Assert.assertEquals("signal 一致", "好", host.__getInputSignal1());
    }

    /**
     * 5：BACKSPACE 删 emoji → 整个 codepoint（2 个 char）一次性消失。
     */
    @Test
    public void backspaceDeletesWholeEmojiCodepoint() {
        focusInput1();
        // 输入 BMP + emoji
        routeFrame(text("a"), text("\uD83D\uDE00")); // a😀
        Assert.assertEquals("a\uD83D\uDE00", host.__getInputModel1());

        // 一次 BACKSPACE 删掉整个 emoji（codepoint-aware 回退 2 个 char）
        routeFrame(backspace());
        Assert.assertEquals("emoji 整体消失，仅剩 a", "a", host.__getInputModel1());
        Assert.assertEquals("signal 一致", "a", host.__getInputSignal1());
    }

    /**
     * 6：字段与 signal flush 后终值始终一致（混合操作）。
     */
    @Test
    public void fieldAndSignalRemainConsistent() {
        focusInput1();
        routeFrame(text("x"), text("y"), text("z"));
        Assert.assertEquals(host.__getInputModel1(), host.__getInputSignal1());

        routeFrame(backspace());
        Assert.assertEquals(host.__getInputModel1(), host.__getInputSignal1());
        Assert.assertEquals("xy", host.__getInputModel1());
    }

    /**
     * 7：input1/input2 模型互不串扰（焦点切换后输入落到各自字段）。
     */
    @Test
    public void input1AndInput2DoNotCrossContaminate() {
        // 先聚焦 input1 输入
        focusInput1();
        routeFrame(text("好"), text("好"));
        Assert.assertEquals("好好", host.__getInputModel1());
        Assert.assertEquals("input2 不应受影响", "", host.__getInputModel2());

        // 切焦点到 input2 输入
        focusInput2();
        routeFrame(text("a"), text("b"), text("c"));
        Assert.assertEquals("input2 累积 abc", "abc", host.__getInputModel2());
        Assert.assertEquals("input1 保持不变", "好好", host.__getInputModel1());

        Assert.assertEquals("input1 signal 一致", "好好", host.__getInputSignal1());
        Assert.assertEquals("input2 signal 一致", "abc", host.__getInputSignal2());
    }
}
