#version 120

// UI 磨玻璃材质着色器。
//
// 质感分层（对齐 iOS UIVisualEffectView 的材质构成，顺序不可交换）：
//   1) 模糊：13 抽头向日葵螺旋核（连续半径，消散光）
//   2) vibrancy：亮度域保护式饱和提升（不是线性乘饱和度）
//   3) 材质蒙层 tint：白/深色半透明叠加（在色彩校正之后）
//   4) 亮度偏置 lift + 边缘亮边 + 内侧上缘柔光 / 下缘暗带
//   5) 抗 banding 噪点：最后一步加性叠加，任何缩放之前
//
// 第三家族（liquidGlass 门控，在 1)~5) 之上增量叠加；关闭时折射偏移恒为 0、
// 缘光调制系数恒为 1、厚度 tint 增量为 0，数值上与经典档一致）：
//   6) Liquid Glass：边缘凸透镜折射（SDF 梯度偏置采样）+ 边缘厚度 tint 递增
//      + 随动缘光（高光峰值沿边缘滑动到光源方向；MC 无陀螺仪，光源=鼠标）。
//
// 色空间口径：Minecraft 帧缓冲是 sRGB 编码但全程按线性值混合，本 shader 沿用既有
// 口径直接处理 framebuffer 原值，不做 sRGB<->linear 往返（往返会让灰阶中点掉到
// 0.47，与原版 UI 整体发暗）。下面所有经验系数都在该口径下取值。
//
// 兼容性红线：只用 GLSL 1.20 内建函数。texture2Dbias 属 ARB_shader_texture_lod
// （2009 扩展，非 1.20 内建），依赖它会在扩展缺失的机器上让整个 shader 编译失败、
// 静默退回固定管线，比不加更糟；且大半径已由快照 downsample + separable filter pass
// 预降采样，mip 偏置本身冗余。
//
// iosMaterial <= 0.5 时退回"线性饱和度乘子"的旧语义，且不叠加 tint / 亮边 / 噪点，
// 保证未显式采用材质档的既有调用方观感不被悄悄改变。

varying vec2 texCoord;
varying vec2 panelUv;

uniform sampler2D mainTex;
uniform vec2 texelSize;
uniform float blurRadius;
uniform float saturation;
uniform float iosMaterial;
uniform float vibrancy;
uniform vec4 materialTint;
uniform vec3 materialLift;
uniform float edgeHighlight;
uniform float innerLightTop;
uniform float innerShadowBottom;
uniform float noiseAmount;
uniform vec2 panelSizePx;
uniform vec4 cornerRadii;
uniform float kernelJitter;
uniform float liquidGlass;
uniform float refraction;
uniform float edgeTint;
uniform vec2 lightDir;

vec3 applySaturation(vec3 color, float amount) {
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 gray = vec3(luma);
    return clamp(gray + (color - gray) * amount, 0.0, 1.0);
}

// iOS vibrancy：亮度域保护式饱和提升。
//
// 只乘饱和度会让暗部与中间调一起吃色、高光处直接 clamp 偏色，这正是"糊一层彩"
// 廉价感的来源。按 luma 加权：暗部（本口径下 luma <= 0.224）完全保持原样，
// 越亮吃得越多——通透感来自"该饱和的地方才饱和"。
// t = 1.289*L - 0.289 是线性域等价式 1.889*L - 0.889 在帧缓冲原值口径下的拟合。
// vibrancy = 1.0 时乘子 k 恒为 1，严格恒等，便于逐档 A/B 比对。
vec3 applyVibrancy(vec3 color, float amount) {
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float t = clamp(1.289 * luma - 0.289, 0.0, 1.0);
    float k = 1.0 + (amount - 1.0) * t;
    vec3 gray = vec3(luma);
    return clamp(gray + (color - gray) * k, 0.0, 1.0);
}

// 按面板局部坐标所在象限取对应角半径。cornerRadii 顺序：左上、右上、右下、左下
// （与 ResolvedCornerRadii 一致；uv 的 y 向下，故 y<0.5 为上半）。
float cornerRadiusAt(vec4 radii, vec2 uv) {
    float topR = mix(radii.x, radii.y, step(0.5, uv.x));
    float bottomR = mix(radii.w, radii.z, step(0.5, uv.x));
    return mix(topR, bottomR, step(0.5, uv.y));
}

