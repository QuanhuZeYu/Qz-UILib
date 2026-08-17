# 规格文档

本目录用于存放示例页专属需求、视觉/交互规格和专项方案。每份文件自己的状态标记决定它是现行规格、历史基线还是候选草案。

这里的文档用于固化局部页面或专项能力的预期行为，不等同于通用接入手册；对外开发使用方式仍以 `docs/使用文档/` 为准。

## 当前文档

- `网络层方案.md`：4.1LTS 网络层（内容语义 Channel + Fetch + Stream + Store + Vanilla mixin 适配器 + Forge 兼容适配器）实验性方案。
- `网络编解码线格式.md`：网络层内容 envelope、可选 POJO codec 与分片格式。
- `网络原版Mixin注入策略.md`：vanilla custom payload early mixin 注入点与传输策略。
- `网络自检.md`：网络层诊断页自检场景规格。
- `qzuilib测试页视觉矩阵规划.md`：`/qzuilib test` 视觉优先测试矩阵规划，被 `UiTestMatrixRegistry` 代码硬引用为 SPEC_PATH（活文档，勿删）。
- `scene浮层P0施工清单.md`：scene 通用 `top-layer/overlay` 地基 P0 文件级施工清单，`SceneSelect` 是首个消费者和验收用例。
- `scene基础API规范.md`：Scene Primitive API 规范。
- `scene装饰配色规格.md`：4.x Scene Chrome 配色基线；候选替代方向见 `材质配置动效.md`。
- `数据表可编辑单元格视觉规格.md`：DataTable 可编辑列视觉方案（D1 聚焦蓝 / D2 圆角 2px）。
- `现代配置实现蓝图.md`：现行 ModernConfig 三态与数据事务蓝图；候选 presentation 见 `材质配置动效.md`。
- `UI投影宿主语义.md`：统一 content/projection/host/input 与 state/intent 高层语义。
- `物品视觉渲染接缝.md`：Breaking major 的 snapshot-only ItemStack icon seam。
- `scene材质动效演进.md`：Scene、Material、Motion 与版本实施顺序。
- `材质配置动效.md`：Material 3 配置页与最小 Motion 产品规格。
