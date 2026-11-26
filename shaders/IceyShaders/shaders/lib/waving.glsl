// Wind: leaves shiver all over, plants bend from the root, tall plants carry
// the sway through both blocks. Gusts roll across the world so a field moves in waves.
#ifdef WAVING
vec3 waveOffset(vec3 wp, float id, bool top, float skyLight) {
    float t = frameTimeCounter;
    float gust = 0.55 + 0.45 * sin(t * 0.55 + (wp.x + wp.z) * 0.07) + rainStrength * 0.6;
    float amp = WAVING_STRENGTH * gust * smoothstep(0.35, 0.95, skyLight);
    if (amp <= 0.0) return vec3(0.0);
    vec3 o = vec3(0.0);
    if (id == float(ID_LEAVES)) {
        o = vec3(sin(t * 1.7 + wp.x * 1.3 + wp.y * 0.9 + wp.z * 0.4),
                 sin(t * 1.3 + wp.z * 1.1 + wp.x * 0.6) * 0.55,
                 cos(t * 1.5 + wp.z * 1.2 + wp.y * 0.8 + wp.x * 0.5)) * 0.042;
    } else if (id == float(ID_PLANT) || id == float(ID_TALL_LOWER)) {
        if (top) o = vec3(sin(t * 2.0 + wp.x * 0.8 + wp.z * 0.6) + sin(t * 3.3 + wp.z * 1.7) * 0.35, 0.0,
                          cos(t * 1.8 + wp.z * 0.9 + wp.x * 0.5)) * (id == float(ID_PLANT) ? 0.085 : 0.05);
    } else if (id == float(ID_TALL_UPPER)) {
        o = vec3(sin(t * 2.0 + wp.x * 0.8 + wp.z * 0.6) + sin(t * 3.3 + wp.z * 1.7) * 0.35, 0.0,
                 cos(t * 1.8 + wp.z * 0.9 + wp.x * 0.5)) * (top ? 0.13 : 0.05);
    } else if (id == float(ID_VINE)) {
        o = vec3(sin(t * 1.4 + wp.y * 1.1 + wp.z), 0.0, cos(t * 1.2 + wp.y * 1.3 + wp.x)) * 0.028;
    }
    return o * amp;
}
#endif

float waterHeight(vec2 p, float t) {
    return sin(p.x * 0.9 + t * 1.3) * 0.5 + sin(p.y * 1.3 - t * 1.1) * 0.35 + sin((p.x + p.y) * 2.1 + t * 2.0) * 0.15;
}
