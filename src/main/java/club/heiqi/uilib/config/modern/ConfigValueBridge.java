package club.heiqi.uilib.config.modern;

import java.util.List;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.ui.theme.ConfigThemePreference;
import club.heiqi.config.schema.FieldConstraints;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.uilib.Config;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.env.ProcessUiEnvironment;
import club.heiqi.uilib.ui.render.BackdropQualityService;
import club.heiqi.uilib.util.UiNumbers;

/**
 * 值回灌抽象：从新栈 {@link Authority} 全量拉值回灌 Config + FontConfig 静态字段，
 * 并把非静态字段形态的进程级偏好回灌进各自信号载体：
 * {@code general.pickerDensity} → {@link PickerDensityPreferences}、
 * {@code general.backdropQuality} → {@link BackdropQualityService}、
 * {@code general.configPageTheme} → {@link ConfigThemePreference}、
 * {@code general.chatFrame} → {@link ChatFrameConfig}（再经它写 internal.chat3 的接管开关）。
 *
 * <p>解决阶段 C P0 缺口：新栈 {@code ConfigManager} 保存后值只落 Authority Map + YAML，
 * 从不写 {@code FontConfig.xxx} / {@code Config.xxx} 静态字段；而运行时读取者
 * （MixinFontRenderer / GlyphPageManager / TextLayoutService 等十几处）全读静态字段，
 * 配置改了运行时读不到。本类把 Authority 的值统一回灌到静态字段，闭合该缺口。</p>
 *
 * <h3>单一职责（纯回灌）</h3>
 * <ul>
 *   <li>不判 {@link FontConfig#affectsFontRuntime()} —— 调用方职责</li>
 *   <li>不调 {@code FontService.reload} —— 调用方职责</li>
 *   <li>不刷 {@code FontConfig.last*} 快照 —— 调用方 {@link FontConfig#onConfigReload()} 职责</li>
 *   <li>不涉及反向持久化：旧栈 {@code FontConfig.activeConfiguration} 反向链路已随阶段 E.2 一并删除</li>
 *   <li>{@code FontConfig.missingFontSort}（FontOrderSnapshot 派生态，由
 *       {@link FontConfig#applyFontOrderSnapshot} 在 FontGenerationRegistry.prepare 末端维护，
 *       Bridge 不参与）</li>
 * </ul>
 *
 * <p>预期调用方：</p>
 * <ul>
 *   <li>C2 ConfigSaveListener：保存回调后调用</li>
 *   <li>C3 启动加载首次回灌：CommonProxy.preInit 阶段调用</li>
 * </ul>
 *
 * <h3>合规边界</h3>
 * <p>本类位于 {@code club.heiqi.uilib.config.modern}（uilib 的"mod 配置接入"专门包），
 * import {@code club.heiqi.config.runtime.*}（核心层）+ uilib 自身 Config/FontConfig，
 * 合法使用，守反向依赖红线。</p>
 *
 * <h3>调用前置条件</h3>
 * <p>调用方需保证传入的 {@link Authority} 经 {@code ConfigManager.bootstrap} 完整加载，
 * 其 schema 字段已 normalizeDefault 注入（{@link Authority} 默认走此路径）。</p>
 *
 * <h3>数值字段的表示性守卫（#71 同族审计 A1）</h3>
 * <p>喂给 {@link FontRuntimeSettings} 构造校验的数值字段（lerpMode / spaceWidth /
 * characterSpacing / awtCharSize / charSize）一律经 {@link #representableNumber} 回灌。
 * 原因：新栈 schema 虽然声明了 range 约束，但那份约束只被配置 UI 提交路径
 * （{@code DraftBuffer.validateField}）消费，{@code ConfigManager.bootstrap} 走的是
 * {@code DraftValidator.noop()}；手改文件里的 {@code charSize: 0}、{@code lerpMode: 9}、
 * {@code NaN} 会一路直写静态字段，撞进 {@code FontService} 饿汉单例的类初始化，
 * 先 {@code ExceptionInInitializerError} 再永久 {@code NoClassDefFoundError}，
 * 客户端与专用服务端同时崩（问题 #71 的同类，但触发条件与字体缺失无关）。
 * 布尔/字符串/列表字段没有这类构造校验，不需要守卫。</p>
 *
 * <p><b>残缺 Authority 的降级行为</b>（测试与防御性参考）：若 path 不存在，
 * {@link Authority#getNumber} 返 0.0、{@link Authority#getString} 返 null、
 * {@link Authority#getBool} 返 false、{@link Authority#get} 返 null——字符串/布尔/列表
 * 字段仍会被<b>直接写入</b>对应静态字段。其中 SIMPLE_LIST 字段经 {@link #listToStringArray}
 * null 守卫转为 {@code new String[0]}，不触发 NPE；受守卫的数值字段则因 0.0 不可表示
 * 而回退 schema 默认值。</p>
 *
 * <p><b>生产路径下不会触发降级</b>：{@link QzUiLibModernSchema} 总声明全部字段，
 * bootstrap 后 Authority 必完整。本前置条件主要用于 C2（ConfigSaveListener）/ C3（启动回灌）
 * 调用方的防御性参考与测试护栏。</p>
 */
