#version 120


attribute vec3 pos;
attribute vec2 tex;
attribute vec4 color;

uniform mat4 modelview;
uniform mat4 projection;

varying vec2 texCoord;
varying vec4 Color;

void main(void) {
    gl_Position = projection * modelview * vec4(pos, 1.0);
    texCoord = tex;
    Color = color;
}