#version 120

// 从顶点着色器接收的变量
varying vec2 v_texCoord;
varying vec4 v_color;

// 输入纹理
uniform sampler2D image;

// 模糊参数
// 模糊方向 (例如，水平(1.0, 0.0) 或 垂直(0.0, 1.0))
uniform vec2 direction;
// 模糊半径。这直接控制模糊的扩散范围。建议值为 1.0 ~ 15.0。
uniform float radius;
// FBO 尺寸 (Width, Height)
uniform vec2 targetResolution;

// 高斯函数: G(x) = e^(-(x^2) / (2 * sigma^2))
// 我们将使用 radius 作为 sigma (标准差)，这是一种常见的简化，效果很好。
// 一个更大的 radius (sigma) 会产生一个更平坦的曲线，意味着模糊范围更广。

void main(void) {
    // 1. 计算单个纹理像素的大小 (texel size)
    vec2 texelSize = 1.0 / targetResolution;

    // 2. 计算基于方向的单步偏移量
    vec2 offset = direction * texelSize;

    // 3. 初始化累加器
    vec4 finalColor = vec4(0.0);
    float totalWeight = 0.0;

    // 我们将 radius 直接用作高斯函数的 sigma (标准差)。
    // 这提供了对模糊 "锐利度" 的直观控制。
    float sigma = radius;
    // 预先计算 sigma 的平方
    float sigmaSq = sigma * sigma;

    // 4. 动态采样循环
    // 循环的次数由 radius 决定，确保采样范围足够覆盖高斯曲线的主要部分。
    // 我们从中心点向两侧进行采样，循环次数为 radius 的取整数。
    // 例如, radius = 4.5, 我们将采样 -4, -3, -2, -1, 0, 1, 2, 3, 4 这几个点。
    int kernelRadius = int(ceil(radius));

    // 首先处理中心点 (偏移量为 0)
    // 权重计算: exp( -(0^2) / (2*sigma^2) ) = exp(0) = 1.0
    float centerWeight = 1.0;
    finalColor += texture2D(image, v_texCoord) * centerWeight;
    totalWeight += centerWeight;

    // 循环处理中心点两侧的对称点
    for (int i = 1; i <= kernelRadius; i++) {
        float fi = float(i);

        // 4.1. 计算当前偏移量下的高斯权重
        // 这是高斯函数的简化形式，忽略了常数系数，因为我们最后会归一化。
        float weight = exp(-(fi * fi) / (2.0 * sigmaSq));

        // 4.2. 采样正向和负向偏移点
        vec4 sampleColorP = texture2D(image, v_texCoord + offset * fi);
        vec4 sampleColorN = texture2D(image, v_texCoord - offset * fi);

        // 4.3. 累加颜色和权重
        finalColor += sampleColorP * weight;
        finalColor += sampleColorN * weight;
        // 因为正负两个点使用了相同的权重
        totalWeight += weight * 2.0;
    }

    // 5. 归一化最终颜色 (这是最关键的一步)
    // 将累加的颜色除以累加的总权重，确保最终颜色的总能量(亮度)守恒。
    // 无论采样多少点，无论权重如何，这一步都能保证图像不会变暗或变亮。
    gl_FragColor = (finalColor / totalWeight) * v_color;
    // gl_FragColor = vec4(1);
}