public final class ConfigValueBridge {

    private ConfigValueBridge() {
    }

    /**
     * 从 Authority 全量拉值回灌 Config + FontConfig 静态字段。
     *
     * <p>逐字段按 {@code QzUiLibModernSchema} 声明的 path（{@code "section.key"} 全路径）
     * 从 Authority 取值，做必要类型转换后写入对应 public static 静态字段。
     * 写完 {@code characterFontRules} 后调 {@link FontConfig#refreshDerivedRuleSet()}
     * 刷新 {@code characterRuleSet} 派生态（守派生态不陈旧）。</p>
     *
     * <p><b>不做</b>：判 affectsFontRuntime、调 FontService.reload、刷 last* 快照
     * （调用方职责，见类级 Javadoc）。</p>
     *
     * @param authority 新栈配置权威源（非 null）
     */
    public static void applyFromAuthority(Authority authority) {
        MyMod.LOG.debug("ConfigValueBridge.applyFromAuthority 开始");
        applyGeneral(authority);
        applyFontSystem(authority);
        applyFontSizeSetting(authority);

        // characterRuleSet 是 private，Bridge 喂完 characterFontRules 后委托 FontConfig 刷新派生态
        FontConfig.refreshDerivedRuleSet();
        MyMod.LOG.debug("Bridge 回灌完成: Config 4 + FontConfig 20 字段 + 进程级偏好信号（pickerDensity / backdropQuality / configPageTheme）");
    }

    /**
     * 回灌 general section（Config.useDebug / uiDebug / fontRuntimeDebug / netTransport
     * + 进程级偏好信号 {@link PickerDensityPreferences} / {@link BackdropQualityService}
     * / {@link ChatFrameConfig}）。
     *
     * @param authority 权威源
     */
    private static void applyGeneral(Authority authority) {
        Config.useDebug = authority.getBool("general.useDebug");
        Config.uiDebug = authority.getBool("general.uiDebug");
        // 调试开关的值读权威是上面的静态字段（每帧直读）；调试浮层的响应式消费方订阅的是
        // 诊断域的进程级通道，必须由本方法在写字段之后投影一次 —— 本方法是三条回灌入口
        // （启动加载 / 配置页保存 / 磁盘热更）的唯一汇合点，故也是订阅通道的唯一写入口。
        ProcessUiEnvironment.publishUiDebug(Config.uiDebug);
        Config.fontRuntimeDebug = authority.getBool("general.fontRuntimeDebug");
        Config.netTransport = authority.getString("general.netTransport");
        ConfigThemePreference.applyConfigured(authority.getString("general.configPageTheme"));
        // 密度偏好不是静态字段而是进程级信号（P5 §1.4「配置 → 信号 → 面板」）：面板经依赖追踪消费，
        // 保存 / 磁盘热更 / 配置页 initial apply 三条既有通道都经本方法回灌，无需重开面板。
        // 值非法（手改配置/缺键）时由 PickerDensityPreferences 回落 auto（= 现状档）并留 WARN。
        PickerDensityPreferences.applyConfigured(authority.getString(PickerDensityPreferences.CONFIG_PATH));
        // 背景滤镜档位同理不是静态字段：渲染层不得反向依赖配置层，本档位唯一的运行时权威是
        // BackdropQualityService 的进程级信号（默认 full = 现状液态玻璃，观感零变化），
        // 主题层与渲染层都经该信号动态解析。值非法（手改配置 / 缺键）由 service 回落 FULL 并留 WARN。
        BackdropQualityService.getInstance().applyConfigured(
                authority.getString(BackdropQualityService.CONFIG_PATH));
        // 聊天框形态同理不是静态字段：运行态权威是 internal.chat3 的接管开关（安装器每渲染帧读它，
        // 决定接管自定义聊天框还是把原版实例写回），本回灌是「配置文件 → 运行态」的唯一通道——
        // 启动加载 / 配置页保存 / 磁盘重载三条既有入口都经本方法，故改配置即改形态。
        ChatFrameConfig.applyConfigured(authority.getString(ChatFrameConfig.CONFIG_PATH));
    }

