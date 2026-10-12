package club.heiqi.uilib.testing.gallery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.ui.config.ConfigCodec;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigException;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.localization.LocalizationBinding;
import club.heiqi.uilib.ui.scene.localization.LocalizationFlow;
import club.heiqi.uilib.ui.scene.localization.LocalizationResource;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.TextPolicy;

/** 独立资源练习只提供输入与业务类型；解析、发布、排版及输入均沿正式生产路径。 */
final class ResourceScenarios {
    private ResourceScenarios() {}

    static SceneNode create(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        SceneNode page = new SceneNode().size(Size.fill(1), Size.fill(1)).gap(12).scrollable(true);
        page.add(label(context, () -> "语言与配置", theme, 26, false));
        page.add(label(context, () -> "在同一页面切语言、重载资料，再导入独立 YAML。所有配置仅在本次广场内存中；这里不验证实例路径迁移或文件监视。", theme, 14, true));
        page.add(GalleryStyle.frame(language(context, theme), theme));
        page.add(GalleryStyle.frame(configuration(context, theme), theme));
        return page;
    }

    private static SceneNode language(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        SceneNode card = GalleryStyle.card(theme);
        Signal<Integer> input = context.domain().signal(0);
        Signal<Integer> updates = context.domain().signal(0);
        Signal<Integer> parameter = context.domain().signal(1);
        Signal<String> key = context.domain().signal("qz_uilib.lang.title");
        Signal<String> feedback = context.domain().signal("已加载中文资料 A；格式 locale 显式固定为 Locale.US。");
        SceneEditState draft = new SceneEditState(context.domain(), "先在这里写一段未提交草稿，再切语言或重载资料。", SceneEditState.Mode.MULTI_LINE);
        LocalizationResource resource = new ExerciseResource(input);
        final LocalizationFlow flow;
        try {
            // ScenePageContext 没有宿主语言偏好入口；本场景显式选择初始语言。
            // classpath 样本及后声明的 UTF-8 输入都交生产 Properties Reader 解析。
            flow = new LocalizationFlow("zh_CN", Locale.US,
                    LocalizationResource.classpath(ResourceScenarios.class.getClassLoader(), "uilib/localization/core-loop"), resource);
        } catch (IOException failure) {
            card.add(label(context, () -> "语言初始化失败", theme, 20, false));
            card.add(label(context, () -> diagnostic(failure), theme, 14, false));
            return card;
        }
        context.own(flow);
        LocalizationBinding binding = context.localization(flow);
        context.own(flow.subscribe(snapshot -> updates.set(updates.get() + 1)));
        card.add(label(context, binding.text(key, parameter), theme, 22, false));
        card.add(label(context, () -> "生效语言：" + binding.language().get() + "；资料发布：" + updates.get()
                + "；标题键：" + key.get() + "；参数：" + parameter.get(), theme, 13, true));
        card.add(label(context, () -> "预期：键与计数变化立即派生标题；中文/English 只读取指定语言。同语言先换资料 A/B，标题保持原表，显式重载后再改变。加载失败显示生产 IOException，已生效语言和资料发布数保持不变。", theme, 14, true));
        card.add(button(context, () -> "切换中文 / English", theme, () -> loadLanguage(flow, feedback,
                () -> flow.select("zh_CN".equals(flow.current().language()) ? "en_US" : "zh_CN"))));
        card.add(button(context, () -> "参数计数 +1", theme, () -> parameter.set(parameter.get() + 1)));
        card.add(button(context, () -> "切换短 / 长标题键", theme, () -> key.set(
                "qz_uilib.lang.title.long".equals(key.get()) ? "qz_uilib.lang.title" : "qz_uilib.lang.title.long")));
        card.add(button(context, () -> "标题使用缺键：gallery.missing", theme, () -> key.set("gallery.missing")));
        card.add(button(context, () -> "标题使用非法格式：%q", theme, () -> key.set("gallery.bad")));
        card.add(label(context, binding.text("gallery.number", 1234.5), theme, 14, false));
        card.add(label(context, () -> "数值格式 locale=US：中英资料均应显示 1,234.50；语言资料选择不暗中改变格式 locale。", theme, 13, true));
        card.add(label(context, binding.text("gallery.missing"), theme, 14, false));
        card.add(label(context, binding.text("gallery.bad", 1), theme, 14, false));
        card.add(label(context, () -> "以上两行预期分别显示键名及 Format error: 当前模板；它们不是加载异常。", theme, 13, true));
        card.add(button(context, () -> "待加载资料：" + resourceName(input.get()) + " → 切换 A/B（不重载）", theme, () -> {
            input.set(input.get() == 0 ? 1 : 0);
            feedback.set("资料输入已切为 " + resourceName(input.get()) + "；尚未重载，当前标题应保持原表。");
        }));
        card.add(button(context, () -> "重载当前语言资料", theme, () -> loadLanguage(flow, feedback, flow::reload)));
        card.add(button(context, () -> "预置：读取 IOException 并重载", theme, () -> {
            input.set(2);
            loadLanguage(flow, feedback, flow::reload);
        }));
        card.add(button(context, () -> "预置：非法 properties 转义并重载", theme, () -> {
            input.set(3);
            loadLanguage(flow, feedback, flow::reload);
        }));
        card.add(label(context, () -> "语言结果：" + feedback.get(), theme, 14, false));
        card.add(label(context, () -> "失败输入会继续生效于下一次读取；要再加载正常资料，明确切回 A/B 后点击重载。没有自动回退或重试。", theme, 13, true));
        card.add(label(context, () -> "独立草稿（切语言、重载与切场景后应保留）", theme, 16, false));
        card.add(area(context, draft, theme, false, 120));
        card.add(label(context, () -> "草稿 revision=" + draft.snapshot().get().revision
                + "；选区=" + draft.snapshot().get().anchor + "–" + draft.snapshot().get().caret, theme, 13, true));
        return card;
    }

