// 顶点着色器 vertex_shader.vs
#version 330

layout (location = 0) in vec3 aPos;    // 顶点位置属性
layout (location = 1) in vec3 aColor;  // 顶点颜色属性

out vec3 vColor;                       // 传递给片元着色器的颜色

uniform mat4 mvpMatrix;                // 模型-视图-投影矩阵

void main()
{
    gl_Position = mvpMatrix * vec4(aPos, 1.0);
    vColor = aColor;
}
