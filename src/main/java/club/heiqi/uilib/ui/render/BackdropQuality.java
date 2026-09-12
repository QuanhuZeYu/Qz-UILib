package club.heiqi.uilib.ui.render;

/**
 * 背景滤镜（磨玻璃）质量档位。
 *
 * <p>用户侧配置项 {@code general.backdropQuality} 的取值载体，同时是渲染层「抽头预算」的唯一来源。
 * 档位只决定两件事：玻璃链路是否参与绘制（{@link #OFF}），以及模糊卷积核每像素采样多少次
 * （{@link #FULL} = 13 抽头，{@link #ECO} = 9 抽头）。半径、饱和度、主题配方、亮边与噪点
 * 都不由档位决定——默认档必须与引入档位前逐像素一致。</p>
 *
 * <h3>预算与核的对应（{@code uiBackdropF.frag}）</h3>
 * <ul>
 *   <li>{@link #FULL}(13)：走 {@code #if UIB_TAP_BUDGET >= 13} 分支，是与引入档位前
 *       <b>逐字节相同</b>的那段抽头代码（向日葵螺旋 13 抽头，权重和 1000/1000）；</li>
 *   <li>{@link #ECO}(9)：走 {@code #else} 分支的 9 抽头向日葵螺旋核（中心 + 8，
 *       r=sqrt(i/8)*1.6、黄金角 2.39996），权重和仍精确为 1、最远抽头 1.6001 步
 *       （完整档 1.5996 步）、加权 RMS 半径与完整档相差 -0.07%（等效模糊强度不变），
 *       片元采样次数 9/13 = -30.8%。数值由 {@code temp/perf-impl-render/kernel-9tap.py}
 *       Python 复算，并由 {@code UiBackdropKernelEnergyTest} 逐项钉住。</li>
 * </ul>
 *
 * <p>{@link #OFF} 不换核——整条链路在渲染层早退，抽头代码不会获得执行机会——故其预算与默认档
 * 同为 13，避免「关闭档的预算值」成为一个需要单独解释的第三个数。</p>
 *
 * <h3>解析口径</h3>
 * <p>本枚举的 {@link #parse(String)} 是纯函数：{@code null}／空白／未知取值一律回落
 * {@link #FULL}（= 现象与引入档位前一致），<b>不打日志</b>；非法值的 WARN 由写入口
 * {@link BackdropQualityService#applyConfigured(String)} 负责，避免解析函数带副作用、
 * 也避免同一坏值在多处解析时刷屏。</p>
 */
public enum BackdropQuality {

    /** 完整档（默认）：13 抽头，与引入档位前逐像素一致。 */
    FULL("full", 13),

    /** 省电档：9 抽头变体，片元采样次数 -30.8%，等效模糊强度与完整档相差 -0.07%。 */
    ECO("eco", 9),

    /**
     * 关闭档：玻璃链路整体早退，主题侧回落 {@code withoutBackdrop()} 实色配方（库内既有能力）。
     *
     * <p>配置值用 {@code solid} 而不是 {@code off}：YAML 1.1 的裸词 {@code off} 会被解析成布尔
     * {@code false}，手改配置写 {@code backdropQuality: off} 会让 schema 的严格类型校验失败、
     * 配置页直接打不开（impl-config 实测）。{@code solid} 是普通字符串，语义也更直白
     * （回落成实色底）。</p>
     */
    OFF("solid", 13);

    private final String configValue;
    private final int tapBudget;

    BackdropQuality(String configValue, int tapBudget) {
        this.configValue = configValue;
        this.tapBudget = tapBudget;
    }

    /**
     * 配置文件里的取值字面量（全小写 ASCII）。
     *
     * @return 配置取值
     */
    public String configValue() {
        return configValue;
    }

    /**
     * 卷积核抽头预算（含中心样本）。
     *
     * @return 13 = 完整档，9 = 省电档，关闭档同完整档
     */
    public int tapBudget() {
        return tapBudget;
    }

    /**
     * 解析配置取值（大小写不敏感、忽略首尾空白）。
     *
     * @param raw 配置里的原始字符串，可为 {@code null}
     * @return 非 null 档位；{@code null}／空白／未知取值一律回落 {@link #FULL}
     */
    public static BackdropQuality parse(String raw) {
        if (raw == null) {
            return FULL;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return FULL;
        }
        for (BackdropQuality quality : values()) {
            if (quality.configValue.equalsIgnoreCase(trimmed)) {
                return quality;
            }
        }
        return FULL;
    }
}
