package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.SceneTextEvent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * LwjglInputSource 文本输入修复（Bug2）单元测试 —— 纯沙箱，无 lwjgl3ify 运行时。
 *
 * <p>覆盖 emoji/codepoint 文本输入的两条路径：</p>
 * <ul>
 *   <li><b>external 模式</b>：{@code pushText} 直接承载完整 String；字符键减噪不产 KEY/TEXT</li>
 *   <li><b>降级模式</b>：{@code pushKeyTyped} surrogate-aware 累积，自行组合 surrogate pair</li>
 * </ul>
 *
 * <p>另含 {@link SceneLwjgl3ifyTextBridge#isAvailable()} 沙箱探测不抛断言。</p>
 */
public class LwjglInputSourceTextInputTest {

    /** 笑脸 emoji 😀 = U+1F600，UTF-16 高/低代理项 */
    private static final char EMOJI_HIGH = '\uD83D';
    private static final char EMOJI_LOW = '\uDE00';
    /** 完整 emoji String 😀 */
    private static final String EMOJI = "\uD83D\uDE00";

    /** KEY_BACKSPACE native code（LWJGL Keyboard.KEY_BACK=14） */
    private static final int NATIVE_BACKSPACE = 14;
    /** KEY_A native code（LWJGL Keyboard.KEY_A=30） */
    private static final int NATIVE_A = 30;

    private MockPlatformStateReader reader;
    private LwjglInputSource source;

    @Before
    public void setUp() {
        reader = new MockPlatformStateReader();
        source = new LwjglInputSource(reader);
        // 首帧建立差分基线，使后续帧能正常产事件
        source.drainFrame();
        reader.advanceTime();
    }

    /** drain 一帧并返回文本事件列表 */
    private List<SceneTextEvent> drainTextEvents() {
        SceneInputFrame frame = source.drainFrame();
        return frame.getTextEvents();
    }

    /** drain 一帧并返回键盘事件列表 */
    private List<SceneKeyEvent> drainKeyEvents() {
        SceneInputFrame frame = source.drainFrame();
        return frame.getKeyEvents();
    }

    // ==================== external 模式：pushText 完整承载 ====================

    /**
     * external 模式 push 完整 emoji String → 单条完整 TEXT 事件（非碎字符）。
     */
    @Test
    public void pushTextEmojiProducesSingleCompleteTextEvent() {
        source.pushText(EMOJI, reader.nowNanos());

        List<SceneTextEvent> textEvents = drainTextEvents();
        Assert.assertEquals("应产 1 条 TEXT 事件", 1, textEvents.size());
        Assert.assertEquals("文本应为完整 emoji", EMOJI, textEvents.get(0).getText());
        Assert.assertEquals("codepoint 数应为 1", 1, textEvents.get(0).getText().codePointCount(0,
                textEvents.get(0).getText().length()));
    }

    /**
     * pushText 空串/null 不产事件。
     */
    @Test
    public void pushTextEmptyOrNullProducesNothing() {
        source.pushText("", reader.nowNanos());
        source.pushText(null, reader.nowNanos());

        List<SceneTextEvent> textEvents = drainTextEvents();
        Assert.assertTrue("空串/null 不应产 TEXT 事件", textEvents.isEmpty());
    }

    /**
     * 同帧多次 pushText 会在封板层合并为一条完整 TEXT。
     */
    @Test
    public void multiplePushTextBeforeDrainMergesIntoSingleTextEvent() {
        source.pushText("修", reader.nowNanos());
        source.pushText("好", reader.nowNanos());
        source.pushText("了", reader.nowNanos());

        List<SceneTextEvent> textEvents = drainTextEvents();
        Assert.assertEquals("同帧多次 pushText 应合并为 1 条 TEXT", 1, textEvents.size());
        Assert.assertEquals("合并文本应保持原顺序", "修好了", textEvents.get(0).getText());
    }

    // ==================== 降级模式：surrogate-aware 累积 ====================

    /**
     * 降级模式连续 push high+low surrogate → 单条完整 emoji String（非两条坏串）。
     */
    @Test
    public void fallbackSurrogatePairCombinesIntoSingleTextEvent() {
        // 默认即降级模式（externalTextMode=false）
        source.pushKeyTyped(EMOJI_HIGH, 0, reader.nowNanos());
        source.pushKeyTyped(EMOJI_LOW, 0, reader.nowNanos());

        List<SceneTextEvent> textEvents = drainTextEvents();
        Assert.assertEquals("应产 1 条完整 TEXT 事件（非两条碎串）", 1, textEvents.size());
        Assert.assertEquals("文本应为完整 emoji", EMOJI, textEvents.get(0).getText());
    }

    /**
     * 降级模式孤立 high surrogate 后跟 BMP 字符 → 仅 BMP 那条 TEXT，high 不泄漏。
     */
    @Test
    public void fallbackIsolatedHighThenBmpDropsHigh() {
        source.pushKeyTyped(EMOJI_HIGH, 0, reader.nowNanos()); // 孤立 high，暂存
        source.pushKeyTyped('a', NATIVE_A, reader.nowNanos()); // BMP 字符到达，应清掉残留 high

        List<SceneTextEvent> textEvents = drainTextEvents();
        Assert.assertEquals("仅应产 BMP 那条 TEXT", 1, textEvents.size());
        Assert.assertEquals("文本应为 'a'（high 不泄漏）", "a", textEvents.get(0).getText());
    }

    /**
     * 降级模式孤立 low surrogate（无暂存 high）→ 丢弃，不产 TEXT。
     */
    @Test
    public void fallbackIsolatedLowIsDropped() {
        source.pushKeyTyped(EMOJI_LOW, 0, reader.nowNanos()); // 孤立 low，丢弃

        List<SceneTextEvent> textEvents = drainTextEvents();
        Assert.assertTrue("孤立 low surrogate 应被丢弃", textEvents.isEmpty());
    }

    // ==================== external 模式：分流减噪 ====================

    /**
     * external 模式 push 可打印字符 → 不产 TEXT（文本完全交给 onTextEvent）。
     */
    @Test
    public void externalModePrintableCharProducesNoText() {
        source.setExternalTextMode(true);
        source.pushKeyTyped('a', NATIVE_A, reader.nowNanos());

        SceneInputFrame frame = source.drainFrame();
        Assert.assertTrue("external 模式可打印字符不应产 TEXT", frame.getTextEvents().isEmpty());
    }

    /**
     * external 模式 push 可打印字符 → 不产 KEY（字符键减噪）。
     */
    @Test
    public void externalModePrintableCharProducesNoKey() {
        source.setExternalTextMode(true);
        source.pushKeyTyped('a', NATIVE_A, reader.nowNanos());

        SceneInputFrame frame = source.drainFrame();
        Assert.assertTrue("external 模式字符键应减噪不产 KEY", frame.getKeyEvents().isEmpty());
    }

    /**
     * external 模式 push 控制键（BACKSPACE）→ KEY 仍产出，TEXT 为空。
     */
    @Test
    public void externalModeControlKeyStillProducesKey() {
        source.setExternalTextMode(true);
        // BACKSPACE typedChar 通常为 DEL 0x7F（不可打印，非 surrogate，属控制键）
        source.pushKeyTyped((char) 0x7F, NATIVE_BACKSPACE, reader.nowNanos());

        SceneInputFrame frame = source.drainFrame();
        List<SceneKeyEvent> keyEvents = frame.getKeyEvents();
        Assert.assertEquals("控制键应产 1 条 KEY 事件", 1, keyEvents.size());
        Assert.assertEquals("key=BACKSPACE", SceneKey.BACKSPACE, keyEvents.get(0).getKey());
        Assert.assertTrue("external 模式不产 TEXT", frame.getTextEvents().isEmpty());
    }

    /**
     * setExternalTextMode 切换时清空暂存 high surrogate，避免跨模式泄漏。
     */
    @Test
    public void setExternalTextModeClearsPendingHighSurrogate() {
        // 降级模式暂存一个 high
        source.pushKeyTyped(EMOJI_HIGH, 0, reader.nowNanos());
        // 切到 external 再切回降级（应清掉暂存）
        source.setExternalTextMode(true);
        source.setExternalTextMode(false);
        // 此时 push 一个孤立 low：若 high 未清，会误组合；正确行为应丢弃
        source.pushKeyTyped(EMOJI_LOW, 0, reader.nowNanos());

        List<SceneTextEvent> textEvents = drainTextEvents();
        Assert.assertTrue("切换模式应清掉暂存 high，孤立 low 被丢弃", textEvents.isEmpty());
    }

    // ==================== bridge 探测/生命周期安全（环境无关契约） ====================
    // 注：测试 classpath 可能存在 lwjgl3ify 反编译依赖，故不硬断言 isAvailable=false；
    // 改为断言探测与生命周期在「有无 lwjgl3ify」两种环境下都安全、幂等、不抛。

    /**
     * isAvailable 探测自身安全：无论运行时是否存在 lwjgl3ify，调用都不抛异常。
     */
    @Test
    public void bridgeIsAvailableDoesNotThrow() {
        SceneLwjgl3ifyTextBridge.isAvailable(); // 不抛即通过（返回 true/false 取决于运行时）
    }

    /**
     * register/unregister 生命周期安全：register 不抛异常，unregister 幂等不抛。
     * lwjgl3ify 不可用时 register 必返回 false（降级契约）；可用时可能 true，
     * 也可能因测试环境无 SDL 窗口而降级 false，两者皆合法。
     */
    @Test
    public void bridgeLifecycleIsSafeRegardlessOfRuntime() {
        SceneLwjgl3ifyTextBridge bridge =
                new SceneLwjgl3ifyTextBridge(text -> source.pushText(text, reader.nowNanos()));
        try {
            boolean registered = bridge.register(); // 不抛
            if (!SceneLwjgl3ifyTextBridge.isAvailable()) {
                Assert.assertFalse("lwjgl3ify 不可用时 register 必返回 false（降级契约）", registered);
            }
        } finally {
            // unregister 幂等不抛（双调），并清理可能注册到 lwjgl3ify 全局的监听器，避免污染
            bridge.unregister();
            bridge.unregister();
        }
    }
}
