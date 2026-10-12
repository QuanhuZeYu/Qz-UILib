package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 三宿主共用的场景工作台；页面树按本次运行保留，旧专项页面不再装配。 */
public final class TestGallery implements ScenePage {
    public enum Page {
        HOME("场景概览", "从一次真实任务开始"),
        ARTICLE("知识库", "在指南、排产清单与交接记录之间切换阅读"),
        DETAILS("生产计划", "联动核对材料、产出、配方与计算过程"),
        SETTINGS("项目设置", "编辑项目资料并保存有效设置"),
        MESSAGES("活动中心", "浏览消息与任务详情"),
        REPORT("数据报告", "切换周期，核对分组明细与完成率"),
        TEXT("文本与排版", "对比内容模式、完整样式、来源与换行"),
        MEDIA("图像与公式", "切换来源、适配和公式，查看失败与恢复"),
        EDITING("编辑与提交", "验证草稿、历史、权限与迟到提交"),
        STRUCTURE("结构与输入", "增删移动条目，核对焦点、裁剪与手势"),
        RESOURCES("语言与配置", "加载和重载真实语言资料，编辑独立配置");
        public final String title, purpose;

        Page(String title, String purpose) {
            this.title = title;
            this.purpose = purpose;
        }

        String activities() {
            switch (this) {
                case TEXT:
                    return "全部文本样式 · Unicode/双向 · 来源诊断 · 宽度/断行";
                case MEDIA:
                    return "图像适配 · 加载失败/恢复 · 公式样式/错误 · 图像槽";
                case EDITING:
                    return "只读/禁用 · 选区/历史 · 异步接受/拒绝 · 草稿冲突";
                case STRUCTURE:
                    return "增删重排/移动 · 状态保持 · 模态/Tab · 失活取消";
                case RESOURCES:
                    return "语言选择/重载 · 缺键/格式错误 · 配置保存/重载/拒绝";
                case ARTICLE:
                    return "文档切换 · 元数据与嵌套列表 · 表格与长代码 · 图片更新";
                case DETAILS:
                    return "批次规划 · 材料图像 · 分式与伸缩公式 · 操作清单";
                case SETTINGS:
                    return "草稿编辑 · 多行备注 · 校验反馈 · 保存与恢复";
                case MESSAGES:
                    return "新增记录 · 展开详情 · 多语言段落 · 切页保留";
                case REPORT:
                    return "今日与本周 · 分组明细 · 汇总关系 · 完成率";
                default:
                    return "";
            }
        }
    }

    private final GalleryEnvironment environment;
    private final Page initial;

    public TestGallery(GalleryEnvironment environment) {
        this(environment, Page.HOME);
    }

    public TestGallery(GalleryEnvironment environment, Page initial) {
        this.environment = environment;
        this.initial = initial;
    }

    @Override
    public void build(ScenePageContext context) {
        Signal<Page> selected = context.domain().signal(initial);
        Signal<ThemeMode> preview = context.domain().signal(null);
        ReadableSignal<ThemeMode> global = context.themeMode();
        // 预览只在本次页面有效；正式配置变化恢复跟随，避免局部选择永久覆盖全局。
        context.domain().effect(context.scope(), () -> {
            global.get();
            preview.set(null);
        });
        ReadableSignal<Boolean> dark = context.domain().computed(context.scope(),
                () -> (preview.get() == null ? global.get() : preview.get()).isDark());
        ReadableSignal<SceneTheme> theme =
                context.domain().computed(context.scope(), () -> GalleryStyle.theme(dark.get()));
        Consumer<Page> navigate = selected::set;
        SceneNode shell = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(24).gap(20);
        shell.bind(
                NodeBinding.value(
                        dark,
                        (node, value) ->
                                node.background(
                                        value
                                                ? GalleryStyle.DARK_BACKGROUND
                                                : GalleryStyle.LIGHT_BACKGROUND)));
        SceneNode header = new SceneNode().size(Size.fill(1), Size.natural()).gap(6);
        header.add(GalleryStyle.label(context, () -> "QZ / 场景工作台", theme, 24, false));
        header.add(
                GalleryStyle.label(
                        context, () -> "一次声明 · 自动化 / Desktop / Minecraft", theme, 13, true));
        SceneNode actions =
                new SceneNode().size(Size.fill(1), Size.natural()).axis(SceneNode.Axis.ROW).gap(8);
        actions.add(
                GalleryStyle.button(context, () -> "场景概览", theme, () -> navigate.accept(Page.HOME))
                        .clickFocus(false));
        actions.add(
                GalleryStyle.button(
                        context,
                        () -> dark.get() ? "预览浅色" : "预览深色",
                        theme,
                        () -> preview.set(dark.get() ? ThemeMode.LIGHT : ThemeMode.DARK)));
        header.add(actions);
        shell.add(header);
        SceneNode deck = new SceneNode().size(Size.fill(1), Size.fill(1)).control(new Layers());
        for (Page id : Page.values()) {
            SceneNode page;
            if (id == Page.HOME) page = directory(context, theme, navigate);
            else if (id == Page.SETTINGS) {
                page = new SceneNode().size(Size.fill(1), Size.fill(1)).scrollable(true);
                page.add(GalleryStyle.frame(ProjectSettings.create(context, theme), theme));
            } else if (id == Page.TEXT) page = TextScenarios.create(context, theme);
            else if (id == Page.MEDIA) page = MediaScenarios.create(context, environment, theme);
            else if (id == Page.EDITING || id == Page.STRUCTURE)
                page = InteractionScenarios.create(context, id, theme);
            else if (id == Page.RESOURCES) page = ResourceScenarios.create(context, theme);
            else page = UsageScenarios.create(context, environment, id, theme);
            SceneNode layer =
                    new SceneNode().size(Size.fill(1), Size.fill(1)).control(new Layers());
            layer.bind(
                    NodeBinding.children(
                            () ->
                                    selected.get() == id
                                            ? Collections.singletonList(page)
                                            : Collections.emptyList()));
            // 页面逻辑树在失活期间保留；广场结束再释放编辑状态和全部派生。
            context.own(page::destroy);
            deck.add(layer);
        }
        shell.add(deck);
        context.own(shell::destroy);
        context.mount(shell);
    }