    @FunctionalInterface
    private interface LanguageOperation { void run() throws IOException; }

    private static void loadLanguage(LocalizationFlow flow, Signal<String> feedback, LanguageOperation operation) {
        try {
            operation.run();
            feedback.set("加载完成：" + flow.current().language() + "；查看标题、资料发布数与草稿。");
        } catch (IOException failure) {
            // 显示生产异常及原始原因；不选择另一语言，不调用第二次 reload。
            feedback.set(diagnostic(failure));
        }
    }

    private static String resourceName(int input) {
        return new String[] {"A", "B", "读取失败", "非法转义"}[input];
    }

    private static final class ExerciseResource implements LocalizationResource {
        private final ReadableSignal<Integer> input;
        ExerciseResource(ReadableSignal<Integer> input) { this.input = input; }
        @Override public InputStream open(String language) throws IOException {
            int selected = input.get();
            if (selected == 2) throw new IOException("gallery UTF-8 输入读取失败：" + language);
            String source;
            if (selected == 3) source = "gallery.invalid=\\uZZZZ\n";
            else if ("zh_CN".equals(language)) {
                source = selected == 0
                        ? "qz_uilib.lang.title=资料 A：第 %s 次操作\nqz_uilib.lang.title.long=资料 A 的中文长标题：改变语言和参数时自然换行，草稿仍保持原内容。第 %s 次操作\n"
                        : "qz_uilib.lang.title=资料 B：第 %s 次操作（同语言新内容）\nqz_uilib.lang.title.long=资料 B 的中文长标题：显式重载同语言资料后，已有标题与布局随新内容更新。第 %s 次操作\n";
                source += "gallery.bad=故意非法的格式 %q\n";
            } else if ("en_US".equals(language)) {
                source = selected == 0
                        ? "qz_uilib.lang.title=Resource A: operation %s\nqz_uilib.lang.title.long=Resource A: this natural English heading wraps when language and parameters change. The draft keeps its content. Operation %s\n"
                        : "qz_uilib.lang.title=Resource B: operation %s (same-language update)\nqz_uilib.lang.title.long=Resource B: an explicit reload updates the existing heading and layout while preserving the draft. Operation %s\n";
                source += "gallery.bad=Intentionally invalid format %q\n";
            } else throw new IOException("未声明的练习语言：" + language);
            source += "gallery.number=%,.2f\n";
            return new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
        }
        @Override public String toString() { return "gallery:固定 UTF-8 输入/" + resourceName(input.get()); }
    }

