#version 120

varying vec2 texCoord;
varying vec4 Color;


uniform sampler2D mainTex;
uniform sampler2D maskTex;
uniform vec4 uvBounds = vec4(0.0, 0.0, 1.0, 1.0);
uniform vec2 textureSize = vec2(2048.0, 2048.0);
uniform vec2 smoothRange = vec2(0.0, 1.0);
uniform float sigma = 3.14;
uniform float blurRadius = 1.0;
uniform int sampleRadius = 1;
uniform float colorGain = 0.0;
uniform float alphaGain = 0.0;

const float PI = 3.14159265359;

const float INV_SQRT_2PI = 0.3989422804014327;
float sigmaSquared;

vec4 safeSampler(sampler2D tex, vec2 uv) {
    if (uv.x < uvBounds.x || uv.x > uvBounds.z || uv.y < uvBounds.y || uv.y > uvBounds.w) {
        return vec4(0.0);
    }
    return texture2D(tex, uv);
}


float gaussianWeight2D_Optimized(vec2 offset) {

    float exponent = -(offset.x * offset.x + offset.y * offset.y) / (2.0 * sigmaSquared);

    return exp(exponent);
}

vec4 gaussianBlur(sampler2D tex, vec2 uv, vec2 texelSize) {
    float totalWeight = 0.0;
    vec4 accumulatedColor = vec4(0.0);

    float stepScale = blurRadius;
    vec2 baseStep = texelSize * stepScale;

    for (int i = -sampleRadius; i <= sampleRadius; ++i) {
        for (int j = -sampleRadius; j <= sampleRadius; ++j) {

            vec2 offsetUV = vec2(float(i), float(j)) * baseStep;
            vec2 sampleUV = uv + offsetUV;

            vec2 offsetNormed = vec2(float(i), float(j)) * stepScale;

            float weight = gaussianWeight2D_Optimized(offsetNormed);

            vec4 sampleColor = safeSampler(tex, sampleUV);

            accumulatedColor += sampleColor * weight;
            totalWeight += weight;
        }
    }

    if (totalWeight > 0.0) {
        accumulatedColor /= totalWeight;
    }

    return accumulatedColor;
}

// *** RGB 到 HSV 转换函数 ***
// r, g, b values are in [0, 1]
// h, s, v values are in [0, 1]
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

// *** HSV 到 RGB 转换函数 ***
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    sigmaSquared = sigma * sigma;

    vec2 texelSize = 1.0 / textureSize;
    // 对主纹理进行高斯模糊采样
    vec4 sampleColor = gaussianBlur(mainTex, texCoord, texelSize);

    // 对遮罩纹理进行采样
    vec4 maskColor = safeSampler(maskTex, texCoord);

    // 对主采样的alpha进行平滑处理
    float smoothedAlpha = smoothstep(smoothRange.x, smoothRange.y, sampleColor.a);

    vec3 finalRGB;
    float finalAlpha;

    // 检查主采样的alpha是否小于遮罩纹理的alpha
    if (smoothedAlpha < maskColor.a) {
        // 当主采样的alpha小于遮罩纹理时，最终颜色替换为Color并使用mask的alpha
        finalRGB = vec3(1);
        finalAlpha = maskColor.a;
    } else {
        // 否则，使用高斯模糊后的颜色和alpha
        finalRGB = sampleColor.rgb;
        finalAlpha = smoothedAlpha;
    }

    // 应用 alpha 增益
    if (finalAlpha != 0.0) {
        // 确保 alpha 不超过 1.0
        finalAlpha = min(finalAlpha + alphaGain, 1.0);
    }

    // *** 亮度增强逻辑 ***

    // 1. 转换到 HSV 色彩空间
    vec3 hsvColor = rgb2hsv(finalRGB);

    // 2. 增强 V (Value/亮度) 分量
    // colorGain 视为一个乘数（1.0 + colorGain），并应用到 V
    float brightnessMultiplier = 1.0 + colorGain;
    hsvColor.z = clamp(hsvColor.z * brightnessMultiplier, 0.0, 1.0);

    // 3. 转换回 RGB
    // 注意：这里也乘上了 Color.rgb
    vec3 processedRGB = hsv2rgb(hsvColor) * Color.rgb;

    gl_FragColor = vec4(processedRGB, finalAlpha);
}