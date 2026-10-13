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
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
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

    static final class SoftwareHost implements SceneHost, SceneTextMeasurer {
        int width = 100, height = 80, glyph = 5, lineHeight = 10, measures;
        long metrics;
        PaintPlan last;

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public long metricsRevision() {
            return metrics;
        }

        @Override
        public void replay(PaintPlan plan) {
            last = plan;
        }

        @Override
        public TextLayout layout(String text, TextStyle style, int availableWidth) {
            measures++;
            List<TextLayout.Line> lines = new ArrayList<>();
            int characters = Math.max(1, availableWidth / glyph), maxWidth = 0;
            if (!text.isEmpty()) {
                for (int start = 0; start < text.length(); start += characters) {
                    String line =
                            text.substring(start, Math.min(text.length(), start + characters));
                    maxWidth = Math.max(maxWidth, line.length() * glyph);
                    lines.add(new TextLayout.Line(line, 0, lines.size() * lineHeight));
                }
            }
            return new TextLayout(maxWidth, lines.size() * lineHeight, style, lines);
        }
    }

    private void boundedScrolling() {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareHost host = new SoftwareHost();
        Signal<Integer>[] contentHeight = new Signal[1];
        SceneNode[] viewport = new SceneNode[1];
        ScenePageSession session =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            contentHeight[0] = domain.signal(300);
                            SceneNode content =
                                    new SceneNode()
                                            .size(Size.fixed(100), Size.fixed(300))
                                            .bind(
                                                    NodeBinding.value(
                                                            contentHeight[0],
                                                            (node, value) ->
                                                                    node.size(
                                                                            Size.fixed(100),
                                                                            Size.fixed(value))));
                            viewport[0] =
                                    new SceneNode()
                                            .size(Size.fixed(100), Size.fixed(80))
                                            .scrollable(true)
                                            .scrollStep(20)
                                            .add(content);
                            ctx.mount(viewport[0]);
                        });
        onClose(session::close);
        SceneFramePipeline pipe = session.pipeline();
        domain.execute(
                () -> {
                    ScenePresentation original = pipe.current();
                    for (int i = 0; i < 100; i++)
                        session.pointer(ScenePointerEvent.scroll(5, 5, -1));
                    session.frame();
                    SceneGeometryView.Entry end = pipe.current().geometry.get(viewport[0]);
                    require(
                            end.scrollOffset == end.scrollMaximum && end.scrollMaximum > 20,
                            "实际滚动必须止于真实内容末尾");
                    session.pointer(ScenePointerEvent.scroll(5, 5, 1));
                    session.frame();
                    require(
                            pipe.current().geometry.get(viewport[0]).scrollOffset
                                    == end.scrollMaximum - 20,
                            "末尾反向一格立即移动，不偿还不可见滚动债务");
                    require(original.geometry.get(viewport[0]).scrollOffset == 0, "滚动后旧几何仍不可变");
                    contentHeight[0].set(100);
                    SceneGeometryView.Entry smaller = pipe.current().geometry.get(viewport[0]);
                    require(smaller.scrollOffset == smaller.scrollMaximum, "内容缩短即时夹到新边界");
                    session.pointer(ScenePointerEvent.scroll(5, 5, 1));
                    session.frame();
                    require(
                            pipe.current().geometry.get(viewport[0]).scrollOffset == 0,
                            "内容缩短后反向输入立即有效");
                    contentHeight[0].set(300);
                    session.pointer(ScenePointerEvent.scroll(5, 5, Integer.MIN_VALUE));
                    session.frame();
                    require(
                            pipe.current().geometry.get(viewport[0]).scrollOffset
                                    == pipe.current().geometry.get(viewport[0]).scrollMaximum,
                            "极端滚轮值不可整数溢出");
                });
    }

    @Override
    protected void runTests() throws Exception {
        scenario("有界滚动与内容缩短", this::boundedScrolling);
        scenario("动态列表布局与同代绘制命中", this::dynamicPanel);
        ReactiveDomain domain = ReactiveDomain.direct();
        scenario("裁剪与模态输入", () -> clippingAndModal(domain));
        scenario("不可变绘制载荷", SoftwareSceneScenario::immutablePayload);
        scenario("评审边界回归", () -> reviewedBoundaries(domain));
        scenario("自定义控件共同几何", () -> customControl(domain));
        scenario("自定义排列两轴尺寸诊断与合法溢出", this::customPlacementSizes);
    }

    private void dynamicPanel() throws Exception {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            throw new AssertionError("软件场景执行类路径不得包含 MC");
        } catch (ClassNotFoundException expected) {
        }
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareHost host = new SoftwareHost();
        SceneRuntime[] runtime = new SceneRuntime[1];
        SceneFramePipeline[] pipe = new SceneFramePipeline[1];
        Signal<Integer>[] count = new Signal[1];
        Signal<String>[] heading = new Signal[1];
        Signal<List<Integer>>[] items = new Signal[1];
        SceneNode[] panel = new SceneNode[1],
                title = new SceneNode[1],
                body = new SceneNode[1],
                button = new SceneNode[1];
        ScenePresentation[] saved = new ScenePresentation[1];
        UUID[] firstIdentity = new UUID[1];
        ScenePageSession session =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            runtime[0] = ctx.runtime();
                            count[0] = domain.signal(0);
                            heading[0] = domain.signal("标题");
                            items[0] = domain.signal(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8));
                            Owner business = ctx.scope();
                            Computed<String> headingText =
                                    domain.computed(
                                            business, () -> heading[0].get() + count[0].get());
                            title[0] =
                                    new SceneNode()
                                            .size(Size.fill(1), Size.natural())
                                            .bind(NodeBinding.value(headingText, SceneNode::text));
                            button[0] =
                                    new SceneNode()
                                            .size(Size.fixed(40), Size.fixed(10))
                                            .text("添加")
                                            .onClick(
                                                    () -> {
                                                        count[0].set(count[0].get() + 1);
                                                        List<Integer> next =
                                                                new ArrayList<>(items[0].get());
                                                        next.add(next.size() + 1);
                                                        items[0].set(next);
                                                    });
                            SceneNode actions =
                                    new SceneNode()
                                            .axis(SceneNode.Axis.ROW)
                                            .size(Size.fill(1), Size.fixed(10))
                                            .add(button[0]);
                            body[0] =
                                    new SceneNode()
                                            .size(Size.fill(1), Size.fill(1))
                                            .scrollable(true)
                                            .bind(
                                                    NodeBinding.repeat(
                                                            items[0],
                                                            value -> value,
                                                            value ->
                                                                    new SceneNode()
                                                                            .size(
                                                                                    Size.fill(1),
                                                                                    Size.fixed(10))
                                                                            .bind(
                                                                                    NodeBinding
                                                                                            .value(
                                                                                                    value,
                                                                                                    (node,
                                                                                                            item) ->
                                                                                                            node
                                                                                                                    .text(
                                                                                                                            "条目"
                                                                                                                                    + item)))));
                            panel[0] =
                                    new SceneNode()
                                            .size(Size.fill(1), Size.fill(1))
                                            .gap(1)
                                            .add(title[0])
                                            .add(actions)
                                            .add(body[0]);
                            ctx.mount(panel[0]);
                        });
        onClose(session::close);
        pipe[0] = session.pipeline();
        session.frame();
        domain.execute(
                () -> {
                    saved[0] = pipe[0].current();
                    LayoutBox header = saved[0].geometry.get(title[0]).box;
                    LayoutBox contents = saved[0].geometry.get(body[0]).box;
                    int expectedRemaining =
                            host.height
                                    - header.height
                                    - button[0].height().value
                                    - panel[0].gap() * 2;
                    require(contents.height == expectedRemaining, "正文自动获得扣除自然标题/操作区/间距后的剩余空间");
                    firstIdentity[0] = body[0].children().get(0).id();
                    LayoutBox action = saved[0].geometry.get(button[0]).box;
                    session.pointer(
                            ScenePointerEvent.syntheticActivation(action.x + 1, action.y + 1));
                    session.pointer(
                            ScenePointerEvent.syntheticActivation(action.x + 1, action.y + 1));
                });
        session.frame();
        domain.execute(
                () -> {
                    require(
                            count[0].get() == 2 && items[0].get().size() == 10,
                            "连续事件按原序独立操作并立即读取累计值");
                    require(
                            body[0].children().size() == 10
                                    && body[0].children().get(0).id().equals(firstIdentity[0]),
                            "列表增量声明保留既有节点身份");
                    require(
                            saved[0].geometry.get(title[0]).text.lines().get(0).text.equals("标题0"),
                            "旧代几何不随新标题修改");
                    heading[0].set("标题变长以后应自动折行并重新分配正文剩余空间");
                });
        session.frame();
        domain.execute(
                () -> {
                    ScenePresentation current = pipe[0].current();
                    int expectedTitleHeight =
                            (int)
                                            Math.ceil(
                                                    (double) title[0].text().length()
                                                            / Math.max(1, host.width / host.glyph))
                                    * host.lineHeight;
                    require(
                            current.geometry.get(title[0]).box.height == expectedTitleHeight,
                            "标题按当前度量和宽度自动重排");
                    require(
                            current.geometry.get(body[0]).box.height
                                    == host.height - expectedTitleHeight - 10 - 2,
                            "正文随标题高度重分配");
                    items[0].set(Arrays.asList(1, 3, 5));
                });
        session.frame();
        domain.execute(
                () ->
                        require(
                                body[0].children().size() == 3
                                        && body[0].children().get(0).id().equals(firstIdentity[0]),
                                "移除条目无需业务摘树且保留其他节点"));
        int priorMeasures = host.measures;
        PaintPlan stable = host.last;
        session.frame();
        require(host.measures == priorMeasures && host.last == stable, "无失效帧复用几何/计划，不全树重新测量");
        host.width = 60;
        host.glyph = 6;
        host.metrics++;
        session.frame();
        domain.execute(
                () -> {
                    require(
                            pipe[0].current().geometry.get(panel[0]).box.width == host.width,
                            "窗口及字体变化进入共同几何");
                    PaintCommand rendered =
                            host.last.commands().stream()
                                    .filter(
                                            command ->
                                                    command.kind == PaintCommand.Kind.TEXT
                                                            && command.text
                                                                    .lines()
                                                                    .get(0)
                                                                    .text
                                                                    .startsWith("标题"))
                                    .findFirst()
                                    .get();
                    LayoutBox hitBox = pipe[0].current().geometry.get(title[0]).box;
                    require(
                            rendered.box.x == hitBox.x && rendered.box.y == hitBox.y,
                            "绘制和命中使用同代坐标");
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
        SceneNode[] child = new SceneNode[1],
                overflow = new SceneNode[1],
                modalNode = new SceneNode[1];
        ScenePageSession session =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            runtime[0] = ctx.runtime();
                            clip[0] = domain.signal(false);
                            modal[0] = domain.signal(false);
                            hits[0] = domain.signal(0);
                            scrolled[0] = domain.signal(0);
                            child[0] =
                                    new SceneNode()
                                            .size(Size.fixed(40), Size.fixed(10))
                                            .onClick(() -> hits[0].set(hits[0].get() + 1));
                            overflow[0] =
                                    new SceneNode()
                                            .size(Size.fixed(10), Size.fixed(10))
                                            .onScroll(
                                                    delta ->
                                                            scrolled[0].set(
                                                                    scrolled[0].get() + delta))
                                            .add(child[0])
                                            .bind(NodeBinding.value(clip[0], SceneNode::clip));
                            ctx.mount(overflow[0]);
                            modalNode[0] =
                                    new SceneNode()
                                            .size(Size.fixed(5), Size.fixed(5))
                                            .modal(true)
                                            .bind(NodeBinding.value(modal[0], SceneNode::visible));
                            ctx.mount(modalNode[0]);
                        });
        onClose(session::close);
        pipe[0] = session.pipeline();
        session.pointer(ScenePointerEvent.syntheticActivation(20, 1));
        session.frame();
        domain.execute(
                () -> {
                    require(hits[0].get() == 1, "父盒外未裁剪子节点可点");
                    clip[0].set(true);
                });
        session.pointer(ScenePointerEvent.syntheticActivation(20, 1));
        session.frame();
        domain.execute(
                () -> {
                    require(hits[0].get() == 1, "相同坐标受祖先裁剪后不可点");
                    clip[0].set(false);
                    modal[0].set(true);
                });
        session.pointer(ScenePointerEvent.syntheticActivation(20, 1));
        session.pointer(ScenePointerEvent.scroll(1, 1, 1));
        session.frame();
        domain.execute(
                () -> {
                    require(hits[0].get() == 1 && scrolled[0].get() == 0, "模态隔离后台点击及滚轮操作");
                    modal[0].set(false);
                });
        session.pointer(ScenePointerEvent.syntheticActivation(20, 1));
        session.pointer(ScenePointerEvent.scroll(1, 1, 1));
        session.frame();
        domain.execute(
                () -> {
                    require(hits[0].get() == 2 && scrolled[0].get() == 1, "模态关闭恢复点击和滚轮资格");
                });
        SceneNode[] first = new SceneNode[1], second = new SceneNode[1];
        ScenePageSession bounded =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            first[0] =
                                    new SceneNode().size(Size.fill(1).bounds(9, 20), Size.fixed(1));
                            second[0] =
                                    new SceneNode().size(Size.fill(1).bounds(0, 2), Size.fixed(1));
                            SceneNode row =
                                    new SceneNode()
                                            .axis(SceneNode.Axis.ROW)
                                            .size(Size.fixed(10), Size.fixed(1))
                                            .add(first[0])
                                            .add(second[0]);
                            ctx.mount(row);
                        });
        onClose(bounded::close);
        domain.execute(
                () -> {
                    SceneFramePipeline boundedPipe = bounded.pipeline();
                    SceneGeometryView geometry = boundedPipe.current().geometry;
                    require(
                            geometry.get(first[0]).box.width == 9
                                    && geometry.get(second[0]).box.width == 1,
                            "填充上下界共同约束时空间可行不应溢出");
                });
    }

    private void reviewedBoundaries(ReactiveDomain domain) {
        SoftwareHost host = new SoftwareHost();
        SceneNode[] repeat = new SceneNode[1];
        MountHandle[] handle = new MountHandle[1];
        Signal<Integer>[] color = new Signal[1];
        ScenePageSession session =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            Signal<List<Integer>> list = domain.signal(Arrays.asList(1, 2));
                            color[0] = domain.signal(0xFF000000);
                            repeat[0] =
                                    new SceneNode()
                                            .bind(
                                                    NodeBinding.repeat(
                                                            list,
                                                            item -> item,
                                                            item ->
                                                                    new SceneNode()
                                                                            .bind(
                                                                                    NodeBinding
                                                                                            .value(
                                                                                                    item,
                                                                                                    (node,
                                                                                                            value) ->
                                                                                                            node
                                                                                                                    .text(
                                                                                                                            "条目"
                                                                                                                                    + value)))));
                            handle[0] = ctx.mount(repeat[0]);
                            ctx.mount(
                                    new SceneNode()
                                            .text("颜色测试")
                                            .bind(
                                                    NodeBinding.value(
                                                            color[0], SceneNode::background)));
                        });
        onClose(session::close);
        domain.execute(
                () -> {
                    SceneRuntime runtime = session.runtime();
                    SceneFramePipeline pipe = session.pipeline();
                    runtime.preparePresentation();
                    SceneNode saved = repeat[0].children().get(0);
                    UUID identity = saved.id();
                    handle[0].detach();
                    runtime.attach(null, repeat[0]);
                    runtime.preparePresentation();
                    require(
                            repeat[0].children().get(0) == saved && saved.id().equals(identity),
                            "repeat 卸载/重挂载保留逻辑条目对象与 UUID");
                    ScenePresentation old = pipe.current();
                    int measures = host.measures;
                    color[0].set(0xFFFFFFFF);
                    ScenePresentation next = pipe.current();
                    require(
                            host.measures == measures
                                    && next.geometry == old.geometry
                                    && next.paint != old.paint,
                            "paint-only 失效只重建计划，复用几何并且不测量文本");
                });
        SceneNode[] limited = new SceneNode[1], column = new SceneNode[1];
        MountHandle[] initial = new MountHandle[1];
        ScenePageSession layoutSession =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            limited[0] =
                                    new SceneNode()
                                            .size(Size.fill(1).bounds(0, 25), Size.natural())
                                            .text("abcdefghij");
                            column[0] =
                                    new SceneNode()
                                            .size(Size.fill(1), Size.natural())
                                            .add(limited[0]);
                            initial[0] = ctx.mount(column[0]);
                        });
        onClose(layoutSession::close);
        domain.execute(
                () -> {
                    SceneRuntime layouts = layoutSession.runtime();
                    SceneFramePipeline geometry = layoutSession.pipeline();
                    MountHandle active = initial[0];
                    SceneGeometryView result = geometry.current().geometry;
                    int expectedLimitedHeight =
                            (int)
                                            Math.ceil(
                                                    (double) limited[0].text().length()
                                                            * host.glyph
                                                            / limited[0].width().max)
                                    * host.lineHeight;
                    require(
                            result.get(limited[0]).box.height == expectedLimitedHeight
                                    && result.get(column[0]).box.height == expectedLimitedHeight,
                            "受限实际宽度换行高度递归传播自然父高");
                    active.destroy();
                    SceneNode a =
                            new SceneNode()
                                    .size(Size.fill(1), Size.natural())
                                    .text("abcdefghijklmnopqrst");
                    SceneNode b =
                            new SceneNode()
                                    .size(Size.fill(1), Size.natural())
                                    .text("abcdefghijklmnopqrst");
                    SceneNode row =
                            new SceneNode()
                                    .axis(SceneNode.Axis.ROW)
                                    .size(Size.fill(1), Size.natural())
                                    .add(a)
                                    .add(b);
                    active = layouts.attach(null, row);
                    result = geometry.current().geometry;
                    int actualWidth = result.get(a).box.width;
                    int expectedRowHeight =
                            (int) Math.ceil((double) a.text().length() * host.glyph / actualWidth)
                                    * host.lineHeight;
                    require(
                            result.get(a).box.height == expectedRowHeight
                                    && result.get(row).box.height == expectedRowHeight,
                            "兄弟分配最终宽度决定子自然高度与行自然高度");
                    active.destroy();
                    SceneNode min =
                            new SceneNode()
                                    .size(Size.fill(1), Size.fill(1).bounds(60, Integer.MAX_VALUE));
                    SceneNode max = new SceneNode().size(Size.fill(1), Size.fill(1).bounds(0, 20));
                    SceneNode bounded =
                            new SceneNode().size(Size.fill(1), Size.fixed(100)).add(min).add(max);
                    active = layouts.attach(null, bounded);
                    result = geometry.current().geometry;
                    require(
                            result.get(min).box.height == bounded.height().value - max.height().max
                                    && result.get(max).box.height == max.height().max,
                            "max 释放的剩余空间回到暂触 min 的可增长项");
                    active.destroy();
                    SceneNode capped =
                            new SceneNode().size(Size.fill(1), Size.fill(2).bounds(0, 1));
                    SceneNode rest1 = new SceneNode().size(Size.fill(1), Size.fill(1)),
                            rest2 = new SceneNode().size(Size.fill(1), Size.fill(1));
                    SceneNode remainder =
                            new SceneNode()
                                    .size(Size.fixed(10), Size.fixed(2))
                                    .add(capped)
                                    .add(rest1)
                                    .add(rest2);
                    layouts.attach(null, remainder);
                    result = geometry.current().geometry;
                    int total =
                            result.get(capped).box.height
                                    + result.get(rest1).box.height
                                    + result.get(rest2).box.height;
                    require(
                            result.get(capped).box.height <= capped.height().max
                                    && total == remainder.height().value,
                            "整数余数保持总量且不能突破硬 max");
                });
    }

    private void customControl(ReactiveDomain domain) {
        SoftwareHost host = new SoftwareHost();
        Signal<Integer>[] desired = new Signal[1];
        SceneNode[] child = new SceneNode[1], control = new SceneNode[1];
        ScenePageSession session =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            desired[0] = domain.signal(15);
                            child[0] = new SceneNode().text("组合子节点");
                            control[0] =
                                    new SceneNode()
                                            .size(Size.fill(1), Size.natural())
                                            .add(child[0])
                                            .control(
                                                    new SceneControl() {
                                                        @Override
                                                        public List<ReadableSignal<?>>
                                                                measureDependencies(
                                                                        SceneNode node) {
                                                            return Collections.singletonList(
                                                                    desired[0]);
                                                        }

                                                        @Override
                                                        public MeasuredSize measure(
                                                                SceneNode node,
                                                                Constraints limits,
                                                                SceneTextMeasurer text) {
                                                            return new MeasuredSize(
                                                                    limits.availableWidth,
                                                                    desired[0].get());
                                                        }

                                                        @Override
                                                        public ChildPlacements arrange(
                                                                SceneNode node,
                                                                LayoutBox allocated, SceneChildMeasurer childMeasurer) {
                                                            return new ChildPlacements(
                                                                    Collections.singletonList(
                                                                            new ChildPlacements
                                                                                    .Entry(
                                                                                    child[0],
                                                                                    new LayoutBox(
                                                                                            3,
                                                                                            2,
                                                                                            allocated
                                                                                                            .width
                                                                                                    - 3,
                                                                                            allocated
                                                                                                            .height
                                                                                                    - 2))));
                                                        }

                                                        @Override
                                                        public void paint(
                                                                SceneNode node,
                                                                SceneGeometryView geometry,
                                                                PaintPlanBuilder target) {
                                                            target.add(
                                                                    PaintCommand.rect(
                                                                            geometry.get(node).box,
                                                                            geometry.get(node).clip,
                                                                            0xFFFFFFFF));
                                                        }
                                                    });
                            ctx.mount(control[0]);
                        });
        onClose(session::close);
        domain.execute(
                () -> {
                    SceneFramePipeline pipe = session.pipeline();
                    require(
                            pipe.current().geometry.get(control[0]).box.height == desired[0].get(),
                            "控件 measure 决定自然尺寸");
                    desired[0].set(30);
                    ScenePresentation current = pipe.current();
                    require(
                            current.geometry.get(control[0]).box.height == desired[0].get()
                                    && current.geometry.get(child[0]).box.y == 2,
                            "声明测量依赖通知后自动重排并保存子区域");
                    require(
                            current.paint.commands().get(0).box
                                    == current.geometry.get(control[0]).box,
                            "扩展绘制消费共同几何");
                });
    }

    /** 重现审计中的 Frame 分配，仍由生产引擎测量与发布；此控件只返回待核验的作者结果。 */
    private static final class PlacementProbe implements SceneControl {
        final int width, height;
        int measuredWidth, measuredHeight;

        PlacementProbe(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }

        public MeasuredSize measure(
                SceneNode node, Constraints limits, SceneTextMeasurer text,
                SceneChildMeasurer children) {
            MeasuredSize measured = children.measureChild(node.children().get(0), limits);
            measuredWidth = measured.width;
            measuredHeight = measured.height;
            return measured;
        }

        public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer childMeasurer) {
            return new ChildPlacements(Collections.singletonList(
                    new ChildPlacements.Entry(node.children().get(0), new LayoutBox(1, 1, width, height))));
        }

        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
    }

    private void customPlacementSizes() {
        SoftwareHost host = new SoftwareHost();
        // 审计的两轴40/200突破；另测无bounds的固定值、自然min与fill max，避免镜像参数乘积。
        Size fixed = Size.fixed(120).bounds(80, 120);
        invalidPlacement(host, fixed, true, 40);
        invalidPlacement(host, fixed, true, 200);
        invalidPlacement(host, fixed, false, 40);
        invalidPlacement(host, fixed, false, 200);
        invalidPlacement(host, Size.fixed(120), true, 100);
        invalidPlacement(host, Size.natural().bounds(80, 120), true, 40);
        invalidPlacement(host, Size.fill(1).bounds(80, 120), false, 200);
        ReactiveDomain domain = ReactiveDomain.direct();
        int[] clicks = {0};
        SceneNode child = new SceneNode().size(Size.fixed(200).bounds(80, 120), Size.fixed(20))
                .onClick(() -> clicks[0]++);
        SceneNode parent = new SceneNode().size(Size.fixed(42), Size.fixed(22))
                .control(new PlacementProbe(120, 20)).add(child);
        try (ScenePageSession page = ScenePageSession.open(domain, host, host, ctx -> ctx.mount(parent))) {
            page.frame();
            domain.execute(() -> require(page.pipeline().current().geometry.get(child).box.width == 120,
                    "固定值先经自身边界限制，合法溢出不得压缩"));
            page.pointer(ScenePointerEvent.down(0, 70, 10));
            page.pointer(ScenePointerEvent.up(0, 70, 10));
            page.frame();
            require(clicks[0] == 1, "父盒外的合法溢出仍沿共同几何命中");
        }
        SceneNode ordinaryChild = new SceneNode().size(Size.fixed(120).bounds(80, 120), Size.fixed(20));
        SceneNode ordinary = new SceneNode().size(Size.fixed(42), Size.fixed(22)).add(ordinaryChild);
        require(new SceneLayoutEngine(host)
                        .layout(Collections.singletonList(ordinary), 300, 300).get(ordinaryChild).box.width == 120,
                "普通布局对照保持固定120");
    }

    private static void invalidPlacement(SoftwareHost host, Size declared, boolean horizontal, int actual) {
        SceneNode child = new SceneNode().size(
                horizontal ? declared : Size.fixed(20), horizontal ? Size.fixed(20) : declared);
        PlacementProbe rule = new PlacementProbe(horizontal ? actual : 20, horizontal ? 20 : actual);
        SceneNode parent = new SceneNode().size(Size.fixed(42), Size.fixed(42)).control(rule).add(child);
        try {
            new SceneLayoutEngine(host).layout(Collections.singletonList(parent), 300, 300);
            throw new AssertionError("非法普通分配被发布或静默钳位：" + actual);
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            require(message.contains("parent=" + parent.id())
                            && message.contains("control=" + rule.getClass().getName())
                            && message.contains("child=" + child.id())
                            && message.contains("axis=" + (horizontal ? "width" : "height"))
                            && message.contains("actual=" + actual)
                            && message.contains("declared=" + declared.mode),
                    "诊断缺少定位或声明事实：" + message);
        }
        if (declared.mode == Size.Mode.FIXED)
            require((horizontal ? rule.measuredWidth : rule.measuredHeight) == 120,
                    "正式测量应保留固定尺寸120");
    }

    private static void immutablePayload() {
        List<TextLayout.Line> supplied = new ArrayList<>();
        supplied.add(new TextLayout.Line("原文", 0, 0));
        TextStyle style = new TextStyle("default", 12, 0xFFFFFFFF);
        TextLayout text = new TextLayout(10, 10, style, supplied);
        PaintPlanBuilder builder =
                new PaintPlanBuilder()
                        .add(PaintCommand.text(new LayoutBox(0, 0, 10, 10), null, text));
        PaintPlan saved = builder.build();
        supplied.clear();
        supplied.add(new TextLayout.Line("篡改", 0, 0));
        builder.add(PaintCommand.rect(new LayoutBox(1, 1, 1, 1), null, 0xFFFFFFFF));
        require(
                saved.commands().size() == 1
                        && saved.commands().get(0).text.lines().get(0).text.equals("原文"),
                "构建者及外部列表后续修改不影响已封闭计划");
        try {
            saved.commands().clear();
            throw new AssertionError("计划命令不可变");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            text.lines().clear();
            throw new AssertionError("嵌套文本行不可变");
        } catch (UnsupportedOperationException expected) {
        }
    }
}
