package club.heiqi.uilib.ui.scene.integration;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.localization.LocalizationBinding;
import club.heiqi.uilib.ui.scene.localization.LocalizationFlow;
import club.heiqi.uilib.ui.scene.localization.LocalizationResource;
import club.heiqi.uilib.ui.scene.localization.LocalizationSnapshot;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 正式独立资源闭环：真实 classpath 和临时目录，不替换加载器或 Formatter。
 * 输入包装仅观测关闭与注入可达 IO 失败；文案/布局/域递交仍使用生产路径。 */
public final class LocalizationResourceScenario extends TestApplication {
    @Override
    protected void runTests() throws Exception {
        scenario("真实classpath UTF8转义数值格式和目录顺序覆盖，仅加载当前语言", this::classpathAndOrder);
        scenario("真实目录修改同语言重载，同一页面自然布局及独立域自动更新", this::directoryPage);
        scenario("Flow重入及消费者失败保留新事实，关闭保留绑定快照并拒绝新操作", this::reentrantAndClosed);
        scenario("外部旧通知排队与本域新选择交错，排空后不倒退", this::queuedDelivery);
        scenario("缺文件非法转义空键及读取失败不发布半表，输入始终关闭", this::loadingFailures);
    }

    private Path directory() throws IOException {
        Path root = Files.createTempDirectory("uilib-localization-");
        onClose(
                () -> {
                    List<Path> paths = new ArrayList<>();
                    try (Stream<Path> contents = Files.walk(root)) {
                        contents.forEach(paths::add);
                    }
                    paths.sort(java.util.Comparator.reverseOrder());
                    for (Path path : paths) Files.delete(path);
                });
        return root;
    }

    private static void write(Path root, String language, String content) throws IOException {
        Files.write(
                root.resolve(language + ".properties"), content.getBytes(StandardCharsets.UTF_8));
    }

    private LocalizationResource classpath(String root) {
        return LocalizationResource.classpath(getClass().getClassLoader(), root);
    }

