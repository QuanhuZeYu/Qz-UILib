package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.localization.LocalizationBinding;
import club.heiqi.uilib.ui.scene.localization.LocalizationSnapshot;
import club.heiqi.uilib.ui.scene.localization.LocalizationSource;
import club.heiqi.uilib.ui.scene.localization.MutableLocalizationSource;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 正式页面/标准控件/输入/呈现管线的语言场景；显式提供独立资料并替换字体度量边界。
 * 反例针对同语言换表、写后读取和真正释放后的迟到通知，不镜像文案或布局实现。 */
public final class LocalizationScenario extends TestApplication {
    private static LocalizationSnapshot table(String language, String heading) {
        Map<String, String> values = new HashMap<>();
        values.put("heading", heading);
        values.put("action", "Add %s");
        values.put("row", "row %s / %s");
        return new LocalizationSnapshot(language, values, Locale.US);
    }

    static final class Host implements SceneHost, SceneTextMeasurer {
        final SoftwareSceneScenario.SoftwareHost software =
                new SoftwareSceneScenario.SoftwareHost();

        Host() {
            software.width = 240;
            software.height = 480;
        }

        @Override
        public int width() {
            return software.width;
        }

        @Override
        public int height() {
            return software.height;
        }

        @Override
        public long metricsRevision() {
            return software.metrics;
        }

        @Override
        public void replay(PaintPlan plan) {
            software.replay(plan);
        }

        @Override
        public TextLayout layout(String text, TextStyle style, int width) {
            software.glyph = Math.max(1, style.size / 2);
            software.lineHeight = style.size;
            TextLayout measured = software.layout(text, style, width);
            // 旧软件边界的简写Line无行指标，标准控件需要真实advance/baseline/lineHeight。
            List<TextLayout.Line> lines = new ArrayList<>();
            int start = 0;
            for (TextLayout.Line line : measured.lines()) {
                int end = start + line.text.length();
                lines.add(
                        new TextLayout.Line(
                                line.text,
                                line.x,
                                line.y,
                                start,
                                end,
                                start,
                                end,
                                line.text.length() * software.glyph,
                                line.y + style.size * .75f,
                                style.size * .75f,
                                style.size * .25f,
                                style.size,
                                java.util.Collections.emptyList(),
                                java.util.Collections.emptyList(),
                                java.util.Collections.emptyList()));
                start = end;
            }
            return new TextLayout(
                    measured.width,
                    measured.height,
                    measured.width,
                    measured.height,
                    style,
                    lines,
                    text,
                    text,
                    0,
                    java.util.Collections.emptyList());
        }
    }

    /** 资料提供者可能已取走回调，解除订阅后仍有迟到消息；只在测试环境保留该消息。 */
    private static final class DelayedSource implements LocalizationSource {
        final MutableLocalizationSource source =
                new MutableLocalizationSource(table("en_US", "Head %s"));
        final List<Consumer<LocalizationSnapshot>> captured = new ArrayList<>();
        int active;

        @Override
        public LocalizationSnapshot current() {
            return source.current();
        }

        @Override
        public Subscription subscribe(Consumer<LocalizationSnapshot> listener) {
            captured.add(listener);
            Subscription handle = source.subscribe(listener);
            active++;
            return () -> {
                active--;
                handle.close();
            };
        }

        void late(LocalizationSnapshot next) {
            for (Consumer<LocalizationSnapshot> listener : captured) listener.accept(next);
        }
    }

    @Override
    protected void runTests() throws Exception {
        scenario("隔离核心首次及键参数写后直接读，纯读取不执行Effect", this::immediateReads);
        scenario("Java位置参数百分号缺键格式错误与快照格式环境", this::formatResults);
        scenario("真实Effect重入发布两域最终一致，迟订阅及同实例新批次不回送旧事实", this::reentrantFacts);
        scenario("同页语言及同语言换表重排自然固定区，主题窗口输入共享几何", this::pageLayout);
        scenario("卸载重挂载读当前资料，关闭迟到消息不复活且独立绑定有效", this::lifecycle);
        scenario(
                "未使用语言的页面无需来源",
                () -> {
                    SoftwareSceneScenario.SoftwareHost host =
                            new SoftwareSceneScenario.SoftwareHost();
                    ScenePageSession page =
                            ScenePageSession.open(
                                    ReactiveDomain.direct(),
                                    host,
                                    host,
                                    ctx -> ctx.mount(new SceneNode()));
                    onClose(page::close);
                    page.frame();
                });
    }

