package club.heiqi.uilib.ui.screen;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.host.UiHostRenderSupport;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.host.lwjgl.SceneLwjgl3ifyTextBridge;
import club.heiqi.uilib.ui.scene.host.lwjgl.SceneTextBridgeLifecycle;

/**
 * Minecraft GuiScreen 到平台无关 scene 渲染面的桥接外壳。
 *
 * <h3>真机闸门诊断插桩</h3>
 * <p>本壳内置可开关诊断日志（日志名 {@code QzUiLib/McScreenBridge}），用于在沙箱无 GUI、
 * 只能靠真机验收时一次性收集足够信息，减少反复重启尝试。默认开启，可用 JVM 参数
 * {@code -Dqzuilib.scene.bridge.debug=false} 关闭。覆盖真机闸门重点风险：</p>
 * <ul>
 *   <li>FBO 泄漏：onGuiClosed 记录 close 前 FBO 离屏层数 + 三步释放各自成败；drawScreen 边缘触发
 *       记录离屏层池增长（稳态零日志，持续增长即泄漏）。</li>
 *   <li>实例泄漏：构造/关闭维护存活实例计数，反复开关后应回基线。</li>
 *   <li>GUI Scale 命中偏移：首帧记录 native / scaled / scaleFactor / mouse 坐标，供对照命中是否偏移。</li>
 *   <li>渲染异常：surface.render 抛异常时记录后重抛（不改行为，仅补日志定位）。</li>
 *   <li>ESC 返回：记录 ESC 决策路径（returnScreen / currentScreen / 是否返回）。</li>
 * </ul>
 */
public abstract class McScreenBridge extends GuiScreen {

    /** 真机闸门诊断日志。 */
    private static final Logger LOG = LogManager.getLogger("QzUiLib/McScreenBridge");

    /** 诊断开关：默认关（与 Config 调试开关默认值一致），{@code -Dqzuilib.scene.bridge.debug=true} 可开。 */
    private static final boolean DEBUG =
            "true".equalsIgnoreCase(System.getProperty("qzuilib.scene.bridge.debug", "false"));

    /** 当前存活的桥接实例数（反复开关泄漏指标，关闭后应回基线）。 */
    private static final AtomicInteger LIVE_INSTANCE_COUNT = new AtomicInteger();

    /** 累计打开次数（真机反复开关计数）。 */
    private static final AtomicInteger TOTAL_OPENED_COUNT = new AtomicInteger();

    private static final Method KEYBOARD_ENABLE_REPEAT_EVENTS = resolveKeyboardEnableRepeatEvents();
    private static final int KEY_ESCAPE = 1;

    private final GuiScreen returnScreen;
    private final UiSurface surface;

    /** 屏幕生命周期内复用的 Minecraft 宿主适配器，保留图片 renderer 缓存。 */
    private final UiRuntimeAdapters runtimeAdapters = UiRuntimeAdapters.minecraftDefaults();

    /** 通用宿主唯一拥有的 lwjgl3ify 文本桥。 */
    private final SceneLwjgl3ifyTextBridge textBridge;

    /** 文本桥注册状态与宿主 external mode 的生命周期协调器。 */
    private final SceneTextBridgeLifecycle textBridgeLifecycle = new SceneTextBridgeLifecycle();

    /** 当前壳的诊断标签（实际子类简名，区分三个 demo）。 */
    private final String screenLabel;

    /** 跨帧复用的绘制上下文合成器，避免每帧借用离屏资源后无法集中释放。 */
    private final PaintContextCompositor paintContextCompositor = new PaintContextCompositor();

    /** 跨帧复用的主图层快照服务，随屏幕关闭统一释放持有的渲染资源。 */
    private final UiMainLayerSnapshotService mainLayerSnapshotService = new UiMainLayerSnapshotService();

    /** 首帧诊断是否已打印（initGui 重置，使 resize/GUI Scale 变化后重新诊断）。 */
    private boolean firstFrameLogged;

    /** 上次记录的离屏层池大小（边缘触发用，-1 表示尚未记录）。 */
    private int lastPooledLayerCount = -1;

    /** 上次记录的快照池大小（边缘触发用，-1 表示尚未记录）。 */
    private int lastSnapshotPoolSize = -1;

    /**
     * 创建 MC 屏幕桥接外壳。
     *
     * @param returnScreen 关闭后返回的父界面
     * @param surface scene 渲染面
     */
    protected McScreenBridge(GuiScreen returnScreen, UiSurface surface) {
        this.returnScreen = returnScreen;
        this.surface = surface;
        this.textBridge = new SceneLwjgl3ifyTextBridge(surface::pushText);
        this.screenLabel = getClass().getSimpleName();
        if (DEBUG) {
            int live = LIVE_INSTANCE_COUNT.incrementAndGet();
            int total = TOTAL_OPENED_COUNT.incrementAndGet();
            LOG.info("[{}] 构造桥接壳: 存活实例={}, 累计打开={}（反复开关后存活数应回基线, 持续增长=screen 实例泄漏）",
                    screenLabel, Integer.valueOf(live), Integer.valueOf(total));
        }
    }

