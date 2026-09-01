#version 120

varying vec2 texCoord;
varying vec2 panelUv;

// panelOrigin/panelSizePx 用于算面板局部 uv（边缘亮边与内侧柔光的坐标基准）。
// gl_Vertex 与 panelOrigin 处于同一模型空间（都来自宿主传入的 left/top/right/bottom），
// 相减后 GUI scale 自然约掉，故 panelUv 与缩放无关，边缘宽度是"逻辑像素"口径。
// panelSizePx 在顶点与片元阶段同名同型声明，GLSL 视其为同一个 program uniform，只设一次。
uniform vec2 panelOrigin;
uniform vec2 panelSizePx;

void main(void) {
    gl_Position = ftransform();
    texCoord = gl_MultiTexCoord0.xy;
    panelUv = (gl_Vertex.xy - panelOrigin) / max(panelSizePx, vec2(1.0, 1.0));
}
