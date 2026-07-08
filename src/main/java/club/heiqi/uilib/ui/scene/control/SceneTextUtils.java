package club.heiqi.uilib.ui.scene.control;

/**
 * SceneTextUtils —— scene 控件层通用文本值工具。
 *
 * <p>收口与具体文本几何度量无关的字符串归一化逻辑，避免非文本几何类依赖
 * {@link SceneTextGeometry} 的几何语义。</p>
 */
public final class SceneTextUtils {

    /** 纯静态工具类，禁止实例化。 */
    private SceneTextUtils() {
    }

    /**
     * null 安全：null 转为空串。
     *
     * @param value 可能为 null 的字符串
     * @return 非 null 字符串
     */
    public static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
