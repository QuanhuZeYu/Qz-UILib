package club.heiqi.uilib.ui.render;

import java.util.Objects;

/**
 * 页面级背景模糊策略。
 *
 * <p>策略对象不可变，每个字段都允许保持未声明状态；未声明字段会在解析时继承
 * {@link BackdropBlurConfig} 的全局默认值。页面运行时覆盖会与基础策略合并，避免修改全局单例污染其他页面。</p>
 */
public final class BackdropBlurPolicy {

    private final Boolean enabled;
    private final Boolean hostBackgroundBlurEnabled;
    private final Float hostBackgroundBlurStrength;
    private final Integer maxBlurRadius;
    private final Boolean shaderEnabled;
    private final Boolean fixedPipelineEnabled;
    private final Boolean tintFallbackEnabled;

    private BackdropBlurPolicy(Boolean enabled, Boolean hostBackgroundBlurEnabled,
            Float hostBackgroundBlurStrength, Integer maxBlurRadius, Boolean shaderEnabled,
            Boolean fixedPipelineEnabled, Boolean tintFallbackEnabled) {
        this.enabled = enabled;
        this.hostBackgroundBlurEnabled = hostBackgroundBlurEnabled;
        this.hostBackgroundBlurStrength = hostBackgroundBlurStrength;
        this.maxBlurRadius = maxBlurRadius;
        this.shaderEnabled = shaderEnabled;
        this.fixedPipelineEnabled = fixedPipelineEnabled;
        this.tintFallbackEnabled = tintFallbackEnabled;
    }

    /**
     * 创建完全继承全局配置的策略。
     *
     * @return 继承全局配置的策略
     */
    public static BackdropBlurPolicy inheritGlobal() {
        return new BackdropBlurPolicy(null, null, null, null, null, null, null);
    }

    /**
     * 创建禁用当前页面背景模糊的策略。
     *
     * @return 禁用策略
     */
    public static BackdropBlurPolicy disabled() {
        return inheritGlobal()
                .withEnabled(false)
                .withHostBackgroundBlurEnabled(false)
                .withShaderEnabled(false)
                .withFixedPipelineEnabled(false)
                .withTintFallbackEnabled(false);
    }

    /**
     * 创建性能优先策略。
     *
     * @return 性能优先策略
     */
    public static BackdropBlurPolicy performance() {
        return inheritGlobal()
                .withEnabled(true)
                .withHostBackgroundBlurEnabled(true)
                .withHostBackgroundBlurStrength(0.7F)
                .withMaxBlurRadius(32)
                .withShaderEnabled(true)
                .withFixedPipelineEnabled(true)
                .withTintFallbackEnabled(true);
    }

    /**
     * 创建质量优先策略。
     *
     * @return 质量优先策略
     */
    public static BackdropBlurPolicy quality() {
        return inheritGlobal()
                .withEnabled(true)
                .withHostBackgroundBlurEnabled(true)
                .withHostBackgroundBlurStrength(1.2F)
                .withMaxBlurRadius(64)
                .withShaderEnabled(true)
                .withFixedPipelineEnabled(true)
                .withTintFallbackEnabled(true);
    }

    /**
     * 创建兼容性优先策略。
     *
     * @return 兼容性优先策略
     */
    public static BackdropBlurPolicy compatibility() {
        return inheritGlobal()
                .withEnabled(true)
                .withHostBackgroundBlurEnabled(true)
                .withHostBackgroundBlurStrength(0.9F)
                .withMaxBlurRadius(32)
                .withShaderEnabled(false)
                .withFixedPipelineEnabled(true)
                .withTintFallbackEnabled(true);
    }

    /**
     * 基于预设创建策略。
     *
     * @param preset 预设；为 null 时继承全局
     * @return 页面级策略
     */
    public static BackdropBlurPolicy fromPreset(BackdropBlurPreset preset) {
        if (preset == null || preset == BackdropBlurPreset.DEFAULT) {
            return inheritGlobal();
        }
        switch (preset) {
            case DISABLED:
                return disabled();
            case PERFORMANCE:
                return performance();
            case QUALITY:
                return quality();
            case COMPATIBILITY:
                return compatibility();
            default:
                return inheritGlobal();
        }
    }

    /**
     * 返回启用状态覆盖后的新策略。
     *
     * @param enabled 是否启用当前页面背景模糊
     * @return 新策略
     */
    public BackdropBlurPolicy withEnabled(boolean enabled) {
        return new BackdropBlurPolicy(Boolean.valueOf(enabled), hostBackgroundBlurEnabled,
                hostBackgroundBlurStrength, maxBlurRadius, shaderEnabled, fixedPipelineEnabled,
                tintFallbackEnabled);
    }

