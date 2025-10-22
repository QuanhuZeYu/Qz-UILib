#version 120

varying vec2 texCoord;
varying vec4 Color;
varying vec4 uvBounds;


uniform sampler2D mainTex;
uniform vec2 textureSize = vec2(2048.0, 2048.0);
uniform vec2 smoothRange = vec2(0.0, 0.7);
uniform float colorGain = 0.0;
uniform int aaMode = 2;
uniform float aaStrength = 12.0/120.0;

float sigmaSquared;

float calculateWeight(float du, float dv) {
    float distSquared = sqrt(du * du + dv * dv);
    float wt = exp(-distSquared / 6.0);
    return wt;
}

vec4 safeSamplerAlpha(sampler2D tex, vec2 uv, float du, float dv, float factorU, float factorV, float weight) {
    float finalU = uv.x + factorU * du;
    float finalV = uv.y + factorV * dv;
    if (finalU < uvBounds.x || finalU > uvBounds.z || finalV < uvBounds.y || finalV > uvBounds.w) {
        return vec4(0.0);
    }
    return weight * texture2D(mainTex, vec2(finalU, finalV));
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
    vec4 finalColor = Color;
    if (texCoord.s != 0.0 || texCoord.t != 0.0) {
        vec4 res = vec4(0.0);

        float fu = aaStrength * fwidth(texCoord.x);
        float fv = aaStrength * fwidth(texCoord.y);

        float totalWt = 0.0;

        if (aaMode == 1) {
            float wt = calculateWeight( 2.0,  6.0);
            res += safeSamplerAlpha(mainTex, texCoord, 2.0, 6.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight( 6.0, -2.0);
            res += safeSamplerAlpha(mainTex, texCoord, 6.0, -2.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight(-2.0, -6.0);
            res += safeSamplerAlpha(mainTex, texCoord, -2.0, -6.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight(-6.0,  2.0);
            res += safeSamplerAlpha(mainTex, texCoord, -6.0, 2.0, fu, fv, wt);
            totalWt += wt;
        }
        else {
            float wt = calculateWeight( 1.0,  1.0);
            res += safeSamplerAlpha(mainTex, texCoord, 1.0, 1.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight(-1.0, -3.0);
            res += safeSamplerAlpha(mainTex, texCoord, -1.0, -3.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight(-3.0,  2.0);
            res += safeSamplerAlpha(mainTex, texCoord, -3.0, 2.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight( 4.0, -1.0);
            res += safeSamplerAlpha(mainTex, texCoord, 4.0, -1.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight(-5.0, -2.0);
            res += safeSamplerAlpha(mainTex, texCoord, -5.0, -2.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight( 2.0,  5.0);
            res += safeSamplerAlpha(mainTex, texCoord, 2.0, 5.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight( 5.0,  3.0);
            res += safeSamplerAlpha(mainTex, texCoord, 5.0, 3.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight( 3.0, -5.0);
            res += safeSamplerAlpha(mainTex, texCoord, 3.0, -5.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight(-2.0,  6.0);
            res += safeSamplerAlpha(mainTex, texCoord, -2.0, 6.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight( 0.0, -7.0);
            res += safeSamplerAlpha(mainTex, texCoord, 0.0, -7.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight(-4.0, -6.0);
            res += safeSamplerAlpha(mainTex, texCoord, -4.0, -6.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight(-6.0,  4.0);
            res += safeSamplerAlpha(mainTex, texCoord, -6.0, 4.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight(-8.0,  0.0);
            res += safeSamplerAlpha(mainTex, texCoord, -8.0, 0.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight( 7.0, -4.0);
            res += safeSamplerAlpha(mainTex, texCoord, 7.0, -4.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight( 6.0,  7.0);
            res += safeSamplerAlpha(mainTex, texCoord, 6.0, 7.0, fu, fv, wt);
            totalWt += wt;
            wt = calculateWeight(-7.0, -8.0);
            res += safeSamplerAlpha(mainTex, texCoord, -7.0, -8.0, fu, fv, wt);
            totalWt += wt;
        }

        if (totalWt > 0.0) {
            res /= totalWt;
        }
        else {
            res = vec4(0.0);
        }
        finalColor = res;
    }

    finalColor.rgb = rgb2hsv(finalColor.rgb);
    finalColor.b *= colorGain;
    finalColor.b = clamp(finalColor.b, 0.0, 1.0);

    finalColor.rgb = hsv2rgb(finalColor.rgb) * Color.rgb;

    finalColor.a = smoothstep(smoothRange.x, smoothRange.y, finalColor.a);

    gl_FragColor = finalColor;
}