    /**
     * 回灌 fontSystem section（FontConfig 字体系统字段）。
     *
     * @param authority 权威源
     */
    private static void applyFontSystem(Authority authority) {
        // int 字段：Authority.getNumber 返回 double 原始类型，Math.round 避免 2.9999→2 浮点截断
        // 送进 Math.round 之前必须先过 representableNumber：越界/NaN/Infinity 会被字体运行时
        // 的构造校验拒绝，而 Math.round(NaN)=0、Math.round(Infinity)=Integer.MAX_VALUE，
        // 强转后正好落在非法区间（A1）。
        FontConfig.lerpMode = (int) Math.round(representableNumber(authority, "fontSystem.lerpMode",
                FontRuntimeSettings.FIELD_LERP_MODE, authority.getNumber("fontSystem.lerpMode"),
                FontConfig.lerpMode));
        FontConfig.aaMode = (int) Math.round(authority.getNumber("fontSystem.aaMode"));
        FontConfig.brightnessGain = authority.getNumber("fontSystem.brightnessGain");
        FontConfig.spaceWidth = representableNumber(authority, "fontSystem.spaceWidth",
                FontRuntimeSettings.FIELD_SPACE_WIDTH, authority.getNumber("fontSystem.spaceWidth"),
                FontConfig.spaceWidth);
        FontConfig.characterSpacing = representableNumber(authority, "fontSystem.characterSpacing",
                FontRuntimeSettings.FIELD_CHARACTER_SPACING,
                authority.getNumber("fontSystem.characterSpacing"), FontConfig.characterSpacing);
        FontConfig.shadowOffsetX = authority.getNumber("fontSystem.shadowOffsetX");
        FontConfig.shadowOffsetY = authority.getNumber("fontSystem.shadowOffsetY");
        FontConfig.renderOffset = authority.getNumber("fontSystem.renderOffset");
        FontConfig.smoothRangeMin = authority.getNumber("fontSystem.smoothRangeMin");
        FontConfig.smoothRangeMax = authority.getNumber("fontSystem.smoothRangeMax");
        FontConfig.drawStageUploadIntervalMs = authority.getNumber("fontSystem.drawStageUploadIntervalMs");
        FontConfig.drawStageUploadLimitPerSecond = (int) Math.round(
                authority.getNumber("fontSystem.drawStageUploadLimitPerSecond"));
        FontConfig.drawStageUploadBatchSize = (int) Math.round(
                authority.getNumber("fontSystem.drawStageUploadBatchSize"));
        FontConfig.aaStrength = authority.getNumber("fontSystem.aaStrength");
        FontConfig.replaceOrigin = authority.getBool("fontSystem.replaceOrigin");
        FontConfig.customInvCountFont = authority.getBool("fontSystem.customInvCountFont");

        // SIMPLE_LIST 字段：Authority 存 ArrayList<String>，转 String[]（null 守卫，回空数组避免下游 NPE）
        FontConfig.fontSort = listToStringArray(authority.<List<String>>get("fontSystem.fontSort"));
        FontConfig.characterFontRules = listToStringArray(authority.<List<String>>get("fontSystem.characterFontRules"));

        // fontSortConfigured 语义按 fontSort 数组是否非空决定（活消费点=FontGenerationRegistry
        // .prepare 的 orderHints 二分支，经 FontRuntimeSettings.isFontSortConfigured 读取；
        // 注：同名旧类 font/util/FontRegistry 为零实例化遗留壳，勿按其找逻辑）：
        // - fontSort 非空 = 用户在 yaml 中填了字体顺序 → true，orderHints=settings.getFontSort()
        //   （用户配置优先），FontOrderPlanner.plan 让用户配置字体先进 resolved（按用户顺序）
        // - fontSort 为空 = 用户未配置 → false，orderHints=resources.getDefaultOrderHints()
        //   （系统字体优先级提示，让中文字体如 Microsoft YaHei / PingFang SC 排前），
        //   FontOrderPlanner.plan 系统字体按自然名排序追加
        // 不再强置 true（旧实现），让二分支自动生效；resolved 末尾追加剩余字体的逻辑
        // 保证用户配置字体不会被插队（FontOrderPlanner 已守）。
        FontConfig.fontSortConfigured = FontConfig.fontSort != null && FontConfig.fontSort.length > 0;
    }

