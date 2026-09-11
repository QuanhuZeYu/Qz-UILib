package club.heiqi.uilib.ui.scene.control.search;

/**
 * 密度档位用户偏好（P5 §1.4 / Q5）——含 {@code AUTO} 自动档。
 *
 * <p>{@code AUTO}（默认）由 {@link PickerMetrics} 在 {@code STANDARD → COMPACT} 内按
 * 「支配现状 + 5% 安全裕度」逐级求解；显式档位（{@link #COMPACT}/{@link #STANDARD}/{@link #ROOMY}）
 * 覆盖自动求解，但仍受同一条硬约束（可见项 ≥ 现状同盒）保护 —— 求解器在显式档位下若仍不达标，
 * 会沿面板比例阶梯继续放大面板。</p>
 *
 * <p>本枚举是<b>动态注入</b>面：经 {@code ReadableSignal} 进入面板，变更即时重派生
 * （配置页改档位无需关闭重开面板）。</p>
 */
public enum PickerDensityPreference {

    /** 自动档（默认）：在标准与紧凑之间向下求解。 */
    AUTO,
    /** 显式紧凑档。 */
    COMPACT,
    /** 显式标准档。 */
    STANDARD,
    /** 显式宽松档。 */
    ROOMY;

    /** @return 是否为自动档 */
    public boolean isAuto() {
        return this == AUTO;
    }

    /**
     * @return 偏好对应的显式档位；{@link #AUTO} 返回 {@code fallback}（自动档的字号基准取标准档）
     */
    public PickerDensity explicitDensity(PickerDensity fallback) {
        switch (this) {
            case COMPACT: return PickerDensity.COMPACT;
            case STANDARD: return PickerDensity.STANDARD;
            case ROOMY: return PickerDensity.ROOMY;
            default: return fallback;
        }
    }

    /**
     * 按名称解析偏好（大小写不敏感）；未知名称回落 {@link #AUTO}（含 null）。
     *
     * @param name 偏好名
     * @return 解析结果（非 null）
     */
    public static PickerDensityPreference byName(String name) {
        if (name != null) {
            String trimmed = name.trim();
            for (PickerDensityPreference value : values()) {
                if (value.name().equalsIgnoreCase(trimmed)) {
                    return value;
                }
            }
        }
        return AUTO;
    }
}
