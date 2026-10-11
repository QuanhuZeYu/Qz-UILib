package club.heiqi.uilib.ui.scene.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.client.font.FontConfigurationFiles;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

/** 真实 MC 来源选择生产路径，在没有 Forge/MC 的类路径上读写真实迁移文件。 */
public final class FontConfigurationFilesScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new FontConfigurationFilesScenario().run(); }
    private List<FontSource> builtins;
    private final ConfigKey<Boolean> enabled = new ConfigKey<>("mc.replaceVanillaFont", ConfigCodecs.BOOLEAN, true);
    private ConfigKey<List<String>> candidates;

    @Override protected void runTests() throws Exception {
        builtins = FontTextService.defaultSources(getClass().getClassLoader(), 1);
        candidates = FontCandidateConfiguration.key(builtins);
        scenario("旧Properties优先迁移且保留显式空候选", () -> {
            Path old = fixture();
            Path properties = old.resolveSibling("font.properties");
            byte[] bytes = "mc.replaceVanillaFont=false\nfont.candidates.count=0\n".getBytes(StandardCharsets.UTF_8);
            Files.write(properties, bytes);
            Files.write(old, "故意损坏的低优先级旧Forge文件".getBytes(StandardCharsets.UTF_8));
            load(old, false, Collections.emptyList());
            require(Arrays.equals(bytes, Files.readAllBytes(properties)), "迁移不改Properties字节");
            Path yaml = old.resolveSibling("font.yaml");
            require(Files.exists(yaml), "保存新YAML来源");
            // YAML 已成为唯一权威，不再解析损坏的旧来源。
            Files.write(properties, "损坏Properties".getBytes(StandardCharsets.UTF_8));
            load(old, false, Collections.emptyList());
        });
        scenario("旧Forge一次迁移维持完整顺序并保留原文件", () -> {
            Path old = fixture();
            byte[] bytes = "font {\nB:replaceVanillaFont=false\nS:systemFontFiles <\n C:/fonts/中文.ttf\n >\n}\n".getBytes(StandardCharsets.UTF_8);
            Files.write(old, bytes);
            List<String> expected = FontCandidateConfiguration.migrate(Arrays.asList("C:/fonts/中文.ttf"), builtins);
            load(old, false, expected);
            require(Arrays.equals(bytes, Files.readAllBytes(old)), "迁移不改Forge文件");
        });
        scenario("新YAML错误不得向旧来源降级", () -> {
            Path old = fixture();
            Files.write(old.resolveSibling("font.yaml"), "font: [".getBytes(StandardCharsets.UTF_8));
            Files.write(old.resolveSibling("font.properties"), "font.candidates.count=0".getBytes(StandardCharsets.UTF_8));
            boolean failed = false;
            try { load(old, true, Collections.emptyList()); }
            catch (IllegalArgumentException | UncheckedIOException expected) { failed = true; }
            require(failed, "YAML权威来源错误必须上抛，不切换来源");
        });
        scenario("缺来源生成默认YAML且完整候选可往返", () -> {
            Path old = fixture();
            load(old, true, FontCandidateConfiguration.defaults(builtins));
            require(!Files.exists(old) && !Files.exists(old.resolveSibling("font.properties")), "默认生成不制造旧来源");
            load(old, true, FontCandidateConfiguration.defaults(builtins));
        });
    }

    private Path fixture() throws IOException {
        Path directory = Files.createTempDirectory("uilib-mc-yaml-");
        onClose(() -> {
            for (String name : Arrays.asList("font.yaml", "font.properties", "font.cfg")) Files.deleteIfExists(directory.resolve(name));
            Files.deleteIfExists(directory);
        });
        return directory.resolve("font.cfg");
    }

    private void load(Path old, boolean expectedEnabled, List<String> expectedCandidates) {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(() -> {
            ConfigSession config = new ConfigSession(domain, ConfigSchema.of(enabled, candidates));
            try {
                Path selected = FontConfigurationFiles.initialize(config, old, enabled, candidates, builtins);
                require(selected.equals(old.resolveSibling("font.yaml")), "正式来源使用.yaml");
                require(config.get(enabled) == expectedEnabled && config.get(candidates).equals(expectedCandidates),
                        "单来源有效值和候选顺序不变");
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
    }
}
