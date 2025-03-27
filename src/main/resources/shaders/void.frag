// 片元着色器 fragment_shader.fs
#version 330

in vec3 vColor;        // 来自顶点着色器的颜色输入
out vec4 FragColor;    // 最终输出的颜色

void main()
{
    FragColor = vec4(vColor, 1.0);  // 使用插值后的颜色
}
