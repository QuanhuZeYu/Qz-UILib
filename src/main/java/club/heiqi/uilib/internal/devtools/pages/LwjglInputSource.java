package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.input.*;

/**
 * LWJGL 输入源 —— 方案 C 当前态差分，implements {@link PlatformInputSource}。
 *
 * <h3>差分状态机时序（drainFrame 内）</h3>
 * <ol>
 *   <li><b>poll</b>：一次读齐当前态（坐标 Y 已翻转、mods）</li>
 *   <li><b>首帧基线</b>：baselineInitialized=false 时仅建基线不产事件，
 *       防止已按下/非零坐标误当增量喷假事件</li>
 *   <li><b>差分 push</b>：MOVE → 按钮边沿 → 滚轮</li>
 *   <li><b>更新基线</b>：整体覆盖 cur→last</li>
 *   <li><b>封板</b>：builder.drainFrame()</li>
 * </ol>
 *
 * <h3>纯指针范围（I3.5）</h3>
 * <p>MOVE / BUTTON_DOWN/UP / SCROLL。键盘/TEXT 推迟 I4。</p>
 */
public class LwjglInputSource implements PlatformInputSource {

    private static final int MOUSE_BUTTON_COUNT = 5; // LEFT=0, RIGHT=1, MIDDLE=2, BUTTON_4=3, BUTTON_5=4

    private final PlatformStateReader reader;
    private final InputFrameBuilder builder;

    // 基线
    private boolean baselineInitialized;
    private int lastMouseX;
    private int lastMouseY;
    private boolean[] lastButtons;
    private double lastScrollAccum;

    public LwjglInputSource(PlatformStateReader reader) {
        this.reader = reader;
        this.builder = new InputFrameBuilder(0, 0);
        this.baselineInitialized = false;
        this.lastButtons = new boolean[MOUSE_BUTTON_COUNT];
        this.lastScrollAccum = 0.0;
    }

    @Override
    public SceneInputFrame drainFrame() {
        // === 阶段1：poll 当前态 ===
        int curX = reader.mouseX();
        int curY = reader.mouseY();
        boolean ctrl = reader.control();
        boolean shift = reader.shift();
        boolean alt = reader.alt();
        boolean meta = reader.meta();
        long now = reader.nowNanos();

        boolean[] curButtons = new boolean[MOUSE_BUTTON_COUNT];
        for (int i = 0; i < MOUSE_BUTTON_COUNT; i++) {
            curButtons[i] = reader.buttonDown(i);
        }
        double curScrollAccum = reader.scrollAccum();

        // === 阶段2：首帧建基线 ===
        if (!baselineInitialized) {
            lastMouseX = curX;
            lastMouseY = curY;
            for (int i = 0; i < MOUSE_BUTTON_COUNT; i++) {
                lastButtons[i] = curButtons[i];
            }
            lastScrollAccum = curScrollAccum;
            baselineInitialized = true;
            return builder.drainFrame(); // 首帧不产事件
        }

        // === 阶段3：差分 push（顺序：MOVE → 按钮 → 滚轮）===

        // MOVE：坐标变化才产
        if (curX != lastMouseX || curY != lastMouseY) {
            int deltaX = curX - lastMouseX;
            int deltaY = curY - lastMouseY;
            builder.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE,
                    curX, curY, SceneMouseButton.NONE,
                    0, deltaX, deltaY,
                    ctrl, shift, alt, meta, now));
        }

        // 按钮边沿：false→true 产 BUTTON_DOWN，true→false 产 BUTTON_UP。
        // L1 已知局限：DOWN+UP 同帧完成（按钮按下-释放在一次 drainFrame 间隔内发生）时，
        // 当前态已恢复 false，差分只看到 false→false（无净变化），不产任何事件，
        // click 丢失。方案 C 一帧 poll 一次的固有局限，极短手动点击可能触发。
        for (int i = 0; i < MOUSE_BUTTON_COUNT; i++) {
            if (curButtons[i] != lastButtons[i]) {
                ScenePointerAction action = curButtons[i]
                        ? ScenePointerAction.BUTTON_DOWN
                        : ScenePointerAction.BUTTON_UP;
                SceneMouseButton btn = mapButtonCode(i);
                builder.push(RawInputEvent.ofPointer(action,
                        curX, curY, btn,
                        0, 0, 0,
                        ctrl, shift, alt, meta, now));
            }
        }

        // 滚轮：累计量差分
        double scrollDiff = curScrollAccum - lastScrollAccum;
        if (Math.abs(scrollDiff) > 0.0001) {
            int wheelDelta = (int) Math.round(scrollDiff * 120.0);
            if (wheelDelta != 0) {
                builder.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL,
                        curX, curY, SceneMouseButton.NONE,
                        wheelDelta, 0, 0,
                        ctrl, shift, alt, meta, now));
            }
        }

        // === 阶段4：更新基线 ===
        lastMouseX = curX;
        lastMouseY = curY;
        for (int i = 0; i < MOUSE_BUTTON_COUNT; i++) {
            lastButtons[i] = curButtons[i];
        }
        lastScrollAccum = curScrollAccum;

        // === 阶段5：封板 ===
        return builder.drainFrame();
    }

    @Override
    public int logicalWidth() {
        return reader.logicalWidth();
    }

    @Override
    public int logicalHeight() {
        return reader.logicalHeight();
    }

    /**
     * 将 LWJGL button code (0-4) 映射为 SceneMouseButton。
     */
    static SceneMouseButton mapButtonCode(int button) {
        switch (button) {
            case 0: return SceneMouseButton.LEFT;
            case 1: return SceneMouseButton.RIGHT;
            case 2: return SceneMouseButton.MIDDLE;
            case 3: return SceneMouseButton.BUTTON_4;
            case 4: return SceneMouseButton.BUTTON_5;
            default: return SceneMouseButton.NONE;
        }
    }
}
