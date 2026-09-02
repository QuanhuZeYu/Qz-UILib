package club.heiqi.uilib.ui.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver;

/**
 * UI backdrop-filter 渲染管线。
 *
 * <p>从 {@code UiRenderContext} 抽出的"背后内容滤镜"职责：负责协调
 * 主层快照获取、shader 路径、固定管线 fallback、tint 兜底，以及全局
 * 最近一次渲染路径与诊断说明的记录。</p>
 *
 * <p>本类自身无外部公开 API；最近渲染路径仍由 {@link UiRenderContext} 暴露
 * （静态访问器通过本类 trampoline 读取）。</p>
 */
final class UiBackdropFilterRenderer {

    private static final UiBackdropShaderProgram BACKDROP_SHADER_PROGRAM = new UiBackdropShaderProgram();

    private static final float[][] UI_BACKDROP_BLUR_SAMPLES = new float[][] {
            { -1.0F, 0.0F, 0.18F },
            { 1.0F, 0.0F, 0.18F },
            { 0.0F, -1.0F, 0.18F },
            { 0.0F, 1.0F, 0.18F },
            { -1.0F, -1.0F, 0.12F },
            { 1.0F, -1.0F, 0.12F },
            { -1.0F, 1.0F, 0.12F },
            { 1.0F, 1.0F, 0.12F }
    };

    private static volatile BackdropFilterRenderPath lastRenderPath = BackdropFilterRenderPath.NONE;
    private static volatile String lastDetail = "not-run";

    private UiBackdropFilterRenderer() {}

    /**
     * 返回最近一次 backdrop-filter 实际渲染路径。
     */
    static BackdropFilterRenderPath getLastRenderPath() {
        return lastRenderPath;
    }

    /**
     * 返回最近一次 backdrop-filter 诊断说明。
     */
    static String getLastDetail() {
        return lastDetail;
    }

    /**
     * 渲染一次 backdrop-filter，并记录最终路径。
     *
     * @param context 调用方渲染上下文
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param blurRadius 模糊半径像素
     * @param saturation 饱和度倍率，1.0 表示不改变
     * @param cornerRadii 四角圆角
     */
    static void render(UiRenderContext context, int left, int top, int right, int bottom, int blurRadius,
            float saturation, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        render(context, left, top, right, bottom, blurRadius, saturation, cornerRadii, (UiGlassMaterial) null);
    }

    /**
     * 渲染一次 backdrop-filter，带 iOS 材质档。
     *
     * @param context 调用方渲染上下文
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param blurRadius 模糊半径像素
     * @param saturation 饱和度倍率；material 非空时语义转为 vibrancy 乘子（1.0=严格采用材质配方值）
     * @param cornerRadii 四角圆角
     * @param material iOS 风格材质档；为 null 时走旧的线性饱和度语义
     */
    static void render(UiRenderContext context, int left, int top, int right, int bottom, int blurRadius,
            float saturation, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, UiGlassMaterial material) {
        render(context, left, top, right, bottom, blurRadius, saturation, cornerRadii,
                UiBackdropEffect.classic(material));
    }

    /**
     * 渲染一次 backdrop 效果（带完整配方：家族 + 材质档 + 液态强度）。
     *
     * @param context 调用方渲染上下文
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param blurRadius 模糊半径像素
     * @param saturation 饱和度倍率；effect 带材质档时语义转为 vibrancy 乘子
     * @param cornerRadii 四角圆角
     * @param effect 效果配方；null 等价旧线性饱和度语义
     */
    static void render(UiRenderContext context, int left, int top, int right, int bottom, int blurRadius,
            float saturation, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, UiBackdropEffect effect) {
        BackdropBlurPolicy policy = context == null ? BackdropBlurPolicy.inheritGlobal()
                : context.getBackdropBlurPolicy();
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        if (!policy.resolveEnabled(config)) {
            recordPath(BackdropFilterRenderPath.NONE, "disabled by page policy");
            return;
        }
        // 材质档自带 tint / 亮边 / 噪点：blur=0 仍有视觉产出（等价 CSS
        // backdrop-filter: blur(0) saturate(...)——不糊但材质照旧），故不能按旧规则短路。
        // 旧语义（material=null）保持"blur<=0 且饱和度恒等"即无操作的既有短路。
        boolean noVisualEffect = effect == null
                && blurRadius <= 0 && Float.compare(saturation, 1.0F) == 0;
        if (right <= left || bottom <= top || noVisualEffect) {
            recordPath(BackdropFilterRenderPath.NONE, "skipped");
            return;
        }
        String pendingFallbackDetail = drawCurrentUiBackdropFilter(context, left, top, right, bottom, blurRadius,
                saturation, cornerRadii, effect);
        if (pendingFallbackDetail == null) {
            return;
        }
        drawTintFallback(context, left, top, right, bottom, blurRadius, saturation, cornerRadii,
                pendingFallbackDetail, effect);
    }

