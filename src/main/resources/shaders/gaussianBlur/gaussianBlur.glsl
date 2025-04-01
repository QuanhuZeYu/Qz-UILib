#version 330
out vec4 FragColor;
in vec2 texCoords;
uniform sampler2D image;

uniform bool horizontal;
uniform float sigma;       // 控制模糊程度

const int maxSamples = 32;

void main() {
    vec2 texSize = textureSize(image, 0);
    vec2 tex_offset = 1.0 / texSize;

    // 根据sigma计算实际采样半径
    int radius = int(ceil(sigma * 2.5));
    radius = min(radius, maxSamples);

    vec3 result = texture(image, texCoords).rgb;
    float totalWeight = 1.0; // 中心权重

    for(int i = 1; i <= radius; ++i) {
        float weight = exp(-(i*i)/(2.0*sigma*sigma));

        if(horizontal) {
            result += texture(image, texCoords + vec2(tex_offset.x * i, 0.0)).rgb * weight;
            result += texture(image, texCoords - vec2(tex_offset.x * i, 0.0)).rgb * weight;
        } else {
            result += texture(image, texCoords + vec2(0.0, tex_offset.y * i)).rgb * weight;
            result += texture(image, texCoords - vec2(0.0, tex_offset.y * i)).rgb * weight;
        }
        totalWeight += 2.0 * weight;
    }

    FragColor = vec4(result / totalWeight, 1.0);
}