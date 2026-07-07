package club.heiqi.uilib.font.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字符字体覆盖规则。
 */
public final class FontCharacterRule {

    public static final String DISABLED_PREFIX = "disabled:";

    private final String selector;
    private final String fontName;
    private final int startCodepoint;
    private final int endCodepoint;
    private final boolean enabled;
    private final String errorMessage;

    private FontCharacterRule(String selector, String fontName, int startCodepoint, int endCodepoint,
            boolean enabled, String errorMessage) {
        this.selector = selector == null ? "" : selector;
        this.fontName = fontName == null ? "" : fontName;
        this.startCodepoint = startCodepoint;
        this.endCodepoint = endCodepoint;
        this.enabled = enabled;
        this.errorMessage = errorMessage;
    }

    /**
     * 解析配置项中的单条字符字体规则，按逗号展开为多条独立规则（形态 A）。
     *
     * <p>语义要点：</p>
     * <ul>
     *   <li>逗号是多选择器分隔符：{@code a,b,c=Font} 展开为 3 条独立 rule，下游 matches/resolveFontName 零改动。</li>
     *   <li>disabled 前缀剥离在逗号拆分之前（边界②：disabled 作用于整条 rawRule，所有展开段共享 enabled 态）。</li>
     *   <li>逗号间空段（trim 后 isEmpty）跳过，不报错不展开（边界③裁决：如 {@code a,,b=Font} → 2 条）。</li>
     *   <li>整条 rawRule 缺 {@code =} / selector 空 / fontName 空时，返回单条 invalid 的 list（保留兜底语义）。</li>
     *   <li>全段皆空（如 {@code ,,=Font}）返回空 list。</li>
     *   <li>展开段可混合 valid / invalid，invalid 段携带自身 errorMessage。</li>
     * </ul>
     *
     * <p><b>逗号歧义裁决</b>：逗号不再支持作为单字符 selector，需用 {@code U+002C} 码点形式。</p>
     *
     * <p><b>配置写回不变</b>（边界①）：展开只发生在 parse 运行时，{@link #toConfigValue()} 仍输出
     * {@code selector=fontName} 单行文本，逗号在 selector 内原样保留。</p>
     *
     * @param rawRule 原始规则文本
     * @return 解析后的规则列表（可能含 valid + invalid 段混合；兜底场景返回单元素 list；全空段返回空 list）
     */
    public static List<FontCharacterRule> parse(String rawRule) {
        String normalizedRule = rawRule == null ? "" : rawRule.trim();
        boolean enabled = true;
        if (normalizedRule.regionMatches(true, 0, DISABLED_PREFIX, 0, DISABLED_PREFIX.length())) {
            enabled = false;
            normalizedRule = normalizedRule.substring(DISABLED_PREFIX.length()).trim();
        }

        int separatorIndex = normalizedRule.indexOf('=');
        if (separatorIndex < 0) {
            return Collections.singletonList(invalid("", "", enabled, "规则必须使用 字符或范围=字体名 格式"));
        }

        String selector = normalizedRule.substring(0, separatorIndex).trim();
        String fontName = normalizedRule.substring(separatorIndex + 1).trim();
        if (selector.isEmpty()) {
            return Collections.singletonList(invalid(selector, fontName, enabled, "字符或范围不能为空"));
        }
        if (fontName.isEmpty()) {
            return Collections.singletonList(invalid(selector, fontName, enabled, "字体名不能为空"));
        }

        // 逗号多点展开：disabled 已剥离（边界②），空段跳过（边界③）
        List<FontCharacterRule> result = new ArrayList<FontCharacterRule>();
        for (String segment : selector.split(",")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Range range = parseSelector(trimmed);
            if (range.errorMessage != null) {
                result.add(new FontCharacterRule(trimmed, fontName, -1, -1, enabled, range.errorMessage));
            } else {
                result.add(new FontCharacterRule(trimmed, fontName, range.startCodepoint, range.endCodepoint, enabled, null));
            }
        }
        return result;
    }

    /**
     * 单行解析（不展开逗号多点）：保留完整 selector 文本（含逗号），用于 UI 编辑器行数据
     * （{@code CharacterRuleFieldRenderer} 的 normalize / fromRaw）。
     *
     * <p>与 {@link #parse(String)} 的关系：</p>
     * <ul>
     *   <li>共享 disabled 剥离 + 首 {@code =} 拆分逻辑；兜底场景（缺 {@code =} / 空字体名）返回单条 invalid。</li>
     *   <li>selector 字段保留完整逗号文本（不替换为单段），匹配 UI 行"一条 rawRule 一行"的语义。</li>
     *   <li>errorMessage 派生自逗号展开后首个 invalid 段（与 parse 一致）；全 valid 则为 null；
     *       全段皆空（如 {@code ",,=Font}）返回 invalid "字符或范围不能为空"。</li>
     *   <li>startCodepoint/endCodepoint 字段无意义（多段无法用单区间表达），不应据此调用 matches；
     *       该方法产出对象仅用于 UI 行字段表示与 errorMessage 透出。</li>
     * </ul>
     *
     * @param rawRule 原始规则文本
     * @return 单条规则（selector 含逗号原样保留）
     */
    public static FontCharacterRule parseLine(String rawRule) {
        String normalizedRule = rawRule == null ? "" : rawRule.trim();
        boolean enabled = true;
        if (normalizedRule.regionMatches(true, 0, DISABLED_PREFIX, 0, DISABLED_PREFIX.length())) {
            enabled = false;
            normalizedRule = normalizedRule.substring(DISABLED_PREFIX.length()).trim();
        }

        int separatorIndex = normalizedRule.indexOf('=');
        if (separatorIndex < 0) {
            return invalid("", "", enabled, "规则必须使用 字符或范围=字体名 格式");
        }

        String selector = normalizedRule.substring(0, separatorIndex).trim();
        String fontName = normalizedRule.substring(separatorIndex + 1).trim();
        if (selector.isEmpty()) {
            return invalid(selector, fontName, enabled, "字符或范围不能为空");
        }
        if (fontName.isEmpty()) {
            return invalid(selector, fontName, enabled, "字体名不能为空");
        }

        // errorMessage 派生：展开后首个 invalid 段；全段皆空视作 selector 空
        String firstError = null;
        boolean hasNonEmptySegment = false;
        for (String segment : selector.split(",")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            hasNonEmptySegment = true;
            Range range = parseSelector(trimmed);
            if (range.errorMessage != null) {
                firstError = range.errorMessage;
                break;
            }
        }
        if (!hasNonEmptySegment) {
            return invalid(selector, fontName, enabled, "字符或范围不能为空");
        }
        return new FontCharacterRule(selector, fontName, -1, -1, enabled, firstError);
    }