    /**
     * 走主流程：当前 UI 主层快照 + shader / 固定管线模糊。
     *
     * @return 当走完仍未完成绘制时返回 fallback 诊断字符串；成功完成绘制时返回 {@code null}
     */
    private static String drawCurrentUiBackdropFilter(UiRenderContext context, int left, int top, int right,
            int bottom, int blurRadius, float saturation, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii,
            UiBackdropEffect effect) {
        int screenWidth = context.getScreenWidth();
        int screenHeight = context.getScreenHeight();
        UiMainLayerSnapshotService snapshotService = context.getMainLayerSnapshotService();

        SampleRegion sampleRegion = UiMainLayerSnapshotService.resolveSampleRegion(screenWidth, screenHeight, left,
                top, right, bottom, blurRadius);
        if (sampleRegion == null) {
            return "texture-copy-unavailable";
        }

        int backdropReadFramebufferId = context.getCurrentBackdropReadFramebufferId();
        MainLayerSnapshot snapshot = snapshotService.acquireSnapshot(screenWidth, screenHeight,
                backdropReadFramebufferId, context.getMainLayerContentRevisionForDiagnostics(), sampleRegion,
                blurRadius);
        if (snapshot == null) {
            return "snapshot-unavailable: " + snapshotService.getLastFailureDetail();
        }

        context.pushClip(left, top, right, bottom, cornerRadii);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean drewBackdrop = false;
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.getTextureId());
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDisable(GL11.GL_BLEND);

            float[] lightDir = resolveLightDirection(context, left, top, right, bottom);
            if (drawBackdropTextureWithShader(left, top, right, bottom, snapshot.getSampleLeft(),
                    snapshot.getSampleTop(), snapshot.getWidth(), snapshot.getHeight(), snapshot.getTextureWidth(),
                    snapshot.getTextureHeight(), snapshot.getDownsampleFactor(), blurRadius, saturation,
                    context.getBackdropBlurPolicy(), snapshot, effect, cornerRadii,
                    lightDir[0], lightDir[1])) {
                drewBackdrop = true;
                return null;
            }

            BackdropBlurPolicy policy = context.getBackdropBlurPolicy();
            BackdropBlurConfig config = BackdropBlurConfig.getInstance();
            if (!policy.resolveFixedPipelineEnabled(config)) {
                return "fixed-pipeline-disabled";
            }
            if (blurRadius <= 0) {
                return "shader-and-blur-unavailable";
            }

