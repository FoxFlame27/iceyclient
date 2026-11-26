// Shadows and the one lighting function used by the deferred pass and by
// everything drawn forward (water, glass, translucent entities).

#ifdef USE_SHADOWS
uniform sampler2DShadow shadowtex1;
const float SHADOW_MAP = 1536.0;

float shadowAt(vec3 viewPos, vec3 viewNormal, float NdotL, float dither) {
    vec3 player = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz;
    float dist = length(player);
    if (dist > 88.0) return 1.0;
    vec3 nW = mat3(gbufferModelViewInverse) * viewNormal;
    player += nW * (0.035 + dist * 0.0045) * (1.6 - NdotL);         // off the surface, more at grazing light
    vec3 p = (shadowProjection * (shadowModelView * vec4(player, 1.0))).xyz;
    float f = length(p.xy) * 0.9 + 0.1;
    p.xy /= f;
    p.z *= 0.2;
    p = p * 0.5 + 0.5;
    if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
    p.z -= 0.00012 * f;
    float r = SHADOW_SOFTNESS * 1.3 / SHADOW_MAP;
    float a = dither * 6.2831853;
    vec2 o = vec2(cos(a), sin(a)) * r;
    float s = texture(shadowtex1, vec3(p.xy + o, p.z))
            + texture(shadowtex1, vec3(p.xy - o, p.z))
            + texture(shadowtex1, vec3(p.xy + vec2(-o.y, o.x), p.z))
            + texture(shadowtex1, vec3(p.xy - vec2(-o.y, o.x), p.z));
    s *= 0.25;
    return mix(s, 1.0, smoothstep(70.0, 87.0, dist));
}
#else
float shadowAt(vec3 viewPos, vec3 viewNormal, float NdotL, float dither) { return 1.0; }
#endif

// albedo is linear. lm = (block light, sky light), both 0..1.
vec3 shade(vec3 albedo, vec3 viewPos, vec3 N, vec2 lm, float mat, float dither) {
    float sunH = sunHeightNow();
    vec3 L = normalize(shadowLightPosition);
    float NdotL = dot(N, L);
    bool foliage = abs(mat - MAT_FOLIAGE) < 0.5;
    float diffuse = foliage ? 0.62 + 0.18 * NdotL : max(NdotL, 0.0);

    float sky = lm.y * lm.y;
    float block = lm.x;
#ifdef HAND_LIGHT
    // A torch, lantern or anything else that glows in either hand lights the
    // place around you: full strength at your feet, gone after about as many
    // blocks as the item's light level.
    float held = float(max(heldBlockLightValue, heldBlockLightValue2));
    if (held > 0.0) {
        float reach = clamp(1.0 - length(viewPos) / (held * 0.95 + 1.0), 0.0, 1.0);
        block = max(block, (held / 15.0) * reach * reach * (3.0 - 2.0 * reach));
    }
#endif

#ifdef NETHER
    vec3 direct = vec3(0.0);
    vec3 ambient = toLinear(fogColor) * 0.9 + vec3(0.10, 0.07, 0.06);
#elif defined END
    vec3 direct = vec3(0.0);
    vec3 ambient = vec3(0.26, 0.20, 0.34) * (0.75 + 0.25 * N.y);
#else
    float shadow = 1.0;
    if (diffuse > 0.0 && lm.y > 0.02 && abs(mat - MAT_HAND) > 0.5) shadow = shadowAt(viewPos, N, max(NdotL, 0.0), dither);
    // Light leaking into caves through the edge of the shadow map is held back by sky light
    vec3 direct = lightColorNow(sunH) * diffuse * shadow * smoothstep(0.0, 0.25, lm.y);
    vec3 up = normalize(upPosition);
    vec3 ambient = ambientColorNow(sunH) * sky * (0.72 + 0.28 * dot(N, up));
    ambient += vec3(0.030, 0.034, 0.046);                                   // never pitch black
#endif

    // Torch light: warm, tight falloff, a little hotter right at the source
    vec3 torch = vec3(1.00, 0.56, 0.24) * (pow(block, 3.2) * 2.6 + block * 0.10);
    // Players, mobs and your own hand keep a base glow in the dark, as in the
    // unshaded game: a skin that turns into a black cut-out in a cave is
    // worse than one that is a little too readable.
    if (abs(mat - MAT_ENTITY) < 0.5 || abs(mat - MAT_HAND) < 0.5) ambient = max(ambient, vec3(0.105, 0.110, 0.125));
    vec3 light = direct + ambient + torch + vec3(0.35, 0.45, 0.55) * nightVision;
    // Near-white blocks (snow, quartz) keep their shading instead of clipping to one flat white
    albedo *= 1.0 - 0.34 * smoothstep(0.45, 0.95, luma(albedo));
    vec3 col = albedo * light;
    if (abs(mat - MAT_EMISSIVE) < 0.5) col += albedo * 2.2;
    return col * (1.0 - blindness * 0.8);
}

// Distance fog towards the sky colour, thicker in rain and under water.
vec3 applyFog(vec3 col, vec3 viewPos, vec3 skyCol) {
    float dist = length(viewPos);
    if (isEyeInWater == 1) {
        float f = 1.0 - exp(-dist * 0.055);
        return mix(col, vec3(0.020, 0.090, 0.140) * (0.35 + 0.65 * float(eyeBrightnessSmooth.y) / 240.0), f);
    }
    if (isEyeInWater == 2) return mix(col, vec3(1.0, 0.25, 0.02), 1.0 - exp(-dist * 0.9));
#ifdef NETHER
    return mix(col, skyCol, 1.0 - exp(-dist * 0.022));
#endif
    float density = mix(0.9, 1.7, rainStrength);
    float f = 1.0 - exp(-pow(dist / far, 4.0) * density * 2.0);
    f = max(f, (1.0 - exp(-dist * 0.00028 * density)) * 0.5);
    f = max(f, darknessFactor * (1.0 - exp(-dist * 0.12)));
    return mix(col, skyCol, clamp(f, 0.0, 1.0));
}
