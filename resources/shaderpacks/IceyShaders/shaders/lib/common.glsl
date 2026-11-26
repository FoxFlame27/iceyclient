// Icey Shaders: settings, uniforms and the helpers every program shares.

// ── Settings (shown in the shader options screen) ──
#define SHADOWS
#define SHADOW_SOFTNESS 1.0      // [0.5 1.0 1.5 2.0]
#define CLOUDS
#define CLOUD_STEPS 10           // [8 10 12 16 24]
#define CLOUD_HEIGHT 192.0       // [128.0 160.0 192.0 224.0 256.0]
#define WAVING
#define WAVING_STRENGTH 1.0      // [0.5 1.0 1.5 2.0]
#define WATER_REFLECTIONS
#define SSR_STEPS 12             // [8 12 16 24]
#define BLOOM
#define BLOOM_STRENGTH 1.0       // [0.5 1.0 1.5 2.0]
#define WEATHER_FX
#define HAND_LIGHT
#define SATURATION 1.12          // [0.9 1.0 1.12 1.25 1.4]
#define EXPOSURE 1.0             // [0.8 0.9 1.0 1.1 1.2]

// What the dimension allows on top of what the player switched on
#ifdef SHADOWS
#define USE_SHADOWS
#endif
#ifdef CLOUDS
#define USE_CLOUDS
#endif
#ifdef NETHER
#undef USE_SHADOWS
#undef USE_CLOUDS
#endif
#ifdef END
#undef USE_SHADOWS
#undef USE_CLOUDS
#endif

// ── Block ids from block.properties ──
#define ID_LEAVES 10001
#define ID_PLANT 10002
#define ID_TALL_UPPER 10003
#define ID_TALL_LOWER 10004
#define ID_VINE 10005
#define ID_WATER 10006
#define ID_EMISSIVE 10007

// ── Material written to colortex2.b (x/255) ──
#define MAT_DEFAULT 0.0
#define MAT_FOLIAGE 1.0
#define MAT_EMISSIVE 2.0
#define MAT_WATER 3.0
#define MAT_HAND 5.0
#define MAT_UNLIT 7.0

const float PI = 3.14159265;

uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowProjection;
uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
uniform vec3 cameraPosition;
uniform vec3 sunPosition;
uniform vec3 shadowLightPosition;
uniform vec3 upPosition;
uniform vec3 fogColor;
uniform vec3 skyColor;
uniform float frameTimeCounter;
uniform float rainStrength;
uniform float viewWidth;
uniform float viewHeight;
uniform float near;
uniform float far;
uniform int isEyeInWater;
uniform int worldTime;
uniform ivec2 eyeBrightnessSmooth;
uniform float blindness;
uniform float darknessFactor;
uniform float nightVision;
uniform sampler2D noisetex;
uniform int heldBlockLightValue;      // light level (0..15) of what the main hand holds
uniform int heldBlockLightValue2;     // and the off hand
uniform float wetness;               // rain, eased in and out
uniform int biome_precipitation;     // Iris: 0 none, 1 rain, 2 snow where the player stands

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Interleaved gradient noise: a stable per-pixel dither.
float ign(vec2 px) {
    return fract(52.9829189 * fract(dot(px, vec2(0.06711056, 0.00583715))));
}

vec3 toLinear(vec3 c) { return pow(c, vec3(2.2)); }

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 viewFromDepth(vec2 uv, float depth) {
    vec4 p = gbufferProjectionInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// ── Time of day, from where the sun stands ──
// sunH: -1 (midnight) .. 1 (noon), the sun's height above the horizon.
float sunHeightNow() { return dot(normalize(sunPosition), normalize(upPosition)); }
float dayFactor(float sunH) { return smoothstep(-0.10, 0.22, sunH); }
float duskFactor(float sunH) { return smoothstep(0.42, 0.02, abs(sunH)); }

// Colour and strength of the light that casts the shadows (sun by day, moon by night).
vec3 lightColorNow(float sunH) {
    vec3 sun = mix(vec3(1.00, 0.42, 0.15), vec3(1.00, 0.94, 0.82), smoothstep(0.0, 0.40, sunH)) * 2.7;
    vec3 moon = vec3(0.30, 0.44, 0.78) * 0.62;
    vec3 c = sunH > 0.0 ? sun : moon;
    c *= smoothstep(0.0, 0.10, abs(sunH));           // nothing while the light changes hands at the horizon
    return c * (1.0 - rainStrength * 0.85);
}

vec3 ambientColorNow(float sunH) {
    float day = dayFactor(sunH);
    vec3 c = mix(vec3(0.040, 0.062, 0.135), vec3(0.44, 0.56, 0.80), day);
    c += vec3(0.34, 0.14, 0.08) * duskFactor(sunH) * day;
    return mix(c, vec3(luma(c)) * 0.8, rainStrength * 0.7);
}