    private static SceneNode configuration(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        Configuration exercise = new Configuration(context);
        SceneNode card = GalleryStyle.card(theme);
        card.add(label(context, () -> "独立 YAML 配置练习", theme, 22, false));
        card.add(label(context, () -> "先选择预置，再点击导入；编辑区不是已生效值。有效输入一次发布全部键，非法输入不半批发布。缺字段使用声明默认；空串、空列表、空 map 与允许的 null 保留。", theme, 14, true));
        for (int i = 0; i < YAML_NAMES.length; i++) {
            final int selected = i;
            card.add(button(context, () -> "预置：" + YAML_NAMES[selected], theme, () -> exercise.preset(selected)));
        }
        card.add(label(context, exercise.expected, theme, 14, true));
        card.add(label(context, () -> "可编辑 YAML（Enter 换行，Ctrl+Enter 导入）", theme, 16, false));
        card.add(SceneTextArea.create(context, exercise.editor, theme, () -> false, request -> exercise.importYaml(request))
                .size(Size.fill(1), Size.fixed(260)).textPolicy(wrap()));
        card.add(button(context, () -> "导入编辑区 YAML", theme, () -> exercise.importYaml(null)));
        card.add(button(context, () -> "修改配置值：切换 enabled", theme, exercise::change));
        card.add(button(context, () -> "导出已生效配置到下方（StringWriter）", theme, exercise::exportYaml));
        card.add(button(context, () -> "仅改注释并导入已生效配置", theme, exercise::commentsOnly));
        card.add(label(context, () -> "值变化通知批次数：" + exercise.notices.get() + "（初次观察不计，相同值/仅注释导入不增加）", theme, 14, false));
        card.add(label(context, exercise::summary, theme, 14, false));
        card.add(label(context, exercise.feedback, theme, 14, false));
        card.add(label(context, () -> "导出 YAML（只读，可选择复制）", theme, 16, false));
        card.add(area(context, exercise.output, theme, true, 230));
        card.add(label(context, () -> "注释练习：导入有效预置 → 修改 enabled → 导出，workspace 与 note 的前置/行尾用户注释应保留。仅改注释按钮先用正式 Writer 取得当前值，再添加注释并走同一 Reader；通知数应不变。下方导出内容只有显式导出才更新。", theme, 13, true));
        return card;
    }

