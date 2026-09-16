package club.heiqi.uilib.internal.devtools.headless;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;

import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;

/**
 * headless 输入设备模型：表达「用户动作」，而不是「平台事件」。
 *
 * <h3>表达什么</h3>
 * <p>调用方写的是 {@code moveTo / press / release / click / scroll / type} 这类动作；
 * 设备负责三件事：动作 → 帧的切分、时间轴推进、修饰键与按钮状态维护。
 * 平台事件（{@link RawInputEvent}）由设备在推进时构造，调用方不见平台类型。</p>
 *
 * <h3>不变量</h3>
 * <ol>
 *   <li><b>复用生产封板器</b>：事件经 {@code InputFrameBuilder} 投影为 {@code SceneInputFrame}，
 *       不另造帧构造路径；设备只在推进时向封板器 push。</li>
 *   <li><b>帧边界显式</b>：动作默认落在同一帧；{@link #click} / {@link #pressKey} 会自动插入帧边界，
 *       因为「同一帧内按下并抬起」对部分控件不可靠（生产侧为此专门有跨帧注入先例）。</li>
 *   <li><b>时间轴单调</b>：每推进一帧，时钟前进 {@link #FRAME_STEP_NANOS}；长按与双击时间窗由时间轴跨度表达，
 *       框架层的双击合成（500ms 窗）因此可直接生效。</li>
 *   <li><b>状态自洽</b>：修饰键由已按下的键集合推导，按钮按下态由设备自己跟踪，不依赖调用方传参。</li>
 * </ol>
 */
public final class HeadlessInputDevice {

    /** 帧步长（纳秒）：60fps，与帧管线的时间语义一致。 */
    public static final long FRAME_STEP_NANOS = 16_666_667L;

    /** 单条待发动作：在推进到该动作所在帧时被翻译为原始事件。 */
    private interface Action {
        void apply(InputFrameBuilder builder, long timeNanos, HeadlessInputDevice device);
    }

    /** 帧边界标记：消费到它即结束本帧（不发送事件）。 */
    private static final Action FRAME_BOUNDARY = new Action() {
        @Override
        public void apply(InputFrameBuilder builder, long timeNanos, HeadlessInputDevice device) {
            // 帧边界本身不产生事件。
        }
    };

    private final Deque<Action> pending = new ArrayDeque<Action>();
    private final EnumSet<SceneKey> keysDown = EnumSet.noneOf(SceneKey.class);
    private final EnumSet<SceneMouseButton> buttonsDown = EnumSet.noneOf(SceneMouseButton.class);

    private long clockNanos;
    private int pointerX;
    private int pointerY;
    private int pendingWaitFrames;
    private int dispatchedActions;

