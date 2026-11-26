// Bloom from the picture's own mip levels, then tone mapping and colour.
#include "/lib/common.glsl"

uniform sampler2D colortex0;
in vec2 uv;

/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGB10_A2;
const int colortex2Format = RGBA8;
const bool colortex0MipmapEnabled = true;
const int shadowMapResolution = 1536;
const float shadowDistance = 88.0;
const float shadowDistanceRenderMul = 1.0;
const bool shadowHardwareFiltering = true;
const float sunPathRotation = -28.0;
const float ambientOcclusionLevel = 1.0;
const int noiseTextureResolution = 64;
*/

layout(location = 0) out vec4 outColor;

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec3 col = textureLod(colortex0, uv, 0.0).rgb;

#ifdef BLOOM
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    vec3 glow = vec3(0.0);
    float lod = 3.0;
    for (int i = 0; i < 3; i++) {
        float r = exp2(lod) * 0.5;
        vec3 b = textureLod(colortex0, uv + vec2(r, r) * px, lod).rgb
               + textureLod(colortex0, uv + vec2(-r, r) * px, lod).rgb
               + textureLod(colortex0, uv + vec2(r, -r) * px, lod).rgb
               + textureLod(colortex0, uv + vec2(-r, -r) * px, lod).rgb;
        glow += b * 0.25;
        lod += 1.0;
    }
    glow /= 3.0;
    col += max(glow - 0.85, 0.0) * 0.55 * BLOOM_STRENGTH + glow * 0.035 * BLOOM_STRENGTH;
#endif

    col = aces(col * 0.56 * EXPOSURE);
    col = pow(col, vec3(1.0 / 2.2));
    col = mix(vec3(luma(col)), col, SATURATION);
    vec2 c = uv - 0.5;
    col *= 1.0 - dot(c, c) * 0.22;
    outColor = vec4(col, 1.0);
}
