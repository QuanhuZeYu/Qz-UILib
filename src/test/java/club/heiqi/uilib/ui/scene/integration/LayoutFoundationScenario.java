package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneGrid;
import club.heiqi.uilib.ui.scene.control.SceneDocument;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageState;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import club.heiqi.uilib.host.client.image.JavaImageLoader;
import club.heiqi.uilib.ui.scene.text.MarkdownDocument;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 正式页面的外部非文字扩展：真实 FT/HB、同批 PaintCommand 与注入事件业务结果。 */
public final class LayoutFoundationScenario extends TestApplication {
    private static final String LABEL =
            "Review the current layout and activate this wrapped button after the parent allocates its remaining space.";
    private FontTextService fonts;

    @Override
    protected void setup() throws Exception {
        fonts = own(new FontTextService(new LwjglFontEngine()));
    }

    @Override
    protected void runTests() throws Exception {
        scenario("非文字Frame在带权最终分配中重测真实按钮并保持绘制命中", this::allocatedFrame);
        scenario("普通排列不能突破fixed与min且沿既有定位诊断", this::invalidSizes);
        scenario("公共网格真实卡片换列内容策略和编辑身份连续", this::responsiveCards);
        scenario("公共网格自然槽宽行高硬尺寸与padding", this::naturalGrid);
    }

    private class Host implements SceneHost {
        int width = 520;
        PaintPlan paint;
        public int width() { return width; }
        public int height() { return 900; }
        public long metricsRevision() { return fonts.generation(); }
        public void replay(PaintPlan plan) { paint = plan; }
    }

    /** 仅使用正式扩展接口；没有文字呈现准备、宿主查询或第二几何。 */
    private static class Frame implements SceneControl {
        static final int BORDER = 2;
        final List<Integer> measureWidths = new ArrayList<>();

        public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }

        public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text,
                                    SceneChildMeasurer children) {
            measureWidths.add(body.availableWidth);
            // 引擎已扣 node.padding；此处只扣扩展自身的边框。
            MeasuredSize size = children.measureChild(children.flowChildren().get(0), new Constraints(
                    Math.max(0, body.availableWidth - BORDER * 2),
                    Math.max(0, body.availableHeight - BORDER * 2)));
            return new MeasuredSize(size.width + BORDER * 2, size.height + BORDER * 2);
        }

        public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
            int inset = node.padding() + BORDER;
            int width = Math.max(0, allocated.width - inset * 2);
            SceneNode child = children.flowChildren().get(0);
            MeasuredSize size = children.measureChild(child, new Constraints(
                    width, Math.max(0, allocated.height - inset * 2)));
            int childWidth = child.width().mode == Size.Mode.FILL
                    ? child.width().constrain(width) : size.width;
            return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(
                    child, new LayoutBox(inset, inset, childWidth, size.height))));
        }

        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            SceneGeometryView.Entry entry = geometry.get(node);
            target.add(PaintCommand.rect(entry.box, entry.clip, 0xff24638a));
        }
    }

    private void allocatedFrame() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        Frame extension = new Frame();
        SceneNode[] frame = new SceneNode[1], button = new SceneNode[1];
        Signal<String>[] label = new Signal[1];
        List<String> actions = new ArrayList<>();
        ScenePageSession page = ScenePageSession.open(domain, host, fonts, context -> {
            label[0] = domain.signal(LABEL);
            button[0] = SceneButton.create(context, label[0],
                    domain.signal(SceneTheme.light()), () -> actions.add(label[0].get()));
            frame[0] = new SceneNode().size(Size.fill(1), Size.natural()).padding(7)
                    .control(extension).add(button[0]);
            context.mount(new SceneNode().axis(SceneNode.Axis.ROW)
                    .size(Size.fill(1), Size.natural()).gap(20).add(frame[0])
                    .add(new SceneNode().size(Size.fill(3), Size.fixed(24))));
        });
        onClose(page::close);
        page.frame();
        int[] narrowHeight = {0}, narrowLines = {0};
        domain.execute(() -> {
            verifyFrame(page, host, frame[0], button[0]);
            SceneGeometryView.Entry outer = entry(page, frame[0]), child = entry(page, button[0]);
            require(extension.measureWidths.contains(host.width - frame[0].padding() * 2)
                            && outer.box.width < host.width,
                    "未覆盖先前宽约束与最终窄分配不同的路径");
            require(child.text.lines().size() > 1, "最终窄宽未造成真实换行");
            narrowHeight[0] = child.box.height;
            narrowLines[0] = child.text.lines().size();
            System.out.println("LAYOUT width=" + host.width + " measureBodies=" + extension.measureWidths
                    + " allocated=" + outer.box.width + " child=" + child.box.width
                    + " height=" + child.box.height + " lines=" + narrowLines[0]);
        });
        clickLastLine(page, domain, button[0]);
        require(actions.equals(Collections.singletonList(LABEL)), "可见末行点击未单次发布按钮业务结果");
        // 加宽与文案更新仍沿宿主事实和 Signal，控件身份与输入由同一页面保持。
        host.width = 820;
        page.frame();
        domain.execute(() -> {
            verifyFrame(page, host, frame[0], button[0]);
            SceneGeometryView.Entry child = entry(page, button[0]);
            require(child.box.height < narrowHeight[0] && child.text.lines().size() < narrowLines[0],
                    "加宽未按实际分配缩短真实换行高度");
            label[0].set("Apply the new layout");
        });
        page.frame();
        domain.execute(() -> verifyFrame(page, host, frame[0], button[0]));
        clickLastLine(page, domain, button[0]);
        require(actions.size() == 2 && actions.get(1).equals("Apply the new layout"),
                "动态文案后点击未消费当前业务值");
    }

    private static SceneGeometryView.Entry entry(ScenePageSession page, SceneNode node) {
        return page.pipeline().current().geometry.get(node);
    }

    private static void verifyFrame(ScenePageSession page, Host host, SceneNode frame, SceneNode button) {
        SceneGeometryView.Entry outer = entry(page, frame), child = entry(page, button);
        int expectedOuterWidth = (host.width - 20) / 4;
        int inset = frame.padding() + Frame.BORDER;
        require(outer.box.width == expectedOuterWidth
                        && child.box.x == outer.box.x + inset && child.box.y == outer.box.y + inset
                        && child.box.width == expectedOuterWidth - inset * 2
                        && outer.box.height == child.box.height + inset * 2,
                "边框、padding或带权分配没有在共同几何中只计算一次");
        float bottom = 0;
        for (TextLayout.Line line : child.text.lines()) bottom = Math.max(bottom, line.y + line.lineHeight);
        // 字形墨迹可能超出行框；验证全部内容与行框容纳，不把行高误当最终墨迹高度。
        require(child.box.height >= (int) Math.ceil(bottom) + button.padding() * 2
                        && child.text.lines().get(child.text.lines().size() - 1).logicalEnd == button.text().length(),
                "最终自然高度未容纳完整文字内容与行框");
        PaintCommand text = host.paint.commands().stream()
                .filter(c -> c.kind == PaintCommand.Kind.TEXT && c.text == child.text)
                .findFirst().orElseThrow(() -> new AssertionError("按钮没有绘制同批文本事实"));
        require(text.box.x == child.box.x + button.padding()
                        && text.box.y == child.box.y + button.padding()
                        && text.box.width == child.box.width - button.padding() * 2
                        && text.text.lines().stream().flatMap(line -> line.glyphs().stream())
                                .anyMatch(g -> g.source != null),
                "PaintCommand没有消费当前子盒与真实字体字形");
        require(text.text.lines().stream().flatMap(line -> line.glyphs().stream())
                        .filter(g -> g.inkBounds.known)
                        .allMatch(g -> g.y + g.inkBounds.bottom <= text.box.height
                                && text.box.y + g.y + g.inkBounds.bottom <= text.clip.y + text.clip.height),
                "最终正文区域或祖先裁剪截断了实际字形底部墨迹");
    }

    private static void clickLastLine(ScenePageSession page, ReactiveDomain domain, SceneNode button) {
        int[] point = new int[2];
        domain.execute(() -> {
            SceneGeometryView.Entry child = entry(page, button);
            TextLayout.Line last = child.text.lines().get(child.text.lines().size() - 1);
            point[0] = child.box.x + button.padding() + 1;
            point[1] = child.box.y + button.padding() + (int) last.y + 1;
        });
        page.pointer(ScenePointerEvent.down(0, point[0], point[1]));
        page.pointer(ScenePointerEvent.up(0, point[0], point[1]));
        page.frame();
    }

    private void invalidSizes() {
        invalidSize(Size.fixed(120).bounds(80, 120));
        invalidSize(Size.natural().bounds(80, 120));
    }

    private void invalidSize(Size declared) {
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        Frame illegal = new Frame() {
            @Override
            public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
                ChildPlacements.Entry legal = super.arrange(node, allocated, children).entries().get(0);
                return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(
                        legal.child, new LayoutBox(2, 2, 40, legal.relativeBox.height))));
            }
        };
        SceneNode child = new SceneNode().size(declared, Size.fixed(20)).text("硬边界");
        SceneNode parent = new SceneNode().size(Size.fixed(42), Size.natural()).control(illegal).add(child);
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, context -> context.mount(parent))) {
            try {
                page.frame();
                throw new AssertionError("普通容器压小fixed/min却发布了结果");
            } catch (IllegalArgumentException expected) {
                String message = expected.getMessage();
                require(message.contains("parent=" + parent.id())
                                && message.contains("control=" + illegal.getClass().getName())
                                && message.contains("child=" + child.id()) && message.contains("axis=width")
                                && message.contains("actual=40") && message.contains("declared=" + declared.mode),
                        "没有沿用既有非法尺寸定位诊断：" + message);
                System.out.println("LAYOUT expectedDiagnostic=" + message);
            }
        }
    }

    private void responsiveCards() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        ClassLoader loader = getClass().getClassLoader();
        try (SharedImageService images = new SharedImageService(new JavaImageLoader(id -> {
            int colon = id.indexOf(':');
            java.io.InputStream stream = loader.getResourceAsStream("assets/" + id.substring(0, colon)
                    + "/" + id.substring(colon + 1));
            if (stream == null) throw new java.io.IOException("样例图像缺失：" + id);
            return stream;
        }, JavaImageLoader.Budget.defaults()), SharedImageService.Budget.defaults())) {
            Host host = new Host() { public void prepareResources() { images.drain(); } };
            host.width = 600;
            SceneNode[] grid = {null}, field = {null}, image = {null};
            SceneEditState[] draft = {null};
            Signal<List<Integer>>[] items = new Signal[1];
            Signal<String>[] label = new Signal[1];
            Signal<SceneTheme>[] theme = new Signal[1];
            Signal<Integer>[] minimum = new Signal[1];
            Signal<Boolean>[] hidden = new Signal[1];
            int[] clicked = {-1};
            try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, context -> {
                items[0] = domain.signal(java.util.Arrays.asList(0, 1, 2, 3, 4));
                label[0] = domain.signal("Read this real wrapped card and activate the current result.");
                theme[0] = domain.signal(SceneTheme.light());
                minimum[0] = domain.signal(180);
                hidden[0] = domain.signal(false);
                // 策略由当前主题间距派生，Owner 沿页面释放，工厂不接管外部来源。
                grid[0] = SceneGrid.create(context, domain.computed(context.scope(), () ->
                        new SceneGrid.Policy(minimum[0].get(), theme[0].get().gap + 4, theme[0].get().gap + 4)));
                grid[0].bind(NodeBinding.repeat(items[0], key -> key, item -> {
                    int key = item.get();
                    SceneNode card = new SceneNode().size(Size.fill(1), Size.natural()).padding(6).gap(4);
                    card.add(SceneButton.create(context, label[0], theme[0], () -> clicked[0] = item.get()));
                    card.add(SceneDocument.create(context, () -> MarkdownDocument.parse(
                            "**真实文档** follows the final card width and wraps its content naturally."),
                            theme[0], null, link -> {}).size(Size.fill(1), Size.natural()));
                    if (key == 0) {
                        image[0] = SceneImage.create(images,
                                () -> ImageSource.resource("qz_uilib_test:textures/sample.png"),
                                () -> ImageOptions.defaults().size(Size.fixed(48), Size.fixed(32)));
                        card.add(image[0]);
                        draft[0] = new SceneEditState(domain, "草稿");
                        field[0] = SceneTextField.create(context, draft[0], theme[0], () -> false, request -> {});
                        card.add(field[0]);
                    }
                    if (key == 1) card.bind(NodeBinding.value(hidden[0], (node, value) -> node.visible(!value)));
                    return card;
                }));
                context.mountOwned(grid[0]);
            })) {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                for (;;) {
                    page.frame();
                    boolean[] ready = {false};
                    domain.execute(() -> ready[0] = SceneImage.state(image[0]).get().status == ImageState.Status.READY);
                    if (ready[0]) break;
                    require(System.nanoTime() < deadline, "真实样例图像未完成加载");
                    Thread.sleep(5);
                }
                assertColumns(domain, page, grid[0], 3, new int[] {192, 192, 192}, 12);
                require(host.paint.commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.IMAGE
                        && c.image.content != null), "公共卡片没有真实图像绘制载荷");
                SceneNode original = grid[0].children().get(0);
                java.util.UUID identity = original.id(), fieldIdentity = field[0].id();
                clickNode(domain, page, field[0]);
                page.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL, false, null));
                page.edit(SceneEditEvent.text("保留中的编辑草稿"));
                page.frame();
                domain.execute(() -> require("保留中的编辑草稿".equals(draft[0].snapshot().get().text),
                        "真实字段未接收编辑事件"));
                for (int width : new int[] {400, 160, 601}) {
                    host.width = width;
                    page.frame();
                    if (width == 400) assertColumns(domain, page, grid[0], 2, new int[] {194, 194}, 12);
                    else if (width == 160) assertColumns(domain, page, grid[0], 1, new int[] {180}, 12);
                    else assertColumns(domain, page, grid[0], 3, new int[] {193, 192, 192}, 12);
                    domain.execute(() -> require(page.runtime().focusRouter().focused().get() == field[0]
                                    && identity.equals(original.id()) && fieldIdentity.equals(field[0].id())
                                    && "保留中的编辑草稿".equals(draft[0].snapshot().get().text),
                            "换列丢失合法焦点、UUID或草稿"));
                }
                int oldHeight = height(domain, page, original);
                domain.execute(() -> {
                    label[0].set("The current content is longer and must be measured again at the final card width. "
                            + "This paragraph changes both the painted content and the natural row height.");
                    SceneTheme next = theme[0].get();
                    theme[0].set(new SceneTheme(next.textStyle.withEm(24), next.panel, next.button, next.hover,
                            next.pressed, next.focus, next.disabled, next.disabledText, next.tooltip,
                            next.tooltipText, next.padding, 12));
                });
                page.frame();
                require(height(domain, page, original) > oldHeight, "真实文案/字体变化未增加自然行高");
                domain.execute(() -> require(page.pipeline().current().geometry.get(grid[0].children().get(1)).box.x
                        - page.pipeline().current().geometry.get(original).box.x == 206,
                        "主题派生列间距未更新"));
                domain.execute(() -> {
                    SceneNode button = original.children().get(0);
                    SceneGeometryView.Entry current = page.pipeline().current().geometry.get(button);
                    require(host.paint.commands().stream().anyMatch(command -> command.kind == PaintCommand.Kind.TEXT
                                    && command.text == current.text
                                    && command.text.lines().stream().flatMap(line -> line.glyphs().stream())
                                            .anyMatch(glyph -> glyph.source != null))
                                    && current.text.lines().get(current.text.lines().size() - 1).logicalEnd == label[0].get().length(),
                            "文案/字体变化没有发布同批真实完整文字绘制");
                });
                clickNode(domain, page, original.children().get(0));
                domain.execute(() -> require(clicked[0] == 0, "新绘制位置未命中当前卡片业务"));
                clickNode(domain, page, field[0]);
                domain.execute(() -> items[0].set(java.util.Arrays.asList(2, 0, 4, 1, 3)));
                page.frame();
                domain.execute(() -> require(grid[0].children().get(1) == original && identity.equals(original.id())
                                && page.runtime().focusRouter().focused().get() == field[0]
                                && "保留中的编辑草稿".equals(draft[0].snapshot().get().text),
                        "稳定键重排重建了卡片或编辑状态"));
                domain.execute(() -> hidden[0].set(true));
                page.frame();
                domain.execute(() -> {
                    SceneGeometryView geometry = page.pipeline().current().geometry;
                    LayoutBox fourthVisible = geometry.get(grid[0].children().get(4)).box;
                    require(geometry.get(grid[0].children().get(3)) == null
                                    && fourthVisible.x == geometry.get(grid[0]).box.x
                                    && fourthVisible.y == geometry.get(original).box.y + geometry.get(original).box.height + 16,
                            "隐藏子项仍占列槽或几何");
                });
                domain.execute(() -> items[0].set(Collections.emptyList()));
                page.frame();
                domain.execute(() -> require(page.pipeline().current().geometry.get(grid[0]).box.height == 0,
                        "空网格留下内容行或gap"));
            }
        }
    }

    private static void assertColumns(ReactiveDomain domain, ScenePageSession page, SceneNode grid,
                                      int columns, int[] widths, int gap) {
        domain.execute(() -> {
            SceneGeometryView geometry = page.pipeline().current().geometry;
            int x = geometry.get(grid).box.x, top = geometry.get(grid.children().get(0)).box.y;
            int rowHeight = 0;
            for (int i = 0; i < columns; i++) {
                LayoutBox box = geometry.get(grid.children().get(i)).box;
                require(box.x == x && box.y == top && box.width == widths[i], "网格列与独立示例不符");
                x += widths[i] + gap;
                rowHeight = Math.max(rowHeight, box.height);
            }
            for (int i = columns; i < grid.children().size(); i++) {
                LayoutBox box = geometry.get(grid.children().get(i)).box;
                require(box.width == widths[i % columns], "最后一行重新伸展了列槽");
                if (i < columns * 2) require(box.y == top + rowHeight + gap, "行高未按真实卡片确定");
            }
            System.out.println("GRID body=" + geometry.get(grid).box.width + " columns=" + columns
                    + " widths=" + java.util.Arrays.toString(widths) + " rowHeight=" + rowHeight);
        });
    }

    private static int height(ReactiveDomain domain, ScenePageSession page, SceneNode node) {
        int[] value = {0};
        domain.execute(() -> value[0] = page.pipeline().current().geometry.get(node).box.height);
        return value[0];
    }

    private static void clickNode(ReactiveDomain domain, ScenePageSession page, SceneNode node) {
        LayoutBox[] box = {null};
        domain.execute(() -> box[0] = page.pipeline().current().geometry.get(node).box);
        page.pointer(ScenePointerEvent.down(0, box[0].x + 2, box[0].y + 2));
        page.pointer(ScenePointerEvent.up(0, box[0].x + 2, box[0].y + 2));
        page.frame();
    }

    private void naturalGrid() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Host host = new Host();
        host.width = 400;
        SceneNode[] grid = {null};
        Signal<Boolean>[] fixed = new Signal[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, fonts, context -> {
            fixed[0] = domain.signal(false);
            grid[0] = SceneGrid.create(context, () -> new SceneGrid.Policy(100, 12, 12)).padding(7)
                    .bind(NodeBinding.value(fixed[0], (node, value) -> node.size(
                            value ? Size.fixed(400) : Size.natural(), value ? Size.fixed(700) : Size.natural())));
            grid[0].add(SceneButton.create(context, () -> LABEL, () -> SceneTheme.light(), () -> {})
                    .size(Size.fill(1), Size.fill(1)));
            grid[0].add(new SceneNode().size(Size.fixed(260), Size.fixed(31)));
            grid[0].add(new SceneNode().size(Size.natural().bounds(130, 150), Size.fixed(20)));
            grid[0].add(new SceneNode().size(Size.fill(1).bounds(120, 150), Size.fill(1).bounds(45, 60)));
            context.mountOwned(grid[0]);
        })) {
            page.frame();
            domain.execute(() -> {
                SceneGeometryView geometry = page.pipeline().current().geometry;
                LayoutBox parent = geometry.get(grid[0]).box;
                LayoutBox first = geometry.get(grid[0].children().get(0)).box;
                LayoutBox hard = geometry.get(grid[0].children().get(1)).box;
                LayoutBox natural = geometry.get(grid[0].children().get(2)).box;
                LayoutBox last = geometry.get(grid[0].children().get(3)).box;
                require(parent.width == 338 && first.x == parent.x + 7 && first.width == 100
                        && hard.width == 260 && natural.width == 130 && last.width == 120,
                        "自然槽宽、padding或硬尺寸合同被突破");
                TextLayout actual = geometry.get(grid[0].children().get(0)).text;
                require(actual.lines().size() > 2 && first.height >= actual.height
                                && parent.height == first.height + 12 + 45 + 14
                                && last.y == first.y + first.height + 12 && last.height == 45,
                        "自然网格使用父满槽测矮了，或将父高灌入FILL行");
                System.out.println("GRID naturalWidth=" + parent.width + " firstWidth=" + first.width
                        + " firstHeight=" + first.height + " lastHeight=" + last.height);
                fixed[0].set(true);
            });
            page.frame();
            domain.execute(() -> {
                LayoutBox last = page.pipeline().current().geometry.get(grid[0].children().get(3)).box;
                require(last.height == 45 && last.y < 700 - 45, "固定父高度被自动分摊进网格行");
            });
        }
    }

    public static void main(String[] args) throws Exception {
        new LayoutFoundationScenario().run(args);
    }
}
