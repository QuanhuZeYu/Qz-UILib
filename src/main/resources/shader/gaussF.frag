#version 120

// 从顶点着色器接收的纹理坐标
varying vec2 v_texCoord;

// 输入纹理 (待模糊的图像)
uniform sampler2D image;

// 模糊参数 (由 Java 代码通过 ShaderManager::setUniformf 设置)
uniform vec2 direction;          // 模糊方向 (1, 0) 或 (0, 1)
uniform float radius;            // 模糊半径 (用于决定采样数量)
uniform vec2 targetResolution;   // FBO尺寸 (Width, Height)

// 预定义的高斯权重 (通常是 9 个采样点)
// 这是一个优化的内核，旨在提供良好的性能和效果。
const float weights[9] = float[](
0.05, 0.09, 0.12, 0.15, 0.16, 0.15, 0.12, 0.09, 0.05
);
const int KERNEL_SIZE = 9;

void main(void) {
    // 1. 计算纹理坐标的步长 (texel size)
    // 步长是 1.0 / 目标分辨率。
    vec2 texelSize = 1.0 / targetResolution;

    // 2. 采样偏移量
    // 沿 direction 方向移动一个像素步长。
    // 例如，水平模糊时：offset = (1.0 / width, 0.0)
    vec2 offset = direction * texelSize;

    // 3. 初始化累积颜色
    vec4 finalColor = texture2D(image, v_texCoord) * weights[4]; // 中心点权重最大

    // 4. 循环采样 (Ping-Pong 优化)
    // 从 KERNEL_SIZE / 2 处开始，即从中心点左右/上下偏移。
    // KERNEL_SIZE=9 时，中心点是第 4 个 (索引 4)，左右/上下分别有 4 个点。
    for (int i = 1; i < (KERNEL_SIZE / 2) + 1; i++) {
        // 4.1 采样正向偏移点 (+i)
        vec2 sampleTexCoordP = v_texCoord + offset * float(i);
        vec4 sampleColorP = texture2D(image, sampleTexCoordP);

        // 4.2 采样负向偏移点 (-i)
        vec2 sampleTexCoordN = v_texCoord - offset * float(i);
        vec4 sampleColorN = texture2D(image, sampleTexCoordN);

        // 4.3 累加颜色 (正负点使用相同的权重)
        // 注意：这里使用索引 i+4 和 4-i 也可以，但由于权重是对称的，直接使用 i+4 即可简化
        float weight = weights[4 + i];
        finalColor += sampleColorP * weight;
        finalColor += sampleColorN * weight;
    }

    // 5. 输出最终颜色
    gl_FragColor = finalColor;
}