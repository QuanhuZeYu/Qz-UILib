#version 120

attribute vec3 position;
attribute vec2 texCoord;
attribute vec4 color;

uniform mat4 modelView;
uniform mat4 projection;

varying vec2 v_texCoord;
varying vec4 v_color;

void main(void) {
    gl_Position = projection * modelView * vec4(position, 1.0);

    v_texCoord = texCoord;
    v_color = color;
}