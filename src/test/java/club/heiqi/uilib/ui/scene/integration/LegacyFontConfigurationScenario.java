package club.heiqi.uilib.ui.scene.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import club.heiqi.uilib.host.client.font.LegacyFontConfiguration;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

/** 用真实旧 UTF-8 文件验证只读迁移，运行类路径不需要 Forge、MC 或 native。 */
public final class LegacyFontConfigurationScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new LegacyFontConfigurationScenario().run(); }

    @Override
    protected void runTests() throws Exception {
        List<FontSource> builtins = FontTextService.defaultSources(getClass().getClassLoader(), 1);
        scenario("UTF8 BOM 路径原语义与核心配置迁移", () -> {
            String first = "C:/字体/中文 #=<>.otf";
            String second = "D:/fonts/second font.ttf";
            Path file = fixture("\uFEFF# Configuration file\r\n\r\nfont{\r\n"
                    + "    B:replaceVanillaFont=false\r\n    S:systemFontFiles <\r\n"
                    + "        " + first + "  \r\n\t  " + second + "\t\r\n    >\r\n}\r\n");
            byte[] before = Files.readAllBytes(file);
            LegacyFontConfiguration.Snapshot old = LegacyFontConfiguration.read(file);
            require(!old.enabled, "迁移原开关");
            require(old.paths.equals(Arrays.asList(first, second)), "Forge 路径 trim，内部特殊字符不变");
            try {
                old.paths.add("不能修改");
                throw new AssertionError("迁移快照应不可变");
            } catch (UnsupportedOperationException expected) { }
            List<String> expected = new ArrayList<>(Arrays.asList("file:" + first, "file:" + second));
            expected.addAll(FontCandidateConfiguration.defaults(builtins));
            ReactiveDomain domain = ReactiveDomain.direct();
            ConfigKey<Boolean> enabled = new ConfigKey<>("mc.replaceVanillaFont", ConfigCodecs.BOOLEAN, true);
            ConfigKey<List<String>> candidates = FontCandidateConfiguration.key(builtins);
            Path saved = file.resolveSibling("font.yaml");
            onClose(() -> Files.deleteIfExists(saved));
            domain.execute(() -> {
                ConfigSession config = new ConfigSession(domain, ConfigSchema.of(enabled, candidates));
                config.set(enabled, old.enabled);
                config.set(candidates, FontCandidateConfiguration.migrate(old.paths, builtins));
                require(!config.get(enabled) && config.get(candidates).equals(expected),
                        "核心发布开关、旧路径前缀和当次默认完整尾链");
                try {
                    config.save(saved);
                    ConfigSession reloaded = new ConfigSession(domain, ConfigSchema.of(enabled, candidates));
                    reloaded.load(saved);
                    require(!reloaded.get(enabled) && reloaded.get(candidates).equals(expected),
                            "新配置文件保存完整迁移结果");
                } catch (IOException error) { throw new UncheckedIOException(error); }
            });
            require(Arrays.equals(before, Files.readAllBytes(file)), "成功迁移不修改旧文件字节");
        });
        scenario("旧 BOM 编码只读兼容", () -> {
            for (String encoding : Arrays.asList("UTF-16LE", "UTF-16BE", "UTF-32BE")) {
                String text = "\uFEFFfont {\nB:replaceVanillaFont=false\nS:systemFontFiles <\n C:/字体/旧.ttf\n >\n}\n";
                Path file = fixture("");
                Files.write(file, text.getBytes(java.nio.charset.Charset.forName(encoding)));
                LegacyFontConfiguration.Snapshot old = readUnchanged(file);
                require(!old.enabled && old.paths.equals(Arrays.asList("C:/字体/旧.ttf")),
                        "旧编码合法声明不改变有效值: " + encoding);
            }
        });
        scenario("缺项和旧空列表采用原默认尾链", () -> {
            for (String text : Arrays.asList("", "# Configuration file\n", "font{\n}\n",
                    "font {\nS:systemFontFiles <\n >\n}\n")) {
                Path file = fixture(text);
                LegacyFontConfiguration.Snapshot old = readUnchanged(file);
                require(old.enabled && old.paths.isEmpty(), "旧开关默认 true，旧前缀默认空列表");
                require(FontCandidateConfiguration.migrate(old.paths, builtins)
                        .equals(FontCandidateConfiguration.defaults(builtins)), "空旧前缀接入当次默认尾链");
            }
            LegacyFontConfiguration.Snapshot quoted = readUnchanged(fixture(
                    "\"font\" {\nB:\"replaceVanillaFont\"=TrUe\nS:\"systemFontFiles\" <\n >\n}\n"));
            require(quoted.enabled && quoted.paths.isEmpty(), "支持同名 quoted 类别和键");
            LegacyFontConfiguration.Snapshot missing = readUnchanged(fixture(
                    "font {\nB:replaceVanillaFont=false\n}\n"));
            require(!missing.enabled && missing.paths.isEmpty(), "缺列表不覆盖显式开关");
        });
        scenario("损坏旧格式明确定位且保持原文件", () -> {
            reject("font {\nB:replaceVanillaFont=true\nB:replaceVanillaFont=false\n}\n",
                    3, "font.replaceVanillaFont");
            reject("font {\nS:systemFontFiles <\n >\nS:systemFontFiles <\n >\n}\n",
                    4, "font.systemFontFiles");
            reject("font {\nS:systemFontFiles < trailing\n}\n", 2, "font.systemFontFiles");
            reject("font {\nS:systemFontFiles <\nfont.ttf\n > trailing\n}\n", 4, "font.systemFontFiles");
            reject("font {\nS:systemFontFiles <\nfont.ttf\n", 4, "font.systemFontFiles");
            reject("font {\nS:systemFontFiles <\nfont.ttf\n}\n", 4, "font.systemFontFiles");
            reject("font {\nB:replaceVanillaFont=perhaps\n}\n", 2, "font.replaceVanillaFont");
            reject("font {\nB:replaceVanillaFont=true # extra\n}\n", 2, "font.replaceVanillaFont");
            reject("font {\nB:unknown=true\n}\n", 2, "font");
            reject("font {\n", 2, "font");
            reject("font {\n}\n}\n", 3, "font");
            reject("font { trailing\n}\n", 1, "font");
            reject("other {\n}\n", 1, "font");
        });
        scenario("读取失败上抛且不创建旧文件", () -> {
            Path existing = fixture("");
            Path missing = existing.resolveSibling("不存在.cfg");
            try {
                LegacyFontConfiguration.read(missing);
                throw new AssertionError("不存在文件必须报告 IOException");
            } catch (IOException expected) {
                require(!Files.exists(missing), "读取失败不能创建文件");
            }
        });
    }

    private Path fixture(String text) throws IOException {
        Path directory = Files.createTempDirectory("uilib-legacy-font-");
        onClose(() -> Files.deleteIfExists(directory));
        Path file = directory.resolve("font.cfg");
        onClose(() -> Files.deleteIfExists(file));
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private LegacyFontConfiguration.Snapshot readUnchanged(Path file) throws IOException {
        byte[] before = Files.readAllBytes(file);
        LegacyFontConfiguration.Snapshot result = LegacyFontConfiguration.read(file);
        require(Arrays.equals(before, Files.readAllBytes(file)), "读取不改旧文件字节");
        return result;
    }

    private void reject(String text, int line, String key) throws IOException {
        Path file = fixture(text);
        byte[] before = Files.readAllBytes(file);
        try {
            LegacyFontConfiguration.read(file);
            throw new AssertionError("损坏旧文件必须明确失败: " + text);
        } catch (IOException expected) {
            require(expected.getMessage().contains(file + ":" + line + " [" + key + "]"),
                    "错误定位文件、行号与键: " + expected.getMessage());
        }
        require(Arrays.equals(before, Files.readAllBytes(file)), "失败不改旧文件字节");
        try (Stream<Path> files = Files.list(file.getParent())) {
            require(files.count() == 1, "失败不重建文件或产生 errored 副本");
        }
    }
}
