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
#if defined WEATHER_FX && !defined NETHER && !defined END
        if (abs(mat - MAT_HAND) > 0.5) {
            vec3 upV = normalize(upPosition);
            float open = smoothstep(0.80, 0.95, data.g);                  // under the sky, not in a cave
            float flat_ = smoothstep(0.55, 0.95, dot(N, upV));
            vec3 wp = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz + cameraPosition;
            if (wetness > 0.01 && biome_precipitation != 2) {
                // Rain: the ground goes dark and shiny, with standing water in the dips
                float wet = wetness * open;
                float puddle = smoothstep(0.44, 0.60, vnoise(wp.xz * 0.33) * 0.7 + vnoise(wp.xz * 1.1 + 9.0) * 0.3) * flat_;
                col *= 1.0 - 0.34 * wet;
                vec3 Nr = normalize(mix(N, upV, puddle));
                vec3 R = reflect(dirV, Nr);
                float fres = 0.04 + 0.96 * pow(1.0 - max(dot(-dirV, Nr), 0.0), 5.0);
                vec3 refl = skyGradient(mat3(gbufferModelViewInverse) * R, sunW, sunH);
                col = mix(col, refl, clamp(fres * 1.15, 0.0, 1.0) * wet * (0.22 * flat_ + 0.70 * puddle));
            }
            // Snow (any near-white ground in the open): grains catch the light as you move
            if (luma(albedo) > 0.62 && flat_ > 0.5 && luma(col) > 0.45) {
                vec3 cell = floor(wp * 20.0);
                float h = hash12(cell.xz + cell.y * 7.13);
                float glint = fract(h * 91.7 + dot(dirW, vec3(13.1, 7.7, 9.3)) * 2.5);
                col += lightColorNow(sunH) * step(0.985, h) * smoothstep(0.80, 1.0, glint) * open * 1.4;
            }
        }
#endif
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
