package club.heiqi.uilib.ui.scene.control.search;

/**
 * PickerIconKey —— 选择器图标（= 渲染分级）键的<b>唯一</b>生成器与解析器。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §5.1（Z-2/Z-3/S-08）。一套键 + 一套解析：</p>
 * <ul>
 *   <li>键空间 = <b>候选域键</b>：候选级 {@code candidateKey}（形如 {@code minecraft:stone}）、
 *       变体级 {@code candidateKey@meta}（形如 {@code minecraft:stone@3}）；</li>
 *   <li>生成端：选择器图标源（{@code PickerIconSource} 返回的 {@code SceneImageSource} 实现）
 *       <b>覆写既有</b> {@code SceneImageSource#registryKey()} 返回本类的返回值——
 *       <b>不新增 tierKey()</b>，{@code registryKey()} 是渲染分级唯一消费入口；</li>
 *   <li>消费端：{@code UiRenderContext} 与回退集合装配<b>原样读键、不做任何字符串拆分</b>
 *       （{@code ItemRenderFallbackKeys.track(registryKey -> registryKey)}）。</li>
 * </ul>
 *
 * <p><b>只允许选择器图标源调用</b>：{@code HostImageSource} 与非选择器图片源不得调用本类，
 * 以免污染跨 Mod 分级键族（后者键形态保持 {@code 注册名:meta}，无前缀、不迁移）。</p>
 *
 * <h3>{@link #split(String)} 语义（S-08 写死）</h3>
 * <ul>
 *   <li>{@code null} 入参 → {@code null}；</li>
 *   <li>无 {@code @}（如 {@code minecraft:stone}）→ 单元素数组 {@code {"minecraft:stone"}}（<b>不是 null</b>）；</li>
 *   <li>{@code minecraft:stone@3} → {@code {"minecraft:stone", "3"}}（按<b>最后一个</b> {@code @} 切）；</li>
 *   <li>{@code @} 在首位（{@code @3}）或末位（{@code key@}）→ {@code null}。</li>
 * </ul>
 * <p>注意：{@code split} 只服务于「诊断/展示回读」，<b>不得</b>用于「拆键再拼候选键」的回退装配
 * （那正是被删除的错解析形态，见 {@code ItemRenderFallbackKeys} 的键空间说明）。</p>
 */
public final class PickerIconKey {

    private PickerIconKey() {
    }

    /**
     * 候选级键 = 候选 key 原样（两种形态的键空间连续、无空洞）。
     *
     * @param candidateKey 候选域键（非空）
     * @return 候选级图标键
     */
    public static String candidate(String candidateKey) {
        return requireText(candidateKey, "candidateKey");
    }

    /**
     * 变体级键 = {@code candidateKey + "@" + metaToken}；{@code metaToken} 为 null 或空串时返回
     * {@link #candidate(String)}（= 候选整体，保证 {@code split} 不会产生非法形态）。
     *
     * @param candidateKey    候选域键（非空）
     * @param metaTokenOrNull 变体 meta 令牌；null/空串 = 候选整体
     * @return 变体级或候选级图标键
     */
    public static String variant(String candidateKey, String metaTokenOrNull) {
        String key = requireText(candidateKey, "candidateKey");
        if (metaTokenOrNull == null || metaTokenOrNull.isEmpty()) {
            return key;
        }
        return key + "@" + metaTokenOrNull;
    }

    /**
     * 取最后一个 {@code @} 切成两段（语义见类 javadoc；不用于回退装配）。
     *
     * @param iconKey 图标键
     * @return 单元素或两段数组；非法形态返回 null
     */
    public static String[] split(String iconKey) {
        if (iconKey == null) {
            return null;
        }
        int separator = iconKey.lastIndexOf('@');
        if (separator < 0) {
            return new String[] { iconKey };
        }
        if (separator == 0 || separator == iconKey.length() - 1) {
            return null;
        }
        return new String[] { iconKey.substring(0, separator), iconKey.substring(separator + 1) };
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }
}