    private static void select(LocalizationFlow flow, String language) {
        try {
            flow.select(language);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void reload(LocalizationFlow flow) {
        try {
            flow.reload();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void drain(ArrayDeque<Runnable> tasks) {
        while (!tasks.isEmpty()) tasks.remove().run();
    }

    private LocalizationBinding bind(ReactiveDomain domain, LocalizationFlow flow) {
        Owner owner = domain.owner();
        onClose(() -> domain.execute(owner::close));
        return new LocalizationBinding(domain, owner, flow);
    }

    private void classpathAndOrder() throws Exception {
        for (String name :
                new String[] {
                    "net.minecraft.client.Minecraft",
                    "cpw.mods.fml.common.Loader",
                    "org.lwjgl.opengl.Display"
                }) {
            try {
                Class.forName(name);
                throw new AssertionError("独立运行类路径混入宿主 " + name);
            } catch (ClassNotFoundException expected) {
            }
        }
        LocalizationFlow pageResources =
                own(
                        new LocalizationFlow(
                                "zh_CN", Locale.ROOT, classpath("uilib/localization/core-loop")));
        require(
                "语言动态接入：第3次操作".equals(pageResources.current().format("qz_uilib.lang.title", 3)),
                "实际验收页中文资源缺失");
        pageResources.select("en_US");
        require(
                "Localization: operation 3"
                        .equals(pageResources.current().format("qz_uilib.lang.title", 3)),
                "实际验收页英文资源缺失");
        Path overlay = directory();
        write(overlay, "en_US", "shared=directory override\n");
        write(overlay, "zh_CN", "shared=目录覆盖\n");
        LocalizationResource[] resources = {
            classpath("uilib/localization/scenario"), LocalizationResource.directory(overlay)
        };
        LocalizationFlow flow = own(new LocalizationFlow("en_US", Locale.US, resources));
        resources[1] =
                language -> {
                    throw new IOException("调用者数组不应改变流");
                };
        flow.reload();
        LocalizationSnapshot initial = flow.current();
        require("独立中文 café".equals(initial.format("utf8")), "UTF8被当作ISO8859读取");
        require(
                "首行\n次行\t中\\末尾".equals(initial.format("escaped key"))
                        && "firstsecond".equals(initial.format("continued")),
                "Java转义或续行错误");
        require(
                "after".equals(initial.format("duplicate"))
                        && "directory override".equals(initial.format("shared")),
                "重复键或资源声明覆盖顺序错误");
        require("%2$d / %1$.2f / 100%%".equals(initial.translations().get("numeric")), "数值模板被归一");
        require("2 / 1.50 / 100%".equals(initial.format("numeric", 1.5, 2)), "真实Java数值格式错误");
        require(
                "Format error: %2$d / %1$.2f / 100%%"
                        .equals(initial.format("numeric", "not a number", "not an integer")),
                "真实数值类型错误未显式呈现");
        int[] notices = {0};
        own(flow.subscribe(next -> notices[0]++));
        flow.select("zh_CN");
        require(
                notices[0] == 2 && "目录覆盖".equals(flow.current().format("shared")),
                "语言选择没有完整发布有序资料");
        require("english.only".equals(flow.current().format("english.only")), "中文暗中补英文缺键");
        require("directory override".equals(initial.format("shared")), "旧快照跟随资料变动");
        System.out.println(
                "ACTUAL_PROPERTIES utf8="
                        + initial.format("utf8")
                        + " numeric="
                        + initial.format("numeric", 1.5, 2)
                        + " current="
                        + flow.current().language());
    }

    private void directoryPage() throws Exception {
        Path root = directory();
        write(root, "en_US", "heading=Short\naction=Select\n");
        write(
                root,
                "zh_CN",
                "heading=中文自然标题很长，切换后应自动换行，页面控件保持身份，正文使用同一布局事实，既有独立域也获得当前资料\naction=选择\n");
        LocalizationFlow flow =
                own(
                        new LocalizationFlow(
                                "en_US", Locale.ROOT, LocalizationResource.directory(root)));
        ReactiveDomain pageDomain = ReactiveDomain.direct(), business = ReactiveDomain.direct();
        Computed<String>[] independent = new Computed[1];
        business.execute(() -> independent[0] = bind(business, flow).text("heading"));
        LocalizationScenario.Host host = new LocalizationScenario.Host();
        SceneNode[] title = {null}, action = {null};
        int[] builds = {0};
        ScenePageSession page =
                ScenePageSession.open(
                        pageDomain,
                        host,
                        host,
                        ctx -> {
                            builds[0]++;
                            LocalizationBinding binding = ctx.localization(flow);
                            title[0] =
                                    SceneLabel.create(
                                                    ctx,
                                                    binding.text("heading"),
                                                    () -> SceneTheme.dark())
                                            .size(Size.fill(1), Size.natural());
                            action[0] =
                                    SceneButton.create(
                                            ctx,
                                            binding.text("action"),
                                            () -> SceneTheme.dark(),
                                            () -> select(flow, "zh_CN"));
                            ctx.mount(
                                    new SceneNode()
                                            .size(Size.fill(1), Size.fill(1))
                                            .gap(3)
                                            .add(title[0])
                                            .add(action[0]));
                        });
        onClose(page::close);
        page.frame();
        int[] shortHeight = {0};
        pageDomain.execute(
                () -> shortHeight[0] = page.pipeline().current().geometry.get(title[0]).box.height);
        flow.select("zh_CN");
        page.frame();
        pageDomain.execute(
                () -> {
                    LayoutBox heading = page.pipeline().current().geometry.get(title[0]).box,
                            button = page.pipeline().current().geometry.get(action[0]).box;
                    require(
                            heading.height > shortHeight[0]
                                    && button.y == heading.y + heading.height + 3
                                    && builds[0] == 1,
                            "真实资源切换未更新同页自然布局");
                });
        business.execute(() -> require(independent[0].get().startsWith("中文自然标题"), "共享流没有递交另一个独立域"));
        LocalizationSnapshot previous = flow.current();
        write(root, "zh_CN", "heading=目录重载\naction=重载后按钮\n");
        flow.reload();
        page.frame();
        require(
                previous != flow.current() && "zh_CN".equals(flow.current().language()),
                "同语言reload未发布新资料");
        pageDomain.execute(
                () ->
                        require(
                                "目录重载".equals(title[0].text())
                                        && "重载后按钮".equals(action[0].text())
                                        && page.pipeline()
                                                        .current()
                                                        .geometry
                                                        .get(title[0])
                                                        .box
                                                        .height
                                                == shortHeight[0]
                                        && builds[0] == 1,
                                "真实同语言换值未进入标准控件和几何"));
        business.execute(() -> require("目录重载".equals(independent[0].get()), "目录重载未更新业务域"));
        page.close();
        flow.reload();
        business.execute(() -> require("目录重载".equals(independent[0].get()), "页关闭连带关闭共享流"));
        System.out.println(
                "DIRECTORY_RELOAD pageBuilds="
                        + builds[0]
                        + " current="
                        + flow.current().format("heading"));
    }

    private void reentrantAndClosed() throws Exception {
        Path root = directory();
        for (String language : new String[] {"A", "B", "C", "D"})
            write(root, language, "heading=" + language + "\n");
        LocalizationFlow flow =
                own(new LocalizationFlow("A", Locale.ROOT, LocalizationResource.directory(root)));
        ReactiveDomain first = ReactiveDomain.direct(), second = ReactiveDomain.direct();
        Computed<String>[] texts = new Computed[2];
        List<String> secondSeen = new ArrayList<>();
        first.execute(
                () -> {
                    Owner owner = first.owner();
                    onClose(() -> first.execute(owner::close));
                    texts[0] = new LocalizationBinding(first, owner, flow).text("heading");
                    first.effect(
                            owner,
                            () -> {
                                if ("B".equals(texts[0].get())) {
                                    select(flow, "C");
                                    require(
                                            "C".equals(texts[0].get())
                                                    && "C".equals(flow.current().language()),
                                            "Flow重入选择返回后仍是旧事实");
                                }
                            });
                });
        second.execute(
                () -> {
                    Owner owner = second.owner();
                    onClose(() -> second.execute(owner::close));
                    texts[1] = new LocalizationBinding(second, owner, flow).text("heading");
                    second.effect(owner, () -> secondSeen.add(texts[1].get()));
                });
        flow.select("B");
        require(
                "C".equals(flow.current().language()) && secondSeen.equals(Arrays.asList("A", "C")),
                "外层select把语言倒退 " + secondSeen);
        LocalizationSnapshot beforeReload = flow.current();
        flow.reload();
        require(
                beforeReload != flow.current()
                        && "C".equals(flow.current().language())
                        && secondSeen.equals(Arrays.asList("A", "C", "C")),
                "reload没有读取重入后的当前语言并发布新事实 " + secondSeen);
        RuntimeException original = new IllegalStateException("消费者原异常"),
                another = new IllegalArgumentException("消费者次异常");
        own(
                flow.subscribe(
                        next -> {
                            if ("D".equals(next.language())) throw original;
                        }));
        own(
                flow.subscribe(
                        next -> {
                            if ("D".equals(next.language())) throw another;
                        }));
        int[] completed = {0};
        own(
                flow.subscribe(
                        next -> {
                            if ("D".equals(next.language())) completed[0]++;
                        }));
        Throwable caught = null;
        try {
            flow.select("D");
        } catch (RuntimeException failure) {
            caught = failure;
        }
        require(
                caught == original
                        && Arrays.asList(caught.getSuppressed()).contains(another)
                        && completed[0] == 1
                        && "D".equals(flow.current().language()),
                "消费者失败吞错截断或回滚新事实");
        LocalizationSnapshot retained = flow.current();
        flow.close();
        first.execute(() -> require("D".equals(texts[0].get()), "关闭流销毁已绑定快照"));
        require("D".equals(retained.format("heading")), "关闭流销毁不可变资料");
        for (CheckedRunnable operation :
                new CheckedRunnable[] {
                    () -> flow.current(),
                    () -> flow.subscribe(next -> {}),
                    () -> flow.select("A"),
                    flow::reload
                }) {
            try {
                operation.run();
                throw new AssertionError("关闭流接受新操作");
            } catch (IllegalStateException expected) {
            }
        }
    }

    private void queuedDelivery() throws Exception {
        Path root = directory();
        for (String language : new String[] {"A", "B", "C"})
            write(root, language, "heading=" + language + "\n");
        LocalizationFlow flow =
                own(new LocalizationFlow("A", Locale.ROOT, LocalizationResource.directory(root)));
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        ReactiveDomain domain = new ReactiveDomain(tasks::add);
        Owner[] owner = {null};
        LocalizationBinding[] binding = {null};
        domain.execute(
                () -> {
                    owner[0] = domain.owner();
                    binding[0] = new LocalizationBinding(domain, owner[0], flow);
                });
        drain(tasks);
        onClose(
                () -> {
                    domain.execute(owner[0]::close);
                    drain(tasks);
                });
        domain.execute(
                () -> {
                    // 域仍在本次业务操作中；外部线程只将 B 排队，不争抢同一域的状态访问。
                    AtomicReference<Throwable> failure = new AtomicReference<>();
                    Thread external =
                            new Thread(
                                    () -> {
                                        try {
                                            flow.select("B");
                                        } catch (Throwable error) {
                                            failure.set(error);
                                        }
                                    },
                                    "localization-external");
                    external.start();
                    try {
                        external.join();
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                    require(
                            failure.get() == null
                                    && "B".equals(flow.current().language())
                                    && "A".equals(binding[0].language().get()),
                            "没有命中外部通知排队条件");
                    select(flow, "C");
                    require("C".equals(binding[0].language().get()), "本域select不能即时读到新资料");
                });
        drain(tasks);
        domain.execute(
                () ->
                        require(
                                "C".equals(binding[0].language().get())
                                        && "C".equals(binding[0].text("heading").get()),
                                "排队B晚于即时C执行导致资料倒退"));
        drain(tasks);
        System.out.println("QUEUED_DELIVERY external=B immediate=C drained=C");
    }

    private void loadingFailures() throws Exception {
        Path root = directory();
        write(root, "en_US", "value=initial\n");
        LocalizationResource files = LocalizationResource.directory(root);
        int[] opened = {0}, closed = {0};
        boolean[] failRead = {false};
        IOException original = new IOException("实际输入读取失败");
        LocalizationResource tracked =
                language -> {
                    InputStream input = files.open(language);
                    opened[0]++;
                    return new FilterInputStream(input) {
                        @Override
                        public int read(byte[] bytes, int offset, int length) throws IOException {
                            if (failRead[0]) throw original;
                            return super.read(bytes, offset, length);
                        }

                        @Override
                        public void close() throws IOException {
                            closed[0]++;
                            super.close();
                        }
                    };
                };
        LocalizationFlow flow = own(new LocalizationFlow("en_US", Locale.ROOT, tracked));
        int[] notices = {0};
        own(flow.subscribe(next -> notices[0]++));
        LocalizationSnapshot initial = flow.current();
        try {
            flow.select("missing");
            throw new AssertionError("缺文件加载成功");
        } catch (IOException expected) {
            require(
                    expected.getMessage().contains("missing.properties")
                            && expected.getCause() instanceof java.nio.file.NoSuchFileException,
                    "缺文件定位或原始原因丢失");
        }
        write(root, "bad", "value=partial\nbroken=\\uZZZZ\n");
        try {
            flow.select("bad");
            throw new AssertionError("非法转义加载成功");
        } catch (IOException expected) {
            require(
                    expected.getMessage().contains("bad.properties")
                            && expected.getCause() instanceof IllegalArgumentException,
                    "非法转义原因不可见");
        }
        write(root, "empty", "=illegal\n");
        try {
            flow.select("empty");
            throw new AssertionError("空键加载成功");
        } catch (IOException expected) {
            require(expected.getCause() instanceof IllegalArgumentException, "空键合同失败无原始原因");
        }
        failRead[0] = true;
        try {
            flow.reload();
            throw new AssertionError("输入读取失败被忽略");
        } catch (IOException expected) {
            require(expected.getCause() == original, "IO原始原因身份丢失");
        }
        require(
                initial == flow.current() && notices[0] == 1 && opened[0] == closed[0],
                "失败发布半表或重新通知旧资料，输入未关闭");
        failRead[0] = false;
        Path absentOverlay = directory();
        try {
            new LocalizationFlow(
                    "en_US", Locale.ROOT, tracked, LocalizationResource.directory(absentOverlay));
            throw new AssertionError("构造未读完所有声明资源");
        } catch (IOException expected) {
            require(
                    expected.getMessage().contains("resource[1]")
                            && expected.getMessage().contains(absentOverlay.toString()),
                    "缺少后层资源定位");
        }
        try {
            new LocalizationFlow("missing", Locale.ROOT, classpath("uilib/localization/core-loop"));
            throw new AssertionError("缺classpath资源未失败");
        } catch (IOException expected) {
            require(
                    expected.getCause() instanceof java.io.FileNotFoundException
                            && expected.getCause()
                                    .getMessage()
                                    .contains("uilib/localization/core-loop/missing.properties"),
                    "缺classpath资源原定位丢失");
        }
        for (String invalid :
                new String[] {
                    "", ".", "..", "../en_US", "a/b", "a\\b", "C:drive", "nul\0segment"
                }) {
            try {
                flow.select(invalid);
                throw new AssertionError("非法语言标识未拒绝");
            } catch (IllegalArgumentException expected) {
            }
        }
        require(
                initial == flow.current() && notices[0] == 1 && opened[0] == closed[0],
                "非法选择或构造失败改变现有流/未关闭输入");
        System.out.println(
                "LOAD_FAILURES notices="
                        + notices[0]
                        + " streamsOpened="
                        + opened[0]
                        + " streamsClosed="
                        + closed[0]);
    }

    public static void main(String[] args) throws Exception {
        new LocalizationResourceScenario().run(args);
    }
}