    /**
     * 回灌 fontSizeSetting section（FontConfig 字号字段）。
     *
     * @param authority 权威源
     */
    private static void applyFontSizeSetting(Authority authority) {
        FontConfig.glyphGenerationSize = representableNumber(authority, "fontSizeSetting.glyphGenerationSize",
                FontRuntimeSettings.FIELD_GLYPH_GENERATION_SIZE,
                configuredFontSizeSetting(authority, "glyphGenerationSize", "awtCharSize"),
                FontConfig.glyphGenerationSize);
        FontConfig.gameCharSize = representableNumber(authority, "fontSizeSetting.gameCharSize",
                FontRuntimeSettings.FIELD_GAME_CHAR_SIZE,
                configuredFontSizeSetting(authority, "gameCharSize", "charSize"),
                FontConfig.gameCharSize);
    }

    /**
     * 字号配置项改名兼容读取：新键（schema 字段）优先；用户配置文件里仍写着历史键时，
     * <b>一次性读取历史值并从权威态移除历史键</b>，下次保存只写新键。
     *
     * <p>为什么必须移除而不是长期回退：历史键在新 schema 里不再是字段，会落进 section raw overlay
     * 并在每次启动被本方法读到——若不移除，用户在界面里改新键后仍会被旧键值顶回，且旧键永久留在文件里。</p>
     *
     * <p>删除条件：改名版本发布满一个发布周期、确认存量配置都已迁移后，连同
     * {@link Authority#consumeLegacySectionNumber} 一起删除。</p>
     *
     * @param authority  权威源
     * @param currentKey 新键（section 内相对名）
     * @param legacyKey  历史键（section 内相对名）
     * @return 本次生效的数值
     */
    private static double configuredFontSizeSetting(Authority authority, String currentKey, String legacyKey) {
        Double legacy = authority.consumeLegacySectionNumber("fontSizeSetting", legacyKey);
        if (legacy == null) {
            return authority.getNumber("fontSizeSetting." + currentKey);
        }
        MyMod.LOG.warn("配置项 fontSizeSetting.{} 已更名为 fontSizeSetting.{}，旧值 {} 继续生效并完成迁移"
                + "（旧键已移除，下次保存只写新键）", legacyKey, currentKey, legacy);
        return legacy.doubleValue();
    }

