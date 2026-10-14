package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;

/** 防住真实长列表绘制时每个文字都重新命中整页；不设置机器时间阈值。 */
public final class HoverProjectionScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new HoverProjectionScenario().run(args); }
    @Override protected void runTests() throws Exception {
        scenario("重复读取、交互换代与移动", () -> {
            ReactiveDomain domain = ReactiveDomain.direct();
            SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
            int[] queries = {0}; SceneNode[] button = {null};
            club.heiqi.uilib.ui.reactive.Signal<Boolean>[] enabled = new club.heiqi.uilib.ui.reactive.Signal[1];
            SceneGeometryView[] captured = {null};
            ScenePageSession session = own(ScenePageSession.open(domain, host, host, context -> {
                enabled[0] = domain.signal(true);
                button[0] = new SceneNode().size(Size.fixed(60), Size.fixed(30)).bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(enabled[0], SceneNode::enabled)).onClick(() -> {}).control(new SceneControl() {
                    @Override public club.heiqi.uilib.ui.scene.layout.MeasuredSize measure(SceneNode node, club.heiqi.uilib.ui.scene.layout.Constraints limits, club.heiqi.uilib.ui.scene.text.SceneTextMeasurer text) { return new club.heiqi.uilib.ui.scene.layout.MeasuredSize(60, 30); }
                    @Override public club.heiqi.uilib.ui.scene.layout.ChildPlacements arrange(SceneNode node, club.heiqi.uilib.ui.scene.layout.LayoutBox allocated, club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer children) { return new club.heiqi.uilib.ui.scene.layout.ChildPlacements(java.util.Collections.emptyList()); }
                    @Override public Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
                        queries[0]++; return geometry.get(node).box.contains(x, y) ? node : null;
                    }
                    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder output) {}
                });
                context.mount(button[0]);
            }));
            session.pointer(ScenePointerEvent.move(10, 10)); session.frame();
            domain.execute(() -> {
                SceneGeometryView view = session.pipeline().current().geometry; captured[0] = view;
                require(session.runtime().pointerRouter().hovered(view) == button[0], "初始命中错误");
                int once = queries[0];
                for (int i = 0; i < 40; i++) {
                    require(session.runtime().pointerRouter().hovered(view) == button[0], "重复呈现丢失hover");
                    require(session.runtime().pointerRouter().passiveHovered(view) == button[0], "passive与click投影串用");
                }
                require(queries[0] == once, "每个消费者重复扫描了整页");
                // 相同几何也必须立即服从交互声明换代，不能只按几何身份缓存。
            });
            domain.execute(() -> enabled[0].set(false)); session.frame();
            domain.execute(() -> {
                SceneGeometryView view = captured[0];
                require(session.runtime().pointerRouter().hovered(view) == null, "禁用后残留hover");
                require(session.runtime().pointerRouter().passiveHovered(view) == null, "禁用后残留passive");
            });
            domain.execute(() -> enabled[0].set(true)); session.frame();
            domain.execute(() -> {
                SceneGeometryView view = captured[0];
                require(session.runtime().pointerRouter().hovered(view) == button[0], "恢复后未重新命中");
            });
            session.pointer(ScenePointerEvent.move(90, 70)); session.frame();
            domain.execute(() -> require(session.runtime().pointerRouter().hovered(session.pipeline().current().geometry) == null, "移动后残留旧hover"));
            session.pointer(ScenePointerEvent.cancel()); session.frame();
            domain.execute(() -> require(session.runtime().pointerRouter().passiveHovered(session.pipeline().current().geometry) == null, "取消后残留passive"));
        });
    }
}
