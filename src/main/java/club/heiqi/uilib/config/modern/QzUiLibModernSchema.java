package club.heiqi.uilib.config.modern;

import java.util.ArrayList;

import club.heiqi.config.schema.ConfigSchema;

/**
 * uilib 自身配置的新架构 Schema 声明（实验性并行接入）。
 *
 * <p>用 {@link ConfigSchema#builder(String)} DSL 把 uilib 现有 Forge 配置
 * （{@link club.heiqi.uilib.Config} + {@link club.heiqi.uilib.font.config.FontConfig}）
 * 的标量字段重新声明为新架构 {@code ConfigSchema}，供 {@link club.heiqi.config.runtime.ConfigManager}
 * 端到端实验使用。</p>
 *
 * <h3>合规边界</h3>
 * <ul>
 *   <li>本类位于 {@code club.heiqi.uilib.config.modern}（uilib 的"mod 配置接入"专门包），
 *       非 {@code uilib.ui.*} 通用组件包。</li>
 *   <li>只 import {@code club.heiqi.config.schema.*}（核心层），合法使用。</li>
 *   <li>不 import {@code club.heiqi.config.ui.*}（UI 层），守反向依赖红线。</li>
 * </ul>
 *
 * <h3>字段映射</h3>
 * <p>从现有 Forge 配置映射标量字段。{@code fontSort} / {@code characterFontRules} 是
 * {@code String[]} 数组，经 {@link club.heiqi.config.schema.FieldType#SIMPLE_LIST} 接入；前者由
 * 专用 renderer 约束为已发现字体的固定集合并支持拖拽/全局索引排序，后者允许编辑规则行。</p>
 *
 * <p>新架构配置文件独立于 Forge cfg，使用 YAML 格式，路径由接入入口
 * {@link ModernConfigEntry} 决定，避免与 Forge 配置互相覆盖。</p>
 */
public final class QzUiLibModernSchema {

    private QzUiLibModernSchema() {
    }

