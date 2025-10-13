#version 120

varying vec2 v_texCoord;
varying vec4 v_color;

uniform sampler2D image;

uniform vec2 direction;
uniform float radius;
uniform vec2 targetResolution;


void main(void) {
    vec2 texelSize = 1.0 / targetResolution;

    vec2 offset = direction * texelSize;

    vec4 finalColor = vec4(0.0);
    float totalWeight = 0.0;

    float sigma = radius;
    float sigmaSq = sigma * sigma;

    int kernelRadius = int(ceil(radius));

    float centerWeight = 1.0;
    finalColor += texture2D(image, v_texCoord) * centerWeight;
    totalWeight += centerWeight;

    for (int i = 1; i <= kernelRadius; i++) {
        float fi = float(i);

        float weight = exp(-(fi * fi) / (2.0 * sigmaSq));

        vec4 sampleColorP = texture2D(image, v_texCoord + offset * fi);
        vec4 sampleColorN = texture2D(image, v_texCoord - offset * fi);

        finalColor += sampleColorP * weight;
        finalColor += sampleColorN * weight;
        totalWeight += weight * 2.0;
    }

    gl_FragColor = (finalColor / totalWeight) * v_color;
}