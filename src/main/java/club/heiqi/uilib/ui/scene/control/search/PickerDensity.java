package club.heiqi.uilib.ui.scene.control.search;

/**
 * 选择器密度档位（P5 §1.3）——语义档位，不是分辨率档位。
 *
 * <p>档位只定义两件事：<b>图标目标边长</b>与<b>基准字号</b>（仅作"宿主未声明字号时的默认声明值"）。
 * 其余几何（cellW / trackH / stride / gap / pad / labelGap）一律由 {@link PickerMetrics} 从
 * 「逻辑盒 + 字号 + 档位」派生，档位本身不携带任何布局像素
 * （禁止把档位实现成"一套固定尺寸表"）。</p>
 *
 * <p><b>标签可读宽度预算不随档位变</b>：它登记在 {@link PickerDensityTokens#LABEL_BUDGET_EM}，
 * 是"标签应有多少空间"的全局策略 —— 否则 auto 求解会偏向"标签预算更窄的档"（窄档列数更多、
 * 更容易达标），把"想一屏看更多"的档位偏好变成"标签更不可读"，与档位的图标语义藕断丝连。</p>
 *
 * <ul>
 *   <li>{@link #COMPACT} 紧凑：大屏/高分辨率、想一屏看更多；</li>
 *   <li>{@link #STANDARD} 标准（默认）：与既有字号线一致的标准态；</li>
 *   <li>{@link #ROOMY} 宽松：触摸/远距阅读、低分辨率；</li>
 * </ul>
 *
 * <p>档位是<b>用户偏好上限</b>：{@code auto} 只在 {@code STANDARD → COMPACT} 内向下求解，
 * 不自动升到 {@link #ROOMY}（P5 §1.4，Q6 裁决）。</p>
 */
public enum PickerDensity {

    /** 紧凑档：图标目标边长 32，基准字号 11。 */
    COMPACT(32, 11),
    /** 标准档（默认）：图标目标边长 40，基准字号 12。 */
    STANDARD(40, 12),
    /** 宽松档：图标目标边长 48，基准字号 13。 */
    ROOMY(48, 13);

    private final int iconSidePx;
    private final int baseFontPx;

    PickerDensity(int iconSidePx, int baseFontPx) {
        this.iconSidePx = iconSidePx;
        this.baseFontPx = baseFontPx;
    }

    /** @return 图标目标边长（逻辑 px；受 {@code k} 与最小边长约束后才是实际边长） */
    public int iconSidePx() {
        return iconSidePx;
    }

    /**
     * {@code @return} 基准字号（逻辑 px；再乘用户字号倍率并夹取到字号域）
     *
     * <p><b>现行语义 = 宿主未声明字号时的默认面板声明值</b>（{@code ScenePickerPanel} 的
     * {@code resolvePanelDeclaredFontSize}）；宿主字号链上出现显式声明（层 1/2/3）时该档位
     * 字号不参与派生 —— 字号真值唯一来自字号链，档位不得成为第二真值。</p>
     */
    public int baseFontPx() {
        return baseFontPx;
    }



    /**
     * 按名称解析档位（大小写不敏感，允许首尾空白）。
     *
     * <p>配置面（Miner / 服务端配置 / 资源包）传入的是字符串：解析失败<b>不抛异常</b>，
     * 回落到调用方给的缺省档（配置错误不得让 UI 起不来）。</p>
     *
     * @param name     档位名（可为 null/空白）
     * @param fallback 解析失败时的回落档（非 null）
     * @return 解析出的档位，或 {@code fallback}
     */
    public static PickerDensity byName(String name, PickerDensity fallback) {
        if (name != null) {
            String trimmed = name.trim();
            for (PickerDensity value : values()) {
                if (value.name().equalsIgnoreCase(trimmed)) {
                    return value;
                }
            }
        }
        return fallback;
    }
}
