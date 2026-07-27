# 规格文档

本目录用于存放示例页专属需求、视觉/交互规格和专项方案定稿。

这里的文档用于固化局部页面或专项能力的预期行为，不等同于通用接入手册；对外开发使用方式仍以 `docs/使用文档/` 为准。

## 当前文档

- `network-layer-plan.md`：4.1LTS 网络层（内容语义 Channel + Fetch + Stream + Store + Vanilla mixin 适配器 + Forge 兼容适配器）实验性方案。
- `net-codec-wire-format.md`：网络层内容 envelope、可选 POJO codec 与分片格式。
- `net-vanilla-mixin-strategy.md`：vanilla custom payload early mixin 注入点与传输策略。
- `net-self-check.md`：网络层诊断页自检场景规格。
- `qzuilib-test-page-visual-matrix-plan.md`：`/qzuilib test` 视觉优先测试矩阵规划，被 `UiTestMatrixRegistry` 代码硬引用为 SPEC_PATH（活文档，勿删）。
- `scene-overlay-p0-plan.md`：scene 通用 `top-layer/overlay` 地基 P0 文件级施工清单，`SceneSelect` 是首个消费者和验收用例。
- `scene-primitive-api-spec.md`：Scene Primitive API 规范。
- `scene-chrome-color-spec.md`：Scene 控件 Chrome 配色规范（SceneChromeTokens + SceneStateColors 查表体系）。
- `datatable-editable-cell-visual-spec.md`：DataTable 可编辑列视觉方案（D1 聚焦蓝 / D2 圆角 2px）。
- `modern-config-implementation-blueprint.md`：现代化配置页施工图（实现蓝图），三态四层软依赖架构，含包结构/核心层与 UI 层详细设计/数据流/实现顺序/5 项裁决记录。
- `experimental-long-entry-container.md`：experimental long Entry 容器施工图/实现蓝图；当前尚未实现，含 P1-P3 契约、scene 投影与 #66 GuiContainer host。
