package club.heiqi.uilib.ui.render;

/**
 * UI 背景模糊全局配置。
 *
 * <p>该配置类提供常用参数与高级参数的统一管理，支持运行时动态调整。
 * 配置变更会影响后续所有 backdrop-filter 渲染行为。</p>
 *
 * <h3>常用参数</h3>
 * <ul>
 *   <li>{@link #getMaxBlurRadius()} - 元素级模糊半径上限</li>
 *   <li>{@link #getHostBackgroundBlurEnabled()} - 宿主级背景模糊开关</li>
 *   <li>{@link #getHostBackgroundBlurStrength()} - 宿主级模糊强度</li>
 * </ul>
 *
 * <h3>高级参数</h3>
 * <ul>
 *   <li>{@link #getShaderBlurRadiusLimit()} - Shader 内模糊半径上限</li>
 *   <li>{@link #getFixedPipelineEnabled()} - 固定管线降级开关</li>
 *   <li>{@link #getSnapshotPoolSize()} - 快照池大小</li>
 *   <li>{@link #getDownsampleThreshold()} - 降采样阈值</li>
 *   <li>{@link #getMaxDownsampleFactor()} - 最大降采样因子</li>
 *   <li>{@link #getTileSize()} - Tile 网格尺寸</li>
 *   <li>{@link #getFixedPipelineSampleCount()} - 固定管线采样点数</li>
 *   <li>{@link #getShaderEnabled()} - Shader 路径开关</li>
 *   <li>{@link #getTintFallbackEnabled()} - Tint 降级开关</li>
 * </ul>
 */
public final class BackdropBlurConfig {

    private static final BackdropBlurConfig INSTANCE = new BackdropBlurConfig();

    // ========== 常用参数 ==========

    /**
     * 元素级 backdrop blur 半径上限（像素）。
     * 默认值：48
     * 范围：[0, 128]
     */
    private int maxBlurRadius = 48;

    /**
     * 宿主级背景模糊是否启用。
     * 默认值：true
     */
    private boolean hostBackgroundBlurEnabled = true;

    /**
     * 宿主级背景模糊强度倍率。
     * 默认值：1.0（标准强度）
     * 范围：[0.0, 3.0]
     * 说明：影响宿主级模糊的采样偏移量，越大越模糊
     */
    private float hostBackgroundBlurStrength = 1.0F;

    // ========== 高级参数 - 渲染路径控制 ==========

    /**
     * Shader 路径是否启用。
     * 默认值：true
     * 说明：禁用后会跳过 GLSL shader 尝试，直接使用固定管线
     */
    private boolean shaderEnabled = true;

    /**
     * Shader 内 blur 半径上限（texel 单位）。
     * 默认值：56.0
     * 范围：[1.0, 128.0]
     * 说明：shader 代码中的 clamp 上限，防止过大采样偏移
     */
    private float shaderBlurRadiusLimit = 56.0F;

    /**
     * 固定管线降级路径是否启用。
     * 默认值：true
     * 说明：Shader 不可用时的降级方案
     */
    private boolean fixedPipelineEnabled = true;

    /**
     * 固定管线采样点数。
     * 默认值：8
     * 范围：[4, 16]
     * 说明：固定管线模糊使用的采样点数量，越多越平滑但性能越差
     */
    private int fixedPipelineSampleCount = 8;

    /**
     * Tint 降级路径是否启用。
     * 默认值：true
     * 说明：所有渲染路径都失败时的最终兜底
     */
    private boolean tintFallbackEnabled = true;

    // ========== 高级参数 - 性能优化 ==========

    /**
     * 快照池最大容量。
     * 默认值：32
     * 范围：[8, 128]
     * 说明：控制最多保留多少个快照纹理，超出后最久未使用的会被释放
     */
    private int snapshotPoolSize = 32;

    /**
     * 降采样触发阈值（模糊半径像素）。
     * 默认值：16
     * 范围：[8, 64]
     * 说明：模糊半径超过此值时启用降采样优化
     */
    private int downsampleThreshold = 16;

    /**
     * 最大降采样因子。
     * 默认值：4
     * 范围：[1, 8]
     * 说明：限制降采样最大倍数，防止过度失真
     */
    private int maxDownsampleFactor = 4;

