package club.heiqi.uilib.ui.container.experimental.minecraft;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.gui.inventory.GuiContainer;

import club.heiqi.uilib.ui.input.UiKeyboardCaptureState;

/** early mixin 与显式 attach host 之间的静态、弱引用 phase callback。 */
public final class GuiContainerScenePhaseHook {
    private static final Map<GuiContainer, GuiContainerLongEntryHost> HOSTS =
            new WeakHashMap<GuiContainer, GuiContainerLongEntryHost>();
    private static final List<String> PHASE_ORDER = Collections.unmodifiableList(Arrays.asList(
            "VANILLA_BACKGROUND", "SCENE_MAIN", "VANILLA_SLOT_FOREGROUND", "THIRD_PARTY_OBJECTS",
            "SCENE_OVERLAY", "CARRIED", "VANILLA_THIRD_PARTY_TOOLTIP"));

    private GuiContainerScenePhaseHook() {}

    static synchronized void attach(GuiContainer screen, GuiContainerLongEntryHost host) {
        GuiContainerLongEntryHost previous = HOSTS.put(screen, host);
        if (previous != null && previous != host) {
            HOSTS.put(screen, previous);
            host.close();
            throw new IllegalStateException("GuiContainer already has a scene host");
        }
        tempLog("attach screen={}", screen.getClass().getName());
    }

    static synchronized void detach(GuiContainer screen, GuiContainerLongEntryHost host) {
        if (HOSTS.get(screen) == host) HOSTS.remove(screen);
    }

    /** draw background 后的 scene main phase。 */
    public static void afterBackground(GuiContainer screen, int mouseX, int mouseY, float partialTicks) {
        GuiContainerLongEntryHost host = find(screen);
        if (host != null) host.paintMain(mouseX, mouseY, partialTicks);
    }

    /** Slot/foreground/第三方 objects 后、carried 前的 scene overlay phase。 */
    public static void beforeCarried(GuiContainer screen) {
        GuiContainerLongEntryHost host = find(screen);
        if (host != null) host.paintOverlay();
    }

    /** draw tail 的资源帧封板。 */
    public static void afterDraw(GuiContainer screen) {
        GuiContainerLongEntryHost host = find(screen);
        if (host != null) host.finishDraw();
    }

    /** pointer DOWN 返回 true 时 mixin 取消 vanilla。 */
    public static boolean pointerDown(GuiContainer screen, int x, int y, int button) {
        GuiContainerLongEntryHost host = find(screen);
        return host != null && host.pointerDown(x, y, button);
    }

    /** drag MOVE 返回 true 时 mixin 取消 vanilla。 */
    public static boolean pointerMove(GuiContainer screen, int x, int y) {
        GuiContainerLongEntryHost host = find(screen);
        return host != null && host.pointerMove(x, y);
    }

    /** pointer UP 返回 true 时 mixin 取消 vanilla。 */
    public static boolean pointerUp(GuiContainer screen, int x, int y, int button) {
        GuiContainerLongEntryHost host = find(screen);
        return host != null && host.pointerUp(x, y, button);
    }

    /** key activation 返回 true 时 mixin 取消 vanilla。 */
    public static boolean keyTyped(GuiContainer screen, int keyCode) {
        if (UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured()) return false;
        GuiContainerLongEntryHost host = find(screen);
        return host != null && host.keyTyped(keyCode);
    }

    /** screen close 时幂等 detach/dispose。 */
    public static void close(GuiContainer screen) {
        GuiContainerLongEntryHost host = find(screen);
        if (host != null) host.close();
    }

    /** P3 真机闭环临时详细日志；最终验收前必须清理。 */
    public static void tempLog(String message, Object... arguments) {
        ExperimentalContainerDiagnostics.log(message, arguments);
    }

    /** 返回不可变 phase 合同，供同包契约测试和诊断页展示。 */
    static List<String> phaseOrder() { return PHASE_ORDER; }

    private static synchronized GuiContainerLongEntryHost find(GuiContainer screen) {
        return HOSTS.get(screen);
    }
}
