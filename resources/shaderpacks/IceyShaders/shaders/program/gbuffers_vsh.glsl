// Vertex stage shared by all gbuffers programs. The stub that includes this
// sets what the program is: TERRAIN, WATER, ENTITY, HAND, TEXTURED, LIGHTMAP…
#include "/lib/common.glsl"
#include "/lib/waving.glsl"

out vec2 uv;
out vec2 lm;
out vec4 tint;
out vec3 normalV;
out vec3 viewPosV;
out vec3 worldPosV;
out float matV;

#if defined TERRAIN || defined WATER
in vec4 mc_Entity;
in vec4 mc_midTexCoord;
#endif

void main() {
    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lm = clamp(((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy - 1.0 / 32.0) * (32.0 / 30.0), 0.0, 1.0);
    tint = gl_Color;
    normalV = normalize(gl_NormalMatrix * gl_Normal);
    matV = MAT_DEFAULT;

    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;

#if defined TERRAIN || defined WATER
    float id = mc_Entity.x;
    vec3 wp = (gbufferModelViewInverse * viewPos).xyz + cameraPosition;
    bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t;
#ifdef TERRAIN
#ifdef WAVING
    wp += waveOffset(wp, id, top, lm.y);
#endif
    if (id == float(ID_LEAVES) || id == float(ID_PLANT) || id == float(ID_TALL_UPPER) || id == float(ID_TALL_LOWER) || id == float(ID_VINE)) matV = MAT_FOLIAGE;
    if (id == float(ID_EMISSIVE)) matV = MAT_EMISSIVE;
#endif
#ifdef WATER
    if (id == float(ID_WATER)) {
        matV = MAT_WATER;
        // Only the surface moves; the sides and the bed stay put
        if (gl_Normal.y > 0.5) wp.y += waterHeight(wp.xz, frameTimeCounter) * 0.035 - 0.03;
    }
#endif
    viewPos = gbufferModelView * vec4(wp - cameraPosition, 1.0);
    worldPosV = wp;
#else
    worldPosV = (gbufferModelViewInverse * viewPos).xyz + cameraPosition;
#endif

#ifdef HAND
    matV = MAT_HAND;
#endif
#ifdef UNLIT
    matV = MAT_UNLIT;
#endif
    viewPosV = viewPos.xyz;
    gl_Position = gl_ProjectionMatrix * viewPos;
}
