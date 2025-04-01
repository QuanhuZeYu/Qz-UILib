#version 330
layout (location = 0) in vec2 aPos;       // 标准化设备坐标
layout (location = 1) in vec2 aTexCoords; // 纹理坐标

out vec2 texCoords;

void main() {
    // 将顶点坐标直接映射到[-1,1]的NDC空间
    gl_Position = vec4(aPos, 0.0, 1.0);
    // 传递纹理坐标到片段着色器
    texCoords = aTexCoords;
}