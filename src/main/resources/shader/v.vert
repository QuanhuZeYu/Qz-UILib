#version 330

layout (location = 0) in vec3 inPos;
uniform mat4 modelView;
uniform mat4 projection;

void main() {
    gl_Position = projection * modelView * vec4(inPos, 1);
}