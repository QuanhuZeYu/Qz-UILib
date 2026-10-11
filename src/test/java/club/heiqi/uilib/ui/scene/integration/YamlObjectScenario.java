package club.heiqi.uilib.ui.scene.integration;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.config.ConfigCodec;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;

/** 独立产物的真实 YAML 对象场景。业务按自己的不可变类型读写，不接触 YAML 节点。 */
public final class YamlObjectScenario extends TestApplication {
    private static final class Range {
        final int low, high;
        Range(int low, int high) {
            if (low > high) throw new IllegalArgumentException("low 必须不大于 high");
            this.low = low; this.high = high;
        }
        public boolean equals(Object other) { return other instanceof Range && low == ((Range) other).low && high == ((Range) other).high; }
        public int hashCode() { return low * 31 + high; }
    }
    private static final class Profile {
        final String name;
        final Range range;
        final List<Range> bands;
        final Map<String, List<String>> groups;
        final String note;
        final double scale;
        Profile(String name, Range range, List<Range> bands, Map<String, List<String>> groups, String note, double scale) {
            this.name = name; this.range = range; this.bands = bands; this.groups = groups; this.note = note; this.scale = scale;
        }
        public boolean equals(Object other) {
            if (!(other instanceof Profile)) return false;
            Profile p = (Profile) other;
            return name.equals(p.name) && range.equals(p.range) && bands.equals(p.bands) && groups.equals(p.groups)
                    && java.util.Objects.equals(note, p.note) && Double.compare(scale, p.scale) == 0;
        }
        public int hashCode() { return java.util.Objects.hash(name, range, bands, groups, note, scale); }
    }
    private static final ConfigCodec<Range> RANGE = ConfigCodecs.<Range>object()
            .requiredField("low", ConfigCodecs.INTEGER, value -> value.low)
            .requiredField("high", ConfigCodecs.INTEGER, value -> value.high)
            .build(fields -> new Range((Integer) fields.get("low"), (Integer) fields.get("high")));
    @SuppressWarnings("unchecked")
    private static final ConfigCodec<Profile> PROFILE = ConfigCodecs.<Profile>object()
            .field("name", ConfigCodecs.STRING, "默认", value -> value.name, "业务名称说明")
            .field("range", RANGE, new Range(0, 10), value -> value.range)
            .field("bands", ConfigCodecs.listOf(RANGE), Collections.emptyList(), value -> value.bands)
            .field("groups", ConfigCodecs.mapOf(ConfigCodecs.STRING_LIST), Collections.emptyMap(), value -> value.groups)
            .field("note", ConfigCodecs.nullable(ConfigCodecs.STRING), null, value -> value.note)
            .field("scale", ConfigCodecs.DOUBLE, 1.0, value -> value.scale, value -> value > 0)
            .build(fields -> new Profile((String) fields.get("name"), (Range) fields.get("range"),
                    (List<Range>) fields.get("bands"), (Map<String, List<String>>) fields.get("groups"),
                    (String) fields.get("note"), (Double) fields.get("scale")));
    private static final ConfigKey<Profile> SETTINGS = new ConfigKey<>("module.settings", PROFILE,
            new Profile("默认", new Range(0, 10), Collections.emptyList(), Collections.emptyMap(), null, 1.0)).withComment("模块对象说明");
    private static final ConfigKey<List<Profile>> PRESETS = new ConfigKey<>("module.presets", ConfigCodecs.listOf(PROFILE), Collections.emptyList());
    private static final ConfigKey<String> OPTIONAL = new ConfigKey<>("optional", ConfigCodecs.nullable(ConfigCodecs.STRING), "缺失默认");
    private static final ConfigSchema SCHEMA = ConfigSchema.of(SETTINGS, PRESETS, OPTIONAL);
    private static final String YAML = "# 文件用户说明\nmodule:\n  # 对象用户说明\n  settings:\n"
            + "    # 名称前用户说明\n    name: 业务 # 名称尾用户说明\n"
            + "    range: {low: 2, high: 7} # 范围用户说明\n"
            + "    bands:\n      # 列表项用户说明\n      - {low: 1, high: 3} # 列表项尾说明\n"
            + "    groups:\n      first: [a, b] # 分组用户说明\n    note: null\n    scale: 1.25\n"
            + "  presets:\n    - name: 预设\n      range: {low: 0, high: 4}\n      bands: [{low: 2, high: 4}]\n"
            + "      groups: {empty: []}\n      note: ''\n      scale: 2\noptional: null\n";