    private static final String VALID_YAML = "# 用户：启用开关\nenabled: false # 开关行尾\n"
            + "# 用户：工作区说明\nworkspace:\n  name: 工坊 # 名称行尾\n  limits:\n    jobs: 8\n    ratio: 0.75\n"
            + "  endpoints:\n    - name: 主节点\n      weight: 3\n  quotas:\n    copper: 12\n    iron: 24\n"
            + "# 用户：允许空备注\nnote: null # 备注行尾\n";
    private static final String[] YAML_NAMES = {
        "有效嵌套对象 / 对象列表 / map / null", "缺少字段 → 声明默认", "显式空值 / null", "非法越界 → 无半批",
        "未知字段 → 拒绝", "重复 key → 拒绝", "类型错误 → 拒绝", "对象列表缺必填 → 拒绝", "YAML 语法错误 → 拒绝"
    };
    private static final String[] YAML_INPUTS = {
        VALID_YAML,
        "workspace:\n  name: 仅填写名称\n",
        "enabled: false\nworkspace:\n  name: ''\n  endpoints: []\n  quotas: {}\nnote: null\n",
        "enabled: false\nworkspace:\n  name: 不应生效\n  limits:\n    jobs: 101\n",
        "enabled: false\nworkspace:\n  unknown: 1\n",
        "enabled: false\nenabled: true\n",
        "enabled: false\nworkspace:\n  limits:\n    jobs: 非整数\n",
        "enabled: false\nworkspace:\n  endpoints:\n    - weight: 3\n",
        "enabled: false\nworkspace: [\n"
    };
    private static final String[] YAML_EXPECTATIONS = {
        "预期：enabled=false；Workspace 内含 Limits、Endpoint 列表及整数 map；note=null。值不同才增加一次批次通知。",
        "预期：enabled=true，note=默认备注；name=仅填写名称，其余 Workspace 子字段回到声明默认。",
        "预期：name 为空串、endpoints 为空列表、quotas 为空映射、note=null；都不会替换成默认。",
        "预期：ConfigException 路径 workspace.limits.jobs，含输入来源及行列。即使前面的 enabled 合法，整批值与通知数均不变。",
        "预期：ConfigException 路径 workspace.unknown，含行列；整批值与通知数不变。",
        "预期：ConfigException 路径 enabled，含重复 key 的行列；整批值与通知数不变。",
        "预期：ConfigException 路径 workspace.limits.jobs，提示需要整数并含行列；整批值与通知数不变。",
        "预期：ConfigException 路径 workspace.endpoints[0].name，定位最近存在的对象行列；整批值与通知数不变。",
        "预期：ConfigException 路径 $，生产诊断显示 YAML 语法错误及行列；整批值与通知数不变。"
    };

    private static final class Configuration {
        final ConfigKey<Boolean> enabled = new ConfigKey<>("enabled", ConfigCodecs.BOOLEAN, true).withComment("是否启用练习工作区");
        final ConfigKey<Workspace> workspace;
        final ConfigKey<String> note = new ConfigKey<>("note", ConfigCodecs.nullable(ConfigCodecs.STRING), "默认备注").withComment("允许 null 的备注");
        final ConfigSession session;
        final SceneEditState editor, output;
        final Signal<String> expected, feedback;
        final Signal<Integer> notices;
        private boolean observing;
        private int notifications;

        Configuration(ScenePageContext context) {
            Limits defaultLimits = new Limits(4, 0.5);
            Endpoint defaultEndpoint = new Endpoint("默认节点", 1);
            ConfigCodec<Limits> limitsCodec = ConfigCodecs.<Limits>object()
                    .field("jobs", ConfigCodecs.INTEGER, 4, value -> value.jobs, value -> value >= 1 && value <= 100)
                    .field("ratio", ConfigCodecs.DOUBLE, 0.5, value -> value.ratio, value -> value >= 0 && value <= 1)
                    .build(values -> new Limits((Integer) values.get("jobs"), (Double) values.get("ratio")));
            ConfigCodec<Endpoint> endpointCodec = ConfigCodecs.<Endpoint>object()
                    .requiredField("name", ConfigCodecs.STRING, value -> value.name)
                    .field("weight", ConfigCodecs.INTEGER, 1, value -> value.weight, value -> value >= 1 && value <= 10)
                    .build(values -> new Endpoint((String) values.get("name"), (Integer) values.get("weight")));
            ConfigCodec<Workspace> workspaceCodec = ConfigCodecs.<Workspace>object()
                    .field("name", ConfigCodecs.STRING, "默认工作区", value -> value.name, "工作区名称（允许空串）")
                    .field("limits", limitsCodec, defaultLimits, value -> value.limits, "嵌套限额")
                    .field("endpoints", ConfigCodecs.listOf(endpointCodec), Collections.singletonList(defaultEndpoint), value -> value.endpoints, "有序对象列表")
                    .field("quotas", ConfigCodecs.mapOf(ConfigCodecs.INTEGER), Collections.singletonMap("default", 6), value -> value.quotas, "整数映射")
                    .build(Configuration::workspace);
            workspace = new ConfigKey<>("workspace", workspaceCodec,
                    new Workspace("默认工作区", defaultLimits, Collections.singletonList(defaultEndpoint), Collections.singletonMap("default", 6)))
                    .withComment("完整类型对象");
            session = new ConfigSession(context.domain(), ConfigSchema.of(enabled, workspace, note));
            editor = new SceneEditState(context.domain(), VALID_YAML, SceneEditState.Mode.MULTI_LINE);
            output = new SceneEditState(context.domain(), "点击导出查看正式 YAML Writer 输出。", SceneEditState.Mode.MULTI_LINE);
            expected = context.domain().signal(YAML_EXPECTATIONS[0]);
            feedback = context.domain().signal("当前生效值为声明默认；有效预置尚未导入。");
            notices = context.domain().signal(0);
            // 业务观察跟踪全部键，正式 batch 结束后只看到完整值；计数自身不成为 effect 依赖。
            context.domain().effect(context.scope(), () -> {
                session.get(enabled);
                session.get(workspace);
                session.get(note);
                if (observing) notices.set(++notifications);
                else observing = true;
            });
        }