    private static SceneNode directory(
            ScenePageContext context, ReadableSignal<SceneTheme> theme, Consumer<Page> navigate) {
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).gap(16).scrollable(true);
        root.add(GalleryStyle.label(context, () -> "探索场景", theme, 28, false));
        root.add(
                GalleryStyle.label(
                        context, () -> "把文字、公式、图片与交互放回实际工作流。选择一个场景开始。", theme, 15, true));
        SceneNode grid = GalleryStyle.grid();
        for (Page id : Page.values())
            if (id != Page.HOME) {
                SceneNode card = GalleryStyle.card(theme);
                card.add(GalleryStyle.label(context, () -> id.title, theme, 20, false));
                card.add(GalleryStyle.label(context, () -> id.purpose, theme, 15, true));
                card.add(GalleryStyle.label(context, id::activities, theme, 13, true));
                card.add(
                        GalleryStyle.button(
                                        context,
                                        () -> "打开" + id.title,
                                        theme,
                                        () -> navigate.accept(id))
                                .clickFocus(false));
                grid.add(GalleryStyle.frame(card, theme));
            }
        root.add(grid);
        return root;
    }

    /** 多根页面复用正式控件扩展端口，模态仍按原根顺序叠放；几何与输入由scene唯一引擎维护。 */
    private static final class Layers implements SceneControl {
        @Override
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }

        @Override
        public MeasuredSize measure(
                SceneNode node,
                Constraints limits,
                SceneTextMeasurer text,
                SceneChildMeasurer children) {
            int width = 0, height = 0;
            for (SceneNode child : node.children())
                if (child.visible()) {
                    MeasuredSize size = children.measureChild(child, limits);
                    width = Math.max(width, size.width);
                    height = Math.max(height, size.height);
                }
            return new MeasuredSize(width, height);
        }

        private ChildPlacements prepared;

        // 正式引擎在arrange前调用presentText并提供本次最终约束，结果仅供紧邻排列消费。
        @Override
        public club.heiqi.uilib.ui.scene.text.TextLayout presentText(
                SceneNode node,
                club.heiqi.uilib.ui.scene.text.TextLayout full,
                int width,
                int height,
                SceneTextMeasurer text,
                SceneChildMeasurer children) {
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            for (SceneNode child : node.children())
                if (child.visible()) {
                    MeasuredSize measured =
                            children.measureChild(child, new Constraints(width, height));
                    placed.add(
                            new ChildPlacements.Entry(
                                    child,
                                    new LayoutBox(
                                            0,
                                            0,
                                            resolve(child.width(), width, measured.width),
                                            resolve(child.height(), height, measured.height))));
                }
            prepared = new ChildPlacements(placed);
            return full;
        }

        private static int resolve(Size size, int available, int natural) {
            return size.constrain(
                    size.mode == Size.Mode.FIXED
                            ? size.value
                            : size.mode == Size.Mode.FILL ? available : natural);
        }

        @Override
        public ChildPlacements arrange(SceneNode node, LayoutBox allocated) {
            ChildPlacements result = prepared;
            prepared = null;
            return result;
        }

        @Override
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
    }
}
