package club.heiqi.uilib.ui.scene.host.lwjgl;

import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * LwjglInputSource 指针按钮旁路测试（Bug3 修复）。
 *
 * <p>覆盖 oracle 方案 A 三个 L3 关键场景：</p>
 * <ol>
 *   <li>poll 单帧往返丢失复现（红→绿基线）：验证 externalPointerMode=false 时 buttonDown 在两次
 *       drainFrame 之间经历 false→true→false 完整往返，drainFrame 不产 DOWN/UP（复现 bug）</li>
 *   <li>事件旁路产出按钮事件：externalPointerMode=true + pushPointerButton 注入 DOWN/UP，
 *       drainFrame 不丢事件（验证修复）</li>
 *   <li>poll 与事件旁路去重：externalPointerMode=true 时，reader.buttonDown 从 false→true 变化，
 *       drainFrame 仍不产 button 事件（poll 停产 button 边沿，验证开关生效）</li>
 * </ol>
 *
 * <p>MOVE/SCROLL/CANCEL 不受 externalPointerMode 影响（仍走 poll），用 MOVE 做 sanity 校验。</p>
 */
public class LwjglInputSourcePointerBypassTest {

    private MockPlatformStateReader reader;
    private LwjglInputSource source;

    @Before
    public void setUp() {
        reader = new MockPlatformStateReader();
        source = new LwjglInputSource(reader);
    }

    /** drain 一帧并返回指针事件列表 */
    private List<ScenePointerEvent> drainPointerEvents() {
        SceneInputFrame frame = source.drainFrame();
        return frame.getPointerEvents();
    }

    // ==================== 测试 1：poll 单帧往返丢失复现（红→绿基线） ====================

    /**
     * 复现 Bug3 真因：poll 模式下 buttonDown 在两次 drainFrame 之间经历 false→true→false 完整往返，
     * 差分只看到 false→false（无净变化），不产任何按钮事件。
     *
     * <p>这是 oracle 裁决的"稳定复现的时序竞态"根因——下拉 item 点击失效的微观成因。</p>
     */
    @Test
    public void p1_pollMissesClickWhenDownUpRoundtripWithinOneFrame() {
        // 首帧建基线（buttonLeft=false）
        source.drainFrame();
        reader.advanceTime();

        // 模拟长帧：两次 drainFrame 之间用户完成按下→释放
        // （物理上 buttonDown 经历了 false→true→false，但 reader 当前态读到的仍是 false）
        reader.buttonLeft = false; // 当前态已恢复，与基线一致
        List<ScenePointerEvent> events = drainPointerEvents();

        // poll 差分看不到往返，不产任何按钮事件（复现 bug，证明问题存在）
        boolean hasButtonEvent = false;
        for (ScenePointerEvent e : events) {
            if (e.getAction() == ScenePointerAction.BUTTON_DOWN
                    || e.getAction() == ScenePointerAction.BUTTON_UP) {
                hasButtonEvent = true;
                break;
            }
        }
        Assert.assertFalse("poll 模式下完整往返丢失，不应产任何按钮事件（Bug3 复现）", hasButtonEvent);
    }

    // ==================== 测试 2：事件旁路产出按钮事件（验证修复） ====================

    /**
     * 启用 externalPointerMode 后，宿主回调通过 pushPointerButton 注入 DOWN+UP，
     * drainFrame 不丢事件。
     */
    @Test
    public void p2_externalBypassDeliversButtonDownAndUp() {
        // 首帧建基线
        source.drainFrame();
        reader.advanceTime();

        // 启用旁路（McScreenBridge.initGui 时会调）
        source.setExternalPointerMode(true);

        // 帧 A：宿主 mouseClicked 回调注入 BUTTON_DOWN
        long t1 = reader.nowNanos();
        source.pushPointerButton(ScenePointerAction.BUTTON_DOWN, 100, 50,
                SceneMouseButton.LEFT, t1);

        List<ScenePointerEvent> eventsA = drainPointerEvents();
        Assert.assertEquals("帧 A 应有 1 个 BUTTON_DOWN 事件", 1, eventsA.size());
        ScenePointerEvent down = eventsA.get(0);
        Assert.assertEquals("action=BUTTON_DOWN", ScenePointerAction.BUTTON_DOWN, down.getAction());
        Assert.assertEquals("button=LEFT", SceneMouseButton.LEFT, down.getButton());
        Assert.assertEquals("physicalX=100", 100, down.getLogicalX());
        Assert.assertEquals("physicalY=50", 50, down.getLogicalY());

        reader.advanceTime();
        // 帧 B：宿主 mouseMovedOrUp 回调注入 BUTTON_UP
        long t2 = reader.nowNanos();
        source.pushPointerButton(ScenePointerAction.BUTTON_UP, 100, 50,
                SceneMouseButton.LEFT, t2);

        List<ScenePointerEvent> eventsB = drainPointerEvents();
        Assert.assertEquals("帧 B 应有 1 个 BUTTON_UP 事件", 1, eventsB.size());
        ScenePointerEvent up = eventsB.get(0);
        Assert.assertEquals("action=BUTTON_UP", ScenePointerAction.BUTTON_UP, up.getAction());
        Assert.assertEquals("button=LEFT", SceneMouseButton.LEFT, up.getButton());
    }

