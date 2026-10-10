package club.heiqi.uilib.ui.scene.integration;

import java.util.Collections;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 固定按钮区域验证生产导航/滚动/命中，不以宿主文字模拟显露算法。 */
public final class FocusRevealScenario extends TestApplication {
    private static final class Host implements SceneHost, SceneTextMeasurer {
        public int width() {
            return 200;
        }

        public int height() {
            return 180;
        }

        public long metricsRevision() {
            return 0;
        }

        public void replay(PaintPlan plan) {}

        public TextLayout layout(String text, TextStyle style, int width) {
            return new TextLayout(0, 0, style, Collections.emptyList());
        }
    }

    private static SceneNode button(
            club.heiqi.uilib.ui.scene.host.ScenePageContext page,
            Runnable action,
            int width,
            int height) {
        return SceneButton.create(page, () -> "", () -> SceneTheme.dark(), action)
                .size(Size.fixed(width), Size.fixed(height));
    }

    private static SceneGeometryView.Entry entry(ScenePageSession page, SceneNode node) {
        return page.pipeline().current().geometry.get(node);
    }

    @Override
    protected void runTests() throws Exception {
        scenario("纵向最小显露、循环返回和导航后命中", this::vertical);
        scenario("嵌套横纵滚动逐层显露", this::nested);
        scenario("初始请求和普通更新不追焦点，大目标单项导航稳定", this::intentBoundaries);
        scenario("来源变更后失效导航目标不滚后台或新挂载", this::invalidatedIntent);
        scenario("非滚动祖先裁剪限制导航显露区域", this::ancestorClip);
        scenario("正交外层裁剪保留内层导航轴的可见限制", this::orthogonalClip);
    }