    private void immediateReads() throws Exception {
        for (String type :
                new String[] {
                    "net.minecraft.client.Minecraft",
                    "org.lwjgl.opengl.Display",
                    "cpw.mods.fml.common.Loader"
                }) {
            try {
                Class.forName(type);
                throw new AssertionError("核心运行类路径含宿主 " + type);
            } catch (ClassNotFoundException expected) {
            }
        }
        ReactiveDomain domain = ReactiveDomain.direct();
        MutableLocalizationSource source =
                own(new MutableLocalizationSource(table("en_US", "Hello %s")));
        domain.execute(
                () -> {
                    Owner owner = domain.owner();
                    onClose(() -> domain.execute(owner::close));
                    LocalizationBinding binding = new LocalizationBinding(domain, owner, source);
                    Signal<String> key = domain.signal("heading");
                    Signal<Integer> count = domain.signal(1);
                    Computed<String> text = binding.text(key, count),
                            outer = domain.computed(owner, () -> "[" + text.get() + "]");
                    int[] effects = {0};
                    domain.effect(
                            owner,
                            () -> {
                                count.get();
                                effects[0]++;
                            });
                    require("[Hello 1]".equals(outer.get()) && effects[0] == 0, "首次纯读取依赖帧或Effect");
                    count.set(2);
                    require(
                            "[Hello 2]".equals(outer.get()) && effects[0] == 0,
                            "参数写后派生读取旧值或执行Effect");
                    key.set("action");
                    require("[Add 2]".equals(outer.get()), "响应键未跟随当前状态");
                    source.publish(table("zh_CN", "你好 %s"));
                    key.set("heading");
                    require(
                            "zh_CN".equals(binding.language().get())
                                    && "[你好 2]".equals(outer.get()),
                            "加载完成语言和资料不是同一事实");
                    source.publish(table("zh_CN", "同语言新表 %s"));
                    require("[同语言新表 2]".equals(outer.get()), "相同语言码阻断表换代");
                });
    }

    private void reentrantFacts() {
        MutableLocalizationSource source = own(new MutableLocalizationSource(table("A", "A %s")));
        ReactiveDomain first = ReactiveDomain.direct(), second = ReactiveDomain.direct();
        Computed<String>[] texts = new Computed[2];
        List<String> secondSeen = new ArrayList<>(), lateSeen = new ArrayList<>();
        first.execute(
                () -> {
                    Owner owner = first.owner();
                    onClose(() -> first.execute(owner::close));
                    texts[0] = new LocalizationBinding(first, owner, source).text("heading", 1);
                    first.effect(
                            owner,
                            () -> {
                                if (!"B 1".equals(texts[0].get())) return;
                                source.publish(table("C", "C %s"));
                                require(
                                        "C 1".equals(texts[0].get())
                                                && "C".equals(source.current().language()),
                                        "重入publish返回后第一域或current仍是旧事实");
                                second.execute(
                                        () ->
                                                require(
                                                        "C 1".equals(texts[1].get()),
                                                        "重入publish返回后第二域未读到最新事实"));
                                own(source.subscribe(next -> lateSeen.add(next.language())));
                                require(
                                        lateSeen.equals(java.util.Arrays.asList("C")),
                                        "重入中的迟订阅没有同步最新事实");
                            });
                });
        second.execute(
                () -> {
                    Owner owner = second.owner();
                    onClose(() -> second.execute(owner::close));
                    LocalizationBinding binding = new LocalizationBinding(second, owner, source);
                    texts[1] = binding.text("heading", 1);
                    second.effect(owner, () -> secondSeen.add(binding.language().get()));
                });
        source.publish(table("B", "B %s"));
        second.execute(() -> require("C 1".equals(texts[1].get()), "外层旧批次把第二域覆盖回B"));
        require(
                secondSeen.equals(java.util.Arrays.asList("A", "C"))
                        && lateSeen.equals(java.util.Arrays.asList("C")),
                "新事实之后重新递交旧事实 " + secondSeen);
        source.publish(table("D", "D %s"));
        require(
                secondSeen.equals(java.util.Arrays.asList("A", "C", "D"))
                        && lateSeen.equals(java.util.Arrays.asList("C", "D")),
                "重入后的普通发布或迟订阅生命周期失效");

        // 同一snapshot可代表又一次完成加载；用snapshot身份判断批次会把外层B重复送给后订阅。
        LocalizationSnapshot reused = table("B", "same %s");
        MutableLocalizationSource same = own(new MutableLocalizationSource(table("A", "A %s")));
        boolean[] entered = {false};
        List<String> sameSeen = new ArrayList<>();
        same.subscribe(
                next -> {
                    if (next == reused && !entered[0]) {
                        entered[0] = true;
                        same.publish(reused);
                        require(same.current() == reused, "同实例重入没有立即更新当前事实");
                    }
                });
        same.subscribe(next -> sameSeen.add(next.language()));
        same.publish(reused);
        require(sameSeen.equals(java.util.Arrays.asList("A", "B")), "同实例新批次未替代旧批次 " + sameSeen);

        // 旧批次已有错误不能因为后面的订阅发布新事实而被吞掉。
        MutableLocalizationSource failed = own(new MutableLocalizationSource(table("A", "A %s")));
        RuntimeException original = new IllegalStateException("旧批次原异常");
        List<String> failedSeen = new ArrayList<>();
        failed.subscribe(
                next -> {
                    if ("B".equals(next.language())) throw original;
                });
        failed.subscribe(
                next -> {
                    if ("B".equals(next.language())) failed.publish(table("C", "C %s"));
                });
        failed.subscribe(next -> failedSeen.add(next.language()));
        Throwable caught = null;
        try {
            failed.publish(table("B", "B %s"));
        } catch (Throwable failure) {
            caught = failure;
        }
        require(
                caught == original
                        && "C".equals(failed.current().language())
                        && failedSeen.equals(java.util.Arrays.asList("A", "C")),
                "旧批次被替代时吞错或回送旧事实");
        System.out.println(
                "REENTRANT_FACTS second="
                        + secondSeen
                        + " late="
                        + lateSeen
                        + " sameSnapshot="
                        + sameSeen
                        + " oldFailurePreserved="
                        + (caught == original));
    }

