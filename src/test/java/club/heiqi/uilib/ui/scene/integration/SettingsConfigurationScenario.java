package club.heiqi.uilib.ui.scene.integration;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.settings.SettingsConfiguration;
import club.heiqi.uilib.ui.settings.SettingsModel;
import club.heiqi.uilib.ui.settings.SettingsModel.Source;

/** 真实配置文件与正式状态域：替代页面状态的手工验收，不冒充字体像素或 MC 实机。 */
public final class SettingsConfigurationScenario extends TestApplication {
    private List<FontSource> builtins;

    @Override protected void setup() throws IOException { builtins = FontTextService.defaultSources(getClass().getClassLoader(), 1); }

    private final class Fixture {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final ConfigKey<List<String>> fonts = FontCandidateConfiguration.key(builtins);
        final ConfigKey<Boolean> mc;
        final ConfigKey<Map<String, List<String>>> profile = new ConfigKey<>(
                "profile", ConfigCodecs.mapOf(ConfigCodecs.STRING_LIST), Collections.emptyMap());
        final Path file;
        ConfigSession session;
        SettingsConfiguration configuration;
        SettingsModel model;
        Owner owner;

        Fixture(boolean minecraft, boolean rejectFalse) throws Exception {
            this(minecraft, rejectFalse, () -> {});
        }
        Fixture(boolean minecraft, boolean rejectFalse, Runnable reloadResources) throws Exception {
            mc = minecraft ? new ConfigKey<>("mc.replaceVanillaFont", ConfigCodecs.BOOLEAN, true,
                    value -> !rejectFalse || value) : null;
            file = Files.createTempFile("uilib-settings-中文-", ".yaml");
            onClose(() -> Files.deleteIfExists(file));
            domain.execute(() -> {
                session = new ConfigSession(domain, minecraft ? ConfigSchema.of(fonts, mc, profile, ThemeConfiguration.KEY)
                        : ConfigSchema.of(fonts, profile, ThemeConfiguration.KEY));
                try {
                    session.load(new StringReader("# 用户字体说明\nfont:\n  candidates: [builtin:" + builtins.get(0).id
                            + "] # 用户候选说明\n" + (minecraft ? "mc:\n  replaceVanillaFont: true\n" : "")
                            + "# 用户其他模块说明\nprofile:\n  groups: [甲, 乙] # 用户组说明\n"));
                    session.save(file);
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
                configuration = new SettingsConfiguration(domain, session, fonts, mc, file,
                        FontCandidateConfiguration.defaults(builtins), values -> {
                            try {
                                FontCandidateConfiguration.resolve(values, builtins, (path, position) ->
                                        FontSource.file("settings-file-" + position, 1, java.nio.file.Paths.get(path),
                                                0, false, Collections.emptySet()));
                            } catch (IOException failure) { throw new UncheckedIOException(failure); }
                        }, reloadResources);
                owner = domain.owner();
                model = new SettingsModel(configuration, owner);
            });
            onClose(() -> domain.execute(owner::dispose));
        }
        void run(Runnable action) { domain.execute(action); }
        void apply() { run(model::apply); }
        void save() { run(model::save); }
        void reload() { run(model::reload); }
        String declaration(int index) { return "builtin:" + builtins.get(index).id; }
        String disk() throws IOException { return new String(Files.readAllBytes(file), StandardCharsets.UTF_8); }
    }

    @Override protected void runTests() throws Exception {
        scenario("主题草稿放弃应用与真实文件新会话往返", this::themeLifecycle);
        scenario("主题外部正式发布保留草稿与同步", this::themeExternal);
        scenario("完整草稿应用保存与注释及其他模块字段保留", this::applyAndSave);
        scenario("重复来源排序身份与空列表及语义改回", this::identityAndEmpty);
        scenario("外部正式发布与关闭作用域及重开保存基线", this::externalAndLifecycle);
        scenario("来源拒绝与多键全校验不半发布", this::validation);
        scenario("草稿期间真实字体文件失效仍保留编辑", this::sourceDisappears);
        scenario("真实文件错误保留路径草稿和保存基线", this::fileErrors);
        scenario("显式同值重载资源与失败后的文件事实", this::reloadResources);
        scenario("同步观察及延后资源Effect错误按已发布事实反馈", this::resourceErrors);
    }

