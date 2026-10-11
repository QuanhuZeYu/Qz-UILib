package club.heiqi.uilib.ui.scene.integration;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;

/** 真实状态域与 UTF-8 文件配置场景；不需要 UI、MC 或 native。
 * 观察者同时读多键，反例覆盖半发布、键诊断、值不可变和订阅释放。 */
public final class ConfigScenario extends TestApplication {
    private static final ConfigKey<Boolean> ENABLED = new ConfigKey<>("enabled", ConfigCodecs.BOOLEAN, true);
    private static final ConfigKey<String> TITLE = new ConfigKey<>("title", ConfigCodecs.STRING, "默认标题", value -> !value.equals("拒绝"));
    private static final ConfigKey<List<String>> CANDIDATES = new ConfigKey<>(
            "font.candidates", ConfigCodecs.STRING_LIST, Arrays.asList("builtin:默认", "file:C:/字体.ttf"));
    private static final ConfigSchema SCHEMA = ConfigSchema.of(ENABLED, TITLE, CANDIDATES);

    @Override
    protected void runTests() throws Exception {
        scenario("默认空值与真实UTF8往返", this::filesAndValues);
        scenario("批次通知与释放", this::batchAndLifecycle);
        scenario("非法输入定位且不半发布", this::invalidInputs);
        scenario("观察者失败仍完成批次失效", this::observerFailure);
    }