    /**
     * 创建规则配置文本。
     *
     * @param enabled 是否启用
     * @param selector 字符或范围
     * @param fontName 字体名
     * @return 配置文本
     */
    public static String toConfigValue(boolean enabled, String selector, String fontName) {
        String body = normalizeDraftValue(selector) + "=" + normalizeDraftValue(fontName);
        return enabled ? body : DISABLED_PREFIX + body;
    }

    /**
     * 判断指定码点是否命中当前规则。
     *
     * @param codepoint 字符码点
     * @return 是否命中
     */
    public boolean matches(int codepoint) {
        return isActive() && codepoint >= startCodepoint && codepoint <= endCodepoint;
    }

    /**
     * 判断规则是否可参与运行时匹配。
     *
     * @return 是否启用且格式有效
     */
    public boolean isActive() {
        return enabled && isValid();
    }

    /**
     * 判断规则格式是否有效。
     *
     * @return 是否有效
     */
    public boolean isValid() {
        return errorMessage == null;
    }

    /**
     * 返回字符或范围选择器。
     *
     * @return 选择器
     */
    public String getSelector() {
        return selector;
    }

    /**
     * 返回目标字体名。
     *
     * @return 字体名
     */
    public String getFontName() {
        return fontName;
    }

    /**
     * 返回起始码点。
     *
     * @return 起始码点
     */
    public int getStartCodepoint() {
        return startCodepoint;
    }

    /**
     * 返回结束码点。
     *
     * @return 结束码点
     */
    public int getEndCodepoint() {
        return endCodepoint;
    }

    /**
     * 返回规则是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 返回错误说明。
     *
     * @return 错误说明，格式有效时为 null
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 返回可写回配置的规则文本。
     *
     * @return 配置文本
     */
    public String toConfigValue() {
        return toConfigValue(enabled, selector, fontName);
    }

    private static FontCharacterRule invalid(String selector, String fontName, boolean enabled, String errorMessage) {
        return new FontCharacterRule(selector, fontName, -1, -1, enabled, errorMessage);
    }

    private static Range parseSelector(String selector) {
        int rangeSeparatorIndex = selector.indexOf('-');
        if (rangeSeparatorIndex > 0 && rangeSeparatorIndex < selector.length() - 1) {
            String startText = selector.substring(0, rangeSeparatorIndex).trim();
            String endText = selector.substring(rangeSeparatorIndex + 1).trim();
            Endpoint start = parseEndpoint(startText);
            if (start.errorMessage != null) {
                return Range.invalid(start.errorMessage);
            }
            Endpoint end = parseEndpoint(endText);
            if (end.errorMessage != null) {
                return Range.invalid(end.errorMessage);
            }
            if (start.codepoint > end.codepoint) {
                return Range.invalid("范围起点不能大于终点");
            }
            return new Range(start.codepoint, end.codepoint, null);
        }

        Endpoint endpoint = parseEndpoint(selector);
        if (endpoint.errorMessage != null) {
            return Range.invalid(endpoint.errorMessage);
        }
        return new Range(endpoint.codepoint, endpoint.codepoint, null);
    }

    private static Endpoint parseEndpoint(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return Endpoint.invalid("字符端点不能为空");
        }
        if (normalized.regionMatches(true, 0, "U+", 0, 2)) {
            try {
                int codepoint = Integer.parseInt(normalized.substring(2), 16);
                return validEndpoint(codepoint);
            } catch (NumberFormatException exception) {
                return Endpoint.invalid("Unicode 码点格式无效");
            }
        }
        if (normalized.codePointCount(0, normalized.length()) != 1) {
            return Endpoint.invalid("字符端点只能包含一个字符或 U+XXXX 码点");
        }
        return validEndpoint(normalized.codePointAt(0));
    }

    private static Endpoint validEndpoint(int codepoint) {
        if (!Character.isValidCodePoint(codepoint)) {
            return Endpoint.invalid("Unicode 码点超出有效范围");
        }
        return new Endpoint(codepoint, null);
    }

    private static String normalizeDraftValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Range {

        private final int startCodepoint;
        private final int endCodepoint;
        private final String errorMessage;

        private Range(int startCodepoint, int endCodepoint, String errorMessage) {
            this.startCodepoint = startCodepoint;
            this.endCodepoint = endCodepoint;
            this.errorMessage = errorMessage;
        }

        private static Range invalid(String errorMessage) {
            return new Range(-1, -1, errorMessage);
        }
    }

    private static final class Endpoint {

        private final int codepoint;
        private final String errorMessage;

        private Endpoint(int codepoint, String errorMessage) {
            this.codepoint = codepoint;
            this.errorMessage = errorMessage;
        }

        private static Endpoint invalid(String errorMessage) {
            return new Endpoint(-1, errorMessage);
        }
    }
}