    private void themeLifecycle() throws Exception {
        for (boolean minecraft : Arrays.asList(false, true)) {
            Fixture f = new Fixture(minecraft, false);
            String original = f.disk();
            f.run(() -> {
                require(f.configuration.defaultTheme() == ThemeMode.LIGHT && f.model.theme().get() == ThemeMode.LIGHT,
                        "缺少主题字段采用正式声明默认浅色");
                require(new SettingsConfiguration.Snapshot(Collections.emptyList(), false).theme() == ThemeMode.LIGHT,
                        "旧快照构造默认浅色");
                f.model.setTheme(ThemeMode.DARK);
                require(f.model.theme().get().isDark() && f.model.dirty().get() && !f.model.unsaved().get(),
                        "主题预览纳入草稿但不冒充已发布或未保存");
                require(f.session.get(ThemeConfiguration.KEY) == ThemeMode.LIGHT, "主题编辑不发布全局值");
                f.model.setTheme(ThemeMode.LIGHT);
                require(!f.model.dirty().get(), "主题改回基线恢复无变化");
                f.model.setTheme(ThemeMode.DARK);
                f.model.discard();
                require(f.model.theme().get() == ThemeMode.LIGHT && !f.model.dirty().get(), "放弃恢复当前主题");
                f.model.setTheme(ThemeMode.DARK);
            });
            f.save();
            require(f.disk().equals(original), "未应用主题不能保存");
            f.apply();
            f.run(() -> {
                require(f.configuration.current().theme() == ThemeMode.DARK && !f.model.dirty().get() && f.model.unsaved().get(),
                        "应用主题发布全局且准确标记未保存");
                f.model.setTheme(ThemeMode.LIGHT);
                f.model.discard();
                require(f.model.theme().get() == ThemeMode.DARK && f.model.unsaved().get(), "放弃不撤销已应用主题");
                f.configuration.apply(f.configuration.current().candidates(), f.configuration.current().replace());
                require(f.configuration.current().theme() == ThemeMode.DARK, "旧应用接口保留当前主题");
            });
            require(f.disk().equals(original), "应用主题不落盘");
            f.save();
            f.run(() -> {
                require(!f.model.unsaved().get() && f.configuration.saved().get().theme() == ThemeMode.DARK,
                        "保存主题更新工作区基线");
                ConfigSession reopened = new ConfigSession(f.domain, minecraft
                        ? ConfigSchema.of(f.fonts, f.mc, f.profile, ThemeConfiguration.KEY)
                        : ConfigSchema.of(f.fonts, f.profile, ThemeConfiguration.KEY));
                try { reopened.load(f.file); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
                require(reopened.get(ThemeConfiguration.KEY) == ThemeMode.DARK, "新配置会话从真实文件读取深色");
                f.model.setTheme(ThemeMode.LIGHT);
            });
            f.reload();
            f.run(() -> require(f.model.theme().get() == ThemeMode.DARK && !f.model.dirty().get(),
                    "显式加载同步主题草稿与文件值"));
        }
    }

    private void themeExternal() throws Exception {
        Fixture f = new Fixture(false, false);
        f.run(() -> f.session.set(ThemeConfiguration.KEY, ThemeMode.DARK));
        f.run(() -> {
            require(f.model.theme().get() == ThemeMode.DARK && !f.model.dirty().get(), "无草稿同步外部主题");
            f.model.add(f.declaration(1));
        });
        f.run(() -> f.session.set(ThemeConfiguration.KEY, ThemeMode.LIGHT));
        f.run(() -> {
            require(f.model.theme().get() == ThemeMode.DARK && f.model.sources().get().size() == 2,
                    "外部主题保留整个已修改草稿");
            require(f.model.externalChanged().get(), "主题参与外部变化提示");
            f.model.setTheme(ThemeMode.LIGHT);
            require(f.model.dirty().get() && f.model.externalChanged().get(), "外部变化后仍可编辑主题");
            f.model.discard();
            require(f.model.theme().get() == ThemeMode.LIGHT && !f.model.dirty().get() && !f.model.externalChanged().get(),
                    "放弃同步外部主题并清理提示");
            f.model.setTheme(ThemeMode.DARK);
        });
        f.run(() -> f.session.set(f.fonts, Collections.singletonList(f.declaration(2))));
        f.run(() -> {
            require(f.model.theme().get() == ThemeMode.DARK && f.model.sources().get().get(0).declaration().equals(f.declaration(0)),
                    "只有主题草稿时外部字体变化仍保留完整草稿");
            require(f.model.externalChanged().get(), "主题草稿可观察外部字体变化");
        });
        f.apply();
        f.run(() -> require(f.configuration.current().theme() == ThemeMode.DARK && !f.model.externalChanged().get()
                && !f.model.dirty().get(), "外部变化后可明确再次应用全部草稿"));
    }

    private void applyAndSave() throws Exception {
        Fixture f = new Fixture(true, false);
        String original = f.disk();
        List<String> notifications = new ArrayList<>();
        f.run(() -> {
            require(f.configuration.saved().get().candidates().equals(Collections.singletonList(f.declaration(0))), "saved取实际loaded值而非模块默认");
            f.domain.effect(f.owner, () -> notifications.add(f.session.get(f.fonts) + "/" + f.session.get(f.mc)
                    + "/" + f.session.get(ThemeConfiguration.KEY)));
            f.model.add(f.declaration(1));
            f.model.setReplace(false);
            f.model.setTheme(ThemeMode.DARK);
            require(f.model.dirty().get() && !f.model.unsaved().get(), "草稿不自动生效");
            require(f.model.saveDisabledReason().get().equals("先应用更改"), "未应用保存禁用原因");
        });
        f.save();
        require(f.disk().equals(original), "有草稿时保存不能落盘");
        f.apply();
        f.run(() -> {
            require(!f.model.dirty().get() && f.model.unsaved().get(), "应用后未保存");
            require(!f.model.externalChanged().get(), "自己的发布不报外部变化");
            require(!f.session.get(f.mc), "whole page包含MC字段");
        });
        require(f.disk().equals(original), "应用不写文件");
        require(notifications.size() == 2 && notifications.get(1).endsWith("/false/DARK"), "字体、MC与主题在同一批次通知");
        f.apply();
        require(notifications.size() == 2, "无变化应用不通知");
        f.save();
        f.run(() -> require(!f.model.unsaved().get(), "成功保存更新基线"));
        String saved = f.disk();
        require(saved.contains("用户候选说明") && saved.contains("用户其他模块说明") && saved.contains("用户组说明"), "页面保存保留用户注释");
        f.run(() -> {
            f.model.setReplace(true);
            f.model.add(f.declaration(2));
        });
        f.reload();
        f.run(() -> {
            require(!f.model.dirty().get() && !f.model.unsaved().get() && !f.model.replace().get(), "显式加载同步全部基线与草稿");
            require(f.session.get(f.profile).get("groups").equals(Arrays.asList("甲", "乙")), "保存仍保留其他模块复杂字段");
        });
    }

    private void identityAndEmpty() throws Exception {
        Fixture f = new Fixture(false, false);
        f.run(() -> {
            f.model.add(f.declaration(0));
            Source first = f.model.sources().get().get(0);
            Source duplicate = f.model.sources().get().get(1);
            require(first.identity() != duplicate.identity(), "合法重复来源有独立身份");
            f.model.move(duplicate, -1);
            require(f.model.sources().get().get(0) == duplicate && f.model.sources().get().get(1) == first, "移动保持实际对象身份");
            f.model.remove(first);
            require(!f.model.dirty().get(), "删回同值恢复无变化，身份不参与dirty");
            f.model.setReplace(true);
            require(!f.model.replace().get(), "非MC无可修改开关");
            f.model.remove(duplicate);
            require(f.model.dirty().get(), "空列表是可应用草稿");
            try { f.model.sources().get().clear(); throw new AssertionError("行列表可修改"); }
            catch (UnsupportedOperationException expected) { }
            try { f.configuration.current().candidates().clear(); throw new AssertionError("快照可修改"); }
            catch (UnsupportedOperationException expected) { }
        });
        f.apply();
        f.save();
        f.reload();
        f.run(() -> {
            require(f.configuration.current().candidates().isEmpty() && f.model.sources().get().isEmpty(), "空候选真实往返不补默认");
            f.model.resetFonts();
            require(f.model.dirty().get() && f.model.sources().get().size() == f.fonts.defaultValue().size(), "恢复默认只改草稿");
            require(f.configuration.current().candidates().isEmpty(), "恢复默认不发布");
            f.model.discard();
            require(!f.model.dirty().get() && f.model.sources().get().isEmpty(), "放弃恢复live空值");
        });
    }

    private void externalAndLifecycle() throws Exception {
        Fixture f = new Fixture(true, false);
        f.run(() -> f.session.set(f.fonts, Collections.singletonList(f.declaration(1))));
        f.run(() -> {
            require(f.model.sources().get().get(0).declaration().equals(f.declaration(1)) && !f.model.dirty().get(), "pristine自动同步外部live");
            f.model.add(f.declaration(2));
        });
        f.run(() -> f.session.set(f.mc, false));
        f.run(() -> {
            require(f.model.sources().get().size() == 2 && f.model.replace().get(), "外部变化保留整个dirty草稿");
            require(f.model.externalChanged().get(), "外部变化提示可读");
            f.model.discard();
            require(!f.model.replace().get() && !f.model.dirty().get() && !f.model.externalChanged().get(), "放弃同步当前生效值");
            f.model.add(f.declaration(2));
        });
        f.apply();
        f.run(() -> {
            require(f.model.unsaved().get(), "发布未保存");
            f.owner.dispose();
            f.owner = f.domain.owner();
            f.model = new SettingsModel(f.configuration, f.owner);
            require(f.model.unsaved().get() && !f.model.dirty().get(), "重开不将live冒充saved");
        });
        List<List<Source>> old = new ArrayList<>();
        f.run(() -> { old.add(f.model.sources().get()); f.owner.dispose(); });
        f.run(() -> f.session.set(f.fonts, Collections.emptyList()));
        f.run(() -> require(f.model.sources().get() == old.get(0), "释放后结束本页同步，不释放独立配置"));
    }

    private void validation() throws Exception {
        Fixture f = new Fixture(true, true);
        f.run(() -> {
            f.model.add("builtin:missing");
            require(!f.model.fontError().get().isEmpty() && !f.model.dirty().get(), "非法标识拒绝并定位字体区");
            f.model.add("file: ");
            require(!f.model.fontError().get().isEmpty(), "空路径拒绝");
            f.model.add("file:" + f.file.resolveSibling("不存在的字体.ttf"));
            require(f.model.fontError().get().contains("不存在的字体.ttf"), "真实来源读取失败保留具体路径与原因");
            f.model.add(f.declaration(1));
            f.model.setReplace(false);
            f.model.setTheme(ThemeMode.DARK);
        });
        f.apply();
        f.run(() -> {
            require(f.model.dirty().get() && f.model.sources().get().size() == 2 && f.model.theme().get() == ThemeMode.DARK,
                    "校验失败保留包括主题的完整草稿");
            require(f.session.get(f.fonts).equals(Collections.singletonList(f.declaration(0))) && f.session.get(f.mc)
                    && f.session.get(ThemeConfiguration.KEY) == ThemeMode.LIGHT, "MC字段拒绝时字体与主题也不发布");
            require(f.model.fontError().get().contains("mc.replaceVanillaFont"), "全字段诊断保留实际声明路径");
            Map<ConfigKey<?>, Object> updates = new LinkedHashMap<>();
            updates.put(f.fonts, Collections.emptyList());
            updates.put(new ConfigKey<>("foreign", ConfigCodecs.STRING, ""), "非法外键");
            try { f.session.setAll(updates); throw new AssertionError("接受外键"); }
            catch (IllegalArgumentException expected) { }
            require(f.session.get(f.fonts).equals(Collections.singletonList(f.declaration(0))), "未知键不部分发布");
            f.model.setTheme(ThemeMode.LIGHT);
            require(f.model.fontError().get().isEmpty() && f.model.dirty().get(), "应用错误不阻断继续编辑主题");
            f.model.setReplace(true);
            f.model.setTheme(ThemeMode.DARK);
        });
        f.apply();
        f.run(() -> require(f.configuration.current().theme() == ThemeMode.DARK && !f.model.dirty().get(),
                "修正拒绝字段后仍可应用主题"));
    }

    private void sourceDisappears() throws Exception {
        Fixture f = new Fixture(true, false);
        Path source = Files.createTempFile("uilib-settings-font-", ".otf");
        onClose(() -> Files.deleteIfExists(source));
        try (java.io.InputStream input = getClass().getClassLoader().getResourceAsStream(
                builtins.get(0).origin.substring("classpath:".length()))) {
            if (input == null) throw new IOException("实际内置字体资源缺失");
            Files.copy(input, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Source[] added = new Source[1];
        f.run(() -> {
            f.model.add("file:" + source);
            require(f.model.fontError().get().isEmpty(), "真实可读字体来源可加入草稿");
            added[0] = f.model.sources().get().get(1);
            f.model.setReplace(false);
        });
        Files.delete(source);
        f.apply();
        f.run(() -> {
            require(f.model.dirty().get() && f.model.sources().get().get(1) == added[0], "应用时来源失效保留草稿与行身份");
            require(f.model.fontError().get().contains(source.toString()), "来源错误保留实际字体路径");
            require(f.session.get(f.mc) && f.session.get(f.fonts).size() == 1, "来源失败不发布任何所属字段");
        });
    }

    private void fileErrors() throws Exception {
        Fixture f = new Fixture(true, false);
        f.run(() -> f.model.setReplace(false));
        f.apply();
        Files.delete(f.file);
        Files.createDirectory(f.file);
        f.save();
        f.run(() -> {
            require(f.model.unsaved().get() && f.model.fileError().get().contains(f.file.toString()), "真实写入失败有准确路径，仍未保存");
            require(f.model.message().get().isEmpty(), "保存失败不伪成功");
            f.model.add(f.declaration(1));
        });
        f.reload();
        f.run(() -> {
            require(f.model.dirty().get() && f.model.sources().get().size() == 2, "真实读取失败保留草稿");
            require(f.model.fileError().get().contains(f.file.toString()) && f.model.message().get().isEmpty(), "重载失败只在文件区呈现");
        });
    }

    private void reloadResources() throws Exception {
        int[] callbacks = {0};
        boolean[] fail = {false};
        Fixture f = new Fixture(true, false, () -> {
            callbacks[0]++;
            if (fail[0]) throw new IllegalStateException("显式资源重载失败");
        });
        String initial = f.disk();
        List<List<String>> notifications = new ArrayList<>();
        f.run(() -> f.domain.effect(f.owner, () -> notifications.add(f.session.get(f.fonts))));
        f.reload();
        require(callbacks[0] == 1 && notifications.size() == 1, "同值load无值通知但仍显式刷新资源");
        Files.write(f.file, "font: [非法字段结构]".getBytes(StandardCharsets.UTF_8));
        f.reload();
        require(callbacks[0] == 1, "文件load校验失败不执行资源回调");
        f.run(() -> require(f.configuration.saved().get().equals(f.configuration.current()), "load拒绝保持原保存事实"));
        Files.write(f.file, initial.replace("replaceVanillaFont: true", "replaceVanillaFont: false").getBytes(StandardCharsets.UTF_8));
        Source[] original = new Source[1];
        f.run(() -> { f.model.add(f.declaration(1)); original[0] = f.model.sources().get().get(1); });
        fail[0] = true;
        f.reload();
        require(callbacks[0] == 2, "成功load后执行资源回调");
        f.run(() -> {
            require(!f.configuration.current().replace() && !f.configuration.saved().get().replace(), "资源失败仍保留loaded与saved事实");
            require(!f.model.unsaved().get() && f.model.dirty().get(), "实际文件基线已更新，原草稿仍存在");
            require(f.model.sources().get().get(1) == original[0], "资源回调失败不丢草稿身份");
            require(f.model.fileError().get().contains("显式资源重载失败") && f.model.fileError().get().contains(f.file.toString()), "资源失败定位文件操作及原因");
            require(f.model.message().get().isEmpty(), "资源回调失败不显示重载成功");
        });
    }

    private void resourceErrors() throws Exception {
        for (boolean deferred : Arrays.asList(false, true)) {
            Fixture f = new Fixture(true, false);
            Owner[] resource = new Owner[1];
            RuntimeException expected = new IllegalStateException(deferred ? "延后资源失败" : "同步资源失败");
            Source[] added = new Source[1];
            f.run(() -> {
                resource[0] = f.domain.owner();
                if (deferred) f.domain.effect(resource[0], () -> {
                    if (f.session.get(f.fonts).size() > 1) throw expected;
                });
                else {
                    Observation observation = f.domain.observe(resource[0], () -> f.session.get(f.fonts), () -> { throw expected; });
                    observation.refresh();
                }
                f.model.add(f.declaration(1));
                added[0] = f.model.sources().get().get(1);
                f.model.setReplace(false);
                f.model.setTheme(ThemeMode.DARK);
            });
            onClose(() -> f.domain.execute(resource[0]::dispose));
            try { f.apply(); }
            catch (RuntimeException actual) { require(deferred && actual == expected, "延后异常保留原域通道与身份"); }
            f.run(() -> {
                require(f.session.get(f.fonts).size() == 2 && !f.session.get(f.mc)
                        && f.configuration.current().theme() == ThemeMode.DARK, "资源错误发生后字体、MC和主题已完整发布");
                require(f.model.sources().get().get(1) == added[0], "资源失败保留原草稿身份");
                require(!f.model.dirty().get() && f.model.unsaved().get(), "状态按已发布事实比较");
                require(f.model.fontError().get().contains("配置已发布") && f.model.fontError().get().contains(expected.getMessage()), "错误包含阶段与资源原因");
                require(!f.model.externalChanged().get() && f.model.message().get().isEmpty(), "自己的失败发布不报外部、不伪成功");
                resource[0].dispose();
                f.model.setTheme(ThemeMode.LIGHT);
                require(f.model.fontError().get().isEmpty() && f.model.dirty().get(), "资源异常不永久阻断修改主题");
            });
            f.apply();
            f.run(() -> require(f.configuration.current().theme() == ThemeMode.LIGHT && !f.model.dirty().get(),
                    "资源异常解除后可继续应用主题"));
        }
    }

    public static void main(String[] args) throws Exception { new SettingsConfigurationScenario().run(args); }
}