    @Override
    public void initGui() {
        enableRepeatEventsReflectively(true);
        // resize / GUI Scale 变化会再次触发 initGui，重置后下一帧重新打印首帧诊断。
        firstFrameLogged = false;
        // Bug3：启用指针按钮旁路 —— 本壳重写 mouseClicked/mouseMovedOrUp 后，
        // 按钮事件改走 MC 回调（事件驱动，不丢边沿），poll 停产 button 边沿避免 double-dispatch。
        surface.setExternalPointerMode(true);
        // 文本桥由通用宿主统一拥有；不可用或注册失败时保持 char 降级路径。
        textBridgeLifecycle.init(new SceneTextBridgeLifecycle.Registration() {
            @Override
            public boolean register() {
                return textBridge.register();
            }

            @Override
            public void unregister() {
                textBridge.unregister();
            }
        }, new SceneTextBridgeLifecycle.Mode() {
            @Override
            public void setExternalTextMode(boolean external) {
                surface.setExternalTextMode(external);
            }
        });
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Minecraft minecraft = Minecraft.getMinecraft();
        int nativeWidth = Math.max(1, minecraft.displayWidth);
        int nativeHeight = Math.max(1, minecraft.displayHeight);

        if (DEBUG && !firstFrameLogged) {
            logFirstFrameDiagnostics(minecraft, mouseX, mouseY, nativeWidth, nativeHeight);
            firstFrameLogged = true;
        }

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        try {
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, nativeWidth, nativeHeight, 0.0D, -1000.0D, 1000.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            try {
                GL11.glLoadIdentity();
                UiHostRenderSupport.prepareMainUiRenderState();
                paintContextCompositor.beginFrame();
                mainLayerSnapshotService.beginFrame();
                try {
                    UiRenderContext context = new UiRenderContext(nativeWidth, nativeHeight, mouseX, mouseY,
                            partialTicks, paintContextCompositor, mainLayerSnapshotService,
                            runtimeAdapters);
                    surface.render(nativeWidth, nativeHeight, context, 0, 0);
                } catch (RuntimeException renderError) {
                    if (DEBUG) {
                        LOG.error("[" + screenLabel + "] surface.render 抛 RuntimeException（新壳渲染失败，将重抛冒泡）",
                                renderError);
                    }
                    throw renderError;
                } catch (LinkageError renderError) {
                    if (DEBUG) {
                        LOG.error("[" + screenLabel + "] surface.render 抛 LinkageError（新壳渲染失败，将重抛冒泡）",
                                renderError);
                    }
                    throw renderError;
                } finally {
                    mainLayerSnapshotService.finishFrame();
                    paintContextCompositor.finishFrame();
                }
            } finally {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(previousMatrixMode);
        }

        if (DEBUG) {
            logResourcePoolEdgeChange();
        }
    }

    /**
     * MC 鼠标按下回调（1.7.10 签名：{@code protected void mouseClicked(int mouseX, int mouseY, int button)}）。
     *
     * <p>Bug3 修复：每次物理按下必回调一次（事件驱动，不丢边沿），把事件 push 进输入源旁路入口，
     * 绕开 poll 差分对"长帧内 DOWN+UP 完成往返"的系统性丢失。</p>
     *
     * <p>MC 回调坐标是 scaled 逻辑像素，不能无损反推物理坐标；这里只透传兼容参数，
     * 输入源在 push 时从与 MOVE 同源的平台 reader 读取权威物理坐标。</p>
     *
     * @param mouseX MC scaled 逻辑像素 X
     * @param mouseY MC scaled 逻辑像素 Y
     * @param button LWJGL button code（0=左，1=右，2=中）
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        surface.onPointerButton(ScenePointerAction.BUTTON_DOWN,
                mouseX, mouseY, mapButton(button), System.nanoTime());
        if (DEBUG) {
            LOG.info("[{}] mouseClicked: callbackScaled=({},{})，物理坐标由输入 reader 读取，button={}",
                    screenLabel, Integer.valueOf(mouseX), Integer.valueOf(mouseY),
                    Integer.valueOf(button));
        }
    }

    /**
     * MC 鼠标释放/移动回调（1.7.10 签名：{@code protected void mouseMovedOrUp(int mouseX, int mouseY, int which)}）。
     *
     * <p>{@code which >= 0} 是按钮释放；{@code which == -1} 是 mouseClickMove 的内部 move 通知。
     * 按钮释放走旁路 push（与 mouseClicked 对称），move 继续走 poll（不动）。</p>
     *
     * @param mouseX MC scaled 逻辑像素 X
     * @param mouseY MC scaled 逻辑像素 Y
     * @param which  按钮 code（≥0 表示该按钮释放）；-1 表示 move（不处理）
     */
    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        super.mouseMovedOrUp(mouseX, mouseY, which);
        if (which < 0) {
            // which == -1 是拖拽 move 通知，poll 路径已覆盖，不重复 push
            return;
        }
        surface.onPointerButton(ScenePointerAction.BUTTON_UP,
                mouseX, mouseY, mapButton(which), System.nanoTime());
        if (DEBUG) {
            LOG.info("[{}] mouseMovedOrUp(BUTTON_UP): callbackScaled=({},{})，物理坐标由输入 reader 读取，button={}",
                    screenLabel, Integer.valueOf(mouseX), Integer.valueOf(mouseY),
                    Integer.valueOf(which));
        }
    }

