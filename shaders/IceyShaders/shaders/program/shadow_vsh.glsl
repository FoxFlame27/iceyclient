#include "/lib/common.glsl"
#include "/lib/waving.glsl"
in vec4 mc_Entity;
in vec4 mc_midTexCoord;
out vec2 uv;
out float alphaScale;

void main() {
    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    alphaScale = gl_Color.a;
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
#ifdef WAVING
    // The same wind as on screen, or shadows would stand still under moving leaves
    vec3 wp = (shadowModelViewInverse * viewPos).xyz + cameraPosition;
    vec2 lmv = clamp(((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy - 1.0 / 32.0) * (32.0 / 30.0), 0.0, 1.0);
    wp += waveOffset(wp, mc_Entity.x, gl_MultiTexCoord0.t < mc_midTexCoord.t, lmv.y);
    viewPos = shadowModelView * vec4(wp - cameraPosition, 1.0);
#endif
    gl_Position = gl_ProjectionMatrix * viewPos;
    gl_Position.xy /= length(gl_Position.xy) * 0.9 + 0.1;     // more texels near the player
    gl_Position.z *= 0.2;
}