    private void formatResults() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Map<String, String> values = new HashMap<>();
        values.put("positions", "%2$s / %1$s / 100%%");
        values.put("bad", "%q");
        values.put("arity", "%2$s");
        values.put("number", "%.1f");
        values.put("null", "%s");
        LocalizationSnapshot snapshot = new LocalizationSnapshot("en_US", values, Locale.US);
        values.put("positions", "wrong");
        Locale previous = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.FRANCE);
            domain.execute(
                    () -> {
                        Owner owner = domain.owner();
                        onClose(() -> domain.execute(owner::close));
                        MutableLocalizationSource source =
                                own(new MutableLocalizationSource(snapshot));
                        LocalizationBinding binding =
                                new LocalizationBinding(domain, owner, source);
                        Object[] arguments = {"literal.key", "B"};
                        Computed<String> text = binding.text("positions", arguments);
                        arguments[0] = "mutated";
                        require(
                                "B / literal.key / 100%".equals(text.get()),
                                "位置参数/百分号/字面参数或声明数组复制错误");
                        require(
                                "1.5".equals(binding.text("number", 1.5).get()),
                                "快照读取了变化后的全局格式locale");
                        require(
                                "null".equals(binding.text("null", (Object) null).get()),
                                "null参数被非法拒绝");
                        require(
                                "missing%key".equals(binding.text("missing%key").get()),
                                "缺键没有直接显示键名");
                        require("Format error: %q".equals(binding.text("bad").get()), "非法格式不可见");
                        require(
                                "Format error: %2$s".equals(binding.text("arity", "A").get()),
                                "参数不匹配不可见");
                        try {
                            binding.text("");
                            throw new AssertionError("空键未拒绝");
                        } catch (IllegalArgumentException expected) {
                        }
                        try {
                            binding.text((String) null);
                            throw new AssertionError("null键未拒绝");
                        } catch (IllegalArgumentException expected) {
                        }
                        try {
                            binding.text(
                                    () -> "null",
                                    new club.heiqi.uilib.ui.reactive.ReadableSignal<?>[] {null});
                            throw new AssertionError("null参数来源未拒绝");
                        } catch (NullPointerException expected) {
                        }
                        try {
                            snapshot.translations().put("null", "wrong");
                            throw new AssertionError("资料表可变");
                        } catch (UnsupportedOperationException expected) {
                        }
                    });
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previous);
        }
    }

    private static SceneGeometryView.Entry entry(ScenePageSession page, SceneNode node) {
        return page.pipeline().current().geometry.get(node);
    }

    private void pageLayout() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MutableLocalizationSource source =
                own(new MutableLocalizationSource(table("en_US", "Head %s")));
        Host host = new Host();
        SceneNode[] title = {null}, button = {null}, body = {null};
        Signal<Integer>[] count = new Signal[1];
        Signal<SceneTheme>[] theme = new Signal[1];
        MountHandle[] mount = {null};
        int[] builds = {0};
        ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            builds[0]++;
                            require(
                                    ctx.localization(source) == ctx.localization(source),
                                    "同页重复创建语言订阅");
                            MutableLocalizationSource other =
                                    own(new MutableLocalizationSource(table("other", "Other")));
                            try {
                                ctx.localization(other);
                                throw new AssertionError("同页允许偷偷更换来源");
                            } catch (IllegalStateException expected) {
                            }
                            count[0] = domain.signal(1);
                            theme[0] = domain.signal(SceneTheme.dark());
                            title[0] =
                                    SceneLabel.create(
                                                    ctx,
                                                    ctx.localization(source)
                                                            .text(() -> "heading", count[0]),
                                                    theme[0])
                                            .size(Size.fill(1), Size.natural());
                            button[0] =
                                    SceneButton.create(
                                            ctx,
                                            ctx.localization(source).text(() -> "action", count[0]),
                                            theme[0],
                                            () -> count[0].set(count[0].get() + 1));
                            body[0] =
                                    new SceneNode()
                                            .size(Size.fill(1), Size.fill(1))
                                            .scrollable(true)
                                            .scrollStep(20);
                            for (int row = 1; row <= 40; row++) {
                                final int index = row;
                                body[0].add(
                                        SceneLabel.create(
                                                        ctx,
                                                        ctx.localization(source)
                                                                .text(
                                                                        () -> "row",
                                                                        () -> index,
                                                                        count[0]),
                                                        theme[0])
                                                .size(Size.fill(1), Size.natural()));
                            }
                            ctx.mount(
                                    new SceneNode()
                                            .size(Size.fill(1), Size.fill(1))
                                            .gap(3)
                                            .add(title[0])
                                            .add(button[0])
                                            .add(body[0]));
                            mount[0] = ctx.runtime().captureMount(button[0]);
                        });
        onClose(page::close);
        page.frame();
        int[] shortHeight = {0}, offset = {0};
        domain.execute(
                () -> {
                    shortHeight[0] = entry(page, title[0]).box.height;
                    LayoutBox box = entry(page, button[0]).box;
                    page.pointer(ScenePointerEvent.down(0, box.x + 1, box.y + 1));
                    page.pointer(ScenePointerEvent.up(0, box.x + 1, box.y + 1));
                });
        page.frame();
        domain.execute(
                () -> {
                    require(
                            count[0].get() == 2 && "Add 2".equals(button[0].text()),
                            "翻译按钮未走正式路由更新参数");
                    LayoutBox box = entry(page, body[0]).box;
                    page.pointer(ScenePointerEvent.scroll(box.x + 1, box.y + 1, -2));
                });
        page.frame();
        domain.execute(
                () -> {
                    offset[0] = entry(page, body[0]).scrollOffset;
                    require(
                            offset[0] > 0,
                            "正文没有实际滚动 maximum="
                                    + entry(page, body[0]).scrollMaximum
                                    + " body="
                                    + entry(page, body[0]).box.width
                                    + "x"
                                    + entry(page, body[0]).box.height
                                    + " rowHeight="
                                    + entry(page, body[0].children().get(0)).box.height);
                });
        source.publish(table("zh_CN", "切换语言后这个自然标题应换行，固定操作区不随正文滚动，已有按钮与页面身份继续保留，操作 %s"));
        page.frame();
        domain.execute(
                () -> {
                    require(entry(page, title[0]).box.height > shortHeight[0], "译文变化未重新测量自然标题");
                    checkRegions(page, host, title[0], button[0], body[0]);
                    require(
                            entry(page, body[0]).scrollOffset == offset[0]
                                    && mount[0].active()
                                    && builds[0] == 1,
                            "语言切换重建/丢滚动或挂载身份");
                });
        source.publish(table("zh_CN", "同语言换表 %s"));
        page.frame();
        domain.execute(
                () -> {
                    require(
                            "同语言换表 2".equals(title[0].text())
                                    && entry(page, title[0]).box.height == shortHeight[0],
                            "同语言新表保留旧文案或旧几何");
                    theme[0].set(
                            SceneTheme.light()
                                    .withTextStyle(
                                            new TextStyle(
                                                    "default",
                                                    26,
                                                    SceneTheme.light().textStyle.color)));
                });
        host.software.width = 140;
        page.frame();
        domain.execute(
                () -> {
                    require(title[0].style().size == 26 && mount[0].active(), "主题字号更新冻结或重建控件");
                    checkRegions(page, host, title[0], button[0], body[0]);
                    LayoutBox box = entry(page, button[0]).box;
                    require(
                            host.software.last.commands().stream()
                                    .anyMatch(
                                            command ->
                                                    command.kind == PaintCommand.Kind.RECT
                                                            && command.box.x == box.x
                                                            && command.box.y == box.y
                                                            && command.box.width == box.width),
                            "绘制未使用当前按钮命中几何");
                    page.pointer(ScenePointerEvent.syntheticActivation(box.x + 1, box.y + 1));
                });
        page.frame();
        domain.execute(
                () ->
                        require(
                                count[0].get() == 3 && "Add 3".equals(button[0].text()),
                                "重排后的当前命中未激活当前译文按钮"));
        int measures = host.software.measures;
        page.frame();
        require(host.software.measures == measures, "无变化帧逐文案轮询或重测");
    }

    private static void checkRegions(
            ScenePageSession page, Host host, SceneNode title, SceneNode button, SceneNode body) {
        LayoutBox heading = entry(page, title).box,
                action = entry(page, button).box,
                viewport = entry(page, body).box;
        require(
                action.y == heading.y + heading.height + 3
                        && viewport.y == action.y + action.height + 3
                        && viewport.height == host.height() - heading.height - action.height - 6,
                "自然固定区和剩余正文未使用同一布局");
    }

    private void lifecycle() {
        DelayedSource source = new DelayedSource();
        onClose(source.source::close);
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        ReactiveDomain domain = new ReactiveDomain(tasks::add);
        Host host = new Host();
        Owner[] independent = {null};
        Computed<String>[] independentText = new Computed[1];
        domain.execute(
                () -> {
                    independent[0] = domain.owner();
                    independentText[0] =
                            new LocalizationBinding(domain, independent[0], source)
                                    .text("heading", 7);
                });
        while (!tasks.isEmpty()) tasks.remove().run();
        onClose(
                () -> {
                    domain.execute(independent[0]::close);
                    while (!tasks.isEmpty()) tasks.remove().run();
                });
        SceneNode[] label = {null};
        MountHandle[] mount = {null};
        ScenePageSession page =
                ScenePageSession.open(
                        domain,
                        host,
                        host,
                        ctx -> {
                            label[0] =
                                    SceneLabel.create(
                                            ctx,
                                            ctx.localization(source).text("heading", 1),
                                            () -> SceneTheme.dark());
                            mount[0] = ctx.mount(label[0]);
                        });
        while (!tasks.isEmpty()) tasks.remove().run();
        page.frame();
        while (!tasks.isEmpty()) tasks.remove().run();
        require(source.active == 2, "页面或独立绑定重复订阅");
        domain.execute(() -> mount[0].detach());
        while (!tasks.isEmpty()) tasks.remove().run();
        source.source.publish(table("en_US", "Remount %s"));
        while (!tasks.isEmpty()) tasks.remove().run();
        domain.execute(
                () -> {
                    require(label[0].runtime() == null, "卸载仍有呈现归属");
                    mount[0] = page.runtime().attach(null, label[0]);
                });
        while (!tasks.isEmpty()) tasks.remove().run();
        page.frame();
        while (!tasks.isEmpty()) tasks.remove().run();
        domain.execute(
                () ->
                        require(
                                "Remount 1".equals(label[0].text()) && source.active == 2,
                                "重挂载未读当前文案或叠加来源订阅"));
        while (!tasks.isEmpty()) tasks.remove().run();
        page.close();
        while (!tasks.isEmpty()) tasks.remove().run();
        require(source.active == 1, "页面关闭未释放所属语言订阅");
        source.late(table("en_US", "Late %s"));
        while (!tasks.isEmpty()) tasks.remove().run();
        domain.execute(
                () ->
                        require(
                                "Late 7".equals(independentText[0].get())
                                        && label[0].runtime() == null
                                        && "Remount 1".equals(label[0].text()),
                                "迟到通知访问旧控件或关闭独立绑定"));
        while (!tasks.isEmpty()) tasks.remove().run();
        domain.execute(independent[0]::close);
        while (!tasks.isEmpty()) tasks.remove().run();
        source.late(table("zh_CN", "ignored %s"));
        require(tasks.isEmpty() && source.active == 0, "全部释放后迟到通知仍递交状态操作");
    }

    public static void main(String[] args) throws Exception {
        new LocalizationScenario().run(args);
    }
}
