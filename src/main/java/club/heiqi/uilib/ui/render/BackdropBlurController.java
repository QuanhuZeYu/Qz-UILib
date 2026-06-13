package club.heiqi.uilib.ui.render;

import java.util.function.UnaryOperator;

/**
 * 页面级背景模糊运行时控制器。
 *
 * <p>控制器只作用于所属页面，不会修改 {@link BackdropBlurConfig} 全局单例。</p>
 */
public interface BackdropBlurController {

    /**
     * 创建页面级背景模糊控制器。
     *
     * @param basePolicy 页面基础策略
     * @param changeCallback 策略变化后的失效回调
     * @return 控制器
     */
    static BackdropBlurController create(BackdropBlurPolicy basePolicy, Runnable changeCallback) {
        return new DefaultBackdropBlurController(basePolicy, changeCallback);
    }

    /**
     * 返回当前有效页面策略。
     *
     * @return 基础策略与运行时覆盖合并后的策略
     */
    BackdropBlurPolicy getPolicy();

    /**
     * 设置当前页面运行时覆盖策略。
     *
     * @param policy 新覆盖策略
     */
    void setPolicy(BackdropBlurPolicy policy);

    /**
     * 基于当前有效策略更新页面运行时覆盖。
     *
     * @param updater 策略更新函数
     */
    void updatePolicy(UnaryOperator<BackdropBlurPolicy> updater);

    /**
     * 清除运行时覆盖，恢复页面基础策略。
     */
    void resetPolicyOverride();
}
