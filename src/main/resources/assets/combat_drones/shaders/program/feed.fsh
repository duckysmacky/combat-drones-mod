#version 120

// Camera feed grade: lens bulge, colour fringing, saturation / contrast / tint, scanlines, sensor noise, vignette.

uniform sampler2D DiffuseSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform vec2 InSize;
uniform float Time;
uniform float Saturation;
uniform float Contrast;
uniform float Scanline;
uniform float Vignette;
uniform float Barrel;
uniform float Chroma;
uniform float Noise;
uniform vec3 Tint;

float hash(vec2 point) {
    return fract(sin(dot(point, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 centred = texCoord - 0.5;
    // Bulge the middle, keeping the corners where they are so nothing outside the frame is sampled
    vec2 uv = 0.5 + centred * (1.0 + Barrel * dot(centred, centred)) / (1.0 + Barrel * 0.5);
    vec2 fringe = vec2(Chroma * oneTexel.x, 0.0);
    vec3 colour = vec3(texture2D(DiffuseSampler, uv + fringe).r, texture2D(DiffuseSampler, uv).g, texture2D(DiffuseSampler, uv - fringe).b);
    float luma = dot(colour, vec3(0.299, 0.587, 0.114));
    colour = mix(vec3(luma), colour, Saturation);
    colour = (colour - 0.5) * Contrast + 0.5;
    colour *= Tint;
    // One dark line in every three real pixels
    colour *= 1.0 - Scanline * step(0.66, fract(texCoord.y * InSize.y / 3.0));
    colour += (hash(floor(texCoord * InSize / 2.0) + floor(Time * 400.0)) - 0.5) * Noise;
    colour *= 1.0 - Vignette * smoothstep(0.3, 0.8, length(centred));
    gl_FragColor = vec4(colour, 1.0);
}