    /**
     * 移动指针到逻辑坐标。
     *
     * @param x 逻辑 X
     * @param y 逻辑 Y
     */
    public void moveTo(final int x, final int y) {
        pending.addLast(new Action() {
            @Override
            public void apply(InputFrameBuilder builder, long timeNanos, HeadlessInputDevice device) {
                int dx = x - device.pointerX;
                int dy = y - device.pointerY;
                device.pointerX = x;
                device.pointerY = y;
                builder.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE, x, y, SceneMouseButton.NONE, 0,
                        dx, dy, device.controlDown(), device.shiftDown(), device.altDown(), device.metaDown(),
                        timeNanos));
            }
        });
    }

    /**
     * 相对移动指针。
     *
     * @param dx X 增量
     * @param dy Y 增量
     */
    public void moveBy(int dx, int dy) {
        moveTo(pointerX + dx, pointerY + dy);
    }

    /**
     * 按下鼠标按钮（在当前位置）。
     *
     * @param button 按钮
     */
    public void press(final SceneMouseButton button) {
        pending.addLast(new Action() {
            @Override
            public void apply(InputFrameBuilder builder, long timeNanos, HeadlessInputDevice device) {
                device.buttonsDown.add(button);
                builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, device.pointerX,
                        device.pointerY, button, 0, 0, 0, device.shiftDown(), device.controlDown(),
                        device.altDown(), device.metaDown(), timeNanos));
            }
        });
    }

    /**
     * 释放鼠标按钮（在当前位置）。
     *
     * @param button 按钮
     */
    public void release(final SceneMouseButton button) {
        pending.addLast(new Action() {
            @Override
            public void apply(InputFrameBuilder builder, long timeNanos, HeadlessInputDevice device) {
                device.buttonsDown.remove(button);
                builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, device.pointerX,
                        device.pointerY, button, 0, 0, 0, device.shiftDown(), device.controlDown(),
                        device.altDown(), device.metaDown(), timeNanos));
            }
        });
    }

    /**
     * 点击：按下与抬起<b>跨帧</b>（同帧往返对部分控件不可靠）。
     *
     * @param button 按钮
     */
    public void click(SceneMouseButton button) {
        press(button);
        frame();
        release(button);
    }

    /**
     * 双击：两次点击（时间轴跨度落在框架层的双击合成窗内）。
     *
     * @param button 按钮
     */
    public void doubleClick(SceneMouseButton button) {
        click(button);
        click(button);
    }

    /**
     * 滚轮滚动（纵向）。
     *
     * @param deltaY 纵向增量（正负号语义与平台一致）
     */
    public void scroll(int deltaY) {
        scroll(0, deltaY);
    }

    /**
     * 滚轮滚动（横纵）。
     *
     * @param deltaX 横向增量
     * @param deltaY 纵向增量
     */
    public void scroll(final int deltaX, final int deltaY) {
        pending.addLast(new Action() {
            @Override
            public void apply(InputFrameBuilder builder, long timeNanos, HeadlessInputDevice device) {
                builder.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, device.pointerX,
                        device.pointerY, SceneMouseButton.NONE, deltaY, deltaX, deltaY, device.shiftDown(),
                        device.controlDown(), device.altDown(), device.metaDown(), timeNanos));
            }
        });
    }

    /**
     * 按下键盘按键。
     *
     * @param key 按键
     */
    public void keyDown(final SceneKey key) {
        pending.addLast(new Action() {
            @Override
            public void apply(InputFrameBuilder builder, long timeNanos, HeadlessInputDevice device) {
                device.keysDown.add(key);
                builder.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED, device.controlDown(),
                        device.shiftDown(), device.altDown(), device.metaDown(), 0, 0, timeNanos));
            }
        });
    }

    /**
     * 释放键盘按键。
     *
     * @param key 按键
     */
    public void keyUp(final SceneKey key) {
        pending.addLast(new Action() {
            @Override
            public void apply(InputFrameBuilder builder, long timeNanos, HeadlessInputDevice device) {
                device.keysDown.remove(key);
                builder.push(RawInputEvent.ofKey(key, SceneKeyAction.RELEASED, device.controlDown(),
                        device.shiftDown(), device.altDown(), device.metaDown(), 0, 0, timeNanos));
            }
        });
    }

    /**
     * 按键（按下与抬起跨帧）。
     *
     * @param key 按键
     */
    public void pressKey(SceneKey key) {
        keyDown(key);
        frame();
        keyUp(key);
    }

    /**
     * 逐字符输入（char 路径，等价平台逐字符回调）。
     *
     * @param text 文本
     */
    public void type(String text) {
        if (text == null) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            final String ch = String.valueOf(text.charAt(i));
            pending.addLast(new Action() {
                @Override
                public void apply(InputFrameBuilder builder, long timeNanos, HeadlessInputDevice device) {
                    builder.push(RawInputEvent.ofText(ch, timeNanos));
                }
            });
        }
    }

    /**
     * 整串提交文本（外部文本接管 / IME 语义，一次 TEXT 事件）。
     *
     * @param text 文本
     */
    public void compose(final String text) {
        if (text == null) {
            return;
        }
        pending.addLast(new Action() {
            @Override
            public void apply(InputFrameBuilder builder, long timeNanos, HeadlessInputDevice device) {
                builder.push(RawInputEvent.ofText(text, timeNanos));
            }
        });
    }

    /** 指针取消（窗口失焦语义）。 */
    public void cancelPointer() {
        pending.addLast(new Action() {
            @Override
            public void apply(InputFrameBuilder builder, long timeNanos, HeadlessInputDevice device) {
                builder.push(RawInputEvent.ofPointer(ScenePointerAction.CANCEL, device.pointerX,
                        device.pointerY, SceneMouseButton.NONE, 0, 0, 0, device.shiftDown(),
                        device.controlDown(), device.altDown(), device.metaDown(), timeNanos));
            }
        });
    }

    /** 插入显式帧边界：其后的动作落到下一帧。 */
    public void frame() {
        pending.addLast(FRAME_BOUNDARY);
    }

    /**
     * 空推进若干帧（保持当前状态，不发送事件）。
     *
     * @param frames 帧数，至少 0
     */
    public void waitFrames(int frames) {
        pendingWaitFrames += Math.max(0, frames);
        pending.addLast(FRAME_BOUNDARY);
    }

    /**
     * 推进一帧：把本帧动作推入封板器，并让时钟前进一个帧步长。
     *
     * @param builder 生产封板器
     * @return 本帧实际下发的动作数
     */
    int pumpFrame(InputFrameBuilder builder) {
        clockNanos += FRAME_STEP_NANOS;
        int dispatched = 0;
        while (!pending.isEmpty()) {
            if (pending.peekFirst() == FRAME_BOUNDARY) {
                pending.removeFirst();
                dispatchedActions += dispatched;
                return dispatched;
            }
            pending.removeFirst().apply(builder, clockNanos, this);
            dispatched++;
        }
        // wait 只在动作队列耗尽后生效：语义是「先把动作发完，再空转 N 帧」，
        // 而不是「一上来就吞掉 N 帧动作」（后者会让脚本静默不执行，实测踩过）。
        if (pendingWaitFrames > 0) {
            pendingWaitFrames--;
        }
        dispatchedActions += dispatched;
        return dispatched;
    }

    /** @return 当前设备时钟（纳秒） */
    public long clockNanos() {
        return clockNanos;
    }

    /** @return 尚未下发的动作数（含帧边界） */
    public int pendingActionCount() {
        return pending.size();
    }

    /** @return 累计已下发动作数 */
    public int dispatchedActionCount() {
        return dispatchedActions;
    }

    /** @return 单行摘要：下发动作数、待发动作数、指针位置、设备时钟 */
    public String describe() {
        return "dispatched=" + dispatchedActions + " pending=" + pending.size()
                + " pointer=" + pointerX + "," + pointerY + " clock=" + (clockNanos / 1_000_000L) + "ms";
    }

    /** @return 指针当前位置，格式 {@code x,y} */
    public String pointerDescription() {
        return pointerX + "," + pointerY;
    }

    private boolean shiftDown() {
        return keysDown.contains(SceneKey.SHIFT_LEFT) || keysDown.contains(SceneKey.SHIFT_RIGHT);
    }

    private boolean controlDown() {
        return keysDown.contains(SceneKey.CONTROL_LEFT) || keysDown.contains(SceneKey.CONTROL_RIGHT);
    }

    private boolean altDown() {
        return keysDown.contains(SceneKey.ALT_LEFT) || keysDown.contains(SceneKey.ALT_RIGHT);
    }

    private boolean metaDown() {
        return keysDown.contains(SceneKey.META_LEFT) || keysDown.contains(SceneKey.META_RIGHT);
    }
}
