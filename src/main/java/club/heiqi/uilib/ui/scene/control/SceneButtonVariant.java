package club.heiqi.uilib.ui.scene.control;

/**
 * SceneButton 视觉变体。
 *
 * <ul>
 *   <li>{@link #STANDARD}：标准灰底，次要动作默认形态。</li>
 *   <li>{@link #PRIMARY}：ACCENT 蓝底 + 白字，主按钮高亮（如保存）。</li>
 *   <li>{@link #DANGER}：Red 危险底 + 白字，不可恢复动作（删除等）。</li>
 * </ul>
 */
public enum SceneButtonVariant {
    /** 标准灰底，次要动作 */
    STANDARD,
    /** ACCENT 蓝底白字，主按钮 */
    PRIMARY,
    /** Red 危险底白字，不可恢复动作 */
    DANGER
}