    /**
     * Tile 网格尺寸（像素）。
     * 默认值：128
     * 范围：[64, 256]
     * 说明：Atlas 复用的基础单元大小，必须是2的幂
     */
    private int tileSize = 128;

    /**
     * 快照内容版本追踪是否启用。
     * 默认值：true
     * 说明：追踪两次 backdrop 之间是否有绘制变更，决定是否复用快照
     */
    private boolean contentVersionTrackingEnabled = true;

    /**
     * Separable blur filter 是否启用。
     * 默认值：true
     * 说明：降采样时使用水平+垂直两遍分离式模糊，O(2n) 代替 O(n²)
     */
    private boolean separableBlurEnabled = true;

    // ========== 高级参数 - 调试与诊断 ==========

    /**
     * 是否记录渲染路径诊断信息。
     * 默认值：true
     * 说明：影响 {@link UiBackdropFilterRenderer#getLastDetail()} 的详细程度
     */
    private boolean diagnosticsEnabled = true;

    /**
     * 是否在快照失败时输出警告日志。
     * 默认值：false
     * 说明：调试时可打开，正式环境建议关闭避免日志刷屏
     */
    private boolean logSnapshotFailures = false;

    private BackdropBlurConfig() {}

    /**
     * 获取全局配置实例。
     *
     * @return 配置实例
     */
    public static BackdropBlurConfig getInstance() {
        return INSTANCE;
    }

    // ========== 常用参数访问器 ==========

    public int getMaxBlurRadius() {
        return maxBlurRadius;
    }

    public void setMaxBlurRadius(int maxBlurRadius) {
        this.maxBlurRadius = Math.max(0, Math.min(maxBlurRadius, 128));
    }

    public boolean getHostBackgroundBlurEnabled() {
        return hostBackgroundBlurEnabled;
    }

    public void setHostBackgroundBlurEnabled(boolean hostBackgroundBlurEnabled) {
        this.hostBackgroundBlurEnabled = hostBackgroundBlurEnabled;
    }

    public float getHostBackgroundBlurStrength() {
        return hostBackgroundBlurStrength;
    }

    public void setHostBackgroundBlurStrength(float hostBackgroundBlurStrength) {
        this.hostBackgroundBlurStrength = Math.max(0.0F, Math.min(hostBackgroundBlurStrength, 3.0F));
    }

    // ========== 高级参数访问器 - 渲染路径控制 ==========

    public boolean getShaderEnabled() {
        return shaderEnabled;
    }

    public void setShaderEnabled(boolean shaderEnabled) {
        this.shaderEnabled = shaderEnabled;
    }

    public float getShaderBlurRadiusLimit() {
        return shaderBlurRadiusLimit;
    }

    public void setShaderBlurRadiusLimit(float shaderBlurRadiusLimit) {
        this.shaderBlurRadiusLimit = Math.max(1.0F, Math.min(shaderBlurRadiusLimit, 128.0F));
    }

    public boolean getFixedPipelineEnabled() {
        return fixedPipelineEnabled;
    }

    public void setFixedPipelineEnabled(boolean fixedPipelineEnabled) {
        this.fixedPipelineEnabled = fixedPipelineEnabled;
    }

    public int getFixedPipelineSampleCount() {
        return fixedPipelineSampleCount;
    }

    public void setFixedPipelineSampleCount(int fixedPipelineSampleCount) {
        this.fixedPipelineSampleCount = Math.max(4, Math.min(fixedPipelineSampleCount, 16));
    }

    public boolean getTintFallbackEnabled() {
        return tintFallbackEnabled;
    }

    public void setTintFallbackEnabled(boolean tintFallbackEnabled) {
        this.tintFallbackEnabled = tintFallbackEnabled;
    }

    // ========== 高级参数访问器 - 性能优化 ==========

    public int getSnapshotPoolSize() {
        return snapshotPoolSize;
    }

    public void setSnapshotPoolSize(int snapshotPoolSize) {
        this.snapshotPoolSize = Math.max(8, Math.min(snapshotPoolSize, 128));
    }