        @SuppressWarnings("unchecked")
        private static Workspace workspace(Map<String, Object> values) {
            return new Workspace((String) values.get("name"), (Limits) values.get("limits"),
                    (List<Endpoint>) values.get("endpoints"), (Map<String, Integer>) values.get("quotas"));
        }

        void preset(int selected) {
            if (!ready()) return;
            editor.reset(YAML_INPUTS[selected]);
            expected.set(YAML_EXPECTATIONS[selected]);
            feedback.set("已放入预置：" + YAML_NAMES[selected] + "；尚未导入。");
        }

        private boolean ready() {
            if (!editor.snapshot().get().composing) return true;
            feedback.set("请先完成输入法组字，再操作 YAML。");
            return false;
        }

        void importYaml(SceneEditState.Submission request) {
            if (!ready()) return;
            String text = request == null ? editor.text().get() : request.text;
            try (StringReader reader = new StringReader(text)) {
                session.load(reader, "gallery:编辑区.yaml");
                if (request != null) editor.accept(request, text);
                feedback.set("导入完成；读取下方类型摘要和通知数。编辑区文本保持不变。");
            } catch (IOException | ConfigException failure) {
                if (request != null) editor.reject(request, failure.getMessage());
                feedback.set(diagnostic(failure));
            }
        }

        void change() {
            session.set(enabled, !session.get(enabled));
            feedback.set("通过 ConfigSession.set 修改 enabled；再导出观察未改节点注释。");
        }

        private String serialized() throws IOException {
            StringWriter writer = new StringWriter();
            session.save(writer);
            return writer.toString();
        }

        void exportYaml() {
            try {
                output.reset(serialized());
                feedback.set("已导出当前类型值；导出不发布配置值，也不写磁盘。");
            } catch (IOException | ConfigException failure) { feedback.set(diagnostic(failure)); }
        }

        void commentsOnly() {
            if (!ready()) return;
            try {
                String text = "# 用户：仅修改注释的导入练习\n" + serialized();
                try (StringReader reader = new StringReader(text)) { session.load(reader, "gallery:仅注释.yaml"); }
                editor.reset(text);
                expected.set("预期：所有类型值等于导入前，值变化通知批次数不增加；下次导出保留新增注释。");
                feedback.set("已通过正式 Reader 导入仅新增注释的文档；未替换成预置业务值。");
            } catch (IOException | ConfigException failure) { feedback.set(diagnostic(failure)); }
        }

