package club.heiqi.uilib.ui.scene.control;

/**
 * scene 新栈文本输入类型 —— 轻量枚举（strangler 隔离，绝不复用旧栈 DocumentInputType）。
 *
 * <p>仅影响 {@link SceneTextInput} 的字符过滤与显示：</p>
 * <ul>
 *   <li>{@link #TEXT} — 普通文本：过滤控制字符后全放行，明文显示。</li>
 *   <li>{@link #PASSWORD} — 密码：过滤同 TEXT，显示层按码点数掩码为等量 {@code •}，回调仍上抛真实值。</li>
 *   <li>{@link #NUMBER} — 数字：过滤控制字符后额外只放行 {@code 0-9} 及 {@code . - + e E}
 *       （仅字符集过滤，不校验是否为合法数字），明文显示。</li>
 * </ul>
 */
public enum SceneInputType {
    /** 普通文本，明文显示 */
    TEXT,
    /** 密码，显示掩码为等量圆点，回调上抛真实值 */
    PASSWORD,
    /** 数字，仅放行数字相关字符集 */
    NUMBER
}