    private static void operation(ReactiveDomain domain, CheckedRunnable action) {
        domain.execute(() -> {
            try { action.run(); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { throw failure; }
            catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    private static ConfigSession session(ReactiveDomain domain) {
        ConfigSession[] result = new ConfigSession[1];
        domain.execute(() -> result[0] = new ConfigSession(domain, SCHEMA));
        return result[0];
    }

    private void filesAndValues() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        ConfigSession config = session(domain);
        Path file = Files.createTempFile("uilib-config-", ".properties");
        try {
            operation(domain, () -> {
                require(config.get(ENABLED), "默认 boolean");
                require(config.get(TITLE).equals("默认标题"), "默认 string");
                require(config.get(CANDIDATES).equals(Arrays.asList("builtin:默认", "file:C:/字体.ttf")), "默认完整顺序");
                List<String> input = new ArrayList<>(Arrays.asList("file:C:/中文 字体.ttf", "builtin:后备", ""));
                config.set(CANDIDATES, input);
                input.clear();
                require(config.get(CANDIDATES).size() == 3, "set 必须复制外部列表");
                immutable(config.get(CANDIDATES));
                immutable(CANDIDATES.defaultValue());
                config.set(TITLE, "汉字与é\\路径\n第二行");
                config.set(ENABLED, false);
                config.save(file);
            });
            String disk = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            require(disk.contains("中文") && disk.contains("汉字"), "文件必须是实际 UTF-8 字符");
            ConfigSession restored = session(domain);
            operation(domain, () -> {
                restored.load(file);
                require(restored.get(TITLE).equals("汉字与é\\路径\n第二行"), "Unicode及转义往返");
                require(restored.get(CANDIDATES).equals(Arrays.asList("file:C:/中文 字体.ttf", "builtin:后备", "")), "顺序与空项往返");
                require(!restored.get(ENABLED), "boolean 往返");
                restored.load(new StringReader("title=\nfont.candidates.count=0\n"));
                require(restored.get(TITLE).isEmpty(), "显式空串不能回默认");
                require(restored.get(CANDIDATES).isEmpty(), "显式空列表不能回默认");
                require(restored.get(ENABLED), "缺失键使用默认");
                StringWriter writer = new StringWriter();
                restored.save(writer);
                config.load(new StringReader(writer.toString()));
                require(config.get(TITLE).isEmpty() && config.get(CANDIDATES).isEmpty(), "空值保存再加载");
                config.load(new StringReader(""));
                require(config.get(TITLE).equals("默认标题") && config.get(CANDIDATES).equals(CANDIDATES.defaultValue()), "缺失与显式空的区别");
            });
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void immutable(List<String> values) {
        try {
            values.add("绕过通知");
            throw new AssertionError("列表可被外部修改");
        } catch (UnsupportedOperationException expected) {
            // 外部修改必须被不可变值阻止。
        }
    }

    private void batchAndLifecycle() {
        ReactiveDomain domain = ReactiveDomain.direct();
        ConfigSession config = session(domain);
        List<String> observed = new ArrayList<>();
        List<String> synchronous = new ArrayList<>();
        Owner[] owner = new Owner[1];
        Observation[] observation = new Observation[1];
        domain.execute(() -> {
            owner[0] = domain.owner();
            domain.effect(owner[0], () -> observed.add(config.signal(ENABLED).get() + "/" + config.signal(TITLE).get()));
            Computed<String> title = domain.computed(owner[0], () -> config.get(TITLE));
            require(title.get().equals("默认标题"), "先缓存只依赖后一个声明键的派生");
            observation[0] = domain.observe(owner[0], () -> config.get(ENABLED), () -> {
                // ENABLED 先失效；回调必须在整个批次的依赖失效后读取 TITLE 派生。
                require(config.get(TITLE).equals(title.get()), "同步观察者读取另键派生必须与权威值同批次");
                synchronous.add(config.get(ENABLED) + "/" + title.get());
            });
            observation[0].refresh();
        });
        require(observed.equals(Collections.singletonList("true/默认标题")), "首次 effect 真实读值");
        operation(domain, () -> {
            config.load(new StringReader("enabled=false\ntitle=新标题\n"));
            require(!config.get(ENABLED) && config.get(TITLE).equals("新标题"), "加载后立即读取");
        });
        require(observed.equals(Arrays.asList("true/默认标题", "false/新标题")), "effect 不能看到混代或重复批次");
        require(synchronous.equals(Collections.singletonList("false/新标题")), "同步失效回调也必须看到完整快照");
        operation(domain, () -> {
            config.load(new StringReader("enabled=false\ntitle=新标题\n"));
            config.set(TITLE, "新标题");
            config.set(CANDIDATES, Collections.singletonList("builtin:另一个"));
        });
        require(observed.size() == 2, "相同值以及不依赖的键不能重复通知");
        domain.execute(() -> {
            owner[0].dispose();
            config.set(TITLE, "释放后");
            config.set(ENABLED, true);
            require(config.get(TITLE).equals("释放后"), "释放订阅不释放独立配置");
        });
        require(observed.size() == 2 && synchronous.size() == 1, "Owner.dispose 后真实效应停止");
    }

    private void invalidInputs() throws Exception {
        expectKey("title", () -> ConfigSchema.of(TITLE, new ConfigKey<>("title", ConfigCodecs.STRING, "重复")));
        expectKey("font.candidates", () -> ConfigSchema.of(CANDIDATES,
                new ConfigKey<>("font.candidates.count", ConfigCodecs.STRING, "冲突")));
        expectKey("font.candidates", () -> ConfigSchema.of(
                new ConfigKey<>("font.candidates.0", ConfigCodecs.STRING, "冲突"), CANDIDATES));
        ReactiveDomain domain = ReactiveDomain.direct();
        ConfigSession config = session(domain);
        List<String> effects = new ArrayList<>();
        Owner[] owner = new Owner[1];
        domain.execute(() -> {
            owner[0] = domain.owner();
            domain.effect(owner[0], () -> effects.add(config.get(ENABLED) + "/" + config.get(TITLE)));
        });
        operation(domain, () -> {
            expectKey("title", () -> config.load(new StringReader("enabled=false\ntitle=拒绝\n")));
            expectKey("enabled", () -> config.load(new StringReader("title=试图半发布\nenabled=maybe\n")));
            expectKey("unknown", () -> config.load(new StringReader("enabled=false\nunknown=1\n")));
            for (String list : Arrays.asList("font.candidates.0=a\n", "font.candidates.count=-1\n",
                    "font.candidates.count=2\nfont.candidates.0=a\n", "font.candidates.count=1\nfont.candidates.1=a\n",
                    "font.candidates.count=0\nfont.candidates.extra=a\n", "font.candidates.count=1\nfont.candidates.00=a\n")) {
                expectKey("font.candidates", () -> config.load(new StringReader("enabled=false\n" + list)));
            }
            expectKey("title", () -> config.set(TITLE, "拒绝"));
            expectKey("title", () -> config.get(new ConfigKey<>("title", ConfigCodecs.STRING, "外来声明")));
            Reader broken = new Reader() {
                public int read(char[] buffer, int offset, int length) throws IOException { throw new IOException("真实Reader故障"); }
                public void close() {}
            };
            try {
                config.load(broken);
                throw new AssertionError("IOException被吞掉");
            } catch (IOException expected) {
                require(expected.getMessage().equals("真实Reader故障"), "IO错误沿调用通道返回");
            }
            require(config.get(ENABLED) && config.get(TITLE).equals("默认标题"), "失败不能半发布");
            owner[0].dispose();
        });
        require(effects.equals(Collections.singletonList("true/默认标题")), "失败不能发送状态变化");
    }

    private void observerFailure() {
        ReactiveDomain domain = ReactiveDomain.direct();
        ConfigSession config = session(domain);
        Owner[] owner = new Owner[1];
        List<Computed<String>> computed = new ArrayList<>();
        RuntimeException first = new IllegalStateException("enabled观察者失败");
        RuntimeException second = new IllegalStateException("title观察者失败");
        domain.execute(() -> {
            owner[0] = domain.owner();
            Observation enabled = domain.observe(owner[0], () -> config.get(ENABLED), () -> { throw first; });
            enabled.refresh();
            Observation title = domain.observe(owner[0], () -> config.get(TITLE), () -> { throw second; });
            title.refresh();
            // 故意晚于会抛错的同键观察者注册，验证真正的依赖失效而非直接读值。
            computed.add(domain.computed(owner[0], () -> "派生:" + config.get(TITLE)));
            require(computed.get(0).get().equals("派生:默认标题"), "初始缓存值");
        });
        try {
            try {
                operation(domain, () -> config.load(new StringReader("enabled=false\ntitle=新批次\n")));
                throw new AssertionError("观察者异常被吞掉");
            } catch (RuntimeException actual) {
                require(actual == first, "保留首个观察者异常身份");
                require(Arrays.asList(actual.getSuppressed()).contains(second), "保留后续观察者异常");
            }
            domain.execute(() -> {
                require(!config.get(ENABLED) && config.get(TITLE).equals("新批次"), "异常发生前已发布完整值");
                require(computed.get(0).get().equals("派生:新批次"), "其他键及同键的派生不能永久停留旧值");
            });
        } finally {
            domain.execute(() -> owner[0].dispose());
        }
    }

    private static void expectKey(String key, CheckedRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError("应拒绝配置: " + key);
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains(key), "诊断未定位配置键: " + expected.getMessage());
        }
    }

    public static void main(String[] args) throws Exception { new ConfigScenario().run(args); }
}