        String summary() {
            Workspace value = session.get(workspace);
            StringBuilder result = new StringBuilder("Boolean enabled=").append(session.get(enabled))
                    .append("\nWorkspace.name=[").append(value.name).append("]\nLimits { Integer jobs=")
                    .append(value.limits.jobs).append(", Double ratio=").append(value.limits.ratio)
                    .append(" }\nList<Endpoint> (").append(value.endpoints.size()).append(")");
            for (Endpoint endpoint : value.endpoints) result.append("\n  { String name=[").append(endpoint.name)
                    .append("], Integer weight=").append(endpoint.weight).append(" }");
            result.append("\nMap<String,Integer> quotas=").append(value.quotas)
                    .append("\nnullable String note=").append(session.get(note) == null ? "null" : "[" + session.get(note) + "]");
            return result.toString();
        }
    }

    /** 工厂持有深层不可变业务值；equals/hashCode 使同值和注释重载不触发业务通知。 */
    private static final class Limits {
        final int jobs;
        final double ratio;
        Limits(int jobs, double ratio) { this.jobs = jobs; this.ratio = ratio; }
        @Override public boolean equals(Object other) {
            return other instanceof Limits && jobs == ((Limits) other).jobs && Double.compare(ratio, ((Limits) other).ratio) == 0;
        }
        @Override public int hashCode() { return Objects.hash(jobs, ratio); }
    }

    private static final class Endpoint {
        final String name;
        final int weight;
        Endpoint(String name, int weight) { this.name = name; this.weight = weight; }
        @Override public boolean equals(Object other) {
            return other instanceof Endpoint && weight == ((Endpoint) other).weight && name.equals(((Endpoint) other).name);
        }
        @Override public int hashCode() { return Objects.hash(name, weight); }
    }

    private static final class Workspace {
        final String name;
        final Limits limits;
        final List<Endpoint> endpoints;
        final Map<String, Integer> quotas;
        Workspace(String name, Limits limits, List<Endpoint> endpoints, Map<String, Integer> quotas) {
            this.name = name;
            this.limits = limits;
            this.endpoints = Collections.unmodifiableList(new ArrayList<>(endpoints));
            this.quotas = Collections.unmodifiableMap(new LinkedHashMap<>(quotas));
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Workspace)) return false;
            Workspace value = (Workspace) other;
            return name.equals(value.name) && limits.equals(value.limits) && endpoints.equals(value.endpoints) && quotas.equals(value.quotas);
        }
        @Override public int hashCode() { return Objects.hash(name, limits, endpoints, quotas); }
    }

    private static SceneNode label(ScenePageContext context, ReadableSignal<String> text, ReadableSignal<SceneTheme> theme, int em, boolean muted) {
        return GalleryStyle.label(context, text, theme, em, muted).textPolicy(wrap());
    }

    private static SceneNode button(ScenePageContext context, ReadableSignal<String> text, ReadableSignal<SceneTheme> theme, Runnable action) {
        return GalleryStyle.button(context, text, theme, action).size(Size.fill(1), Size.natural());
    }

    private static SceneNode area(ScenePageContext context, SceneEditState state, ReadableSignal<SceneTheme> theme, boolean readOnly, int height) {
        return SceneTextArea.create(context, state, theme, () -> readOnly,
                request -> state.reject(request, "此区域仅用于草稿/查看；请使用 YAML 导入按钮提交配置。"))
                .size(Size.fill(1), Size.fixed(height)).textPolicy(wrap());
    }

    private static TextPolicy wrap() { return new TextPolicy(TextPolicy.Direction.AUTO, TextPolicy.Wrap.EMERGENCY, "und", 0, 0); }

    private static String diagnostic(Throwable failure) {
        StringBuilder text = new StringBuilder(failure.getClass().getSimpleName());
        if (failure instanceof ConfigException) text.append("；路径=").append(((ConfigException) failure).path());
        text.append("\n").append(failure.getMessage());
        for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause())
            text.append("\nCaused by ").append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
        return text.toString();
    }
}
