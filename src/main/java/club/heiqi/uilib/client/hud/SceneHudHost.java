package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudVisibility;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 保留式 scene HUD 宿主：每个注册项一个「虚拟窗口」，内容由 {@code HudWindowFactory}
 * 用与 UI 页面完全相同的 scene 代码构建（控件 + signal），帧循环复用 {@link SceneFramePipeline}
 * （无输入源退化模式），layout/paint/replay/settle 与 UI 页面同源。
 *
 * <p>无输入、无 Widget/GuiScreen 生命周期；四角锚定与堆叠由 {@link HudLayoutEngine} 负责，
 * 宿主只做「挂载 → 测量 → 锚定 → 帧循环」。</p>
 */
public final class SceneHudHost {
    private final HudRegistry registry;
    private final HudLayoutEngine hudLayout = new HudLayoutEngine();
    private final SceneTextMeasurer measurer;
    private final Map<String, RetainedWindow> retained = new HashMap<String, RetainedWindow>();
    private final HudScaleSetting scaleSetting;

    /** 创建消费指定服务注册表的 HUD host。 */
    public SceneHudHost(ClientHudServiceImpl service) {
        this(service.registry(), new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance()),
                new HudScaleSetting());
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
        float scale = scaleSetting.get();
        width = Math.max(1, (int) Math.floor(width / scale));
        height = Math.max(1, (int) Math.floor(height / scale));
        backend = scale == 1F ? backend : new ScaledHudBackend(backend, scale);
        HudInsets safeInsets = registry.avoidanceInsets(this::reportProviderFailure);
        ArrayList<HudLayoutEngine.MeasuredHud> measured = new ArrayList<HudLayoutEngine.MeasuredHud>();
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
            if (window.isEmptyContent()) continue;
            int minimum = entry.spec.getMinWidth() == 0
                    ? Math.min(HudTokens.NORMAL.minWidth, entry.spec.getMaxWidth()) : entry.spec.getMinWidth();
            int measuredWidth = Math.max(minimum, Math.min(entry.spec.getMaxWidth(), box.getWidth()));
            measured.add(new HudLayoutEngine.MeasuredHud(entry, measuredWidth, box.getHeight()));
        }
        for (HudLayoutEngine.PlacedHud placed : hudLayout.layout(measured, width, height, safeInsets)) {
            RetainedWindow window = retained.get(placed.entry.spec.getId());
            window.frame(backend, placed, frameTimeNanos);
        }
    }

    /** 释放世界级保留窗口；registration 仍归 mod 持有，重连后自动重建。 */
    public void clearWorld() {
        for (RetainedWindow window : retained.values()) window.dispose();
        retained.clear();
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
            root = SceneNode.column().setHitTestable(false).setClipChildren(true)
                    .setPadding(tokens.paddingY, tokens.paddingX, tokens.paddingY, tokens.paddingX)
                    .setBackgroundColor(0xA0000000)
                    .setWidthSizing(SceneNode.WidthSizing.SHRINK);
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
        void frame(UiRenderBackend backend, HudLayoutEngine.PlacedHud placed, long frameTimeNanos) {
            runtime.__tickFrame(frameTimeNanos);
            pipeline.run(root, placed.width, placed.height, backend, placed.x, placed.y, frameTimeNanos,
                    new club.heiqi.uilib.ui.scene.layout.AnchorRect(0, 0, placed.width, placed.height));
        }

        void dispose() { runtime.dispose(); }

        SceneNode root() { return root; }
    }
}
