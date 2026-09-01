package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudVisibility;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;

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
    /** 最近一帧各窗口的权威放置盒（视口逻辑 px；每帧 render 清空重建）。 */
    private final HashMap<String, AnchorRect> lastPlacements = new HashMap<String, AnchorRect>();

    /** 创建消费指定服务注册表的 HUD host；唯一生产构造点，自附到服务供投放方查放置盒。 */
    public SceneHudHost(ClientHudServiceImpl service) {
        this(service.registry(), new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance()),
                new HudScaleSetting());
        service.attachHost(this);
    }

    SceneHudHost(HudRegistry registry, SceneTextMeasurer measurer) {
        this(registry, measurer, new HudScaleSetting());
    }

    SceneHudHost(HudRegistry registry, SceneTextMeasurer measurer, HudScaleSetting scaleSetting) {
        this.registry = registry;
        this.measurer = measurer;
        this.scaleSetting = scaleSetting;
    }

    /** 临时诊断:每 300 帧打印各保留窗口状态(真机定位 HUD 不渲染用)。 */
    private int hostDiagFrames;

    /** 临时诊断:chat3 窗口本帧实测盒(controller 侧逐帧诊断读取;验证后删除)。 */
    public static volatile String __diagChat3Box = "none";

    /** 在 render 主线程执行一帧：挂载缺失窗口 → 测量 → 四角锚定 → 逐窗口帧循环。 */
    public void render(UiRenderBackend backend, HudViewportMetrics viewport, boolean inWorld, boolean screenOpen) {
        render(backend, viewport.getWidth(), viewport.getHeight(), inWorld, screenOpen);
    }

    /** 在 render 主线程执行一帧：挂载缺失窗口 → 测量 → 四角锚定 → 逐窗口帧循环。 */
    public void render(UiRenderBackend backend, int width, int height, boolean inWorld, boolean screenOpen) {
        float scale = scaleSetting.get();
        width = Math.max(1, (int) Math.floor(width / scale));
        height = Math.max(1, (int) Math.floor(height / scale));
        backend = backend.scaled(scale);
        HudInsets safeInsets = registry.avoidanceInsets(this::reportProviderFailure);
        lastPlacements.clear();
        ArrayList<MeasuredHud> measured = new ArrayList<MeasuredHud>();
        Set<String> registered = new HashSet<String>();
        Set<String> visible = new HashSet<String>();
        long frameTimeNanos = System.nanoTime();
        for (HudRegistry.Entry entry : registry.frameEntries()) {
            registered.add(entry.spec.getId());
            RetainedWindow window = retained.get(entry.spec.getId());
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
        for (HudRegistry.Entry entry : registry.frameEntries()) {
            RetainedWindow window = retained.get(entry.spec.getId());
            if (window == null || !visible.contains(entry.spec.getId())) continue;
            LayoutBox box = window.measure(width, height);
            // 临时诊断:chat3 窗口实测盒(含空窗跳过标记;验证后删除)
            if ("qzuilib:chat3".equals(entry.spec.getId())) {
                if (window.isEmptyContent()) {
                    __diagChat3Box = "EMPTY";
                } else {
                    __diagChat3Box = box.getWidth() + "x" + box.getHeight();
                }
            }
            if (window.isEmptyContent()) continue;
            int minimum = entry.spec.getMinWidth() == 0
                    ? Math.min(HudTokens.NORMAL.minWidth, entry.spec.getMaxWidth()) : entry.spec.getMinWidth();
            int measuredWidth = Math.max(minimum, Math.min(entry.spec.getMaxWidth(), box.getWidth()));
            measured.add(new MeasuredHud(entry, measuredWidth, box.getHeight()));
        }
        if (++hostDiagFrames >= 300) {
            hostDiagFrames = 0;
            for (HudRegistry.Entry entry : registry.frameEntries()) {
                RetainedWindow window = retained.get(entry.spec.getId());
                if (window == null) {
                    continue;
                }
                LayoutBox box = (LayoutBox) window.root().getCachedLayout();
                MyMod.LOG.info("[HudHostDiag] id={} visible={} empty={} box={}",
                        entry.spec.getId(),
                        Boolean.valueOf(visible.contains(entry.spec.getId())),
                        Boolean.valueOf(window.isEmptyContent()),
                        box == null ? "null" : box.getWidth() + "x" + box.getHeight());
            }
        }
        placeAndFrame(backend, measured, width, height, safeInsets, frameTimeNanos);
    }

    /**
     * 四角锚定 + 同锚点稳定堆叠（视口锚定数学在 {@link SceneAnchorResolver}，
     * 这里只做排序、offset 累积与帧派发）。
     */
    private void placeAndFrame(UiRenderBackend backend, ArrayList<MeasuredHud> measured,
            int width, int height, HudInsets safeInsets, long frameTimeNanos) {
        ArrayList<MeasuredHud> sorted = new ArrayList<MeasuredHud>(measured);
        sorted.sort(Comparator.comparing((MeasuredHud item) -> item.entry.spec.getAnchor())
                .thenComparingInt(item -> item.entry.spec.getStackOrder())
                .thenComparingLong(item -> item.entry.registrationOrder));
        EnumMap<HudAnchor, Integer> offsets = new EnumMap<HudAnchor, Integer>(HudAnchor.class);
        for (MeasuredHud item : sorted) {
            HudSpec spec = item.entry.spec;
            int offset = offsets.containsKey(spec.getAnchor()) ? offsets.get(spec.getAnchor()) : 0;
            SceneAnchorResolver.ResolvedViewport placed = SceneAnchorResolver.resolveViewport(
                    isRight(spec.getAnchor()), isBottom(spec.getAnchor()),
                    width, height, item.width, item.height, spec.getMargin(),
                    safeInsets.getLeft(), safeInsets.getTop(), safeInsets.getRight(), safeInsets.getBottom(),
                    offset);
            RetainedWindow window = retained.get(spec.getId());
            lastPlacements.put(spec.getId(),
                    new AnchorRect(placed.getX(), placed.getY(), placed.getWidth(), placed.getHeight()));
            window.frame(backend, placed.getX(), placed.getY(), placed.getWidth(), placed.getHeight(),
                    frameTimeNanos);
            offsets.put(spec.getAnchor(), offset + placed.getHeight() + HudTokens.STACK_GAP);
        }
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
        MeasuredHud(HudRegistry.Entry entry, int width, int height) {
            this.entry = entry; this.width = Math.max(1, width); this.height = Math.max(1, height);
        }
    }

    /**
     * 最近一帧某窗口的权威放置盒（视口逻辑 px），未放置（不可见/空内容/已注销/无 host 帧）时 null。
     *
     * <p>投放方（如 chat3 命中检测）以宿主实际放置为准——含堆叠偏移、安全区与 clamp——
     * 替代自行反推锚点数学的第二事实源。与 render 同为客户端主线程，逐帧重建。</p>
     */
    public AnchorRect currentPlacement(String hudId) {
        return hudId == null ? null : lastPlacements.get(hudId);
    }

    /** 释放世界级保留窗口；registration 仍归 mod 持有，重连后自动重建。 */
    public void clearWorld() {
        for (RetainedWindow window : retained.values()) window.dispose();
        retained.clear();
        lastPlacements.clear();
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
        private final SceneNode root;
        private final SceneNode content;
        private final SceneRuntime runtime;
        private final SceneLayoutEngine layoutEngine;
        private final SceneFramePipeline pipeline;

        RetainedWindow(HudRegistry.Entry entry, SceneTextMeasurer measurer) {
            HudTokens tokens = HudTokens.NORMAL;
            runtime = new SceneRuntime(measurer);
            layoutEngine = new SceneLayoutEngine(measurer);
            pipeline = new SceneFramePipeline(runtime, layoutEngine, new ScenePaintEngine(measurer),
                    new ScenePaintReplayer(), measurer, null);
            SceneNode shell = SceneNode.column().setHitTestable(false).setClipChildren(true)
                    .setWidthSizing(SceneNode.WidthSizing.SHRINK);
            if (entry.spec.isChrome()) {
                // 默认外壳:半透明背景 + 内边距;chrome(false) 时内容直接浮在画面上(现代悬浮风格)
                shell.setPadding(tokens.paddingY, tokens.paddingX, tokens.paddingY, tokens.paddingX)
                        .setBackgroundColor(club.heiqi.uilib.ui.scene.paint.SceneChromeTokens.HUD_SHELL_BG);
            }
            root = shell;
            SceneNode contentRoot = entry.factory.build(runtime);
            if (contentRoot == null) {
                throw new IllegalStateException("HUD window factory must return a content root: "
                        + entry.spec.getId());
            }
            root.appendChild(contentRoot);
            content = contentRoot;
            runtime.flush();
        }

        /** 测量（含外壳）：layout 后返回外壳盒。 */
        LayoutBox measure(int width, int height) {
            layoutEngine.layout(root, new Constraints(Math.max(1, width), Math.max(1, height)));
            return (LayoutBox) root.getCachedLayout();
        }

        /** 内容子树无可见尺寸（signal 卸载/空文本）→ 整窗隐藏，对齐旧「空快照不显示」语义。 */
        boolean isEmptyContent() {
            Object box = content.getCachedLayout();
            return box == null || ((LayoutBox) box).getWidth() <= 0 || ((LayoutBox) box).getHeight() <= 0;
        }

        /** 窗口帧循环：与 UI 页面同源的 11 阶段帧管线，并以放置盒硬裁剪（内容超长不溢出窗口）。 */
        void frame(UiRenderBackend backend, int x, int y, int width, int height, long frameTimeNanos) {
            runtime.__tickFrame(frameTimeNanos);
            pipeline.run(root, width, height, backend, x, y, frameTimeNanos,
                    new club.heiqi.uilib.ui.scene.layout.AnchorRect(0, 0, width, height));
        }

        void dispose() { runtime.dispose(); }

        SceneNode root() { return root; }
    }
}