    /**
     * 返回宿主级背景模糊开关覆盖后的新策略。
     *
     * @param enabled 是否启用宿主级背景模糊
     * @return 新策略
     */
    public BackdropBlurPolicy withHostBackgroundBlurEnabled(boolean enabled) {
        return new BackdropBlurPolicy(this.enabled, Boolean.valueOf(enabled), hostBackgroundBlurStrength,
                maxBlurRadius, shaderEnabled, fixedPipelineEnabled, tintFallbackEnabled);
    }

    /**
     * 返回宿主级背景模糊强度覆盖后的新策略。
     *
     * @param strength 强度倍率，范围会被限制到 [0.0, 3.0]
     * @return 新策略
     */
    public BackdropBlurPolicy withHostBackgroundBlurStrength(float strength) {
        return new BackdropBlurPolicy(enabled, hostBackgroundBlurEnabled,
                Float.valueOf(clampFloat(strength, 0.0F, 3.0F)), maxBlurRadius, shaderEnabled,
                fixedPipelineEnabled, tintFallbackEnabled);
    }

    /**
     * 返回元素级最大模糊半径覆盖后的新策略。
     *
     * @param radius 最大半径，范围会被限制到 [0, 128]
     * @return 新策略
     */
    public BackdropBlurPolicy withMaxBlurRadius(int radius) {
        return new BackdropBlurPolicy(enabled, hostBackgroundBlurEnabled, hostBackgroundBlurStrength,
                Integer.valueOf(clampInt(radius, 0, 128)), shaderEnabled, fixedPipelineEnabled,
                tintFallbackEnabled);
    }

    /**
     * 返回 shader 路径开关覆盖后的新策略。
     *
     * @param enabled 是否启用 shader 路径
     * @return 新策略
     */
    public BackdropBlurPolicy withShaderEnabled(boolean enabled) {
        return new BackdropBlurPolicy(this.enabled, hostBackgroundBlurEnabled, hostBackgroundBlurStrength,
                maxBlurRadius, Boolean.valueOf(enabled), fixedPipelineEnabled, tintFallbackEnabled);
    }

    /**
     * 返回固定管线 fallback 开关覆盖后的新策略。
     *
     * @param enabled 是否启用固定管线 fallback
     * @return 新策略
     */
    public BackdropBlurPolicy withFixedPipelineEnabled(boolean enabled) {
        return new BackdropBlurPolicy(this.enabled, hostBackgroundBlurEnabled, hostBackgroundBlurStrength,
                maxBlurRadius, shaderEnabled, Boolean.valueOf(enabled), tintFallbackEnabled);
    }

    /**
     * 返回 tint fallback 开关覆盖后的新策略。
     *
     * @param enabled 是否启用 tint fallback
     * @return 新策略
     */
    public BackdropBlurPolicy withTintFallbackEnabled(boolean enabled) {
        return new BackdropBlurPolicy(this.enabled, hostBackgroundBlurEnabled, hostBackgroundBlurStrength,
                maxBlurRadius, shaderEnabled, fixedPipelineEnabled, Boolean.valueOf(enabled));
    }

    /**
     * 合并运行时覆盖策略。
     *
     * @param overridePolicy 覆盖策略；为 null 时返回当前策略
     * @return 合并后的策略
     */
    public BackdropBlurPolicy merge(BackdropBlurPolicy overridePolicy) {
        if (overridePolicy == null) {
            return this;
        }
        return new BackdropBlurPolicy(
                overridePolicy.enabled == null ? enabled : overridePolicy.enabled,
                overridePolicy.hostBackgroundBlurEnabled == null ? hostBackgroundBlurEnabled
                        : overridePolicy.hostBackgroundBlurEnabled,
                overridePolicy.hostBackgroundBlurStrength == null ? hostBackgroundBlurStrength
                        : overridePolicy.hostBackgroundBlurStrength,
                overridePolicy.maxBlurRadius == null ? maxBlurRadius : overridePolicy.maxBlurRadius,
                overridePolicy.shaderEnabled == null ? shaderEnabled : overridePolicy.shaderEnabled,
                overridePolicy.fixedPipelineEnabled == null ? fixedPipelineEnabled
                        : overridePolicy.fixedPipelineEnabled,
                overridePolicy.tintFallbackEnabled == null ? tintFallbackEnabled : overridePolicy.tintFallbackEnabled);
    }

    /**
     * 解析当前页面背景模糊是否启用。
     *
     * @param config 全局默认配置
     * @return 是否启用
     */
    public boolean resolveEnabled(BackdropBlurConfig config) {
        return enabled == null || enabled.booleanValue();
    }