    private void orthogonalClip() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        SceneNode[] n = new SceneNode[3];
        ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            n[0] =
                                    new SceneNode()
                                            .size(Size.fixed(50), Size.fixed(60))
                                            .scrollable(true);
                            n[1] =
                                    new SceneNode()
                                            .axis(SceneNode.Axis.ROW)
                                            .size(Size.fixed(100), Size.fixed(40))
                                            .scrollable(true);
                            n[2] = button(ctx, () -> {}, 20, 40);
                            n[1].add(button(ctx, () -> {}, 80, 40))
                                    .add(n[2])
                                    .add(new SceneNode().size(Size.fixed(100), Size.fixed(40)));
                            ctx.mount(n[0].add(n[1]));
                        });
        onClose(page::close);
        page.frame();
        page.navigation(SceneNavigationEvent.NEXT);
        page.frame();
        domain.execute(
                () -> {
                    require(entry(page, n[1]).scrollOffset == 50, "正交祖先裁剪被丢弃");
                    require(entry(page, n[2]).box.x == 30, "目标未进入不可横滚外层可视区");
                });
    }

    private void ancestorClip() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        SceneNode[] n = new SceneNode[3];
        ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            n[0] =
                                    new SceneNode()
                                            .size(Size.fixed(100), Size.fixed(100))
                                            .scrollable(true);
                            n[1] = button(ctx, () -> {}, 100, 30);
                            n[2] = button(ctx, () -> {}, 100, 30);
                            ctx.mount(
                                    new SceneNode()
                                            .size(Size.fixed(100), Size.fixed(50))
                                            .clip(true)
                                            .add(
                                                    n[0].add(n[1])
                                                            .add(n[2])
                                                            .add(
                                                                    new SceneNode()
                                                                            .size(
                                                                                    Size.fixed(100),
                                                                                    Size.fixed(
                                                                                            140)))));
                        });
        onClose(page::close);
        page.frame();
        page.navigation(SceneNavigationEvent.NEXT);
        page.frame();
        domain.execute(
                () -> {
                    require(entry(page, n[0]).scrollOffset == 10, "非滚动祖先裁剪未纳入最小显露");
                    require(entry(page, n[2]).box.y == 20, "非滚动祖先内目标未完整显露");
                });
        page.navigation(SceneNavigationEvent.PREVIOUS);
        page.frame();
        domain.execute(() -> require(entry(page, n[0]).scrollOffset == 0, "返回裁剪内首项未最小滚动"));
    }

    private void invalidatedIntent() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        SceneNode[] n = new SceneNode[4];
        Signal<Boolean>[] modal = new Signal[1];
        ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            modal[0] = domain.signal(false);
                            n[0] =
                                    new SceneNode()
                                            .size(Size.fixed(100), Size.fixed(60))
                                            .scrollable(true);
                            n[1] = button(ctx, () -> {}, 100, 40);
                            n[2] = button(ctx, () -> {}, 100, 40);
                            n[0].add(n[1]).add(n[2]);
                            ctx.mount(n[0]);
                            n[3] =
                                    new SceneNode()
                                            .modal(true)
                                            .size(Size.fixed(100), Size.fixed(60))
                                            .bind(NodeBinding.value(modal[0], SceneNode::visible))
                                            .add(button(ctx, () -> {}, 100, 40));
                            ctx.mount(n[3]);
                        });
        onClose(page::close);
        page.frame();
        domain.execute(
                () -> {
                    page.runtime().focusRouter().navigate(false);
                    page.runtime().focusRouter().request(page.runtime().captureMount(n[1]));
                });
        page.frame();
        domain.execute(() -> require(entry(page, n[0]).scrollOffset == 0, "request改焦点后旧显露仍滚"));
        domain.execute(
                () -> {
                    page.runtime().focusRouter().navigate(false);
                    modal[0].set(true);
                });
        page.frame();
        domain.execute(() -> require(entry(page, n[0]).scrollOffset == 0, "新模态打开仍滚后台旧目标"));
        domain.execute(() -> modal[0].set(false));
        page.frame();
        domain.execute(() -> require(entry(page, n[0]).scrollOffset == 0, "模态返回擅自显露"));
        domain.execute(
                () -> {
                    page.runtime().focusRouter().navigate(false);
                    page.close();
                });
        require(page.ended(), "关闭没有结束显露所属会话");
    }

    private void vertical() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        SceneNode[] nodes = new SceneNode[4];
        int[] clicks = {0};
        ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            nodes[0] =
                                    new SceneNode()
                                            .size(Size.fixed(120), Size.fixed(70))
                                            .padding(5)
                                            .gap(5)
                                            .scrollable(true);
                            for (int i = 1; i < 4; i++) {
                                nodes[i] = button(ctx, () -> clicks[0]++, 110, 30);
                                nodes[0].add(nodes[i]);
                            }
                            ctx.mount(nodes[0]);
                        });
        onClose(page::close);
        page.frame();
        domain.execute(() -> require(entry(page, nodes[0]).scrollOffset == 0, "初始焦点改变滚动"));
        page.navigation(SceneNavigationEvent.NEXT);
        page.frame();
        domain.execute(
                () -> {
                    require(
                            entry(page, nodes[0]).scrollOffset == 5,
                            "第二按钮应只滚5px actual=" + entry(page, nodes[0]).scrollOffset);
                    require(entry(page, nodes[2]).box.y == 35, "显露最终几何错误");
                });
        page.navigation(SceneNavigationEvent.NEXT);
        page.frame();
        domain.execute(() -> require(entry(page, nodes[0]).scrollOffset == 40, "第三按钮最小滚40px"));
        page.pointer(ScenePointerEvent.down(0, 20, 45));
        page.pointer(ScenePointerEvent.up(0, 20, 45));
        page.frame();
        require(clicks[0] == 1, "新可见几何未用于命中");
        page.navigation(SceneNavigationEvent.NEXT);
        page.frame();
        domain.execute(() -> require(entry(page, nodes[0]).scrollOffset == 0, "循环返回首项未显露"));
        page.navigation(SceneNavigationEvent.NEXT);
        page.navigation(SceneNavigationEvent.PREVIOUS);
        page.frame();
        domain.execute(() -> require(entry(page, nodes[0]).scrollOffset == 0, "批次前后导航未保持顺序"));
    }

    private void nested() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        SceneNode[] n = new SceneNode[4];
        ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            n[0] =
                                    new SceneNode()
                                            .size(Size.fixed(120), Size.fixed(80))
                                            .scrollable(true);
                            n[1] =
                                    new SceneNode()
                                            .axis(SceneNode.Axis.ROW)
                                            .size(Size.fixed(120), Size.fixed(40))
                                            .scrollable(true);
                            n[2] = button(ctx, () -> {}, 80, 40);
                            n[3] = button(ctx, () -> {}, 80, 40);
                            n[1].add(n[2]).add(n[3]);
                            ctx.mount(
                                    n[0].add(new SceneNode().size(Size.fixed(120), Size.fixed(70)))
                                            .add(n[1]));
                        });
        onClose(page::close);
        page.frame();
        page.navigation(SceneNavigationEvent.NEXT);
        page.frame();
        domain.execute(
                () -> {
                    require(entry(page, n[1]).scrollOffset == 40, "横向内层未最小滚动");
                    require(entry(page, n[0]).scrollOffset == 30, "外层未协调显露");
                    require(
                            entry(page, n[3]).box.x == 40 && entry(page, n[3]).box.y == 40,
                            "最终目标坐标错误");
                });
        page.frame();
        domain.execute(
                () ->
                        require(
                                entry(page, n[0]).scrollOffset == 30
                                        && entry(page, n[1]).scrollOffset == 40,
                                "静态帧重复显露振荡"));
    }

    private void intentBoundaries() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        SceneNode[] n = new SceneNode[2];
        Signal<Boolean>[] hidden = new Signal[1];
        ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            hidden[0] = domain.signal(false);
                            n[1] =
                                    button(ctx, () -> {}, 100, 140)
                                            .bind(
                                                    NodeBinding.value(
                                                            hidden[0],
                                                            (node, value) -> node.enabled(!value)));
                            n[0] =
                                    new SceneNode()
                                            .size(Size.fixed(100), Size.fixed(60))
                                            .scrollable(true)
                                            .add(
                                                    new SceneNode()
                                                            .size(Size.fixed(100), Size.fixed(40)))
                                            .add(n[1]);
                            ctx.mount(n[0]);
                        });
        onClose(page::close);
        page.frame();
        domain.execute(
                () -> {
                    page.runtime().focusRouter().request(page.runtime().captureMount(n[1]));
                    require(entry(page, n[0]).scrollOffset == 0, "显式请求擅自滚动");
                });
        page.navigation(SceneNavigationEvent.NEXT);
        page.frame();
        domain.execute(() -> require(entry(page, n[0]).scrollOffset == 40, "大按钮应显露起始边缘"));
        page.navigation(SceneNavigationEvent.NEXT);
        page.frame();
        domain.execute(() -> require(entry(page, n[0]).scrollOffset == 40, "大按钮单项重复振荡"));
        page.pointer(ScenePointerEvent.scroll(20, 20, -1));
        page.frame();
        final int[] offset = {0};
        domain.execute(() -> offset[0] = entry(page, n[0]).scrollOffset);
        page.frame();
        domain.execute(
                () -> {
                    require(entry(page, n[0]).scrollOffset == offset[0], "普通帧拉回焦点");
                    hidden[0].set(true);
                });
        page.navigation(SceneNavigationEvent.NEXT);
        page.frame();
        domain.execute(
                () -> require(page.runtime().focusRouter().focused().get() == null, "失活目标仍被导航"));
    }

    public static void main(String[] args) throws Exception {
        new FocusRevealScenario().run(args);
    }
}
