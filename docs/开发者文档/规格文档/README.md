# 规格入口

[核心职责规格](<../../superpowers/specs/2026-10-07-uilib-core-contract-design.md>)定义状态、布局、生命周期、输入权威、业务边界及平台边界。模块行为和验收在对应规格维护，使用或修改模块时同时读取核心与模块合同；不以文件多少建立多套相互覆盖的要求。

| 模块 | 规格 |
| --- | --- |
| 主题与控件文案 | [主题规格](<../../superpowers/specs/2026-10-07-uilib-theme-spec.md>) |
| 草稿、提交、撤销与输入法 | [编辑规格](<../../superpowers/specs/2026-10-07-uilib-editing-spec.md>) |
| 声明式页面与宿主生命周期 | [页面接入规格](<../../superpowers/specs/2026-10-07-uilib-page-host-spec.md>) |
| 测试应用、场景与报告 | [测试应用规格](<../../superpowers/specs/2026-10-07-uilib-testing-spec.md>) |
| 文本、Markdown、富文本与数学内容 | [文本规格](<../../superpowers/specs/2026-10-07-uilib-text-content-spec.md>) |
| 图像来源、尺寸、加载状态与物品图标 | [图像规格](<../../superpowers/specs/2026-10-07-uilib-image-spec.md>) |
| 字体整形、光栅及渲染 | [字体规格](<../../superpowers/specs/2026-10-07-uilib-font-renderer-spec.md>) |
| MC宿主有序合批 | [渲染优化规格](<../../superpowers/specs/2026-10-07-mc-host-render-optimization-spec.md>) |

[核心骨架实现设计](<../../superpowers/specs/2026-10-07-uilib-core-implementation-design.md>)记录机制与接口选择，服从对应规格。源码、测试和使用指南描述实现，不另立要求。

旧规范正文已退役；[历史裁定与调查](<反向规格/UILib反向需求基线.md>)只用于追溯，不凭旧母本、编号或未采纳建议恢复要求。未覆盖能力保持现状，必要规格缺口补到对应职责。
