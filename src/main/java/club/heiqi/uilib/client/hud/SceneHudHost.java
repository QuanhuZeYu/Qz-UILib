package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudLayoutMetrics;
import club.heiqi.uilib.ui.hud.api.HudLayoutResolver;
import club.heiqi.uilib.ui.hud.api.HudLayoutService;
import club.heiqi.uilib.ui.hud.api.HudPlacement;
import club.heiqi.uilib.ui.hud.api.HudScaleState;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudToolbarLayer;
import club.heiqi.uilib.ui.hud.api.HudToolbarService;
import club.heiqi.uilib.ui.hud.api.HudVisibility;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.host.SceneHostAssembly;
import club.heiqi.uilib.ui.scene.host.SceneHostWindow;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 保留式 scene HUD 宿主：每个注册项一个「虚拟窗口」，内容由 {@code HudWindowFactory}
 * 用与 UI 页面完全相同的 scene 代码构建（控件 + signal），帧循环复用 {@link SceneFramePipeline}
 * （无输入源退化模式），layout/paint/replay/settle 与 UI 页面同源。
 *
 * <p>无输入、无 Widget/GuiScreen 生命周期；四角锚定数学在 {@link SceneAnchorResolver}（视口模式），
 * 宿主只做「挂载 → 测量 → 锚定/堆叠 → 帧循环」。</p>
 */
public final class SceneHudHost {
    private final HudRegistry registry;
    private final SceneTextMeasurer measurer;
    private final Map<String, RetainedWindow> retained = new HashMap<String, RetainedWindow>();
    private final HudScaleSetting scaleSetting;
    /** 最近一帧各窗口的实际视觉盒（物理 px；与 backend 端点舍入一致）。 */
    private final HashMap<String, AnchorRect> lastPlacements = new HashMap<String, AnchorRect>();
    private final Map<String, AnchorRect> lastLogicalPlacements = new HashMap<String, AnchorRect>();
    private final Map<String, Float> lastScales = new HashMap<String, Float>();
    /** 最近一帧安全区（打开态容器与关闭态 HUD 共用同一份安全区事实的对外端口）。 */
    private HudInsets lastSafeInsets = HudInsets.NONE;

    /** 创建消费指定服务注册表的 HUD host；唯一生产构造点在 {@code UiHudRenderListener}。 */
    public SceneHudHost(ClientHudServiceImpl service) {
        this(service.registry(), SceneHostAssembly.defaultMeasurer(), new HudScaleSetting());
    }

    SceneHudHost(HudRegistry registry, SceneTextMeasurer measurer) {
        this(registry, measurer, new HudScaleSetting());
    }

    SceneHudHost(HudRegistry registry, SceneTextMeasurer measurer, HudScaleSetting scaleSetting) {
        this.registry = registry;
        this.measurer = measurer;
        this.scaleSetting = scaleSetting;
    }

    /** 在 render 主线程执行一帧：挂载缺失窗口 → 测量 → 四角锚定 → 逐窗口帧循环。 */
    public void render(UiRenderBackend backend, HudViewportMetrics viewport, boolean inWorld, boolean screenOpen) {
        render(backend, viewport.getWidth(), viewport.getHeight(), inWorld, screenOpen);
    }

