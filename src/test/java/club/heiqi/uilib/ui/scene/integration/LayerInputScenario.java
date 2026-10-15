package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneGrid;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.control.SceneLayer;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.input.SceneCompositionSource;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ImageState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 正式 PageSession、真实 FT/HB、标准编辑/按钮及同批 PaintCommand 的所属层验收。 */
public final class LayerInputScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new LayerInputScenario().run(args); }
    private FontTextService fonts;
    protected void setup() throws Exception { fonts = own(new FontTextService(new LwjglFontEngine())); }
    protected void runTests() throws Exception {
        scenario("所属层首次焦点颜色在本帧结算", this::focusColorAtFirstLayout);
        scenario("真实所属Layer按钮和编辑完整业务链路", this::inputLoop);
        scenario("PASSIVE祖先传播指针透明且Tab保逻辑顺序", this::passiveAndOrder);
        scenario("modal盒外所属点击内层返回及结束", this::modalInput);
        scenario("来源失效取消范围手势组字而不提交草稿", this::sourceEnds);
        scenario("覆盖期间来源失效永久取消saved恢复资格", this::coveredSourceEnds);
        scenario("首次来源fullclip及resize不能进入无效内层范围", this::unavailableScopeStart);
        scenario("Layer内部导航和滚轮停在呈现边界", this::scrollBoundary);
        scenario("普通clip和正交滚动来源下嵌套Layer主动显露", this::clippedSourceReveal);
        scenario("同页来源移动保持挂载并按新scope复核", this::sourceMove);
        scenario("隐藏PASSIVE中的modal声明仍定位诊断", this::passiveModalDiagnostic);
        scenario("来源普通自然大小不被层改变且绘制逃离局部clip", this::ownedSpace);
        scenario("独立锚点两例硬尺寸和嵌套来源最终盒", this::anchorExamples);
        scenario("ROW COLUMN grid普通流不计所属层", this::ordinaryFlows);
        scenario("普通placements与图像备用捕获的层声明定位拒绝", this::unsupportedBranches);
        scenario("当前modal祖先层链保留及禁用来源不压制有效背景层", this::presentationScopes);
        scenario("禁用modal祖先不使失活后代压过当前有效modal", this::inactiveModalOrder);
        scenario("不同深度有效modal分支绘制与输入范围同序", this::activeModalBranchOrder);
    }
    private final class Host implements SceneHost {
        int width = 420, height = 320;
        PaintPlan paint;
        public int width() { return width; }
        public int height() { return height; }
        public long metricsRevision() { return fonts.generation(); }
        public void replay(PaintPlan plan) { paint = plan; }
    }
    private static SceneGeometryView geometry(ScenePageSession page) { return page.pipeline().current().geometry; }
    private void click(ScenePageSession page, ReactiveDomain domain, SceneNode node) {
        int[] xy = new int[2];
        domain.execute(() -> { LayoutBox box = geometry(page).get(node).box; xy[0] = box.x + box.width / 2; xy[1] = box.y + box.height / 2; });
        page.pointer(ScenePointerEvent.down(0, xy[0], xy[1]));
        page.pointer(ScenePointerEvent.up(0, xy[0], xy[1])); page.frame();
    }
    private void focusColorAtFirstLayout() {
        Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct();
        SceneNode[] label = new SceneNode[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            label[0] = new SceneNode().text("Focus").focusable(true).size(Size.fixed(80), Size.fixed(20));
            TextStyle base = new TextStyle("default", 18, 0xffff0000);
            label[0].bind(NodeBinding.value(domain.computed(ctx.scope(), () -> base.withColor(
                    ctx.runtime().focusRouter().focused().get() == label[0] ? 0xff00ff00 : 0xffff0000)), SceneNode::style));
            SceneNode layer = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(7, SceneLayer.Participation.INTERACTIVE)))
                    .modal(true).initialFocus(label[0]).add(label[0]);
            ctx.mount(new SceneNode().size(Size.fixed(80), Size.fixed(20)).add(layer));
        })) {
            for (int frame = 0; frame < 2; frame++) {
                page.frame();
                domain.execute(() -> {
                    require(page.runtime().focusRouter().focused().get() == label[0], "所属层首帧未选择初始焦点");
                    TextLayout text = geometry(page).get(label[0]).text;
                    require(text.style.color == 0xff00ff00, "量排后的焦点绑定没有更新当前绘制色");
                    require(host.paint.commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT
                            && c.text.lines().stream().flatMap(line -> line.glyphs().stream())
                                    .anyMatch(g -> g.resolvedStyle.color == 0xff00ff00)), "当前PaintPlan仍保留未聚焦色");
                });
            }
        }
    }

    private void activeModalBranchOrder() {
        Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct();
        SceneNode[] nested = new SceneNode[1], current = new SceneNode[1], nestedButton = new SceneNode[1], currentButton = new SceneNode[1];
        Signal<Integer>[] nestedClicks = new Signal[1], currentClicks = new Signal[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            Signal<SceneTheme> theme = domain.signal(SceneTheme.light());
            nestedClicks[0] = domain.signal(0); currentClicks[0] = domain.signal(0);
            nestedButton[0] = SceneButton.create(ctx, domain.signal("Nested background"), theme,
                    () -> nestedClicks[0].set(nestedClicks[0].get() + 1)).size(Size.fixed(180), Size.fixed(40));
            currentButton[0] = SceneButton.create(ctx, domain.signal("Current root"), theme,
                    () -> currentClicks[0].set(currentClicks[0].get() + 1)).size(Size.fixed(180), Size.fixed(40));
            nested[0] = new SceneNode().modal(true).size(Size.fixed(180), Size.fixed(80)).background(0xffa12345).add(nestedButton[0]);
            ctx.mount(new SceneNode().modal(true).size(Size.fixed(180), Size.fixed(120)).add(nested[0]));
            current[0] = new SceneNode().modal(true).size(Size.fixed(180), Size.fixed(120)).background(0xffb23456).add(currentButton[0]);
            ctx.mount(current[0]);
        })) {
            page.frame();
            domain.execute(() -> {
                SceneGeometryView g = geometry(page);
                require(g.activeModal() == current[0], "后挂载有效modal未成为当前范围");
                require(g.get(nestedButton[0]).box.contains(90, 20) && g.get(currentButton[0]).box.contains(90, 20)
                        && (g.get(nestedButton[0]).clip == null || g.get(nestedButton[0]).clip.contains(90, 20))
                        && (g.get(currentButton[0]).clip == null || g.get(currentButton[0]).clip.contains(90, 20)),
                        "反例没有命中同一可见重叠点");
                int nestedPaint = -1, currentPaint = -1;
                for (int i = 0; i < host.paint.commands().size(); i++) {
                    PaintCommand command = host.paint.commands().get(i);
                    if (command.color == 0xffa12345) nestedPaint = i;
                    if (command.color == 0xffb23456) currentPaint = i;
                }
                System.out.println("MODAL BRANCH currentB=" + (g.activeModal() == current[0]) + " nestedPaint=" + nestedPaint + " currentPaint=" + currentPaint);
                require(nestedPaint >= 0 && currentPaint > nestedPaint, "后台深层modal绘制压过当前有效根modal");
            });
            page.pointer(ScenePointerEvent.down(0, 90, 20)); page.pointer(ScenePointerEvent.up(0, 90, 20)); page.frame();
            domain.execute(() -> {
                require(currentClicks[0].get() == 1 && nestedClicks[0].get() == 0, "重叠点输入没有只更新当前modal业务");
                System.out.println("MODAL BRANCH clickB=" + currentClicks[0].get() + " clickN=" + nestedClicks[0].get());
            });
        }
    }
    private void inputLoop() {
        Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct();
        SceneNode[] opener = new SceneNode[1], field = new SceneNode[1], action = new SceneNode[1], panel = new SceneNode[1];
        Signal<Boolean>[] open = new Signal[1]; Signal<String>[] value = new Signal[1]; SceneEditState[] edit = new SceneEditState[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            open[0] = domain.signal(false); value[0] = domain.signal("unchanged"); edit[0] = new SceneEditState(domain, "");
            Signal<SceneTheme> theme = domain.signal(SceneTheme.light());
            field[0] = SceneTextField.create(ctx, edit[0], theme, domain.signal(false), request -> {}).size(Size.fixed(180), Size.natural());
            action[0] = SceneButton.create(ctx, domain.signal("Apply current draft"), theme, () -> value[0].set(edit[0].snapshot().get().text)).size(Size.fixed(180), Size.natural());
            SceneNode layer = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(7, SceneLayer.Participation.INTERACTIVE)))
                    .bind(NodeBinding.value(open[0], SceneNode::visible)).add(field[0]).add(action[0]);
            opener[0] = SceneButton.create(ctx, domain.signal("Open"), theme, () -> open[0].set(true));
            SceneNode source = new SceneNode().size(Size.fixed(210), Size.natural()).add(opener[0]).add(layer);
            panel[0] = new SceneNode().size(Size.fixed(230), Size.fixed(50)).clip(true).scrollable(true).add(source);
            ctx.mount(panel[0]);
        })) {
            page.frame(); click(page, domain, opener[0]);
            domain.execute(() -> require(page.runtime().focusRouter().focused().get() == opener[0], "非modal Layer出现抢焦点"));
            click(page, domain, field[0]); page.edit(SceneEditEvent.text("layer draft")); page.frame();
            click(page, domain, action[0]);
            domain.execute(() -> {
                require("layer draft".equals(value[0].get()), "Layer标准按钮未更新当前业务值");
                require(geometry(page).get(action[0]).box.y >= geometry(page).get(panel[0]).box.height, "闭环未在面板外点击");
                require("layer draft".equals(edit[0].snapshot().get().text), "Layer编辑未保草稿");
                System.out.println("LAYER INPUT business=" + value[0].get());
            });
        }
    }
    private int[] center(ScenePageSession page, ReactiveDomain domain, SceneNode node) {
        int[] xy = new int[2]; domain.execute(() -> { LayoutBox b = geometry(page).get(node).box; xy[0] = b.x + b.width / 2; xy[1] = b.y + b.height / 2; }); return xy;
    }
    private final class InputPage implements AutoCloseable {
        final Host host = new Host(); final ReactiveDomain domain = ReactiveDomain.direct();
        final ScenePageSession page;
        SceneNode source, tail, panel, layer, field, action, outer, inner, innerLayer, innerButton, opener, background;
        Signal<Boolean> outerOpen, innerOpen, enabled, visible;
        Signal<Integer> scroll; Signal<List<SceneNode>> children;
        Signal<Integer> applied, back; SceneEditState edit;
        final List<SceneEditState.Submission> submissions = new ArrayList<>();
        InputPage() { this(false); }
        InputPage(boolean initiallyClipped) {
            page = ScenePageSession.open(domain, host, fonts, ctx -> {
                outerOpen = domain.signal(true); innerOpen = domain.signal(initiallyClipped); enabled = domain.signal(true); visible = domain.signal(true);
                scroll = domain.signal(initiallyClipped ? 60 : 0); applied = domain.signal(0); back = domain.signal(0); edit = new SceneEditState(domain, "");
                Signal<SceneTheme> theme = domain.signal(SceneTheme.light());
                background = SceneButton.create(ctx, domain.signal("Background"), theme, () -> back.set(back.get() + 1)).size(Size.fixed(400), Size.fixed(80));
                ctx.mount(new SceneNode().add(background));
                field = SceneTextField.create(ctx, edit, theme, domain.signal(false), submissions::add, SceneTextField.SubmitPolicy.BLUR).size(Size.fixed(210), Size.fixed(40));
                action = SceneButton.create(ctx, domain.signal("Layer action"), theme, () -> applied.set(applied.get() + 1)).size(Size.fixed(210), Size.fixed(40));
                layer = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(7, SceneLayer.Participation.INTERACTIVE))).add(field).add(action);
                innerButton = SceneButton.create(ctx, domain.signal("Inner close"), theme, () -> innerOpen.set(false)).size(Size.fixed(210), Size.fixed(40));
                inner = new SceneNode().modal(true).initialFocus(innerButton).add(innerButton);
                innerLayer = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(7, SceneLayer.Participation.INTERACTIVE)))
                        .bind(NodeBinding.value(innerOpen, SceneNode::visible)).add(inner);
                opener = SceneButton.create(ctx, domain.signal("Source"), theme, () -> {}).size(Size.fixed(210), Size.fixed(30));
                source = new SceneNode().size(Size.fixed(210), Size.fixed(30)).add(opener).add(layer).add(innerLayer)
                        .bind(NodeBinding.value(enabled, SceneNode::enabled)).bind(NodeBinding.value(visible, SceneNode::visible));
                tail = new SceneNode().size(Size.fixed(210), Size.fixed(200));
                children = domain.signal(Arrays.asList(source, tail));
                panel = new SceneNode().size(Size.fixed(230), Size.fixed(50)).clip(true).scrollable(true)
                        .bind(NodeBinding.value(scroll, SceneNode::scrollOffset)).bind(NodeBinding.children(children));
                // 普通尾项保证来源确实能被scroll完全裁掉。
                outer = new SceneNode().size(Size.fixed(230), Size.fixed(80)).modal(true).initialFocus(opener)
                        .bind(NodeBinding.value(outerOpen, SceneNode::visible)).add(panel);
                ctx.mount(outer);
            });
            page.frame();
        }
        public void close() { page.close(); }
    }
    private void modalInput() {
        try (InputPage p = new InputPage()) {
            click(p.page, p.domain, p.action);
            p.domain.execute(() -> { require(p.applied.get() == 1 && p.back.get() == 0, "modal盒外合法Layer点击被拒绝或触后台");
                require(geometry(p.page).get(p.action).box.y + 20 > geometry(p.page).get(p.outer).box.height, "未覆盖modal盒外输入"); });
            click(p.page, p.domain, p.field); p.page.edit(SceneEditEvent.text("kept")); p.page.frame();
            p.domain.execute(() -> p.innerOpen.set(true)); p.page.frame();
            p.domain.execute(() -> { require(p.page.runtime().focusRouter().focused().get() == p.innerButton && geometry(p.page).get(p.layer) == null
                    && geometry(p.page).layerSourceAvailable(p.layer) && geometry(p.page).get(p.innerLayer) != null, "内层modal资格/来源祖先或覆盖事实错误"); });
            p.page.frame(); click(p.page, p.domain, p.innerButton);
            p.domain.execute(() -> require(p.page.runtime().focusRouter().focused().get() == p.field && p.submissions.isEmpty() && "kept".equals(p.edit.snapshot().get().text), "覆盖Layer无Entry取消了合法恢复或暗提交草稿"));
            p.domain.execute(() -> { p.innerOpen.set(true); p.outerOpen.set(false); }); p.page.frame();
            click(p.page, p.domain, p.background);
            p.domain.execute(() -> require(geometry(p.page).activeModal() == null && geometry(p.page).get(p.layer) == null
                    && geometry(p.page).get(p.innerLayer) == null && p.back.get() == 1, "外层结束未带走内层与Layer"));
        }
    }
    private void sourceEnds() {
        for (int mode = 0; mode < 4; mode++) {
            final int variant = mode;
            try (InputPage p = new InputPage()) {
                click(p.page, p.domain, p.field); p.page.edit(SceneEditEvent.text("draft"));
                SceneCompositionSource composition = new SceneCompositionSource();
                p.page.edit(SceneEditEvent.compositionBegin(composition, "候选")); p.page.frame();
                int[] xy = center(p.page, p.domain, p.action); p.page.pointer(ScenePointerEvent.down(0, xy[0], xy[1])); p.page.frame();
                p.domain.execute(() -> { p.innerOpen.set(true);
                    if (variant == 0) p.scroll.set(60);
                    else if (variant == 1) p.enabled.set(false);
                    else if (variant == 2) p.visible.set(false);
                    else p.children.set(Collections.emptyList()); }); p.page.frame();
                p.page.edit(SceneEditEvent.compositionCommit(composition, "旧字")); p.page.frame();
                p.page.pointer(ScenePointerEvent.up(0, 300, 40)); p.page.frame();
                p.domain.execute(() -> System.out.println("SOURCE END variant=" + variant + " modalOuter=" + (geometry(p.page).activeModal() == p.outer) + " source=" + geometry(p.page).layerSourceAvailable(p.layer) + " apply=" + p.applied.get() + " back=" + p.back.get() + " submit=" + p.submissions.size() + " text=" + p.edit.snapshot().get().text + " composing=" + p.edit.snapshot().get().composing));
                p.domain.execute(() -> require(geometry(p.page).activeModal() == p.outer && p.applied.get() == 0 && p.back.get() == 0
                        && p.submissions.isEmpty() && "draft".equals(p.edit.snapshot().get().text) && !p.edit.snapshot().get().composing,
                        "来源失效未取消内modal/gesture/composition或暗提交 variant=" + variant));
                p.domain.execute(() -> { p.scroll.set(0); p.enabled.set(true); p.visible.set(true); p.children.set(Arrays.asList(p.source, p.tail)); p.innerOpen.set(false); }); p.page.frame();
                p.page.pointer(ScenePointerEvent.up(0, xy[0], xy[1])); p.page.edit(SceneEditEvent.text("late")); p.page.frame();
                p.domain.execute(() -> require(p.applied.get() == 0 && "draft".equals(p.edit.snapshot().get().text), "来源恢复复活旧焦点或手势"));
            }
        }
    }
    private void coveredSourceEnds() {
        try (InputPage p = new InputPage()) {
            click(p.page, p.domain, p.field); p.page.edit(SceneEditEvent.text("saved draft")); p.page.frame();
            p.domain.execute(() -> p.innerOpen.set(true)); p.page.frame();
            p.domain.execute(() -> { require(geometry(p.page).get(p.layer) == null && geometry(p.page).layerSourceAvailable(p.layer), "未覆盖合法外层saved目标"); p.scroll.set(60); }); p.page.frame();
            p.domain.execute(() -> { require(!geometry(p.page).layerSourceAvailable(p.layer) && geometry(p.page).activeModal() == p.outer, "被覆盖source fullclip仍保内范围"); p.scroll.set(0); }); p.page.frame();
            click(p.page, p.domain, p.innerButton);
            p.domain.execute(() -> require(p.page.runtime().focusRouter().focused().get() == p.opener && p.submissions.isEmpty()
                    && "saved draft".equals(p.edit.snapshot().get().text), "source失效后复活旧saved联系或暗提交"));
        }
    }
    private void unavailableScopeStart() {
        try (InputPage p = new InputPage(true)) {
            p.domain.execute(() -> require(geometry(p.page).activeModal() == p.outer
                    && p.page.runtime().focusRouter().focused().get() == p.opener && geometry(p.page).get(p.innerLayer) == null,
                    "首次在未求来源事实时进入了无效内modal"));
            p.domain.execute(() -> p.scroll.set(0)); p.page.frame();
            p.domain.execute(() -> { require(geometry(p.page).activeModal() == p.inner, "来源恢复后的真实modal未进入"); p.host.height = 0; }); p.page.frame();
            p.domain.execute(() -> require(geometry(p.page).activeModal() == p.outer && geometry(p.page).get(p.innerLayer) == null,
                    "resize来源不可用仍建立空白内范围"));
        }
    }
    private void passiveAndOrder() {
        Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct();
        SceneNode[] base = new SceneNode[1], behind = new SceneNode[1], overlay = new SceneNode[1], nestedButton = new SceneNode[1];
        Signal<SceneLayer.Policy>[] policy = new Signal[1]; Signal<Integer>[] hits = new Signal[1], escapes = new Signal[1]; SceneEditState[] edit = new SceneEditState[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            hits[0] = domain.signal(0); escapes[0] = domain.signal(0); edit[0] = new SceneEditState(domain, "");
            Signal<SceneTheme> theme = domain.signal(SceneTheme.light()); policy[0] = domain.signal(new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE));
            base[0] = SceneTextField.create(ctx, edit[0], theme, domain.signal(false), r -> {}).size(Size.fixed(210), Size.fixed(30));
            overlay[0] = SceneButton.create(ctx, domain.signal("Overlay"), theme, () -> hits[0].set(hits[0].get() + 100)).size(Size.fixed(210), Size.fixed(40));
            nestedButton[0] = SceneButton.create(ctx, domain.signal("Nested"), theme, () -> hits[0].set(hits[0].get() + 1000)).size(Size.fixed(210), Size.fixed(40));
            SceneNode layer = SceneLayer.create(ctx, policy[0]).add(overlay[0]).add(SceneLayer.create(ctx,
                    domain.signal(new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE))).add(nestedButton[0]));
            behind[0] = SceneButton.create(ctx, domain.signal("Behind"), theme, () -> hits[0].set(hits[0].get() + 1)).size(Size.fixed(210), Size.fixed(80)).clickFocus(false);
            ctx.mount(new SceneNode().initialFocus(base[0]).onEscape(() -> { escapes[0].set(escapes[0].get() + 1); return true; })
                    .add(new SceneNode().size(Size.fixed(210), Size.fixed(30)).add(base[0]).add(layer)).add(behind[0]));
        })) {
            page.frame(); page.navigation(SceneNavigationEvent.NEXT); page.frame();
            domain.execute(() -> require(page.runtime().focusRouter().focused().get() == overlay[0], "视觉层序改变逻辑Tab顺序"));
            page.navigation(SceneNavigationEvent.NEXT); page.frame();
            domain.execute(() -> require(page.runtime().focusRouter().focused().get() == nestedButton[0], "嵌套Layer未按逻辑顺序导航"));
            domain.execute(() -> policy[0].set(new SceneLayer.Policy(0, SceneLayer.Participation.PASSIVE))); page.frame();
            click(page, domain, base[0]); int[] xy = center(page, domain, nestedButton[0]);
            page.pointer(ScenePointerEvent.down(0, xy[0], xy[1])); page.pointer(ScenePointerEvent.up(0, xy[0], xy[1]));
            page.edit(SceneEditEvent.text("base")); page.escape(() -> { throw new AssertionError("PASSIVE改了ESC目标"); }); page.frame();
            domain.execute(() -> require(hits[0].get() == 1 && escapes[0].get() == 1 && "base".equals(edit[0].snapshot().get().text)
                    && page.runtime().pointerRouter().hovered(geometry(page)) == behind[0], "PASSIVE祖先输入泄漏或文字hover目标改变"));
            page.navigation(SceneNavigationEvent.NEXT); page.frame();
            domain.execute(() -> require(page.runtime().focusRouter().focused().get() == behind[0], "Tab未跳过整PASSIVE分支"));
        }
    }
    private void scrollBoundary() {
        Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct();
        SceneNode[] panel = new SceneNode[1], layer = new SceneNode[1], last = new SceneNode[1], first = new SceneNode[1];
        Signal<Boolean>[] scrolling = new Signal[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            Signal<SceneTheme> theme = domain.signal(SceneTheme.light()); scrolling[0] = domain.signal(true);
            first[0] = SceneButton.create(ctx, domain.signal("First"), theme, () -> {}).size(Size.fixed(180), Size.fixed(40));
            last[0] = SceneButton.create(ctx, domain.signal("Last"), theme, () -> {}).size(Size.fixed(180), Size.fixed(40));
            layer[0] = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(5, SceneLayer.Participation.INTERACTIVE)))
                    .size(Size.fixed(180), Size.fixed(55)).clip(true).bind(NodeBinding.value(scrolling[0], SceneNode::scrollable)).add(first[0]).add(last[0]);
            SceneNode source = new SceneNode().size(Size.fixed(180), Size.fixed(30)).add(layer[0]);
            panel[0] = new SceneNode().size(Size.fixed(230), Size.fixed(70)).clip(true).scrollable(true)
                    .add(source).add(new SceneNode().size(Size.fixed(180), Size.fixed(250)));
            ctx.mount(panel[0]);
        })) {
            page.frame(); page.navigation(SceneNavigationEvent.NEXT); page.frame();
            domain.execute(() -> require(page.runtime().focusRouter().focused().get() == last[0] && geometry(page).get(layer[0]).scrollOffset > 0
                    && geometry(page).get(panel[0]).scrollOffset == 0, "Layer Tab没有滚自身或错误滚来源"));
            int[] xy = center(page, domain, layer[0]); page.pointer(ScenePointerEvent.scroll(xy[0], xy[1], 1)); page.frame();
            domain.execute(() -> { System.out.println("LAYER WHEEL offset=" + geometry(page).get(layer[0]).scrollOffset + " panel=" + geometry(page).get(panel[0]).scrollOffset); require(geometry(page).get(layer[0]).scrollOffset == Math.max(0, 80 - 55 - layer[0].scrollStep()) && geometry(page).get(panel[0]).scrollOffset == 0, "Layer wheel越过边界"); scrolling[0].set(false); }); page.frame();
            page.pointer(ScenePointerEvent.scroll(xy[0], xy[1], -1)); page.frame();
            domain.execute(() -> require(geometry(page).get(panel[0]).scrollOffset == 0, "命中Layer无scroll目标时透到底"));
            page.pointer(ScenePointerEvent.scroll(220, 60, -1)); page.frame();
            domain.execute(() -> require(geometry(page).get(panel[0]).scrollOffset > 0, "未命中Layer的旧wheel行为被破坏"));
        }
    }
    private void clippedSourceReveal() {
        for (boolean sourceScrollable : new boolean[] {false, true}) {
            for (boolean nested : new boolean[] {false, true}) {
                Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct();
                SceneNode[] panel = new SceneNode[1], layer = new SceneNode[1], outer = new SceneNode[1];
                SceneNode[] first = new SceneNode[1], last = new SceneNode[1];
                try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
                    Signal<SceneTheme> theme = domain.signal(SceneTheme.light());
                    first[0] = SceneButton.create(ctx, domain.signal("First"), theme, () -> {})
                            .size(Size.fixed(180), Size.fixed(40));
                    last[0] = SceneButton.create(ctx, domain.signal("Last"), theme, () -> {})
                            .size(Size.fixed(180), Size.fixed(40));
                    layer[0] = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(5, SceneLayer.Participation.INTERACTIVE)))
                            .size(Size.fixed(180), Size.fixed(55)).clip(true).scrollable(true).add(first[0]).add(last[0]);
                    SceneNode source = new SceneNode().size(Size.fixed(180), Size.fixed(30)).add(layer[0]);
                    if (nested) {
                        outer[0] = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(5, SceneLayer.Participation.INTERACTIVE)))
                                .size(Size.fixed(200), Size.fixed(30)).clip(true).add(source);
                        source = new SceneNode().size(Size.fixed(180), Size.fixed(30)).add(outer[0]);
                    }
                    // 正交滚动也不能使来源裁剪成为层内导航的显露窗口。
                    panel[0] = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fixed(230), Size.fixed(30))
                            .clip(true).scrollable(sourceScrollable).add(source)
                            .add(new SceneNode().size(Size.fixed(250), Size.fixed(30)));
                    ctx.mount(panel[0]);
                })) {
                    page.frame();
                    domain.execute(() -> page.runtime().focusRouter().request(page.runtime().captureMount(first[0])));
                    page.frame(); page.navigation(SceneNavigationEvent.NEXT); page.frame();
                    domain.execute(() -> {
                        SceneGeometryView g = geometry(page);
                        SceneGeometryView.Entry end = g.get(last[0]);
                        require(page.runtime().focusRouter().focused().get() == last[0]
                                && g.get(layer[0]).scrollOffset == 25 && g.get(layer[0]).scrollMaximum == 25
                                && end.box.intersect(end.clip).height == end.box.height, "来源clip使Layer焦点末项仍被裁掉");
                        require(g.get(panel[0]).scrollOffset == 0 && g.layerBoundary(last[0]) == layer[0]
                                && (!nested || g.get(outer[0]).scrollOffset == 0), "Layer显露越过最近呈现边界");
                    });
                }
            }
        }
    }
    private void sourceMove() {
        Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct();
        SceneNode[] source = new SceneNode[1], left = new SceneNode[1], right = new SceneNode[1], isolated = new SceneNode[1], field = new SceneNode[1];
        Signal<SceneNode>[] destination = new Signal[1]; SceneEditState[] edit = new SceneEditState[1]; Signal<Boolean>[] modal = new Signal[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            Signal<SceneTheme> theme = domain.signal(SceneTheme.light()); edit[0] = new SceneEditState(domain, ""); modal[0] = domain.signal(false);
            left[0] = new SceneNode().size(Size.fixed(130), Size.fixed(40)); right[0] = new SceneNode().size(Size.fixed(130), Size.fixed(40));
            isolated[0] = new SceneNode().size(Size.fixed(130), Size.fixed(40)).bind(NodeBinding.value(modal[0], SceneNode::modal));
            destination[0] = domain.signal(left[0]); field[0] = SceneTextField.create(ctx, edit[0], theme, domain.signal(false), r -> {}).size(Size.fixed(100), Size.fixed(35));
            source[0] = new SceneNode().size(Size.fixed(100), Size.fixed(30)).bind(NodeBinding.move(destination[0])).add(SceneLayer.create(ctx,
                    domain.signal(new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE))).add(field[0]));
            left[0].add(source[0]); ctx.mount(new SceneNode().axis(SceneNode.Axis.ROW).add(left[0]).add(right[0]).add(isolated[0]));
        })) {
            page.frame(); click(page, domain, field[0]); page.edit(SceneEditEvent.text("moving")); page.frame();
            UUID id = source[0].id(); club.heiqi.uilib.ui.scene.runtime.MountHandle[] handle = new club.heiqi.uilib.ui.scene.runtime.MountHandle[1];
            domain.execute(() -> { handle[0] = page.runtime().captureMount(source[0]); destination[0].set(right[0]); }); page.frame();
            page.edit(SceneEditEvent.text("!")); page.frame();
            domain.execute(() -> { require(handle[0].active() && source[0].id().equals(id) && source[0].parent() == right[0]
                    && page.runtime().focusRouter().focused().get() == field[0] && "moving!".equals(edit[0].snapshot().get().text), "合法来源移动重建挂载或丢草稿焦点"); modal[0].set(true); destination[0].set(isolated[0]); }); page.frame();
            domain.execute(() -> { require(source[0].parent() == isolated[0] && page.runtime().focusRouter().focused().get() == field[0], "来源移入有效modal未按新范围选择"); destination[0].set(right[0]); }); page.frame();
            page.edit(SceneEditEvent.text("ignored")); page.frame();
            domain.execute(() -> require(handle[0].active() && page.runtime().focusRouter().focused().get() == null
                    && "moving!".equals(edit[0].snapshot().get().text), "新范围隔离未结束Layer交互"));
        }
    }
    private void passiveModalDiagnostic() {
        Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct(); SceneNode[] layer = new SceneNode[1], modal = new SceneNode[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            modal[0] = new SceneNode().modal(true).visible(false);
            layer[0] = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(0, SceneLayer.Participation.PASSIVE))).visible(false).add(modal[0]);
            ctx.mount(new SceneNode().add(layer[0]));
        })) {
            try { page.frame(); throw new AssertionError("隐藏PASSIVE接受modal声明"); }
            catch (IllegalArgumentException expected) { require(expected.getMessage().contains(layer[0].id().toString()) && expected.getMessage().contains(modal[0].id().toString()), "PASSIVE modal诊断不可定位"); }
        }
    }
    private void ownedSpace() {
        Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct();
        SceneNode[] panel = new SceneNode[1], source = new SceneNode[1], layer = new SceneNode[1], button = new SceneNode[1];
        Signal<Boolean>[] open = new Signal[1]; Signal<String>[] label = new Signal[1];
        Signal<Integer>[] scroll = new Signal[1]; Signal<SceneTheme>[] theme = new Signal[1];
        Signal<SceneLayer.Policy>[] policy = new Signal[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            open[0] = domain.signal(false); scroll[0] = domain.signal(0); theme[0] = domain.signal(SceneTheme.light());
            policy[0] = domain.signal(new SceneLayer.Policy(7, SceneLayer.Participation.INTERACTIVE));
            label[0] = domain.signal("A real long button caption continues with several words and wraps using the actual font metrics.");
            button[0] = SceneButton.create(ctx, label[0], theme[0], () -> {})
                    .size(Size.fixed(210), Size.natural());
            layer[0] = SceneLayer.create(ctx, policy[0])
                    .padding(5).background(0xffaa3300).bind(NodeBinding.value(open[0], SceneNode::visible)).add(button[0]);
            source[0] = new SceneNode().size(Size.fill(1), Size.natural()).padding(3)
                    .add(SceneButton.create(ctx, domain.signal("Open"), domain.signal(SceneTheme.light()), () -> {}))
                    .add(layer[0]);
            panel[0] = new SceneNode().size(Size.fixed(250), Size.fixed(90)).scrollable(true).clip(true)
                    .bind(NodeBinding.value(scroll[0], SceneNode::scrollOffset))
                    .add(new SceneNode().size(Size.fixed(10), Size.fixed(20))).add(source[0])
                    .add(new SceneNode().size(Size.fixed(10), Size.fixed(160)));
            ctx.mount(panel[0]);
        })) {
            page.frame(); int[] before = new int[2]; Object[] owner = new Object[1]; UUID[] id = new UUID[1];
            domain.execute(() -> {
                before[0] = geometry(page).get(source[0]).box.height;
                before[1] = geometry(page).get(panel[0]).scrollMaximum;
                owner[0] = layer[0].logicalOwner(); id[0] = layer[0].id(); open[0].set(true);
            });
            page.frame();
            domain.execute(() -> {
                SceneGeometryView g = geometry(page);
                SceneGeometryView.Entry s = g.get(source[0]), l = g.get(layer[0]), b = g.get(button[0]);
                require(s.box.height == before[0] && g.get(panel[0]).scrollMaximum == before[1], "层改变普通自然高度或滚动extent");
                require(l.box.x == s.box.x && l.box.y == s.box.y + s.box.height + 7, "未按最终来源外盒和gap锚定");
                require(l.box.height == b.box.height + 10 && b.text.lines().size() > 1, "层自然高度未来自实际换行字体");
                require(layer[0].parent() == source[0] && id[0].equals(layer[0].id()) && owner[0] == layer[0].logicalOwner(), "投影改变逻辑身份或Owner");
                PaintCommand text = host.paint.commands().stream().filter(c -> c.kind == PaintCommand.Kind.TEXT && c.text == b.text)
                        .findFirst().orElseThrow(() -> new AssertionError("没有同代层文本绘制"));
                System.out.println("LAYER CLIP textBox=" + text.box.y + "," + text.box.height + " textClip=" + text.clip.y + "," + text.clip.height
                        + " layerClip=" + l.clip.y + "," + l.clip.height + " panelClip=" + g.get(panel[0]).clip.y + "," + g.get(panel[0]).clip.height);
                require(text.box.y + text.box.height > g.get(panel[0]).clip.y + g.get(panel[0]).clip.height
                        && text.clip.y + text.clip.height > g.get(panel[0]).clip.y + g.get(panel[0]).clip.height
                        && l.clip.x == 0 && l.clip.y == 0 && l.clip.width == host.width && l.clip.height == host.height,
                        "层绘制仍受到面板clip限制");
                require(g.layerBoundary(button[0]) == layer[0] && g.layerSourceAvailable(layer[0]), "未发布当批来源和层边界事实");
                Set<SceneNode> unique = Collections.newSetFromMap(new IdentityHashMap<>());
                for (SceneGeometryView.Entry entry : g.ordered()) require(unique.add(entry.node), "同批节点重复");
                System.out.println("LAYER sourceHeight=" + s.box.height + " scrollMaximum=" + before[1] + " layer="
                        + l.box.x + "," + l.box.y + "," + l.box.width + "," + l.box.height + " lines=" + b.text.lines().size());
            });
            Object[] textBefore = new Object[1]; int[] heightBefore = new int[1];
            domain.execute(() -> { textBefore[0] = geometry(page).get(button[0]).text; scroll[0].set(30); });
            page.frame();
            domain.execute(() -> {
                SceneGeometryView g = geometry(page); LayoutBox s = g.get(source[0]).box;
                require(s.y < 0 && s.y + s.height > 0 && g.get(layer[0]) != null, "partial clip 来源未保层");
                require(g.get(layer[0]).box.y == s.y + s.height + 7, "来源滚动被叠加第二次");
                require(g.get(button[0]).text == textBefore[0], "纯滚动未复用有效文字度量");
                scroll[0].set(100);
            });
            page.frame();
            domain.execute(() -> { require(geometry(page).get(layer[0]) == null && !geometry(page).layerSourceAvailable(layer[0]), "full clip 来源仍发布层"); scroll[0].set(0); });
            page.frame();
            domain.execute(() -> { heightBefore[0] = geometry(page).get(layer[0]).box.height; label[0].set("Apply"); });
            page.frame();
            domain.execute(() -> {
                require(geometry(page).get(layer[0]).box.height < heightBefore[0] && geometry(page).get(source[0]).box.height == before[0], "文案未沿真实依赖更新自然层");
                heightBefore[0] = geometry(page).get(layer[0]).box.height;
                theme[0].set(SceneTheme.light().withTextStyle(new club.heiqi.uilib.ui.scene.text.TextStyle("default", 28, 0xff20252b)));
            });
            page.frame();
            domain.execute(() -> { require(geometry(page).get(layer[0]).box.height > heightBefore[0], "字号未重算自然层"); policy[0].set(new SceneLayer.Policy(13, SceneLayer.Participation.INTERACTIVE)); });
            page.frame();
            domain.execute(() -> { require(geometry(page).get(layer[0]).box.y == geometry(page).get(source[0]).box.y + before[0] + 13, "policy未驱动排列"); });
            host.width = 180; page.frame();
            domain.execute(() -> { require(geometry(page).get(layer[0]).box.width == 220 && geometry(page).get(layer[0]).box.x == 0, "超视口硬宽被缩小"); });
        }
    }

    /** 示例的坐标是页面声明给普通扩展的输入，不读取或回写生产几何。 */
    private static class SourceBox implements SceneControl {
        final int x, y;
        SourceBox(int x, int y) { this.x = x; this.y = y; }
        public MeasuredSize measure(SceneNode n, Constraints c, club.heiqi.uilib.ui.scene.text.SceneTextMeasurer t) { return new MeasuredSize(0, 0); }
        public ChildPlacements arrange(SceneNode n, LayoutBox b, SceneChildMeasurer children) {
            SceneNode child = children.flowChildren().get(0);
            return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(child,
                    new LayoutBox(x, y, child.width().value, child.height().value))));
        }
        public void paint(SceneNode n, SceneGeometryView g, PaintPlanBuilder p) {}
    }
    private void anchorExamples() {
        anchorExample(600, 400, 520, 350, 60, 30, Size.fixed(180), Size.fixed(100), 420, 300, 180, 100, true);
        anchorExample(160, 90, 20, 30, 40, 20, Size.natural().bounds(200, 240), Size.natural().bounds(120, 150), 0, 0, 200, 120, false);
    }
    private void anchorExample(int vw, int vh, int sx, int sy, int sw, int sh, Size w, Size h,
            int ex, int ey, int ew, int eh, boolean nested) {
        Host host = new Host(); host.width = vw; host.height = vh; ReactiveDomain domain = ReactiveDomain.direct();
        SceneNode[] layer = new SceneNode[1], inner = new SceneNode[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            layer[0] = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(8, SceneLayer.Participation.INTERACTIVE)))
                    .size(w, h).padding(nested ? 9 : 0).background(0xff008833);
            if (nested) { inner[0] = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(8, SceneLayer.Participation.INTERACTIVE)))
                    .size(Size.fixed(70), Size.fixed(40)).background(0xff003388); layer[0].add(inner[0]); }
            SceneNode source = new SceneNode().size(Size.fixed(sw), Size.fixed(sh)).add(layer[0]);
            ctx.mount(new SceneNode().size(Size.fixed(vw), Size.fixed(vh)).control(new SourceBox(sx, sy)).add(source));
        })) {
            page.frame(); domain.execute(() -> {
                SceneGeometryView g = geometry(page); LayoutBox box = g.get(layer[0]).box;
                require(box.x == ex && box.y == ey && box.width == ew && box.height == eh, "独立例子最终盒不符");
                if (nested) { LayoutBox i = g.get(inner[0]).box;
                    require(i.x == 420 && i.y == 360 && i.width == 70 && i.height == 40, "嵌套层未使用外层最终外盒"); }
                Set<SceneNode> unique = Collections.newSetFromMap(new IdentityHashMap<>());
                for (SceneGeometryView.Entry entry : g.ordered()) require(unique.add(entry.node), "嵌套层重复发布");
                System.out.println("ANCHOR viewport=" + vw + "," + vh + " box=" + box.x + "," + box.y + "," + box.width + "," + box.height);
            });
        }
    }
    private void ordinaryFlows() {
        for (int kind = 0; kind < 3; kind++) {
            final int mode = kind; Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct();
            SceneNode[] parent = new SceneNode[1], last = new SceneNode[1]; Signal<Boolean>[] open = new Signal[1];
            try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
                open[0] = domain.signal(false);
                parent[0] = mode == 2 ? SceneGrid.create(ctx, domain.signal(new SceneGrid.Policy(180, 12, 9)))
                        : new SceneNode().axis(mode == 0 ? SceneNode.Axis.ROW : SceneNode.Axis.COLUMN).size(Size.fill(1), Size.natural()).gap(9);
                SceneNode layer = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE)))
                        .size(Size.fixed(390), Size.fixed(200)).bind(NodeBinding.value(open[0], SceneNode::visible));
                last[0] = new SceneNode().size(Size.fixed(30), Size.fixed(15));
                parent[0].add(new SceneNode().size(Size.fixed(20), Size.fixed(10))).add(layer).add(last[0]); ctx.mount(parent[0]);
            })) {
                page.frame(); int[] before = new int[4];
                domain.execute(() -> { LayoutBox p = geometry(page).get(parent[0]).box, l = geometry(page).get(last[0]).box;
                    before[0] = p.width; before[1] = p.height; before[2] = l.x; before[3] = l.y; open[0].set(true); });
                page.frame(); domain.execute(() -> { LayoutBox p = geometry(page).get(parent[0]).box, l = geometry(page).get(last[0]).box;
                    require(p.width == before[0] && p.height == before[1] && l.x == before[2] && l.y == before[3], "所属层污染普通流gap/列槽/extent mode=" + mode); });
            }
        }
    }
    private void inactiveModalOrder() {
        Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct();
        SceneNode[] disabled = new SceneNode[1], inactive = new SceneNode[1], current = new SceneNode[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            inactive[0] = new SceneNode().modal(true).size(Size.fixed(100), Size.fixed(60)).background(0xffaa2200);
            disabled[0] = new SceneNode().modal(true).enabled(false).size(Size.fixed(100), Size.fixed(60))
                    .background(0xffbb3300).add(inactive[0]);
            current[0] = new SceneNode().modal(true).size(Size.fixed(100), Size.fixed(60)).background(0xff0088cc);
            ctx.mount(disabled[0]); ctx.mount(current[0]);
        })) {
            page.frame(); domain.execute(() -> {
                SceneGeometryView g = geometry(page);
                require(g.activeModal() == current[0] && g.get(inactive[0]) != null, "有效modal选择或失活普通绘制丢失");
                require(g.logicalScope(inactive[0]) == null && g.logicalScope(disabled[0]) == null,
                        "失活modal建立了有效scope");
                int inactiveIndex = -1, currentIndex = -1;
                for (int i = 0; i < host.paint.commands().size(); i++) {
                    PaintCommand command = host.paint.commands().get(i);
                    if (command.color == 0xffaa2200) inactiveIndex = i;
                    if (command.color == 0xff0088cc) currentIndex = i;
                }
                require(inactiveIndex >= 0 && currentIndex > inactiveIndex, "失活modal后代绘制压过当前有效modal");
                System.out.println("MODAL ORDER inactive=" + inactiveIndex + " current=" + currentIndex);
            });
        }
    }
    private void presentationScopes() {
        Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct();
        SceneNode[] outer = new SceneNode[1], modal = new SceneNode[1], inner = new SceneNode[1], background = new SceneNode[1];
        Signal<Boolean>[] enabled = new Signal[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            enabled[0] = domain.signal(true);
            inner[0] = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE)))
                    .size(Size.fixed(90), Size.fixed(40)).background(0xff112233);
            modal[0] = new SceneNode().size(Size.fixed(160), Size.fixed(40)).modal(true).background(0xff334455)
                    .bind(NodeBinding.value(enabled[0], SceneNode::enabled)).add(inner[0]);
            outer[0] = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE)))
                    .size(Size.fixed(180), Size.fixed(120)).background(0xff556677).add(modal[0]);
            background[0] = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE)))
                    .size(Size.fixed(100), Size.fixed(50)).background(0xff778899);
            ctx.mount(new SceneNode().size(Size.fill(1), Size.natural())
                    .add(new SceneNode().size(Size.fixed(180), Size.fixed(40)).add(outer[0]))
                    .add(new SceneNode().size(Size.fixed(100), Size.fixed(40)).add(background[0])));
        })) {
            page.frame(); domain.execute(() -> {
                SceneGeometryView g = geometry(page);
                require(g.activeModal() == modal[0] && g.get(outer[0]) != null && g.get(inner[0]) != null
                        && g.get(background[0]) == null && g.logicalScope(inner[0]) == modal[0], "modal来源祖先链或背景层隔离错误");
                List<PaintCommand> commands = host.paint.commands(); int a = -1, b = -1, c = -1;
                for (int i = 0; i < commands.size(); i++) { int color = commands.get(i).color;
                    if (color == 0xff556677) a = i; if (color == 0xff334455) b = i; if (color == 0xff112233) c = i; }
                require(a >= 0 && b > a && c > b, "绘制未消费同批scope层序"); enabled[0].set(false);
            });
            page.frame(); domain.execute(() -> {
                SceneGeometryView g = geometry(page);
                require(g.activeModal() == null && g.get(inner[0]) == null && g.get(background[0]) != null,
                        "disabled modal来源仍压制有效背景所属层");
            });
        }
    }
    private void unsupportedBranches() {
        Host host = new Host(); ReactiveDomain domain = ReactiveDomain.direct(); SceneNode[] source = new SceneNode[1], layer = new SceneNode[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            layer[0] = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE)));
            source[0] = new SceneNode().size(Size.fixed(40), Size.fixed(20)).control(new SourceBox(0, 0) {
                public ChildPlacements arrange(SceneNode n, LayoutBox b, SceneChildMeasurer children) {
                    return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(n.children().get(0), new LayoutBox(0, 0, 0, 0))));
                }
            }).add(layer[0]); ctx.mount(source[0]);
        })) {
            try { page.frame(); throw new AssertionError("普通placements接受了层"); }
            catch (IllegalArgumentException expected) { require(expected.getMessage().contains(source[0].id().toString()) && expected.getMessage().contains(layer[0].id().toString()), "排列诊断无定位"); }
        }
        ImageService service = new ImageService() {
            public Lease acquire(ImageSource s, ReactiveDomain d) { return new Lease() {
                final ReadableSignal<ImageState> state = d.signal(ImageState.loading(s, 1));
                public ReadableSignal<ImageState> state() { return state; } public void close() {}
            }; }
            public void reload(ImageSource s) {}
        };
        SceneNode[] image = new SceneNode[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, ctx -> {
            layer[0] = SceneLayer.create(ctx, domain.signal(new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE)));
            SceneNode fallback = new SceneNode().add(layer[0]);
            image[0] = SceneImage.create(service, domain.signal(ImageSource.resource("test:loading")),
                    domain.signal(ImageOptions.defaults().size(Size.fixed(100), Size.fixed(50))), fallback); ctx.mount(image[0]);
        })) {
            try { page.frame(); throw new AssertionError("image fallback接受了层"); }
            catch (IllegalArgumentException expected) { require(expected.getMessage().contains("image fallback") && expected.getMessage().contains(image[0].id().toString()) && expected.getMessage().contains(layer[0].id().toString()), "捕获诊断无所属边界位置"); }
        }
    }
}

