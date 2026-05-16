#version 120

varying vec2 texCoord;
varying vec4 Color;
varying vec4 uvBounds;
// 0.0 mono glyph, 1.0 colored glyph, 2.0 solid decoration quad.
varying float glyphFlags;

uniform sampler2D mainTex;
uniform vec2 smoothRange;
uniform float brightnessGain;
uniform int aaMode;
uniform float aaStrength;

float calculateWeight(float du, float dv) {
    float distSquared = sqrt(du * du + dv * dv);
    return exp(-distSquared / 6.0);
}

vec4 safeSample(vec2 uv, float du, float dv, float factorU, float factorV, float weight) {
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
    if (glyphFlags > 1.5) {
        gl_FragColor = Color;
        return;
    }

    if (texCoord.x < uvBounds.x || texCoord.x > uvBounds.z || texCoord.y < uvBounds.y || texCoord.y > uvBounds.w) {
        gl_FragColor = vec4(0.0);
        return;
    }

    float fu = aaStrength * fwidth(texCoord.x);
    float fv = aaStrength * fwidth(texCoord.y);
    vec4 tex = vec4(0.0);
    float totalWeight = 0.0;

    float wt = calculateWeight(0.0, 0.0);
    tex += safeSample(texCoord, 0.0, 0.0, fu, fv, wt);
    totalWeight += wt;

    if (aaMode == 1) {
        wt = calculateWeight(1.0, 1.0);
        tex += safeSample(texCoord, 1.0, 1.0, fu, fv, wt);
        totalWeight += wt;

        wt = calculateWeight(-1.0, -1.0);
        tex += safeSample(texCoord, -1.0, -1.0, fu, fv, wt);
        totalWeight += wt;

        wt = calculateWeight(1.0, -1.0);
        tex += safeSample(texCoord, 1.0, -1.0, fu, fv, wt);
        totalWeight += wt;

        wt = calculateWeight(-1.0, 1.0);
        tex += safeSample(texCoord, -1.0, 1.0, fu, fv, wt);
        totalWeight += wt;
    } else {
        wt = calculateWeight(1.0, 1.0);
        tex += safeSample(texCoord, 1.0, 1.0, fu, fv, wt);
        totalWeight += wt;

        wt = calculateWeight(-1.0, -1.0);
        tex += safeSample(texCoord, -1.0, -1.0, fu, fv, wt);
        totalWeight += wt;

        wt = calculateWeight(1.0, -1.0);
        tex += safeSample(texCoord, 1.0, -1.0, fu, fv, wt);
        totalWeight += wt;

        wt = calculateWeight(-1.0, 1.0);
        tex += safeSample(texCoord, -1.0, 1.0, fu, fv, wt);
        totalWeight += wt;

        wt = calculateWeight(2.0, 0.0);
        tex += safeSample(texCoord, 2.0, 0.0, fu, fv, wt);
        totalWeight += wt;

        wt = calculateWeight(-2.0, 0.0);
        tex += safeSample(texCoord, -2.0, 0.0, fu, fv, wt);
        totalWeight += wt;

        wt = calculateWeight(0.0, 2.0);
        tex += safeSample(texCoord, 0.0, 2.0, fu, fv, wt);
        totalWeight += wt;

        wt = calculateWeight(0.0, -2.0);
        tex += safeSample(texCoord, 0.0, -2.0, fu, fv, wt);
        totalWeight += wt;
    }

    if (totalWeight > 0.0) {
        tex /= totalWeight;
    }

    vec4 finalColor;
    if (glyphFlags > 0.5) {
        finalColor = vec4(tex.rgb, tex.a * Color.a);
    } else {
        finalColor = vec4(Color.rgb, tex.a * Color.a);
        finalColor.rgb = rgb2hsv(finalColor.rgb);
        float originalValue = finalColor.b;
        float brightnessWeight = smoothstep(0.35, 0.95, originalValue);
        finalColor.b = mix(originalValue, originalValue * brightnessGain, brightnessWeight);
        finalColor.b = clamp(finalColor.b, 0.0, 1.0);
        finalColor.rgb = hsv2rgb(finalColor.rgb);
    }
    finalColor.a = smoothstep(smoothRange.x, smoothRange.y, finalColor.a);
    gl_FragColor = finalColor;
}