    public int getDownsampleThreshold() {
        return downsampleThreshold;
    }

    public void setDownsampleThreshold(int downsampleThreshold) {
        this.downsampleThreshold = Math.max(8, Math.min(downsampleThreshold, 64));
    }

    public int getMaxDownsampleFactor() {
        return maxDownsampleFactor;
    }

    public void setMaxDownsampleFactor(int maxDownsampleFactor) {
        this.maxDownsampleFactor = Math.max(1, Math.min(maxDownsampleFactor, 8));
    }

    public int getTileSize() {
        return tileSize;
    }

    public void setTileSize(int tileSize) {
        // 确保是2的幂
        int clamped = Math.max(64, Math.min(tileSize, 256));
        this.tileSize = Integer.highestOneBit(clamped);
    }

    public boolean getContentVersionTrackingEnabled() {
        return contentVersionTrackingEnabled;
    }

    public void setContentVersionTrackingEnabled(boolean contentVersionTrackingEnabled) {
        this.contentVersionTrackingEnabled = contentVersionTrackingEnabled;
    }

    public boolean getSeparableBlurEnabled() {
        return separableBlurEnabled;
    }

    public void setSeparableBlurEnabled(boolean separableBlurEnabled) {
        this.separableBlurEnabled = separableBlurEnabled;
    }

    // ========== 高级参数访问器 - 调试与诊断 ==========

    public boolean getDiagnosticsEnabled() {
        return diagnosticsEnabled;
    }

    public void setDiagnosticsEnabled(boolean diagnosticsEnabled) {
        this.diagnosticsEnabled = diagnosticsEnabled;
    }

    public boolean getLogSnapshotFailures() {
        return logSnapshotFailures;
    }

    public void setLogSnapshotFailures(boolean logSnapshotFailures) {
        this.logSnapshotFailures = logSnapshotFailures;
    }

    // ========== 便捷方法 ==========

    /**
     * 重置所有配置为默认值。
     */
    public void resetToDefaults() {
        maxBlurRadius = 48;
        hostBackgroundBlurEnabled = true;
        hostBackgroundBlurStrength = 1.0F;
        shaderEnabled = true;
        shaderBlurRadiusLimit = 56.0F;
        fixedPipelineEnabled = true;
        fixedPipelineSampleCount = 8;
        tintFallbackEnabled = true;
        snapshotPoolSize = 32;
        downsampleThreshold = 16;
        maxDownsampleFactor = 4;
        tileSize = 128;
        contentVersionTrackingEnabled = true;
        separableBlurEnabled = true;
        diagnosticsEnabled = true;
        logSnapshotFailures = false;
    }

    /**
     * 应用性能优先预设（降低质量提升性能）。
     */
    public void applyPerformancePreset() {
        maxBlurRadius = 32;
        hostBackgroundBlurStrength = 0.7F;
        shaderBlurRadiusLimit = 32.0F;
        fixedPipelineSampleCount = 6;
        snapshotPoolSize = 16;
        downsampleThreshold = 12;
        maxDownsampleFactor = 4;
        contentVersionTrackingEnabled = true;
        separableBlurEnabled = true;
    }

    /**
     * 应用质量优先预设（提升质量可能降低性能）。
     */
    public void applyQualityPreset() {
        maxBlurRadius = 64;
        hostBackgroundBlurStrength = 1.2F;
        shaderBlurRadiusLimit = 80.0F;
        fixedPipelineSampleCount = 12;
        snapshotPoolSize = 48;
        downsampleThreshold = 24;
        maxDownsampleFactor = 2;
        contentVersionTrackingEnabled = true;
        separableBlurEnabled = true;
    }

    /**
     * 应用兼容性预设（禁用高级特性确保兼容性）。
     */
    public void applyCompatibilityPreset() {
        shaderEnabled = false;
        fixedPipelineEnabled = true;
        fixedPipelineSampleCount = 6;
        tintFallbackEnabled = true;
        snapshotPoolSize = 16;
        downsampleThreshold = 16;
        maxDownsampleFactor = 2;
        contentVersionTrackingEnabled = false;
        separableBlurEnabled = false;
    }
}
