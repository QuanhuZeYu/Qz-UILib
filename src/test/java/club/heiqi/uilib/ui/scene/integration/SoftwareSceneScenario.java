package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.ScenePresentation;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 软件场景只替换度量/宿主回放边界，实际状态、节点、布局、路由和计划均使用生产实现。 */
final class SoftwareSceneScenario extends TestApplication {

    private static final class SoftwareHost implements SceneHost, SceneTextMeasurer {
        int width = 100, height = 80, glyph = 5, lineHeight = 10, measures;
        long metrics;
        PaintPlan last;
        @Override public int width() { return width; }
        @Override public int height() { return height; }
        @Override public long metricsRevision() { return metrics; }
        @Override public void replay(PaintPlan plan) { last = plan; }
        @Override public TextLayout layout(String text, TextStyle style, int availableWidth) {
            measures++;
            List<TextLayout.Line> lines = new ArrayList<>();
            int characters = Math.max(1, availableWidth / glyph), maxWidth = 0;
            if (!text.isEmpty()) {
                for (int start = 0; start < text.length(); start += characters) {
                    String line = text.substring(start, Math.min(text.length(), start + characters));
                    maxWidth = Math.max(maxWidth, line.length() * glyph);
                    lines.add(new TextLayout.Line(line, 0, lines.size() * lineHeight));
                }
            }
            return new TextLayout(maxWidth, lines.size() * lineHeight, style, lines);
        }
    }
    /** 在实际domain内关闭，场景失败时同样释放挂载与订阅。 */
    private SceneRuntime sceneRuntime(ReactiveDomain domain) {
        SceneRuntime runtime = new SceneRuntime(domain);
        onClose(() -> domain.execute(runtime::close));
        return runtime;
    }

    private void boundedScrolling() {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(() -> {
            SoftwareHost host = new SoftwareHost();
            SceneRuntime runtime = sceneRuntime(domain);
            Signal<Integer> contentHeight = domain.signal(300);
            SceneNode content = new SceneNode().size(Size.fixed(100), Size.fixed(300))
                .bind(NodeBinding.value(contentHeight, (node, value) -> node.size(Size.fixed(100), Size.fixed(value))));
            SceneNode viewport = new SceneNode().size(Size.fixed(100), Size.fixed(80)).scrollable(true).scrollStep(20).add(content);
            runtime.attach(null, viewport);
            SceneFramePipeline pipe = new SceneFramePipeline(runtime, host, host);
            ScenePresentation original = pipe.current();
            for (int i = 0; i < 100; i++) pipe.scroll(5, 5, -1);
            pipe.frame();
            SceneGeometryView.Entry end = pipe.current().geometry.get(viewport);
            require(end.scrollOffset == end.scrollMaximum && end.scrollMaximum > 20, "实际滚动必须止于真实内容末尾");
            pipe.scroll(5, 5, 1); pipe.frame();
            require(pipe.current().geometry.get(viewport).scrollOffset == end.scrollMaximum - 20, "末尾反向一格立即移动，不偿还不可见滚动债务");
            require(original.geometry.get(viewport).scrollOffset == 0, "滚动后旧几何仍不可变");
            contentHeight.set(100); SceneGeometryView.Entry smaller = pipe.current().geometry.get(viewport);
            require(smaller.scrollOffset == smaller.scrollMaximum, "内容缩短即时夹到新边界");
            pipe.scroll(5, 5, 1); pipe.frame();
            require(pipe.current().geometry.get(viewport).scrollOffset == 0, "内容缩短后反向输入立即有效");
            contentHeight.set(300); pipe.scroll(5, 5, Integer.MIN_VALUE); pipe.frame();
            require(pipe.current().geometry.get(viewport).scrollOffset == pipe.current().geometry.get(viewport).scrollMaximum, "极端滚轮值不可整数溢出");

        });
    }
    @Override protected void runTests() throws Exception {
        scenario("有界滚动与内容缩短", this::boundedScrolling);
        scenario("动态列表布局与同代绘制命中", this::dynamicPanel);
        ReactiveDomain domain = ReactiveDomain.direct();
        scenario("裁剪与模态输入", () -> clippingAndModal(domain));
        scenario("不可变绘制载荷", SoftwareSceneScenario::immutablePayload);
        scenario("评审边界回归", () -> reviewedBoundaries(domain));
        scenario("自定义控件共同几何", () -> customControl(domain));
    }

