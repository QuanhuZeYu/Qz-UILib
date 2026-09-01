#version 120

varying vec2 texCoord;

uniform sampler2D mainTex;
uniform vec2 texelSize;
uniform float blurRadius;
uniform float saturation;

vec3 applySaturation(vec3 color, float amount) {
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 gray = vec3(luma);
    return clamp(gray + (color - gray) * amount, 0.0, 1.0);
}

void main(void) {
    // 核能量守恒：13 抽头按高斯形状权重 24/10/7/5 除以总和 112，权重和恒为 1，
    // 保证磨玻璃维持背后画面的平均亮度与色彩。柔化提交 e5a6b2ae 重写核时权重和
    // 漂移到 1.12，导致磨玻璃整体过曝 12%、高光处 clamp 偏色（旧核历史和为 1.02，
    // 证明归一是本意而非风格选择）。2026-09-01 混合语义特勘修复；
    // 权重和由 UiBackdropKernelEnergyTest 源码契约锚定，改核必须保持和为 1。
    vec2 radiusStep = texelSize * clamp(blurRadius, 1.0, 128.0);
    vec4 blurred = texture2D(mainTex, texCoord) * (24.0 / 112.0);

    blurred += texture2D(mainTex, texCoord + vec2(-0.65, 0.0) * radiusStep) * (10.0 / 112.0);
    blurred += texture2D(mainTex, texCoord + vec2(0.65, 0.0) * radiusStep) * (10.0 / 112.0);
    blurred += texture2D(mainTex, texCoord + vec2(0.0, -0.65) * radiusStep) * (10.0 / 112.0);
    blurred += texture2D(mainTex, texCoord + vec2(0.0, 0.65) * radiusStep) * (10.0 / 112.0);

    blurred += texture2D(mainTex, texCoord + vec2(-1.25, 0.0) * radiusStep) * (7.0 / 112.0);
    blurred += texture2D(mainTex, texCoord + vec2(1.25, 0.0) * radiusStep) * (7.0 / 112.0);
    blurred += texture2D(mainTex, texCoord + vec2(0.0, -1.25) * radiusStep) * (7.0 / 112.0);
    blurred += texture2D(mainTex, texCoord + vec2(0.0, 1.25) * radiusStep) * (7.0 / 112.0);

    blurred += texture2D(mainTex, texCoord + vec2(-0.9, -0.9) * radiusStep) * (5.0 / 112.0);
    blurred += texture2D(mainTex, texCoord + vec2(0.9, -0.9) * radiusStep) * (5.0 / 112.0);
    blurred += texture2D(mainTex, texCoord + vec2(-0.9, 0.9) * radiusStep) * (5.0 / 112.0);
    blurred += texture2D(mainTex, texCoord + vec2(0.9, 0.9) * radiusStep) * (5.0 / 112.0);

    gl_FragColor = vec4(applySaturation(blurred.rgb, saturation), blurred.a);
}