    /**
     * 数值字段安全回灌：只修产品无法表示的值，可表示的值一律原样写入。
     *
     * <p>语义要点（刻意的取舍，不是遗漏）：判据是<b>产品能不能表示</b>，不是 schema 的
     * UI 范围。{@code charSize: 90} 超出 schema 声明的 1..72，但字体运行时完全能表示它，
     * 因此不得被钳成 72——手改配置放大字号是受支持的用法。反之 {@code charSize: 0}
     * 会让 {@code FontRuntimeSettings.capture()} 抛异常，必须修。</p>
     *
     * <p>修复顺序：①按 schema 声明的 min/max 钳位 → ②schema 默认值 → ③保持字段当前值不动
     * （启动期即代码默认值；③ 成立的前提是该值本身来自一次受守卫的写入或静态初始化，
     * 否则等于把一个已知坏值原封不动留下）。每步都用 {@link FontRuntimeSettings#isRepresentable} 复核，
     * 绝不写入仍不可表示的值；配置文件本身不改写，坏值留在文件里并由 WARN 指明。</p>
     *
     * @param authority 权威源（用于查 schema 声明的范围与默认值）
     * @param path 配置路径，例如 {@code fontSizeSetting.charSize}
     * @param field 运行时字段名，取 {@link FontRuntimeSettings} 的 FIELD_* 常量
     * @param configured 文件里的原始值
     * @param current 该静态字段当前值，作为最后兜底
     * @return 一定可被字体运行时表示的值
     */
    private static double representableNumber(Authority authority, String path, String field,
            double configured, double current) {
        if (FontRuntimeSettings.isRepresentable(field, configured)) {
            return configured;
        }
        FieldSpec spec = authority.schema() == null ? null : authority.schema().field(path);
        double repaired = clampToSchema(configured, spec);
        String action = "按 schema 声明范围钳位";
        if (!FontRuntimeSettings.isRepresentable(field, repaired) && spec != null) {
            repaired = asNumber(spec.defaultValue());
            action = "回退 schema 默认值";
        }
        if (!FontRuntimeSettings.isRepresentable(field, repaired)) {
            MyMod.LOG.warn("配置字段 {} 的值 {} 无法被字体运行时表示，且 schema 也修不出可表示的值，" +
                    "保持当前值 {}", path, fmt(configured), fmt(current));
            return current;
        }
        MyMod.LOG.warn("配置字段 {} 的值 {} 无法被字体运行时表示，已{}为 {}（配置文件未被改写）",
                path, fmt(configured), action, fmt(repaired));
        return repaired;
    }

    /**
     * 按 schema 声明的 min/max 钳位；非有限值无法钳位，原样返回交由下一步兜底。
     */
    private static double clampToSchema(double configured, FieldSpec spec) {
        if (spec == null || !UiNumbers.isFinite(configured)) {
            return configured;
        }
        FieldConstraints constraints = spec.constraints();
        if (constraints == null) {
            return configured;
        }
        double clamped = configured;
        if (clamped < constraints.min()) {
            clamped = constraints.min();
        }
        if (clamped > constraints.max()) {
            clamped = constraints.max();
        }
        return clamped;
    }

    /**
     * schema 默认值取数值；非数值默认值返 NaN（交给表示性复核兜底）。
     */
    private static double asNumber(Object defaultValue) {
        return defaultValue instanceof Number ? ((Number) defaultValue).doubleValue() : Double.NaN;
    }

    /**
     * 日志文案：非有限值用 String 表示，避免 NaN/Infinity 在占位符里丢失语义。
     */
    private static String fmt(double value) {
        return !UiNumbers.isFinite(value) ? String.valueOf(value) : Double.toString(value);
    }

    /**
     * 把 Authority 的 SIMPLE_LIST（{@code List<String>}）转为 {@code String[]}。
     *
     * <p>null 守卫：Authority.get 在 path 不存在时返 null，回退为空数组，避免下游 NPE。</p>
     *
     * @param list 原始列表（可能为 null）
     * @return 不为 null 的 String 数组
     */
    private static String[] listToStringArray(List<String> list) {
        return list == null ? new String[0] : list.toArray(new String[0]);
    }
}
