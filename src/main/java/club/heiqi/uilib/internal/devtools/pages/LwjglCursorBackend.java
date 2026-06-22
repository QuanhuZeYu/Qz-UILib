package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.host.UiCursorHost;
import club.heiqi.uilib.ui.scene.input.CursorBackend;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.style.props.UiCursor;

import java.util.EnumMap;
import java.util.Map;

/**
 * 宿主系统光标后端实现 —— I4c 适配层。
 *
 * <h3>设计理由：委托宿主能力而非自写反射桥</h3>
 * <p>中性系统宿主已含完整的 {@code SdlReflectionBridge}
 * （lwjglx 优先 / lwjgl 降级 / SDL 系统光标创建 / 主线程调度 / 全失败 no-op 降级），
 * 自写一份轻量版既重复又有遗漏风险（漏某个光标常量、漏主线程调度、漏去重逻辑）。</p>
 *
 * <h3>SceneCursor → UiCursor 映射</h3>
 * <p>映射表在适配层完成（不在核心包），守 I10：核心包不 import UiCursor。</p>
 *
 * <h3>静默降级</h3>
 * <p>中性系统宿主内部已实现全失败 no-op 降级
 * （Display 创建探测失败 / 反射解析失败 / 运行时光标应用失败均静默降级），
 * 本类直接继承此保证，满足 I4c 叫停关口⑤。</p>
 */
public class LwjglCursorBackend implements CursorBackend {

    /** SceneCursor → UiCursor 映射表（适配层专有，核心包不可见） */
    private static final Map<SceneCursor, UiCursor> CURSOR_MAP = buildCursorMap();

    /** 系统光标宿主单例 */
    private final UiCursorHost delegate;

    public LwjglCursorBackend() {
        this.delegate = UiCursorHost.system();
    }

    @Override
    public void apply(SceneCursor cursor) {
        delegate.applyCursor(toUiCursor(cursor));
    }

    /**
     * 强制同步宿主原生光标到当前 Scene 期望值，不改变 Scene 的 cursorSignal 状态源。
     *
     * @param cursor 当前 Scene 期望光标；为空时按默认光标处理
     */
    @Override
    public void forceApply(SceneCursor cursor) {
        delegate.forceApplyCursor(toUiCursor(cursor));
    }

    private static UiCursor toUiCursor(SceneCursor cursor) {
        UiCursor uiCursor = CURSOR_MAP.get(cursor);
        return uiCursor == null ? UiCursor.DEFAULT : uiCursor;
    }

    /**
     * 构建 SceneCursor → UiCursor 一对一映射表。
     *
     * <p>两枚举值集完全一致，直接一一对应。</p>
     */
    private static Map<SceneCursor, UiCursor> buildCursorMap() {
        Map<SceneCursor, UiCursor> map = new EnumMap<SceneCursor, UiCursor>(SceneCursor.class);
        map.put(SceneCursor.DEFAULT, UiCursor.DEFAULT);
        map.put(SceneCursor.POINTER, UiCursor.POINTER);
        map.put(SceneCursor.TEXT, UiCursor.TEXT);
        map.put(SceneCursor.MOVE, UiCursor.MOVE);
        map.put(SceneCursor.GRAB, UiCursor.GRAB);
        map.put(SceneCursor.GRABBING, UiCursor.GRABBING);
        map.put(SceneCursor.NOT_ALLOWED, UiCursor.NOT_ALLOWED);
        map.put(SceneCursor.WAIT, UiCursor.WAIT);
        map.put(SceneCursor.CROSSHAIR, UiCursor.CROSSHAIR);
        map.put(SceneCursor.NONE, UiCursor.NONE);
        map.put(SceneCursor.EW_RESIZE, UiCursor.EW_RESIZE);
        map.put(SceneCursor.NS_RESIZE, UiCursor.NS_RESIZE);
        map.put(SceneCursor.HELP, UiCursor.HELP);
        return map;
    }
}