    /**
     * 构建 uilib 自身配置的新架构 Schema。
     *
     * @return 不可变 ConfigSchema
     */
    public static ConfigSchema create() {
        return ConfigSchema.builder("qzuilib")
                .title("QzUiLib 配置")
                .section("general")
                    .title("General")
                    .bool("useDebug").defaultValue(Boolean.FALSE)
                        .label("useDebug").helper("是否启用调试输出").build()
                    .bool("uiDebug").defaultValue(Boolean.FALSE)
                        .label("uiDebug").helper("是否在屏幕右上角显示当前页面类名").build()
                    .bool("fontRuntimeDebug").defaultValue(Boolean.FALSE)
                        .label("fontRuntimeDebug").helper("是否启用字体运行时高频诊断日志；默认关闭").build()
                    .choice("netTransport").options("vanilla", "forge").defaultValue("vanilla")
                        .label("netTransport").helper("网络传输适配器：vanilla 默认 early mixin 路径，forge 仅兼容排障").build()
                    .choice("pickerDensity").options("auto", "compact", "standard", "roomy").defaultValue("auto")
                        .label("pickerDensity").helper("选择器面板密度档位：auto 自动求解（默认，不低于现状）；"
                                + "compact 紧凑 / standard 标准 / roomy 宽松为显式覆盖，三档同样受不低于现状约束。"
                                + "改档位即时生效，无需重开配置页或选择器。").build()
                    .choice("configPageTheme").options("flat", "glass").defaultValue("flat")
                        .label("配置页主题").helper("flat 平面（默认，不使用背景模糊或玻璃材质）；"
                                + "glass 液态玻璃。保存后更新已打开的配置页，无需重开。"
                                + "液态玻璃的滤镜质量由 backdropQuality 控制。").build()
                    .choice("backdropQuality").options("full", "eco", "solid").defaultValue("full")
                        .label("backdropQuality").helper("背景滤镜（液态玻璃模糊）档位："
                                + "full 完整＝现状液态玻璃（默认，观感零变化）；"
                                + "eco 省电＝减少模糊核采样抽头数（片元采样约 −31%，观感轻微变化）；"
                                + "solid 关闭＝玻璃链路整体早退，页面改用实色替代底（GPU 成本归零，观感变为实色）。"
                                + "笔记本 / 低端 GPU 建议 eco 或 solid。"
                                + "改档位即时生效：已构建页面自动重派生外观，无需重开配置页或页面。").build()
                .endSection()
                .section("fontSystem")
                    .title("Font System")
                    .number("lerpMode").defaultValue(Double.valueOf(3.0)).range(0, 3)
                        .label("lerpMode").helper("插值模式").build()
                    .number("aaMode").defaultValue(Double.valueOf(2.0)).range(1, 2)
                        .label("aaMode").helper("AA 模式").build()
                    .number("brightnessGain").defaultValue(Double.valueOf(2.0))
                        .label("brightnessGain").helper("HSV 亮度增强，仅增强亮度并保持原有颜色倾向").build()
                    .number("spaceWidth").defaultValue(Double.valueOf(4.0))
                        .label("spaceWidth").helper("空格宽度").build()
                    .number("characterSpacing").defaultValue(Double.valueOf(0.1))
                        .label("characterSpacing").helper("字间距").build()
                    .number("shadowOffsetX").defaultValue(Double.valueOf(0.5))
                        .label("shadowOffsetX").helper("阴影 X 偏移").build()
                    .number("shadowOffsetY").defaultValue(Double.valueOf(0.5))
                        .label("shadowOffsetY").helper("阴影 Y 偏移").build()
                    .number("renderOffset").defaultValue(Double.valueOf(0.0))
                        .label("renderOffset").helper("渲染 Z 偏移").build()
                    .number("smoothRangeMin").defaultValue(Double.valueOf(0.0)).range(0, 1)
                        .label("smoothRangeMin").helper("平滑下界").build()
                    .number("smoothRangeMax").defaultValue(Double.valueOf(0.9)).range(0, 1)
                        .label("smoothRangeMax").helper("平滑上界").build()
                    .number("drawStageUploadIntervalMs").defaultValue(Double.valueOf(20.0))
                        .range(0, 1000)
                        .label("drawStageUploadIntervalMs").helper("drawString 阶段补充上传的最短间隔（毫秒）").build()
                    .number("drawStageUploadLimitPerSecond").defaultValue(Double.valueOf(20.0))
                        .range(0, 1000)
                        .label("drawStageUploadLimitPerSecond").helper("drawString 阶段每秒最多补充上传次数").build()
                    .number("drawStageUploadBatchSize").defaultValue(Double.valueOf(2.0))
                        .range(0, 256)
                        .label("drawStageUploadBatchSize").helper("drawString 阶段每次最多补充上传字符数").build()
                    .number("aaStrength").defaultValue(Double.valueOf(12.0)).range(1, 120)
                        .label("aaStrength").helper("AA 强度").build()
                    .bool("replaceOrigin").defaultValue(Boolean.FALSE)
                        .label("replaceOrigin").helper("是否替换原版字体渲染").build()
                    .bool("customInvCountFont").defaultValue(Boolean.FALSE)
                        .label("customInvCountFont").helper("是否接管物品数量字体").build()
                    .simpleList("fontSort").defaultValue(new ArrayList<String>())
                        .label("fontSort").helper("字库排序优先级，靠前者优先匹配。拖动把手或输入全局序号调整顺序；列表固定为当前已发现字体，不能添加、删除或改名。").build()
                    .simpleList("characterFontRules").defaultValue(new ArrayList<String>())
                        .label("characterFontRules").helper("字符字体规则，每行\"选择器=字体名\"。选择器支持单字符(a)、Unicode码点(U+0041)、连续范围(a-z 或 U+4E00-U+9FFF)。禁用某条规则加 disabled: 前缀。").build()
                .endSection()
                .section("fontSizeSetting")
                    .title("Font Size")
                    .number("awtCharSize").defaultValue(Double.valueOf(64.0)).range(8, 256)
                        .label("awtCharSize").helper("字符生成分辨率；与 charSize 的比值是字体层缩放因子").build()
                    .number("charSize").defaultValue(Double.valueOf(9.0)).range(1, 72)
                        .label("charSize").helper("默认显示字号；与 awtCharSize 的比值是显示侧缩放因子").build()
                .endSection()
                .build();
    }
}