            drawBackdropTextureQuad(left, top, right, bottom, snapshot.getSampleLeft(), snapshot.getSampleTop(),
                    snapshot.getWidth(), snapshot.getHeight(), 0.0F, 0.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            float sampleStep = (float) resolveBackdropSampleStep(blurRadius);
            int sampleCount = Math.min(config.getFixedPipelineSampleCount(), UI_BACKDROP_BLUR_SAMPLES.length);
            for (int i = 0; i < sampleCount; i++) {
                float[] sample = UI_BACKDROP_BLUR_SAMPLES[i];
                GL11.glColor4f(1.0F, 1.0F, 1.0F, sample[2]);
                drawBackdropTextureQuad(left, top, right, bottom, snapshot.getSampleLeft(), snapshot.getSampleTop(),
                        snapshot.getWidth(), snapshot.getHeight(), sample[0] * sampleStep, sample[1] * sampleStep);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            // 固定管线逐 quad 叠加无法表达 vibrancy/亮边/噪点，材质档在此降级为"仅模糊"。
            recordPath(BackdropFilterRenderPath.FIXED_PIPELINE,
                    "shader-unavailable, samples=" + sampleCount
                            + (effect == null ? "" : ", effect-degraded(no-vibrancy)")
                            + ", snapshot=" + formatSnapshotState(snapshot));
            drewBackdrop = true;
            return null;
        } finally {
            GL20.glUseProgram(previousProgram);
            GL13.glActiveTexture(previousActiveTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glPopAttrib();
            context.popClip();
            snapshotService.releaseSnapshot(snapshot);
            if (drewBackdrop) {
                context.notifyMainLayerContentChanged();
            }
        }
    }

    private static boolean drawBackdropTextureWithShader(int left, int top, int right, int bottom, int sampleLeft,
            int sampleTop, int sampleWidth, int sampleHeight, int textureWidth, int textureHeight,
            int downsampleFactor, int blurRadius, float saturation, BackdropBlurPolicy backdropBlurPolicy,
            MainLayerSnapshot snapshot, UiBackdropEffect effect,
            UiBorderRadiusResolver.ResolvedCornerRadii panelCornerRadii, float lightDirX, float lightDirY) {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        BackdropBlurPolicy policy = backdropBlurPolicy == null ? BackdropBlurPolicy.inheritGlobal()
                : backdropBlurPolicy;
        if (!policy.resolveShaderEnabled(config)) {
            recordPath(BackdropFilterRenderPath.FIXED_PIPELINE, "shader disabled by config");
            return false;
        }
        if (!BACKDROP_SHADER_PROGRAM.ensureInitialized()) {
            recordPath(BackdropFilterRenderPath.FIXED_PIPELINE,
                    "shader unavailable: " + BACKDROP_SHADER_PROGRAM.getLastFailureMessage());
            return false;
        }
        BACKDROP_SHADER_PROGRAM.bind();
        BACKDROP_SHADER_PROGRAM.setUniformI("mainTex", 0);
        BACKDROP_SHADER_PROGRAM.setUniform2f("texelSize", 1.0F / (float) textureWidth, 1.0F / (float) textureHeight);
        BACKDROP_SHADER_PROGRAM.setUniformF("blurRadius", resolveBackdropShaderRadius(blurRadius,
                downsampleFactor, policy));
        BACKDROP_SHADER_PROGRAM.setUniformF("saturation", Math.max(0.0F, saturation));
        // 面板局部坐标基准：模型空间原点是面板左上角，减去后 GUI scale 自然约掉。
        BACKDROP_SHADER_PROGRAM.setUniform2f("panelOrigin", (float) left, (float) top);
        BACKDROP_SHADER_PROGRAM.setUniform2f("panelSizePx", (float) Math.max(1, right - left),
                (float) Math.max(1, bottom - top));
        // 四角半径（左上/右上/右下/左下），供 SDF 圆角亮边使用；与 panelSizePx 同一像素空间。
        UiBorderRadiusResolver.ResolvedCornerRadii radii = panelCornerRadii == null
                ? UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0) : panelCornerRadii;
        BACKDROP_SHADER_PROGRAM.setUniform4f("cornerRadii", (float) radii.getTopLeft(),
                (float) radii.getTopRight(), (float) radii.getBottomRight(), (float) radii.getBottomLeft());
        applyMaterialUniforms(effect, saturation, lightDirX, lightDirY,
                Math.max(1, downsampleFactor));
        drawBackdropTextureQuad(left, top, right, bottom, sampleLeft, sampleTop, sampleWidth, sampleHeight,
                0.0F, 0.0F);
        BACKDROP_SHADER_PROGRAM.unbind();
        UiGlassMaterial material = effect == null ? null : effect.getMaterial();
        recordPath(BackdropFilterRenderPath.SHADER, "blur=" + blurRadius + ", saturation="
                + String.format(java.util.Locale.ROOT, "%.2f", Float.valueOf(Math.max(0.0F, saturation)))
                + (effect == null ? "" : ", " + describeEffect(effect, material))
                + ", snapshot=" + formatSnapshotState(snapshot));
        return true;
    }

