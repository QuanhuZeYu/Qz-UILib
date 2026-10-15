package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneSortableList;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 字体来源形态的排序负载；对象身份是key，相同声明路径的两项仍独立。 */
public final class SortPerformanceWorkload implements ScenePage {
    public static final class FontEntry {
        public final int number;
        public final Object identity = new Object();
        public final String declaration;
        private FontEntry(int number, String declaration) { this.number = number; this.declaration = declaration; }
    }
    private static final class Nodes {
        final SceneNode content, handle;
        SceneNode parent;
        Nodes(SceneNode content, SceneNode handle) { this.content = content; this.handle = handle; }
    }
    private final int scale;
    private final Map<Object, Nodes> nodes = new IdentityHashMap<>();
    private final FontEntry[] initial;
    private Signal<List<FontEntry>> items;
    private ScenePageContext context;
    private ReadableSignal<SceneTheme> theme;
    private SceneNode list, currentPreview;
    private long declarationNanos;
    private int contentLength, commits, rowFactoryCalls, lastMovedNumber = -1;

    public SortPerformanceWorkload(int scale) {
        if (scale != 50 && scale != 200 && scale != 1000) throw new IllegalArgumentException("规模须为50/200/1000");
        this.scale = scale; initial = new FontEntry[scale];
    }
    @Override public void build(ScenePageContext context) {
        long begin = System.nanoTime(); this.context = context;
        theme = context.domain().computed(context.scope(),
                () -> GalleryStyle.theme(context.themeMode().get() == club.heiqi.uilib.ui.scene.control.ThemeMode.DARK));
        List<FontEntry> values = new ArrayList<>();
        for (int i = 0; i < scale; i++) {
            int pair = i / 2;
            StringBuilder path = new StringBuilder("file:C:/Fonts/工坊资源包/");
            for (int part = 0; part <= pair % 4; part++) path.append("多语言字体来源与可变字重目录-Café-连接文字/长路径声明/");
            path.append("Font-").append(pair).append("-NotoSansSC-VariableFont.ttf");
            FontEntry entry = new FontEntry(i, path.toString());
            initial[i] = entry; values.add(entry);
            contentLength += entry.declaration.length() + ("来源 " + i + " · 字体候选 Café 中文").length() + "::上移下移移除".length();
        }
        items = context.domain().signal(Collections.unmodifiableList(values));
        list = SceneSortableList.create(context, items, entry -> entry.identity, this::row, this::preview,
                move -> moveBefore(move.key, move.beforeKey))
                .size(Size.fill(1), Size.fill(1)).padding(16).gap(5).scrollable(true).clip(true);
        context.mountOwned(list); declarationNanos = System.nanoTime() - begin;
    }
    private SceneNode information(ReadableSignal<FontEntry> source) {
        SceneNode info = new SceneNode().size(Size.fill(1), Size.natural()).gap(4);
        info.add(GalleryStyle.label(context, () -> "来源 " + source.get().number + " · 字体候选 Café 中文", theme, 14, false));
        info.add(GalleryStyle.label(context, () -> source.get().declaration, theme, 12, true)
                .size(Size.fill(1), Size.natural()).textPolicy(new TextPolicy(TextPolicy.Direction.LTR, TextPolicy.Wrap.EMERGENCY, "und", 0, 0)));
        return info;
    }
    private SceneSortableList.Row row(ReadableSignal<FontEntry> source) {
        FontEntry entry = source.get();
        if (nodes.containsKey(entry.identity)) throw new AssertionError("排序重建了持续存在的row：" + entry.number);
        rowFactoryCalls++;
        SceneNode handle = GalleryStyle.label(context, () -> "::", theme, 20, true).size(Size.fixed(28), Size.natural()).padding(4);
        SceneNode actions = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.natural(), Size.natural()).gap(4);
        for (String text : new String[]{"上移", "下移", "移除"}) actions.add(GalleryStyle.button(context, () -> text, theme, () -> {}));
        SceneNode content = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).padding(8).gap(8)
                .background(entry.number % 2 == 0 ? 0xffeef2ff : 0xfff8fafc)
                .add(handle).add(information(source)).add(actions);
        nodes.put(entry.identity, new Nodes(content, handle));
        return new SceneSortableList.Row(content, handle);
    }
    private SceneNode preview(ReadableSignal<FontEntry> source) {
        currentPreview = new SceneNode().size(Size.fixed(560), Size.natural()).padding(8).background(0xffdbeafe).add(information(source));
        return currentPreview;
    }
    private void moveBefore(Object key, Object beforeKey) {
        List<FontEntry> current = items.get(); FontEntry moved = null, before = null;
        for (FontEntry entry : current) { if (entry.identity == key) moved = entry; if (entry.identity == beforeKey) before = entry; }
        if (moved == null || key == beforeKey || beforeKey != null && before == null) throw new AssertionError("排序提交引用无效key");
        List<FontEntry> next = new ArrayList<>(current); next.remove(moved);
        next.add(before == null ? next.size() : next.indexOf(before), moved);
        boolean changed = false;
        for (int i = 0; i < next.size(); i++) changed |= next.get(i) != current.get(i);
        if (!changed) throw new AssertionError("drop提交未改变真实key序");
        commits++; lastMovedNumber = moved.number; items.set(Collections.unmodifiableList(next));
    }
    public SceneNode list() { return list; }
    public SceneNode preview() { return currentPreview; }
    public FontEntry initialEntry(int position) { return initial[position]; }
    public FontEntry at(int position) { return items.get().get(position); }
    public SceneNode content(FontEntry entry) { return nodes.get(entry.identity).content; }
    public SceneNode handle(FontEntry entry) { return nodes.get(entry.identity).handle; }
    public int commits() { return commits; }
    public int lastMovedNumber() { return lastMovedNumber; }
    public int rowFactoryCalls() { return rowFactoryCalls; }
    /** 运行边界记录并在drop后校验；不每帧重建序列或集合。 */
    public void captureIdentity() { for (Nodes row : nodes.values()) row.parent = row.content.parent(); }
    public boolean identityPreserved() {
        if (nodes.size() != scale || rowFactoryCalls != scale) return false;
        for (FontEntry entry : initial) {
            Nodes row = nodes.get(entry.identity);
            if (row == null || row.content.destroyed() || row.content.parent() != row.parent || row.content.runtime() == null) return false;
        }
        return true;
    }
    public boolean duplicatePathIndependent() {
        return initial[0].identity != initial[1].identity && initial[0].declaration.equals(initial[1].declaration)
                && nodes.get(initial[0].identity).content != nodes.get(initial[1].identity).content;
    }
    public String report(String load, String version, int warmup, int frames, boolean complete, int observedChanges,
            int width, int height, long firstFrame, PerformanceSamples samples, PerformanceReport.Snapshot before, PerformanceReport.Snapshot after) {
        return PerformanceReport.json("desktop-glfw-hidden",
                "actions+complete window.frame; GLFW handle callbacks,poll,clear,presentation,swap; vsync=0; observations outside timer; no independent GPU timer",
                version, load, scale, warmup, frames, complete, observedChanges > 0, observedChanges,
                contentLength, width, height, declarationNanos, firstFrame, samples, before, after);
    }
}
