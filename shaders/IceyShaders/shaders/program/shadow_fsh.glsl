uniform sampler2D gtexture;
in vec2 uv;
in float alphaScale;
layout(location = 0) out vec4 outColor;
void main() {
    vec4 c = texture(gtexture, uv);
    if (c.a * alphaScale < 0.1) discard;
    outColor = c;
}
