package club.heiqi.uilib.config.modern;

import java.util.List;

import club.heiqi.config.runtime.Authority;
import club.heiqi.uilib.Config;
import club.heiqi.uilib.font.config.FontConfig;

/**
 * 值回灌抽象：从新栈 {@link Authority} 全量拉值回灌 Config + FontConfig 静态字段。
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
 *   <li>主动 detach {@code FontConfig.activeConfiguration}（反向持久化子任务，
 *       C 后续子任务 A2 实现）——见 {@link FontConfig#detachLegacyConfiguration}；
 *       与 {@code FontConfig.missingFontSort}（FontRegistry 派生态，由 applyFontOrderSnapshot
 *       维护，Bridge 不参与）</li>
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
 * <p><b>残缺 Authority 的降级行为</b>（测试与防御性参考）：若 path 不存在，
 * {@link Authority#getNumber} 返 0.0、{@link Authority#getString} 返 null、
 * {@link Authority#getBool} 返 false、{@link Authority#get} 返 null——这些值会被
 * <b>直接写入</b>对应静态字段。其中 SIMPLE_LIST 字段经 {@link #listToStringArray}
 * null 守卫转为 {@code new String[0]}，不触发 NPE。</p>
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
     * 刷新 {@code characterRuleSet} 派生态（守宪章派生态不陈旧）。</p>
     *
     * <p><b>不做</b>：判 affectsFontRuntime、调 FontService.reload、刷 last* 快照
     * （调用方职责，见类级 Javadoc）。</p>
     *
     * <p><b>承担</b>：主动 detach {@link FontConfig#detachLegacyConfiguration}
     * （反向持久化改向子任务 A2，见类级 Javadoc §"单一职责"）。</p>
     *
     * @param authority 新栈配置权威源（非 null）
     */
    public static void applyFromAuthority(Authority authority) {
        applyGeneral(authority);
        applyFontSystem(authority);
        applyFontSizeSetting(authority);

        // characterRuleSet 是 private，Bridge 喂完 characterFontRules 后委托 FontConfig 刷新派生态
        FontConfig.refreshDerivedRuleSet();

        // 反向持久化改向：detach 旧 Forge Configuration 引用，使后续 FontService.reload →
        // FontRegistry.reload → applyFontOrderSnapshot → persistFontSortToConfiguration 自动 no-op
        // （守 handoff「反向改向子任务不得 publish BATCH_SAVE 防回环」约束）
        FontConfig.detachLegacyConfiguration();
    }

    /**
     * 回灌 general section（Config.useDebug / uiDebug / fontRuntimeDebug / netTransport）。
     *
     * @param authority 权威源
     */
    private static void applyGeneral(Authority authority) {
        Config.useDebug = authority.getBool("general.useDebug");
        Config.uiDebug = authority.getBool("general.uiDebug");
        Config.fontRuntimeDebug = authority.getBool("general.fontRuntimeDebug");
        Config.netTransport = authority.getString("general.netTransport");
    }

    /**
     * 回灌 fontSystem section（FontConfig 字体系统字段）。
     *
     * @param authority 权威源
     */
    private static void applyFontSystem(Authority authority) {
        // int 字段：Authority.getNumber 返回 double 原始类型，Math.round 避免 2.9999→2 浮点截断
        FontConfig.lerpMode = (int) Math.round(authority.getNumber("fontSystem.lerpMode"));
        FontConfig.aaMode = (int) Math.round(authority.getNumber("fontSystem.aaMode"));
        FontConfig.brightnessGain = authority.getNumber("fontSystem.brightnessGain");
        FontConfig.spaceWidth = authority.getNumber("fontSystem.spaceWidth");
        FontConfig.characterSpacing = authority.getNumber("fontSystem.characterSpacing");
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

        // SIMPLE_LIST 字段：Authority 存 ArrayList<String>，转 String[]（null 守卫，与 FontConfig.load 一致）
        FontConfig.fontSort = listToStringArray(authority.<List<String>>get("fontSystem.fontSort"));
        FontConfig.characterFontRules = listToStringArray(authority.<List<String>>get("fontSystem.characterFontRules"));

        // 新栈 schema 总声明 fontSort path（即使用户未配置也存在），语义上等价于"用户已配置字体顺序"。
        // FontRegistry.reload:40-42 仅用此标志决定 orderHints 来源（默认提示 vs 用户顺序）与
        // 传给 fontOrderPlanner.plan 的 configured 形参，新栈下恒真合理。
        FontConfig.fontSortConfigured = true;
    }

    /**
     * 回灌 fontSizeSetting section（FontConfig 字号字段）。
     *
     * @param authority 权威源
     */
    private static void applyFontSizeSetting(Authority authority) {
        FontConfig.awtCharSize = authority.getNumber("fontSizeSetting.awtCharSize");
        FontConfig.charSize = authority.getNumber("fontSizeSetting.charSize");
    }

    /**
     * 把 Authority 的 SIMPLE_LIST（{@code List<String>}）转为 {@code String[]}。
     *
     * <p>null 守卫：Authority.get 在 path 不存在时返 null，回退为空数组，
     * 与 {@link FontConfig#load(Configuration)} 中 {@code if (fontSort == null) fontSort = new String[0]}
     * 一致，避免下游 NPE。</p>
     *
     * @param list 原始列表（可能为 null）
     * @return 不为 null 的 String 数组
     */
    private static String[] listToStringArray(List<String> list) {
        return list == null ? new String[0] : list.toArray(new String[0]);
    }
}
