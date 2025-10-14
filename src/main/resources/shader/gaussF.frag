#version 120

varying vec2 v_texCoord;
varying vec4 v_color;

uniform sampler2D image;

uniform vec2 direction;
uniform float radius;
uniform vec2 targetResolution;

// --- 新增的 Uniform ---
// 用于控制归一化分母。
// 较小的值（例如 1.0 到 3.0）将保持较高的 Alpha 强度（发光/膨胀）。
// 较大的值（例如 5.0 到 10.0）将使 Alpha 值更接近标准高斯模糊（稀释）。
uniform float intensityDivisor;


void main(void) {
    vec2 texelSize = 1.0 / targetResolution;
    vec2 offset = direction * texelSize;

    float finalAlpha = 0.0;
    // totalWeight 仍计算，但在此方法中不用于分母
    // float totalWeight = 0.0;

    float sigma = radius;
    float sigmaSq = sigma * sigma;

    int kernelRadius = int(ceil(radius));

    // --- 累加步骤与原始代码相同 ---

    // 中心点采样
    float centerAlpha = texture2D(image, v_texCoord).a;
    float centerWeight = 1.0;
    finalAlpha += centerAlpha * centerWeight;
    // totalWeight += centerWeight;

    // 周围点采样
    for (int i = 1; i <= kernelRadius; i++) {
        float fi = float(i);
        float weight = exp(-(fi * fi) / (2.0 * sigmaSq));

        float sampleAlphaP = texture2D(image, v_texCoord + offset * fi).a;
        float sampleAlphaN = texture2D(image, v_texCoord - offset * fi).a;

        finalAlpha += sampleAlphaP * weight;
        finalAlpha += sampleAlphaN * weight;
        // totalWeight += weight * 2.0;
    }

    // --- 关键改进：使用 uniform 动态分母 ---

    // 确保分母至少为 1.0，防止除以零或产生不稳定的效果。
    // 这将归一化分母的控制权交给了 CPU 端，允许实时调整。
    float divisor = max(1.0, intensityDivisor);

    // 归一化，得到最终的模糊alpha值，并钳制
    float blurredAlpha = clamp(finalAlpha / divisor, 0.0, 1.0);

    // --- 输出最终颜色 ---
    gl_FragColor = vec4(v_color.rgb, blurredAlpha * v_color.a);
}