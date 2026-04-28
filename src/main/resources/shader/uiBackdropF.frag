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
    vec2 radiusStep = texelSize * clamp(blurRadius, 1.0, 48.0);
    vec4 blurred = texture2D(mainTex, texCoord) * 0.18;

    blurred += texture2D(mainTex, texCoord + vec2(-1.0, 0.0) * radiusStep) * 0.11;
    blurred += texture2D(mainTex, texCoord + vec2(1.0, 0.0) * radiusStep) * 0.11;
    blurred += texture2D(mainTex, texCoord + vec2(0.0, -1.0) * radiusStep) * 0.11;
    blurred += texture2D(mainTex, texCoord + vec2(0.0, 1.0) * radiusStep) * 0.11;

    blurred += texture2D(mainTex, texCoord + vec2(-0.85, -0.85) * radiusStep) * 0.07;
    blurred += texture2D(mainTex, texCoord + vec2(0.85, -0.85) * radiusStep) * 0.07;
    blurred += texture2D(mainTex, texCoord + vec2(-0.85, 0.85) * radiusStep) * 0.07;
    blurred += texture2D(mainTex, texCoord + vec2(0.85, 0.85) * radiusStep) * 0.07;

    blurred += texture2D(mainTex, texCoord + vec2(-1.75, 0.0) * radiusStep) * 0.03;
    blurred += texture2D(mainTex, texCoord + vec2(1.75, 0.0) * radiusStep) * 0.03;
    blurred += texture2D(mainTex, texCoord + vec2(0.0, -1.75) * radiusStep) * 0.03;
    blurred += texture2D(mainTex, texCoord + vec2(0.0, 1.75) * radiusStep) * 0.03;

    gl_FragColor = vec4(applySaturation(blurred.rgb, saturation), blurred.a);
}
