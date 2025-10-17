#version 120

varying vec2 texCoord;
varying vec4 Color;
varying vec4 uvBounds;


uniform sampler2D mainTex;
uniform vec2 textureSize = vec2(2048.0, 2048.0);
uniform vec2 smoothRange = vec2(0.0, 0.7);
uniform float colorGain = 0.0;
uniform float shrink = 1;

const float PI = 3.14159265359;

const float INV_SQRT_2PI = 0.3989422804014327;
float sigmaSquared;

vec4 safeSampler(sampler2D tex, vec2 uv) {
    if (uv.x < uvBounds.x || uv.x > uvBounds.z || uv.y < uvBounds.y || uv.y > uvBounds.w) {
        return vec4(0.0);
    }
    return texture2D(tex, uv);
}

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec4 mainColor = safeSampler(mainTex, texCoord);

    mainColor.rgb = rgb2hsv(mainColor.rgb);
    mainColor.b *= colorGain;
    mainColor.b = clamp(mainColor.b, 0.0, 1.0);
    mainColor.rgb = hsv2rgb(mainColor.rgb) * Color.rgb;
    mainColor.a = smoothstep(smoothRange.x, smoothRange.y, mainColor.a);

    gl_FragColor = mainColor;
}