    /**
     * 下发材质档 uniform。
     *
     * <p>material 为 null 时显式把 iosMaterial 置 0 并把所有材质附加项归零：
     * shader 走旧的线性饱和度分支，亮边/噪点也严格不产生，保证旧调用方观感
     * 与升级前逐像素一致（缺 uniform 只会静默留 0，但那依赖"恰好为 0"的巧合，
     * 显式赋值才可读）。</p>
     *
     * <p>材质档下入参 {@code saturationMultiplier} 的语义从"线性饱和度乘子"转为
     * <strong>vibrancy 乘子</strong>：1.0 表示严格采用材质配方值，&gt;1 更艳、&lt;1 更哑。
     * 之所以复用同一参数位而不是新增重载，是因为材质档本来就不读线性饱和度——
     * 留着一个被静默忽略的旋钮比改语义更会让人误判（验收页的滑杆会变成死控件）。
     * 不设 1.0 下限：低于 1 在 shader 里是"按亮度加权去饱和"的合法哑光玻璃观感，
     * 强行夹住反而会让整段低区间变成等值的死区。</p>
     *
     * @param material 材质档，可为 null
     * @param saturationMultiplier 旧语义的线性饱和度乘子，或材质档的 vibrancy 倍率
     */
    private static void applyMaterialUniforms(UiBackdropEffect effect, float saturationMultiplier,
            float lightDirX, float lightDirY, int snapshotDownsampleFactor) {
        UiGlassMaterial material = effect == null ? null : effect.getMaterial();
        boolean liquid = effect != null && effect.isLiquid();
        // 液态三参数在所有路径显式赋值（含 null/经典），缺省留 0 依赖"恰好为 0"不可读。
        BACKDROP_SHADER_PROGRAM.setUniformF("liquidGlass", liquid ? 1.0F : 0.0F);
        // 作者侧 2~12 屏幕像素 -> 纹理素（与 blurRadius 同口径换算）。
        BACKDROP_SHADER_PROGRAM.setUniformF("refraction", liquid
                ? (2.0F + 10.0F * effect.getLensStrength()) / (float) snapshotDownsampleFactor
                : 0.0F);
        BACKDROP_SHADER_PROGRAM.setUniformF("edgeTint", liquid ? 0.10F + 0.25F * effect.getLensStrength() : 0.0F);
        BACKDROP_SHADER_PROGRAM.setUniform2f("lightDir", lightDirX, lightDirY);
        if (material == null) {
            BACKDROP_SHADER_PROGRAM.setUniformF("iosMaterial", 0.0F);
            BACKDROP_SHADER_PROGRAM.setUniformF("vibrancy", 1.0F);
            BACKDROP_SHADER_PROGRAM.setUniform4f("materialTint", 1.0F, 1.0F, 1.0F, 0.0F);
            BACKDROP_SHADER_PROGRAM.setUniform3f("materialLift", 0.0F, 0.0F, 0.0F);
            BACKDROP_SHADER_PROGRAM.setUniformF("edgeHighlight", 0.0F);
            BACKDROP_SHADER_PROGRAM.setUniformF("innerLightTop", 0.0F);
            BACKDROP_SHADER_PROGRAM.setUniformF("innerShadowBottom", 0.0F);
            BACKDROP_SHADER_PROGRAM.setUniformF("noiseAmount", 0.0F);
            // 旧语义：保持升级前的规则十字核行为，不做按像素旋转，逐像素可复现。
            BACKDROP_SHADER_PROGRAM.setUniformF("kernelJitter", 0.0F);
            return;
        }
        BACKDROP_SHADER_PROGRAM.setUniformF("iosMaterial", 1.0F);
        BACKDROP_SHADER_PROGRAM.setUniformF("vibrancy", material.getVibrancy()
                * Math.max(0.0F, saturationMultiplier));
        BACKDROP_SHADER_PROGRAM.setUniform4f("materialTint", material.getTintRed(), material.getTintGreen(),
                material.getTintBlue(), material.getTintAlpha());
        BACKDROP_SHADER_PROGRAM.setUniform3f("materialLift", material.getLuminanceLift(),
                material.getLuminanceLift(), material.getLuminanceLift());
        BACKDROP_SHADER_PROGRAM.setUniformF("edgeHighlight", material.getEdgeHighlight());
        BACKDROP_SHADER_PROGRAM.setUniformF("innerLightTop", material.getInnerLightTop());
        BACKDROP_SHADER_PROGRAM.setUniformF("innerShadowBottom", material.getInnerShadowBottom());
        BACKDROP_SHADER_PROGRAM.setUniformF("noiseAmount", material.getNoiseAmount());
        // 材质档启用按像素旋转采样盘：消除固定核的"蜡感"，且不含时间项故静止画面不闪烁。
        BACKDROP_SHADER_PROGRAM.setUniformF("kernelJitter", 1.0F);
    }

