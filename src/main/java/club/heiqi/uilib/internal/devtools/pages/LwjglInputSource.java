package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;

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

    /**
     * 外部文本模式开关（Bug2）。
     *
     * <p>true 表示文本输入已由 lwjgl3ify {@code InputEvents#onTextEvent} 旁路接管
     * （传完整 String，含 IME/补充平面 emoji）：此时 {@link #pushKeyTyped} 不再产 TEXT，
     * 且字符键不再产 KEY（减噪），控制键仍产 KEY。</p>
     *
     * <p>false 表示降级路径：{@link #pushKeyTyped} 走 char 累积，自行组合 surrogate pair。</p>
     */
    private boolean externalTextMode = false;

    /**
     * 降级路径下暂存的 UTF-16 高代理项（high surrogate）。
     *
     * <p>0 表示无暂存。emoji（codepoint &gt; 0xFFFF）被 MC/lwjgl3ify 拆成两次 surrogate
     * keyTyped 回调，先到 high 后到 low；此字段暂存 high，待 low 到达后组合成完整 String。</p>
     */
    private char pendingHighSurrogate = 0;

    // 基线
    private boolean baselineInitialized;
    private int lastMouseX;
    private int lastMouseY;
    private boolean[] lastButtons;
    private double lastScrollAccum;
    /** 窗口焦点状态基线（边沿检测：true→false 合成 CANCEL） */
    private boolean lastWindowFocused;

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
        boolean curWindowFocused = reader.windowFocused();

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
            lastWindowFocused = curWindowFocused;
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

        // 滚轮：双路径修复（Bug1 真根因止血 + Oracle 更优方案）
        //
        // 路径 1：totalScrollAmount 差分（累计值差分 = 增量）
        //   lwjgl3ify Mouse.totalScrollAmount 是浮点累计（yoffset * INPUT_SCROLL_SPEED）。
        //   真机若该字段正常更新，差分即单帧增量。
        //
        // 路径 2：getDWheel() 增量 fallback（破坏性读取，读后清零）
        //   真机若 totalScrollAmount 恒为 0（字段不更新），路径 1 差分恒为 0，
        //   SCROLL 事件从不 push。此时 fallback 读 getDWheel() 拿单帧增量。
        //   仅在路径 1 无效时调用，避免每帧清零影响旧层 UiInputService 消费同一队列。
        //
        // ★路径互斥精确语义（Oracle 建议项 2）：
        //   路径 1 一旦有效（scrollDiff != 0）就绝不触发路径 2 的破坏性读取。
        //   这是为了避免在滚轮活跃帧偷走旧层 UiInputService 的 getDWheel 增量——
        //   旧层若同一帧也调 Mouse.next()/getEventDWheel() 消费同一事件队列，
        //   路径 2 的 getDWheel() 会清零内部计数，导致旧层读到 0、丢失该帧滚轮事件。
        //   因此阈值取 `> 0`（任何非零差分都走路径 1），只在累计值完全无更新时才 fallback。
        //
        // 保底步长算法（Oracle 更优方案：保留幅度 + 保底最小步长，非完全固定步长）：
        //   wheelDelta = signum(scrollDiff) * max(1, round(|scrollDiff| * 120))
        //   - 传统滚轮单 notch ≈ 1.0，× 120 = 120，保留传统手感
        //   - 触控板/高精度滚轮极小差分（如 0.001），× 120 取整为 0 → 保底 1px
        //   - 符号约定（与 SceneScrolls handler `next = current - wheelDelta` 对齐）：
        //     scrollDiff > 0（向上滚，看上方内容，scrollOffsetY 应减）→ wheelDelta > 0
        //     scrollDiff < 0（向下滚，看下方内容，scrollOffsetY 应增）→ wheelDelta < 0
        //
        // 对路径 2（getDWheel），dWheel 本身就是整数增量（LWJGL 已按 notch 量化），
        // 直接用其符号与幅度，不再 × 120（避免双重放大）。
        double scrollDiff = curScrollAccum - lastScrollAccum;
        int wheelDelta = 0;
        if (scrollDiff != 0) {
            // 路径 1：totalScrollAmount 差分（保留幅度 + 保底最小步长）
            // 阈值 `!= 0`（Oracle 建议项 4）：任何非零差分都走路径 1，
            // 避免单帧增量落在 (0, 0.0001] 区间时误走路径 2 清零 getDWheel。
            int magnitude = Math.max(1, (int) Math.abs(Math.round(scrollDiff * 120.0)));
            wheelDelta = (int) Math.signum(scrollDiff) * magnitude;
        } else {
            // 路径 2：getDWheel() 增量 fallback（破坏性读取，读后清零）
            int dWheel = reader.dWheelDelta();
            if (dWheel != 0) {
                wheelDelta = dWheel;
            }
        }
        if (wheelDelta != 0) {
            // [scroll-diag] 临时诊断日志：确认滚轮双路径哪条生效（Bug 1 排查，待回贴后删除）
            // TODO(bug1-scroll-cleanup) 真机日志回贴并确认根因后删除此诊断块
            System.err.println("[scroll-diag] curAccum=" + curScrollAccum
                + " lastAccum=" + lastScrollAccum
                + " diff=" + scrollDiff
                + " wheelDelta=" + wheelDelta
                + " path=" + (scrollDiff != 0 ? "totalScrollAmount" : "getDWheel"));
            builder.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL,
                    curX, curY, SceneMouseButton.NONE,
                    wheelDelta, 0, 0,
                    ctrl, shift, alt, meta, now));
        }

        // === I4d 失焦边沿差分：lastWindowFocused==true && cur==false → 合成 CANCEL ===
        // 在 MOVE/按钮/滚轮之后、封板之前 push（一帧内若同时失焦+有其他事件，CANCEL 最后到达语义合理）
        if (lastWindowFocused && !curWindowFocused) {
            // 坐标用当前帧 poll 的 curX/curY（当前指针位置），mods 全 false
            builder.push(RawInputEvent.ofPointer(ScenePointerAction.CANCEL,
                    curX, curY, SceneMouseButton.NONE,
                    0, 0, 0,
                    false, false, false, false, now));
        }

        // === 阶段4：更新基线 ===
        lastMouseX = curX;
        lastMouseY = curY;
        for (int i = 0; i < MOUSE_BUTTON_COUNT; i++) {
            lastButtons[i] = curButtons[i];
        }
        lastScrollAccum = curScrollAccum;
        lastWindowFocused = curWindowFocused;

        // === 阶段5：封板 ===
        return builder.drainFrame();
    }

    // ==================== I4b 键盘/文本输入旁路 ====================

    /**
     * 宿主 keyTyped 回调入口 —— 将键盘按下事件推入 builder 缓冲。
     *
     * <h3>I4b 帧中途 push 语义</h3>
     * <p>与 {@link #drainFrame()} 内 poll 差分共享同一 {@code builder}（单线程顺序 push 无冲突）：
     * 帧中途 keyTyped 推入 KEY/TEXT 事件 → 帧末 drainFrame poll pointer 再 push → 统一封板。</p>
     *
     * <h3>映射规则</h3>
     * <ol>
     *   <li>{@link LwjglKeyMapper#map(int)} native→SceneKey</li>
     *   <li>push {@link RawInputEvent#ofKey}（action=PRESSED，mods 从 reader 读当前态）；
     *       external 模式下字符键跳过 KEY 减噪，控制键仍产 KEY</li>
     *   <li>repeat 不区分（用户拍板 D5-A），全当 KEY_DOWN（action=PRESSED）</li>
     * </ol>
     *
     * <h3>文本分流（Bug2）</h3>
     * <ul>
     *   <li><b>external 模式</b>：文本完全交给 lwjgl3ify {@code onTextEvent} → {@link #pushText}，此处不产 TEXT</li>
     *   <li><b>降级模式</b>：surrogate-aware 累积——高代理项暂存、低代理项与暂存 high 组合成完整 emoji String，
     *       BMP 可打印字符直接 push（守 I1 契约 ofText 物理上不携带修饰键）</li>
     * </ul>
     *
     * @param typedChar     MC GuiScreen.keyTyped 传入的字符（'\0' 表示无字符）
     * @param nativeKeyCode LWJGL 原生键码（Keyboard.KEY_xxx 常量）
     * @param timeNanos     事件时间戳（纳秒），通常传 {@code System.nanoTime()}
     */
    public void pushKeyTyped(char typedChar, int nativeKeyCode, long timeNanos) {
        // １）native→SceneKey 映射
        SceneKey key = LwjglKeyMapper.map(nativeKeyCode);

        // ２）读当前修饰键态（与 poll 阶段的 reader 同源）
        boolean ctrl = reader.control();
        boolean shift = reader.shift();
        boolean alt = reader.alt();
        boolean meta = reader.meta();

        // 字符键判定：可打印字符或 surrogate（emoji 半体）都算字符键，控制键不算
        boolean isCharKey = isPrintable(typedChar) || Character.isSurrogate(typedChar);

        // ３）push KEY 事件（action=PRESSED，repeat 不区分）
        //    external 模式下字符键跳过 KEY 减噪（文本走 onTextEvent），控制键仍产 KEY 保留快捷键/导航
        if (!(externalTextMode && isCharKey)) {
            builder.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                    ctrl, shift, alt, meta,
                    nativeKeyCode, RawInputEvent.NATIVE_NONE,
                    timeNanos));
        }

        // ４）TEXT 事件分流
        if (externalTextMode) {
            // external 模式：文本完全交给 lwjgl3ify onTextEvent → pushText，此处不产 TEXT
            return;
        }

        // 降级路径：surrogate-aware 累积，自行组合 surrogate pair 还原完整 codepoint
        if (Character.isHighSurrogate(typedChar)) {
            // 高代理项先暂存，等待后续 low 到达
            pendingHighSurrogate = typedChar;
            return;
        }
        if (Character.isLowSurrogate(typedChar)) {
            if (pendingHighSurrogate != 0) {
                // 有暂存 high → 组合为完整 emoji String 并清暂存
                String combined = new String(new char[] {pendingHighSurrogate, typedChar});
                pendingHighSurrogate = 0;
                builder.push(RawInputEvent.ofText(combined, timeNanos));
            }
            // 否则孤立 low surrogate，无法组合，丢弃
            return;
        }

        // BMP 字符：先清掉可能残留的孤立 high（high 后未跟 low），再正常处理可打印字符
        pendingHighSurrogate = 0;
        if (isPrintable(typedChar)) {
            builder.push(RawInputEvent.ofText(String.valueOf(typedChar), timeNanos));
        }
    }

    /**
     * 外部文本旁路入口（Bug2）—— 接收 lwjgl3ify {@code onTextEvent} 传来的完整文本 String。
     *
     * <p>text 天然承载完整 codepoint（含 IME 合成结果与补充平面 emoji），
     * 直接包成单条 TEXT 事件 push，不再按字符拆分。</p>
     *
     * @param text      完整文本内容（可能多字符，含 emoji）
     * @param timeNanos 事件时间戳（纳秒）
     */
    public void pushText(String text, long timeNanos) {
        if (text != null && !text.isEmpty()) {
            builder.push(RawInputEvent.ofText(text, timeNanos));
        }
    }

    /**
     * 切换外部文本模式（Bug2）。
     *
     * <p>由宿主在检测到 lwjgl3ify {@code InputEvents} 可用并成功注册 onTextEvent 监听后置 true，
     * 关闭界面时置 false 回到降级路径。切换时清空暂存的高代理项，避免跨模式泄漏。</p>
     *
     * @param external true=外部 onTextEvent 接管文本；false=降级 char 路径
     */
    public void setExternalTextMode(boolean external) {
        this.externalTextMode = external;
        this.pendingHighSurrogate = 0;
    }

    /**
     * 判定 typedChar 是否为可打印文本字符。
     *
     * <p>过滤规则：</p>
     * <ul>
     *   <li>{@code typedChar >= 0x20} — 排除控制字符（含 '\0' = NUL，
     *       LWJGL keyTyped 无字符时给 0）</li>
     *   <li>{@code typedChar != 0x7F} — 排除 DEL</li>
     * </ul>
     *
     * @param c 待判定的字符
     * @return true 表示应产 TEXT_INPUT 事件
     */
    static boolean isPrintable(char c) {
        return c >= 0x20 && c != 0x7F;
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
