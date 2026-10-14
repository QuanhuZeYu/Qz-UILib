package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.MarkdownDocument;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 普通容器必须能直接消费最终盒arrange，不能借先调用presentText保存排列。 */
public final class DocumentArrangementScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new DocumentArrangementScenario().run(args); }

    private static SceneChildMeasurer children(SceneNode parent, int minimum, int natural) {
        return new SceneChildMeasurer() {
            public List<SceneNode> flowChildren() { return parent.children(); }
            public MeasuredSize measureChild(SceneNode child, Constraints limits) {
                return new MeasuredSize(limits.availableWidth == 0 ? minimum
                        : limits.availableWidth == Integer.MAX_VALUE ? natural : limits.availableWidth, 40);
            }
        };
    }

    @Override protected void runTests() throws Exception {
        scenario("代码最终盒排列及变宽限界", () -> {
            DocumentCodeViewport control = new DocumentCodeViewport();
            SceneNode content = new SceneNode();
            SceneNode node = new SceneNode().control(control).padding(5).add(content);
            SceneChildMeasurer facts = children(node, 300, 300);
            ChildPlacements placed = control.arrange(node, new LayoutBox(70, 30, 100, 60), facts);
            require(placed != null && placed.entries().size() == 1, "直接arrange没有代码子盒");
            LayoutBox box = placed.entries().get(0).relativeBox;
            require(box.x == 5 && box.y == 5 && box.width == 300 && box.height == 40,
                    "普通排列未使用外盒相对padding与实际内容尺寸");
            SceneGeometryView geometry = new SceneGeometryView() {
                public Entry get(SceneNode target) {
                    return target == node ? new Entry(node, new LayoutBox(0, 0, 100, 60), null, null)
                            : target == content ? new Entry(content, box, null, null) : null;
                }
                public List<Entry> ordered() { return Arrays.asList(get(node), get(content)); }
                public LayoutBox viewport() { return new LayoutBox(0, 0, 100, 60); }
            };
            require(control.scroll(node, ScenePointerEvent.scroll(10, 10, -1), geometry), "超宽代码未滚动");
            require(control.arrange(node, new LayoutBox(0, 0, 100, 60), facts).entries().get(0).relativeBox.x < 5,
                    "同一容器未消费正式滚动事实");
            LayoutBox wide = control.arrange(node, new LayoutBox(0, 0, 400, 60), facts).entries().get(0).relativeBox;
            require(wide.x == 5 && wide.width == 390, "变宽后未按最终盒归零并填正文区");
            SceneNode empty = new SceneNode().control(new DocumentCodeViewport());
            require(empty.control().arrange(empty, new LayoutBox(0, 0, 0, 0), children(empty, 0, 0))
                    .entries().isEmpty(), "空内容仍依赖文字准备或旧子盒");
        });
        scenario("表格与单元格直接最终排列", () -> {
            ReactiveDomain domain = ReactiveDomain.direct();
            SceneHost host = new SceneHost() {
                public int width() { return 160; }
                public int height() { return 100; }
                public long metricsRevision() { return 1; }
                public void replay(PaintPlan plan) {}
            };
            SceneTextMeasurer text = (value, style, width) -> new TextLayout(0, 0, style, Collections.emptyList());
            SceneNode[] table = {null};
            ScenePageSession session = own(ScenePageSession.open(domain, host, text, context -> {
                table[0] = SceneDocumentTable.create(context,
                        domain.signal(MarkdownDocument.parse("| A | B |\n| --- | --- |\n| C | D |").blocks().get(0)),
                        domain.signal(SceneTheme.light()), null, ignored -> {});
                context.mount(table[0]);
            }));
            domain.execute(() -> {
                session.runtime().preparePresentation();
                SceneNode node = table[0];
                SceneChildMeasurer facts = children(node, 40, 120);
                ChildPlacements narrow = node.control().arrange(node, new LayoutBox(0, 0, 40, 90), facts);
                require(narrow != null && narrow.entries().size() == 4, "表格直接arrange没有完整单元格");
                require(narrow.entries().get(0).relativeBox.width == 40, "窄表格损失不可拆分最小内容");
                ChildPlacements wide = node.control().arrange(node, new LayoutBox(0, 0, 400, 90), facts);
                require(wide.entries().get(0).relativeBox.width > narrow.entries().get(0).relativeBox.width
                        && wide.entries().get(1).relativeBox.x + wide.entries().get(1).relativeBox.width == 400,
                        "表格最终外盒改变未重新分配列宽与余宽");
                SceneNode cell = node.children().get(0);
                ChildPlacements content = cell.control().arrange(cell,
                        new LayoutBox(30, 10, 90, 70), children(cell, 10, 100));
                require(content != null && content.entries().size() == 1, "单元格仍依赖文字投影");
                LayoutBox box = content.entries().get(0).relativeBox;
                require(box.x == cell.padding() && box.y == cell.padding()
                        && box.width == 90 - cell.padding() * 2, "单元格未扣一次外盒padding");
            });
        });
    }
}