    private static void drawBackdropTextureQuad(int left, int top, int right, int bottom, int sampleLeft, int sampleTop,
            int sampleWidth, int sampleHeight, float sampleOffsetX, float sampleOffsetY) {
        float leftU = clampFloat(((float) left + sampleOffsetX - (float) sampleLeft) / (float) sampleWidth, 0.0F, 1.0F);
        float rightU = clampFloat(((float) right + sampleOffsetX - (float) sampleLeft) / (float) sampleWidth, 0.0F, 1.0F);
        float topV = clampFloat(1.0F - ((float) top + sampleOffsetY - (float) sampleTop) / (float) sampleHeight,
                0.0F, 1.0F);
        float bottomV = clampFloat(1.0F - ((float) bottom + sampleOffsetY - (float) sampleTop) / (float) sampleHeight,
                0.0F, 1.0F);
        // 架构禁令:不使用原版包装类(Tessellator),直接 GL 立即模式
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(leftU, bottomV);
        GL11.glVertex2f((float) left, (float) bottom);
        GL11.glTexCoord2f(rightU, bottomV);
        GL11.glVertex2f((float) right, (float) bottom);
        GL11.glTexCoord2f(rightU, topV);
        GL11.glVertex2f((float) right, (float) top);
        GL11.glTexCoord2f(leftU, topV);
        GL11.glVertex2f((float) left, (float) top);
        GL11.glEnd();
    }

    private static void drawTintFallback(UiRenderContext context, int left, int top, int right, int bottom,
            int blurRadius, float saturation, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii,
            String fallbackDetail, UiBackdropEffect effect) {
        UiGlassMaterial material = effect == null ? null : effect.getMaterial();
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        BackdropBlurPolicy policy = context.getBackdropBlurPolicy();
        if (!policy.resolveTintFallbackEnabled(config)) {
            recordPath(BackdropFilterRenderPath.NONE, "tint-fallback-disabled: " + fallbackDetail);
            return;
        }
        recordPath(BackdropFilterRenderPath.TINT_FALLBACK,
                fallbackDetail + (effect == null ? "" : ", " + describeEffect(effect, material)));
        if (material != null) {
            // 材质档自带 tint 蒙层：降级时直接用它做纯色玻璃，保证 shader
            // 可用与否的两类机器看到的玻璃底色一致（模糊没了，但材质色与亮边还在）。
            int materialAlpha = clampInt(Math.round(material.getTintAlpha() * 255.0F)
                    + Math.max(0, blurRadius) / 2, 16, 200);
            int materialRgb = material.getTintArgb() & 0x00FFFFFF;
            int tintColor = materialAlpha << 24 | materialRgb;
            int highlightColor = clampInt(materialAlpha + 26, 32, 230) << 24 | materialRgb;
            context.drawSurface(left, top, right, bottom, tintColor, highlightColor, cornerRadii);
            return;
        }
        int tintAlpha = clampInt(18 + Math.max(0, blurRadius) * 2 + Math.round(Math.max(0.0F,
                saturation - 1.0F) * 16.0F), 18, 72);
        int highlightAlpha = clampInt(tintAlpha + 22, 32, 96);
        int tintColor = tintAlpha << 24 | 0x00FFFFFF;
        int highlightColor = highlightAlpha << 24 | 0x00FFFFFF;
        context.drawSurface(left, top, right, bottom, tintColor, highlightColor, cornerRadii);
    }