// 廉价 hash 噪声：不用 sin 做 hash（各驱动 sin 实现差异会让噪声分布随硬件变化）。
float hashNoise(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main(void) {
    // 核能量契约：13 抽头（中心 + 12 个向日葵螺旋抽头）权重和恒为 1（/1000 整数化），保证磨玻璃
    // 是"亮度保持"操作。柔化提交 e5a6b2ae 重写核时权重和漂移到 1.12，导致磨玻璃
    // 整体过曝 12%、高光处 clamp 偏色（旧核历史和为 1.02，证明归一是本意而非风格
    // 选择）；2026-09-01 混合语义特勘修复，权重和由 UiBackdropKernelEnergyTest 以
    // 源码契约锚定（抽头数 13 + 和为 1），改核必须同步保持。
    //
    // 核形状演进（2026-09-02 散光调优）：规则十字+对角 -> 双半径 Poisson 盘 -> 向日葵螺旋。
    // 前两版散光（大半径下"星星点点"如散光眼）的数学根源是抽头半径只有离散几档：
    // 双半径盘 12 个外圈抽头挤在 r≈0.45 与 r≈0.9 两个环上，模糊读作"中心 + 两亮环"；
    // 旋转是刚体变换救不了径向分层（环旋转对称，转了等于没转）。向日葵螺旋
    // （r=sqrt(i/12)*1.6 面积均匀、角步进黄金角 2.39996 rad）让 13 个抽头落在 13 个
    // 连续半径上、角向黄金分布，高斯权重按半径单调衰减——径向能量摊平，亮环消失；
    // 螺旋残留的角向臂结构恰由下面的逐像素旋转打散（对双半径环旋转无效，对螺旋有效）。
    // 0.98 为抽头半径补偿，按**加权 RMS 半径**校准（决定糊度的是积分量而非极值）：
    // 螺旋核未补偿 RMS=0.9294，乘 0.98 后 0.9108，与旧规则核基准 0.91148 差 -0.07%，
    // 保持作者侧 blurRadius 观感口径。该 RMS 由核守卫断言锁定，改核会静默改变模糊
    // 强度，故必须在契约里同步。
    vec2 radiusStep = texelSize * clamp(blurRadius, 1.0, 128.0) * 0.98;

    // 面板几何必须先于采样计算：Liquid Glass 的透镜折射要偏置采样坐标。
    // 到"圆角矩形边界"的带符号距离（inigo-quirk 的 rounded-box SDF，约 10 ALU）。
    vec2 halfSize = max(panelSizePx * 0.5, vec2(1.0, 1.0));
    vec2 local = (panelUv - 0.5) * panelSizePx;
    // 半径夹到短半轴内：宿主不保证已 scaleToFit，半径超过短半轴时 halfSize-cornerR
    //  变负、SDF 几何失效乱贴亮边，故在 shader 侧兜底。
    float cornerR = min(cornerRadiusAt(cornerRadii, panelUv), min(halfSize.x, halfSize.y));
    vec2 q = abs(local) - (halfSize - vec2(cornerR));
    float signedDistance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - cornerR;
    float edgeDistance = max(-signedDistance, 0.0);

    // 按像素旋转整个采样盘：固定核在大半径下会让每个像素呈现同一套"星星点点"，
    // 叠加起来读作塑料感/蜡感。给每个像素一个确定性的盘旋转角，把结构化伪影打散成
    // 高频噪声（再被最后的抖噪掩盖）。关键是它只依赖 gl_FragCoord、不含时间项，
    // 因此静止画面不会闪烁——优于抖动偏移量或引入帧号相位。
    // 旧语义路径（kernelJitter=0）保持恒等基，升级前后逐像素一致。
    mat2 kernelBasis = mat2(1.0, 0.0, 0.0, 1.0);
    if (kernelJitter > 0.5) {
        // 偏移采样域再取 hash：与最终抖噪用的 hashNoise(gl_FragCoord.xy) 解耦，
        // 否则同一像素的旋转角与噪声值相关，会露出规则性花纹。
        float kernelAngle = hashNoise(gl_FragCoord.xy + vec2(37.0, 91.0)) * 6.28318530718;
        float ka = cos(kernelAngle);
        float kb = sin(kernelAngle);
        kernelBasis = mat2(ka, kb, -kb, ka);
    }

    // Liquid Glass 边缘折射：圆角矩形像一块有厚度的凸缘玻璃——靠近边缘的
    // 背景被"抽向轮廓外"再压缩进缘带，产生透镜感（官方 Liquid Glass 区别于
    // 经典磨砂的决定性特征）。做法：SDF 对位置的梯度就是外法线（等于"该点到
    // 内切矩形的方向"，无需求导数），把全部 13 个抽头的采样中心沿外法线推到
    // 轮廓外，越贴边推得越远；中心区梯度为零向量，天然不折射。
    // lensShift 是 UV 空间偏移：refraction 以纹理素计（宿主已把作者侧屏幕像素数
    // 除以 downsampleFactor），乘 texelSize 换算到 UV，与 radiusStep 同口径，
    // 屏幕观感不随快照缩放档位跳变。
    float lensBevel = 0.0;
    vec2 sdfGradient = vec2(0.0);
    vec2 lensShift = vec2(0.0);
    // 缘带宽度提到外层作用域：液态镜面环带的宽度要按它的比例取（见下方 border 段）。
    float lensBandPx = 0.0;
    if (liquidGlass > 0.5) {
        // 缘带宽度：比例 0.35 为主，上下限只兜极端。上限 28px 防大面板整块被当成边缘
        // （短边 164px 若按 0.85 比例会算出 70px 缘带，折射与厚度 tint 摊薄到全表面，
        // 观感退化回普通磨砂——Liquid Glass 的辨识度恰恰来自"只有边缘鼓"）。
        // 下限从 8px 降到 3px 是真机"液态看不出"的根因：聊天气泡短边仅 28px、半高 14，
        // 8px 下限把缘带撑到占满半高的 57%，bevel 在气泡内部几乎恒为 1 —— 而"整体一致
        // 位移"是不可见的（等价于平移采样坐标），梯度才是透镜本身。下限再小也必须留
        // 平坦中心，故另用 shortHalf*0.5 兜底：任何尺寸的面板中心区 bevel 必为 0。
        float lensShortHalf = min(halfSize.x, halfSize.y);
        lensBandPx = min(clamp(lensShortHalf * 0.35, 3.0, 28.0), lensShortHalf * 0.5);
        lensBevel = 1.0 - smoothstep(0.0, lensBandPx, edgeDistance);
        lensBevel = lensBevel * lensBevel;
        vec2 inner = clamp(local, -(halfSize - vec2(cornerR)), halfSize - vec2(cornerR));
        vec2 outward = local - inner;
        float outwardLength = length(outward);
        if (outwardLength > 0.001) {
            sdfGradient = outward / outwardLength;
            lensShift = sdfGradient * lensBevel * refraction * texelSize;
        }
    }

    vec4 blurred = texture2D(mainTex, texCoord + lensShift) * (161.0 / 1000.0);

    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-0.341, 0.312) * radiusStep) * (139.0 / 1000.0);
    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(0.057, -0.651) * radiusStep) * (120.0 / 1000.0);
    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(0.487, 0.635) * radiusStep) * (103.0 / 1000.0);
    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-0.910, -0.161) * radiusStep) * (89.0 / 1000.0);

    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(0.871, -0.554) * radiusStep) * (77.0 / 1000.0);
    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-0.294, 1.093) * radiusStep) * (66.0 / 1000.0);
    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-0.563, -1.084) * radiusStep) * (57.0 / 1000.0);
    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(1.227, 0.448) * radiusStep) * (49.0 / 1000.0);

    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-1.281, 0.529) * radiusStep) * (43.0 / 1000.0);
    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(0.619, -1.323) * radiusStep) * (37.0 / 1000.0);
    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(0.458, 1.462) * radiusStep) * (32.0 / 1000.0);
    blurred += texture2D(mainTex, texCoord + lensShift + kernelBasis * vec2(-1.384, -0.802) * radiusStep) * (27.0 / 1000.0);

    vec3 color;
    if (iosMaterial > 0.5) {
        // 材质分级顺序：vibrancy -> tint 蒙层 -> 亮度偏置。先做色彩校正再叠蒙层，
        // 才能既通透又有 iOS 那层"奶白"；反向会把 tint 一起饱和掉。
        color = applyVibrancy(blurred.rgb, vibrancy);
        // 白 tint 必须按背景亮度门控，否则暗背景必发灰：mix(c, 1, a) 把黑场从 0 抬到
        // a（本档 a=0.2），等于压掉 20% 动态范围——这就是"洗成脏灰"的数学本质，
        // 不是参数问题。深色 tint 往下压不伤黑场，无需门控。iOS 的真实做法是在暗背景
        // 上自动改用 dark material（trait 感知），这里用亮度门控近似同一行为：
        // 暗背景几乎不叠白（保持通透），亮背景照常吃奶白（保住文字可读性）。
        // 方向从 tint 自身亮度推出，零新增 uniform。
        float tintLuma = dot(materialTint.rgb, vec3(0.2126, 0.7152, 0.0722));
        float backdropLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float whiteGate = mix(1.0, smoothstep(0.05, 0.55, backdropLuma), step(0.5, tintLuma));
        // 厚度 tint：真实玻璃边缘更厚、吃色更多——edgeTint 沿缘带递增蒙层（经典档
        // edgeTint=0，逐项恒等）。但它必须同样受背景亮度门控：吸收型变暗只在亮背景上
        // 成立（光程长、吃掉得多），暗背景上再叠近黑 tint 只是把缘带糊成一条脏黑边。
        // 真机实测（2026-09-02 容器左缘）暗 23 个单位、亮边仅 2~4 个单位，用户反馈
        // "没有光泽、黑黑的"即此。玻璃缘的第一线索永远是镜面高光，吸收是次要线索。
        float thicknessGate = smoothstep(0.15, 0.55, backdropLuma);
        color = mix(color, materialTint.rgb,
                clamp((materialTint.a + edgeTint * lensBevel * thicknessGate) * whiteGate, 0.0, 1.0));
        // 亮度补偿同受门控：不门控的话 tint 不抬黑场、lift 却抬，灰底照样被洗白。
        color = color + materialLift * whiteGate;
    } else {
        color = applySaturation(blurred.rgb, saturation);
    }

    // ── 边缘亮边：两条路径形态不同，且这是刻意的 ──────────────────────────────
    // 经典档：iOS 导航栏那种 1.5px 发丝描边，亮边集中在顶缘、向两侧衰减
    //   （SDF 距离算带宽，圆角处准确，旧 min(到直边) 近似会把弧段亮边裁掉）。
    // 液态档：**峰值内移的高斯环带 + 对向次高光**，形态取自一手参考 WebGlass
    //   docs/specular.md + docs/tokens.md（--wg-specular-edge 0.05 / --wg-specular-width
    //   0.25 / --wg-specular-back 0.20，且明确"counter-highlight 恒锁在 light-angle+180°"）。
    float borderBand = 1.0 - smoothstep(0.0, 1.5, edgeDistance);
    float borderWeight = borderBand * mix(0.30, 1.0, 1.0 - clamp(panelUv.y, 0.0, 1.0));
    if (liquidGlass > 0.5) {
        // 真机反馈「边缘生硬」的根因：上一版液态档误用了经典档的形态——
        //   1 - smoothstep(0, 2px, d) 的**峰值正好压在物理轮廓上**，且只有 2px 宽。
        // 实测该处 1px 内亮度 42 -> 145（蓝通道直接 clip 到 255），读起来就是"沿轮廓
        // 画了一条白线"，而不是"玻璃在边缘鼓起来"。参考实现的两个机制恰好各自治一半：
        //   (a) specular-edge：把峰值**往里挪**，让轮廓线上不是最亮点 -> 消除描边感；
        //   (b) specular-width：环带取 bezel 的比例（默认 0.25）而不是固定 2px -> 同样的
        //       能量摊到更宽的肩部上，"软"来自分布而不是降低总亮度。
        // 高斯而不是 smoothstep：后者在带宽端点斜率为 0 但峰值仍在边缘，前者天然双侧肩部。
        float specBandPx = max(2.5, lensBandPx * 0.25);
        float specT = (edgeDistance - specBandPx * 0.35) / specBandPx;
        float specLobe = exp(-4.0 * specT * specT);
        // 随动缘光：MC 无陀螺仪，宿主以鼠标为光源（官方语义 lighting responds to
        // device motion）。pow 1.5 让光斑有方向但不缩成一点。
        float nDotL = dot(sdfGradient, lightDir);
        float primary = pow(max(nDotL, 0.0), 1.5);
        // 对向次高光：真实玻璃背光侧那条弱反光。缺了它，背光缘就只剩"死"和"暗"
        // （上一轮「黑黑的」有一半是这个）。强度按参考默认 0.20。
        float counter = 0.20 * pow(max(-nDotL, 0.0), 1.5);
        // 0.25 底光：非受光方位也保留一丝抛光感，避免某些角度整圈无光。
        borderWeight = specLobe * (0.25 + primary + counter);
    }

    // 内侧上缘柔光 + 内侧下缘暗带：镜面反射与厚度感的近似（pow3 让能量贴住边缘）。
    float topGlow = pow(1.0 - clamp(panelUv.y, 0.0, 1.0), 3.0) * innerLightTop;
    float bottomShade = pow(clamp(panelUv.y, 0.0, 1.0), 3.0) * innerShadowBottom;

    color = color + vec3(topGlow) - vec3(bottomShade) + vec3(borderWeight * edgeHighlight);

    // 抗 banding 噪点：必须是大半径模糊之后、任何缩放或 gamma 之前的最后一步加性
    // 叠加。8-bit 帧缓冲上平滑渐变必然出现量化色带。取 TPDF（两个独立均匀源之和，
    // 三角分布）：同峰值下对渐变的去带优于均匀噪声，Zed 的渐变 dither PR 即此法。
    // 第二路 hash 走转置+偏移域，防两路同源相关、退化回均匀分布。
    if (noiseAmount > 0.0) {
        float n = hashNoise(gl_FragCoord.xy)
                + hashNoise(gl_FragCoord.yx * 1.03 + vec2(7.0, 13.0)) - 1.0;
        color = clamp(color + n * noiseAmount, 0.0, 1.0);
    }

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), blurred.a);
}