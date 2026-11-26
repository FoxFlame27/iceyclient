// Lights everything solid, and draws the sky and clouds behind and above it.
#include "/lib/common.glsl"
#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D depthtex0;
in vec2 uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
    float depth = texture(depthtex0, uv).r;
    vec4 albedoA = texture(colortex0, uv);
    vec4 data = texture(colortex2, uv);
    float mat = floor(data.b * 255.0 + 0.5);
    vec3 viewPos = viewFromDepth(uv, depth);
    vec3 dirV = normalize(viewPos);
    vec3 dirW = mat3(gbufferModelViewInverse) * dirV;
    vec3 sunW = mat3(gbufferModelViewInverse) * normalize(sunPosition);
    float sunH = sunHeightNow();
    float dither = ign(gl_FragCoord.xy);

    if (depth >= 1.0) {
        vec3 sky = skyAll(cameraPosition, dirW, sunW, sunH, dither, false);
#if !defined NETHER && !defined END
        if (mat > 150.0) sky += toLinear(albedoA.rgb) * (sunH > 0.0 || dirW.y < 0.0 ? 3.0 : 1.6) * (1.0 - rainStrength);
#ifdef USE_CLOUDS
        vec4 c = renderClouds(cameraPosition, dirW, sunW, sunH, 1.0e6, dither);
        sky = mix(sky, c.rgb, c.a);
#endif
#endif
        outColor = vec4(sky, 1.0);
        return;
    }

    vec3 albedo = toLinear(albedoA.rgb);
    vec3 col;
    if (abs(mat - MAT_UNLIT) < 0.5) {
        col = albedo;
    } else {
        vec3 N = normalize(texture(colortex1, uv).rgb * 2.0 - 1.0);
        col = shade(albedo, viewPos, N, data.rg, mat, dither);
    }
    if (abs(mat - MAT_HAND) > 0.5) {
        col = applyFog(col, viewPos, skyGradient(dirW, sunW, sunH));
#ifdef USE_CLOUDS
        // Clouds in front of far terrain, for when you are above or among them
        float dist = length(viewPos);
        if (dist > 48.0) {
            vec4 c = renderClouds(cameraPosition, dirW, sunW, sunH, dist, dither);
            col = mix(col, c.rgb, c.a);
        }
#endif
    }
    outColor = vec4(col, 1.0);
}