    private static void recordPath(BackdropFilterRenderPath renderPath, String detail) {
        lastRenderPath = renderPath == null ? BackdropFilterRenderPath.NONE : renderPath;
        lastDetail = detail == null ? "" : detail;
    }

    private static int resolveBackdropSampleStep(int blurRadius) {
        return Math.max(1, Math.min(12, Math.round(Math.max(1, blurRadius) / 2.5F)));
    }

    private static float resolveBackdropShaderRadius(int blurRadius, int downsampleFactor,
            BackdropBlurPolicy backdropBlurPolicy) {
        if (blurRadius <= 0) {
            return 0.0F;
        }
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        BackdropBlurPolicy policy = backdropBlurPolicy == null ? BackdropBlurPolicy.inheritGlobal()
                : backdropBlurPolicy;
        float maxShaderRadius = Math.min(config.getShaderBlurRadiusLimit(), policy.resolveMaxBlurRadius(config));
        return Math.max(1.0F, Math.min(maxShaderRadius, (float) blurRadius * 0.75F
                / (float) Math.max(1, downsampleFactor)));
    }

    private static String formatSnapshotState(MainLayerSnapshot snapshot) {
        if (snapshot == null) {
            return "none";
        }
        return (snapshot.isReused() ? "reused" : "captured") + " " + snapshot.getWidth() + "x"
                + snapshot.getHeight() + " @" + snapshot.getSampleLeft() + "," + snapshot.getSampleTop()
                + " fbo=" + snapshot.getReadFramebufferId() + " rev=" + snapshot.getContentRevision()
                + " region=" + snapshot.getRegionDetail() + " " + snapshot.getTileDetail()
                + " filter=" + snapshot.getFilterDetail();
    }

    /** 诊断串里的效果描述：家族 + 档名 + 液态强度（shader 与降级路径共用）。 */
    private static String describeEffect(UiBackdropEffect effect, UiGlassMaterial material) {
        StringBuilder sb = new StringBuilder();
        sb.append("family=").append(effect.getFamily().name());
        if (material != null) {
            sb.append(", material=").append(material.name())
                    .append(" vibrancy=")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", Float.valueOf(material.getVibrancy())));
        }
        if (effect.isLiquid()) {
            sb.append(" lens=").append(String.format(java.util.Locale.ROOT, "%.2f",
                    Float.valueOf(effect.getLensStrength())));
        }
        return sb.toString();
    }

    /**
     * 解析随动缘光的光源方向：以指针相对面板中心的方向为单位向量。
     *
     * <p>官方 Liquid Glass 的缘光"responds to device motion"；MC 1.7.10 无陀螺仪，
     * 宿主以鼠标指针为虚拟光源——指针在哪个方位，缘带就朝哪边最亮。指针不可得或
     * 恰在中心时退回左上 (0.32, -0.95)（调研推荐的静态高光方向）。</p>
     *
     * <p>口径如实说明：{@code getMouseX/Y} 是屏幕绝对坐标，而面板矩形可能处于
     * 宿主局部空间（带 absX/absY 偏移），偏移大时缘光方向会偏。缘光只是装饰性
     * 方向调制，不影响采样正确性；若将来要精确，需把面板矩形换算到屏幕空间后
     * 再传进来（会牵动 render 签名，暂不做）。</p>
     */
    private static float[] resolveLightDirection(UiRenderContext context, int left, int top, int right,
            int bottom) {
        float centerX = (float) (left + right) * 0.5F;
        float centerY = (float) (top + bottom) * 0.5F;
        float dirX = 0.32F;
        float dirY = -0.95F;
        if (context != null) {
            float px = (float) context.getMouseX() - centerX;
            float py = (float) context.getMouseY() - centerY;
            float length = (float) Math.sqrt(px * px + py * py);
            if (length > 1.0F) {
                dirX = px / length;
                dirY = py / length;
            }
        }
        return new float[] { dirX, dirY };
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}