    /**
     * 解析当前页面宿主级背景模糊是否启用。
     *
     * @param config 全局默认配置
     * @return 是否启用宿主级背景模糊
     */
    public boolean resolveHostBackgroundBlurEnabled(BackdropBlurConfig config) {
        BackdropBlurConfig resolvedConfig = resolveConfig(config);
        return resolveEnabled(resolvedConfig)
                && (hostBackgroundBlurEnabled == null ? resolvedConfig.getHostBackgroundBlurEnabled()
                        : hostBackgroundBlurEnabled.booleanValue());
    }

    /**
     * 解析宿主级背景模糊强度。
     *
     * @param config 全局默认配置
     * @return 强度倍率
     */
    public float resolveHostBackgroundBlurStrength(BackdropBlurConfig config) {
        BackdropBlurConfig resolvedConfig = resolveConfig(config);
        return hostBackgroundBlurStrength == null ? resolvedConfig.getHostBackgroundBlurStrength()
                : hostBackgroundBlurStrength.floatValue();
    }

    /**
     * 解析元素级最大模糊半径。
     *
     * @param config 全局默认配置
     * @return 最大半径
     */
    public int resolveMaxBlurRadius(BackdropBlurConfig config) {
        BackdropBlurConfig resolvedConfig = resolveConfig(config);
        return maxBlurRadius == null ? resolvedConfig.getMaxBlurRadius() : maxBlurRadius.intValue();
    }

    /**
     * 解析 shader 路径是否启用。
     *
     * @param config 全局默认配置
     * @return 是否启用 shader
     */
    public boolean resolveShaderEnabled(BackdropBlurConfig config) {
        BackdropBlurConfig resolvedConfig = resolveConfig(config);
        return resolveEnabled(resolvedConfig)
                && (shaderEnabled == null ? resolvedConfig.getShaderEnabled() : shaderEnabled.booleanValue());
    }

    /**
     * 解析固定管线 fallback 是否启用。
     *
     * @param config 全局默认配置
     * @return 是否启用固定管线 fallback
     */
    public boolean resolveFixedPipelineEnabled(BackdropBlurConfig config) {
        BackdropBlurConfig resolvedConfig = resolveConfig(config);
        return resolveEnabled(resolvedConfig)
                && (fixedPipelineEnabled == null ? resolvedConfig.getFixedPipelineEnabled()
                        : fixedPipelineEnabled.booleanValue());
    }

    /**
     * 解析 tint fallback 是否启用。
     *
     * @param config 全局默认配置
     * @return 是否启用 tint fallback
     */
    public boolean resolveTintFallbackEnabled(BackdropBlurConfig config) {
        BackdropBlurConfig resolvedConfig = resolveConfig(config);
        return resolveEnabled(resolvedConfig)
                && (tintFallbackEnabled == null ? resolvedConfig.getTintFallbackEnabled()
                        : tintFallbackEnabled.booleanValue());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof BackdropBlurPolicy)) {
            return false;
        }
        BackdropBlurPolicy other = (BackdropBlurPolicy) object;
        return Objects.equals(enabled, other.enabled)
                && Objects.equals(hostBackgroundBlurEnabled, other.hostBackgroundBlurEnabled)
                && Objects.equals(hostBackgroundBlurStrength, other.hostBackgroundBlurStrength)
                && Objects.equals(maxBlurRadius, other.maxBlurRadius)
                && Objects.equals(shaderEnabled, other.shaderEnabled)
                && Objects.equals(fixedPipelineEnabled, other.fixedPipelineEnabled)
                && Objects.equals(tintFallbackEnabled, other.tintFallbackEnabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, hostBackgroundBlurEnabled, hostBackgroundBlurStrength, maxBlurRadius,
                shaderEnabled, fixedPipelineEnabled, tintFallbackEnabled);
    }

    @Override
    public String toString() {
        return "BackdropBlurPolicy{"
                + "enabled=" + enabled
                + ", hostBackgroundBlurEnabled=" + hostBackgroundBlurEnabled
                + ", hostBackgroundBlurStrength=" + hostBackgroundBlurStrength
                + ", maxBlurRadius=" + maxBlurRadius
                + ", shaderEnabled=" + shaderEnabled
                + ", fixedPipelineEnabled=" + fixedPipelineEnabled
                + ", tintFallbackEnabled=" + tintFallbackEnabled
                + '}';
    }

    private static BackdropBlurConfig resolveConfig(BackdropBlurConfig config) {
        return config == null ? BackdropBlurConfig.getInstance() : config;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