    /** 在 render 主线程执行一帧：挂载缺失窗口 → 测量 → 四角锚定 → 逐窗口帧循环。 */
    public void render(UiRenderBackend backend, int width, int height, boolean inWorld, boolean screenOpen) {
        final float globalScale = scaleSetting.get();
        HudInsets safeInsets = registry.avoidanceInsets(this::reportProviderFailure);
        safeInsets = new HudInsets(Math.round(safeInsets.getLeft() * globalScale),
                Math.round(safeInsets.getTop() * globalScale), Math.round(safeInsets.getRight() * globalScale),
                Math.round(safeInsets.getBottom() * globalScale));
        lastSafeInsets = safeInsets;
        lastPlacements.clear();
        lastLogicalPlacements.clear();
        lastScales.clear();
        // 外接工具栏注册表版本：本帧与保留窗口建立时的版本不一致 → 该窗口重建（接上/摘掉外接层）
        int toolbarRevision = HudToolbarService.getInstance().revision().get().intValue();
        ArrayList<MeasuredHud> measured = new ArrayList<MeasuredHud>();
        Set<String> registered = new HashSet<String>();
        Set<String> visible = new HashSet<String>();
        long frameTimeNanos = System.nanoTime();
        for (HudRegistry.Entry entry : registry.frameEntries()) {
            registered.add(entry.spec.getId());
            RetainedWindow window = retained.get(entry.spec.getId());
            if (window != null && window.toolbarRevision() != toolbarRevision) {
                // 工具栏注册/注销：保留窗口重建才能换掉外接层（注册变更低频，代价可接受）
                window.dispose();
                retained.remove(entry.spec.getId());
                window = null;
            }
            if (window == null) {
                try {
                    window = new RetainedWindow(entry, measurer);
                    retained.put(entry.spec.getId(), window);
                } catch (RuntimeException exception) {
                    // 单个窗口工厂失败仅跳过该 HUD，不影响其它窗口（对齐旧 provider 异常隔离语义）
                    reportWindowFailure(exception);
                    continue;
                }
            }
            if (visible(entry.spec.getVisibility(), inWorld, screenOpen)) {
                visible.add(entry.spec.getId());
            }
        }
        disposeInactive(registered);
        // 在任何窗口推进 signal/动画前采样全部倍率，帧中改动下一帧生效。
        // 倍率真值 = 统一缩放状态（HUD 自身能力）：不再取工具栏层的挂载倍率，
        // 未注册外接工具栏的 HUD 同样按统一倍率缩放。
        Map<String, Float> frameScales = new HashMap<String, Float>();
        for (Map.Entry<String, RetainedWindow> item : retained.entrySet()) {
            frameScales.put(item.getKey(), globalScale * unifiedScaleFactor(item.getKey()));
        }
        for (HudRegistry.Entry entry : registry.frameEntries()) {
            RetainedWindow window = retained.get(entry.spec.getId());
            if (window == null || !visible.contains(entry.spec.getId())) continue;
            float scale = frameScales.get(entry.spec.getId());
            int logicalWidth = Math.max(1, (int) Math.floor(width / scale));
            int logicalHeight = Math.max(1, (int) Math.floor(height / scale));
            LayoutBox box = window.measure(logicalWidth, logicalHeight);
            // 宿主合同（A2）：空窗 flush 照常、paint 跳过——signal 物化不被跳帧锁死，
            // 下一帧有内容即恢复绘制；投放方因此无须在宿主栈外强制 flush。
            if (window.isEmptyContent()) {
                window.settleWithoutPaint(logicalWidth, logicalHeight, frameTimeNanos);
                continue;
            }
            int minimum = entry.spec.getMinWidth() == 0
                    ? Math.min(HudTokens.MIN_WIDTH, entry.spec.getMaxWidth()) : entry.spec.getMinWidth();
            int measuredWidth = Math.max(minimum, Math.min(entry.spec.getMaxWidth(), box.getWidth()));
            measured.add(new MeasuredHud(entry, measuredWidth, box.getHeight(), scale));
        }
        placeAndFrame(backend, measured, width, height, safeInsets, frameTimeNanos, globalScale);
    }

    /**
     * 该 HUD 的统一缩放倍率：读 {@link HudToolbarService#scale(String)}（惰性创建），
     * 与打开态聊天屏、编辑态预览浮层同源；未注册外接工具栏的 HUD 不再恒为 1.0。
     */
    private static float unifiedScaleFactor(String hudId) {
        HudScaleState state = HudToolbarService.getInstance().scale(hudId);
        return state == null ? 1.0F : state.factor();
    }

    /**
     * 四角锚定 + 同锚点稳定堆叠（视口锚定数学在 {@link SceneAnchorResolver}，
     * 这里只做排序、offset 累积与帧派发）。
     */
    private void placeAndFrame(UiRenderBackend backend, ArrayList<MeasuredHud> measured,
            int width, int height, HudInsets safeInsets, long frameTimeNanos, float globalScale) {
        ArrayList<MeasuredHud> sorted = new ArrayList<MeasuredHud>(measured);
        sorted.sort(Comparator.comparing((MeasuredHud item) -> item.entry.spec.getAnchor())
                .thenComparingInt(item -> item.entry.spec.getStackOrder())
                .thenComparingLong(item -> item.entry.registrationOrder));
        EnumMap<HudAnchor, Integer> offsets = new EnumMap<HudAnchor, Integer>(HudAnchor.class);
        for (MeasuredHud item : sorted) {
            HudSpec spec = item.entry.spec;
            // 持久化度量上报（task-11 接线，仅新增本调用）：度量与下方 resolve 实参逐项一致；
            // 未挂 HudLayoutStore 时 observe 在同步块外快速返回，零额外开销、零行为变化。
            HudLayoutService.getInstance().observe(spec.getId(),
                    HudLayoutMetrics.of(width, height, item.width, item.height, safeInsets)
                            .offsetScale(globalScale));
            // 用户布局覆盖（会话内）：走统一解析数学，并脱离默认堆叠（不参与 offset 累积）。
            // 无覆盖时保持原四角锚定 + 同锚点堆叠，既有行为零回归。
            HudPlacement custom = HudLayoutService.getInstance().placement(spec.getId());
            if (custom != null) {
                RetainedWindow customWindow = retained.get(spec.getId());
                HudPlacement visualCustom = custom.withOffset(Math.round(custom.getOffsetX() * globalScale),
                        Math.round(custom.getOffsetY() * globalScale));
                AnchorRect rect = HudLayoutResolver.resolve(visualCustom, width, height,
                        item.width, item.height, safeInsets);
                framePlaced(backend, item, customWindow, rect, frameTimeNanos);
                continue;
            }
            int offset = offsets.containsKey(spec.getAnchor()) ? offsets.get(spec.getAnchor()) : 0;
            SceneAnchorResolver.ResolvedViewport placed = SceneAnchorResolver.resolveViewport(
                    isRight(spec.getAnchor()), isBottom(spec.getAnchor()),
                    width, height, item.width, item.height, Math.round(spec.getMargin() * globalScale),
                    safeInsets.getLeft(), safeInsets.getTop(), safeInsets.getRight(), safeInsets.getBottom(),
                    offset);
            RetainedWindow window = retained.get(spec.getId());
            framePlaced(backend, item, window,
                    new AnchorRect(placed.getX(), placed.getY(), placed.getWidth(), placed.getHeight()),
                    frameTimeNanos);
            offsets.put(spec.getAnchor(), offset + placed.getHeight() + Math.round(HudTokens.STACK_GAP * globalScale));
        }
    }

