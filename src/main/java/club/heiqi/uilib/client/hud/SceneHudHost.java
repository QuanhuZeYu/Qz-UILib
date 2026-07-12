package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudLine;
import club.heiqi.uilib.ui.hud.api.HudSnapshot;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudTone;
import club.heiqi.uilib.ui.hud.api.HudVisibility;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 无输入、无 Widget/GuiScreen 生命周期的保留式 scene HUD 宿主。 */
public final class SceneHudHost {
    private final HudRegistry registry;
    private final HudLayoutEngine hudLayout = new HudLayoutEngine();
    private final SceneTextMeasurer measurer;
    private final ScenePaintEngine paintEngine;
    private final ScenePaintReplayer replayer = new ScenePaintReplayer();
    private final Map<String, RetainedHud> retained = new HashMap<String, RetainedHud>();

    /** 创建消费指定服务注册表的 HUD host。 */
    public SceneHudHost(ClientHudServiceImpl service) {
        this(service.registry(), new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance()));
    }

    SceneHudHost(HudRegistry registry, SceneTextMeasurer measurer) {
        this.registry = registry;
        this.measurer = measurer;
        this.paintEngine = new ScenePaintEngine(measurer);
    }

    /** 在 render 主线程读取 provider，并经单一响应式事务完成本帧 scene 更新。 */
    public void render(UiRenderBackend backend, int width, int height, boolean inWorld, boolean screenOpen) {
        List<HudRegistry.FrameEntry> frame = registry.snapshot(this::reportProviderFailure);
        HudInsets safeInsets = registry.avoidanceInsets(this::reportProviderFailure);
        ArrayList<HudLayoutEngine.MeasuredHud> measured = new ArrayList<HudLayoutEngine.MeasuredHud>();
        Set<String> registered = new HashSet<String>();
        Set<String> visible = new HashSet<String>();
        for (HudRegistry.FrameEntry entry : frame) {
            registered.add(entry.spec.getId());
            RetainedHud hud = retained.get(entry.spec.getId());
            if (hud == null) {
                hud = new RetainedHud(entry.spec, measurer);
                retained.put(entry.spec.getId(), hud);
            }
            hud.accept(entry.snapshot);
            if (!entry.snapshot.isEmpty() && visible(entry.spec.getVisibility(), inWorld, screenOpen)) {
                visible.add(entry.spec.getId());
            }
        }
        ReactiveScheduler.get().labelNextTransaction("hud.frame");
        ReactiveScheduler.get().flush();
        disposeInactive(registered);
        for (HudRegistry.FrameEntry entry : frame) {
            RetainedHud hud = retained.get(entry.spec.getId());
            if (hud == null || !visible.contains(entry.spec.getId())) continue;
            LayoutBox box = hud.layout(width, height);
            measured.add(new HudLayoutEngine.MeasuredHud(entry, box.getWidth(), box.getHeight()));
        }
        for (HudLayoutEngine.PlacedHud placed : hudLayout.layout(measured, width, height, safeInsets)) {
            RetainedHud hud = retained.get(placed.entry.spec.getId());
            hud.constrain(placed.width, placed.height);
            PaintPlan content = paintEngine.paint(hud.root).getPlan();
            PaintPlan plan = new PaintPlan().addClipPush(0, 0, placed.width, placed.height, 0);
            for (PaintCommand command : content.getCommands()) plan.addCommand(command);
            plan.addClipPop();
            replayer.replay(plan, backend, placed.x, placed.y);
        }
    }

    /** 释放世界级保留 scene，registration/provider 仍归 mod 持有。 */
    public void clearWorld() {
        for (RetainedHud hud : retained.values()) hud.dispose();
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
        MyMod.LOG.warn("HUD provider 本帧读取失败，已隔离", exception);
    }

    /** 每个注册项只挂载一次，snapshot 变化经 signal/effect 更新属性和 keyed 行列表。 */
    static final class RetainedHud {
        private static final int TRACK_HEIGHT = 3;
        private final SceneNode root = SceneNode.column().setHitTestable(false).setClipChildren(true);
        private final SceneRuntime runtime;
        private final SceneLayoutEngine layoutEngine;
        private final Signal<HudSnapshot> snapshot = Signal.create(HudSnapshot.EMPTY);

        RetainedHud(HudSpec spec, SceneTextMeasurer measurer) {
            runtime = new SceneRuntime(measurer);
            layoutEngine = new SceneLayoutEngine(measurer);
            root.setPadding(HudLayoutEngine.padding(spec)).setBackgroundColor(0xA0000000)
                    .setWidthSizing(SceneNode.WidthSizing.SHRINK);
            runtime.forEach(root, Computed.create(() -> snapshot.get().getLines()), HudLine::getId,
                    line -> createLine(spec, line));
            runtime.flush();
        }

        private SceneNode createLine(HudSpec spec, HudLine initial) {
            SceneNode row = SceneNode.column().setHitTestable(false);
            SceneNode label = new SceneNode().setHitTestable(false);
            SceneNode track = new SceneNode().setHitTestable(false).setPreferredHeight(TRACK_HEIGHT)
                    .setPreferredWidth(100).setClipChildren(true);
            SceneNode fill = new SceneNode().setHitTestable(false).setPreferredHeight(TRACK_HEIGHT);
            track.appendChild(fill);
            row.appendChild(label);
            row.appendChild(track);
            runtime.bindComputed(() -> lineById(initial.getId()).getText(), label::setText);
            runtime.bindComputed(() -> color(lineById(initial.getId()).getTone()), label::setTextColor);
            runtime.bindComputed(() -> lineById(initial.getId()).hasProgress(), value -> {
                track.setPreferredHeight(Boolean.TRUE.equals(value) ? TRACK_HEIGHT : 0);
                track.setBackgroundColor(Boolean.TRUE.equals(value) ? 0x60000000 : 0x00000000);
            });
            runtime.bindComputed(() -> Math.round(lineById(initial.getId()).getProgress() * 100F), value ->
                    fill.setPreferredWidth(Math.max(0, value.intValue())));
            runtime.bindComputed(() -> color(lineById(initial.getId()).getTone()), fill::setBackgroundColor);
            runtime.bindComputed(() -> lineById(initial.getId()).hasProgress(), value -> row.setPreferredHeight(
                    HudLayoutEngine.lineHeight(spec) + (Boolean.TRUE.equals(value) ? TRACK_HEIGHT : 0)));
            return row;
        }

        private HudLine lineById(String id) {
            for (HudLine line : snapshot.get().getLines()) if (id.equals(line.getId())) return line;
            return HudLine.text(id, "");
        }

        void accept(HudSnapshot value) { snapshot.set(value); }

        LayoutBox layout(int width, int height) {
            root.setPreferredWidth(0).setPreferredHeight(0).setWidthSizing(SceneNode.WidthSizing.SHRINK);
            layoutEngine.layout(root, new Constraints(Math.max(1, width), Math.max(1, height)));
            return (LayoutBox) root.getCachedLayout();
        }

        private void constrain(int width, int height) {
            root.setPreferredWidth(Math.max(1, width)).setPreferredHeight(Math.max(1, height))
                    .setWidthSizing(SceneNode.WidthSizing.FILL);
            layoutEngine.layout(root, new Constraints(Math.max(1, width), Math.max(1, height)));
        }

        private void dispose() { runtime.dispose(); }

        SceneNode root() { return root; }

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
