// Fragment stage shared by all gbuffers programs.
// Opaque programs fill the gbuffer (albedo, normal, light + material) and the
// deferred pass lights them. FORWARD programs are drawn after that pass, so
// they light themselves here and blend straight into the picture.
#include "/lib/common.glsl"

in vec2 uv;
in vec2 lm;
in vec4 tint;
in vec3 normalV;
in vec3 viewPosV;
in vec3 worldPosV;
in float matV;

#ifdef TEXTURED
uniform sampler2D gtexture;
#endif
#ifdef ENTITY
uniform vec4 entityColor;
#endif

#ifdef FORWARD
#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"
#include "/lib/waving.glsl"
#endif

#ifdef ONLY_COLOR
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;
#else
/* RENDERTARGETS: 0,1,2 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outData;
#endif

void main() {
#ifdef DISCARD_ALL
    discard;
#endif
    vec4 col = tint;
#ifdef TEXTURED
    col *= texture(gtexture, uv);
#endif
#ifdef SKYBASIC
    col = vec4(0.0, 0.0, 0.0, 1.0);          // the sky itself is drawn by the deferred pass
#endif
    if (col.a < 0.1) discard;
#ifdef ENTITY
    col.rgb = mix(col.rgb, entityColor.rgb, entityColor.a);
#endif

    vec3 N = normalize(normalV);
    float mat = matV;

#ifdef FORWARD
    vec3 albedo = toLinear(col.rgb);
    float alpha = col.a;
#ifdef WATER
    if (abs(mat - MAT_WATER) < 0.5) {
        // Flat-colour water for flat-colour textures: the biome tint, a little deeper
        albedo = toLinear(tint.rgb) * vec3(0.30, 0.55, 0.75);
        alpha = 0.58;
        if (abs(dot(N, normalize(upPosition))) > 0.5) {
            float t = frameTimeCounter;
            float e = 0.12;
            float h0 = waterHeight(worldPosV.xz, t);
            float hx = waterHeight(worldPosV.xz + vec2(e, 0.0), t);
            float hz = waterHeight(worldPosV.xz + vec2(0.0, e), t);
            vec3 nW = normalize(vec3((h0 - hx) * 0.05 / e, 1.0, (h0 - hz) * 0.05 / e));
            N = normalize(mat3(gbufferModelView) * nW);
        }
    }
#endif
#ifdef WEATHER
    vec3 lit = albedo * (ambientColorNow(sunHeightNow()) * 1.4 + 0.05);
    alpha *= 0.7;
#else
    float dither = ign(gl_FragCoord.xy);
    vec3 lit = shade(albedo, viewPosV, N, lm, mat, dither);
    float sunH = sunHeightNow();
    vec3 dirW = mat3(gbufferModelViewInverse) * normalize(viewPosV);
    vec3 sunW = mat3(gbufferModelViewInverse) * normalize(sunPosition);
    lit = applyFog(lit, viewPosV, skyGradient(dirW, sunW, sunH));
#endif
    outColor = vec4(lit, alpha);
    outNormal = vec4(N * 0.5 + 0.5, 1.0);
    outData = vec4(lm, mat / 255.0, 1.0);
#else
#ifdef ONLY_COLOR
    outColor = col;
#else
    outColor = col;
    outNormal = vec4(N * 0.5 + 0.5, 1.0);
#ifdef SKYTEX
    outData = vec4(0.0, 0.0, 200.0 / 255.0, 1.0);     // sun or moon texel, added on top of the drawn sky
#else
    outData = vec4(lm, mat / 255.0, 1.0);
#endif
#endif
#endif
}
