// Sky gradient and the blocky clouds. Directions here are in world space.

vec3 skyGradient(vec3 d, vec3 sunW, float sunH) {
#ifdef NETHER
    return toLinear(fogColor);
#endif
#ifdef END
    return vec3(0.020, 0.012, 0.035) + vec3(0.02, 0.01, 0.04) * max(d.y, 0.0);
#endif
    float day = dayFactor(sunH);
    float h = max(d.y, 0.0);
    vec3 zenith = mix(vec3(0.004, 0.010, 0.036), vec3(0.085, 0.260, 0.760), day);
    vec3 horizon = mix(vec3(0.020, 0.050, 0.105), vec3(0.460, 0.670, 0.940), day);
    vec3 col = mix(horizon, zenith, 1.0 - exp(-h * 3.2));
    // Dusk and dawn: warm band along the horizon, strongest towards the sun
    float dusk = duskFactor(sunH) * smoothstep(-0.25, 0.05, sunH);
    float toSun = max(dot(d, sunW), 0.0);
    col += vec3(1.00, 0.34, 0.08) * dusk * (0.40 + 1.50 * toSun * toSun * toSun) * pow(1.0 - h, 4.0);
    col += vec3(0.75, 0.24, 0.42) * dusk * 0.34 * pow(1.0 - h, 1.6);
    // Night: a band of teal low in the sky, and the moon's glow
    col += vec3(0.010, 0.035, 0.050) * (1.0 - day) * pow(1.0 - h, 6.0);
    col += vec3(0.20, 0.30, 0.50) * pow(max(dot(d, -sunW), 0.0), 40.0) * 0.30 * (1.0 - day);
    // A soft halo round the sun by day
    col += vec3(1.0, 0.85, 0.60) * pow(toSun, 24.0) * 0.25 * day;
    col = mix(col, horizon * 0.85, smoothstep(0.0, -0.35, d.y));
    return mix(col, vec3(luma(col)) * 0.40, rainStrength * 0.88);
}

float stars(vec3 d, float sunH) {
    if (d.y < 0.0) return 0.0;
    // Square stars on a grid wrapped round the sky: a few big bright ones, many faint ones
    vec3 p = d * 150.0;
    vec3 c = floor(p);
    float n = hash12(c.xz + c.y * 17.31);
    float bright = hash12(c.zy + c.x * 9.17);
    float s = step(0.9925, n) * (0.25 + 0.75 * bright * bright);
    s *= 0.75 + 0.25 * sin(frameTimeCounter * (1.0 + bright * 3.0) + n * 600.0);
    return s * (1.0 - dayFactor(sunH)) * smoothstep(0.0, 0.2, d.y) * (1.0 - rainStrength);
}

#ifdef USE_CLOUDS
const float CLOUD_CELL = 12.0;       // blocks per cloud "pixel", as in the vanilla sheet
const float CLOUD_THICK = 16.0;

// How much cloud a column holds: 0 none, up to 1 at the middle of a big cloud.
float cloudColumn(vec2 xz) {
    vec2 p = floor((xz + vec2(frameTimeCounter * 1.4, frameTimeCounter * 0.35)) / CLOUD_CELL);
    // Two taps of the noise texture, read smoothly: cheaper than computed noise
    float n = texture(noisetex, p * (0.105 / 64.0)).r * 0.62 + texture(noisetex, p * (0.270 / 64.0) + 0.37).g * 0.38;
    float cover = mix(0.555, 0.40, rainStrength);
    return clamp((n - cover) * 5.5, 0.0, 1.0);
}

// Inside a cloud? Clouds are flat underneath and stepped on top, one block-high step per level.
bool inCloud(vec3 wp) {
    float c = cloudColumn(wp.xz);
    if (c <= 0.0) return false;
    float top = CLOUD_HEIGHT + 4.0 + floor(c * 3.0 + 0.5) * 4.0;
    return wp.y >= CLOUD_HEIGHT && wp.y <= top;
}

// Returns rgb = cloud colour, a = cover. maxDist: don't draw clouds behind this (terrain).
vec4 renderClouds(vec3 camW, vec3 d, vec3 sunW, float sunH, float maxDist, float dither) {
    float lo = CLOUD_HEIGHT, hi = CLOUD_HEIGHT + CLOUD_THICK;
    if (abs(d.y) < 0.0008) return vec4(0.0);
    float t0 = (lo - camW.y) / d.y, t1 = (hi - camW.y) / d.y;
    float tn = max(min(t0, t1), 0.0), tf = min(max(t0, t1), maxDist);
    const float REACH = 1500.0;
    tf = min(tf, tn + 420.0);
    if (tf <= tn || tn > REACH) return vec4(0.0);

    float dt = (tf - tn) / float(CLOUD_STEPS);
    float t = tn + dt * dither;
    float hit = -1.0;
    for (int i = 0; i < CLOUD_STEPS; i++) {
        if (inCloud(camW + d * t)) { hit = t; break; }
        t += dt;
    }
    if (hit < 0.0) return vec4(0.0);
    // Walk back to the cloud's face so its edges stay crisp cubes
    float a = hit - dt, b = hit;
    for (int i = 0; i < 4; i++) {
        float m = (a + b) * 0.5;
        if (inCloud(camW + d * m)) b = m; else a = m;
    }
    vec3 p = camW + d * b;

    float hf = clamp((p.y - lo) / CLOUD_THICK, 0.0, 1.0);
    float lit = inCloud(p + sunW * 7.0) ? 0.0 : 1.0;                 // one tap towards the light
    vec3 light = lightColorNow(sunH), amb = ambientColorNow(sunH);
    vec3 col = amb * (0.60 + 0.80 * hf) + light * (0.10 + 0.36 * lit * (0.45 + 0.55 * hf));
    col += vec3(0.05, 0.07, 0.11) * (1.0 - dayFactor(sunH)) * hf;        // moonlit tops
    col = mix(col, vec3(luma(col)) * 0.55, rainStrength * 0.6);
    float fade = exp(-max(b - 200.0, 0.0) / (REACH * 0.36));
    return vec4(col, fade);
}
#endif

// Everything behind the world: gradient, stars, clouds.
vec3 skyAll(vec3 camW, vec3 d, vec3 sunW, float sunH, float dither, bool withClouds) {
    vec3 col = skyGradient(d, sunW, sunH);
#if !defined NETHER && !defined END
    col += vec3(0.85, 0.92, 1.0) * stars(d, sunH) * 2.2;
#ifdef USE_CLOUDS
    if (withClouds) {
        vec4 c = renderClouds(camW, d, sunW, sunH, 1.0e6, dither);
        col = mix(col, c.rgb, c.a);
    }
#endif
#endif
    return col;
}
