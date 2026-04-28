#version 120

varying vec2 texCoord;

uniform sampler2D mainTex;
uniform vec2 texelSize;
uniform float blurRadius;
uniform float saturation;
uniform float lodBias;

vec3 applySaturation(vec3 color, float amount) {
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 gray = vec3(luma);
    return clamp(gray + (color - gray) * amount, 0.0, 1.0);
}

void main(void) {
    vec2 radiusStep = texelSize * clamp(blurRadius, 1.0, 56.0);
    vec4 blurred = texture2D(mainTex, texCoord, lodBias) * 0.24;

    blurred += texture2D(mainTex, texCoord + vec2(-0.65, 0.0) * radiusStep, lodBias) * 0.10;
    blurred += texture2D(mainTex, texCoord + vec2(0.65, 0.0) * radiusStep, lodBias) * 0.10;
    blurred += texture2D(mainTex, texCoord + vec2(0.0, -0.65) * radiusStep, lodBias) * 0.10;
    blurred += texture2D(mainTex, texCoord + vec2(0.0, 0.65) * radiusStep, lodBias) * 0.10;

    blurred += texture2D(mainTex, texCoord + vec2(-1.25, 0.0) * radiusStep, lodBias) * 0.07;
    blurred += texture2D(mainTex, texCoord + vec2(1.25, 0.0) * radiusStep, lodBias) * 0.07;
    blurred += texture2D(mainTex, texCoord + vec2(0.0, -1.25) * radiusStep, lodBias) * 0.07;
    blurred += texture2D(mainTex, texCoord + vec2(0.0, 1.25) * radiusStep, lodBias) * 0.07;

    blurred += texture2D(mainTex, texCoord + vec2(-0.9, -0.9) * radiusStep, lodBias) * 0.05;
    blurred += texture2D(mainTex, texCoord + vec2(0.9, -0.9) * radiusStep, lodBias) * 0.05;
    blurred += texture2D(mainTex, texCoord + vec2(-0.9, 0.9) * radiusStep, lodBias) * 0.05;
    blurred += texture2D(mainTex, texCoord + vec2(0.9, 0.9) * radiusStep, lodBias) * 0.05;

    gl_FragColor = vec4(applySaturation(blurred.rgb, saturation), blurred.a);
}
