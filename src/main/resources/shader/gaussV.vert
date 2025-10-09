#version 120

// 输入属性 (由 renderQuad() 提供)
attribute vec3 position;
attribute vec2 texCoord;

// 输出变量 (传递给片段着色器)
varying vec2 v_texCoord;

void main(void) {
    // 设置顶点位置。四边形的顶点通常在 [-1, 1] 范围内，
    // 已经位于裁剪空间 (Clip Space)，所以直接赋值即可。
    gl_Position = vec4(position, 1.0);

    // 传递纹理坐标
    v_texCoord = texCoord;
}