package club.heiqi.uilib.ui.scene.integration;

import java.io.InputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

/** 生产配置、实际内置字体目录及真实系统文件来源；无 MC 或 native 类路径。 */
public final class FontConfigurationScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new FontConfigurationScenario().run(); }

    @Override
    protected void runTests() throws Exception {
        List<FontSource> builtins = FontTextService.defaultSources(getClass().getClassLoader(), 1);
        scenario("完整列表排序排除与显式空候选", () -> {
            List<String> declarations = Arrays.asList(
                    "builtin:" + builtins.get(1).id, "builtin:" + builtins.get(0).id);
            List<FontSource> resolved = FontCandidateConfiguration.resolve(declarations, builtins,
                    (path, index) -> { throw new AssertionError("内置声明不读取文件"); });
            require(resolved.equals(Arrays.asList(builtins.get(1), builtins.get(0))),
                    "明确顺序，未声明内置来源不能被追加");
            require(FontCandidateConfiguration.resolve(Collections.emptyList(), builtins,
                    (path, index) -> { throw new AssertionError("空声明不读取文件"); }).isEmpty(),
                    "显式空表保持空，不附默认候选");
        });
        Path directory = Files.createTempDirectory("uilib-font-configuration-");
        Path file = directory.resolve("字体.otf");
        onClose(() -> { Files.deleteIfExists(file); Files.deleteIfExists(directory); });
        FontSource first = builtins.get(0);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(
                first.origin.substring("classpath:".length()))) {
            if (in == null) throw new IOException("实际内置字体资源缺失: " + first.origin);
            Files.copy(in, file);
        }
        scenario("实际文件与内置候选混排及一次迁移", () -> {
            List<String> declarations = Arrays.asList(
                    "builtin:" + builtins.get(1).id, "file:" + file, "builtin:" + first.id);
            List<FontSource> resolved = FontCandidateConfiguration.resolve(declarations, builtins,
                    (path, index) -> FontSource.file("system-" + index, 2,
                            java.nio.file.Paths.get(path), 0, false, Collections.emptySet()));
            require(resolved.get(0) == builtins.get(1) && resolved.get(2) == first,
                    "实际文件的位置不改变内置来源顺序");
            require(resolved.get(1).contentFingerprint().equals(first.contentFingerprint()),
                    "系统文件实际读取同一字体字节快照");
            List<String> migrated = FontCandidateConfiguration.migrate(Arrays.asList(file.toString()), builtins);
            List<String> expected = new ArrayList<>();
            expected.add("file:" + file);
            expected.addAll(FontCandidateConfiguration.defaults(builtins));
            require(migrated.equals(expected), "一次迁移保留旧前缀及当次默认完整尾链");
            require(FontCandidateConfiguration.files(declarations).equals(Arrays.asList(file.toString())),
                    "宿主来源目录仅收集已声明文件");
        });
        scenario("核心配置驱动字体候选并持久化与拒绝未知来源", () -> {
            ReactiveDomain domain = ReactiveDomain.direct();
            ConfigKey<List<String>> key = FontCandidateConfiguration.key(builtins);
            final ConfigSession[] config = new ConfigSession[1];
            final Owner[] observer = new Owner[1];
            List<List<String>> published = new ArrayList<>();
            domain.execute(() -> {
                config[0] = new ConfigSession(domain, ConfigSchema.of(key));
                observer[0] = domain.owner();
                domain.effect(observer[0], () -> published.add(config[0].get(key)));
            });
            onClose(() -> domain.execute(observer[0]::dispose));
            require(published.get(0).equals(FontCandidateConfiguration.defaults(builtins)),
                    "未配置采用字体模块当前默认列表");
            List<String> explicit = Arrays.asList("builtin:" + builtins.get(1).id, "file:" + file);
            StringWriter saved = new StringWriter();
            domain.execute(() -> {
                config[0].set(key, explicit);
                try { config[0].save(saved); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            require(published.get(published.size() - 1).equals(explicit), "变化自动通知消费者");
            domain.execute(() -> config[0].set(key, Collections.emptyList()));
            require(published.get(published.size() - 1).isEmpty(), "空列表经核心配置通知");
            domain.execute(() -> {
                try { config[0].load(new StringReader(saved.toString())); }
                catch (IOException e) { throw new UncheckedIOException(e); }
                require(config[0].get(key).equals(explicit), "文件往返保留完整候选声明");
                try {
                    config[0].set(key, Arrays.asList("builtin:missing-font"));
                    throw new AssertionError("未知来源必须拒绝");
                } catch (IllegalArgumentException expected) {
                    require(expected.getMessage().contains(FontCandidateConfiguration.KEY), "来源错误可定位键");
                }
                require(config[0].get(key).equals(explicit), "非法配置未发布新值");
            });
            int before = published.size();
            domain.execute(observer[0]::dispose);
            domain.execute(() -> config[0].set(key, Collections.emptyList()));
            require(published.size() == before, "订阅释放后不访问已结束消费者");
        });
    }
}