    private void dynamicPanel() throws Exception {
        try { Class.forName("net.minecraft.client.Minecraft"); throw new AssertionError("软件场景执行类路径不得包含 MC"); }
        catch (ClassNotFoundException expected) { }
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareHost host = new SoftwareHost();
        SceneRuntime[] runtime = new SceneRuntime[1];
        SceneFramePipeline[] pipe = new SceneFramePipeline[1];
        Signal<Integer>[] count = new Signal[1];
        Signal<String>[] heading = new Signal[1];
        Signal<List<Integer>>[] items = new Signal[1];
        SceneNode[] panel = new SceneNode[1], title = new SceneNode[1], body = new SceneNode[1], button = new SceneNode[1];
        ScenePresentation[] saved = new ScenePresentation[1];
        UUID[] firstIdentity = new UUID[1];
        domain.execute(() -> {
            runtime[0] = sceneRuntime(domain);
            pipe[0] = new SceneFramePipeline(runtime[0], host, host);
            count[0] = domain.signal(0); heading[0] = domain.signal("标题");
            items[0] = domain.signal(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8));
            Owner business = domain.owner();
            Computed<String> headingText = domain.computed(business, () -> heading[0].get() + count[0].get());
            title[0] = new SceneNode().size(Size.fill(1), Size.natural()).bind(NodeBinding.value(headingText, SceneNode::text));
            button[0] = new SceneNode().size(Size.fixed(40), Size.fixed(10)).text("添加")
                .onClick(() -> {
                    count[0].set(count[0].get() + 1);
                    List<Integer> next = new ArrayList<>(items[0].get()); next.add(next.size() + 1); items[0].set(next);
                });
            SceneNode actions = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.fixed(10)).add(button[0]);
            body[0] = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true)
                .bind(NodeBinding.repeat(items[0], value -> value, value -> new SceneNode().size(Size.fill(1), Size.fixed(10))
                    .bind(NodeBinding.value(value, (node, item) -> node.text("条目" + item)))));
            panel[0] = new SceneNode().size(Size.fill(1), Size.fill(1)).gap(1).add(title[0]).add(actions).add(body[0]);
            runtime[0].attach(null, panel[0]);
        });
        pipe[0].frame();
        domain.execute(() -> {
            saved[0] = pipe[0].current();
            LayoutBox header = saved[0].geometry.get(title[0]).box;
            LayoutBox contents = saved[0].geometry.get(body[0]).box;
            int expectedRemaining = host.height - header.height - button[0].height().value - panel[0].gap() * 2;
            require(contents.height == expectedRemaining, "正文自动获得扣除自然标题/操作区/间距后的剩余空间");
            firstIdentity[0] = body[0].children().get(0).id();
            LayoutBox action = saved[0].geometry.get(button[0]).box;
            pipe[0].click(action.x + 1, action.y + 1); pipe[0].click(action.x + 1, action.y + 1);
        });
        pipe[0].frame();
        domain.execute(() -> {
            require(count[0].get() == 2 && items[0].get().size() == 10, "连续事件按原序独立操作并立即读取累计值");
            require(body[0].children().size() == 10 && body[0].children().get(0).id().equals(firstIdentity[0]), "列表增量声明保留既有节点身份");
            require(saved[0].geometry.get(title[0]).text.lines().get(0).text.equals("标题0"), "旧代几何不随新标题修改");
            heading[0].set("标题变长以后应自动折行并重新分配正文剩余空间");
        });
        pipe[0].frame();
        domain.execute(() -> {
            ScenePresentation current = pipe[0].current();
            int expectedTitleHeight = (int) Math.ceil((double) title[0].text().length() / Math.max(1, host.width / host.glyph)) * host.lineHeight;
            require(current.geometry.get(title[0]).box.height == expectedTitleHeight, "标题按当前度量和宽度自动重排");
            require(current.geometry.get(body[0]).box.height == host.height - expectedTitleHeight - 10 - 2, "正文随标题高度重分配");
            items[0].set(Arrays.asList(1, 3, 5));
        });
        pipe[0].frame();
        domain.execute(() -> require(body[0].children().size() == 3 && body[0].children().get(0).id().equals(firstIdentity[0]), "移除条目无需业务摘树且保留其他节点"));
        int priorMeasures = host.measures;
        PaintPlan stable = host.last;
        pipe[0].frame();
        require(host.measures == priorMeasures && host.last == stable, "无失效帧复用几何/计划，不全树重新测量");
        host.width = 60; host.glyph = 6; host.metrics++;
        pipe[0].frame();
        domain.execute(() -> {
            require(pipe[0].current().geometry.get(panel[0]).box.width == host.width, "窗口及字体变化进入共同几何");
            PaintCommand rendered = host.last.commands().stream().filter(command -> command.kind == PaintCommand.Kind.TEXT && command.text.lines().get(0).text.startsWith("标题")).findFirst().get();
            LayoutBox hitBox = pipe[0].current().geometry.get(title[0]).box;
            require(rendered.box.x == hitBox.x && rendered.box.y == hitBox.y, "绘制和命中使用同代坐标");

        });

        System.out.println("软件面板：动态列表/自然标题/剩余正文/事件/环境变化/裁剪/模态/不可变计划通过");
    }
    private void clippingAndModal(ReactiveDomain domain) {
        SoftwareHost host = new SoftwareHost();
        SceneRuntime[] runtime = new SceneRuntime[1];
        SceneFramePipeline[] pipe = new SceneFramePipeline[1];
        Signal<Boolean>[] clip = new Signal[1];
        Signal<Boolean>[] modal = new Signal[1];
        Signal<Integer>[] scrolled = new Signal[1];
        Signal<Integer>[] hits = new Signal[1];
        SceneNode[] child = new SceneNode[1], overflow = new SceneNode[1], modalNode = new SceneNode[1];
        domain.execute(() -> {
            runtime[0] = sceneRuntime(domain); pipe[0] = new SceneFramePipeline(runtime[0], host, host);
            clip[0] = domain.signal(false); modal[0] = domain.signal(false); hits[0] = domain.signal(0); scrolled[0] = domain.signal(0);
            child[0] = new SceneNode().size(Size.fixed(40), Size.fixed(10)).onClick(() -> hits[0].set(hits[0].get() + 1));
            overflow[0] = new SceneNode().size(Size.fixed(10), Size.fixed(10)).onScroll(delta -> scrolled[0].set(scrolled[0].get() + delta)).add(child[0]).bind(NodeBinding.value(clip[0], SceneNode::clip));
            runtime[0].attach(null, overflow[0]);
            modalNode[0] = new SceneNode().size(Size.fixed(5), Size.fixed(5)).modal(true).bind(NodeBinding.value(modal[0], SceneNode::visible));
            runtime[0].attach(null, modalNode[0]);
        });
        pipe[0].click(20, 1); pipe[0].frame();
        domain.execute(() -> { require(hits[0].get() == 1, "父盒外未裁剪子节点可点"); clip[0].set(true); });
        pipe[0].click(20, 1); pipe[0].frame();
        domain.execute(() -> { require(hits[0].get() == 1, "相同坐标受祖先裁剪后不可点"); clip[0].set(false); modal[0].set(true); });
        pipe[0].click(20, 1); pipe[0].scroll(1, 1, 1); pipe[0].frame();
        domain.execute(() -> { require(hits[0].get() == 1 && scrolled[0].get() == 0, "模态隔离后台点击及滚轮操作"); modal[0].set(false); });
        pipe[0].click(20, 1); pipe[0].scroll(1, 1, 1); pipe[0].frame();
        domain.execute(() -> {
            require(hits[0].get() == 2 && scrolled[0].get() == 1, "模态关闭恢复点击和滚轮资格");

            SceneRuntime bounded = sceneRuntime(domain);
            SceneNode first = new SceneNode().size(Size.fill(1).bounds(9, 20), Size.fixed(1));
            SceneNode second = new SceneNode().size(Size.fill(1).bounds(0, 2), Size.fixed(1));
            SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fixed(10), Size.fixed(1)).add(first).add(second);
            bounded.attach(null, row);
            SceneFramePipeline boundedPipe = new SceneFramePipeline(bounded, host, host);
            SceneGeometryView geometry = boundedPipe.current().geometry;
            require(geometry.get(first).box.width == 9 && geometry.get(second).box.width == 1, "填充上下界共同约束时空间可行不应溢出");

        });
    }

    private void reviewedBoundaries(ReactiveDomain domain) {
        domain.execute(() -> {
            SoftwareHost host = new SoftwareHost();
            SceneRuntime runtime = sceneRuntime(domain);
            Signal<List<Integer>> list = domain.signal(Arrays.asList(1, 2));
            Signal<Integer> color = domain.signal(0xFF000000);
            SceneNode repeat = new SceneNode().bind(NodeBinding.repeat(list, item -> item,
                item -> new SceneNode().bind(NodeBinding.value(item, (node, value) -> node.text("条目" + value)))));
            MountHandle handle = runtime.attach(null, repeat); runtime.preparePresentation();
            SceneNode saved = repeat.children().get(0); UUID identity = saved.id();
            handle.detach(); runtime.attach(null, repeat); runtime.preparePresentation();
            require(repeat.children().get(0) == saved && saved.id().equals(identity), "repeat 卸载/重挂载保留逻辑条目对象与 UUID");
            SceneNode paint = new SceneNode().text("颜色测试").bind(NodeBinding.value(color, SceneNode::background));
            runtime.attach(null, paint);
            SceneFramePipeline pipe = new SceneFramePipeline(runtime, host, host);
            ScenePresentation old = pipe.current(); int measures = host.measures;
            color.set(0xFFFFFFFF); ScenePresentation next = pipe.current();
            require(host.measures == measures && next.geometry == old.geometry && next.paint != old.paint, "paint-only 失效只重建计划，复用几何并且不测量文本");

            SceneRuntime layouts = sceneRuntime(domain);
            SceneNode limited = new SceneNode().size(Size.fill(1).bounds(0, 25), Size.natural()).text("abcdefghij");
            SceneNode column = new SceneNode().size(Size.fill(1), Size.natural()).add(limited);
            MountHandle active = layouts.attach(null, column);
            SceneFramePipeline geometry = new SceneFramePipeline(layouts, host, host);
            SceneGeometryView result = geometry.current().geometry;
            int expectedLimitedHeight = (int) Math.ceil((double) limited.text().length() * host.glyph / limited.width().max) * host.lineHeight;
            require(result.get(limited).box.height == expectedLimitedHeight && result.get(column).box.height == expectedLimitedHeight, "受限实际宽度换行高度递归传播自然父高");
            active.destroy();
            SceneNode a = new SceneNode().size(Size.fill(1), Size.natural()).text("abcdefghijklmnopqrst");
            SceneNode b = new SceneNode().size(Size.fill(1), Size.natural()).text("abcdefghijklmnopqrst");
            SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).add(a).add(b);
            active = layouts.attach(null, row); result = geometry.current().geometry;
            int actualWidth = result.get(a).box.width;
            int expectedRowHeight = (int) Math.ceil((double) a.text().length() * host.glyph / actualWidth) * host.lineHeight;
            require(result.get(a).box.height == expectedRowHeight && result.get(row).box.height == expectedRowHeight, "兄弟分配最终宽度决定子自然高度与行自然高度");
            active.destroy();
            SceneNode min = new SceneNode().size(Size.fill(1), Size.fill(1).bounds(60, Integer.MAX_VALUE));
            SceneNode max = new SceneNode().size(Size.fill(1), Size.fill(1).bounds(0, 20));
            SceneNode bounded = new SceneNode().size(Size.fill(1), Size.fixed(100)).add(min).add(max);
            active = layouts.attach(null, bounded); result = geometry.current().geometry;
            require(result.get(min).box.height == bounded.height().value - max.height().max && result.get(max).box.height == max.height().max, "max 释放的剩余空间回到暂触 min 的可增长项");
            active.destroy();
            SceneNode capped = new SceneNode().size(Size.fill(1), Size.fill(2).bounds(0, 1));
            SceneNode rest1 = new SceneNode().size(Size.fill(1), Size.fill(1)), rest2 = new SceneNode().size(Size.fill(1), Size.fill(1));
            SceneNode remainder = new SceneNode().size(Size.fixed(10), Size.fixed(2)).add(capped).add(rest1).add(rest2);
            layouts.attach(null, remainder); result = geometry.current().geometry;
            int total = result.get(capped).box.height + result.get(rest1).box.height + result.get(rest2).box.height;
            require(result.get(capped).box.height <= capped.height().max && total == remainder.height().value, "整数余数保持总量且不能突破硬 max");

        });
    }
    private void customControl(ReactiveDomain domain) {
        domain.execute(() -> {
            SoftwareHost host = new SoftwareHost(); SceneRuntime runtime = sceneRuntime(domain);
            Signal<Integer> desired = domain.signal(15);
            SceneNode child = new SceneNode().text("组合子节点");
            SceneNode control = new SceneNode().size(Size.fill(1), Size.natural()).add(child).control(new SceneControl() {
                @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(desired); }
                @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(limits.availableWidth, desired.get()); }
                @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated) {
                    return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(child, new LayoutBox(3, 2, allocated.width - 3, allocated.height - 2))));
                }
                @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) { target.add(PaintCommand.rect(geometry.get(node).box, geometry.get(node).clip, 0xFFFFFFFF)); }
            });
            runtime.attach(null, control); SceneFramePipeline pipe = new SceneFramePipeline(runtime, host, host);
            require(pipe.current().geometry.get(control).box.height == desired.get(), "控件 measure 决定自然尺寸");
            desired.set(30); ScenePresentation current = pipe.current();
            require(current.geometry.get(control).box.height == desired.get() && current.geometry.get(child).box.y == 2, "声明测量依赖通知后自动重排并保存子区域");
            require(current.paint.commands().get(0).box == current.geometry.get(control).box, "扩展绘制消费共同几何");

        });
    }
    private static void immutablePayload() {
        List<TextLayout.Line> supplied = new ArrayList<>(); supplied.add(new TextLayout.Line("原文", 0, 0));
        TextStyle style = new TextStyle("default", 12, 0xFFFFFFFF);
        TextLayout text = new TextLayout(10, 10, style, supplied);
        PaintPlanBuilder builder = new PaintPlanBuilder().add(PaintCommand.text(new LayoutBox(0, 0, 10, 10), null, text));
        PaintPlan saved = builder.build();
        supplied.clear(); supplied.add(new TextLayout.Line("篡改", 0, 0));
        builder.add(PaintCommand.rect(new LayoutBox(1, 1, 1, 1), null, 0xFFFFFFFF));
        require(saved.commands().size() == 1 && saved.commands().get(0).text.lines().get(0).text.equals("原文"), "构建者及外部列表后续修改不影响已封闭计划");
        try { saved.commands().clear(); throw new AssertionError("计划命令不可变"); } catch (UnsupportedOperationException expected) { }
        try { text.lines().clear(); throw new AssertionError("嵌套文本行不可变"); } catch (UnsupportedOperationException expected) { }
    }
}
