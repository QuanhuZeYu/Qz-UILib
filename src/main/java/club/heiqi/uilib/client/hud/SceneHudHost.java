package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudLine;
import club.heiqi.uilib.ui.hud.api.HudSnapshot;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudTone;
import club.heiqi.uilib.ui.hud.api.HudVisibility;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 无输入、无 Widget/GuiScreen 生命周期的保留式 scene HUD 宿主。 */
public final class SceneHudHost {
    private final HudRegistry registry;
    private final HudLayoutEngine hudLayout = new HudLayoutEngine();
    private final SceneTextMeasurer measurer = new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
    private final ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);
    private final ScenePaintReplayer replayer = new ScenePaintReplayer();
    private final Map<String, RetainedHud> retained = new HashMap<String, RetainedHud>();

    /** 创建消费指定服务注册表的 HUD host。 */
    public SceneHudHost(ClientHudServiceImpl service) { this.registry = service.registry(); }

    /**
     * 渲染一帧 HUD。provider 只在本方法的 render 主线程调用。
     *
     * @param backend scene replay 渲染出口
     * @param width GUI 缩放后的逻辑宽度
     * @param height GUI 缩放后的逻辑高度
     * @param inWorld 当前是否已进入世界
     * @param screenOpen 当前是否存在普通 GuiScreen
     */
    public void render(UiRenderBackend backend, int width, int height, boolean inWorld, boolean screenOpen) {
        List<HudRegistry.FrameEntry> frame = registry.snapshot(this::reportProviderFailure);
        HudInsets safeInsets = registry.avoidanceInsets(this::reportProviderFailure);
        ArrayList<HudLayoutEngine.MeasuredHud> measured = new ArrayList<HudLayoutEngine.MeasuredHud>();
        Set<String> active = new HashSet<String>();
        for (HudRegistry.FrameEntry entry : frame) {
            if (!visible(entry.spec.getVisibility(), inWorld, screenOpen)) continue;
            RetainedHud hud = retained.computeIfAbsent(entry.spec.getId(), ignored -> new RetainedHud(measurer));
            hud.update(entry.spec, entry.snapshot);
            LayoutBox box = hud.layout(width, height);
            measured.add(new HudLayoutEngine.MeasuredHud(entry, box.getWidth(), box.getHeight()));
            active.add(entry.spec.getId());
        }
        retained.keySet().retainAll(active);
        for (HudLayoutEngine.PlacedHud placed : hudLayout.layout(measured, width, height, safeInsets)) {
            RetainedHud hud = retained.get(placed.entry.spec.getId());
            PaintPlan plan = paintEngine.paint(hud.root).getPlan();
            replayer.replay(plan, backend, placed.x, placed.y);
        }
    }

    /** 释放世界级保留 scene。 */
    public void clearWorld() { retained.clear(); }

    private static boolean visible(HudVisibility visibility, boolean inWorld, boolean screenOpen) {
        return inWorld && (visibility == HudVisibility.IN_WORLD || !screenOpen);
    }

    private void reportProviderFailure(RuntimeException exception) {
        MyMod.LOG.warn("HUD provider 本帧读取失败，已隔离", exception);
    }

    /** 每个注册项独立持有 keyed 行节点和 layout cache。 */
    private static final class RetainedHud {
        private final SceneNode root = SceneNode.column().setHitTestable(false);
        private final SceneLayoutEngine layoutEngine;
        private final Map<String, SceneNode> lines = new HashMap<String, SceneNode>();

        private RetainedHud(SceneTextMeasurer measurer) { this.layoutEngine = new SceneLayoutEngine(measurer); }

        private void update(HudSpec spec, HudSnapshot snapshot) {
            int padding = HudLayoutEngine.padding(spec);
            root.setPadding(padding).setBackgroundColor(0xA0000000).setWidthSizing(SceneNode.WidthSizing.SHRINK);
            ArrayList<SceneNode> order = new ArrayList<SceneNode>();
            Set<SceneNode> changed = Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
            HashSet<String> activeIds = new HashSet<String>();
            for (HudLine line : snapshot.getLines()) {
                activeIds.add(line.getId());
                SceneNode node = lines.get(line.getId());
                if (node == null) {
                    node = new SceneNode().setHitTestable(false);
                    lines.put(line.getId(), node);
                    changed.add(node);
                }
                String text = line.hasProgress() ? progressText(line) : line.getText();
                node.setText(text).setTextColor(color(line.getTone()))
                        .setPreferredHeight(HudLayoutEngine.lineHeight(spec));
                order.add(node);
            }
            lines.keySet().retainAll(activeIds);
            root.applyChildReconcile(order, changed);
        }

        private LayoutBox layout(int width, int height) {
            layoutEngine.layout(root, new Constraints(Math.max(1, width), Math.max(1, height)));
            return (LayoutBox) root.getCachedLayout();
        }

        private static String progressText(HudLine line) {
            int filled = Math.round(line.getProgress() * 10F);
            StringBuilder text = new StringBuilder(line.getText()).append(" [");
            for (int i = 0; i < 10; i++) text.append(i < filled ? '#' : '-');
            return text.append(']').toString();
        }

        private static int color(HudTone tone) {
            switch (tone) {
                case MUTED: return 0xFFAAAAAA;
                case INFO: return 0xFF55FFFF;
                case SUCCESS: return 0xFF55FF55;
                case WARNING: return 0xFFFFFF55;
                case DANGER: return 0xFFFF5555;
                default: return 0xFFFFFFFF;
            }
        }
    }
}
