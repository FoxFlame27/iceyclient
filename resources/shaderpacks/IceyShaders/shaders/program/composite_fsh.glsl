// After water and glass are in: reflections on water, and the tint of being under it.
#include "/lib/common.glsl"
#include "/lib/sky.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D depthtex0;
in vec2 uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
    vec3 col = texture(colortex0, uv).rgb;
    vec4 data = texture(colortex2, uv);
    float mat = floor(data.b * 255.0 + 0.5);

#ifdef WATER_REFLECTIONS
    if (abs(mat - MAT_WATER) < 0.5 && isEyeInWater == 0) {
        float depth = texture(depthtex0, uv).r;
        vec3 viewPos = viewFromDepth(uv, depth);
        vec3 V = normalize(viewPos);
        vec3 N = normalize(texture(colortex1, uv).rgb * 2.0 - 1.0);
        vec3 R = reflect(V, N);
        float fresnel = 0.03 + 0.97 * pow(1.0 - max(dot(-V, N), 0.0), 5.0);

        float sunH = sunHeightNow();
        vec3 sunW = mat3(gbufferModelViewInverse) * normalize(sunPosition);
        vec3 RW = mat3(gbufferModelViewInverse) * R;
        float dither = ign(gl_FragCoord.xy);
        vec3 refl = skyAll(cameraPosition, RW, sunW, sunH, dither, true) * smoothstep(0.1, 0.8, data.g);
        // The light's glitter on the waves
        vec3 L = normalize(shadowLightPosition);
        refl += lightColorNow(sunH) * pow(max(dot(R, L), 0.0), 220.0) * 6.0 * smoothstep(0.5, 0.9, data.g);

        // Screen-space: march the reflected ray and take what it runs into
        vec3 p = viewPos;
        float stepLen = 0.9;
        bool hit = false;
        vec2 hitUv = uv;
        for (int i = 0; i < SSR_STEPS; i++) {
            p += R * stepLen * (0.6 + 0.8 * dither);
            stepLen *= 1.45;
            vec4 clip = gbufferProjection * vec4(p, 1.0);
            vec3 ndc = clip.xyz / clip.w * 0.5 + 0.5;
            if (ndc.x < 0.0 || ndc.x > 1.0 || ndc.y < 0.0 || ndc.y > 1.0 || ndc.z > 1.0) break;
            float d = texture(depthtex0, ndc.xy).r;
            if (d < ndc.z && d < 1.0) {
                vec3 q = viewFromDepth(ndc.xy, d);
                if (abs(q.z - p.z) < stepLen * 1.6 + 1.0) { hit = true; hitUv = ndc.xy; }
                break;
            }
        }
        if (hit) {
            vec2 edge = smoothstep(vec2(0.0), vec2(0.12), hitUv) * smoothstep(vec2(1.0), vec2(0.88), hitUv);
            refl = mix(refl, texture(colortex0, hitUv).rgb, edge.x * edge.y);
        }
        col = mix(col, refl, fresnel);
    }
#endif

    if (isEyeInWater == 1) col *= vec3(0.55, 0.85, 1.0);
    outColor = vec4(col, 1.0);
}