    /**
     * 将 LWJGL/MC button code 映射为 {@link SceneMouseButton}。
     *
     * <p>与 {@code LwjglInputSource.mapButtonCode} 同表，但桥接层不能依赖平台包私有静态方法，
     * 故在此重写一份等价实现。两表必须保持同步。</p>
     *
     * @param button MC/LWJGL button code
     * @return 平台无关鼠标按钮枚举
     */
    private static SceneMouseButton mapButton(int button) {
        switch (button) {
            case 0: return SceneMouseButton.LEFT;
            case 1: return SceneMouseButton.RIGHT;
            case 2: return SceneMouseButton.MIDDLE;
            case 3: return SceneMouseButton.BUTTON_4;
            case 4: return SceneMouseButton.BUTTON_5;
            default: return SceneMouseButton.NONE;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        surface.onKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
        if (keyCode == KEY_ESCAPE) {
            Minecraft minecraft = Minecraft.getMinecraft();
            boolean willReturn = returnScreen != null && minecraft != null && minecraft.currentScreen == null;
            if (DEBUG) {
                LOG.info("[{}] ESC 按下: returnScreen={}, currentScreen={}, 是否返回父界面={}",
                        screenLabel,
                        returnScreen != null ? returnScreen.getClass().getSimpleName() : "null",
                        minecraft != null && minecraft.currentScreen != null
                                ? minecraft.currentScreen.getClass().getSimpleName() : "null",
                        Boolean.valueOf(willReturn));
            }
            if (willReturn) {
                minecraft.displayGuiScreen(returnScreen);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        int pooledLayersBeforeClose = DEBUG ? paintContextCompositor.__getPooledLayerCount() : 0;
        int snapshotPoolBeforeClose = DEBUG ? mainLayerSnapshotService.__getSnapshotPoolSize() : 0;
        boolean surfaceDisposed = false;
        boolean compositorClosed = false;
        boolean snapshotClosed = false;
        try {
            try {
                surface.dispose();
                surfaceDisposed = true;
            } finally {
                try {
                    paintContextCompositor.close();
                    compositorClosed = true;
                } finally {
                    mainLayerSnapshotService.close();
                    snapshotClosed = true;
                }
            }
        } finally {
            try {
                textBridgeLifecycle.close(new SceneTextBridgeLifecycle.Registration() {
                    @Override
                    public boolean register() {
                        return textBridge.register();
                    }

                    @Override
                    public void unregister() {
                        textBridge.unregister();
                    }
                }, new SceneTextBridgeLifecycle.Mode() {
                    @Override
                    public void setExternalTextMode(boolean external) {
                        surface.setExternalTextMode(external);
                    }
                });
            } finally {
                try {
                    // 文本桥注销失败或 surface.dispose 抛异常时也必须回到降级模式。
                    surface.setExternalTextMode(false);
                } finally {
                    enableRepeatEventsReflectively(false);
                    // Bug3：关闭指针旁路，回到 poll 路径（避免下一界面若复用同一输入源时 button 差分被误停产）
                    surface.setExternalPointerMode(false);
                    if (DEBUG) {
                        int live = LIVE_INSTANCE_COUNT.decrementAndGet();
                        LOG.info("[{}] onGuiClosed 资源释放: surface.dispose={}, compositor.close={}（释放前 FBO 离屏层={}）,"
                                        + " snapshot.close={}（释放前快照={}）, 剩余存活实例={}",
                                screenLabel, Boolean.valueOf(surfaceDisposed), Boolean.valueOf(compositorClosed),
                                Integer.valueOf(pooledLayersBeforeClose), Boolean.valueOf(snapshotClosed),
                                Integer.valueOf(snapshotPoolBeforeClose), Integer.valueOf(live));
                        if (!surfaceDisposed || !compositorClosed || !snapshotClosed) {
                            LOG.error("[{}] 资源释放不完整！某一步抛异常未执行完, FBO/纹理可能泄漏, 检查上方堆栈", screenLabel);
                        }
                    }
                    super.onGuiClosed();
                }
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * 返回当前桥接的 scene 渲染面，供子类接入宿主旁路桥。
     *
     * @return scene 渲染面
     */
    protected UiSurface getSurface() {
        return surface;
    }

    /**
     * 打印首帧诊断（native / scaled / scaleFactor / mouse 坐标），供真机对照 GUI Scale 命中是否偏移。
     *
     * <p>诊断兜底捕获所有异常，绝不让诊断本身影响渲染主流程。</p>
     *
     * @param minecraft MC 客户端
     * @param mouseX MC 传入鼠标 X（逻辑像素，已按 scaleFactor 缩放）
     * @param mouseY MC 传入鼠标 Y（逻辑像素）
     * @param nativeWidth 原生像素宽
     * @param nativeHeight 原生像素高
     */
    private void logFirstFrameDiagnostics(Minecraft minecraft, int mouseX, int mouseY,
            int nativeWidth, int nativeHeight) {
        try {
            ScaledResolution scaledResolution = new ScaledResolution(minecraft, nativeWidth, nativeHeight);
            int scaledWidth = scaledResolution.getScaledWidth();
            int scaledHeight = scaledResolution.getScaledHeight();
            int scaleFactor = scaledResolution.getScaleFactor();
            LOG.info("[{}] 首帧诊断: native={}x{}, scaled={}x{}, scaleFactor={}; surface.render 用 native 坐标系布局",
                    screenLabel, Integer.valueOf(nativeWidth), Integer.valueOf(nativeHeight),
                    Integer.valueOf(scaledWidth), Integer.valueOf(scaledHeight), Integer.valueOf(scaleFactor));
            LOG.info("[{}] GUI Scale 命中诊断: mouse(MC 逻辑像素)=({},{}), 预期对应 native=({},{}); "
                            + "context 用 native({}x{}) 但 mouse 是逻辑像素, scaleFactor!=1 时命中可能偏移, 真机重点验 hover/click 落点",
                    screenLabel, Integer.valueOf(mouseX), Integer.valueOf(mouseY),
                    Integer.valueOf(mouseX * scaleFactor), Integer.valueOf(mouseY * scaleFactor),
                    Integer.valueOf(nativeWidth), Integer.valueOf(nativeHeight));
        } catch (Throwable diagError) {
            LOG.warn("[{}] 首帧 GUI Scale 诊断失败（不影响渲染）: {}", screenLabel, diagError.toString());
        }
    }

    /**
     * 边缘触发记录渲染资源池大小：仅在离屏层池或快照池大小变化时打印一行。
     *
     * <p>稳态零日志；opacity 帧首次借 FBO 时池从 0 增长后稳定。若反复滚动/交互中池持续增长不收敛，即 FBO 泄漏信号。</p>
     */
    private void logResourcePoolEdgeChange() {
        int pooledLayers = paintContextCompositor.__getPooledLayerCount();
        int snapshotPool = mainLayerSnapshotService.__getSnapshotPoolSize();
        if (pooledLayers != lastPooledLayerCount || snapshotPool != lastSnapshotPoolSize) {
            LOG.info("[{}] 渲染资源池变化: FBO 离屏层={}（上次 {}）, 快照池={}/{}（上次 {}）",
                    screenLabel, Integer.valueOf(pooledLayers), Integer.valueOf(lastPooledLayerCount),
                    Integer.valueOf(snapshotPool), Integer.valueOf(mainLayerSnapshotService.__getMaxPooledSnapshots()),
                    Integer.valueOf(lastSnapshotPoolSize));
            lastPooledLayerCount = pooledLayers;
            lastSnapshotPoolSize = snapshotPool;
        }
    }

    /**
     * 通过反射调用 Keyboard.enableRepeatEvents。
     *
     * @param enable true 启用键盘重复，false 关闭
     */
    private static void enableRepeatEventsReflectively(boolean enable) {
        if (KEYBOARD_ENABLE_REPEAT_EVENTS == null) {
            return;
        }
        try {
            KEYBOARD_ENABLE_REPEAT_EVENTS.invoke(null, Boolean.valueOf(enable));
        } catch (Exception exception) {
            // 静默降级。
        }
    }

    private static Method resolveKeyboardEnableRepeatEvents() {
        Class<?> keyboardClass = resolveKeyboardClass();
        if (keyboardClass == null) {
            return null;
        }
        try {
            return keyboardClass.getMethod("enableRepeatEvents", boolean.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Class<?> resolveKeyboardClass() {
        try {
            return Class.forName("org.lwjglx.input.Keyboard");
        } catch (Exception exception) {
            try {
                return Class.forName("org.lwjgl.input.Keyboard");
            } catch (Exception fallbackException) {
                return null;
            }
        }
    }
}
