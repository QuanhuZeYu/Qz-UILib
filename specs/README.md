# 规格入口

[核心职责规格](<2026-10-07-uilib-core-contract-design.md>)定义跨模块的状态、布局、生命周期、输入权威、业务与平台边界。模块行为与验收在对应规格维护；同一要求只在所属规格维护。

| 模块 | 规格 |
| --- | --- |
| 语言事实、翻译键与动态文案 | [语言与翻译规格](<2026-10-07-uilib-localization-spec.md>) |
| 主题与控件文案 | [主题规格](<2026-10-07-uilib-theme-spec.md>) |
| 草稿、提交、撤销与输入法 | [编辑规格](<2026-10-07-uilib-editing-spec.md>) |
| 声明式页面与宿主生命周期 | [页面接入规格](<2026-10-07-uilib-page-host-spec.md>) |
| 横纵排列、响应网格与布局扩展 | [常规布局规格](<2026-10-13-uilib-layout-spec.md>) |
| 所属层空间、共同呈现、输入参与与结束 | [所属层与输入范围规格](<2026-10-13-uilib-layer-input-spec.md>) |
| 内部协作、类职责与结构整理 | [内部职责整理规格](<2026-10-14-uilib-internal-responsibilities-spec.md>) |
| 动态节点结构与交互身份 | [动态结构规格](<uilib-dynamic-structure-spec.md>) |
| 测试应用、场景与报告 | [测试应用规格](<2026-10-07-uilib-testing-spec.md>) |
| 专用性能压测、采样口径与基线 | [性能压测广场规格](<2026-10-14-uilib-performance-testing-spec.md>) |
| 局部失效传播与增量文字测量（本次设计稿） | [增量更新规格](<2026-10-14-uilib-incremental-update-spec.md>) |
| 文本、Markdown、富文本与数学内容 | [文本规格](<2026-10-07-uilib-text-content-spec.md>) |
| 图像来源、尺寸、加载状态与物品图标 | [图像规格](<2026-10-07-uilib-image-spec.md>) |
| 字体整形、光栅及渲染 | [字体规格](<2026-10-07-uilib-font-renderer-spec.md>) |
| 独立配置声明、状态与持久化 | [无平台配置规格](<2026-10-11-uilib-config-spec.md>) |
| 配置页面视觉与编辑流程 | [配置页面规格](<2026-10-12-uilib-settings-page-design.md>) |
| 独立桌面窗口与应用宿主 | [桌面宿主规格](<2026-10-11-uilib-desktop-spec.md>) |
| MC宿主有序合批 | [渲染优化规格](<2026-10-07-mc-host-render-optimization-spec.md>) |

规格是目标与验收依据，不表示所有模块已经实现。源码与运行证据说明实现现状，不能反向定义要求。必要行为裁定更新到所属规格，局部机制及失败原因留在最近的代码注释。