    @Override protected void runTests() throws Exception {
        scenario("复杂对象实际文件往返与深冻结", this::objectsAndFiles);
        scenario("用户注释保留与只改注释不通知", this::commentsAndNotifications);
        scenario("完整错误路径位置与数值对象校验", this::invalidValues);
        scenario("旧Properties单次转换保留有效值", this::legacyMigration);
        scenario("共享alias保存隔离与声明真实路径", this::aliasesAndPaths);
    }
    private static void operation(ReactiveDomain domain, CheckedRunnable action) {
        domain.execute(() -> {
            try { action.run(); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { throw failure; }
            catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }
    private static ConfigSession session(ReactiveDomain domain, ConfigSchema schema) {
        ConfigSession[] result = new ConfigSession[1];
        domain.execute(() -> result[0] = new ConfigSession(domain, schema));
        return result[0];
    }
    private static String save(ConfigSession config) throws IOException { StringWriter writer = new StringWriter(); config.save(writer); return writer.toString(); }
    private void objectsAndFiles() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        ConfigSession config = session(domain, SCHEMA);
        Path file = Files.createTempFile("uilib-object-", ".yaml");
        try {
            operation(domain, () -> {
                config.load(new StringReader(YAML));
                Profile loaded = config.get(SETTINGS);
                require(loaded.range.low == 2 && loaded.range.high == 7 && loaded.bands.get(0).high == 3, "读取真实嵌套业务类型");
                require(loaded.note == null && config.get(OPTIONAL) == null, "显式 nullable null");
                require(config.get(PRESETS).get(0).note.isEmpty(), "空串与null区分");
                blocked(() -> loaded.bands.add(new Range(1, 2)));
                blocked(() -> loaded.groups.put("绕过", Collections.emptyList()));
                blocked(() -> loaded.groups.get("first").add("绕过"));
                List<Range> mutableBands = new ArrayList<>(loaded.bands);
                Map<String, List<String>> mutableGroups = new LinkedHashMap<>();
                List<String> names = new ArrayList<>(Arrays.asList("输入", "副本"));
                mutableGroups.put("new", names);
                Profile input = new Profile("修改", new Range(3, 9), mutableBands, mutableGroups, "", 2.5);
                config.set(SETTINGS, input);
                mutableBands.clear(); names.clear(); mutableGroups.clear();
                Profile frozen = config.get(SETTINGS);
                require(frozen != input && frozen.bands.size() == 1 && frozen.groups.get("new").size() == 2, "factory 必须用冻结children重建对象");
                blocked(() -> frozen.groups.get("new").clear());
                config.save(file);
                ConfigSession restored = new ConfigSession(domain, SCHEMA);
                restored.load(file);
                require(restored.get(SETTINGS).equals(frozen) && restored.get(PRESETS).equals(config.get(PRESETS)), "对象与对象列表文件往返");
                restored.load(new StringReader("module: {settings: {groups: {}, bands: []}}"));
                require(restored.get(SETTINGS).groups.isEmpty() && restored.get(SETTINGS).bands.isEmpty(), "空映射与空序列");
                require(restored.get(OPTIONAL).equals("缺失默认"), "缺字段使用声明默认");
                restored.set(OPTIONAL, null);
                restored.load(new StringReader(save(restored)));
                require(restored.get(OPTIONAL) == null, "nullable set和save往返");
            });
        } finally { Files.deleteIfExists(file); }
    }
    private void commentsAndNotifications() {
        ReactiveDomain domain = ReactiveDomain.direct();
        ConfigSession config = session(domain, SCHEMA);
        List<Profile> observed = new ArrayList<>();
        Owner[] owner = new Owner[1];
        domain.execute(() -> { owner[0] = domain.owner(); domain.effect(owner[0], () -> observed.add(config.get(SETTINGS))); });
        operation(domain, () -> {
            config.load(new StringReader("# 空文档用户说明\n"));
            String defaults = save(config);
            require(defaults.contains("空文档用户说明"), "只有注释的空文档使用默认且保留说明");
            require(defaults.contains("模块对象说明") && defaults.contains("业务名称说明"), "首次生成声明对象及字段说明");
            ConfigKey<List<Profile>> list = new ConfigKey<>("list", ConfigCodecs.listOf(PROFILE), Collections.singletonList(SETTINGS.defaultValue()));
            ConfigKey<Map<String, Profile>> map = new ConfigKey<>("map", ConfigCodecs.mapOf(PROFILE), Collections.singletonMap("one", SETTINGS.defaultValue()));
            ConfigKey<Profile> nullable = new ConfigKey<>("nullable", ConfigCodecs.nullable(PROFILE), SETTINGS.defaultValue());
            ConfigSession nested = new ConfigSession(domain, ConfigSchema.of(list, map, nullable));
            String nestedComments = save(nested);
            require(nestedComments.split("业务名称说明", -1).length - 1 == 3, "列表/map/nullable 内对象字段说明应随真实children展开");
            nested.load(new StringReader(nestedComments));
            config.load(new StringReader(YAML));
            int notifications = observed.size();
            config.load(new StringReader(YAML.replace("名称前用户说明", "修改的用户说明")));
            require(observed.size() == notifications, "仅改注释不得通知业务值");
            Profile p = config.get(SETTINGS);
            config.set(SETTINGS, new Profile("已改", new Range(3, 8), Collections.singletonList(new Range(2, 5)),
                    Collections.singletonMap("first", Arrays.asList("a", "c")), p.note, p.scale));
            String saved = save(config);
            for (String comment : Arrays.asList("文件用户说明", "对象用户说明", "修改的用户说明", "名称尾用户说明", "范围用户说明", "列表项用户说明", "列表项尾说明", "分组用户说明"))
                require(saved.contains(comment), "保存必须保留注释: " + comment);
            ConfigSession restored = new ConfigSession(domain, SCHEMA);
            restored.load(new StringReader(saved));
            require(restored.get(SETTINGS).equals(config.get(SETTINGS)), "修改值与注释共存的真实YAML");
            owner[0].dispose();
        });
    }
    private void invalidValues() {
        ReactiveDomain domain = ReactiveDomain.direct();
        ConfigSession config = session(domain, SCHEMA);
        operation(domain, () -> {
            for (String invalid : Arrays.asList("2147483648", "-2147483649", "1.5", "'2'", "null"))
                bad(config, "module.settings.range.low", "module:\n  settings:\n    range: {low: " + invalid + ", high: 9}\n");
            bad(config, "module.settings.range.high", "module: {settings: {range: {low: 1}}}");
            bad(config, "module.settings.range", "module: {settings: {range: {low: 9, high: 1}}}");
            bad(config, "module.settings.bands[0].high", "module: {settings: {bands: [{low: 1, high: nope}]}}");
            bad(config, "module.settings.groups.a[1]", "module: {settings: {groups: {a: [ok, 3]}}}");
            bad(config, "module.settings.scale", "module: {settings: {scale: 0}}");
            bad(config, "module.settings.scale", "module: {settings: {scale: .inf}}");
            bad(config, "module.settings.scale", "module: {settings: {scale: 1.0e999}}");
            bad(config, "module.settings.scale", "module: {settings: {scale: 1.0e-999}}");
            bad(config, "module.settings.extra", "module: {settings: {extra: true}}");
            bad(config, "module.settings.name", "module: {settings: {name: a, name: b}}");
            bad(config, "module.settings.name", "module: {settings: {name: !!java/object:java.lang.String {}}}");
            bad(config, "module.settings", "module: {settings: null}");
            bad(config, "$", "module: [\n");
            bad(config, "$", "[]");
            require(config.get(SETTINGS).equals(SETTINGS.defaultValue()), "全量校验失败不部分发布");
            config.load(new StringReader("module: {settings: {range: {low: -2147483648, high: 2147483647}, scale: 2}}"));
            require(config.get(SETTINGS).range.low == Integer.MIN_VALUE && config.get(SETTINGS).range.high == Integer.MAX_VALUE, "Integer边界数值可读取");
        });
    }
    private static void bad(ConfigSession config, String path, String yaml) throws IOException {
        try { config.load(new StringReader(yaml), "broken.yaml"); throw new AssertionError("应拒绝: " + yaml); }
        catch (IllegalArgumentException expected) {
            String diagnostic = expected.getMessage();
            require(diagnostic.contains(path) && diagnostic.matches("(?s).*broken\\.yaml:[0-9]+:[0-9]+.*"), "错误需完整路径与来源行列: " + diagnostic);
        }
    }
    private void legacyMigration() throws Exception {
        ConfigKey<Boolean> enabled = new ConfigKey<>("mc.enabled", ConfigCodecs.BOOLEAN, true);
        ConfigKey<String> title = new ConfigKey<>("title", ConfigCodecs.STRING, "默认");
        ConfigKey<List<String>> candidates = new ConfigKey<>("font.candidates", ConfigCodecs.STRING_LIST, Arrays.asList("默认"));
        ConfigSchema schema = ConfigSchema.of(enabled, title, candidates);
        ReactiveDomain domain = ReactiveDomain.direct();
        ConfigSession config = session(domain, schema);
        Path legacy = Files.createTempFile("uilib-legacy-", ".properties");
        Path target = Files.createTempFile("uilib-migrated-", ".yaml");
        String properties = "mc.enabled=false\ntitle=\nfont.candidates.count=3\nfont.candidates.0=file:C:/字体.ttf\nfont.candidates.1=builtin:后备\nfont.candidates.2=\n";
        try {
            Files.write(legacy, properties.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            operation(domain, () -> {
                config.loadProperties(legacy);
                require(!config.get(enabled) && config.get(title).isEmpty(), "旧有效bool与显式空串");
                require(config.get(candidates).equals(Arrays.asList("file:C:/字体.ttf", "builtin:后备", "")), "旧完整顺序与空项");
                config.save(target);
                String yaml = new String(Files.readAllBytes(target), java.nio.charset.StandardCharsets.UTF_8);
                require(!yaml.contains(".count=") && yaml.contains("字体"), "迁移保存为真正YAML对象序列");
                require(new String(Files.readAllBytes(legacy), java.nio.charset.StandardCharsets.UTF_8).equals(properties), "原Properties保留");
                ConfigSession restored = new ConfigSession(domain, schema);
                restored.load(target);
                require(restored.get(candidates).equals(config.get(candidates)), "转换后的YAML为正式读取来源");
                restored.loadProperties(new StringReader("font.candidates.count=0\n"));
                require(restored.get(candidates).isEmpty(), "旧显式空列表");
                for (String invalid : Arrays.asList("font.candidates.0=a\n", "font.candidates.count=-1\n", "font.candidates.count=2\nfont.candidates.0=a\n", "font.candidates.count=1\nfont.candidates.00=a\n", "mc.enabled=TRUE\n", "unknown=1\n")) {
                    try { restored.loadProperties(new StringReader(invalid)); throw new AssertionError("非法旧配置应拒绝"); }
                    catch (IllegalArgumentException expected) { require(expected.getMessage().contains("Properties"), "旧输入诊断来源"); }
                }
            });
        } finally { Files.deleteIfExists(legacy); Files.deleteIfExists(target); }
    }
    private void aliasesAndPaths() {
        ConfigKey<Map<String, Integer>> a = new ConfigKey<>("a", ConfigCodecs.mapOf(ConfigCodecs.INTEGER), Collections.emptyMap());
        ConfigKey<Map<String, Integer>> b = new ConfigKey<>("b", ConfigCodecs.mapOf(ConfigCodecs.INTEGER), Collections.emptyMap());
        ConfigKey<Integer> dotted = new ConfigKey<>("nested.value", ConfigCodecs.INTEGER, 1);
        ReactiveDomain domain = ReactiveDomain.direct();
        ConfigSession config = session(domain, ConfigSchema.of(a, b, dotted));
        operation(domain, () -> {
            config.load(new StringReader("a: &shared {x: 1}\nb: *shared\nnested: {value: 9}\n"));
            config.set(a, Collections.singletonMap("x", 2));
            String saved = save(config);
            config.load(new StringReader(saved));
            require(config.get(a).get("x") == 2 && config.get(b).get("x") == 1, "alias共享输入节点不能使不同权威值保存时互相覆盖");
            bad(config, "nested.value", "nested.value: 9\n");
            config.load(new StringReader("a: {'literal.dot': 4}\nnested: {value: 9}\n"));
            require(config.get(a).get("literal.dot") == 4 && config.get(dotted) == 9, "实际map键可含点；声明骨架必须嵌套");
        });
    }
    private static void blocked(Runnable action) {
        try { action.run(); throw new AssertionError("复合值可变"); }
        catch (UnsupportedOperationException expected) { /* 外部修改冻结内容必须失败。 */ }
    }
    public static void main(String[] args) throws Exception { new YamlObjectScenario().run(args); }
}