    private void framePlaced(UiRenderBackend backend, MeasuredHud item, RetainedWindow window,
            AnchorRect desired, long frameTimeNanos) {
        int x = Math.round(desired.getX() / item.scale);
        int y = Math.round(desired.getY() / item.scale);
        int width = Math.min(item.logicalWidth, Math.max(1, (int) Math.floor(desired.getWidth() / item.scale)));
        int height = Math.min(item.logicalHeight, Math.max(1, (int) Math.floor(desired.getHeight() / item.scale)));
        AnchorRect logical = new AnchorRect(x, y, width, height);
        int left = Math.round(x * item.scale);
        int top = Math.round(y * item.scale);
        String id = item.entry.spec.getId();
        lastLogicalPlacements.put(id, logical);
        lastScales.put(id, item.scale);
        // scaled() 连绝对原点一起转换；不能把视觉原点再次送进后端。
        lastPlacements.put(id, new AnchorRect(left, top,
                Math.round((x + width) * item.scale) - left,
                Math.round((y + height) * item.scale) - top));
        window.frame(backend.scaled(item.scale), x, y, width, height, frameTimeNanos);
    }

    private static boolean isRight(HudAnchor anchor) {
        return anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT;
    }

    private static boolean isBottom(HudAnchor anchor) {
        return anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT;
    }

    /** 预先测得的窗口内容尺寸（含外壳）。 */
    private static final class MeasuredHud {
        final HudRegistry.Entry entry;
        final int width;
        final int height;
        final float scale;
        final int logicalWidth;
        final int logicalHeight;
        MeasuredHud(HudRegistry.Entry entry, int width, int height, float scale) {
            this.entry = entry;
            this.scale = scale;
            this.logicalWidth = Math.max(1, width);
            this.logicalHeight = Math.max(1, height);
            this.width = (int) Math.ceil(logicalWidth * scale);
            this.height = (int) Math.ceil(logicalHeight * scale);
        }
    }

    /**
     * 最近一帧某窗口的权威放置盒（物理 px），未放置（不可见/空内容/已注销/无 host 帧）时 null。
     *
     * <p>投放方（如 chat3 命中检测）以宿主实际放置为准——含堆叠偏移、安全区与 clamp——
     * 替代自行反推锚点数学的第二事实源。与 render 同为客户端主线程，逐帧重建。</p>
     */
    public AnchorRect currentPlacement(String hudId) {
        return hudId == null ? null : lastPlacements.get(hudId);
    }

    /** 最近已绘制帧的合成倍率；没有放置时返回单位倍率。 */
    public float currentScaleFactor(String hudId) {
        Float scale = lastScales.get(hudId);
        return scale == null ? 1F : scale;
    }

    /** 最近已绘制帧传给 pipeline 的逻辑原点与裁剪尺寸。 */
    public AnchorRect currentLogicalPlacement(String hudId) {
        return lastLogicalPlacements.get(hudId);
    }

    /** @return 最近一帧安全区（未渲染过时 {@link HudInsets#NONE}）；供打开态容器共用同一事实。 */
    public HudInsets currentSafeInsets() {
        return lastSafeInsets;
    }

    /** 释放世界级保留窗口；registration 仍归 mod 持有，重连后自动重建。 */
    public void clearWorld() {
        for (RetainedWindow window : retained.values()) window.dispose();
        retained.clear();
        lastPlacements.clear();
        lastLogicalPlacements.clear();
        lastScales.clear();
    }

