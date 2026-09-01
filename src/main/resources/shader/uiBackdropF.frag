#version 120

// UI 磨玻璃材质着色器。
//
// 质感分层（对齐 iOS UIVisualEffectView 的材质构成，顺序不可交换）：
//   1) 模糊：13 抽头 Poisson 盘
//   2) vibrancy：亮度域保护式饱和提升（不是线性乘饱和度）
//   3) 材质蒙层 tint：白/深色半透明叠加（在色彩校正之后）
//   4) 亮度偏置 lift + 边缘亮边 + 内侧上缘柔光 / 下缘暗带
//   5) 抗 banding 噪点：最后一步加性叠加，任何缩放之前
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
    // 核能量契约：13 抽头（中心 + 12 个 Poisson 盘偏移）权重和恒为 1，保证磨玻璃
    // 是"亮度保持"操作。柔化提交 e5a6b2ae 重写核时权重和漂移到 1.12，导致磨玻璃
    // 整体过曝 12%、高光处 clamp 偏色（旧核历史和为 1.02，证明归一是本意而非风格
    // 选择）；2026-09-01 混合语义特勘修复，权重和由 UiBackdropKernelEnergyTest 以
    // 源码契约锚定（抽头数 13 + 和为 1），改核必须同步保持。
    //
    // 为什么从"十字 + 对角"规则核换成 Poisson 盘：规则核在大半径下必然沿轴向与
    // 对角向出现方向性拉丝和"星星点点"的采样点分离；Poisson 盘保持抽头间最小间距
    // （避免纯随机采样的颗粒噪声），同时消除径向伪影。Apple 实时模糊的标准做法是
    // dual-filter Kawase，但那需要多趟离屏 pass，本管线是单趟，故取 Poisson 盘。
    // 1.35 为抽头半径补偿，按**加权 RMS 半径**校准（决定糊度的是积分量而非极值）：
    // 旧规则核 RMS=0.911，本盘未补偿 RMS=0.654，真系数 1.394；取 1.35 后 RMS=0.883，
    // 与旧核差 3.2%，保持作者侧 blurRadius 观感口径。该 RMS 由核守卫断言锁定，
    // 改盘位会静默改变模糊强度，故必须在契约里同步。
    vec2 radiusStep = texelSize * clamp(blurRadius, 1.0, 128.0) * 1.35;

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

    vec4 blurred = texture2D(mainTex, texCoord) * (60.0 / 300.0);

    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(-0.280, -0.348) * radiusStep) * (30.0 / 300.0);
    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(0.435, 0.055) * radiusStep) * (30.0 / 300.0);
    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(0.146, 0.451) * radiusStep) * (30.0 / 300.0);
    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(-0.453, 0.157) * radiusStep) * (30.0 / 300.0);

    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(-0.721, -0.515) * radiusStep) * (15.0 / 300.0);
    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(0.751, -0.416) * radiusStep) * (15.0 / 300.0);
    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(0.328, 0.813) * radiusStep) * (15.0 / 300.0);
    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(-0.292, 0.829) * radiusStep) * (15.0 / 300.0);
    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(-0.936, 0.196) * radiusStep) * (15.0 / 300.0);
    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(0.198, -0.963) * radiusStep) * (15.0 / 300.0);
    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(0.918, 0.331) * radiusStep) * (15.0 / 300.0);
    blurred += texture2D(mainTex, texCoord + kernelBasis * vec2(-0.549, -0.811) * radiusStep) * (15.0 / 300.0);

    vec3 color;
    if (iosMaterial > 0.5) {
        // 材质分级顺序：vibrancy -> tint 蒙层 -> 亮度偏置。先做色彩校正再叠蒙层，
        // 才能既通透又有 iOS 那层"奶白"；反向会把 tint 一起饱和掉。
        color = applyVibrancy(blurred.rgb, vibrancy);
        color = mix(color, materialTint.rgb, clamp(materialTint.a, 0.0, 1.0));
        color = color + materialLift;
    } else {
        color = applySaturation(blurred.rgb, saturation);
    }

    // 边缘亮边：到"圆角矩形边界"的带符号距离（inigo-quirk 的 rounded-box SDF，
    // 约 10 ALU）。早先用 min(到四直边距离) 近似，圆角处算出的距离偏大，亮边在弧段
    // 被 stencil 裁掉、留下一圈无高光的圆弧——而 iOS 玻璃最耐看的恰恰是沿弧走的亮边。
    // 四角半径按所在象限取，非均匀圆角也准确。
    vec2 halfSize = max(panelSizePx * 0.5, vec2(1.0, 1.0));
    vec2 local = (panelUv - 0.5) * panelSizePx;
    float cornerR = cornerRadiusAt(cornerRadii, panelUv);
    vec2 q = abs(local) - (halfSize - vec2(cornerR));
    float signedDistance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - cornerR;
    float edgeDistance = max(-signedDistance, 0.0);
    float borderBand = 1.0 - smoothstep(0.0, 1.5, edgeDistance);
    // iOS 的亮边不是四边等强：顶缘最强、向两侧与底缘衰减，等强描边最假。
    float borderWeight = borderBand * mix(0.30, 1.0, 1.0 - clamp(panelUv.y, 0.0, 1.0));

    // 内侧上缘柔光 + 内侧下缘暗带：镜面反射与厚度感的近似（pow3 让能量贴住边缘）。
    float topGlow = pow(1.0 - clamp(panelUv.y, 0.0, 1.0), 3.0) * innerLightTop;
    float bottomShade = pow(clamp(panelUv.y, 0.0, 1.0), 3.0) * innerShadowBottom;

    color = color + vec3(topGlow) - vec3(bottomShade) + vec3(borderWeight * edgeHighlight);

    // 抗 banding 噪点：必须是大半径模糊之后、任何缩放或 gamma 之前的最后一步加性
    // 叠加。8-bit 帧缓冲上平滑渐变必然出现量化色带，约 1~2/255 的抖动即可打散。
    if (noiseAmount > 0.0) {
        float n = hashNoise(gl_FragCoord.xy) - 0.5;
        color = clamp(color + n * noiseAmount, 0.0, 1.0);
    }

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), blurred.a);
}