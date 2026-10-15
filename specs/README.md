# 规格入口

[核心职责规格](<2026-10-07-uilib-core-contract-design.md>)定义跨模块的状态、布局、生命周期、输入权威、业务与平台边界。对应模块维护所属能力及验收；同一要求只在其归属规格维护。本页统一登记模块目录，核心不维护第二份目录。

“已确认”说明合同认可状态，不代表代码、构建或实机已通过；运行结论须对应具体版本、环境和原始反馈。规划稿单列，未经确认的目标或核心边界缺口不替代现有合同。

## 已确认规格

| 模块 | 规格 |
| --- | --- |
| 语言事实、翻译键与动态文案 | [语言与翻译规格](<2026-10-07-uilib-localization-spec.md>) |
| 主题与控件文案 | [主题规格](<2026-10-07-uilib-theme-spec.md>) |
| 草稿、提交、撤销与输入法 | [编辑规格](<2026-10-07-uilib-editing-spec.md>) |
| 声明式页面与宿主生命周期 | [页面接入规格](<2026-10-07-uilib-page-host-spec.md>) |
| 普通内容的指针拖拽与结束清理 | [Draggable 规格](<2026-10-15-uilib-draggable-spec.md>) |
| 接收区域、候选与跨容器排序 | [Droppable 与 Sortable 规格](<2026-10-15-uilib-drop-sort-spec.md>) |
| 横纵排列、响应网格与布局扩展 | [常规布局规格](<2026-10-13-uilib-layout-spec.md>) |
| 所属层空间、共同呈现、输入参与与结束 | [所属层与输入范围规格](<2026-10-13-uilib-layer-input-spec.md>) |
| 内部协作者职责与交接 | [内部协作职责规格](<2026-10-14-uilib-internal-responsibilities-spec.md>) |
| 动态节点结构与交互身份 | [动态结构规格](<uilib-dynamic-structure-spec.md>) |
| 测试应用、场景与报告 | [测试应用规格](<2026-10-07-uilib-testing-spec.md>) |
| 专用性能压测、采样口径与基线 | [性能压测广场规格](<2026-10-14-uilib-performance-testing-spec.md>) |
| 文本、Markdown、富文本与数学内容 | [文本规格](<2026-10-07-uilib-text-content-spec.md>) |
| 图像来源、尺寸、加载状态与物品图标 | [图像规格](<2026-10-07-uilib-image-spec.md>) |
| 字体整形、光栅及渲染 | [字体规格](<2026-10-07-uilib-font-renderer-spec.md>) |
| 独立配置声明、状态与持久化 | [无平台配置规格](<2026-10-11-uilib-config-spec.md>) |
| 配置页面视觉与编辑流程 | [配置页面规格](<2026-10-12-uilib-settings-page-design.md>) |
| 独立桌面窗口与应用宿主 | [桌面宿主规格](<2026-10-11-uilib-desktop-spec.md>) |
| MC宿主有序合批 | [渲染优化规格](<2026-10-07-mc-host-render-optimization-spec.md>) |

## 规划中的任务目标

| 任务 | 目标稿 |
| --- | --- |
| 局部失效传播与增量文字测量 | [增量更新目标稿](<2026-10-14-uilib-incremental-update-spec.md>) |
| 大列表更新与滚动的性能收敛 | [大列表性能目标稿](<2026-10-14-uilib-large-list-performance-spec.md>) |
| 真实应用案例驱动的新能力：收件箱与内容浏览 | [现代应用案例目标稿](<2026-10-14-uilib-modern-app-cases-spec.md>) |
| 收件箱尽可能接近固定 Gmail 前端参考 | [收件箱前端还原目标稿](<2026-10-15-uilib-mailbox-fidelity-spec.md>) |
| 支撑真实交互行的虚拟集合候选专题 | [虚拟集合候选稿](<2026-10-14-uilib-virtual-list-spec.md>) |

实现不足记录为偏差，不能反向修改已确认合同。局部机制及失败原因维护在源码旁；临时计划、调查与运行记录归忽略的 Superpowers 目录，协作流程沿[AGENTS.md](<../AGENTS.md>)，不在规格中维护提交、审计或编排标准。