    /**
     * 完整端到端：DOWN+UP 旁路注入，验证事件不丢（与 p1 对照：同样往返场景下旁路不丢）。
     */
    @Test
    public void p3_bypassDoesNotLoseRoundtripClick() {
        source.drainFrame(); // 基线
        reader.advanceTime();

        source.setExternalPointerMode(true);

        // 同一 drainFrame 间隔内注入 DOWN 和 UP（模拟用户极快点击）
        long t1 = reader.nowNanos();
        source.pushPointerButton(ScenePointerAction.BUTTON_DOWN, 200, 100,
                SceneMouseButton.LEFT, t1);
        reader.advanceTime();
        long t2 = reader.nowNanos();
        source.pushPointerButton(ScenePointerAction.BUTTON_UP, 200, 100,
                SceneMouseButton.LEFT, t2);

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("应产 2 个事件（DOWN + UP 都不丢）", 2, events.size());
        Assert.assertEquals("第一个=DOWN", ScenePointerAction.BUTTON_DOWN, events.get(0).getAction());
        Assert.assertEquals("第二个=UP", ScenePointerAction.BUTTON_UP, events.get(1).getAction());
    }

    // ==================== 测试 3：poll 与事件旁路去重（验证开关生效） ====================

    /**
     * externalPointerMode=true 时，即使 reader.buttonDown 真实变化（false→true），
     * drainFrame 也不产 button 事件（poll 停产 button 边沿，避免 double-dispatch）。
     */
    @Test
    public void p4_pollStopsButtonDiffWhenExternalModeEnabled() {
        source.drainFrame(); // 基线
        reader.advanceTime();

        source.setExternalPointerMode(true);

        // 模拟 poll 看到的真实 button 变化（false→true）
        reader.buttonLeft = true;

        List<ScenePointerEvent> events = drainPointerEvents();

        // poll 应停产 button 事件（externalPointerMode 守卫生效）
        for (ScenePointerEvent e : events) {
            Assert.assertNotEquals("externalPointerMode 下 poll 不应产 BUTTON_DOWN",
                    ScenePointerAction.BUTTON_DOWN, e.getAction());
            Assert.assertNotEquals("externalPointerMode 下 poll 不应产 BUTTON_UP",
                    ScenePointerAction.BUTTON_UP, e.getAction());
        }
    }

    /**
     * externalPointerMode 关闭后恢复 poll 差分：buttonDown false→true 重新产出 BUTTON_DOWN。
     */
    @Test
    public void p5_pollResumesButtonDiffWhenExternalModeDisabled() {
        source.drainFrame(); // 基线
        reader.advanceTime();

        // 开启旁路一帧
        source.setExternalPointerMode(true);
        reader.buttonLeft = true;
        source.drainFrame(); // 不产 button
        reader.advanceTime();

        // 关闭旁路，下一帧 button 仍按住（无变化）
        source.setExternalPointerMode(false);
        List<ScenePointerEvent> eventsHeld = drainPointerEvents();
        for (ScenePointerEvent e : eventsHeld) {
            Assert.assertNotEquals("持续按住不应产 BUTTON_DOWN",
                    ScenePointerAction.BUTTON_DOWN, e.getAction());
        }
        reader.advanceTime();

        // 按钮释放：现在 poll 差分应产出 BUTTON_UP（证明 poll 恢复工作）
        reader.buttonLeft = false;
        List<ScenePointerEvent> events = drainPointerEvents();
        boolean hasUp = false;
        for (ScenePointerEvent e : events) {
            if (e.getAction() == ScenePointerAction.BUTTON_UP) {
                hasUp = true;
                break;
            }
        }
        Assert.assertTrue("关闭旁路后 poll 恢复 button 差分，应产 BUTTON_UP", hasUp);
    }

    // ==================== Sanity：MOVE/SCROLL/CANCEL 不受影响 ====================

    /**
     * externalPointerMode=true 不影响 MOVE 事件产出（poll 仍负责 MOVE）。
     */
    @Test
    public void p6_moveStillPolledWhenExternalPointerModeEnabled() {
        source.drainFrame(); // 基线
        reader.advanceTime();

        source.setExternalPointerMode(true);

        reader.mouseX = 80;
        reader.mouseY = 60;

        List<ScenePointerEvent> events = drainPointerEvents();
        boolean hasMove = false;
        for (ScenePointerEvent e : events) {
            if (e.getAction() == ScenePointerAction.MOVE) {
                hasMove = true;
                Assert.assertEquals("MOVE X=80", 80, e.getLogicalX());
                Assert.assertEquals("MOVE Y=60", 60, e.getLogicalY());
            }
        }
        Assert.assertTrue("MOVE 仍应走 poll（不受 externalPointerMode 影响）", hasMove);
    }

    /**
     * mods 从 reader 读当前态，保证 pushPointerButton 事件与同帧 MOVE 事件 mods 一致。
     */
    @Test
    public void p7_pushPointerButtonReadsModsFromReader() {
        source.drainFrame(); // 基线
        reader.advanceTime();

        source.setExternalPointerMode(true);

        reader.control = true;
        reader.shift = true;

        source.pushPointerButton(ScenePointerAction.BUTTON_DOWN, 10, 20,
                SceneMouseButton.LEFT, reader.nowNanos());

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("应产 1 个事件", 1, events.size());
        ScenePointerEvent e = events.get(0);
        Assert.assertTrue("isControlDown=true", e.isControlDown());
        Assert.assertTrue("isShiftDown=true", e.isShiftDown());
        Assert.assertFalse("isAltDown=false", e.isAltDown());
    }
}
