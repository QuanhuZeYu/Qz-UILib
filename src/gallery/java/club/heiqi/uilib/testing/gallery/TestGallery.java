package club.heiqi.uilib.testing.gallery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/** 一次声明由三宿主引用。每次build创建独立应用状态与页面树，没有进程静态草稿。 */
public final class TestGallery implements ScenePage {
    public enum Page {
        HOME("场景目录", "选择真实使用任务或专项定位样本", false),
        ARTICLE("文章阅读", "阅读含公式与插图的使用说明，切换语言、字号和阅读宽度", true),
        DETAILS("配方详情", "调整制作批次，查看材料数量、计算说明与图标", true),
        CONTROLS("设置与表单", "编辑、校验和提交数值，验证草稿、焦点与嵌套弹窗", true),
        MESSAGES("消息与活动", "接收消息、展开详情，观察多语言和Emoji的动态列表", true),
        REPORT("数据报告", "切换统计周期，核对数字、公式与说明同步变化", true),
        LANGUAGE("语言样本", "语言资源切换与重载", false),
        RICH("富文本样本", "属性、链接和复杂文字", false),
        IMAGE("图像样本", "资源图像、动态图像与显式联网操作", false),
        MATH("数学样本", "公式专项与错误定位", false),
        DOCUMENT("混排样本", "共同文档复杂内容边界", false);
        public final String title, purpose;
        public final boolean scenario;
        Page(String title, String purpose, boolean scenario) { this.title = title; this.purpose = purpose; this.scenario = scenario; }
    }
    private final GalleryEnvironment environment;
    private final Page initial;
    public TestGallery(GalleryEnvironment environment) { this(environment, Page.HOME); }
    public TestGallery(GalleryEnvironment environment, Page initial) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.initial = Objects.requireNonNull(initial, "initial");
    }
    @Override public void build(ScenePageContext context) {
        Signal<Page> selected = context.domain().signal(initial);
        java.util.function.Consumer<Page> navigate = selected::set;
        club.heiqi.uilib.ui.reactive.ReadableSignal<SceneTheme> theme = SceneTheme::dark;
        SceneNode shell = new SceneNode().size(Size.fill(1), Size.fill(1)).background(0xff161c24).gap(4);
        SceneNode navigation = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).gap(3);
        navigation.add(SceneButton.create(context, () -> "返回场景目录", theme, () -> navigate.accept(Page.HOME)).size(Size.natural(), Size.natural()).clickFocus(false));
        shell.add(SceneLabel.create(context, () -> "测试广场 · " + selected.get().title, theme));
        shell.add(navigation);
        List<SceneNode> layers = new ArrayList<>();
        // 每页在本次运行声明一次。解绑只解除呈现；广场结束再销毁保留树。
        for (Page id : Page.values()) {
            List<SceneNode> roots = new ArrayList<>();
            switch (id) {
                case HOME: roots.add(directory(context, theme, navigate)); break;
                case ARTICLE: case DETAILS: case MESSAGES: case REPORT:
                    roots.add(UsageScenarios.create(context, environment, id)); break;
                case CONTROLS:
                    roots.add(SceneLabel.create(context, () -> "操作：添加记录、编辑并确认整数、打开与关闭弹窗。预期：业务结果更新；切页保留草稿并隔离失活输入。", theme));
                    new CoreLoopPage(environment, navigate).buildPage(context, roots); break;
                case LANGUAGE: new CoreLocalizationPage(environment, navigate).buildPage(context, roots); break;
                case RICH: new CoreRichTextPage(environment, navigate).buildPage(context, roots); break;
                case MATH: new CoreMathPage(environment, navigate).buildPage(context, roots); break;
                case DOCUMENT: new CoreDocumentPage(environment, navigate).buildPage(context, roots); break;
                case IMAGE: roots.add(new CoreImagePage(environment, navigate).create(context, theme).size(Size.fill(1), Size.fill(1)).scrollable(true)); break;
            }
            for (SceneNode root : roots) context.own(root::destroy);
            SceneNode layer = new SceneNode().size(Size.fill(1), Size.fill(1)).control(new Layers());
            layer.bind(NodeBinding.children(() -> selected.get() == id ? roots : Collections.emptyList()));
            layers.add(layer);
        }
        SceneNode content = new SceneNode().size(Size.fill(1), Size.fill(1)).control(new Layers());
        for (SceneNode layer : layers) content.add(layer);
        shell.add(content);
        context.mountOwned(shell);
    }
    private static SceneNode directory(ScenePageContext context, club.heiqi.uilib.ui.reactive.ReadableSignal<SceneTheme> theme, java.util.function.Consumer<Page> navigate) {
        SceneNode directory = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(12).gap(8).scrollable(true);
        directory.add(SceneLabel.create(context, () -> "从真实任务开始 · 每个场景共用文字、公式、图像与正式输入", theme));
        for (boolean scenarios : new boolean[] {true, false}) {
            directory.add(SceneLabel.create(context, () -> scenarios ? "使用场景" : "专项定位样本", theme));
            for (Page id : Page.values()) if (id != Page.HOME && id.scenario == scenarios) {
                SceneNode card = new SceneNode().size(Size.fill(1), Size.natural()).padding(8).gap(4).background(0xff253141);
                card.add(SceneButton.create(context, () -> id.title, theme, () -> navigate.accept(id)).clickFocus(false));
                card.add(SceneLabel.create(context, () -> id.purpose, theme));
                directory.add(card);
            }
        }
        return directory;
    }
    /** 多根页面复用正式控件扩展端口，模态仍按原根顺序叠放；几何与输入由scene唯一引擎维护。 */
    private static final class Layers implements SceneControl {
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
            int width = 0, height = 0;
            for (SceneNode child : node.children()) if (child.visible()) {
                MeasuredSize size = children.measureChild(child, limits);
                width = Math.max(width, size.width); height = Math.max(height, size.height);
            }
            return new MeasuredSize(width, height);
        }
        private ChildPlacements prepared;
        // 正式引擎在arrange前调用presentText并提供本次最终约束，结果仅供紧邻排列消费。
        @Override public club.heiqi.uilib.ui.scene.text.TextLayout presentText(SceneNode node,
                club.heiqi.uilib.ui.scene.text.TextLayout full, int width, int height, SceneTextMeasurer text, SceneChildMeasurer children) {
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            for (SceneNode child : node.children()) if (child.visible()) {
                MeasuredSize measured = children.measureChild(child, new Constraints(width, height));
                placed.add(new ChildPlacements.Entry(child, new LayoutBox(0, 0,
                        resolve(child.width(), width, measured.width), resolve(child.height(), height, measured.height))));
            }
            prepared = new ChildPlacements(placed);
            return full;
        }
        private static int resolve(Size size, int available, int natural) {
            return size.constrain(size.mode == Size.Mode.FIXED ? size.value : size.mode == Size.Mode.FILL ? available : natural);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated) {
            ChildPlacements result = prepared; prepared = null;
            return result;
        }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
    }
}