    private void disposeInactive(Set<String> active) {
        for (String id : new HashSet<String>(retained.keySet())) {
            if (!active.contains(id)) {
                retained.remove(id).dispose();
            }
        }
    }

    private static boolean visible(HudVisibility visibility, boolean inWorld, boolean screenOpen) {
        return inWorld && (visibility == HudVisibility.IN_WORLD || !screenOpen);
    }

    private void reportProviderFailure(RuntimeException exception) {
        MyMod.LOG.warn("HUD avoidance provider 本帧读取失败，已隔离", exception);
    }

    private void reportWindowFailure(RuntimeException exception) {
        MyMod.LOG.warn("HUD 窗口工厂挂载失败，已跳过该 HUD", exception);
    }

    /**
     * 单个注册项的保留虚拟窗口：外壳（host 默认皮肤）+ 工厂内容树 + 独立 scene 帧管线。
     *
     * <p>外壳统一提供背景、padding、子树裁剪与收缩宽度；内容树完全由 mod 的 scene 代码决定，
     * 内容空尺寸（signal 卸载或空文本）时整窗（含外壳）隐藏。</p>
     */
    static final class RetainedWindow {
        /** 窗口装配本体（外壳 + 内容 + 装饰层 + 独立帧管线；上提至 ui.scene.host 供 headless 共用）。 */
        private final SceneHostWindow window;
        /** 外接工具栏层（未注册该 HUD 的工具栏时为内容直通）。 */
        private final HudToolbarLayer.Result toolbarLayer;
        /** 建立本窗口时的工具栏注册表版本（宿主据此判断是否重建）。 */
        private final int toolbarRevision;

        RetainedWindow(HudRegistry.Entry entry, SceneTextMeasurer measurer) {
            final String hudId = entry.spec.getId();
            // 外接工具栏层：挂在内容盒外侧一条边，尺寸参与外框测量与放置（四边工具栏
            // 不遮挡主体）；未注册该 HUD 的工具栏时直通，子树与既有行为逐位一致。
            // 工具栏工厂失败只丢工具栏、HUD 主体照常显示（单点隔离）——隔离由 SceneHostWindow
            // 统一承担，这里只把挂载结果记进探针字段。
            final HudToolbarLayer.Result[] mounted = new HudToolbarLayer.Result[1];
            SceneHostWindow assembled = new SceneHostWindow(measurer,
                    SceneHostAssembly.defaultEnvironment(),
                    entry.spec.isChrome() ? SceneHostWindow.Shell.HUD_DEFAULT
                            : SceneHostWindow.Shell.BARE,
                    entry.factory::build,
                    (rt, contentRoot) -> {
                        HudToolbarLayer.Result layer = HudToolbarService.getInstance()
                                .mountLayer(rt, hudId, contentRoot);
                        mounted[0] = layer;
                        return layer.root();
                    },
                    failure -> MyMod.LOG.warn(
                            "HUD 外接层挂载失败，已跳过该工具栏: id={}", hudId, failure));
            this.window = assembled;
            this.toolbarLayer = mounted[0] != null
                    ? mounted[0] : HudToolbarLayer.passthrough(assembled.content());
            this.toolbarRevision = HudToolbarService.getInstance().revision().get().intValue();
        }

        /** 测量（含外壳）：layout 后返回外壳盒。 */
        LayoutBox measure(int width, int height) {
            return window.measure(width, height);
        }

        /** 空窗帧推进：flush/layout/settle 照常，不 paint 不 replay（投放方合同 A2）。 */
        void settleWithoutPaint(int width, int height, long frameTimeNanos) {
            window.settleWithoutPaint(width, height, frameTimeNanos);
        }

        /** 内容子树无可见尺寸（signal 卸载/空文本）→ 整窗隐藏（语义见 {@link SceneHostWindow}）。 */
        boolean isEmptyContent() {
            return window.isEmptyContent();
        }

        /** @return 建立本窗口时的工具栏注册表版本 */
        int toolbarRevision() {
            return toolbarRevision;
        }

        /** @return 外接工具栏层（测试/诊断探针；无注册时是内容直通） */
        HudToolbarLayer.Result toolbarLayer() {
            return toolbarLayer;
        }

        /** 窗口帧循环：与 UI 页面同源的帧管线，并以放置盒硬裁剪（内容超长不溢出窗口）。 */
        void frame(UiRenderBackend backend, int x, int y, int width, int height, long frameTimeNanos) {
            window.frame(backend, x, y, width, height, frameTimeNanos);
        }

        void dispose() {
            window.dispose();
        }

        SceneNode root() {
            return window.root();
        }
    }
}
